package org.app.kmediaplayer.logic

data class Cue(val startMs: Long, val endMs: Long, val text: String, val italic: Boolean = false)

class SubtitleDocument(val cues: List<Cue>) {
    private val sorted = cues.sortedBy { it.startMs }

    /**
     * Cues visible at media position [positionMs]. A positive [syncMs] delays the subtitles; [speed]
     * stretches the subtitle timeline (1.0 = unchanged), matching the Sync/Speed rows of the subtitle panel.
     */
    fun cuesAt(positionMs: Long, syncMs: Long = 0, speed: Float = 1f): List<Cue> {
        val t = ((positionMs - syncMs) * speed).toLong()
        return sorted.filter { t >= it.startMs && t < it.endMs }
    }

    fun nextCueStart(positionMs: Long, syncMs: Long = 0, speed: Float = 1f): Long? {
        val t = ((positionMs - syncMs) * speed).toLong()
        val cue = sorted.firstOrNull { it.startMs > t } ?: return null
        return (cue.startMs / speed).toLong() + syncMs
    }

    fun previousCueStart(positionMs: Long, syncMs: Long = 0, speed: Float = 1f): Long? {
        val t = ((positionMs - syncMs) * speed).toLong()
        val cue = sorted.lastOrNull { it.startMs < t - 500 } ?: return null
        return (cue.startMs / speed).toLong() + syncMs
    }
}

object SubtitleParser {
    val supportedExtensions = setOf("srt", "vtt", "webvtt")

    fun parse(content: String, extension: String, italicSlash: Boolean = false): SubtitleDocument {
        val text = content.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val cues = when (extension.lowercase()) {
            "vtt", "webvtt" -> parseBlocks(text.substringAfter("WEBVTT", text), italicSlash)
            else -> parseBlocks(text, italicSlash)
        }
        return SubtitleDocument(cues)
    }

    private val timing = Regex("""(\d{1,2}:)?(\d{1,2}):(\d{1,2})[,.](\d{1,3})\s*-->\s*(\d{1,2}:)?(\d{1,2}):(\d{1,2})[,.](\d{1,3})""")
    private val tags = Regex("""<[^>]+>|\{\\[^}]*\}""")

    private fun parseBlocks(text: String, italicSlash: Boolean): List<Cue> {
        val result = mutableListOf<Cue>()
        for (block in text.split(Regex("\n\\s*\n"))) {
            val lines = block.lines().filter { it.isNotBlank() }
            val idx = lines.indexOfFirst { timing.containsMatchIn(it) }
            if (idx < 0) continue
            val m = timing.find(lines[idx])!!.groupValues
            val start = toMs(m[1], m[2], m[3], m[4])
            val end = toMs(m[5], m[6], m[7], m[8])
            val raw = lines.drop(idx + 1).joinToString("\n")
            val wasItalic = raw.contains("<i>")
            var body = raw.replace(tags, "").trim()
            var italic = wasItalic
            if (italicSlash && body.startsWith("/")) { italic = true; body = body.removePrefix("/").trim() }
            if (body.isNotEmpty()) result += Cue(start, end, body, italic)
        }
        return result
    }

    private fun toMs(h: String, m: String, s: String, frac: String): Long {
        val hours = h.removeSuffix(":").toLongOrNull() ?: 0
        val ms = frac.padEnd(3, '0').take(3).toLong()
        return ((hours * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + ms
    }
}
