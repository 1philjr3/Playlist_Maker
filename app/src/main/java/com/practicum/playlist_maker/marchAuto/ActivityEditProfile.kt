package com.practicum.playlist_maker.marchAuto

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.databinding.ActivityEditProfileBinding
import com.practicum.playlist_maker.settings.ui.view_model.UserProfileViewModel
import com.practicum.playlist_maker.settings.ui.view_model.UserProfileViewModelFactory
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.*
import com.practicum.playlist_maker.settings.ui.activity.SettingsFragment

class ActivityEditProfile : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var storage: FirebaseStorage
    private var binding: ActivityEditProfileBinding? = null
    private lateinit var userProfileViewModel: UserProfileViewModel
    private var selectedImageUri: Uri? = null
    private val client = OkHttpClient()
    private var SERVER_URL = "http://172.17.3.66:3000" // Значение по умолчанию для локального сервера
    private var photoFile: File? = null
    private var photoUri: Uri? = null
    
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001
    private val STORAGE_PERMISSION_REQUEST_CODE = 1002

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                // Показываем выбранное изображение в ImageView
                try {
                    Glide.with(this)
                        .load(uri)
                        .error(R.drawable.ic_person_placeholder)
                        .circleCrop()
                        .into(binding!!.avatarPreview)
                    binding!!.avatarPreview.visibility = View.VISIBLE
                    binding!!.selectAvatarButton.text = "Изменить фото"
                } catch (e: Exception) {
                    Log.e("ActivityEditProfile", "Error displaying selected image", e)
                    Toast.makeText(this, "Ошибка при загрузке изображения: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            photoUri?.let { uri ->
                selectedImageUri = uri
                // Показываем сделанное фото в ImageView
                try {
                    Glide.with(this)
                        .load(uri)
                        .error(R.drawable.ic_person_placeholder)
                        .circleCrop()
                        .into(binding!!.avatarPreview)
                    binding!!.avatarPreview.visibility = View.VISIBLE
                    binding!!.selectAvatarButton.text = "Изменить фото"
                } catch (e: Exception) {
                    Log.e("ActivityEditProfile", "Error displaying captured photo", e)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        
        // Загружаем URL сервера из SharedPreferences
        val prefs = getSharedPreferences("PROVITA_SETTINGS", Context.MODE_PRIVATE)
        val savedServerUrl = prefs.getString("SERVER_URL", null)
        if (savedServerUrl != null) {
            SERVER_URL = savedServerUrl
            Log.d("ActivityEditProfile", "Загружен SERVER_URL из SharedPreferences: $SERVER_URL")
        } else {
            Log.w("ActivityEditProfile", "SERVER_URL не найден в SharedPreferences, используем значение по умолчанию: $SERVER_URL")
        }
        
        // Принудительно включаем взаимодействие с полями ввода
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        
        // Скрываем все индикаторы загрузки и прочие слои
        binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.GONE

        // Инициализируем Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference.child("users")
        storage = FirebaseStorage.getInstance()

        // Инициализируем ViewModel
        val factory = UserProfileViewModelFactory(application)
        userProfileViewModel = ViewModelProvider(this, factory)[UserProfileViewModel::class.java]

        // Получаем начальные данные из интента
        val intent = intent
        val name = intent.getStringExtra("name") ?: ""
        val institute = intent.getStringExtra("institute") ?: ""
        
        // Если материальные компоненты не работают, создаем обычные поля ввода
        if (binding!!.editTextName.isEnabled && !binding!!.editTextName.isFocusable) {
            createSimpleEditTexts(name, institute)
        } else {
            // Загружаем данные пользователя в стандартные поля
            showAllUserData()
            
            // Добавляем проверку и слушатели на поля ввода
            setupInputFields()
        }

        // Обработчик для кнопки назад
        binding!!.backButton.setOnClickListener {
            finish()
        }
        
        // Принудительно даем возможность фокуса на поля ввода
        binding!!.nameTextInputLayout.clearFocus()
        binding!!.instituteTextInputLayout.clearFocus()

        // Настраиваем кнопку выбора аватарки
        binding!!.selectAvatarButton.setOnClickListener {
            showImageSourceDialog()
        }

        // Настраиваем кнопку сохранения изменений
        binding!!.buttonSignup.setOnClickListener {
            saveChanges()
        }
    }
    
    private fun setupInputFields() {
        // Проверяем что поля ввода доступны для редактирования
        binding!!.editTextName.isEnabled = true
        binding!!.editTextInstitute.isEnabled = true
        
        // Явно устанавливаем сфокусированность и кликабельность
        binding!!.editTextName.isFocusable = true
        binding!!.editTextName.isFocusableInTouchMode = true
        binding!!.editTextName.isClickable = true
        
        binding!!.editTextInstitute.isFocusable = true
        binding!!.editTextInstitute.isFocusableInTouchMode = true
        binding!!.editTextInstitute.isClickable = true
        
        // Явно устанавливаем InputType
        binding!!.editTextName.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        binding!!.editTextInstitute.inputType = android.text.InputType.TYPE_CLASS_TEXT
        
        // Добавляем обработчики касаний
        binding!!.editTextName.setOnClickListener {
            it.requestFocus()
            showKeyboard(it)
        }
        
        binding!!.editTextInstitute.setOnClickListener {
            it.requestFocus()
            showKeyboard(it)
        }
        
        // Добавляем дополнительный обработчик касаний на все окно
        binding!!.rootView.setOnClickListener {
            // Скрываем клавиатуру при касании вне полей ввода
            hideKeyboard()
        }
        
        // Очищаем ошибки при начале ввода
        binding!!.editTextName.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                binding!!.nameTextInputLayout.error = null
                showKeyboard(view)
            }
        }
        
        binding!!.editTextInstitute.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                binding!!.instituteTextInputLayout.error = null
                showKeyboard(view)
            }
        }
        
        // Проверяем, есть ли у полей значения по умолчанию
        if (binding!!.editTextName.text.isNullOrEmpty()) {
            binding!!.editTextName.setText("") // Устанавливаем пустое значение, чтобы активировать поле
        }
        
        if (binding!!.editTextInstitute.text.isNullOrEmpty()) {
            binding!!.editTextInstitute.setText("") // Устанавливаем пустое значение, чтобы активировать поле
        }
        
        // Добавим логирование для отладки
        Log.d("EditProfile", "EditText Name enabled: ${binding!!.editTextName.isEnabled}, " +
                "focusable: ${binding!!.editTextName.isFocusable}, " +
                "clickable: ${binding!!.editTextName.isClickable}")
        
        Log.d("EditProfile", "EditText Institute enabled: ${binding!!.editTextInstitute.isEnabled}, " +
                "focusable: ${binding!!.editTextInstitute.isFocusable}, " +
                "clickable: ${binding!!.editTextInstitute.isClickable}")
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding!!.rootView.windowToken, 0)
    }
    
    private fun showImageSourceDialog() {
        val options = arrayOf("Сделать фото", "Выбрать из галереи")
        AlertDialog.Builder(this)
            .setTitle("Выберите источник изображения")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> requestCameraPermission()
                    1 -> openGallery()
                }
            }
            .show()
    }
    
    private fun takePicture() {
        if (checkCameraPermission()) {
            createPhotoFile()
        } else {
            requestCameraPermission()
        }
    }
    
    private fun createPhotoFile() {
        try {
            // Создаем временный файл для фото
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            photoFile = File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            )
            
            photoFile?.let { file ->
                photoUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    file
                )
                
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                
                // Проверяем, что есть приложение для обработки этого intent
                if (intent.resolveActivity(packageManager) != null) {
                    takePhotoLauncher.launch(intent)
                } else {
                    Toast.makeText(this, "Камера недоступна", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this, "Не удалось создать файл для фото", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Log.e("ActivityEditProfile", "Error creating image file", e)
            Toast.makeText(this, "Ошибка при создании файла: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkCameraPermission(): Boolean {
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
                checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestCameraPermission() {
        requestPermissions(
            arrayOf(android.Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }
    
    private fun checkStoragePermission(): Boolean {
        // Для Android 10 (API 29) и выше не требуется READ_EXTERNAL_STORAGE
        // для доступа к медиафайлам через MediaStore
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            return true
        }
        
        // Для Android ниже 10 проверяем разрешение
        return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == 
               android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Для Android 13+ используем READ_MEDIA_IMAGES
            requestPermissions(
                arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Для Android 10-12 можно открыть галерею без разрешений
            openGallery()
        } else {
            // Для Android 9 и ниже запрашиваем READ_EXTERNAL_STORAGE
            requestPermissions(
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    createPhotoFile()
                } else {
                    Toast.makeText(this, "Разрешение на камеру не предоставлено. Выберите фото из галереи.", Toast.LENGTH_SHORT).show()
                    // Если камера не доступна, предложим выбрать из галереи
                    openGallery()
                }
            }
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    Log.d("ActivityEditProfile", "Разрешение на доступ к галерее не предоставлено. Пробуем обходной путь через ACTION_GET_CONTENT")
                    
                    // Если разрешение не предоставлено, все равно попытаемся открыть галерею
                    // через ACTION_GET_CONTENT, который может работать без разрешений
                    try {
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                        intent.type = "image/*"
                        pickImageLauncher.launch(intent)
                    } catch (e: Exception) {
                        Log.e("ActivityEditProfile", "Не удалось открыть выбор файла", e)
                        Toast.makeText(this, "Невозможно выбрать изображение. Пожалуйста, предоставьте разрешения в настройках приложения.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    private fun openGallery() {
        try {
            Log.d("ActivityEditProfile", "Открываем галерею для выбора изображения")
            
            // Используем явный Intent для открытия галереи
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryIntent.type = "image/*"
            
            // Создаем chooser с заголовком
            val chooserIntent = Intent.createChooser(galleryIntent, "Выберите изображение")
            
            pickImageLauncher.launch(chooserIntent)
        } catch (e: Exception) {
            Log.e("ActivityEditProfile", "Ошибка при открытии галереи", e)
            Toast.makeText(this, "Не удалось открыть галерею: ${e.message}", Toast.LENGTH_SHORT).show()
            
            // Запасной вариант - просто открыть выбор файла
            try {
                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT)
                fallbackIntent.type = "image/*"
                pickImageLauncher.launch(fallbackIntent)
            } catch (e: Exception) {
                Log.e("ActivityEditProfile", "Ошибка при использовании запасного метода", e)
                Toast.makeText(this, "Не удалось открыть выбор файла: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadImageAndUpdateProfile(imageUri: Uri, name: String, institute: String) {
        binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.VISIBLE

        // Получаем UID пользователя из SharedPreferences или генерируем временный
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val uid = sharedPreferences.getString("uid", null) ?: createAnonymousUserId()
        
        try {
            // Создаем локальную копию изображения в приватном хранилище приложения
            val localUri = saveImageToLocalStorage(imageUri)
            Log.d("ActivityEditProfile", "Created local copy of image: $localUri")
            
            // Сохраняем локальный URI в ViewModel для временного использования
            userProfileViewModel.saveAvatarUri(localUri)
            
            // Загружаем изображение на сервер
            uploadAvatarToServer(localUri, name, institute, { serverAvatarUrl ->
                if (serverAvatarUrl != null) {
                    // Успешно загружена, обновляем профиль с полученным URL
                    runOnUiThread {
                        Log.d("ActivityEditProfile", "Аватарка успешно загружена, URL: $serverAvatarUrl")
                        userProfileViewModel.updateProfile(name, institute, Uri.parse(serverAvatarUrl))
                        
                        Toast.makeText(
                            this, 
                            "Профиль успешно обновлен с новой аватаркой", 
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
                        finish()
                    }
                } else {
                    // Ошибка при загрузке аватарки, обновляем профиль без аватарки
                    runOnUiThread {
                        userProfileViewModel.updateProfile(name, institute, null)
                        
                        Toast.makeText(
                            this, 
                            "Не удалось загрузить аватарку, профиль обновлен без неё", 
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
                        finish()
                    }
                }
            })
        } catch (e: Exception) {
            binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
            Log.e("ActivityEditProfile", "Error uploading image", e)
            Toast.makeText(this, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadAvatarToServer(localAvatarUri: Uri, name: String, institute: String, callback: (String?) -> Unit) {
        try {
            // Перед загрузкой сразу сохраняем локальную версию в SharedPreferences
            // Это нужно, чтобы аватарка была видна даже если загрузка на сервер не удалась
            val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            with(sharedPreferences.edit()) {
                putString("avatar_uri", localAvatarUri.toString())
                // Сохраняем также путь к файлу (без scheme)
                try {
                    val path = localAvatarUri.path
                    if (path != null) {
                        putString("avatar_file_path", path)
                    }
                } catch (e: Exception) {
                    Log.e("ActivityEditProfile", "Ошибка при сохранении пути к файлу", e)
                }
                val success = commit() // Используем commit() вместо apply() для гарантированного сохранения
                Log.d("ActivityEditProfile", "Результат сохранения локального URI: $success")
            }
            
            Log.d("ActivityEditProfile", "Временно сохранен локальный URI аватарки: $localAvatarUri")
            
            // Проверяем, что URI успешно сохранен
            val savedUri = sharedPreferences.getString("avatar_uri", null)
            Log.d("ActivityEditProfile", "Проверка сохраненного URI: $savedUri")
            
            // Создаем файл для загрузки
            val file = File(localAvatarUri.path!!)
            if (!file.exists()) {
                Log.e("ActivityEditProfile", "Avatar file does not exist: ${file.absolutePath}")
                // Проверяем альтернативные пути
                val filePath = sharedPreferences.getString("avatar_file_path", null)
                if (filePath != null) {
                    val alternativeFile = File(filePath)
                    if (alternativeFile.exists()) {
                        Log.d("ActivityEditProfile", "Найден альтернативный файл: $filePath")
                        // Пробуем использовать альтернативный файл
                        uploadFileToServer(alternativeFile, name, institute, callback)
                        return
                    }
                }
                
                // Если файл не найден, возвращаем локальный URI (он уже сохранен)
                callback(localAvatarUri.toString())
                return
            }
            
            // Загружаем найденный файл
            uploadFileToServer(file, name, institute, callback)
        } catch (e: Exception) {
            Log.e("ActivityEditProfile", "Error preparing avatar upload", e)
            // При ошибке возвращаем локальный URI (он уже сохранен в SharedPreferences)
            callback(localAvatarUri.toString())
        }
    }
    
    private fun uploadFileToServer(file: File, name: String, institute: String, callback: (String?) -> Unit) {
        try {
            // Создаем multipart запрос с файлом
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "avatar",
                    file.name,
                    file.asRequestBody("image/*".toMediaType())
                )
                .addFormDataPart("uid", name)
                .addFormDataPart("institute", institute)
                .build()

            // Создаем запрос к серверу
            val request = Request.Builder()
                .url("$SERVER_URL/upload/avatar")
                .post(requestBody)
                .build()

            Log.d("ActivityEditProfile", "Отправка запроса на загрузку аватарки")
            
            // Выполняем запрос асинхронно
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("ActivityEditProfile", "Failed to upload avatar", e)
                    // Возвращаем локальный URI файла, который уже сохранен в SharedPreferences
                    callback(Uri.fromFile(file).toString())
                }
                
                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()
                            Log.d("ActivityEditProfile", "Avatar upload response: $responseBody")
                            
                            val jsonResponse = JSONObject(responseBody)
                            val fileUrl = jsonResponse.optString("fileUrl", null)
                            
                            if (fileUrl != null) {
                                // Используем URL, который вернул сервер, без модификаций
                                // Это может быть URL с другим IP, но мы его сохраняем как есть
                                val fullAvatarUrl = fileUrl
                                
                                Log.d("ActivityEditProfile", "Аватарка успешно загружена, полный URL: $fullAvatarUrl")
                                
                                // Сохраняем серверный URL в SharedPreferences
                                runOnUiThread {
                                    val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                                    
                                    // Проверяем существующее значение перед сохранением
                                    val oldAvatarUrl = prefs.getString("avatar_uri", null)
                                    Log.d("ActivityEditProfile", "Старый URL аватарки: $oldAvatarUrl")
                                    
                                    // Сохраняем новый URL
                                    val result = prefs.edit()
                                        .putString("avatar_uri", fullAvatarUrl)
                                        .commit()
                                    
                                    // Проверяем, сохранилось ли значение
                                    val newAvatarUrl = prefs.getString("avatar_uri", null)
                                    Log.d("ActivityEditProfile", "Сохранен серверный URL аватарки: $fullAvatarUrl, успех: $result, проверка: $newAvatarUrl")
                                    
                                    // Вызываем обработчик в UI потоке
                                    callback(fullAvatarUrl)
                                }
                                return
                            }
                        }
                        
                        Log.e("ActivityEditProfile", "Error uploading avatar: ${response.code}")
                        // При ошибке сервера возвращаем локальный URI
                        runOnUiThread {
                            callback(Uri.fromFile(file).toString())
                        }
                    } catch (e: Exception) {
                        Log.e("ActivityEditProfile", "Error parsing avatar upload response", e)
                        // При ошибке разбора ответа возвращаем локальный URI
                        runOnUiThread {
                            callback(Uri.fromFile(file).toString())
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("ActivityEditProfile", "Error creating upload request", e)
            // При ошибке создания запроса возвращаем локальный URI
            callback(Uri.fromFile(file).toString())
        }
    }

    private fun saveImageToLocalStorage(sourceUri: Uri): Uri {
        try {
            // Создаем уникальное имя файла
            val fileName = "avatar_${UUID.randomUUID()}.jpg"
            
            // Открываем поток для чтения исходного изображения
            val inputStream = contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Не удалось открыть исходное изображение")
            
            // Создаем файл в приватном хранилище приложения
            val outputFile = File(filesDir, fileName)
            
            // Копируем изображение
            inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d("ActivityEditProfile", "Saved image to ${outputFile.absolutePath}")
            
            // Возвращаем URI для локального файла
            return Uri.fromFile(outputFile)
        } catch (e: Exception) {
            Log.e("ActivityEditProfile", "Error saving image locally", e)
            throw e
        }
    }

    private fun updateUserProfile(name: String, institute: String, avatarUrl: Uri?) {
        Log.d("ActivityEditProfile", "updateUserProfile - начало обновления профиля")
        
        // Получаем UID пользователя из SharedPreferences или генерируем временный
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val uid = sharedPreferences.getString("uid", null) ?: createAnonymousUserId()
        
        Log.d("ActivityEditProfile", "userId: $uid")

        // Показываем индикатор загрузки, если он еще не показан
        if (binding!!.loadingLayout.loadingRootConstraintLayout.visibility != View.VISIBLE) {
            binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.VISIBLE
            Log.d("ActivityEditProfile", "Показываем loading layout")
        }

        try {
            // Проверяем, является ли URL аватарки уже серверным URL или локальным URI
            val isServerUrl = avatarUrl?.toString()?.startsWith("http") == true
            
            if (isServerUrl) {
                // Если URL уже с сервера, отправляем обычный JSON запрос
                sendJsonProfileUpdate(uid, name, institute, avatarUrl)
            } else if (avatarUrl != null) {
                // Если это локальный URI и не с сервера, отправляем multipart запрос
                sendMultipartProfileUpdate(uid, name, institute, avatarUrl)
            } else {
                // Если аватарки нет, отправляем только текстовые данные
                sendJsonProfileUpdate(uid, name, institute, null)
            }
        } catch (e: Exception) {
            binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
            Log.e("ActivityEditProfile", "Error updating profile", e)
            Toast.makeText(this, "Ошибка обновления профиля: ${e.message}", Toast.LENGTH_SHORT).show()
            
            // При любых ошибках сохраняем данные локально
            saveDataLocally(uid, name, institute, avatarUrl)
        }
    }
    
    private fun sendJsonProfileUpdate(uid: String, name: String, institute: String, avatarUrl: Uri?) {
        // Создаем JSON данные для запроса
        val jsonObject = JSONObject()
        jsonObject.put("uid", uid)
        jsonObject.put("name", name)
        jsonObject.put("institute", institute)
        avatarUrl?.let { uri ->
            jsonObject.put("avatarUrl", uri.toString())
        }
        
        val requestBody = jsonObject.toString()
            .toRequestBody("application/json".toMediaTypeOrNull())
        
        // Создаем запрос к серверу
        val request = Request.Builder()
            .url("$SERVER_URL/api/user/profile")
            .post(requestBody)
            .build()
        
        Log.d("ActivityEditProfile", "Отправка JSON запроса на сервер: $jsonObject")
        
        // Выполняем запрос асинхронно
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handleProfileUpdateResponse(null, e, uid, name, institute, avatarUrl)
            }
            
            override fun onResponse(call: Call, response: Response) {
                handleProfileUpdateResponse(response, null, uid, name, institute, avatarUrl)
            }
        })
    }

    private fun sendMultipartProfileUpdate(uid: String, name: String, institute: String, avatarUri: Uri) {
        try {
            // Проверяем, существует ли файл
            val file = File(avatarUri.path ?: "")
            if (!file.exists()) {
                Log.e("ActivityEditProfile", "Avatar file does not exist: ${file.absolutePath}")
                // Если файл не существует, отправляем обычный JSON запрос без аватарки
                sendJsonProfileUpdate(uid, name, institute, null)
                return
            }
            
            // Подготавливаем multipart запрос с файлом и данными профиля
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "avatar",
                    file.name,
                    file.asRequestBody("image/*".toMediaType())
                )
                .addFormDataPart("uid", uid)
                .addFormDataPart("name", name)
                .addFormDataPart("institute", institute)
                .build()

            // Создаем запрос к серверу
            val request = Request.Builder()
                .url("$SERVER_URL/api/user/profile")
                .post(requestBody)
                .build()

            Log.d("ActivityEditProfile", "Отправка multipart запроса на сервер с аватаркой и данными профиля")
            
            // Выполняем запрос асинхронно
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    handleProfileUpdateResponse(null, e, uid, name, institute, avatarUri)
                }
                
                override fun onResponse(call: Call, response: Response) {
                    handleProfileUpdateResponse(response, null, uid, name, institute, avatarUri)
                }
            })
        } catch (e: Exception) {
            Log.e("ActivityEditProfile", "Error preparing multipart request", e)
            // При ошибке подготовки запроса, сохраняем данные локально
            runOnUiThread {
                binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
                Toast.makeText(
                    this@ActivityEditProfile, 
                    "Ошибка при подготовке запроса: ${e.message}", 
                    Toast.LENGTH_SHORT
                ).show()
                saveDataLocally(uid, name, institute, avatarUri)
                finish()
            }
        }
    }

    private fun handleProfileUpdateResponse(response: Response?, error: IOException?, uid: String, name: String, institute: String, avatarUrl: Uri?) {
        val responseBody = response?.body?.string()
        Log.d("ActivityEditProfile", "Ответ сервера: $responseBody")
        
        runOnUiThread {
            binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
            
            if (error != null) {
                Log.d("ActivityEditProfile", "Скрываем loading layout при ошибке")
                Log.e("ActivityEditProfile", "Failed to connect to server", error)
                Toast.makeText(
                    this@ActivityEditProfile, 
                    "Ошибка соединения с сервером: ${error.message}", 
                    Toast.LENGTH_SHORT
                ).show()
                
                // Даже при ошибке сохраняем данные локально
                saveDataLocally(uid, name, institute, avatarUrl)
                finish()
                return@runOnUiThread
            }
            
            if (response != null && response.isSuccessful) {
                // Проверяем, вернул ли сервер новый URL аватарки
                var updatedAvatarUrl = avatarUrl
                if (responseBody != null) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.has("user") && jsonResponse.getJSONObject("user").has("avatarUrl")) {
                            val serverAvatarUrl = jsonResponse.getJSONObject("user").getString("avatarUrl")
                            if (serverAvatarUrl.isNotEmpty()) {
                                updatedAvatarUrl = Uri.parse(serverAvatarUrl)
                                Log.d("ActivityEditProfile", "Получен новый URL аватарки с сервера: $serverAvatarUrl")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ActivityEditProfile", "Error parsing response JSON", e)
                    }
                }
                
                // Сохраняем данные локально в любом случае
                saveDataLocally(uid, name, institute, updatedAvatarUrl)
                
                Toast.makeText(
                    this@ActivityEditProfile,
                    "Профиль успешно обновлен",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Возвращаемся на экран настроек
                finish()
            } else {
                // При ошибке от сервера все равно сохраняем локально
                saveDataLocally(uid, name, institute, avatarUrl)
                
                val errorCode = response?.code ?: -1
                Log.e("ActivityEditProfile", "Server error: $errorCode")
                Toast.makeText(
                    this@ActivityEditProfile,
                    "Ошибка сервера: $errorCode, но данные сохранены локально",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Все равно закрываем окно
                finish()
            }
        }
    }
    
    private fun saveDataLocally(uid: String, name: String, institute: String, avatarUrl: Uri?) {
        Log.d("ActivityEditProfile", "Сохранение данных локально")
        
        // Сохраняем изменения в SharedPreferences
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("uid", uid)
            putString("name", name)
            putString("institute", institute)
            // Если выбрана новая аватарка, сохраняем локальный URI
            avatarUrl?.let { uri ->
                putString("avatar_uri", uri.toString())
            }
            apply()
            
            Log.d("ActivityEditProfile", "SharedPreferences updated")
        }
    }
    
    private fun createAnonymousUserId(): String {
        val anonymousId = "anonymous_${System.currentTimeMillis()}"
        // Сохраняем сгенерированный ID
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("uid", anonymousId).apply()
        Log.d("ActivityEditProfile", "Created anonymous user ID: $anonymousId")
        return anonymousId
    }

    private fun showAllUserData() {
        // Убеждаемся, что загрузочный слой скрыт при инициализации полей
        binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
        Log.d("ActivityEditProfile", "Убедились, что loading layout скрыт: ${binding!!.loadingLayout.loadingRootConstraintLayout.visibility == View.GONE}")
        
        val intent = intent
        val name = intent.getStringExtra("name") ?: ""
        val institute = intent.getStringExtra("institute") ?: ""
        Log.d("ActivityEditProfile", "Received data: name=$name, institute=$institute")
        
        // Устанавливаем значения в поля ввода с явным указанием фокусируемости
        binding!!.editTextName.apply { 
            setText(name)
            isEnabled = true
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
        }
        
        binding!!.editTextInstitute.apply {
            setText(institute)
            isEnabled = true
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
        }
        
        // Загружаем текущую аватарку из SharedPreferences
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val avatarUriString = sharedPreferences.getString("avatar_uri", null)
        
        // Если аватарка есть, показываем её
        if (avatarUriString != null) {
            try {
                val uri = Uri.parse(avatarUriString)
                Log.d("ActivityEditProfile", "Loading avatar from URI: $uri")
                
                // Используем Glide для загрузки изображения - он поддерживает разные типы URI
                // и будет корректно загружать изображения даже с другого IP-адреса
                Glide.with(this)
                    .load(uri)
                    .error(R.drawable.ic_person_placeholder)
                    .circleCrop()
                    .into(binding!!.avatarPreview)
                
                binding!!.avatarPreview.visibility = View.VISIBLE
                binding!!.selectAvatarButton.text = "Изменить фото"
                selectedImageUri = uri
            } catch (e: Exception) {
                Log.e("ActivityEditProfile", "Error loading avatar from SharedPreferences", e)
                // При ошибке загрузки аватарки просто показываем плейсхолдер
                binding!!.avatarPreview.setImageResource(R.drawable.ic_person_placeholder)
                binding!!.avatarPreview.visibility = View.VISIBLE
            }
        }
        
        // Проверяем статус полей ввода после инициализации
        Log.d("ActivityEditProfile", "После инициализации - EditText Name enabled: ${binding!!.editTextName.isEnabled}, " +
                "focusable: ${binding!!.editTextName.isFocusable}, " +
                "text: ${binding!!.editTextName.text}")
                
        Log.d("ActivityEditProfile", "После инициализации - EditText Institute enabled: ${binding!!.editTextInstitute.isEnabled}, " +
                "focusable: ${binding!!.editTextInstitute.isFocusable}, " +
                "text: ${binding!!.editTextInstitute.text}")
    }
    
    private fun createSimpleEditTexts(name: String, institute: String) {
        Log.d("ActivityEditProfile", "Создаем простые EditText, потому что материальные компоненты не работают")
        
        // Скрываем материальные компоненты
        binding!!.nameTextInputLayout.visibility = View.GONE
        binding!!.instituteTextInputLayout.visibility = View.GONE
        
        // Создаем простые EditText
        val simpleNameEditText = android.widget.EditText(this).apply {
            id = R.id.simple_edit_text_name
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(46, 16, 46, 0)
            }
            setText(name)
            hint = "ФИО"
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        }
        
        val simpleInstituteEditText = android.widget.EditText(this).apply {
            id = R.id.simple_edit_text_institute
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(46, 16, 46, 0)
            }
            setText(institute)
            hint = "Отдел"
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        
        // Находим родительский контейнер для вставки
        val parentContainer = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.signupCardView)
        
        // Добавляем наши EditText в контейнер
        parentContainer.addView(simpleNameEditText)
        parentContainer.addView(simpleInstituteEditText)
        
        // Устанавливаем constraints программно
        val constraints = androidx.constraintlayout.widget.ConstraintSet()
        constraints.clone(parentContainer)
        
        // Настраиваем constraints для simpleNameEditText
        constraints.connect(
            simpleNameEditText.id,
            androidx.constraintlayout.widget.ConstraintSet.TOP,
            binding!!.avatarPreview.id,
            androidx.constraintlayout.widget.ConstraintSet.BOTTOM,
            16
        )
        constraints.connect(
            simpleNameEditText.id,
            androidx.constraintlayout.widget.ConstraintSet.START,
            R.id.guideLineStart,
            androidx.constraintlayout.widget.ConstraintSet.START,
            0
        )
        constraints.connect(
            simpleNameEditText.id,
            androidx.constraintlayout.widget.ConstraintSet.END,
            R.id.guidelineEnd,
            androidx.constraintlayout.widget.ConstraintSet.END,
            0
        )
        
        // Настраиваем constraints для simpleInstituteEditText
        constraints.connect(
            simpleInstituteEditText.id,
            androidx.constraintlayout.widget.ConstraintSet.TOP,
            simpleNameEditText.id,
            androidx.constraintlayout.widget.ConstraintSet.BOTTOM,
            16
        )
        constraints.connect(
            simpleInstituteEditText.id,
            androidx.constraintlayout.widget.ConstraintSet.START,
            R.id.guideLineStart,
            androidx.constraintlayout.widget.ConstraintSet.START,
            0
        )
        constraints.connect(
            simpleInstituteEditText.id,
            androidx.constraintlayout.widget.ConstraintSet.END,
            R.id.guidelineEnd,
            androidx.constraintlayout.widget.ConstraintSet.END,
            0
        )
        
        // Connect button to institute
        constraints.connect(
            binding!!.buttonSignup.id,
            androidx.constraintlayout.widget.ConstraintSet.TOP,
            simpleInstituteEditText.id,
            androidx.constraintlayout.widget.ConstraintSet.BOTTOM,
            35
        )
        
        // Применяем constraints
        constraints.applyTo(parentContainer)
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    // Вспомогательный метод для отображения клавиатуры
    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun saveChanges() {
        // Получаем введенные пользователем данные
        val name = binding!!.editTextName.text.toString().trim()
        val institute = binding!!.editTextInstitute.text.toString().trim()

        // Проверки обязательных полей
        if (name.isEmpty()) {
            binding!!.editTextName.error = "Пожалуйста, введите имя"
            return
        }

        // Показываем индикатор загрузки
        binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.VISIBLE
        
        // Если аватарка выбрана, сначала загружаем её на сервер, затем обновляем профиль
        if (selectedImageUri != null) {
            try {
                // Сохраняем аватарку локально и получаем URI для локального файла
                val localAvatarUri = saveImageLocally(selectedImageUri!!)
                
                // Загружаем аватарку на сервер
                uploadAvatarToServer(localAvatarUri, name, institute) { serverAvatarUrl ->
                    if (serverAvatarUrl != null) {
                        // Успешно загружена, обновляем профиль с полученным URL
                        runOnUiThread {
                            Log.d("ActivityEditProfile", "Аватарка успешно загружена, URL: $serverAvatarUrl")
                            // Принимаем любой URL, который вернул сервер, без модификаций
                            userProfileViewModel.updateProfile(name, institute, Uri.parse(serverAvatarUrl))
                            
                            Toast.makeText(
                                this, 
                                "Профиль успешно обновлен с новой аватаркой", 
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
                            finish()
                        }
                    } else {
                        // Ошибка при загрузке аватарки, обновляем профиль без аватарки
                        runOnUiThread {
                            userProfileViewModel.updateProfile(name, institute, null)
                            
                            Toast.makeText(
                                this, 
                                "Не удалось загрузить аватарку, профиль обновлен без неё", 
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
                            finish()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ActivityEditProfile", "Error saving avatar locally", e)
                binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
                Toast.makeText(this, "Ошибка сохранения аватарки: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Если аватарка не выбрана, просто обновляем профиль
            userProfileViewModel.updateProfile(name, institute, null)
            
            Toast.makeText(
                this, 
                "Обновление профиля...", 
                Toast.LENGTH_SHORT
            ).show()
            
            binding!!.loadingLayout.loadingRootConstraintLayout.visibility = View.GONE
            finish()
        }
    }

    private fun saveImageLocally(sourceUri: Uri): Uri {
        try {
            // Создаем уникальное имя файла
            val fileName = "avatar_${UUID.randomUUID()}.jpg"
            
            // Открываем поток для чтения исходного изображения
            val inputStream = contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Не удалось открыть исходное изображение")
            
            // Создаем файл в приватном хранилище приложения
            val outputFile = File(filesDir, fileName)
            
            // Копируем изображение
            inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d("ActivityEditProfile", "Saved image to ${outputFile.absolutePath}")
            
            // Дополнительно сохраняем путь к файлу в SharedPreferences
            val uri = Uri.fromFile(outputFile)
            val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putString("avatar_file_path", outputFile.absolutePath)
            editor.putString("avatar_uri", uri.toString())
            val success = editor.commit()
            
            Log.d("ActivityEditProfile", "Сохранение в SharedPreferences: success=$success, avatar_uri=${uri.toString()}")
            
            // Проверяем, что данные действительно сохранились
            val savedUri = sharedPreferences.getString("avatar_uri", null)
            Log.d("ActivityEditProfile", "После сохранения, URI в SharedPreferences: $savedUri")
            
            // Возвращаем URI для локального файла
            return uri
        } catch (e: Exception) {
            Log.e("ActivityEditProfile", "Error saving image locally", e)
            throw e
        }
    }
}
