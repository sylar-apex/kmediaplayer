package org.app.kmediaplayer.ui.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
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
import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.engine.RepeatMode
import org.app.kmediaplayer.model.MediaFile
import org.app.kmediaplayer.platform.PlatformMedia
import org.app.kmediaplayer.ui.common.edgeToEdgeDialogProperties
import org.app.kmediaplayer.ui.resources.Res
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

private val Bg = Color(0xFF08090D)
private val Dim = Color(0x99FFFFFF)
private val Faint = Color(0x1FFFFFFF)
private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Full-screen music player: generated cover art with a record sliding out while playing, a soft
 * glow in the cover's colours, transport, A-B repeat, speed, equalizer and the queue. Wide windows
 * show the cover and the queue side by side.
 */
@Composable
fun NowPlayingScreen(addToPlaylist: AddToPlaylistSlot? = null, onClose: () -> Unit) {
    val player = KMediaPlayer.engine
    val queue by KMediaPlayer.queue.collectAsState()
    var index by remember { mutableIntStateOf(player.currentIndex) }
    var playing by remember { mutableStateOf(player.playWhenReady && player.state != EngineState.Ended) }
    var shuffle by remember { mutableStateOf(player.shuffleEnabled) }
    var repeat by remember { mutableStateOf(player.repeatMode) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val ab = remember { AbRepeat() }
    val file = queue.getOrNull(index)

    DisposableEffect(player) {
        val l = object : EngineListener {
            override fun onEvents() { playing = player.playWhenReady && player.state != EngineState.Ended }
            override fun onItemTransition() { index = player.currentIndex; ab.clear() }
            override fun onShuffleChanged(enabled: Boolean) { shuffle = enabled }
            override fun onRepeatChanged(mode: RepeatMode) { repeat = mode }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    val seed = file?.album?.takeIf { it.isNotBlank() && it != "Unknown" } ?: file?.title ?: player.title.orEmpty()
    val (c1, c2) = coverColors(seed)
    val glow1 by animateColorAsState(c1, tween(700), label = "glow1")
    val glow2 by animateColorAsState(c2, tween(700), label = "glow2")

    Dialog(onClose, edgeToEdgeDialogProperties()) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Bg)
                .background(Brush.radialGradient(listOf(glow1.copy(alpha = 0.28f), Color.Transparent), center = Offset(0f, 0f), radius = 1400f))
                .background(Brush.radialGradient(listOf(glow2.copy(alpha = 0.16f), Color.Transparent), center = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY), radius = 1600f)),
        ) {
            val wide = maxWidth >= 840.dp && maxHeight >= 480.dp
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Header(file, player, wide, onClose)
                if (wide) {
                    Row(Modifier.fillMaxWidth().weight(1f).padding(start = 40.dp, end = 24.dp, top = 8.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Column(Modifier.widthIn(max = 440.dp)) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Artwork(seed, playing, Modifier.widthIn(max = 330.dp)) }
                                Spacer(Modifier.height(28.dp))
                                Controls(file, player, playing, shuffle, repeat, ab, addToPlaylist != null, onSheet = { sheet = it }, showQueueButton = false)
                            }
                        }
                        QueuePanel(queue, index, playing, player, Modifier.weight(1f).widthIn(max = 520.dp).fillMaxHeight())
                    }
                } else {
                    Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 32.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Artwork(seed, playing, Modifier.widthIn(max = 320.dp))
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                        Controls(file, player, playing, shuffle, repeat, ab, addToPlaylist != null, onSheet = { sheet = it }, showQueueButton = true)
                    }
                }
            }
        }

        when (sheet) {
            "eq" -> Dialog(onDismissRequest = { sheet = null }) {
                Column(Modifier.fillMaxWidth().background(Color(0xF2181A20), RoundedCornerShape(20.dp)).padding(20.dp)) { EqualizerPanel() }
            }
            "queue" -> Dialog(onDismissRequest = { sheet = null }) {
                QueuePanel(queue, index, playing, player, Modifier.fillMaxWidth().heightIn(max = 560.dp), onPicked = { sheet = null })
            }
            "add" -> file?.let { f -> addToPlaylist?.invoke(f) { sheet = null } }
        }
    }
}

/** A-B repeat points; -1 when unset. */
private class AbRepeat {
    var a by mutableLongStateOf(-1L)
    var b by mutableLongStateOf(-1L)
    fun clear() { a = -1; b = -1 }
}

@Composable
private fun Header(file: MediaFile?, player: MediaEngine, wide: Boolean, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.height(38.dp).clip(CircleShape).background(Color(0x14FFFFFF)).clickable(onClick = onClose).padding(start = 10.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(20.dp), tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text("Back", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NOW PLAYING", color = Dim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
            val from = file?.album?.takeIf { it.isNotBlank() && it != "Unknown" } ?: file?.folderName
            if (!from.isNullOrBlank() && !wide) Text(from, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GlassButton(painterResource(Res.drawable.ic_ov_share), "Share") { file?.let { PlatformMedia.share(listOf(it)) } ?: Unit }
    }
}

/** Cover card with a record behind it that slides out and spins while playing. */
@Composable
private fun Artwork(seed: String, playing: Boolean, modifier: Modifier) {
    val spin = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        while (playing) {
            spin.animateTo(spin.value + 360f, tween(6000, easing = LinearEasing))
            spin.snapTo(spin.value % 360f)
        }
    }
    val slide by animateFloatAsState(if (playing) 0.30f else 0.06f, spring(dampingRatio = 0.75f, stiffness = 120f), label = "slide")
    val scale by animateFloatAsState(if (playing) 1f else 0.94f, spring(stiffness = 200f), label = "scale")
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1.25f), contentAlignment = Alignment.CenterStart) {
        val card = minOf(maxHeight, maxWidth / 1.25f) * scale
        val (a, _) = coverColors(seed)
        Box(Modifier.offset(x = card * slide).size(card * 0.96f).rotate(spin.value), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(Color(0xFF050505), r)
                for (k in 0..14) drawCircle(Color(0xFF1C1C1F), r * (0.42f + k * 0.038f), style = Stroke(0.8.dp.toPx()))
                drawCircle(Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent)), r)
                drawCircle(a, r * 0.30f)
                drawCircle(Color(0xFF050505), r * 0.04f)
            }
        }
        CoverArt(
            seed,
            Modifier.size(card).shadow(32.dp, RoundedCornerShape(22.dp), ambientColor = a, spotColor = a),
            corner = 22.dp,
        )
    }
}

@Composable
private fun Controls(
    file: MediaFile?, player: MediaEngine, playing: Boolean, shuffle: Boolean, repeat: RepeatMode, ab: AbRepeat,
    canAddToPlaylist: Boolean, onSheet: (String) -> Unit, showQueueButton: Boolean,
) {
    val host = KMediaPlayer.host
    val favourites by remember { host.favourites() }.collectAsState(emptySet())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(file?.title ?: player.title.orEmpty(), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(file?.artist?.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Unknown artist", color = Dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val fav = file != null && file.uri in favourites
        IconButton({ file?.let { f -> host.toggleFavourite(f.uri) } }) {
            Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favourite", tint = if (fav) Color(0xFFFF4D6D) else Color.White)
        }
    }
    Spacer(Modifier.height(18.dp))
    SeekBar(player, ab)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton({ player.shuffleEnabled = !shuffle }) { ToggleIcon(painterResource(Res.drawable.ic_pl_shuffle), "Shuffle", shuffle) }
        IconButton({ player.seekToPrevious() }, Modifier.size(56.dp)) { Icon(painterResource(Res.drawable.ic_pl_prev), "Previous", Modifier.size(26.dp), tint = Color.White) }
        val press by animateDpAsState(if (playing) 62.dp else 60.dp, label = "play")
        Box(
            Modifier.size(press).shadow(10.dp, CircleShape, spotColor = Color.White).clip(CircleShape).background(Color.White).clickable { player.togglePlayback() },
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(if (playing) Res.drawable.ic_pl_pause else Res.drawable.ic_pl_play), if (playing) "Pause" else "Play", Modifier.size(28.dp), tint = Bg) }
        IconButton({ player.seekToNext() }, Modifier.size(56.dp)) { Icon(painterResource(Res.drawable.ic_pl_next), "Next", Modifier.size(26.dp), tint = Color.White) }
        IconButton({
            player.repeatMode = when (repeat) { RepeatMode.Off -> RepeatMode.All; RepeatMode.All -> RepeatMode.One; else -> RepeatMode.Off }
        }) { ToggleIcon(painterResource(if (repeat == RepeatMode.One) Res.drawable.ic_pl_loop_one else Res.drawable.ic_pl_loop), "Repeat", repeat != RepeatMode.Off) }
    }
    Spacer(Modifier.height(18.dp))
    ActionBar(file, player, ab, canAddToPlaylist, onSheet, showQueueButton)
}

/** Icon that turns accent-coloured with a dot underneath when on. */
@Composable
private fun ToggleIcon(painter: Painter, label: String, on: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painter, label, Modifier.size(22.dp), tint = if (on) PlayerAccent else Dim)
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(4.dp).background(if (on) PlayerAccent else Color.Transparent, CircleShape))
    }
}

@Composable
private fun SeekBar(player: MediaEngine, ab: AbRepeat) {
    var pos by remember { mutableLongStateOf(player.position) }
    var dur by remember { mutableLongStateOf(player.duration) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(player) {
        while (true) {
            pos = player.position
            dur = player.duration
            if (ab.a >= 0 && ab.b > ab.a && pos >= ab.b) player.seekTo(ab.a)
            delay(250)
        }
    }
    val frac = dragging ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val durNow by rememberUpdatedState(dur)
    val thumb by animateDpAsState(if (dragging != null) 8.dp else 6.dp, label = "thumb")
    fun commit(f: Float) { val t = (f * durNow).toLong(); pos = t; player.seekTo(t) }
    Canvas(
        Modifier.fillMaxWidth().height(28.dp)
            .pointerInput(Unit) { detectTapGestures { o -> commit((o.x / size.width).coerceIn(0f, 1f)) } }
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
        val stroke = 4.dp.toPx()
        drawLine(Faint, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        if (dur > 0 && ab.a >= 0) {
            val end = if (ab.b > ab.a) ab.b else ab.a
            drawLine(PlayerAccent.copy(alpha = 0.35f), Offset(size.width * ab.a / dur, y), Offset((size.width * end / dur).coerceAtLeast(size.width * ab.a / dur + 2), y), stroke * 2, StrokeCap.Round)
        }
        drawLine(Color.White, Offset(0f, y), Offset(x, y), stroke, StrokeCap.Round)
        drawCircle(Color.White, thumb.toPx(), Offset(x, y))
    }
    Row(Modifier.fillMaxWidth()) {
        Text(formatDuration(dragging?.let { (it * dur).toLong() } ?: pos), color = Dim, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(if (dur > 0) "-" + formatDuration(dur - pos) else "--:--", color = Dim, fontSize = 12.sp)
    }
}

/** Equalizer, A-B repeat, speed, lyrics, queue and more, as a row of glass pills. */
@Composable
private fun ActionBar(file: MediaFile?, player: MediaEngine, ab: AbRepeat, canAddToPlaylist: Boolean, onSheet: (String) -> Unit, showQueueButton: Boolean) {
    var speedMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x0FFFFFFF)).padding(horizontal = 4.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton({ onSheet("eq") }) { Icon(painterResource(Res.drawable.ic_pl_eq), "Equalizer", Modifier.size(22.dp), tint = Color.White) }
        IconButton({
            when {
                ab.a < 0 -> { ab.a = player.position; PlatformMedia.showToast("A point set") }
                ab.b < 0 -> { ab.b = player.position.coerceAtLeast(ab.a + 500); PlatformMedia.showToast("A-B repeat on") }
                else -> { ab.clear(); PlatformMedia.showToast("A-B repeat off") }
            }
        }) { Text("A-B", color = if (ab.a >= 0) PlayerAccent else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Box {
            IconButton({ speedMenu = true }) {
                if (player.speed != 1f) Text("${player.speed}x", color = PlayerAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                else Icon(Icons.Default.Speed, "Speed", Modifier.size(22.dp), tint = Color.White)
            }
            DropdownMenu(speedMenu, { speedMenu = false }) {
                speeds.forEach { s -> DropdownMenuItem({ Text("${s}x") }, { speedMenu = false; player.speed = s }) }
            }
        }
        IconButton({ PlatformMedia.showToast("No lyrics found") }) { Icon(Icons.Default.Lyrics, "Lyrics", Modifier.size(22.dp), tint = Color.White) }
        if (showQueueButton) IconButton({ onSheet("queue") }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Playing queue", Modifier.size(22.dp), tint = Color.White) }
        Box {
            IconButton({ moreMenu = true }) { Icon(Icons.Default.MoreHoriz, "More", Modifier.size(22.dp), tint = Color.White) }
            DropdownMenu(moreMenu, { moreMenu = false }) {
                if (canAddToPlaylist) DropdownMenuItem({ Text("Add to Playlist") }, { moreMenu = false; onSheet("add") })
                DropdownMenuItem({ Text("Share") }, { moreMenu = false; file?.let { PlatformMedia.share(listOf(it)) } })
            }
        }
    }
}

@Composable
private fun QueuePanel(queue: List<MediaFile>, index: Int, playing: Boolean, player: MediaEngine, modifier: Modifier, onPicked: () -> Unit = {}) {
    val list = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    val shown = remember(queue, query) {
        queue.withIndex().filter { (_, f) -> query.isBlank() || f.title.contains(query, true) || f.artist.contains(query, true) || f.album.contains(query, true) }
    }
    LaunchedEffect(index) { if (query.isBlank() && index in queue.indices) list.animateScrollToItem((index - 2).coerceAtLeast(0)) }
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x99101216)).padding(top = 16.dp, bottom = 8.dp)) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Up next", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${queue.size} tracks", color = Dim, fontSize = 12.sp)
        }
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth().height(38.dp).clip(CircleShape).background(Color(0x14FFFFFF)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(16.dp), tint = Dim)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search in queue", color = Dim, fontSize = 13.sp)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
            }
            if (query.isNotEmpty()) Icon(Icons.Default.Close, "Clear", Modifier.size(16.dp).clip(CircleShape).clickable { query = "" }, tint = Dim)
        }
        if (shown.isEmpty()) Text("No matching tracks", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(18.dp))
        LazyColumn(state = list) {
            items(shown, key = { it.index }) { (i, f) ->
                val current = i == index
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (current) Color(0x17FFFFFF) else Color.Transparent)
                        .clickable { player.seekTo(i, 0); player.play(); onPicked() }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CoverArt(f.album.takeIf { it.isNotBlank() && it != "Unknown" } ?: f.title, Modifier.size(38.dp), corner = 7.dp)
                        if (current) Box(Modifier.size(38.dp).background(Color(0x99000000), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                            PlayingBars(Color.White, playing, Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.title, color = if (current) PlayerAccent else Color.White, fontSize = 13.sp, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(f.artist.takeIf { it.isNotBlank() && it != "Unknown" } ?: "Unknown artist", color = Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (f.durationMs > 0) Text(formatDuration(f.durationMs), color = Dim, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun GlassButton(painter: Painter, label: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x1FFFFFFF)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painter, label, Modifier.size(22.dp), tint = Color.White)
    }
}
