package org.app.kmediaplayer.ui.inline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.model.MediaFile
import org.app.kmediaplayer.ui.music.ThinSlider
import org.app.kmediaplayer.ui.video.VideoPlayerState
import org.app.kmediaplayer.util.formatDuration

private const val VIDEO_RATIO = 16f / 9f
private const val HIDE_AFTER_MS = 3_000L
private val Scrim = Color(0x66000000)
private val OnVideo = Color(0xFFFFFFFF)
private val Track = Color(0x66FFFFFF)

/**
 * A 16:9 player for the top of a detail page: tap to show play/pause, the seek bar and the full-screen
 * button. Full screen opens the library's full player on the same playback, without restarting it.
 * Leaving the page pauses playback unless it moved to the full player.
 */
@Composable
fun InlineVideoPlayer(files: List<MediaFile>, index: Int, autoPlay: Boolean, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val state = remember(files, index) { VideoPlayerState(KMediaPlayer.engine, scope) }
    var started by remember(files, index) { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    /** Bumped on return from full screen so a fresh surface is attached to the engine. */
    var surfaceKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(state, autoPlay) {
        if (autoPlay && !started) {
            state.start(files, index)
            started = true
        }
    }
    // Back from full screen: continue here, from where the full player stopped.
    val fullOpen by KMediaPlayer.fullPlayerOpen.collectAsState()
    LaunchedEffect(fullOpen) {
        if (fullOpen || !expanded) return@LaunchedEffect
        expanded = false
        surfaceKey++
        val closed = KMediaPlayer.lastClosed
        if (KMediaPlayer.queue.value.isNotEmpty() && state.engine.duration > 0 && closed == null) {
            state.reattach()
        } else {
            state.start(files, index)
            closed?.takeIf { c -> files.any { it.uri == c.uri } }?.let { c ->
                state.seekTo(c.positionMs)
                if (!c.playing) state.engine.pause()
            }
        }
    }
    DisposableEffect(state) {
        onDispose {
            if (started && !expanded) KMediaPlayer.engine.pause()
            if (started) state.detach()
        }
    }
    LaunchedEffect(controls, state.isPlaying) {
        if (controls && state.isPlaying) {
            delay(HIDE_AFTER_MS)
            controls = false
        }
    }

    Box(
        modifier.fillMaxWidth().aspectRatio(VIDEO_RATIO).background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls },
    ) {
        if (started) key(surfaceKey) { InlineVideoSurface(Modifier.fillMaxSize()) }
        if (started && state.buffering) CircularProgressIndicator(Modifier.align(Alignment.Center), color = OnVideo)
        AnimatedVisibility(controls || !started, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Scrim)) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play",
                    tint = OnVideo,
                    modifier = Modifier.align(Alignment.Center).size(56.dp).background(Scrim, CircleShape).clickable {
                        if (!started) {
                            state.start(files, index)
                            started = true
                        } else {
                            state.togglePlay()
                        }
                    }.padding(8.dp),
                )
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(state.position), color = OnVideo, fontSize = 12.sp)
                    ThinSlider(
                        value = if (state.duration > 0) state.position.toFloat() / state.duration else 0f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        active = OnVideo,
                        inactive = Track,
                        onCommit = { if (started) state.seekTo((it * state.duration).toLong()) },
                    )
                    Text(formatDuration(state.duration), color = OnVideo, fontSize = 12.sp)
                    Icon(
                        Icons.Default.Fullscreen, "Full screen", tint = OnVideo,
                        modifier = Modifier.padding(start = 8.dp).size(26.dp).clickable {
                            if (!started) state.start(files, index)
                            started = true
                            expanded = true
                            KMediaPlayer.expandVideo()
                        },
                    )
                }
            }
        }
    }
}
