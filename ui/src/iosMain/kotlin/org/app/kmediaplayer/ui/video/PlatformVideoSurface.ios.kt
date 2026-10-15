@file:OptIn(ExperimentalForeignApi::class)

package org.app.kmediaplayer.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import org.app.kmediaplayer.ios.IosPlayback
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth

/**
 * Hosts the active backend's video view (AVPlayerLayer or VLCKit) under the Compose controls. The
 * view is swapped when an item moves between AVPlayer and VLCKit.
 */
@Composable
actual fun PlatformVideoSurface(modifier: Modifier) {
    var backendVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        IosPlayback.backend.onActiveChanged = { backendVersion++ }
        onDispose { IosPlayback.backend.onActiveChanged = null }
    }
    UIKitView(
        factory = { UIView(frame = CGRectZero.readValue()).apply { backgroundColor = UIColor.blackColor } },
        modifier = modifier,
        update = { container ->
            backendVersion
            val video = IosPlayback.currentVideoView ?: return@UIKitView
            if (video.superview != container) {
                container.subviews.forEach { (it as UIView).removeFromSuperview() }
                video.setFrame(container.bounds)
                video.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
                container.addSubview(video)
            }
        },
    )
}
