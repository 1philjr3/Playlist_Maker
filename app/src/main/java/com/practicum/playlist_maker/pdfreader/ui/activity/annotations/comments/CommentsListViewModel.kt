package com.practicum.playlist_maker.pdfreader.ui.activity.annotations.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.practicum.playlist_maker.pdfreader.repository.PDFRepository
import com.practicum.playlist_maker.pdfreader.tools.OperationsStateHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CommentsListViewModel @Inject constructor(
    private val pdfRepository: PDFRepository
) : ViewModel() {
    val deleteCommentResponse = OperationsStateHandler(viewModelScope)

    fun deleteComments(ids: List<Long>) {
        deleteCommentResponse.load {
            pdfRepository.deleteComments(ids)
        }
    }
}