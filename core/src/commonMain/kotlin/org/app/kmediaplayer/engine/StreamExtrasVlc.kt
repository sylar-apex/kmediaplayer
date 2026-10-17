package org.app.kmediaplayer.engine

import org.app.kmediaplayer.model.StreamExtras

/**
 * libVLC media options for [StreamExtras], shared by every libVLC backend (vlcj on desktop, libvlc-android,
 * MobileVLCKit on iOS): the User-Agent for HTTP inputs and a separate audio stream as an input slave, which
 * libVLC demuxes and plays in sync with the video.
 */
fun StreamExtras.vlcOptions(): List<String> = buildList {
    userAgent?.let { add(":http-user-agent=$it") }
    audioUri?.let { add(":input-slave=$it") }
}
