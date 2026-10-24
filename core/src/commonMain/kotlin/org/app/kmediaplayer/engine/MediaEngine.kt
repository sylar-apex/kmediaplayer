package org.app.kmediaplayer.engine

import org.app.kmediaplayer.model.MediaFile

enum class Decoder(val label: String, val key: String) {
    HW("HW", "hw"), HWPlus("HW+", "hwplus"), SW("SW", "sw");

    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } ?: HW }
}

enum class EngineState { Idle, Buffering, Ready, Ended }

enum class RepeatMode { Off, One, All }

enum class TrackType { Audio, Text }

data class TrackInfo(val id: String, val label: String, val selected: Boolean)

/** Technical details of the current video or audio stream, shown by the Information panel. */
data class FormatInfo(
    val containerMimeType: String? = null,
    val codecs: String? = null,
    val sampleMimeType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val bitrate: Int = 0,
    val channelCount: Int = 0,
    val sampleRate: Int = 0,
    val language: String? = null,
)

data class EngineError(
    val codeName: String,
    /** Plain-language reason supplied by the app (for example "no longer shared"); shown instead of the code. */
    val explanation: String? = null,
) {
    /** Source/network failures, where switching decoder cannot help. */
    val isIo: Boolean get() = "_IO_" in codeName

    /** The OS blocked reading the file; on Android this needs "All files access". */
    val needsFileAccess: Boolean get() = codeName == NO_FILE_ACCESS

    companion object {
        const val NO_FILE_ACCESS = "ERROR_CODE_IO_NO_PERMISSION"
    }
}

/** Callbacks from a [MediaEngine]; override only what you need. */
interface EngineListener {
    fun onIsPlayingChanged(playing: Boolean) {}
    fun onStateChanged(state: EngineState) {}
    fun onItemTransition() {}
    /** [width] already accounts for the pixel aspect ratio. */
    fun onVideoSizeChanged(width: Int, height: Int) {}
    fun onTracksChanged() {}
    fun onCues(lines: List<String>) {}
    fun onError(error: EngineError) {}
    fun onShuffleChanged(enabled: Boolean) {}
    fun onRepeatChanged(mode: RepeatMode) {}
    /** Fired after any batch of player changes. */
    fun onEvents() {}
}

/** Equalizer, bass boost, reverb and loudness (volume boost) bound to the engine's audio output. */
interface AudioEffectsController {
    val presetCount: Int
    fun presetName(index: Int): String
    fun usePreset(index: Int)
    val bandCount: Int
    val bandLevelRange: IntRange
    fun bandLevel(band: Int): Int
    fun setBandLevel(band: Int, level: Int)
    fun centerFreqHz(band: Int): Int
    val hasReverb: Boolean
    val hasBassBoost: Boolean
    /** Re-applies the equalizer, bass boost and reverb settings from [org.app.kmediaplayer.settings.PlayerKeys]. */
    fun applySaved()
    fun setBoostMillibels(mb: Int)

    companion object {
        val reverbNames = listOf("None", "Small room", "Medium room", "Large room", "Medium hall", "Large hall", "Plate")
    }
}

/** One process-wide player shared by the video screen, the music screens and background playback. */
interface MediaEngine {
    val isPlaying: Boolean
    val playWhenReady: Boolean
    val state: EngineState
    val position: Long
    /** Length of the current item, 0 while unknown. */
    val duration: Long
    val currentIndex: Int
    val itemCount: Int
    val title: String?
    /**
     * The current item's picture size as last reported, or null before it is known. Lets a screen that starts
     * listening after the size event (it fires while loading) still size the picture.
     */
    val videoSize: Pair<Int, Int>? get() = null
    var volume: Float
    var speed: Float
    var shuffleEnabled: Boolean
    var repeatMode: RepeatMode
    /** An item that finishes stays on screen, ended, instead of the queue moving on (play restarts it, next moves on). */
    var pauseAtEndOfItems: Boolean
    /** Decoder preference; takes effect the next time the item is prepared. */
    var decoder: Decoder
    val effects: AudioEffectsController?

    fun hasNext(): Boolean
    fun hasPrevious(): Boolean

    fun setItems(files: List<MediaFile>, startIndex: Int, startPositionMs: Long = 0)
    fun setStream(url: String)
    fun prepare()
    fun play()
    fun pause()
    fun stop()
    fun clearItems()
    fun seekTo(positionMs: Long)
    fun seekTo(index: Int, positionMs: Long)
    fun seekToNext()
    fun seekToPrevious()

    fun addListener(listener: EngineListener)
    fun removeListener(listener: EngineListener)

    fun tracks(type: TrackType): List<TrackInfo>
    fun selectTrack(type: TrackType, id: String)
    fun setTrackTypeDisabled(type: TrackType, disabled: Boolean)
    fun isTrackTypeDisabled(type: TrackType): Boolean
    fun setPreferredLanguages(audio: String?, text: String?)

    fun videoFormat(): FormatInfo?
    fun audioFormat(): FormatInfo?

    /** Re-applies settings the engine only reads at build time (audio focus, noisy handling, seek mode). */
    fun applySettings()
    fun release()
}
