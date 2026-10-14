package org.app.kmediaplayer.ui.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.engine.EngineListener
import org.app.kmediaplayer.engine.EngineState
import org.app.kmediaplayer.engine.RepeatMode
import org.app.kmediaplayer.platform.PlatformMedia
import org.app.kmediaplayer.ui.common.edgeToEdgeDialogProperties
import org.app.kmediaplayer.ui.resources.Res
import org.app.kmediaplayer.ui.resources.ic_chevron_down
import org.app.kmediaplayer.ui.resources.ic_ov_share
import org.app.kmediaplayer.ui.resources.ic_pl_eq
import org.app.kmediaplayer.ui.resources.ic_pl_loop
import org.app.kmediaplayer.ui.resources.ic_pl_loop_one
import org.app.kmediaplayer.ui.resources.ic_pl_next
import org.app.kmediaplayer.ui.resources.ic_pl_pause
import org.app.kmediaplayer.ui.resources.ic_pl_play
import org.app.kmediaplayer.ui.resources.ic_pl_prev
import org.app.kmediaplayer.ui.resources.ic_pl_shuffle
import org.app.kmediaplayer.ui.theme.PlayerAccent
import org.app.kmediaplayer.ui.video.EqualizerPanel
import org.app.kmediaplayer.util.formatDuration
import org.jetbrains.compose.resources.painterResource

private val Bg = Color(0xFF0D1117)
private val Dim = Color(0x99FFFFFF)
private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Full-screen music player with a spinning record, transport, A-B repeat, speed, equalizer and queue. */
@Composable
fun NowPlayingScreen(addToPlaylist: AddToPlaylistSlot? = null, onClose: () -> Unit) {
    val player = KMediaPlayer.engine
    val host = KMediaPlayer.host
    val queue by KMediaPlayer.queue.collectAsState()
    val favourites by remember { host.favourites() }.collectAsState(emptySet())
    var index by remember { mutableIntStateOf(player.currentIndex) }
    var playing by remember { mutableStateOf(player.playWhenReady && player.state != EngineState.Ended) }
    var shuffle by remember { mutableStateOf(player.shuffleEnabled) }
    var repeat by remember { mutableStateOf(player.repeatMode) }
    var pos by remember { mutableLongStateOf(player.position) }
    var dur by remember { mutableLongStateOf(player.duration) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var abA by remember { mutableLongStateOf(-1L) }
    var abB by remember { mutableLongStateOf(-1L) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val file = queue.getOrNull(index)

    DisposableEffect(player) {
        val l = object : EngineListener {
            override fun onEvents() { playing = player.playWhenReady && player.state != EngineState.Ended }
            override fun onItemTransition() { index = player.currentIndex; abA = -1; abB = -1 }
            override fun onShuffleChanged(enabled: Boolean) { shuffle = enabled }
            override fun onRepeatChanged(mode: RepeatMode) { repeat = mode }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(player) {
        while (true) {
            pos = player.position
            dur = player.duration
            if (abA >= 0 && abB > abA && pos >= abB) player.seekTo(abA)
            delay(250)
        }
    }

    val spin = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        while (playing) {
            spin.animateTo(spin.value + 360f, tween(12000, easing = LinearEasing))
            spin.snapTo(spin.value % 360f)
        }
    }
    val arm by animateFloatAsState(if (playing) 16f else 0f, tween(400), label = "arm")

    Dialog(onClose, edgeToEdgeDialogProperties()) {
        Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(painterResource(Res.drawable.ic_chevron_down), "Close", tint = Color.White) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(file?.title ?: player.title.orEmpty(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.basicMarquee())
                    Text(file?.artist?.ifEmpty { null } ?: "Unknown", color = Dim, fontSize = 13.sp, maxLines = 1)
                }
                IconButton({ file?.let { PlatformMedia.share(listOf(it)) } }) { Icon(painterResource(Res.drawable.ic_ov_share), "Share", tint = Color.White) }
            }

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.padding(horizontal = 44.dp).fillMaxWidth().aspectRatio(1f).rotate(spin.value), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2
                        drawCircle(Color(0xFF3A3F46), r)
                        drawCircle(Color(0xFF0A0A0A), r * 0.96f)
                        for (k in 1..6) drawCircle(Color(0xFF1A1A1A), r * (0.68f + k * 0.045f), style = Stroke(1.dp.toPx()))
                        drawCircle(Color(0xFF222222), r * 0.68f)
                        drawCircle(Color(0xFF8A8A8A), r * 0.64f)
                    }
                    Icon(Icons.Default.MusicNote, null, Modifier.size(150.dp), tint = Color(0xFF5E5E5E))
                }
                Canvas(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 40.dp).size(170.dp, 90.dp)) {
                    val pivot = Offset(14.dp.toPx(), 14.dp.toPx())
                    rotate(arm, pivot) {
                        drawLine(Color(0xFFCFCFCF), pivot, Offset(size.width * 0.62f, size.height * 0.35f), 3.dp.toPx(), StrokeCap.Round)
                        drawLine(Color(0xFFCFCFCF), Offset(size.width * 0.62f, size.height * 0.35f), Offset(size.width * 0.9f, size.height * 0.7f), 3.dp.toPx(), StrokeCap.Round)
                        drawRect(Color(0xFFBDBDBD), Offset(size.width * 0.86f, size.height * 0.66f), androidx.compose.ui.geometry.Size(14.dp.toPx(), 10.dp.toPx()))
                    }
                    drawCircle(Color(0xFF9E9E9E), 13.dp.toPx(), pivot)
                    drawCircle(Color(0xFFE0E0E0), 8.dp.toPx(), pivot)
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton({ sheet = "eq" }) { Icon(painterResource(Res.drawable.ic_pl_eq), "Equalizer", tint = Color.White) }
                IconButton({
                    when {
                        abA < 0 -> { abA = player.position; PlatformMedia.showToast("A point set") }
                        abB < 0 -> { abB = player.position.coerceAtLeast(abA + 500); PlatformMedia.showToast("A-B repeat on") }
                        else -> { abA = -1; abB = -1; PlatformMedia.showToast("A-B repeat off") }
                    }
                }) {
                    val c = if (abA >= 0) PlayerAccent else Color.White
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("A→", color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
                        Text("←B", color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
                    }
                }
                Box {
                    IconButton({ speedMenu = true }) { Icon(Icons.Default.Speed, "Speed", tint = if (player.speed != 1f) PlayerAccent else Color.White) }
                    DropdownMenu(speedMenu, { speedMenu = false }) {
                        speeds.forEach { s -> DropdownMenuItem({ Text("${s}x") }, { speedMenu = false; player.speed = s }) }
                    }
                }
                val fav = file != null && file.uri in favourites
                IconButton({ file?.let { f -> host.toggleFavourite(f.uri) } }) {
                    Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favourite", tint = Color.White)
                }
                Box {
                    IconButton({ moreMenu = true }) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
                    DropdownMenu(moreMenu, { moreMenu = false }) {
                        if (addToPlaylist != null) DropdownMenuItem({ Text("Add to Playlist") }, { moreMenu = false; sheet = "add" })
                        DropdownMenuItem({ Text("Share") }, { moreMenu = false; file?.let { PlatformMedia.share(listOf(it)) } })
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text(formatDuration(dragging?.let { (it * dur).toLong() } ?: pos), color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(dur), color = Color.White, fontSize = 13.sp)
            }
            val frac = dragging ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            val durNow by rememberUpdatedState(dur)
            fun commit(f: Float) { val t = (f * durNow).toLong(); pos = t; player.seekTo(t) }
            Canvas(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(40.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { o -> commit((o.x / size.width).coerceIn(0f, 1f)) }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { o -> dragging = (o.x / size.width).coerceIn(0f, 1f) },
                            onDragEnd = { dragging?.let { commit(it) }; dragging = null },
                            onDragCancel = { dragging = null },
                        ) { change, _ -> change.consume(); dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
                    },
            ) {
                val y = size.height / 2
                val x = size.width * frac
                drawLine(Color(0xFF4A4F57), Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                drawLine(PlayerAccent, Offset(0f, y), Offset(x, y), 2.dp.toPx())
                drawCircle(PlayerAccent, 10.dp.toPx(), Offset(x, y))
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ player.shuffleEnabled = !shuffle }) { Icon(painterResource(Res.drawable.ic_pl_shuffle), "Shuffle", tint = if (shuffle) PlayerAccent else Color.White) }
                IconButton({ player.seekToPrevious() }) { Icon(painterResource(Res.drawable.ic_pl_prev), "Previous", tint = Color.White) }
                Box(
                    Modifier.size(64.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape).clickable { player.togglePlayback() },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(if (playing) Res.drawable.ic_pl_pause else Res.drawable.ic_pl_play), "Play/Pause", Modifier.size(32.dp), tint = Color.White) }
                IconButton({ player.seekToNext() }) { Icon(painterResource(Res.drawable.ic_pl_next), "Next", tint = Color.White) }
                IconButton({
                    player.repeatMode = when (repeat) { RepeatMode.Off -> RepeatMode.All; RepeatMode.All -> RepeatMode.One; else -> RepeatMode.Off }
                }) {
                    Icon(painterResource(if (repeat == RepeatMode.One) Res.drawable.ic_pl_loop_one else Res.drawable.ic_pl_loop), "Repeat", tint = if (repeat == RepeatMode.Off) Color.White else PlayerAccent)
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clickable { PlatformMedia.showToast("No lyrics found") }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, Modifier.size(22.dp), tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("Lyrics", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.clickable { sheet = "queue" }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(22.dp), tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("Playing Queue", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        when (sheet) {
            "eq" -> Dialog(onDismissRequest = { sheet = null }) {
                Column(Modifier.fillMaxWidth().background(Color(0xF0202429), RoundedCornerShape(8.dp)).padding(20.dp)) { EqualizerPanel() }
            }
            "queue" -> Dialog(onDismissRequest = { sheet = null }) {
                Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).background(Color(0xFF202429), RoundedCornerShape(8.dp)).padding(vertical = 12.dp)) {
                    Text("Playing Queue (${queue.size})", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    LazyColumn {
                        itemsIndexed(queue) { i, f ->
                            Row(Modifier.fillMaxWidth().clickable { player.seekTo(i, 0); player.play(); sheet = null }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.title, color = if (i == index) PlayerAccent else Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(f.artist.ifEmpty { "Unknown" }, color = Dim, fontSize = 12.sp, maxLines = 1)
                                }
                                if (i == index) Icon(Icons.Default.MusicNote, null, Modifier.size(18.dp), tint = PlayerAccent)
                            }
                        }
                    }
                }
            }
            "add" -> file?.let { f -> addToPlaylist?.invoke(f) { sheet = null } }
        }
    }
}
