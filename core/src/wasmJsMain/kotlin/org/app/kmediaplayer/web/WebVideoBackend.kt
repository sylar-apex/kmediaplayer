package org.app.kmediaplayer.web

import org.app.kmediaplayer.model.StreamExtras

import kotlinx.browser.document
import org.app.kmediaplayer.engine.BackendCallback
import org.app.kmediaplayer.engine.Decoder
import org.app.kmediaplayer.engine.EngineState
import org.app.kmediaplayer.engine.FormatInfo
import org.app.kmediaplayer.engine.PlaybackBackend
import org.app.kmediaplayer.engine.TrackInfo
import org.app.kmediaplayer.engine.TrackType
import org.app.kmediaplayer.logic.ContainerAudioTrack
import org.app.kmediaplayer.logic.ContainerAudioTracks
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.files.Blob
import kotlin.js.Promise
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

    /** Audio streams read from the file header, for browsers without `audioTracks`. */
    private var detectedAudio: List<ContainerAudioTrack> = emptyList()
    private var detectJob: Job? = null
    private val scope = MainScope()

    private fun detectAudio(uri: String) {
        detectJob?.cancel()
        detectedAudio = emptyList()
        if (nativeAudioTracks) return
        val file = WebFiles.file(uri) ?: return
        detectJob = scope.launch {
            val found = runCatching { ContainerAudioTracks.read(file.size.toDouble().toLong()) { off, len -> readRange(file, off, len) } }.getOrDefault(emptyList())
            if (currentId != uri) return@launch
            detectedAudio = found
            callback?.onTracksChanged()
        }
    }

    /** The library ID of the item loaded now (a stable `webfs://` ID for local files). */
    var currentId: String? = null
        private set

    override fun open(uri: String, startMs: Long, playWhenReady: Boolean, decoder: Decoder, extras: StreamExtras) {
        this.playWhenReady = playWhenReady
        pendingSeekMs = startMs
        currentId = uri
        setState(EngineState.Buffering)
        if (WebFiles.isLocal(uri) && !WebFiles.isAvailable(uri)) {
            setState(EngineState.Idle)
            return callback?.onError("ERROR_CODE_IO_FILE_NOT_FOUND") ?: Unit
        }
        detectAudio(uri)
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

    /**
     * Audio tracks come from `video.audioTracks` (Safari; Chromium and Firefox behind a flag). Subtitles are
     * drawn by the player itself, so only audio is handled here.
     */
    override fun tracks(type: TrackType): List<TrackInfo> {
        if (type != TrackType.Audio) return emptyList()
        // Without audioTracks the browser plays the first track; list what the file header says it holds.
        if (!nativeAudioTracks) return detectedAudio.mapIndexed { i, t -> TrackInfo(i.toString(), t.label ?: "Track ${i + 1}", i == 0) }
        return (0 until audioTrackCount(video)).map { i ->
            // Same "Hindi · Name" label as the header-based list; browsers keep language and name apart.
            val label = ContainerAudioTrack(audioTrackLanguage(video, i).ifBlank { null }, audioTrackLabel(video, i)).label ?: "Track ${i + 1}"
            TrackInfo(i.toString(), label, audioTrackEnabled(video, i))
        }
    }

    override fun selectTrack(type: TrackType, id: String) {
        if (type == TrackType.Audio && nativeAudioTracks) id.toIntOrNull()?.let { enableOnlyAudioTrack(video, it) }
    }

    override fun setTrackTypeDisabled(type: TrackType, disabled: Boolean) {
        if (type == TrackType.Audio && disabled) enableOnlyAudioTrack(video, -1)
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
        onAudioTracksChanged(video) { callback?.onTracksChanged() }
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

private fun audioTrackCount(video: HTMLVideoElement): Int = js("video.audioTracks ? video.audioTracks.length : 0")
private fun audioTrackLabel(video: HTMLVideoElement, i: Int): String = js("video.audioTracks[i].label || ''")
private fun audioTrackLanguage(video: HTMLVideoElement, i: Int): String = js("video.audioTracks[i].language || ''")
private fun audioTrackEnabled(video: HTMLVideoElement, i: Int): Boolean = js("video.audioTracks[i].enabled")
/** Enables track [index] and disables the rest; -1 mutes every track. */
private fun enableOnlyAudioTrack(video: HTMLVideoElement, index: Int): Unit =
    js("{ const t = video.audioTracks; if (t) for (let k = 0; k < t.length; k++) t[k].enabled = (k === index); }")
private fun onAudioTracksChanged(video: HTMLVideoElement, block: () -> Unit): Unit =
    js("{ const t = video.audioTracks; if (t) { t.addEventListener('addtrack', () => block()); t.addEventListener('removetrack', () => block()); t.addEventListener('change', () => block()); } }")

/** True when this browser lets pages switch audio tracks (`HTMLMediaElement.audioTracks`). */
val nativeAudioTracks: Boolean by lazy { hasAudioTracks() }

private fun hasAudioTracks(): Boolean = js("'audioTracks' in HTMLMediaElement.prototype")

private suspend fun readRange(blob: Blob, offset: Long, length: Int): ByteArray {
    val view = Int8Array(sliceBuffer(blob, offset.toDouble(), (offset + length).toDouble()).await<ArrayBuffer>())
    return ByteArray(view.length) { view[it] }
}

private fun sliceBuffer(blob: Blob, start: Double, end: Double): Promise<ArrayBuffer> = js("blob.slice(start, end).arrayBuffer()")
