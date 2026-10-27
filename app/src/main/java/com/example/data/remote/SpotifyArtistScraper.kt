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
 * **Spotify artist metadata**, ported from the keyless open.spotify.com
 * scraper technique: an anonymous access token is read from a public embed
 * page (`__NEXT_DATA__` → `state.settings.session.accessToken`), then the
 * web player's own `api-partner.spotify.com/pathfinder` GraphQL endpoint is
 * queried with the persisted query hashes extracted from its bundle.
 *
 * What this adds that Deezer/MusicBrainz/YouTube cannot give us:
 *  * **monthly listeners** — the headline number on every Spotify artist page;
 *  * **Spotify follower count** — a second, very recognised audience figure;
 *  * avatar + header artwork sources for a richer artist header.
 *
 * Spotify's public web payload does not expose genres or popularity (they are
 * `null` / `[]` there — verified by the original scraper), so those stay with
 * MusicBrainz. Everything is best-effort: any failure degrades to an empty
 * [SpotifyArtistMeta] and the artist page simply shows what it already had.
 */
class SpotifyArtistScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val cache = HashMap<String, SpotifyArtistMeta>()

    private var cachedToken: String? = null
    private var tokenExpiresAtMs: Long = 0L

    suspend fun metaFor(artistName: String): SpotifyArtistMeta = withContext(Dispatchers.IO) {
        val key = artistName.trim().lowercase()
        cache[key]?.let { return@withContext it }
        val meta = runCatching { fetch(artistName.trim()) }
            .getOrElse {
                Log.w(TAG, "Spotify lookup for \"$artistName\" failed: ${it.message}")
                SpotifyArtistMeta()
            }
        if (cache.size >= CACHE_LIMIT) cache.clear()
        cache[key] = meta
        meta
    }

    private fun fetch(artistName: String): SpotifyArtistMeta {
        val token = token() ?: return SpotifyArtistMeta()
        val uri = searchArtistUri(artistName, token) ?: return SpotifyArtistMeta()
        val union = artistOverview(uri, token) ?: return SpotifyArtistMeta()
        val stats = union.optJSONObject("stats") ?: JSONObject()
        return SpotifyArtistMeta(
            monthlyListeners = stats.optLongOrNull("monthlyListeners"),
            followers = stats.optLongOrNull("followers") ?: stats.optLongOrNull("followerCount"),
            avatarUrl = largestSource(
                union.optJSONObject("visuals")
                    ?.optJSONObject("avatarImage")
                    ?.optJSONArray("sources")
            ),
            headerImageUrl = largestSource(
                union.optJSONObject("headerImage")?.optJSONArray("sources")
            )
        )
    }

    // ---------------------------------------------------------------- token

    /**
     * Anonymous token from the first embed page that answers. These public
     * embeds mint a real anonymous session token without any login.
     */
    private fun token(): String? {
        cachedToken?.takeIf { System.currentTimeMillis() < tokenExpiresAtMs - 60_000L }
            ?.let { return it }
        for (path in TOKEN_SOURCES) {
            val html = getHtml("$EMBED_BASE$path") ?: continue
            val raw = NEXT_DATA_REGEX.find(html)?.groupValues?.get(1) ?: continue
            try {
                val session = JSONObject(raw)
                    .optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("state")
                    ?.optJSONObject("settings")
                    ?.optJSONObject("session") ?: continue
                val access = session.optString("accessToken", "")
                if (access.isBlank()) continue
                cachedToken = access
                tokenExpiresAtMs = session.optLong(
                    "accessTokenExpirationTimestampMs",
                    System.currentTimeMillis() + 3_600_000L
                )
                return access
            } catch (e: Exception) {
                Log.w(TAG, "Embed token parse failed for $path: ${e.message}")
            }
        }
        return null
    }

    // ------------------------------------------------------------ pathfinder

    /** Spotify artist id for [artistName], via the web player's search. */
    private fun searchArtistUri(artistName: String, token: String): String? {
        val variables = JSONObject()
            .put("searchTerm", artistName)
            .put("offset", 0)
            .put("limit", 1)
        val body = pathfinder("searchArtists", SEARCH_ARTISTS_HASH, variables, token)
            ?: return null
        return try {
            body.optJSONObject("searchV2")
                ?.optJSONObject("artists")
                ?.optJSONArray("items")
                ?.optJSONObject(0)
                ?.optJSONObject("data")
                ?.optString("uri", "")
                ?.takeIf { it.startsWith("spotify:artist:") }
        } catch (e: Exception) {
            null
        }
    }

    /** `queryArtistOverview` — profile, stats and visuals for one artist URI. */
    private fun artistOverview(uri: String, token: String): JSONObject? {
        val variables = JSONObject().put("uri", uri)
        val body = pathfinder("queryArtistOverview", ARTIST_OVERVIEW_HASH, variables, token)
            ?: return null
        return body.optJSONObject("artistUnion")
    }

    private fun pathfinder(
        operationName: String,
        hash: String,
        variables: JSONObject,
        token: String
    ): JSONObject? {
        val url = "$PATHFINDER_BASE?operationName=$operationName" +
            "&variables=${URLEncoder.encode(variables.toString(), "UTF-8")}" +
            "&extensions=${URLEncoder.encode(
                JSONObject()
                    .put(
                        "persistedQuery",
                        JSONObject().put("version", 1).put("sha256Hash", hash)
                    )
                    .toString(), "UTF-8"
            )}"
        val body = getJson(url, token) ?: return null
        // GraphQL errors without data mean the persisted hash went stale.
        if (body.has("errors") && body.optJSONObject("data") == null) {
            Log.w(TAG, "$operationName answered errors: ${body.optJSONArray("errors")}")
            return null
        }
        return body.optJSONObject("data")
    }

    // ------------------------------------------------------------------ HTTP

    private fun getHtml(url: String): String? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "GET $url failed: ${e.message}")
        null
    }

    private fun getJson(url: String, token: String): JSONObject? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            // 401/403: the anonymous token aged out — clear it so the next
            // attempt mints a fresh one instead of looping on the dead token.
            if (response.code == 401 || response.code == 403) {
                cachedToken = null
                tokenExpiresAtMs = 0L
                return null
            }
            if (!response.isSuccessful) return null
            val text = response.body?.string() ?: return null
            JSONObject(text)
        }
    } catch (e: Exception) {
        Log.w(TAG, "GET $url failed: ${e.message}")
        null
    }

    /** Largest image source URL (width or maxWidth decides). */
    private fun largestSource(sources: org.json.JSONArray?): String? {
        if (sources == null) return null
        var bestUrl: String? = null
        var bestWidth = -1
        for (i in 0 until sources.length()) {
            val source = sources.optJSONObject(i) ?: continue
            val url = source.optString("url", "")
            if (url.isBlank()) continue
            val width = source.optInt("width", source.optInt("maxWidth", 0))
            if (width >= bestWidth) {
                bestWidth = width
                bestUrl = url
            }
        }
        return bestUrl
    }

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (optLong(name, -1L) >= 0L) optLong(name) else null

    companion object {
        private const val TAG = "SpotifyScraper"
        private const val EMBED_BASE = "https://open.spotify.com"
        private const val PATHFINDER_BASE = "https://api-partner.spotify.com/pathfinder/v1/query"
        private const val CACHE_LIMIT = 24

        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        /** Public embed pages that mint an anonymous session token. */
        private val TOKEN_SOURCES = listOf(
            "/embed/track/0VjIjW4GlUZAMYd2vXMi3b", // Blinding Lights
            "/embed/playlist/37i9dQZF1DXcBWIGoYBM5M", // Today's Top Hits
            "/embed/album/4yP0hdKOZPNshxUOjY0cZj" // After Hours
        )

        /** Persisted query hashes from the Spotify web player bundle. */
        private const val SEARCH_ARTISTS_HASH =
            "7bf95d754fdbe32c8b161fbbe54d1ae50974900df4dce4c8f1afcbcad153224d"
        private const val ARTIST_OVERVIEW_HASH =
            "9f8134ef565e78621f1e1793555bd6633c5ac144ae0f89604ed3ae3f80b3c8e6"

        private val NEXT_DATA_REGEX =
            Regex("<script[^>]*id=\"__NEXT_DATA__\"[^>]*>([\\s\\S]*?)</script>")
    }
}

/**
 * Spotify-side artist facts. Every field is optional — an unreachable
 * Spotify leaves all of them null and the artist page renders unchanged.
 */
data class SpotifyArtistMeta(
    /** The famous "monthly listeners" figure from the artist page. */
    val monthlyListeners: Long? = null,
    /** Spotify follower count (a second, widely recognised audience figure). */
    val followers: Long? = null,
    /** Largest avatar image URL. */
    val avatarUrl: String? = null,
    /** Largest header/banner image URL. */
    val headerImageUrl: String? = null
) {
    val isEmpty: Boolean
        get() = monthlyListeners == null && followers == null &&
            avatarUrl == null && headerImageUrl == null
}
