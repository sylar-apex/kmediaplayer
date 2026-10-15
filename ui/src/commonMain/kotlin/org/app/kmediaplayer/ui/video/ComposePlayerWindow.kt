package org.app.kmediaplayer.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.PendingPlayback

/**
 * [PlayerWindow] for hosts that show the player inside their own Compose window (desktop, iOS, web).
 * Platform-only features — brightness, rotation, picture-in-picture, screenshots — are no-ops.
 */
private val SUBTITLE_EXTENSIONS = listOf("srt", "vtt", "ass", "ssa", "sub", "smi", "txt")

class ComposePlayerWindow(scope: CoroutineScope, private val onClose: () -> Unit) : PlayerWindow {
    override val state = VideoPlayerState(KMediaPlayer.engine, scope)
    override val inPip: Boolean = false
    override val pxPerCm: Float = 160f / 2.54f

    private var videoOn by mutableStateOf(true)

    /** Starts whatever [KMediaPlayer.pending] holds. Returns false if there was nothing to play. */
    fun startPending(): Boolean {
        when (val p = KMediaPlayer.pending) {
            is PendingPlayback.Files -> state.start(p.files, p.index)
            is PendingPlayback.Stream -> state.startUrl(p.url)
            null -> return false
        }
        KMediaPlayer.pending = null
        return true
    }

    override fun close() {
        state.saveProgress()
        state.detach()
        state.engine.stop()
        state.engine.clearItems()
        onClose()
    }

    override fun setBrightness(level: Int) {}
    override fun applyScreenSettings() {}
    override fun showSystemBars(show: Boolean) {}
    override fun applyOrientation() {}
    /** There is no screen to rotate on desktop and the web; the button toggles full screen instead. */
    override fun rotate() { if (!PlatformVideoTools.toggleFullscreen()) state.toast = "Full screen is not available here" }
    override fun enterPip(): Boolean = PlatformVideoTools.enterPip()
    override fun screenshot() = PlatformVideoTools.captureFrame { state.snack = it }
    override fun setVideoDisplay(on: Boolean) { videoOn = on }
    override fun openStorePage(): Boolean = false
    override fun pickFile(mimeTypes: List<String>, onPicked: (uri: String, name: String) -> Unit) {
        if (!PlatformVideoTools.pickFile(SUBTITLE_EXTENSIONS, onPicked)) state.toast = "Opening files is not supported on this platform yet"
    }

    @Composable
    override fun VideoSurface(modifier: Modifier) {
        // Brightness gesture (24 of 30 = normal) and night mode tint the picture itself; on the web the
        // video sits above Compose, so the night-mode overlay alone could not dim it.
        val factor = (state.brightness / 24f).coerceIn(0.15f, 1.25f) * (if (state.nightMode && videoNeedsCutouts) 0.45f else 1f)
        LaunchedEffect(factor) { PlatformVideoTools.setPictureBrightness(factor) }
        Box(modifier.background(Color.Black)) {
            if (videoOn) PlatformVideoSurface(Modifier.matchParentSize())
        }
    }
}
