package org.app.kmediaplayer.android.hybrid

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import org.app.kmediaplayer.engine.Decoder

/** Which backend plays an item. */
internal enum class Backend { Exo, Vlc }

/**
 * Decides which backend plays an item. libVLC plays everything, as on iOS and desktop, so every format,
 * gesture and setting behaves the same on all platforms; ExoPlayer only keeps the queue and the media
 * session (notification, headset and lock-screen controls) that [HybridSessionPlayer] mirrors.
 */
@OptIn(UnstableApi::class)
internal object PlaybackRouter {

    /** ExoPlayer errors where another decoder or demuxer can still succeed. */
    private val fallbackErrors = setOf(
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_UNSPECIFIED,
    ).map(PlaybackException::getErrorCodeName).toSet()

    @Suppress("UNUSED_PARAMETER")
    fun route(uri: String, decoder: Decoder): Backend = Backend.Vlc

    fun shouldFallBack(errorCodeName: String): Boolean = errorCodeName in fallbackErrors

    /** Whether Android has any decoder (hardware or its own software one) for [mime]; asked once per type. */
    fun hasAndroidDecoder(mime: String): Boolean = decoders.getOrPut(mime) {
        runCatching {
            android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos
                .any { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, ignoreCase = true) } }
        }.getOrDefault(false)
    }

    private val decoders = mutableMapOf<String, Boolean>()

    /** Android's name for a codec, from ExoPlayer's MIME type or libVLC's description ("AOMedia's AV1 Video", "H264 - MPEG-4 AVC"). */
    fun mimeOf(codec: String): String? {
        val c = codec.lowercase()
        return when {
            c.startsWith("video/") -> c
            "av1" in c || c == "av01" -> "video/av01"
            "hevc" in c || "h265" in c || "h.265" in c -> "video/hevc"
            "h264" in c || "h.264" in c || "avc" in c -> "video/avc"
            "vp9" in c -> "video/x-vnd.on2.vp9"
            "vp8" in c -> "video/x-vnd.on2.vp8"
            else -> null
        }
    }

    /**
     * Whether some decoder here plays [mime] at [width]x[height] and [fps] in real time. Uses the frame rates the
     * maker measured where the phone lists them, else the decoder's declared limits. Null when it can't tell.
     */
    fun canPlaySmoothly(mime: String, width: Int, height: Int, fps: Float): Boolean? {
        if (width <= 0 || height <= 0) return null
        val rate = (if (fps > 0) fps else 30f).toDouble()
        val caps = runCatching {
            android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, ignoreCase = true) } }
                .mapNotNull { runCatching { it.getCapabilitiesForType(mime).videoCapabilities }.getOrNull() }
        }.getOrNull() ?: return null
        if (caps.isEmpty()) return false
        return caps.any { v ->
            // Sizes are checked both ways round: a portrait video uses the same decoder as its landscape twin.
            listOf(width to height, height to width).any { (w, h) ->
                val measured = runCatching { v.getAchievableFrameRatesFor(w, h) }.getOrNull()
                if (measured != null) measured.upper >= rate * 0.9
                else runCatching { v.areSizeAndRateSupported(w, h, rate) }.getOrDefault(false)
            }
        }
    }

    /**
     * True when ExoPlayer opened the file but has no decoder at all for its main video or audio track;
     * ExoPlayer then plays silently or shows black instead of failing, so this is checked on every track
     * change. Tracks that only exceed the decoder's advertised limits (common for 4K60 on phones whose
     * hardware handles it fine) still count as playable; a real failure surfaces as a decoder error.
     */
    fun hasUnplayableMainTrack(tracks: Tracks): Boolean =
        tracks.isTypeUnplayable(C.TRACK_TYPE_VIDEO) || tracks.isTypeUnplayable(C.TRACK_TYPE_AUDIO)

    private fun Tracks.isTypeUnplayable(type: Int): Boolean =
        containsType(type) && !isTypeSupported(type, /* allowExceedsCapabilities = */ true)
}

