package org.app.kmediaplayer.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import org.app.kmediaplayer.web.WebPlayback

/** Embeds the engine's HTML `<video>` element; Compose keeps it under the controls and sized to the video box. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformVideoSurface(modifier: Modifier) {
    HtmlElementView(factory = { WebPlayback.backend.element }, modifier = modifier)
}
