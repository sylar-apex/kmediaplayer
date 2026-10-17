@file:OptIn(ExperimentalForeignApi::class)

package org.app.kmediaplayer.ui.video

import androidx.compose.runtime.Composable
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

/** Hosts libVLC's video view (MobileVLCKit) under the Compose controls. */
@Composable
actual fun PlatformVideoSurface(modifier: Modifier) {
    UIKitView(
        factory = { UIView(frame = CGRectZero.readValue()).apply { backgroundColor = UIColor.blackColor } },
        modifier = modifier,
        update = { container ->
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

actual val videoNeedsCutouts: Boolean = false
