package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A playlist together with how many songs it currently holds. */
data class PlaylistSummary(
    val playlistId: Long,
    val name: String,
    val description: String,
    val createdAt: Long,
    val songCount: Int
)

/** One day's play count for the listening heatmap (`day` = epoch day). */
data class DayPlays(val day: Long, val plays: Int)

/** Artist tally for the mini-wrapped card. */
data class ArtistPlays(val artist: String, val plays: Int)

/** Song tally for the mini-wrapped card. */
data class TitlePlays(val title: String, val plays: Int)

/** Hour-of-day tally (0–23) for the mini-wrapped card. */
data class HourPlays(val hour: Int, val plays: Int)

@Dao
interface NoxaDao {
    // Favorites
    @Query("SELECT * FROM favorite_songs ORDER BY addedAt DESC")
    fun getFavoriteSongs(): Flow<List<FavoriteSongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE id = :songId)")
    fun isFavorite(songId: String): Flow<Boolean>

    /**
     * Read once, inside the toggle transaction, so a double tap can never
     * insert then delete (or the reverse) from a stale in-memory value.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE id = :songId)")
    suspend fun isFavoriteOnce(songId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(song: FavoriteSongEntity)

    @Query("DELETE FROM favorite_songs WHERE id = :songId")
    suspend fun deleteFavorite(songId: String)

    // Playback History
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT 50")
    fun getPlaybackHistory(): Flow<List<HistorySongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(song: HistorySongEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query(
        "SELECT p.playlistId AS playlistId, p.name AS name, p.description AS description, " +
            "p.createdAt AS createdAt, COUNT(s.songId) AS songCount " +
            "FROM playlists p LEFT JOIN playlist_songs s ON s.playlistId = p.playlistId " +
            "GROUP BY p.playlistId, p.name, p.description, p.createdAt " +
            "ORDER BY p.createdAt DESC"
    )
    fun getPlaylistSummaries(): Flow<List<PlaylistSummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE playlistId = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun deletePlaylistSongs(id: Long)

    // Songs inside a playlist
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    fun getPlaylistSongs(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId")
    fun getPlaylistSongIds(playlistId: Long): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    fun countPlaylistSongs(playlistId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(entity: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSong(playlistId: Long, songId: String)

    // Weekly recap — every play counts, repeats included.
    @Insert
    suspend fun insertPlayRecord(record: PlayRecordEntity)

    @Query("SELECT * FROM play_records WHERE playedAt >= :since ORDER BY playedAt DESC")
    fun getPlayRecordsSince(since: Long): Flow<List<PlayRecordEntity>>

    /** Keep the recap cheap: everything older than the retention window goes. */
    @Query("DELETE FROM play_records WHERE playedAt < :cutoff")
    suspend fun prunePlayRecords(cutoff: Long)

    // Aggregates for the listening heatmap & mini-wrapped. Grouping happens
    // in SQLite so a year of plays never has to travel to the app as raw rows.

    @Query(
        "SELECT playedAt / 86400000 AS day, COUNT(*) AS plays FROM play_records " +
            "WHERE playedAt >= :since GROUP BY day"
    )
    fun observePlaysPerDay(since: Long): Flow<List<DayPlays>>

    @Query(
        "SELECT artist AS artist, COUNT(*) AS plays FROM play_records " +
            "WHERE playedAt >= :since AND artist <> '' GROUP BY artist " +
            "ORDER BY plays DESC LIMIT :limit"
    )
    fun observeTopArtists(since: Long, limit: Int): Flow<List<ArtistPlays>>

    @Query(
        "SELECT title AS title, COUNT(*) AS plays FROM play_records " +
            "WHERE playedAt >= :since AND title <> '' GROUP BY title " +
            "ORDER BY plays DESC LIMIT :limit"
    )
    fun observeTopTitles(since: Long, limit: Int): Flow<List<TitlePlays>>

    @Query(
        "SELECT CAST(strftime('%H', playedAt / 1000, 'unixepoch') AS INTEGER) AS hour, " +
            "COUNT(*) AS plays FROM play_records WHERE playedAt >= :since GROUP BY hour"
    )
    fun observePlaysPerHour(since: Long): Flow<List<HourPlays>>

    @Query(
        "SELECT COUNT(DISTINCT artist) FROM play_records " +
            "WHERE playedAt >= :since AND artist <> ''"
    )
    fun observeDistinctArtistCount(since: Long): Flow<Int>

    // ---------------------------------------------------- followed artists

    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC")
    fun getFollowedArtists(): Flow<List<FollowedArtistEntity>>

    @Query("SELECT artistId FROM followed_artists")
    suspend fun followedArtistIdsOnce(): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM followed_artists WHERE artistId = :artistId)")
    fun isArtistFollowed(artistId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowedArtist(artist: FollowedArtistEntity)

    @Query("DELETE FROM followed_artists WHERE artistId = :artistId")
    suspend fun unfollowArtist(artistId: Long)

    // ------------------------------------------------------- song ratings

    @Query("SELECT * FROM song_ratings")
    fun getSongRatings(): Flow<List<SongRatingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongRating(rating: SongRatingEntity)

    @Query("DELETE FROM song_ratings WHERE songId = :songId")
    suspend fun clearSongRating(songId: String)

    // ---------------------------------------------- muted (jangan putar lagi)

    @Query("SELECT * FROM muted_entries")
    fun getMutedEntries(): Flow<List<MutedEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMuted(entry: MutedEntryEntity)

    @Query("DELETE FROM muted_entries WHERE kind = :kind AND value = :value")
    suspend fun deleteMuted(kind: String, value: String)

    // ----------------------------------------------- offline downloads (v5)

    @Query("SELECT * FROM offline_tracks ORDER BY downloadedAt DESC")
    fun observeOfflineTracks(): Flow<List<OfflineTrackEntity>>

    /** Read once for the playback substitution map built at start-up. */
    @Query("SELECT * FROM offline_tracks")
    suspend fun offlineTracksOnce(): List<OfflineTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOfflineTrack(track: OfflineTrackEntity)

    @Query("DELETE FROM offline_tracks WHERE songId = :songId")
    suspend fun deleteOfflineTrack(songId: String)
}
