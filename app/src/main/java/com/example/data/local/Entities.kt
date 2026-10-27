package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Song

@Entity(tableName = "favorite_songs")
data class FavoriteSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val streamUrl: String,
    val durationMs: Long,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        durationMs = durationMs,
        isFavorite = true
    )

    companion object {
        fun fromSong(song: Song): FavoriteSongEntity = FavoriteSongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            artworkUrl = song.artworkUrl,
            streamUrl = song.streamUrl,
            durationMs = song.durationMs
        )
    }
}

@Entity(tableName = "playback_history")
data class HistorySongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val streamUrl: String,
    val durationMs: Long,
    val playedAt: Long = System.currentTimeMillis()
) {
    fun toSong(isFav: Boolean = false): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        durationMs = durationMs,
        isFavorite = isFav
    )

    companion object {
        fun fromSong(song: Song): HistorySongEntity = HistorySongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            artworkUrl = song.artworkUrl,
            streamUrl = song.streamUrl,
            durationMs = song.durationMs,
            playedAt = System.currentTimeMillis()
        )
    }
}

/**
 * One play of one track, kept for the mini weekly recap on the profile
 * screen. Separate from `playback_history` (deduplicated, capped at 50): the
 * recap counts *plays*, including repeats within the week.
 */
@Entity(tableName = "play_records")
data class PlayRecordEntity(
    @PrimaryKey(autoGenerate = true) val recordId: Long = 0,
    val songId: String,
    val title: String,
    val artist: String,
    val playedAt: Long = System.currentTimeMillis()
)

/**
 * An artist the user follows. Backs the "Ikuti" button on the artist page
 * and the Home "Radar Rilis" shelf (new albums from followed artists).
 */
@Entity(tableName = "followed_artists")
data class FollowedArtistEntity(
    @PrimaryKey val artistId: Long,
    val name: String,
    val pictureUrl: String = "",
    val followedAt: Long = System.currentTimeMillis()
)

/**
 * A 1–5 star rating the user gave a song. Ratings steer Mix Sky and autoplay:
 * higher-rated songs are picked more often.
 */
@Entity(tableName = "song_ratings")
data class SongRatingEntity(
    @PrimaryKey val songId: String,
    val rating: Int,
    val ratedAt: Long = System.currentTimeMillis()
)

/**
 * A "jangan putar lagi" order: either one song (kind = `song`, value = song
 * id) or a whole artist (kind = `artist`, value = artist name). Muted entries
 * are filtered out of autoplay, Mix Sky and auto-appended similar tracks.
 */
@Entity(
    tableName = "muted_entries",
    primaryKeys = ["kind", "value"]
)
data class MutedEntryEntity(
    val kind: String,
    val value: String,
    val mutedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val KIND_SONG = "song"
        const val KIND_ARTIST = "artist"
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
