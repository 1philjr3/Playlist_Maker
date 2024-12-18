package com.practicum.playlist_maker.pdfreader.repository

import com.practicum.playlist_maker.pdfreader.data.ValidationErrorException
import com.practicum.playlist_maker.pdfreader.model.AnnotationListResponse
import com.practicum.playlist_maker.pdfreader.model.DeleteAnnotationResponse
import com.practicum.playlist_maker.pdfreader.model.PdfNoteListModel
import com.practicum.playlist_maker.pdfreader.model.PdfNotesResponse
import com.practicum.playlist_maker.pdfreader.model.RemoveTagResponse
import com.practicum.playlist_maker.pdfreader.model.StatusMessageResponse
import com.practicum.playlist_maker.pdfreader.model.TagModel
import com.practicum.playlist_maker.pdfreader.room.Dao
import com.practicum.playlist_maker.pdfreader.room.entity.BookmarkEntity
import com.practicum.playlist_maker.pdfreader.room.entity.CommentEntity
import com.practicum.playlist_maker.pdfreader.room.entity.HighlightEntity
import com.practicum.playlist_maker.pdfreader.room.entity.PdfNoteEntity
import com.practicum.playlist_maker.pdfreader.room.entity.PdfTagEntity
import com.practicum.playlist_maker.pdfreader.state.ResponseState
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.BookmarkModel
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.CommentModel
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.Coordinates
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.HighlightModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject


class PdfRepositoryImpl @Inject constructor(
    private val dao: Dao,
) : PDFRepository {

    override suspend fun addNewPdf(
        filePath: String,
        title: String,
        about: String?,
        tagId: Long?
    ): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val pdfEntry = PdfNoteEntity(
                    null,
                    title,
                    filePath,
                    about,
                    tagId,
                    System.currentTimeMillis()
                )

                val id = dao.addPdfNote(pdfEntry)
                if (id != -1L){
                    val tagModel = tagId?.let { dao.getTagById(it) }?.let {
                        TagModel(it.id ?: -1, it.title, it.colorCode)
                    }
                    val model = PdfNoteListModel(
                        id,
                        title,
                        tagModel,
                        about,
                        filePath,
                        pdfEntry.updateAt
                    )
                    return@withContext ResponseState.Success<PdfNoteListModel>(model)
                }
                throw java.lang.Exception("Failed to add pdf")
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun getAllPdfs(): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
               val notes = dao.getAllPdfNotes()
                val pdfNotes = notes.map {
                    val tagModel = it.tagId?.let {tagId-> dao.getTagById(tagId) }?.let { tag->
                        TagModel(tag.id ?: -1, tag.title, tag.colorCode)
                    }
                    PdfNoteListModel(
                        it.id?:-1,
                        it.title,
                        tagModel,
                        it.about,
                        it.filePath,
                        it.updateAt
                    )
                }
                return@withContext ResponseState.Success<PdfNotesResponse>(PdfNotesResponse(pdfNotes))

            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun deletePdf(pdfId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val pdf = dao.getPdfById(pdfId)?: throw Exception("Pdf Already deleted")
                dao.deleteAllCommentsByPdfId(pdfId)
                dao.deleteAllHighlightsByPdfId(pdfId)
                dao.deleteAllBookmarksWithPdfId(pdfId)
                val pdfFile = File(pdf.filePath)
                pdfFile.delete()
                dao.deletePdfById(pdfId)
                return@withContext ResponseState.Success<StatusMessageResponse>(
                    StatusMessageResponse("Pdf deleted successfully")
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun addTag(title: String, color: String): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val allTags = dao.getAllTags()
                val alreadyExistName = allTags.indexOfFirst { it.title == title } != -1
                if (alreadyExistName) {
                    throw ValidationErrorException(1,"There is already a Tag exist with the same name")
                }

                val tagEntity = PdfTagEntity(null,title,color)
                val id = dao.addPdfTag(tagEntity)
                return@withContext ResponseState.Success<TagModel>(TagModel(id,title,color))
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }
    override suspend fun getAllTags(): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val tags = dao.getAllTags().map {
                    TagModel(it.id?:-1,it.title,it.colorCode)
                }
                return@withContext ResponseState.Success<List<TagModel>>(tags)
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }
    override suspend fun removeTagById(tagId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                dao.removeTagById(tagId)
                return@withContext ResponseState.Success<RemoveTagResponse>(RemoveTagResponse(tagId))
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun addComment(
        pdfId: Long,
        snippet: String,
        text: String,
        page: Int,
        coordinates: Coordinates
    ): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val commentEntity = CommentEntity(
                    null,
                    pdfId,
                    snippet,
                    text,
                    page,
                    System.currentTimeMillis(),
                    coordinates
                )
                val id = dao.insertComment(commentEntity)
                return@withContext ResponseState.Success<CommentModel>(
                    CommentModel(
                    id,
                    snippet,
                    text,
                    page,
                    commentEntity.updatedAt,
                    coordinates
                )
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun getAllComments(pdfId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val comments = dao.getCommentsOfPdf(pdfId).map {
                    CommentModel(
                        it.id?:-1L,
                        it.snippet,
                        it.text,
                        it.page,
                        it.updatedAt,
                        it.coordinates
                    )
                }
                return@withContext ResponseState.Success<List<CommentModel>>(comments)
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun deleteComments(commentIds: List<Long>): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                dao.deleteCommentsWithIds(commentIds)
                return@withContext ResponseState.Success<DeleteAnnotationResponse>(
                    DeleteAnnotationResponse(commentIds)
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }
    override suspend fun updateComment(commentId: Long, newText: String): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val comment = dao.getCommentWithId(commentId) ?: throw Exception("Comment Not found")
                val updatedAt = System.currentTimeMillis()
                dao.updateComment(commentId,newText, updatedAt)
                return@withContext ResponseState.Success<CommentModel>(
                    CommentModel(
                    comment.id?:-1,
                    comment.snippet,
                    newText,
                    comment.page,
                    updatedAt,
                    comment.coordinates
                )
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun addHighlight(
        pdfId: Long,
        snippet: String,
        color: String,
        page: Int,
        coordinates: Coordinates
    ): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val highlightEntity = HighlightEntity(
                    null,
                    pdfId,
                    snippet,
                    color,
                    page,
                    System.currentTimeMillis(),
                    coordinates
                )
                val id = dao.insertHighlight(highlightEntity)
                return@withContext ResponseState.Success<HighlightModel>(
                    HighlightModel(
                        id,
                        snippet,
                        color,
                        page,
                        highlightEntity.updatedAt,
                        coordinates
                    )
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun getAllHighlight(pdfId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val highlights = dao.getHighlightsOfPdf(pdfId).map {
                    HighlightModel(
                        it.id?:-1L,
                        it.snippet,
                        it.color,
                        it.page,
                        it.updatedAt,
                        it.coordinates
                    )
                }
                return@withContext ResponseState.Success<List<HighlightModel>>(highlights)
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun deleteHighlight(highlightIds: List<Long>): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                dao.deleteHighlightsWithIds(highlightIds)
                return@withContext ResponseState.Success<DeleteAnnotationResponse>(
                    DeleteAnnotationResponse(highlightIds)
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun addBookmark(
        pdfId: Long,
        page: Int
    ): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val bookmarkEntity = BookmarkEntity(
                    null,
                    pdfId,
                    page,
                    System.currentTimeMillis()
                )
                val id = dao.insertBookmark(bookmarkEntity)
                return@withContext ResponseState.Success<BookmarkModel>(
                    BookmarkModel(
                        id,
                        page,
                        bookmarkEntity.updatedAt,
                    )
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun getAllBookmark(pdfId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val bookmarks = dao.getBookmarksOfPdf(pdfId).map {
                    BookmarkModel(
                        it.id?:-1L,
                        it.page,
                        it.updatedAt
                    )
                }
                return@withContext ResponseState.Success<List<BookmarkModel>>(bookmarks)
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun deleteBookmarks(bookmarkIds: List<Long>): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                dao.deleteBookmarksWithIds(bookmarkIds)
                return@withContext ResponseState.Success<Nothing>(null)
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun deleteBookmarkWithPageAndPdfId(page: Int, pdfId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {
                val bookmarksIds = dao.getBookmarksWithPageAndPdfId(page, pdfId).map { it.id?:-1 }
                dao.deleteBookmarksWithIds(bookmarksIds)
                return@withContext ResponseState.Success<DeleteAnnotationResponse>(
                    DeleteAnnotationResponse(bookmarksIds)
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    override suspend fun getAllAnnotations(pdfId: Long): ResponseState {
        return withContext(Dispatchers.IO) {
            try {

                val comments = dao.getCommentsOfPdf(pdfId).map {
                    CommentModel(
                        it.id ?: -1L,
                        it.snippet,
                        it.text,
                        it.page,
                        it.updatedAt,
                        it.coordinates
                    )
                }

                val highlights = dao.getHighlightsOfPdf(pdfId).map {
                    HighlightModel(
                        it.id ?: -1L,
                        it.snippet,
                        it.color,
                        it.page,
                        it.updatedAt,
                        it.coordinates
                    )
                }

                val bookmarks = dao.getBookmarksOfPdf(pdfId).map {
                    BookmarkModel(
                        it.id ?: -1L,
                        it.page,
                        it.updatedAt
                    )
                }

                return@withContext ResponseState.Success<AnnotationListResponse>(
                    AnnotationListResponse(
                        ArrayList(comments),
                        ArrayList(highlights),
                        ArrayList(bookmarks),
                    )
                )
            } catch (e: Exception) {
                return@withContext ResponseState.Failed(e.message ?: "Something went wrong")
            }
        }
    }
}