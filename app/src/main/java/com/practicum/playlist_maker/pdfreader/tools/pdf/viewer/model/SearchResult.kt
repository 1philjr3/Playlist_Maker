package com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model

data class SearchResult(
    val pageIndex: Int,
    val text: String,
    val bounds: android.graphics.RectF
) 