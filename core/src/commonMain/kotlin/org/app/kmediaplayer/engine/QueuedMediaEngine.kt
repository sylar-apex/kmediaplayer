package org.app.kmediaplayer.engine

import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.logic.shuffledOrder
import org.app.kmediaplayer.model.MediaFile
import org.app.kmediaplayer.settings.PlayerKeys

/**
 * A [MediaEngine] built from a single-item [PlaybackBackend]: it owns the queue, shuffle, repeat and
 * listeners, and opens one item at a time in the backend.
 *
 * @param post runs a block on the UI thread. Backend events arrive on decoder threads, and listeners
 *   update UI state, so every listener call goes through [post].
 */
class QueuedMediaEngine(
    private val backend: PlaybackBackend,
    private val post: (() -> Unit) -> Unit,
) : MediaEngine {
    private val listeners = mutableListOf<EngineListener>()
    private var items: List<MediaFile> = emptyList()
    /** Play order as indices into [items]; a permutation while shuffle is on. */
    private var order: List<Int> = emptyList()
    private var cursor = 0
    private var startPositionMs = 0L
    private var opened = false
    private var ended = false

    override var playWhenReady = false
        private set
    override val isPlaying: Boolean get() = backend.isPlaying
    override val state: EngineState get() = if (ended) EngineState.Ended else if (opened) backend.state else EngineState.Idle
    override val position: Long get() = if (opened) backend.position else startPositionMs
    override val duration: Long get() = if (opened) backend.duration else 0
    override val currentIndex: Int get() = order.getOrElse(cursor) { 0 }
    override val itemCount: Int get() = items.size
    override val title: String? get() = items.getOrNull(currentIndex)?.title
    override var volume: Float
        get() = backend.volume
        set(v) { backend.volume = v }
    override var speed: Float
        get() = backend.speed
        set(v) { backend.speed = v }
    override var shuffleEnabled = false
        set(v) {
            if (field == v) return
            field = v
            val current = currentIndex
            order = if (v) shuffledOrder(items.size, current) else items.indices.toList()
            cursor = order.indexOf(current).coerceAtLeast(0)
            emit { it.onShuffleChanged(v) }
        }
    override var repeatMode = RepeatMode.Off
        set(v) { field = v; emit { it.onRepeatChanged(v) } }
    override var pauseAtEndOfItems = false
    override var decoder = Decoder.HW
    override val effects: AudioEffectsController? get() = backend.effects

    init {
        backend.callback = object : BackendCallback {
            override fun onPlayingChanged(playing: Boolean) = emit { it.onIsPlayingChanged(playing); it.onEvents() }
            override fun onStateChanged(state: EngineState) = emit { it.onStateChanged(state); it.onEvents() }
            override fun onVideoSize(width: Int, height: Int) = emit { it.onVideoSizeChanged(width, height) }
            override fun onTracksChanged() = emit { it.onTracksChanged(); it.onEvents() }
            override fun onCues(lines: List<String>) = emit { it.onCues(lines) }
            override fun onEnded() = post { advanceAfterEnd() }
            override fun onError(codeName: String) = emit { it.onError(EngineError(codeName)) }
        }
    }

    // region queue

    override fun hasNext() = cursor < order.lastIndex || (repeatMode == RepeatMode.All && order.isNotEmpty())
    override fun hasPrevious() = cursor > 0 || (repeatMode == RepeatMode.All && order.isNotEmpty())

    override fun setItems(files: List<MediaFile>, startIndex: Int, startPositionMs: Long) {
        closeCurrent()
        items = files
        order = if (shuffleEnabled) shuffledOrder(files.size, startIndex) else files.indices.toList()
        cursor = order.indexOf(startIndex).coerceAtLeast(0)
        this.startPositionMs = startPositionMs
        emit { it.onItemTransition(); it.onEvents() }
    }

    override fun setStream(url: String) {
        val name = url.substringBefore('?').substringAfterLast('/').ifEmpty { url }
        setItems(listOf(MediaFile(url.hashCode().toLong(), url, url, name, 0, 0, 0)), 0, 0)
    }

    override fun prepare() {
        val file = items.getOrNull(currentIndex) ?: return
        ended = false
        opened = true
        backend.open(file.uri, startPositionMs, playWhenReady, decoder)
        startPositionMs = 0
    }

    override fun play() {
        playWhenReady = true
        if (ended) { ended = false; backend.seekTo(0, fast = false) }
        if (opened) backend.play()
        emit { it.onEvents() }
    }

    override fun pause() {
        playWhenReady = false
        if (opened) backend.pause()
        emit { it.onEvents() }
    }

    override fun stop() {
        playWhenReady = false
        closeCurrent()
        emit { it.onStateChanged(EngineState.Idle); it.onEvents() }
    }

    override fun clearItems() {
        closeCurrent()
        items = emptyList(); order = emptyList(); cursor = 0
        emit { it.onItemTransition(); it.onEvents() }
    }

    override fun seekTo(positionMs: Long) {
        ended = false
        if (opened) backend.seekTo(positionMs.coerceAtLeast(0), KMediaPlayer.settings[PlayerKeys.fastSeek]) else startPositionMs = positionMs
    }

    override fun seekTo(index: Int, positionMs: Long) {
        val target = order.indexOf(index)
        if (target < 0) return
        moveTo(target, positionMs)
    }

    override fun seekToNext() { if (hasNext()) moveTo((cursor + 1) % order.size, 0) }
    override fun seekToPrevious() { if (hasPrevious()) moveTo((cursor - 1 + order.size) % order.size, 0) }

    /** Switches to the item at [target] in play order; keeps preparing if an item was open. */
    private fun moveTo(target: Int, positionMs: Long) {
        val wasOpen = opened
        closeCurrent()
        cursor = target
        startPositionMs = positionMs
        emit { it.onItemTransition(); it.onEvents() }
        if (wasOpen) prepare()
    }

    private fun advanceAfterEnd() {
        when {
            repeatMode == RepeatMode.One -> { backend.seekTo(0, fast = false); backend.play() }
            cursor < order.lastIndex || repeatMode == RepeatMode.All -> {
                if (pauseAtEndOfItems) playWhenReady = false
                moveTo((cursor + 1) % order.size, 0)
            }
            else -> { ended = true; emit { it.onStateChanged(EngineState.Ended); it.onIsPlayingChanged(false); it.onEvents() } }
        }
    }

    private fun closeCurrent() {
        if (opened) backend.stop()
        opened = false
        ended = false
    }

    // endregion

    override fun addListener(listener: EngineListener) { if (listener !in listeners) listeners += listener }
    override fun removeListener(listener: EngineListener) { listeners -= listener }

    private fun emit(event: (EngineListener) -> Unit) = post { listeners.toList().forEach(event) }

    override fun tracks(type: TrackType): List<TrackInfo> = if (opened) backend.tracks(type) else emptyList()
    override fun selectTrack(type: TrackType, id: String) = backend.selectTrack(type, id)

    private val disabledTypes = mutableSetOf<TrackType>()
    override fun setTrackTypeDisabled(type: TrackType, disabled: Boolean) {
        if (disabled) disabledTypes += type else disabledTypes -= type
        backend.setTrackTypeDisabled(type, disabled)
    }
    override fun isTrackTypeDisabled(type: TrackType) = type in disabledTypes
    override fun setPreferredLanguages(audio: String?, text: String?) {}

    override fun videoFormat(): FormatInfo? = if (opened) backend.videoFormat() else null
    override fun audioFormat(): FormatInfo? = if (opened) backend.audioFormat() else null
    override fun applySettings() { backend.effects?.applySaved() }

    override fun release() {
        clearItems()
        backend.release()
    }
}
