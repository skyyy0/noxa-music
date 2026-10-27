package com.example

import com.example.data.local.PerfProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sistem Pintar: aturan tier murni (RAM dulu, jumlah inti sebagai pembeda)
 * plus knob performa yang membaca dari tier tersebut.
 */
class PerfProfileTest {

    private fun profile(memGb: Double, cores: Int, lowRam: Boolean = false): PerfProfile =
        PerfProfile.from(
            totalMemBytes = (memGb * 1024 * 1024 * 1024).toLong(),
            cores = cores,
            isLowRamDevice = lowRam,
            sdkInt = 34,
            chipName = "test-soc"
        )

    @Test
    fun `a low-ram flag forces HEMAT on a few-core device`() {
        assertEquals(PerfProfile.Tier.HEMAT, profile(8.0, 4, lowRam = true).tier)
    }

    @Test
    fun `a low-ram flag with many cores settles at SEIMBANG`() {
        // The rule is RAM first, cores as the tiebreaker: a low-RAM phone
        // that still has 8 cores is usable at the middle tier.
        assertEquals(PerfProfile.Tier.SEIMBANG, profile(8.0, 8, lowRam = true).tier)
    }

    @Test
    fun `2 GB or less with few cores is HEMAT`() {
        assertEquals(PerfProfile.Tier.HEMAT, profile(2.0, 4).tier)
        assertEquals(PerfProfile.Tier.HEMAT, profile(1.0, 4).tier)
    }

    @Test
    fun `low memory but many cores lifts to SEIMBANG`() {
        assertEquals(PerfProfile.Tier.SEIMBANG, profile(2.0, 8).tier)
    }

    @Test
    fun `between 2 and 6 GB is SEIMBANG`() {
        assertEquals(PerfProfile.Tier.SEIMBANG, profile(4.0, 6).tier)
        assertEquals(PerfProfile.Tier.SEIMBANG, profile(6.0, 4).tier)
    }

    @Test
    fun `above 6 GB is NGEBUT`() {
        assertEquals(PerfProfile.Tier.NGEBUT, profile(8.0, 8).tier)
    }

    @Test
    fun `zero or negative memory falls through to the safe tier`() {
        // A failed memory probe (0 bytes) must not crash nor force HEMAT.
        val p = PerfProfile.from(0L, 8, isLowRamDevice = false, sdkInt = 34)
        assertEquals(PerfProfile.Tier.NGEBUT, p.tier)
    }

    @Test
    fun `core count is coerced to at least one`() {
        val p = PerfProfile.from(0L, 0, isLowRamDevice = false, sdkInt = 34)
        assertEquals(1, p.cores)
    }

    @Test
    fun `knobs follow the tier`() {
        val hemat = profile(1.5, 4)
        assertEquals(1, hemat.warmTrackLimit)
        assertEquals(30_000L, hemat.preloadWindowMs)
        assertFalse(hemat.crossfadeAllowed)
        assertEquals(120, hemat.artworkCrossfadeMs)

        val seimbang = profile(4.0, 8)
        assertEquals(2, seimbang.warmTrackLimit)
        assertEquals(45_000L, seimbang.preloadWindowMs)
        assertTrue(seimbang.crossfadeAllowed)
        assertEquals(180, seimbang.artworkCrossfadeMs)

        val ngebut = profile(12.0, 8)
        assertEquals(4, ngebut.warmTrackLimit)
        assertEquals(60_000L, ngebut.preloadWindowMs)
        assertTrue(ngebut.crossfadeAllowed)
        assertEquals(240, ngebut.artworkCrossfadeMs)
    }

    @Test
    fun `tier labels are human readable`() {
        assertEquals("Hemat", profile(1.0, 4).tierLabel)
        assertEquals("Seimbang", profile(4.0, 4).tierLabel)
        assertEquals("Ngebut", profile(12.0, 8).tierLabel)
    }

    @Test
    fun `totalMemGb rounds to one decimal`() {
        // 5.5 GB is exact in binary floating point, so the rounding is stable.
        val p = profile(5.5, 8)
        assertEquals(5.5, p.totalMemGb, 0.01)
    }
}
