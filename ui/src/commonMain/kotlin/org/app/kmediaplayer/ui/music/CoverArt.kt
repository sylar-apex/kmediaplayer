package org.app.kmediaplayer.ui.music

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.engine.EngineListener
import org.app.kmediaplayer.engine.EngineState
import kotlin.math.abs

/** Two-colour gradients used as generated cover art; a track always gets the same one. */
private val palettes = listOf(
    Color(0xFF7F5AF0) to Color(0xFF2CB1BC),
    Color(0xFFFF6B6B) to Color(0xFFFFA94D),
    Color(0xFF4C6EF5) to Color(0xFF15AABF),
    Color(0xFFE64980) to Color(0xFF7950F2),
    Color(0xFF12B886) to Color(0xFF228BE6),
    Color(0xFFF59F00) to Color(0xFFE8590C),
    Color(0xFF845EF7) to Color(0xFFF06595),
    Color(0xFF0CA678) to Color(0xFF94D82D),
    Color(0xFF3B5BDB) to Color(0xFF9C36B5),
    Color(0xFFD6336C) to Color(0xFFFF922B),
)

/** The gradient colours for [seed] (a title, album or artist). */
fun coverColors(seed: String): Pair<Color, Color> = palettes[abs(seed.lowercase().hashCode()) % palettes.size]

/** Generated cover art: a gradient tile with a music note, coloured from [seed]. */
@Composable
fun CoverArt(seed: String, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    val (a, b) = coverColors(seed)
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = Offset.Zero)))
        Icon(Icons.Default.MusicNote, null, Modifier.fillMaxSize(0.5f), tint = Color.White.copy(alpha = 0.92f))
    }
}

/** The URI playing in the music session (null when none) and whether it is playing right now. */
@Composable
fun rememberNowPlaying(): Pair<String?, Boolean> {
    val isMusic by KMediaPlayer.isMusicSession.collectAsState()
    val queue by KMediaPlayer.queue.collectAsState()
    val player = KMediaPlayer.engine
    var index by remember { mutableStateOf(player.currentIndex) }
    var playing by remember { mutableStateOf(player.playWhenReady && player.state != EngineState.Ended) }
    DisposableEffect(player) {
        val l = object : EngineListener {
            override fun onEvents() { playing = player.playWhenReady && player.state != EngineState.Ended; index = player.currentIndex }
            override fun onItemTransition() { index = player.currentIndex }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    if (!isMusic) return null to false
    return queue.getOrNull(index)?.uri to playing
}

/** Three bouncing bars marking the track that is playing. */
@Composable
fun PlayingBars(color: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bars")
    val phases = listOf(0, 180, 360).map { delay ->
        t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(520, delayMillis = delay), RepeatMode.Reverse), label = "bar$delay")
    }
    Canvas(modifier) {
        val w = size.width / 5
        phases.forEachIndexed { i, p ->
            val h = size.height * (if (animate) p.value else 0.35f + i * 0.15f)
            drawRoundRect(color, Offset(w * (i * 2), size.height - h), Size(w, h), CornerRadius(w / 2))
        }
    }
}
