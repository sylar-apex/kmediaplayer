package org.app.kmediaplayer.ui.video

actual object SeekPreview {
    actual suspend fun open(uri: String, maxWidth: Int): FrameSource? = null
}
