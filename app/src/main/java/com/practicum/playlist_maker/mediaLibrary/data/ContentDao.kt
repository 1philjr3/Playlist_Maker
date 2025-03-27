//package com.practicum.playlist_maker.mediaLibrary.data
//
//import androidx.room.Dao
//import androidx.room.Insert
//import androidx.room.Query
//import com.practicum.playlist_maker.mediaLibrary.model.Content
//
//@Dao
//interface ContentDao {
//    @Insert
//    suspend fun insert(content: Content)
//
//    @Query("SELECT * FROM contents WHERE parentDirectoryId = :parentDirectoryId")
//    suspend fun getContentsByParentDirectoryId(parentDirectoryId: String): List<Content>
//}