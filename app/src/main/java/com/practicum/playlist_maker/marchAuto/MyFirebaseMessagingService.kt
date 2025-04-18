package com.practicum.playlist_maker.marchAuto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.mediaLibrary.data.NotificationService
import com.practicum.playlist_maker.root.RootActivity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val CHANNEL_ID = "FCM_CHANNEL"
private const val CHANNEL_NAME = "FCM Notifications"
private const val TAG = "FCM_Service"
private const val TOPIC_ALL = "all"
private const val PREFS_NAME = "FCM_NOTIFICATION_PREFS"
private const val DUPLICATES_CHECK_PERIOD = 24 * 60 * 60 * 1000 // 24 часа в миллисекундах

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private val notificationId = AtomicInteger(0)
        private val isTokenUpdateInProgress = AtomicBoolean(false)
        private var cachedFcmToken: String? = null
        
        // Исполнитель для выполнения задач в фоновом потоке
        private val executor: ExecutorService = Executors.newSingleThreadExecutor()
        
        // Кэш для отслеживания отправленных уведомлений, чтобы избежать дубликатов
        private val sentNotifications = ConcurrentHashMap<String, Long>()
        
        // Функция для подписки на топик FCM при запуске приложения
        fun subscribeToTopics() {
            executor.submit {
                try {
                    // Подписываемся на топик "all" для получения общих уведомлений
                    FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d(TAG, "Subscribed to topic $TOPIC_ALL successfully!")
                            } else {
                                Log.e(TAG, "Failed to subscribe to topic $TOPIC_ALL", task.exception)
                            }
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error subscribing to topics", e)
                }
            }
        }
        
        // Инициализация FCM в приложении
        fun initializeFCM(context: Context) {
            executor.submit {
                try {
                    Log.d(TAG, "Initializing FCM in background thread...")
                    
                    // Получаем и отправляем FCM токен на сервер
                    requestFcmToken(context)
                    
                    // Подписываемся на общую тему для всех устройств
                    subscribeToTopics()
                    
                    Log.d(TAG, "FCM initialization submitted")
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing FCM", e)
                }
            }
        }
        
        // Отправляет запрос на получение токена FCM и обновляет его на сервере
        private fun requestFcmToken(context: Context) {
            if (isTokenUpdateInProgress.getAndSet(true)) {
                Log.d(TAG, "Token update already in progress, skipping")
                return
            }
            
            try {
                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        isTokenUpdateInProgress.set(false)
                        
                        if (!task.isSuccessful) {
                            Log.e(TAG, "Fetching FCM registration token failed", task.exception)
                            return@addOnCompleteListener
                        }
                        
                        // Получаем токен FCM
                        val token = task.result
                        cachedFcmToken = token
                        
                        // Выводим токен в лог
                        Log.d(TAG, "FCM Registration Token: $token")
                        
                        // Отправляем токен на сервер в фоновом потоке
                        executor.submit {
                            try {
                                val notificationService = NotificationService()
                                notificationService.updateFcmToken(context, token)
                                Log.d(TAG, "FCM token updated on server")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error updating FCM token on server", e)
                            }
                        }
                    }
            } catch (e: Exception) {
                isTokenUpdateInProgress.set(false)
                Log.e(TAG, "Error requesting FCM token", e)
            }
        }
        
        // Получение текущего токена FCM
        fun getCurrentFcmToken(): String? {
            return cachedFcmToken
        }
    }
    
    private var localNotificationId = 0
    private lateinit var sharedPreferences: SharedPreferences
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FCM service created")
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Загружаем сохраненные уведомления в память при создании сервиса
        loadSentNotifications()
    }
    
    // Загружает информацию о ранее отправленных уведомлениях из SharedPreferences
    private fun loadSentNotifications() {
        try {
            // Получаем все сохраненные ключи уведомлений
            val allNotifications = sharedPreferences.all
            val currentTime = System.currentTimeMillis()
            
            // Очищаем старые записи (старше 24 часов)
            val editor = sharedPreferences.edit()
            
            for ((key, timestamp) in allNotifications) {
                if (timestamp is Long) {
                    if (currentTime - timestamp > DUPLICATES_CHECK_PERIOD) {
                        // Удаляем старые записи
                        editor.remove(key)
                    } else {
                        // Загружаем актуальные записи в память
                        sentNotifications[key] = timestamp
                    }
                }
            }
            
            editor.apply()
            Log.d(TAG, "Loaded ${sentNotifications.size} sent notifications from storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sent notifications", e)
        }
    }
    
    // Просто переопределяем метод onNewToken для обработки новых токенов
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        
        Log.d(TAG, "New FCM token received: $token")
        cachedFcmToken = token
        
        // Отправляем токен на сервер в фоновом потоке
        executor.submit {
            try {
                val notificationService = NotificationService()
                notificationService.updateFcmToken(applicationContext, token)
                Log.d(TAG, "FCM token updated on server after refresh")
                
                // Обновляем подписки на топики при получении нового токена
                subscribeToTopics()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating token on server", e)
            }
        }
    }
    
    // Обрабатываем входящие сообщения FCM
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        executor.submit {
            try {
                Log.d(TAG, "Message received from: ${remoteMessage.from}")
                
                // Обрабатываем только сообщения о загрузке файлов
                if (remoteMessage.data.isNotEmpty() && remoteMessage.data.containsKey("fileId")) {
                    Log.d(TAG, "File upload notification received: ${remoteMessage.data}")
                    
                    val fileId = remoteMessage.data["fileId"]
                    val fileName = remoteMessage.data["fileName"] ?: "Unknown file"
                    val userName = remoteMessage.data["userName"] ?: "Unknown user"
                    val description = remoteMessage.data["description"] ?: ""
                    
                    // Показываем уведомление только для файлов
                    val notificationTitle = "Загружен новый файл: $fileName"
                    val notificationBody = when {
                        userName != "Unknown user" && description.isNotEmpty() -> {
                            "От пользователя: $userName\n$description"
                        }
                        userName != "Unknown user" -> {
                            "От пользователя: $userName"
                        }
                        description.isNotEmpty() -> {
                            "Описание: $description"
                        }
                        else -> {
                            "Файл загружен"
                        }
                    }
                    
                    // Создаем уникальный идентификатор уведомления - файловые уведомления дедуплицируются по fileId
                    val notificationKey = "file_notification:$fileId"
                    
                    // Показываем локальное уведомление только если оно ещё не было показано
                    if (!wasNotificationAlreadySent(notificationKey)) {
                        sendNotification(notificationTitle, notificationBody, fileId, fileName, notificationKey)
                    } else {
                        Log.d(TAG, "Notification for fileId=$fileId already sent, skipping")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing FCM message", e)
            }
        }
    }
    
    // Проверяет, было ли уведомление уже отправлено
    private fun wasNotificationAlreadySent(notificationKey: String): Boolean {
        val timestamp = sharedPreferences.getLong(notificationKey, 0L)
        val currentTime = System.currentTimeMillis()
        
        // Если есть запись и она не старше периода проверки дубликатов, то уведомление уже было отправлено
        return timestamp > 0 && currentTime - timestamp < DUPLICATES_CHECK_PERIOD
    }
    
    // Сохраняет информацию об отправленном уведомлении
    private fun saveNotificationSent(notificationKey: String) {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Записываем время отправки этого уведомления в память
            sentNotifications[notificationKey] = currentTime
            
            // И сохраняем его в постоянное хранилище
            sharedPreferences.edit()
                .putLong(notificationKey, currentTime)
                .apply()
            
            Log.d(TAG, "Saved notification key: $notificationKey")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving notification record", e)
        }
    }
    
    // Показывает локальное уведомление
    private fun sendNotification(title: String, body: String, fileId: String? = null, fileName: String? = null, notificationKey: String? = null) {
        try {
            // Сохраняем информацию об отправке уведомления, если указан ключ
            if (notificationKey != null) {
                saveNotificationSent(notificationKey)
            }
            
            localNotificationId = (localNotificationId + 1) % 1000
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
            // Создаем канал уведомлений для Android 8.0 и выше
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Channel for file notifications"
                    enableLights(true)
                    lightColor = Color.GREEN
                    enableVibration(true)
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        
            // Создаем intent для открытия активности при нажатии на уведомление
            val intent = Intent(this, RootActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (fileId != null) {
                    putExtra("fileId", fileId)
                }
                if (fileName != null) {
                    putExtra("fileName", fileName)
                }
            }
            
            // Создаем уникальный идентификатор для PendingIntent
            val requestCode = if (fileId != null) {
                fileId.hashCode()
            } else {
                System.currentTimeMillis().toInt()
            }
            
            val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        
            val pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                pendingIntentFlag
            )
            
            // Используем BigTextStyle для отображения длинного текста с описанием
            val bigTextStyle = NotificationCompat.BigTextStyle()
                .bigText(body)
                .setBigContentTitle(title)
            
            // Создаем и показываем уведомление
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(bigTextStyle) // Добавляем стиль для длинного текста
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            
            // Используем постоянный ID для уведомлений с одинаковым fileId
            val actualNotificationId = fileId?.hashCode() ?: localNotificationId
            
            notificationManager.notify(actualNotificationId, notification)
            Log.d(TAG, "File notification sent (id=$actualNotificationId): $title - $body")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FCM service destroyed")
    }
}
