package com.example.data.remote

import com.squareup.moshi.JsonClass

/**
 * Response shapes for the three services NOXA uses for YouTube tracks:
 *
 *  * **Piped** — `/search?q=…&filter=videos` and `/streams/{videoId}`. This is
 *    the primary source of playable stream URLs: Piped proxies the media
 *    through its own servers, so the URLs work from any device.
 *  * **Invidious** — `/api/v1/search` (a bare JSON array) and
 *    `/api/v1/videos/{id}`. Used as a fallback when no Piped instance
 *    answers. Its stream URLs point straight at Google's servers and are
 *    sometimes IP-restricted, which is why Piped is preferred.
 *  * **YouTube Data API v3** — optional metadata-only search used when the
 *    user configured `YOUTUBE_API_KEY`. It never returns audio.
 *
 * Every field is nullable: these are unofficial, changing APIs and a single
 * missing field must never crash a search or a playback.
 */

// --- Piped -------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class PipedSearchResponse(
    val items: List<PipedSearchItem>? = null
)

@JsonClass(generateAdapter = true)
data class PipedSearchItem(
    val type: String? = null,
    /** Usually `/watch?v=<videoId>`; the id is also sometimes top-level. */
    val url: String? = null,
    val id: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val uploaderName: String? = null,
    /** Seconds. `null` for channels/playlists/live streams. */
    val duration: Long? = null,
    val views: Long? = null
)

@JsonClass(generateAdapter = true)
data class PipedStreamsResponse(
    val title: String? = null,
    val uploaderName: String? = null,
    val thumbnail: String? = null,
    val duration: Long? = null,
    val views: Long? = null,
    val audioStreams: List<PipedAudioStream>? = null,
    val videoStreams: List<PipedVideoStream>? = null
)

@JsonClass(generateAdapter = true)
data class PipedAudioStream(
    val url: String? = null,
    /** e.g. `audio/mp4; codecs="mp4a.40.2"` or `audio/webm; codecs="opus"`. */
    val mimeType: String? = null,
    /** Bits per second. */
    val bitrate: Long? = null,
    val quality: String? = null,
    val codec: String? = null,
    val videoOnly: Boolean? = null,
    val format: String? = null
)

@JsonClass(generateAdapter = true)
data class PipedVideoStream(
    val url: String? = null,
    val mimeType: String? = null,
    val bitrate: Long? = null,
    /** e.g. `360p`. */
    val quality: String? = null,
    val codec: String? = null,
    /** True when the stream carries video only (no audio track). */
    val videoOnly: Boolean? = null,
    val fps: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val format: String? = null
)

// --- Invidious ---------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class InvidiousThumbnail(
    val quality: String? = null,
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class InvidiousSearchItem(
    val type: String? = null,
    val videoId: String? = null,
    val title: String? = null,
    val author: String? = null,
    val lengthSeconds: Long? = null,
    val viewCount: Long? = null,
    val videoThumbnails: List<InvidiousThumbnail>? = null
)

@JsonClass(generateAdapter = true)
data class InvidiousAdaptiveFormat(
    val url: String? = null,
    val itag: String? = null,
    /** e.g. `audio/webm; codecs="opus"` or `video/mp4; codecs="avc1.4d401f"`. */
    val type: String? = null,
    val container: String? = null,
    val qualityLabel: String? = null,
    /** `tiny` / `low` / `medium` / `high`. */
    val audioQuality: String? = null,
    val bitrate: Long? = null,
    val fps: Int? = null
)

@JsonClass(generateAdapter = true)
data class InvidiousFormatStream(
    val url: String? = null,
    val itag: String? = null,
    val type: String? = null,
    val container: String? = null,
    val quality: String? = null,
    /** e.g. `360p` — muxed (audio + video) progressive streams. */
    val resolution: String? = null,
    val qualityLabel: String? = null,
    val fps: Int? = null
)

@JsonClass(generateAdapter = true)
data class InvidiousVideoResponse(
    val videoId: String? = null,
    val title: String? = null,
    val author: String? = null,
    val lengthSeconds: Long? = null,
    val adaptiveFormats: List<InvidiousAdaptiveFormat>? = null,
    val formatStreams: List<InvidiousFormatStream>? = null
)

// --- YouTube Data API v3 (optional, metadata only) ---------------------------

@JsonClass(generateAdapter = true)
data class YouTubeSearchListResponse(
    val items: List<YouTubeSearchItem>? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeSearchItem(
    val id: YouTubeSearchItemId? = null,
    val snippet: YouTubeSearchSnippet? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeSearchItemId(
    val videoId: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeSearchSnippet(
    val title: String? = null,
    val channelTitle: String? = null,
    val publishedAt: String? = null,
    val thumbnails: YouTubeThumbnails? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnails(
    val high: YouTubeThumbnail? = null,
    val medium: YouTubeThumbnail? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeThumbnail(
    val url: String? = null
)
