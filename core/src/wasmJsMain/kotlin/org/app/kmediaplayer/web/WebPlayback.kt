package org.app.kmediaplayer.web

import kotlinx.browser.window
import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.engine.QueuedMediaEngine

/** The browser engine: a queue over one HTML video element. */
object WebPlayback {
    val backend: WebVideoBackend by lazy { WebVideoBackend() }

    val engine: MediaEngine by lazy {
        QueuedMediaEngine(backend) { block -> window.setTimeout({ block(); null }, 0) }
    }
}
