package com.example.data.remote

import android.util.Log
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Optional bridge to a **stream resolver endpoint you run yourself**
 * (`STREAM_RESOLVER_URL` in `.env`).
 *
 * NOXA can already search and display tracks from YouTube's metadata API, but
 * YouTube never returns audio. This class is the single, explicit extension point
 * where a track can be upgraded to a real full-length audio URL — supplied by a
 * backend you control and are responsible for. Nothing is embedded here: NOXA
 * simply asks your endpoint and plays whatever HTTPS URL it returns.
 *
 * Request (issued when the user starts playback):
 *
 * ```
 * GET <STREAM_RESOLVER_URL>?id=<song id>&source=<AUDIUS|DEEZER|...>
 *                           &title=<title>&artist=<artist>&durationMs=<ms>
 * Accept: application/json
 * ```
 *
 * Accepted responses, first match wins:
 *
 * ```json
 * { "streamUrl": "https://cdn.example/full-track.mp3" }
 * { "url": "https://cdn.example/full-track.mp3" }
 * ```
 *
 * Returning HTTP 404 / an empty body / no URL means "no full version available",
 * and the app keeps the track's original source instead.
 */
class StreamResolver {

    private val endpoint: String? = Secrets.get("STREAM_RESOLVER_URL")
        .trim()
        .takeIf { it.isNotEmpty() && it.startsWith("http") }

    /** True when a resolver is configured, so the UI can explain what will happen. */
    val isConfigured: Boolean get() = endpoint != null

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Asks the resolver for a full-length audio URL for [song].
     *
     * Returns a copy of [song] pointing at the resolved stream (and no longer
     * flagged as a preview), or `null` when no resolver is configured or it had
     * nothing to offer.
     */
    suspend fun resolve(song: Song): Song? = withContext(Dispatchers.IO) {
        val base = endpoint ?: return@withContext null
        val url = buildString {
            append(base)
            append(if (base.contains('?')) '&' else '?')
            append("id=").append(encode(song.id))
            append("&source=").append(encode(song.source.name))
            append("&title=").append(encode(song.title))
            append("&artist=").append(encode(song.artist))
            append("&durationMs=").append(song.durationMs)
            if (song.remoteId.isNotBlank()) {
                append("&remoteId=").append(encode(song.remoteId))
            }
        }

        val body = try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "NOXA/1.13.3")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Resolver request failed: ${e.message}")
            null
        } ?: return@withContext null

        val streamUrl = try {
            val json = JSONObject(body)
            val candidate = when {
                !json.isNull("streamUrl") -> json.optString("streamUrl")
                !json.isNull("url") -> json.optString("url")
                else -> ""
            }
            candidate.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        } catch (e: Exception) {
            null
        }

        streamUrl?.let {
            Log.i(TAG, "Resolver supplied a full stream for ${song.title}")
            song.copy(streamUrl = it, isPreview = false)
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val TAG = "StreamResolver"
    }
}
