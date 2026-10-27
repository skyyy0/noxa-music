package com.example

import com.example.data.model.MusicSource
import com.example.data.model.Song
import com.example.data.remote.InvidiousAdaptiveFormat
import com.example.data.remote.InvidiousFormatStream
import com.example.data.remote.InvidiousSearchItem
import com.example.data.remote.InvidiousVideoResponse
import com.example.data.remote.PipedAudioStream
import com.example.data.remote.PipedSearchResponse
import com.example.data.remote.PipedStreamsResponse
import com.example.data.remote.PipedVideoStream
import com.example.data.remote.YouTubeSearchListResponse
import com.example.data.remote.YouTubeSourceRepository
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Network-free coverage of the YouTube integration: JSON parsing of the three
 * provider response shapes (Piped, Invidious, YouTube Data API) and the pure
 * "which stream do we play" selection rules.
 */
class YouTubeSourceRepositoryTest {

    private val moshi: Moshi = Moshi.Builder().build()
    private val repo = YouTubeSourceRepository()

    // ------------------------------------------------------------- video ids

    @Test
    fun `extracts video ids from the url shapes instances use`() {
        assertEquals("dQw4w9WgXcQ", YouTubeSourceRepository.extractVideoId("/watch?v=dQw4w9WgXcQ"))
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeSourceRepository.extractVideoId("https://piped.video/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeSourceRepository.extractVideoId("https://youtu.be/dQw4w9WgXcQ")
        )
        assertEquals("dQw4w9WgXcQ", YouTubeSourceRepository.extractVideoId("dQw4w9WgXcQ"))
        assertNull(YouTubeSourceRepository.extractVideoId(null))
        assertNull(YouTubeSourceRepository.extractVideoId(""))
        // Not 11 chars — must not be mistaken for an id.
        assertNull(YouTubeSourceRepository.extractVideoId("/watch?v=short"))
    }

    // ------------------------------------------------------------- parsing

    @Test
    fun `parses a piped search response`() {
        val json = """
            {
              "items": [
                {
                  "type": "stream",
                  "url": "/watch?v=dQw4w9WgXcQ",
                  "title": "Never Gonna Give You Up",
                  "thumbnail": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg",
                  "uploaderName": "Rick Astley",
                  "duration": 213,
                  "views": 1234567
                },
                { "type": "channel", "url": "/channel/UCxyz", "title": "Channel" }
              ]
            }
        """.trimIndent()

        val response = moshi.adapter(PipedSearchResponse::class.java).fromJson(json)
        assertNotNull(response)
        val items = response!!.items
        assertNotNull(items)
        assertEquals(2, items!!.size)
        assertEquals("dQw4w9WgXcQ", YouTubeSourceRepository.extractVideoId(items[0].url))
        assertEquals("Rick Astley", items[0].uploaderName)
        assertEquals(213L, items[0].duration)
    }

    @Test
    fun `parses a piped streams response`() {
        val json = """
            {
              "title": "Never Gonna Give You Up",
              "uploaderName": "Rick Astley",
              "duration": 213,
              "audioStreams": [
                {
                  "url": "https://proxy.example/audio-251.webm",
                  "mimeType": "audio/webm; codecs=\"opus\"",
                  "bitrate": 160000,
                  "format": "webm",
                  "videoOnly": false
                },
                {
                  "url": "https://proxy.example/audio-140.m4a",
                  "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                  "bitrate": 128000,
                  "format": "m4a",
                  "videoOnly": false
                }
              ],
              "videoStreams": [
                {
                  "url": "https://proxy.example/video-1080.mp4",
                  "mimeType": "video/mp4; codecs=\"avc1.640028\"",
                  "bitrate": 3000000,
                  "quality": "1080p",
                  "height": 1080,
                  "videoOnly": true
                },
                {
                  "url": "https://proxy.example/video-360.mp4",
                  "mimeType": "video/mp4; codecs=\"avc1.4d401e\"",
                  "bitrate": 600000,
                  "quality": "360p",
                  "height": 360,
                  "videoOnly": true
                }
              ]
            }
        """.trimIndent()

        val response = moshi.adapter(PipedStreamsResponse::class.java).fromJson(json)
        assertNotNull(response)
        assertEquals(2, response!!.audioStreams!!.size)
        assertEquals(2, response.videoStreams!!.size)
    }

    @Test
    fun `parses an invidious search array and a video response`() {
        val searchJson = """
            [
              {
                "type": "video",
                "videoId": "dQw4w9WgXcQ",
                "title": "Never Gonna Give You Up",
                "author": "Rick Astley",
                "lengthSeconds": 213,
                "videoThumbnails": [
                  { "quality": "maxres", "url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxres.jpg" }
                ]
              }
            ]
        """.trimIndent()
        val listType = com.squareup.moshi.Types.newParameterizedType(
            List::class.java,
            InvidiousSearchItem::class.java
        )
        val items = moshi.adapter<List<InvidiousSearchItem>>(listType).fromJson(searchJson)
        assertNotNull(items)
        assertEquals("dQw4w9WgXcQ", items!![0].videoId)
        assertEquals(213L, items[0].lengthSeconds)

        val videoJson = """
            {
              "videoId": "dQw4w9WgXcQ",
              "adaptiveFormats": [
                {
                  "url": "https://googlevideo.example/videoplayback-audio",
                  "type": "audio/webm; codecs=\"opus\"",
                  "audioQuality": "medium",
                  "bitrate": 128000
                },
                {
                  "url": "https://googlevideo.example/videoplayback-video",
                  "type": "video/mp4; codecs=\"avc1.4d401e\"",
                  "qualityLabel": "360p",
                  "bitrate": 600000
                }
              ],
              "formatStreams": [
                {
                  "url": "https://googlevideo.example/videoplayback-muxed",
                  "type": "video/mp4; codecs=\"avc1\"",
                  "resolution": "480p",
                  "qualityLabel": "480p"
                }
              ]
            }
        """.trimIndent()
        val video = moshi.adapter(InvidiousVideoResponse::class.java).fromJson(videoJson)
        assertNotNull(video)
        assertEquals(2, video!!.adaptiveFormats!!.size)
        assertEquals(1, video.formatStreams!!.size)
    }

    @Test
    fun `parses a youtube data api search response`() {
        val json = """
            {
              "items": [
                {
                  "id": { "videoId": "abc123def45" },
                  "snippet": {
                    "title": "Official Audio",
                    "channelTitle": "Artist Topic",
                    "thumbnails": {
                      "high": { "url": "https://i.ytimg.com/vi/abc123def45/hqdefault.jpg" }
                    }
                  }
                }
              ]
            }
        """.trimIndent()
        val response = moshi.adapter(YouTubeSearchListResponse::class.java).fromJson(json)
        assertNotNull(response)
        assertEquals("abc123def45", response!!.items!![0].id!!.videoId)
        assertEquals("Artist Topic", response.items!![0].snippet!!.channelTitle)
    }

    // ------------------------------------------------------------ selection

    @Test
    fun `piped audio pick prefers m4a over higher-bitrate webm and skips video-only`() {
        val streams = listOf(
            PipedAudioStream(
                url = "https://proxy.example/opuss.webm",
                mimeType = "audio/webm; codecs=\"opus\"",
                bitrate = 160000,
                format = "webm",
                videoOnly = false
            ),
            PipedAudioStream(
                url = "https://proxy.example/aac.m4a",
                mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
                bitrate = 128000,
                format = "m4a",
                videoOnly = false
            ),
            // Video-only entries must never be chosen for audio.
            PipedAudioStream(
                url = "https://proxy.example/videoaudio.mp4",
                mimeType = "audio/mp4",
                bitrate = 384000,
                videoOnly = true
            ),
            // Entries without a usable URL must never be chosen either.
            PipedAudioStream(url = null, mimeType = "audio/mp4", bitrate = 999000)
        )

        val picked = repo.pickPipedAudioStream(streams)
        assertNotNull(picked)
        assertEquals("https://proxy.example/aac.m4a", picked!!.url)
    }

    @Test
    fun `piped audio pick falls back to the highest bitrate when no m4a exists`() {
        val streams = listOf(
            PipedAudioStream(url = "https://a", bitrate = 64000, videoOnly = false),
            PipedAudioStream(url = "https://b", bitrate = 128000, videoOnly = false)
        )
        assertEquals("https://b", repo.pickPipedAudioStream(streams)?.url)
        assertNull(repo.pickPipedAudioStream(null))
    }

    @Test
    fun `piped video pick targets 360p video-only streams`() {
        val streams = listOf(
            PipedVideoStream(
                url = "https://proxy.example/v1080.mp4",
                quality = "1080p",
                height = 1080,
                videoOnly = true
            ),
            PipedVideoStream(
                url = "https://proxy.example/v360.mp4",
                quality = "360p",
                height = 360,
                videoOnly = true,
                mimeType = "video/mp4"
            ),
            // A muxed (audio+video) entry must not be picked: the MV player is
            // meant to render a silent video track alongside the main audio.
            PipedVideoStream(
                url = "https://proxy.example/muxed.mp4",
                quality = "360p",
                height = 360,
                videoOnly = false
            )
        )
        assertEquals("https://proxy.example/v360.mp4", repo.pickPipedVideoStream(streams)?.url)
        assertNull(repo.pickPipedVideoStream(emptyList()))
    }

    @Test
    fun `invidious audio pick takes the best audio quality`() {
        val formats = listOf(
            InvidiousAdaptiveFormat(
                url = "https://g/videoplayback-video",
                type = "video/mp4; codecs=\"avc1\"",
                qualityLabel = "360p"
            ),
            InvidiousAdaptiveFormat(
                url = "https://g/videoplayback-low",
                type = "audio/webm; codecs=\"opus\"",
                audioQuality = "low",
                bitrate = 64000
            ),
            InvidiousAdaptiveFormat(
                url = "https://g/videoplayback-med",
                type = "audio/webm; codecs=\"opus\"",
                audioQuality = "medium",
                bitrate = 128000
            )
        )
        assertEquals(
            "https://g/videoplayback-med",
            repo.pickInvidiousAudioFormat(formats)?.url
        )
        assertNull(repo.pickInvidiousAudioFormat(null))
    }

    @Test
    fun `invidious video pick prefers adaptive video-only near 360p, then muxed`() {
        val adaptive = listOf(
            InvidiousAdaptiveFormat(
                url = "https://g/video-1080",
                type = "video/mp4",
                qualityLabel = "1080p"
            ),
            InvidiousAdaptiveFormat(
                url = "https://g/video-360",
                type = "video/mp4",
                qualityLabel = "360p"
            )
        )
        val muxed = listOf(
            InvidiousFormatStream(
                url = "https://g/muxed-480",
                type = "video/mp4",
                resolution = "480p",
                qualityLabel = "480p"
            )
        )
        assertEquals(
            "https://g/video-360",
            repo.pickInvidiousVideoFormat(adaptive, muxed)
        )
        // No adaptive video-only → the muxed progressive stream is the fallback.
        assertEquals(
            "https://g/muxed-480",
            repo.pickInvidiousVideoFormat(null, muxed)
        )
        assertNull(repo.pickInvidiousVideoFormat(null, null))
    }

    // --------------------------------------------------------- song mapping

    @Test
    fun `youtube song ids round-trip back to source and remote id`() {
        val song = Song(
            id = Song.idFor(MusicSource.YOUTUBE, "dQw4w9WgXcQ"),
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            artworkUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            // Simulates a favourite persisted before source/remoteId existed.
            source = MusicSource.AUDIUS,
            remoteId = ""
        )

        val restored = song.withDerivedProviderFields()
        assertEquals(MusicSource.YOUTUBE, restored.source)
        assertEquals("dQw4w9WgXcQ", restored.remoteId)
        assertEquals("youtube:dQw4w9WgXcQ", restored.id)
    }

    // ------------------------------------------------------ full-audio match

    private fun score(
        candidateTitle: String,
        candidateChannel: String = "Dewa 19",
        candidateDurationMs: Long = 314_000L,
        targetTitle: String = "Kangen",
        targetArtist: String = "Dewa 19",
        targetDurationMs: Long = 314_000L
    ): Int = YouTubeSourceRepository.scoreMatch(
        candidateTitle = candidateTitle,
        candidateChannel = candidateChannel,
        candidateDurationMs = candidateDurationMs,
        targetTitle = targetTitle,
        targetArtist = targetArtist,
        targetDurationMs = targetDurationMs
    )

    @Test
    fun `an official upload with matching title and duration scores high`() {
        val official = score(
            candidateTitle = "DEWA 19 - KANGEN (Official Audio)",
            candidateChannel = "Dewa 19 Topic",
            candidateDurationMs = 316_000L
        )
        assertTrue("expected a confident match, got $official", official > 100)
    }

    @Test
    fun `the official upload beats a live version of the same song`() {
        val official = score(
            candidateTitle = "DEWA 19 - KANGEN (Official Audio)",
            candidateDurationMs = 314_000L
        )
        val live = score(
            candidateTitle = "DEWA 19 KANGEN Live in Jakarta",
            candidateDurationMs = 340_000L
        )
        assertTrue("live ($live) should score below official ($official)", live < official)
    }

    @Test
    fun `karaoke playlists and compilations are rejected outright`() {
        assertEquals(0, score("Kangen Karaoke Instrumental Version"))
        assertEquals(0, score("Dewa 19 Best Playlist"))
        assertEquals(0, score("Dewa 19 2 Hours Mix"))
        assertEquals(0, score("Kangen — Full Album Collection"))
    }

    @Test
    fun `a different song by the same artist is not a match`() {
        assertEquals(
            0,
            score(
                candidateTitle = "DEWA 19 - Pupus (Official Audio)",
                candidateChannel = "Dewa 19 Topic"
            )
        )
    }

    @Test
    fun `duration proximity decides between two otherwise equal uploads`() {
        val rightLength = score(
            candidateTitle = "Dewa 19 Kangen",
            candidateDurationMs = 315_000L
        )
        val halfLength = score(
            candidateTitle = "Dewa 19 Kangen",
            candidateDurationMs = 150_000L
        )
        assertTrue(
            "right-length ($rightLength) should beat the cut version ($halfLength)",
            rightLength > halfLength
        )
    }

    @Test
    fun `featured artists and remaster notes in the target do not break matching`() {
        val featured = score(
            candidateTitle = "KANGEN (Official Audio)",
            targetTitle = "Kangen (feat. Ariel)",
            targetDurationMs = 314_000L
        )
        assertTrue("feat credits should not block the match, got $featured", featured > 100)

        val remastered = score(
            candidateTitle = "Kangen (Official Audio)",
            targetTitle = "Kangen - 2011 Remaster",
            targetDurationMs = 314_000L
        )
        assertTrue("remaster notes should not block the match, got $remastered", remastered > 100)
    }

    @Test
    fun `a remix is only accepted when the target is also a remix`() {
        val studioCandidate = score(candidateTitle = "Kangen (Official Audio)")
        val remixTarget = score(
            candidateTitle = "Kangen (Official Audio)",
            targetTitle = "Kangen (Remix)"
        )
        val remixCandidate = score(candidateTitle = "Kangen Remix (Audio)")
        assertTrue(studioCandidate > remixCandidate)
        assertTrue(remixTarget > remixCandidate)
    }
}
