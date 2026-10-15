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

    fun configure(settings: ObservableSettings, host: PlayerHost) {
        this.settings = settings
        this.host = host
    }

    fun playVideo(files: List<MediaFile>, index: Int) {
        if (files.isEmpty()) return
        pending = PendingPlayback.Files(files, index.coerceIn(files.indices))
        PlatformMedia.launchVideoPlayer()
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
