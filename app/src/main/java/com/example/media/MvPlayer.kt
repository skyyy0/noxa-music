package com.example.media

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The muted second player behind the full player's "Video (MV)" mode.
 *
 * It is deliberately a **separate, always-muted** ExoPlayer: the main audio
 * player (and therefore the notification, lock screen controls, equalizer and
 * audio focus) keeps running untouched while the music video is shown in the
 * sheet. A stream failure here can never kill the music.
 *
 * The video is kept roughly in sync with the main player by seeking it to the
 * main position when it is attached and whenever the user seeks manually.
 */
class MvPlayer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var player: ExoPlayer? = null

    /** Guards against the SurfaceView re-attach that runs on every recomposition. */
    private var activeUrl: String? = null
    private var activeSurface: SurfaceView? = null

    var isPlaying: Boolean = false
        private set

    /**
     * Invoked when the muted video player hits a fatal playback error (an
     * expired stream URL is the common case). The owner can re-resolve the
     * stream and attach again — the music itself is never affected.
     */
    var onPlaybackError: (() -> Unit)? = null

    /**
     * Starts showing [videoUrl] on [surfaceView], beginning at [positionMs].
     * The player is created lazily and always muted. Re-attaching the same URL
     * to the same surface is a no-op, so Compose recompositions are free.
     */
    fun playOnSurface(videoUrl: String, surfaceView: SurfaceView, positionMs: Long) {
        if (activeUrl == videoUrl && activeSurface === surfaceView && player != null) {
            player?.setVideoSurfaceView(surfaceView)
            return
        }
        val exo = player ?: ExoPlayer.Builder(context)
            .build()
            .also { created ->
                created.volume = 0f
                created.repeatMode = Player.REPEAT_MODE_OFF
                created.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.w("MvPlayer", "MV playback failed: ${error.errorCodeName}")
                        onPlaybackError?.invoke()
                    }
                })
                player = created
            }

        exo.setVideoSurfaceView(surfaceView)
        exo.setMediaItem(MediaItem.fromUri(videoUrl))
        exo.seekTo(positionMs.coerceAtLeast(0L))
        exo.prepare()
        exo.play()
        activeUrl = videoUrl
        activeSurface = surfaceView
        isPlaying = true
    }

    /** Re-binds the video to a (possibly new) surface without restarting it. */
    fun attachSurface(surfaceView: SurfaceView?) {
        player?.setVideoSurfaceView(surfaceView)
    }

    /** Follows a manual seek of the main player. */
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun pause() {
        player?.pause()
        isPlaying = false
    }

    fun play() {
        player?.play()
        isPlaying = player != null
    }

    /**
     * Detaches the surface (audio keeps playing) and releases the decoder after
     * a short grace period, so quickly toggling Video -> Foto -> Video does not
     * rebuild the whole pipeline each time.
     */
    fun detachAndReleaseSoon() {
        player?.setVideoSurfaceView(null)
        val exo = player ?: return
        scope.launch {
            delay(RELEASE_GRACE_MS)
            if (!isPlaying && player === exo) {
                releaseNow()
            }
        }
    }

    fun release() {
        releaseNow()
    }

    private fun releaseNow() {
        try {
            player?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Could not release MV player: ${e.message}")
        }
        player = null
        activeUrl = null
        activeSurface = null
        isPlaying = false
    }

    private companion object {
        const val TAG = "MvPlayer"
        const val RELEASE_GRACE_MS = 10_000L
    }
}
