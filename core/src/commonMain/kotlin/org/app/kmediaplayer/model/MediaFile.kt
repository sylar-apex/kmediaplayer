package org.app.kmediaplayer.model

/** A playable file or stream. [uri] is what the engine opens; [path] is the file-system location when known. */
data class MediaFile(
    val id: Long,
    val uri: String,
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val dateModifiedMs: Long,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val mimeType: String = "",
    val isAudio: Boolean = false,
    val artist: String = "",
    val album: String = "",
    /** Network extras for streams (separate audio track, User-Agent); none for local files. */
    val extras: StreamExtras = StreamExtras.None,
    /** A small picture of the video (a frame), shown blurred while a stream starts; null when there is none. */
    val posterUrl: String? = null,
) {
    val folderPath: String get() = path.substringBeforeLast('/', "")
    val folderName: String get() = folderPath.substringAfterLast('/')
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val title: String get() = if ('.' in name) name.substringBeforeLast('.') else name
    val resolutionPixels: Long get() = width.toLong() * height
    val isHidden: Boolean get() = path.split('/').any { it.startsWith('.') && it.length > 1 }
}

/**
 * What a network stream needs beyond its URL: [audioUri] is a separate audio stream played in sync
 * with the video (YouTube's DASH qualities), [userAgent] the HTTP User-Agent the server expects.
 */
data class StreamExtras(val audioUri: String? = null, val userAgent: String? = null) {
    companion object { val None = StreamExtras() }
}

/** Per-file user state recorded by the player. */
data class PlaybackRecord(
    val positionMs: Long = 0,
    val lastPlayedMs: Long = 0,
    val finished: Boolean = false,
)

data class Bookmark(val positionMs: Long, val createdMs: Long)

/** Choices remembered per file when "Remember selections" is on. */
data class FileSelections(
    val audioTrack: String? = null,
    val subtitleTrack: String? = null,
    val decoder: String? = null,
    val aspect: String? = null,
    val zoom: String? = null,
    val speed: Float? = null,
    val subtitleSyncMs: Long? = null,
)

/** A subtitle file found next to a video, opened through [uri]. */
data class SubtitleRef(val uri: String, val name: String)
