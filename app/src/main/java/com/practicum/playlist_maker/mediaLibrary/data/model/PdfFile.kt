package com.practicum.playlist_maker.mediaLibrary.data.model

data class PdfFile(
    val id: String,
    val name: String,
    val description: String?,
    val fileUrl: String,
    val directoryId: String,
    val size: Long,
    val createdAt: Long,
    val lastOpenedAt: Long?
) {
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }

    fun getFormattedLastOpened(): String {
        return if (lastOpenedAt != null) {
            "Открыто: ${android.text.format.DateUtils.getRelativeTimeSpanString(
                lastOpenedAt,
                System.currentTimeMillis(),
                android.text.format.DateUtils.MINUTE_IN_MILLIS
            )}"
        } else {
            "Не открывалось"
        }
    }
} 