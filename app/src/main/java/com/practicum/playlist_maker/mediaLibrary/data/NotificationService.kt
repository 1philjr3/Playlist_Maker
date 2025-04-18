package com.practicum.playlist_maker.mediaLibrary.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.marchAuto.MyFirebaseMessagingService
import com.practicum.playlist_maker.network.AppContextProvider
import com.practicum.playlist_maker.pdfreader.ui.activity.reader.PdfReaderActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import com.practicum.playlist_maker.network.ServerUrlUpdater

/**
 * Сервис для отправки уведомлений о новых файлах через Firebase Cloud Messaging
 */
class NotificationService {
    private val TAG = "NotificationService"
    private val CHANNEL_ID = "file_notification_channel"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()
    
    private val gson = Gson()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val APP_VERSION = "1.0"
    
    // Создает каналы уведомлений при инициализации сервиса
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        try {
            // Получаем контекст приложения
            val context = getApplicationContext()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // Канал для уведомлений о файлах
                val fileChannel = NotificationChannel(
                    CHANNEL_ID,
                    "File Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications about new files and updates"
                    enableLights(true)
                    lightColor = Color.BLUE
                    enableVibration(true)
                    setShowBadge(true)
                }
                
                notificationManager.createNotificationChannel(fileChannel)
                Log.d(TAG, "Notification channels created")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification channels", e)
        }
    }
    
    // Безопасное получение контекста приложения
    private fun getApplicationContext(): Context {
        return AppContextProvider.appContext
            ?: throw IllegalStateException("Application context not initialized")
    }
    
    /**
     * Отправляет уведомление о файле на сервер
     */
    fun sendFileNotification(
        fileId: String,
        fileName: String,
        description: String,
        userId: String? = null,
        userName: String? = null,
        retry: Int = 3
    ) {
        coroutineScope.launch {
            Log.d(TAG, "Preparing to send file notification: $fileId - $fileName")
            
            try {
                // Проверяем, не отправлялось ли уже уведомление для этого файла
                val notificationKey = "file_notification:$fileId"
                val prefs = getApplicationContext().getSharedPreferences("FCM_NOTIFICATION_PREFS", Context.MODE_PRIVATE)
                val lastSentTime = prefs.getLong(notificationKey, 0L)
                val currentTime = System.currentTimeMillis()
                
                // Если уведомление уже было отправлено в течение последних 24 часов, пропускаем
                if (lastSentTime > 0 && currentTime - lastSentTime < 24 * 60 * 60 * 1000) {
                    Log.d(TAG, "Notification for file $fileId already sent at ${Date(lastSentTime)}, skipping")
                    return@launch
                }
                
                // 1. Отправляем локальное уведомление на устройстве
                checkAndSendLocalNotification(fileId, fileName, description, userName)
                
                // 2. Пытаемся отправить уведомление через FCM с использованием локального URL
                // Используем локальный URL
                val localUrl = com.practicum.playlist_maker.network.ServerUrlUpdater.getDuckDnsUrl()
                
                var isNotificationSent = false
                var lastError: Exception? = null
                
                // Указываем конкретный URL который будем использовать
                val notificationEndpointUrl = "$localUrl/api/notifications/fcm"
                
                try {
                    Log.d(TAG, "Попытка подключения к серверу: $localUrl")
                    
                    // Подготавливаем данные уведомления
                    val notificationData = JsonObject().apply {
                        addProperty("fileId", fileId)
                        addProperty("fileName", fileName)
                        addProperty("description", description)
                        
                        // Добавляем userId только если он не пустой
                        if (!userId.isNullOrEmpty()) {
                            addProperty("userId", userId)
                        }
                        
                        // Добавляем userName только если он не пустой
                        if (!userName.isNullOrEmpty()) {
                            addProperty("userName", userName)
                        }
                        
                        // Добавляем флаг, чтобы сервер знал, что это уведомление нужно отправить только один раз
                        addProperty("sendOnce", true)
                    }
                    
                    val requestBody = gson.toJson(notificationData)
                        .toRequestBody("application/json".toMediaTypeOrNull())
                    
                    val request = Request.Builder()
                        .url(notificationEndpointUrl)
                        .post(requestBody)
                        .header("Content-Type", "application/json")
                        .build()
                    
                    Log.d(TAG, "Sending FCM notification to $notificationEndpointUrl")
                    Log.d(TAG, "Notification data: $notificationData")
                    
                    // Используем синхронный вызов для лучшей обработки ошибок
                    try {
                        val response = client.newCall(request).execute()
                        val responseBody = response.body?.string() ?: ""
                        
                        if (response.isSuccessful) {
                            Log.d(TAG, "Уведомление успешно отправлено: $responseBody")
                            isNotificationSent = true
                            
                            // Сохраняем информацию о том, что уведомление было отправлено
                            prefs.edit().putLong(notificationKey, currentTime).apply()
                        } else {
                            // Анализируем код ошибки и сообщение
                            when (response.code) {
                                404 -> Log.e(TAG, "Серверный эндпоинт не найден: $notificationEndpointUrl")
                                400 -> Log.e(TAG, "Неверный запрос к серверу: $responseBody")
                                401 -> Log.e(TAG, "Неавторизованный доступ к серверу: $responseBody")
                                else -> Log.e(TAG, "Ошибка отправки уведомления: ${response.code} - $responseBody")
                            }
                        }
                        response.close()
                    } catch (e: IOException) {
                        Log.e(TAG, "Ошибка сети при отправке уведомления", e)
                        lastError = e
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Исключение при отправке уведомления", e)
                    lastError = e
                }
                
                // Если уведомление не отправлено, повторяем попытку с уменьшенным счетчиком
                if (!isNotificationSent && retry > 0) {
                    Log.d(TAG, "Повторная попытка отправки уведомления. Осталось попыток: ${retry - 1}")
                    delay(1000) // Ждем 1 секунду перед повторной попыткой
                    sendFileNotification(fileId, fileName, description, userId, userName, retry - 1)
                } else if (!isNotificationSent) {
                    Log.e(TAG, "Не удалось отправить уведомление после всех попыток", lastError)
                    // В конце концов, пытаемся отправить локальное уведомление как последнее средство
                    checkAndSendLocalNotification(fileId, fileName, description, userName, true)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendFileNotification", e)
                // Пытаемся отправить локальное уведомление при критической ошибке
                checkAndSendLocalNotification(fileId, fileName, description, userName, true)
            }
        }
    }
    
    // Проверяет возможность отправки локального уведомления и отправляет его
    private fun checkAndSendLocalNotification(
        fileId: String,
        fileName: String,
        description: String,
        userName: String?,
        forceSend: Boolean = false
    ) {
        try {
            val context = getApplicationContext()
            
            // Создаем title и body для уведомления
            val title = "proVITA • Новый файл" // Брендирование в заголовке
            val body = if (userName != null) {
                "$fileName\nОт пользователя: $userName\n$description"
            } else {
                "$fileName\n$description"
            }
            
            // Просто отправляем через системный NotificationManager
            Log.d(TAG, "Using system NotificationManager for local notification")
            sendLocalNotification(context, title, body, fileId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking and sending local notification", e)
        }
    }
    
    // Отправляет локальное уведомление через NotificationManager
    private fun sendLocalNotification(context: Context, title: String, body: String, fileId: String) {
        try {
            // Создаем канал уведомлений для Android 8.0 и выше
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "File Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications about new files and updates"
                    enableLights(true)
                    lightColor = Color.BLUE
                    enableVibration(true)
                    setShowBadge(true)
                }
                
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
            
            // Создаем intent для открытия активности при нажатии на уведомление
            val intent = Intent(context, PdfReaderActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("fileId", fileId)
            }
            
            val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                fileId.hashCode(),
                intent,
                pendingIntentFlag
            )
            
            // Создаем звук уведомления
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            // Создаем большую иконку (цветную) - либо из круглой иконки приложения,
            // либо из цветной версии иконки с полосками
            val largeIconBitmap = try {
                // Пытаемся загрузить цветную версию иконки с полосками
                val drawable = ResourcesCompat.getDrawable(context.resources, 
                    R.drawable.ic_notification_color, null)
                
                if (drawable != null) {
                    // Преобразуем векторный drawable в Bitmap
                    val bitmap = Bitmap.createBitmap(
                        drawable.intrinsicWidth, 
                        drawable.intrinsicHeight, 
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap
                } else {
                    // В случае ошибки используем стандартную иконку
                    BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_round)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating large icon", e)
                BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_round)
            }
            
            // Монохромная иконка для статус-бара (будет отображаться белым цветом)
            val smallIconResId = R.drawable.ic_notification_mono
            
            // Используем BigTextStyle для отображения длинного текста с описанием
            val bigTextStyle = NotificationCompat.BigTextStyle()
                .bigText(body)
            
            // Создаем настройки для уведомления
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(smallIconResId) // Монохромная иконка для статус-бара
                .setLargeIcon(largeIconBitmap)      // Цветная иконка для самого уведомления
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(bigTextStyle)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
            
            // Устанавливаем цвет фона для иконки в статус-баре
            // Этот цвет НЕ меняет белый цвет иконки, а только задает цвет фона
            builder.setColor(Color.rgb(51, 102, 225)) // Ярко-синий цвет
                
            // Отправляем уведомление
            val notificationManager = NotificationManagerCompat.from(context)
            
            // Используем hashCode fileId как notificationId, чтобы избежать дублирования
            val notificationId = fileId.hashCode()
            
            try {
                notificationManager.notify(notificationId, builder.build())
                Log.d(TAG, "Local notification sent (id=$notificationId): $title - $body")
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied to show notification", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending local notification", e)
        }
    }
    
    /**
     * Обновляет FCM токен на сервере
     */
    fun updateFcmToken(context: Context, fcmToken: String) {
        coroutineScope.launch {
        try {
                Log.d(TAG, "Updating FCM token on server...")
                
                // Получаем userId из SharedPreferences
            val prefs = context.getSharedPreferences("PROVITA_SETTINGS", Context.MODE_PRIVATE)
                var userId = prefs.getString("USER_ID", null)
                
                // Если userId не найден, генерируем случайный userId для анонимного пользователя
                if (userId == null) {
                    userId = "anonymous_${System.currentTimeMillis()}"
                    Log.d(TAG, "Generating anonymous userId: $userId")
                    
                    // Сохраняем новый userId в SharedPreferences
                    prefs.edit().putString("USER_ID", userId).apply()
                }
                
                Log.d(TAG, "Using userId: $userId")
                
                // Проверяем, что у нас есть и токен, и userId
                if (fcmToken.isNotBlank() && !userId.isNullOrBlank()) {
                    
                    // Используем локальный URL
                    val localUrl = com.practicum.playlist_maker.network.ServerUrlUpdater.getDuckDnsUrl()
                    val tokenEndpointUrl = "$localUrl/api/users/fcm-token"
                    
                    var tokenUpdateSuccessful = false
                    var lastError: Exception? = null
                    
                    try {
                        Log.d(TAG, "Attempting to connect to local server: $localUrl for token update")
                        
                        // Создаем данные для запроса обновления токена
                        val deviceInfo = Build.MANUFACTURER + " " + Build.MODEL
                        val appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        
                        val tokenData = JsonObject().apply {
                            addProperty("userId", userId)
                            addProperty("fcmToken", fcmToken)
                            addProperty("deviceInfo", deviceInfo)
                            addProperty("appVersion", appVersion)
                            addProperty("timestamp", System.currentTimeMillis())
                        }
                        
                        val requestBody = gson.toJson(tokenData)
                            .toRequestBody("application/json".toMediaTypeOrNull())
                
                        val request = Request.Builder()
                            .url(tokenEndpointUrl)
                            .post(requestBody)
                            .header("Content-Type", "application/json")
                            .build()
                        
                        Log.d(TAG, "Sending FCM token to $tokenEndpointUrl")
                        Log.d(TAG, "Token data: $tokenData")
                        
                        // Используем синхронный вызов для лучшей обработки ошибок
                        try {
                            val response = client.newCall(request).execute()
                            val responseBody = response.body?.string() ?: ""
                            
                            if (response.isSuccessful) {
                                Log.d(TAG, "FCM token updated successfully on local server: $responseBody")
                                tokenUpdateSuccessful = true
                            } else {
                                // Анализируем код ошибки и сообщение
                                when (response.code) {
                                    404 -> Log.e(TAG, "Server endpoint not found: $tokenEndpointUrl")
                                    400 -> Log.e(TAG, "Bad request to server: $responseBody")
                                    401 -> Log.e(TAG, "Unauthorized access to server: $responseBody")
                                    else -> Log.e(TAG, "Error updating token: ${response.code} - $responseBody")
                                }
                            }
                            response.close()
                        } catch (e: IOException) {
                            Log.e(TAG, "Network error while updating FCM token on local server", e)
                            lastError = e
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception while updating FCM token on local server", e)
                        lastError = e
                    }
                    
                    if (!tokenUpdateSuccessful) {
                        Log.e(TAG, "Failed to update FCM token on local server", lastError)
                        
                        // Сохраняем токен локально для использования в последующих запросах
                        prefs.edit().putString("FCM_TOKEN", fcmToken).apply()
                        
                        // Показываем уведомление о проблеме подключения
                        withContext(Dispatchers.Main) {
                            try {
                                sendLocalNotification(
                                    context,
                                    "Проблема подключения",
                                    "Не удалось подключиться к серверу. Пожалуйста, проверьте подключение к сети.",
                                    "connection_error"
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error showing connection error notification", e)
                            }
                        }
                    } else {
                        // Сохраняем токен и отмечаем успешную регистрацию
                        prefs.edit()
                            .putString("FCM_TOKEN", fcmToken)
                            .putBoolean("FCM_TOKEN_REGISTERED", true)
                            .apply()
                    }
                    
                } else {
                    Log.e(TAG, "Cannot update FCM token: token or userId is empty")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in updateFcmToken", e)
            }
        }
    }
    
    /**
     * Получает URL сервера.
     * @return URL сервера
     */
    fun getServerUrl(): String {
        // Используем локальный URL
        val localUrl = com.practicum.playlist_maker.network.ServerUrlUpdater.getDuckDnsUrl()
        Log.d(TAG, "Using local URL: $localUrl")
        
        // Сохраняем URL в настройках
        val prefs = getApplicationContext().getSharedPreferences("PROVITA_SETTINGS", Context.MODE_PRIVATE)
        prefs.edit().putString("SERVER_URL", localUrl).apply()
        
        return localUrl
    }
    
    // Отправляет тестовое уведомление для проверки работы системы уведомлений
    fun sendTestNotification(context: Context) {
        try {
            // Показываем тестовое уведомление локально
            sendLocalNotification(
                context,
                "Тестовое уведомление",
                "Проверка системы уведомлений. Приложение запущено и уведомления работают.",
                "test_notification"
            )
            
            // Выводим текущий FCM токен в лог
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        Log.d(TAG, "Current FCM token: $token")
                    } else {
                        Log.e(TAG, "Error retrieving FCM token for test", task.exception)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending test notification", e)
        }
    }
} 
