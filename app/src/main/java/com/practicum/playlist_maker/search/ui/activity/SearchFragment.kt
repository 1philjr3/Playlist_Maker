package com.practicum.playlist_maker.search.ui.activity

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.databinding.FragmentSearchBinding
import com.practicum.playlist_maker.pdfreader.adapter.PdfListAdapter
import com.practicum.playlist_maker.pdfreader.model.PdfNoteListModel
import com.practicum.playlist_maker.pdfreader.model.PdfNotesResponse
import com.practicum.playlist_maker.pdfreader.state.ResponseState
import com.practicum.playlist_maker.pdfreader.ui.activity.add.AddPdfActivity
import com.practicum.playlist_maker.pdfreader.ui.activity.home.HomeViewModel
import com.practicum.playlist_maker.pdfreader.ui.activity.reader.PdfReaderActivity
import com.practicum.playlist_maker.pdfreader.ui.fragment.MoreOptionModel
import com.practicum.playlist_maker.pdfreader.ui.fragment.OptionPickFragment
import com.practicum.playlist_maker.pdfreader.utils.Alerts
import com.practicum.playlist_maker.pdfreader.utils.BundleArguments
import com.practicum.playlist_maker.pdfreader.utils.log
import com.practicum.playlist_maker.search.data.api.DirectoryResponse
import com.practicum.playlist_maker.search.data.api.SearchResponse
import com.practicum.playlist_maker.search.data.api.toSearchResponse
import com.practicum.playlist_maker.search.ui.adapter.SearchResultAdapter
import com.practicum.playlist_maker.search.ui.SearchHistoryAdapter
import com.practicum.playlist_maker.search.ui.view_model.SearchViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SearchFragment : Fragment(), View.OnClickListener, OptionPickFragment.Listener, PdfListAdapter.Listener {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SearchViewModel by viewModels()

    private var adapter: PdfListAdapter? = null
    private var historyAdapter: SearchHistoryAdapter? = null
    private var pdfList = arrayListOf<PdfNoteListModel>()
    private var pdfListAll = arrayListOf<PdfNoteListModel>()
    private var searchHistory = arrayListOf<String>()

    private lateinit var unifiedSearchAdapter: SearchResultAdapter
    
    // Для дебаунсинга поискового запроса
    private var searchJob: Job? = null
    private val SEARCH_DEBOUNCE_DELAY = 300L  // миллисекунды

    // Режимы поиска
    private enum class SearchMode {
        LOCAL_FAVORITES, // Поиск в избранном локально
        SERVER // Поиск через сервер
    }
    
    private var currentSearchMode = SearchMode.SERVER // Устанавливаем серверный поиск по умолчанию

    private var isViewingDirectory = false // Флаг для отслеживания режима просмотра директории

    companion object {
        private const val FROM_GALLERY = 1
        private const val DOWNLOAD_PDF = 2
    }

    private val addPdfLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                if (it.data?.action == AddPdfActivity.RESULT_ACTION_PDF_ADDED) {
                    Alerts.successSnackBar(binding.root, "Note added successfully.")
                    loadCurrentData()
                }
            }
        }

    private val pdfReaderLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result != null && result.resultCode == RESULT_OK) {
                if (result.data?.action == PdfReaderActivity.RESULT_ACTION_PDF_DELETED) {
                    Alerts.successSnackBar(binding.root, "Pdf Deleted")
                    loadCurrentData()
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        enableEdgeToEdge()
        setupInsets()
        setupUnifiedSearchAdapter()
        initView()
        bindUI()
        setupObservers()
        
        loadCurrentData()
        viewModel.getSearchHistory()
        
        // Устанавливаем серверный режим поиска по умолчанию
        updateUIForSearchMode()
        
        binding.tvTitle.setOnClickListener {
            toggleSearchMode()
        }
    }
    
    private fun setupUnifiedSearchAdapter() {
        unifiedSearchAdapter = SearchResultAdapter { searchResult ->
            when (searchResult.type) {
                "pdf" -> openPdfFromServer(searchResult)
                "directory" -> {
                    // При клике на директорию ищем все файлы в этой директории
                    // НЕ очищаем поле поиска
                    // binding.etSearch.setText("")
                    
                    // Устанавливаем флаг просмотра директории
                    isViewingDirectory = true
                    
                    // Запрашиваем содержимое директории
                    viewModel.searchFiles("", searchResult.path)
                    
                    // Показываем путь в заголовке
                    binding.tvTitle.text = "Файлы в: ${searchResult.name}"
                }
                else -> {
                    Toast.makeText(context, "Файл: ${searchResult.name} (${searchResult.type})", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.unifiedSearchResultsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.unifiedSearchResultsRecyclerView.adapter = unifiedSearchAdapter
    }
    
    private fun loadCurrentData() {
        when (currentSearchMode) {
            SearchMode.LOCAL_FAVORITES -> viewModel.getFavoritePdfs()
            SearchMode.SERVER -> {
                // При первом входе на экран поиска не загружаем директории
                // viewModel.loadDirectories()
            }
        }
    }

    private fun toggleSearchMode() {
        currentSearchMode = when (currentSearchMode) {
            SearchMode.LOCAL_FAVORITES -> SearchMode.SERVER
            SearchMode.SERVER -> SearchMode.LOCAL_FAVORITES
        }
        updateUIForSearchMode()
        loadCurrentData()
    }

    private fun updateUIForSearchMode() {
        when (currentSearchMode) {
            SearchMode.SERVER -> {
                binding.tvTitle.text = "Поиск на сервере"
                binding.unifiedSearchResultsRecyclerView.visibility = View.GONE // При первом входе скрываем результаты
                
                binding.rvPdfList.visibility = View.GONE
                binding.rvSearchHistory.visibility = View.GONE
                binding.tvHistoryTitle.visibility = View.GONE
                binding.btnClearHistory.visibility = View.GONE
                binding.errorText.visibility = View.GONE // Скрываем сообщение об ошибке при первом входе
            }
            SearchMode.LOCAL_FAVORITES -> {
                binding.tvTitle.text = "Избранное"
                binding.rvPdfList.visibility = View.VISIBLE
                
                // Не показываем историю поиска при первом входе
                binding.rvSearchHistory.visibility = View.GONE
                binding.tvHistoryTitle.visibility = View.GONE
                binding.btnClearHistory.visibility = View.GONE
                
                binding.unifiedSearchResultsRecyclerView.visibility = View.GONE
            }
        }
    }

    private fun enableEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initView() {
        adapter = PdfListAdapter(pdfList, this)
        binding.rvPdfList.adapter = adapter
        binding.rvPdfList.layoutManager = LinearLayoutManager(requireContext())
        
        historyAdapter = SearchHistoryAdapter(searchHistory) { query ->
            binding.etSearch.setText(query)
            performSearch(query)
        }
        
        binding.rvSearchHistory.adapter = historyAdapter
        binding.rvSearchHistory.layoutManager = LinearLayoutManager(requireContext())
        
        binding.etSearch.doAfterTextChanged { text ->
            searchJob?.cancel()
            
            if (text.isNullOrEmpty()) {
                if (currentSearchMode == SearchMode.LOCAL_FAVORITES) {
                    showSearchHistory()
                    filterPdfList("")
                } else {
                    // Для серверного поиска - показываем все директории
                    viewModel.loadDirectories()
                }
            } else {
                val query = text.toString().trim()
                
                // Используем дебаунсинг для предотвращения слишком частых запросов
                searchJob = lifecycleScope.launch {
                    delay(SEARCH_DEBOUNCE_DELAY)
                    performSearch(query)
                }
            }
        }
        
        binding.btSearch.setOnClickListener { 
            val query = binding.etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }
        
        binding.btnClearHistory.setOnClickListener {
            viewModel.clearSearchHistory()
            searchHistory.clear()
            historyAdapter?.notifyDataSetChanged()
            if (currentSearchMode == SearchMode.LOCAL_FAVORITES && binding.etSearch.text.isNullOrEmpty()) {
                showSearchHistory()
            }
        }
    }
    
    private fun performSearch(query: String) {
        if (query.isNotEmpty()) {
            viewModel.addToSearchHistory(query)
        }
        
        // Сбрасываем флаг просмотра директории при новом поиске
        isViewingDirectory = false
        
        when (currentSearchMode) {
            SearchMode.SERVER -> {
                viewModel.searchFiles(query)
            }
            SearchMode.LOCAL_FAVORITES -> {
                hideSearchHistory()
                filterPdfList(query)
            }
        }
    }
    
    private fun openPdfFromServer(result: SearchResponse) {
        // Строим полный URL к файлу
        val serverUrl = "http://10.0.2.2:3000" // URL сервера для эмулятора
        val fullPath = if (result.path.startsWith("http")) {
            // Если путь уже полный URL
            result.path
        } else {
            // Если путь относительный, добавляем базовый URL сервера
            "$serverUrl/uploads/pdfs/${result.path}"
        }
        
        Log.d("SearchFragment", "Opening PDF from server: $fullPath")
        
        val launchIntent = Intent(requireContext(), PdfReaderActivity::class.java).apply {
            putExtra(PdfReaderActivity.EXTRA_PDF_FILE_PATH, fullPath)
            putExtra(PdfReaderActivity.EXTRA_PDF_FILE_NAME, result.name)
        }
        pdfReaderLauncher.launch(launchIntent)
    }
    
    private fun showSearchHistory() {
        if (searchHistory.isNotEmpty()) {
            binding.rvSearchHistory.visibility = View.VISIBLE
            binding.rvPdfList.visibility = View.GONE
            binding.tvHistoryTitle.visibility = View.VISIBLE
            binding.btnClearHistory.visibility = View.VISIBLE
        } else {
            binding.rvSearchHistory.visibility = View.GONE
            binding.tvHistoryTitle.visibility = View.GONE
            binding.btnClearHistory.visibility = View.GONE
            binding.rvPdfList.visibility = View.VISIBLE
        }
    }
    
    private fun hideSearchHistory() {
        binding.rvSearchHistory.visibility = View.GONE
        binding.tvHistoryTitle.visibility = View.GONE
        binding.rvPdfList.visibility = View.VISIBLE
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btBack -> {
                requireActivity().onBackPressed()
            }
            R.id.btAddNewPdf -> {
                showAddOptions()
            }
        }
    }

    private fun bindUI() {
        // Используем lifecycleScope.launch для наблюдения за StateFlow
        lifecycleScope.launch {
            viewModel.pdfListResponse.collectLatest { state ->
                when (state) {
                    is ResponseState.Failed -> {
                        binding.progressBar.visibility = View.GONE
                        setError(state.error)
                    }

                    ResponseState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }

                    is ResponseState.Success<*> -> {
                        binding.progressBar.visibility = View.GONE
                        val pdfListResponse = state.response as PdfNotesResponse?

                        pdfListResponse?.let {
                            withContext(Dispatchers.Main) {
                                pdfListAll.clear()
                                pdfListAll.addAll(it.notes)
                                pdfList.clear()
                                pdfList.addAll(pdfListAll)
                                adapter?.notifyDataSetChanged()
                            }
                        }

                        // При пустом списке ничего не показываем
                        if (pdfList.isEmpty() && !binding.etSearch.text.isNullOrEmpty()) {
                            setError("такого не существует :(")
                        } else {
                            setError(null)
                        }
                    }

                    is ResponseState.ValidationError -> {
                        // Handle validation errors if needed
                    }
                }
            }
        }
        
        viewModel.searchHistoryLiveData.observe(viewLifecycleOwner) { history ->
            searchHistory.clear()
            searchHistory.addAll(history)
            historyAdapter?.notifyDataSetChanged()
            // Не показываем историю при первом входе
            // if (currentSearchMode == SearchMode.LOCAL_FAVORITES && binding.etSearch.text.isNullOrEmpty()) {
            //     showSearchHistory()
            // }
        }
    }

    private fun setupObservers() {
        // Наблюдаем за директориями
        viewModel.directories.observe(viewLifecycleOwner) { directories ->
            // Показываем директории только если был введен поисковый запрос
            if (!binding.etSearch.text.isNullOrEmpty() || isViewingDirectory) {
                // Преобразуем директории в формат SearchResponse для унифицированного отображения
                val searchResults = directories.map { it.toSearchResponse() }
                unifiedSearchAdapter.submitList(searchResults)
                
                // Обновляем заголовок если мы не в режиме просмотра директории
                if (currentSearchMode == SearchMode.SERVER && !isViewingDirectory) {
                    binding.tvTitle.text = "Поиск на сервере"
                }
                
                // Показываем RecyclerView
                binding.unifiedSearchResultsRecyclerView.visibility = View.VISIBLE
                
                // Скрываем сообщение об ошибке, если есть директории
                if (directories.isNotEmpty()) {
                    binding.errorText.visibility = View.GONE
                }
            } else {
                // Скрываем список, если поисковый запрос пуст и нет результатов
                binding.unifiedSearchResultsRecyclerView.visibility = View.GONE
                unifiedSearchAdapter.submitList(emptyList())
            }
        }

        // Наблюдаем за результатами поиска
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            if (!binding.etSearch.text.isNullOrEmpty() || results.isNotEmpty() || isViewingDirectory) {
                unifiedSearchAdapter.submitList(results)
                binding.unifiedSearchResultsRecyclerView.visibility = View.VISIBLE
                
                if (results.isEmpty() && !isViewingDirectory) {
                    // Если результатов нет и мы не просматриваем директорию, показать сообщение
                    binding.errorText.text = "такого не существует :("
                    binding.errorText.visibility = View.VISIBLE
                } else {
                    binding.errorText.visibility = View.GONE
                }
            } else {
                // Скрываем список, если поисковый запрос пуст и нет результатов
                binding.unifiedSearchResultsRecyclerView.visibility = View.GONE
                binding.errorText.visibility = View.GONE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (currentSearchMode == SearchMode.SERVER) {
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (currentSearchMode == SearchMode.SERVER) {
                error?.let {
                    binding.errorText.text = "такого не существует :("
                    binding.errorText.visibility = View.VISIBLE
                } ?: run {
                    binding.errorText.visibility = View.GONE
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterPdfList(key: String?) {
        pdfList.clear()
        if (key.isNullOrEmpty()) {
            pdfList.addAll(pdfListAll)
        } else {
            pdfList.addAll(pdfListAll.filter {
                it.title.uppercase().contains(key.uppercase()) ||
                        it.tag?.title?.uppercase()?.contains(key.uppercase()) ?: false ||
                        it.filePath.uppercase().contains(key.uppercase()) ||  // Добавлен поиск по пути файла
                        it.about?.uppercase()?.contains(key.uppercase()) ?: false // Добавлен поиск по описанию
            })
        }
        adapter?.notifyDataSetChanged()

        if (pdfList.isEmpty() && !key.isNullOrEmpty()) {
            setError("такого не существует :(")
        } else {
            setError(null)
        }
    }

    private fun setError(error: String?) {
        if (error.isNullOrEmpty()) {
            binding.tvError.visibility = View.GONE
        } else {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = error
        }
    }

    private fun showAddOptions() {
        val options = arrayListOf(
            MoreOptionModel(FROM_GALLERY, "Pick From Gallery"),
            MoreOptionModel(DOWNLOAD_PDF, "Download PDF")
        )
        OptionPickFragment.show(
            parentFragmentManager,
            "Add New PDF",
            options
        )
    }

    override fun onMoreOptionSelected(option: MoreOptionModel) {
        val launchIntent = Intent(requireContext(), AddPdfActivity::class.java)
        val pageType = if (option.id == FROM_GALLERY) {
            AddPdfActivity.PageType.PickFromGallery
        } else {
            AddPdfActivity.PageType.DownloadPdf
        }
        launchIntent.putExtra(BundleArguments.ARGS_PAGE_TYPE, pageType.name)
        addPdfLauncher.launch(launchIntent)
    }

    override fun onPdfItemClicked(pdf: PdfNoteListModel) {
        Log.d("SearchFragment", "PDF model: id=${pdf.id}, title=${pdf.title}, filePath=${pdf.filePath}")
        
        val launchIntent = Intent(requireContext(), PdfReaderActivity::class.java).apply {
            putExtra(PdfReaderActivity.EXTRA_PDF_FILE_PATH, pdf.filePath)
            putExtra(PdfReaderActivity.EXTRA_PDF_FILE_NAME, pdf.title)
        }
        
        Log.d("SearchFragment", "Launching PdfReaderActivity with extras: ${launchIntent.extras}")
        pdfReaderLauncher.launch(launchIntent)
    }

    override fun onPdfItemLongClicked(pdf: PdfNoteListModel) {
        // Handle long click if needed
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
