package org.app.kmediaplayer.ui.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.engine.AudioEffectsController
import org.app.kmediaplayer.engine.Decoder
import org.app.kmediaplayer.engine.TrackType
import org.app.kmediaplayer.logic.AspectRatio
import org.app.kmediaplayer.logic.SleepTimerInput
import org.app.kmediaplayer.logic.Speed
import org.app.kmediaplayer.platform.PlatformMedia
import org.app.kmediaplayer.settings.BoolSetting
import org.app.kmediaplayer.settings.ChoiceSetting
import org.app.kmediaplayer.settings.ColorSetting
import org.app.kmediaplayer.settings.PlayerKeys
import org.app.kmediaplayer.settings.Setting
import org.app.kmediaplayer.settings.Shortcut
import org.app.kmediaplayer.settings.tunerTabs
import org.app.kmediaplayer.ui.common.NetworkStreamDialog
import org.app.kmediaplayer.ui.common.PlayerDialog
import org.app.kmediaplayer.ui.resources.Res
import org.app.kmediaplayer.ui.resources.ic_back
import org.app.kmediaplayer.ui.resources.ic_bm_add
import org.app.kmediaplayer.ui.resources.ic_bm_import
import org.app.kmediaplayer.ui.resources.ic_check
import org.app.kmediaplayer.ui.resources.ic_chevron_down
import org.app.kmediaplayer.ui.resources.ic_chevron_right
import org.app.kmediaplayer.ui.resources.ic_close
import org.app.kmediaplayer.ui.resources.ic_ov_aspect
import org.app.kmediaplayer.ui.resources.ic_ov_bookmark
import org.app.kmediaplayer.ui.resources.ic_ov_cut
import org.app.kmediaplayer.ui.resources.ic_ov_display
import org.app.kmediaplayer.ui.resources.ic_ov_info
import org.app.kmediaplayer.ui.resources.ic_ov_network
import org.app.kmediaplayer.ui.resources.ic_ov_playlist
import org.app.kmediaplayer.ui.resources.ic_ov_share
import org.app.kmediaplayer.ui.resources.ic_ov_tutorial
import org.app.kmediaplayer.ui.resources.ic_pl_pause
import org.app.kmediaplayer.ui.resources.ic_pl_play
import org.app.kmediaplayer.ui.resources.ic_pl_zoom
import org.app.kmediaplayer.ui.resources.ic_sp_edit
import org.app.kmediaplayer.ui.resources.ic_sp_minus
import org.app.kmediaplayer.ui.resources.ic_sp_plus
import org.app.kmediaplayer.ui.resources.ic_sp_reset
import org.app.kmediaplayer.ui.settings.ColorPickerDialog
import org.app.kmediaplayer.ui.settings.MxSwitch
import org.app.kmediaplayer.ui.settings.SettingRow
import org.app.kmediaplayer.ui.settings.settingsVersion
import org.app.kmediaplayer.ui.theme.OnPlayerAccent
import org.app.kmediaplayer.ui.theme.PlayerAccent
import org.app.kmediaplayer.util.formatDecimal
import org.app.kmediaplayer.util.formatDuration
import org.app.kmediaplayer.util.groupThousands
import org.app.kmediaplayer.util.nowMs
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.round

private val PanelBg = Color(0xE61A1F26)
private val Dim = Color(0xFF9AA6B2)

/** Outlives the player screen, so file operations finish after the screen closes. */
private val fileOpsScope = MainScope()

@Composable
internal fun PanelHost(window: PlayerWindow) {
    val st = window.state
    if (st.panel == Panel.Decoder) { DecoderDialog(st); return }
    if (st.panel == Panel.AbRepeat) { AbRepeatCard(st); return }
    val widthFraction = when (st.panel) { Panel.More -> 0.28f; Panel.Display -> 0.57f; Panel.Equalizer -> 0.5f; Panel.Speed -> 1f; else -> 0.46f }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val portrait = maxHeight > maxWidth
        if (st.panel == Panel.Speed) { SpeedPanel(st, Modifier.align(Alignment.BottomCenter).videoCutout()); return@BoxWithConstraints }
        Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { st.panel = Panel.None })
        val overflow = st.panel != Panel.Display
        val mod = if (portrait) Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.6f)
        else Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(when { st.panel == Panel.Equalizer -> 367.dp; st.panel == Panel.More -> 220.dp; overflow -> 360.dp; st.panel == Panel.Display -> 448.dp; else -> maxWidth * widthFraction })
        val bg = if (overflow) Modifier.background(Brush.horizontalGradient(0f to Color(0x66000000), 0.2f to Color(0xB3000000), 1f to Color(0xCC000000)))
        else if (st.panel == Panel.Display) Modifier.background(Color(0x99000000)) else Modifier.background(PanelBg)
        Column(mod.videoCutout().then(bg).let { if (overflow && !portrait) it else it.safeDrawingPadding() }.clickable(interactionSource = null, indication = null) {}
            .let { if (overflow) it.padding(start = 24.dp, end = 24.dp, top = if (st.panel == Panel.Overflow) 24.dp else 16.dp) else if (st.panel == Panel.Display) it.padding(start = 24.dp, end = 24.dp) else it.padding(horizontal = 20.dp, vertical = 12.dp) }) {
            when (st.panel) {
                Panel.Overflow -> OverflowPanel(window)
                Panel.Audio -> AudioPanel(window)
                Panel.Subtitle -> SubtitlePanel(window)
                Panel.Equalizer -> EqualizerPanel()
                Panel.SleepTimer -> SleepTimerPanel(st)
                Panel.Aspect -> AspectPanel(st)
                Panel.Info -> InfoPanel(st)
                Panel.Bookmark -> BookmarkPanel(window)
                Panel.More -> MorePanel(window)
                Panel.Display -> DisplayPanel(window)
                Panel.Playlist -> PlaylistPanel(st)
                Panel.Customise -> CustomisePanel()
                else -> {}
            }
        }
    }
}

@Composable
private fun PanelTitle(text: String, onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) Box(Modifier.layout { m, c -> val p = m.measure(c); layout(0, p.height) { p.place(-36.dp.roundToPx(), 0) } }) {
            Icon(painterResource(Res.drawable.ic_back), "Back", Modifier.size(24.dp).clickable(onClick = onBack), tint = Color.White)
        }
        Text(text, Modifier.weight(1f), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        trailing()
    }
}

private val MxBlue: Color get() = PlayerAccent
private val Disabled = Color(0x73FFFFFF)

@Composable
private fun PanelRow(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(42.dp).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text(text, color = if (enabled) Color.White else Disabled, fontSize = 14.sp)
    }
}

@Composable
private fun PanelDivider() = Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(Color(0x26FFFFFF)))

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(46.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        PanelRadio(selected); Spacer(Modifier.width(4.dp))
        Text(text, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, enabled: Boolean = true, height: Dp = 46.dp, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(height).clickable(enabled = enabled, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        PanelCheck(checked); Spacer(Modifier.width(4.dp))
        Text(text, color = if (enabled) Color.White else Disabled, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PanelRadio(selected: Boolean) {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(20.dp).border(2.dp, if (selected) MxBlue else Color(0xFFBFC5CC), CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).background(MxBlue, CircleShape))
        }
    }
}

@Composable
private fun PanelCheck(checked: Boolean) {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(18.dp).background(if (checked) MxBlue else Color.Transparent, RoundedCornerShape(2.dp)).border(2.dp, if (checked) MxBlue else Color(0xFF3F5470), RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
            if (checked) Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(14.dp), tint = Color(0xFF101820))
        }
    }
}

@Composable
private fun Scroll(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), content = content)
}

@Composable
private fun OverflowPanel(window: PlayerWindow) {
    val st = window.state
    val settings = KMediaPlayer.settings
    var networkStream by remember { mutableStateOf(false) }
    var tutorial by remember { mutableStateOf(false) }
    var videoOn by remember { mutableStateOf(settings[PlayerKeys.videoDisplay]) }
    var shortcutsOn by remember { mutableStateOf(settings[PlayerKeys.shortcutsVisible]) }
    val ffSpeed = settings[PlayerKeys.longPressSpeed]
    data class Item(val label: String, val icon: DrawableResource?, val text: String? = null, val action: () -> Unit)
    val items = listOf(
        Item("Aspect Ratio", Res.drawable.ic_ov_aspect) { st.panel = Panel.Aspect },
        Item("Speed FF\n(Long Press)", null, "${formatDecimal(ffSpeed, 1)}X") {
            val next = listOf(1.5f, 2f, 3f, 4f).let { l -> l[(l.indexOf(ffSpeed) + 1) % l.size] }
            settings[PlayerKeys.longPressSpeed] = next
        },
        Item("Display Settings", Res.drawable.ic_ov_display) { st.panel = Panel.Display },
        Item("Playlists", Res.drawable.ic_ov_playlist) { st.panel = Panel.Playlist },
        Item("Network Stream", Res.drawable.ic_ov_network) { networkStream = true },
        Item("Information", Res.drawable.ic_ov_info) { st.panel = Panel.Info },
        Item("Share", Res.drawable.ic_ov_share) { st.current?.let { PlatformMedia.share(listOf(it)) } },
        Item("Bookmark", Res.drawable.ic_ov_bookmark) { st.panel = Panel.Bookmark },
        Item("Tutorial", Res.drawable.ic_ov_tutorial) { tutorial = true },
        Item("More", Res.drawable.ic_chevron_right) { st.panel = Panel.More },
    )
    Column(Modifier.verticalScroll(rememberScrollState())) {
        items.chunked(4).forEach { row ->
            Row {
                row.forEach { item ->
                    Column(Modifier.size(78.dp).clickable(onClick = item.action).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(42.dp).border(1.dp, Color(0x59FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
                            if (item.icon != null) Icon(painterResource(item.icon), null, Modifier.size(22.dp), tint = Color.White)
                            else Text(item.text.orEmpty(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(item.label, Modifier.padding(top = 3.dp).width(72.dp), color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SwitchRow("Video Display", videoOn) { videoOn = it; settings[PlayerKeys.videoDisplay] = it; window.setVideoDisplay(it) }
        Spacer(Modifier.height(16.dp))
        SwitchRow("Shortcuts", shortcutsOn) { shortcutsOn = it; settings[PlayerKeys.shortcutsVisible] = it }
    }
    if (networkStream) NetworkStreamDialog { networkStream = false }
    if (tutorial) PlayerDialog(
        onDismissRequest = { tutorial = false },
        containerColor = Color(0xFF2B3640),
        title = { Text("Gestures", color = Color.White) },
        text = {
            Text(
                "• Swipe left/right: seek\n• Swipe up/down on the left: brightness\n• Swipe up/down on the right: volume\n" +
                    "• Double tap sides: rewind / forward\n• Pinch: zoom, drag with two fingers: pan\n• Long press: fast forward at ${Speed.label(ffSpeed)}\n• Drag subtitles up/down to move them",
                color = Color(0xFFC3CAD3), fontSize = 14.sp, lineHeight = 22.sp,
            )
        },
        confirmButton = { TextButton({ tutorial = false }) { Text("OK") } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Color.White, fontSize = 15.sp)
        MxSwitch(checked, onChange)
    }
}

@Composable
private fun AudioPanel(window: PlayerWindow) {
    val st = window.state
    val help = remember { PlatformVideoTools.audioTrackHelp() }
    var showHelp by remember { mutableStateOf(false) }
    if (help != null && showHelp) return AudioTrackHelpPanel(help) { showHelp = false }
    PanelTitle("Audio Track")
    Scroll {
        st.audioTracks.forEach { t ->
            RadioRow(t.label, t.selected) { if (help != null) { if (!t.selected) showHelp = true } else st.selectTrack(t, TrackType.Audio) }
        }
        if (st.audioTracks.isEmpty()) PanelRow("No audio tracks found", enabled = false) {}
        // Muting a track needs the same browser support as switching, so it is offered only where switching works.
        if (help == null) RadioRow("Disable", st.audioTracks.none { it.selected }) { st.disableAudio() }
        // External audio files, stereo mode and audio sync are not implemented on any platform, so they are not offered.
        if (PlayerCapabilities.decoderChoice) CheckRow("Use SW audio decoder", st.decoder == Decoder.SW, height = 42.dp) { st.setDecoderMode(if (st.decoder == Decoder.SW) Decoder.HW else Decoder.SW) }
    }
}

@Composable
private fun SubtitlePanel(window: PlayerWindow) {
    val st = window.state
    PanelTitle("Subtitle")
    Scroll {
        st.externalSubsName?.let { n -> CheckRow(n, true) { st.disableSubtitles() } }
        st.textTracks.forEach { t ->
            val on = t.selected && !st.subtitlesDisabled && st.externalSubs == null
            CheckRow(t.label, on) { if (on) st.disableSubtitles() else { st.externalSubs = null; st.externalSubsName = null; st.selectTrack(t, TrackType.Text) } }
        }
        if (st.textTracks.isEmpty() && st.externalSubs == null) PanelRow("No subtitles", enabled = false) {}
        PanelDivider()
        PanelRow("Open") { window.pickFile(listOf("*/*")) { uri, name -> st.loadSubtitle(uri, name) } }
        Stepper("Synchronization", "${formatDecimal(st.subtitleSyncMs / 1000f, 1)}s", enabled = st.externalSubs != null,
            onMinus = { st.setSubtitleSync(st.subtitleSyncMs - 100) }, onPlus = { st.setSubtitleSync(st.subtitleSyncMs + 100) })
        Stepper("Speed", "${formatDecimal(st.subtitleSpeed * 100, 1)}%", enabled = st.externalSubs != null,
            onMinus = { st.subtitleSpeed = (st.subtitleSpeed - 0.001f).coerceAtLeast(0.5f) }, onPlus = { st.subtitleSpeed = (st.subtitleSpeed + 0.001f).coerceAtMost(2f) })
        PanelRow("Panel") { st.panel = Panel.Display }
    }
}

@Composable
private fun Stepper(label: String, value: String, enabled: Boolean = true, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Color.White, fontSize = 14.sp)
        RoundStep(Res.drawable.ic_sp_minus, "Decrease", enabled, onMinus)
        Box(Modifier.padding(horizontal = 8.dp).size(104.dp, 30.dp).background(Color(0x14FFFFFF)), contentAlignment = Alignment.Center) {
            Text(value, color = Color.White, fontSize = 14.sp)
        }
        RoundStep(Res.drawable.ic_sp_plus, "Increase", enabled, onPlus)
    }
}

@Composable
private fun DecoderDialog(st: VideoPlayerState) {
    PlayerDialog(
        onDismissRequest = { st.panel = Panel.None },
        title = { Text("Select decoder") },
        text = {
            Column {
                Decoder.entries.forEach { d ->
                    Row(Modifier.fillMaxWidth().height(48.dp).clickable { st.setDecoderMode(d); st.panel = Panel.None }, verticalAlignment = Alignment.CenterVertically) {
                        PanelRadio(d == st.decoder); Spacer(Modifier.width(16.dp))
                        Text("${d.label} decoder", color = Color.White, fontSize = 17.sp)
                    }
                }
            }
        },
        confirmButton = {},
        buttons = false,
    )
}

@Composable
private fun SpeedPanel(st: VideoPlayerState, modifier: Modifier) {
    val accent = PlayerAccent
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { st.panel = Panel.None })
    Column(
        modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(bottom = 17.dp, start = 16.dp, end = 16.dp).widthIn(max = 586.dp).fillMaxWidth()
            .background(Color(0xB3141414), RoundedCornerShape(6.dp)).clickable(interactionSource = null, indication = null) {}
            .padding(top = 10.dp, bottom = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 16.dp, end = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundStep(Res.drawable.ic_sp_minus, "Decrease") { st.setSpeed(Speed.decrease(st.speed)) }
            Spacer(Modifier.weight(1f))
            Row(Modifier.width(120.dp).fillMaxHeight().background(Color(0x14FFFFFF), RoundedCornerShape(4.dp)).clickable { draft = formatDecimal(st.speed, 2); editing = true },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("${formatDecimal(st.speed, 2)}x", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.width(6.dp))
                Icon(painterResource(Res.drawable.ic_sp_edit), "Edit", Modifier.size(18.dp), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            RoundStep(Res.drawable.ic_sp_plus, "Increase") { st.setSpeed(Speed.increase(st.speed)) }
        }
        Row(Modifier.fillMaxWidth().height(30.dp).padding(start = 15.dp, end = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            SpeedTrack(st.speed, accent, Modifier.weight(1f).fillMaxHeight()) { st.setSpeed(it) }
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(Res.drawable.ic_sp_reset), "Reset", Modifier.size(24.dp).clickable { st.setSpeed(1f) }, tint = Color.White)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(23.dp).padding(start = 15.dp, end = 47.dp)) {
            val track = maxWidth - 32.dp
            listOf(0.25f, 1f, 2f, 3f, 4f).forEach { v ->
                val x = 16.dp + track * ((v - Speed.MIN) / (Speed.MAX - Speed.MIN))
                Text(if (v == 0.25f) "0.25x" else "${v}x", Modifier.layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0)); layout(p.width, p.height) { p.place(x.roundToPx() - p.width / 2, 0) }
                }.clickable { st.setSpeed(v) }, color = if (st.speed == v) accent else Color.White, fontSize = 12.sp)
            }
        }
    }
    if (editing) PlayerDialog(
        onDismissRequest = { editing = false },
        containerColor = Color(0xFF2B3640),
        text = {
            OutlinedTextField(draft, { draft = it }, singleLine = true, suffix = { Text("x") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        },
        confirmButton = { TextButton({ draft.toFloatOrNull()?.let { st.setSpeed(it.coerceIn(Speed.MIN, Speed.MAX)) }; editing = false }) { Text("OK") } },
        dismissButton = { TextButton({ editing = false }) { Text("CANCEL") } },
    )
}

@Composable
private fun RoundStep(icon: DrawableResource, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(30.dp).background(Color(0x33FFFFFF), CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), desc, Modifier.size(16.dp), tint = Color(0xFFDADDE4))
    }
}

@Composable
private fun SpeedTrack(speed: Float, color: Color, modifier: Modifier, onChange: (Float) -> Unit) {
    val inset = with(LocalDensity.current) { 16.dp.toPx() }
    fun value(x: Float, w: Int): Float {
        val raw = Speed.MIN + ((x - inset) / (w - 2 * inset)).coerceIn(0f, 1f) * (Speed.MAX - Speed.MIN)
        return (round(raw * 20) / 20f).coerceIn(Speed.MIN, Speed.MAX)
    }
    Canvas(
        modifier.pointerInput(Unit) { detectTapGestures { onChange(value(it.x, size.width)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> change.consume(); onChange(value(change.position.x, size.width)) } },
    ) {
        val y = size.height / 2
        val w = size.width - 2 * inset
        val frac = (speed - Speed.MIN) / (Speed.MAX - Speed.MIN)
        drawLine(Color(0x4DFFFFFF), Offset(inset, y), Offset(inset + w, y), 2.dp.toPx())
        drawLine(color, Offset(inset, y), Offset(inset + w * frac, y), 2.dp.toPx())
        var tick = 0.5f
        while (tick < Speed.MAX) {
            drawCircle(Color.White, 2.dp.toPx(), Offset(inset + w * (tick - Speed.MIN) / (Speed.MAX - Speed.MIN), y)); tick += 0.5f
        }
        drawCircle(Color.White, 7.dp.toPx(), Offset(inset + w * frac, y))
    }
}

/** Equalizer, reverb and bass boost of the shared engine. Reused by the music Now Playing screen. */
@Composable
fun EqualizerPanel() {
    val settings = KMediaPlayer.settings
    val fx: AudioEffectsController? = KMediaPlayer.engine.effects
    var on by remember { mutableStateOf(settings[PlayerKeys.equalizerEnabled]) }
    var preset by remember { mutableIntStateOf(settings[PlayerKeys.equalizerPreset]) }
    var version by remember { mutableIntStateOf(0) }
    val fg = if (on) Color.White else Color(0x99FFFFFF)
    val active = if (on) MxBlue else Color(0xFF4A7BA8)
    PanelTitle("Equalizer") {
        Text(if (on) "On" else "Off", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        MxSwitch(on, { on = it; settings[PlayerKeys.equalizerEnabled] = it; fx?.applySaved() }, color = MxBlue)
    }
    if (fx == null) { Text("Equalizer is not available for this audio output.", color = Dim, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)); return }
    Row(Modifier.padding(top = 13.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val names = listOf("Custom") + (0 until fx.presetCount).map { fx.presetName(it) }
        names.forEachIndexed { i, n ->
            val id = i - 1
            Box(
                Modifier.height(28.dp).border(1.dp, if (preset == id) active else Color(0x66FFFFFF), RoundedCornerShape(50))
                    .clickable { preset = id; settings[PlayerKeys.equalizerPreset] = id; if (id >= 0) fx.usePreset(id); version++ }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(n, color = if (preset == id) active else fg, fontSize = 11.5.sp) }
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        val range = fx.bandLevelRange
        version
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            (0 until fx.bandCount).forEach { b ->
                var level by remember(version, b) { mutableFloatStateOf(fx.bandLevel(b).toFloat()) }
                // Bands share the width evenly so 5-10 of them fit any screen.
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${round(level / 100).toInt()}" + if (fx.bandCount > 6) "" else " dB", color = fg, fontSize = 10.sp, maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(3.dp))
                    VerticalSlider(level, range.first.toFloat()..range.last.toFloat(), enabled = on, color = active) {
                        level = it
                        fx.setBandLevel(b, it.toInt())
                        preset = -1
                        settings[PlayerKeys.equalizerPreset] = -1
                        settings[PlayerKeys.equalizerBands] = (0 until fx.bandCount).joinToString(",") { i -> fx.bandLevel(i).toString() }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(fx.centerFreqHz(b).let { if (it >= 1000) "${it / 1000}k" else "$it" } + if (fx.bandCount > 6) "" else "Hz", color = fg, fontSize = 10.sp, maxLines = 1, softWrap = false)
                }
            }
        }
        if (fx.hasReverb) {
            var reverb by remember { mutableIntStateOf(settings[PlayerKeys.reverb]) }
            var open by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().height(40.dp).clickable(enabled = on) { open = true }, verticalAlignment = Alignment.CenterVertically) {
                Text("Reverb", Modifier.width(216.dp), color = fg, fontSize = 14.5.sp)
                Box(Modifier.weight(1f)) {
                    Text(AudioEffectsController.reverbNames[reverb], color = fg, fontSize = 14.sp)
                    DropdownMenu(open, { open = false }, containerColor = Color(0xFF2B3640)) {
                        AudioEffectsController.reverbNames.forEachIndexed { idx, name ->
                            DropdownMenuItem({ Text(name, color = Color.White) }, {
                                reverb = idx; open = false; settings[PlayerKeys.reverb] = idx; fx.applySaved()
                            })
                        }
                    }
                }
                Icon(painterResource(Res.drawable.ic_chevron_down), null, Modifier.size(16.dp), tint = fg)
            }
        }
        if (fx.hasBassBoost) {
            var bass by remember { mutableFloatStateOf(settings[PlayerKeys.bassBoost].toFloat()) }
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bass Boost", color = fg, fontSize = 14.5.sp)
                MxHSlider(bass / 1000f, active, on, Modifier.weight(1f).fillMaxHeight(), onChange = { bass = it * 1000f },
                    onFinished = { settings[PlayerKeys.bassBoost] = bass.toInt(); fx.applySaved() })
                Text("${(bass / 10).toInt()}%", color = fg, fontSize = 12.sp, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun VerticalSlider(value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, color: Color, onChange: (Float) -> Unit) {
    val span = range.endInclusive - range.start
    fun at(y: Float, h: Int, pad: Float) = range.endInclusive - ((y - pad) / (h - 2 * pad)).coerceIn(0f, 1f) * span
    val pad = with(LocalDensity.current) { 7.dp.toPx() }
    Canvas(
        Modifier.size(28.dp, 102.dp)
            .pointerInput(enabled) { if (enabled) detectTapGestures { onChange(at(it.y, size.height, pad)) } }
            .pointerInput(enabled) { if (enabled) detectVerticalDragGestures { c, _ -> c.consume(); onChange(at(c.position.y, size.height, pad)) } },
    ) {
        val x = size.width / 2
        val y = pad + (size.height - 2 * pad) * (1 - (value - range.start) / span)
        drawLine(color.copy(alpha = 0.6f), Offset(x, pad), Offset(x, size.height - pad), 2.dp.toPx())
        drawCircle(color, 7.dp.toPx(), Offset(x, y))
    }
}

@Composable
private fun MxHSlider(value: Float, color: Color, enabled: Boolean, modifier: Modifier, onChange: (Float) -> Unit, onFinished: () -> Unit) {
    val pad = with(LocalDensity.current) { 12.dp.toPx() }
    fun at(x: Float, w: Int) = ((x - pad) / (w - 2 * pad)).coerceIn(0f, 1f)
    Canvas(
        modifier.pointerInput(enabled) { if (enabled) detectTapGestures { onChange(at(it.x, size.width)); onFinished() } }
            .pointerInput(enabled) { if (enabled) detectHorizontalDragGestures(onDragEnd = onFinished) { c, _ -> c.consume(); onChange(at(c.position.x, size.width)) } },
    ) {
        val y = size.height / 2
        val x = pad + (size.width - 2 * pad) * value
        drawLine(color.copy(alpha = 0.45f), Offset(pad, y), Offset(size.width - pad, y), 2.dp.toPx())
        drawLine(color, Offset(pad, y), Offset(x, y), 2.dp.toPx())
        drawCircle(color, 7.dp.toPx(), Offset(x, y))
    }
}

@Composable
private fun SleepTimerPanel(st: VideoPlayerState) {
    var input by remember { mutableStateOf(SleepTimerInput()) }
    var finishLast by remember { mutableStateOf(st.sleepFinishLast) }
    PanelTitle("Sleep Timer")
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(62.dp)) {
            val running = st.sleepEndAt?.let { ((it - nowMs()) / 60000).coerceAtLeast(0) }
            val h = running?.let { (it / 60).toInt() } ?: input.hours
            val m = running?.let { (it % 60).toInt() } ?: input.minutes
            Row(Modifier.align(Alignment.TopCenter).offset(y = 6.dp), verticalAlignment = Alignment.Bottom) {
                Text("$h", color = Color.White, fontSize = 44.sp, lineHeight = 50.sp)
                Text("h ", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(bottom = 6.dp))
                Text(m.toString().padStart(2, '0'), color = Color.White, fontSize = 44.sp, lineHeight = 50.sp)
                Text("m", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            Box(Modifier.align(Alignment.TopEnd).offset(y = 18.7.dp).size(24.dp).background(Color(0x59FFFFFF), CircleShape).clickable { input = input.clear() }, contentAlignment = Alignment.Center) {
                Icon(painterResource(Res.drawable.ic_close), "Clear", Modifier.size(15.dp), tint = Color.White)
            }
            Box(Modifier.align(Alignment.TopStart).offset(y = 57.dp).fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
        }
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(null, 0, null)).forEach { row ->
            Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                row.forEach { d ->
                    Box(Modifier.width(70.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (d != null) Box(Modifier.size(30.dp).background(Color(0x26FFFFFF), CircleShape).clickable { input = input.press(d) }, contentAlignment = Alignment.Center) {
                            Text("$d", color = Color.White, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
        CheckRow("Play last media to the end", finishLast, enabled = input.isValid || st.sleepEndAt != null, height = 32.dp) { finishLast = !finishLast }
        Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(62.dp).fillMaxHeight().clickable { st.stopSleepTimer(); st.panel = Panel.None }, contentAlignment = Alignment.Center) {
                Text("STOP", color = MxBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(62.dp).fillMaxHeight().clickable(enabled = input.isValid) { st.startSleepTimer(input.totalMinutes, finishLast); st.panel = Panel.None }, contentAlignment = Alignment.Center) {
                Text("START", color = if (input.isValid) MxBlue else Disabled, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AbRepeatCard(st: VideoPlayerState) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.align(Alignment.TopEnd).padding(top = 106.dp, end = 4.dp).width(233.dp).videoCutout().background(Color(0xCC161616), RoundedCornerShape(4.dp)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(4.dp)).padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("A - B Repeat", Modifier.weight(1f), color = Color.White, fontSize = 14.sp)
                Box(Modifier.size(42.dp).clickable { st.ab = st.ab.clear(); st.panel = Panel.None }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.ic_close), "Close", Modifier.size(18.dp), tint = Color.White)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AbButton("A", st.ab.a, Modifier.width(88.dp)) { st.ab = st.ab.setA(st.engine.position) }
                Icon(painterResource(Res.drawable.ic_pl_zoom), null, Modifier.weight(1f).size(20.dp), tint = Color(0x59FFFFFF))
                AbButton("B", st.ab.b, Modifier.width(88.dp), enabled = st.ab.a != null) {
                    val next = st.ab.setB(st.engine.position)
                    if (next == st.ab) st.toast = "B must be after A" else { st.ab = next; st.engine.seekTo(next.a!!); st.panel = Panel.None }
                }
            }
        }
    }
}

@Composable
private fun AbButton(label: String, value: Long?, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.height(48.dp).background(Color(0x26FFFFFF), RoundedCornerShape(2.dp)).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(value?.let { "$label  ${formatDuration(it)}" } ?: label, color = if (enabled) Color.White else Dim, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AspectPanel(st: VideoPlayerState) {
    var applyAll by remember { mutableStateOf(false) }
    var custom by remember { mutableFloatStateOf(st.customAspect) }
    PanelTitle("Aspect Ratio", onBack = { st.panel = Panel.Overflow })
    Scroll {
        Spacer(Modifier.height(7.dp))
        AspectRatio.entries.forEach { a -> RadioRow(a.label, st.aspect == a) { st.setAspectRatio(a, applyAll) } }
        if (st.aspect == AspectRatio.Custom) {
            Text("Custom ${formatDecimal(custom, 2)}:1", color = Color.White, fontSize = 14.sp)
            MxHSlider((custom - 0.5f) / 2.5f, MxBlue, true, Modifier.fillMaxWidth().height(36.dp), onChange = { custom = 0.5f + it * 2.5f; st.customAspect = custom }, onFinished = {})
        }
        CheckRow("Apply to all videos", applyAll) { applyAll = !applyAll; st.setAspectRatio(st.aspect, applyAll) }
    }
}

@Composable
private fun InfoPanel(st: VideoPlayerState) {
    val f = st.current
    val engine = st.engine
    val video = engine.videoFormat()
    val audio = engine.audioFormat()
    PanelTitle("Information", onBack = { st.panel = Panel.Overflow })
    Scroll {
        Spacer(Modifier.height(22.dp))
        InfoSection("File")
        if (f != null) {
            InfoRow("File", f.name); InfoRow("Location", f.folderPath)
            if (f.sizeBytes > 0) InfoRow("Size", "${PlatformMedia.formatShortFileSize(f.sizeBytes)} (${groupThousands(f.sizeBytes)} bytes)")
            if (f.dateModifiedMs > 0) InfoRow("Date", PlatformMedia.formatDate(f.dateModifiedMs, "d MMMM yyyy 'at' HH:mm"))
            InfoRow("Subtitle", st.externalSubsName ?: if (st.textTracks.isNotEmpty()) "Embedded subtitle" else "None")
        }
        InfoSection("Media")
        engine.title?.takeIf { f == null || (it != f.title && it != f.name && it != f.uri.substringAfterLast('/')) }?.let { InfoRow("Title", it) }
        (video?.containerMimeType ?: audio?.containerMimeType)?.let { InfoRow("Format", containerLabel(it)) }
        InfoRow("Length", formatDuration(st.duration))
        InfoRow("Decoder", "${st.decoder.label} decoder")
        video?.let { v ->
            InfoSection("Video")
            InfoRow("Codec", v.codecs ?: v.sampleMimeType ?: "—")
            InfoRow("Resolution", "${v.width} x ${v.height}")
            if (v.frameRate > 0) InfoRow("Frame rate", "${formatDecimal(v.frameRate, 3)} fps")
            if (v.bitrate > 0) InfoRow("Bitrate", "${v.bitrate / 1000} kbps")
        }
        audio?.let { a ->
            InfoSection("Audio")
            InfoRow("Codec", a.codecs ?: a.sampleMimeType ?: "—")
            InfoRow("Channels", "${a.channelCount}")
            InfoRow("Sample rate", "${a.sampleRate} Hz")
            a.language?.let { InfoRow("Language", PlatformMedia.displayLanguage(it)) }
        }
    }
}

private fun containerLabel(mime: String) = when {
    "matroska" in mime || "webm" in mime -> "Matroska / WebM"
    "mp4" in mime -> "MPEG-4"
    "avi" in mime -> "AVI"
    "mp2t" in mime -> "MPEG-TS"
    else -> mime.substringAfter('/')
}

@Composable
private fun InfoSection(text: String) = Text(text, Modifier.padding(top = 4.dp, bottom = 4.dp), color = Color.White, fontSize = 13.sp, lineHeight = 16.sp)

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 33.3.dp).padding(bottom = 4.4.dp)) {
        Text(k, Modifier.width(96.dp).padding(top = 5.7.dp), color = Color(0xB3FFFFFF), fontSize = 13.sp, lineHeight = 17.sp)
        Text(v, color = Color.White, fontSize = 14.sp, lineHeight = 16.7.sp)
    }
}

@Composable
private fun BookmarkPanel(window: PlayerWindow) {
    val st = window.state
    val host = KMediaPlayer.host
    val f = st.current
    val marks by remember(f?.uri) { f?.let { host.bookmarks(it.uri) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(emptyList())
    val import = {
        window.pickFile(listOf("text/*")) { uri, _ ->
            if (f != null) {
                val text = PlatformMedia.readBytes(uri)?.decodeToString().orEmpty()
                val times = Regex("""(\d{1,2}):(\d{2}):(\d{2})|(\d{1,3}):(\d{2})""").findAll(text).map { m ->
                    val g = m.groupValues
                    if (g[1].isNotEmpty()) (g[1].toLong() * 3600 + g[2].toLong() * 60 + g[3].toLong()) * 1000 else (g[4].toLong() * 60 + g[5].toLong()) * 1000
                }.filter { it in 0..st.duration }.toList()
                times.forEach { host.addBookmark(f.uri, it) }
                st.toast = if (times.isEmpty()) "No bookmarks found in file" else "${times.size} bookmarks imported"
            }
        }
    }
    PanelTitle("Bookmark", onBack = { st.panel = Panel.Overflow })
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        if (marks.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).offset(y = 35.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("No bookmarks yet.", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.size(181.dp, 36.dp).border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(2.dp)).clickable { import() },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(Res.drawable.ic_bm_import), null, Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Import Bookmarks", color = Color.White, fontSize = 14.sp)
                }
            }
        } else Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            marks.forEach { b ->
                Row(Modifier.fillMaxWidth().height(42.dp).clickable { st.seekTo(b.positionMs) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(b.positionMs), Modifier.weight(1f), color = Color.White, fontSize = 14.sp)
                    Icon(painterResource(Res.drawable.ic_close), "Delete", Modifier.size(20.dp).clickable { f?.let { host.removeBookmark(it.uri, b.positionMs) } }, tint = Disabled)
                }
            }
        }
        val dur = st.duration.coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(59.dp).offset(y = 10.dp).layout { m, c -> val p = m.measure(c.copy(maxWidth = c.maxWidth + 28.dp.roundToPx())); layout(c.maxWidth, p.height) { p.place(-16.dp.roundToPx(), 0) } }, verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(st.position), Modifier.width(52.dp), color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
            MxSeekBar((st.position.toFloat() / dur).coerceIn(0f, 1f), MxBlue, false, Modifier.weight(1f).fillMaxHeight(), onChange = { st.seekTo((it * dur).toLong()) }, onFinished = {})
            Text(formatDuration(st.duration), color = Color.White, fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            Icon(painterResource(if (st.isPlaying) Res.drawable.ic_pl_pause else Res.drawable.ic_pl_play), "Play/Pause", Modifier.align(Alignment.Center).size(30.dp).clickable { st.togglePlay() }, tint = Color.White)
            Row(Modifier.align(Alignment.CenterEnd).offset(x = 8.dp).height(27.dp).background(PlayerAccent, RoundedCornerShape(50))
                .clickable(enabled = f != null) { f?.let { host.addBookmark(it.uri, st.engine.position) } }
                .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(Res.drawable.ic_bm_add), null, Modifier.size(14.dp), tint = OnPlayerAccent)
                Spacer(Modifier.width(4.dp))
                Text("Add", color = OnPlayerAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(5.dp))
    }
}

@Composable
private fun MorePanel(window: PlayerWindow) {
    val st = window.state
    val host = KMediaPlayer.host
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val editable = PlayerCapabilities.fileEditing && st.current?.uri?.let { it.startsWith("content") || it.startsWith("file:") } == true
    PanelTitle("More", onBack = { st.panel = Panel.Overflow })
    Scroll {
        Spacer(Modifier.height(8.dp))
        MoreSection("Tools")
        if (editable) PanelRow("Delete") { confirmDelete = true }
        if (editable) PanelRow("Rename") { renaming = true }
        PanelRow("Lock") { st.panel = Panel.None; st.locked = true }
        PanelRow("Settings") { host.openSettings() }
        Spacer(Modifier.height(8.dp))
        MoreSection("Help")
        PanelRow("FAQ") { host.openHelp() }
        if (PlayerCapabilities.storeUpdates) PanelRow("Check for updates") { if (!window.openStorePage()) st.toast = "You're on the latest version" }
        PanelRow("Bug Report") { if (!PlatformMedia.openUri("mailto:?subject=${host.appName} bug report")) st.toast = "No email app found" }
    }
    if (confirmDelete) PlayerDialog(
        onDismissRequest = { confirmDelete = false },
        text = { Text("Delete \"${st.current?.name}\"?") },
        confirmButton = {
            TextButton({
                confirmDelete = false
                st.current?.let { f ->
                    fileOpsScope.launch { host.deleteFile(f) }
                    if (st.engine.hasNext()) st.next() else window.close()
                }
            }) { Text("DELETE") }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("CANCEL") } },
    )
    if (renaming) {
        val f = st.current
        var name by remember { mutableStateOf(f?.name?.substringBeforeLast('.') ?: "") }
        PlayerDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton({
                    renaming = false
                    if (f != null && name.isNotBlank()) {
                        val ext = f.name.substringAfterLast('.', "")
                        val newName = if (ext.isEmpty()) name.trim() else "${name.trim()}.$ext"
                        fileOpsScope.launch {
                            if (host.renameFile(f, newName)) { st.title = newName.substringBeforeLast('.'); st.toast = "Renamed" } else st.toast = "Rename failed"
                        }
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ renaming = false }) { Text("CANCEL") } },
        )
    }
}

@Composable
private fun MoreSection(text: String) = Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.CenterStart) { Text(text, color = Disabled, fontSize = 13.sp) }

@Composable
private fun DisplayPanel(window: PlayerWindow) {
    val settings = KMediaPlayer.settings
    var tab by remember { mutableIntStateOf(0) }
    settingsVersion()
    val changed = { window.applyScreenSettings(); KMediaPlayer.engine.applySettings() }
    Row(Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState())) {
        tunerTabs.forEachIndexed { i, t ->
            Box(Modifier.width(120.dp).fillMaxHeight().clickable { tab = i }, contentAlignment = Alignment.Center) {
                Text(t.title, color = if (i == tab) Color.White else Disabled, fontSize = 14.sp)
                if (i == tab) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(PlayerAccent))
            }
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
        val paired = mapOf<Setting<*>, Setting<*>>(PlayerKeys.controlNormalColor to PlayerKeys.controlHighlightColor, PlayerKeys.progressBarColor to PlayerKeys.progressBarStyle)
        val skip = paired.values.toSet()
        tunerTabs[tab].groups.forEach { g ->
            g.title?.let { Text(it, Modifier.padding(top = 8.dp, bottom = 4.dp), color = Disabled, fontSize = 13.sp) }
            g.items.filter { it !in skip }.forEach { item ->
                if (item is BoolSetting) {
                    val v = settings[item]
                    CheckRow(item.title, v, height = 40.dp) { settings[item] = !v; changed() }
                    return@forEach
                }
                if (item !is ColorSetting && item !is ChoiceSetting) { SettingRow(item, compact = true) { changed() }; return@forEach }
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (item == PlayerKeys.controlNormalColor) "Controls" else item.title, Modifier.width(96.dp), color = Color.White, fontSize = 13.sp, maxLines = 2, lineHeight = 15.sp)
                    TunerControl(item, changed)
                    paired[item]?.let { Spacer(Modifier.width(12.dp)); TunerControl(it, changed) }
                }
            }
        }
    }
}

@Composable
private fun TunerControl(item: Setting<*>, changed: () -> Unit) {
    val settings = KMediaPlayer.settings
    var open by remember { mutableStateOf(false) }
    when (item) {
        is ColorSetting -> {
            val v = settings[item]
            Box(Modifier.size(40.dp, 20.dp).background(Color(0xFF808080)).background(Color(v)).border(1.dp, Color(0x66FFFFFF)).clickable { open = true })
            if (open) ColorPickerDialog(item.title, v, { open = false }) { settings[item] = it; changed(); open = false }
        }
        is ChoiceSetting -> Box {
            val v = settings[item]
            Row(Modifier.height(33.dp).clickable { open = true }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.labelOf(v).ifEmpty { "Default" }, color = Color.White, fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.width(24.dp))
                Canvas(Modifier.size(8.dp, 5.dp)) {
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, Color.White)
                }
            }
            DropdownMenu(open, { open = false }, containerColor = Color(0xF0161616)) {
                item.choices.forEach { (key, label) ->
                    DropdownMenuItem({ Text(label, color = if (key == v) PlayerAccent else Color.White, fontSize = 14.sp) }, { settings[item] = key; changed(); open = false })
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun PlaylistPanel(st: VideoPlayerState) {
    PanelTitle("Playlist (${st.queue.size})")
    Scroll {
        st.queue.forEachIndexed { i, f ->
            Text(f.title, Modifier.fillMaxWidth().clickable { st.jumpTo(i); st.panel = Panel.None }.padding(vertical = 10.dp),
                color = if (i == st.index) Color(KMediaPlayer.settings[PlayerKeys.controlHighlightColor]) else Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CustomisePanel() {
    val settings = KMediaPlayer.settings
    var enabled by remember { mutableStateOf(Shortcut.decode(settings[PlayerKeys.shortcutItems]).toSet()) }
    PanelTitle("Customise Items")
    Scroll {
        Shortcut.entries.filter { it != Shortcut.Customise && it.isAvailable }.forEach { sc ->
            Row(Modifier.fillMaxWidth().clickable {
                enabled = if (sc in enabled) enabled - sc else enabled + sc
                settings[PlayerKeys.shortcutItems] = Shortcut.entries.filter { it in enabled || it == Shortcut.Customise }.joinToString(",") { it.name }
            }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(sc in enabled, null, colors = CheckboxDefaults.colors(uncheckedColor = Dim))
                Text(sc.label, color = Color.White)
            }
        }
    }
}

/** Steps to turn on audio-track switching in a browser that plays only the first track. */
@Composable
private fun AudioTrackHelpPanel(help: AudioTrackHelp, onBack: () -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }
    PanelTitle("Switch audio in ${help.browser}", onBack = onBack)
    Scroll {
        Spacer(Modifier.height(12.dp))
        Text("${help.browser} plays only the first audio track until one setting is turned on. Do this once:",
            color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        help.steps.forEachIndexed { i, step ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Box(Modifier.size(22.dp).background(Color(0x26FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = Color.White, fontSize = 12.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(step, Modifier.weight(1f), color = Color.White, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(40.dp).background(Color.White, RoundedCornerShape(20.dp))
                .clickable { message = help.openSettings() },
            contentAlignment = Alignment.Center,
        ) { Text("Open settings", color = Color.Black, fontSize = 14.sp) }
        Spacer(Modifier.height(8.dp))
        Text(message ?: help.settingsLink, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}
