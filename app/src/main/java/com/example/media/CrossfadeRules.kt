package com.example.media

import kotlin.math.max
import kotlin.math.min

/**
 * **Crossfade lembut** — volume automation that fades the current track out
 * over the last [FADE_MS] and fades the next one in over [FADE_IN_MS], so a
 * queue change never clicks or drops into silence. ExoPlayer is already
 * gapless for prepared items; this only adds the volume curve on top.
 *
 * Pure functions so the curve is unit-testable without a player. When the
 * sleep timer is armed it owns the volume instead ([NoxaAudioPlayer] skips
 * crossfade in that case — the timer's fade must win).
 */
object CrossfadeRules {

    /** Fade-out length at the tail of a track. */
    const val FADE_MS = 4_000L

    /** Fade-in length at the start of a track. */
    const val FADE_IN_MS = 1_500L

    /**
     * The volume a track should be at, given its position.
     *
     * Fade-in wins at the start, fade-out wins at the end; a track shorter
     * than both fades combined keeps a constant 0.5 so it stays audible
     * instead of being squeezed to nothing.
     */
    fun volumeAt(positionMs: Long, durationMs: Long, enabled: Boolean): Float {
        if (!enabled) return 1f
        if (durationMs <= 0L) return 1f
        val fadeIn = if (FADE_IN_MS > 0) {
            min(1f, positionMs.toFloat() / FADE_IN_MS.toFloat())
        } else {
            1f
        }
        val remaining = durationMs - positionMs
        val fadeOut = if (FADE_MS > 0) {
            min(1f, max(0f, remaining.toFloat() / FADE_MS.toFloat()))
        } else {
            1f
        }
        // Both fades applied: multiply, but never below the audible floor of
        // the shorter overlap region.
        val volume = min(fadeIn, fadeOut)
        return if (durationMs <= FADE_IN_MS + FADE_MS) {
            max(volume, 0.5f)
        } else {
            volume.coerceIn(0f, 1f)
        }
    }
}
