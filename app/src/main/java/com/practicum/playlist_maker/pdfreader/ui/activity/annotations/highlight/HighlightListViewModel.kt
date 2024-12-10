package com.practicum.playlist_maker.pdfreader.ui.activity.annotations.highlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.practicum.playlist_maker.pdfreader.repository.PDFRepository
import com.practicum.playlist_maker.pdfreader.tools.OperationsStateHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HighlightListViewModel @Inject constructor(
    private val pdfRepository: PDFRepository
) : ViewModel() {
    val deleteHighlightResponse = OperationsStateHandler(viewModelScope)

    fun deleteHighlights(ids: List<Long>) {
        deleteHighlightResponse.load {
            pdfRepository.deleteHighlight(ids)
        }
    }
}