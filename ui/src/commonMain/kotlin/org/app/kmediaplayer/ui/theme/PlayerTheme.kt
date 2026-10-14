package org.app.kmediaplayer.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.settings.PlayerKeys
import org.app.kmediaplayer.ui.resources.Res
import org.app.kmediaplayer.ui.resources.mulish
import org.jetbrains.compose.resources.Font

val PlayerAccent: Color get() = Color(KMediaPlayer.settings[PlayerKeys.accentColor])

/** Text/icon colour that stays readable on top of [PlayerAccent]. */
val OnPlayerAccent: Color get() = if (PlayerAccent.luminance() > 0.5f) Color.Black else Color.White

/** Mulish, the player's typeface, as a variable font. */
@Composable
fun mulishFontFamily(): FontFamily = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(Res.font.mulish, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

/** Applies [font] to every text style of the default Material typography. */
fun Typography.withFont(font: FontFamily, keepLetterSpacing: Boolean = true): Typography {
    fun TextStyle.styled() = if (keepLetterSpacing) copy(fontFamily = font) else copy(fontFamily = font, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
    return copy(
        displayLarge = displayLarge.styled(), displayMedium = displayMedium.styled(), displaySmall = displaySmall.styled(),
        headlineLarge = headlineLarge.styled(), headlineMedium = headlineMedium.styled(), headlineSmall = headlineSmall.styled(),
        titleLarge = titleLarge.styled(), titleMedium = titleMedium.styled(), titleSmall = titleSmall.styled(),
        bodyLarge = bodyLarge.styled(), bodyMedium = bodyMedium.styled(), bodySmall = bodySmall.styled(),
        labelLarge = labelLarge.styled(), labelMedium = labelMedium.styled(), labelSmall = labelSmall.styled(),
    )
}

/** Default dark theme for the player when the host app supplies none. */
@Composable
fun PlayerTheme(content: @Composable () -> Unit) {
    KMediaPlayer.settings.version.collectAsState().value
    val accent = PlayerAccent
    val surface = Color(0xFF29333D)
    val scheme = darkColorScheme(
        primary = accent, secondary = accent, onPrimary = OnPlayerAccent, background = Color(0xFF16202B), surface = Color(0xFF16202B),
        surfaceVariant = surface, surfaceContainer = surface, surfaceContainerHigh = surface,
        onBackground = Color(0xFFDADDE4), onSurface = Color(0xFFDADDE4), onSurfaceVariant = Color(0xFF9AA6B2),
    )
    MaterialTheme(colorScheme = scheme, typography = Typography().withFont(mulishFontFamily())) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
