package com.example.data.local

import android.content.Context
import com.example.data.model.Song
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * The playback session as it needs to survive a process death or a reboot:
 * what was queued, which item was playing, and where it had reached.
 */
@JsonClass(generateAdapter = true)
data class PlaybackSession(
    val songs: List<Song> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
    val savedAt: Long = 0L
)

/**
 * Stores the last playback session in `SharedPreferences` as JSON.
 *
 * The whole [Song] list is persisted (not just ids) because the media resumption
 * path runs while the device may still have no network, so titles and artwork
 * have to come from disk.
 */
class PlaybackSessionStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(PlaybackSession::class.java)

    fun save(session: PlaybackSession) {
        try {
            prefs.edit()
                .putString(KEY_SESSION, adapter.toJson(session))
                .apply()
        } catch (e: Exception) {
            // Never let persistence break playback.
        }
    }

    fun load(): PlaybackSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            adapter.fromJson(raw)?.takeIf { it.songs.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    private companion object {
        const val PREFS_NAME = "noxa_playback_session"
        const val KEY_SESSION = "last_session"
    }
}
