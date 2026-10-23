package org.app.kmediaplayer.ui.video

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.android.KMediaPlayerAndroid
import org.app.kmediaplayer.settings.PlayerKeys

actual object SeekPreview {
    actual suspend fun open(uri: String, maxWidth: Int): FrameSource? = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            when {
                uri.startsWith("http") -> r.setDataSource(uri, emptyMap())
                uri.startsWith("content:") || uri.startsWith("file:") -> r.setDataSource(KMediaPlayerAndroid.context, Uri.parse(uri))
                else -> r.setDataSource(uri)
            }
        } catch (_: Exception) {
            runCatching { r.release() }
            return@withContext null
        }
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val tw = if (w > 0) minOf(maxWidth, w) else maxWidth
        val th = if (w > 0 && h > 0) (h.toLong() * tw / w).toInt() else tw * 9 / 16
        // One retriever, one request at a time: it is not thread-safe.
        val lock = Mutex()
        object : FrameSource {
            override suspend fun frameAt(ms: Long, exact: Boolean): ImageBitmap? = lock.withLock {
                // Same rule as the player: with fast seeking it lands on the nearest keyframe, otherwise on the exact time.
                val option = if (exact && !KMediaPlayer.settings[PlayerKeys.fastSeek]) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                withContext(Dispatchers.IO) {
                    runCatching { r.getScaledFrameAtTime(ms * 1000, option, tw, th)?.asImageBitmap() }.getOrNull()
                }
            }
            override fun close() { runCatching { r.release() } }
        }
    }
}
