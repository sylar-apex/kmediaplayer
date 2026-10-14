package org.app.kmediaplayer.platform

import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.engine.QueueEngine
import org.app.kmediaplayer.model.MediaFile

actual object PlatformMedia {
    private val engine by lazy { QueueEngine() }
    private var volume = 15

    actual fun engine(): MediaEngine = engine

    actual fun systemVolume(): Int = volume
    actual fun systemMaxVolume(): Int = 15
    actual fun setSystemVolume(level: Int) { volume = level }

    actual fun readBytes(uri: String): ByteArray? = null
    actual fun decodeText(bytes: ByteArray, charset: String): String = bytes.decodeToString()
    actual fun displayName(uri: String): String? = uri.substringAfterLast('/')
    actual fun lookup(uri: String): MediaFile? = null

    actual fun share(files: List<MediaFile>) {}
    actual fun openUri(uri: String): Boolean = openWindow(uri)
    actual fun showToast(message: String) = println(message)

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
