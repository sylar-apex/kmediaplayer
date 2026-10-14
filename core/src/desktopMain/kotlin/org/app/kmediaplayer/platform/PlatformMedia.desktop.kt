package org.app.kmediaplayer.platform

import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.engine.QueueEngine
import org.app.kmediaplayer.model.MediaFile
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual object PlatformMedia {
    private val engine by lazy { QueueEngine() }
    private var volume = 15

    actual fun engine(): MediaEngine = engine

    actual fun systemVolume(): Int = volume
    actual fun systemMaxVolume(): Int = 15
    actual fun setSystemVolume(level: Int) { volume = level }

    actual fun readBytes(uri: String): ByteArray? = runCatching { File(URI(uri).takeIf { it.scheme == "file" }?.path ?: uri).readBytes() }.getOrNull()
    actual fun decodeText(bytes: ByteArray, charset: String): String = String(bytes, Charset.forName(charset))
    actual fun displayName(uri: String): String? = uri.substringAfterLast('/')
    actual fun lookup(uri: String): MediaFile? = null

    actual fun share(files: List<MediaFile>) {}
    actual fun openUri(uri: String): Boolean = runCatching { Desktop.getDesktop().browse(URI(uri)) }.isSuccess
    actual fun showToast(message: String) = println(message)

    actual fun displayLanguage(code: String): String = Locale(code).displayLanguage
    actual fun formatShortFileSize(bytes: Long): String = org.app.kmediaplayer.util.formatSize(bytes)
    actual fun formatDate(ms: Long, pattern: String): String = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
    actual fun formatDateLocalized(ms: Long, dateStyle: String?, timeStyle: String?): String {
        fun style(s: String?) = if (s == "short") java.text.DateFormat.SHORT else java.text.DateFormat.MEDIUM
        val f = when {
            dateStyle != null && timeStyle != null -> java.text.DateFormat.getDateTimeInstance(style(dateStyle), style(timeStyle))
            timeStyle != null -> java.text.DateFormat.getTimeInstance(style(timeStyle))
            else -> java.text.DateFormat.getDateInstance(style(dateStyle))
        }
        return f.format(Date(ms))
    }
    actual fun batteryPercent(): Int? = null

    actual fun launchVideoPlayer() { org.app.kmediaplayer.KMediaPlayer.videoRequested.value = true }
    actual fun startBackgroundPlayback() {}
    actual fun stopBackgroundPlayback() {}

}
