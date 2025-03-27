package com.practicum.playlist_maker.network

import com.practicum.playlist_maker.mediaLibrary.model.Directory
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface DirectoryApiService {

    @GET("directories/{directoryId}")
    fun getDirectoryContents(@Path("directoryId") directoryId: String): Call<Directory>

    // Получение корневых директорий
    @GET("/directories/root")
    fun getRootDirectories(): Call<List<Directory>>

    // Создание новой директории
    @POST("/directories")
    fun createDirectory(@Body directory: Directory): Call<Directory>
}