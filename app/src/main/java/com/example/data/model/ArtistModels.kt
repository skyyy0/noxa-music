package com.example.data.model

/**
 * An artist page's identity, straight from the catalogue (Deezer).
 *
 * Every artist that has a page here is a listed catalogue artist and shows a
 * verified check; [isBigArtist] decides whether that check renders red (a
 * major artist) or blue (a regular verified artist).
 */
data class ArtistProfile(
    val id: Long,
    val name: String,
    val pictureUrl: String,
    /** Deezer's `nb_fan` — the honest "listener" number shown on the page. */
    val fanCount: Long = 0L,
    val albumCount: Int = 0,
    val link: String = ""
) {
    /** Centang merah untuk artis besar (mis. Justin Bieber), biru untuk lainnya. */
    val isBigArtist: Boolean get() = fanCount >= BIG_ARTIST_FAN_THRESHOLD

    companion object {
        /** At or above this many listeners an artist gets the red check. */
        const val BIG_ARTIST_FAN_THRESHOLD = 1_000_000L
    }
}

/** One release on the artist page's discography shelf. */
data class AlbumInfo(
    val id: Long,
    val title: String,
    val coverUrl: String,
    val releaseYear: String = "",
    val trackCount: Int = 0,
    val artistName: String = "",
    val artistId: Long = 0L,
    /** Deezer `record_type`: album / single / compile — drives the filter chips. */
    val type: String = "",
    /** Full ISO release date when Deezer provides it (radar rilis filter). */
    val releaseDate: String = ""
)

/** Album header plus its full tracklist, in track order. */
data class AlbumDetail(
    val album: AlbumInfo,
    val tracks: List<Song>
)
