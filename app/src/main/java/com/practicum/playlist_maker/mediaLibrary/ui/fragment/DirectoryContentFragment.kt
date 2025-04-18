package com.practicum.playlist_maker.mediaLibrary.ui.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.mediaLibrary.data.model.Directory
import com.practicum.playlist_maker.mediaLibrary.data.model.PdfFile
import com.practicum.playlist_maker.mediaLibrary.ui.adapter.DirectoryContentAdapter
import com.practicum.playlist_maker.mediaLibrary.ui.adapter.DirectoryContentItem
import com.practicum.playlist_maker.pdfreader.ui.activity.reader.PdfReaderActivity
import com.google.android.material.snackbar.Snackbar
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.provider.OpenableColumns
import android.util.TypedValue
import androidx.appcompat.widget.PopupMenu
import android.content.ContentResolver
import android.app.DownloadManager
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.practicum.playlist_maker.mediaLibrary.data.NotificationService
import com.practicum.playlist_maker.mediaLibrary.ui.dialog.PdfUploadDialog
import com.practicum.playlist_maker.network.ServerUrlUpdater
import android.util.Log

class DirectoryContentFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddDirectory: FloatingActionButton
    private lateinit var btnBack: ImageButton
    private lateinit var tvPath: TextView
    private lateinit var tvEmptyDirectory: TextView
    private lateinit var tvServerError: TextView
    private lateinit var directoryAdapter: DirectoryContentAdapter
    private var currentDirectory: Directory? = null
    private var pathHistory: MutableList<Directory> = mutableListOf()
    private val client = OkHttpClient()
    private var selectedImageUri: Uri? = null
    private var dialogView: View? = null
    private var actionMode: ActionMode? = null
    private var selectedItemsCount = 0
    private val STORAGE_PERMISSION_CODE = 100

    private val pickPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handlePdfSelection(it) }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val contentResolver = requireContext().contentResolver
                val type = contentResolver.getType(uri)
                
                if (type?.startsWith("image/") == true) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val fileSize = inputStream.available()
                        val maxSize = 5 * 1024 * 1024 // 5MB
                        
                        if (fileSize <= maxSize) {
                            selectedImageUri = uri
                            dialogView?.findViewById<ImageView>(R.id.ivSelectedImage)?.let { imageView ->
                                imageView.visibility = View.VISIBLE
                                Glide.with(this)
                                    .load(uri)
                                    .override(200, 200)
                                    .centerInside()
                                    .into(imageView)
                            }
                            dialogView?.findViewById<Button>(R.id.btnSelectImage)?.text = "Изменить изображение"
                        } else {
                            Snackbar.make(requireView(), "Размер файла не должен превышать 5MB", Snackbar.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Snackbar.make(requireView(), "Пожалуйста, выберите изображение", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Snackbar.make(requireView(), "Ошибка при обработке изображения: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_selection, menu)
            val typedValue = TypedValue()
            requireContext().theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            mode.title = "$selectedItemsCount выбрано"
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.title = "$selectedItemsCount выбрано"
            
            // Деактивировать кнопку редактирования, если выбрано больше одного элемента
            val renameMenuItem = menu.findItem(R.id.action_rename)
            renameMenuItem?.isEnabled = selectedItemsCount == 1
            
            // Визуально показать, что кнопка неактивна
            if (selectedItemsCount > 1) {
                renameMenuItem?.icon?.alpha = 130 // полупрозрачный 
            } else {
                renameMenuItem?.icon?.alpha = 255 // полностью непрозрачный
            }
            
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_delete -> {
                    showDeleteSelectedItemsDialog()
                    true
                }
                R.id.action_rename -> {
                    val selectedItems = directoryAdapter.getSelectedItems()
                    if (selectedItems.size == 1) {
                        when (val selectedItem = selectedItems.first()) {
                            is DirectoryContentItem.DirectoryItem -> {
                                println("DEBUG: Выбрана директория для переименования: ${selectedItem.directory}")
                                showRenameDialog(selectedItem.directory.name, true, selectedItem.directory.id)
                            }
                            is DirectoryContentItem.PdfItem -> {
                                println("DEBUG: Выбран PDF файл для переименования: ${selectedItem.pdfFile}")
                                showRenameDialog(selectedItem.pdfFile.name, false, selectedItem.pdfFile.id)
                            }
                        }
                    } else {
                        Snackbar.make(requireView(), "Выберите только один элемент для переименования", Snackbar.LENGTH_LONG).show()
                    }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            directoryAdapter.clearSelection()
            actionMode = null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_directory_content, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rvDirectories)
        fabAddDirectory = view.findViewById(R.id.fabAddDirectory)
        btnBack = view.findViewById(R.id.btnBack)
        tvPath = view.findViewById(R.id.tvPath)
        tvEmptyDirectory = view.findViewById(R.id.tvEmptyDirectory)
        tvServerError = view.findViewById(R.id.tvServerError)

        setupRecyclerView()
        setupFab()
        setupBackButton()
        loadDirectoryContent()
    }

    private fun setupRecyclerView() {
        directoryAdapter = DirectoryContentAdapter(
            onDirectoryClick = { directory ->
                if (!directoryAdapter.isInSelectionMode()) {
                    navigateToDirectory(directory)
                }
            },
            onDirectoryLongClick = { directory ->
                if (!directoryAdapter.isInSelectionMode()) {
                    showItemContextMenu(
                        view = recyclerView.findViewHolderForItemId(directory.id.hashCode().toLong())?.itemView,
                        item = DirectoryContentItem.DirectoryItem(directory)
                    )
                }
                return@DirectoryContentAdapter
            },
            onPdfClick = { pdfFile ->
                if (!directoryAdapter.isInSelectionMode()) {
                    openPdfFile(pdfFile)
                }
            },
            onPdfLongClick = { pdfFile ->
                if (!directoryAdapter.isInSelectionMode()) {
                    showItemContextMenu(
                        view = recyclerView.findViewHolderForItemId(pdfFile.id.hashCode().toLong())?.itemView,
                        item = DirectoryContentItem.PdfItem(pdfFile)
                    )
                }
                return@DirectoryContentAdapter
            },
            onPdfDownloadClick = { pdfFile ->
                downloadPdf(pdfFile)
            },
            onSelectionChanged = { count ->
                selectedItemsCount = count
                if (count > 0) {
                    if (actionMode == null) {
                        actionMode = activity?.startActionMode(actionModeCallback)
                    }
                    actionMode?.title = "$count выбрано"
                } else {
                    actionMode?.finish()
                }
            }
        )
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = directoryAdapter
        }
    }

    private fun setupFab() {
        fabAddDirectory.setOnClickListener { view ->
            val popup = PopupMenu(requireContext(), view)
            popup.menuInflater.inflate(R.menu.menu_fab, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_create_directory -> {
                        showAddDirectoryDialog()
                        true
                    }
                    R.id.action_add_pdf -> {
                        pickPdf.launch("application/pdf")
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun handlePdfSelection(uri: Uri) {
        try {
            val contentResolver = requireContext().contentResolver
            val cursor = contentResolver.query(uri, null, null, null, null)
            
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                    
                    val name = c.getString(nameIndex)
                    val size = c.getLong(sizeIndex)

                    if (size > 10 * 1024 * 1024) { // 10MB limit
                        Snackbar.make(requireView(), "Файл слишком большой. Максимальный размер 10MB", Snackbar.LENGTH_LONG).show()
                        return
                    }

                    // Вместо прямой загрузки показываем диалог с опцией уведомления
                    showPdfUploadDialog(uri, name, size)
                }
            }
        } catch (e: Exception) {
            Snackbar.make(requireView(), "Ошибка при обработке файла", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showPdfUploadDialog(uri: Uri, fileName: String, fileSize: Long) {
        val dialogFragment = PdfUploadDialog.newInstance(
            uri = uri,
            fileName = fileName,
            fileSize = fileSize,
            listener = object : PdfUploadDialog.PdfUploadListener {
                override fun onUploadWithNotification(uri: Uri, fileName: String, fileSize: Long, description: String) {
                    uploadPdfFileWithNotification(uri, fileName, fileSize, description)
                }

                override fun onUploadWithoutNotification(uri: Uri, fileName: String, fileSize: Long) {
                    uploadPdfFile(uri, fileName, fileSize)
                }
            }
        )
        dialogFragment.show(childFragmentManager, "pdfUploadDialog")
    }

    private fun uploadPdfFileWithNotification(uri: Uri, fileName: String, fileSize: Long, description: String) {
        try {
            val tempFile = File(requireContext().cacheDir, "temp_pdf")
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "pdf",
                    fileName,
                    tempFile.asRequestBody("application/pdf".toMediaType())
                )
                .addFormDataPart("name", fileName)
                .addFormDataPart("size", fileSize.toString())
                .addFormDataPart("directoryId", currentDirectory?.id ?: "root")
                .addFormDataPart("userId", getUserIdFromPrefs())
                .addFormDataPart("userName", getUserNameFromPrefs())
                .addFormDataPart("sendNotification", "true")
                .addFormDataPart("notificationDescription", description)
                .build()

            val request = Request.Builder()
                .url("${getServerUrl()}/directories/${currentDirectory?.id ?: "root"}/files")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                @Throws(IOException::class)
                override fun onFailure(call: Call, e: IOException) {
                    activity?.runOnUiThread {
                        Snackbar.make(requireView(), "Ошибка при загрузке файла: ${e.message}", Snackbar.LENGTH_LONG).show()
                        e.printStackTrace()
                    }
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    tempFile.delete()
                    
                    if (response.isSuccessful) {
                        // Получаем ID загруженного файла для отправки уведомления
                        val responseJson = JSONObject(response.body?.string() ?: "{}")
                        val fileId = responseJson.optString("id", "")
                        
                        // Если файл успешно загружен и получен ID, отправляем уведомление
                        if (fileId.isNotEmpty()) {
                            val notificationService = NotificationService()
                            notificationService.sendFileNotification(
                                fileId = fileId,
                                fileName = fileName,
                                description = description,
                                userId = getUserIdFromPrefs(),
                                userName = getUserNameFromPrefs()
                            )
                            
                            activity?.runOnUiThread {
                                loadDirectoryContent()
                                Snackbar.make(requireView(), "Файл успешно загружен и уведомление отправлено", Snackbar.LENGTH_LONG).show()
                            }
                        } else {
                            activity?.runOnUiThread {
                                loadDirectoryContent()
                                Snackbar.make(requireView(), "Файл загружен, но не удалось получить ID для отправки уведомления", Snackbar.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        val errorBody = response.body?.string()
                        activity?.runOnUiThread {
                            Snackbar.make(requireView(), "Ошибка при загрузке файла: ${response.code} - $errorBody", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(requireView(), "Ошибка при обработке файла: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun uploadPdfFile(uri: Uri, fileName: String, fileSize: Long) {
        try {
            val tempFile = File(requireContext().cacheDir, "temp_pdf")
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "pdf",
                    fileName,
                    tempFile.asRequestBody("application/pdf".toMediaType())
                )
                .addFormDataPart("name", fileName)
                .addFormDataPart("size", fileSize.toString())
                .addFormDataPart("directoryId", currentDirectory?.id ?: "root")
                .addFormDataPart("userId", getUserIdFromPrefs())
                .addFormDataPart("userName", getUserNameFromPrefs())
                .build()

            val request = Request.Builder()
                .url("${getServerUrl()}/directories/${currentDirectory?.id ?: "root"}/files")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                @Throws(IOException::class)
                override fun onFailure(call: Call, e: IOException) {
                    activity?.runOnUiThread {
                        Snackbar.make(requireView(), "Ошибка при загрузке файла: ${e.message}", Snackbar.LENGTH_LONG).show()
                        e.printStackTrace()
                    }
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    tempFile.delete()
                    if (response.isSuccessful) {
                        activity?.runOnUiThread {
                            loadDirectoryContent()
                            Snackbar.make(requireView(), "Файл успешно загружен", Snackbar.LENGTH_LONG).show()
                        }
                    } else {
                        val errorBody = response.body?.string()
                        activity?.runOnUiThread {
                            Snackbar.make(requireView(), "Ошибка при загрузке файла: ${response.code} - $errorBody", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(requireView(), "Ошибка при обработке файла: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun openPdfFile(pdfFile: PdfFile) {
        // Формируем полный URL файла
        val fileUrl = if (!pdfFile.fileUrl.startsWith("http")) {
            "${getServerUrl()}${pdfFile.fileUrl}"
        } else {
            pdfFile.fileUrl
        }

        // Проверяем, что URL файла корректный
        try {
            val url = Uri.parse(fileUrl)
            if (url.scheme.isNullOrEmpty() || url.host.isNullOrEmpty()) {
                showError("Некорректный URL файла")
                return
            }

            // Создаем Intent для открытия PDF
            val intent = Intent(requireContext(), PdfReaderActivity::class.java).apply {
                putExtra(PdfReaderActivity.EXTRA_PDF_FILE_PATH, fileUrl)
                putExtra(PdfReaderActivity.EXTRA_PDF_FILE_NAME, pdfFile.name)
            }
            
            // Обновляем время последнего открытия
            val request = Request.Builder()
                .url("${getServerUrl()}/files/${pdfFile.id}/lastOpened")
                .put(RequestBody.create(null, ByteArray(0)))
                .build()

            client.newCall(request).enqueue(object : Callback {
                @Throws(IOException::class)
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        activity?.runOnUiThread {
                            loadDirectoryContent()
                        }
                    }
                }
            })

            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            showError("Ошибка при открытии файла: ${e.message}")
        }
    }

    private fun loadDirectoryContent() {
        // Показываем загрузку, скрываем ошибки
        showLoading()
        
        // Загружаем директории
        val directoriesRequest = Request.Builder()
            .url("${getServerUrl()}/directories?parentId=${currentDirectory?.id ?: ""}")
            .build()

        client.newCall(directoriesRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    if (e is java.net.ConnectException || e is java.net.SocketTimeoutException) {
                        showServerError()
                    } else {
                        showError("Ошибка при загрузке директорий: ${e.message}")
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val directories = parseDirectories(response.body?.string())
                    
                    // Загружаем файлы
                    val filesRequest = Request.Builder()
                        .url("${getServerUrl()}/directories/${currentDirectory?.id ?: "root"}/files")
                        .build()

                    client.newCall(filesRequest).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            activity?.runOnUiThread {
                                if (e is java.net.ConnectException || e is java.net.SocketTimeoutException) {
                                    showServerError()
                                } else {
                                    showError("Ошибка при загрузке файлов: ${e.message}")
                                }
                            }
                        }

                        override fun onResponse(call: Call, response: Response) {
                            if (response.isSuccessful) {
                                val files = parsePdfFiles(response.body?.string())
                                
                                activity?.runOnUiThread {
                                    updateContent(directories, files)
                                }
                            } else {
                                activity?.runOnUiThread {
                                    showError("Ошибка при загрузке файлов: ${response.code}")
                                }
                            }
                        }
                    })
                } else {
                    activity?.runOnUiThread {
                        showError("Ошибка при загрузке директорий: ${response.code}")
                    }
                }
            }
        })
    }

    private fun showLoading() {
        recyclerView.visibility = View.VISIBLE
        tvEmptyDirectory.visibility = View.GONE
        tvServerError.visibility = View.GONE
    }

    private fun updateContent(directories: List<Directory>, files: List<PdfFile>) {
        val items = mutableListOf<DirectoryContentItem>()
        items.addAll(directories.map { DirectoryContentItem.DirectoryItem(it) })
        items.addAll(files.map { DirectoryContentItem.PdfItem(it) })
        
        directoryAdapter.submitList(items)
        
        tvEmptyDirectory.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        tvServerError.visibility = View.GONE
    }

    private fun showServerError() {
        recyclerView.visibility = View.GONE
        tvEmptyDirectory.visibility = View.GONE
        tvServerError.visibility = View.VISIBLE
        // Убираем скрытие кнопки, чтобы всегда можно было создать директорию
        // fabAddDirectory.visibility = View.GONE
    }

    private fun showDeleteSelectedItemsDialog() {
        val selectedItems = directoryAdapter.getSelectedItems()
        if (selectedItems.isEmpty()) return

        val message = when {
            selectedItems.all { it is DirectoryContentItem.DirectoryItem } -> 
                "Удалить выбранные директории (${selectedItems.size})?"
            selectedItems.all { it is DirectoryContentItem.PdfItem } -> 
                "Удалить выбранные файлы (${selectedItems.size})?"
            else -> "Удалить выбранные элементы (${selectedItems.size})?"
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.DeleteDialogStyle)
            .setTitle("Удаление")
            .setMessage(message)
            .setPositiveButton("УДАЛИТЬ") { dialog, _ ->
                deleteSelectedItems(selectedItems)
                dialog.dismiss()
            }
            .setNegativeButton("ОТМЕНА") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteSelectedItems(items: List<DirectoryContentItem>) {
        items.forEach { item ->
            when (item) {
                is DirectoryContentItem.DirectoryItem -> deleteDirectory(item.directory.id)
                is DirectoryContentItem.PdfItem -> deletePdfFile(item.pdfFile.id)
            }
        }
        actionMode?.finish()
    }

    private fun deletePdfFile(fileId: String) {
        val url = "${getServerUrl()}/files/$fileId"
        
        // Создаем тело запроса с информацией о пользователе
        val requestBody = FormBody.Builder()
            .add("userId", getUserIdFromPrefs())
            .add("userName", getUserNameFromPrefs())
            .build()
        
        val request = Request.Builder()
            .url(url)
            .delete(requestBody) // Используем тело в DELETE запросе
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Snackbar.make(requireView(), "Ошибка при удалении файла: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    activity?.runOnUiThread {
                        loadDirectoryContent()
                        Snackbar.make(requireView(), "Файл удален", Snackbar.LENGTH_LONG).show()
                    }
                } else {
                    val errorBody = response.body?.string()
                    activity?.runOnUiThread {
                        Snackbar.make(requireView(), "Ошибка при удалении файла: ${response.code} - $errorBody", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun showError(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
    }

    private fun navigateBack() {
        if (pathHistory.isNotEmpty()) {
            pathHistory.removeAt(pathHistory.lastIndex)
            currentDirectory = pathHistory.lastOrNull()
            updatePathDisplay()
            loadDirectoryContent()
        }
    }

    private fun navigateToDirectory(directory: Directory) {
        currentDirectory = directory
        pathHistory.add(directory)
        updatePathDisplay()
        loadDirectoryContent()
    }

    private fun updatePathDisplay() {
        val path = if (pathHistory.isEmpty()) {
            "~/"
        } else {
            "~/" + pathHistory.joinToString(" / ") { it.name }
        }
        tvPath.text = path
        btnBack.visibility = if (pathHistory.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddDirectoryDialog() {
        dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_directory, null)
        val nameInput = dialogView?.findViewById<EditText>(R.id.etDirectoryName)
        val descriptionInput = dialogView?.findViewById<EditText>(R.id.etDirectoryDescription)
        val selectImageButton = dialogView?.findViewById<Button>(R.id.btnSelectImage)
        val selectedImageView = dialogView?.findViewById<ImageView>(R.id.ivSelectedImage)

        selectImageButton?.setOnClickListener {
            pickImage.launch("image/*")
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.AddDirectoryDialogStyle)
            .setTitle("Создание директории")
            .setView(dialogView)
            .setPositiveButton("ДОБАВИТЬ") { dialog, _ ->
                val name = nameInput?.text.toString()
                if (name.isNotEmpty()) {
                    createDirectory(
                        name = name,
                        description = descriptionInput?.text.toString() ?: "",
                        selectedImageUri = selectedImageUri
                    )
                    selectedImageUri = null
                    dialog.dismiss()
                } else {
                    Snackbar.make(requireView(), "Введите название директории", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("ОТМЕНА") { dialog, _ ->
                selectedImageUri = null
                dialog.dismiss()
            }
            .show()
    }

    private fun createDirectory(name: String, description: String, selectedImageUri: Uri?) {
        if (selectedImageUri != null) {
            uploadImageAndCreateDirectory(name, description, selectedImageUri)
        } else {
            createDirectoryWithImage(name, description, "${getServerUrl()}/uploads/avatars/1744893780749-avatar_48bfcbfe_78dc_4cdc_9bc9_9b09160bb61f.jpg")
        }
    }

    private fun uploadImageAndCreateDirectory(name: String, description: String, imageUri: Uri) {
        try {
            val tempFile = createTempFileFromUri(imageUri)
            if (tempFile == null) {
                Snackbar.make(requireView(), "Ошибка при подготовке файла", Snackbar.LENGTH_LONG).show()
                return
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    tempFile.name,
                    tempFile.asRequestBody("image/*".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("${getServerUrl()}/upload/image")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                @Throws(IOException::class)
                override fun onFailure(call: Call, e: IOException) {
                    activity?.runOnUiThread {
                        Snackbar.make(requireView(), "Ошибка при загрузке изображения: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    tempFile.delete()
                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body?.string()
                            val jsonObject = JSONObject(responseBody)
                            val fileUrl = jsonObject.getString("fileUrl")
                            
                            activity?.runOnUiThread {
                                createDirectoryWithImage(name, description, fileUrl)
                            }
                        } catch (e: Exception) {
                            activity?.runOnUiThread {
                                Snackbar.make(requireView(), "Ошибка при обработке ответа сервера", Snackbar.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        activity?.runOnUiThread {
                            Snackbar.make(requireView(), "Ошибка при загрузке изображения: ${response.code}", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Snackbar.make(requireView(), "Ошибка при загрузке изображения: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun createDirectoryWithImage(name: String, description: String, imageUrl: String) {
        val json = JSONObject().apply {
            put("name", name)
            put("description", description)
            put("imageUrl", imageUrl)
            put("parentId", currentDirectory?.id)
            put("userId", getUserIdFromPrefs())
            put("userName", getUserNameFromPrefs())
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${getServerUrl()}/directories")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            @Throws(IOException::class)
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Snackbar.make(requireView(), "Ошибка при создании директории: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        Snackbar.make(requireView(), "Директория успешно создана", Snackbar.LENGTH_LONG).show()
                        loadDirectoryContent()
                    } else {
                        Snackbar.make(requireView(), "Ошибка при создании директории: ${response.code}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            val contentResolver = requireContext().contentResolver
            val fileName = getFileName(contentResolver, uri)
            val tempFile = File(requireContext().cacheDir, fileName)
            
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
        var fileName = "temp_image_${System.currentTimeMillis()}.jpg"
        
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        
        return fileName
    }

    private fun startSelectionMode() {
        if (actionMode == null) {
            actionMode = activity?.startActionMode(actionModeCallback)
            directoryAdapter.toggleSelectionMode()
        }
    }

    private fun setupBackButton() {
        btnBack.setOnClickListener {
            navigateBack()
        }
    }

    private fun parseDirectories(jsonStr: String?): List<Directory> {
        return try {
            jsonStr?.let { str ->
                val jsonArray = JSONArray(str)
                val directories = mutableListOf<Directory>()
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    directories.add(
                        Directory(
                            id = jsonObject.getString("id"),
                            name = jsonObject.getString("name"),
                            description = jsonObject.getString("description"),
                            imageUrl = jsonObject.getString("imageUrl"),
                            parentId = if (jsonObject.has("parentId") && !jsonObject.isNull("parentId")) 
                                jsonObject.getString("parentId") else null
                        )
                    )
                }
                directories
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parsePdfFiles(jsonStr: String?): List<PdfFile> {
        return try {
            jsonStr?.let { str ->
                val jsonArray = JSONArray(str)
                val files = mutableListOf<PdfFile>()
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    files.add(
                        PdfFile(
                            id = jsonObject.getString("id"),
                            name = jsonObject.getString("name"),
                            description = if (jsonObject.has("description") && !jsonObject.isNull("description"))
                                jsonObject.getString("description") else null,
                            fileUrl = jsonObject.getString("fileUrl"),
                            directoryId = jsonObject.getString("directoryId"),
                            size = jsonObject.getLong("size"),
                            createdAt = jsonObject.getLong("createdAt"),
                            lastOpenedAt = if (jsonObject.has("lastOpenedAt") && !jsonObject.isNull("lastOpenedAt"))
                                jsonObject.getLong("lastOpenedAt") else null
                        )
                    )
                }
                files
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun deleteDirectory(directoryId: String) {
        // Создаем тело запроса с информацией о пользователе
        val requestBody = FormBody.Builder()
            .add("userId", getUserIdFromPrefs())
            .add("userName", getUserNameFromPrefs())
            .build()

        val request = Request.Builder()
            .url("${getServerUrl()}/directories/$directoryId")
            .delete(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    showError("Ошибка при удалении директории")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    activity?.runOnUiThread {
                        loadDirectoryContent()
                    }
                }
            }
        })
    }

    private fun showRenameDialog(currentName: String, isDirectory: Boolean, itemId: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_rename_item, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.etNewName)
        nameInput.setText(currentName)
        nameInput.setSelection(currentName.length) // Курсор в конец текста
        
        MaterialAlertDialogBuilder(requireContext(), R.style.RenameDialogStyle)
            .setTitle(if (isDirectory) "Переименовать директорию" else "Переименовать файл")
            .setView(dialogView)
            .setPositiveButton("СОХРАНИТЬ") { dialog, _ ->
                val newName = nameInput.text.toString()
                if (newName.isNotEmpty()) {
                    if (isDirectory) {
                        renameDirectory(itemId, newName)
                    } else {
                        renamePdfFile(itemId, newName)
                    }
                    actionMode?.finish()
                    dialog.dismiss()
                } else {
                    Snackbar.make(requireView(), "Имя не может быть пустым", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ОТМЕНА") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun renameDirectory(directoryId: String, newName: String) {
        val json = JSONObject().apply {
            put("name", newName)
            put("userId", getUserIdFromPrefs())
            put("userName", getUserNameFromPrefs())
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${getServerUrl()}/directories/$directoryId")
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    showError("Ошибка при переименовании директории: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        Snackbar.make(requireView(), "Директория успешно переименована", Snackbar.LENGTH_SHORT).show()
                        loadDirectoryContent()
                        
                        // Обновляем имя текущей директории, если переименовали ее
                        if (currentDirectory?.id == directoryId) {
                            currentDirectory = currentDirectory?.copy(name = newName)
                            updatePathDisplay()
                        }
                        
                        // Проверяем историю навигации и обновляем названия
                        for (i in pathHistory.indices) {
                            if (pathHistory[i].id == directoryId) {
                                pathHistory[i] = pathHistory[i].copy(name = newName)
                                updatePathDisplay()
                                break
                            }
                        }
                    } else {
                        showError("Ошибка при переименовании директории: код ${response.code}, ${response.body?.string()}")
                    }
                }
            }
        })
    }

    private fun renamePdfFile(fileId: String, newName: String) {
        println("DEBUG: Начинаем переименование файла с ID: $fileId на имя: $newName")
        
        val json = JSONObject().apply {
            put("name", newName)
            put("userId", getUserIdFromPrefs())
            put("userName", getUserNameFromPrefs())
        }
        
        val jsonString = json.toString()
        println("DEBUG: JSON в запросе: $jsonString")
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonString.toRequestBody(mediaType)
        
        println("DEBUG: URL запроса: ${getServerUrl()}/files/$fileId")
        println("DEBUG: Content-Type: ${mediaType}")
        
        val request = Request.Builder()
            .url("${getServerUrl()}/files/$fileId")
            .put(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
            
        println("DEBUG: Заголовки запроса: ${request.headers}")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("DEBUG: Ошибка при переименовании файла: ${e.message}")
                println("DEBUG: Тип исключения: ${e.javaClass.name}")
                e.printStackTrace()
                activity?.runOnUiThread {
                    showError("Ошибка при переименовании файла: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBodyString = response.body?.string()
                println("DEBUG: Ответ сервера при переименовании файла:")
                println("DEBUG: Код ответа: ${response.code}")
                println("DEBUG: Тело ответа: $responseBodyString")
                println("DEBUG: Заголовки ответа: ${response.headers}")
                
                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        Snackbar.make(requireView(), "Файл успешно переименован", Snackbar.LENGTH_SHORT).show()
                        loadDirectoryContent()
                    } else {
                        val errorMsg = if (!responseBodyString.isNullOrEmpty()) {
                            "Ошибка при переименовании файла: код ${response.code}, ошибка: $responseBodyString"
                        } else {
                            "Ошибка при переименовании файла: код ${response.code}"
                        }
                        println("DEBUG: Отображаем ошибку: $errorMsg")
                        showError(errorMsg)
                    }
                }
            }
        })
    }

    private fun showItemContextMenu(view: View?, item: DirectoryContentItem) {
        if (view == null) return
        
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(R.menu.menu_item_context, popupMenu.menu)
        
        // Добавляем отладочную информацию
        when (item) {
            is DirectoryContentItem.DirectoryItem -> {
                println("DEBUG: Показываем контекстное меню для директории: ${item.directory.name}, ID: ${item.directory.id}")
            }
            is DirectoryContentItem.PdfItem -> {
                println("DEBUG: Показываем контекстное меню для PDF: ${item.pdfFile.name}, ID: ${item.pdfFile.id}")
            }
        }
        
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_rename -> {
                    when (item) {
                        is DirectoryContentItem.DirectoryItem -> {
                            println("DEBUG: Переименование директории ID: ${item.directory.id}")
                            showRenameDialog(item.directory.name, true, item.directory.id)
                        }
                        is DirectoryContentItem.PdfItem -> {
                            println("DEBUG: Переименование PDF файла ID: ${item.pdfFile.id}")
                            showRenameDialog(item.pdfFile.name, false, item.pdfFile.id)
                        }
                    }
                    true
                }
                R.id.action_delete -> {
                    when (item) {
                        is DirectoryContentItem.DirectoryItem -> {
                            showDeleteConfirmationDialog(isDirectory = true, id = item.directory.id)
                        }
                        is DirectoryContentItem.PdfItem -> {
                            showDeleteConfirmationDialog(isDirectory = false, id = item.pdfFile.id)
                        }
                    }
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }
    
    private fun showDeleteConfirmationDialog(isDirectory: Boolean, id: String) {
        val message = if (isDirectory) "Удалить эту директорию?" else "Удалить этот файл?"
        
        MaterialAlertDialogBuilder(requireContext(), R.style.DeleteDialogStyle)
            .setTitle("Удаление")
            .setMessage(message)
            .setPositiveButton("УДАЛИТЬ") { dialog, _ ->
                if (isDirectory) {
                    deleteDirectory(id)
                } else {
                    deletePdfFile(id)
                }
                dialog.dismiss()
            }
            .setNegativeButton("ОТМЕНА") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun downloadPdf(pdfFile: PdfFile) {
        // Показываем диалог выбора: сохранить или поделиться
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Скачать файл")
            .setItems(arrayOf("Сохранить на устройство", "Поделиться")) { _, which ->
                when (which) {
                    0 -> requestStoragePermissionAndSave(pdfFile)
                    1 -> shareFile(pdfFile)
                }
            }
            .show()
    }

    private fun requestStoragePermissionAndSave(pdfFile: PdfFile) {
        // Для Android 10 (API 29) и выше разрешения на запись в общее хранилище не требуются
        // Для Android 6.0 (API 23) и выше до Android 10 нужны разрешения
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Запрашиваем разрешение
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
                // Сохраняем файл, который нужно скачать
                requireActivity().getSharedPreferences("pdf_preferences", Context.MODE_PRIVATE)
                    .edit()
                    .putString("pending_download_file_id", pdfFile.id)
                    .apply()
            } else {
                // Разрешение уже есть, сохраняем файл
                saveFileToDevice(pdfFile)
            }
        } else {
            // На новых версиях Android разрешение не требуется
            saveFileToDevice(pdfFile)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            // Проверяем, дано ли разрешение
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Разрешение дано, продолжаем сохранение
                val pendingFileId = requireActivity().getSharedPreferences("pdf_preferences", Context.MODE_PRIVATE)
                    .getString("pending_download_file_id", null)
                
                if (pendingFileId != null) {
                    // Находим файл в списке
                    val items = directoryAdapter.currentList
                    items.find { it is DirectoryContentItem.PdfItem && it.pdfFile.id == pendingFileId }?.let {
                        if (it is DirectoryContentItem.PdfItem) {
                            saveFileToDevice(it.pdfFile)
                        }
                    }
                    // Очищаем сохраненный ID
                    requireActivity().getSharedPreferences("pdf_preferences", Context.MODE_PRIVATE)
                        .edit()
                        .remove("pending_download_file_id")
                        .apply()
                }
            } else {
                // Отказано в разрешении
                Snackbar.make(
                    requireView(),
                    "Разрешение на сохранение файлов необходимо для скачивания",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveFileToDevice(pdfFile: PdfFile) {
        try {
            val fileUrl = if (!pdfFile.fileUrl.startsWith("http")) {
                "${getServerUrl()}${pdfFile.fileUrl}"
            } else {
                pdfFile.fileUrl
            }

            val request = DownloadManager.Request(Uri.parse(fileUrl))
                .setTitle("Загрузка ${pdfFile.name}")
                .setDescription("Загрузка PDF файла")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, pdfFile.name)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            
            Snackbar.make(requireView(), "Загрузка файла начата", Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            showError("Ошибка при загрузке файла: ${e.message}")
        }
    }

    private fun shareFile(pdfFile: PdfFile) {
        // Показываем индикатор загрузки
        val progressDialog = android.app.ProgressDialog(requireContext()).apply {
            setTitle("Подготовка файла")
            setMessage("Пожалуйста, подождите...")
            setCancelable(false)
            show()
        }

        // Скачиваем файл во временную директорию
        Thread {
            try {
                val fileUrl = if (!pdfFile.fileUrl.startsWith("http")) {
                    "${getServerUrl()}${pdfFile.fileUrl}"
                } else {
                    pdfFile.fileUrl
                }

                val tempFile = File(requireContext().cacheDir, pdfFile.name)
                
                val request = Request.Builder()
                    .url(fileUrl)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // Создаем URI с использованием FileProvider для Android 7.0+
                    val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            tempFile
                        )
                    } else {
                        Uri.fromFile(tempFile)
                    }
                    
                    // Закрываем диалог и показываем диалог шаринга
                    activity?.runOnUiThread {
                        progressDialog.dismiss()
                        
                        // Создаем интент для шаринга
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            type = "application/pdf"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        startActivity(Intent.createChooser(shareIntent, "Поделиться файлом"))
                    }
                } else {
                    activity?.runOnUiThread {
                        progressDialog.dismiss()
                        showError("Ошибка при загрузке файла: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activity?.runOnUiThread {
                    progressDialog.dismiss()
                    showError("Ошибка при загрузке файла: ${e.message}")
                }
            }
        }.start()
    }

    private fun getUserNameFromPrefs(): String {
        val sharedPreferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("name", "Неизвестный пользователь") ?: "Неизвестный пользователь"
    }

    private fun getUserIdFromPrefs(): String {
        val sharedPreferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("email", "unknown@example.com") ?: "unknown@example.com"
    }

    // Функция для получения URL сервера в зависимости от типа устройства
    private fun getServerUrl(): String {
        try {
            // Проверяем, прикреплен ли фрагмент к контексту
            if (!isAdded) {
                Log.w(TAG, "Фрагмент не прикреплен к контексту, возвращаем URL по умолчанию")
                return "http://192.168.0.86:3000"
            }
            
            // Проверяем, есть ли сохраненный URL
            val prefs = requireContext().getSharedPreferences("PROVITA_SETTINGS", Context.MODE_PRIVATE)
            val savedUrl = prefs.getString("SERVER_URL", null)
            
            // Если есть, используем его
            if (savedUrl != null) {
                return savedUrl
            }
            
            // Иначе используем URL из ServerUrlUpdater
            return ServerUrlUpdater.getDuckDnsUrl()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при получении URL сервера", e)
            return "http://192.168.0.86:3000"
        }
    }

    companion object {
        private const val TAG = "DirectoryContentFragment"
        
        fun newInstance(): DirectoryContentFragment {
            return DirectoryContentFragment()
        }
    }
}
