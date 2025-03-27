package com.practicum.playlist_maker.mediaLibrary.di

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
//import com.practicum.playlist_maker.mediaLibrary.data.repository.DirectoryRepository
import com.practicum.playlist_maker.mediaLibrary.ui.view_model.FavoriteTracksFragmentViewModel
import com.practicum.playlist_maker.mediaLibrary.ui.view_model.PlaylistFragmentViewModel
//import com.practicum.playlist_maker.mediaLibrary.ui.view_model.ReadingFragmentViewModel

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val mediaLibraryModule = module {

    viewModel { FavoriteTracksFragmentViewModel(get()) }
    viewModel { PlaylistFragmentViewModel(get()) }
    single { FirebaseFirestore.getInstance() } // Регистрация FirebaseFirestore
    single { FirebaseStorage.getInstance() } // Регистрация FirebaseStorage

}