package com.example

import com.example.data.model.MusicSource
import com.example.data.remote.AudiusRepository
import com.example.data.remote.AudiusTrackListResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * These tests exercise the real Audius integration code paths that do not need a
 * network call: JSON parsing of the API response shape, the mapping onto the app
 * `Song` model, and the playable stream URL that is handed to ExoPlayer.
 */
class AudiusRepositoryTest {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    /** Sample payload that mirrors the documented Audius search/trending shape. */
    private val sampleJson = """
        {
          "data": [
            {
              "id": "D7KyD",
              "title": "Sunset Drive",
              "duration": 226,
              "genre": "Electronic",
              "user": { "id": "u1", "name": "Nova Beats", "handle": "novabeats" },
              "artwork": {
                "150x150": "https://cdn.audius.co/150.jpg",
                "480x480": "https://cdn.audius.co/480.jpg",
                "1000x1000": "https://cdn.audius.co/1000.jpg",
                "mirrors": ["https://mirror.audius.co/480.jpg"]
              },
              "play_count": 12345
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses the audius track list response`() {
        val adapter = moshi.adapter(AudiusTrackListResponse::class.java)
        val response = adapter.fromJson(sampleJson)

        assertNotNull(response)
        val tracks = response!!.data
        assertNotNull(tracks)
        assertEquals(1, tracks!!.size)

        val track = tracks.first()
        assertEquals("D7KyD", track.id)
        assertEquals("Sunset Drive", track.title)
        assertEquals(226, track.duration)
        assertEquals("Nova Beats", track.user?.name)
        assertEquals("https://cdn.audius.co/480.jpg", track.artwork?.x480)
        assertEquals(12345L, track.playCount)
    }

    @Test
    fun `maps an audius track onto the app song model with a real stream url`() {
        val adapter = moshi.adapter(AudiusTrackListResponse::class.java)
        val track = adapter.fromJson(sampleJson)!!.data!!.first()

        val repository = AudiusRepository()
        val song = with(repository) { track.toSong() }

        assertNotNull(song)
        // The id is namespaced by provider so Deezer ids can never collide with it.
        assertEquals("audius:D7KyD", song!!.id)
        assertEquals(MusicSource.AUDIUS, song.source)
        assertEquals("D7KyD", song.remoteId)
        assertFalse(song.isPreview)
        assertEquals("Sunset Drive", song.title)
        assertEquals("Nova Beats", song.artist)
        assertEquals("Electronic", song.album)
        assertEquals("https://cdn.audius.co/480.jpg", song.artworkUrl)
        assertEquals(226_000L, song.durationMs)
        assertEquals("novabeats", song.channelId)
        assertEquals("12345", song.viewCount)
        // The URL handed to ExoPlayer must be a real, absolute HTTPS stream URL.
        assertEquals(
            "https://api.audius.co/v1/tracks/D7KyD/stream?app_name=NOXA",
            song.streamUrl
        )
    }

    @Test
    fun `stream url is built from the track id`() {
        val repository = AudiusRepository()
        assertEquals(
            "https://api.audius.co/v1/tracks/abc123/stream?app_name=NOXA",
            repository.streamUrlFor("abc123")
        )
    }

    @Test
    fun `tracks without an id are ignored`() {
        val adapter = moshi.adapter(AudiusTrackListResponse::class.java)
        val response = adapter.fromJson("""{"data":[{"title":"No id here"}]}""")

        val repository = AudiusRepository()
        val song = response!!.data!!.first().let { with(repository) { it.toSong() } }

        assertNull(song)
    }
}
