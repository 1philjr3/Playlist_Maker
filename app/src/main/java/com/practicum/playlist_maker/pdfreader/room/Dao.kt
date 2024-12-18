package com.practicum.playlist_maker.pdfreader.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.practicum.playlist_maker.pdfreader.room.entity.BookmarkEntity
import com.practicum.playlist_maker.pdfreader.room.entity.CommentEntity
import com.practicum.playlist_maker.pdfreader.room.entity.HighlightEntity
import com.practicum.playlist_maker.pdfreader.room.entity.PdfNoteEntity
import com.practicum.playlist_maker.pdfreader.room.entity.PdfTagEntity

@Dao
interface Dao {

    // PDF
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPdfNote(note: PdfNoteEntity) : Long
    @Query(Queries.GET_ALL_PDF_NOTES)
    suspend fun getAllPdfNotes(): List<PdfNoteEntity>
    @Query(Queries.GET_PDF_NOTES_BY_ID)
    suspend fun getPdfById(id: Long): PdfNoteEntity?
    @Query(Queries.DELETE_PDF_NOTES_BY_ID)
    suspend fun deletePdfById(id: Long)

    // TAG
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPdfTag(tag: PdfTagEntity): Long
    @Query(Queries.GET_TAG_BY_TAG_ID)
    suspend fun getTagById(tagId:Long): PdfTagEntity?
    @Query(Queries.GET_ALL_TAGS)
    suspend fun getAllTags(): List<PdfTagEntity>
    @Query(Queries.REMOVE_TAG_BY_ID)
    suspend fun removeTagById(tagId: Long)

    // COMMENTS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity): Long
    @Query(Queries.GET_ALL_COMMENTS_WITH_PDF_ID)
    suspend fun getCommentsOfPdf(pdfId: Long): List<CommentEntity>
    @Query(Queries.DELETE_ALL_COMMENTS_WITH_PDF_ID)
    suspend fun deleteAllCommentsByPdfId(pdfId: Long)
    @Query(Queries.GET_COMMENT_WITH_ID)
    suspend fun getCommentWithId(id: Long): CommentEntity?
    @Query(Queries.UPDATE_COMMENT_WITH_ID)
    suspend fun updateComment(id: Long, newText: String, updatedAt: Long)
    @Query(Queries.DELETE_COMMENTS_WITH_IDS)
    suspend fun deleteCommentsWithIds(ids: List<Long>)

    // HIGHLIGHTS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(comment: HighlightEntity): Long
    @Query(Queries.GET_ALL_HIGHLIGHT_WITH_PDF_ID)
    suspend fun getHighlightsOfPdf(pdfId: Long): List<HighlightEntity>
    @Query(Queries.DELETE_ALL_HIGHLIGHT_WITH_PDF_ID)
    suspend fun deleteAllHighlightsByPdfId(pdfId: Long)
    @Query(Queries.DELETE_HIGHLIGHTS_WITH_IDS)
    suspend fun deleteHighlightsWithIds(ids: List<Long>)

    // BOOKMARKS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(comment: BookmarkEntity): Long
    @Query(Queries.GET_ALL_BOOKMARK_WITH_PDF_ID)
    suspend fun getBookmarksOfPdf(pdfId: Long): List<BookmarkEntity>
    @Query(Queries.DELETE_ALL_BOOKMARK_WITH_PDF_ID)
    suspend fun deleteAllBookmarksWithPdfId(pdfId: Long)
    @Query(Queries.DELETE_BOOKMARK_WITH_IDS)
    suspend fun deleteBookmarksWithIds(ids: List<Long>)
    @Query(Queries.GET_BOOKMARK_WITH_PAGE_AND_PDF_ID)
    suspend fun getBookmarksWithPageAndPdfId(page: Int, pdfId: Long): List<BookmarkEntity>


}