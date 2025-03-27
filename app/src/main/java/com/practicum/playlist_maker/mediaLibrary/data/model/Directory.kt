package com.practicum.playlist_maker.mediaLibrary.data.model

data class Directory(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String,
    val parentId: String? = null
) 