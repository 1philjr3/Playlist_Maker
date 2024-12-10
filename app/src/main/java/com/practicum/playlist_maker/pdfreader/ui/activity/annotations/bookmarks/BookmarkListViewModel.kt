package com.practicum.playlist_maker.pdfreader.ui.activity.annotations.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.practicum.playlist_maker.pdfreader.repository.PDFRepository
import com.practicum.playlist_maker.pdfreader.tools.OperationsStateHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BookmarkListViewModel @Inject constructor(
    private val pdfRepository: PDFRepository
) : ViewModel() {
    val deleteBookmarksResponse = OperationsStateHandler(viewModelScope)

    fun deleteBookmarks(ids: List<Long>) {
        deleteBookmarksResponse.load {
            pdfRepository.deleteBookmarks(ids)
        }
    }
}