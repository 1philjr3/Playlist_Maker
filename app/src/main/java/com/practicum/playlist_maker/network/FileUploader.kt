// FileUploader.kt
package com.practicum.playlist_maker.network

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class FileUploader(private val context: Context) {

    private val retrofit = Retrofit.Builder()
        .baseUrl(getServerUrl(context))
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service: FileUploadService = retrofit.create(FileUploadService::class.java)

    fun getDirectories(onSuccess: (List<DirectoryResponse>) -> Unit, onError: (Throwable) -> Unit) {
        val call = service.getDirectories()
        call.enqueue(object : Callback<List<DirectoryResponse>> {
            override fun onResponse(call: Call<List<DirectoryResponse>>, response: Response<List<DirectoryResponse>>) {
                if (response.isSuccessful) {
                    val directories = response.body()
                    if (directories != null) {
                        onSuccess(directories)
                    } else {
                        onError(Throwable("Directories list is null"))
                    }
                } else {
                    onError(Throwable("Error fetching directories: ${response.errorBody()}"))
                }
            }

            override fun onFailure(call: Call<List<DirectoryResponse>>, t: Throwable) {
                onError(t)
            }
        })
    }

    fun uploadFile(imageUri: Uri, name: String, description: String, onSuccess: (String) -> Unit, onError: (Throwable) -> Unit) {
        val file = createTempFileFromUri(imageUri)
        if (file != null) {
            val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)

            // Создаем MultipartBody.Part для name и description
            val namePart = MultipartBody.Part.createFormData("name", name)
            val descriptionPart = MultipartBody.Part.createFormData("description", description)

            val call = service.uploadFile(filePart, namePart, descriptionPart)
            call.enqueue(object : Callback<UploadResponse> {
                override fun onResponse(call: Call<UploadResponse>, response: Response<UploadResponse>) {
                    if (response.isSuccessful) {
                        val fileUrl = response.body()?.fileUrl
                        if (fileUrl != null) {
                            // Заменяем localhost на 10.0.2.2 (для эмулятора)
                            val correctedUrl = fileUrl.replace("localhost", "10.0.2.2")
                            Log.d("Upload", "File uploaded successfully: $correctedUrl")
                            onSuccess(correctedUrl)
                        } else {
                            Log.e("Upload", "File URL is null")
                            onError(Throwable("File URL is null"))
                        }
                    } else {
                        Log.e("Upload", "Error uploading file: ${response.errorBody()}")
                        onError(Throwable("Error uploading file: ${response.errorBody()}"))
                    }
                }

                override fun onFailure(call: Call<UploadResponse>, t: Throwable) {
                    Log.e("Upload", "Upload failed", t)
                    onError(t)
                }
            })
        } else {
            Log.e("Upload", "Failed to create temp file from URI")
            onError(Throwable("Failed to create temp file from URI"))
        }
    }

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            val contentResolver: ContentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val file = File.createTempFile("temp_image", ".jpg", context.cacheDir)
                val outputStream = FileOutputStream(file)
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                file
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Функция для получения URL сервера в зависимости от типа устройства
    private fun getServerUrl(context: Context): String {
        // Проверяем, запущено ли приложение на эмуляторе
        val isEmulator = android.os.Build.MODEL.contains("sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK")
        
        return if (isEmulator) {
            "http://10.0.2.2:3000"
        } else {
            "http://192.168.0.105:3000"
        }
    }
}