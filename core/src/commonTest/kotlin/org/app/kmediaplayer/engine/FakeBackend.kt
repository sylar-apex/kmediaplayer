package org.app.kmediaplayer.engine

import org.app.kmediaplayer.model.StreamExtras

/** Records what the engine asks of it; tests drive its events by hand. */
class FakeBackend(val name: String = "fake") : PlaybackBackend {
    override var callback: BackendCallback? = null
    val opened = mutableListOf<String>()
    var openedAt = -1L
    var lastDecoder: Decoder? = null
    var playing = false
    var stops = 0

    override val isPlaying get() = playing
    override var state = EngineState.Idle
    override var position = 0L
    override var duration = 10_000L
    override var volume = 1f
    override var speed = 1f

    override fun open(uri: String, startMs: Long, playWhenReady: Boolean, decoder: Decoder, extras: StreamExtras) {
        opened += uri; openedAt = startMs; lastDecoder = decoder; playing = playWhenReady; state = EngineState.Ready
    }
    override fun play() { playing = true }
    override fun pause() { playing = false }
    override fun stop() { stops++; playing = false; state = EngineState.Idle }
    override fun seekTo(positionMs: Long, fast: Boolean) { position = positionMs }
    override fun release() {}

    fun end() = callback?.onEnded()
    fun fail(code: String = "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED") = callback?.onError(code)
}
