package org.app.kmediaplayer.ui.video

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.app.kmediaplayer.desktop.VlcThumbnailer
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

actual object SeekPreview {
    actual suspend fun frame(uri: String, positionMs: Long, maxWidth: Int): ImageBitmap? {
        val f = VlcThumbnailer.frame(uri, maxWidth, timeoutMs = 3_000, atMs = positionMs) ?: return null
        return Image.makeRaster(ImageInfo(f.width, f.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), f.pixels, f.width * 4).toComposeImageBitmap()
    }
}
