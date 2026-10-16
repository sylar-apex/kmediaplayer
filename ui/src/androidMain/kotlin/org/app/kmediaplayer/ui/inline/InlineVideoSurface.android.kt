package org.app.kmediaplayer.ui.inline

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.app.kmediaplayer.android.hybrid.HybridEngine

/** A SurfaceView bound to the engine while it is on screen. */
@Composable
actual fun InlineVideoSurface(modifier: Modifier) {
    val context = LocalContext.current
    val view = remember {
        SurfaceView(context).also { sv ->
            sv.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> HybridEngine.onSurfaceSizeChanged(v.width, v.height) }
        }
    }
    DisposableEffect(view) {
        HybridEngine.attachSurface(view)
        onDispose { HybridEngine.detachSurface(view) }
    }
    AndroidView({ view }, modifier)
}
