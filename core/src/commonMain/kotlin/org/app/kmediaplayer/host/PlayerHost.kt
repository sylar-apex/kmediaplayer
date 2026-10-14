package org.app.kmediaplayer.host

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.app.kmediaplayer.model.Bookmark
import org.app.kmediaplayer.model.FileSelections
import org.app.kmediaplayer.model.MediaFile
import org.app.kmediaplayer.model.PlaybackRecord
import org.app.kmediaplayer.model.SubtitleRef

/**
 * What the player needs from the app embedding it: watch history, bookmarks, remembered choices,
 * file operations and navigation. Every member has a no-op default, so a host overrides only what
 * it supports and the player still works with [PlayerHost.None].
 */
interface PlayerHost {
    /** Shown in bug reports and other messages that name the app. */
    val appName: String get() = "KMediaPlayer"

    fun playbackRecord(uri: String): PlaybackRecord? = null
    fun recordPlayback(file: MediaFile, positionMs: Long, durationMs: Long) {}

    fun selections(uri: String): FileSelections? = null
    fun updateSelections(uri: String, transform: (FileSelections) -> FileSelections) {}

    fun bookmarks(uri: String): Flow<List<Bookmark>> = flowOf(emptyList())
    fun addBookmark(uri: String, positionMs: Long) {}
    fun removeBookmark(uri: String, positionMs: Long) {}

    fun favourites(): Flow<Set<String>> = flowOf(emptySet())
    fun toggleFavourite(uri: String) {}

    /** The library record for [uri], when the host knows it. */
    fun findFile(uri: String): MediaFile? = null
    fun subtitleFiles(file: MediaFile): List<SubtitleRef> = emptyList()

    /** Deletes [file], asking the user for consent if the platform requires it. */
    suspend fun deleteFile(file: MediaFile): Boolean = false
    suspend fun renameFile(file: MediaFile, newName: String): Boolean = false

    fun openSettings() {}
    fun openHelp() {}

    object None : PlayerHost
}
