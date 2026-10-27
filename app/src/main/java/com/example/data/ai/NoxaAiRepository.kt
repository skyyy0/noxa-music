package com.example.data.ai

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * A music action the AI asked the app to execute, carried inside its reply as
 * a machine-readable tag. The assistant cannot touch the player directly — it
 * *declares* intent via tags and the app executes them, which keeps the blast
 * radius of a creative LLM nicely contained.
 */
sealed interface NoxaAiAction {
    /** Play (or offer to play) one song: `[NOXA_PLAY|artist|title]`. */
    data class Play(val artist: String, val title: String) : NoxaAiAction

    /** Create a playlist: `[NOXA_PLAYLIST|name|title1;title2;...]`. */
    data class CreatePlaylist(val name: String, val tracks: List<String>) : NoxaAiAction
}

/** Parses/strips NOXA AI action tags. Kept pure so unit tests can pin it. */
object NoxaAiActions {

    private val PLAY = Regex("""\[NOXA_PLAY\|([^|\]\n]+)\|([^\]\n]+)]""")
    private val PLAYLIST = Regex("""\[NOXA_PLAYLIST\|([^|\]\n]+)\|([^\]\n]+)]""")

    /**
     * Splits a raw reply into its display text and the actions it requested,
     * in order of appearance. Tags never leak into the chat bubble.
     */
    fun parse(reply: String): Pair<String, List<NoxaAiAction>> {
        val actions = mutableListOf<NoxaAiAction>()
        var text = reply
        PLAY.findAll(reply).forEach { match ->
            actions.add(
                NoxaAiAction.Play(
                    artist = match.groupValues[1].trim(),
                    title = match.groupValues[2].trim()
                )
            )
        }
        PLAYLIST.findAll(reply).forEach { match ->
            actions.add(
                NoxaAiAction.CreatePlaylist(
                    name = match.groupValues[1].trim(),
                    tracks = match.groupValues[2].split(';')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                )
            )
        }
        text = text.replace(PLAY, "").replace(PLAYLIST, "")
        return text.trim() to actions
    }

    /** Removes emoji glyphs — the persona forbids them, belt and braces. */
    fun stripEmoji(text: String): String = text
        .replace(Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{1F1E6}-\\x{1F1FF}]"), "")
        .replace(Regex("[ \\t]+\\n"), "\n")
        .trim()
}

/** One chat message rendered in the NOXA AI screen. */
data class AiChatMessage(
    val fromUser: Boolean,
    val text: String,
    /** The song the AI picked, shown as a "Dipilih oleh NOXA AI" card. */
    val songId: String? = null,
    val songTitle: String? = null,
    val songArtist: String? = null,
    val songArtwork: String? = null,
    val songNote: String? = null,
    val playlistName: String? = null
)

/** Result of one AI round-trip. */
data class AiReply(
    val text: String,
    val actions: List<NoxaAiAction>
)

/**
 * The NOXA AI chat backend.
 *
 * Talks to the keyless Omegatech **Gemini2** chat endpoint
 * (`api.omegatech.app/api/ai/Gemini2`) which keeps per-session memory via
 * `sessionId` — persisted here, so the assistant remembers the conversation
 * across app restarts. The previous provider (`Chatgpt-v2`) started failing;
 * the wire format differs only in the reply field (`response` vs `reply`),
 * which is parsed tolerantly below.
 *
 * A strict system prompt is prepended to the **first** message of each
 * session: it fixes the persona (part of NOXA, controls the player through
 * action tags, never uses emoji, stays polite in Indonesian). Replies are
 * additionally emoji-stripped and tag-parsed client side, so the prompt is
 * enforced even when the model gets creative.
 */
class NoxaAiRepository(context: Context) {

    private val prefs = context.getSharedPreferences("noxa_ai", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .build()

    val sessionId: String? get() = prefs.getString(KEY_SESSION, null)?.takeIf { it.isNotBlank() }

    /**
     * Sends one user message and returns the cleaned reply with its actions.
     * Throws [java.io.IOException] (or an [IllegalStateException]) on failure —
     * the caller shows an honest error and keeps the draft.
     */
    suspend fun chat(userMessage: String): AiReply = withContext(Dispatchers.IO) {
        val firstOfSession = sessionId == null
        val payload = if (firstOfSession) "$SYSTEM_PROMPT\n\n$userMessage" else userMessage

        val urlBuilder = BASE_URL.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("action", "chat")
            .addQueryParameter("message", payload)
        sessionId?.let { urlBuilder.addQueryParameter("sessionId", it) }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "NOXA/1.15.7")
            .get()
            .build()

        val raw = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            response.body?.string()
        } ?: throw IllegalStateException("empty body")

        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw IllegalStateException("bad payload")
        }
        // Gemini2 answers { success, statusCode, data }; older Omegatech
        // payloads only carried success — accept either shape.
        val ok = root.optBoolean("success") || root.optInt("statusCode", 0) == 200
        if (!ok) {
            throw IllegalStateException(
                root.optString("message", root.optString("error", "not success"))
            )
        }
        val data = root.optJSONObject("data") ?: throw IllegalStateException("no data")

        // The session token rotates per call — always keep the newest one so
        // the assistant's memory survives app restarts.
        data.optString("sessionId").takeIf { it.isNotBlank() }?.let {
            prefs.edit().putString(KEY_SESSION, it).apply()
        }

        // Gemini2 calls it "response"; tolerate a "reply" payload too.
        val reply = data.optString("response").ifBlank { data.optString("reply") }
        if (reply.isBlank()) throw IllegalStateException("empty reply")

        val cleaned = NoxaAiActions.stripEmoji(reply)
        val (text, actions) = NoxaAiActions.parse(cleaned)
        AiReply(
            text = text.ifBlank { "Siap." },
            actions = actions
        )
    }

    /** Starts a fresh conversation (called from the chat header menu). */
    fun resetSession() {
        prefs.edit().remove(KEY_SESSION).remove(KEY_HISTORY).apply()
    }

    /** Restores the locally persisted chat transcript (newest last). */
    fun loadHistory(): List<AiChatMessage> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                AiChatMessage(
                    fromUser = obj.optBoolean("fromUser"),
                    text = obj.optString("text"),
                    songId = obj.optString("songId").takeIf { it.isNotBlank() },
                    songTitle = obj.optString("songTitle").takeIf { it.isNotBlank() },
                    songArtist = obj.optString("songArtist").takeIf { it.isNotBlank() },
                    songArtwork = obj.optString("songArtwork").takeIf { it.isNotBlank() },
                    songNote = obj.optString("songNote").takeIf { it.isNotBlank() },
                    playlistName = obj.optString("playlistName").takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Persists the transcript so reopening the chat keeps the conversation. */
    fun saveHistory(messages: List<AiChatMessage>) {
        val array = org.json.JSONArray()
        messages.takeLast(HISTORY_LIMIT).forEach { message ->
            array.put(
                org.json.JSONObject()
                    .put("fromUser", message.fromUser)
                    .put("text", message.text)
                    .put("songId", message.songId ?: "")
                    .put("songTitle", message.songTitle ?: "")
                    .put("songArtist", message.songArtist ?: "")
                    .put("songArtwork", message.songArtwork ?: "")
                    .put("songNote", message.songNote ?: "")
                    .put("playlistName", message.playlistName ?: "")
            )
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        private const val KEY_SESSION = "session_id"
        private const val KEY_HISTORY = "history_json"
        private const val HISTORY_LIMIT = 120
        private const val BASE_URL = "https://api.omegatech.app/api/ai/Gemini2"

        /**
         * The persona, sent once per session. Deliberately strict: identity,
         * the tag protocol for player control, Indonesian-only, and a hard
         * no-emoji rule. Personality is "fun friend who knows music" — the
         * user asked for playful, not corporate.
         */
        internal val SYSTEM_PROMPT = """
Kamu adalah "NOXA AI", asisten musik pribadi yang tinggal di dalam aplikasi musik bernama NOXA. Panggil pengguna dengan namanya kalau dia kasih tahu.

KARAKTER: hangat, profesional, dan ceria secukupnya — seperti asisten resmi yang paham musik. Selalu gunakan bahasa Indonesia BAKU yang rapi: ejaan benar, kalimat runtut, tanpa bahasa gaul, singkatan, atau kata seperti "gue", "elo", "bro", "guys", "nih", "dong", "deh". Sapa pengguna dengan "Anda" (atau namanya bila sudah diketahui). Boleh humor ringan dan kalimat yang menyenangkan, tetap sopan dan dengan bahasa yang pantas untuk aplikasi resmi. Jangan pernah bersikap norak atau berlebihan.

KEMAMPUAN KHUSUS: kamu TERHUBUNG ke pemutar musik NOXA. Untuk memutar lagu atau membuat playlist, akhiri balasan dengan tag persis di baris terakhir:
- Satu lagu: [NOXA_PLAY|nama artis|judul lagu]
  Contoh: [NOXA_PLAY|Dewa 19|Kangen]
- Playlist: [NOXA_PLAYLIST|nama playlist|judul1;judul2;judul3]
  Contoh: [NOXA_PLAYLIST|Galau Mode|The One That Got Away;Tak Segampang Itu;Sad]

ATURAN TAG:
- Format tag HARUS persis, tanpa spasi di dalam tag, tanpa backtick, selalu di baris terakhir.
- Pakai tag SAAT user minta lagu diputar, minta playlist dibuatkan, atau memberi mood/curhat yang pantas dapat rekomendasi lagu yang benar-benar ada (katalog NOXA: Deezer, YouTube, Audius — termasuk musik Indonesia).
- Jangan pakai tag kalau user tidak minta musik sama sekali.
- Pilih lagu yang benar-benar ada dan sesuai; jangan mengarang judul.
- Kalau yang diminta ambigu, pilih versi paling terkenal dan bilang singkat pilihanmu.

ATURAN GAYA:
- JANGAN PERNAH memakai emoji, karakter dekoratif, atau ASCII art. Teks biasa saja. (Ini aturan keras.)
- Bahasa Indonesia baku, singkat dan rapi: 1-4 kalimat, kecuali pengguna meminta penjelasan panjang.
- Jangan pernah menyebut dirimu model bahasa atau AI dari perusahaan lain. Kamu NOXA AI, bagian dari NOXA.
""".trimIndent()
    }
}
