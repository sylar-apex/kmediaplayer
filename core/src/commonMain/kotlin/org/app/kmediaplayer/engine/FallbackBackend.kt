package org.app.kmediaplayer.engine

import org.app.kmediaplayer.model.StreamExtras

/**
 * Plays each item with [primary] when [primaryHandles] says it can, otherwise with [secondary]; if
 * the primary fails on an item it is reopened in the secondary at the same position. On iOS the
 * primary is AVPlayer (hardware decoding, system integration) and the secondary VLCKit (every format).
 */
class FallbackBackend(
    private val primary: PlaybackBackend,
    private val secondary: () -> PlaybackBackend?,
    private val primaryHandles: (uri: String) -> Boolean,
) : PlaybackBackend {
    override var callback: BackendCallback? = null

    private var secondaryInstance: PlaybackBackend? = null
    /** The backend playing the current item. */
    var active: PlaybackBackend = primary
        private set

    /** Called when [active] changes, so the video surface can show the other backend's view. */
    var onActiveChanged: ((PlaybackBackend) -> Unit)? = null

    private var lastOpen: OpenRequest? = null

    private data class OpenRequest(val uri: String, val startMs: Long, val playWhenReady: Boolean, val decoder: Decoder, val extras: StreamExtras)

    init { primary.callback = relay(primary) }

    private fun secondaryBackend(): PlaybackBackend? =
        secondaryInstance ?: secondary()?.also { it.callback = relay(it); secondaryInstance = it }

    /** Forwards events from [source] only while it is active; turns primary errors into a fallback. */
    private fun relay(source: PlaybackBackend) = object : BackendCallback {
        override fun onPlayingChanged(playing: Boolean) { if (active === source) callback?.onPlayingChanged(playing) }
        override fun onStateChanged(state: EngineState) { if (active === source) callback?.onStateChanged(state) }
        override fun onBufferProgress(percent: Int) { if (active === source) callback?.onBufferProgress(percent) }
        override fun onVideoSize(width: Int, height: Int) { if (active === source) callback?.onVideoSize(width, height) }
        override fun onTracksChanged() { if (active === source) callback?.onTracksChanged() }
        override fun onCues(lines: List<String>) { if (active === source) callback?.onCues(lines) }
        override fun onEnded() { if (active === source) callback?.onEnded() }
        override fun onError(codeName: String) {
            if (active !== source) return
            val request = lastOpen
            val fallback = if (source === primary && request != null) secondaryBackend() else null
            if (fallback == null || request == null) return callback?.onError(codeName) ?: Unit
            val resumeAt = source.position.takeIf { it > 0 } ?: request.startMs
            source.stop()
            switchTo(fallback)
            fallback.open(request.uri, resumeAt, request.playWhenReady, request.decoder, request.extras)
        }
    }

    private fun switchTo(backend: PlaybackBackend) {
        if (active === backend) return
        backend.volume = active.volume
        backend.speed = active.speed
        active = backend
        onActiveChanged?.invoke(backend)
    }

    override fun open(uri: String, startMs: Long, playWhenReady: Boolean, decoder: Decoder, extras: StreamExtras) {
        lastOpen = OpenRequest(uri, startMs, playWhenReady, decoder, extras)
        val usePrimary = decoder != Decoder.SW && primaryHandles(uri)
        val target = if (usePrimary) primary else secondaryBackend() ?: primary
        if (target !== active) active.stop()
        switchTo(target)
        target.open(uri, startMs, playWhenReady, decoder, extras)
    }

    override val isPlaying get() = active.isPlaying
    override val state get() = active.state
    override val position get() = active.position
    override val duration get() = active.duration
    override var volume: Float
        get() = active.volume
        set(v) { active.volume = v }
    override var speed: Float
        get() = active.speed
        set(v) { active.speed = v }
    override val effects get() = active.effects

    override fun play() { lastOpen = lastOpen?.copy(playWhenReady = true); active.play() }
    override fun pause() { lastOpen = lastOpen?.copy(playWhenReady = false); active.pause() }
    override fun stop() = active.stop()
    override fun seekTo(positionMs: Long, fast: Boolean) = active.seekTo(positionMs, fast)

    override fun tracks(type: TrackType) = active.tracks(type)
    override fun selectTrack(type: TrackType, id: String) = active.selectTrack(type, id)
    override fun setTrackTypeDisabled(type: TrackType, disabled: Boolean) = active.setTrackTypeDisabled(type, disabled)
    override fun videoFormat() = active.videoFormat()
    override fun audioFormat() = active.audioFormat()

    override fun release() {
        primary.release()
        secondaryInstance?.release()
    }
}
