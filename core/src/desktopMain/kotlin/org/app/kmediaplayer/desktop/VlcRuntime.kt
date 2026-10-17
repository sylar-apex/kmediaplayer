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
        // Trust the shipped plugins.dat: packaging rewrites file times, which would otherwise make libVLC
        // treat the cache as stale and dlopen all ~350 plugins at every start.
        "--no-plugins-scan",
    )

    /** The libVLC factory, or null when VLC is not installed. */
    val factory: MediaPlayerFactory? by lazy {
        runCatching {
            val started = System.currentTimeMillis()
            (System.getProperty("vlc.path") ?: bundledLibDir())?.let { System.setProperty("jna.library.path", it) }
            if (!NativeDiscovery().discover()) {
                System.err.println("libVLC not found or wrong architecture (${System.getProperty("os.arch")}): the bundled libVLC is missing (run :desktopApp:prepareVlc), or pass -Dvlc.path")
                return@runCatching null
            }
            // `-Dvlc.verbose` prints libVLC's own log for diagnosing a file that will not play.
            MediaPlayerFactory(if (System.getProperty("vlc.verbose") != null) args - "--quiet" + "-vv" else args)
                .also { println("libVLC ${it.application().version()} ready in ${System.currentTimeMillis() - started} ms") }
        }.onFailure { System.err.println("libVLC unavailable: $it") }.getOrNull()
    }

    val isAvailable: Boolean get() = factory != null

    /**
     * The libVLC the app ships with: `<resources>/vlc/lib` on macOS (plugins in `../plugins`, where libVLC
     * looks for them) or `<resources>/vlc` on Windows. Null when the app runs without bundled resources.
     */
    private fun bundledLibDir(): String? {
        val vlc = System.getProperty("compose.application.resources.dir")?.let { java.io.File(it, "vlc") } ?: return null
        return listOf(java.io.File(vlc, "lib"), vlc).firstOrNull { dir ->
            dir.listFiles()?.any { it.name.startsWith("libvlc.") } == true
        }?.absolutePath
    }
}
