package com.practicum.playlist_maker.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Класс для автоматического обновления URL сервера в приложении.
 * 
 * Вставьте этот файл в пакет com.practicum.playlist_maker.network вашего проекта
 * и вызовите его в onCreate() вашего класса Application или в активити.
 */
class ServerUrlUpdater(context: Context) {
    companion object {
        private const val TAG = "ServerUrlUpdater"
        private const val SETTINGS_PREFS = "PROVITA_SETTINGS"
        private const val SERVER_URL_KEY = "SERVER_URL"
        
        /**
         * Удобный метод для использования в Kotlin-коде
         */
        fun updateServerUrl(context: Context, callback: ((String?) -> Unit)? = null) {
            val updater = ServerUrlUpdater(context)
            val defaultUrls = arrayOf(
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "http://10.0.2.2:3000"
            )
            updater.checkAndUpdateServerUrl(defaultUrls) { serverUrl ->
                Log.d(TAG, "Server URL updated: $serverUrl")
                callback?.invoke(serverUrl)
            }
        }
    }
    
    private val appContext = context.applicationContext
    private val executor: Executor = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()
    
    private val availableUrls = listOf(
        "http://172.17.3.66:3000",
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://10.0.2.2:3000"
    )
    
    /**
     * Проверяет и обновляет URL сервера в SharedPreferences.
     * @param serverCheckUrls список URL для проверки (например, localhost, ngrok URL)
     * @param callback обратный вызов, возвращающий найденный URL или null
     */
    fun checkAndUpdateServerUrl(serverCheckUrls: Array<String>, callback: (String?) -> Unit) {
        executor.execute {
            Log.d(TAG, "Checking server URLs...")
            
            // 1. Сначала проверяем сохраненный URL
            val savedUrl = getSavedServerUrl()
            Log.d(TAG, "Saved server URL: $savedUrl")
            
            if (savedUrl != null) {
                // Проверяем, доступен ли сохраненный URL
                if (checkServerAvailability(savedUrl)) {
                    Log.d(TAG, "Saved server URL is available: $savedUrl")
                    // Проверяем, не изменился ли публичный URL
                    val publicUrl = checkPublicUrlFromServer(savedUrl)
                    if (publicUrl != null && publicUrl != savedUrl) {
                        Log.d(TAG, "Found new public URL: $publicUrl")
                        saveServerUrl(publicUrl)
                        notifyCallback(callback, publicUrl)
                        return@execute
                    }
                    
                    // Если публичный URL не изменился, используем сохраненный
                    notifyCallback(callback, savedUrl)
                    return@execute
                } else {
                    Log.d(TAG, "Saved server URL is not available: $savedUrl")
                }
            }
            
            // 2. Если сохраненный URL недоступен, перебираем все URL
            for (url in serverCheckUrls) {
                if (checkServerAvailability(url)) {
                    Log.d(TAG, "Found available server URL: $url")
                    
                    // Проверяем, не предоставляет ли сервер публичный URL
                    val publicUrl = checkPublicUrlFromServer(url)
                    val finalUrl = publicUrl ?: url
                    
                    // Сохраняем URL в настройках
                    saveServerUrl(finalUrl)
                    notifyCallback(callback, finalUrl)
                    return@execute
                }
            }
            
            // 3. Если ни один URL не доступен, возвращаем null
            Log.e(TAG, "No server URLs available")
            notifyCallback(callback, null)
        }
    }
    
    /**
     * Проверяет, предоставляет ли сервер публичный URL
     * @param baseUrl базовый URL сервера
     * @return публичный URL или null
     */
    private fun checkPublicUrlFromServer(baseUrl: String): String? {
        try {
            val url = if (baseUrl.endsWith("/")) baseUrl.substring(0, baseUrl.length - 1) else baseUrl
            
            val request = Request.Builder()
                .url("$url/api/mobile/server-url")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    
                    val serverUrl = json.getString("server_url")
                    val isPublic = json.optBoolean("is_public", false)
                    
                    Log.d(TAG, "Server URL from API: $serverUrl, isPublic: $isPublic")
                    
                    if (isPublic) {
                        return serverUrl
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking public URL from server", e)
        }
        return null
    }
    
    /**
     * Проверяет доступность сервера по URL
     * @param url URL сервера для проверки
     * @return true, если сервер доступен
     */
    private fun checkServerAvailability(url: String): Boolean {
        try {
            val healthUrl = if (url.endsWith("/")) url.substring(0, url.length - 1) else url
            
            val request = Request.Builder()
                .url("$healthUrl/api/server-info")
                .build()
            
            client.newCall(request).execute().use { response ->
                return response.isSuccessful
            }
        } catch (e: IOException) {
            Log.e(TAG, "Server not available: $url", e)
            return false
        }
    }
    
    /**
     * Сохраняет URL сервера в настройках
     * @param url URL сервера
     */
    private fun saveServerUrl(url: String?) {
        try {
            if (url == null) {
                Log.e(TAG, "Cannot save null server URL")
                return
            }
            
            val prefs = appContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            val oldUrl = prefs.getString(SERVER_URL_KEY, "")
            
            if (url != oldUrl) {
                Log.d(TAG, "Saving new server URL: $url (was: $oldUrl)")
                prefs.edit().putString(SERVER_URL_KEY, url).apply()
            } else {
                Log.d(TAG, "Server URL unchanged: $url")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving server URL", e)
        }
    }
    
    /**
     * Получает сохраненный URL сервера из настроек
     * @return сохраненный URL или null
     */
    private fun getSavedServerUrl(): String? {
        val prefs = appContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(SERVER_URL_KEY, null)
    }
    
    /**
     * Вызывает обратный вызов в основном потоке
     * @param callback обратный вызов
     * @param url найденный URL сервера
     */
    private fun notifyCallback(callback: (String?) -> Unit, url: String?) {
        Handler(Looper.getMainLooper()).post {
            callback(url)
        }
    }
} 