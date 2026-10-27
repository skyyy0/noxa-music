package com.example.data.remote

import android.util.Log
import com.example.data.model.LyricLine
import com.example.data.model.Song
import com.example.data.model.TrackLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Synchronised lyrics from **LRCLIB** (https://lrclib.net).
 *
 * LRCLIB is free, needs no API key and returns real LRC data with per-line
 * timestamps, which is what lets NOXA highlight the line that is being sung
 * right now instead of scrolling a static wall of text.
 *
 * The client is a plain [OkHttpClient] rather than Retrofit because the API has
 * exactly two GET endpoints and every field is optional.
 */
class LyricsRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Small in-memory cache: replaying a track must not re-hit the network. */
    private val cache = LinkedHashMap<String, TrackLyrics?>()
    private val cacheLimit = 40

    suspend fun lyricsFor(song: Song): TrackLyrics? = withContext(Dispatchers.IO) {
        val key = buildString {
            append(song.artist.lowercase())
            append('|')
            append(song.title.lowercase())
            append('|')
            append(song.durationMs / 1000)
        }
        synchronized(cache) { cache[key] }?.let { return@withContext it }

        // 1. Exact match first: artist + title + duration is LRCLIB's strongest key.
        val exact = fetchExact(song)
        // 2. Fall back to a fuzzy search and pick the best candidate.
        val result = exact ?: fetchBestMatch(song)

        synchronized(cache) {
            if (cache.size >= cacheLimit) cache.remove(cache.keys.first())
            cache[key] = result
        }
        result
    }

    /**
     * **Cari pakai lirik** — reverse lookup: a fragment of lyrics becomes the
     * tracks those words belong to.
     *
     * Two keyless sources, merged and de-duplicated by artist+title:
     *
     *  1. **songsear.ch** — the only one that actually full-text searches the
     *     *lyrics themselves* (LRCLIB's `q` only matches title/artist/album,
     *     verified: a pure lyric line returns 0 hits there). Fast (~0.2s),
     *     covers Indonesian songs, fuzzy-matched when no exact line exists.
     *  2. **LRCLIB** — supplement/fallback: matches when the fragment happens
     *     to contain title or artist words. Transient 503 "server busy"
     *     answers get one short retry instead of silently returning nothing.
     *
     * Throws [java.io.IOException] only when *no* source could be reached at
     * all, so the UI can tell "layanan tidak terjangkau" apart from a genuine
     * "belum ada lagu yang cocok". The caller resolves hits against the
     * catalogue so every result is actually playable.
     */
    suspend fun searchByLyricText(query: String): List<LyricSearchHit> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < MIN_LYRIC_QUERY) return@withContext emptyList()

            var reachable = false
            val hits = LinkedHashMap<String, LyricSearchHit>()

            // 1) songsear.ch — the lyrics-content search.
            try {
                val html = get(
                    "$SONGSEARCH_BASE/q/${encode(trimmed)}",
                    retryOnBusy = true,
                    browserUa = true
                )
                if (html != null) {
                    reachable = true
                    for (hit in parseSongSeachResults(html)) {
                        hits.putIfAbsent(hit.dedupeKey(), hit)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "songsear.ch search failed: ${e.message}")
            }

            // 2) LRCLIB q= — title/artist words, as a supplement & fallback.
            if (hits.size < LYRIC_SEARCH_LIMIT) {
                try {
                    val body = get(
                        "$BASE_URL/api/search?q=${encode(trimmed)}",
                        retryOnBusy = true
                    )
                    if (body != null) {
                        reachable = true
                        val array = try {
                            JSONArray(body)
                        } catch (e: Exception) {
                            null
                        }
                        if (array != null) {
                            for (i in 0 until array.length()) {
                                if (hits.size >= LYRIC_SEARCH_LIMIT) break
                                val item = array.optJSONObject(i) ?: continue
                                val track = item.optStringOrNull("trackName") ?: continue
                                val artist = item.optStringOrNull("artistName") ?: continue
                                // LRCLIB mixes instrumental/cover entries; only
                                // real lyric carriers count for a lyric search.
                                if (item.isNull("syncedLyrics") && item.isNull("plainLyrics")) continue
                                val hit = LyricSearchHit(
                                    trackName = track,
                                    artistName = artist,
                                    durationMs = item.optLong("duration", 0L) * 1000L
                                )
                                hits.putIfAbsent(hit.dedupeKey(), hit)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "LRCLIB search failed: ${e.message}")
                }
            }

            // Nothing answered → not "no match", but an unreachable service:
            // the UI says so honestly instead of blaming the query.
            if (!reachable) {
                throw java.io.IOException("Semua layanan lirik tidak terjangkau")
            }
            hits.values.toList()
        }

    private fun fetchExact(song: Song): TrackLyrics? {
        val url = buildString {
            append(BASE_URL)
            append("/api/get?artist_name=").append(encode(song.artist))
            append("&track_name=").append(encode(cleanTitle(song.title)))
            if (song.durationMs > 0) {
                append("&duration=").append(song.durationMs / 1000)
            }
        }
        val body = get(url) ?: return null
        return parseEntry(JSONObject(body))
    }

    private fun fetchBestMatch(song: Song): TrackLyrics? {
        val url = buildString {
            append(BASE_URL)
            append("/api/search?artist_name=").append(encode(song.artist))
            append("&track_name=").append(encode(cleanTitle(song.title)))
        }
        val body = get(url) ?: return null
        val array = try {
            JSONArray(body)
        } catch (e: Exception) {
            return null
        }

        val target = song.durationMs / 1000
        var bestEntry: JSONObject? = null
        var bestScore = Long.MAX_VALUE
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val duration = item.optLong("duration", 0L)
            // Prefer entries that actually carry synchronised lyrics, then the
            // closest duration to the track we are playing.
            val hasSync = !item.isNull("syncedLyrics")
            val distance = if (target > 0 && duration > 0) Math.abs(duration - target) else 0L
            val score = distance + if (hasSync) 0L else OFFSET_PENALTY
            if (score < bestScore) {
                bestScore = score
                bestEntry = item
            }
        }
        return bestEntry?.let { parseEntry(it) }
    }

    private fun parseEntry(json: JSONObject): TrackLyrics? {
        if (json.has("error")) return null
        val synced = json.optStringOrNull("syncedLyrics")
        val plain = json.optStringOrNull("plainLyrics")
        val lines = synced?.let { LrcParser.parse(it) }.orEmpty()
        if (lines.isEmpty() && plain.isNullOrBlank()) return null
        return TrackLyrics(lines = lines, plainText = plain)
    }

    /**
     * One GET with an optional single retry when the answer is LRCLIB's
     * transient `503 ServerOverloaded` (very common when requests arrive back
     * to back — one short pause usually makes the very next try succeed).
     */
    private fun get(
        url: String,
        retryOnBusy: Boolean = false,
        browserUa: Boolean = false
    ): String? {
        repeat(2) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", if (browserUa) BROWSER_UA else USER_AGENT)
                    .header("Accept", if (browserUa) "text/html,application/xhtml+xml" else "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return response.body?.string()
                    if (!(retryOnBusy && attempt == 0 && response.code == 503)) return null
                }
                Thread.sleep(BUSY_RETRY_MS)
            } catch (e: Exception) {
                Log.w(TAG, "Lyrics request failed: ${e.message}")
                return null
            }
        }
        return null
    }

    /**
     * Deezer and YouTube titles carry decoration that LRCLIB does not store,
     * e.g. "Kangen (Remastered 2019)" or "Blinding Lights - Single Version".
     */
    private fun cleanTitle(title: String): String =
        title.replace(Regex("\\s*[\\[(].*?[)\\]]\\s*$"), "")
            .replace(Regex("\\s+-\\s+.*$"), "")
            .trim()
            .ifEmpty { title.trim() }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (isNull(name)) return null
        val value = optString(name, "")
        return value.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "LyricsRepo"
        private const val BASE_URL = "https://lrclib.net"
        private const val USER_AGENT = "NOXA/1.5 (https://github.com/skyyy0/noxa-app)"
        /** Added to a candidate's score when it has no synced lyrics at all. */
        private const val OFFSET_PENALTY = 1_000_000L

        /** A lyric fragment must be at least this long to search by text. */
        const val MIN_LYRIC_QUERY = 4

        /** How many distinct tracks a lyric search returns. */
        private const val LYRIC_SEARCH_LIMIT = 8

        /** songsear.ch — keyless full-text search over lyrics *content*. */
        private const val SONGSEARCH_BASE = "https://songsear.ch"

        /** Browser-looking UA: songsear.ch answers its normal result page. */
        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        /** One pause before retrying LRCLIB's transient "server busy" 503. */
        private const val BUSY_RETRY_MS = 600L
    }
}

/**
 * Parses songsear.ch's `/q/<query>` result page into lyric-search hits.
 *
 * The page is plain server-rendered HTML with a very regular shape — one block
 * per match:
 *
 * ```
 * <div class="result collapsed">
 *   <div class="head">
 *     <h2 title="…"><a href="/song/…">Track Title</a></h2>
 *     <h3><span class="by">by</span> <b>Artist Name</b></h3>
 *     …
 * ```
 *
 * `internal` (and pure) so the parser is unit-testable against a real
 * fixture without touching the network. Blocks without both a title and an
 * artist are skipped; duplicates collapse.
 */
internal fun parseSongSeachResults(html: String): List<LyricSearchHit> {
    val blocks = html.split(Regex("<div class=\"result[ >]"))
    if (blocks.size <= 1) return emptyList()
    val titleRegex = Regex("<h2[^>]*>\\s*<a[^>]*>([^<]+)</a>")
    val h3Regex = Regex("<h3>([\\s\\S]*?)</h3>")
    val boldRegex = Regex("<b>([^<]+)</b>")
    val seen = HashSet<String>()
    val hits = mutableListOf<LyricSearchHit>()
    for (i in 1 until blocks.size) {
        val block = blocks[i]
        val title = titleRegex.find(block)?.groupValues?.get(1)
            ?.let { decodeHtmlEntities(it) }?.trim()
            ?: continue
        // Scope the artist lookup to this block's own <h3> — the album line
        // below it also carries a <b> tag that must not be mistaken for it.
        val head = h3Regex.find(block)?.groupValues?.get(1) ?: continue
        val artist = boldRegex.find(head)?.groupValues?.get(1)
            ?.let { decodeHtmlEntities(it) }?.trim()
            ?: continue
        if (title.isEmpty() || artist.isEmpty()) continue
        val key = "${artist.lowercase()}|${title.lowercase()}"
        if (!seen.add(key)) continue
        // songsear.ch carries no track duration in the list view.
        hits += LyricSearchHit(trackName = title, artistName = artist, durationMs = 0L)
    }
    return hits
}

/** The few entities that actually appear in song & artist names. */
private fun decodeHtmlEntities(text: String): String = text
    .replace("&quot;", "\"")
    .replace("&#x27;", "'")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")

/**
 * One LRCLIB search hit for the "cari pakai lirik" feature: the track the
 * given lyric text belongs to. [durationMs] is the track's own length as
 * reported by LRCLIB, used to pick the best catalog match afterwards.
 */
data class LyricSearchHit(
    val trackName: String,
    val artistName: String,
    val durationMs: Long
) {
    /** Normalised identity so two sources naming the same track collapse. */
    internal fun dedupeKey(): String =
        "${artistName.lowercase().trim()}|${trackName.lowercase().trim()}"
}

/**
 * Parser for the LRC format LRCLIB returns.
 *
 * Understands `[mm:ss.xx]`, `[mm:ss.xxx]` and `[mm:ss]` stamps, several stamps on
 * one line, and ignores the metadata tags (`[ar:]`, `[ti:]`, …).
 */
object LrcParser {

    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun parse(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val matches = timeTag.findAll(raw).toList()
            if (matches.isEmpty()) continue

            val text = raw.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) continue

            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                val fractionRaw = match.groupValues[3]
                val millis = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> fractionRaw.toLongOrNull()?.times(100) ?: 0L
                    2 -> fractionRaw.toLongOrNull()?.times(10) ?: 0L
                    else -> fractionRaw.take(3).toLongOrNull() ?: 0L
                }
                lines += LyricLine(
                    timeMs = (minutes * 60_000L) + (seconds * 1_000L) + millis,
                    text = text
                )
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}
