package com.practicum.playlist_maker.mediaLibrary.model

data class FavoriteFile(
    val id: String,
    val name: String,
    val fileUrl: String,
    val directoryPath: String,
    val addedToFavoritesAt: String
) 