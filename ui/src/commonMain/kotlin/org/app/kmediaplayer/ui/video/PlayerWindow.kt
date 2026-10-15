package org.app.kmediaplayer.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The platform window hosting the video player: screen, orientation, picture-in-picture and pickers. */
interface PlayerWindow {
    val state: VideoPlayerState
    /** True while shown as a picture-in-picture window. Must be snapshot state so the UI recomposes. */
    val inPip: Boolean
    /** Screen density used to turn drag distance into seek time. */
    val pxPerCm: Float

    fun close()
    fun setBrightness(level: Int)
    fun applyScreenSettings()
    fun showSystemBars(show: Boolean)
    fun applyOrientation()
    fun rotate()
    fun enterPip(): Boolean
    fun screenshot()
    fun setVideoDisplay(on: Boolean)
    fun openStorePage(): Boolean
    /** Lets the user pick a file; [onPicked] receives its URI and display name. */
    fun pickFile(mimeTypes: List<String>, onPicked: (uri: String, name: String) -> Unit)
    /** Asks the OS for broad file access, then retries playback. False when the platform has no such permission. */
    fun requestFileAccess(): Boolean = false

    @Composable
    fun VideoSurface(modifier: Modifier)
}
