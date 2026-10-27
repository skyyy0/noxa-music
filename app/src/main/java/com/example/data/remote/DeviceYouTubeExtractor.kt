package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

/** One on-device search hit, already reduced to what NOXA needs. */
data class DeviceSearchResult(
    val videoId: String,
    val title: String,
    val uploader: String,
    val durationMs: Long
)

/**
 * Full-length YouTube audio **extracted on the device** with the open-source
 * NewPipeExtractor library — no Piped/Invidious instance required.
 *
 * This is what turns "PREVIEW 30s" tracks into full songs reliably: the
 * matcher finds the right video (via the normal instance/Data-API search) and
 * this extractor returns the real googlevideo audio URL for it. Instances are
 * kept as a fallback, never a dependency.
 *
 * Everything runs on [Dispatchers.IO] and every failure returns `null` — an
 * extractor that breaks after a YouTube change must never crash playback, it
 * only degrades to the instance fallback chain.
 */
object DeviceYouTubeExtractor {

    private const val TAG = "DeviceYTExtractor"

    @Volatile
    private var initialized = false

    @Volatile
    private var disabled = false

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** NewPipe must be initialised exactly once, with our own HTTP backend. */
    private fun ensureInitialized(): Boolean {
        if (disabled) return false
        if (initialized) return true
        synchronized(this) {
            if (initialized) return true
            return try {
                NewPipe.init(OkHttpDownloader(), Localization("en"), ContentCountry("US"))
                initialized = true
                true
            } catch (t: Throwable) {
                Log.w(TAG, "NewPipe init failed: ${t.message}")
                disabled = true
                false
            }
        }
    }

    /**
     * Best full-length audio URL for [videoId], or `null` when extraction
     * fails (broken extractor, unavailable video, network problems).
     */
    suspend fun extractAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || !ensureInitialized()) return@withContext null
        try {
            val extractor = ServiceList.YouTube.getStreamExtractor(
                "https://www.youtube.com/watch?v=$videoId"
            )
            extractor.fetchPage()
            val stream = pickAudioStream(extractor.audioStreams) ?: return@withContext null
            if (stream.isUrl) stream.content else null
        } catch (t: Throwable) {
            Log.w(TAG, "Extraction failed for $videoId: ${t.message}")
            null
        }
    }

    /**
     * Title/uploader/duration for one video id — what turns a shared NOXA link
     * (`youtube:<id>`) into a song with a real name instead of a placeholder.
     */
    suspend fun extractVideoInfo(videoId: String): DeviceSearchResult? =
        withContext(Dispatchers.IO) {
            if (videoId.isBlank() || !ensureInitialized()) return@withContext null
            try {
                val extractor = ServiceList.YouTube.getStreamExtractor(
                    "https://www.youtube.com/watch?v=$videoId"
                )
                extractor.fetchPage()
                DeviceSearchResult(
                    videoId = videoId,
                    title = extractor.name.orEmpty(),
                    uploader = extractor.uploaderName.orEmpty(),
                    durationMs = extractor.length.coerceAtLeast(0L) * 1000L
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Info extraction failed for $videoId: ${t.message}")
                null
            }
        }

    /**
     * On-device YouTube search — the safety net when the instance search
     * endpoints (and an optional Data API key) are all unavailable.
     */
    suspend fun searchVideos(query: String): List<DeviceSearchResult> =
        withContext(Dispatchers.IO) {
            if (query.isBlank() || !ensureInitialized()) return@withContext emptyList()
            try {
                val queryHandler = ServiceList.YouTube.searchQHFactory
                    .fromQuery(query, emptyList(), "")
                val info = SearchInfo.getInfo(ServiceList.YouTube, queryHandler)
                info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .filter { it.duration > 0 }
                    .take(MAX_DEVICE_RESULTS)
                    .mapNotNull { item ->
                        val videoId = YouTubeSourceRepository.extractVideoId(item.url)
                            ?: return@mapNotNull null
                        DeviceSearchResult(
                            videoId = videoId,
                            title = item.name.orEmpty(),
                            uploader = item.uploaderName.orEmpty(),
                            durationMs = item.duration * 1000L
                        )
                    }
            } catch (t: Throwable) {
                Log.w(TAG, "Device search failed: ${t.message}")
                emptyList()
            }
        }

    /**
     * Picks the audio stream to play: itag 140 (M4A — decodes everywhere),
     * then 251 (Opus), then by format, then simply the highest bitrate.
     * Pure so the preference rule stays unit-testable.
     */
    internal fun pickAudioStream(streams: List<AudioStream>?): AudioStream? {
        val usable = streams.orEmpty().filter { it.isUrl }
        if (usable.isEmpty()) return null
        usable.firstOrNull { it.itag == 140 }?.let { return it }
        usable.firstOrNull { it.itag == 251 }?.let { return it }
        usable.firstOrNull { it.format == MediaFormat.M4A }?.let { return it }
        usable.firstOrNull {
            it.format == MediaFormat.OPUS || it.format == MediaFormat.WEBMA_OPUS
        }?.let { return it }
        return usable.maxByOrNull { it.bitrate }
    }

    private const val MAX_DEVICE_RESULTS = 15

    /** Minimal OkHttp-backed Downloader — NewPipe ships no Android client. */
    private class OkHttpDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder().url(request.url())
            request.headers().forEach { (name, values) ->
                values.forEach { value -> builder.addHeader(name, value) }
            }
            val payload = request.dataToSend()
            if (payload != null && request.httpMethod().equals("POST", ignoreCase = true)) {
                builder.post(payload.toRequestBody(null))
            } else {
                builder.get()
            }

            client.newCall(builder.build()).execute().use { response ->
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string(),
                    response.request.url.toString()
                )
            }
        }
    }
}
