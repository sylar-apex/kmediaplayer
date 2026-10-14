package org.app.kmediaplayer.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.engine.EngineListener
import org.app.kmediaplayer.engine.EngineState
import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.model.MediaFile
import org.app.kmediaplayer.platform.PlatformMedia

/** Extra UI the host may show from the music screens, e.g. its own "add to playlist" dialog. */
typealias AddToPlaylistSlot = @Composable (file: MediaFile, onDismiss: () -> Unit) -> Unit

internal fun MediaEngine.togglePlayback() {
    if (playWhenReady && state != EngineState.Ended) pause() else {
        if (state == EngineState.Idle) prepare()
        if (state == EngineState.Ended) seekTo(currentIndex, 0)
        play(); PlatformMedia.startBackgroundPlayback()
    }
}

/** Bar showing the current music track; tapping it opens [NowPlayingScreen]. Hidden outside music sessions. */
@Composable
fun MiniPlayer(addToPlaylist: AddToPlaylistSlot? = null) {
    val isMusic by KMediaPlayer.isMusicSession.collectAsState()
    if (!isMusic) return
    val player = KMediaPlayer.engine
    var title by remember { mutableStateOf(player.title ?: "") }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var progress by remember { mutableFloatStateOf(0f) }
    DisposableEffect(player) {
        val l = object : EngineListener {
            override fun onEvents() { playing = player.playWhenReady && player.state != EngineState.Ended }
            override fun onItemTransition() { title = player.title ?: "" }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(player) {
        var tick = 0
        while (true) {
            val d = player.duration
            progress = if (d > 0) player.position.toFloat() / d else 0f
            if (player.isPlaying && ++tick % 5 == 0) KMediaPlayer.queue.value.getOrNull(player.currentIndex)?.let { f ->
                KMediaPlayer.host.recordPlayback(f, player.position, d)
            }
            delay(1000)
        }
    }
    var open by remember { mutableStateOf(false) }
    if (open) NowPlayingScreen(addToPlaylist) { open = false }
    if (player.itemCount == 0) return
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).clickable { open = true }) {
        Row(Modifier.height(52.dp).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Color(0xFFE4DCFA), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, null, Modifier.size(20.dp), tint = Color(0xFF9C7BEA))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            IconButton({ player.togglePlayback() }) {
                Box(Modifier.size(32.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", Modifier.size(20.dp))
                }
            }
            IconButton({ player.seekToNext() }) { Icon(Icons.Default.SkipNext, "Next") }
        }
    }
}
