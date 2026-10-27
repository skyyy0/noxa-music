package com.example

import com.example.data.ai.NoxaAiAction
import com.example.data.ai.NoxaAiActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AI controls the player through tags embedded in its reply. These tests
 * pin the protocol: tags are extracted in order, never leak into the visible
 * chat text, and emoji are stripped even when the model disobeys the prompt.
 */
class NoxaAiActionsTest {

    @Test
    fun `play tag is extracted and removed from text`() {
        val reply = "Siap, gue putarin Kangen.\n[NOXA_PLAY|Dewa 19|Kangen]"
        val (text, actions) = NoxaAiActions.parse(reply)

        assertFalse(text.contains("NOXA_PLAY"))
        assertEquals(1, actions.size)
        val play = actions[0] as NoxaAiAction.Play
        assertEquals("Dewa 19", play.artist)
        assertEquals("Kangen", play.title)
        assertTrue(text.startsWith("Siap"))
    }

    @Test
    fun `playlist tag carries name and tracks`() {
        val reply = "Playlist Galau Mode jadi ya.\n[NOXA_PLAYLIST|Galau Mode|The One That Got Away;Tak Segampang Itu]"
        val (text, actions) = NoxaAiActions.parse(reply)

        assertFalse(text.contains("NOXA_PLAYLIST"))
        val playlist = actions[0] as NoxaAiAction.CreatePlaylist
        assertEquals("Galau Mode", playlist.name)
        assertEquals(listOf("The One That Got Away", "Tak Segampang Itu"), playlist.tracks)
    }

    @Test
    fun `reply without tags yields plain text and no actions`() {
        val (text, actions) = NoxaAiActions.parse("Halo! Mau dengar apa hari ini?")
        assertEquals("Halo! Mau dengar apa hari ini?", text)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `emoji are stripped from replies`() {
        val cleaned = NoxaAiActions.stripEmoji("Hai, Sky! 👋😊 Senang berkenalan 💙")
        assertFalse(cleaned.any { it.code in 0x1F000..0x1FAFF })
        assertTrue(cleaned.contains("Hai, Sky!"))
    }
}
