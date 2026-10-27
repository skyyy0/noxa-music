package com.example

import com.example.data.model.ArtistProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The verified-check rule: every catalogue artist gets a check, but the check
 * turns red once the artist crosses the "big artist" listener threshold
 * (Justin Bieber) and stays blue below it (regular verified artists).
 */
class ArtistProfileTest {

    private fun profile(fans: Long) = ArtistProfile(
        id = 1L,
        name = "Test Artist",
        pictureUrl = "",
        fanCount = fans
    )

    @Test
    fun `artists with a million listeners or more get the red check`() {
        assertTrue(profile(1_000_000L).isBigArtist)
        assertTrue(profile(38_000_000L).isBigArtist)
    }

    @Test
    fun `regular verified artists keep the blue check`() {
        assertFalse(profile(999_999L).isBigArtist)
        assertFalse(profile(42_000L).isBigArtist)
        assertFalse(profile(0L).isBigArtist)
    }
}
