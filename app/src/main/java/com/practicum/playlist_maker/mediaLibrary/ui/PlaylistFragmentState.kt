package com.practicum.playlist_maker.mediaLibrary.ui

import com.practicum.playlist_maker.player.domain.model.Library

sealed class PlaylistFragmentState() {
    class EmptyPlaylists() : PlaylistFragmentState()
    data class ContentPlaylists(val playlists: List<Library>) : PlaylistFragmentState()
}