package org.app.kmediaplayer.android

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import android.util.Log
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.android.exo.AndroidAudioEffects
import org.app.kmediaplayer.android.exo.ExoPlayerFactory
import org.app.kmediaplayer.android.exo.media3
import org.app.kmediaplayer.android.exo.seekParameters
import org.app.kmediaplayer.android.exo.toEngineState
import org.app.kmediaplayer.android.exo.toFormatInfo
import org.app.kmediaplayer.android.exo.toMedia3
import org.app.kmediaplayer.android.exo.toRepeatMode
import org.app.kmediaplayer.engine.AudioEffectsController
import org.app.kmediaplayer.engine.Decoder
import org.app.kmediaplayer.engine.EngineError
import org.app.kmediaplayer.engine.EngineListener
import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.engine.RepeatMode
import org.app.kmediaplayer.engine.TrackInfo
import org.app.kmediaplayer.engine.TrackType
import org.app.kmediaplayer.model.MediaFile
import org.app.kmediaplayer.settings.PlayerKeys
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [MediaEngine] backed by one process-wide Media3 [ExoPlayer]. It owns the play queue; the app talks
 * to it through [HybridEngine], which hands single items to libVLC when ExoPlayer cannot play them.
 */
@OptIn(UnstableApi::class)
object Media3Engine : MediaEngine {
    @Volatile override var decoder: Decoder = Decoder.HW

    private var player: ExoPlayer? = null
    private var effectsImpl: AndroidAudioEffects? = null
    private val listeners = CopyOnWriteArrayList<EngineListener>()

    /** The underlying player, created on first use. Exposed for surfaces and media sessions. */
    val exo: ExoPlayer get() = player ?: create().also { player = it }

    /** The player if it has been created, without creating it. */
    val current: ExoPlayer? get() = player

    /** The queue item currently selected, or null for an empty queue. */
    val currentItem: MediaItem? get() = player?.currentMediaItem

    private val dispatcher = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = listeners.forEach { it.onIsPlayingChanged(isPlaying) }
        override fun onPlaybackStateChanged(playbackState: Int) = listeners.forEach { it.onStateChanged(playbackState.toEngineState()) }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = listeners.forEach { it.onItemTransition() }
        override fun onVideoSizeChanged(videoSize: VideoSize) =
            listeners.forEach { it.onVideoSizeChanged((videoSize.width * videoSize.pixelWidthHeightRatio).toInt(), videoSize.height) }
        override fun onTracksChanged(tracks: Tracks) = listeners.forEach { it.onTracksChanged() }
        override fun onCues(cueGroup: CueGroup) {
            val lines = cueGroup.cues.mapNotNull { it.text?.toString() }
            listeners.forEach { it.onCues(lines) }
        }
        override fun onPlayerError(error: PlaybackException) = listeners.forEach { it.onError(EngineError(error.errorCodeName)) }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = listeners.forEach { it.onShuffleChanged(shuffleModeEnabled) }
        override fun onRepeatModeChanged(repeatMode: Int) = listeners.forEach { it.onRepeatChanged(repeatMode.toRepeatMode()) }
        override fun onEvents(player: Player, events: Player.Events) = listeners.forEach { it.onEvents() }
        override fun onAudioSessionIdChanged(audioSessionId: Int) = attachEffects(audioSessionId)
    }

    private fun create(): ExoPlayer =
        ExoPlayerFactory.create(KMediaPlayerAndroid.context, KMediaPlayer.settings) { decoder }.apply {
            addListener(dispatcher)
            addAnalyticsListener(frameDropLogger)
            attachEffects(audioSessionId)
        }

    /** Logs dropped video frames so stutter can be diagnosed from logcat (tag "KMediaFrames"). */
    private val frameDropLogger = object : AnalyticsListener {
        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            Log.w("KMediaFrames", "dropped $droppedFrames frames in ${elapsedMs}ms")
        }
        override fun onVideoFrameProcessingOffset(eventTime: AnalyticsListener.EventTime, totalProcessingOffsetUs: Long, frameCount: Int) {
            if (frameCount > 0) Log.i("KMediaFrames", "avg frame offset ${totalProcessingOffsetUs / frameCount / 1000}ms over $frameCount frames (negative = late)")
        }
        override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            Log.i("KMediaFrames", "video decoder $decoderName")
        }
    }

    private fun attachEffects(sessionId: Int) {
        effectsImpl?.release()
        effectsImpl = if (sessionId == C.AUDIO_SESSION_ID_UNSET) null else runCatching { AndroidAudioEffects(sessionId) }.getOrNull()
    }

    private data class TrackOption(val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean, val id: String)

    private fun options(type: Int): List<TrackOption> = exo.currentTracks.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
            val f = g.getTrackFormat(i)
            val label = listOfNotNull(f.label, f.language?.let { Locale(it).displayLanguage })
                .distinct().joinToString(" - ").ifEmpty { "Track ${i + 1}" }
            TrackOption(g, i, label, g.isTrackSelected(i), f.id ?: "${g.mediaTrackGroup.id}:$i")
        }
    }

    fun mediaItem(file: MediaFile): MediaItem =
        MediaItem.Builder()
            .setUri(Uri.parse(file.uri))
            .setMediaId(file.uri)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(file.title).setArtist(file.artist.ifEmpty { null }).setAlbumTitle(file.album.ifEmpty { null }).build(),
            )
            .build()

    private fun streamItem(url: String): MediaItem =
        MediaItem.Builder().setUri(url).setMediaId(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(Uri.parse(url).lastPathSegment ?: url).build()).build()

    override val isPlaying get() = exo.isPlaying
    override val playWhenReady get() = exo.playWhenReady
    override val state get() = exo.playbackState.toEngineState()
    override val position get() = exo.currentPosition
    override val duration get() = exo.duration.coerceAtLeast(0)
    override val currentIndex get() = exo.currentMediaItemIndex
    override val itemCount get() = exo.mediaItemCount
    override val title: String? get() = exo.mediaMetadata.title?.toString()
    override var volume: Float
        get() = exo.volume
        set(v) { exo.volume = v }
    override var speed: Float
        get() = exo.playbackParameters.speed
        set(v) { exo.setPlaybackSpeed(v) }
    override var shuffleEnabled: Boolean
        get() = exo.shuffleModeEnabled
        set(v) { exo.shuffleModeEnabled = v }
    override var repeatMode: RepeatMode
        get() = exo.repeatMode.toRepeatMode()
        set(v) { exo.repeatMode = v.toMedia3() }
    override var pauseAtEndOfItems: Boolean
        get() = exo.pauseAtEndOfMediaItems
        set(v) { exo.pauseAtEndOfMediaItems = v }
    override val effects: AudioEffectsController? get() = effectsImpl

    override fun hasNext() = exo.hasNextMediaItem()
    override fun hasPrevious() = exo.hasPreviousMediaItem()

    override fun setItems(files: List<MediaFile>, startIndex: Int, startPositionMs: Long) =
        exo.setMediaItems(files.map(::mediaItem), startIndex, startPositionMs)

    override fun setStream(url: String) = exo.setMediaItem(streamItem(url))
    override fun prepare() = exo.prepare()
    override fun play() = exo.play()
    override fun pause() = exo.pause()
    override fun stop() = exo.stop()
    override fun clearItems() = exo.clearMediaItems()
    override fun seekTo(positionMs: Long) = exo.seekTo(positionMs)
    override fun seekTo(index: Int, positionMs: Long) = exo.seekTo(index, positionMs)
    override fun seekToNext() = exo.seekToNextMediaItem()
    override fun seekToPrevious() = exo.seekToPreviousMediaItem()

    override fun addListener(listener: EngineListener) { if (listener !in listeners) listeners += listener }
    override fun removeListener(listener: EngineListener) { listeners -= listener }

    override fun tracks(type: TrackType): List<TrackInfo> = options(type.media3).map { TrackInfo(it.id, it.label, it.selected) }

    override fun selectTrack(type: TrackType, id: String) {
        val t = options(type.media3).firstOrNull { it.id == id } ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type.media3, false)
            .setOverrideForType(TrackSelectionOverride(t.group.mediaTrackGroup, t.index))
            .build()
    }

    override fun setTrackTypeDisabled(type: TrackType, disabled: Boolean) {
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type.media3, disabled).build()
    }

    override fun isTrackTypeDisabled(type: TrackType) = exo.trackSelectionParameters.disabledTrackTypes.contains(type.media3)

    override fun setPreferredLanguages(audio: String?, text: String?) {
        val b = exo.trackSelectionParameters.buildUpon()
        audio?.takeIf { it.isNotBlank() }?.let { b.setPreferredAudioLanguage(it) }
        text?.takeIf { it.isNotBlank() }?.let { b.setPreferredTextLanguage(it) }
        exo.trackSelectionParameters = b.build()
    }

    override fun videoFormat() = exo.videoFormat?.toFormatInfo()
    override fun audioFormat() = exo.audioFormat?.toFormatInfo()

    override fun applySettings() {
        val p = player ?: return
        val s = KMediaPlayer.settings
        p.setAudioAttributes(p.audioAttributes, s[PlayerKeys.playAlone])
        p.setHandleAudioBecomingNoisy(s[PlayerKeys.pauseOnHeadsetDisconnect])
        p.setSeekParameters(s.seekParameters())
    }

    override fun release() {
        effectsImpl?.release(); effectsImpl = null
        player?.release(); player = null
    }
}
