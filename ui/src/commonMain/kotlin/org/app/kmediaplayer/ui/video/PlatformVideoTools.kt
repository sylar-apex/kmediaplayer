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

    /**
     * Non-null when the player can list audio tracks but the platform will not switch them until the user
     * changes a setting (Chromium and Firefox in the browser); the audio panel then shows these steps.
     */
    fun audioTrackHelp(): AudioTrackHelp?

    /** Pinch zoom and pan (pan in px) for surfaces that Compose cannot transform; no-op elsewhere. */
    fun setPictureTransform(scale: Float, panX: Float, panY: Float)
}

/** How to turn on audio-track switching in the user's browser. */
class AudioTrackHelp(
    val browser: String,
    val steps: List<String>,
    /** The settings page, which pages cannot open themselves; [openSettings] copies it for the user to paste. */
    val settingsLink: String,
    /** Copies [settingsLink]; returns a short message for the user. */
    val openSettings: () -> String,
)
