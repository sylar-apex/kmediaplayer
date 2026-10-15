package org.app.kmediaplayer.ui.video

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where the player draws over the video. On the web the video is an HTML element *above* the
 * Compose canvas, so it would hide the controls; the web video surface cuts these rectangles out of
 * the video (CSS clip-path) and the controls on the canvas show through. Other platforms draw video
 * inside Compose and never provide a registry, so tagging costs nothing there.
 */
class VideoCutouts {
    /** Overlay bounds in window pixels, by overlay. */
    val rects = mutableStateMapOf<Any, Rect>()
}

val LocalVideoCutouts = compositionLocalOf<VideoCutouts?> { null }

/** Marks this overlay as drawn over the video, so the web player keeps the video out of its area. */
fun Modifier.videoCutout(): Modifier = composed {
    val registry = LocalVideoCutouts.current ?: return@composed Modifier
    val key = remember { Any() }
    DisposableEffect(registry) { onDispose { registry.rects.remove(key) } }
    Modifier.onGloballyPositioned { registry.rects[key] = it.boundsInWindow() }
}
