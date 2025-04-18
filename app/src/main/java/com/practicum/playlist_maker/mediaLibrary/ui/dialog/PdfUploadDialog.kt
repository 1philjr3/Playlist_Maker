package com.practicum.playlist_maker.mediaLibrary.ui.dialog

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputLayout
import com.practicum.playlist_maker.R

/**
 * Диалог для загрузки PDF файла с опцией уведомления
 */
class PdfUploadDialog : DialogFragment() {

    private lateinit var fileName: String
    private lateinit var fileUri: Uri
    private var fileSize: Long = 0

    private var listener: PdfUploadListener? = null

    interface PdfUploadListener {
        fun onUploadWithNotification(uri: Uri, fileName: String, fileSize: Long, description: String)
        fun onUploadWithoutNotification(uri: Uri, fileName: String, fileSize: Long)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_pdf_upload, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvFileName = view.findViewById<TextView>(R.id.tvFileName)
        val tvFileSize = view.findViewById<TextView>(R.id.tvFileSize)
        val chkNotify = view.findViewById<MaterialCheckBox>(R.id.chkNotify)
        val tilDescription = view.findViewById<TextInputLayout>(R.id.tilDescription)
        val etDescription = view.findViewById<EditText>(R.id.etDescription)
        val btnUpload = view.findViewById<Button>(R.id.btnUpload)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        // Устанавливаем имя файла и размер
        tvFileName.text = fileName
        tvFileSize.text = formatFileSize(fileSize)

        // Обработчик состояния чекбокса
        chkNotify.setOnCheckedChangeListener { _, isChecked ->
            tilDescription.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Изначально скрываем поле описания
        tilDescription.visibility = View.GONE

        // Обработчик кнопки загрузки
        btnUpload.setOnClickListener {
            if (chkNotify.isChecked) {
                val description = etDescription.text.toString()
                listener?.onUploadWithNotification(fileUri, fileName, fileSize, description)
            } else {
                listener?.onUploadWithoutNotification(fileUri, fileName, fileSize)
            }
            dismiss()
        }

        // Обработчик кнопки отмены
        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size Б"
            size < 1024 * 1024 -> "${size / 1024} КБ"
            else -> "${size / (1024 * 1024)} МБ"
        }
    }

    companion object {
        fun newInstance(
            uri: Uri,
            fileName: String,
            fileSize: Long,
            listener: PdfUploadListener
        ): PdfUploadDialog {
            return PdfUploadDialog().apply {
                this.fileUri = uri
                this.fileName = fileName
                this.fileSize = fileSize
                this.listener = listener
            }
        }
    }
} 