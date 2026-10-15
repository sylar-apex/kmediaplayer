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
import platform.AVFoundation.AVLayerVideoGravityResize
import platform.AVFoundation.AVMediaCharacteristicAudible
import platform.AVFoundation.AVMediaCharacteristicLegible
import platform.AVFoundation.AVMediaSelectionGroup
import platform.AVFoundation.AVMediaSelectionOption
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.asset
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.mediaSelectionGroupForMediaCharacteristic
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.presentationSize
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.selectMediaOption
import platform.AVFoundation.selectedMediaOptionInMediaSelectionGroup
import platform.AVFoundation.setRate
import platform.AVFoundation.setVolume
import platform.AVFoundation.timeControlStatus
import platform.AVFoundation.volume
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObjectProtocol

/**
 * Apple's AVPlayer: hardware decoding of H.264/HEVC/ProRes, AAC/ALAC/AC-3/E-AC-3, MP4/MOV/M4V/HLS.
 * Formats outside that set go to VLCKit through [org.app.kmediaplayer.engine.FallbackBackend].
 */
class AvPlayerBackend : PlaybackBackend, IosVideoBackend {
    override var callback: BackendCallback? = null

    private val player = AVPlayer()
    private val layerView = PlayerLayerView(player)
    override val videoView: UIView get() = layerView

    private var observers = listOf<NSObjectProtocol>()
    private var poller: NSTimer? = null
    private var playWhenReady = false
    private var pendingSeekMs = -1L
    private var lastPlaying = false
    private var reportedSize = Pair(0, 0)

    override var state: EngineState = EngineState.Idle
        private set

    override val isPlaying: Boolean get() = player.timeControlStatus == AVPlayerTimeControlStatusPlaying
    override val position: Long get() = (CMTimeGetSeconds(player.currentTime()) * 1000).toLong().coerceAtLeast(0)
    override val duration: Long
        get() = player.currentItem?.duration?.let { CMTimeGetSeconds(it) }?.takeIf { it.isFinite() }?.times(1000)?.toLong() ?: 0

    override var volume: Float = 1f
        set(v) { field = v; player.setVolume(v) }

    override var speed: Float = 1f
        set(v) { field = v; if (playWhenReady) player.setRate(v) }

    override fun open(uri: String, startMs: Long, playWhenReady: Boolean, decoder: Decoder) {
        stop()
        val url = uri.toNsUrl() ?: return callback?.onError("ERROR_CODE_IO_FILE_NOT_FOUND") ?: Unit
        val item = AVPlayerItem(uRL = url)
        this.playWhenReady = playWhenReady
        pendingSeekMs = startMs
        setState(EngineState.Buffering)
        player.replaceCurrentItemWithPlayerItem(item)
        observe(item)
    }

    override fun play() {
        playWhenReady = true
        if (state == EngineState.Ended) seekTo(0, fast = false)
        player.setRate(speed)
    }

    override fun pause() {
        playWhenReady = false
        player.pause()
    }

    override fun stop() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        observers.forEach(NSNotificationCenter.defaultCenter::removeObserver)
        observers = emptyList()
        poller?.invalidate(); poller = null
        reportedSize = 0 to 0
        setState(EngineState.Idle)
    }

    override fun seekTo(positionMs: Long, fast: Boolean) {
        if (state == EngineState.Ended) setState(EngineState.Ready)
        val time = CMTimeMakeWithSeconds(positionMs / 1000.0, 600)
        if (fast) player.seekToTime(time) else player.seekToTime(time, kCMTimeZero.readValue(), kCMTimeZero.readValue())
    }

    // region tracks

    private fun group(type: TrackType): AVMediaSelectionGroup? {
        val asset = player.currentItem?.asset ?: return null
        val characteristic = if (type == TrackType.Audio) AVMediaCharacteristicAudible else AVMediaCharacteristicLegible
        return asset.mediaSelectionGroupForMediaCharacteristic(characteristic)
    }

    override fun tracks(type: TrackType): List<TrackInfo> {
        val g = group(type) ?: return emptyList()
        val selected = player.currentItem?.selectedMediaOptionInMediaSelectionGroup(g)
        return g.options.mapIndexedNotNull { i, o -> (o as? AVMediaSelectionOption)?.let { TrackInfo(i.toString(), it.displayName, it == selected) } }
    }

    override fun selectTrack(type: TrackType, id: String) {
        val g = group(type) ?: return
        val option = g.options.getOrNull(id.toIntOrNull() ?: return) as? AVMediaSelectionOption ?: return
        player.currentItem?.selectMediaOption(option, g)
    }

    override fun setTrackTypeDisabled(type: TrackType, disabled: Boolean) {
        if (!disabled) return
        val g = group(type) ?: return
        if (g.allowsEmptySelection) player.currentItem?.selectMediaOption(null, g)
    }

    override fun videoFormat(): FormatInfo? {
        val (w, h) = player.currentItem?.presentationSize?.useContents { width.toInt() to height.toInt() } ?: return null
        return if (w == 0) null else FormatInfo(width = w, height = h)
    }

    // endregion

    override fun release() = stop()

    private fun observe(item: AVPlayerItem) {
        val center = NSNotificationCenter.defaultCenter
        val main = NSOperationQueue.mainQueue
        observers = listOf(
            center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, item, main) { _ ->
                setState(EngineState.Ended); updatePlaying(); callback?.onEnded()
            },
            center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, item, main) { _ ->
                callback?.onError("ERROR_CODE_DECODING_FAILED")
            },
        )
        // AVPlayer reports status through KVO; a light main-thread poll keeps this backend free of KVO glue.
        poller = NSTimer.scheduledTimerWithTimeInterval(0.1, repeats = true) { _ -> poll(item) }
    }

    private fun poll(item: AVPlayerItem) {
        when (item.status) {
            AVPlayerItemStatusFailed -> { stop(); callback?.onError("ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED"); return }
            AVPlayerItemStatusReadyToPlay -> if (state == EngineState.Buffering && pendingSeekMs >= 0) {
                if (pendingSeekMs > 0) seekTo(pendingSeekMs, fast = false)
                pendingSeekMs = -1
                if (playWhenReady) player.setRate(speed)
            }
        }
        if (state != EngineState.Ended && pendingSeekMs < 0) {
            setState(if (player.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate) EngineState.Buffering else EngineState.Ready)
        }
        updatePlaying()
        item.presentationSize.useContents { width.toInt() to height.toInt() }.let { size ->
            if (size != reportedSize && size.first > 0) { reportedSize = size; callback?.onVideoSize(size.first, size.second) }
        }
    }

    private fun updatePlaying() {
        val playing = isPlaying
        if (playing != lastPlaying) { lastPlaying = playing; callback?.onPlayingChanged(playing) }
    }

    private fun setState(s: EngineState) {
        if (state == s) return
        state = s
        callback?.onStateChanged(s)
    }
}

/** A view whose AVPlayerLayer always fills its bounds; the Compose layout sets the picture size. */
private class PlayerLayerView(player: AVPlayer) : UIView(frame = CGRectZero.readValue()) {
    private val playerLayer = AVPlayerLayer.playerLayerWithPlayer(player).apply { videoGravity = AVLayerVideoGravityResize }

    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.frame = bounds
        CATransaction.commit()
    }
}

/** A file path or URL as an NSURL. */
internal fun String.toNsUrl(): NSURL? = when {
    startsWith("/") -> NSURL.fileURLWithPath(this)
    else -> NSURL.URLWithString(this)
}
