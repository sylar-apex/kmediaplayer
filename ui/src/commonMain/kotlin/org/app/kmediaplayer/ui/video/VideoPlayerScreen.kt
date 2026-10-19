package org.app.kmediaplayer.ui.video

import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import kotlin.math.exp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.engine.Decoder
import org.app.kmediaplayer.logic.GestureKind
import org.app.kmediaplayer.logic.Gestures
import org.app.kmediaplayer.logic.LoopMode
import org.app.kmediaplayer.logic.Size
import org.app.kmediaplayer.logic.Speed
import org.app.kmediaplayer.logic.fitVideo
import org.app.kmediaplayer.platform.PlatformMedia
import org.app.kmediaplayer.settings.PlayerKeys
import org.app.kmediaplayer.settings.Shortcut
import org.app.kmediaplayer.ui.common.PlayerDialog
import org.app.kmediaplayer.ui.resources.Res
import org.app.kmediaplayer.ui.resources.ic_back
import org.app.kmediaplayer.ui.resources.ic_chevron_left
import org.app.kmediaplayer.ui.resources.ic_chevron_right
import org.app.kmediaplayer.ui.resources.ic_close
import org.app.kmediaplayer.ui.resources.ic_more_vert
import org.app.kmediaplayer.ui.resources.ic_osd_volume
import org.app.kmediaplayer.ui.resources.ic_pl_ab
import org.app.kmediaplayer.ui.resources.ic_pl_audio
import org.app.kmediaplayer.ui.resources.ic_pl_bg
import org.app.kmediaplayer.ui.resources.ic_pl_brightness
import org.app.kmediaplayer.ui.resources.ic_pl_edit
import org.app.kmediaplayer.ui.resources.ic_pl_eq
import org.app.kmediaplayer.ui.resources.ic_pl_ff
import org.app.kmediaplayer.ui.resources.ic_pl_lock_filled
import org.app.kmediaplayer.ui.resources.ic_pl_loop
import org.app.kmediaplayer.ui.resources.ic_pl_loop_one
import org.app.kmediaplayer.ui.resources.ic_pl_mute
import org.app.kmediaplayer.ui.resources.ic_pl_next
import org.app.kmediaplayer.ui.resources.ic_pl_night
import org.app.kmediaplayer.ui.resources.ic_pl_pause
import org.app.kmediaplayer.ui.resources.ic_pl_pip
import org.app.kmediaplayer.ui.resources.ic_pl_play
import org.app.kmediaplayer.ui.resources.ic_pl_prev
import org.app.kmediaplayer.ui.resources.ic_pl_rew
import org.app.kmediaplayer.ui.resources.ic_pl_rotate
import org.app.kmediaplayer.ui.resources.ic_pl_shot
import org.app.kmediaplayer.ui.resources.ic_pl_shuffle
import org.app.kmediaplayer.ui.resources.ic_pl_sleep
import org.app.kmediaplayer.ui.resources.ic_pl_subs
import org.app.kmediaplayer.ui.resources.ic_pl_unlock
import org.app.kmediaplayer.ui.resources.ic_pl_volume
import org.app.kmediaplayer.ui.resources.ic_pl_zoom
import org.app.kmediaplayer.ui.settings.settingsVersion
import org.app.kmediaplayer.ui.theme.PlayerAccent
import org.app.kmediaplayer.util.formatDecimal
import org.app.kmediaplayer.util.formatDuration
import org.app.kmediaplayer.util.formatRemaining
import org.app.kmediaplayer.util.nowMs
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

/** The full-screen video player: surface, gestures, controls, shortcuts, panels and dialogs. */
@Composable
fun VideoPlayerScreen(window: PlayerWindow) {
    val cutouts = remember { if (videoNeedsCutouts) VideoCutouts() else null }
    CompositionLocalProvider(LocalVideoCutouts provides cutouts) { VideoPlayerContent(window) }
}

@Composable
private fun VideoPlayerContent(window: PlayerWindow) {
    val st = window.state
    settingsVersion()
    val s = KMediaPlayer.settings
    // Window-level input (trackpad pinch on macOS) zooms whichever player is on screen.
    DisposableEffect(st) { ZoomInput.target = st; onDispose { if (ZoomInput.target === st) ZoomInput.target = null } }
    LaunchedEffect(st.finishRequested) { if (st.finishRequested) window.close() }
    LaunchedEffect(st.controlsVisible, st.panel, st.locked) { window.showSystemBars(st.controlsVisible && !st.locked && (st.panel == Panel.None || st.panel == Panel.AbRepeat)) }
    LaunchedEffect(st.panel) { if (st.panel == Panel.None) st.scheduleHide() }
    LaunchedEffect(st.snack) { if (st.snack != null) { delay(3000); st.snack = null } }
    LaunchedEffect(st.toast) { if (st.toast != null) { delay(1500); st.toast = null } }
    // Level and seek badges hide shortly after their last change, whether a gesture or a key set them.
    LaunchedEffect(st.levelOsd) { if (st.levelOsd != null) { delay(1500); st.levelOsd = null } }
    LaunchedEffect(st.tapSeekOsd) { if (st.tapSeekOsd != null) { delay(1500); st.tapSeekOsd = null } }
    // Messages from platform code (desktop, web) shown like the player's own toasts.
    LaunchedEffect(Unit) { KMediaPlayer.notices.collect { msg -> if (msg != null) { st.toast = msg; KMediaPlayer.notices.value = null } } }
    LaunchedEffect(st.videoW, st.videoH) { if (st.landscape == null) window.applyOrientation() }

    val keyboard = remember { FocusRequester() }
    if (PlayerCapabilities.keyboardShortcuts) LaunchedEffect(Unit) { runCatching { keyboard.requestFocus() } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)
        .let { m ->
            if (!PlayerCapabilities.keyboardShortcuts) m
            else m.focusRequester(keyboard).focusable().onPreviewKeyEvent { PlayerKeyboard.handle(it, window) }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (st.controlsVisible) st.scheduleHide()
                // Clicking buttons can move focus away; bring it back so shortcuts keep working.
                if (PlayerCapabilities.keyboardShortcuts) runCatching { keyboard.requestFocus() }
            }
        }) {
        val density = LocalDensity.current
        val cw = with(density) { maxWidth.toPx() }
        val ch = with(density) { maxHeight.toPx() }
        val fitted = fitVideo(Size(cw, ch), Size(st.videoW.toFloat(), st.videoH.toFloat()), st.zoom, st.aspectValue())
        val vw = with(density) { fitted.width.toDp() }
        val vh = with(density) { fitted.height.toDp() }

        // The web video is an HTML element over the canvas: it zooms itself, since a Compose layer cannot scale it.
        LaunchedEffect(st.pinchScale, st.panX, st.panY) { PlatformVideoTools.setPictureTransform(st.pinchScale, st.panX, st.panY) }
        window.VideoSurface(
            Modifier.align(Alignment.Center).size(vw, vh).let { m ->
                if (videoNeedsCutouts) m
                else m.graphicsLayer { scaleX = st.pinchScale; scaleY = st.pinchScale; translationX = st.panX; translationY = st.panY }
            },
        )

        if (st.nightMode) Box(Modifier.fillMaxSize().background(Color(0x99000000)))

        SubtitleLayer(st, Modifier.align(Alignment.BottomCenter), fitted, ch, if (window.inPip) 0.4f else 1f)

        GestureLayer(window, cw, ch)

        if (st.buffering && s[PlayerKeys.loadingCircle]) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)

        if (window.inPip) return@BoxWithConstraints

        if (st.locked) {
            LockOverlay(st)
        } else {
            AnimatedVisibility(st.controlsVisible && (st.panel == Panel.None || st.panel == Panel.AbRepeat), enter = fadeIn(), exit = fadeOut()) { Controls(window, portrait = ch > cw) }
            if (!st.controlsVisible) PersistentOsd(st, Modifier.align(if (s[PlayerKeys.osdBottom]) Alignment.BottomStart else Alignment.TopStart))
        }

        st.tapSeekOsd?.let { (dir, secs) -> TapSeekOsd(dir, secs) }
        st.pillOsd?.let { (icon, text) -> OsdPill(icon, text, if (text.endsWith("Speed Playing")) Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 28.dp) else Modifier.align(Alignment.Center)) }
        st.levelOsd?.let { LevelPill(it, Modifier.align(if (it.brightness) Alignment.CenterEnd else Alignment.CenterStart)) }
        st.seekOsd?.let { (target, offset) -> SeekOsd(target, offset, Modifier.align(Alignment.Center)) }
        st.gestureLabel?.let { label ->
            Text(label, color = Color(s[PlayerKeys.osdTextColor]), fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).videoCutout().background(if (s[PlayerKeys.osdBackground]) Color(s[PlayerKeys.osdBackColor]) else Color.Transparent, RoundedCornerShape(10.dp)).padding(horizontal = 22.dp, vertical = 12.dp))
        }
        st.toast?.let { msg ->
            Text(msg, color = Color.White, fontSize = 15.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp).videoCutout().background(Color(0xCC222222), RoundedCornerShape(6.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
        }

        st.snack?.let { msg ->
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().safeDrawingPadding().padding(bottom = 25.dp).height(28.dp).videoCutout().background(Color(0xCC000000)).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                Text(msg, color = Color.White, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (st.panel != Panel.None) PanelHost(window)

        st.resumePrompt?.let { pos ->
            PlayerDialog(
                onDismissRequest = { st.answerResume(false) },
                containerColor = Color(0xFF2B3640), shape = RoundedCornerShape(24.dp),
                text = { Text("Do you wish to resume from where you stopped? (${formatDuration(pos)})", color = Color(0xFFC3CAD3), fontSize = 15.sp, lineHeight = 22.sp) },
                confirmButton = { TextButton({ st.answerResume(true) }) { Text("RESUME") } },
                dismissButton = { TextButton({ st.answerResume(false) }) { Text("START OVER") } },
            )
        }
        st.error?.let { err ->
            if (err.needsFileAccess) FileAccessDialog(window) else PlayerDialog(
                onDismissRequest = { st.error = null },
                text = { Text("Can't play this media.\n${err.codeName}") },
                confirmButton = { TextButton({ st.error = null; if (st.engine.hasNext()) st.next() else window.close() }) { Text("OK") } },
                dismissButton = if (err.isIo || st.decoder == Decoder.SW) null else ({ TextButton({ st.error = null; st.setDecoderMode(Decoder.SW) }) { Text("TRY SW DECODER") } }),
            )
        }
    }
}

/** Shown when the OS blocks reading the file, typically a type the media scanner does not know. */
@Composable
private fun FileAccessDialog(window: PlayerWindow) {
    val st = window.state
    PlayerDialog(
        onDismissRequest = { st.error = null },
        text = { Text("This file type needs \"All files access\". Allow it so Max Player can open every video format on your device.") },
        confirmButton = { TextButton({ st.error = null; if (!window.requestFileAccess()) window.close() }) { Text("ALLOW") } },
        dismissButton = { TextButton({ st.error = null; window.close() }) { Text("CANCEL") } },
    )
}

@Composable
private fun SubtitleLayer(st: VideoPlayerState, modifier: Modifier, fitted: Size, containerH: Float, windowScale: Float) {
    settingsVersion()
    val s = KMediaPlayer.settings
    val lines = st.visibleSubtitleLines()
    if (lines.isEmpty()) return
    val density = LocalDensity.current
    val videoBottomGap = if (s[PlayerKeys.subFitToVideo]) with(density) { ((containerH - fitted.height).coerceAtLeast(0f) / 2).toDp() } else 0.dp
    val size = (s[PlayerKeys.subSize] * s[PlayerKeys.subScale] / 100f * st.subtitleZoom * windowScale).sp
    val family = when (s[PlayerKeys.subFont]) { "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; "sans" -> FontFamily.SansSerif; else -> FontFamily.Default }
    val align = when (s[PlayerKeys.subAlignment]) { "left" -> TextAlign.Left; "right" -> TextAlign.Right; else -> TextAlign.Center }
    val shadow = when {
        s[PlayerKeys.subBorder] -> Shadow(Color(s[PlayerKeys.subBorderColor]), Offset.Zero, s[PlayerKeys.subBorderThickness] / 15f)
        s[PlayerKeys.subShadow] -> Shadow(Color.Black, Offset(2f, 2f), 4f)
        else -> null
    }
    Column(
        modifier.fillMaxWidth()
            .padding(bottom = videoBottomGap + (s[PlayerKeys.subBottomMargin] * 2).dp, start = 24.dp, end = 24.dp)
            .graphicsLayer { translationY = st.subtitleOffsetY }
            // With a full-width background the whole block covers the video; otherwise only each line does.
            .let { if (s[PlayerKeys.subBackground]) it.videoCutout().background(Color(s[PlayerKeys.subBackgroundColor])) else it },
        horizontalAlignment = when (align) { TextAlign.Left -> Alignment.Start; TextAlign.Right -> Alignment.End; else -> Alignment.CenterHorizontally },
    ) {
        lines.forEach { line ->
            Text(
                line.text,
                Modifier.videoCutout(),
                style = TextStyle(
                    color = Color(s[PlayerKeys.subColor]), fontSize = size, fontFamily = family, textAlign = align,
                    fontWeight = if (s[PlayerKeys.subBold]) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (line.italic) FontStyle.Italic else FontStyle.Normal,
                    shadow = shadow,
                    textDirection = if (s[PlayerKeys.forceLtr]) TextDirection.Ltr else TextDirection.Content,
                    background = if (s[PlayerKeys.subTextBackground]) Color(s[PlayerKeys.subTextBackgroundColor]) else Color.Unspecified,
                ),
            )
        }
    }
}

/** Single-finger seek/volume/brightness drags, pinch zoom + pan, taps, double taps and long-press fast-forward. */
@Composable
private fun GestureLayer(window: PlayerWindow, width: Float, height: Float) {
    val st = window.state
    val s = KMediaPlayer.settings
    val scope = rememberCoroutineScope()
    val pxPerCm = window.pxPerCm
    val longPressMs = LocalViewConfiguration.current.longPressTimeoutMillis
    var lastTapAt by remember { mutableStateOf(0L) }
    var pendingTap by remember { mutableStateOf<Job?>(null) }
    var tapSeekJob by remember { mutableStateOf<Job?>(null) }

    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        // Mouse wheel (and trackpad two-finger scroll): wheel up zooms in, wheel down zooms out.
        awaitPointerEventScope {
            while (true) {
                val ev = awaitPointerEvent()
                if (ev.type != PointerEventType.Scroll) continue
                val dy = ev.changes.fold(0f) { acc, c -> acc + c.scrollDelta.y }
                if (dy != 0f) {
                    // Capped per event so a burst of large deltas (fast wheel spin) cannot jump straight to 25 % or 400 %.
                    st.zoomBy(exp(-dy * wheelZoomStep).coerceIn(0.8f, 1.25f))
                    ev.changes.forEach { it.consume() }
                }
            }
        }
    }.pointerInput(width, height) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = down.position
            val startedAt = nowMs()
            var kind = GestureKind.None
            var total = Offset.Zero
            var multi = false
            var longPressed = false
            var seekBase = st.engine.position
            var levelBase = 0
            val inSubtitleZone = start.y > height * 0.72f
            var subtitleDrag = false

            if (st.locked) {
                do { val ev = awaitPointerEvent() } while (ev.changes.any { it.pressed })
                st.lockTapCount++
                if (s[PlayerKeys.lockShowInterface]) st.showControls()
                return@awaitEachGesture
            }

            while (true) {
                val ev = awaitPointerEvent()
                val pressed = ev.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    multi = true
                    val zoomChange = ev.calculateZoom()
                    val pan = ev.calculatePan()
                    if (inSubtitleZone && s[PlayerKeys.gSubtitleZoom]) {
                        st.subtitleZoom = (st.subtitleZoom * zoomChange).coerceIn(0.5f, 3f)
                    } else {
                        if (s[PlayerKeys.gZoom] || s[PlayerKeys.gZoomAndPan]) st.pinchScale = (st.pinchScale * zoomChange).coerceIn(0.25f, 4f)
                        if (s[PlayerKeys.gPan] || s[PlayerKeys.gZoomAndPan]) { st.panX += pan.x; st.panY += pan.y }
                        st.gestureLabel = "${(st.pinchScale * 100).toInt()}%"
                    }
                    ev.changes.forEach { it.consume() }
                    continue
                }
                if (multi) continue
                val change = pressed.first()
                total += change.positionChange()
                if (!longPressed && kind == GestureKind.None && !subtitleDrag && total.getDistance() < Gestures.SLOP_PX &&
                    nowMs() - startedAt > longPressMs && s[PlayerKeys.gLongPressSpeed] && st.isPlaying) {
                    longPressed = true
                    st.setTemporarySpeed(s[PlayerKeys.longPressSpeed])
                    st.pillOsd = Res.drawable.ic_pl_ff to "${formatDecimal(s[PlayerKeys.longPressSpeed], 1)}X Speed Playing"
                }
                if (longPressed) continue
                if (kind == GestureKind.None && !subtitleDrag) {
                    if (inSubtitleZone && s[PlayerKeys.gSubtitleUpDown] && abs(total.y) > Gestures.SLOP_PX && abs(total.y) > abs(total.x) && st.visibleSubtitleLines().isNotEmpty()) {
                        subtitleDrag = true
                    } else {
                        kind = Gestures.classify(total.x, total.y, start.x, width, s[PlayerKeys.gSeek], s[PlayerKeys.gVolume], s[PlayerKeys.gBrightness])
                        when (kind) {
                            GestureKind.Seek -> seekBase = st.engine.position
                            GestureKind.Volume -> levelBase = st.volume.level
                            GestureKind.Brightness -> levelBase = st.brightness
                            else -> {}
                        }
                    }
                }
                if (subtitleDrag) { st.subtitleOffsetY += change.positionChange().y; change.consume(); continue }
                when (kind) {
                    GestureKind.Seek -> {
                        val offset = Gestures.seekOffsetMs(total.x, pxPerCm, s[PlayerKeys.seekSpeed])
                        val target = (seekBase + offset).coerceIn(0, st.duration)
                        st.seekOsd = target to offset
                        if (!st.controlsVisible) st.showControls()
                        if (s[PlayerKeys.displaySeekPosition]) st.position = target
                    }
                    GestureKind.Volume -> {
                        val target = levelBase + Gestures.levelDelta(total.y, height, st.volume.systemMax)
                        st.adjustVolume(target - st.volume.level)
                        val v = st.volume
                        st.levelOsd = LevelOsd(false, v.percent, v.level.toFloat() / v.maxLevel.coerceAtLeast(1), if (v.boostEnabled) 0.5f else null)
                    }
                    GestureKind.Brightness -> {
                        val target = (levelBase + Gestures.levelDelta(total.y, height, 30)).coerceIn(0, 30)
                        st.brightness = target
                        window.setBrightness(target)
                        st.levelOsd = LevelOsd(true, target, target / 30f, null)
                    }
                    else -> {}
                }
                if (kind != GestureKind.None) change.consume()
            }

            if (longPressed) { st.setTemporarySpeed(null); st.pillOsd = null; return@awaitEachGesture }
            if (multi) {
                if (st.pinchScale in 0.95f..1.05f) st.pinchScale = 1f
                scope.launch { delay(600); st.gestureLabel = null }
                return@awaitEachGesture
            }
            when (kind) {
                GestureKind.Seek -> {
                    st.seekTo(seekBase + Gestures.seekOffsetMs(total.x, pxPerCm, s[PlayerKeys.seekSpeed]))
                    scope.launch { delay(400); st.seekOsd = null }
                    return@awaitEachGesture
                }
                GestureKind.Volume, GestureKind.Brightness -> {
                    if (kind == GestureKind.Brightness) s[PlayerKeys.brightnessLevel] = st.brightness
                    scope.launch { delay(600); st.levelOsd = null }
                    return@awaitEachGesture
                }
                else -> {}
            }
            if (subtitleDrag) return@awaitEachGesture

            val now = nowMs()
            val doubleEnabled = s[PlayerKeys.gDoubleTapPlayPause] || s[PlayerKeys.gDoubleTapFfRw] || s[PlayerKeys.gDoubleTapZoom]
            if (doubleEnabled && now - lastTapAt < 300) {
                pendingTap?.cancel()
                lastTapAt = 0
                when (Gestures.doubleTapZone(start.x, width)) {
                    0 -> when {
                        s[PlayerKeys.gDoubleTapZoom] -> st.cycleZoom()
                        s[PlayerKeys.gDoubleTapPlayPause] -> { st.togglePlay(); flashPill(st, scope, if (st.engine.playWhenReady) Res.drawable.ic_pl_play to "Playing" else Res.drawable.ic_pl_pause to "Paused") }
                    }
                    else -> if (s[PlayerKeys.gDoubleTapFfRw]) {
                        val dir = Gestures.doubleTapZone(start.x, width)
                        st.seekBy(dir * s[PlayerKeys.moveInterval] * 1000L)
                        val prev = st.tapSeekOsd?.takeIf { it.first == dir }?.second ?: 0
                        st.tapSeekOsd = dir to prev + s[PlayerKeys.moveInterval]
                        tapSeekJob?.cancel()
                        tapSeekJob = scope.launch { delay(700); st.tapSeekOsd = null }
                    } else if (s[PlayerKeys.gDoubleTapPlayPause]) st.togglePlay()
                }
            } else {
                lastTapAt = now
                val action = {
                    when (s[PlayerKeys.touchAction]) {
                        "pause" -> st.togglePlay()
                        "both" -> { st.togglePlay(); st.showControls() }
                        else -> { if (st.panel != Panel.None) st.panel = Panel.None else st.toggleControls() }
                    }
                }
                pendingTap?.cancel()
                pendingTap = if (doubleEnabled) scope.launch { delay(300); action() } else { action(); null }
            }
        }
    })
}

private var pillJob: Job? = null

private fun flashPill(st: VideoPlayerState, scope: CoroutineScope, value: Pair<DrawableResource, String>) {
    st.pillOsd = value
    pillJob?.cancel()
    pillJob = scope.launch { delay(800); st.pillOsd = null }
}

@Composable
private fun OsdPill(icon: DrawableResource, text: String, modifier: Modifier) {
    Row(modifier.videoCutout().height(30.dp).background(Color(0xD91E2A33), RoundedCornerShape(50)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(14.dp), tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TapSeekOsd(dir: Int, seconds: Int) {
    // Only a small, soft badge on the seek side: no shaded half-circle, so fast repeated seeks never flash the picture.
    Box(Modifier.fillMaxSize().padding(horizontal = 48.dp), contentAlignment = if (dir > 0) Alignment.CenterEnd else Alignment.CenterStart) {
        Row(
            Modifier.videoCutout().background(Color(0x59000000), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(if (dir > 0) Res.drawable.ic_pl_ff else Res.drawable.ic_pl_rew), null, Modifier.size(14.dp), tint = Color(0xE6FFFFFF))
            Spacer(Modifier.width(6.dp))
            Text("${seconds}s", color = Color(0xE6FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LevelPill(osd: LevelOsd, modifier: Modifier) {
    val blue = PlayerAccent
    Column(
        modifier.safeDrawingPadding().padding(start = 16.dp, end = 32.dp).size(33.dp, 160.dp).videoCutout()
            .background(Color(0xBF1E2229), RoundedCornerShape(50)).padding(top = 8.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${osd.value}", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        Canvas(Modifier.padding(top = 6.dp, bottom = 8.dp).width(4.dp).weight(1f)) {
            val w = size.width
            drawRoundRect(Color(0x66FFFFFF), cornerRadius = CornerRadius(w / 2))
            val f = osd.fraction.coerceIn(0f, 1f)
            if (f > 0f) {
                val top = size.height * (1 - f)
                val boostY = osd.boostFrom?.let { size.height * (1 - it) }
                val brush = if (boostY != null && top < boostY)
                    Brush.verticalGradient(listOf(Color(0xFFFFA726), blue), startY = top, endY = boostY + (boostY - top))
                else SolidColor(blue)
                drawRoundRect(brush, Offset(0f, top), androidx.compose.ui.geometry.Size(w, size.height - top), CornerRadius(w / 2))
            }
        }
        Icon(painterResource(if (osd.brightness) Res.drawable.ic_pl_brightness else Res.drawable.ic_osd_volume), null, Modifier.size(22.dp), tint = Color.White)
    }
}

@Composable
private fun SeekOsd(target: Long, offset: Long, modifier: Modifier) {
    val style = TextStyle(color = Color(0xFFF2F2F2), fontSize = 46.sp, fontWeight = FontWeight.SemiBold, shadow = Shadow(Color(0xCC000000), Offset(0f, 2f), 8f), textAlign = TextAlign.Center)
    Column(modifier.videoCutout(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatDuration(target), style = style, lineHeight = 52.sp)
        Text("[${if (offset >= 0) "+" else "-"}${formatDuration(abs(offset))}]", style = style.copy(fontSize = 40.sp), lineHeight = 48.sp)
    }
}

@Composable
private fun LockOverlay(st: VideoPlayerState) {
    val kids = KMediaPlayer.settings[PlayerKeys.lockMode] == "kids"
    var shown by remember { mutableStateOf(true) }
    LaunchedEffect(st.lockTapCount) { shown = true; delay(2500); shown = false }
    if (!shown) return
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(
            Modifier.padding(start = 59.dp, top = 0.dp).size(44.dp).videoCutout()
                .combinedClickable(interactionSource = null, indication = null,
                    onClick = { if (!kids) { st.locked = false; st.showControls() } else st.toast = "Long press to unlock" },
                    onLongClick = { st.locked = false; st.showControls() }),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(Res.drawable.ic_pl_lock_filled), "Unlock", Modifier.size(24.dp), tint = Color.White) }
    }
}

@Composable
private fun PersistentOsd(st: VideoPlayerState, modifier: Modifier) {
    val s = KMediaPlayer.settings
    val parts = buildList {
        if (s[PlayerKeys.alwaysShowElapsed]) add(formatDuration(st.position))
        if (s[PlayerKeys.alwaysShowStatus]) add(clockAndBattery())
    }
    if (parts.isEmpty()) return
    val pad = if (s[PlayerKeys.cornerOffsetEnabled]) (s[PlayerKeys.cornerOffset] + 12).dp else 12.dp
    Text(parts.joinToString("   "), modifier.safeDrawingPadding().padding(pad).videoCutout()
        .background(if (s[PlayerKeys.osdBackground]) Color(s[PlayerKeys.osdBackColor]) else Color.Transparent, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        color = Color(s[PlayerKeys.osdTextColor]), fontSize = 13.sp)
}

private fun clockAndBattery(): String {
    val pct = PlatformMedia.batteryPercent()
    return PlatformMedia.formatDate(nowMs(), "HH:mm") + (pct?.let { "  🔋$it%" } ?: "")
}

@Composable
private fun Ic(res: DrawableResource, desc: String?, tint: Color, size: Dp = 24.dp) =
    Icon(painterResource(res), desc, Modifier.size(size), tint = tint)

@Composable
private fun CtlButton(onClick: () -> Unit, width: Dp, height: Dp, content: @Composable () -> Unit) {
    Box(Modifier.size(width, height).clickable(interactionSource = null, indication = ripple(bounded = false, radius = 24.dp), onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Controls(window: PlayerWindow, portrait: Boolean) {
    val st = window.state
    val s = KMediaPlayer.settings
    val tint = Color(s[PlayerKeys.controlNormalColor])
    val highlight = Color(s[PlayerKeys.controlHighlightColor])
    val frame = Color(s[PlayerKeys.frameColor])
    val transportWidth = if (portrait) 50.dp else 90.dp
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().videoCutout().background(Brush.verticalGradient(listOf(frame, Color.Transparent))).safeDrawingPadding()
            // Room for window controls drawn over the player (macOS traffic lights).
            .padding(top = org.app.kmediaplayer.ui.music.LocalWindowTopInset.current)) {
            Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                CtlButton({ window.close() }, 56.dp, 56.dp) { Ic(Res.drawable.ic_back, "Navigate up", tint) }
                Spacer(Modifier.width(16.dp))
                Text(st.title, Modifier.weight(1f), color = tint, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s[PlayerKeys.batteryClockInTitle]) Text(clockAndBattery(), color = tint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
                Spacer(Modifier.width(12.dp))
                if (PlayerCapabilities.audioTrackSelection) CtlButton({ st.panel = Panel.Audio }, 48.dp, 48.dp) { Ic(Res.drawable.ic_pl_audio, "Audio Track", tint) }
                CtlButton({ st.panel = Panel.Subtitle }, 48.dp, 48.dp) { Ic(Res.drawable.ic_pl_subs, "Subtitles", tint) }
                if (PlayerCapabilities.decoderChoice) CtlButton({ st.panel = Panel.Decoder }, 48.dp, 48.dp) { Text(st.decoder.label, color = tint, fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
                CtlButton({ st.panel = Panel.Overflow }, 48.dp, 48.dp) { Ic(Res.drawable.ic_more_vert, "More", tint) }
            }
            if (s[PlayerKeys.shortcutsVisible]) ShortcutRow(window, tint, highlight)
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().videoCutout()) {
            st.resumeNotice?.let { Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) { ResumeNotice(st) } }
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, frame))).safeDrawingPadding().padding(bottom = 16.dp)) {
                val below = s[PlayerKeys.progressBelowButtons]
                if (!below) SeekRow(st)
                Box(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 8.dp)) {
                    Box(Modifier.align(Alignment.CenterStart)) { CtlButton({ st.locked = true; st.panel = Panel.None }, 50.dp, 46.dp) { Ic(Res.drawable.ic_pl_unlock, "Lock", tint) } }
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        if (s[PlayerKeys.showMoveButtons]) CtlButton({ st.seekBy(-s[PlayerKeys.moveInterval] * 1000L) }, 50.dp, 46.dp) { Ic(Res.drawable.ic_pl_rew, "Rewind", tint) }
                        if (s[PlayerKeys.showPrevNext]) CtlButton({ st.previous() }, transportWidth, 46.dp) { Ic(Res.drawable.ic_pl_prev, "Previous", tint, 30.dp) }
                        CtlButton({ st.togglePlay() }, transportWidth, 46.dp) { Ic(if (st.isPlaying) Res.drawable.ic_pl_pause else Res.drawable.ic_pl_play, "Play/Pause", tint, 40.dp) }
                        if (s[PlayerKeys.showPrevNext]) CtlButton({ st.next() }, transportWidth, 46.dp) { Ic(Res.drawable.ic_pl_next, "Next", tint, 30.dp) }
                        if (s[PlayerKeys.showMoveButtons]) CtlButton({ st.seekBy(s[PlayerKeys.moveInterval] * 1000L) }, 50.dp, 46.dp) { Ic(Res.drawable.ic_pl_ff, "Forward", tint) }
                    }
                    Row(Modifier.align(Alignment.CenterEnd)) {
                        CtlButton({ st.cycleZoom(); st.toast = st.zoom.name }, 50.dp, 46.dp) { Ic(Res.drawable.ic_pl_zoom, "Zoom", tint) }
                        if (PlayerCapabilities.pictureInPicture) CtlButton({ if (!window.enterPip()) st.toast = "Picture-in-picture not supported" }, 50.dp, 46.dp) { Ic(Res.drawable.ic_pl_pip, "Popup", tint) }
                        if (PlayerCapabilities.fullscreenButton) CtlButton({ PlatformVideoTools.toggleFullscreen() }, 50.dp, 46.dp) {
                            Icon(
                                if (PlatformVideoTools.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                if (PlatformVideoTools.isFullscreen) "Exit full screen" else "Full screen", Modifier.size(26.dp), tint = tint,
                            )
                        }
                    }
                }
                if (below) SeekRow(st)
            }
        }
    }
}

@Composable
private fun ResumeNotice(st: VideoPlayerState) {
    LaunchedEffect(st.resumeNotice) { delay(5000); st.resumeNotice = null }
    Row(
        Modifier.padding(start = 16.dp).offset(y = 12.dp).height(32.dp).background(Color(0xCC000000), RoundedCornerShape(16.dp)).padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clickable { st.resumeNotice = null }, contentAlignment = Alignment.Center) {
            Icon(painterResource(Res.drawable.ic_close), "Close", Modifier.size(18.dp), tint = Color.White)
        }
        Spacer(Modifier.width(6.dp))
        Text("Continue from where you stopped.", color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        Text("START OVER", Modifier.clickable { st.startOverFromNotice() }, color = PlayerAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SeekRow(st: VideoPlayerState) {
    val s = KMediaPlayer.settings
    val tint = Color(s[PlayerKeys.controlNormalColor])
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = st.duration.coerceAtLeast(1)
    val value = dragging ?: (st.position.toFloat() / dur).coerceIn(0f, 1f)
    val shown = dragging?.let { (it * dur).toLong() } ?: st.position
    Row(Modifier.height(50.dp).offset(y = 4.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatDuration(shown), color = tint, fontSize = 12.5.sp, modifier = Modifier.width(52.dp))
        MxSeekBar(
            value, Color(s[PlayerKeys.progressBarColor]), s[PlayerKeys.progressBarStyle] != "thin", Modifier.weight(1f).fillMaxHeight(),
            onChange = { dragging = it; st.scheduleHide() },
            onFinished = { dragging?.let { st.seekTo((it * dur).toLong()) }; dragging = null },
        )
        Text(if (s[PlayerKeys.showRemaining]) formatRemaining(shown, st.duration) else formatDuration(st.duration), color = tint, fontSize = 12.5.sp,
            modifier = Modifier.width(52.dp), textAlign = TextAlign.End, maxLines = 1, softWrap = false)
    }
    if (st.ab.a != null) Text("A-B: ${formatDuration(st.ab.a!!)} → ${st.ab.b?.let { formatDuration(it) } ?: "…"}", Modifier.padding(start = 16.dp), color = Color(0xFFFFC107), fontSize = 12.sp)
}

/** Thin track with a round thumb, 16dp internal inset like the platform SeekBar. */
@Composable
fun MxSeekBar(value: Float, color: Color, thick: Boolean, modifier: Modifier, onChange: (Float) -> Unit, onFinished: () -> Unit) {
    val inset = with(LocalDensity.current) { 16.dp.toPx() }
    fun frac(x: Float, w: Int) = ((x - inset) / (w - 2 * inset)).coerceIn(0f, 1f)
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures { onChange(frac(it.x, size.width)); onFinished() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onChange(frac(it.x, size.width)) },
                    onDragEnd = onFinished, onDragCancel = onFinished,
                ) { change, _ -> change.consume(); onChange(frac(change.position.x, size.width)) }
            },
    ) {
        val y = size.height / 2
        val track = (if (thick) 4.dp else 2.dp).toPx()
        val x = inset + (size.width - 2 * inset) * value
        drawLine(Color(0x4DFFFFFF), Offset(inset, y), Offset(size.width - inset, y), track, StrokeCap.Round)
        drawLine(color, Offset(inset, y), Offset(x, y), track, StrokeCap.Round)
        drawCircle(color, 6.dp.toPx(), Offset(x, y))
    }
}

@Composable
private fun ShortcutRow(window: PlayerWindow, tint: Color, highlight: Color) {
    val st = window.state
    val enabled = Shortcut.decode(KMediaPlayer.settings[PlayerKeys.shortcutItems]).toSet()
    val expanded = st.shortcutsExpanded
    val items = (if (expanded) Shortcut.entries.filter { it in enabled || it == Shortcut.Customise } else Shortcut.collapsed.filter { it in enabled })
        .filter { it.isAvailable }
    Row(Modifier.height(if (expanded) 80.dp else 50.dp).horizontalScroll(rememberScrollState()).padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        items.forEach { sc ->
            val active = when (sc) {
                Shortcut.NightMode -> st.nightMode; Shortcut.Mute -> st.muted; Shortcut.Loop -> st.loop != LoopMode.Off
                Shortcut.Shuffle -> st.shuffle; Shortcut.AbRepeat -> st.ab.a != null; Shortcut.SleepTimer -> st.sleepEndAt != null
                Shortcut.BackgroundPlay -> st.backgroundPlay; Shortcut.Speed -> st.speed != 1f; else -> false
            }
            Column(Modifier.width(56.dp).clickable(interactionSource = null, indication = null) { onShortcut(window, sc) }, horizontalAlignment = Alignment.CenterHorizontally) {
                val iconTint = if (active && highlight.luminance() > 0.5f) Color.Black else tint
                Box(Modifier.size(40.dp).background(if (active) highlight else Color(0x8C000000), CircleShape), contentAlignment = Alignment.Center) {
                    if (sc == Shortcut.Speed) Text(Speed.label(st.speed).uppercase(), color = iconTint, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    else Ic(shortcutIcon(sc, st), sc.label, iconTint, 19.dp)
                }
                if (expanded) Text(sc.label, Modifier.width(58.dp).offset(y = (-1).dp), color = tint, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 11.5.sp)
            }
        }
        Box(Modifier.padding(start = 4.dp, top = 8.dp).size(24.dp).background(Color(0x8C000000), CircleShape).clickable { st.shortcutsExpanded = !expanded }, contentAlignment = Alignment.Center) {
            Ic(if (expanded) Res.drawable.ic_chevron_left else Res.drawable.ic_chevron_right, "Expand shortcuts", tint, 16.dp)
        }
    }
}

private fun shortcutIcon(sc: Shortcut, st: VideoPlayerState): DrawableResource = when (sc) {
    Shortcut.NightMode -> Res.drawable.ic_pl_night
    Shortcut.Customise -> Res.drawable.ic_pl_edit
    Shortcut.Shuffle -> Res.drawable.ic_pl_shuffle
    Shortcut.Loop -> if (st.loop == LoopMode.One) Res.drawable.ic_pl_loop_one else Res.drawable.ic_pl_loop
    Shortcut.Mute -> if (st.muted) Res.drawable.ic_pl_mute else Res.drawable.ic_pl_volume
    Shortcut.SleepTimer -> Res.drawable.ic_pl_sleep
    Shortcut.AbRepeat -> Res.drawable.ic_pl_ab
    Shortcut.Equalizer -> Res.drawable.ic_pl_eq
    Shortcut.Speed -> Res.drawable.ic_pl_ff
    Shortcut.Screenshot -> Res.drawable.ic_pl_shot
    Shortcut.BackgroundPlay -> Res.drawable.ic_pl_bg
    Shortcut.Rotation -> Res.drawable.ic_pl_rotate
}

private fun onShortcut(window: PlayerWindow, sc: Shortcut) {
    val st = window.state
    when (sc) {
        Shortcut.NightMode -> { st.nightMode = !st.nightMode; st.toast = if (st.nightMode) "Night mode on" else "Night mode off" }
        Shortcut.Customise -> st.panel = Panel.Customise
        Shortcut.Shuffle -> st.toggleShuffle()
        Shortcut.Loop -> st.cycleLoop()
        Shortcut.Mute -> st.toggleMute()
        Shortcut.SleepTimer -> st.panel = Panel.SleepTimer
        Shortcut.AbRepeat -> st.panel = Panel.AbRepeat
        Shortcut.Equalizer -> st.panel = Panel.Equalizer
        Shortcut.Speed -> st.panel = Panel.Speed
        Shortcut.Screenshot -> window.screenshot()
        Shortcut.BackgroundPlay -> { st.backgroundPlay = !st.backgroundPlay; st.toast = if (st.backgroundPlay) "Background play on" else "Background play off" }
        Shortcut.Rotation -> window.rotate()
    }
}
