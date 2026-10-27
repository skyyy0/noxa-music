package com.example

import com.example.media.SleepTimerRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sleep timer: the fade curve and the end-of-track arithmetic, pure. */
class SleepTimerRulesTest {

    @Test
    fun `volume stays full outside the fade window`() {
        assertEquals(1f, SleepTimerRules.fadeVolume(SleepTimerRules.FADE_MS))
        assertEquals(1f, SleepTimerRules.fadeVolume(SleepTimerRules.FADE_MS + 1))
        assertEquals(1f, SleepTimerRules.fadeVolume(60 * 60 * 1000L))
    }

    @Test
    fun `volume ramps linearly to silence inside the fade window`() {
        val half = SleepTimerRules.fadeVolume(SleepTimerRules.FADE_MS / 2)
        assertEquals(0.5f, half, 0.01f)
        val quarter = SleepTimerRules.fadeVolume(SleepTimerRules.FADE_MS / 4)
        assertEquals(0.25f, quarter, 0.01f)
        assertEquals(0f, SleepTimerRules.fadeVolume(0L), 0f)
        // Past the end the volume never goes negative.
        assertEquals(0f, SleepTimerRules.fadeVolume(-5_000L), 0f)
    }

    @Test
    fun `a zero or negative fade window never mutes by accident`() {
        assertEquals(1f, SleepTimerRules.fadeVolume(500L, fadeMs = 0L))
        assertEquals(1f, SleepTimerRules.fadeVolume(500L, fadeMs = -10L))
    }

    @Test
    fun `end-of-track counts the real tail of the song`() {
        assertEquals(30_000L, SleepTimerRules.endOfTrackRemainingMs(60_000L, 90_000L))
        // At (or past) the end the remaining time clamps to zero, not negative.
        assertEquals(0L, SleepTimerRules.endOfTrackRemainingMs(90_000L, 90_000L))
        assertEquals(0L, SleepTimerRules.endOfTrackRemainingMs(95_000L, 90_000L))
    }

    @Test
    fun `an unknown duration never fires blind`() {
        assertNull(SleepTimerRules.endOfTrackRemainingMs(10_000L, 0L))
        assertNull(SleepTimerRules.endOfTrackRemainingMs(10_000L, -1L))
    }

    @Test
    fun `stop threshold sits where the fade is already inaudible`() {
        assertTrue(SleepTimerRules.shouldStop(0L))
        assertTrue(SleepTimerRules.shouldStop(SleepTimerRules.STOP_THRESHOLD_MS))
        assertFalse(SleepTimerRules.shouldStop(SleepTimerRules.STOP_THRESHOLD_MS + 1))
        assertFalse(SleepTimerRules.shouldStop(5_000L))
    }

    @Test
    fun `the end-of-track fade is shorter than the minute fade`() {
        assertTrue(SleepTimerRules.END_OF_TRACK_FADE_MS < SleepTimerRules.FADE_MS)
    }
}
