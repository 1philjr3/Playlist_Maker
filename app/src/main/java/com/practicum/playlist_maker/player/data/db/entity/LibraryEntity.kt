package com.practicum.playlist_maker.player.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("library_table")
data class LibraryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val description: String,
    val path: String,
    val fileIds: String,
    val countOfFiles: Int,
) 