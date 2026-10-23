package org.app.kmediaplayer.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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
 * Grabs a frame from ~12% into a video with hidden, muted libVLC players, scaled down by libVLC
 * itself, so any format VLC plays gets a thumbnail. A small pool of players works in parallel;
 * slow files time out. Safe to call from any thread: libVLC is touched only on [Dispatchers.IO].
 */
object VlcThumbnailer {
    private val POOL_SIZE = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
    private val idle = Channel<Grabber>(Channel.UNLIMITED)
    @Volatile private var created = 0

    val isAvailable: Boolean get() = VlcRuntime.isAvailable

    /** A frame of [path]: at [atMs] when given (seek-bar previews), else a little way in (list thumbnails). */
    suspend fun frame(path: String, maxWidth: Int = 320, timeoutMs: Long = 5_000, atMs: Long? = null): ThumbnailFrame? = withContext(Dispatchers.IO) {
        val grabber = acquire() ?: return@withContext null
        try { grabber.frame(path, maxWidth, timeoutMs, atMs) } finally { idle.trySend(grabber) }
    }

    /** An idle player, a new one while the pool is below [POOL_SIZE], or waits for one to free up. */
    private suspend fun acquire(): Grabber? {
        idle.tryReceive().getOrNull()?.let { return it }
        val factory = VlcRuntime.factory ?: return null
        synchronized(this) { if (created < POOL_SIZE) { created++; return Grabber(factory.newGrabberPlayer()) } }
        return idle.receive()
    }

    private fun uk.co.caprica.vlcj.factory.MediaPlayerFactory.newGrabberPlayer(): (Grabber) -> EmbeddedMediaPlayer = { g ->
        mediaPlayers().newEmbeddedMediaPlayer().apply {
            videoSurface().set(videoSurfaces().newVideoSurface(g.Format(), g.Frames(), true))
            audio().setMute(true)
            events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    // Seek once the input is open; frames before the seek are ignored.
                    mediaPlayer.submit {
                        g.atMs?.let { mediaPlayer.controls().setTime(it) } ?: mediaPlayer.controls().setPosition(0.12f)
                        g.seeked = true
                    }
                }
                override fun error(mediaPlayer: MediaPlayer) { g.pending?.cancel() }
            })
        }
    }

    /** One hidden player and the state of the grab it is doing. */
    private class Grabber(build: (Grabber) -> EmbeddedMediaPlayer) {
        @Volatile var maxWidth = 320
        @Volatile var pending: CompletableDeferred<ThumbnailFrame>? = null
        @Volatile var seeked = false
        @Volatile var atMs: Long? = null
        private val player = build(this)

        suspend fun frame(path: String, maxWidth: Int, timeoutMs: Long, atMs: Long?): ThumbnailFrame? {
            this.maxWidth = maxWidth
            this.atMs = atMs
            val result = CompletableDeferred<ThumbnailFrame>()
            pending = result
            seeked = false
            player.media().play(path.toMrl(), ":no-audio", ":no-spu", ":avcodec-threads=2", ":input-fast-seek")
            val frame = withTimeoutOrNull(timeoutMs) { runCatching { result.await() }.getOrNull() }
            pending = null
            player.controls().stop()
            return frame
        }

        /** Requests frames already scaled to [maxWidth], keeping the aspect ratio. */
        inner class Format : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                val w = minOf(maxWidth, sourceWidth).coerceAtLeast(1)
                val h = (sourceHeight.toLong() * w / sourceWidth.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                return RV32BufferFormat(w, h)
            }
            override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {}
            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
        }

        inner class Frames : RenderCallback {
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
}
