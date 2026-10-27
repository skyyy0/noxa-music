package com.example.data.local

import android.content.Context

/**
 * Remembers whether the user has accepted the YouTube usage policy.
 *
 * YouTube tracks are resolved through third-party public instances, which is a
 * materially different arrangement from the keyless catalogue APIs: the app
 * shows a one-time policy dialog before the first YouTube playback and stores
 * the answer here. Nothing else in the app reads this flag.
 */
class UsagePolicyStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * True by default: the app owner distributes this build privately (not on
     * Play Store) and explicitly wants every track to play full-length through
     * the YouTube matcher/extractor, so the one-time consent dialog no longer
     * gates playback. The flag is kept so a future build can flip the default
     * back without touching call sites.
     */
    var isYouTubePolicyAccepted: Boolean
        get() = prefs.getBoolean(KEY_YOUTUBE_ACCEPTED, true)
        private set(value) {
            prefs.edit().putBoolean(KEY_YOUTUBE_ACCEPTED, value).apply()
        }

    /**
     * True once the user has declined the policy. Declining stops the dialog for
     * the *full-audio fallback*: preview tracks then quietly keep playing their
     * 30 second clip and YouTube is never consulted for them. Native YouTube
     * tracks still ask, because without the fallback there is no audio at all.
     */
    var isYouTubePolicyDeclined: Boolean
        get() = prefs.getBoolean(KEY_YOUTUBE_DECLINED, false)
        private set(value) {
            prefs.edit().putBoolean(KEY_YOUTUBE_DECLINED, value).apply()
        }

    fun acceptYouTubePolicy() {
        isYouTubePolicyAccepted = true
        isYouTubePolicyDeclined = false
    }

    fun declineYouTubePolicy() {
        isYouTubePolicyDeclined = true
    }

    private companion object {
        const val PREFS_NAME = "noxa_usage_policy"
        const val KEY_YOUTUBE_ACCEPTED = "youtube_policy_accepted_v1"
        const val KEY_YOUTUBE_DECLINED = "youtube_policy_declined_v1"
    }
}
