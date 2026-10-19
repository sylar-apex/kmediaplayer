package org.app.kmediaplayer.ui.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.app.kmediaplayer.util.nowMs
import org.app.kmediaplayer.web.WebBlobStore
import org.app.kmediaplayer.web.WebPlayback
import org.app.kmediaplayer.web.nativeAudioTracks
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.url.URL
import org.w3c.files.get

actual object PlatformVideoTools {
    /** Zoom and pan of the `<video>` element, applied by [PlatformVideoSurface] together with its clip path. */
    internal var pictureTransform by mutableStateOf(PictureTransform())
        private set

    actual fun setPictureTransform(scale: Float, panX: Float, panY: Float) { pictureTransform = PictureTransform(scale, panX, panY) }

    /** Steps for browsers that play only the first audio track until a setting is turned on. */
    actual fun audioTrackHelp(): AudioTrackHelp? {
        if (nativeAudioTracks) return null
        val ua = userAgent()
        val copy = { link: String -> { copyText(link); "Link copied. Paste it in a new tab." } }
        return when {
            "Firefox/" in ua -> AudioTrackHelp(
                "Firefox",
                listOf(
                    "Tap Open settings below to copy the link, open a new tab, paste it in the address bar and press Enter. Accept the warning.",
                    "Search for media.track.enabled and set it to true.",
                    "Come back here, reload the page and choose the audio track again.",
                ),
                "about:config", copy("about:config"),
            )
            else -> {
                val (name, scheme) = when {
                    "Edg/" in ua -> "Edge" to "edge"
                    "OPR/" in ua -> "Opera" to "opera"
                    else -> "Chrome" to "chrome"
                }
                val link = "$scheme://flags/#enable-experimental-web-platform-features"
                AudioTrackHelp(
                    name,
                    listOf(
                        "Tap Open settings below to copy the link, open a new tab, paste it in the address bar and press Enter.",
                        "Set \"Experimental Web Platform features\" to Enabled.",
                        "Press Relaunch, come back to this page and choose the audio track again.",
                    ),
                    link, copy(link),
                )
            }
        }
    }

    private val video get() = WebPlayback.backend.element
    private val scope = MainScope()

    /** A CSS filter: the video is an HTML element, so Compose overlays cannot dim it. */
    actual fun setPictureBrightness(factor: Float) {
        video.style.setProperty("filter", if (factor == 1f) "none" else "brightness($factor)")
    }

    /** Draws the current frame to a canvas and downloads it as a PNG. */
    actual fun captureFrame(onDone: (message: String) -> Unit) {
        if (video.videoWidth == 0) return onDone("No frame to capture yet")
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = video.videoWidth; canvas.height = video.videoHeight
        (canvas.getContext("2d") as CanvasRenderingContext2D).drawImage(video, 0.0, 0.0)
        // Browsers refuse to export frames of videos from another site that does not allow it (CORS).
        val png = runCatching { canvas.toDataURL("image/png") }.getOrNull()
            ?: return onDone("Screenshots aren't allowed for videos from other websites")
        val link = document.createElement("a") as HTMLAnchorElement
        link.href = png
        link.download = "MaxPlayer_${nowMs()}.png"
        link.click()
        onDone("Screenshot downloaded")
    }

    actual fun enterPip(): Boolean = requestPip(video)

    private var fullscreen by mutableStateOf(false)
    actual val isFullscreen: Boolean get() = fullscreen

    init {
        // Also tracks F11 / Esc, which change full screen without going through the button.
        document.addEventListener("fullscreenchange", { fullscreen = documentIsFullscreen() })
    }

    actual fun toggleFullscreen(): Boolean = toggleDocumentFullscreen()

    /** Reads the picked file into [WebBlobStore], so the subtitle parser can read it synchronously. */
    actual fun pickFile(extensions: List<String>, onPicked: (uri: String, name: String) -> Unit): Boolean {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = extensions.joinToString(",") { ".$it" }
        input.onchange = {
            input.files?.get(0)?.let { file ->
                scope.launch {
                    val url = URL.createObjectURL(file)
                    WebBlobStore.put(url, WebBlobStore.readAll(file))
                    onPicked(url, file.name)
                }
            }
            null
        }
        input.click()
        return true
    }
}

@JsFun("(v) => { if (!document.pictureInPictureEnabled || !v.requestPictureInPicture) return false; v.requestPictureInPicture().catch(() => {}); return true; }")
private external fun requestPip(video: org.w3c.dom.HTMLVideoElement): Boolean

@JsFun("() => { const d = document; if (!d.fullscreenEnabled) return false; if (d.fullscreenElement) d.exitFullscreen(); else d.documentElement.requestFullscreen().catch(() => {}); return true; }")
private external fun toggleDocumentFullscreen(): Boolean

@JsFun("() => !!document.fullscreenElement")
private external fun documentIsFullscreen(): Boolean

private fun userAgent(): String = js("navigator.userAgent")
private fun copyText(text: String): Unit = js("{ if (navigator.clipboard) navigator.clipboard.writeText(text).catch(() => {}); }")

/** Pan in device pixels, as Compose reports it. */
internal data class PictureTransform(val scale: Float = 1f, val panX: Float = 0f, val panY: Float = 0f)
