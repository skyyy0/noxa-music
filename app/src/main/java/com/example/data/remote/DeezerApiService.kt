package com.example.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Deezer public REST API.
 *
 * These read-only endpoints answer without an API key or OAuth token, which is
 * why NOXA can offer a broad (including Indonesian) catalogue with no signup.
 * Every [DeezerTrack.preview] is a real, playable HTTPS MP3 — enforced by
 * Deezer to be a 30 second clip.
 *
 * Explicit parameters (no Kotlin default values) are used on purpose: Retrofit
 * proxies the abstract method, and default arguments would otherwise be dropped.
 */
interface DeezerApiService {

    @GET("search/track")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int
    ): Response<DeezerTrackListResponse>

    @GET("search/artist")
    suspend fun searchArtists(
        @Query("q") query: String,
        @Query("limit") limit: Int
    ): Response<DeezerArtistListResponse>

    @GET("artist/{artistId}/top")
    suspend fun artistTopTracks(
        @Path("artistId") artistId: Long,
        @Query("limit") limit: Int
    ): Response<DeezerTrackListResponse>

    @GET("artist/{artistId}")
    suspend fun artistDetail(
        @Path("artistId") artistId: Long
    ): Response<DeezerArtistDetailResponse>

    @GET("artist/{artistId}/related")
    suspend fun relatedArtists(
        @Path("artistId") artistId: Long,
        @Query("limit") limit: Int
    ): Response<DeezerArtistListResponse>

    @GET("artist/{artistId}/albums")
    suspend fun artistAlbums(
        @Path("artistId") artistId: Long,
        @Query("limit") limit: Int
    ): Response<DeezerAlbumListResponse>

    @GET("album/{albumId}")
    suspend fun albumDetail(
        @Path("albumId") albumId: Long
    ): Response<DeezerAlbumDetailResponse>

    @GET("album/{albumId}/tracks")
    suspend fun albumTracks(
        @Path("albumId") albumId: Long,
        @Query("limit") limit: Int
    ): Response<DeezerTrackListResponse>

    @GET("track/{trackId}")
    suspend fun trackDetail(
        @Path("trackId") trackId: Long
    ): Response<DeezerTrack>

    @GET("chart/0/tracks")
    suspend fun chartTracks(
        @Query("limit") limit: Int
    ): Response<DeezerTrackListResponse>

    @GET("playlist/{playlistId}/tracks")
    suspend fun playlistTracks(
        @Path("playlistId") playlistId: Long,
        @Query("limit") limit: Int
    ): Response<DeezerTrackListResponse>
}
