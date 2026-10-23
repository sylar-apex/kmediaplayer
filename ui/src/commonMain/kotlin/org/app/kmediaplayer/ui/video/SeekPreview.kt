package org.app.kmediaplayer.ui.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Frames of one open file at chosen times; kept open so further frames only cost a seek. */
interface FrameSource {
    /** [exact]: the frame a click would land on (follows the player's fast-seek setting); else any nearby frame. */
    suspend fun frameAt(ms: Long, exact: Boolean = false): ImageBitmap?
    fun close()
}

/** Opens a [FrameSource] for the seek-bar preview, or null where the platform can't grab frames. */
expect object SeekPreview {
    suspend fun open(uri: String, maxWidth: Int): FrameSource?
}

/**
 * A YouTube-style storyboard: small frames across the whole video, grabbed in the background as soon as playback
 * starts, coarse first (every 1/8, then 1/16, …) so the bar is covered early. Hovering shows the nearest storyboard
 * frame at once, and the exact frame for the hovered second is grabbed ahead of everything else, so what the preview
 * shows is what a click lands on.
 */
class SeekStoryboard(private val uri: String, private val durationMs: Long) {
    val frames = mutableStateMapOf<Int, ImageBitmap>()
    /** The exact frame for one second of the video: (second, frame). */
    var exact by mutableStateOf<Pair<Long, ImageBitmap>?>(null)
        private set
    private val exactWanted = MutableStateFlow<Long?>(null)
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    init {
        scope.launch {
            val source = SeekPreview.open(uri, WIDTH) ?: return@launch
            try {
                val order = coarseToFine().iterator()
                while (isActive) {
                    // The hovered second always goes first; between hovers, keep filling the storyboard.
                    val second = exactWanted.getAndUpdate { null }
                    if (second != null) {
                        source.frameAt(second * 1000, exact = true)?.let { img -> withContext(Dispatchers.Main) { exact = second to img } }
                        continue
                    }
                    if (!order.hasNext()) { exactWanted.first { it != null }; continue }
                    val slot = order.next()
                    if (slot in frames) continue
                    source.frameAt(slotMs(slot))?.let { img -> withContext(Dispatchers.Main) { frames[slot] = img } }
                }
            } finally {
                source.close()
            }
        }
    }

    /** What to show for [ms]: its exact frame when grabbed, else the nearest storyboard frame while it is fetched. */
    fun preview(ms: Long): ImageBitmap? {
        val second = ms / 1000
        exact?.takeIf { it.first == second }?.let { return it.second }
        if (exactWanted.value != second) exactWanted.value = second
        val slot = slotOf(ms)
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
