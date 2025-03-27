package com.practicum.playlist_maker.network

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface FileUploadService {
    @Multipart
    @POST("/upload")
    fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part name: MultipartBody.Part, // Убрано имя параметра
        @Part description: MultipartBody.Part // Убрано имя параметра
    ): Call<UploadResponse>

    @GET("/directories")
    fun getDirectories(): Call<List<DirectoryResponse>>
}

data class UploadResponse(val fileUrl: String)
data class DirectoryResponse(val name: String, val description: String, val imageUrl: String)