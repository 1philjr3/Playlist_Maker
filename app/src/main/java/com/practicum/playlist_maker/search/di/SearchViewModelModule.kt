package com.practicum.playlist_maker.search.di

import com.practicum.playlist_maker.pdfreader.repository.PDFRepository
import com.practicum.playlist_maker.search.data.api.PdfServerApiService
import com.practicum.playlist_maker.search.ui.view_model.SearchViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object SearchViewModelModule {
    
    @Provides
    @ViewModelScoped
    fun provideSearchViewModel(
        pdfRepository: PDFRepository,
        pdfServerApiService: PdfServerApiService
    ): SearchViewModel {
        return SearchViewModel(pdfRepository, pdfServerApiService)
    }
}