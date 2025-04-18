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
import java.net.UnknownHostException

/**
 * Класс для автоматического обновления URL сервера в приложении.
 * 
 * ВЕРСИЯ С НАСТРОЕННЫМ DUCKDNS ДОМЕНОМ
 * 
 * Вставьте этот файл в пакет com.practicum.playlist_maker.network вашего проекта
 * и вызовите его в onCreate() вашего класса Application или в активити.
 */
class ServerUrlUpdater(context: Context) {
    companion object {
        private const val TAG = "ServerUrlUpdater"
        private const val SETTINGS_PREFS = "PROVITA_SETTINGS"
        private const val SERVER_URL_KEY = "SERVER_URL"
        private const val LAST_WORKING_IP_KEY = "LAST_WORKING_IP"
        private const val RETRY_COUNT = 3
        private const val CONNECT_TIMEOUT_SECONDS = 5L
        
        // Постоянный домен DuckDNS с портом
        private const val DUCKDNS_URL = "http://provitaserver.duckdns.org:3000"
        
        // Список доступных IP-адресов
        private val availableIps = listOf(
            "http://93.100.35.126:3000",  // Текущий IP из логов
            "http://45.85.105.92:3000"    // Предыдущий IP из логов
        )
        
        // Локальные URL для отладки
        private val localUrls = listOf(
            "http://localhost:3000",  // Локальный с портом 3000
            "http://127.0.0.1:3000",  // Локальный с портом 3000
            "http://10.0.2.2:3000"    // Эмулятор с портом 3000
        )
        
        /**
         * Удобный метод для использования в Kotlin-коде
         */
        fun updateServerUrl(context: Context, callback: ((String?) -> Unit)? = null) {
            val updater = ServerUrlUpdater(context)
            val defaultUrls = arrayOf(
                DUCKDNS_URL,        // Постоянный DuckDNS домен (приоритет)
                "http://localhost:3000",  // Локальный с портом 3000
                "http://127.0.0.1:3000",  // Локальный с портом 3000
                "http://10.0.2.2:3000"    // Эмулятор с портом 3000
            )
            
            // Добавляем IP-адреса
            val prefs = context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            val lastWorkingIp = prefs.getString(LAST_WORKING_IP_KEY, null)
            val allUrls = if (lastWorkingIp != null) {
                val ipUrl = "http://$lastWorkingIp:3000"
                Log.d(TAG, "Добавляем последний рабочий IP: $ipUrl")
                arrayOf(DUCKDNS_URL, ipUrl) + availableIps + localUrls
            } else {
                arrayOf(DUCKDNS_URL) + availableIps + localUrls
            }
            
            updater.checkAndUpdateServerUrl(allUrls) { serverUrl ->
                Log.d(TAG, "Server URL updated: $serverUrl")
                callback?.invoke(serverUrl)
            }
        }
        
        /**
         * Получить постоянный URL сервера (без проверки доступности)
         */
        fun getDuckDnsUrl(): String {
            Log.d(TAG, "Используем локальный IP: http://172.17.3.66:3000")
            return "http://172.17.3.66:3000"
        }

        /**
         * Исправляет URL, добавляя порт 3000 если его нет
         */
        fun fixServerUrl(url: String?): String? {
            if (url == null) return null
            
            // Если URL уже содержит порт или это не DuckDNS, возвращаем как есть
            if (url.contains(":3000") || !url.contains("provitaserver.duckdns.org")) {
                return url
            }
            
            // Иначе добавляем порт 3000
            return url.replace("provitaserver.duckdns.org", "provitaserver.duckdns.org:3000")
        }
    }
    
    private val appContext = context.applicationContext
    private val executor: Executor = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(CONNECT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    /**
     * Проверяет и обновляет URL сервера в SharedPreferences.
     * @param serverCheckUrls список URL для проверки (например, localhost, ngrok URL)
     * @param callback обратный вызов, возвращающий найденный URL или null
     */
    fun checkAndUpdateServerUrl(serverCheckUrls: Array<String>, callback: (String?) -> Unit) {
        executor.execute {
            Log.d(TAG, "Checking server URLs...")
            
            // 1. Проверяем сохраненный URL
            val savedUrl = getSavedServerUrl()
            Log.d(TAG, "Saved server URL: $savedUrl")
            
            // 2. Если есть сохраненный URL, проверяем его первым с повторными попытками
            if (savedUrl != null) {
                if (tryWithRetry(savedUrl)) {
                    Log.d(TAG, "✅ Сохраненный URL доступен: $savedUrl")
                    extractAndSaveIpIfNeeded(savedUrl)
                    notifyCallback(callback, savedUrl)
                    return@execute
                } else {
                    Log.d(TAG, "❌ Сохраненный URL недоступен: $savedUrl")
                }
            }
            
            // 3. Проверяем все URL по порядку с повторными попытками
            for (url in serverCheckUrls) {
                Log.d(TAG, "Проверка URL: $url")
                if (tryWithRetry(url)) {
                    Log.d(TAG, "✅ URL доступен: $url")
                    saveServerUrl(url)
                    extractAndSaveIpIfNeeded(url)
                    notifyCallback(callback, url)
                    return@execute
                }
            }
            
            // 4. Возвращаем DuckDNS в крайнем случае
            Log.e(TAG, "❌ Все URL недоступны, возвращаем DuckDNS как крайний вариант")
            saveServerUrl(DUCKDNS_URL)
            notifyCallback(callback, DUCKDNS_URL)
        }
    }
    
    /**
     * Пытается подключиться к серверу с несколькими попытками
     */
    private fun tryWithRetry(url: String): Boolean {
        for (i in 1..RETRY_COUNT) {
            try {
                if (checkServerAvailability(url)) {
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при попытке $i для $url", e)
            }
            
            // Небольшая задержка между попытками
            Thread.sleep(300)
        }
        return false
    }
    
    /**
     * Проверяет доступность сервера по URL
     * @param url URL сервера для проверки
     * @return true, если сервер доступен
     */
    private fun checkServerAvailability(url: String): Boolean {
        try {
            // Убедимся, что URL содержит порт 3000 для DuckDNS
            val fixedUrl = ServerUrlUpdater.fixServerUrl(url) ?: url
            val healthUrl = if (fixedUrl.endsWith("/")) fixedUrl.substring(0, fixedUrl.length - 1) else fixedUrl
            
            Log.d(TAG, "Проверка доступности сервера: $healthUrl/api/server-info")
            
            val request = Request.Builder()
                .url("$healthUrl/api/server-info")
                .build()
            
            client.newCall(request).execute().use { response ->
                val isSuccess = response.isSuccessful
                if (isSuccess) {
                    Log.d(TAG, "✅ Соединение успешно: $healthUrl")
                } else {
                    Log.d(TAG, "❌ Сервер ответил с кодом ${response.code}: $healthUrl")
                }
                return isSuccess
            }
        } catch (e: UnknownHostException) {
            Log.e(TAG, "⚠️ DNS не может разрешить хост: $url", e)
            return false
        } catch (e: IOException) {
            Log.e(TAG, "⚠️ Сервер недоступен: $url", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Неизвестная ошибка при проверке сервера: $url", e)
            return false
        }
    }
    
    /**
     * Извлекает IP из URL и сохраняет его, если это не localhost
     */
    private fun extractAndSaveIpIfNeeded(url: String) {
        try {
            val regex = "http://(\\d+\\.\\d+\\.\\d+\\.\\d+):".toRegex()
            val matchResult = regex.find(url)
            val ip = matchResult?.groupValues?.get(1)
            
            if (ip != null && !ip.startsWith("127.") && !ip.startsWith("10.") && !ip.startsWith("192.168.")) {
                Log.d(TAG, "Сохраняем рабочий IP: $ip")
                val prefs = appContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                prefs.edit().putString(LAST_WORKING_IP_KEY, ip).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при извлечении IP", e)
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