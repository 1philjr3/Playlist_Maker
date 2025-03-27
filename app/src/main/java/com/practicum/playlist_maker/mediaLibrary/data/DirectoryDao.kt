//package com.practicum.playlist_maker.mediaLibrary.data
//
//import androidx.room.Dao
//import androidx.room.Insert
//import androidx.room.Query
//import com.practicum.playlist_maker.mediaLibrary.model.Directory
//
//@Dao
//interface DirectoryDao {
//    @Insert
//    suspend fun insert(directory: Directory)
//
//    @Query("SELECT * FROM directories WHERE parentId = :parentId")
//    suspend fun getDirectoriesByParentId(parentId: String?): List<Directory>
//
//    @Query("SELECT * FROM directories WHERE id = :id")
//    suspend fun getDirectoryById(id: String): Directory?
//}