package org.app.kmediaplayer.ui.video

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.app.kmediaplayer.android.KMediaPlayerAndroid

actual object SeekPreview {
    actual suspend fun frame(uri: String, positionMs: Long, maxWidth: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            when {
                uri.startsWith("http") -> r.setDataSource(uri, emptyMap())
                uri.startsWith("content:") || uri.startsWith("file:") -> r.setDataSource(KMediaPlayerAndroid.context, Uri.parse(uri))
                else -> r.setDataSource(uri)
            }
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val tw = if (w > 0) minOf(maxWidth, w) else maxWidth
            val th = if (w > 0 && h > 0) (h.toLong() * tw / w).toInt() else tw * 9 / 16
            r.getScaledFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, tw, th)?.asImageBitmap()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
}
