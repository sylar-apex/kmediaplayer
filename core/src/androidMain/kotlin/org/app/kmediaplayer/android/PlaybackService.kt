package org.app.kmediaplayer.android

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.settings.PlayerKeys

/**
 * Background play: exposes the shared player through a media session so Media3 posts the
 * playback notification, lock-screen controls and responds to headset/Bluetooth media buttons.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        KMediaPlayerAndroid.init(this)
        val player = Media3Engine.exo
        val target = if (KMediaPlayer.isMusicSession.value) KMediaPlayerAndroid.musicActivity else KMediaPlayerAndroid.videoActivity
        val builder = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean =
                    !KMediaPlayer.settings[PlayerKeys.mediaButtons]
            })
        if (target != null) {
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setSessionActivity(open)
        }
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) stopSelf()
    }

    override fun onDestroy() {
        session?.run { release() }
        session = null
        super.onDestroy()
    }
}
