package org.app.kmediaplayer.ui.android

import android.app.PictureInPictureParams
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Rational
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaSession
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.PendingPlayback
import org.app.kmediaplayer.android.KMediaPlayerAndroid
import org.app.kmediaplayer.android.Media3Engine
import org.app.kmediaplayer.android.hybrid.HybridEngine
import org.app.kmediaplayer.engine.EngineListener
import org.app.kmediaplayer.model.MediaFile
import org.app.kmediaplayer.platform.PlatformMedia
import org.app.kmediaplayer.settings.OrientationMode
import org.app.kmediaplayer.settings.PlayerKeys
import org.app.kmediaplayer.ui.theme.PlayerTheme
import org.app.kmediaplayer.ui.video.Panel
import org.app.kmediaplayer.ui.video.PlayerWindow
import org.app.kmediaplayer.ui.video.VideoPlayerScreen
import org.app.kmediaplayer.ui.video.VideoPlayerState
import org.app.kmediaplayer.util.nowMs
import java.util.concurrent.atomic.AtomicInteger

/**
 * Full-screen video player. Open it with [KMediaPlayer.playVideo] / [KMediaPlayer.playUrl], or with
 * an ACTION_VIEW intent. Subclass and override [Content] to theme it.
 */
open class KMediaPlayerActivity : ComponentActivity(), PlayerWindow {
    final override lateinit var state: VideoPlayerState
        private set
    final override var inPip by mutableStateOf(false)
        private set
    override val pxPerCm: Float get() = resources.displayMetrics.xdpi / 2.54f

    private var surfaceView: SurfaceView? = null
    private var videoDisplayOn = true
    private var awaitingFileAccess = false
    private var session: MediaSession? = null
    private var lastBackAt = 0L
    private var closing = false
    private val settings get() = KMediaPlayer.settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KMediaPlayerAndroid.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        val engine = KMediaPlayer.engine
        state = VideoPlayerState(engine, lifecycleScope)
        if (!handleIntent(intent)) { finish(); return }
        session = runCatching { MediaSession.Builder(this, HybridEngine.sessionPlayer).setId("video").build() }.getOrNull()
        engine.addListener(windowListener)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    state.panel != Panel.None -> state.panel = Panel.None
                    state.locked -> state.showControls()
                    settings[PlayerKeys.doubleTapBack] && nowMs() - lastBackAt > 2000 -> {
                        lastBackAt = nowMs()
                        Toast.makeText(this@KMediaPlayerActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                    }
                    else -> close()
                }
            }
        })
        applyScreenSettings()
        setContent { Content() }
    }

    /** The player UI; override to wrap it in the host app's theme. */
    @Composable
    open fun Content() {
        PlayerTheme { VideoPlayerScreen(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Returns false if there is nothing to play. */
    private fun handleIntent(intent: Intent): Boolean {
        val data = intent.data
        when {
            intent.getBooleanExtra(KMediaPlayerAndroid.EXTRA_PENDING, false) -> {
                intent.removeExtra(KMediaPlayerAndroid.EXTRA_PENDING)
                when (val p = KMediaPlayer.pending) {
                    is PendingPlayback.Files -> state.start(p.files, p.index)
                    is PendingPlayback.Stream -> state.startUrl(p.url)
                    // KMediaPlayer.expandVideo(): an inline player hands over what it is playing.
                    null -> if ((Media3Engine.current?.mediaItemCount ?: 0) > 0) state.reattach() else return false
                }
                KMediaPlayer.pending = null
            }
            data != null -> {
                val scheme = data.scheme ?: ""
                if (scheme.startsWith("http") && !settings[PlayerKeys.playMediaLinks]) {
                    Toast.makeText(this, "Playing media links is disabled in Settings → General", Toast.LENGTH_LONG).show()
                    return false
                }
                if (scheme == "content" || scheme == "file") {
                    val known = KMediaPlayer.host.findFile(data.toString()) ?: PlatformMedia.lookup(data.toString())
                    val name = data.lastPathSegment?.substringAfterLast('/') ?: "Video"
                    state.start(listOf(known ?: MediaFile(data.hashCode().toLong(), data.toString(), data.path ?: name, name, 0, 0, 0)), 0)
                } else state.startUrl(data.toString())
            }
            (Media3Engine.current?.mediaItemCount ?: 0) > 0 -> state.reattach()
            else -> return false
        }
        PlatformMedia.stopBackgroundPlayback()
        return true
    }

    override fun applyScreenSettings() {
        if (settings[PlayerKeys.brightnessEnabled]) setBrightness(state.brightness)
        updateKeepScreenOn(state.isPlaying)
        applyOrientation()
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        when (settings[PlayerKeys.fullScreen]) {
            "off" -> controller.show(WindowInsetsCompat.Type.systemBars())
            else -> controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun showSystemBars(show: Boolean) {
        if (settings[PlayerKeys.fullScreen] != "auto") return
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (show && settings[PlayerKeys.softButtons] != "hide") c.show(WindowInsetsCompat.Type.statusBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun updateKeepScreenOn(playing: Boolean) {
        if (playing || settings[PlayerKeys.keepScreenOn]) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun setBrightness(level: Int) {
        window.attributes = window.attributes.apply { screenBrightness = (level / 30f).coerceIn(0.01f, 1f) }
    }

    override fun applyOrientation() {
        val forced = state.landscape
        requestedOrientation = when {
            forced == true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            forced == false -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> when (OrientationMode.valueOf(settings[PlayerKeys.orientation])) {
                OrientationMode.Video -> if (state.videoH > state.videoW && state.videoW > 0) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                OrientationMode.System -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                OrientationMode.Sensor -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                OrientationMode.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                OrientationMode.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                OrientationMode.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    override fun rotate() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        state.landscape = !isLandscape
        applyOrientation()
    }

    override fun enterPip(): Boolean {
        if (Build.VERSION.SDK_INT < 26 || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        val w = state.videoW.coerceAtLeast(1); val h = state.videoH.coerceAtLeast(1)
        val ratio = (w.toFloat() / h).coerceIn(0.42f, 2.38f)
        state.panel = Panel.None
        return runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational((ratio * 1000).toInt(), 1000)).build()) }.getOrDefault(false)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) state.controlsVisible = false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!closing && state.isPlaying && !state.backgroundPlay && settings[PlayerKeys.backgroundMode] == "pip") enterPip()
    }

    override fun onPause() {
        super.onPause()
        if (!isInPictureInPictureMode && !isFinishing && settings[PlayerKeys.pauseIfObscured] && !state.backgroundPlay) state.engine.pause()
    }

    override fun onStop() {
        super.onStop()
        if (!::state.isInitialized) return
        state.saveProgress()
        if (closing || isFinishing) return
        if (isInPictureInPictureMode) return
        val audioOnly = state.videoW == 0 && settings[PlayerKeys.backgroundAudio]
        if ((state.backgroundPlay || audioOnly) && state.engine.playWhenReady) {
            HybridEngine.setVideoEnabled(false)
            PlatformMedia.startBackgroundPlayback()
        } else state.engine.pause()
    }

    override fun requestFileAccess(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        awaitingFileAccess = true
        return runCatching {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        }.recoverCatching {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }.isSuccess
    }

    override fun onResume() {
        super.onResume()
        if (awaitingFileAccess && Build.VERSION.SDK_INT >= 30) {
            awaitingFileAccess = false
            if (Environment.isExternalStorageManager()) { state.engine.prepare(); state.engine.play() } else close()
        }
    }

    override fun onStart() {
        super.onStart()
        PlatformMedia.stopBackgroundPlayback()
        HybridEngine.setVideoEnabled(videoDisplayOn)
    }

    override fun close() {
        closing = true
        KMediaPlayer.onPlayerClosed(state.current?.uri, state.engine.position, state.engine.isPlaying)
        state.saveProgress()
        state.detach()
        state.engine.stop()
        state.engine.clearItems()
        PlatformMedia.stopBackgroundPlayback()
        finish()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        if (::state.isInitialized) {
            state.engine.removeListener(windowListener)
            val keepPlaying = state.backgroundPlay && state.engine.playWhenReady
            if (isFinishing && !closing && !keepPlaying) { state.detach(); state.engine.stop() }
        }
        super.onDestroy()
    }

    override fun screenshot() {
        val view = surfaceView?.takeIf { it.width > 0 && it.height > 0 } ?: run { state.toast = "Screenshot unavailable"; return }
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(view, bmp, { result ->
            if (result == PixelCopy.SUCCESS) saveScreenshot(bmp) else state.toast = "Screenshot failed"
        }, Handler(Looper.getMainLooper()))
    }

    private fun saveScreenshot(bmp: Bitmap) {
        val name = "MaxPlayerPro_${System.currentTimeMillis()}.png"
        val ok = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MaxPlayerPro")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.isSuccess
        if (ok) state.snack = "Screenshot saved to " + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).path + "/MaxPlayerPro" else state.toast = "Screenshot failed"
    }

    override fun setVideoDisplay(on: Boolean) {
        videoDisplayOn = on
        HybridEngine.setVideoEnabled(on)
    }

    override fun openStorePage(): Boolean =
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))) }.isSuccess

    private val pickerKeys = AtomicInteger()

    override fun pickFile(mimeTypes: List<String>, onPicked: (uri: String, name: String) -> Unit) {
        lateinit var launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
        launcher = activityResultRegistry.register("kmp_pick_${pickerKeys.incrementAndGet()}", ActivityResultContracts.OpenDocument()) { uri ->
            launcher.unregister()
            uri ?: return@register
            onPicked(uri.toString(), PlatformMedia.displayName(uri.toString()) ?: "subtitle.srt")
        }
        launcher.launch(mimeTypes.toTypedArray())
    }

    @Composable
    override fun VideoSurface(modifier: Modifier) {
        // SurfaceView, not TextureView: frames go straight to the display compositor (a hardware
        // overlay where available) instead of through the app's UI thread and GPU on every frame.
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { sv ->
                    surfaceView = sv
                    sv.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> HybridEngine.onSurfaceSizeChanged(v.width, v.height) }
                    HybridEngine.attachSurface(sv)
                }
            },
            modifier = modifier,
        )
        DisposableEffect(Unit) { onDispose { surfaceView?.let(HybridEngine::detachSurface); surfaceView = null } }
    }

    private val windowListener = object : EngineListener {
        override fun onIsPlayingChanged(playing: Boolean) { updateKeepScreenOn(playing) }
        override fun onVideoSizeChanged(width: Int, height: Int) { if (state.landscape == null) applyOrientation() }
    }
}
