package com.practicum.playlist_maker.player.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.practicum.playlist_maker.player.data.db.dao.LibraryDao
import com.practicum.playlist_maker.player.data.db.entity.LibraryEntity

@Database(
    version = 1,
    entities = [LibraryEntity::class],
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}
