package org.app.kmediaplayer.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

/**
 * The latest decoded video frame, BGRA, shared between libVLC's decoder thread (writer) and the
 * Compose render thread (reader). Two pixel arrays are swapped so the reader never sees a half
 * written frame and the writer never waits for drawing.
 */
class VideoFrameBuffer {
    @Volatile var width: Int = 0
        private set
    @Volatile var height: Int = 0
        private set

    private var back = ByteArray(0)
    @Volatile private var front = ByteArray(0)

    private val _version = MutableStateFlow(0L)
    /** Increments for every new frame; collect it to redraw. */
    val version: StateFlow<Long> = _version.asStateFlow()

    /** Called on the decoder thread with libVLC's RV32 buffer. */
    internal fun publish(source: ByteBuffer, frameWidth: Int, frameHeight: Int) {
        val size = frameWidth * frameHeight * 4
        if (back.size != size) back = ByteArray(size)
        source.rewind()
        source.get(back, 0, size)
        synchronized(this) {
            val t = front; front = back; back = t
            width = frameWidth; height = frameHeight
        }
        _version.value++
    }

    /** Runs [read] with the current frame's pixels; the array must not be kept. */
    inline fun <T> withFrame(read: (pixels: ByteArray, width: Int, height: Int) -> T): T? = synchronized(this) {
        if (width == 0 || height == 0 || frontPixels().isEmpty()) null else read(frontPixels(), width, height)
    }

    @PublishedApi internal fun frontPixels(): ByteArray = front

    fun clear() {
        synchronized(this) { width = 0; height = 0 }
        _version.value++
    }
}
