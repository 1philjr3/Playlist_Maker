class SearchViewModel @Inject constructor(
    private val pdfRepository: PDFRepository
) : ViewModel() {

    val pdfListResponse = OperationsStateHandler(viewModelScope) // обработчик состояния списка

    fun getAllPdfs() { // метод для получения всех
        pdfListResponse.load { // загрузка данных через обработчик
            pdfRepository.getAllPdfs() // вызов метода репозитория для получения данных
        }
    }
}

private val addPdfLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { // регистрация запуска действия с результатом
        if (it.resultCode == RESULT_OK) { // проверка результата
            if (it.data?.action == AddPdfActivity.RESULT_ACTION_PDF_ADDED) { // проверка действия
                Alerts.successSnackBar(binding.root, "Note added successfully.") // отображение уведомления
                viewModel.getAllPdfs() // обновление списка PDF
            }
        }
    }