@file:OptIn(ExperimentalForeignApi::class)

package org.app.kmediaplayer.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import org.app.kmediaplayer.engine.BackendCallback
import org.app.kmediaplayer.engine.Decoder
import org.app.kmediaplayer.engine.EngineState
import org.app.kmediaplayer.engine.FormatInfo
import org.app.kmediaplayer.engine.PlaybackBackend
import org.app.kmediaplayer.engine.TrackInfo
import org.app.kmediaplayer.engine.TrackType
import org.app.kmediaplayer.engine.vlcOptions
import org.app.kmediaplayer.model.StreamExtras
import org.videolan.vlckit.VLCMedia
import org.videolan.vlckit.VLCMediaPlayer
import org.videolan.vlckit.VLCMediaPlayerDelegateProtocol
import org.videolan.vlckit.VLCMediaPlayerState
import org.videolan.vlckit.VLCTime
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSNotification
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject

/**
 * iOS playback through libVLC (VideoLAN's MobileVLCKit), the same engine as Android and desktop, so every
 * format plays and behaves alike. libVLC draws into [videoView] with VideoToolbox hardware decoding.
 */
class VlcKitBackend : PlaybackBackend, IosVideoBackend {
    override var callback: BackendCallback? = null

    private val view = UIView(frame = CGRectZero.readValue()).apply { backgroundColor = UIColor.blackColor }
    override val videoView: UIView get() = view

    private val player = VLCMediaPlayer().apply { drawable = view }
    private val events = Events()
    private var started = false
    private var pendingSeekMs = -1L
    private var reportedSize = Pair(0, 0)

    override var state: EngineState = EngineState.Idle
        private set

    override val isPlaying: Boolean get() = player.isPlaying()
    override val position: Long get() = player.time.value?.longValue?.coerceAtLeast(0) ?: 0
    override val duration: Long get() = player.media?.length?.value?.longValue?.coerceAtLeast(0) ?: 0

    override var volume: Float = 1f
        set(v) { field = v; applyVolume() }

    override var speed: Float = 1f
        set(v) { field = v; player.rate = v }

    init { player.delegate = events }

    override fun open(uri: String, startMs: Long, playWhenReady: Boolean, decoder: Decoder, extras: StreamExtras) {
        val url = uri.toNsUrl() ?: return callback?.onError("ERROR_CODE_IO_FILE_NOT_FOUND") ?: Unit
        val media = VLCMedia.mediaWithURL(url).apply {
            if (startMs > 0) addOption(":start-time=${startMs / 1000.0}")
            if (decoder == Decoder.SW) addOption(":videotoolbox=0")
            extras.vlcOptions().forEach { addOption(it) }
        }
        started = false
        pendingSeekMs = -1
        reportedSize = 0 to 0
        player.media = media
        player.rate = speed
        setState(EngineState.Buffering)
        if (playWhenReady) start()
    }

    private fun start() {
        started = true
        player.play()
    }

    override fun play() { if (!started) start() else player.play() }
    override fun pause() { if (started) player.pause() }

    override fun stop() {
        started = false
        player.stop()
        setState(EngineState.Idle)
    }

    override fun seekTo(positionMs: Long, fast: Boolean) {
        if (!started) { pendingSeekMs = positionMs; return }
        if (state == EngineState.Ended) player.play()
        player.time = VLCTime.timeWithInt(positionMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
    }

    // region tracks

    override fun tracks(type: TrackType): List<TrackInfo> {
        val (names, ids, selected) = when (type) {
            TrackType.Audio -> Triple(player.audioTrackNames, player.audioTrackIndexes, player.currentAudioTrackIndex)
            TrackType.Text -> Triple(player.videoSubTitlesNames, player.videoSubTitlesIndexes, player.currentVideoSubTitleIndex)
        }
        return ids.indices.mapNotNull { i ->
            val id = (ids[i] as? platform.Foundation.NSNumber)?.intValue ?: return@mapNotNull null
            if (id < 0) return@mapNotNull null
            TrackInfo(id.toString(), names.getOrNull(i) as? String ?: "Track $id", id == selected)
        }
    }

    override fun selectTrack(type: TrackType, id: String) {
        val trackId = id.toIntOrNull() ?: return
        if (type == TrackType.Audio) player.currentAudioTrackIndex = trackId else player.currentVideoSubTitleIndex = trackId
    }

    override fun setTrackTypeDisabled(type: TrackType, disabled: Boolean) {
        if (!disabled) return
        if (type == TrackType.Audio) player.currentAudioTrackIndex = -1 else player.currentVideoSubTitleIndex = -1
    }

    override fun videoFormat(): FormatInfo? {
        val (w, h) = player.videoSize.useContents { width.toInt() to height.toInt() }
        return if (w > 0) FormatInfo(width = w, height = h) else null
    }

    // endregion

    override fun release() {
        player.delegate = null
        player.stop()
        player.drawable = null
    }

    private fun applyVolume() { player.audio?.volume = (volume * 100).toInt().coerceIn(0, 200) }

    private fun setState(s: EngineState) {
        if (state == s) return
        state = s
        callback?.onStateChanged(s)
    }

    private fun reportVideoSize() {
        val size = player.videoSize.useContents { width.toInt() to height.toInt() }
        if (size.first > 0 && size != reportedSize) { reportedSize = size; callback?.onVideoSize(size.first, size.second) }
    }

    /** VLCKit delivers these on the main thread. */
    private inner class Events : NSObject(), VLCMediaPlayerDelegateProtocol {
        override fun mediaPlayerStateChanged(aNotification: NSNotification) {
            when (player.state) {
                VLCMediaPlayerState.VLCMediaPlayerStateOpening, VLCMediaPlayerState.VLCMediaPlayerStateBuffering ->
                    if (!player.isPlaying()) setState(EngineState.Buffering)
                VLCMediaPlayerState.VLCMediaPlayerStatePlaying -> {
                    setState(EngineState.Ready)
                    applyVolume()
                    if (pendingSeekMs >= 0) { val ms = pendingSeekMs; pendingSeekMs = -1; seekTo(ms, fast = false) }
                    callback?.onPlayingChanged(true)
                    callback?.onTracksChanged()
                }
                VLCMediaPlayerState.VLCMediaPlayerStatePaused -> { setState(EngineState.Ready); callback?.onPlayingChanged(false) }
                VLCMediaPlayerState.VLCMediaPlayerStateEnded -> { setState(EngineState.Ended); callback?.onPlayingChanged(false); callback?.onEnded() }
                VLCMediaPlayerState.VLCMediaPlayerStateError -> { setState(EngineState.Idle); callback?.onError("ERROR_CODE_DECODING_FORMAT_UNSUPPORTED") }
                VLCMediaPlayerState.VLCMediaPlayerStateESAdded -> callback?.onTracksChanged()
                else -> callback?.onPlayingChanged(false)
            }
        }

        override fun mediaPlayerTimeChanged(aNotification: NSNotification) {
            if (state == EngineState.Buffering) setState(EngineState.Ready)
            reportVideoSize()
        }
    }
}

/** A file path or URL as an NSURL. */
internal fun String.toNsUrl(): NSURL? = when {
    startsWith("/") -> NSURL.fileURLWithPath(this)
    else -> NSURL.URLWithString(this)
}
