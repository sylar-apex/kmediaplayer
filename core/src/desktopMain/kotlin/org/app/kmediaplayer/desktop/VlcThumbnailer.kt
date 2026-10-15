package org.app.kmediaplayer.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer

/** One decoded video frame, BGRA, plus the video's length when known. */
class ThumbnailFrame(val pixels: ByteArray, val width: Int, val height: Int, val durationMs: Long)

/**
 * Grabs a frame from ~12% into a video with a hidden, muted libVLC player, scaled down by libVLC
 * itself, so any format VLC plays gets a thumbnail. One file at a time; slow files time out.
 */
object VlcThumbnailer {
    private val lock = Mutex()
    private var player: EmbeddedMediaPlayer? = null
    @Volatile private var maxWidth = 320
    @Volatile private var pending: CompletableDeferred<ThumbnailFrame>? = null
    @Volatile private var seeked = false
    @Volatile private var size = 0 to 0

    val isAvailable: Boolean get() = VlcRuntime.isAvailable

    suspend fun frame(path: String, maxWidth: Int = 320, timeoutMs: Long = 5_000): ThumbnailFrame? = lock.withLock {
        val mp = player() ?: return null
        this.maxWidth = maxWidth
        val result = CompletableDeferred<ThumbnailFrame>()
        pending = result
        seeked = false
        withContext(Dispatchers.IO) { mp.media().play(path.toMrl(), ":no-audio", ":no-spu", ":avcodec-threads=2", ":input-fast-seek") }
        val frame = withTimeoutOrNull(timeoutMs) { result.await() }
        pending = null
        withContext(Dispatchers.IO) { mp.controls().stop() }
        frame
    }

    private fun player(): EmbeddedMediaPlayer? = player ?: VlcRuntime.factory?.let { factory ->
        factory.mediaPlayers().newEmbeddedMediaPlayer().apply {
            videoSurface().set(factory.videoSurfaces().newVideoSurface(Format(), Frames(), true))
            audio().setMute(true)
            events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    // Seek once the input is open; frames before the seek are ignored.
                    mediaPlayer.submit { mediaPlayer.controls().setPosition(0.12f); seeked = true }
                }
                override fun error(mediaPlayer: MediaPlayer) { pending?.cancel() }
            })
        }.also { player = it }
    }

    /** Requests frames already scaled to [maxWidth], keeping the aspect ratio. */
    private class Format : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            val w = minOf(maxWidth, sourceWidth).coerceAtLeast(1)
            val h = (sourceHeight.toLong() * w / sourceWidth.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            size = w to h
            return RV32BufferFormat(w, h)
        }
        override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {}
        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
    }

    private class Frames : RenderCallback {
        private var afterSeek = 0
        override fun lock(mediaPlayer: MediaPlayer) {}
        override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat, displayWidth: Int, displayHeight: Int) {
            val target = pending ?: return
            if (!seeked) { afterSeek = 0; return }
            // Skip the first frames after the seek; they can still be the pre-seek picture.
            if (++afterSeek < 3) return
            val (w, h) = bufferFormat.width to bufferFormat.height
            val bytes = ByteArray(w * h * 4)
            nativeBuffers[0].rewind(); nativeBuffers[0].get(bytes, 0, bytes.size)
            target.complete(ThumbnailFrame(bytes, w, h, mediaPlayer.status().length()))
        }
        override fun unlock(mediaPlayer: MediaPlayer) {}
    }
}
