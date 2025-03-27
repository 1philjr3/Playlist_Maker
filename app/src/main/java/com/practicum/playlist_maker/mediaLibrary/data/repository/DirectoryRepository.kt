//package com.practicum.playlist_maker.mediaLibrary.data.repository
//
//import android.net.Uri
//import com.google.firebase.firestore.FirebaseFirestore
//import com.google.firebase.storage.FirebaseStorage
//import com.practicum.playlist_maker.mediaLibrary.ui.FavoriteScreenState
//import kotlinx.coroutines.tasks.await
//import java.io.File
//
//class DirectoryRepository {
//
//    private val db = FirebaseFirestore.getInstance()
//    private val storage = FirebaseStorage.getInstance()
//
//    // Метод для получения списка директорий
//    suspend fun getDirectories(): List<FavoriteScreenState.Directory> {
//        return db.collection("directories").get().await()
//            .map { document ->
//                FavoriteScreenState.Directory(
//                    id = document.id,
//                    name = document.getString("name") ?: "",
//                    imageUrl = document.getString("imageUrl") ?: "",
//                    createdAt = document.getLong("createdAt") ?: 0
//                )
//            }
//    }
//
//    fun getFiles(directoryId: String) = db.collection("directories")
//        .document(directoryId)
//        .collection("files")
//        .get()
//
//    // Метод для загрузки файла в Firebase Storage
//    fun uploadFile(file: File, directoryId: String, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
//        val storageRef = storage.reference
//        val fileRef = storageRef.child("directories/$directoryId/${file.name}")
//        fileRef.putFile(Uri.fromFile(file))
//            .addOnSuccessListener {
//                fileRef.downloadUrl.addOnSuccessListener { uri ->
//                    onSuccess(uri.toString())
//                }
//            }
//            .addOnFailureListener { exception ->
//                onFailure(exception)
//            }
//    }
//
//    // Метод для создания директории
//    suspend fun createDirectory(name: String, imageUrl: String) {
//        val directory = hashMapOf(
//            "name" to name,
//            "imageUrl" to imageUrl,
//            "createdAt" to System.currentTimeMillis()
//        )
//        db.collection("directories").add(directory).await()
//    }
//
//    fun uploadImage(imageUri: Uri, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
//        val storageRef = storage.reference
//        val imageRef = storageRef.child("images/${System.currentTimeMillis()}.jpg")
//        imageRef.putFile(imageUri)
//            .addOnSuccessListener {
//                imageRef.downloadUrl.addOnSuccessListener { uri ->
//                    onSuccess(uri.toString())
//                }
//            }
//            .addOnFailureListener { exception ->
//                onFailure(exception)
//            }
//    }
//}