package com.example.data.local

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.MusicSource
import com.example.data.model.Song
import java.io.File

/**
 * A track the user downloaded for **Mode offline** — the audio bytes live in
 * `filesDir/offline/`, so the song keeps playing with the radio off.
 *
 * The whole `Song` is mirrored here (not just the id) because an offline list
 * must render with no network at all: artwork, duration and the share link are
 * all read from this row instead of being re-fetched.
 *
 * A real migration (v5) creates the table — upgrading never discards
 * favourites, history or playlists.
 */
@Entity(tableName = "offline_tracks")
data class OfflineTrackEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    /** Remote artwork URL — the fallback when the local copy is missing. */
    val artworkUrl: String,
    /** Local artwork file, downloaded alongside the audio ("" when it failed). */
    val artworkPath: String = "",
    val durationMs: Long,
    /** Absolute path of the downloaded audio file. */
    val audioPath: String,
    val sizeBytes: Long,
    /** [MusicSource] name — kept as text so the row survives enum renames. */
    val source: String,
    val remoteId: String,
    val shareUrl: String,
    val artistId: String = "",
    val isPreview: Boolean = false,
    val downloadedAt: Long = System.currentTimeMillis()
) {

    /** True when the audio file is still on disk (it can be deleted by the OS). */
    fun audioExists(): Boolean = audioPath.isNotBlank() && File(audioPath).exists()

    /**
     * Rebuilds the playable [Song]. Playback points at the local file, so a
     * phone in aeroplane mode plays it exactly like a streaming track; if the
     * file vanished the row is treated as missing and the caller re-downloads.
     */
    fun toSong(): Song {
        val localArtwork = artworkPath.takeIf { it.isNotBlank() && File(it).exists() }
        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            artworkUrl = localArtwork?.let { Uri.fromFile(File(it)).toString() } ?: artworkUrl,
            streamUrl = Uri.fromFile(File(audioPath)).toString(),
            durationMs = durationMs,
            source = runCatching { MusicSource.valueOf(source) }.getOrDefault(MusicSource.DEEZER),
            remoteId = remoteId,
            shareUrl = shareUrl,
            artistId = artistId,
            isPreview = isPreview
        )
    }

    companion object {
        fun fromSong(song: Song, audioPath: String, sizeBytes: Long, artworkPath: String): OfflineTrackEntity =
            OfflineTrackEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                artworkUrl = song.artworkUrl,
                artworkPath = artworkPath,
                durationMs = song.durationMs,
                audioPath = audioPath,
                sizeBytes = sizeBytes,
                source = song.source.name,
                remoteId = song.remoteId,
                shareUrl = song.shareUrl,
                artistId = song.artistId,
                isPreview = song.isPreview
            )
    }
}
