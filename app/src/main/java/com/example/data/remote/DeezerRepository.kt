package com.example.data.remote

import android.util.Log
import com.example.data.model.AlbumDetail
import com.example.data.model.AlbumInfo
import com.example.data.model.ArtistProfile
import com.example.data.model.MusicSource
import com.example.data.model.Song
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The Deezer-backed half of NOXA's catalogue.
 *
 * Deezer is the only large, keyless source that actually carries mainstream
 * Indonesian music (Dewa 19, Sheila On 7, Iwan Fals, Tulus, …) as well as global
 * pop. Its API returns a directly playable HTTPS MP3 for every track, but Deezer
 * legally caps those clips at 30 seconds, so every [Song] built here is marked
 * [Song.isPreview].
 *
 * Plays are therefore real audio, just short. Full-length streaming is only
 * available from providers that expose it, which is why NOXA keeps Audius as a
 * second source.
 */
class DeezerRepository {

    private val api: DeezerApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DeezerApiService::class.java)
    }

    /** Artist-id lookups are cached: a shelf needs one, not one per scroll. */
    private val artistIdCache = HashMap<String, Long?>()
    private val artistIdMutex = Mutex()

    suspend fun search(query: String, limit: Int = SEARCH_LIMIT): List<Song> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val response = safeCall { api.searchTracks(trimmed, limit) }
            response?.data.orEmpty().mapNotNull { it.toSong() }.distinctBy { it.id }
        }

    suspend fun chart(limit: Int = SEARCH_LIMIT): List<Song> = withContext(Dispatchers.IO) {
        val response = safeCall { api.chartTracks(limit) }
        response?.data.orEmpty().mapNotNull { it.toSong() }.distinctBy { it.id }
    }

    /**
     * Top tracks of an artist, resolved by name.
     *
     * Deezer's `search?q=artist:"…"` field syntax does not work reliably, so the
     * artist is resolved once through `search/artist` and then reused from cache.
     */
    suspend fun artistTopTracks(artistName: String, limit: Int = SEARCH_LIMIT): List<Song> =
        withContext(Dispatchers.IO) {
            val artistId = resolveArtistId(artistName) ?: return@withContext emptyList()
            topTracksForArtist(artistId, limit)
        }

    /** Top tracks of an artist, when the id is already known (artist page). */
    suspend fun topTracksForArtist(artistId: Long, limit: Int = SEARCH_LIMIT): List<Song> =
        withContext(Dispatchers.IO) {
            val response = safeCall { api.artistTopTracks(artistId, limit) }
            response?.data.orEmpty().mapNotNull { it.toSong() }.distinctBy { it.id }
        }

    /** Public artist-id lookup used to open an artist page from any track. */
    suspend fun artistIdFor(artistName: String): Long? = resolveArtistId(artistName)

    /**
     * Artists musically adjacent to this one (Deezer's "related artists").
     * This is the honest "genre mirip" signal: the recommendation shelf uses
     * it to go from the user's most-played artists to their nearest neighbours
     * instead of guessing genres from titles.
     */
    suspend fun relatedArtists(artistId: Long, limit: Int = RELATED_LIMIT): List<ArtistProfile> =
        withContext(Dispatchers.IO) {
            val body = safeResponse(
                block = { api.relatedArtists(artistId, limit) },
                isFailure = { it.isFailure }
            ) ?: return@withContext emptyList()
            body.data.orEmpty().mapNotNull { artist ->
                val id = artist.id?.takeIf { it > 0 } ?: return@mapNotNull null
                ArtistProfile(
                    id = id,
                    name = artist.name?.trim().orEmpty().ifEmpty { "Unknown Artist" },
                    pictureUrl = artist.pictureXl ?: artist.pictureBig ?: artist.pictureMedium ?: "",
                    fanCount = artist.nbFan ?: 0L,
                    albumCount = artist.nbAlbum ?: 0,
                    link = artist.link ?: ""
                )
            }.distinctBy { it.id }
        }

    /**
     * A single track by Deezer id — what resolves a shared NOXA link
     * (`deezer:<id>`) back into a playable song.
     *
     * [fullCredit] widens the artist line to every name Deezer credits on the
     * track detail (the search payload only carries the main artist). The Home
     * hero uses it so a featured artist actually appears on the banner; every
     * other caller keeps the plain main-artist label.
     */
    suspend fun trackById(trackId: Long, fullCredit: Boolean = false): Song? =
        withContext(Dispatchers.IO) {
            val body = safeResponse(
                block = { api.trackDetail(trackId) },
                isFailure = { it.error != null || it.id == null }
            ) ?: return@withContext null
            body.toSong(fullCredit = fullCredit)
        }

    /**
     * Artist page header: picture, listener count and release count.
     * The listener count (`nb_fan`) is also what decides the red vs blue check.
     */
    suspend fun artistProfile(artistId: Long): ArtistProfile? = withContext(Dispatchers.IO) {
        val body = safeResponse(
            block = { api.artistDetail(artistId) },
            isFailure = { it.isFailure }
        ) ?: return@withContext null
        val id = body.id?.takeIf { it > 0 } ?: return@withContext null
        ArtistProfile(
            id = id,
            name = body.name?.trim().orEmpty().ifEmpty { "Unknown Artist" },
            pictureUrl = body.pictureXl ?: body.pictureBig ?: body.pictureMedium ?: "",
            fanCount = body.nbFan ?: 0L,
            albumCount = body.nbAlbum ?: 0,
            link = body.link ?: ""
        )
    }

    /** The artist's discography, newest first when Deezer provides dates. */
    suspend fun artistAlbums(artistId: Long): List<AlbumInfo> = withContext(Dispatchers.IO) {
        val body = safeResponse(
            block = { api.artistAlbums(artistId, ALBUM_LIMIT) },
            isFailure = { it.isFailure }
        ) ?: return@withContext emptyList()
        body.data.orEmpty()
            .mapNotNull { it.toAlbumInfo(fallbackArtistId = artistId) }
            .distinctBy { it.id }
    }

    /** Album header plus its full tracklist (tracks come back in track order). */
    suspend fun albumDetail(albumId: Long): AlbumDetail? = withContext(Dispatchers.IO) {
        val header = safeResponse(
            block = { api.albumDetail(albumId) },
            isFailure = { it.isFailure }
        ) ?: return@withContext null
        val id = header.id?.takeIf { it > 0 } ?: return@withContext null
        val albumTitle = header.title?.trim().orEmpty().ifEmpty { "Album" }
        val cover = header.coverXl ?: header.coverBig ?: header.coverMedium ?: ""
        val trackResponse = safeCall { api.albumTracks(albumId, ALBUM_TRACK_LIMIT) }
        val tracks = trackResponse?.data.orEmpty().mapNotNull {
            it.toSong(defaultAlbum = albumTitle, defaultCover = cover)
        }
        AlbumDetail(
            album = AlbumInfo(
                id = id,
                title = albumTitle,
                coverUrl = cover,
                releaseYear = header.releaseDate?.take(4).orEmpty(),
                trackCount = header.nbTracks ?: tracks.size,
                artistName = header.artist?.name?.trim().orEmpty().ifEmpty { "Unknown Artist" },
                artistId = header.artist?.id ?: 0L
            ),
            tracks = tracks
        )
    }

    private suspend fun resolveArtistId(artistName: String): Long? {
        artistIdMutex.withLock { artistIdCache[artistName] }?.let { return it }
        val found = safeArtistCall { api.searchArtists(artistName, 1) }
            ?.data
            ?.firstOrNull()
            ?.id
        artistIdMutex.withLock { artistIdCache[artistName] = found }
        return found
    }

    /**
     * Generic Deezer call guard: HTTP failures and Deezer's in-body errors both
     * come back as `null`, so callers never branch on error shapes themselves.
     */
    private suspend fun <T> safeResponse(
        block: suspend () -> Response<T>,
        isFailure: (T) -> Boolean
    ): T? = try {
        val response = block()
        val body = response.body()
        when {
            !response.isSuccessful -> {
                Log.w(TAG, "HTTP ${response.code()}")
                null
            }
            body == null || isFailure(body) -> {
                Log.w(TAG, "API error on ${response.raw().request.url.encodedPath}")
                null
            }
            else -> body
        }
    } catch (e: Exception) {
        Log.w(TAG, "Request failed: ${e.message}")
        null
    }

    private suspend fun safeCall(
        block: suspend () -> Response<DeezerTrackListResponse>
    ): DeezerTrackListResponse? = try {
        val response = block()
        val body = response.body()
        when {
            !response.isSuccessful -> {
                Log.w(TAG, "HTTP ${response.code()}")
                null
            }
            // Deezer reports failures inside a 200 body.
            body == null || body.isFailure -> {
                Log.w(TAG, "API error: ${body?.error?.message ?: "empty body"}")
                null
            }
            else -> body
        }
    } catch (e: Exception) {
        Log.w(TAG, "Request failed: ${e.message}")
        null
    }

    private suspend fun safeArtistCall(
        block: suspend () -> Response<DeezerArtistListResponse>
    ): DeezerArtistListResponse? = try {
        val response = block()
        val body = response.body()
        if (!response.isSuccessful || body == null || body.isFailure) null else body
    } catch (e: Exception) {
        Log.w(TAG, "Artist lookup failed: ${e.message}")
        null
    }

    /**
     * Maps a Deezer track onto the app model.
     *
     * A track is only kept when Deezer actually handed back a playable preview
     * URL; otherwise it would be a silent, unplayable entry in the list.
     * [defaultAlbum] / [defaultCover] fill in what `album/{id}/tracks` leaves
     * out (its track objects carry no `album` block). [fullCredit] widens the
     * artist line to every name Deezer credits (main + featured) for the Home
     * hero only. Internal so the mapping can be unit-tested.
     */
    internal fun DeezerTrack.toSong(
        defaultAlbum: String = "",
        defaultCover: String = "",
        fullCredit: Boolean = false
    ): Song? {
        val remoteId = id?.takeIf { it > 0 } ?: return null
        val previewUrl = preview?.takeIf { it.startsWith("https://") } ?: return null

        val artwork = album?.coverXl
            ?: album?.coverBig
            ?: album?.coverMedium
            ?: defaultCover.takeIf { it.isNotBlank() }
            ?: artist?.pictureXl
            ?: artist?.pictureBig
            ?: ""

        val albumTitle = album?.title?.trim().orEmpty().ifEmpty {
            defaultAlbum.trim().ifEmpty { "Single" }
        }

        val mainArtist = artist?.name?.trim().orEmpty().ifEmpty { "Unknown Artist" }
        return Song(
            id = "$ID_PREFIX$remoteId",
            title = title?.trim().orEmpty().ifEmpty { "Untitled" },
            // Display credit only: [artistId] below still points at the main
            // artist, so artist pages, radio and lyrics keep their old behaviour.
            artist = if (fullCredit) fullArtistCredit(mainArtist) else mainArtist,
            album = albumTitle,
            artworkUrl = artwork,
            streamUrl = previewUrl,
            durationMs = (duration ?: 0).toLong() * 1000L,
            channelId = artist?.id?.toString(),
            viewCount = rank?.toString(),
            source = MusicSource.DEEZER,
            remoteId = remoteId.toString(),
            isPreview = true,
            shareUrl = link ?: SHARE_URL_PREFIX + remoteId,
            artistId = artist?.id?.toString().orEmpty()
        )
    }

    /**
     * Deezer's own credit line: main artist plus every featured name, in the
     * order Deezer lists them (e.g. "Ellie Goulding, Juice WRLD"). Never a
     * guess — if the response carries no contributors, the main artist stands.
     */
    private fun DeezerTrack.fullArtistCredit(mainArtist: String): String {
        val names = contributors.orEmpty()
            .mapNotNull { it.name?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return if (names.isEmpty()) mainArtist else names.joinToString(", ")
    }

    /** Maps a discography entry; entries without an id are dropped. */
    private fun DeezerAlbum.toAlbumInfo(fallbackArtistId: Long): AlbumInfo? {
        val albumId = id?.takeIf { it > 0 } ?: return null
        return AlbumInfo(
            id = albumId,
            title = title?.trim().orEmpty().ifEmpty { "Untitled" },
            coverUrl = coverXl ?: coverBig ?: coverMedium ?: coverSmall ?: "",
            releaseYear = releaseDate?.take(4).orEmpty(),
            trackCount = nbTracks ?: 0,
            artistName = artist?.name?.trim().orEmpty(),
            artistId = artist?.id ?: fallbackArtistId,
            type = recordType?.trim().orEmpty(),
            releaseDate = releaseDate.orEmpty()
        )
    }

    companion object {
        const val ID_PREFIX = "deezer:"
        const val SHARE_URL_PREFIX = "https://www.deezer.com/track/"
        private const val TAG = "DeezerRepo"
        private const val BASE_URL = "https://api.deezer.com/"
        private const val SEARCH_LIMIT = 25
        private const val ALBUM_LIMIT = 50
        private const val ALBUM_TRACK_LIMIT = 100
        private const val RELATED_LIMIT = 12
    }
}
