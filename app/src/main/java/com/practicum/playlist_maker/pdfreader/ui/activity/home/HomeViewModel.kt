package com.practicum.playlist_maker.pdfreader.ui.activity.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.practicum.playlist_maker.pdfreader.repository.PDFRepository
import com.practicum.playlist_maker.pdfreader.tools.OperationsStateHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pdfRepository: PDFRepository
) : ViewModel(){


    val pdfListResponse = OperationsStateHandler(viewModelScope)

    fun getAllPdfs() {
        pdfListResponse.load {
            pdfRepository.getAllPdfs()
        }
    }
}