package org.app.kmediaplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.settings.BoolSetting
import org.app.kmediaplayer.settings.ChoiceSetting
import org.app.kmediaplayer.settings.ColorSetting
import org.app.kmediaplayer.settings.FloatSetting
import org.app.kmediaplayer.settings.IntSetting
import org.app.kmediaplayer.settings.Setting
import org.app.kmediaplayer.settings.TextSetting
import org.app.kmediaplayer.ui.common.PlayerDialog
import org.app.kmediaplayer.ui.resources.Res
import org.app.kmediaplayer.ui.resources.ic_check
import org.app.kmediaplayer.ui.theme.PlayerAccent
import org.jetbrains.compose.resources.painterResource

/** Re-reads settings whenever any setting changes. */
@Composable
fun settingsVersion(): Int = KMediaPlayer.settings.version.collectAsState().value

val SummaryColor = Color(0xFFC3CAD3)

@Composable
fun SectionHeader(title: String, first: Boolean = false) {
    Column {
        if (!first) HorizontalDivider(color = Color(0xFF26303C), thickness = 1.dp)
        Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, lineHeight = 17.sp,
            modifier = Modifier.padding(start = 16.dp, top = if (first) 16.dp else 12.5.dp, bottom = if (first) 0.dp else 12.5.dp))
    }
}

@Composable
fun ActionRow(title: String, summary: String = "", enabled: Boolean = true, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 16.dp)) {
            Text(title, fontSize = 16.sp, lineHeight = 19.5.sp, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (summary.isNotEmpty()) Text(summary, fontSize = 13.5.sp, lineHeight = 16.5.sp, color = if (enabled) SummaryColor else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) { Spacer(Modifier.width(16.dp)); trailing() }
    }
}

@Composable
fun MxSwitch(checked: Boolean, onChange: (Boolean) -> Unit, color: Color = PlayerAccent) {
    Box(Modifier.size(47.dp, 27.dp).clickable(interactionSource = null, indication = null) { onChange(!checked) }, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.padding(horizontal = 4.dp).size(39.dp, 14.dp).background(if (checked) color.copy(alpha = 0.5f) else Color(0x80FFFFFF), RoundedCornerShape(7.dp)))
        Box(Modifier.padding(start = if (checked) 23.dp else 0.dp).size(24.dp).background(if (checked) color else Color(0xFFECECEC), CircleShape))
    }
}

@Composable
fun MxRadio(selected: Boolean) {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(20.dp).border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0xFFE0E4EA), CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

@Composable
fun MxCheckbox(checked: Boolean) {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(19.dp)
                .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(2.dp))
                .border(2.dp, if (checked) MaterialTheme.colorScheme.primary else Color(0xFFE0E4EA), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(15.dp), tint = Color(0xFF17202B))
        }
    }
}

/** Renders any registered setting with the control appropriate to its type. */
@Composable
fun SettingRow(setting: Setting<*>, compact: Boolean = false, onChanged: () -> Unit = {}) {
    settingsVersion()
    when (setting) {
        is BoolSetting -> BoolRow(setting, compact, onChanged)
        is ChoiceSetting -> ChoiceRow(setting, onChanged)
        is IntSetting -> IntRow(setting, onChanged)
        is FloatSetting -> FloatRow(setting, onChanged)
        is TextSetting -> TextRow(setting, onChanged)
        is ColorSetting -> ColorRow(setting, onChanged)
    }
}

@Composable
private fun BoolRow(s: BoolSetting, compact: Boolean, onChanged: () -> Unit) {
    val settings = KMediaPlayer.settings
    val v = settings[s]
    if (compact) {
        Row(Modifier.fillMaxWidth().clickable { settings[s] = !v; onChanged() }.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.title, Modifier.weight(1f), fontSize = 14.sp)
            Checkbox(v, { settings[s] = it; onChanged() })
        }
        return
    }
    ActionRow(s.title, s.summary, trailing = { MxCheckbox(v) }) { settings[s] = !v; onChanged() }
}

@Composable
private fun ChoiceRow(s: ChoiceSetting, onChanged: () -> Unit) {
    val settings = KMediaPlayer.settings
    var open by remember { mutableStateOf(false) }
    val v = settings[s]
    ActionRow(s.title, s.labelOf(v).ifEmpty { "Default" }) { open = true }
    if (open) {
        PlayerDialog(
            onDismissRequest = { open = false },
            title = { Text(s.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    s.choices.forEach { (key, label) ->
                        Row(Modifier.fillMaxWidth().clickable { settings[s] = key; onChanged(); open = false }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(key == v, null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ open = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IntRow(s: IntSetting, onChanged: () -> Unit) {
    val settings = KMediaPlayer.settings
    val v = settings[s]
    if (s.range.last - s.range.first <= 400) {
        SliderRow(s.title, v.toFloat(), s.range.first.toFloat()..s.range.last.toFloat(), "$v${s.unit.let { if (it.isEmpty()) "" else if (it == "%") "%" else " $it" }}") {
            settings[s] = it.toInt(); onChanged()
        }
    } else NumberDialogRow(s.title, v.toString()) { it.toIntOrNull()?.let { n -> settings[s] = n.coerceIn(s.range); onChanged() } }
}

@Composable
private fun FloatRow(s: FloatSetting, onChanged: () -> Unit) {
    val settings = KMediaPlayer.settings
    val v = settings[s]
    NumberDialogRow(s.title, "${(kotlin.math.round(v * 10) / 10f)} ${s.unit}".trim(), s.summary) {
        it.toFloatOrNull()?.let { n -> settings[s] = n.coerceIn(s.range); onChanged() }
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 15.sp)
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(local, { local = it }, valueRange = range, onValueChangeFinished = { onChange(local) })
    }
}

@Composable
private fun NumberDialogRow(title: String, current: String, summary: String = "", onDone: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ActionRow(title, if (summary.isEmpty()) current else "$current · $summary") { open = true }
    if (open) TextInputDialog(title, current.substringBefore(' '), KeyboardType.Decimal, { open = false }) { onDone(it); open = false }
}

@Composable
private fun TextRow(s: TextSetting, onChanged: () -> Unit) {
    val settings = KMediaPlayer.settings
    var open by remember { mutableStateOf(false) }
    val v = settings[s]
    ActionRow(s.title, v.ifEmpty { s.summary }) { open = true }
    if (open) TextInputDialog(s.title, v, KeyboardType.Text, { open = false }, multiline = s.key == "list.scan_folders") {
        settings[s] = it; onChanged(); open = false
    }
}

@Composable
fun TextInputDialog(title: String, initial: String, keyboard: KeyboardType = KeyboardType.Text, onDismiss: () -> Unit, multiline: Boolean = false, label: String = "", onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    PlayerDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(text, { text = it }, singleLine = !multiline, label = if (label.isEmpty()) null else ({ Text(label) }),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
        },
        confirmButton = { TextButton({ onDone(text.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("CANCEL") } },
    )
}

private val palette = listOf(
    0xFFFFFFFF, 0xFFE0E0E0, 0xFF9E9E9E, 0xFF000000, 0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFF44336,
    0xFFE91E63, 0xFF9C27B0, 0xFF3F51B5, 0xFF2196F3, 0xFF5EB4FF, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50,
)

@Composable
private fun ColorRow(s: ColorSetting, onChanged: () -> Unit) {
    val settings = KMediaPlayer.settings
    var open by remember { mutableStateOf(false) }
    val v = settings[s]
    ActionRow(s.title, trailing = { Box(Modifier.size(30.dp).background(Color(v)).border(1.dp, Color(0xFF3A4654))) }) { open = true }
    if (open) ColorPickerDialog(s.title, v, { open = false }) { settings[s] = it; onChanged(); open = false }
}

@Composable
fun ColorSwatch(color: Color, sizeDp: Int, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.size(sizeDp.dp).background(color, CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    )
}

@Composable
fun ColorPickerDialog(title: String, current: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    var alpha by remember { mutableFloatStateOf(((current ushr 24) and 0xFF).toFloat()) }
    var rgb by remember { mutableStateOf(current and 0xFFFFFF) }
    PlayerDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    palette.forEach { c -> ColorSwatch(Color(c), 34, (c and 0xFFFFFF) == rgb) { rgb = c and 0xFFFFFF } }
                }
                Spacer(Modifier.padding(6.dp))
                Text("Opacity ${(alpha / 255 * 100).toInt()}%")
                Slider(alpha, { alpha = it }, valueRange = 0f..255f)
                Box(Modifier.fillMaxWidth().padding(top = 4.dp).size(36.dp).background(Color((alpha.toLong() shl 24) or rgb), RoundedCornerShape(6.dp)))
            }
        },
        confirmButton = { TextButton({ onPick((alpha.toLong() shl 24) or rgb) }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
