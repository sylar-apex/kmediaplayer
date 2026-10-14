package org.app.kmediaplayer.util

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${pad2(m)}:${pad2(s)}" else "${pad2(m)}:${pad2(s)}"
}

/** "-3:16:56" style remaining time shown on the right of the seek bar. */
fun formatRemaining(positionMs: Long, durationMs: Long): String = "-" + formatDuration(durationMs - positionMs)

fun formatSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var u = 0
    while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
    return if (u == 0) "$bytes B" else "${formatDecimal(v, 2)} ${units[u]}"
}

/** Fixed-point formatting, the common-code stand-in for `"%.Nf".format(v)`. */
fun formatDecimal(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val scaled = round(abs(value) * factor).toLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    if (decimals == 0) return "$sign$scaled"
    val whole = scaled / factor.toLong()
    val frac = (scaled % factor.toLong()).toString().padStart(decimals, '0')
    return "$sign$whole.$frac"
}

fun formatDecimal(value: Float, decimals: Int): String = formatDecimal(value.toDouble(), decimals)

/** `1234567` → `1,234,567`. */
fun groupThousands(value: Long): String {
    val digits = abs(value).toString().reversed().chunked(3).joinToString(",").reversed()
    return if (value < 0) "-$digits" else digits
}

private fun pad2(v: Long) = v.toString().padStart(2, '0')
