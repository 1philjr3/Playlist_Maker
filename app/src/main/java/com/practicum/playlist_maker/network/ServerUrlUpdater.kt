package com.practicum.playlist_maker.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Класс для управления URL сервера в приложении.
 * 
 * Использует только локальный URL для отладки.
 */
class ServerUrlUpdater(context: Context) {
    companion object {
        private const val TAG = "ServerUrlUpdater"
        private const val SETTINGS_PREFS = "PROVITA_SETTINGS"
        private const val SERVER_URL_KEY = "SERVER_URL"
        
        // Локальный URL для отладки (вместо реального DuckDNS)
        private const val LOCAL_SERVER_URL = "http://192.168.0.86:3000"
        
        /**
         * Удобный метод для использования в Kotlin-коде
         */
        fun updateServerUrl(context: Context, callback: ((String?) -> Unit)? = null) {
            val updater = ServerUrlUpdater(context)
            val localUrls = arrayOf(
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "http://172.17.3.66:3000",
                "http://192.168.0.86:3000"
            )
            updater.checkAndUpdateLocalServerUrl(localUrls) { serverUrl ->
                Log.d(TAG, "Server URL updated: $serverUrl")
                callback?.invoke(serverUrl)
            }
        }
        
        /**
         * Получить URL сервера (временно возвращает локальный URL вместо DuckDNS)
         */
        fun getDuckDnsUrl(): String {
            Log.d(TAG, "Используем Локальный URL: $LOCAL_SERVER_URL")
            return LOCAL_SERVER_URL
        }
    }
    
    private val appContext = context.applicationContext
    private val executor: Executor = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()
    
    /**
     * Проверяет и обновляет URL локального сервера в SharedPreferences.
     * @param serverUrls список локальных URL для проверки
     * @param callback обратный вызов, возвращающий найденный URL или null
     */
    fun checkAndUpdateLocalServerUrl(serverUrls: Array<String>, callback: (String?) -> Unit) {
        executor.execute {
            Log.d(TAG, "Проверка доступных локальных URL...")
            
            // Перебираем все URL
            for (url in serverUrls) {
                if (checkServerAvailability(url)) {
                    Log.d(TAG, "Найден доступный локальный URL: $url")
                    
                    // Сохраняем URL в настройках
                    saveServerUrl(url)
                    notifyCallback(callback, url)
                    return@execute
                }
            }
            
            // Если ни один URL не доступен, возвращаем null
            Log.e(TAG, "Нет доступных локальных URL")
            notifyCallback(callback, null)
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
                Log.d(TAG, "Сохранение нового URL сервера: $url (было: $oldUrl)")
                prefs.edit().putString(SERVER_URL_KEY, url).apply()
            } else {
                Log.d(TAG, "URL сервера не изменился: $url")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении URL сервера", e)
        }
    }
    
    private fun notifyCallback(callback: (String?) -> Unit, url: String?) {
        Handler(Looper.getMainLooper()).post {
            callback.invoke(url)
        }
    }
    
    private fun checkServerAvailability(url: String): Boolean {
        try {
            val healthUrl = if (url.endsWith("/")) url.substring(0, url.length - 1) else url
            
            val request = Request.Builder()
                .url("$healthUrl/api/server-info")
                .build()
            
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Проверка доступности сервера по URL: $url, результат: ${response.isSuccessful}")
                return response.isSuccessful
            }
        } catch (e: IOException) {
            Log.e(TAG, "Сервер недоступен: $url", e)
            return false
        }
    }
} 