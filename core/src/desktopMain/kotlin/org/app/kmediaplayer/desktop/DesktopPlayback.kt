package org.app.kmediaplayer.desktop

import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.engine.QueueEngine
import org.app.kmediaplayer.engine.QueuedMediaEngine
import javax.swing.SwingUtilities

/** The desktop engine: libVLC when VLC is installed, otherwise a silent queue so the app still runs. */
object DesktopPlayback {
    /** The libVLC backend, or null without VLC. The video surface reads frames from it. */
    val backend: VlcjBackend? by lazy { VlcRuntime.factory?.let(::VlcjBackend) }

    val engine: MediaEngine by lazy {
        backend?.let { QueuedMediaEngine(it) { block -> SwingUtilities.invokeLater(block) } } ?: QueueEngine()
    }
}
