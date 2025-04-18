package com.practicum.playlist_maker.search.ui

sealed interface SearchState {
    object Loading : SearchState

    data class Error(
        val errorMessageResId: Int
    ) : SearchState
}