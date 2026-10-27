package com.example.data.auth

/**
 * Per-device brute-force backoff for email + password sign-in.
 *
 * The rules are pure (unit-testable on the JVM); the ledger itself lives in
 * AuthManager's SharedPreferences. The design is deliberately gentle: the
 * first [FREE_ATTEMPTS] wrong passwords inside [WINDOW_MS] cost nothing, so
 * a typo now and then never punishes a real user — after that the wait
 * doubles from 15 s up to a 5-minute cap. Any successful sign-in forgives
 * the ledger entirely, and a ledger that hasn't seen a failure inside the
 * window is treated as empty.
 */
object LoginThrottle {

    /** Wrong passwords allowed within [WINDOW_MS] before any wait starts. */
    const val FREE_ATTEMPTS = 5

    /** Rolling window: failures older than this are forgiven. */
    const val WINDOW_MS = 10 * 60 * 1000L

    /** Never make a human wait longer than this between attempts. */
    const val CAP_MS = 5 * 60 * 1000L

    /** Wait enforced after [failures] consecutive failures (0 while free). */
    fun backoffMs(failures: Int): Long {
        if (failures <= FREE_ATTEMPTS) return 0L
        val step = (failures - FREE_ATTEMPTS - 1).coerceAtMost(5)
        return minOf(CAP_MS, 15_000L shl step)
    }

    /**
     * Remaining wait given the ledger ([failures] counted so far and when the
     * last one happened). Zero means "try now".
     */
    fun remainingMs(failures: Int, lastFailureAt: Long, now: Long): Long {
        if (failures <= 0 || lastFailureAt <= 0L) return 0L
        // The window expired — the slate is clean again.
        if (now - lastFailureAt > WINDOW_MS) return 0L
        val wait = lastFailureAt + backoffMs(failures) - now
        return if (wait > 0L) wait else 0L
    }
}
