package org.app.kmediaplayer.engine

import org.app.kmediaplayer.model.StreamExtras

/** Events from a [PlaybackBackend]. Backends may call these from any thread; see [QueuedMediaEngine]. */
interface BackendCallback {
    fun onPlayingChanged(playing: Boolean)
    fun onStateChanged(state: EngineState)
    fun onVideoSize(width: Int, height: Int)
    fun onTracksChanged()
    fun onCues(lines: List<String>) {}
    fun onEnded()
    fun onError(codeName: String)
}

/**
 * Plays a single item. Platforms implement this (libVLC on desktop, AVPlayer on iOS, an HTML video
 * element on the web) and [QueuedMediaEngine] turns it into a full [MediaEngine] with a play queue.
 */
interface PlaybackBackend {
    var callback: BackendCallback?

    val isPlaying: Boolean
    val state: EngineState
    val position: Long
    /** 0 while unknown. */
    val duration: Long
    var volume: Float
    var speed: Float
    val effects: AudioEffectsController? get() = null

    /**
     * Opens [uri]; playback starts at [startMs] and only if [playWhenReady]. [decoder] SW forces software
     * decoding where supported; [extras] carries a separate audio stream and the User-Agent for network items.
     */
    fun open(uri: String, startMs: Long, playWhenReady: Boolean, decoder: Decoder, extras: StreamExtras)

    /** Shorthand for an item without [StreamExtras]. */
    fun open(uri: String, startMs: Long, playWhenReady: Boolean, decoder: Decoder) = open(uri, startMs, playWhenReady, decoder, StreamExtras.None)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long, fast: Boolean)

    fun tracks(type: TrackType): List<TrackInfo> = emptyList()
    fun selectTrack(type: TrackType, id: String) {}
    fun setTrackTypeDisabled(type: TrackType, disabled: Boolean) {}
    fun videoFormat(): FormatInfo? = null
    fun audioFormat(): FormatInfo? = null

    fun release()
}
