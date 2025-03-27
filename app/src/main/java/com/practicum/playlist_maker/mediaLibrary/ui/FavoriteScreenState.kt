package com.practicum.playlist_maker.mediaLibrary.ui

import com.practicum.playlist_maker.player.domain.model.Track


sealed class FavoriteScreenState() {

    class Empty : FavoriteScreenState()

    data class Content(val tracks: List<Track>) : FavoriteScreenState()

    data class Directory(
        val id: String,
        val name: String,
        val description: String,
        val imageUrl: String,
        val parentId: String?
    )

    data class Document(
        val id: String = "",
        val title: String,
        val description: String? = null,
        val pdfUrl: String,
        val createdAt: Long = System.currentTimeMillis()
    )

}