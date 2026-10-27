package com.example

import com.example.data.support.RoomVotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vote Skip: strict majority, ghost voters dropped, one deterministic skipper. */
class RoomVotesTest {

    @Test
    fun `majority needs more than half the roster`() {
        assertEquals(1, RoomVotes.votesNeeded(1))
        assertEquals(2, RoomVotes.votesNeeded(2))
        assertEquals(2, RoomVotes.votesNeeded(3))
        assertEquals(3, RoomVotes.votesNeeded(4))
        assertEquals(3, RoomVotes.votesNeeded(5))
    }

    @Test
    fun `skip fires only at strict majority`() {
        assertFalse(RoomVotes.shouldSkip(0, 3))
        assertFalse(RoomVotes.shouldSkip(1, 2))
        assertTrue(RoomVotes.shouldSkip(2, 2))
        assertFalse(RoomVotes.shouldSkip(1, 3))
        assertTrue(RoomVotes.shouldSkip(2, 3))
        assertTrue(RoomVotes.shouldSkip(3, 4))
        // A solo listener's single vote is a skip (it is just "next" anyway).
        assertTrue(RoomVotes.shouldSkip(1, 1))
        // No room, no vote.
        assertFalse(RoomVotes.shouldSkip(2, 0))
        assertFalse(RoomVotes.shouldSkip(-1, 3))
    }

    @Test
    fun `ghost voters who left the room are dropped`() {
        val roster = listOf("uid-a", "uid-b")
        assertEquals(
            listOf("uid-a"),
            RoomVotes.validVotes(listOf("uid-a", "uid-left-long-ago"), roster)
        )
        assertEquals(emptyList<String>(), RoomVotes.validVotes(listOf("uid-x"), roster))
        // Duplicates from a racy publish count once.
        assertEquals(
            listOf("uid-a", "uid-b"),
            RoomVotes.validVotes(listOf("uid-a", "uid-a", "uid-b"), roster)
        )
    }

    @Test
    fun `skipper is deterministic so the room never skips twice`() {
        assertEquals("uid-a", RoomVotes.skipperUid(listOf("uid-b", "uid-a", "uid-c")))
        assertEquals("uid-a", RoomVotes.skipperUid(listOf("uid-a", "uid-b")))
        // Whoever notices the majority, the same single device acts.
        val roster = listOf("z", "a", "m")
        assertEquals(RoomVotes.skipperUid(roster), RoomVotes.skipperUid(roster.shuffled()))
        assertNull(RoomVotes.skipperUid(emptyList()))
        assertNull(RoomVotes.skipperUid(listOf("", " ")))
    }
}
