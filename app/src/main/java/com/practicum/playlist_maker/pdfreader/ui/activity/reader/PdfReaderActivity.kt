package com.practicum.playlist_maker.pdfreader.ui.activity.reader

import android.app.DownloadManager
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.databinding.ActivityPdfReaderBinding
import com.practicum.playlist_maker.databinding.ContainerPdfReaderBinding
import com.practicum.playlist_maker.pdfreader.model.FavoriteUpdateEvent
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.CustomPdfView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.create
import okhttp3.RequestBody.Companion.toRequestBody
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class PdfReaderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPdfReaderBinding
    private lateinit var contentBinding: ContainerPdfReaderBinding
    private lateinit var pdfView: CustomPdfView
    private lateinit var ivBack: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var favoriteButton: ImageView

    private var pdfFile: File? = null
    private var client = OkHttpClient()
    private var isFavorite = false
    private var currentPdfId: String? = null
    private var currentFileName: String? = null
    private var currentFilePath: String? = null

    private val TAG = "PdfReaderActivity"

    companion object {
        private const val BUFFER_SIZE = 32768 // 32KB buffer for faster loading
        const val EXTRA_PDF_FILE_PATH = "extra_pdf_file_path"
        const val EXTRA_PDF_FILE_NAME = "extra_pdf_file_name"
        const val EXTRA_PDF_ID = "extra_pdf_id"
        const val RESULT_ACTION_PDF_DELETED = "action.pdf.deleted"
        
        // Динамически определяем URL сервера в зависимости от типа устройства
        fun getServerUrl(): String {
            // Проверяем, запущено ли приложение на эмуляторе
            val isEmulator = android.os.Build.MODEL.contains("sdk") ||
                    android.os.Build.MODEL.contains("Emulator") ||
                    android.os.Build.MODEL.contains("Android SDK")
            
            return if (isEmulator) {
                "http://10.0.2.2:3000"
            } else {
                "http://192.168.0.105:3000"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfReaderBinding.inflate(layoutInflater)
        contentBinding = binding.container
        setContentView(binding.root)

        Log.d(TAG, "onCreate: intent extras = ${intent.extras}")
        Log.d(TAG, "onCreate: all extras keys = ${intent.extras?.keySet()?.joinToString()}")

        initViews()
        setupListeners()

        client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val filePath = intent.getStringExtra(EXTRA_PDF_FILE_PATH)
        Log.d(TAG, "Received file path: $filePath")
        currentFilePath = filePath
        
        // Получаем ID файла из URL, если он присутствует
        if (filePath?.startsWith("http") == true) {
            // Проверяем формат URL для файлов с сервера
            if (filePath.contains("/uploads/pdfs/")) {
                val fileName = filePath.substringAfterLast("/")
                Log.d(TAG, "Server file detected with filename: $fileName")
                
                // Пытаемся извлечь ID из URL регулярным выражением
                val extractedId = extractFileIdFromUrl(filePath)
                if (extractedId != null) {
                    currentPdfId = extractedId
                    Log.d(TAG, "Extracted PDF ID from URL: $currentPdfId")
                } else {
                    // Попробуем старый способ извлечения ID из имени файла
                    val parts = fileName.split("-", limit = 2)
                    if (parts.size > 1 && parts[0].isNotEmpty() && parts[0].all { it.isDigit() }) {
                        currentPdfId = parts[0]
                        Log.d(TAG, "Extracted PDF ID from filename: $currentPdfId")
                    } else {
                        // Если мы не можем извлечь ID из имени файла или URL, будем искать позже
                        Log.d(TAG, "Could not extract ID from filename or URL, will search for it later")
                    }
                }
            }
        }

        // Получаем ID файла напрямую из интента, если он передан
        intent.getStringExtra(EXTRA_PDF_ID)?.let {
            currentPdfId = it
            Log.d(TAG, "Using PDF ID from intent: $currentPdfId")
        }

        // Если ID файла есть, проверяем статус избранного
        currentPdfId?.let { checkFavoriteStatus(it) }

        if (filePath != null && filePath.isNotEmpty()) {
            currentFileName = intent.getStringExtra(EXTRA_PDF_FILE_NAME)
            
            // Если имя файла не передано, извлекаем из URL
            if (currentFileName == null || currentFileName!!.isEmpty()) {
                currentFileName = filePath.substringAfterLast("/").let {
                    // Удаляем ID из имени файла, если он там есть
                    if (it.contains("-")) {
                        val parts = it.split("-", limit = 2)
                        if (parts[0].all { c -> c.isDigit() }) {
                            parts[1]
                        } else {
                            it
                        }
                    } else {
                        it
                    }
                }
                Log.d(TAG, "Extracted filename from URL: $currentFileName")
            }
            
            if (filePath.startsWith("http")) {
                Log.d(TAG, "URL detected, downloading PDF...")
                val adjustedUrl = if (filePath.contains("localhost")) {
                    filePath.replace("localhost", "10.0.2.2")
                } else {
                    filePath
                }
                Log.d(TAG, "Adjusted URL: $adjustedUrl")
                downloadAndOpenPdf(adjustedUrl)
            } else {
                pdfFile = File(filePath)
                Log.d(TAG, "Local file path detected")
                openPdfFile()
            }
        } else {
            Log.e(TAG, "No PDF file path provided")
            finish()
        }

        // Добавляем обработчик для кнопки меню
        findViewById<ImageView>(R.id.ivMoreOptions).setOnClickListener { view ->
            showOptionsMenu(view)
        }
    }

    private fun initViews() {
        try {
            pdfView = contentBinding.pdfView
            ivBack = binding.ivBack
            tvTitle = binding.tvTitle
            favoriteButton = binding.favoriteButton

            var fileName = intent.getStringExtra(EXTRA_PDF_FILE_NAME)
            if (fileName == null) {
                fileName = intent.getStringExtra("pdfTitle")
                Log.d(TAG, "Using legacy pdfTitle key")
            }
            currentFileName = fileName

            Log.d(TAG, "Setting title to: $fileName")
            tvTitle.text = fileName ?: getString(R.string.pdf_viewer)

            // Setup PDF viewer listeners
            pdfView.setOnPageChangeListener { page, total ->
                Log.d(TAG, "Page changed: $page of $total")
                tvTitle.text = "$fileName ($page/$total)"
            }

            pdfView.setOnErrorListener { error ->
                Log.e(TAG, "PDF error", error)
                Toast.makeText(this, "Ошибка загрузки PDF: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                finish()
            }

            Log.d(TAG, "Views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing views", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            finish()
        }
    }

    private fun setupListeners() {
        ivBack.setOnClickListener {
            finish()
        }

        favoriteButton.setOnClickListener {
            Log.d(TAG, "Favorite button clicked, isFavorite=$isFavorite, currentPdfId=$currentPdfId")
            if (currentPdfId != null) {
                if (isFavorite) {
                    removeFromFavorites(currentPdfId!!)
                } else {
                    addToFavorites(currentPdfId!!)
                }
            } else {
                Log.d(TAG, "No PDF ID available, attempting to register file")
                if (currentFilePath?.startsWith("http") == true) {
                    Toast.makeText(this, "Загрузка файла...", Toast.LENGTH_SHORT).show()
                    // Для URL файлов мы уже пытаемся найти ID в downloadAndOpenPdf
                    // Проверим, загружен ли файл
                    if (pdfFile != null && pdfFile!!.exists()) {
                        Log.d(TAG, "File already downloaded, registering")
                        registerFileAndAddToFavorites()
                    } else {
                        Log.d(TAG, "File not yet downloaded, waiting")
                        Toast.makeText(this, "Пожалуйста, дождитесь загрузки файла", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Для локальных файлов сразу пытаемся регистрировать
                    registerFileAndAddToFavorites()
                }
            }
        }
    }

    private fun registerFileAndAddToFavorites() {
        if (currentFileName == null || currentFilePath == null) {
            Toast.makeText(this, "Не удалось определить данные файла", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "Registering file: $currentFileName, path: $currentFilePath")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Сначала проверяем, существует ли файл с таким URL на сервере
                val fileUrl = currentFilePath
                val fileListUrl = getServerUrl() + "/directories/root/files"
                Log.d(TAG, "Checking if file exists in root directory: $fileListUrl")
                
                val listRequest = Request.Builder()
                    .url(fileListUrl)
                    .get()
                    .build()
                
                try {
                    client.newCall(listRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string() ?: "[]"
                            val jsonArray = JSONObject("{ \"files\": $responseBody }").getJSONArray("files")
                            var fileId: String? = null
                            
                            // Ищем файл с таким же URL
                            for (i in 0 until jsonArray.length()) {
                                val file = jsonArray.getJSONObject(i)
                                val storedFileUrl = file.getString("fileUrl")
                                if (storedFileUrl.endsWith(fileUrl?.substringAfterLast("/") ?: "")) {
                                    fileId = file.getString("id")
                                    Log.d(TAG, "Found existing file with ID: $fileId and URL: $storedFileUrl")
                                    break
                                }
                            }
                            
                            if (fileId != null) {
                                // Файл найден, используем его ID
                                currentPdfId = fileId
                                withContext(Dispatchers.Main) {
                                    addToFavorites(fileId)
                                }
                            } else {
                                // Файл не найден, регистрируем новый
                                registerNewFile()
                            }
                        } else {
                            // Не удалось получить список файлов, пробуем регистрировать напрямую
                            Log.e(TAG, "Failed to get file list: ${response.code}")
                            registerNewFile()
                        }
                    }
                } catch (e: java.net.ConnectException) {
                    Log.e(TAG, "Server connection error during file registration", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfReaderActivity, "Сервер недоступен, попробуйте зайти в другое время", Toast.LENGTH_LONG).show()
                        // Пока сервер недоступен, делаем кнопку неактивной
                        isFavorite = false
                        updateFavoriteButtonUI()
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    Log.e(TAG, "Server timeout error during file registration", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfReaderActivity, "Сервер не отвечает, попробуйте зайти в другое время", Toast.LENGTH_LONG).show()
                        // Пока сервер недоступен, делаем кнопку неактивной
                        isFavorite = false
                        updateFavoriteButtonUI()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during file check", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PdfReaderActivity, "Ошибка при проверке файла: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private suspend fun registerNewFile() {
        try {
            Log.d(TAG, "Registering new file with name: $currentFileName")
            
            // Проверяем, загружен ли файл локально
            if (pdfFile == null || !pdfFile!!.exists()) {
                Log.e(TAG, "PDF file is not downloaded yet")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PdfReaderActivity, "Файл еще не загружен", Toast.LENGTH_SHORT).show()
                }
                return
            }
            
            // Проверяем, что файл уже существует на сервере
            if (currentFilePath?.startsWith("http") == true) {
                // Файл уже на сервере, но мы не можем найти его ID
                // Вместо регистрации нового, попробуем поискать файл другим способом
                val fileIdFromUrl = extractFileIdFromUrl(currentFilePath!!)
                if (fileIdFromUrl != null) {
                    Log.d(TAG, "Extracted file ID from URL: $fileIdFromUrl")
                    currentPdfId = fileIdFromUrl
                    withContext(Dispatchers.Main) {
                        checkFavoriteStatus(fileIdFromUrl)
                    }
                    return
                }
                
                // Попытка использовать существующий файл вместо загрузки нового
                findFileIdByName(currentFileName ?: "unknown")
                return
            }
            
            // Для локальных файлов мы должны загрузить их на сервер
            Log.d(TAG, "Local file needs to be uploaded to server first")
            uploadLocalFile()
        } catch (e: Exception) {
            Log.e(TAG, "Error registering file", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@PdfReaderActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private suspend fun findFileIdByName(fileName: String) {
        try {
            if (fileName.isEmpty()) {
                Log.d(TAG, "Cannot search for file with empty name")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PdfReaderActivity, "Название файла не указано", Toast.LENGTH_SHORT).show()
                }
                return
            }
            
            Log.d(TAG, "Finding file ID by name: $fileName")
            val url = getServerUrl() + "/directories/root/files"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
                
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: "[]"
                    Log.d(TAG, "Files response: $responseBody")
                    
                    // Проверяем, что ответ не пустой и содержит JSON-массив
                    if (responseBody.trim().startsWith("[")) {
                        val jsonArray = JSONArray(responseBody)
                        
                        // Нормализуем имя файла для сравнения
                        val normalizedSearchName = normalizeFileName(fileName)
                        Log.d(TAG, "Normalized search name: $normalizedSearchName")
                        
                        // Сначала ищем точное совпадение
                        var bestMatchId: String? = null
                        var bestMatchScore = 0.0
                        
                        for (i in 0 until jsonArray.length()) {
                            val file = jsonArray.getJSONObject(i)
                            val serverFileName = file.getString("name")
                            val normalizedServerName = normalizeFileName(serverFileName)
                            
                            // Считаем коэффициент схожести
                            val similarityScore = calculateSimilarity(normalizedSearchName, normalizedServerName)
                            Log.d(TAG, "Comparing with server file: $serverFileName, normalized: $normalizedServerName, score: $similarityScore")
                            
                            if (similarityScore > bestMatchScore) {
                                bestMatchScore = similarityScore
                                bestMatchId = file.getString("id")
                            }
                            
                            // Если имена полностью совпадают или очень похожи (> 90%)
                            if (similarityScore > 0.9) {
                                val fileId = file.getString("id")
                                Log.d(TAG, "Found good match for file ID by name: $fileId (server name: $serverFileName, score: $similarityScore)")
                                currentPdfId = fileId
                                withContext(Dispatchers.Main) {
                                    addToFavorites(fileId)
                                }
                                return
                            }
                        }
                        
                        // Если нашли файл с приемлемым совпадением
                        if (bestMatchId != null && bestMatchScore > 0.6) {
                            Log.d(TAG, "Using best match file ID: $bestMatchId with score: $bestMatchScore")
                            currentPdfId = bestMatchId
                            withContext(Dispatchers.Main) {
                                addToFavorites(bestMatchId)
                            }
                            return
                        }
                        
                        // Если все совпадения не найдены, ищем по части URL
                        val fileUrl = currentFilePath?.substringAfterLast("/")
                        if (fileUrl != null) {
                            for (i in 0 until jsonArray.length()) {
                                val file = jsonArray.getJSONObject(i)
                                if (file.has("fileUrl")) {
                                    val url = file.getString("fileUrl")
                                    if (url.endsWith(fileUrl)) {
                                        val fileId = file.getString("id")
                                        Log.d(TAG, "Found file ID by URL: $fileId")
                                        currentPdfId = fileId
                                        withContext(Dispatchers.Main) {
                                            addToFavorites(fileId)
                                        }
                                        return
                                    }
                                }
                            }
                        }
                        
                        // Пробуем найти файл в других директориях
                        findFileInAllDirectories(fileName)
                        
                    } else {
                        Log.e(TAG, "Invalid response format: $responseBody")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PdfReaderActivity, "Некорректный ответ сервера", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to get files list: ${response.code}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfReaderActivity, "Ошибка поиска файла: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding file by name", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@PdfReaderActivity, "Ошибка поиска: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private suspend fun findFileInAllDirectories(fileName: String) {
        try {
            // Получаем список директорий
            val url = getServerUrl() + "/directories"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
                
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val directoriesBody = response.body?.string() ?: "[]"
                    val directoriesArray = JSONArray(directoriesBody)
                    
                    // Перебираем все директории и ищем в них файл
                    for (i in 0 until directoriesArray.length()) {
                        val directory = directoriesArray.getJSONObject(i)
                        val directoryId = directory.getString("id")
                        val directoryName = directory.getString("name")
                        
                        // Для каждой директории получаем список файлов
                        val directoryUrl = getServerUrl() + "/directories/$directoryId/files"
                        val directoryRequest = Request.Builder()
                            .url(directoryUrl)
                            .get()
                            .build()
                            
                        client.newCall(directoryRequest).execute().use { directoryResponse ->
                            if (directoryResponse.isSuccessful) {
                                val filesBody = directoryResponse.body?.string() ?: "[]"
                                val filesArray = JSONArray(filesBody)
                                
                                // Ищем файл с похожим именем
                                val normalizedSearchName = normalizeFileName(fileName)
                                
                                for (j in 0 until filesArray.length()) {
                                    val file = filesArray.getJSONObject(j)
                                    val serverFileName = file.getString("name")
                                    val normalizedServerName = normalizeFileName(serverFileName)
                                    
                                    // Считаем коэффициент схожести
                                    val similarityScore = calculateSimilarity(normalizedSearchName, normalizedServerName)
                                    
                                    if (similarityScore > 0.6) {
                                        val fileId = file.getString("id")
                                        Log.d(TAG, "Found file in directory $directoryId ($directoryName): $fileId (name: $serverFileName, score: $similarityScore)")
                                        
                                        // Сохраняем ID файла
                                        currentPdfId = fileId
                                        
                                        // Обновляем информацию о директории файла, если нужно
                                        if (directoryId != "root") {
                                            Log.d(TAG, "Updating file directory from 'root' to '$directoryId'")
                                            updateFileDirectory(fileId, directoryId)
                                        }
                                        
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                this@PdfReaderActivity, 
                                                "Найден файл в директории: $directoryName", 
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            addToFavorites(fileId)
                                        }
                                        return
                                    }
                                }
                            }
                        }
                    }
                    
                    Log.d(TAG, "File not found in any directory")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfReaderActivity, "Файл не найден на сервере", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching in all directories", e)
        }
    }
    
    private fun normalizeFileName(fileName: String): String {
        // Нормализуем имя файла для сравнения
        return fileName
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")
            .replace(",", "")
            .replace(".", "")
            .replace("(", "")
            .replace(")", "")
            .toLowerCase()
    }
    
    private fun calculateSimilarity(s1: String, s2: String): Double {
        // Простая оценка схожести строк
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        
        // Если одна строка содержит другую
        if (s1.contains(s2)) return s2.length.toDouble() / s1.length
        if (s2.contains(s1)) return s1.length.toDouble() / s2.length
        
        // Находим общие символы
        val commonChars = s1.filter { s2.contains(it) }.length
        return commonChars.toDouble() / Math.max(s1.length, s2.length)
    }
    
    private fun extractFileIdFromUrl(url: String): String? {
        try {
            // Пытаемся извлечь ID из URL формата "http://10.0.2.2:3000/uploads/pdfs/1742732751034-compressor_catalogue__1_.pdf"
            Log.d(TAG, "Extracting ID from URL: $url")
            
            // Попробуем найти ID по регулярному выражению
            val regex = "/uploads/pdfs/([0-9]+)-".toRegex()
            val matchResult = regex.find(url)
            if (matchResult != null) {
                val extractedId = matchResult.groupValues[1]
                Log.d(TAG, "Extracted ID from URL using regex: $extractedId")
                return extractedId
            }
            
            // Если регулярное выражение не подошло, попробуем другие методы
            val urlParts = url.split("/")
            if (urlParts.size > 1) {
                val lastPart = urlParts.last()
                val fileNameParts = lastPart.split("-")
                
                if (fileNameParts.isNotEmpty() && fileNameParts[0].all { it.isDigit() }) {
                    Log.d(TAG, "Extracted ID from filename: ${fileNameParts[0]}")
                    return fileNameParts[0]
                }
            }
            
            Log.d(TAG, "Could not extract ID from URL")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting ID from URL", e)
            return null
        }
    }
    
    private suspend fun uploadLocalFile() {
        // Для будущей реализации загрузки локальных файлов
        // В текущей задаче это не требуется
        withContext(Dispatchers.Main) {
            Toast.makeText(this@PdfReaderActivity, "Загрузка локальных файлов не реализована", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createFileId(): String? {
        // Создаем ID файла на основе имени файла и текущего времени
        return if (currentFileName != null) {
            val timestamp = System.currentTimeMillis().toString()
            // Используем timestamp как ID
            timestamp
        } else {
            null
        }
    }

    private fun checkFavoriteStatus(fileId: String) {
        Log.d(TAG, "Checking favorite status for file ID: $fileId")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = getServerUrl() + "/files/$fileId/favorite"
                Log.d(TAG, "Sending GET request to: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val responseCode = response.code
                        val responseBody = response.body?.string() ?: ""
                        Log.d(TAG, "Response code: $responseCode, body: $responseBody")
                        
                        if (response.isSuccessful) {
                            try {
                                val jsonObject = JSONObject(responseBody)
                                val isFavoriteResponse = jsonObject.getBoolean("isFavorite")
                                Log.d(TAG, "Is favorite: $isFavoriteResponse")
                                
                                withContext(Dispatchers.Main) {
                                    isFavorite = isFavoriteResponse
                                    updateFavoriteButtonUI()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing favorite status", e)
                                withContext(Dispatchers.Main) {
                                    // Даже если не можем распарсить ответ, просто предполагаем, что файл не в избранном
                                    isFavorite = false
                                    updateFavoriteButtonUI()
                                }
                            }
                        } else {
                            Log.e(TAG, "Failed to check favorite status: $responseCode")
                            withContext(Dispatchers.Main) {
                                // По умолчанию считаем, что файл не в избранном
                                isFavorite = false
                                updateFavoriteButtonUI()
                            }
                        }
                    }
                } catch (e: java.net.ConnectException) {
                    Log.e(TAG, "Server connection error during favorite check", e)
                    withContext(Dispatchers.Main) {
                        // По умолчанию считаем, что файл не в избранном
                        isFavorite = false
                        updateFavoriteButtonUI()
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    Log.e(TAG, "Server timeout error during favorite check", e)
                    withContext(Dispatchers.Main) {
                        // По умолчанию считаем, что файл не в избранном
                        isFavorite = false
                        updateFavoriteButtonUI()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking favorite status", e)
                withContext(Dispatchers.Main) {
                    // По умолчанию считаем, что файл не в избранном
                    isFavorite = false
                    updateFavoriteButtonUI()
                }
            }
        }
    }

    private fun addToFavorites(fileId: String) {
        Log.d(TAG, "Attempting to add file to favorites with ID: $fileId")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Получаем информацию о файле перед добавлением в избранное
                getFileInfo(fileId)
                
                val url = getServerUrl() + "/files/$fileId/favorite"
                Log.d(TAG, "Sending PUT request to: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .put(create(null, ByteArray(0)))
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val responseCode = response.code
                        val responseBody = response.body?.string() ?: ""
                        Log.d(TAG, "Response code: $responseCode, body: $responseBody")
                        
                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful) {
                                isFavorite = true
                                updateFavoriteButtonUI()
                                Toast.makeText(this@PdfReaderActivity, "Добавлено в избранное", Toast.LENGTH_SHORT).show()
                                
                                // Отправляем событие о добавлении в избранное
                                EventBus.getDefault().post(FavoriteUpdateEvent(true))
                            } else if (responseCode == 404) {
                                Log.d(TAG, "Server returned 404 for file ID: $fileId")
                                
                                // Глобальный поиск файла на сервере
                                lifecycleScope.launch(Dispatchers.IO) {
                                    // Сначала проверяем другие ID из URL
                                    if (currentFilePath?.contains("/uploads/pdfs/") == true) {
                                        val extractedId = extractFileIdFromUrl(currentFilePath!!)
                                        if (extractedId != null && extractedId != fileId) {
                                            Log.d(TAG, "Trying with extracted ID: $extractedId instead of $fileId")
                                            currentPdfId = extractedId
                                            addToFavorites(extractedId)
                                            return@launch
                                        }
                                    }
                                    
                                    // Ищем любой файл с похожим именем
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@PdfReaderActivity, "Поиск файла на сервере...", Toast.LENGTH_SHORT).show()
                                    }
                                    findFileGlobally()
                                }
                            } else {
                                Toast.makeText(this@PdfReaderActivity, "Ошибка при добавлении в избранное: $responseCode", Toast.LENGTH_SHORT).show()
                                Log.e(TAG, "Failed to add to favorites: $responseCode, message: $responseBody")
                            }
                        }
                    }
                } catch (e: java.net.ConnectException) {
                    Log.e(TAG, "Server connection error", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfReaderActivity, "Сервер недоступен, попробуйте зайти в другое время", Toast.LENGTH_LONG).show()
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    Log.e(TAG, "Server timeout error", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfReaderActivity, "Сервер не отвечает, попробуйте зайти в другое время", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding to favorites", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PdfReaderActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private suspend fun getFileInfo(fileId: String) {
        try {
            val url = getServerUrl() + "/files/$fileId"
            Log.d(TAG, "Getting file info for ID: $fileId")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
                
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "File info response: $responseBody")
                    
                    if (responseBody != null && responseBody.isNotEmpty()) {
                        try {
                            val fileInfo = JSONObject(responseBody)
                            if (fileInfo.has("directoryId")) {
                                val directoryId = fileInfo.getString("directoryId")
                                Log.d(TAG, "File directoryId: $directoryId")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing file info", e)
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to get file info: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file info", e)
        }
    }

    private suspend fun findFileGlobally() {
        try {
            // Если у нас есть имя файла, пробуем найти по нему
            if (!currentFileName.isNullOrEmpty()) {
                Log.d(TAG, "Searching for file by name: $currentFileName")
                findFileIdByName(currentFileName!!)
            } else if (currentFilePath != null) {
                // Если нет имени, но есть путь, получаем имя из пути
                val fileName = currentFilePath!!.substringAfterLast("/")
                if (fileName.isNotEmpty()) {
                    Log.d(TAG, "Searching for file by path-derived name: $fileName")
                    findFileIdByName(fileName)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfReaderActivity, "Не удается определить имя файла", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PdfReaderActivity, "Недостаточно данных для поиска файла", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in global file search", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@PdfReaderActivity, "Ошибка при глобальном поиске: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeFromFavorites(fileId: String) {
        Log.d(TAG, "Attempting to remove file from favorites with ID: $fileId")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = getServerUrl() + "/files/$fileId/favorite"
                Log.d(TAG, "Sending DELETE request to: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .delete()
                    .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val responseCode = response.code
                        val responseBody = response.body?.string() ?: ""
                        Log.d(TAG, "Response code: $responseCode, body: $responseBody")
                        
                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful) {
                                isFavorite = false
                                updateFavoriteButtonUI()
                                Toast.makeText(this@PdfReaderActivity, "Удалено из избранного", Toast.LENGTH_SHORT).show()
                                
                                // Отправляем событие об удалении из избранного
                                EventBus.getDefault().post(FavoriteUpdateEvent(false))
                            } else {
                                Toast.makeText(this@PdfReaderActivity, "Ошибка при удалении из избранного: $responseCode", Toast.LENGTH_SHORT).show()
                                Log.e(TAG, "Failed to remove from favorites: $responseCode, message: $responseBody")
                            }
                        }
                    }
                } catch (e: java.net.ConnectException) {
                    Log.e(TAG, "Server connection error", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfReaderActivity, "Сервер недоступен, попробуйте зайти в другое время", Toast.LENGTH_LONG).show()
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    Log.e(TAG, "Server timeout error", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PdfReaderActivity, "Сервер не отвечает, попробуйте зайти в другое время", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing from favorites", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PdfReaderActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateFavoriteButtonUI() {
        try {
            favoriteButton.setColorFilter(
                if (isFavorite) android.graphics.Color.YELLOW 
                else android.graphics.Color.TRANSPARENT
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating favorite button UI", e)
        }
    }

    private fun downloadAndOpenPdf(pdfUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = pdfUrl.substringAfterLast("/")
                val cachedFile = File(cacheDir, fileName)

                if (cachedFile.exists() && cachedFile.length() > 0) {
                    Log.d(TAG, "Using cached file: ${cachedFile.absolutePath}")
                    withContext(Dispatchers.Main) {
                        pdfFile = cachedFile
                        openPdfFile()
                    }
                    return@launch
                }

                Log.d(TAG, "Starting download from URL: $pdfUrl")
                val request = Request.Builder()
                    .url(pdfUrl)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseCode = response.code
                    Log.d(TAG, "Download response code: $responseCode")
                    
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PdfReaderActivity, "Ошибка загрузки: $responseCode", Toast.LENGTH_SHORT).show()
                        }
                        throw IOException("Download failed with code: ${response.code}")
                    }

                    val body = response.body ?: throw IOException("Empty response body")
                    val contentLength = body.contentLength()
                    Log.d(TAG, "File size to download: $contentLength bytes")

                    FileOutputStream(cachedFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var totalBytesRead = 0L

                        body.byteStream().use { input ->
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                val progress = if (contentLength > 0) {
                                    (totalBytesRead * 100 / contentLength).toInt()
                                } else {
                                    -1
                                }
                                if (progress % 10 == 0 || totalBytesRead == contentLength) {
                                    Log.d(TAG, "Download progress: $progress% ($totalBytesRead/$contentLength bytes)")
                                }
                            }
                        }
                    }

                    Log.d(TAG, "Download completed, file saved to: ${cachedFile.absolutePath}")
                    
                    // Если до сих пор не имеем ID, попробуем найти его по URL
                    if (currentPdfId == null) {
                        Log.d(TAG, "No ID yet, checking if file exists on server")
                        // Проверим, существует ли такой файл на сервере
                        val fileListUrl = getServerUrl() + "/directories/root/files"
                        val listRequest = Request.Builder()
                            .url(fileListUrl)
                            .get()
                            .build()
                            
                        client.newCall(listRequest).execute().use { listResponse ->
                            if (listResponse.isSuccessful) {
                                val listBody = listResponse.body?.string() ?: "[]"
                                val jsonArray = JSONObject("{ \"files\": $listBody }").getJSONArray("files")
                                val fileNameToMatch = fileName
                                
                                for (i in 0 until jsonArray.length()) {
                                    val file = jsonArray.getJSONObject(i)
                                    val fileUrl = file.getString("fileUrl")
                                    if (fileUrl.endsWith(fileNameToMatch)) {
                                        currentPdfId = file.getString("id")
                                        Log.d(TAG, "Found file ID: $currentPdfId for URL: $fileUrl")
                                        withContext(Dispatchers.Main) {
                                            checkFavoriteStatus(currentPdfId!!)
                                        }
                                        break
                                    }
                                }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (cachedFile.exists() && cachedFile.length() > 0) {
                        Log.d(TAG, "Opening downloaded file")
                        pdfFile = cachedFile
                        openPdfFile()
                    } else {
                        Log.e(TAG, "Downloaded file is invalid")
                        finish()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error downloading PDF", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PdfReaderActivity, "Error downloading PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun openPdfFile() {
        try {
            pdfFile?.let { file ->
                if (!file.exists()) {
                    Log.e(TAG, "PDF file does not exist: ${file.path}")
                    Toast.makeText(this, "PDF file not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }

                Log.d(TAG, "Opening PDF file: ${file.absolutePath}, size: ${file.length()} bytes")
                pdfView.loadPdf(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening PDF file", e)
            Toast.makeText(this, "Error opening PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private suspend fun updateFileDirectory(fileId: String, foundInDirectoryId: String?) {
        if (foundInDirectoryId == null || foundInDirectoryId == "root") {
            // Не нужно обновлять, если директория root или не указана
            return
        }

        try {
            // Обновляем directoryId файла на сервере
            val url = getServerUrl() + "/files/$fileId/move"
            Log.d(TAG, "Updating file directoryId to: $foundInDirectoryId (file ID: $fileId)")
            
            // Создаем JSON с новой директорией
            val jsonBody = JSONObject().apply {
                put("newDirectoryId", foundInDirectoryId)
            }
            
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseCode = response.code
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Directory update response: $responseCode, body: $responseBody")
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to update file directory: $responseCode, $responseBody")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating file directory", e)
        }
    }

    private fun showOptionsMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.pdf_reader_options_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> {
                    downloadCurrentPdf()
                    true
                }
                R.id.action_share -> {
                    shareCurrentPdf()
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }

    private fun downloadCurrentPdf() {
        try {
            // Используем корректные ключи и проверяем локальный файл
            val pdfUrl = intent.getStringExtra(EXTRA_PDF_FILE_PATH)
            val fileName = intent.getStringExtra(EXTRA_PDF_FILE_NAME) ?: "document.pdf"
            
            if (pdfUrl == null) {
                Log.e(TAG, "URL файла отсутствует")
                Snackbar.make(binding.root, "Не удалось получить URL файла", Snackbar.LENGTH_LONG).show()
                return
            }
            
            // Сначала проверим, есть ли у нас уже локальный файл
            if (pdfFile != null && pdfFile!!.exists()) {
                // Копируем файл в папку загрузок
                val targetFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                pdfFile!!.copyTo(targetFile, overwrite = true)
                
                Snackbar.make(binding.root, "Файл сохранен в папку загрузок", Snackbar.LENGTH_LONG).show()
                return
            }

            val request = DownloadManager.Request(Uri.parse(pdfUrl))
                .setTitle("Загрузка $fileName")
                .setDescription("Загрузка PDF файла")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            
            Snackbar.make(binding.root, "Загрузка файла начата", Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при загрузке файла", e)
            Snackbar.make(binding.root, "Ошибка при загрузке файла: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun shareCurrentPdf() {
        try {
            // Если у нас уже есть локальный файл, используем его
            if (pdfFile != null && pdfFile!!.exists()) {
                shareLocalFile(pdfFile!!, currentFileName ?: "document.pdf")
                return
            }
            
            val pdfUrl = intent.getStringExtra(EXTRA_PDF_FILE_PATH)
            val fileName = currentFileName ?: intent.getStringExtra(EXTRA_PDF_FILE_NAME) ?: "document.pdf"
            
            if (pdfUrl == null) {
                Log.e(TAG, "URL файла отсутствует")
                Snackbar.make(binding.root, "Не удалось получить URL файла", Snackbar.LENGTH_LONG).show()
                return
            }

            // Показываем индикатор загрузки
            val progressDialog = ProgressDialog(this).apply {
                setTitle("Подготовка файла")
                setMessage("Пожалуйста, подождите...")
                setCancelable(false)
                show()
            }

            // Скачиваем файл во временную директорию
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val tempFile = File(cacheDir, fileName)
                    
                    val request = Request.Builder()
                        .url(pdfUrl)
                        .build()
                    
                    val response = client.newCall(request).execute()
                    
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            shareLocalFile(tempFile, fileName)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            Snackbar.make(binding.root, 
                                "Ошибка при загрузке файла: ${response.code}", 
                                Snackbar.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при шеринге файла", e)
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Snackbar.make(binding.root, 
                            "Ошибка при загрузке файла: ${e.message}", 
                            Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при подготовке к шерингу", e)
            Snackbar.make(binding.root, "Ошибка: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }
    
    private fun shareLocalFile(file: File, fileName: String) {
        try {
            // Создаем URI с использованием FileProvider
            val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }
            
            // Создаем интент для шеринга
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, fileUri)
                type = "application/pdf"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Поделиться файлом"))
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при шеринге локального файла", e)
            Snackbar.make(binding.root, "Ошибка при отправке файла: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}





