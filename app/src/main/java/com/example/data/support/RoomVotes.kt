package com.example.data.support

/**
 * The rulebook for **Vote Skip** in a Dengar Bareng room — pure Kotlin, no
 * Android types, so the democracy is unit-testable.
 *
 * Design (Sky's call): a skip needs a *strict majority* of the members
 * currently in the room (`members / 2 + 1`). With two people that means both
 * have to agree; with five, three. The vote is always about the song that is
 * playing right now — any playback change resets it.
 *
 * Execution is deterministic: every member's app computes the same
 * **skipper** (the first uid in sorted order among the roster), and only that
 * device performs `next()` when the majority is reached. Everyone else just
 * mirrors the new song like they always do — no matter how many devices
 * notice the majority, the room only ever skips once. If the skipper leaves
 * the room, leadership moves to the next uid automatically.
 */
object RoomVotes {

    /** Votes needed to skip, given the current member count. */
    fun votesNeeded(memberCount: Int): Int =
        if (memberCount <= 1) 1 else memberCount / 2 + 1

    /**
     * True when [voteCount] votes from a roster of [memberCount] members
     * reach the strict majority needed to skip.
     */
    fun shouldSkip(voteCount: Int, memberCount: Int): Boolean {
        if (voteCount <= 0 || memberCount <= 0) return false
        return voteCount >= votesNeeded(memberCount)
    }

    /**
     * Drops ghost ballots: votes from uids no longer on the roster (they
     * left, or their presence timed out) never count toward the majority.
     */
    fun validVotes(votes: List<String>, memberUids: List<String>): List<String> {
        if (votes.isEmpty()) return emptyList()
        val roster = memberUids.toSet()
        return votes.distinct().filter { it in roster }
    }

    /**
     * The one device that performs the skip: the first uid in sorted order
     * among [memberUids]. Deterministic so every member agrees on who acts.
     * Returns null for an empty roster (no room, nobody to skip).
     */
    fun skipperUid(memberUids: List<String>): String? =
        memberUids.filter { it.isNotBlank() }.minOrNull()
}
