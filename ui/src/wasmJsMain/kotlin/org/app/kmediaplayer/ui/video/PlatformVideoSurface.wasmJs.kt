package org.app.kmediaplayer.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.window
import org.app.kmediaplayer.web.WebPlayback
import org.w3c.dom.HTMLElement

/**
 * Embeds the engine's HTML `<video>` element, sized to the video box by Compose. Compose places it
 * above its canvas, so the player's overlays (controls, panels, dialogs, subtitles) are cut out of
 * the video with a CSS clip-path and show through from the canvas; see [VideoCutouts]. The element
 * is click-through so taps reach the player's gestures.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformVideoSurface(modifier: Modifier) {
    val element = WebPlayback.backend.element
    val cutouts = LocalVideoCutouts.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    // Browsers send keys to the focused element; focus the app canvas so shortcuts work without a click first.
    LaunchedEffect(Unit) { focusComposeCanvas() }
    LaunchedEffect(element, cutouts) {
        snapshotFlow { bounds to cutouts?.rects?.values?.toList().orEmpty() }
            .collect { (box, holes) -> element.style.setProperty("clip-path", clipPath(box, holes)) }
    }
    HtmlElementView(
        factory = { element },
        modifier = modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
        update = { it.clickThrough() },
    )
}

actual val videoNeedsCutouts: Boolean = true

/**
 * The video's own rectangle minus every overlay, as an even-odd CSS path in the element's
 * coordinates (CSS pixels; Compose reports device pixels).
 */
private fun clipPath(box: Rect, holes: List<Rect>): String {
    if (box.isEmpty) return "none"
    val ratio = window.devicePixelRatio.toFloat()
    val w = box.width / ratio
    val h = box.height / ratio
    val path = StringBuilder("M0,0 H$w V$h H0 Z")
    holes.mapNotNull { it.intersectOrNull(box) }.forEach { r ->
        val x1 = (r.left - box.left) / ratio; val y1 = (r.top - box.top) / ratio
        val x2 = (r.right - box.left) / ratio; val y2 = (r.bottom - box.top) / ratio
        path.append(" M$x1,$y1 H$x2 V$y2 H$x1 Z")
    }
    return "path(evenodd, '$path')"
}

private fun Rect.intersectOrNull(other: Rect): Rect? = intersect(other).takeIf { it.width > 0 && it.height > 0 }

/** `pointer-events: none` on the element and the interop wrappers Compose placed around it. */
private fun HTMLElement.clickThrough(levels: Int = 3) {
    var node: HTMLElement? = this
    repeat(levels) {
        node?.style?.setProperty("pointer-events", "none")
        node = node?.parentElement as? HTMLElement
    }
}

@JsFun("""() => {
    const find = (root) => { for (const e of root.querySelectorAll('*')) { if (e.tagName === 'CANVAS') return e; if (e.shadowRoot) { const c = find(e.shadowRoot); if (c) return c; } } return null; };
    const c = find(document); if (c) { if (!c.hasAttribute('tabindex')) c.setAttribute('tabindex', '0'); c.focus({ preventScroll: true }); }
}""")
private external fun focusComposeCanvas()
