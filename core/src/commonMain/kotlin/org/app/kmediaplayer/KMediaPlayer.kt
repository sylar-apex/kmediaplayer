package org.app.kmediaplayer

import kotlinx.coroutines.flow.MutableStateFlow
import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.host.PlayerHost
import org.app.kmediaplayer.logic.shuffledOrder
import org.app.kmediaplayer.model.MediaFile
import org.app.kmediaplayer.platform.PlatformMedia
import org.app.kmediaplayer.settings.MemoryStore
import org.app.kmediaplayer.settings.ObservableSettings
import org.app.kmediaplayer.settings.PlayerKeys

/** What the video screen should open next, handed over without serialising the queue into an intent. */
/** The file the full player showed when it closed, its position and whether it was playing. */
data class ClosedPlayback(val uri: String, val positionMs: Long, val playing: Boolean)

sealed interface PendingPlayback {
    data class Files(val files: List<MediaFile>, val index: Int) : PendingPlayback
    data class Stream(val url: String) : PendingPlayback
}

/**
 * Entry point of the library. Plug and play: `KMediaPlayer.playVideo(files, 0)` opens the full player,
 * `KMediaPlayer.playMusic(tracks, 0)` starts background audio. Call [configure] once to share the
 * app's settings store and hook the player into the app's data through a [PlayerHost].
 */
object KMediaPlayer {
    var settings: ObservableSettings = ObservableSettings(MemoryStore(), listOf(PlayerKeys))
        private set
    var host: PlayerHost = PlayerHost.None
        private set

    /** The files currently queued, in player order. Empty for network streams. */
    val queue = MutableStateFlow<List<MediaFile>>(emptyList())
    /** True when the current queue was started from a music screen. */
    val isMusicSession = MutableStateFlow(false)

    var pending: PendingPlayback? = null

    /**
     * On platforms where the player is shown inside the host's own window (desktop, iOS, web), true
     * while the host should show the video player for [pending]. Android opens an activity instead.
     */
    val videoRequested = MutableStateFlow(false)

    /**
     * Short messages from platform code (desktop, web, iOS) for the player to show as a toast;
     * Android shows its own toasts.
     */
    val notices = MutableStateFlow<String?>(null)

    val engine: MediaEngine get() = PlatformMedia.engine()

    /**
     * False while the platform is still loading its playback libraries (libVLC on desktop). Touching [engine]
     * before then blocks, so hosts show a loading player until it turns true.
     */
    val engineReady = MutableStateFlow(true)

    /** True while the full player is shown after [expandVideo]; an inline player takes over again when it turns false. */
    val fullPlayerOpen = MutableStateFlow(false)

    /** Where the full player was when it closed, so an inline player can continue from there. */
    var lastClosed: ClosedPlayback? = null

    fun configure(settings: ObservableSettings, host: PlayerHost) {
        this.settings = settings
        this.host = host
    }

    fun playVideo(files: List<MediaFile>, index: Int) {
        if (files.isEmpty()) return
        pending = PendingPlayback.Files(files, index.coerceIn(files.indices))
        PlatformMedia.launchVideoPlayer()
    }

    /**
     * Opens the full player on whatever is playing now, without restarting it. An inline player uses
     * this for its full-screen button.
     */
    fun expandVideo() {
        pending = null
        fullPlayerOpen.value = true
        PlatformMedia.launchVideoPlayer()
    }

    /** Called by player windows as they close. */
    fun onPlayerClosed(uri: String?, positionMs: Long, playing: Boolean) {
        lastClosed = uri?.let { ClosedPlayback(it, positionMs, playing) }
        fullPlayerOpen.value = false
    }

    fun playUrl(url: String) {
        pending = PendingPlayback.Stream(url)
        PlatformMedia.launchVideoPlayer()
    }

    fun playMusic(files: List<MediaFile>, index: Int, shuffle: Boolean = false) {
        if (files.isEmpty()) return
        val order = if (shuffle) shuffledOrder(files.size, index).map { files[it] } else files
        val start = if (shuffle) 0 else index
        val engine = engine
        queue.value = order
        isMusicSession.value = true
        engine.setItems(order, start, 0)
        engine.shuffleEnabled = false
        engine.prepare()
        engine.play()
        PlatformMedia.startBackgroundPlayback()
    }

    /** Stops playback everywhere and frees the engine. */
    fun quit() {
        PlatformMedia.stopBackgroundPlayback()
        engine.release()
        queue.value = emptyList()
    }
}
