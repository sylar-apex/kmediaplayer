package org.app.kmediaplayer.ui.video

/** Unused: Android's player activity provides these features. */

actual object PlatformVideoTools {
    actual fun audioTrackHelp(): AudioTrackHelp? = null
    actual fun setPictureTransform(scale: Float, panX: Float, panY: Float) {}

    actual fun setPictureBrightness(factor: Float) {}
    actual fun captureFrame(onDone: (message: String) -> Unit) = onDone("Screenshot is not available here")
    actual fun enterPip(): Boolean = false
    actual val isFullscreen: Boolean = false
    actual fun toggleFullscreen(): Boolean = false
    actual fun pickFile(extensions: List<String>, onPicked: (uri: String, name: String) -> Unit): Boolean = false
}
