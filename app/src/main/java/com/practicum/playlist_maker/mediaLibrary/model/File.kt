package com.practicum.playlist_maker.mediaLibrary.model

data class File(
    val id: String,          // Уникальный идентификатор файла
    val name: String,        // Название файла
    val filePath: String,    // Путь к файлу (например, URL или локальный путь)
    val isPdf: Boolean       // Флаг, указывающий, является ли файл PDF
)