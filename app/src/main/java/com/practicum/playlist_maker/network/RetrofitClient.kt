package com.practicum.playlist_maker.network

import android.content.Context
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// Глобальный класс для доступа к контексту приложения
object AppContextProvider {
    lateinit var appContext: Context
    
    // Проверка инициализации
    fun isInitialized(): Boolean {
        return ::appContext.isInitialized
    }
}

object RetrofitClient {

    // Используем локальный URL для отладки
    private const val LOCAL_URL = "http://192.168.0.86:3000"
    private const val TAG = "RetrofitClient"
    
    // Функция для определения базового URL
    fun getBaseUrl(context: Context): String {
        Log.d(TAG, "Using Local URL: $LOCAL_URL")
        
        // Сохраняем локальный URL для будущего использования
        val prefs = context.getSharedPreferences("PROVITA_SETTINGS", Context.MODE_PRIVATE) 
        prefs.edit().putString("SERVER_URL", LOCAL_URL).apply()
        return LOCAL_URL
    }

    // OkHttp клиент с перехватчиком для добавления заголовка user-id
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(UserIdInterceptor())
        .build()

    // Создаем Retrofit сервис с локальным URL
    fun <T> createService(serviceClass: Class<T>, context: Context): T {
        val baseUrl = getBaseUrl(context)
        Log.d(TAG, "Creating Retrofit service for ${serviceClass.simpleName} with base URL: $baseUrl")
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(serviceClass)
    }

    // Для обратной совместимости
    fun <T> createService(serviceClass: Class<T>): T {
        // Используем локальный URL
        Log.d(TAG, "Creating Retrofit service without context, using Local URL: $LOCAL_URL")
        
        val retrofit = Retrofit.Builder()
            .baseUrl(LOCAL_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(serviceClass)
    }

    fun getUserIdFromSharedPreferences(context: Context): String {
        val sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("email", "qwertyred@mail.ru") ?: "qwertyred@mail.ru"
    }
    
    // Функция для получения токена авторизации из SharedPreferences
    fun getAuthTokenFromSharedPreferences(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("token", null)
    }
    
    // Интерцептор для добавления токена авторизации в заголовки запросов
    class AuthInterceptor(private val context: Context) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val originalRequest = chain.request()
            
            // Получаем токен
            val token = getAuthTokenFromSharedPreferences(context)
            
            // Если токен есть, добавляем его в заголовок
            val modifiedRequest = if (token != null) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }
            
            return chain.proceed(modifiedRequest)
        }
    }
    
    // При создании OkHttpClient добавляем интерцептор авторизации
    fun createOkHttpClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(UserIdInterceptor())
            .addInterceptor(AuthInterceptor(context))
            .build()
    }
    
    // Функция для создания сервиса с аутентификацией
    fun <T> createAuthenticatedService(serviceClass: Class<T>, context: Context): T {
        val client = createOkHttpClient(context)
        Log.d(TAG, "Creating authenticated service for ${serviceClass.simpleName} with Local URL: $LOCAL_URL")
        
        val retrofit = Retrofit.Builder()
            .baseUrl(LOCAL_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            
        return retrofit.create(serviceClass)
    }
}

/**
 * Перехватчик, который добавляет заголовок user-id ко всем запросам
 */
class UserIdInterceptor : Interceptor {
    private val TAG = "UserIdInterceptor"

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val userId = getUserIdFromSharedPreferences()
        val userName = getUserNameFromSharedPreferences()
        val encodedUserName = android.util.Base64.encodeToString(userName.toByteArray(), android.util.Base64.NO_WRAP)
        Log.d(TAG, "Adding user-id header: $userId, user-name: $userName (encoded: $encodedUserName)")
        
        val request = chain.request().newBuilder()
            .addHeader("user-id", userId)
            // Добавляем имя пользователя в разных форматах заголовков для совместимости с сервером
            .addHeader("username", encodedUserName)     // Вариант 1
            .addHeader("user-name", encodedUserName)    // Вариант 2
            .addHeader("User-Name", encodedUserName)    // Вариант 3 (с заглавными буквами)
            .addHeader("userName", encodedUserName)     // Вариант 4 (camelCase)
            // Добавляем расшифровку в заголовке
            .addHeader("user-name-encoding", "base64")  // Указываем тип кодирования
            .build()
        
        // Логируем все заголовки для отладки
        val headers = request.headers
        for (i in 0 until headers.size) {
            Log.d(TAG, "Header: ${headers.name(i)} = ${headers.value(i)}")
        }
        
        return chain.proceed(request)
    }
    
    private fun getUserIdFromSharedPreferences(): String {
        try {
            if (!AppContextProvider.isInitialized()) {
                Log.e(TAG, "AppContext is not initialized")
                return "unknown@example.com"
            }

            // Получаем SharedPreferences
            val sharedPreferences = AppContextProvider.appContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            
            // Получаем email пользователя или используем значение по умолчанию
            val email = sharedPreferences.getString("email", "unknown@example.com") ?: "unknown@example.com"
            Log.d(TAG, "Retrieved email from SharedPreferences: $email")
            return email
        } catch (e: Exception) {
            // В случае ошибки возвращаем значение по умолчанию
            Log.e(TAG, "Error getting user id: ${e.message}")
            return "unknown@example.com"
        }
    }
    
    private fun getUserNameFromSharedPreferences(): String {
        try {
            if (!AppContextProvider.isInitialized()) {
                Log.e(TAG, "AppContext is not initialized")
                return "Неизвестный пользователь"
            }

            // Получаем SharedPreferences
            val sharedPreferences = AppContextProvider.appContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            
            // Получаем имя пользователя или используем значение по умолчанию
            val name = sharedPreferences.getString("name", "Неизвестный пользователь") ?: "Неизвестный пользователь"
            Log.d(TAG, "Retrieved user name from SharedPreferences: $name")
            return name
        } catch (e: Exception) {
            // В случае ошибки возвращаем значение по умолчанию
            Log.e(TAG, "Error getting user name: ${e.message}")
            return "Неизвестный пользователь"
        }
    }
}