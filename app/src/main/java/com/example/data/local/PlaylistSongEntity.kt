package com.example.data.local

import androidx.room.Entity
import com.example.data.model.MusicSource
import com.example.data.model.Song

/**
 * A song inside a playlist.
 *
 * The full song is stored rather than a foreign key, for the same reason the
 * resume session stores whole [Song]s: playlists have to work offline, and the
 * provider's own ids are not stable across sources.
 */
@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val streamUrl: String,
    val durationMs: Long,
    val source: String,
    val remoteId: String,
    val isPreview: Boolean,
    val shareUrl: String,
    val position: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song = Song(
        id = songId,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        durationMs = durationMs,
        source = runCatching { MusicSource.valueOf(source) }.getOrDefault(MusicSource.AUDIUS),
        remoteId = remoteId,
        isPreview = isPreview,
        shareUrl = shareUrl
    )

    companion object {
        fun fromSong(playlistId: Long, song: Song, position: Int): PlaylistSongEntity =
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                artworkUrl = song.artworkUrl,
                streamUrl = song.streamUrl,
                durationMs = song.durationMs,
                source = song.source.name,
                remoteId = song.remoteId,
                isPreview = song.isPreview,
                shareUrl = song.shareUrl,
                position = position
            )
    }
}
