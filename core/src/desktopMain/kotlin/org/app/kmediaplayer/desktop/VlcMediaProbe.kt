package org.app.kmediaplayer.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.caprica.vlcj.media.Media
import uk.co.caprica.vlcj.media.MediaEventAdapter
import uk.co.caprica.vlcj.media.MediaParsedStatus
import uk.co.caprica.vlcj.media.ParseFlag

/** What libVLC reads from a file's header: length, picture size and frame rate. */
data class ProbedInfo(val durationMs: Long, val width: Int, val height: Int, val frameRate: Float)

/** Reads media headers with libVLC's parser, without decoding or playing anything. */
object VlcMediaProbe {
    val isAvailable: Boolean get() = VlcRuntime.isAvailable

    suspend fun probe(path: String, timeoutMs: Int = 4_000): ProbedInfo? {
        val factory = VlcRuntime.factory ?: return null
        val media: Media = factory.media().newMedia(path.toMrl()) ?: return null
        return try {
            val done = CompletableDeferred<Boolean>()
            media.events().addMediaEventListener(object : MediaEventAdapter() {
                override fun mediaParsedChanged(media: Media, newStatus: MediaParsedStatus) {
                    done.complete(newStatus == MediaParsedStatus.DONE)
                }
            })
            if (!media.parsing().parse(timeoutMs, ParseFlag.PARSE_LOCAL)) return null
            if (withTimeoutOrNull(timeoutMs + 500L) { done.await() } != true) return null
            val video = media.info().videoTracks().firstOrNull()
            val fps = video?.takeIf { it.frameRateBase() > 0 }?.let { it.frameRate().toFloat() / it.frameRateBase() } ?: 0f
            val sar = video?.takeIf { it.sampleAspectRatioBase() > 0 && it.sampleAspectRatio() > 0 }
                ?.let { it.sampleAspectRatio().toFloat() / it.sampleAspectRatioBase() } ?: 1f
            ProbedInfo(media.info().duration().coerceAtLeast(0), ((video?.width() ?: 0) * sar).toInt(), video?.height() ?: 0, fps)
        } finally {
            media.release()
        }
    }
}
