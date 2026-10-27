package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.example.MainActivity
import com.example.NoxaApplication
import com.example.R
import com.example.media.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The NOXA home-screen "Now Playing" widget.
 *
 * The widget mirrors the shared [NoxaAudioPlayer][com.example.media.NoxaAudioPlayer]
 * facade owned by [NoxaApplication]: title, artist, artwork and the play/pause +
 * next buttons all track `player.uiState`, so the widget never owns playback
 * state of its own — it is a window onto the same state the mini player draws.
 *
 * Design notes:
 *  * The collector lives in the companion object because [AppWidgetProvider]
 *    instances are short-lived: Android creates a fresh receiver for every
 *    broadcast, and restarting a collector each time would stack subscriptions.
 *  * Artwork is fetched through Coil off the main thread (a widget update on the
 *    UI thread counts against the app's ANR budget) and scaled to widget size.
 *    Bitmaps travel to the launcher over Binder, which has a ~1 MB transaction
 *    limit, so a 256px decode (≈256 KB) keeps every update comfortably below it.
 *  * When the app process starts fresh (widget tapped after the system killed
 *    the app), the persisted session is restored — without autoplay — exactly
 *    like the mini player, so the widget still shows what was last playing and
 *    the play button can continue it.
 */
class NoxaWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startObserving(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Start observing, and also render the current state immediately: a
        // widget added while observation is already running would otherwise
        // wait until the next playback change to draw anything.
        startObserving(context)
        renderNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> appOf(context)?.player?.togglePlayPause()
            ACTION_NEXT -> appOf(context)?.player?.next()
        }
        startObserving(context)
        renderNow(context)
    }

    private fun appOf(context: Context): NoxaApplication? =
        context.applicationContext as? NoxaApplication

    /**
     * Subscribes to the shared player's state exactly once per process and
     * repaints every widget on each meaningful change.
     */
    private fun startObserving(context: Context) {
        if (observeJob?.isActive == true) return
        val app = appOf(context) ?: return
        observeJob = scope.launch {
            // After a process restart the player is empty; load the persisted
            // session (no autoplay) so the widget can still show the last song.
            if (app.player.uiState.value.currentSong == null) {
                val session = withContext(Dispatchers.IO) { app.player.prepareResumableSession() }
                if (session != null) app.player.restoreSession(session)
            }
            // StateFlow already conflates duplicate consecutive states, so no
            // extra distinctUntilChanged is needed (and it is deprecated there).
            app.player.uiState.collect { state ->
                render(context.applicationContext, state)
            }
        }
    }

    /** Repaints from whatever state the player holds right now. */
    private fun renderNow(context: Context) {
        val app = appOf(context) ?: return
        val appContext = context.applicationContext
        scope.launch { render(appContext, app.player.uiState.value) }
    }

    /** Builds one RemoteViews snapshot and pushes it to every placed widget. */
    private suspend fun render(context: Context, state: PlayerUiState) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, NoxaWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        val song = state.currentSong
        val views = RemoteViews(context.packageName, R.layout.widget_now_playing)

        views.setTextViewText(R.id.widget_title, song?.title ?: "NOXA")
        views.setTextViewText(
            R.id.widget_artist,
            song?.artist ?: "Ketuk untuk membuka NOXA"
        )

        // Transport controls only exist once there is a queue to act on.
        val showTransport = song != null
        views.setViewVisibility(
            R.id.widget_play,
            if (showTransport) View.VISIBLE else View.GONE
        )
        views.setViewVisibility(
            R.id.widget_next,
            if (showTransport) View.VISIBLE else View.GONE
        )
        views.setImageViewResource(
            R.id.widget_play,
            if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )

        val artwork = song?.artworkUrl?.takeIf { it.isNotBlank() }?.let { loadArtwork(context, it) }
        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_art, artwork)
        } else {
            views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_bg)
        }

        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        if (showTransport) {
            views.setOnClickPendingIntent(
                R.id.widget_play,
                actionPendingIntent(context, ACTION_PLAY_PAUSE, REQUEST_PLAY_PAUSE)
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                actionPendingIntent(context, ACTION_NEXT, REQUEST_NEXT)
            )
        }

        ids.forEach { id -> manager.updateAppWidget(id, views) }
    }

    /**
     * Decodes the cover art at widget size through the app's Coil loader (its
     * disk cache means a repaint usually costs no network at all).
     */
    private suspend fun loadArtwork(context: Context, url: String): Bitmap? {
        artworkCache.get(url)?.let { return it }
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(ARTWORK_SIZE_PX)
            .allowHardware(false)
            .crossfade(false)
            .build()
        val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
        val drawable = (result as? SuccessResult)?.drawable ?: return null
        val bitmap = runCatching { drawable.toWidgetBitmap() }.getOrNull() ?: return null
        artworkCache.put(url, bitmap)
        return bitmap
    }

    /** Coil hands back a drawable; RemoteViews needs a plain bitmap to parcel. */
    private fun Drawable.toWidgetBitmap(): Bitmap =
        if (this is BitmapDrawable && bitmap != null) bitmap else toBitmap()

    /** Opens the main activity. */
    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("noxa://widget/open")
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * A broadcast back into this provider; [onReceive] forwards it to the
     * shared player and repaints from the resulting state.
     */
    private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, NoxaWidgetProvider::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val ACTION_PLAY_PAUSE = "com.example.widget.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.widget.action.NEXT"

        const val REQUEST_OPEN_APP = 0
        const val REQUEST_PLAY_PAUSE = 1
        const val REQUEST_NEXT = 2

        /** 256px ≈ 256 KB as ARGB_8888 — far below the Binder limit. */
        const val ARTWORK_SIZE_PX = 256

        /** One scope/collector per process, shared by every broadcast. */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var observeJob: Job? = null

        /** Covers repaint when the launcher re-renders without a state change. */
        private val artworkCache = object : android.util.LruCache<String, Bitmap>(8) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }
}
