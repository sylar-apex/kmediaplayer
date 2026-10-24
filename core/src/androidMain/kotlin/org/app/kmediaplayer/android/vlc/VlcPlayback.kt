package org.app.kmediaplayer.android.vlc

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.SurfaceView
import org.app.kmediaplayer.engine.EngineState
import org.app.kmediaplayer.engine.FormatInfo
import org.app.kmediaplayer.engine.TrackInfo
import org.app.kmediaplayer.engine.TrackType
import org.app.kmediaplayer.engine.vlcOptions
import org.app.kmediaplayer.model.StreamExtras
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

/** Events from [VlcPlayback], delivered on the main thread. */
internal interface VlcCallback {
    fun onPlayingChanged(playing: Boolean)
    fun onStateChanged(state: EngineState)
    fun onVideoSize(width: Int, height: Int)
    fun onTracksChanged()
    fun onBufferProgress(percent: Int)
    fun onEnded()
    fun onError()
}

/**
 * Plays one item at a time through libVLC, the Android playback engine. libVLC decodes with MediaCodec
 * when [open] asks for hardware and falls back to its FFmpeg decoders for everything else.
 */
internal class VlcPlayback(private val context: Context, private val callback: VlcCallback) {
    private val libVlc: LibVLC by lazy { LibVLC(context, ArrayList(VlcOptions.libVlcArgs)) }
    private var player: MediaPlayer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var surface: SurfaceView? = null
    /** False until libVLC is told to play; the input is opened lazily on the first play(). */
    private var started = false
    private var pendingSeekMs = -1L

    var state: EngineState = EngineState.Idle
        private set
    var playWhenReady: Boolean = false
        private set
    var currentUri: String? = null
        private set

    val isOpen: Boolean get() = player != null
    val isPlaying: Boolean get() = player?.isPlaying == true
    val position: Long get() = player?.time?.coerceAtLeast(0) ?: 0
    val duration: Long get() = player?.length?.coerceAtLeast(0) ?: 0

    var rate: Float = 1f
        set(v) { field = v; player?.setRate(v) }

    /** Player volume, 0..1, as the engine reports it. */
    var volume: Float = 1f
        set(v) { field = v; player?.volume = appliedVolume }

    /** Loudness multiplier from the volume boost setting, 1 = none. */
    var boost: Float = 1f
        set(v) { field = v; player?.volume = appliedVolume }

    /** libVLC's software gain, 0..200 where above 100 amplifies. */
    private val appliedVolume: Int get() = (volume * boost * 100).toInt().coerceIn(0, 200)

    /**
     * Opens [uri] and seeks to [startMs]. [hardware] lets libVLC use MediaCodec; false forces FFmpeg.
     * [extras] adds a separate audio stream and the User-Agent for network items.
     */
    fun open(uri: String, startMs: Long, hardware: Boolean, playWhenReady: Boolean, extras: StreamExtras = StreamExtras.None) {
        reportedSize = null
        close()
        val mp = MediaPlayer(libVlc)
        mp.setEventListener(::onEvent)
        val media = newMedia(uri).apply {
            setHWDecoderEnabled(hardware, false)
            if (startMs > 0) addOption(":start-time=${startMs / 1000.0}")
            VlcOptions.mediaOptions.forEach(::addOption)
            // Network streams buffer a little ahead for busy Wi-Fi moments; libVLC waits this long before the first frame, so keep it short.
            if (uri.startsWith("http")) addOption(":network-caching=1500")
            extras.vlcOptions().forEach(::addOption)
        }
        mp.media = media
        media.release()
        mp.setRate(rate)
        mp.volume = appliedVolume
        player = mp
        currentUri = uri
        surface?.let { attachTo(mp, it) }
        this.playWhenReady = playWhenReady
        started = false
        setState(EngineState.Buffering)
        // libVLC drops a pause() issued while the input is still opening, so a paused open just waits for play().
        if (playWhenReady) start(mp)
    }

    private fun start(mp: MediaPlayer) {
        started = true
        mp.play()
        if (pendingSeekMs >= 0) { mp.setTime(pendingSeekMs, false); pendingSeekMs = -1 }
    }

    private fun newMedia(uri: String): Media {
        val parsed = Uri.parse(uri)
        return if (parsed.scheme == "content") {
            val fd = context.contentResolver.openFileDescriptor(parsed, "r") ?: error("Cannot open $uri")
            descriptor = fd
            Media(libVlc, fd.fileDescriptor)
        } else Media(libVlc, parsed)
    }

    fun play() {
        playWhenReady = true
        val mp = player ?: return
        when {
            !started -> start(mp)
            state == EngineState.Ended -> { mp.stop(); mp.play(); setState(EngineState.Buffering) }
            else -> mp.play()
        }
    }

    fun pause() {
        playWhenReady = false
        if (started) player?.pause()
    }

    fun seekTo(ms: Long, fast: Boolean) {
        val mp = player ?: return
        if (!started) { pendingSeekMs = ms; return }
        if (state == EngineState.Ended) { mp.play(); if (!playWhenReady) mp.pause() }
        mp.setTime(ms.coerceAtLeast(0), fast)
    }

    fun close() {
        player?.let { mp ->
            mp.setEventListener(null)
            mp.vlcVout.detachViews()
            mp.stop()
            mp.release()
        }
        player = null
        started = false
        pendingSeekMs = -1
        runCatching { descriptor?.close() }
        descriptor = null
        currentUri = null
        playWhenReady = false
        setState(EngineState.Idle)
    }

    fun release() {
        close()
        surface = null
    }

    // region video output

    /** Renders into [view]; libVLC keeps the picture inside the view's bounds, so the view sets the aspect. */
    fun attachSurface(view: SurfaceView) {
        surface = view
        player?.let { attachTo(it, view) }
    }

    fun detachSurface() {
        player?.vlcVout?.let { if (it.areViewsAttached()) it.detachViews() }
        surface = null
    }

    /** Call when the hosting view changes size so libVLC renders at the new resolution. */
    fun onSurfaceSizeChanged(width: Int, height: Int) {
        val mp = player ?: return
        if (width <= 0 || height <= 0) return
        mp.vlcVout.setWindowSize(width, height)
        mp.aspectRatio = "$width:$height"
        mp.scale = 0f
    }

    private fun attachTo(mp: MediaPlayer, view: SurfaceView) {
        val vout = mp.vlcVout
        if (vout.areViewsAttached()) vout.detachViews()
        vout.setVideoView(view)
        vout.attachViews()
        onSurfaceSizeChanged(view.width, view.height)
    }

    // endregion

    // region tracks and formats

    fun tracks(type: TrackType): List<TrackInfo> {
        val mp = player ?: return emptyList()
        val (all, selected) = when (type) {
            TrackType.Audio -> mp.audioTracks to mp.audioTrack
            TrackType.Text -> mp.spuTracks to mp.spuTrack
        }
        return all.orEmpty().filter { it.id >= 0 }.map { TrackInfo(it.id.toString(), it.name ?: "Track ${it.id}", it.id == selected) }
    }

    fun selectTrack(type: TrackType, id: String) {
        val mp = player ?: return
        val trackId = id.toIntOrNull() ?: return
        if (type == TrackType.Audio) mp.setAudioTrack(trackId) else mp.setSpuTrack(trackId)
    }

    /** Disabling a type selects libVLC's "-1" (none) track. */
    fun setTrackTypeDisabled(type: TrackType, disabled: Boolean) {
        val mp = player ?: return
        if (!disabled) return
        if (type == TrackType.Audio) mp.setAudioTrack(-1) else mp.setSpuTrack(-1)
    }

    fun videoFormat(): FormatInfo? {
        val track = player?.currentVideoTrack ?: return null
        val fps = if (track.frameRateDen > 0) track.frameRateNum.toFloat() / track.frameRateDen else 0f
        return FormatInfo(codecs = track.codec, sampleMimeType = track.codec, width = track.width, height = track.height, frameRate = fps, bitrate = track.bitrate, language = track.language)
    }

    fun audioFormat(): FormatInfo? {
        val media = player?.media ?: return null
        val selected = player?.audioTrack ?: return null
        val track = (0 until media.trackCount).map { media.getTrack(it) }
            .filterIsInstance<IMedia.AudioTrack>().firstOrNull { it.id == selected }
        media.release()
        track ?: return null
        return FormatInfo(codecs = track.codec, sampleMimeType = track.codec, channelCount = track.channels, sampleRate = track.rate, bitrate = track.bitrate, language = track.language)
    }

    fun setEqualizer(eq: MediaPlayer.Equalizer?) { player?.setEqualizer(eq) }

    // endregion

    private fun onEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Buffering -> {
                callback.onBufferProgress(event.buffering.toInt().coerceIn(0, 100))
                if (event.buffering < 100f) setState(EngineState.Buffering) else { setState(EngineState.Ready); reportSize() }
            }
            MediaPlayer.Event.Playing -> { setState(EngineState.Ready); callback.onPlayingChanged(true); reportSize() }
            MediaPlayer.Event.Paused -> { setState(EngineState.Ready); callback.onPlayingChanged(false) }
            MediaPlayer.Event.Stopped -> callback.onPlayingChanged(false)
            MediaPlayer.Event.EndReached -> { setState(EngineState.Ended); callback.onPlayingChanged(false); callback.onEnded() }
            MediaPlayer.Event.EncounteredError -> { setState(EngineState.Idle); callback.onError() }
            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> { callback.onTracksChanged(); reportSize() }
            MediaPlayer.Event.Vout -> reportSize()
        }
    }

    /** Size last sent; the next item reuses the video output, so Vout alone would never report its size. */
    private var reportedSize: Pair<Int, Int>? = null

    /** Sends the picture size (sample aspect and rotation applied) once the video track is known and it changed. */
    private fun reportSize() {
        val t = player?.currentVideoTrack?.takeIf { it.width > 0 && it.height > 0 } ?: return
        val sar = if (t.sarDen > 0 && t.sarNum > 0) t.sarNum.toFloat() / t.sarDen else 1f
        val rotated = t.orientation >= IMedia.VideoTrack.Orientation.LeftTop
        val w = (t.width * sar).toInt()
        val size = if (rotated) t.height to w else w to t.height
        if (size == reportedSize) return
        reportedSize = size
        callback.onVideoSize(size.first, size.second)
    }

    private fun setState(s: EngineState) {
        if (state == s) return
        state = s
        callback.onStateChanged(s)
    }
}
