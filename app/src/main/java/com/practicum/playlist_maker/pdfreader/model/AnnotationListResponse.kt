package com.practicum.playlist_maker.pdfreader.model

import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.BookmarkModel
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.CommentModel
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.HighlightModel

data class AnnotationListResponse(
    val comments: ArrayList<CommentModel> = arrayListOf(),
    val highlights: ArrayList<HighlightModel> = arrayListOf(),
    val bookmarks: ArrayList<BookmarkModel> = arrayListOf()
)
