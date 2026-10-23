package org.app.kmediaplayer.ui.video

import androidx.compose.ui.graphics.ImageBitmap

actual object SeekPreview {
    actual suspend fun frame(uri: String, positionMs: Long, maxWidth: Int): ImageBitmap? = null
}
