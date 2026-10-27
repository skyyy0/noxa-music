package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Resolves a pushed update link into a direct APK download URL.
 *
 * The developer publishes either a direct `.apk` URL or a MediaFire page
 * link; MediaFire pages embed the real file host, which is extracted here
 * without any API key. Anything else is passed through unchanged and the
 * system DownloadManager does the rest — an unusable link then fails
 * honestly instead of being guessed at.
 *
 * **Why two User-Agents:** MediaFire answers the old Android UA with a
 * `server_error` repair page that contains no download link at all (this is
 * exactly the "Link update tidak bisa diakses" bug). A desktop Chrome UA
 * receives the real page, so that is tried first and the Android UA is the
 * retry.
 */
object UpdateRepository {

    /** The repo CI publishes release APKs to (used by the Push Update form). */
    private const val RELEASE_REPO = "skyyy0/noxa-app"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** One GitHub Release as shown in the Push Update form. */
    data class GitHubRelease(
        val tag: String,
        val note: String,
        val versionName: String,
        val apkUrl: String
    )

    /**
     * Reads the project's latest GitHub Release and returns its first APK
     * asset — the keyless way for the developer to fill the update form in
     * one tap (the CI attaches the built APK to every release). Returns null
     * when GitHub is unreachable or the release carries no APK.
     */
    suspend fun fetchLatestRelease(repo: String = RELEASE_REPO): GitHubRelease? =
        withContext(Dispatchers.IO) { fetchLatestReleaseBlocking(repo) }

    private fun fetchLatestReleaseBlocking(repo: String): GitHubRelease? {
        val body = try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NOXA-Update-Panel")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            null
        } ?: return null

        return try {
            val json = org.json.JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            val apkUrl = json.optJSONArray("assets")?.let { assets ->
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val url = asset.optString("browser_download_url")
                    if (url.endsWith(".apk", ignoreCase = true)) return@let url
                }
                null
            } ?: return null
            GitHubRelease(
                tag = tag,
                // First non-blank line of the release body as a starting note.
                note = json.optString("body").lineSequence()
                    .firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
                versionName = tag.removePrefix("v").trim(),
                apkUrl = apkUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    /** UAs in resolution order — desktop first, per the doc above. */
    private val USER_AGENTS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    )

    /** The direct download URL for [url], or null when it cannot be resolved. */
    fun resolveDirectApkUrl(url: String): String? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http")) return null
        if (!trimmed.contains("mediafire.com", ignoreCase = true)) {
            // Already a direct file link (or any non-MediaFire host).
            return trimmed
        }
        for (ua in USER_AGENTS) {
            extractDirectUrl(fetchHtml(trimmed, ua))?.let { return it }
        }
        return null
    }

    /** GETs [url] and returns its body, or null on any failure. */
    private fun fetchHtml(url: String, userAgent: String): String? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,*/*")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Pulls the direct file-host link out of a MediaFire page body.
     * Two shapes are recognised: the `downloadNN.mediafire.com/<hash>/<file>`
     * link embedded in the download button (with or without a trailing `/file`
     * segment or query string), and the `downloadURL` JSON field.
     */
    internal fun extractDirectUrl(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val hostLink = Regex("https://download\\d*\\.mediafire\\.com/[^\\s\"'<>]+")
            .find(body)?.value
        if (hostLink != null) {
            // A trailing "/file" (page path leaked into the match) never
            // belongs to the real download URL.
            return hostLink.removeSuffix("/file")
        }
        return Regex("\"downloadURL\":\"(https:[^\"]+)\"")
            .find(body)?.groupValues?.get(1)
    }
}
