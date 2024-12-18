package com.practicum.playlist_maker.pdfreader.room

import com.practicum.playlist_maker.pdfreader.room.entity.BookmarkEntity
import com.practicum.playlist_maker.pdfreader.room.entity.CommentEntity
import com.practicum.playlist_maker.pdfreader.room.entity.HighlightEntity
import com.practicum.playlist_maker.pdfreader.room.entity.PdfNoteEntity
import com.practicum.playlist_maker.pdfreader.room.entity.PdfTagEntity

object Queries {
    // PDF
    const val GET_ALL_PDF_NOTES = "SELECT * FROM ${PdfNoteEntity.TABLE_NAME}"
    const val GET_PDF_NOTES_BY_ID = "SELECT * FROM ${PdfNoteEntity.TABLE_NAME} WHERE ${PdfNoteEntity.FIELD_ID} = :id"
    const val DELETE_PDF_NOTES_BY_ID = "DELETE FROM ${PdfNoteEntity.TABLE_NAME} WHERE ${PdfNoteEntity.FIELD_ID} = :id"

    // TAG
    const val GET_TAG_BY_TAG_ID =
        "SELECT * FROM ${PdfTagEntity.TABLE_NAME} WHERE ${PdfTagEntity.FIELD_ID} = :tagId"
    const val GET_ALL_TAGS =
        "SELECT * FROM ${PdfTagEntity.TABLE_NAME}"

    const val REMOVE_TAG_BY_ID =
        "DELETE FROM ${PdfTagEntity.TABLE_NAME} WHERE ${PdfTagEntity.FIELD_ID} = :tagId"

    // COMMENT
    const val GET_ALL_COMMENTS_WITH_PDF_ID =
        "SELECT * FROM ${CommentEntity.TABLE_NAME} WHERE ${CommentEntity.FIELD_PDF_ID}=:pdfId"
    const val DELETE_ALL_COMMENTS_WITH_PDF_ID =
        "DELETE FROM ${CommentEntity.TABLE_NAME} WHERE ${CommentEntity.FIELD_PDF_ID}=:pdfId"
    const val GET_COMMENT_WITH_ID =
        "SELECT * FROM ${CommentEntity.TABLE_NAME} WHERE ${CommentEntity.FIELD_ID}=:id"
    const val DELETE_COMMENTS_WITH_IDS =
        "DELETE FROM ${CommentEntity.TABLE_NAME} WHERE ${CommentEntity.FIELD_ID} IN (:ids)"
    const val UPDATE_COMMENT_WITH_ID =
        "UPDATE ${CommentEntity.TABLE_NAME} SET ${CommentEntity.FIELD_TEXT} = :newText, ${CommentEntity.FIELD_UPDATED_AT} = :updatedAt WHERE ${CommentEntity.FIELD_ID} = :id"

    // HIGHLIGHT
    const val GET_ALL_HIGHLIGHT_WITH_PDF_ID =
        "SELECT * FROM ${HighlightEntity.TABLE_NAME} WHERE ${HighlightEntity.FIELD_PDF_ID}=:pdfId"
    const val DELETE_ALL_HIGHLIGHT_WITH_PDF_ID =
        "DELETE FROM ${HighlightEntity.TABLE_NAME} WHERE ${HighlightEntity.FIELD_PDF_ID}=:pdfId"
    const val DELETE_HIGHLIGHTS_WITH_IDS =
        "DELETE FROM ${HighlightEntity.TABLE_NAME} WHERE ${HighlightEntity.FIELD_ID} IN (:ids)"

    // BOOKMARK
    const val GET_ALL_BOOKMARK_WITH_PDF_ID =
        "SELECT * FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.FIELD_PDF_ID}=:pdfId"
    const val DELETE_ALL_BOOKMARK_WITH_PDF_ID =
        "DELETE FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.FIELD_PDF_ID}=:pdfId"
    const val DELETE_BOOKMARK_WITH_IDS =
        "DELETE FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.FIELD_ID} IN (:ids)"
    const val GET_BOOKMARK_WITH_PAGE_AND_PDF_ID =
        "SELECT * FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.FIELD_PAGE}=:page AND ${BookmarkEntity.FIELD_PDF_ID}=:pdfId"


}