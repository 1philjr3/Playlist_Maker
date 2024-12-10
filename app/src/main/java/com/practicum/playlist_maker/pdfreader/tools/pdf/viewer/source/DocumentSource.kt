package com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.source

import android.content.Context
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.File
import java.io.IOException

interface DocumentSource {
    @Throws(IOException::class)
    fun createDocument(context: Context?, core: PdfiumCore?, password: String?): PdfDocument?

    fun getBytes(): ByteArray

    /**get file of pdf, note that file will only get for FileSource*/
    fun getFile(): File?

    fun getStartPageIndex(): Int

    fun defaultPageIndexToLoad(): Int
}
