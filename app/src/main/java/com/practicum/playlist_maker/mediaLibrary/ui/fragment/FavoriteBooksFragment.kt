package com.practicum.playlist_maker.mediaLibrary.ui.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.databinding.FragmentFavoriteBooksBinding
import com.practicum.playlist_maker.mediaLibrary.model.FavoriteFile
import com.practicum.playlist_maker.mediaLibrary.ui.adapter.FavoriteFilesAdapter
import com.practicum.playlist_maker.pdfreader.model.FavoriteUpdateEvent
import com.practicum.playlist_maker.pdfreader.ui.activity.reader.PdfReaderActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FavoriteBooksFragment : Fragment() {

    private var _binding: FragmentFavoriteBooksBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: FavoriteFilesAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var serverErrorView: TextView
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBooksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerView = binding.recyclerViewFavorites
        emptyView = binding.emptyFavoritesMessage
        serverErrorView = binding.serverErrorMessage
        
        setupRecyclerView()
        loadFavorites()
    }
    
    private fun setupRecyclerView() {
        adapter = FavoriteFilesAdapter { favoriteFile ->
            openPdfFile(favoriteFile)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }
    
    private fun openPdfFile(favoriteFile: FavoriteFile) {
        val intent = Intent(requireContext(), PdfReaderActivity::class.java).apply {
            putExtra(PdfReaderActivity.EXTRA_PDF_FILE_PATH, favoriteFile.fileUrl)
            putExtra(PdfReaderActivity.EXTRA_PDF_FILE_NAME, favoriteFile.name)
            putExtra(PdfReaderActivity.EXTRA_PDF_ID, favoriteFile.id)
        }
        startActivity(intent)
    }
    
    private fun getServerUrl(): String {
        val prefs = requireContext().getSharedPreferences("PROVITA_SETTINGS", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("SERVER_URL", null)
        
        // Если есть сохраненный URL, используем его
        if (!savedUrl.isNullOrEmpty()) {
            Log.d("FavoriteBooksFragment", "Используем сохраненный URL сервера: $savedUrl")
            return savedUrl
        }
        
        // Всегда используем URL для эмулятора
        Log.d("FavoriteBooksFragment", "Используем URL для эмулятора: http://10.0.2.2:3000")
        return "http://10.0.2.2:3000"
    }
    
    private fun loadFavorites() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Получаем user-id из SharedPreferences
                val userId = getUserIdFromSharedPreferences()
                Log.d(TAG, "Loading favorites for user: $userId")

                val request = Request.Builder()
                    .url("${getServerUrl()}/favorites")
                    .addHeader("user-id", userId) // Добавляем заголовок user-id
                    .get()
                    .build()
                
                try {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()
                            val favoriteFiles = parseFavoriteFiles(responseBody)
                            
                            withContext(Dispatchers.Main) {
                                if (favoriteFiles.isEmpty()) {
                                    showEmptyState()
                                } else {
                                    showContent(favoriteFiles)
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                showServerError()
                            }
                        }
                    }
                } catch (e: java.net.ConnectException) {
                    Log.e(TAG, "Server connection error", e)
                    withContext(Dispatchers.Main) {
                        showServerError()
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    Log.e(TAG, "Server timeout error", e)
                    withContext(Dispatchers.Main) {
                        showServerError()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading favorites", e)
                withContext(Dispatchers.Main) {
                    showServerError()
                }
            }
        }
    }
    
    private fun parseFavoriteFiles(json: String?): List<FavoriteFile> {
        if (json.isNullOrEmpty()) {
            return emptyList()
        }
        
        try {
            val favoriteFiles = mutableListOf<FavoriteFile>()
            val jsonArray = JSONArray(json)
            
            // Получаем словарь директорий для построения путей
            val directoriesMap = try {
                fetchDirectoriesMap()
            } catch (e: Exception) {
                Log.e("FavoriteBooksFragment", "Ошибка при получении директорий: ${e.message}")
                mapOf()
            }
            
            for (i in 0 until jsonArray.length()) {
                try {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val id = jsonObject.getString("id")
                    val name = jsonObject.getString("name")
                    val fileUrl = jsonObject.getString("fileUrl")
                    val directoryId = jsonObject.optString("directoryId", "root")
                    
                    // Получаем путь к директории
                    val directoryPath = if (directoryId == "root") {
                        "root"
                    } else {
                        directoriesMap[directoryId] ?: "Неизвестная директория"
                    }
                    
                    val addedToFavoritesAt = jsonObject.optString("addedToFavoritesAt", "")
                    
                    val favoriteFile = FavoriteFile(
                        id = id,
                        name = name,
                        fileUrl = fileUrl,
                        directoryPath = directoryPath,
                        addedToFavoritesAt = addedToFavoritesAt
                    )
                    
                    favoriteFiles.add(favoriteFile)
                } catch (e: Exception) {
                    Log.e("FavoriteBooksFragment", "Ошибка при парсинге файла: ${e.message}")
                    // Пропускаем проблемный файл и продолжаем
                }
            }
            
            return favoriteFiles
        } catch (e: Exception) {
            Log.e("FavoriteBooksFragment", "Ошибка при парсинге файлов: ${e.message}")
            return emptyList()
        }
    }
    
    // Получаем список всех директорий с их именами
    private fun fetchDirectoriesMap(): Map<String, String> {
        val directoriesMap = mutableMapOf<String, String>()
        
        try {
            val request = Request.Builder()
                .url("${getServerUrl()}/directories")
                .build()
                
            val client = OkHttpClient()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (!responseBody.isNullOrEmpty()) {
                    val jsonArray = JSONArray(responseBody)
                    
                    // Сначала собираем базовую информацию о директориях
                    val dirInfos = mutableMapOf<String, Pair<String, String?>>() // id -> (name, parentId)
                    
                    for (i in 0 until jsonArray.length()) {
                        val directory = jsonArray.getJSONObject(i)
                        val id = directory.getString("id")
                        val name = directory.getString("name")
                        val parentId = if (directory.has("parentId") && !directory.isNull("parentId")) 
                            directory.getString("parentId") else null
                        
                        dirInfos[id] = Pair(name, parentId)
                    }
                    
                    // Теперь строим полные пути
                    for (id in dirInfos.keys) {
                        directoriesMap[id] = buildDirectoryPath(id, dirInfos)
                    }
                }
            } else {
                Log.e("FavoriteBooksFragment", "Ошибка при получении директорий: ${response.code}")
            }
        } catch (e: java.net.ConnectException) {
            Log.e("FavoriteBooksFragment", "Сервер недоступен при получении директорий", e)
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("FavoriteBooksFragment", "Таймаут при получении директорий", e)
            throw e
        } catch (e: Exception) {
            Log.e("FavoriteBooksFragment", "Ошибка при получении директорий: ${e.message}", e)
            throw e
        }
        
        return directoriesMap
    }
    
    // Строим полный путь к директории
    private fun buildDirectoryPath(directoryId: String, dirInfos: Map<String, Pair<String, String?>>): String {
        val pathParts = mutableListOf<String>()
        var currentId = directoryId
        
        // Предотвращаем бесконечные циклы
        val visitedIds = mutableSetOf<String>()
        
        while (currentId != "root" && dirInfos.containsKey(currentId) && !visitedIds.contains(currentId)) {
            visitedIds.add(currentId)
            
            val dirInfo = dirInfos[currentId] ?: break
            pathParts.add(0, dirInfo.first) // Добавляем имя директории в начало списка
            
            // Переходим к родительской директории или выходим из цикла
            currentId = dirInfo.second ?: "root"
        }
        
        return if (pathParts.isEmpty()) "Неизвестная директория" else pathParts.joinToString(" / ")
    }
    
    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        serverErrorView.visibility = View.GONE
    }
    
    private fun showContent(favoriteFiles: List<FavoriteFile>) {
        recyclerView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        serverErrorView.visibility = View.GONE
        adapter.submitList(favoriteFiles)
    }
    
    private fun showServerError() {
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE
        serverErrorView.visibility = View.VISIBLE
    }
    
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFavoriteUpdate(event: FavoriteUpdateEvent) {
        // Перезагружаем список избранного при получении события
        loadFavorites()
    }
    
    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }
    
    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }
    
    override fun onResume() {
        super.onResume()
        // Обновляем список при возвращении к фрагменту
        loadFavorites()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // Получение идентификатора пользователя из SharedPreferences
    private fun getUserIdFromSharedPreferences(): String {
        val sharedPreferences = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("email", "anonymous") ?: "anonymous"
    }
    
    companion object {
        private const val TAG = "FavoriteBooksFragment"
        
        fun newInstance() = FavoriteBooksFragment()
    }
} 