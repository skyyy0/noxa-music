package com.example

import com.example.voice.WakeCommand
import com.example.voice.WakeCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NOXA Wake (v3.0.0): penguraian kata bangun + perintah suara.
 *
 * Murni JVM tanpa Robolectric — ini logika paling rawan salah ("putarkan
 * music dewa 19 aku milikmu" harus menghasilkan query yang benar-benar
 * dipakai ke pencarian), jadi wajib teruji di setiap CI build.
 */
class WakeCommandParserTest {

    // ------------------------------------------------------------ wake word

    @Test
    fun `wake word terdeteksi dalam bentuk umum hasil recognizer`() {
        assertTrue(WakeCommandParser.containsWake("Hi NOXA"))
        assertTrue(WakeCommandParser.containsWake("hai noxa"))
        assertTrue(WakeCommandParser.containsWake("Hey NOXA putarkan lagu"))
        assertTrue(WakeCommandParser.containsWake("hinoxa"))
        assertTrue(WakeCommandParser.containsWake("NOXA"))
    }

    @Test
    fun `kalimat tanpa wake word tidak terdeteksi`() {
        assertFalse(WakeCommandParser.containsWake("putarkan dewa 19 aku milikmu"))
        assertFalse(WakeCommandParser.containsWake(""))
    }

    @Test
    fun `brand di tengah kalimat tanpa sapaan bukan wake word`() {
        // v3.1.0 — aturan diperketat: substring brand di dalam kata lain dan
        // brand tanpa sapaan di tengah kalimat dulu membangunkan layanan
        // tanpa user menyebut \"Hi NOXA\" (false positive sensitivitas).
        assertFalse(WakeCommandParser.containsWake("putarkan lagu noxa"))
        assertFalse(WakeCommandParser.containsWake("aplikasi noxa keren banget"))
        assertFalse(WakeCommandParser.containsWake("monoxa rilis versi baru"))
        assertFalse(WakeCommandParser.containsWake("hipnotis itu bukan noxa"))
        assertNull(WakeCommandParser.stripWake("putarkan lagu noxa"))
    }

    @Test
    fun `sapaan di depan brand dan bentuk gabungan tetap terdeteksi`() {
        assertTrue(WakeCommandParser.containsWake("hi noksa"))
        assertTrue(WakeCommandParser.containsWake("hai nosa putarkan lagu"))
        assertTrue(WakeCommandParser.containsWake("hainoxa putarkan lagu"))
        // Menyo\u2019a langsung tanpa sapaan juga sah (brand = kata pertama).
        assertTrue(WakeCommandParser.containsWake("noxa putarkan lagu x"))
        assertEquals(
            "putarkan lagu x",
            WakeCommandParser.stripWake("tolong hi noxa putarkan lagu x")
        )
    }

    @Test
    fun `stripWake membuang sapaan dan menyisakan perintah`() {
        assertEquals("putarkan lagu x", WakeCommandParser.stripWake("hi noxa putarkan lagu x"))
        assertEquals("putar aku milikmu", WakeCommandParser.stripWake("hai noxa putar aku milikmu"))
        assertEquals("", WakeCommandParser.stripWake("Hi NOXA").orEmpty())
        assertNull(WakeCommandParser.stripWake("putar lagu x"))
    }

    // --------------------------------------------------------------- perintah

    @Test
    fun `query bersih dari kata sapaan dan filler`() {
        assertEquals(
            "dewa 19 aku milikmu",
            WakeCommandParser.parse("putarkan music dewa 19 aku milikmu")
                .let { (it as WakeCommand.Play).query }
        )
        assertEquals(
            "dewa 19 aku milikmu",
            WakeCommandParser.parse("play dewa 19 aku milikmu")
                .let { (it as WakeCommand.Play).query }
        )
        assertEquals(
            "aku milikmu",
            WakeCommandParser.parse("tolong putar lagu aku milikmu dong")
                .let { (it as WakeCommand.Play).query }
        )
        assertEquals(
            "sheila on 7 dari semenjak",
            WakeCommandParser.parse("mainkan sheila on 7 dari semenjak")
                .let { (it as WakeCommand.Play).query }
        )
    }

    @Test
    fun `transcript satu napas dengan wake word langsung jadi perintah`() {
        val stripped = WakeCommandParser.stripWake("hi noxa putarkan dewa 19 aku milikmu")!!
        val cmd = WakeCommandParser.parse(stripped)
        assertEquals("dewa 19 aku milikmu", (cmd as WakeCommand.Play).query)
    }

    @Test
    fun `transport pendek dikenali sebagai aksi bukan judul lagu`() {
        assertEquals(WakeCommand.Pause, WakeCommandParser.parse("jeda"))
        assertEquals(WakeCommand.Pause, WakeCommandParser.parse("stop"))
        // "lagu" adalah filler — kata pertama yang berarti = selanjutnya
        assertEquals(WakeCommand.Next, WakeCommandParser.parse("lagu selanjutnya"))
        assertEquals(WakeCommand.Previous, WakeCommandParser.parse("kembali"))
        assertEquals(WakeCommand.Resume, WakeCommandParser.parse("lanjut"))
    }

    @Test
    fun `kata kerja putar tanpa judul berarti lanjutkan pemutaran`() {
        assertEquals(WakeCommand.Resume, WakeCommandParser.parse("putar"))
        assertEquals(WakeCommand.Resume, WakeCommandParser.parse("play"))
    }

    @Test
    fun `transcript kosong dan tak dikenal ditangani jujur`() {
        assertEquals(WakeCommand.Unknown(""), WakeCommandParser.parse("   "))
        assertEquals("", WakeCommandParser.parse("").let { (it as WakeCommand.Unknown).text })
        val unknown = WakeCommandParser.parse("beli sembako di pasar")
        assertTrue(unknown is WakeCommand.Unknown)
        assertTrue(WakeCommandParser.HINT.contains("Hi NOXA"))
    }

    @Test
    fun `normalisasi membuang tanda baca tanpa mematikan query`() {
        assertEquals("dewa 19 aku milikmu", WakeCommandParser.normalize("Dewa 19, Aku Milikmu!"))
        assertEquals("hi noxa", WakeCommandParser.normalize("Hi NOXA?"))
    }
}
