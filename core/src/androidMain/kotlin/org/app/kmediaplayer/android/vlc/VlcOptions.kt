package org.app.kmediaplayer.android.vlc

/** libVLC tuning for smooth playback on phones. */
internal object VlcOptions {
    val libVlcArgs: List<String> = listOf(
        // Use every core for FFmpeg software decoding (HEVC/VP9/AV1 without a hardware decoder).
        "--avcodec-threads=0",
        // Skip the deblocking filter on non-reference frames only: invisible, but a large speed-up for SW 4K.
        "--avcodec-skiploopfilter=1",
        // Drop late frames instead of stuttering when the CPU falls behind.
        "--drop-late-frames",
        "--skip-frames",
        // Keep pitch when the speed changes.
        "--audio-time-stretch",
        "--file-caching=1000",
        "--network-caching=2000",
        "--subsdec-encoding=UTF-8",
        "--no-stats",
    )

    val mediaOptions: List<String> = listOf(
        ":no-video-title-show",
    )
}
