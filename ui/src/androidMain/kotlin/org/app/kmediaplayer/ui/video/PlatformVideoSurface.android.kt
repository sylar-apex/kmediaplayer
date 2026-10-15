package org.app.kmediaplayer.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Android renders video in [org.app.kmediaplayer.ui.android.KMediaPlayerActivity]'s SurfaceView instead. */
@Composable
actual fun PlatformVideoSurface(modifier: Modifier) = Box(modifier)
