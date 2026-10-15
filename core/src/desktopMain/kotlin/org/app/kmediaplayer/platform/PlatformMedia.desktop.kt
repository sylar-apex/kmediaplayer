package org.app.kmediaplayer.platform

import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.desktop.DesktopPlayback
import org.app.kmediaplayer.model.MediaFile
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual object PlatformMedia {
    private var volume = 15

    actual fun engine(): MediaEngine = DesktopPlayback.engine

    actual fun systemVolume(): Int = volume
    actual fun systemMaxVolume(): Int = 15
    /** The app's own output level (volume gesture); it does not change the computer's master volume. */
    actual fun setSystemVolume(level: Int) {
        volume = level.coerceIn(0, 15)
        DesktopPlayback.backend?.outputGain = volume / 15f
    }

    actual fun readBytes(uri: String): ByteArray? = runCatching { File(URI(uri).takeIf { it.scheme == "file" }?.path ?: uri).readBytes() }.getOrNull()
    actual fun decodeText(bytes: ByteArray, charset: String): String = String(bytes, Charset.forName(charset))
    actual fun displayName(uri: String): String? = uri.substringAfterLast('/')
    actual fun lookup(uri: String): MediaFile? = null

    /** Desktop "share" reveals the file in Finder / Explorer / the file manager. */
    actual fun share(files: List<MediaFile>) {
        val file = files.firstOrNull()?.let { File(it.path) }?.takeIf { it.exists() } ?: return showToast("Only local files can be shown in the file manager")
        val desktop = Desktop.getDesktop()
        val shown = runCatching {
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(file) else desktop.open(file.parentFile)
        }.isSuccess
        if (!shown) showToast("Couldn't open the file manager")
    }
    actual fun openUri(uri: String): Boolean = runCatching { Desktop.getDesktop().browse(URI(uri)) }.isSuccess
    actual fun showToast(message: String) { org.app.kmediaplayer.KMediaPlayer.notices.value = message }

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
