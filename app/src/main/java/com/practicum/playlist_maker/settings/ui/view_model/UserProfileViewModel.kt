package com.practicum.playlist_maker.settings.ui.view_model

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class UserProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "UserProfileViewModel"
    private val client = OkHttpClient()
    private var SERVER_URL = "http://172.17.3.66:3000" // Используем локальный сервер
    
    private val _userName = MutableLiveData<String>()
    private val _userEmail = MutableLiveData<String>()
    private val _userInstitute = MutableLiveData<String>()
    private val _userAvatarUri = MutableLiveData<String>()
    private val _isUpdating = MutableLiveData<Boolean>(false)
    private val _updateError = MutableLiveData<String?>(null)
    
    val userName: LiveData<String> get() = _userName
    val userEmail: LiveData<String> get() = _userEmail
    val userInstitute: LiveData<String> get() = _userInstitute
    val userAvatarUri: LiveData<String> get() = _userAvatarUri
    val isUpdating: LiveData<Boolean> get() = _isUpdating
    val updateError: LiveData<String?> get() = _updateError
    
    init {
        loadUserData()
    }
    
    fun loadUserData() {
        Log.d(TAG, "Loading user data from SharedPreferences")
        val sharedPreferences = getApplication<Application>()
            .getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        
        val name = sharedPreferences.getString("name", null)
        val email = sharedPreferences.getString("email", null)
        val institute = sharedPreferences.getString("institute", null)
        val avatarUri = sharedPreferences.getString("avatar_uri", null)
        
        Log.d(TAG, "SharedPreferences data: name=$name, email=$email, institute=$institute, avatarUri=$avatarUri")
        
        if (name != null && email != null) {
            _userName.setValue(name)
            _userEmail.setValue(email)
            _userInstitute.setValue(institute ?: "Отдел не указан")
            _userAvatarUri.setValue(avatarUri)
            Log.d(TAG, "User data loaded successfully")
        } else {
            _userName.setValue("Неизвестный пользователь")
            _userEmail.setValue("Неизвестный email")
            _userInstitute.setValue("Неизвестный отдел")
            _userAvatarUri.setValue(null)
            Log.d(TAG, "No user data found in SharedPreferences")
        }
    }
    
    // Метод для обновления аватарки пользователя
    fun saveAvatarUri(uri: Uri) {
        val uriString = uri.toString()
        Log.d(TAG, "Saving avatar URI: $uriString")
        
        // Сохраняем URI в SharedPreferences
        val sharedPreferences = getApplication<Application>()
            .getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        
        sharedPreferences.edit()
            .putString("avatar_uri", uriString)
            .apply()
        
        // Обновляем LiveData
        _userAvatarUri.setValue(uriString)
        
        // Проверяем, что данные сохранились
        val savedUri = sharedPreferences.getString("avatar_uri", null)
        Log.d(TAG, "После сохранения, URI в SharedPreferences: $savedUri")
    }
    
    // Метод для обновления профиля на сервере
    fun updateProfile(name: String, institute: String, avatarUri: Uri?) {
        Log.d(TAG, "Начинаем обновление профиля на сервере")
        _isUpdating.postValue(true)
        _updateError.postValue(null)
        
        val sharedPreferences = getApplication<Application>()
            .getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        
        // Используем email в качестве идентификатора
        val email = sharedPreferences.getString("email", null)
        val uid = email ?: sharedPreferences.getString("uid", null) ?: java.util.UUID.randomUUID().toString()
        
        // Создаем JSON данные для запроса
        val jsonObject = JSONObject()
        jsonObject.put("uid", uid)
        jsonObject.put("name", name)
        jsonObject.put("institute", institute)
        
        // Добавляем URL аватарки, если он есть
        avatarUri?.let { uri ->
            val avatarUrl = uri.toString()
            // Если URL начинается с http, то это уже URL с сервера, используем его
            // В противном случае, это может быть файловый URI, который нужно обработать отдельно
            if (avatarUrl.startsWith("http")) {
                Log.d(TAG, "Использование серверного URL для аватарки: $avatarUrl")
                jsonObject.put("avatarUrl", avatarUrl)
            } else if (avatarUrl.startsWith("file:")) {
                Log.d(TAG, "Локальный URI для аватарки: $avatarUrl - этот URL должен быть загружен отдельно")
                // Для локальных URI должна быть выполнена загрузка файла заранее
                jsonObject.put("avatarUrl", avatarUrl)
            } else {
                // Другие типы URI
                Log.d(TAG, "Другой тип URI аватарки: $avatarUrl")
                jsonObject.put("avatarUrl", avatarUrl)
            }
        }
        
        val requestBody = jsonObject.toString()
            .toRequestBody("application/json".toMediaTypeOrNull())
        
        // Создаем запрос к серверу
        val request = Request.Builder()
            .url("$SERVER_URL/api/user/profile")
            .post(requestBody)
            .build()
        
        Log.d(TAG, "Отправка запроса на сервер: $jsonObject")
        
        // Выполняем запрос асинхронно
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Ошибка при обновлении профиля", e)
                _updateError.postValue("Ошибка соединения: ${e.message}")
                _isUpdating.postValue(false)
                
                // При ошибке все равно сохраняем данные локально
                saveProfileLocally(uid, name, institute, avatarUri)
            }
            
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Ответ сервера: $responseBody")
                
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.has("user")) {
                            val user = jsonResponse.getJSONObject("user")
                            val serverName = user.getString("name")
                            val serverInstitute = user.getString("institute")
                            val serverAvatarUrl = if (user.has("avatarUrl")) user.getString("avatarUrl") else null
                            
                            // Сохраняем полученные данные локально
                            saveProfileLocally(
                                uid, 
                                serverName, 
                                serverInstitute, 
                                if (serverAvatarUrl != null) Uri.parse(serverAvatarUrl) else avatarUri
                            )
                            
                            _updateError.postValue(null)
                        } else {
                            _updateError.postValue("Некорректный ответ сервера")
                            saveProfileLocally(uid, name, institute, avatarUri)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка при обработке ответа сервера", e)
                        _updateError.postValue("Ошибка при обработке ответа: ${e.message}")
                        saveProfileLocally(uid, name, institute, avatarUri)
                    }
                } else {
                    Log.e(TAG, "Сервер вернул ошибку: ${response.code}")
                    _updateError.postValue("Сервер вернул ошибку: ${response.code}")
                    saveProfileLocally(uid, name, institute, avatarUri)
                }
                
                _isUpdating.postValue(false)
            }
        })
    }
    
    // Вспомогательный метод для сохранения данных в SharedPreferences
    private fun saveProfileLocally(uid: String, name: String, institute: String, avatarUri: Uri?) {
        Log.d(TAG, "Сохраняем данные профиля локально")
        val sharedPreferences = getApplication<Application>()
            .getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        
        // Проверяем, не является ли avatarUri относительным путем
        var avatarUriString: String? = null
        if (avatarUri != null) {
            avatarUriString = avatarUri.toString()
            Log.d(TAG, "Сохраняем URI аватарки: $avatarUriString")
        }
        
        val editor = sharedPreferences.edit()
        editor.putString("uid", uid)
        editor.putString("name", name)
        editor.putString("institute", institute)
        
        if (avatarUriString != null) {
            editor.putString("avatar_uri", avatarUriString)
            Log.d(TAG, "Аватарка сохранена в SharedPreferences: $avatarUriString")
        }
        
        // Используем commit() вместо apply() для синхронного сохранения
        val success = editor.commit()
        Log.d(TAG, "Результат сохранения в SharedPreferences: $success")
        
        // Проверяем сохраненные данные
        val savedAvatarUri = sharedPreferences.getString("avatar_uri", null)
        Log.d(TAG, "После сохранения, проверка URI в SharedPreferences: $savedAvatarUri")
        
        // Обновляем LiveData
        _userName.postValue(name)
        _userInstitute.postValue(institute)
        avatarUriString?.let { uri ->
            _userAvatarUri.postValue(uri)
            Log.d(TAG, "Аватарка обновлена в LiveData: $uri")
        }
        
        Log.d(TAG, "Данные профиля успешно сохранены локально")
    }
}

