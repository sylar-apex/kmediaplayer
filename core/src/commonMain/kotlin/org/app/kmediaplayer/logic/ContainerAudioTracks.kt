package org.app.kmediaplayer.logic

/** One audio stream found in a file's header, in file order. */
data class ContainerAudioTrack(val language: String?, val name: String?) {
    /** "Hindi · ORG 5.1", "English", or null when the file names nothing. */
    val label: String? get() = listOfNotNull(language?.let(::languageName), name?.takeIf { it.isNotBlank() }).distinct().joinToString(" · ").ifEmpty { null }
}

/**
 * Lists the audio streams of an MKV/WebM or MP4/MOV file from its header, for players (browsers) that play a
 * file without telling the page which audio tracks it has. [read] returns up to `length` bytes at `offset`.
 * Returns an empty list for other formats or when the header cannot be found.
 */
object ContainerAudioTracks {
    private const val MAX_HEADER_BYTES = 64 * 1024 * 1024

    suspend fun read(size: Long, read: suspend (offset: Long, length: Int) -> ByteArray): List<ContainerAudioTrack> {
        val head = read(0, 16)
        return when {
            head.size >= 4 && head.u32(0) == 0x1A45DFA3L -> matroska(size, read)
            head.size >= 8 && head.ascii(4, 4) in setOf("ftyp", "moov", "free", "mdat", "wide", "skip") -> mp4(size, read)
            else -> emptyList()
        }
    }

    // --- Matroska / WebM (EBML) ---

    private suspend fun matroska(size: Long, read: suspend (Long, Int) -> ByteArray): List<ContainerAudioTrack> {
        var pos = 0L
        var end = size
        repeat(256) {
            if (pos >= end) return emptyList()
            val h = read(pos, 16)
            val id = h.vintId(0) ?: return emptyList()
            val len = h.vintSize(id.second) ?: return emptyList()
            val dataStart = pos + id.second + len.second
            val dataSize = len.first
            when (id.first) {
                0x18538067L -> { // Segment: descend
                    end = if (dataSize < 0) size else minOf(size, dataStart + dataSize)
                    pos = dataStart
                }
                0x1654AE6BL -> { // Tracks
                    if (dataSize !in 0..MAX_HEADER_BYTES) return emptyList()
                    return matroskaTracks(read(dataStart, dataSize.toInt()))
                }
                0x1F43B675L -> return emptyList() // Cluster before Tracks: give up rather than scan the media
                else -> {
                    if (dataSize < 0) return emptyList()
                    pos = dataStart + dataSize
                }
            }
        }
        return emptyList()
    }

    private fun matroskaTracks(b: ByteArray): List<ContainerAudioTrack> {
        val out = mutableListOf<ContainerAudioTrack>()
        ebmlChildren(b, 0, b.size) { id, start, len ->
            if (id != 0xAEL) return@ebmlChildren // TrackEntry
            var type = 0L
            var language: String? = "eng" // Matroska's default when the element is absent
            var ietf: String? = null
            var name: String? = null
            ebmlChildren(b, start, start + len) { cid, cs, cl ->
                when (cid) {
                    0x83L -> type = b.uint(cs, cl)
                    0x22B59CL -> language = b.utf8(cs, cl)
                    0x22B59DL -> ietf = b.utf8(cs, cl)
                    0x536EL -> name = b.utf8(cs, cl)
                }
            }
            if (type == 2L) out += ContainerAudioTrack((ietf ?: language)?.takeUnless { it == "und" }, name)
        }
        return out
    }

    private inline fun ebmlChildren(b: ByteArray, from: Int, to: Int, each: (id: Long, start: Int, length: Int) -> Unit) {
        var p = from
        while (p < to) {
            val id = b.vintId(p) ?: return
            val len = b.vintSize(p + id.second) ?: return
            val start = p + id.second + len.second
            val l = if (len.first < 0) to - start else len.first.toInt()
            if (start + l > to || l < 0) return
            each(id.first, start, l)
            p = start + l
        }
    }

    /** Element ID with its marker bits, and its length in bytes. */
    private fun ByteArray.vintId(p: Int): Pair<Long, Int>? {
        if (p >= size) return null
        val first = this[p].toInt() and 0xFF
        val n = (1..4).firstOrNull { first and (0x80 shr (it - 1)) != 0 } ?: return null
        if (p + n > size) return null
        var v = 0L
        for (i in 0 until n) v = (v shl 8) or (this[p + i].toLong() and 0xFF)
        return v to n
    }

    /** Element size (-1 when "unknown") and its length in bytes. */
    private fun ByteArray.vintSize(p: Int): Pair<Long, Int>? {
        if (p >= size) return null
        val first = this[p].toInt() and 0xFF
        val n = (1..8).firstOrNull { first and (0x80 shr (it - 1)) != 0 } ?: return null
        if (p + n > size) return null
        var v = (first and (0xFF shr n)).toLong()
        var allOnes = v == (0xFF shr n).toLong()
        for (i in 1 until n) {
            val byte = this[p + i].toLong() and 0xFF
            if (byte != 0xFFL) allOnes = false
            v = (v shl 8) or byte
        }
        return (if (allOnes) -1L else v) to n
    }

    // --- MP4 / QuickTime (ISO BMFF) ---

    private suspend fun mp4(size: Long, read: suspend (Long, Int) -> ByteArray): List<ContainerAudioTrack> {
        var pos = 0L
        repeat(64) {
            if (pos + 8 > size) return emptyList()
            val h = read(pos, 16)
            if (h.size < 8) return emptyList()
            var boxSize = h.u32(0)
            var header = 8
            if (boxSize == 1L && h.size >= 16) { boxSize = h.u64(8); header = 16 }
            if (boxSize == 0L) boxSize = size - pos
            if (boxSize < header) return emptyList()
            if (h.ascii(4, 4) == "moov") {
                val len = boxSize - header
                if (len > MAX_HEADER_BYTES) return emptyList()
                return mp4Tracks(read(pos + header, len.toInt()))
            }
            pos += boxSize
        }
        return emptyList()
    }

    private fun mp4Tracks(moov: ByteArray): List<ContainerAudioTrack> {
        val out = mutableListOf<ContainerAudioTrack>()
        boxes(moov, 0, moov.size) { type, s, e ->
            if (type != "trak") return@boxes
            var handler: String? = null
            var language: String? = null
            var name: String? = null
            boxes(moov, s, e) { t2, s2, e2 ->
                when (t2) {
                    "mdia" -> boxes(moov, s2, e2) { t3, s3, e3 ->
                        when (t3) {
                            "hdlr" -> if (e3 - s3 >= 12) handler = moov.ascii(s3 + 8, 4)
                            "mdhd" -> {
                                val at = s3 + if (moov[s3].toInt() == 1) 32 else 20
                                if (at + 2 <= e3) language = isoLanguage(((moov[at].toInt() and 0xFF) shl 8) or (moov[at + 1].toInt() and 0xFF))
                            }
                        }
                    }
                    "udta" -> boxes(moov, s2, e2) { t3, s3, e3 -> if (t3 == "name") name = moov.utf8(s3, e3 - s3) }
                }
            }
            if (handler == "soun") out += ContainerAudioTrack(language, name)
        }
        return out
    }

    private inline fun boxes(b: ByteArray, from: Int, to: Int, each: (type: String, start: Int, end: Int) -> Unit) {
        var p = from
        while (p + 8 <= to) {
            var len = b.u32(p)
            var header = 8
            if (len == 1L && p + 16 <= to) { len = b.u64(p + 8); header = 16 }
            if (len == 0L) len = (to - p).toLong()
            if (len < header || p + len > to) return
            each(b.ascii(p + 4, 4), p + header, (p + len).toInt())
            p += len.toInt()
        }
    }

    /** ISO 639-2/T code packed as three 5-bit letters; null for "und". */
    private fun isoLanguage(packed: Int): String? {
        val code = CharArray(3) { ((packed shr (10 - it * 5)) and 0x1F).plus(0x60).toChar() }.concatToString()
        return code.takeUnless { it == "und" || it.any { c -> c !in 'a'..'z' } }
    }

    // --- bytes ---

    private fun ByteArray.u32(p: Int): Long = (0 until 4).fold(0L) { v, i -> (v shl 8) or (this[p + i].toLong() and 0xFF) }
    private fun ByteArray.u64(p: Int): Long = (0 until 8).fold(0L) { v, i -> (v shl 8) or (this[p + i].toLong() and 0xFF) }
    private fun ByteArray.uint(p: Int, n: Int): Long = (0 until n).fold(0L) { v, i -> (v shl 8) or (this[p + i].toLong() and 0xFF) }
    private fun ByteArray.ascii(p: Int, n: Int): String = if (p + n > size) "" else CharArray(n) { (this[p + it].toInt() and 0xFF).toChar() }.concatToString()
    private fun ByteArray.utf8(p: Int, n: Int): String = copyOfRange(p, p + n).decodeToString().trimEnd('\u0000').trim()
}

/** "hin" / "hi" / "en-US" → "Hindi" / "English"; unknown codes come back upper-cased. */
fun languageName(code: String): String {
    val base = code.substringBefore('-').lowercase()
    return LANGUAGES[base] ?: code.uppercase()
}

private val LANGUAGES = mapOf(
    "en" to "English", "eng" to "English", "hi" to "Hindi", "hin" to "Hindi", "ta" to "Tamil", "tam" to "Tamil",
    "te" to "Telugu", "tel" to "Telugu", "ml" to "Malayalam", "mal" to "Malayalam", "kn" to "Kannada", "kan" to "Kannada",
    "bn" to "Bengali", "ben" to "Bengali", "mr" to "Marathi", "mar" to "Marathi", "gu" to "Gujarati", "guj" to "Gujarati",
    "pa" to "Punjabi", "pan" to "Punjabi", "ur" to "Urdu", "urd" to "Urdu", "es" to "Spanish", "spa" to "Spanish",
    "fr" to "French", "fra" to "French", "fre" to "French", "de" to "German", "deu" to "German", "ger" to "German",
    "it" to "Italian", "ita" to "Italian", "pt" to "Portuguese", "por" to "Portuguese", "ru" to "Russian", "rus" to "Russian",
    "ja" to "Japanese", "jpn" to "Japanese", "ko" to "Korean", "kor" to "Korean", "zh" to "Chinese", "zho" to "Chinese",
    "chi" to "Chinese", "ar" to "Arabic", "ara" to "Arabic", "tr" to "Turkish", "tur" to "Turkish",
)
