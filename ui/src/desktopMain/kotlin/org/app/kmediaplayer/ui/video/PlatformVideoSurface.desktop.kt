package org.app.kmediaplayer.ui.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.app.kmediaplayer.desktop.DesktopPlayback
import org.app.kmediaplayer.desktop.VideoFrameBuffer
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/** Draws libVLC's frames with Skia. Only the draw phase reads the frame counter, so frames never recompose. */
@Composable
actual fun PlatformVideoSurface(modifier: Modifier) {
    val frames = DesktopPlayback.backend?.frames ?: return Box(modifier)
    val version = frames.version.collectAsState()
    val bitmap = remember { Bitmap() }
    DisposableEffect(bitmap) { onDispose { bitmap.close() } }
    Canvas(modifier) {
        version.value
        drawFrame(frames, bitmap)
    }
}

private fun DrawScope.drawFrame(frames: VideoFrameBuffer, bitmap: Bitmap) {
    val ready = frames.withFrame { pixels, w, h ->
        if (bitmap.width != w || bitmap.height != h) {
            bitmap.allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE))
        }
        bitmap.installPixels(pixels)
    } ?: false
    if (!ready) return
    val b = PlatformVideoTools.brightness
    drawImage(
        bitmap.asComposeImageBitmap(),
        srcOffset = IntOffset.Zero, srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.Low,
        colorFilter = if (b == 1f) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(b, b, b, 1f) }),
    )
}

actual val videoNeedsCutouts: Boolean = false
