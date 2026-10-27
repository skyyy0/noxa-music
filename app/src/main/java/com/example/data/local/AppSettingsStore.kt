package com.example.data.local

import android.content.Context

/**
 * Immutable snapshot of the user's settings, mirrored in a StateFlow by the
 * view model so Compose recomposes on every change.
 */
data class AppSettings(
    val mvQualityHeight: Int = AppSettingsStore.DEFAULT_MV_QUALITY,
    val autoplaySimilar: Boolean = true,
    val karaokeOverlay: Boolean = true,
    val autoResumeOnOpen: Boolean = false,
    val crossfadeEnabled: Boolean = true
)

/**
 * User-facing app settings that are not audio DSP (that lives in
 * [UsagePolicyStore] / the equalizer). Kept in plain SharedPreferences so the
 * values survive restarts and can be read before any Compose state exists;
 * the view model mirrors them in StateFlows for recomposition.
 */
class AppSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /**
     * Target height for the muted music-video stream. The video pickers choose
     * the stream closest to this value (360p is the data-friendly default).
     */
    var mvQualityHeight: Int
        get() = prefs.getInt(KEY_MV_QUALITY, DEFAULT_MV_QUALITY)
        set(value) {
            prefs.edit().putInt(KEY_MV_QUALITY, value).apply()
        }

    /** Auto-append similar tracks when the last song of a queue nears its end. */
    var autoplaySimilar: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY_SIMILAR, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTOPLAY_SIMILAR, value).apply()
        }

    /** Show the synced lyric line karaoke-style over the player artwork. */
    var karaokeOverlay: Boolean
        get() = prefs.getBoolean(KEY_KARAOKE_OVERLAY, true)
        set(value) {
            prefs.edit().putBoolean(KEY_KARAOKE_OVERLAY, value).apply()
        }

    /** Start the last session automatically when the app opens. */
    var autoResumeOnOpen: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RESUME, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_RESUME, value).apply()
        }

    /** Soft volume crossfade between consecutive tracks. */
    var crossfadeEnabled: Boolean
        get() = prefs.getBoolean(KEY_CROSSFADE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CROSSFADE, value).apply()
        }

    companion object {
        private const val NAME = "noxa_app_settings"
        private const val KEY_MV_QUALITY = "mv_quality_height"
        private const val KEY_AUTOPLAY_SIMILAR = "autoplay_similar"
        private const val KEY_KARAOKE_OVERLAY = "karaoke_overlay"
        private const val KEY_AUTO_RESUME = "auto_resume_on_open"
        private const val KEY_CROSSFADE = "crossfade_enabled"

        const val DEFAULT_MV_QUALITY = 360

        /** Quality choices offered in Profile & Settings. */
        val MV_QUALITY_CHOICES = listOf(360, 480, 720)
    }
}
