package com.example.data.support

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One comment left on one song. */
data class SongComment(
    val id: String,
    val uid: String,
    /** Display name at the time of posting (renames don't rewrite history). */
    val name: String,
    val text: String,
    /** Posting time (epoch ms). */
    val at: Long
)

/**
 * **Komentar per Lagu** — a shared comment thread under every song, stored on
 * its own keyless Telegraph page (same architecture as [NoxaBackend]: no
 * server, no API key, every installation reads and writes one JSON document).
 *
 * The document is `{"v":1,"seq":N,"songs":{"<songId>":[comment, ...]}}`
 * rendered as the page content — identical payload tricks to the core
 * backend, so a slow or rate-limited CDN degrades to "comments unavailable"
 * instead of ever crashing the player.
 *
 * Disciplines that keep the shared document healthy:
 *  * text is trimmed and capped at [MAX_TEXT] characters;
 *  * each song keeps at most the newest [PER_SONG_LIMIT] comments;
 *  * a client posts at most one comment per [POST_COOLDOWN_MS];
 *  * every write re-reads (optimistic `seq` retry) so a concurrent post from
 *    another device is retried rather than clobbered.
 */
class SongCommentsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    /** Small in-memory cache so reopening the sheet is instant. */
    private val cache = HashMap<String, List<SongComment>>()

    /** Epoch ms of this device's last successful post (cooldown). */
    private var lastPostAt = 0L

    /** Newest first, from cache when warm. */
    suspend fun commentsFor(songId: String): List<SongComment> =
        cache[songId] ?: fetch(songId).also { cache[songId] = it }

    /**
     * Posts one comment for [songId].
     *
     * @return `null` on success, or a user-facing Indonesian refusal
     *         (cooldown / empty / backend unreachable).
     */
    suspend fun post(songId: String, uid: String, name: String, rawText: String): String? =
        withContext(Dispatchers.IO) {
            val text = rawText.trim().take(MAX_TEXT)
            if (text.isEmpty()) return@withContext "Komentar masih kosong."
            val now = System.currentTimeMillis()
            if (now - lastPostAt < POST_COOLDOWN_MS) {
                return@withContext "Komentar keburu cepat. Tunggu sebentar lagi, ya."
            }
            val comment = SongComment(
                id = "$now-${uid.hashCode()}",
                uid = uid,
                name = name,
                text = text,
                at = now
            )
            // Optimistic read-modify-write with a short retry: seq ordering
            // mirrors NoxaBackend.mutate, so a concurrent post elsewhere is
            // re-read instead of silently overwritten.
            repeat(WRITE_ATTEMPTS) { attempt ->
                val doc = readDoc() ?: return@withContext UNREACHABLE
                val songs = doc.optJSONObject("songs") ?: JSONObject()
                val list = songs.optJSONArray(songId) ?: JSONArray()
                val mine = buildList<SongComment> {
                    for (i in 0 until list.length()) {
                        list.optJSONObject(i)?.let { add(parseComment(it)) }
                    }
                }
                val updated = mine.filter { it.uid != uid || it.at != comment.at } + comment
                songs.put(songId, JSONArray().also { arr ->
                    updated.takeLast(PER_SONG_LIMIT).forEach { arr.put(serialize(it)) }
                })
                doc.put("songs", songs)
                doc.put("seq", doc.optLong("seq") + 1)
                if (writeDoc(doc)) {
                    cache[songId] = updated.takeLast(PER_SONG_LIMIT).sortedByDescending { it.at }
                    lastPostAt = System.currentTimeMillis()
                    return@withContext null
                }
                if (attempt < WRITE_ATTEMPTS - 1) delay(400L * (attempt + 1))
            }
            UNREACHABLE
        }

    /** Drops the cache (used after a failed write so the next open re-fetches). */
    fun invalidate() {
        cache.clear()
    }

    // ------------------------------------------------------------------ net

    private suspend fun fetch(songId: String): List<SongComment> = withContext(Dispatchers.IO) {
        val doc = readDoc() ?: return@withContext emptyList()
        val songs = doc.optJSONObject("songs") ?: return@withContext emptyList()
        val list = songs.optJSONArray(songId) ?: return@withContext emptyList()
        buildList<SongComment> {
            for (i in 0 until list.length()) {
                list.optJSONObject(i)?.let { add(parseComment(it)) }
            }
        }.sortedByDescending { it.at }
    }

    /** Reads the comments page and yields its JSON payload (or `null`). */
    private suspend fun readDoc(): JSONObject? {
        repeat(READ_ATTEMPTS) { attempt ->
            val raw = httpGet("$API_BASE/getPage/$PAGE_PATH?return_content=true")
            if (raw != null) {
                val payload = try {
                    val root = JSONObject(raw)
                    if (!root.optBoolean("ok")) null else {
                        val content = root.getJSONObject("result").optJSONArray("content")
                        var found: String? = null
                        if (content != null) {
                            for (i in 0 until content.length()) {
                                val node = content.optJSONObject(i) ?: continue
                                val children = node.optJSONArray("children") ?: continue
                                for (j in 0 until children.length()) {
                                    val text = children.optString(j)
                                    if (text.startsWith("{")) {
                                        found = text
                                        break
                                    }
                                }
                                if (found != null) break
                            }
                        }
                        found?.let { JSONObject(it) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Comment doc parse failed: ${e.message}")
                    null
                }
                if (payload != null) return payload
            }
            if (attempt < READ_ATTEMPTS - 1) delay(350L * (attempt + 1))
        }
        return null
    }

    private suspend fun writeDoc(doc: JSONObject): Boolean {
        val children = JSONArray().put(doc.toString())
        val content = JSONArray().put(JSONObject().put("tag", "p").put("children", children))
        val body = FormBody.Builder()
            .add("access_token", PAGE_TOKEN)
            .add("title", PAGE_TITLE)
            .add("content", content.toString())
            .build()
        repeat(WRITE_ATTEMPTS) { attempt ->
            val raw = httpPost("$API_BASE/editPage/$PAGE_PATH", body)
            val ok = try {
                raw != null && JSONObject(raw).optBoolean("ok")
            } catch (e: Exception) {
                false
            }
            if (ok) return true
            if (attempt < WRITE_ATTEMPTS - 1) delay(400L * (attempt + 1))
        }
        return false
    }

    private fun httpGet(url: String): String? = try {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { it.body?.string() }
    } catch (e: Exception) {
        Log.w(TAG, "GET failed: ${e.message}")
        null
    }

    private fun httpPost(url: String, body: FormBody): String? = try {
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { it.body?.string() }
    } catch (e: Exception) {
        Log.w(TAG, "POST failed: ${e.message}")
        null
    }

    private fun parseComment(obj: JSONObject): SongComment = SongComment(
        id = obj.optString("id"),
        uid = obj.optString("uid"),
        name = obj.optString("name"),
        text = obj.optString("text"),
        at = obj.optLong("at")
    )

    private fun serialize(c: SongComment): JSONObject = JSONObject()
        .put("id", c.id)
        .put("uid", c.uid)
        .put("name", c.name)
        .put("text", c.text)
        .put("at", c.at)

    companion object {
        private const val TAG = "SongComments"
        private const val USER_AGENT = "NOXA/2.0 (https://github.com/skyyy0/noxa-app)"

        private const val API_BASE = "https://api.telegra.ph"

        /** The dedicated comments document (created once, token in the APK). */
        private const val PAGE_PATH = "NOXA-Song-Comments-10-18"
        private const val PAGE_TITLE = "NOXA Song Comments"
        private const val PAGE_TOKEN =
            "7941e33f1aeb16bf70aed150bdf81ef1f0700eee189f7ca79cdbf2a416bf"

        private const val READ_ATTEMPTS = 3
        private const val WRITE_ATTEMPTS = 2

        /** Character cap per comment (keeps the shared document bounded). */
        private const val MAX_TEXT = 400

        /** Newest comments kept per song. */
        private const val PER_SONG_LIMIT = 40

        /** Minimum gap between two posts from this device. */
        private const val POST_COOLDOWN_MS = 10_000L

        /** User-facing marker for "backend unreachable". */
        const val UNREACHABLE = "server_noxa_tidak_tersedia"
    }
}
