package com.practicum.playlist_maker.marchAuto.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE isApproved = 1")
    fun getApprovedUsers(): Flow<List<UserEntity>>

    @Query("UPDATE users SET isApproved = :isApproved WHERE email = :email")
    suspend fun updateUserApprovalStatus(email: String, isApproved: Boolean)
} 