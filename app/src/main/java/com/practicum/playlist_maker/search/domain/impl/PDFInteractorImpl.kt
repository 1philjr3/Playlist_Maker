package com.practicum.playlist_maker.search.domain.impl

import com.practicum.playlist_maker.player.domain.model.Track
import com.practicum.playlist_maker.search.domain.api.PDFInteractor
import com.practicum.playlist_maker.search.domain.api.PDFRepository
import com.practicum.playlist_maker.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PDFInteractorImpl(private val repository: PDFRepository) : PDFInteractor {

    override fun searchTracks(expression: String): Flow<Pair<List<Track>?, String?>> {
        return repository.searchTracks(expression).map { result ->
            when (result) {
                is Resource.Success -> Pair(result.data, null)
                is Resource.ServerError -> Pair(null, result.message)
                is Resource.InternetError -> Pair(null, result.message)
            }
        }
    }

    override suspend fun getTracksFromSearchHistory(): Flow<ArrayList<Track>> {
        return repository.getTracksFromSearchHistory()
    }

    override fun addTrackToSearchHistory(track: Track) {
        repository.addTrackToSearchHistory(track)
    }

    override fun clearSearchHistory() {
        repository.clearSearchHistory()
    }
}