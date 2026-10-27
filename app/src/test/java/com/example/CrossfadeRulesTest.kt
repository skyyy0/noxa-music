package com.example

import com.example.media.CrossfadeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Crossfade lembut: kurva volume murni, tanpa player. */
class CrossfadeRulesTest {

    @Test
    fun `disabled crossfade keeps full volume everywhere`() {
        assertEquals(1f, CrossfadeRules.volumeAt(0L, 200_000L, enabled = false))
        assertEquals(1f, CrossfadeRules.volumeAt(199_000L, 200_000L, enabled = false))
        assertEquals(1f, CrossfadeRules.volumeAt(100_000L, 200_000L, enabled = false))
    }

    @Test
    fun `unknown duration never mutes by accident`() {
        assertEquals(1f, CrossfadeRules.volumeAt(0L, 0L, enabled = true))
        assertEquals(1f, CrossfadeRules.volumeAt(5_000L, -1L, enabled = true))
    }

    @Test
    fun `fade-in ramps from silence at the start`() {
        // One second into a 1.5 s fade-in → ~0.66.
        val oneSecond = CrossfadeRules.volumeAt(1_000L, 200_000L, enabled = true)
        assertEquals(1_000f / CrossfadeRules.FADE_IN_MS, oneSecond, 0.01f)
        // Past the fade-in the volume is governed by the tail only.
        assertEquals(1f, CrossfadeRules.volumeAt(CrossfadeRules.FADE_IN_MS + 1, 200_000L, enabled = true))
    }

    @Test
    fun `fade-out ramps down over the tail`() {
        val duration = 200_000L
        // Halfway through the fade-out window → 0.5.
        val half = CrossfadeRules.volumeAt(duration - CrossfadeRules.FADE_MS / 2, duration, enabled = true)
        assertEquals(0.5f, half, 0.02f)
        // At the very end the volume is silent.
        assertEquals(0f, CrossfadeRules.volumeAt(duration, duration, enabled = true), 0.01f)
    }

    @Test
    fun `a track shorter than both fades stays audible`() {
        // 4 s track: fade-in 1.5 + fade-out 4 > duration, so the floor of 0.5
        // keeps it from being squeezed to silence.
        val volume = CrossfadeRules.volumeAt(2_000L, 4_000L, enabled = true)
        assertTrue("expected at least the audible floor, got $volume", volume >= 0.5f)
    }

    @Test
    fun `volume stays within the 0 to 1 range for a normal track`() {
        val duration = 180_000L
        for (pos in 0L..duration step 5_000L) {
            val v = CrossfadeRules.volumeAt(pos, duration, enabled = true)
            assertTrue("volume $v at $pos ms out of range", v in 0f..1f)
        }
    }
}
