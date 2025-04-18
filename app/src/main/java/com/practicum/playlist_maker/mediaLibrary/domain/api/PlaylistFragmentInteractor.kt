package com.practicum.playlist_maker.mediaLibrary.domain.api

import com.practicum.playlist_maker.player.domain.model.Library
import kotlinx.coroutines.flow.Flow

interface PlaylistFragmentInteractor {

    suspend fun deletePlaylist(playlist: Library)
    fun getPlaylists(): Flow<List<Library>>
}