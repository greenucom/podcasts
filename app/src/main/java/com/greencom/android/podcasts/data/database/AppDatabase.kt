package com.greencom.android.podcasts.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PodcastEntity::class, EpisodeEntity::class, GenreEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {

    /** Data access object for the `podcasts` table. */
    abstract fun podcastDao(): PodcastDao

    /** Data access object for the `episodes` table. */
    abstract fun episodeDao(): EpisodeDao

    /** Data access object for the `genres` table. */
    abstract fun genreDao(): GenreDao
}