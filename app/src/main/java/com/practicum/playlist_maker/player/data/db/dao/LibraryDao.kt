package com.practicum.playlist_maker.player.data.db.dao

import androidx.room.*
import com.practicum.playlist_maker.player.data.db.entity.LibraryEntity

@Dao
interface LibraryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibrary(library: LibraryEntity)

    @Delete(entity = LibraryEntity::class)
    suspend fun deleteLibrary(library: LibraryEntity)

    @Update
    suspend fun updateLibrary(library: LibraryEntity)

    @Query("SELECT * FROM library_table")
    suspend fun getLibraries(): List<LibraryEntity>

} 