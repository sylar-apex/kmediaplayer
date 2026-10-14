package org.app.kmediaplayer.platform

import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.model.MediaFile

/** Platform services the player relies on. */
expect object PlatformMedia {
    /** The shared engine, created on first use. */
    fun engine(): MediaEngine

    fun systemVolume(): Int
    fun systemMaxVolume(): Int
    fun setSystemVolume(level: Int)

    fun readBytes(uri: String): ByteArray?
    fun decodeText(bytes: ByteArray, charset: String): String
    fun displayName(uri: String): String?
    /** Reads the real file record behind a content URI, so Info/rename/delete see the actual file. */
    fun lookup(uri: String): MediaFile?

    fun share(files: List<MediaFile>)
    fun openUri(uri: String): Boolean
    fun showToast(message: String)

    fun displayLanguage(code: String): String
    fun formatShortFileSize(bytes: Long): String
    fun formatDate(ms: Long, pattern: String): String
    /** Locale-formatted date and/or time; [dateStyle]/[timeStyle] are "short", "medium" or null to omit. */
    fun formatDateLocalized(ms: Long, dateStyle: String?, timeStyle: String?): String
    fun batteryPercent(): Int?

    /** Opens the full-screen video player for [KMediaPlayer.pending]. */
    fun launchVideoPlayer()
    fun startBackgroundPlayback()
    fun stopBackgroundPlayback()
}
