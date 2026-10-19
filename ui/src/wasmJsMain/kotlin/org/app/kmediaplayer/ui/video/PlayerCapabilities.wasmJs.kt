package org.app.kmediaplayer.ui.video

/** Browsers expose no audio effects or decoder choice to web pages. */
actual object PlayerCapabilities {
    actual val equalizer = false
    /** Tracks are listed everywhere: natively where supported, otherwise from the file header (see [PlatformVideoTools.audioTrackHelp]). */
    actual val audioTrackSelection = true
    actual val decoderChoice = false
    actual val backgroundPlay = false
    actual val screenRotation = false
    actual val pictureInPicture: Boolean by lazy { pictureInPictureEnabled() }
    actual val fullscreenButton = true
    actual val keyboardShortcuts = true
    actual val fileEditing = false
    actual val storeUpdates = false
}

@JsFun("() => !!document.pictureInPictureEnabled")
private external fun pictureInPictureEnabled(): Boolean

internal actual val wheelZoomStep: Float = 0.001f
