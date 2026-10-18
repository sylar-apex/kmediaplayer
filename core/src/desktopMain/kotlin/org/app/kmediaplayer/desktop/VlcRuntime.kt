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
            val libDir = System.getProperty("vlc.path") ?: bundledLibDir()
            libDir?.let { System.setProperty("jna.library.path", it) }
            if (!NativeDiscovery().discover()) {
                System.err.println("libVLC not found or wrong architecture (${System.getProperty("os.arch")}): the bundled libVLC is missing (run :desktopApp:prepareVlc), or pass -Dvlc.path")
                return@runCatching null
            }
            // `-Dvlc.verbose` prints libVLC's own log for diagnosing a file that will not play.
            val stamp = libDir?.let(::pluginCacheStamp)
            val rebuild = stamp != null && stamp.second != runCatching { stamp.first.readText() }.getOrNull()
            val base = if (rebuild) args - "--no-plugins-scan" + "--reset-plugins-cache" else args
            MediaPlayerFactory(if (System.getProperty("vlc.verbose") != null) base - "--quiet" + "-vv" else base)
                .also { if (rebuild) runCatching { stamp!!.first.writeText(stamp.second) } }
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
     * Copying the app (Gradle resources, installers) rewrites file times, so the shipped plugins.dat looks
     * stale and libVLC dlopens every plugin at each start. The stamp records the plugins' times as of the last
     * cache rebuild; when they differ, libVLC rebuilds plugins.dat once and later starts read it directly.
     * Null when the plugins folder is missing or read-only.
     */
    private fun pluginCacheStamp(libDir: String): Pair<java.io.File, String>? {
        val plugins = listOf(java.io.File(libDir, "../plugins"), java.io.File(libDir, "plugins"))
            .map { it.canonicalFile }.firstOrNull { it.isDirectory && it.canWrite() } ?: return null
        val times = plugins.listFiles { f -> f.name != "plugins.dat" && f.name != STAMP }.orEmpty()
            .sortedBy { it.name }.joinToString("\n") { "${it.name} ${it.lastModified()}" }
        return java.io.File(plugins, STAMP) to times.hashCode().toString()
    }

    private const val STAMP = "plugins.stamp"

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
