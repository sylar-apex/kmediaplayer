package org.app.kmediaplayer.desktop

import org.app.kmediaplayer.model.StreamExtras
import org.app.kmediaplayer.engine.vlcOptions

import org.app.kmediaplayer.engine.AudioEffectsController
import org.app.kmediaplayer.engine.BackendCallback
import org.app.kmediaplayer.engine.Decoder
import org.app.kmediaplayer.engine.EngineState
import org.app.kmediaplayer.engine.FormatInfo
import org.app.kmediaplayer.engine.PlaybackBackend
import org.app.kmediaplayer.engine.TrackInfo
import org.app.kmediaplayer.engine.TrackType
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.TrackType as VlcTrackType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.net.URI
import java.nio.ByteBuffer

/**
 * Desktop playback (macOS, Windows, Linux) through libVLC, which decodes practically every format
 * with hardware acceleration where the OS offers it (VideoToolbox, D3D11/DXVA, VA-API). Frames are
 * rendered into [frames] so the Compose UI can draw controls on top of the video.
 */
class VlcjBackend(private val factory: MediaPlayerFactory) : PlaybackBackend {
    override var callback: BackendCallback? = null

    /** Decoded video for the UI to draw. */
    val frames = VideoFrameBuffer()

    private val player: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()
    private var started = false
    private var pendingSeekMs = -1L

    @Volatile override var state: EngineState = EngineState.Idle
        private set

    override val isPlaying: Boolean get() = player.status().isPlaying
    override val position: Long get() = player.status().time().coerceAtLeast(0)
    override val duration: Long get() = player.status().length().coerceAtLeast(0)

    override var volume: Float = 1f
        set(v) { field = v; applyVolume() }

    /** The player's "device volume" (volume gesture), 0..1, applied on top of [volume]. */
    var outputGain: Float = 1f
        set(v) { field = v.coerceIn(0f, 1f); applyVolume() }

    /** Loudness multiplier from volume boost, 1 = none. */
    internal var boost: Float = 1f
        set(v) { field = v; applyVolume() }

    override var speed: Float = 1f
        set(v) { field = v; player.controls().setRate(v) }

    private val effectsImpl by lazy { VlcjAudioEffects(factory, player) { boost = it } }
    override val effects: AudioEffectsController get() = effectsImpl

    init {
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(FormatCallback(), FrameCallback(), true))
        player.events().addMediaPlayerEventListener(Events())
    }

    override fun open(uri: String, startMs: Long, playWhenReady: Boolean, decoder: Decoder, extras: StreamExtras) {
        val options = buildList {
            if (startMs > 0) add(":start-time=${startMs / 1000.0}")
            if (decoder == Decoder.SW) add(":avcodec-hw=none")
            // Network streams buffer a little ahead for busy Wi-Fi moments; libVLC waits this long before the first frame, so keep it short.
            if (uri.startsWith("http")) add(":network-caching=1500")
            addAll(extras.vlcOptions())
        }.toTypedArray()
        started = false
        pendingSeekMs = -1
        setState(EngineState.Buffering)
        player.media().prepare(uri.toMrl(), *options)
        player.controls().setRate(speed)
        if (playWhenReady) start()
    }

    private fun start() {
        started = true
        player.controls().play()
    }

    override fun play() { if (!started) start() else player.controls().setPause(false) }
    override fun pause() { if (started) player.controls().setPause(true) }

    override fun stop() {
        started = false
        player.controls().stop()
        frames.clear()
        setState(EngineState.Idle)
    }

    override fun seekTo(positionMs: Long, fast: Boolean) {
        if (!started) { pendingSeekMs = positionMs; return }
        if (state == EngineState.Ended) player.controls().play()
        player.controls().setTime(positionMs)
    }

    // region tracks

    override fun tracks(type: TrackType): List<TrackInfo> {
        val (all, selected) = when (type) {
            TrackType.Audio -> player.audio().trackDescriptions() to player.audio().track()
            TrackType.Text -> player.subpictures().trackDescriptions() to player.subpictures().track()
        }
        return all.filter { it.id() >= 0 }.map { TrackInfo(it.id().toString(), it.description() ?: "Track ${it.id()}", it.id() == selected) }
    }

    override fun selectTrack(type: TrackType, id: String) {
        val trackId = id.toIntOrNull() ?: return
        if (type == TrackType.Audio) player.audio().setTrack(trackId) else player.subpictures().setTrack(trackId)
    }

    override fun setTrackTypeDisabled(type: TrackType, disabled: Boolean) {
        if (!disabled) return
        if (type == TrackType.Audio) player.audio().setTrack(-1) else player.subpictures().setTrack(-1)
    }

    override fun videoFormat(): FormatInfo? {
        val t = player.media().info()?.videoTracks()?.firstOrNull() ?: return null
        val fps = if (t.frameRateBase() > 0) t.frameRate().toFloat() / t.frameRateBase() else 0f
        return FormatInfo(codecs = t.codecName(), sampleMimeType = t.codecName(), width = t.width(), height = t.height(), frameRate = fps, bitrate = t.bitRate(), language = t.language())
    }

    override fun audioFormat(): FormatInfo? {
        val t = player.media().info()?.audioTracks()?.firstOrNull { it.id() == player.audio().track() } ?: return null
        return FormatInfo(codecs = t.codecName(), sampleMimeType = t.codecName(), channelCount = t.channels(), sampleRate = t.rate(), bitrate = t.bitRate(), language = t.language())
    }

    // endregion

    override fun release() {
        player.controls().stop()
        player.release()
    }

    private fun applyVolume() { player.audio().setVolume((volume * outputGain * boost * 100).toInt().coerceIn(0, 200)) }

    private fun setState(s: EngineState) {
        if (state == s) return
        state = s
        callback?.onStateChanged(s)
    }

    /** libVLC asks for the buffer layout once per video size; RV32 is BGRA, which Skia draws directly. */
    /** [w]×[h] scaled down (never up) to fit the largest attached screen in device pixels, keeping the aspect. */
    private fun fitToScreen(w: Int, h: Int): Pair<Int, Int> {
        val (sw, sh) = maxScreenPixels
        if (w <= 0 || h <= 0 || (w <= sw && h <= sh)) return w to h
        val scale = minOf(sw.toDouble() / w, sh.toDouble() / h)
        // Even sizes: chroma-subsampled video scales cleanly.
        return ((w * scale).toInt() and 1.inv()).coerceAtLeast(2) to ((h * scale).toInt() and 1.inv()).coerceAtLeast(2)
    }

    private val maxScreenPixels: Pair<Int, Int> by lazy {
        runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { d ->
                val t = d.defaultConfiguration.defaultTransform
                val b = d.defaultConfiguration.bounds
                (b.width * t.scaleX).toInt() to (b.height * t.scaleY).toInt()
            }.maxByOrNull { it.first * it.second }
        }.getOrNull()?.takeIf { it.first > 0 && it.second > 0 } ?: (3840 to 2160)
    }

    private inner class FormatCallback : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            callback?.onVideoSize(sourceWidth, sourceHeight)
            // Frames are copied into the window every time, so never ask for more pixels than the screen can show:
            // a 4K video on a smaller display costs a fraction of the copying, and VLC scales on its decode threads.
            val (w, h) = fitToScreen(sourceWidth, sourceHeight)
            return RV32BufferFormat(w, h)
        }
        override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {
            callback?.onVideoSize(displayWidth, displayHeight)
        }
        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
    }

    private inner class FrameCallback : RenderCallback {
        override fun lock(mediaPlayer: MediaPlayer) {}
        override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat, displayWidth: Int, displayHeight: Int) {
            frames.publish(nativeBuffers[0], bufferFormat.width, bufferFormat.height)
        }
        override fun unlock(mediaPlayer: MediaPlayer) {}
    }

    /** libVLC event thread: only record state and forward; never call back into libVLC here. */
    private inner class Events : MediaPlayerEventAdapter() {
        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) = setState(if (newCache < 100f) EngineState.Buffering else EngineState.Ready)
        override fun playing(mediaPlayer: MediaPlayer) {
            setState(EngineState.Ready)
            callback?.onPlayingChanged(true)
            if (pendingSeekMs >= 0) { val ms = pendingSeekMs; pendingSeekMs = -1; mediaPlayer.submit { mediaPlayer.controls().setTime(ms) } }
            mediaPlayer.submit { applyVolume() }
        }
        override fun paused(mediaPlayer: MediaPlayer) { setState(EngineState.Ready); callback?.onPlayingChanged(false) }
        override fun stopped(mediaPlayer: MediaPlayer) = callback?.onPlayingChanged(false) ?: Unit
        override fun finished(mediaPlayer: MediaPlayer) { setState(EngineState.Ended); callback?.onPlayingChanged(false); callback?.onEnded() }
        override fun error(mediaPlayer: MediaPlayer) { setState(EngineState.Idle); callback?.onError("ERROR_CODE_DECODING_FORMAT_UNSUPPORTED") }
        override fun elementaryStreamAdded(mediaPlayer: MediaPlayer, type: VlcTrackType, id: Int) = callback?.onTracksChanged() ?: Unit
        override fun elementaryStreamSelected(mediaPlayer: MediaPlayer, type: VlcTrackType, id: Int) = callback?.onTracksChanged() ?: Unit
    }
}

/**
 * What libVLC opens: a plain absolute path for local files (including Java's single-slash
 * `file:/...` URIs), and the URL itself for network streams.
 */
internal fun String.toMrl(): String = when {
    startsWith("file:") -> File(URI(this)).absolutePath
    contains("://") -> this
    else -> File(this).absolutePath
}
