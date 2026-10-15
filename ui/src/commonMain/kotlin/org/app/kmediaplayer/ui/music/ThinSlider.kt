package org.app.kmediaplayer.ui.music

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Slim 0..1 slider for seek and volume bars: a 4dp track whose thumb grows on hover or drag.
 * [onChange] fires while dragging; [onCommit] once on release or tap.
 */
@Composable
fun ThinSlider(
    value: Float,
    modifier: Modifier = Modifier,
    active: Color,
    inactive: Color,
    onChange: (Float) -> Unit = {},
    onCommit: (Float) -> Unit,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val thumb by animateDpAsState(if (drag != null || hovered) 7.dp else 5.dp, label = "thumb")
    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)
    val shown = (drag ?: value).coerceIn(0f, 1f)
    Canvas(
        modifier.height(24.dp).hoverable(hover)
            .pointerInput(Unit) { detectTapGestures { o -> commit((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { commit(it) }; drag = null },
                    onDragCancel = { drag = null },
                ) { c, _ -> c.consume(); drag = (c.position.x / size.width).coerceIn(0f, 1f).also { change(it) } }
            },
    ) {
        val y = size.height / 2
        val x = size.width * shown
        val stroke = 4.dp.toPx()
        drawLine(inactive, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        drawLine(active, Offset(0f, y), Offset(x, y), stroke, StrokeCap.Round)
        drawCircle(Color.White, thumb.toPx(), Offset(x, y))
        drawCircle(active, thumb.toPx() - 2.dp.toPx(), Offset(x, y))
    }
}
