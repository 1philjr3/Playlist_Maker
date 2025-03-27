package com.practicum.playlist_maker.marchAuto.models

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

data class ActivityHistoryItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("userName") val userName: String? = "Неизвестный пользователь",
    @SerializedName("action") val action: String = "",
    @SerializedName("itemType") val itemType: String = "",
    @SerializedName("itemName") val itemName: String? = "",
    @SerializedName("path") val path: String? = "",
    @SerializedName("timestamp") val timestamp: String = ""
) {
    fun getFormattedDate(): String {
        if (timestamp.isEmpty()) return ""
        
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return try {
            val date = parser.parse(timestamp)
            formatter.format(date!!)
        } catch (e: Exception) {
            timestamp
        }
    }

    fun getActionText(): String {
        return when (action) {
            "add" -> "добавил"
            "delete" -> "удалил"
            "rename" -> "переименовал"
            "" -> "выполнил действие с"
            else -> action
        }
    }

    fun getItemTypeText(): String {
        return when (itemType) {
            "file" -> "файл"
            "directory" -> "директорию"
            "" -> "объектом"
            else -> itemType
        }
    }
} 