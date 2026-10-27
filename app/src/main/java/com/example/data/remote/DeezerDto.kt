package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response models for the Deezer public REST API (https://api.deezer.com).
 *
 * Deezer answers read-only calls without an API key. It also reports failures as
 * an HTTP 200 body containing an `error` object, so [DeezerError] is parsed on
 * every response model instead of relying on the status code alone.
 */
@JsonClass(generateAdapter = true)
data class DeezerError(
    @Json(name = "type") val type: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "code") val code: Int? = null
)

@JsonClass(generateAdapter = true)
data class DeezerTrackListResponse(
    @Json(name = "data") val data: List<DeezerTrack>? = null,
    @Json(name = "error") val error: DeezerError? = null,
    @Json(name = "total") val total: Int? = null
) {
    val isFailure: Boolean get() = error != null
}

@JsonClass(generateAdapter = true)
data class DeezerArtistListResponse(
    @Json(name = "data") val data: List<DeezerArtist>? = null,
    @Json(name = "error") val error: DeezerError? = null
) {
    val isFailure: Boolean get() = error != null
}

@JsonClass(generateAdapter = true)
data class DeezerTrack(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "duration") val duration: Int? = null,
    /** Direct HTTPS MP3 URL of the 30 second, label-approved preview clip. */
    @Json(name = "preview") val preview: String? = null,
    @Json(name = "link") val link: String? = null,
    @Json(name = "rank") val rank: Long? = null,
    @Json(name = "readable") val readable: Boolean? = null,
    @Json(name = "artist") val artist: DeezerArtist? = null,
    @Json(name = "album") val album: DeezerAlbum? = null,
    /**
     * Every credited artist Deezer lists for the track (main + featured),
     * present on search and track-detail responses. Used only where the full
     * credit matters — the Home hero — so the everyday single-artist label
     * stays exactly as it always was.
     */
    @Json(name = "contributors") val contributors: List<DeezerArtist>? = null,
    /** Present on `GET /track/{id}` error bodies (a bare track object). */
    @Json(name = "error") val error: DeezerError? = null
)

@JsonClass(generateAdapter = true)
data class DeezerArtist(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "link") val link: String? = null,
    @Json(name = "picture_small") val pictureSmall: String? = null,
    @Json(name = "picture_medium") val pictureMedium: String? = null,
    @Json(name = "picture_big") val pictureBig: String? = null,
    @Json(name = "picture_xl") val pictureXl: String? = null,
    /** Present on `search/artist` and `artist/{id}` items. */
    @Json(name = "nb_fan") val nbFan: Long? = null,
    @Json(name = "nb_album") val nbAlbum: Int? = null
)

@JsonClass(generateAdapter = true)
data class DeezerAlbum(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "cover_small") val coverSmall: String? = null,
    @Json(name = "cover_medium") val coverMedium: String? = null,
    @Json(name = "cover_big") val coverBig: String? = null,
    @Json(name = "cover_xl") val coverXl: String? = null,
    /** Discography fields: present on album endpoints, absent on track.album. */
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "nb_tracks") val nbTracks: Int? = null,
    @Json(name = "record_type") val recordType: String? = null,
    @Json(name = "artist") val artist: DeezerArtist? = null
)

/**
 * `GET /artist/{id}` — the artist page header. Deezer returns a bare artist
 * object (or an `error` inside a 200 body), so this is a flat response model
 * rather than a wrapper.
 */
@JsonClass(generateAdapter = true)
data class DeezerArtistDetailResponse(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "link") val link: String? = null,
    @Json(name = "picture_medium") val pictureMedium: String? = null,
    @Json(name = "picture_big") val pictureBig: String? = null,
    @Json(name = "picture_xl") val pictureXl: String? = null,
    @Json(name = "nb_fan") val nbFan: Long? = null,
    @Json(name = "nb_album") val nbAlbum: Int? = null,
    @Json(name = "error") val error: DeezerError? = null
) {
    val isFailure: Boolean get() = error != null
}

/** `GET /artist/{id}/albums` — the discography shelf. */
@JsonClass(generateAdapter = true)
data class DeezerAlbumListResponse(
    @Json(name = "data") val data: List<DeezerAlbum>? = null,
    @Json(name = "error") val error: DeezerError? = null
) {
    val isFailure: Boolean get() = error != null
}

/** `GET /album/{id}` — the album header (tracks come from album/{id}/tracks). */
@JsonClass(generateAdapter = true)
data class DeezerAlbumDetailResponse(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "cover_medium") val coverMedium: String? = null,
    @Json(name = "cover_big") val coverBig: String? = null,
    @Json(name = "cover_xl") val coverXl: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "nb_tracks") val nbTracks: Int? = null,
    @Json(name = "artist") val artist: DeezerArtist? = null,
    @Json(name = "error") val error: DeezerError? = null
) {
    val isFailure: Boolean get() = error != null
}
