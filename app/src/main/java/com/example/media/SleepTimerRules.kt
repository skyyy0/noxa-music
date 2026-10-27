package com.example.media

/**
 * Pure sleep-timer math, deliberately kept out of [NoxaAudioPlayer] so the
 * rules can be unit tested without an ExoPlayer instance.
 *
 * The honest behaviour this encodes: the volume ramps down smoothly inside the
 * fade window instead of the audio being cut dead at zero, and a timer whose
 * duration is unknown (a stream that has not reported its length yet) never
 * fires early — it simply waits for a duration it can measure.
 */
object SleepTimerRules {

    /** How long the volume ramps down before a minute-based timer stops. */
    const val FADE_MS = 10_000L

    /** End-of-track hugs the real song length, so its fade is shorter. */
    const val END_OF_TRACK_FADE_MS = 3_000L

    /**
     * Below this remaining time the fade is inaudible anyway; stopping here
     * avoids stepping the volume in audible chunks on a 500 ms tick.
     */
    const val STOP_THRESHOLD_MS = 60L

    /**
     * Volume (0f..1f) for [remainingMs] left before the timer fires:
     * full volume outside the fade window, a straight linear ramp inside it,
     * silence at (or past) the end.
     */
    fun fadeVolume(remainingMs: Long, fadeMs: Long = FADE_MS): Float {
        if (fadeMs <= 0L || remainingMs >= fadeMs) return 1f
        if (remainingMs <= 0L) return 0f
        return (remainingMs.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Remaining milliseconds until the current track ends, or `null` while the
     * duration is still unknown — [companion]'s rule is "never fire blind".
     */
    fun endOfTrackRemainingMs(positionMs: Long, durationMs: Long): Long? =
        if (durationMs > 0L) (durationMs - positionMs).coerceAtLeast(0L) else null

    /** True when the fade has reached the point where playback should stop. */
    fun shouldStop(remainingMs: Long): Boolean = remainingMs <= STOP_THRESHOLD_MS
}
