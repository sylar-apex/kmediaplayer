package org.app.kmediaplayer.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.PendingPlayback

/**
 * [PlayerWindow] for hosts that show the player inside their own Compose window (desktop, iOS, web).
 * Platform-only features — brightness, rotation, picture-in-picture, screenshots — are no-ops.
 */
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
    override fun rotate() {}
    override fun enterPip(): Boolean = false
    override fun screenshot() { state.toast = "Screenshot unavailable" }
    override fun setVideoDisplay(on: Boolean) { videoOn = on }
    override fun openStorePage(): Boolean = false
    override fun pickFile(mimeTypes: List<String>, onPicked: (uri: String, name: String) -> Unit) {
        state.toast = "Opening files is not supported on this platform yet"
    }

    @Composable
    override fun VideoSurface(modifier: Modifier) {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            if (videoOn) Text(state.title, color = Color(0x99FFFFFF))
        }
    }
}
