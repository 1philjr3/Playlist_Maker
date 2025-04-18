package com.practicum.playlist_maker.marchAuto

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.net.URL
import java.net.IDN
import java.util.regex.Pattern
import java.net.MalformedURLException
import java.io.File

data class ProfileData(
    val name: String = "",
    val institute: String = "",
    val avatarUri: String? = null
)

class UserProfileViewModel(private val sharedPreferences: SharedPreferences) : ViewModel() {
    private val TAG = "UserProfileViewModel"

    // Регулярное выражение для проверки IP-адреса
    private val IP_ADDRESS_PATTERN = Pattern.compile(
        "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$"
    )

    // Регулярное выражение для проверки допустимых символов в доменном имени
    private val DOMAIN_CHAR_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9-_.]+$"
    )

    private val _profileData = MutableLiveData<ProfileData>().apply {
        value = loadProfileDataFromPrefs()
    }
    val profileData: LiveData<ProfileData> = _profileData

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadProfileData()
    }

    private fun loadProfileData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val profileData = loadProfileDataFromPrefs()
                _profileData.value = profileData
            } catch (e: Exception) {
                _error.value = "Ошибка при загрузке профиля: ${e.message}"
                Log.e(TAG, "Ошибка при загрузке профиля", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateProfile(name: String, institute: String, avatarUri: String?) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                saveProfileDataToPrefs(name, institute, avatarUri)
                _profileData.value = ProfileData(name, institute, avatarUri)
            } catch (e: Exception) {
                _error.value = "Ошибка при обновлении профиля: ${e.message}"
                Log.e(TAG, "Ошибка при обновлении профиля", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun saveProfileDataToPrefs(name: String, institute: String, avatarUri: String?) {
        val editor = sharedPreferences.edit()
        editor.putString("name", name)
        editor.putString("institute", institute)
        
        // Сохраняем URI аватара
        avatarUri?.let { 
            editor.putString("avatar_uri", it)
            Log.d(TAG, "Сохранение аватарки в SharedPreferences: $it")
        } ?: editor.remove("avatar_uri")
        
        editor.apply()
    }

    private fun loadProfileDataFromPrefs(): ProfileData {
        val name = sharedPreferences.getString("name", "") ?: ""
        val institute = sharedPreferences.getString("institute", "") ?: ""
        val avatarUri = sharedPreferences.getString("avatar_uri", null)
        
        return ProfileData(name, institute, avatarUri)
    }

    private fun processAvatarUri(uri: String?): String? {
        if (uri == null) return null

        return try {
            when {
                uri.startsWith("file://") -> {
                    Log.d("UserProfileViewModel", "Обработка локального файла: $uri")
                    // Для локальных файлов используем значение по умолчанию
                    null
                }
                uri.startsWith("http") -> {
                    Log.d("UserProfileViewModel", "Обработка HTTP URL: $uri")
                    // Проверяем, указывает ли URL на наш сервер
                    val url = URL(uri)
                    val isOurServer = url.host == "localhost" || 
                                     url.host == "127.0.0.1" || 
                                     url.host == SERVER_IP ||
                                     url.host.contains("duckdns.org")
                    
                    if (isOurServer) {
                        Log.d("UserProfileViewModel", "URL указывает на наш сервер")
                        uri
                    } else {
                        Log.d("UserProfileViewModel", "URL указывает на внешний сервер")
                        uri
                    }
                }
                else -> {
                    Log.d("UserProfileViewModel", "Обработка относительного пути: $uri")
                    // Если это просто путь без протокола, преобразуем в полный URL
                    "${PUBLIC_URL}$uri"
                }
            }
        } catch (e: Exception) {
            Log.e("UserProfileViewModel", "Ошибка при обработке URI аватарки", e)
            null
        }
    }

    companion object {
        private const val SERVER_IP = "192.168.1.100" // Замените на реальный IP сервера
        private const val PUBLIC_URL = "http://$SERVER_IP:3000"
    }
}

/**
 * Фабрика для создания UserProfileViewModel
 */
class UserProfileViewModelFactory(private val sharedPreferences: SharedPreferences) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserProfileViewModel::class.java)) {
            return UserProfileViewModel(sharedPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 