package com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.source

import android.content.Context
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.File
import java.io.IOException

class ByteArraySource(private val data: ByteArray) : DocumentSource {
    @Throws(IOException::class)
    override fun createDocument(
        context: Context?,
        core: PdfiumCore?,
        password: String?,
    ): PdfDocument? {
        return core?.newDocument(data, password)
    }

    override fun getBytes(): ByteArray {
        return data
    }

    override fun getFile(): File? {
        return null
    }

    override fun getStartPageIndex(): Int {
        return 0
    }

    override fun defaultPageIndexToLoad(): Int {
        return 0
    }
}
