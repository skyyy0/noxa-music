package com.example.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Audius public REST API.
 *
 * Anonymous (keyless) reads are supported, so the app works with no developer
 * key at all. If you later obtain an Audius developer key you can supply it
 * through the `AUDIUS_API_KEY` secret and it will be sent as `x-api-key`.
 *
 * Explicit parameters (no Kotlin default values) are used on purpose: Retrofit
 * proxies the abstract method, and default arguments would otherwise be dropped.
 */
interface AudiusApiService {

    @GET("v1/tracks/search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("limit") limit: Int,
        @Query("app_name") appName: String,
        @Header("x-api-key") apiKey: String?
    ): Response<AudiusTrackListResponse>

    @GET("v1/tracks/trending")
    suspend fun trendingTracks(
        @Query("limit") limit: Int,
        @Query("app_name") appName: String,
        @Header("x-api-key") apiKey: String?
    ): Response<AudiusTrackListResponse>

    @GET("v1/tracks/trending")
    suspend fun trendingTracksByGenre(
        @Query("genre") genre: String,
        @Query("limit") limit: Int,
        @Query("app_name") appName: String,
        @Header("x-api-key") apiKey: String?
    ): Response<AudiusTrackListResponse>

    @GET("v1/tracks/{trackId}")
    suspend fun getTrack(
        @Path("trackId") trackId: String,
        @Query("app_name") appName: String,
        @Header("x-api-key") apiKey: String?
    ): Response<AudiusTrackSingleResponse>
}
