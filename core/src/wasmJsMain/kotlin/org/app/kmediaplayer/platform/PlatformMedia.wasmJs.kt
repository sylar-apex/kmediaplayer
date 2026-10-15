package org.app.kmediaplayer.platform

import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.web.WebPlayback
import org.app.kmediaplayer.model.MediaFile

actual object PlatformMedia {
    private var volume = 15

    actual fun engine(): MediaEngine = WebPlayback.engine

    actual fun systemVolume(): Int = volume
    actual fun systemMaxVolume(): Int = 15
    /** Browsers have no device volume to change; the level drives the video element's output gain. */
    actual fun setSystemVolume(level: Int) {
        volume = level.coerceIn(0, 15)
        org.app.kmediaplayer.web.WebPlayback.backend.outputGain = volume / 15f
    }

    /** Picked subtitle files are read ahead into [org.app.kmediaplayer.web.WebBlobStore]. */
    actual fun readBytes(uri: String): ByteArray? = org.app.kmediaplayer.web.WebBlobStore.get(uri)
    actual fun decodeText(bytes: ByteArray, charset: String): String = bytes.decodeToString()
    actual fun displayName(uri: String): String? = uri.substringAfterLast('/')
    actual fun lookup(uri: String): MediaFile? = null

    /** Online videos open the browser's share sheet (or copy the link); picked local files cannot be shared from a page. */
    actual fun share(files: List<MediaFile>) {
        val file = files.firstOrNull() ?: return
        if (!file.uri.startsWith("http")) return showToast("Files opened from this computer can't be shared from the browser")
        showToast(if (shareUrl(file.title, file.uri)) "Sharing…" else "Link copied")
    }
    actual fun openUri(uri: String): Boolean = openWindow(uri)
    actual fun showToast(message: String) { org.app.kmediaplayer.KMediaPlayer.notices.value = message }

    actual fun displayLanguage(code: String): String = code
    actual fun formatShortFileSize(bytes: Long): String = org.app.kmediaplayer.util.formatSize(bytes)
    actual fun formatDate(ms: Long, pattern: String): String = ms.toString()
    actual fun formatDateLocalized(ms: Long, dateStyle: String?, timeStyle: String?): String = ms.toString()
    actual fun batteryPercent(): Int? = null

    actual fun launchVideoPlayer() { org.app.kmediaplayer.KMediaPlayer.videoRequested.value = true }
    actual fun startBackgroundPlayback() {}
    actual fun stopBackgroundPlayback() {}

}

@JsFun("(url) => window.open(url) != null")
private external fun openWindow(url: String): Boolean

/** Web Share API when present, otherwise copies the link; true when the share sheet opened. */
@JsFun("(t, u) => { if (navigator.share) { navigator.share({ title: t, url: u }).catch(() => {}); return true; } navigator.clipboard && navigator.clipboard.writeText(u); return false; }")
private external fun shareUrl(title: String, url: String): Boolean
