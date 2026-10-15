package org.app.kmediaplayer.android.exo

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

/** [DefaultRenderersFactory] whose MediaCodec video renderer is [CatchUpVideoRenderer]. */
@OptIn(UnstableApi::class)
internal class TunedRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context, extensionRendererMode: Int, mediaCodecSelector: MediaCodecSelector, enableDecoderFallback: Boolean,
        eventHandler: Handler, eventListener: VideoRendererEventListener, allowedVideoJoiningTimeMs: Long, out: ArrayList<Renderer>,
    ) {
        super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, out)
        val index = out.indexOfFirst { it.javaClass == MediaCodecVideoRenderer::class.java }
        if (index < 0) return
        out[index] = CatchUpVideoRenderer(
            MediaCodecVideoRenderer.Builder(context)
                .setCodecAdapterFactory(codecAdapterFactory)
                .setMediaCodecSelector(mediaCodecSelector)
                .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                .setEnableDecoderFallback(enableDecoderFallback)
                .setEventHandler(eventHandler)
                .setEventListener(eventListener)
                .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
                // Late frames nothing depends on are skipped before decoding, so a decoder running
                // at about real time can catch up instead of staying behind forever.
                .experimentalSetLateThresholdToDropDecoderInputUs(LATE_INPUT_DROP_US)
                .experimentalSetParseAv1SampleDependencies(true),
        )
    }

    private companion object {
        const val LATE_INPUT_DROP_US = 50_000L
    }
}

/**
 * MediaCodec video renderer tuned for heavy streams on phones:
 * - asks the decoder for twice real-time speed on content above 1080p (MediaTek and other decoders
 *   size their clock to this hint, and exactly 60 fps leaves no room to recover);
 * - jumps to the next keyframe once video is [KEYFRAME_RESYNC_US] behind, earlier than ExoPlayer's
 *   500 ms, for containers without per-frame dependency info (MKV).
 */
@OptIn(UnstableApi::class)
internal class CatchUpVideoRenderer(builder: MediaCodecVideoRenderer.Builder) : MediaCodecVideoRenderer(builder) {

    override fun getCodecOperatingRateV23(targetPlaybackSpeed: Float, format: Format, streamFormats: Array<out Format>): Float {
        val base = super.getCodecOperatingRateV23(targetPlaybackSpeed, format, streamFormats)
        val heavy = format.width.toLong() * format.height > HEAVY_PIXELS
        return if (heavy && base > 0) base * HEADROOM else base
    }

    override fun shouldDropBuffersToKeyframe(earlyUs: Long, elapsedRealtimeUs: Long, isLastBuffer: Boolean): Boolean =
        earlyUs < -KEYFRAME_RESYNC_US && !isLastBuffer

    private companion object {
        /** Above 1080p. */
        const val HEAVY_PIXELS = 1920L * 1088
        const val HEADROOM = 2f
        const val KEYFRAME_RESYNC_US = 300_000L
    }
}
