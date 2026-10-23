package org.app.kmediaplayer.ui.video

import androidx.compose.ui.graphics.ImageBitmap

/** Frames for the seek-bar preview: the picture at a time in the playing file, or null where the platform can't grab one. */
expect object SeekPreview {
    suspend fun frame(uri: String, positionMs: Long, maxWidth: Int): ImageBitmap?
}
