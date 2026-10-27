package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response/entity models for the Audius public API (https://api.audius.co/v1).
 *
 * Every field is nullable with a default so a partial/rate-limited response can
 * never crash the app.
 */
@JsonClass(generateAdapter = true)
data class AudiusTrackSingleResponse(
    @Json(name = "data") val data: AudiusTrack? = null
)

@JsonClass(generateAdapter = true)
data class AudiusTrackListResponse(
    @Json(name = "data") val data: List<AudiusTrack>? = null
)

@JsonClass(generateAdapter = true)
data class AudiusTrack(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "duration") val duration: Int? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "mood") val mood: String? = null,
    @Json(name = "user") val user: AudiusUser? = null,
    @Json(name = "artwork") val artwork: AudiusArtwork? = null,
    @Json(name = "stream") val stream: AudiusStream? = null,
    @Json(name = "play_count") val playCount: Long? = null,
    @Json(name = "favorite_count") val favoriteCount: Long? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "permalink") val permalink: String? = null
)

@JsonClass(generateAdapter = true)
data class AudiusUser(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "handle") val handle: String? = null
)

@JsonClass(generateAdapter = true)
data class AudiusArtwork(
    @Json(name = "150x150") val x150: String? = null,
    @Json(name = "480x480") val x480: String? = null,
    @Json(name = "1000x1000") val x1000: String? = null,
    @Json(name = "mirrors") val mirrors: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class AudiusStream(
    @Json(name = "url") val url: String? = null,
    @Json(name = "mirrors") val mirrors: List<String>? = null
)
