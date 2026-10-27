package com.example.voice

import java.util.Locale

/**
 * Satu perintah suara yang sudah dipahami NOXA.
 *
 * Sengaja pure Kotlin (tanpa Android) supaya bisa diuji unit test langsung —
 * logika "putarkan music dewa 19 aku milikmu" → query "dewa 19 aku milikmu"
 * adalah bagian yang paling gampang salah dan paling murah untuk diuji.
 */
sealed class WakeCommand {
    /** "putar / play / mainkan …" — [query] sudah bersih dari kata sapaan & filler. */
    data class Play(val query: String) : WakeCommand()

    object Pause : WakeCommand()
    object Resume : WakeCommand()
    object Next : WakeCommand()
    object Previous : WakeCommand()

    /**
     * Tidak dikenali. [text] kosong bila transcript hanya kata sapaan
     * ("hi noxa") — layanan menangani itu sebagai mode perintah, bukan error.
     */
    data class Unknown(val text: String) : WakeCommand()
}

/**
 * Penerjemah transcript → [WakeCommand] untuk fitur **NOXA Wake** (v3.0.0).
 *
 * Dua tugas:
 *  1. Mendeteksi kata bangun "Hi/Hai NOXA" (dan variasi ejaan hasil ASR
 *     sistem seperti "hinoxa", "noksa") lewat [containsWake] / [stripWake].
 *  2. Menerjemahkan sisa kalimat jadi aksi lewat [parse].
 *
 * Bahasa: campuran Indonesia–Inggris persis seperti yang diucapkan pengguna
 * ("putarkan music dewa 19 aku milikmu", "play dewa 19 aku milikmu",
 * "jeda", "lagu selanjutnya").
 */
object WakeCommandParser {

    /** Kata sapaan yang boleh mendahului brand — dibuang ikut bersama wake word. */
    private val GREETINGS = setOf(
        "hi", "hai", "hey", "hei", "he", "halo", "hallo", "hola", "oi", "woi", "eh"
    )

    /**
     * Bentuk brand yang dieja beda oleh recognizer sistem.
     *
     * Kecocokan harus TEPAT per kata (bukan `contains`): kecocokan substring
     * membuat banyak kata biasa ikut terdeteksi sebagai wake word, sehingga
     * bubble muncul tanpa user benar-benar menyebut "Hi NOXA". Variasi yang
     * digabung dengan sapaan ("hinoxa") ditangani [isMergedWake].
     */
    private val BRAND_FORMS = setOf("noxa", "noksa", "nosa", "nokca")

    /** Kata kerja putar — posisinya menandai awal query lagu. */
    private val PLAY_VERBS = setOf(
        "putar", "putarkan", "putarlah", "puterin", "puter", "memutar",
        "mainkan", "main", "play"
    )

    /** Kata pengisi yang dibuang dari query ("putarkan MUSIK dewa 19 …"). */
    private val FILLERS = setOf(
        "musik", "music", "lagu", "lagunya", "song", "dong", "ya", "yah",
        "tolong", "plis", "please", "bos", "bang", "kak", "yuk", "ayo", "nih"
    )

    private val PAUSE_WORDS = setOf("jeda", "pause", "berhenti", "stop", "hentikan", "diam")
    private val RESUME_WORDS = setOf("lanjut", "lanjutkan", "lanjutin", "resume", "sambung", "putarkan")
    private val NEXT_WORDS = setOf("selanjutnya", "berikutnya", "next", "skip", "lewati")
    private val PREV_WORDS = setOf("sebelumnya", "kembali", "previous", "prev")

    /** Lowercase, buang tanda baca, rapikan spasi. */
    fun normalize(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun wordsOf(text: String): List<String> =
        normalize(text).split(" ").filter { it.isNotBlank() }

    private fun isBrandWord(word: String): Boolean = word in BRAND_FORMS

    /**
     * "hinoxa" / "hainoxa" — sapaan + brand yang disatukan recognizer jadi
     * satu kata. Hanya dihitung bila tepat sapaan + brand (bukan sembarang
     * kata yang kebetulan memuat brand di dalamnya).
     */
    private fun isMergedWake(word: String): Boolean =
        GREETINGS.any { greeting -> BRAND_FORMS.any { word == greeting + it } }

    /**
     * Index token wake pertama dalam utterance, atau -1.
     *
     * Aturan ketat (perbaikan sensitivitas v3.1.0) — wake terdeteksi HANYA bila:
     *  * kata itu bentuk gabungan sapaan+brand ("hinoxa"), ATAU
     *  * kata itu brand persis dan berada di awal kalimat (user menyo\u2019a
     *    langsung: "noxa, putarkan …"), ATAU
     *  * brand persis didahului kata sapaan ("hi noxa", "hai noxa putar …").
     *
     * Brand di tengah kalimat tanpa sapaan ("putarkan lagu noxa",
     * "aplikasi noxa keren", transkrip lirik/obrolan) TIDAK membangunkan
     * layanan — itu sumber false positive yang membuat ikon mikrofon
     * "merespons" tanpa ucapan "Hi NOXA".
     */
    private fun wakeIndex(text: String): Int {
        val words = wordsOf(text)
        words.forEachIndexed { index, word ->
            if (isMergedWake(word)) return index
            if (isBrandWord(word) &&
                (index == 0 || words[index - 1] in GREETINGS)
            ) {
                return index
            }
        }
        return -1
    }

    /** Ada tidaknya kata bangun — lihat [wakeIndex] untuk aturannya. */
    fun containsWake(text: String): Boolean = wakeIndex(text) >= 0

    /**
     * Kalimat SETELAH frase "hi noxa" dibuang, atau null bila tidak ada
     * wake word sama sekali. Kata sapaan sebelum brand ikut terbuang.
     */
    fun stripWake(text: String): String? {
        val i = wakeIndex(text)
        if (i < 0) return null
        return wordsOf(text).drop(i + 1).joinToString(" ").trim()
    }

    /**
     * Terjemahkan satu utterance jadi aksi.
     *
     * Urutan: frasa pendek transport (jeda/lanjut/…) dulu — "jeda" tidak boleh
     * tertangkap sebagai lagu berjudul jeda — lalu kata kerja putar, lalu
     * fallback [WakeCommand.Unknown].
     */
    fun parse(raw: String): WakeCommand {
        val text = normalize(stripWake(raw) ?: raw)
        if (text.isBlank()) return WakeCommand.Unknown("")
        val words = text.split(" ")

        // frasa pendek transport — cukup satu kata berpengaruh di awal kalimat
        val head = words.firstOrNull { it !in FILLERS } ?: return WakeCommand.Unknown(text)
        if (head in PAUSE_WORDS) return WakeCommand.Pause
        if (head in RESUME_WORDS && words.size > 1 && !words.any { it in PLAY_VERBS }) {
            // "lanjutkan" murni = resume; "lanjutkan lagu x" jatuh ke bawah.
            return WakeCommand.Resume
        }
        if (head in RESUME_WORDS && words.size == 1) return WakeCommand.Resume
        if (head in NEXT_WORDS) return WakeCommand.Next
        if (head in PREV_WORDS) return WakeCommand.Previous

        // kata kerja putar → sisa kalimat adalah query (filler di awal &
        // partikel di akhir dibuang: "…aku milikmu DONG")
        val verbIndex = words.indexOfFirst { it in PLAY_VERBS }
        if (verbIndex >= 0) {
            val rest = words
                .drop(verbIndex + 1)
                .dropWhile { it in FILLERS }
                .dropLastWhile { it in FILLERS }
            if (rest.isEmpty()) return WakeCommand.Resume
            return WakeCommand.Play(rest.joinToString(" "))
        }

        // tanpa kata kerja, jujur bahwa tidak dikenali — bubble memberi contoh.
        return WakeCommand.Unknown(text)
    }

    /**
     * Pesan bantuan saat transcript tidak dikenali — dipakai bubble & toast.
     * Konsisten di satu tempat supaya wording tidak menyebar.
     */
    val HINT: String =
        "Belum mengerti. Coba: \u201cHi NOXA, putarkan Dewa 19 Aku Milikmu\u201d."
}
