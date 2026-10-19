package org.app.kmediaplayer.ui.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.app.kmediaplayer.desktop.DesktopPlayback
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.lang.reflect.Proxy
import javax.swing.JComponent
import java.text.SimpleDateFormat
import java.util.Date

actual object PlatformVideoTools {
    actual fun audioTrackHelp(): AudioTrackHelp? = null
    actual fun setPictureTransform(scale: Float, panX: Float, panY: Float) {}

    /** Read by the desktop video surface when drawing frames. */
    var brightness by mutableFloatStateOf(1f)
        private set

    /** Set by the app window to switch full screen (the library has no access to the window). */
    var fullscreenToggle: (() -> Unit)? = null

    private var fullscreen by mutableStateOf(false)
    actual val isFullscreen: Boolean get() = fullscreen

    /**
     * Routes the macOS trackpad pinch on [root] to the player's zoom. AWT reports it only through the
     * non-exported `com.apple.eawt.event` API, so the app runs with
     * `--add-exports java.desktop/com.apple.eawt.event=ALL-UNNAMED`; without that (or off macOS) this does nothing.
     */
    fun installPinchZoom(root: JComponent) {
        if (!System.getProperty("os.name").orEmpty().startsWith("Mac")) return
        runCatching {
            val pkg = "com.apple.eawt.event"
            val magnification = Class.forName("$pkg.MagnificationListener")
            val listener = Proxy.newProxyInstance(magnification.classLoader, arrayOf(magnification)) { proxy, method, args ->
                when (method.name) {
                    "magnify" -> {
                        val amount = args[0].javaClass.getMethod("getMagnification").invoke(args[0]) as Double
                        ZoomInput.target?.zoomBy((1 + amount).toFloat())
                        null
                    }
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args[0]
                    "toString" -> "PinchZoom"
                    else -> null
                }
            }
            Class.forName("$pkg.GestureUtilities")
                .getMethod("addGestureListenerTo", JComponent::class.java, Class.forName("$pkg.GestureListener"))
                .invoke(null, root, listener)
        }.onFailure { System.err.println("Trackpad pinch zoom unavailable: $it") }
    }

    /** Called by the app window whenever its placement changes. */
    fun onFullscreenChanged(value: Boolean) { fullscreen = value }

    actual fun setPictureBrightness(factor: Float) { brightness = factor }

    /** Saves the frame on screen as a PNG under ~/Pictures/MaxPlayer. */
    actual fun captureFrame(onDone: (message: String) -> Unit) {
        val frames = DesktopPlayback.backend?.frames ?: return onDone("Screenshot unavailable")
        val png = frames.withFrame { pixels, w, h ->
            Image.makeRaster(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), pixels, w * 4).encodeToData(EncodedImageFormat.PNG)?.bytes
        } ?: return onDone("No frame to capture yet")
        val dir = File(System.getProperty("user.home"), "Pictures/MaxPlayer").apply { mkdirs() }
        val file = File(dir, "MaxPlayer_${SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())}.png")
        runCatching { file.writeBytes(png) }
            .onSuccess { onDone("Screenshot saved to ${file.path}") }
            .onFailure { onDone("Screenshot failed") }
    }

    actual fun enterPip(): Boolean = false

    actual fun toggleFullscreen(): Boolean = fullscreenToggle?.let { it(); true } ?: false

    actual fun pickFile(extensions: List<String>, onPicked: (uri: String, name: String) -> Unit): Boolean {
        val dialog = FileDialog(null as Frame?, "Open subtitle", FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
            isVisible = true
        }
        val name = dialog.file ?: return true
        onPicked(File(dialog.directory, name).toURI().toString(), name)
        return true
    }
}
