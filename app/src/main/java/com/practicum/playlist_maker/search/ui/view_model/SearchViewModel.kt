package com.practicum.playlist_maker.search.ui.view_model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.practicum.playlist_maker.pdfreader.repository.PDFRepository
import com.practicum.playlist_maker.pdfreader.tools.OperationsStateHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pdfRepository: PDFRepository
) : ViewModel() {

    val pdfListResponse = OperationsStateHandler(viewModelScope)

    fun getAllPdfs() {
        pdfListResponse.load {
            pdfRepository.getAllPdfs()
        }
    }
}
