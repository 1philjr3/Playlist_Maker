package com.practicum.playlist_maker.pdfreader.ui.activity.add

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.databinding.ActivityAddPdfBinding
import com.practicum.playlist_maker.pdfreader.base.ui.BaseActivity
import com.practicum.playlist_maker.pdfreader.model.PdfNoteListModel
import com.practicum.playlist_maker.pdfreader.model.TagModel
import com.practicum.playlist_maker.pdfreader.state.ResponseState
import com.practicum.playlist_maker.pdfreader.tools.AppFileManager
import com.practicum.playlist_maker.pdfreader.tools.FileDownloadTool
import com.practicum.playlist_maker.pdfreader.ui.activity.home.HomeFragment.Companion.DOWNLOAD_PDF
import com.practicum.playlist_maker.pdfreader.ui.activity.home.HomeFragment.Companion.FROM_GALLERY
import com.practicum.playlist_maker.pdfreader.ui.fragment.tag.TagFragment
import com.practicum.playlist_maker.pdfreader.utils.Alerts
import com.practicum.playlist_maker.pdfreader.utils.BundleArguments
import com.practicum.playlist_maker.pdfreader.utils.ProgressButtonHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class AddPdfActivity : BaseActivity(), View.OnClickListener, TagFragment.Listener {

    private val viewModel: AddPdfViewModel by viewModels()
    private lateinit var binding: ActivityAddPdfBinding
    private val downloadProgressButtonHelper = ProgressButtonHelper()

    enum class PageType {
        PickFromGallery,
        DownloadPdf
    }

    companion object {
        const val RESULT_ACTION_PDF_ADDED = "action.new.pdf.added"
    }

    private var pageType: PageType = PageType.PickFromGallery

    private var pdfFilePickResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ::onPdfPickResult
    )

    // ------------------ Дополнительно из ActivityProfile для push-уведомлений ------------------
    private val REQUEST_CODE = 1001 // Код запроса для разрешений
    // ------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAddPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        getDataFromIntent()
        initView()

        // ------------------ Дополнительно из ActivityProfile для push-уведомлений ------------------
        // Проверка и запрос разрешения на уведомления
        if (!isNotificationPermissionAllowed(this)) {
            requestNotificationPermission(this)
        }

        // Получаем FCM токен и подписываемся на глобальный топик
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                // Можно при необходимости логировать token
            }
        }

        FirebaseMessaging.getInstance().subscribeToTopic("global")
        // ------------------------------------------------------------------------------------------
    }

    private fun getDataFromIntent() {
        intent?.let {
            val pageTypeInt = it.getIntExtra(BundleArguments.ARGS_PAGE_TYPE, FROM_GALLERY)
            pageType = when (pageTypeInt) {
                FROM_GALLERY -> PageType.PickFromGallery
                DOWNLOAD_PDF -> PageType.DownloadPdf
                else -> PageType.PickFromGallery
            }
        }
    }

    private fun initView() {
        downloadProgressButtonHelper.attachViews(
            binding.btDownload,
            binding.tvDownload,
            binding.downloadProgress
        )

        binding.btBack.setOnClickListener(this)
        binding.btDownload.setOnClickListener(this)
        binding.btAddNewPdf.setOnClickListener(this)
        binding.btPickPdf.setOnClickListener(this)
        binding.btRemovePdf.setOnClickListener(this)
        binding.tvTag.setOnClickListener(this)
        binding.btRemoveTag.setOnClickListener(this)

        // Show or hide sections based on the selected page type
        showOrHideAddSection(true)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btBack -> finish()
            R.id.btDownload -> downloadPdf()
            R.id.btAddNewPdf -> addPdf()
            R.id.btPickPdf -> pickPdfFromGallery()
            R.id.btRemovePdf -> removePdf()
            R.id.tvTag -> TagFragment.show(supportFragmentManager)
            R.id.btRemoveTag -> removeTag()
        }
    }

    private fun showOrHideAddSection(show: Boolean) {
        if (show) {
            if (pageType == PageType.DownloadPdf) {
                binding.downloadSection.visibility = View.VISIBLE
                binding.pickFromGallerySection.visibility = View.GONE
            } else {
                binding.downloadSection.visibility = View.GONE
                binding.pickFromGallerySection.visibility = View.VISIBLE
            }
        } else {
            binding.downloadSection.visibility = View.GONE
            binding.pickFromGallerySection.visibility = View.GONE
        }
    }

    private fun addPdf() {
        val title = binding.etPdfTitle.text.toString().trim()
        val description = binding.etAbout.text.toString().trim()

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.uid)
            userRef.get().addOnSuccessListener { snapshot ->
                val userName = snapshot.child("name").value?.toString() ?: "Unknown User"
                val organization = snapshot.child("institute").value?.toString() ?: "Unknown Organization"

                // Display notification with user data
                showLocalNotification(
                    "Новый PDF добавлен",
                    "$userName из $organization добавил новый файл."
                )

            }.addOnFailureListener { exception ->
                // Handle error
                Log.e("AddPdfActivity", "Failed to get user data", exception)
                showLocalNotification(
                    "Новый PDF добавлен",
                    "Failed to retrieve user data."
                )
            }
        } else {
            showLocalNotification(
                "Новый PDF добавлен",
                "User is not logged in."
            )
        }

        // Add the PDF (existing logic)
        viewModel.addPdf(title, description)
    }



    private fun removePdf() {
        viewModel.pdfFile?.delete()
        viewModel.pdfFile = null
        binding.pdfImportSuccessSection.visibility = View.GONE
        showOrHideAddSection(show = true)
    }

    private fun downloadPdf() {
        val url = binding.etPdfUrl.text.toString().trim()
        val saveFolder = AppFileManager.getPdfNoteFolder(this)
        if (url.isEmpty()) {
            Alerts.warningSnackBar(binding.root, "Введите ссылку для скачивания")
            return
        }
        if (saveFolder != null) {
            viewModel.downloadPdf(url, saveFolder, downloadPdfCallback)
        }
    }

    private var downloadPdfCallback = object : FileDownloadTool.DownloadCallback {
        override fun onDownloadStart() {
            downloadProgressButtonHelper.start()
        }

        override fun onDownloadFailed(error: String?) {
            downloadProgressButtonHelper.stop()
            Alerts.failureSnackBar(binding.root, error ?: "Ошибка загрузки PDF")
        }

        override fun onDownloading(progress: Double) {
            binding.downloadProgress.progress = progress.toInt()
        }

        override fun onDownloadCompleted(file: File) {
            downloadProgressButtonHelper.stop()
            viewModel.pdfFile = file
            showOrHideAddSection(show = false)
            binding.pdfImportSuccessSection.visibility = View.VISIBLE
        }
    }

    private fun pickPdfFromGallery() {
        val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        pdfFilePickResultLauncher.launch(pickIntent)
    }

    private fun onPdfPickResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                contentResolver?.openInputStream(uri)?.use { input ->
                    val saveFile = AppFileManager.getNewPdfFile(this)
                    FileOutputStream(saveFile).use {
                        input.copyTo(it)
                    }
                    viewModel.pdfFile = saveFile
                    showOrHideAddSection(show = false)
                    binding.pdfImportSuccessSection.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onTagSelected(tagModel: TagModel) {
        viewModel.selectedTag = tagModel
        binding.tvTag.setText(tagModel.title) // Изменено с text на setText
        binding.btRemoveTag.visibility = View.VISIBLE
    }

    override fun onTagRemoved(tagId: Long) {
        if (viewModel.selectedTag?.id == tagId) {
            removeTag()
        }
    }

    private fun removeTag() {
        viewModel.selectedTag = null
        binding.tvTag.setText("") // Изменено с text на setText
        binding.btRemoveTag.visibility = View.GONE
    }


    override fun bindUI() = launch(Dispatchers.Main) {
        viewModel.pdfAddResponse.state.observe(this@AddPdfActivity) { state ->
            when (state) {
                ResponseState.Loading -> {}
                is ResponseState.Success<*> -> {
                    val response = state.response as PdfNoteListModel?
                    if (response != null) {
                        val result = Intent(RESULT_ACTION_PDF_ADDED)
                        setResult(RESULT_OK, result)
                        finish()
                    }
                }

                is ResponseState.ValidationError -> {
                    Alerts.warningSnackBar(binding.root, state.error)
                }

                is ResponseState.Failed -> {
                    Alerts.failureSnackBar(binding.root, state.error)
                }
            }
        }
    }

    // ------------------ Добавленные методы из ActivityProfile для уведомлений ------------------
    private fun isNotificationPermissionAllowed(context: Context): Boolean {
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        return notificationManagerCompat.areNotificationsEnabled()
    }

    private fun requestNotificationPermission(activity: Activity) {
        val notificationManagerCompat = NotificationManagerCompat.from(activity)
        if (!notificationManagerCompat.areNotificationsEnabled()) {
            val intent = Intent().apply {
                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra("android.provider.extra.APP_PACKAGE", activity.packageName)
                } else {
                    putExtra("app_package", activity.packageName)
                    putExtra("app_uid", activity.applicationInfo.uid)
                }
            }
            activity.startActivityForResult(intent, REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Не меняем уже существующие функции, но добавляем обработку разрешений уведомлений
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            // Здесь можно добавить логику, если нужно обработать результат
            // получения разрешений на уведомления, например, вывести снекбар.
        }
    }

    private fun showLocalNotification(title: String, body: String) {
        val channelId = "file_action_channel"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Проверяем, существует ли канал
            val existingChannel = notificationManager.getNotificationChannel(channelId)
            if (existingChannel == null) {
                val channel = NotificationChannel(
                    channelId,
                    "File Action Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    // Используем звук по умолчанию, не указываем custom sound
                    setSound(null, null)
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

        notificationManager.notify(1, notification)
    }
}
