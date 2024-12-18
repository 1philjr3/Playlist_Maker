package com.practicum.playlist_maker.pdfreader.repository

import com.practicum.playlist_maker.pdfreader.state.ResponseState
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.Coordinates

interface PDFRepository {

    // PDF
    suspend fun addNewPdf(
        filePath: String,
        title: String,
        about: String?,
        tagId: Long?
    ) : ResponseState

    suspend fun getAllPdfs(): ResponseState

    suspend fun deletePdf(pdfId: Long): ResponseState

    // TAG
    suspend fun addTag(title: String, color: String) : ResponseState
    suspend fun getAllTags() : ResponseState
    suspend fun removeTagById(tagId: Long) : ResponseState

    // COMMENTS
    suspend fun addComment(
        pdfId: Long,
        snippet: String,
        text: String,
        page: Int,
        coordinates: Coordinates
    ): ResponseState

    suspend fun getAllComments(pdfId: Long): ResponseState
    suspend fun deleteComments(commentIds: List<Long>): ResponseState
    suspend fun updateComment(commentId: Long, newText: String): ResponseState

    // HIGHLIGHTS
    suspend fun addHighlight(
        pdfId: Long,
        snippet: String,
        color: String,
        page: Int,
        coordinates: Coordinates
    ): ResponseState

    suspend fun getAllHighlight(pdfId: Long): ResponseState
    suspend fun deleteHighlight(highlightIds: List<Long>): ResponseState


    // Bookmark
    suspend fun addBookmark(
        pdfId: Long,
        page: Int,
    ): ResponseState

    suspend fun getAllBookmark(pdfId: Long): ResponseState
    suspend fun deleteBookmarks(bookmarkIds: List<Long>): ResponseState
    suspend fun deleteBookmarkWithPageAndPdfId(page: Int, pdfId: Long): ResponseState


    // ANNOTATIONS
    suspend fun getAllAnnotations(pdfId: Long): ResponseState

}