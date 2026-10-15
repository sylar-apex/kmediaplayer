package org.app.kmediaplayer.desktop

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery

/**
 * Finds the libVLC native libraries: an installed VLC (macOS /Applications/VLC.app, Windows
 * Program Files, Linux system packages), or a folder named by the `vlc.path` system property or
 * `VLC_PLUGIN_PATH`, so an installer can ship VLC next to the app.
 */
object VlcRuntime {
    private val args = listOf(
        "--no-video-title-show",
        "--no-snapshot-preview",
        "--quiet",
        "--avcodec-threads=0",
        "--drop-late-frames",
        "--skip-frames",
        "--audio-time-stretch",
        "--file-caching=1000",
        "--network-caching=2000",
        "--subsdec-encoding=UTF-8",
    )

    /** The libVLC factory, or null when VLC is not installed. */
    val factory: MediaPlayerFactory? by lazy {
        runCatching {
            System.getProperty("vlc.path")?.let { System.setProperty("jna.library.path", it) }
            if (!NativeDiscovery().discover()) return@runCatching null
            // `-Dvlc.verbose` prints libVLC's own log for diagnosing a file that will not play.
            MediaPlayerFactory(if (System.getProperty("vlc.verbose") != null) args - "--quiet" + "-vv" else args)
        }.onFailure { System.err.println("libVLC unavailable: $it") }.getOrNull()
    }

    val isAvailable: Boolean get() = factory != null
}
