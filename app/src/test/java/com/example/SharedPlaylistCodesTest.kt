package com.example

import com.example.data.support.SharedPlaylistCodes
import com.example.data.support.SharedPlaylistSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Collaborative playlist codes: parsing what users paste, and the sync rule. */
class SharedPlaylistCodesTest {

    @Test
    fun `bare code with casual formatting still normalizes`() {
        assertEquals("ABC234", SharedPlaylistCodes.normalize("ABC234"))
        assertEquals("ABC234", SharedPlaylistCodes.normalize("  abc234  "))
        assertEquals("ABC234", SharedPlaylistCodes.normalize("abc-234"))
        assertEquals("ABC234", SharedPlaylistCodes.normalize("ABC 234"))
        assertEquals("ABC234", SharedPlaylistCodes.normalize("abc_234"))
    }

    @Test
    fun `a full share message yields the code after its marker`() {
        val shareText = "Playlist NOXA \"Jalan Pulang\" — gabung pakai kode: KODE NP4TQ2\n" +
            "Buka NOXA › Koleksi › Playlist › ikon tautan, lalu tempel kode ini."
        assertEquals("NP4TQ2", SharedPlaylistCodes.normalize(shareText))
    }

    @Test
    fun `codes embedded in links are picked up`() {
        assertEquals(
            "ZK7M2P",
            SharedPlaylistCodes.normalize("https://example.test/playlist/ZK7M2P?x=1")
        )
        assertEquals("ZK7M2P", SharedPlaylistCodes.normalize("kode:ZK7M2P"))
        assertEquals("ZK7M2P", SharedPlaylistCodes.normalize("PLAYLIST ZK7M2P untuk kita"))
    }

    @Test
    fun `garbage input yields an empty code instead of a wrong one`() {
        assertEquals("", SharedPlaylistCodes.normalize(""))
        assertEquals("", SharedPlaylistCodes.normalize("   "))
        assertEquals("", SharedPlaylistCodes.normalize("hello world"))
        // Too short, too long, and letters the alphabet deliberately drops.
        assertEquals("", SharedPlaylistCodes.normalize("ABC1"))
        assertEquals("", SharedPlaylistCodes.normalize("ABC23456"))
        assertEquals("", SharedPlaylistCodes.normalize("ABCIO12")) // I, O and 1 are excluded
    }

    @Test
    fun `validity checks length and alphabet`() {
        assertTrue(SharedPlaylistCodes.isValid("ABC234"))
        assertTrue(SharedPlaylistCodes.isValid(SharedPlaylistCodes.newCode()))
        assertFalse(SharedPlaylistCodes.isValid(""))
        assertFalse(SharedPlaylistCodes.isValid("ABC23"))
        assertFalse(SharedPlaylistCodes.isValid("ABC2345"))
        assertFalse(SharedPlaylistCodes.isValid("ABCI23")) // I not in the alphabet
        assertFalse(SharedPlaylistCodes.isValid("ABCO23")) // O not in the alphabet
        assertFalse(SharedPlaylistCodes.isValid("ABC203")) // 0 not in the alphabet
    }

    @Test
    fun `fresh codes are always well formed`() {
        repeat(50) {
            val code = SharedPlaylistCodes.newCode()
            assertEquals(SharedPlaylistCodes.LENGTH, code.length)
            assertTrue(SharedPlaylistCodes.isValid(code))
        }
    }

    @Test
    fun `push happens only against our own last-synced version`() {
        // Nobody wrote since our sync → we may push.
        assertTrue(SharedPlaylistSync.canPush(baseUpdatedAt = 1_000L, remoteUpdatedAt = 1_000L))
        // A friend wrote after our sync → refuse to clobber their edit.
        assertFalse(SharedPlaylistSync.canPush(baseUpdatedAt = 1_000L, remoteUpdatedAt = 2_000L))
        // No recorded base (fresh link, or prefs wiped) → conservative pull first.
        assertFalse(SharedPlaylistSync.canPush(baseUpdatedAt = 0L, remoteUpdatedAt = 5_000L))
        assertFalse(SharedPlaylistSync.canPush(baseUpdatedAt = 0L, remoteUpdatedAt = 0L))
    }
}
