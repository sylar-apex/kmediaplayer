package org.app.kmediaplayer.platform

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.OpenableColumns
import android.widget.Toast
import org.app.kmediaplayer.android.KMediaPlayerAndroid
import org.app.kmediaplayer.android.Media3Engine
import org.app.kmediaplayer.android.PlaybackService
import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.model.MediaFile
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual object PlatformMedia {
    private val context: Context get() = KMediaPlayerAndroid.context
    private val audioManager: AudioManager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    actual fun engine(): MediaEngine = Media3Engine

    actual fun systemVolume(): Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    actual fun systemMaxVolume(): Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    actual fun setSystemVolume(level: Int) = audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)

    actual fun readBytes(uri: String): ByteArray? =
        runCatching { context.contentResolver.openInputStream(Uri.parse(uri))!!.use { it.readBytes() } }.getOrNull()

    actual fun decodeText(bytes: ByteArray, charset: String): String = String(bytes, Charset.forName(charset))

    actual fun displayName(uri: String): String? {
        val u = Uri.parse(uri)
        if (u.scheme != "content") return u.lastPathSegment
        return runCatching {
            context.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    actual fun lookup(uri: String): MediaFile? {
        val u = Uri.parse(uri)
        if (u.scheme != "content") return null
        return runCatching {
            context.contentResolver.query(u, arrayOf("_data", "_display_name", "_size", "duration", "date_modified", "width", "height", "mime_type"), null, null, null)?.use { c ->
                if (!c.moveToFirst()) null else {
                    val path = c.getString(0) ?: return@use null
                    MediaFile(
                        id = runCatching { ContentUris.parseId(u) }.getOrDefault(uri.hashCode().toLong()),
                        uri = uri, path = path, name = c.getString(1) ?: path.substringAfterLast('/'),
                        sizeBytes = c.getLong(2), durationMs = c.getLong(3), dateModifiedMs = c.getLong(4) * 1000,
                        width = c.getInt(5), height = c.getInt(6), mimeType = c.getString(7) ?: "",
                    )
                }
            }
        }.getOrNull()
    }

    actual fun share(files: List<MediaFile>) {
        if (files.isEmpty()) return
        val intent = if (files.size == 1 && files[0].uri.startsWith("http")) {
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, files[0].uri)
        } else {
            val uris = ArrayList(files.map { Uri.parse(it.uri) })
            (if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris))
                .setType(if (files.all { it.isAudio }) "audio/*" else "video/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    actual fun openUri(uri: String): Boolean = runCatching {
        val intent = if (uri.startsWith("mailto:")) Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_SUBJECT, uri.substringAfter("subject=", ""))
        else Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    actual fun showToast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    actual fun displayLanguage(code: String): String = Locale(code).displayLanguage

    actual fun formatShortFileSize(bytes: Long): String = android.text.format.Formatter.formatShortFileSize(context, bytes)

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

    actual fun batteryPercent(): Int? =
        (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    actual fun launchVideoPlayer() {
        val target = KMediaPlayerAndroid.videoActivity ?: return
        context.startActivity(Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(KMediaPlayerAndroid.EXTRA_PENDING, true))
    }

    actual fun startBackgroundPlayback() {
        context.startService(Intent(context, PlaybackService::class.java))
    }

    actual fun stopBackgroundPlayback() {
        context.stopService(Intent(context, PlaybackService::class.java))
    }
}
