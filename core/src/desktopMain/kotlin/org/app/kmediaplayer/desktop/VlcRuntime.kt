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

    private val loader = lazy {
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

    /** The libVLC factory, or null when VLC is not installed. Blocks while libVLC loads: never call it on the UI thread. */
    val factory: MediaPlayerFactory? get() = loader.value

    /**
     * Never blocks: true while libVLC is still loading (it ships with the app), then whether it loaded.
     * Callers that go on to use [factory] must do so off the UI thread.
     */
    val isAvailable: Boolean get() = !loader.isInitialized() || loader.value != null

    /**
     * The libVLC the app ships with: `<resources>/vlc/lib` on macOS (plugins in `../plugins`, where libVLC
     * looks for them) or `<resources>/vlc` on Windows. Null when the app runs without bundled resources.
     */
    /**
     * Runs started outside Gradle's `run` task (e.g. an IDE run configuration) have no resources dir; use the
     * copy `:desktopApp:prepareVlc` puts under `desktopApp/build/vlc-resources/<os>/vlc` in the project.
     */
    private fun devResources(): java.io.File? {
        val os = System.getProperty("os.name").lowercase().let { if ("mac" in it) "macos" else if ("win" in it) "windows" else "linux" }
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            java.io.File(dir, "desktopApp/build/vlc-resources/$os/vlc").takeIf { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        return null
    }

    private fun bundledLibDir(): String? {
        val vlc = System.getProperty("compose.application.resources.dir")?.let { java.io.File(it, "vlc") } ?: devResources() ?: return null
        return listOf(java.io.File(vlc, "lib"), vlc).firstOrNull { dir ->
            dir.listFiles()?.any { it.name.startsWith("libvlc.") } == true
        }?.absolutePath
    }
}
