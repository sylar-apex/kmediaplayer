package org.app.kmediaplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.app.kmediaplayer.ui.theme.PlayerAccent

/** In-player dialog: square, translucent near-black with a hairline border, 469dp wide in landscape. */
@Composable
fun PlayerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    buttons: Boolean = true,
) {
    Dialog(onDismissRequest, edgeToEdgeDialogProperties()) {
        BoxWithConstraints {
            val width = if (maxWidth > maxHeight) 469.dp else maxWidth - 48.dp
            MaterialTheme(colorScheme = darkColorScheme(primary = PlayerAccent, onSurface = Color.White, onSurfaceVariant = Color(0xFFB0B6BE)), typography = MaterialTheme.typography) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Column(
                        modifier.offset(y = if (maxWidth > maxHeight) 9.dp else 0.dp).width(width).background(Color(0xF0161616)).border(1.dp, Color(0x33FFFFFF))
                            .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 8.dp),
                    ) {
                        title?.let {
                            ProvideTextStyle(TextStyle(color = Color.White, fontSize = 19.sp)) { it() }
                            Box(Modifier.padding(top = 8.dp))
                        }
                        text?.let { ProvideTextStyle(TextStyle(color = Color(0xFFDADDE4), fontSize = 15.sp, lineHeight = 22.sp)) { it() } }
                        if (buttons) Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                            dismissButton?.invoke()
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}
