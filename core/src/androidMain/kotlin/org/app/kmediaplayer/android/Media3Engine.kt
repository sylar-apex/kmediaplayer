package org.app.kmediaplayer.android

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.engine.AudioEffectsController
import org.app.kmediaplayer.engine.Decoder
import org.app.kmediaplayer.engine.EngineError
import org.app.kmediaplayer.engine.EngineListener
import org.app.kmediaplayer.engine.EngineState
import org.app.kmediaplayer.engine.FormatInfo
import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.engine.RepeatMode
import org.app.kmediaplayer.engine.TrackInfo
import org.app.kmediaplayer.engine.TrackType
import org.app.kmediaplayer.model.MediaFile
import org.app.kmediaplayer.settings.PlayerKeys
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/** [MediaEngine] backed by one process-wide Media3 [ExoPlayer]. */
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

    private val dispatcher = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = listeners.forEach { it.onIsPlayingChanged(isPlaying) }
        override fun onPlaybackStateChanged(playbackState: Int) = listeners.forEach { it.onStateChanged(mapState(playbackState)) }
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
        override fun onRepeatModeChanged(repeatMode: Int) = listeners.forEach { it.onRepeatChanged(mapRepeat(repeatMode)) }
        override fun onEvents(player: Player, events: Player.Events) = listeners.forEach { it.onEvents() }
        override fun onAudioSessionIdChanged(audioSessionId: Int) = attachEffects(audioSessionId)
    }

    private fun create(): ExoPlayer {
        val context = KMediaPlayerAndroid.context
        val selector = MediaCodecSelector { mimeType, secure, tunneling ->
            val all = MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling)
            when (decoder) {
                Decoder.SW -> all.sortedBy { !it.softwareOnly }
                else -> all
            }
        }
        val renderers = DefaultRenderersFactory(context)
            .setMediaCodecSelector(selector)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val settings = KMediaPlayer.settings
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(settings[PlayerKeys.userAgent].ifBlank { "MaxPlayerPro" })
        val sources = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                settings[PlayerKeys.playAlone],
            )
            .setHandleAudioBecomingNoisy(settings[PlayerKeys.pauseOnHeadsetDisconnect])
            .setSeekBackIncrementMs(settings[PlayerKeys.moveInterval] * 1000L)
            .setSeekForwardIncrementMs(settings[PlayerKeys.moveInterval] * 1000L)
            .build().apply {
                setSeekParameters(if (settings[PlayerKeys.fastSeek]) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
                addListener(dispatcher)
                attachEffects(audioSessionId)
            }
    }

    private fun attachEffects(sessionId: Int) {
        effectsImpl?.release()
        effectsImpl = if (sessionId == C.AUDIO_SESSION_ID_UNSET) null else runCatching { AndroidAudioEffects(sessionId) }.getOrNull()
    }

    private fun mapState(s: Int) = when (s) {
        Player.STATE_BUFFERING -> EngineState.Buffering
        Player.STATE_READY -> EngineState.Ready
        Player.STATE_ENDED -> EngineState.Ended
        else -> EngineState.Idle
    }

    private fun mapRepeat(m: Int) = when (m) {
        Player.REPEAT_MODE_ONE -> RepeatMode.One
        Player.REPEAT_MODE_ALL -> RepeatMode.All
        else -> RepeatMode.Off
    }

    private fun trackType(t: TrackType) = if (t == TrackType.Audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT

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

    private fun Format.info() = FormatInfo(
        containerMimeType = containerMimeType, codecs = codecs, sampleMimeType = sampleMimeType,
        width = width, height = height, frameRate = frameRate, bitrate = bitrate,
        channelCount = channelCount, sampleRate = sampleRate, language = language,
    )

    override val isPlaying get() = exo.isPlaying
    override val playWhenReady get() = exo.playWhenReady
    override val state get() = mapState(exo.playbackState)
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
        get() = mapRepeat(exo.repeatMode)
        set(v) {
            exo.repeatMode = when (v) { RepeatMode.Off -> Player.REPEAT_MODE_OFF; RepeatMode.One -> Player.REPEAT_MODE_ONE; RepeatMode.All -> Player.REPEAT_MODE_ALL }
        }
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

    override fun tracks(type: TrackType): List<TrackInfo> = options(trackType(type)).map { TrackInfo(it.id, it.label, it.selected) }

    override fun selectTrack(type: TrackType, id: String) {
        val t = options(trackType(type)).firstOrNull { it.id == id } ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(trackType(type), false)
            .setOverrideForType(TrackSelectionOverride(t.group.mediaTrackGroup, t.index))
            .build()
    }

    override fun setTrackTypeDisabled(type: TrackType, disabled: Boolean) {
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(trackType(type), disabled).build()
    }

    override fun isTrackTypeDisabled(type: TrackType) = exo.trackSelectionParameters.disabledTrackTypes.contains(trackType(type))

    override fun setPreferredLanguages(audio: String?, text: String?) {
        val b = exo.trackSelectionParameters.buildUpon()
        audio?.takeIf { it.isNotBlank() }?.let { b.setPreferredAudioLanguage(it) }
        text?.takeIf { it.isNotBlank() }?.let { b.setPreferredTextLanguage(it) }
        exo.trackSelectionParameters = b.build()
    }

    override fun videoFormat() = exo.videoFormat?.info()
    override fun audioFormat() = exo.audioFormat?.info()

    override fun applySettings() {
        val p = player ?: return
        val s = KMediaPlayer.settings
        p.setAudioAttributes(p.audioAttributes, s[PlayerKeys.playAlone])
        p.setHandleAudioBecomingNoisy(s[PlayerKeys.pauseOnHeadsetDisconnect])
        p.setSeekParameters(if (s[PlayerKeys.fastSeek]) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
    }

    override fun release() {
        effectsImpl?.release(); effectsImpl = null
        player?.release(); player = null
    }
}

/** Equalizer, bass boost, reverb and loudness (volume boost) bound to one audio session. */
private class AndroidAudioEffects(sessionId: Int) : AudioEffectsController {
    val equalizer = Equalizer(0, sessionId)
    val bassBoost: BassBoost? = runCatching { BassBoost(0, sessionId) }.getOrNull()
    val reverb: PresetReverb? = runCatching { PresetReverb(0, sessionId) }.getOrNull()
    val loudness: LoudnessEnhancer? = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()

    init { applySaved() }

    override val presetCount: Int get() = equalizer.numberOfPresets.toInt()
    override fun presetName(index: Int): String = equalizer.getPresetName(index.toShort())
    override fun usePreset(index: Int) { runCatching { equalizer.usePreset(index.toShort()) } }
    override val bandCount: Int get() = equalizer.numberOfBands.toInt()
    override val bandLevelRange: IntRange get() = equalizer.bandLevelRange.let { it[0].toInt()..it[1].toInt() }
    override fun bandLevel(band: Int): Int = equalizer.getBandLevel(band.toShort()).toInt()
    override fun setBandLevel(band: Int, level: Int) { runCatching { equalizer.setBandLevel(band.toShort(), level.toShort()) } }
    override fun centerFreqHz(band: Int): Int = equalizer.getCenterFreq(band.toShort()) / 1000
    override val hasReverb: Boolean get() = reverb != null
    override val hasBassBoost: Boolean get() = bassBoost != null

    override fun applySaved() {
        val s = KMediaPlayer.settings
        val on = s[PlayerKeys.equalizerEnabled]
        runCatching {
            equalizer.enabled = on
            val preset = s[PlayerKeys.equalizerPreset]
            val bands = s[PlayerKeys.equalizerBands].split(',').mapNotNull { it.toShortOrNull() }
            if (preset >= 0 && preset < equalizer.numberOfPresets) equalizer.usePreset(preset.toShort())
            else bands.forEachIndexed { i, level -> if (i < equalizer.numberOfBands) equalizer.setBandLevel(i.toShort(), level) }
            bassBoost?.apply { enabled = on && s[PlayerKeys.bassBoost] > 0; if (strengthSupported) setStrength(s[PlayerKeys.bassBoost].toShort()) }
            reverb?.apply { setPreset(s[PlayerKeys.reverb].toShort()); enabled = on && s[PlayerKeys.reverb] > 0 }
        }
    }

    override fun setBoostMillibels(mb: Int) {
        loudness?.runCatching { setTargetGain(mb); enabled = mb > 0 }
    }

    fun release() {
        runCatching { equalizer.release() }
        runCatching { bassBoost?.release() }
        runCatching { reverb?.release() }
        runCatching { loudness?.release() }
    }
}
