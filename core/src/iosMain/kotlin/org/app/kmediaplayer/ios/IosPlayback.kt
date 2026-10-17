package org.app.kmediaplayer.ios

import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.engine.QueuedMediaEngine
import platform.UIKit.UIView
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** A backend that renders into its own UIView, so the player screen can show it. */
interface IosVideoBackend {
    val videoView: UIView
}

/** iOS playback: libVLC (MobileVLCKit) for every item, like Android and desktop. */
object IosPlayback {
    val backend: VlcKitBackend by lazy { VlcKitBackend() }

    val engine: MediaEngine by lazy {
        QueuedMediaEngine(backend) { block -> dispatch_async(dispatch_get_main_queue(), block) }
    }

    /** The view showing the current item's video. */
    val currentVideoView: UIView? get() = backend.videoView
}
