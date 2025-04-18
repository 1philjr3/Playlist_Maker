package com.practicum.playlist_maker.player.domain.model

import android.net.Uri

data class Library(
    val id: Int = 0,
    val name: String,
    val description: String = "",
    val pathUri: Uri? = null,
    val fileIds: MutableList<Int> = mutableListOf(),
    val countOfFiles: Int = 0,
) 