package com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.source

import android.content.Context
import android.os.ParcelFileDescriptor
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class FileSource(private val file: File, private val startPageIndex: Int = 0, private val defaultPageIndexToLoad: Int = 0) :
    DocumentSource {

    private var bytes: ByteArray = FileInputStream(file).use {
        it.readBytes()
    }

    @Throws(IOException::class)
    override fun createDocument(
        context: Context?,
        core: PdfiumCore?,
        password: String?,
    ): PdfDocument? {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return core!!.newDocument(pfd, password)
    }

    override fun getBytes(): ByteArray {
        return bytes
    }

    override fun getFile(): File {
        return file
    }

    override fun getStartPageIndex(): Int {
        return startPageIndex
    }

    override fun defaultPageIndexToLoad(): Int {
        return defaultPageIndexToLoad
    }
}
