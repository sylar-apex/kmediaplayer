package org.app.kmediaplayer.ios

import org.app.kmediaplayer.engine.FallbackBackend
import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.engine.PlaybackBackend
import org.app.kmediaplayer.engine.QueuedMediaEngine
import org.app.kmediaplayer.model.fileExtension
import platform.UIKit.UIView
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** A backend that renders into its own UIView, so the player screen can show it. */
interface IosVideoBackend {
    val videoView: UIView
}

/**
 * iOS playback: AVPlayer for Apple-native formats, and a second backend for everything else. The
 * app registers that second backend from Swift (VLCKit, see iosApp/iosApp/VlcKitBackend.swift):
 *
 * ```swift
 * IosPlayback.shared.fallbackFactory = { VlcKitBackend() }
 * ```
 */
object IosPlayback {
    /** Creates the backend for formats AVPlayer cannot open; it must also implement [IosVideoBackend]. */
    var fallbackFactory: (() -> PlaybackBackend)? = null

    private val nativeExtensions = setOf("mp4", "m4v", "mov", "m4a", "mp3", "aac", "wav", "aif", "aiff", "caf", "ac3", "eac3", "m3u8", "3gp", "3g2")

    private val avPlayer = AvPlayerBackend()

    val backend: FallbackBackend = FallbackBackend(
        primary = avPlayer,
        secondary = { fallbackFactory?.invoke() },
        primaryHandles = { uri -> uri.startsWith("http") && "m3u8" in uri || uri.fileExtension() in nativeExtensions },
    )

    val engine: MediaEngine by lazy {
        QueuedMediaEngine(backend) { block -> dispatch_async(dispatch_get_main_queue(), block) }
    }

    /** The view showing the current item's video. */
    val currentVideoView: UIView? get() = (backend.active as? IosVideoBackend)?.videoView
}
