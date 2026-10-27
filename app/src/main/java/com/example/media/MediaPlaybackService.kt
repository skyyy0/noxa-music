package com.example.media

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.NoxaApplication
import com.example.data.model.Song
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Hosts the MediaSession that backs the system media notification, the lock
 * screen controls and background playback.
 *
 * The session wraps the single ExoPlayer owned by [NoxaApplication], so the
 * notification always reflects the player that is actually running. Media3
 * provides the notification and the device-specific media UI; NOXA does not draw
 * any custom overlay of its own.
 *
 * [ResumptionCallback] additionally lets the system offer to continue the last
 * session after the app process is killed or the device is rebooted, which is
 * the only reboot-survival path Android allows for media playback (an app is not
 * allowed to start a `mediaPlayback` foreground service from `BOOT_COMPLETED`).
 */
@UnstableApi
class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as? NoxaApplication ?: return
        val player = app.player.exoPlayer

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            // The custom shuffle/repeat session commands are granted in
            // ResumptionCallback.onConnect; without them the System UI could never
            // dispatch the notification's shuffle/repeat buttons.
            .setCallback(ResumptionCallback(app))
            .setMediaButtonPreferences(mediaButtonPreferences(player))
            .build()

        // The shuffle/repeat notification buttons are custom commands, so Media3
        // cannot redraw their icons by itself: refresh the preferences whenever
        // the underlying player state changes (from the app, the notification or
        // a headset).
        player.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                mediaSession?.setMediaButtonPreferences(mediaButtonPreferences(player))
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                mediaSession?.setMediaButtonPreferences(mediaButtonPreferences(player))
            }
        })
    }

    /**
     * Shuffle and repeat for the expanded notification / lock screen.
     *
     * They **must be custom session commands**: `DefaultMediaNotificationProvider`
     * only renders media-button preferences whose `sessionCommand` is custom —
     * it silently filters out player-command buttons (see
     * `mediaButtonPreferencesWithEnabledCommandButtonsOnly` in its source), which
     * is why an earlier version that used `setPlayerCommand` never showed them.
     * Taps arrive in [ResumptionCallback.onCustomCommand]. Play/pause and
     * previous/next are added automatically by the provider from the player's
     * own commands.
     */
    private fun mediaButtonPreferences(player: Player): List<CommandButton> {
        val shuffleIcon = if (player.shuffleModeEnabled) {
            CommandButton.ICON_SHUFFLE_ON
        } else {
            CommandButton.ICON_SHUFFLE_OFF
        }
        val repeatIcon = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
            Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
            else -> CommandButton.ICON_REPEAT_OFF
        }
        return listOf(
            CommandButton.Builder(shuffleIcon)
                .setSessionCommand(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
                .setDisplayName("Acak")
                .build(),
            CommandButton.Builder(repeatIcon)
                .setSessionCommand(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                .setDisplayName("Ulangi")
                .build()
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            // Nothing is playing any more, so the service may go away with the task.
            stopSelf()
        }
        // While playback is ongoing the service is deliberately left running so the
        // notification and the lock screen controls survive swiping the app away.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // The ExoPlayer instance is owned by NoxaApplication and shared with the
        // UI, so only the session may be released here. Releasing the player
        // would break playback whenever the service is destroyed.
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        /** Custom session actions handled in [ResumptionCallback.onCustomCommand]. */
        const val ACTION_SHUFFLE = "com.example.noxa.action.TOGGLE_SHUFFLE"
        const val ACTION_REPEAT = "com.example.noxa.action.TOGGLE_REPEAT"
    }

    /**
     * Serves the "continue listening" affordance the system shows after the app
     * was terminated or the device rebooted, and routes the notification's
     * custom shuffle/repeat buttons back into the shared player.
     */
    private class ResumptionCallback(
        private val app: NoxaApplication
    ) : MediaSession.Callback {

        /**
         * Accepts every controller with the default command set plus NOXA's
         * custom shuffle/repeat actions. Without this the system UI could not
         * dispatch those custom commands at all.
         */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = SessionCommands.Builder()
                .addSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.commands
                )
                .add(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            )
        }

        /** Toggles the shared player for the notification's custom buttons. */
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val player = session.player
            when (customCommand.customAction) {
                ACTION_SHUFFLE -> {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                ACTION_REPEAT -> {
                    // Same off -> all -> one cycle as the in-app button.
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val session = app.player.prepareResumableSession()
            if (session == null || session.songs.isEmpty()) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                )
            }

            // Rebuild the in-app queue too, so the mini player and the notification
            // agree with what the system is about to resume.
            app.player.restoreSession(session)

            val startIndex = session.index.coerceIn(0, session.songs.size - 1)
            return try {
                Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        session.songs.map { it.toMediaItem() },
                        startIndex,
                        session.positionMs
                    )
                )
            } catch (e: Exception) {
                Log.w("MediaPlaybackService", "Resumption failed: ${e.message}")
                Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                )
            }
        }
    }
}

/**
 * Builds the MediaItem the system needs for the resumption list. Artwork is
 * included because the resumption UI can appear before the network is up.
 */
private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(Uri.parse(streamUrl))
    .setMediaId(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setIsPlayable(true)
            .setArtworkUri(artworkUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
            .build()
    )
    .build()
