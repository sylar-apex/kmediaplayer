package org.app.kmediaplayer.ui.video

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Frames of one open file at chosen times; kept open so further frames only cost a seek. */
interface FrameSource {
    suspend fun frameAt(ms: Long): ImageBitmap?
    fun close()
}

/** Opens a [FrameSource] for the seek-bar preview, or null where the platform can't grab frames. */
expect object SeekPreview {
    suspend fun open(uri: String, maxWidth: Int): FrameSource?
}

/**
 * A YouTube-style storyboard: small frames across the whole video, grabbed in the background as soon as playback
 * starts, coarse first (every 1/8, then 1/16, …) so the bar is covered early. Hovering shows the nearest frame
 * already grabbed straight away, and the hovered spot jumps the queue.
 */
class SeekStoryboard(private val uri: String, private val durationMs: Long) {
    val frames = mutableStateMapOf<Int, ImageBitmap>()
    private val wanted = MutableStateFlow<Int?>(null)
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    init {
        scope.launch {
            val source = SeekPreview.open(uri, WIDTH) ?: return@launch
            try {
                val order = coarseToFine()
                // Slots already attempted, grabbed or not, so a frame that fails isn't retried forever.
                val tried = HashSet<Int>()
                var next = 0
                while (isActive && tried.size < SLOTS) {
                    val slot = wanted.value?.takeIf { it !in tried }?.also { wanted.value = null }
                        ?: generateSequence(next) { it + 1 }.takeWhile { it < order.size }.map { order[it] }.firstOrNull { it !in tried }
                            ?.also { next = order.indexOf(it) + 1 }
                        ?: break
                    tried += slot
                    val img = source.frameAt(slotMs(slot)) ?: continue
                    withContext(Dispatchers.Main) { frames[slot] = img }
                }
            } finally {
                source.close()
            }
        }
    }

    /** The grabbed frame closest to [ms], if any yet; also moves that spot to the front of the queue. */
    fun nearest(ms: Long): ImageBitmap? {
        val slot = slotOf(ms)
        if (slot !in frames) wanted.value = slot
        for (d in 0 until SLOTS) {
            frames[slot - d]?.let { return it }
            frames[slot + d]?.let { return it }
        }
        return null
    }

    fun close() = scope.cancel()

    private fun slotOf(ms: Long) = (ms * SLOTS / durationMs.coerceAtLeast(1)).toInt().coerceIn(0, SLOTS - 1)
    private fun slotMs(slot: Int) = (durationMs * slot / SLOTS) + durationMs / (SLOTS * 2)

    private fun coarseToFine(): List<Int> {
        val out = LinkedHashSet<Int>()
        var step = SLOTS / 8
        while (step >= 1) { for (i in 0 until SLOTS step step) out += i; step /= 2 }
        for (i in 0 until SLOTS) out += i
        return out.toList()
    }

    private companion object {
        const val SLOTS = 100
        const val WIDTH = 240
    }
}
