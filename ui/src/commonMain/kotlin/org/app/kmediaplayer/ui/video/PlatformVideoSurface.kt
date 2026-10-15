package org.app.kmediaplayer.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shows the shared engine's video for hosts that draw the player inside their own Compose window
 * (desktop, iOS, web). The modifier's size is the picture size; the video is scaled to fill it.
 */
@Composable
expect fun PlatformVideoSurface(modifier: Modifier)

