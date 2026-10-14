package org.app.kmediaplayer.android

import android.app.Activity
import android.content.Context

/** Android wiring for the library: the application context and the screens the player opens. */
object KMediaPlayerAndroid {
    lateinit var context: Context
        private set

    /** Activity hosting the video player. Defaults to the library's own player screen. */
    var videoActivity: Class<out Activity>? = null
    /** Activity the playback notification opens for music sessions. */
    var musicActivity: Class<out Activity>? = null

    const val EXTRA_PENDING = "org.app.kmediaplayer.pending"

    fun init(context: Context, videoActivity: Class<out Activity>? = null, musicActivity: Class<out Activity>? = null) {
        if (!::context.isInitialized) this.context = context.applicationContext
        videoActivity?.let { this.videoActivity = it }
        musicActivity?.let { this.musicActivity = it }
    }

    val isInitialized: Boolean get() = ::context.isInitialized
}
