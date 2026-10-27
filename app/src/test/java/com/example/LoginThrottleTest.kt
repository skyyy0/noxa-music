package com.example

import com.example.data.auth.LoginThrottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sign-in brute-force backoff: gentle for humans, expensive for guessing. */
class LoginThrottleTest {

    @Test
    fun `first five wrong passwords cost nothing`() {
        for (failures in 0..LoginThrottle.FREE_ATTEMPTS) {
            assertEquals(0L, LoginThrottle.backoffMs(failures))
        }
    }

    @Test
    fun `backoff doubles after the free attempts and caps at five minutes`() {
        assertEquals(15_000L, LoginThrottle.backoffMs(6))
        assertEquals(30_000L, LoginThrottle.backoffMs(7))
        assertEquals(60_000L, LoginThrottle.backoffMs(8))
        assertEquals(120_000L, LoginThrottle.backoffMs(9))
        assertEquals(240_000L, LoginThrottle.backoffMs(10))
        assertEquals(LoginThrottle.CAP_MS, LoginThrottle.backoffMs(11))
        // Even a marathon guessing session never waits longer than the cap.
        assertEquals(LoginThrottle.CAP_MS, LoginThrottle.backoffMs(500))
    }

    @Test
    fun `remaining wait counts down and hits zero`() {
        val now = 1_000_000L
        val last = now - 5_000L // failed 5s ago, 6th failure → 15s backoff
        assertEquals(10_000L, LoginThrottle.remainingMs(6, last, now))
        assertEquals(0L, LoginThrottle.remainingMs(6, last, now + 10_000L))
        // Free attempts never block.
        assertEquals(0L, LoginThrottle.remainingMs(3, last, now))
    }

    @Test
    fun `stale ledger is forgiven after the window`() {
        val now = 10_000_000L
        val ancient = now - LoginThrottle.WINDOW_MS - 1
        assertEquals(0L, LoginThrottle.remainingMs(50, ancient, now))
        // …but a fresh one still counts.
        assertTrue(
            LoginThrottle.remainingMs(50, now - 60_000L, now) > 0L
        )
    }

    @Test
    fun `empty ledger never blocks`() {
        assertEquals(0L, LoginThrottle.remainingMs(0, 0L, 1_000L))
        assertEquals(0L, LoginThrottle.remainingMs(7, 0L, 1_000L))
        assertEquals(0L, LoginThrottle.remainingMs(-1, 500L, 1_000L))
    }
}
