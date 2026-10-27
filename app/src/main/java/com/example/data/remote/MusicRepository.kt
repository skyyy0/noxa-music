package com.example.data.remote

import android.util.Log
import com.example.data.model.AlbumDetail
import com.example.data.model.AlbumInfo
import com.example.data.model.ArtistProfile
import com.example.data.model.PinnedTracks
import com.example.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Runs one home-shelf fetch so a single misbehaving provider degrades to an
 * empty shelf instead of failing the whole feed.
 *
 * The feed used to be built fail-fast: one HTTP hiccup, mapping bug or OOM
 * inside any shelf made [MusicRepository.getHomeFeed] throw, the exception
 * escaped the view model's coroutine, and the app force-closed right while
 * Home still showed "Memuat musik...". Now every shelf is independent — a
 * broken provider only loses its own shelf. Cancellation still propagates so
 * structured concurrency keeps working.
 */
internal suspend fun <T> safeShelf(block: suspend () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("MusicRepository", "Shelf failed: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

/** One horizontally scrolling shelf on the home screen. */
data class HomeMusicSection(
    val title: String,
    val subtitle: String,
    val songs: List<Song>
)

/** Everything the home screen needs, in one immutable snapshot. */
data class HomeFeed(
    val hero: Song? = null,
    val sections: List<HomeMusicSection> = emptyList(),
    /** Sky's pinned pick(s) — rendered as their own shelf at the top of Home. */
    val pinned: List<Song> = emptyList(),
    /** Profiles behind the "Artis Populer" avatar shelf. */
    val artists: List<ArtistProfile> = emptyList()
)

private data class ArtistShelf(
    val artistName: String,
    val title: String,
    val subtitle: String
)

/**
 * The single source of music for NOXA.
 *
 * Two keyless providers are combined, and the UI never needs to know which one a
 * track came from:
 *
 *  * **[DeezerRepository]** — the broad catalogue, now including mainstream
 *    Indonesian music. Audio is a label-approved 30 second preview clip, so those
 *    tracks are flagged [Song.isPreview].
 *  * **[AudiusRepository]** — independent artists, with genuinely full-length
 *    audio.
 *
 * Search merges both; the home feed is built from a handful of curated shelves
 * plus the global chart. No API key, no signup, nothing hardcoded.
 */
class MusicRepository(
    private val deezer: DeezerRepository = DeezerRepository(),
    private val audius: AudiusRepository = AudiusRepository()
) {

    /**
     * Search both providers.
     *
     * Deezer results come first because its catalogue matches what people search
     * for most, then Audius is appended so full-length options are still there.
     */
    suspend fun search(query: String): List<Song> = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@coroutineScope emptyList()

        val deezerDeferred = async { deezer.search(trimmed) }
        val audiusDeferred = async { audius.search(trimmed, limit = 10) }

        val deezerResults = runCatching { deezerDeferred.await() }.getOrDefault(emptyList())
        val audiusResults = runCatching { audiusDeferred.await() }.getOrDefault(emptyList())

        (deezerResults + audiusResults).distinctBy { it.id }
    }

    // ----------------------------------------------------- shared NOXA links

    /**
     * Resolves the song behind a shared NOXA link id (`deezer:<id>`,
     * `audius:<id>`) into something playable. YouTube ids are handled by
     * [YouTubeSourceRepository.songByVideoId] in the view model.
     */
    suspend fun songFromDeepLink(songId: String): Song? = when {
        songId.startsWith("deezer:") -> {
            val remote = songId.removePrefix("deezer:").toLongOrNull()
            if (remote == null) null else deezer.trackById(remote)
        }

        songId.startsWith("audius:") -> audius.trackById(songId.removePrefix("audius:"))
        else -> null
    }

    // ----------------------------------------------------- artist / album pages

    /** Deezer artist id for a name, cached after the first hit. */
    suspend fun findArtistId(artistName: String): Long? = deezer.artistIdFor(artistName)

    /** Artist page header (picture, listeners, release count). */
    suspend fun artistProfile(artistId: Long): ArtistProfile? = deezer.artistProfile(artistId)

    /** Top tracks of an artist by id — the artist page's song list. */
    suspend fun artistTopTracks(artistId: Long, limit: Int = 25): List<Song> =
        deezer.topTracksForArtist(artistId, limit)

    /** Musically adjacent artists — the "genre mirip" basis for Buat kamu. */
    suspend fun relatedArtists(artistId: Long, limit: Int = 8): List<ArtistProfile> =
        deezer.relatedArtists(artistId, limit)

    // ----------------------------------------------------------------- radio

    /**
     * **Radio dari artis** — an endless-feeling queue built around one seed
     * artist: their own top tracks plus the top tracks of the artists Deezer
     * reports as related, shuffled, de-duplicated and trimmed to [limit].
     *
     * [seedSong], when given, is placed first so "Putar radio dari lagu ini"
     * starts with the song the user actually tapped. Every provider failure
     * degrades to whatever was already collected — a radio that comes back
     * short is still a radio; it never throws to the caller.
     */
    suspend fun radioFromArtist(
        artistId: Long,
        seedSong: Song? = null,
        limit: Int = RADIO_LIMIT
    ): List<Song> = coroutineScope {
        val ownTop = async {
            runCatching { deezer.topTracksForArtist(artistId, limit) }.getOrDefault(emptyList())
        }
        val related = runCatching { deezer.relatedArtists(artistId, RADIO_ARTISTS) }
            .getOrDefault(emptyList())

        val relatedTop = related.map { profile ->
            async {
                runCatching { deezer.topTracksForArtist(profile.id, limit) }
                    .getOrDefault(emptyList())
            }
        }.awaitAll()

        val pool = (ownTop.await() + relatedTop.flatten()).distinctBy { it.id }
        val shuffled = pool.shuffled()
        // The seed leads; the rest is a stable-but-mixed follow-up so two
        // consecutive radio starts are not the same queue in the same order.
        val ordered = if (seedSong != null) {
            listOfNotNull(pool.firstOrNull { it.id == seedSong.id } ?: seedSong) +
                (shuffled.filter { it.id != seedSong.id })
        } else {
            shuffled
        }
        ordered.distinctBy { it.id }.filter { it.streamUrl.isNotBlank() }.take(limit)
    }

    /**
     * **Radio dari lagu** — the same engine, seeded by the song's artist. When
     * the artist cannot be resolved (a YouTube-only track, say) the track's
     * own catalogue neighbours are not available, so this returns an empty
     * list and the caller says so honestly instead of pretending.
     */
    suspend fun radioFromSong(seed: Song, limit: Int = RADIO_LIMIT): List<Song> {
        val artistId = seed.artistId.toLongOrNull() ?: findArtistId(seed.artist)
            ?: return emptyList()
        return radioFromArtist(artistId, seedSong = seed, limit = limit)
    }

    /** Discography of an artist. */
    suspend fun artistAlbums(artistId: Long): List<AlbumInfo> = deezer.artistAlbums(artistId)

    /** Album header plus full tracklist, in track order. */
    suspend fun albumDetail(albumId: Long): AlbumDetail? = deezer.albumDetail(albumId)

    /** Builds the whole home feed from live API data. */
    suspend fun getHomeFeed(): HomeFeed = coroutineScope {
        // Bounded concurrency keeps Deezer's per-IP query quota happy while the
        // shelves still load in parallel instead of one after another.
        val gate = Semaphore(permits = 3)

        // Every shelf is wrapped in safeShelf: one failing provider loses its
        // own shelf but can never take the whole feed (and the app) down.
        val heroDeferred = async { gate.withPermit { safeShelf { loadHero() } } }

        val shelfDeferred = ARTIST_SHELVES.map { shelf ->
            async {
                gate.withPermit {
                    safeShelf {
                        HomeMusicSection(
                            title = shelf.title,
                            subtitle = shelf.subtitle,
                            songs = deezer.artistTopTracks(shelf.artistName, limit = SHELF_SIZE)
                        )
                    }
                }
            }
        }

        val chartDeferred = async {
            gate.withPermit {
                safeShelf {
                    HomeMusicSection(
                        title = "Global Top 50",
                        subtitle = "Tangga lagu Deezer hari ini",
                        songs = deezer.chart(limit = SHELF_SIZE)
                    )
                }
            }
        }

        val audiusDeferred = async {
            gate.withPermit {
                safeShelf {
                    HomeMusicSection(
                        title = "Komunitas Audius",
                        subtitle = "Lagu penuh dari artis independen",
                        songs = audius.trending(limit = SHELF_SIZE)
                    )
                }
            }
        }

        // Sky's pinned pick + artist avatars, fetched in the same pass so the
        // Home screen still renders from one snapshot.
        val pinnedDeferred = async { gate.withPermit { safeShelf { loadPinned() } } }
        val artistsDeferred = async { gate.withPermit { safeShelf { loadPopularArtists() } } }

        // Belt-and-braces de-duplication: every shelf is keyed by song id in
        // a LazyRow, so a single duplicated id anywhere would crash Home with
        // "Key \"deezer:…\" was already used".
        val sections = (shelfDeferred + chartDeferred + audiusDeferred)
            .awaitAll()
            .filterNotNull()
            .filter { it.songs.isNotEmpty() }
            .distinctBy { it.title }
            .map { section -> section.copy(songs = section.songs.distinctBy { it.id }) }

        HomeFeed(
            hero = heroDeferred.await(),
            sections = sections,
            pinned = pinnedDeferred.await().orEmpty(),
            artists = artistsDeferred.await().orEmpty()
        )
    }

    /**
     * Sky's pinned track(s), fetched live so the shelf shows real artwork.
     *
     * Plain full-text search is unreliable for short titles ("Dan" by Sheila
     * On 7 drowns in results where "dan" is just the Indonesian word for
     * "and"), so the artist's top tracks are consulted first — every pinned
     * song is a signature hit of its artist — with search queries as the
     * fallback. All five pins resolve in parallel.
     */
    private suspend fun loadPinned(): List<Song> = coroutineScope {
        PinnedTracks.ALL.map { pin ->
            async {
                // Match THIS pin only. The old check matched *any* pin, so both
                // Dewa 19 pins resolved to the same top track and the shelf
                // rendered one song twice — a duplicate LazyRow key that
                // force-closed the app while Home loaded.
                val byTop = deezer.artistTopTracks(pin.artist, limit = 40)
                    .firstOrNull { PinnedTracks.matches(pin, it.title, it.artist) }
                byTop ?: deezer.search("${pin.artist} ${pin.title}", limit = 25)
                    .firstOrNull { PinnedTracks.matches(pin, it.title, it.artist) }
                    ?: deezer.search(pin.title, limit = 25)
                        .firstOrNull { PinnedTracks.matches(pin, it.title, it.artist) }
            }
        }.awaitAll().filterNotNull().distinctBy { it.id }
    }

    /** Artist profiles for the Home avatar shelf (the shelf artists themselves). */
    private suspend fun loadPopularArtists(): List<ArtistProfile> =
        ARTIST_SHELVES.mapNotNull { shelf ->
            runCatching {
                val id = deezer.artistIdFor(shelf.artistName) ?: return@runCatching null
                deezer.artistProfile(id)
            }.getOrNull()
        }

    /**
     * The Home hero: **"Hate Me" — Ellie Goulding with Juice WRLD**.
     *
     * The track is fetched from Deezer like everything else, so the banner is
     * real, playable audio with its official cover — never a hardcoded image.
     * Matching is deliberately strict:
     *
     *  1. the title must be exactly "Hate Me" — the catalogue is full of covers
     *     and edits that merely *contain* those words, and a hero that resolved
     *     to one of those would put the wrong artwork on the front page;
     *  2. then the credited artist must name Juice WRLD, or Deezer's own main
     *     credit for the track.
     *
     * The search payload only carries the main artist, so the winner is
     * re-fetched by id to pick up every featured credit — that is what puts
     * "Ellie Goulding, Juice WRLD" on the banner instead of half the credit.
     *
     * When nothing matches, the hero is simply absent — an honest empty slot
     * beats showing an unrelated song as the pick of the day.
     */
    private suspend fun loadHero(): Song? {
        val candidates = deezer.search(HERO_QUERY, limit = 12)
        val exact = candidates.filter { it.title.trim().equals(HERO_TITLE, ignoreCase = true) }
        val match = exact.firstOrNull { it.artist.contains(HERO_ARTIST, ignoreCase = true) }
            ?: exact.firstOrNull { it.artist.equals(HERO_MAIN_ARTIST, ignoreCase = true) }
            ?: exact.firstOrNull { it.artist.contains(HERO_MAIN_ARTIST, ignoreCase = true) }
            ?: return null

        val remoteId = match.remoteId.toLongOrNull() ?: return match
        return runCatching { deezer.trackById(remoteId, fullCredit = true) }
            .getOrNull() ?: match
    }

    companion object {
        const val HERO_TITLE = "Hate Me"
        /** Featured credit the hero must name — the reason the pick exists. */
        const val HERO_ARTIST = "Juice WRLD"
        /** Deezer's main credit on that track (the label that released it). */
        const val HERO_MAIN_ARTIST = "Ellie Goulding"
        private const val HERO_QUERY = "Hate Me Juice WRLD"
        private const val SHELF_SIZE = 12

        /** Radio: how long a generated queue gets, and how many related
         *  artists feed it. */
        private const val RADIO_LIMIT = 30
        private const val RADIO_ARTISTS = 6

        private val ARTIST_SHELVES = listOf(
            ArtistShelf("Dewa 19", "Dewa 19", "Balada & rock legendaris Indonesia"),
            ArtistShelf("Sheila On 7", "Sheila On 7", "Pop Indonesia paling dicari"),
            ArtistShelf("Justin Bieber", "Justin Bieber", "Hits pop global"),
            ArtistShelf("Iwan Fals", "Iwan Fals", "Legenda musik Indonesia"),
            ArtistShelf("Tulus", "Tulus", "Pop & jazz Indonesia"),
            ArtistShelf("NOAH", "NOAH & Peterpan", "Dari Peterpan sampai NOAH")
        )
    }
}
