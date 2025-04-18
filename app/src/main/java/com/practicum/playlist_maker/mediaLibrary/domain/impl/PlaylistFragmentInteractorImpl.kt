package com.practicum.playlist_maker.mediaLibrary.domain.impl

import com.practicum.playlist_maker.player.domain.model.Library
import com.practicum.playlist_maker.mediaLibrary.domain.api.PlaylistFragmentInteractor
import com.practicum.playlist_maker.mediaLibrary.domain.api.PlaylistFragmentRepository
import kotlinx.coroutines.flow.Flow

class PlaylistFragmentInteractorImpl(private val playlistFragmentRepository: PlaylistFragmentRepository) :
    PlaylistFragmentInteractor {

    override suspend fun deletePlaylist(playlist: Library) {
        playlistFragmentRepository.deletePlaylist(playlist)
    }

    override fun getPlaylists(): Flow<List<Library>> {
        return playlistFragmentRepository.getPlaylists()
    }

}