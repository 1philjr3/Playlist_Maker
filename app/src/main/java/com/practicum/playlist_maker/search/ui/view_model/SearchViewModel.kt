package com.practicum.playlist_maker.search.ui.view_model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.practicum.playlist_maker.pdfreader.model.PdfNotesResponse
import com.practicum.playlist_maker.pdfreader.repository.PDFRepository
import com.practicum.playlist_maker.pdfreader.state.ResponseState
import com.practicum.playlist_maker.search.data.api.DirectoryResponse
import com.practicum.playlist_maker.search.data.api.PdfServerApiService
import com.practicum.playlist_maker.search.data.api.SearchResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import retrofit2.HttpException
import java.io.IOException

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pdfRepository: PDFRepository,
    private val pdfServerApiService: PdfServerApiService
) : ViewModel() {

    // LiveData для локального поиска
    private val _pdfListResponse = MutableStateFlow<ResponseState>(ResponseState.Loading)
    val pdfListResponse: StateFlow<ResponseState> = _pdfListResponse

    // LiveData для истории поиска
    private val _searchHistoryLiveData = MutableLiveData<List<String>>()
    val searchHistoryLiveData: LiveData<List<String>> = _searchHistoryLiveData

    // LiveData для результатов поиска на сервере
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _directories = MutableLiveData<List<DirectoryResponse>>()
    val directories: LiveData<List<DirectoryResponse>> = _directories

    private val _searchResults = MutableLiveData<List<SearchResponse>>()
    val searchResults: LiveData<List<SearchResponse>> = _searchResults

    // Текущий путь поиска
    private var currentPath: String? = null

    // Временное хранилище для истории поиска (в реальном приложении это должно быть в хранилище)
    private val searchHistory = mutableListOf<String>()
    
    init {
        getSearchHistory()
    }

    // Получение всех PDF файлов из избранного
    fun getFavoritePdfs() {
        viewModelScope.launch(Dispatchers.IO) {
            _pdfListResponse.value = ResponseState.Loading
            val response = pdfRepository.getFavoritePdfs()
            _pdfListResponse.value = response
        }
    }

    // Получение истории поиска
    fun getSearchHistory() {
        _searchHistoryLiveData.value = searchHistory.toList()
    }

    // Добавление запроса в историю поиска
    fun addToSearchHistory(query: String) {
        // Если запрос уже есть в истории, удаляем его (чтобы переместить в начало списка)
        if (searchHistory.contains(query)) {
            searchHistory.remove(query)
        }
        
        // Добавляем запрос в начало списка
        searchHistory.add(0, query)
        
        // Ограничиваем количество элементов в истории
        if (searchHistory.size > 10) {
            searchHistory.removeAt(searchHistory.size - 1)
        }
        
        // Обновляем LiveData
        _searchHistoryLiveData.value = searchHistory.toList()
    }

    // Очистка истории поиска
    fun clearSearchHistory() {
        searchHistory.clear()
        _searchHistoryLiveData.value = searchHistory.toList()
    }

    // Загрузка всех директорий с сервера
    fun loadDirectories() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val result = pdfServerApiService.getDirectories()
                _directories.value = result
                currentPath = null
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Поиск файлов на сервере
    fun searchFiles(query: String, path: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            currentPath = path
            
            try {
                val result = if (query.isEmpty() && path != null) {
                    // Если запрос пустой, но есть путь - получаем содержимое директории
                    pdfServerApiService.getDirectoryContent(path)
                } else {
                    // Иначе выполняем поиск
                    pdfServerApiService.searchFiles(query, path)
                }
                
                _searchResults.value = result
                
                // Если это новый поисковый запрос (не навигация по директориям), добавляем его в историю
                if (query.isNotEmpty()) {
                    addToSearchHistory(query)
                }
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Обработка ошибок
    private fun handleError(error: Exception) {
        val errorMessage = when (error) {
            is HttpException -> "Ошибка сервера: ${error.message()}"
            is IOException -> "Проверьте подключение к интернету"
            else -> "Произошла ошибка: ${error.message}"
        }
        _error.value = errorMessage
    }
}
