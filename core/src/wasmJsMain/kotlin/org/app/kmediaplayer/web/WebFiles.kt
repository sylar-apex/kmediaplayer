package org.app.kmediaplayer.web

import org.w3c.dom.url.URL
import org.w3c.files.File

/**
 * Local files picked in the browser, under stable IDs such as `webfs://Movies/Series/E01.mkv`.
 *
 * Browsers only play local files through temporary `blob:` URLs that die with the tab. Keeping a
 * stable ID in the library means watch progress, bookmarks and "continue watching" survive visits;
 * the ID is turned into a live URL only when a file is played or read.
 */
object WebFiles {
    const val SCHEME = "webfs://"

    private class Entry(val url: String, val size: Double, val modified: Double, val file: File)

    private val entries = mutableMapOf<String, Entry>()

    /** Live URL for [id] (or [id] itself when it is not a local file). */
    fun resolve(id: String): String = entries[id]?.url ?: id

    /** The picked file behind [id], for reading its header; null for streams. */
    fun file(id: String): File? = entries[id]?.file

    fun isLocal(id: String): Boolean = id.startsWith(SCHEME)
    fun isAvailable(id: String): Boolean = id in entries

    /** Registers [file] under [id], reusing the existing URL when the file is unchanged. */
    fun register(id: String, file: File): String {
        val size = file.size.toDouble()
        val modified = file.lastModified.toDouble()
        entries[id]?.let { old ->
            if (old.size == size && old.modified == modified) return old.url
            release(id)
        }
        return URL.createObjectURL(file).also { entries[id] = Entry(it, size, modified, file) }
    }

    /** Frees every URL whose ID is not in [keep], except the file playing right now. */
    fun retainOnly(keep: Set<String>) {
        val playing = WebPlayback.backend.currentId
        entries.keys.filter { it !in keep && it != playing }.forEach(::release)
    }

    fun release(id: String) {
        entries.remove(id)?.let { URL.revokeObjectURL(it.url) }
    }

    fun stableId(relativePath: String): String = SCHEME + relativePath
}
