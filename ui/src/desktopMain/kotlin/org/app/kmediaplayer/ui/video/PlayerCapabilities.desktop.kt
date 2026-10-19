package org.app.kmediaplayer.ui.video

import org.app.kmediaplayer.desktop.VlcRuntime

/** Desktop plays through libVLC, which brings its own equalizer, track list and decoder choice. */
actual object PlayerCapabilities {
    actual val equalizer get() = VlcRuntime.isAvailable
    actual val audioTrackSelection get() = VlcRuntime.isAvailable
    actual val decoderChoice get() = VlcRuntime.isAvailable
    actual val backgroundPlay = false
    actual val screenRotation = false
    actual val pictureInPicture = false
    actual val fullscreenButton = true
    actual val keyboardShortcuts = true
    actual val fileEditing = true
    actual val storeUpdates = false
}

internal actual val wheelZoomStep: Float = 0.1f
