package org.app.kmediaplayer.ui.video

/** The player on screen, for zoom input that arrives outside Compose (the macOS trackpad pinch). */
internal object ZoomInput {
    var target: VideoPlayerState? = null
}

/**
 * Zoom per unit of [androidx.compose.ui.input.pointer.PointerInputChange.scrollDelta]. Desktop and mobile report
 * wheel notches (1 per notch, 10 % each); browsers report pixels (about 100 per notch, a few per trackpad pinch
 * step), so the web uses a hundredth of that.
 */
internal expect val wheelZoomStep: Float
