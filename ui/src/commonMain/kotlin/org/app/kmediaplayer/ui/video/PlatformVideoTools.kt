package org.app.kmediaplayer.ui.video

/**
 * Player features that depend on the host platform, for windows that embed the player in Compose
 * (desktop, web, iOS). Android's player activity implements these itself.
 */
expect object PlatformVideoTools {
    /** Picture brightness multiplier, 1 = unchanged (brightness gesture and night mode). */
    fun setPictureBrightness(factor: Float)

    /** Saves the current frame; [onDone] gets a message for the user. */
    fun captureFrame(onDone: (message: String) -> Unit)

    /** False when the platform has no picture-in-picture. */
    fun enterPip(): Boolean

    /** True while the window or page is full screen; snapshot state, so buttons update. */
    val isFullscreen: Boolean

    /** Toggles full screen; false when unsupported. */
    fun toggleFullscreen(): Boolean

    /** Lets the user pick a file; false when unsupported. [onPicked] gets a readable URI and the file name. */
    fun pickFile(extensions: List<String>, onPicked: (uri: String, name: String) -> Unit): Boolean
}
