package org.app.kmediaplayer.ui.video

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.settings.PlayerKeys
import org.app.kmediaplayer.util.formatDecimal

/**
 * Keyboard shortcuts for desktop and the web, matching common players (VLC, YouTube):
 *
 * | Key | Action |
 * |---|---|
 * | Space, K | play / pause |
 * | ← / → | back / forward by the "Move interval" setting (Shift: 1 minute) |
 * | J / L | back / forward 10 s |
 * | ↑ / ↓ | volume up / down |
 * | Cmd/Ctrl + ↑ / ↓ | brightness up / down |
 * | M | mute |
 * | F | full screen |
 * | Esc | close panel, leave full screen |
 * | Cmd/Ctrl + →, Cmd/Ctrl + N, Shift + N | next video |
 * | Cmd/Ctrl + ←, Cmd/Ctrl + P, Shift + P | previous video |
 * | [ / ] | slower / faster, \\ resets speed |
 * | 0 – 9 | jump to 0 % … 90 % |
 * | Home / End | start / end |
 * | S | screenshot |
 * | C | subtitles on / off |
 */
internal object PlayerKeyboard {
    private const val LONG_SEEK_MS = 60_000L
    private const val SHORT_SEEK_MS = 10_000L
    private const val SPEED_STEP = 0.25f
    /** Brightness runs 0..30 like the swipe gesture; 2 steps per key press. */
    private const val BRIGHTNESS_STEP = 2
    private const val BRIGHTNESS_MAX = 30

    /** Handles [event] for [window]; true when the key was a player shortcut. */
    fun handle(event: KeyEvent, window: PlayerWindow): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val st = window.state
        val command = event.isMetaPressed || event.isCtrlPressed
        if (st.locked) return false
        val handled = when (event.key) {
            Key.Spacebar, Key.K -> { st.togglePlay(); true }
            Key.DirectionLeft -> { if (command) st.previous() else seek(st, -(if (event.isShiftPressed) LONG_SEEK_MS else moveMs())); true }
            Key.DirectionRight -> { if (command) st.next() else seek(st, if (event.isShiftPressed) LONG_SEEK_MS else moveMs()); true }
            Key.J -> { seek(st, -SHORT_SEEK_MS); true }
            Key.L -> { seek(st, SHORT_SEEK_MS); true }
            Key.DirectionUp -> { if (command) brightness(window, +BRIGHTNESS_STEP) else volume(st, +1); true }
            Key.DirectionDown -> { if (command) brightness(window, -BRIGHTNESS_STEP) else volume(st, -1); true }
            Key.M -> { st.toggleMute(); true }
            Key.F -> { if (!PlatformVideoTools.toggleFullscreen()) st.toast = "Full screen is not available here"; true }
            Key.Escape -> when {
                st.panel != Panel.None -> { st.panel = Panel.None; true }
                PlatformVideoTools.isFullscreen -> { PlatformVideoTools.toggleFullscreen(); true }
                else -> false
            }
            Key.N -> if (command || event.isShiftPressed) { st.next(); true } else false
            Key.P -> if (command || event.isShiftPressed) { st.previous(); true } else false
            Key.LeftBracket -> { speed(st, st.speed - SPEED_STEP); true }
            Key.RightBracket -> { speed(st, st.speed + SPEED_STEP); true }
            Key.Backslash -> { speed(st, 1f); true }
            Key.MoveHome -> { st.seekTo(0); true }
            Key.MoveEnd -> { st.seekTo((st.duration - 1_000).coerceAtLeast(0)); true }
            Key.S -> if (!command) { window.screenshot(); true } else false
            Key.C -> { st.toggleSubtitles(); true }
            else -> digit(event.key)?.let { d -> st.seekTo(st.duration * d / 10); true } ?: false
        }
        return handled
    }

    private fun moveMs(): Long = KMediaPlayer.settings[PlayerKeys.moveInterval] * 1000L

    private fun seek(st: VideoPlayerState, deltaMs: Long) {
        st.seekBy(deltaMs)
        // Repeated presses the same way add up in the badge: 10s, 20s, 30s…
        val dir = if (deltaMs > 0) 1 else -1
        val prev = st.tapSeekOsd?.takeIf { it.first == dir }?.second ?: 0
        st.tapSeekOsd = dir to prev + (kotlin.math.abs(deltaMs) / 1000).toInt()
    }

    private fun volume(st: VideoPlayerState, delta: Int) {
        st.adjustVolume(delta)
        val v = st.volume
        st.levelOsd = LevelOsd(false, v.percent, v.level.toFloat() / v.maxLevel.coerceAtLeast(1), if (v.boostEnabled) 0.5f else null)
    }

    private fun brightness(window: PlayerWindow, delta: Int) {
        val st = window.state
        val level = (st.brightness + delta).coerceIn(0, BRIGHTNESS_MAX)
        st.brightness = level
        window.setBrightness(level)
        KMediaPlayer.settings[PlayerKeys.brightnessLevel] = level
        st.levelOsd = LevelOsd(true, level, level / BRIGHTNESS_MAX.toFloat(), null)
    }

    private fun speed(st: VideoPlayerState, value: Float) {
        val v = value.coerceIn(0.25f, 4f)
        st.setSpeed(v)
        st.toast = "Speed ${formatDecimal(v, 2)}X"
    }

    private fun digit(key: Key): Long? = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
        .indexOf(key).takeIf { it >= 0 }?.toLong()
}
