package com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model

data class PageModel(
    val width: Float,
    val height: Float,
    val coordinates: ArrayList<PdfLine>,
) {
    var relativeSizeCalculated = false
}
