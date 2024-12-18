package com.practicum.playlist_maker.pdfreader.ui.activity.reader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.pdfreader.base.ui.BaseActivity
import com.practicum.playlist_maker.databinding.ActivityPdfReaderBinding
import com.practicum.playlist_maker.databinding.ContainerPdfReaderBinding
import com.practicum.playlist_maker.pdfreader.model.AnnotationListResponse
import com.practicum.playlist_maker.pdfreader.model.DeleteAnnotationResponse
import com.practicum.playlist_maker.pdfreader.model.PdfNoteListModel
import com.practicum.playlist_maker.pdfreader.model.StatusMessageResponse
import com.practicum.playlist_maker.pdfreader.state.ResponseState
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.PDFView
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.PdfFile
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.BookmarkModel
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.CommentModel
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.Coordinates
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.HighlightModel
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.PdfAnnotationModel
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.TextSelectionData
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.selection.TextSelectionOptionsWindow
import com.practicum.playlist_maker.pdfreader.ui.activity.annotations.bookmarks.BookmarkListActivity
import com.practicum.playlist_maker.pdfreader.ui.activity.annotations.comments.CommentsListActivity
import com.practicum.playlist_maker.pdfreader.ui.activity.annotations.highlight.HighlightListActivity
import com.practicum.playlist_maker.pdfreader.ui.fragment.CommentViewFragment
import com.practicum.playlist_maker.pdfreader.ui.fragment.MoreOptionModel
import com.practicum.playlist_maker.pdfreader.ui.fragment.OptionPickFragment
import com.practicum.playlist_maker.pdfreader.utils.Alerts
import com.practicum.playlist_maker.pdfreader.utils.BundleArguments
import com.practicum.playlist_maker.pdfreader.utils.getParcelableExtraVs
import com.practicum.playlist_maker.pdfreader.utils.log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class PdfReaderActivity : BaseActivity(), View.OnClickListener, OptionPickFragment.Listener,
    CommentViewFragment.Listener {
    private lateinit var binding: ActivityPdfReaderBinding
    private lateinit var contentBinding: ContainerPdfReaderBinding

    // Popup window that show after user selecting a text in pdf
    private var textSelectionOptionWindow: TextSelectionOptionsWindow? = null
    private var pdfRenderScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private var commentListActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ::onCommentListActivityResult
    )
    private var highlightListActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ::onHighlightListActivityResult
    )
    private var bookmarkListActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ::onBookmarkActivityResult
    )


    companion object {
        private const val OPTION_COMMENTS = 1
        private const val OPTION_HIGHLIGHTS = 2
        private const val OPTION_BOOKMARKS = 3
        private const val OPTION_DELETE_PDF = 4

        const val RESULT_ACTION_PDF_DELETED = "action.pdf.deleted"
    }

    private val viewModel: PdfReaderViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPdfReaderBinding.inflate(layoutInflater)
        contentBinding = binding.container
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        getDataFromIntent()
        initView()
        preparePdfView()
    }

    private fun getDataFromIntent() {
        viewModel.pdfDetails = intent?.getParcelableExtraVs(
            BundleArguments.ARGS_PDF_DETAILS,
            PdfNoteListModel::class.java
        )
        viewModel.pdfDetails.log("pdfDetails")
    }

    private fun preparePdfView() {
        contentBinding.pdfView
            .attachCoroutineScope(pdfRenderScope)
            .setListener(pdfCallBack)
        textSelectionOptionWindow = TextSelectionOptionsWindow(this, textSelectionWindowCallback)
        textSelectionOptionWindow?.attachToPdfView(contentBinding.pdfView)
        loadPdf()

    }

    private fun loadPdf() {
        if (!viewModel.pdfDetails?.filePath.isNullOrEmpty()) {
            val file = File(viewModel.pdfDetails?.filePath!!)
            contentBinding.pdfView
                .fromFile(file)
                .defaultPage(0)
                .load()
        } else {
            Toast.makeText(this, "pdf file not found", Toast.LENGTH_SHORT).show()
            finish()
        }

    }

    private fun showLocalNotification(title: String, body: String) {
        val channelId = "comment_action_channel"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existingChannel = notificationManager.getNotificationChannel(channelId)
            if (existingChannel == null) {
                val channel = NotificationChannel(
                    channelId,
                    "Comment Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null) // Использует системный звук уведомлений
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }


    override fun bindUI() = launch(Dispatchers.Main) {
        viewModel.addCommentResponse.state.observe(this@PdfReaderActivity) { state ->
            when (state) {
                is ResponseState.Failed -> {
                    Alerts.failureSnackBar(binding.root, state.error)
                }

                ResponseState.Loading -> {

                }

                is ResponseState.Success<*> -> {
                    val response = state.response as CommentModel?
                    if (response != null) {
                        viewModel.annotations.comments.add(0, response)
                        contentBinding.pdfView.addComment(response)

                        // !!!!!! Отображение уведомления о добавлении комментария
                        showLocalNotification(
                            title = "Новый комментарий",
                            body = "Михаил ПНР добавил комментарий: ${response.text}"
                        )
                    }
                    Alerts.successSnackBar(binding.root, "Успешно!")
                }

                is ResponseState.ValidationError -> {

                }
            }
        }
        viewModel.addHighlightResponse.state.observe(this@PdfReaderActivity) { state ->
            when (state) {
                is ResponseState.Failed -> {
                    Alerts.failureSnackBar(binding.root, state.error)

                }

                ResponseState.Loading -> {

                }

                is ResponseState.Success<*> -> {
                    val response = state.response as HighlightModel?
                    if (response != null) {
                        viewModel.annotations.highlights.add(0, response)
                        contentBinding.pdfView.addHighlight(response)
                    }
                    Alerts.successSnackBar(binding.root, "Highlight added successfully")

                }

                is ResponseState.ValidationError -> {

                }
            }
        }
        viewModel.addBookmarkResponse.state.observe(this@PdfReaderActivity) { state ->
            when (state) {
                is ResponseState.Failed -> {
                    Alerts.failureSnackBar(binding.root, state.error)

                }

                ResponseState.Loading -> {

                }

                is ResponseState.Success<*> -> {
                    val response = state.response as BookmarkModel?
                    if (response != null) {
                        viewModel.annotations.bookmarks.add(response)
                        updateBookmarkInfo(contentBinding.pdfView.currentPage + 1)
                        Alerts.successSnackBar(binding.root, "Добавлено в избранное")

                    }
                }

                is ResponseState.ValidationError -> {

                }
            }
        }
        viewModel.removeBookmarkResponse.state.observe(this@PdfReaderActivity) { state ->
            when (state) {
                is ResponseState.Failed -> {
                    Alerts.failureSnackBar(binding.root, state.error)

                }

                ResponseState.Loading -> {

                }

                is ResponseState.Success<*> -> {
                    val response = state.response as DeleteAnnotationResponse?
                    if (response != null) {
                        viewModel.annotations.bookmarks.removeAll {
                            response.deletedIds.contains(it.id)
                        }
                        updateBookmarkInfo(contentBinding.pdfView.currentPage + 1)
                        Alerts.successSnackBar(binding.root, "Удалено!")

                    }
                }

                is ResponseState.ValidationError -> {

                }
            }
        }
        viewModel.annotationListResponse.state.observe(this@PdfReaderActivity) { state ->
            when (state) {
                is ResponseState.Failed -> {
                    Alerts.failureSnackBar(binding.root, state.error)

                }

                ResponseState.Loading -> {

                }

                is ResponseState.Success<*> -> {
                    val response = state.response as AnnotationListResponse?
                    if (response != null) {
                        viewModel.annotations = response

                        val highlightAndComments = arrayListOf<PdfAnnotationModel>()
                        highlightAndComments.addAll(response.comments)
                        highlightAndComments.addAll(response.highlights)
                        contentBinding.pdfView.loadAnnotations(highlightAndComments)
                    }
                }

                is ResponseState.ValidationError -> {

                }
            }
        }
        viewModel.updateCommentResponse.state.observe(this@PdfReaderActivity) { state ->
            when (state) {
                is ResponseState.Failed -> {
                    Alerts.failureSnackBar(binding.root, state.error)
                }

                ResponseState.Loading -> {

                }

                is ResponseState.Success<*> -> {
                    val response = state.response as CommentModel?
                    if (response != null) {
                        viewModel.annotations.comments.map {
                            if (it.id == response.id) {
                                it.text = response.text
                                it.updatedAt = response.updatedAt
                            }
                        }
                        contentBinding.pdfView.updateComment(response)
                        Alerts.successSnackBar(binding.root, "Comment updated")

                    }
                }

                is ResponseState.ValidationError -> {

                }
            }
        }
        viewModel.deleteCommentResponse.state.observe(this@PdfReaderActivity) { state ->
            when (state) {
                is ResponseState.Failed -> {
                    Alerts.failureSnackBar(binding.root, state.error)
                }

                ResponseState.Loading -> {

                }

                is ResponseState.Success<*> -> {
                    val response = state.response as DeleteAnnotationResponse?
                    if (response != null) {
                        viewModel.removeComments(response.deletedIds)
                        contentBinding.pdfView.removeCommentAnnotations(response.deletedIds)
                        Alerts.successSnackBar(binding.root, "Комментарий удален!")

                    }
                }

                is ResponseState.ValidationError -> {

                }
            }
        }
        viewModel.pdfDeleteResponse.state.observe(this@PdfReaderActivity) { state ->
            when (state) {
                is ResponseState.Failed -> {
                    Alerts.failureSnackBar(binding.root, state.error)
                }

                ResponseState.Loading -> {

                }

                is ResponseState.Success<*> -> {
                    val response = state.response as StatusMessageResponse?
                    if (response != null) {
                        val result = Intent(RESULT_ACTION_PDF_DELETED)
                    }
                }

                is ResponseState.ValidationError -> {

                }
            }
        }
    }

    private val pdfCallBack = object : PDFView.Listener {

        override fun onPreparationStarted() {
            contentBinding.progressBar.visibility = View.VISIBLE
        }

        override fun onPreparationSuccess() {
            viewModel.loadAllAnnotations()
            contentBinding.cbBookmark.visibility = View.VISIBLE
            contentBinding.progressBar.visibility = View.GONE
        }

        override fun onPreparationFailed(error: String, e: Exception?) {
            contentBinding.progressBar.visibility = View.GONE
        }

        override fun onPageChanged(pageIndex: Int, paginationPageIndex: Int) {
            val page = pageIndex + 1
            updateBookmarkInfo(page)
            showPageNumber(page)
        }

        override fun onTextSelected(selection: TextSelectionData, rawPoint: PointF) {
            textSelectionOptionWindow?.show(rawPoint.x, rawPoint.y, selection)
        }

        override fun hideTextSelectionOptionWindow() {
            textSelectionOptionWindow?.dismiss()
        }

        override fun onTextSelectionCleared() {
            textSelectionOptionWindow?.dismiss()
        }

        override fun onNotesStampsClicked(comments: List<CommentModel>, pointOfNote: PointF) {
            if (comments.size == 1) {
                CommentViewFragment.showToCommentView(comments.first(), supportFragmentManager)
            } else {
                openCommentList(comments)
            }
        }

        override fun loadTopPdfChunk(mergeId: Int, pageIndexToLoad: Int) {
            // In future i will add pagination to Pdf reader, to load fast :)
        }

        override fun loadBottomPdfChunk(mergedId: Int, pageIndexToLoad: Int) {
            // In future i will add pagination to Pdf reader, to load fast :)
        }

        override fun onScrolling() {

        }

        override fun onTap() {

        }

        override fun onMergeStart(mergeId: Int, mergeType: PdfFile.MergeType) {
            // In future i will add pagination to Pdf reader, to load fast :)
        }

        override fun onMergeEnd(mergeId: Int, mergeType: PdfFile.MergeType) {
            // In future i will add pagination to Pdf reader, to load fast :)
        }

        override fun onMergeFailed(
            mergeId: Int,
            mergeType: PdfFile.MergeType,
            message: String,
            exception: java.lang.Exception?
        ) {
            // In future i will add pagination to Pdf reader, to load fast :)
        }


    }

    private val textSelectionWindowCallback = object : TextSelectionOptionsWindow.Listener {
        override fun onAddHighlightClick(
            snippet: String,
            color: String,
            page: Int,
            coordinates: Coordinates
        ) {
            viewModel.addHighlight(snippet, color, page, coordinates)
        }

        override fun onAddNotClick(snippet: String, page: Int, coordinates: Coordinates) {
            val commentModel = CommentModel(
                -1, snippet, "", page, 0L, coordinates
            )
            CommentViewFragment.showToCommentAdd(commentModel, supportFragmentManager)
        }
    }


    private fun initView() {
        binding.btBack.setOnClickListener(this)
        binding.btMoreOptions.setOnClickListener(this)
        binding.tvTitle.text = viewModel.pdfDetails?.title ?: ""
        contentBinding.cbBookmark.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btBack -> {
                finish()
            }

            R.id.btMoreOptions -> {
                showMoreOptions()
            }

            R.id.cbBookmark -> {
                val currentPage = contentBinding.pdfView.currentPage + 1
                if (contentBinding.cbBookmark.isChecked) {
                    viewModel.addBookmark(currentPage)
                } else {
                    viewModel.removeBookmark(currentPage)
                }
            }
        }
    }

    private fun showMoreOptions() {
        val options = listOf(
            MoreOptionModel(OPTION_COMMENTS, "${viewModel.annotations.comments.size}  Комментарии"),
            MoreOptionModel(
                OPTION_HIGHLIGHTS,
                "${viewModel.annotations.highlights.size}  Заголовки"
            ),
            MoreOptionModel(OPTION_BOOKMARKS, "${viewModel.annotations.bookmarks.size}  Страницы"),
            MoreOptionModel(OPTION_DELETE_PDF, "Удалить PDF"),
        )
        OptionPickFragment.show(
            supportFragmentManager,
            viewModel.pdfDetails?.title ?: "",
            options
        )
    }

    override fun onMoreOptionSelected(option: MoreOptionModel) {
        when (option.id) {
            OPTION_COMMENTS -> {
                val comments = viewModel.annotations.comments.toList()
                openCommentList(comments)
            }

            OPTION_HIGHLIGHTS -> {
                val highlights = viewModel.annotations.highlights.toList()
                openHighlightList(highlights)
            }

            OPTION_BOOKMARKS -> {
                val bookmarks = viewModel.annotations.bookmarks.toList()
                openBookmarksList(bookmarks)
            }

            OPTION_DELETE_PDF -> {
                viewModel.deletePdf()
            }
        }
    }

    override fun onDeleteCommentClick(commentId: Long) {
        viewModel.deleteComment(commentId)
    }

    override fun onEditCommentSaveClick(commentModel: CommentModel) {
        viewModel.updateComment(commentModel.id, commentModel.text)
    }

    override fun onAddCommentSaveClick(commentModel: CommentModel) {
        textSelectionOptionWindow?.dismiss(true)
        viewModel.addComment(
            commentModel.snippet,
            commentModel.text,
            commentModel.page,
            commentModel.coordinates
        )
    }

    private fun openCommentList(comments: List<CommentModel>) {
        val launchIntent = Intent(this, CommentsListActivity::class.java)
        launchIntent.putParcelableArrayListExtra(BundleArguments.ARGS_COMMENTS, ArrayList(comments))
        commentListActivityLauncher.launch(launchIntent)

    }

    private fun openHighlightList(highlights: List<HighlightModel>) {
        val launchIntent = Intent(this, HighlightListActivity::class.java)
        launchIntent.putParcelableArrayListExtra(
            BundleArguments.ARGS_HIGHLIGHTS,
            ArrayList(highlights)
        )
        highlightListActivityLauncher.launch(launchIntent)
    }

    private fun openBookmarksList(bookmarks: List<BookmarkModel>) {
        val launchIntent = Intent(this, BookmarkListActivity::class.java)
        launchIntent.putParcelableArrayListExtra(
            BundleArguments.ARGS_BOOKMARKS,
            ArrayList(bookmarks)
        )
        bookmarkListActivityLauncher.launch(launchIntent)
    }

    private fun onCommentListActivityResult(activityResult: ActivityResult?) {
        if (activityResult != null) {
            activityResult.data?.let { result ->
                val deletedComments =
                    result.getLongArrayExtra(BundleArguments.ARGS_DELETED_COMMENT_IDS) ?: LongArray(
                        0
                    )
                deletedComments.toList()
                if (deletedComments.isNotEmpty()) {
                    viewModel.removeComments(deletedComments.toList())
                    contentBinding.pdfView.removeCommentAnnotations(deletedComments.toList())
                }

                if (result.action == CommentsListActivity.RESULT_ACTION_OPEN_COMMENT) {
                    val comment = result.getParcelableExtraVs(
                        BundleArguments.ARGS_COMMENT,
                        CommentModel::class.java
                    )
                    if (comment != null) {
                        contentBinding.pdfView.jumpTo(comment.page - 1) //Passing index
                        CommentViewFragment.showToCommentView(comment, supportFragmentManager)
                    }
                }
            }

        }
    }

    private fun onHighlightListActivityResult(activityResult: ActivityResult?) {
        if (activityResult != null) {
            activityResult.data?.let { result ->
                val deletedHighlights =
                    result.getLongArrayExtra(BundleArguments.ARGS_DELETED_HIGHLIGHT_IDS)
                        ?: LongArray(0)
                if (deletedHighlights.isNotEmpty()) {
                    viewModel.removeHighlight(deletedHighlights.toList())
                    contentBinding.pdfView.removeHighlightAnnotations(deletedHighlights.toList())
                }

                if (result.action == HighlightListActivity.RESULT_ACTION_OPEN_HIGHLIGHT) {
                    val highlight = result.getParcelableExtraVs(
                        BundleArguments.ARGS_HIGHLIGHT,
                        HighlightModel::class.java
                    )
                    if (highlight != null) {
                        contentBinding.pdfView.jumpTo(highlight.page - 1) //Passing index
                    }
                }
            }

        }
    }

    private fun onBookmarkActivityResult(activityResult: ActivityResult?) {
        if (activityResult != null) {
            activityResult.data?.let { result ->
                val deletedBookmarks =
                    result.getLongArrayExtra(BundleArguments.ARGS_DELETED_BOOKMARK_IDS)
                        ?: LongArray(0)
                if (deletedBookmarks.isNotEmpty()) {
                    viewModel.removeBookmarks(deletedBookmarks.toList())
                }

                if (result.action == BookmarkListActivity.RESULT_ACTION_OPEN_BOOKMARK) {
                    val bookmark = result.getParcelableExtraVs(
                        BundleArguments.ARGS_BOOKMARK,
                        BookmarkModel::class.java
                    )
                    if (bookmark != null) {
                        contentBinding.pdfView.jumpTo(bookmark.page - 1) //Passing index
                    }
                }
            }

        }
    }

    private fun updateBookmarkInfo(currentPage: Int) {
        val isBookmarked =
            viewModel.annotations.bookmarks.indexOfFirst { it.page == currentPage } != -1
        contentBinding.cbBookmark.isChecked = isBookmarked
    }

    private fun showPageNumber(currentPage: Int) {
        val totalPage = contentBinding.pdfView.getTotalPage()
        val pageInfo = "$currentPage/$totalPage"
        contentBinding.tvPageInfo.text = pageInfo
        contentBinding.tvPageInfo.removeCallbacks(hidePageInfoRunnable)
        contentBinding.tvPageInfo.visibility = View.VISIBLE
        contentBinding.tvPageInfo.postDelayed(hidePageInfoRunnable, 10000)
    }

    private val hidePageInfoRunnable = Runnable {
        contentBinding.tvPageInfo.visibility = View.GONE
    }
}


