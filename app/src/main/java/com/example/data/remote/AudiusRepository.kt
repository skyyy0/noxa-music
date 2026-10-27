package com.example.data.remote

import android.util.Log
import com.example.data.model.MusicSource
import com.example.data.model.Song
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The Audius-backed half of NOXA's catalogue.
 *
 * Audius is the only source here that serves **full-length** audio, because the
 * tracks are uploaded by the artists themselves. Its catalogue is independent
 * and community driven, though, so it is weak on mainstream Indonesian music —
 * [DeezerRepository] covers that gap.
 *
 * Anonymous (keyless) reads are supported. If an Audius developer key is supplied
 * through the `AUDIUS_API_KEY` secret it is sent as `x-api-key`.
 */
class AudiusRepository {

    private val developerKey: String? =
        Secrets.get("AUDIUS_API_KEY").trim().takeIf { it.isNotEmpty() && !it.startsWith("REPLACE_WITH") }

    private val apiService: AudiusApiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AudiusApiService::class.java)
    }

    /**
     * Direct, playable audio URL for a track.
     *
     * The `/stream` endpoint resolves (via redirect) to a freshly signed audio
     * file on Audius' content nodes, so it stays valid even though the signed
     * URLs embedded in metadata expire.
     */
    fun streamUrlFor(trackId: String): String =
        BASE_URL + "v1/tracks/" + trackId + "/stream?app_name=" + APP_NAME

    /** Real search results. Returns an empty list when the API fails. */
    suspend fun search(query: String, limit: Int = SEARCH_LIMIT): List<Song> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val response = safeCall {
                apiService.searchTracks(
                    query = trimmed,
                    limit = limit,
                    appName = APP_NAME,
                    apiKey = developerKey
                )
            }
            response?.data.orEmpty().mapNotNull { it.toSong() }.distinctBy { it.id }
        }

    /**
     * A single track by Audius id — what resolves a shared NOXA link
     * (`audius:<id>`) back into a playable song.
     */
    suspend fun trackById(trackId: String): Song? = withContext(Dispatchers.IO) {
        if (trackId.isBlank()) return@withContext null
        val body = try {
            apiService.getTrack(trackId, APP_NAME, developerKey).body()
        } catch (e: Exception) {
            Log.w("AudiusRepo", "Track lookup failed: ${e.message}")
            null
        }
        body?.data?.toSong()
    }

    /** Real global trending chart. */
    suspend fun trending(limit: Int = SEARCH_LIMIT): List<Song> = withContext(Dispatchers.IO) {
        val response = safeCall {
            apiService.trendingTracks(limit = limit, appName = APP_NAME, apiKey = developerKey)
        }
        // Ids are LazyRow keys on Home — never let a duplicate slip through.
        response?.data.orEmpty().mapNotNull { it.toSong() }.distinctBy { it.id }
    }

    /** Real trending chart for a single genre. */
    suspend fun trendingByGenre(genre: String, limit: Int = SEARCH_LIMIT): List<Song> =
        withContext(Dispatchers.IO) {
            val response = safeCall {
            apiService.trendingTracksByGenre(
                genre = genre,
                limit = limit,
                appName = APP_NAME,
                apiKey = developerKey
            )
        }
        response?.data.orEmpty().mapNotNull { it.toSong() }.distinctBy { it.id }
    }

    private suspend fun safeCall(block: suspend () -> Response<AudiusTrackListResponse>): AudiusTrackListResponse? {
        return try {
            val response = block()
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.w("AudiusRepo", "Request failed with HTTP ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.w("AudiusRepo", "Request failed: ${e.message}")
            null
        }
    }

    /** Maps a raw Audius track onto the app model. Internal so it can be unit-tested. */
    internal fun AudiusTrack.toSong(): Song? {
        val trackId = id?.takeIf { it.isNotBlank() } ?: return null
        val artwork = artwork?.x480
            ?: artwork?.x1000
            ?: artwork?.x150
            ?: artwork?.mirrors?.firstOrNull()
            ?: ""
        val handle = user?.handle
        return Song(
            id = "$ID_PREFIX$trackId",
            title = title?.trim().orEmpty().ifEmpty { "Untitled" },
            artist = user?.name?.trim().orEmpty().ifEmpty { "Unknown Artist" },
            album = genre?.trim().orEmpty().ifEmpty { "Single" },
            artworkUrl = artwork,
            // Real, full-length audio URL resolved by Audius.
            streamUrl = streamUrlFor(trackId),
            durationMs = (duration ?: 0).toLong() * 1000L,
            channelId = handle,
            viewCount = playCount?.toString(),
            source = MusicSource.AUDIUS,
            remoteId = trackId,
            isPreview = false,
            shareUrl = if (!handle.isNullOrBlank() && !permalink.isNullOrBlank()) {
                "https://audius.co/$handle/${permalink.trimStart('/')}"
            } else {
                "https://audius.co/track/$trackId"
            }
        )
    }

    companion object {
        const val ID_PREFIX = "audius:"
        private const val BASE_URL = "https://api.audius.co/"
        private const val APP_NAME = "NOXA"
        private const val SEARCH_LIMIT = 25
    }
}
