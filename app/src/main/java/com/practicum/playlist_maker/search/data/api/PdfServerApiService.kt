package com.practicum.playlist_maker.search.data.api

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API для работы с PDF сервером
 */
interface PdfServerApiService {
    
    /**
     * Получить список директорий
     */
    @GET("/directories")
    suspend fun getDirectories(): List<DirectoryResponse>
    
    /**
     * Получить содержимое директории
     * @param path Путь к директории
     */
    @GET("/directory")
    suspend fun getDirectoryContent(@Query("path") path: String): List<SearchResponse>
    
    /**
     * Поиск файлов
     * @param query Поисковый запрос
     * @param path Опциональный путь для ограничения области поиска
     */
    @GET("/search")
    suspend fun searchFiles(
        @Query("query") query: String,
        @Query("path") path: String? = null
    ): List<SearchResponse>
} 