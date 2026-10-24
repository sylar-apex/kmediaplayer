package org.app.kmediaplayer.android.hybrid

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.roundToInt
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.android.KMediaPlayerAndroid
import org.app.kmediaplayer.android.Media3Engine
import org.app.kmediaplayer.android.vlc.VlcAudioEffects
import org.app.kmediaplayer.android.vlc.VlcCallback
import org.app.kmediaplayer.android.vlc.VlcPlayback
import org.app.kmediaplayer.engine.AudioEffectsController
import org.app.kmediaplayer.engine.Decoder
import org.app.kmediaplayer.engine.EngineError
import org.app.kmediaplayer.engine.EngineListener
import org.app.kmediaplayer.engine.EngineState
import org.app.kmediaplayer.engine.FormatInfo
import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.engine.RepeatMode
import org.app.kmediaplayer.engine.TrackInfo
import org.app.kmediaplayer.engine.TrackType
import org.app.kmediaplayer.model.MediaFile
import org.app.kmediaplayer.model.StreamExtras
import org.app.kmediaplayer.settings.PlayerKeys
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The engine the app uses on Android. [Media3Engine] owns the queue and plays everything it can;
 * when an item needs libVLC (a container ExoPlayer cannot open, a codec without a decoder, or the SW
 * decoder chosen) this engine takes a "detour": ExoPlayer stops on that item and [VlcPlayback] plays
 * it, then the queue continues in ExoPlayer. Callers see one continuous player either way.
 */
@OptIn(UnstableApi::class)
object HybridEngine : MediaEngine {
    private const val TAG = "KMediaHybrid"
    private val exo = Media3Engine
    private val listeners = CopyOnWriteArrayList<EngineListener>()
    private val main = Handler(Looper.getMainLooper())

    /** File names by URI, so content:// items can still be routed by extension. */
    private val names = HashMap<String, String>()
    /** Separate audio stream and User-Agent by URI, for network items. */
    private val extras = HashMap<String, StreamExtras>()

    private val vlcHolder = lazy { VlcPlayback(KMediaPlayerAndroid.context, vlcCallback) }
    private val vlc: VlcPlayback by vlcHolder
    private val vlcEffects: VlcAudioEffects by lazy { VlcAudioEffects(vlc).also { it.applySaved() } }

    /** True while the current item is played by libVLC. */
    var isDetour: Boolean = false
        private set

    private var surface: SurfaceView? = null
    private var videoEnabled = true

    private var session: HybridSessionPlayer? = null

    /** Media3 player for media sessions; mirrors libVLC while a detour is active. */
    val sessionPlayer: Player
        get() = session?.takeIf { it.wraps(exo.exo) } ?: HybridSessionPlayer(exo.exo, this).also { session = it }

    init { exo.addListener(ExoForwarder()) }

    // region backend switching

    private fun currentUri(): String? = exo.currentItem?.localConfiguration?.uri?.toString()

    private fun routeOf(uri: String): Backend = if (uri in androidDecoded) Backend.Exo else PlaybackRouter.route(names[uri] ?: uri, decoder)

    /**
     * Items libVLC decodes too slowly here: 4K AV1 on phones without an AV1 chip falls further and further behind in
     * libVLC's decoder, so every frame arrives late and the picture freezes while the sound goes on. Android's own
     * AV1 decoder keeps up far better (it is what other players use), so ExoPlayer plays these.
     */
    private val androidDecoded = mutableSetOf<String>()
    /** Items ExoPlayer turned out unable to play after a hand-off; they stay with libVLC. */
    private val vlcOnly = mutableSetOf<String>()

    /** ExoPlayer can't play the current item after all: back to libVLC for good. */
    private fun keepWithVlc() { currentUri()?.let { androidDecoded -= it; vlcOnly += it } }

    /** On libVLC's first look at the tracks: moves an AV1 video to Android's decoder, at the same place. */
    private fun handOffSlowCodec(): Boolean {
        if (!isDetour) return false
        val codec = vlc.videoFormat()?.codecs?.lowercase()?.trim() ?: return false
        // libVLC names it by description ("AOMedia's AV1 Video") or fourcc ("av01").
        if ("av1" !in codec && codec != "av01") return false
        if (!PlaybackRouter.hasAndroidDecoder("video/av01")) return false
        val uri = currentUri() ?: return false
        if (uri in vlcOnly) return false
        val at = position
        val play = playWhenReady
        Log.i(TAG, "AV1: Android's decoder plays ${names[uri] ?: uri} from ${at}ms")
        androidDecoded += uri
        endDetour()
        exo.seekTo(exo.currentIndex, at)
        exo.exo.playWhenReady = play
        exo.prepare()
        dispatch { it.onTracksChanged(); it.onEvents() }
        return true
    }

    /** Hands the current queue item to libVLC, starting at [startMs]. */
    private fun startDetour(startMs: Long, playWhenReady: Boolean) {
        val uri = currentUri() ?: return
        if (!uri.isReadableFileUri()) {
            Log.w(TAG, "No read access to $uri")
            exo.exo.stop()
            dispatch { it.onError(EngineError(EngineError.NO_FILE_ACCESS)) }
            return
        }
        Log.i(TAG, "libVLC plays ${names[uri] ?: uri} from ${startMs}ms")
        vlcSize = null
        exo.exo.clearVideoSurface()
        exo.exo.stop()
        isDetour = true
        surface?.takeIf { videoEnabled }?.let(vlc::attachSurface)
        vlc.rate = exo.speed
        vlc.volume = exo.volume
        runCatching { vlc.open(uri, startMs, hardware = decoder != Decoder.SW, playWhenReady = playWhenReady, extras = extras[uri] ?: StreamExtras.None) }
            .onFailure { Log.e(TAG, "libVLC could not open $uri", it); endDetour(); dispatch { it.onError(EngineError("ERROR_CODE_IO_FILE_NOT_FOUND")) }; return }
        dispatch { it.onItemTransition(); it.onTracksChanged(); it.onEvents() }
        session?.refresh()
    }

    /** Leaves libVLC and gives the video surface back to ExoPlayer. */
    private fun endDetour() {
        if (!isDetour) return
        isDetour = false
        vlc.detachSurface()
        vlc.close()
        attachExoSurface()
        session?.refresh()
    }

    /** Prepares the current item in whichever backend it belongs to. */
    private fun prepareCurrent(playWhenReady: Boolean = exo.playWhenReady) {
        val uri = currentUri() ?: return exo.prepare()
        if (routeOf(uri) == Backend.Vlc) startDetour(exo.position, playWhenReady)
        else { endDetour(); exo.prepare() }
    }

    /** Moves the queue on after a libVLC item finished, following the repeat and pause settings. */
    private fun onDetourEnded() {
        when {
            repeatMode == RepeatMode.One -> { vlc.seekTo(0, fast = false); vlc.play() }
            // Stays on the finished item; play starts it again, next moves on.
            pauseAtEndOfItems -> dispatch { it.onStateChanged(EngineState.Ended); it.onIsPlayingChanged(false); it.onEvents() }
            exo.hasNext() -> { endDetour(); exo.seekToNext(); exo.exo.playWhenReady = true; prepareCurrent(true) }
            else -> dispatch { it.onStateChanged(EngineState.Ended); it.onEvents() }
        }
    }

    private inline fun dispatch(crossinline event: (EngineListener) -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) listeners.forEach(event)
        else main.post { listeners.forEach(event) }
    }

    /** Forwards ExoPlayer events, and turns "cannot play" errors into a libVLC detour. */
    private class ExoForwarder : EngineListener {
        override fun onIsPlayingChanged(playing: Boolean) { if (!isDetour) dispatch { it.onIsPlayingChanged(playing) } }
        override fun onStateChanged(state: EngineState) { if (!isDetour) dispatch { it.onStateChanged(state) } }
        override fun onItemTransition() {
            if (isDetour) return
            val uri = currentUri()
            if (uri != null && exo.exo.playbackState != Player.STATE_IDLE && routeOf(uri) == Backend.Vlc) {
                startDetour(0, exo.playWhenReady)
            } else dispatch { it.onItemTransition() }
        }
        override fun onVideoSizeChanged(width: Int, height: Int) { if (!isDetour) dispatch { it.onVideoSizeChanged(width, height) } }
        override fun onTracksChanged() {
            if (isDetour) return
            if (PlaybackRouter.hasUnplayableMainTrack(exo.exo.currentTracks)) { keepWithVlc(); startDetour(exo.position, exo.playWhenReady) }
            else dispatch { it.onTracksChanged() }
        }
        override fun onCues(lines: List<String>) { if (!isDetour) dispatch { it.onCues(lines) } }
        override fun onError(error: EngineError) {
            if (isDetour) return
            Log.w(TAG, "ExoPlayer error ${error.codeName}")
            if (PlaybackRouter.shouldFallBack(error.codeName) && currentUri() != null) { keepWithVlc(); startDetour(exo.position, exo.playWhenReady) }
            else dispatch { it.onError(error) }
        }
        override fun onShuffleChanged(enabled: Boolean) = dispatch { it.onShuffleChanged(enabled) }
        override fun onRepeatChanged(mode: RepeatMode) = dispatch { it.onRepeatChanged(mode) }
        override fun onEvents() { if (!isDetour) dispatch { it.onEvents() } }
    }

    private val vlcCallback = object : VlcCallback {
        override fun onPlayingChanged(playing: Boolean) = dispatch { it.onIsPlayingChanged(playing); it.onEvents() }.also { refreshSession() }
        override fun onStateChanged(state: EngineState) = dispatch { it.onStateChanged(state); it.onEvents() }.also { refreshSession() }
        override fun onVideoSize(width: Int, height: Int) { vlcSize = width to height; dispatch { it.onVideoSizeChanged(width, height) } }
        override fun onTracksChanged() { main.post { if (!handOffSlowCodec()) dispatch { it.onTracksChanged() } } }
        override fun onBufferProgress(percent: Int) = dispatch { it.onBufferProgress(percent) }
        override fun onEnded() = onDetourEnded()
        override fun onError() = Log.w(TAG, "libVLC error").let { dispatch { it.onError(EngineError("ERROR_CODE_DECODING_FORMAT_UNSUPPORTED")) } }
    }

    private fun refreshSession() { if (isDetour) session?.refresh() }

    // endregion

    // region video output

    /** Shows video in [view]. The view's size sets the picture size; both backends scale to fill it. */
    fun attachSurface(view: SurfaceView) {
        surface = view
        if (!videoEnabled) return
        if (isDetour) vlc.attachSurface(view) else attachExoSurface()
    }

    fun detachSurface(view: SurfaceView) {
        if (surface !== view) return
        if (isDetour) vlc.detachSurface() else exo.current?.clearVideoSurfaceView(view)
        surface = null
    }

    /** Turns the picture off (audio-only / background) or back on without touching playback. */
    fun setVideoEnabled(enabled: Boolean) {
        videoEnabled = enabled
        val view = surface
        when {
            !enabled && isDetour -> vlc.detachSurface()
            !enabled -> exo.current?.clearVideoSurface()
            view != null -> attachSurface(view)
        }
    }

    fun onSurfaceSizeChanged(width: Int, height: Int) { if (isDetour) vlc.onSurfaceSizeChanged(width, height) }

    private fun attachExoSurface() {
        val view = surface?.takeIf { videoEnabled } ?: return
        exo.exo.setVideoSurfaceView(view)
    }

    // endregion

    // region MediaEngine

    override var decoder: Decoder
        get() = exo.decoder
        set(v) { exo.decoder = v }

    override val isPlaying get() = if (isDetour) vlc.isPlaying else exo.isPlaying
    override val playWhenReady get() = if (isDetour) vlc.playWhenReady else exo.playWhenReady
    override val state get() = if (isDetour) vlc.state else exo.state
    override val position get() = if (isDetour) vlc.position else exo.position
    override val duration get() = if (isDetour) vlc.duration else exo.duration
    override val currentIndex get() = exo.currentIndex
    override val itemCount get() = exo.itemCount
    override val title: String? get() = exo.title
    /** Last size VLC reported while it plays an item ExoPlayer can't. */
    private var vlcSize: Pair<Int, Int>? = null
    override val videoSize: Pair<Int, Int>? get() = if (isDetour) vlcSize else exo.videoSize
    override var volume: Float
        get() = exo.volume
        set(v) { exo.volume = v; vlc.volume = v }
    override var speed: Float
        get() = exo.speed
        set(v) { exo.speed = v; if (isDetour) vlc.rate = v }
    override var shuffleEnabled by exo::shuffleEnabled
    override var repeatMode by exo::repeatMode
    override var pauseAtEndOfItems by exo::pauseAtEndOfItems
    override val effects: AudioEffectsController? get() = if (isDetour) vlcEffects else exo.effects

    override fun hasNext() = exo.hasNext()
    override fun hasPrevious() = exo.hasPrevious()

    override fun setItems(files: List<MediaFile>, startIndex: Int, startPositionMs: Long) {
        endDetour()
        files.forEach { names[it.uri] = it.name; extras[it.uri] = it.extras }
        exo.setItems(files, startIndex, startPositionMs)
    }

    override fun setStream(url: String) { endDetour(); exo.setStream(url) }
    override fun prepare() = prepareCurrent()

    override fun play() {
        if (isDetour) vlc.play() else exo.play()
        refreshSession()
    }

    override fun pause() {
        if (isDetour) vlc.pause() else exo.pause()
        refreshSession()
    }

    override fun stop() { endDetour(); exo.stop() }
    override fun clearItems() { endDetour(); exo.clearItems(); names.clear(); extras.clear() }

    override fun seekTo(positionMs: Long) {
        if (isDetour) vlc.seekTo(positionMs, fast = KMediaPlayer.settings[PlayerKeys.fastSeek]) else exo.seekTo(positionMs)
    }

    override fun seekTo(index: Int, positionMs: Long) {
        if (index == currentIndex && isDetour) return seekTo(positionMs)
        val wasPlaying = playWhenReady
        endDetour()
        exo.seekTo(index, positionMs)
        exo.exo.playWhenReady = wasPlaying
        prepareCurrent(wasPlaying)
    }

    override fun seekToNext() = jump { exo.seekToNext() }
    override fun seekToPrevious() = jump { exo.seekToPrevious() }

    /** Runs a queue move and prepares the new item in its backend, keeping play/pause. */
    private inline fun jump(move: () -> Unit) {
        val wasPlaying = playWhenReady
        val detoured = isDetour
        endDetour()
        move()
        exo.exo.playWhenReady = wasPlaying
        if (detoured || exo.exo.playbackState == Player.STATE_IDLE) prepareCurrent(wasPlaying)
    }

    override fun addListener(listener: EngineListener) { if (listener !in listeners) listeners += listener }
    override fun removeListener(listener: EngineListener) { listeners -= listener }

    override fun tracks(type: TrackType): List<TrackInfo> = if (isDetour) vlc.tracks(type) else exo.tracks(type)
    override fun selectTrack(type: TrackType, id: String) = if (isDetour) vlc.selectTrack(type, id) else exo.selectTrack(type, id)

    override fun setTrackTypeDisabled(type: TrackType, disabled: Boolean) {
        exo.setTrackTypeDisabled(type, disabled)
        if (isDetour) vlc.setTrackTypeDisabled(type, disabled)
    }

    override fun isTrackTypeDisabled(type: TrackType) = exo.isTrackTypeDisabled(type)
    override fun setPreferredLanguages(audio: String?, text: String?) = exo.setPreferredLanguages(audio, text)

    override fun videoFormat(): FormatInfo? = if (isDetour) vlc.videoFormat() else exo.videoFormat()

    override fun capabilityNote(): String? {
        val f = videoFormat() ?: return null
        val mime = PlaybackRouter.mimeOf(f.sampleMimeType ?: f.codecs ?: return null) ?: return null
        if (PlaybackRouter.canPlaySmoothly(mime, f.width, f.height, f.frameRate) != false) return null
        val res = when (maxOf(f.width, f.height)) { in 3000..Int.MAX_VALUE -> "4K"; in 2400..2999 -> "1440p"; in 1800..2399 -> "1080p"; else -> "${minOf(f.width, f.height)}p" }
        val fps = if (f.frameRate > 0) " ${f.frameRate.roundToInt()} fps" else ""
        val codec = when (mime) { "video/av01" -> "AV1"; "video/hevc" -> "HEVC"; "video/avc" -> "H.264"; "video/x-vnd.on2.vp9" -> "VP9"; else -> mime.substringAfter('/') }
        return "$res$fps $codec is too heavy for this device; some frames will be skipped"
    }
    override fun audioFormat(): FormatInfo? = if (isDetour) vlc.audioFormat() else exo.audioFormat()

    override fun applySettings() {
        exo.applySettings()
        if (isDetour) vlcEffects.applySaved()
    }

    override fun release() {
        endDetour()
        if (vlcHolder.isInitialized()) vlc.release()
        session = null
        exo.release()
    }

    // endregion
}

/**
 * False for a file:// path the app may not open. Scoped storage only exposes files MediaStore
 * classifies as media, so legacy types (VOB, WavPack, ...) need "All files access".
 */
private fun String.isReadableFileUri(): Boolean {
    if (!startsWith("file:")) return true
    val path = android.net.Uri.parse(this).path ?: return true
    return File(path).canRead()
}
