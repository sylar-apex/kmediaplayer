package org.app.kmediaplayer.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer

/**
 * Frames of one file at many times, for the seek-bar storyboard. Unlike [VlcThumbnailer], which opens the file for
 * every frame, this keeps one hidden player on the file and seeks it, so each further frame costs one seek and decode.
 */
class VlcFrameSession private constructor(private val maxWidth: Int) : AutoCloseable {
    private val lock = Mutex()
    @Volatile private var pending: CompletableDeferred<ThumbnailFrame>? = null
    @Volatile private var framesSinceSeek = 0
    private lateinit var player: EmbeddedMediaPlayer

    /** The frame nearest [ms] (keyframe accuracy, which is what makes it fast), or null if it doesn't come in time. */
    suspend fun frameAt(ms: Long): ThumbnailFrame? = lock.withLock {
        withContext(Dispatchers.IO) {
            val result = CompletableDeferred<ThumbnailFrame>()
            framesSinceSeek = 0
            pending = result
            player.controls().setTime(ms)
            if (!player.status().isPlaying) player.controls().play()
            val frame = withTimeoutOrNull(2_000) { result.await() }
            pending = null
            player.controls().setPause(true)
            frame
        }
    }

    override fun close() {
        runCatching { player.controls().stop(); player.release() }
    }

    private inner class Format : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            val w = minOf(maxWidth, sourceWidth).coerceAtLeast(1)
            val h = (sourceHeight.toLong() * w / sourceWidth.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            return RV32BufferFormat(w, h)
        }
        override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {}
        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
    }

    private inner class Frames : RenderCallback {
        override fun lock(mediaPlayer: MediaPlayer) {}
        override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat, displayWidth: Int, displayHeight: Int) {
            val target = pending ?: return
            // The first frame after a seek can still be the old picture.
            if (++framesSinceSeek < 2) return
            val (w, h) = bufferFormat.width to bufferFormat.height
            val bytes = ByteArray(w * h * 4)
            nativeBuffers[0].rewind(); nativeBuffers[0].get(bytes, 0, bytes.size)
            target.complete(ThumbnailFrame(bytes, w, h, mediaPlayer.status().length()))
        }
        override fun unlock(mediaPlayer: MediaPlayer) {}
    }

    companion object {
        /** Opens [path] in a hidden, muted player; null when VLC isn't available. */
        suspend fun open(path: String, maxWidth: Int): VlcFrameSession? = withContext(Dispatchers.IO) {
            val factory = VlcRuntime.factory ?: return@withContext null
            val session = VlcFrameSession(maxWidth)
            session.player = factory.mediaPlayers().newEmbeddedMediaPlayer().apply {
                videoSurface().set(factory.videoSurfaces().newVideoSurface(session.Format(), session.Frames(), true))
                audio().setMute(true)
            }
            if (!session.player.media().start(path.toMrl(), ":no-audio", ":no-spu", ":avcodec-threads=2", ":input-fast-seek")) {
                session.close(); return@withContext null
            }
            session.player.controls().setPause(true)
            session
        }
    }
}
