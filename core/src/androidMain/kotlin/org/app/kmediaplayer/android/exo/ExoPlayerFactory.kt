package org.app.kmediaplayer.android.exo

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import org.app.kmediaplayer.engine.Decoder
import org.app.kmediaplayer.settings.ObservableSettings
import org.app.kmediaplayer.settings.PlayerKeys

/**
 * Builds the ExoPlayer used for everything the platform decoders can handle.
 *
 * Smoothness choices:
 * - [TunedRenderersFactory]: late frames are skipped before decoding and heavy streams get decoder
 *   headroom, so playback catches up instead of dropping most frames when the decoder falls behind.
 * - Asynchronous MediaCodec queueing: buffers are fed from a dedicated thread, which keeps
 *   4K / high-frame-rate streams from starving the decoder.
 * - FFmpeg extension renderers in ON mode: the device's hardware decoders stay first choice, FFmpeg
 *   takes over only for codecs the device lacks (AC3, E-AC3, DTS, TrueHD, Vorbis in odd
 *   containers, ALAC, ...).
 * - A larger, time-prioritised buffer so high-bitrate local files and streams never run dry.
 */
@OptIn(UnstableApi::class)
internal object ExoPlayerFactory {

    fun create(context: Context, settings: ObservableSettings, decoder: () -> Decoder): ExoPlayer =
        ExoPlayer.Builder(context, renderers(context, decoder))
            .setMediaSourceFactory(mediaSources(context, settings))
            .setLoadControl(loadControl())
            .setAudioAttributes(movieAudio, settings[PlayerKeys.playAlone])
            .setHandleAudioBecomingNoisy(settings[PlayerKeys.pauseOnHeadsetDisconnect])
            .setSeekBackIncrementMs(settings[PlayerKeys.moveInterval] * 1000L)
            .setSeekForwardIncrementMs(settings[PlayerKeys.moveInterval] * 1000L)
            .build()
            .apply {
                setSeekParameters(settings.seekParameters())
                // Lets the display switch refresh rate to match 24/25/30 fps content where seamless.
                videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            }

    private val movieAudio = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    private fun renderers(context: Context, decoder: () -> Decoder) =
        TunedRenderersFactory(context)
            .setMediaCodecSelector(codecSelector(decoder))
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .forceEnableMediaCodecAsynchronousQueueing()

    /** Hardware decoders first; software-only codecs are kept as the last resort. */
    private fun codecSelector(decoder: () -> Decoder) = MediaCodecSelector { mimeType, secure, tunneling ->
        val all = MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling)
        if (decoder() == Decoder.HW) all.sortedBy { it.softwareOnly } else all
    }

    private fun mediaSources(context: Context, settings: ObservableSettings): DefaultMediaSourceFactory {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setUserAgent(settings[PlayerKeys.userAgent].ifBlank { "MaxPlayerPro" })
        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            // Marks frames nothing references, so late ones can be skipped before decoding.
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES or Mp4Extractor.FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES_H265)
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
        return DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http), extractors)
    }

    private fun loadControl(): LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            // Keep a long stretch buffered ahead (like streaming apps), start fast, and after a stall wait for a
            // little more so it doesn't stall again straight away. Local files fill this instantly.
            /* minBufferMs = */ 30_000,
            /* maxBufferMs = */ 120_000,
            /* bufferForPlaybackMs = */ 1_000,
            /* bufferForPlaybackAfterRebufferMs = */ 4_000,
        )
        .setBackBuffer(10_000, true)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}
