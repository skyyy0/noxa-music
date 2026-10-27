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
 * **Bio artis** from the Wikipedia REST summary API (keyless, free).
 *
 * Deezer's public API no longer returns artist biographies, so the artist
 * page fetches a short, honest summary from Wikipedia instead — first the
 * Indonesian edition, then the English one. Every failure (offline, no page,
 * blocked network) degrades to `null`: the bio section simply does not
 * render, the rest of the artist page is unaffected.
 */
class ArtistBioRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Small in-memory cache so re-opening a profile is instant. */
    private val cache = HashMap<String, String?>()

    suspend fun bioFor(artistName: String): String? = withContext(Dispatchers.IO) {
        val key = artistName.trim().lowercase()
        if (cache.containsKey(key)) return@withContext cache[key]
        val result = fetchSummary(artistName, WIKI_ID) ?: fetchSummary(artistName, WIKI_EN)
        // Keep the cache small; a profile session only ever visits a handful.
        if (cache.size >= CACHE_LIMIT) cache.clear()
        cache[key] = result
        result
    }

    private fun fetchSummary(title: String, lang: String): String? {
        val url = "https://$lang.wikipedia.org/api/rest_v1/page/summary/" +
            URLEncoder.encode(title.trim().replace(' ', '_'), "UTF-8")
        val body = try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                // 404 simply means "no such article" — not an error worth logging.
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bio lookup failed: ${e.message}")
            return null
        } ?: return null
        return try {
            val json = JSONObject(body)
            val extract = json.optString("extract", "")
            extract.takeIf { it.isNotBlank() && it.length >= MIN_EXTRACT }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "ArtistBio"
        private const val USER_AGENT = "NOXA/2.0 (https://github.com/skyyy0/noxa-app)"
        private const val WIKI_ID = "id.wikipedia.org"
        private const val WIKI_EN = "en.wikipedia.org"

        /** Shorter extracts are stubs, not bios — skip them. */
        private const val MIN_EXTRACT = 80
        private const val CACHE_LIMIT = 24
    }
}
