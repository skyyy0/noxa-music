package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Extra artist facts the Deezer API never gives us, scraped from two keyless
 * public sources:
 *
 *  * **YouTube** — the real audience number. Deezer's `nb_fan` is a fan count
 *    for one catalogue and reads as a wildly wrong "listeners" figure; the
 *    subscriber count of the artist's official channel is the number people
 *    actually recognise. Fetched from the public search page's embedded
 *    `ytInitialData` — no API key, no quota.
 *  * **MusicBrainz** — genre, country of origin and the year the act started,
 *    which fills the "info kurang lengkap" gap on the artist page.
 *
 * Every part is best-effort: an unreachable source yields `null`/empty and the
 * page simply shows what it already had. Results are cached in memory for the
 * session so reopening a profile is instant.
 */
class ArtistInsightsScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val cache = HashMap<String, ArtistInsights>()

    /** Spotify's own web metadata — monthly listeners & follower count. */
    private val spotify = SpotifyArtistScraper()

    suspend fun insightsFor(artistName: String): ArtistInsights = withContext(Dispatchers.IO) {
        val key = artistName.trim().lowercase()
        cache[key]?.let { return@withContext it }
        val spotifyMeta = runCatching { spotify.metaFor(artistName) }.getOrDefault(
            com.example.data.remote.SpotifyArtistMeta()
        )
        val merged = ArtistInsights(
            youtubeSubscribers = scrapeYouTubeSubscribers(artistName),
            genres = scrapeMusicBrainzGenres(artistName),
            country = scrapeMusicBrainzCountry(artistName),
            activeSince = scrapeMusicBrainzSince(artistName),
            spotifyMonthlyListeners = spotifyMeta.monthlyListeners,
            spotifyFollowers = spotifyMeta.followers
        )
        if (cache.size >= CACHE_LIMIT) cache.clear()
        cache[key] = merged
        merged
    }

    // ------------------------------------------------------------- YouTube

    /**
     * Subscriber count of the artist's channel from a YouTube search page.
     *
     * The page embeds its result JSON as `var ytInitialData = {...}`; every
     * channel card carries `…K/M subscribers` both as `simpleText` and as an
     * accessibility label ("669 thousand subscribers"). The first hit on an
     * `"<artist>" official` query is the artist's own channel — a wrong guess
     * only inflates/deflates one number, never breaks the page.
     */
    internal fun parseYouTubeSubscribers(html: String): Long? {
        // "669K subscribers" / "1.2M subscribers"
        Regex("\"([0-9][0-9.,]*[KMB]) subscribers\"").find(html)?.groupValues?.get(1)
            ?.let { return parseCompactCount(it) }
        // Accessibility label: "669 thousand subscribers" / "12 million subscribers"
        Regex("\"([0-9][0-9.,]*) (thousand|million|billion) subscribers\"")
            .find(html)?.let { m ->
                val base = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
                val factor = when (m.groupValues[2]) {
                    "thousand" -> 1_000.0
                    "million" -> 1_000_000.0
                    else -> 1_000_000_000.0
                }
                return (base * factor).toLong()
            }
        return null
    }

    private fun scrapeYouTubeSubscribers(artistName: String): Long? {
        val url = "https://www.youtube.com/results?search_query=" +
            URLEncoder.encode("\"$artistName\" official", "UTF-8")
        val html = try {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.w(TAG, "YouTube scrape failed: ${e.message}")
            return null
        } ?: return null
        return parseYouTubeSubscribers(html)
    }

    /** "669K" → 669000, "1.2M" → 1200000, "15.500" → 15500. */
    private fun parseCompactCount(raw: String): Long? {
        val text = raw.trim()
        val factor = when (text.lastOrNull()) {
            'K' -> 1_000L
            'M' -> 1_000_000L
            'B' -> 1_000_000_000L
            else -> 1L
        }
        val digits = text.dropLast(if (factor == 1L) 0 else 1).replace(",", "")
        val base = if (digits.contains('.')) {
            digits.toDoubleOrNull()?.toLong()
        } else {
            digits.toLongOrNull()
        } ?: return null
        return base * factor
    }

    // --------------------------------------------------------- MusicBrainz

    /**
     * One MusicBrainz artist search powering genre / country / debut year.
     * The three accessors below each read the shared [mbCache] filled by the
     * first fetch of the session — one HTTP call per artist, not three.
     */
    private val mbCache = HashMap<String, JSONObject?>()

    private fun mbArtistJson(artistName: String): JSONObject? {
        val key = artistName.trim().lowercase()
        if (mbCache.containsKey(key)) return mbCache[key]
        val url = "https://musicbrainz.org/ws/2/artist/?query=" +
            "artist:%22" + URLEncoder.encode(artistName.trim(), "UTF-8") + "%22" +
            "&fmt=json&limit=1"
        val body = try {
            val request = Request.Builder()
                .url(url)
                // MusicBrainz rejects generic/empty user agents.
                .header("User-Agent", "NOXA/2.0 (https://github.com/skyyy0/noxa-app)")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MusicBrainz lookup failed: ${e.message}")
            return null
        } ?: return null
        val artist = try {
            val json = JSONObject(body)
            json.optJSONArray("artists")?.optJSONObject(0)
        } catch (e: Exception) {
            null
        }
        if (mbCache.size >= CACHE_LIMIT) mbCache.clear()
        mbCache[key] = artist
        return artist
    }

    private fun scrapeMusicBrainzGenres(artistName: String): List<String> {
        val tags = mbArtistJson(artistName)?.optJSONArray("tags") ?: return emptyList()
        return (0 until tags.length())
            .mapNotNull { i -> tags.optJSONObject(i)?.optString("name")?.trim() }
            .filter { it.isNotEmpty() }
            .take(GENRE_LIMIT)
    }

    private fun scrapeMusicBrainzCountry(artistName: String): String? =
        mbArtistJson(artistName)
            ?.optJSONObject("area")
            ?.optString("name")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun scrapeMusicBrainzSince(artistName: String): Int? =
        mbArtistJson(artistName)
            ?.optJSONObject("life-span")
            ?.optString("begin")
            ?.takeIf { it.length >= 4 }
            ?.take(4)
            ?.toIntOrNull()

    companion object {
        private const val TAG = "ArtistInsights"
        private const val CACHE_LIMIT = 24
        private const val GENRE_LIMIT = 3
    }
}

/**
 * Scraped extras for the artist page. Every field is optional — the page
 * renders each chip only when its source answered.
 */
data class ArtistInsights(
    /** Official-channel subscriber count (YouTube); null when scrape failed. */
    val youtubeSubscribers: Long? = null,
    /** Top genres from MusicBrainz (rock, pop rock, …). */
    val genres: List<String> = emptyList(),
    /** Country of origin, e.g. "Indonesia". */
    val country: String? = null,
    /** The year the act started (MusicBrainz `life-span.begin`). */
    val activeSince: Int? = null,
    /** Spotify monthly listeners — scraped from the open web player. */
    val spotifyMonthlyListeners: Long? = null,
    /** Spotify follower count — the second audience figure. */
    val spotifyFollowers: Long? = null
) {
    /** True when nothing came back — the caller can skip the whole section. */
    val isEmpty: Boolean
        get() = youtubeSubscribers == null && genres.isEmpty() &&
            country == null && activeSince == null &&
            spotifyMonthlyListeners == null && spotifyFollowers == null
}
