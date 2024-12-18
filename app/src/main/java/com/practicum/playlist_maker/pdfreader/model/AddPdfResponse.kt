package com.practicum.playlist_maker.pdfreader.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


data class PdfNotesResponse(
    val notes : List<PdfNoteListModel>
)

@Parcelize
data class PdfNoteListModel(
    val id: Long,
    val title: String,
    val tag: TagModel?,
    val about: String?,
    val filePath: String,
    val updatedTime: Long
): Parcelable

@Parcelize
data class TagModel(
    val id: Long,
    val title: String,
    val colorCode: String?
): Parcelable