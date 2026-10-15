package org.app.kmediaplayer.android.hybrid

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import org.app.kmediaplayer.engine.Decoder
import org.app.kmediaplayer.model.MediaFormats
import org.app.kmediaplayer.model.fileExtension

/** Which backend plays an item. */
internal enum class Backend { Exo, Vlc }

/**
 * Decides when an item leaves ExoPlayer for libVLC. ExoPlayer stays first choice: it has the best
 * hardware integration, gapless audio and media-session support. libVLC covers the rest.
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

    fun route(uri: String, decoder: Decoder): Backend = when {
        decoder == Decoder.SW -> Backend.Vlc
        uri.fileExtension() in MediaFormats.legacyExtensions -> Backend.Vlc
        else -> Backend.Exo
    }

    fun shouldFallBack(errorCodeName: String): Boolean = errorCodeName in fallbackErrors

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

