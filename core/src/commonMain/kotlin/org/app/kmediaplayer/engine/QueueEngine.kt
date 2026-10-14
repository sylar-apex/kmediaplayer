package org.app.kmediaplayer.engine

import org.app.kmediaplayer.model.MediaFile

/**
 * Engine for platforms without a native playback backend yet. It keeps the queue and transport
 * state so every screen works, but renders no audio or video.
 */
class QueueEngine : MediaEngine {
    private val listeners = mutableListOf<EngineListener>()
    private var items: List<String> = emptyList()
    private var titles: List<String?> = emptyList()
    private var index = 0
    private var pos = 0L

    override var isPlaying = false
        private set
    override var playWhenReady = false
        private set
    override var state = EngineState.Idle
        private set
    override val position: Long get() = pos
    override val duration: Long get() = 0
    override val currentIndex: Int get() = index
    override val itemCount: Int get() = items.size
    override val title: String? get() = titles.getOrNull(index)
    override var volume = 1f
    override var speed = 1f
    override var shuffleEnabled = false
        set(v) { field = v; listeners.forEach { it.onShuffleChanged(v) } }
    override var repeatMode = RepeatMode.Off
        set(v) { field = v; listeners.forEach { it.onRepeatChanged(v) } }
    override var pauseAtEndOfItems = false
    override var decoder = Decoder.HW
    override val effects: AudioEffectsController? = null

    override fun hasNext() = index < items.lastIndex
    override fun hasPrevious() = index > 0

    override fun setItems(files: List<MediaFile>, startIndex: Int, startPositionMs: Long) {
        items = files.map { it.uri }; titles = files.map { it.title }
        index = startIndex; pos = startPositionMs
        changed(itemChanged = true)
    }

    override fun setStream(url: String) {
        items = listOf(url); titles = listOf(url.substringAfterLast('/'))
        index = 0; pos = 0
        changed(itemChanged = true)
    }

    override fun prepare() { state = if (items.isEmpty()) EngineState.Idle else EngineState.Ready; listeners.forEach { it.onStateChanged(state) } }
    override fun play() { playWhenReady = true; changed() }
    override fun pause() { playWhenReady = false; changed() }
    override fun stop() { playWhenReady = false; state = EngineState.Idle; changed() }
    override fun clearItems() { items = emptyList(); titles = emptyList(); index = 0; changed(itemChanged = true) }
    override fun seekTo(positionMs: Long) { pos = positionMs.coerceAtLeast(0) }
    override fun seekTo(index: Int, positionMs: Long) { if (index in items.indices) { this.index = index; pos = positionMs; changed(itemChanged = true) } }
    override fun seekToNext() { if (hasNext()) seekTo(index + 1, 0) }
    override fun seekToPrevious() { if (hasPrevious()) seekTo(index - 1, 0) }

    override fun addListener(listener: EngineListener) { if (listener !in listeners) listeners += listener }
    override fun removeListener(listener: EngineListener) { listeners -= listener }

    override fun tracks(type: TrackType): List<TrackInfo> = emptyList()
    override fun selectTrack(type: TrackType, id: String) {}
    override fun setTrackTypeDisabled(type: TrackType, disabled: Boolean) {}
    override fun isTrackTypeDisabled(type: TrackType) = false
    override fun setPreferredLanguages(audio: String?, text: String?) {}
    override fun videoFormat(): FormatInfo? = null
    override fun audioFormat(): FormatInfo? = null
    override fun applySettings() {}
    override fun release() { clearItems(); stop() }

    private fun changed(itemChanged: Boolean = false) {
        isPlaying = playWhenReady && state == EngineState.Ready
        listeners.toList().forEach {
            if (itemChanged) it.onItemTransition()
            it.onIsPlayingChanged(isPlaying)
            it.onEvents()
        }
    }
}
