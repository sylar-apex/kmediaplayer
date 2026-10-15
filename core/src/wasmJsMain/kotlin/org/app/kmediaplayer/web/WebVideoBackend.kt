package org.app.kmediaplayer.web

import kotlinx.browser.document
import org.app.kmediaplayer.engine.BackendCallback
import org.app.kmediaplayer.engine.Decoder
import org.app.kmediaplayer.engine.EngineState
import org.app.kmediaplayer.engine.FormatInfo
import org.app.kmediaplayer.engine.PlaybackBackend
import org.w3c.dom.HTMLVideoElement
import kotlin.math.roundToLong

/**
 * Browser playback through an HTML `<video>` element: hardware decoding of whatever the browser
 * supports (H.264, HEVC on most browsers, VP9, AV1, AAC, Opus, MP3, FLAC in MP4/WebM/Ogg, MKV in
 * Chromium). The player screen embeds [element] with Compose's HtmlElementView, which sizes and
 * positions it.
 */
class WebVideoBackend : PlaybackBackend {
    override var callback: BackendCallback? = null

    private val video: HTMLVideoElement = (document.createElement("video") as HTMLVideoElement).apply {
        setAttribute("playsinline", "")
        setAttribute("preload", "auto")
        style.apply {
            width = "100%"; height = "100%"
            setProperty("object-fit", "fill")
            backgroundColor = "black"
            // Gestures belong to the Compose layer on top.
            setProperty("pointer-events", "none")
        }
    }

    /** The `<video>` element to embed in the page. */
    val element: HTMLVideoElement get() = video

    private var playWhenReady = false
    private var pendingSeekMs = -1L

    override var state: EngineState = EngineState.Idle
        private set

    override val isPlaying: Boolean get() = !video.paused && !video.ended && video.readyState >= 3
    override val position: Long get() = (video.currentTime * 1000).roundToLong()
    override val duration: Long get() = video.duration.takeIf { it.isFinite() }?.times(1000)?.roundToLong() ?: 0

    override var volume: Float = 1f
        set(v) { field = v; applyVolume() }

    /** The player's "device volume" (volume gesture), 0..1, applied on top of [volume]. */
    var outputGain: Float = 1f
        set(v) { field = v.coerceIn(0f, 1f); applyVolume() }

    private fun applyVolume() { video.volume = (volume * outputGain).coerceIn(0f, 1f).toDouble() }

    override var speed: Float = 1f
        set(v) { field = v; video.playbackRate = v.toDouble() }

    init { listen() }

    /** The library ID of the item loaded now (a stable `webfs://` ID for local files). */
    var currentId: String? = null
        private set

    override fun open(uri: String, startMs: Long, playWhenReady: Boolean, decoder: Decoder) {
        this.playWhenReady = playWhenReady
        pendingSeekMs = startMs
        currentId = uri
        setState(EngineState.Buffering)
        if (WebFiles.isLocal(uri) && !WebFiles.isAvailable(uri)) {
            setState(EngineState.Idle)
            return callback?.onError("ERROR_CODE_IO_FILE_NOT_FOUND") ?: Unit
        }
        video.src = WebFiles.resolve(uri)
        video.playbackRate = speed.toDouble()
        video.load()
    }

    override fun play() {
        playWhenReady = true
        startPlayback()
    }

    override fun pause() {
        playWhenReady = false
        video.pause()
    }

    override fun stop() {
        playWhenReady = false
        currentId = null
        video.pause()
        video.removeAttribute("src")
        video.load()
        setState(EngineState.Idle)
    }

    override fun seekTo(positionMs: Long, fast: Boolean) {
        if (state == EngineState.Ended) setState(EngineState.Ready)
        video.currentTime = positionMs / 1000.0
    }

    override fun videoFormat(): FormatInfo? =
        if (video.videoWidth == 0) null else FormatInfo(width = video.videoWidth, height = video.videoHeight)

    override fun release() {
        stop()
        video.remove()
    }

    private fun startPlayback() = startPlay(video)

    private fun listen() {
        video.addEventListener("loadedmetadata") {
            callback?.onVideoSize(video.videoWidth, video.videoHeight)
            if (pendingSeekMs > 0) video.currentTime = pendingSeekMs / 1000.0
            pendingSeekMs = -1
            callback?.onTracksChanged()
        }
        video.addEventListener("canplay") {
            if (state == EngineState.Buffering) setState(EngineState.Ready)
            if (playWhenReady && video.paused) startPlayback()
        }
        video.addEventListener("waiting") { setState(EngineState.Buffering) }
        video.addEventListener("playing") { setState(EngineState.Ready); callback?.onPlayingChanged(true) }
        video.addEventListener("pause") { callback?.onPlayingChanged(false) }
        video.addEventListener("ended") { setState(EngineState.Ended); callback?.onPlayingChanged(false); callback?.onEnded() }
        video.addEventListener("error") {
            if (video.getAttribute("src") == null) return@addEventListener
            setState(EngineState.Idle)
            callback?.onError(if (video.error?.code?.toInt() == 4) "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED" else "ERROR_CODE_IO_UNSPECIFIED")
        }
    }

    private fun setState(s: EngineState) {
        if (state == s) return
        state = s
        callback?.onStateChanged(s)
    }
}

/** `video.play()` returns a promise that rejects when autoplay is blocked; swallow that, the user can press play. */
private fun startPlay(video: HTMLVideoElement): Unit = js("{ const p = video.play(); if (p) p.catch(() => {}); }")
