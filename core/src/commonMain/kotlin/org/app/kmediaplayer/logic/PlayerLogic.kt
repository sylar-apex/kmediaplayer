package org.app.kmediaplayer.logic

import kotlin.math.abs
import kotlin.math.roundToInt

/** A-B repeat: first press sets A, second sets B (must be after A), third clears. */
data class AbRepeat(val a: Long? = null, val b: Long? = null) {
    val active: Boolean get() = a != null && b != null

    fun setA(positionMs: Long) = AbRepeat(a = positionMs, b = b?.takeIf { it > positionMs })
    fun setB(positionMs: Long) = if (a != null && positionMs > a) copy(b = positionMs) else this
    fun clear() = AbRepeat()

    /** Position to jump to, or null if playback may continue. */
    fun loopTarget(positionMs: Long): Long? = if (active && positionMs >= b!!) a else null
}

/**
 * Keypad entry for the sleep timer (`0h 00m`). Digits shift in from the right like a microwave
 * timer: typing 1,3,0 gives 1h 30m.
 */
data class SleepTimerInput(val digits: String = "") {
    fun press(d: Int): SleepTimerInput = if (digits.length >= 3 || (digits.isEmpty() && d == 0)) this else SleepTimerInput(digits + d)
    fun clear() = SleepTimerInput()

    private val padded get() = digits.padStart(3, '0')
    val hours: Int get() = padded[0].digitToInt()
    val minutes: Int get() = padded.substring(1).toInt()
    val totalMinutes: Int get() = hours * 60 + minutes
    val isValid: Boolean get() = totalMinutes > 0
}

enum class ZoomMode(val label: String) {
    Fit("Fit to screen"), Stretch("Stretch"), Crop("Crop"), Original("100%");

    fun next(): ZoomMode = entries[(ordinal + 1) % entries.size]
}

enum class AspectRatio(val label: String, val ratio: Float?) {
    Default("Default", null), Square("1:1", 1f), FourThree("4:3", 4f / 3f), SixteenNine("16:9", 16f / 9f), Custom("Custom", null),
}

data class Size(val width: Float, val height: Float)

/** Computes the video surface size inside [container] for a zoom mode and aspect override. */
fun fitVideo(container: Size, video: Size, mode: ZoomMode, aspectOverride: Float? = null): Size {
    if (video.width <= 0 || video.height <= 0 || container.width <= 0 || container.height <= 0) return container
    val ar = aspectOverride ?: (video.width / video.height)
    val containerAr = container.width / container.height
    return when (mode) {
        ZoomMode.Stretch -> container
        ZoomMode.Fit -> if (ar > containerAr) Size(container.width, container.width / ar) else Size(container.height * ar, container.height)
        ZoomMode.Crop -> if (ar > containerAr) Size(container.height * ar, container.height) else Size(container.width, container.width / ar)
        ZoomMode.Original -> Size(video.height * ar, video.height)
    }
}

enum class LoopMode(val label: String) {
    Off("Loop off"), One("Repeat one"), All("Repeat all");

    fun next(): LoopMode = entries[(ordinal + 1) % entries.size]
}

enum class GestureKind { None, Seek, Volume, Brightness, Zoom }

object Gestures {
    /** Pixels before a drag is committed to an axis. */
    const val SLOP_PX = 24f

    /**
     * Decides what a single-finger drag controls, as observed: horizontal drags seek; vertical drags
     * on the left half change brightness and on the right half change volume.
     */
    fun classify(dx: Float, dy: Float, startX: Float, width: Float, seekEnabled: Boolean, volumeEnabled: Boolean, brightnessEnabled: Boolean): GestureKind {
        if (abs(dx) < SLOP_PX && abs(dy) < SLOP_PX) return GestureKind.None
        return if (abs(dx) > abs(dy)) {
            if (seekEnabled) GestureKind.Seek else GestureKind.None
        } else if (startX < width / 2) {
            if (brightnessEnabled) GestureKind.Brightness else GestureKind.None
        } else {
            if (volumeEnabled) GestureKind.Volume else GestureKind.None
        }
    }

    /** Seek offset for a horizontal drag of [dxPx] using the "Seek Speed (sec/cm)" setting. */
    fun seekOffsetMs(dxPx: Float, pxPerCm: Float, secPerCm: Int): Long =
        if (pxPerCm <= 0f) 0 else (dxPx / pxPerCm * secPerCm * 1000).toLong()

    /** Level change for a vertical drag: dragging the full [heightPx] up changes by [maxLevel]. */
    fun levelDelta(dyPx: Float, heightPx: Float, maxLevel: Int): Int =
        if (heightPx <= 0f) 0 else (-dyPx / heightPx * maxLevel).roundToInt()

    /** Which third of the screen a double tap hit: -1 left (rewind), 0 centre (play/pause), 1 right (forward). */
    fun doubleTapZone(x: Float, width: Float): Int = when {
        x < width / 3 -> -1
        x > width * 2 / 3 -> 1
        else -> 0
    }
}

object Speed {
    const val MIN = 0.25f
    const val MAX = 4.0f
    const val STEP = 0.05f

    fun clamp(v: Float): Float = (kotlin.math.round(v.coerceIn(MIN, MAX) * 100) / 100f)
    fun increase(v: Float) = clamp(v + STEP)
    fun decrease(v: Float) = clamp(v - STEP)
    fun label(v: Float): String {
        val s = clamp(v).toString()
        return (if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s) + "X"
    }
}

/**
 * Volume including boost. Levels above [systemMax] are boost steps up to 200% when enabled.
 * Returns system level and boost gain in millibels.
 */
data class VolumeState(val level: Int, val systemMax: Int, val boostEnabled: Boolean) {
    val maxLevel: Int get() = if (boostEnabled) systemMax * 2 else systemMax
    val systemLevel: Int get() = level.coerceAtMost(systemMax)
    val boostPercent: Int get() = if (level <= systemMax || systemMax == 0) 100 else 100 + (level - systemMax) * 100 / systemMax
    val boostMillibels: Int get() = if (boostPercent <= 100) 0 else ((boostPercent - 100) * 12)
    val percent: Int get() = if (systemMax == 0) 0 else level * 100 / systemMax

    fun adjust(delta: Int) = copy(level = (level + delta).coerceIn(0, maxLevel))
}

enum class ResumeDecision { Ask, Resume, StartOver }

object ResumePolicy {
    /** Positions within this distance of either end are treated as not resumable. */
    const val EDGE_MS = 5_000L

    fun decide(setting: String, savedPositionMs: Long, durationMs: Long, isFirstFile: Boolean, onlyFirst: Boolean): ResumeDecision {
        if (savedPositionMs < EDGE_MS) return ResumeDecision.StartOver
        if (durationMs > 0 && durationMs - savedPositionMs < EDGE_MS) return ResumeDecision.StartOver
        if (onlyFirst && !isFirstFile) return ResumeDecision.StartOver
        return when (setting) {
            "resume" -> ResumeDecision.Resume
            "start_over" -> ResumeDecision.StartOver
            else -> ResumeDecision.Ask
        }
    }
}

object SmartPrevious {
    /** Restart the current file when more than this far in; otherwise go to the previous file. */
    const val THRESHOLD_MS = 3_000L

    fun shouldRestart(enabled: Boolean, positionMs: Long) = enabled && positionMs > THRESHOLD_MS
}

/** Shuffled play order that keeps [current] first. */
fun shuffledOrder(size: Int, current: Int, random: kotlin.random.Random = kotlin.random.Random): List<Int> {
    if (size <= 0) return emptyList()
    val rest = (0 until size).filter { it != current }.shuffled(random)
    return listOf(current) + rest
}
