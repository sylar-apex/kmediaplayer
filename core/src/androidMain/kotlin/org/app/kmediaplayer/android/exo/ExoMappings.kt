package org.app.kmediaplayer.android.exo

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.SeekParameters
import org.app.kmediaplayer.engine.EngineState
import org.app.kmediaplayer.engine.FormatInfo
import org.app.kmediaplayer.engine.RepeatMode
import org.app.kmediaplayer.engine.TrackType
import org.app.kmediaplayer.settings.ObservableSettings
import org.app.kmediaplayer.settings.PlayerKeys

/** Conversions between Media3 constants and the engine-neutral types in `org.app.kmediaplayer.engine`. */

internal fun Int.toEngineState(): EngineState = when (this) {
    Player.STATE_BUFFERING -> EngineState.Buffering
    Player.STATE_READY -> EngineState.Ready
    Player.STATE_ENDED -> EngineState.Ended
    else -> EngineState.Idle
}

internal fun EngineState.toMedia3(): Int = when (this) {
    EngineState.Buffering -> Player.STATE_BUFFERING
    EngineState.Ready -> Player.STATE_READY
    EngineState.Ended -> Player.STATE_ENDED
    EngineState.Idle -> Player.STATE_IDLE
}

internal fun Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ONE -> RepeatMode.One
    Player.REPEAT_MODE_ALL -> RepeatMode.All
    else -> RepeatMode.Off
}

internal fun RepeatMode.toMedia3(): Int = when (this) {
    RepeatMode.Off -> Player.REPEAT_MODE_OFF
    RepeatMode.One -> Player.REPEAT_MODE_ONE
    RepeatMode.All -> Player.REPEAT_MODE_ALL
}

internal val TrackType.media3: Int get() = if (this == TrackType.Audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT

internal fun Format.toFormatInfo() = FormatInfo(
    containerMimeType = containerMimeType, codecs = codecs, sampleMimeType = sampleMimeType,
    width = width, height = height, frameRate = frameRate, bitrate = bitrate,
    channelCount = channelCount, sampleRate = sampleRate, language = language,
)

@OptIn(UnstableApi::class)
internal fun ObservableSettings.seekParameters(): SeekParameters =
    if (this[PlayerKeys.fastSeek]) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT
