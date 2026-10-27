package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteSongEntity::class,
        HistorySongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlayRecordEntity::class,
        FollowedArtistEntity::class,
        SongRatingEntity::class,
        MutedEntryEntity::class,
        OfflineTrackEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class NoxaDatabase : RoomDatabase() {
    abstract fun noxaDao(): NoxaDao

    companion object {
        @Volatile
        private var INSTANCE: NoxaDatabase? = null

        /**
         * Adds the `playlist_songs` table introduced in version 2.
         *
         * Written as a real migration (rather than relying on the destructive
         * fallback) so upgrading does not throw away the user's favourites,
         * history and playlists.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_songs` (" +
                        "`playlistId` INTEGER NOT NULL, " +
                        "`songId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, " +
                        "`album` TEXT NOT NULL, " +
                        "`artworkUrl` TEXT NOT NULL, " +
                        "`streamUrl` TEXT NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`remoteId` TEXT NOT NULL, " +
                        "`isPreview` INTEGER NOT NULL, " +
                        "`shareUrl` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlistId`, `songId`))"
                )
            }
        }

        /**
         * Adds the `play_records` table introduced in version 3 (the mini
         * weekly recap). A real migration again — upgrading must not throw
         * away favourites, history and playlists.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_records` (" +
                        "`recordId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`songId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, " +
                        "`playedAt` INTEGER NOT NULL)"
                )
            }
        }

/**
 * Adds the v2.0.0 tables: `followed_artists` (artist follow + release radar),
 * `song_ratings` (star ratings) and `muted_entries` ("jangan putar lagi").
 * A real migration again — upgrading never discards listening data.
 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `followed_artists` (" +
                        "`artistId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`pictureUrl` TEXT NOT NULL, " +
                        "`followedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`artistId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `song_ratings` (" +
                        "`songId` TEXT NOT NULL, " +
                        "`rating` INTEGER NOT NULL, " +
                        "`ratedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`songId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `muted_entries` (" +
                        "`kind` TEXT NOT NULL, " +
                        "`value` TEXT NOT NULL, " +
                        "`mutedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`kind`, `value`))"
                )
            }
        }

/**
 * Adds the `offline_tracks` table introduced in version 5 (Mode offline).
 * A real migration again — downloading a song must never cost the user their
 * favourites, history, playlists or ratings.
 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `offline_tracks` (" +
                        "`songId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, " +
                        "`album` TEXT NOT NULL, " +
                        "`artworkUrl` TEXT NOT NULL, " +
                        "`artworkPath` TEXT NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`audioPath` TEXT NOT NULL, " +
                        "`sizeBytes` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`remoteId` TEXT NOT NULL, " +
                        "`shareUrl` TEXT NOT NULL, " +
                        "`artistId` TEXT NOT NULL, " +
                        "`isPreview` INTEGER NOT NULL, " +
                        "`downloadedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`songId`))"
                )
            }
        }

        fun getDatabase(context: Context): NoxaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoxaDatabase::class.java,
                    "noxa_music.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
