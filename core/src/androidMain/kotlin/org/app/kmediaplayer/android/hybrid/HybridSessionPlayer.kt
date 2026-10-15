package org.app.kmediaplayer.android.hybrid

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.app.kmediaplayer.android.exo.toMedia3

/**
 * The player handed to media sessions (notification, lock screen, headset buttons). It forwards to
 * ExoPlayer, and while [HybridEngine] plays an item through libVLC it reports libVLC's state and
 * routes the session's commands back through the engine.
 */
@OptIn(UnstableApi::class)
internal class HybridSessionPlayer(
    private val exo: ExoPlayer,
    private val engine: HybridEngine,
) : ForwardingSimpleBasePlayer(exo) {

    fun wraps(player: ExoPlayer): Boolean = player === exo

    /** Re-reads the state; call after any libVLC change. */
    fun refresh() = invalidateState()

    override fun getState(): State {
        val base = super.getState()
        if (!engine.isDetour) return base
        val current = engine.currentIndex
        val durationUs = engine.duration.takeIf { it > 0 }?.times(1000) ?: C.TIME_UNSET
        val playlist = base.playlist.mapIndexed { i, item ->
            if (i == current) item.buildUpon().setDurationUs(durationUs).setIsSeekable(true).build() else item
        }
        return base.buildUpon()
            .setPlaylist(playlist)
            .setPlayerError(null)
            .setPlaybackState(engine.state.toMedia3())
            .setPlayWhenReady(engine.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs { engine.position }
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (!engine.isDetour) return super.handleSetPlayWhenReady(playWhenReady)
        if (playWhenReady) engine.play() else engine.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (!engine.isDetour) return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> engine.seekToNext()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> engine.seekToPrevious()
            else ->
                if (mediaItemIndex == C.INDEX_UNSET || mediaItemIndex == engine.currentIndex) engine.seekTo(positionMs.coerceAtLeast(0))
                else engine.seekTo(mediaItemIndex, positionMs.coerceAtLeast(0))
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        if (!engine.isDetour) return super.handleStop()
        engine.stop()
        return Futures.immediateVoidFuture()
    }
}
