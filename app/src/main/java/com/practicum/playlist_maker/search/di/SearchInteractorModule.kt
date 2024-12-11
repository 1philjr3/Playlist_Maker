package com.practicum.playlist_maker.search.di

import com.practicum.playlist_maker.search.domain.api.PDFInteractor
import com.practicum.playlist_maker.search.domain.impl.PDFInteractorImpl
import org.koin.dsl.module

val searchInteractorModule = module {
    single<PDFInteractor> { PDFInteractorImpl(get()) }
}