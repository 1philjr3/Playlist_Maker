package com.practicum.playlist_maker.marchAuto.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val email: String,
    val name: String,
    val department: String,
    val password: String,
    val isApproved: Boolean = false
) 