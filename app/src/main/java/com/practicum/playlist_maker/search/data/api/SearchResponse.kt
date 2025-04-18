package com.practicum.playlist_maker.search.data.api

import com.google.gson.annotations.SerializedName

/**
 * Модель ответа поиска по файлам
 */
data class SearchResponse(
    /**
     * Имя файла
     */
    @SerializedName("name")
    val name: String,
    
    /**
     * Путь к файлу
     */
    @SerializedName("path")
    val path: String,
    
    /**
     * Тип файла (e.g., "pdf", "txt", "directory")
     */
    @SerializedName("type")
    val type: String,
    
    /**
     * Размер файла
     */
    @SerializedName("size")
    val size: Long? = null,
    
    /**
     * Время последнего изменения
     */
    @SerializedName("lastModified")
    val lastModified: Long? = 0,
    
    /**
     * Путь к родительской директории
     */
    @SerializedName("parentPath")
    val parentPath: String? = null
)

/**
 * Модель ответа с директориями
 */
data class DirectoryResponse(
    /**
     * Имя директории
     */
    @SerializedName("name")
    val name: String,
    
    /**
     * Путь к директории
     */
    @SerializedName("path")
    val path: String?,
    
    /**
     * Родительская директория (может быть null для корневой директории)
     */
    @SerializedName("parentPath")
    val parentPath: String? = null
)

/**
 * Преобразование DirectoryResponse в SearchResponse
 */
fun DirectoryResponse.toSearchResponse(): SearchResponse {
    return SearchResponse(
        name = this.name,
        path = this.path ?: "",
        type = "directory",
        parentPath = this.parentPath
    )
} 