package org.app.kmediaplayer.ui.music

import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import org.app.kmediaplayer.engine.RepeatMode
import org.app.kmediaplayer.util.formatDuration
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
import androidx.compose.material.icons.filled.SkipPrevious
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

/**
 * Bar showing the current music track; tapping it opens [NowPlayingScreen]. Wide windows get a full
 * desktop transport (favourite, shuffle, repeat, seek and volume). Hidden outside music sessions.
 */
@Composable
fun MiniPlayer(addToPlaylist: AddToPlaylistSlot? = null) {
    val isMusic by KMediaPlayer.isMusicSession.collectAsState()
    if (!isMusic) return
    val player = KMediaPlayer.engine
    val queue by KMediaPlayer.queue.collectAsState()
    var index by remember { mutableIntStateOf(player.currentIndex) }
    var playing by remember { mutableStateOf(player.playWhenReady && player.state != EngineState.Ended) }
    var shuffle by remember { mutableStateOf(player.shuffleEnabled) }
    var repeat by remember { mutableStateOf(player.repeatMode) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    DisposableEffect(player) {
        val l = object : EngineListener {
            override fun onEvents() { playing = player.playWhenReady && player.state != EngineState.Ended; index = player.currentIndex }
            override fun onItemTransition() { index = player.currentIndex }
            override fun onShuffleChanged(enabled: Boolean) { shuffle = enabled }
            override fun onRepeatChanged(mode: RepeatMode) { repeat = mode }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(player) {
        var tick = 0
        while (true) {
            pos = player.position
            dur = player.duration
            if (player.isPlaying && ++tick % 10 == 0) KMediaPlayer.queue.value.getOrNull(player.currentIndex)?.let { f ->
                KMediaPlayer.host.recordPlayback(f, pos, dur)
            }
            delay(500)
        }
    }
    var open by remember { mutableStateOf(false) }
    if (open) NowPlayingScreen(addToPlaylist) { open = false }
    if (player.itemCount == 0) return
    val file = queue.getOrNull(index)
    val title = file?.title ?: player.title.orEmpty()
    // No artist known (downloads): show nothing rather than "Unknown artist".
    val artist = file?.artist?.takeIf { it.isNotBlank() && it != "Unknown" }.orEmpty()
    val seed = file?.album?.takeIf { it.isNotBlank() && it != "Unknown" } ?: title
    val progress = if (dur > 0) pos.toFloat() / dur else 0f
    val colors = MaterialTheme.colorScheme

    BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
        val card = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant)
        if (maxWidth < 900.dp) {
            Box(card.clickable { open = true }) {
                Row(Modifier.height(64.dp).padding(start = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(seed, Modifier.size(44.dp), corner = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    TitleBlock(title, artist, Modifier.weight(1f))
                    PlayButton(playing, 40.dp) { player.togglePlayback() }
                    IconButton({ player.seekToNext() }) { Icon(Icons.Default.SkipNext, "Next", tint = MaterialTheme.colorScheme.onSurface) }
                    IconButton({ player.stop(); player.clearItems(); KMediaPlayer.queue.value = emptyList() }) { Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(colors.primary))
            }
            return@BoxWithConstraints
        }
        val host = KMediaPlayer.host
        val favourites by remember { host.favourites() }.collectAsState(emptySet())
        var volume by remember { mutableFloatStateOf(player.volume) }
        var seeking by remember { mutableStateOf<Float?>(null) }
        Row(card.height(80.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { open = true }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverArt(seed, Modifier.size(52.dp), corner = 8.dp)
                Spacer(Modifier.width(14.dp))
                TitleBlock(title, artist, Modifier.weight(1f, fill = false))
                val fav = file != null && file.uri in favourites
                IconButton({ file?.let { host.toggleFavourite(it.uri) } }) {
                    Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favourite", Modifier.size(20.dp), tint = if (fav) colors.primary else colors.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton({ player.shuffleEnabled = !shuffle }) { Icon(Icons.Default.Shuffle, "Shuffle", Modifier.size(20.dp), tint = if (shuffle) colors.primary else colors.onSurfaceVariant) }
                IconButton({ player.seekToPrevious() }) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(28.dp), tint = colors.onSurface) }
                PlayButton(playing, 48.dp) { player.togglePlayback() }
                IconButton({ player.seekToNext() }) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(28.dp), tint = colors.onSurface) }
                IconButton({ player.repeatMode = when (repeat) { RepeatMode.Off -> RepeatMode.All; RepeatMode.All -> RepeatMode.One; else -> RepeatMode.Off } }) {
                    Icon(if (repeat == RepeatMode.One) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repeat", Modifier.size(20.dp), tint = if (repeat != RepeatMode.Off) colors.primary else colors.onSurfaceVariant)
                }
            }
            Row(Modifier.weight(1f).padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(seeking?.let { (it * dur).toLong() } ?: pos), fontSize = 12.sp, color = colors.onSurfaceVariant)
                ThinSlider(seeking ?: progress, Modifier.weight(1f).padding(horizontal = 10.dp), colors.primary, colors.onSurface.copy(alpha = 0.12f),
                    onChange = { seeking = it }) { seeking = null; player.seekTo((it * dur).toLong()) }
                Text(formatDuration(dur), fontSize = 12.sp, color = colors.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                IconButton({ volume = if (volume > 0f) 0f else 1f; player.volume = volume }, Modifier.size(32.dp)) {
                    Icon(if (volume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, "Volume", Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                }
                ThinSlider(volume, Modifier.width(100.dp), colors.primary, colors.onSurface.copy(alpha = 0.12f),
                    onChange = { volume = it; player.volume = it }) { volume = it; player.volume = it }
                // Close: stop playback and clear the queue, which hides this bar.
                IconButton({ player.stop(); player.clearItems(); KMediaPlayer.queue.value = emptyList() }, Modifier.padding(start = 6.dp).size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TitleBlock(title: String, artist: String, modifier: Modifier) {
    Column(modifier) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        if (artist.isNotEmpty()) Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlayButton(playing: Boolean, size: Dp, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(size).clip(CircleShape).background(colors.primary).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play", Modifier.size(size * 0.55f), tint = Color.White)
    }
}
