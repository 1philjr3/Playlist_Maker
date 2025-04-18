package com.practicum.playlist_maker.mediaLibrary.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.mediaLibrary.ui.fragment.FileNotificationReceiver
import com.practicum.playlist_maker.root.RootActivity
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Сервис для приема WebSocket уведомлений о новых файлах
 */
class NotificationWebSocketService : Service() {

    private val TAG = "WebSocketService"
    private val FOREGROUND_SERVICE_ID = 9999
    private val CHANNEL_ID = "websocket_service_channel"
    private val APP_VERSION = "1.0" // Константа вместо BuildConfig.VERSION_NAME
    
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket требует бесконечного тайм-аута
        .pingInterval(30, TimeUnit.SECONDS) // Отправка ping для поддержания соединения
        .connectTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private var webSocket: WebSocket? = null
    private val binder = NotificationBinder()
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 5 // Увеличиваем количество попыток
    private var serverAvailable = true
    private var isConnecting = false
    private var currentServerUrl: String? = null
    private var currentUserId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var shouldReconnect = true
    
    inner class NotificationBinder : Binder() {
        fun getService(): NotificationWebSocketService = this@NotificationWebSocketService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Создаем WakeLock для предотвращения перехода в спящий режим
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WebSocketService::WakeLock"
        )
        
        // Запускаем сервис в режиме переднего плана для предотвращения остановки системой
        startForegroundService()
    }
    
    private fun startForegroundService() {
        // Создаем канал уведомлений для Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WebSocket Service",
                NotificationManager.IMPORTANCE_LOW // Низкий приоритет, чтобы не беспокоить пользователя
            ).apply {
                description = "Поддерживает подключение для получения уведомлений"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // Создаем интент для открытия приложения при нажатии
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RootActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Создаем уведомление
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Provita")
            .setContentText("Подключен к серверу уведомлений")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        // Запускаем сервис в режиме переднего плана
        startForeground(FOREGROUND_SERVICE_ID, notification)
        Log.d(TAG, "Started as foreground service")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand, flags=$flags, startId=$startId")
        
        // Если сервис был убит системой, восстанавливаем соединение
        if (currentServerUrl != null && currentUserId != null && webSocket == null) {
            Log.d(TAG, "Reconnecting after system restart")
            connectToWebSocket(currentServerUrl!!, currentUserId!!)
        }
        
        // START_STICKY - перезапускать сервис, если он был убит системой
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    /**
     * Проверка доступности сервера
     */
    private fun checkServerAvailability(serverUrl: String, callback: (Boolean) -> Unit) {
        val request = Request.Builder()
            .url("$serverUrl/health")
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Server availability check failed", e)
                callback(false)
            }
            
            override fun onResponse(call: Call, response: Response) {
                val isAvailable = response.isSuccessful
                Log.d(TAG, "Server availability: $isAvailable (code ${response.code})")
                callback(isAvailable)
            }
        })
    }
    
    /**
     * Подключение к WebSocket серверу для получения уведомлений
     */
    fun connectToWebSocket(serverUrl: String, userId: String) {
        // Сохраняем параметры для возможности переподключения
        currentServerUrl = serverUrl
        currentUserId = userId
        
        Log.d(TAG, "Connecting to WebSocket server: $serverUrl, user: $userId")
        
        // Если соединение уже устанавливается, выходим
        if (isConnecting) {
            Log.d(TAG, "Connection already in progress, skipping")
            return
        }
        
        isConnecting = true
        
        // Активируем WakeLock для предотвращения уснуть устройству
        if (!wakeLock!!.isHeld) {
            wakeLock!!.acquire(10*60*1000L) // 10 минут максимум
        }
        
        // Проверяем доступность сервера только если это новая попытка подключения
        if (reconnectAttempts == 0) {
            // Всегда пытаемся подключиться к WebSocket, независимо от результата проверки
            checkServerAvailability(serverUrl) { available ->
                serverAvailable = available
                Log.d(TAG, "Server availability check result: $available, proceeding with WebSocket connection")
                initiateWebSocketConnection(serverUrl, userId)
            }
        } else if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            // Пробуем переподключиться если не превышено максимальное количество попыток
            initiateWebSocketConnection(serverUrl, userId)
        } else {
            Log.e(TAG, "Max reconnect attempts reached ($MAX_RECONNECT_ATTEMPTS), waiting for network change")
            
            // Сбрасываем счетчик попыток чтобы при следующем вызове снова проверить доступность сервера
            reconnectAttempts = 0
            isConnecting = false
            
            // Через некоторое время снова пробуем подключиться
            handler.postDelayed({
                if (shouldReconnect) {
                    Log.d(TAG, "Trying to reconnect after delay")
                    connectToWebSocket(serverUrl, userId)
                }
            }, 60 * 1000) // Через минуту
            
            // Освобождаем WakeLock на время ожидания
            if (wakeLock!!.isHeld) {
                wakeLock!!.release()
            }
        }
    }
    
    private fun initiateWebSocketConnection(serverUrl: String, userId: String) {
        // Если уже подключены, сначала отключаемся
        disconnectFromWebSocket()
        
        // Пробуем несколько различных путей для WebSocket
        val paths = listOf("/api/ws", "/ws", "", "/socket", "/api/socket", "/api/socket.io", "/socket.io", "/notifications/ws", "/api/v1/ws")
        var currentPathIndex = 0
        
        fun tryNextPath() {
            if (currentPathIndex >= paths.size) {
                // Все пути испробованы, увеличиваем счетчик попыток
                reconnectAttempts++
                isConnecting = false
                
                // Планируем повторную попытку с первого пути
                val delay = when {
                    reconnectAttempts <= 2 -> 5000L  // 5 секунд
                    reconnectAttempts <= 4 -> 30000L // 30 секунд
                    else -> 60000L                    // 1 минута
                }
                
                handler.postDelayed({
                    if (shouldReconnect) {
                        currentPathIndex = 0
                        connectToWebSocket(serverUrl, userId)
                    }
                }, delay)
                
                return
            }
            
            val path = paths[currentPathIndex]
            currentPathIndex++
            
            // Используем путь из текущей попытки
            val wsUrl = if (path.isEmpty()) {
                serverUrl.replace("http://", "ws://") + "?userId=$userId"
            } else {
                serverUrl.replace("http://", "ws://") + path + "?userId=$userId"
            }
            
            val request = Request.Builder()
                .url(wsUrl)
                .build()
                
            Log.d(TAG, "Trying WebSocket connection with URL: $wsUrl")
                
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connection OPENED SUCCESSFULLY with URL: $wsUrl")
                    Log.d(TAG, "Response code: ${response.code}, message: ${response.message}")
                    
                    // Сбрасываем счетчик попыток при успешном подключении
                    reconnectAttempts = 0
                    isConnecting = false
                    
                    // Обновляем уведомление
                    updateForegroundNotification("Активное подключение для уведомлений")
                    
                    // Отправляем сообщение о подключении
                    val connectMessage = JSONObject().apply {
                        put("type", "connect")
                        put("userId", userId)
                        put("deviceInfo", "${Build.MANUFACTURER} ${Build.MODEL}")
                        put("appVersion", APP_VERSION)
                        put("subscribeToNotifications", true)
                    }
                    webSocket.send(connectMessage.toString())
                    Log.d(TAG, "Sent connect message: ${connectMessage.toString()}")
                    
                    // Также подписываемся на уведомления
                    val subscribeMessage = JSONObject().apply {
                        put("type", "subscribe")
                        put("channel", "notifications")
                        put("userId", userId)
                    }
                    webSocket.send(subscribeMessage.toString())
                    Log.d(TAG, "Sent subscription message: ${subscribeMessage.toString()}")
                    
                    // Запускаем периодическую отправку ping для поддержания соединения
                    startPingScheduler(userId)
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "WebSocket message received: $text")
                    try {
                        val json = JSONObject(text)
                        
                        // Проверяем тип сообщения
                        val type = json.optString("type", "")
                        
                        if (type == "pong") {
                            // Это ответ на ping, соединение живо
                            Log.d(TAG, "Received pong from server")
                        } else if (type == "file_notification") {
                            val notification = json.optJSONObject("data")
                            if (notification != null) {
                                val fileId = notification.optString("fileId", "")
                                val fileName = notification.optString("fileName", "")
                                val description = notification.optString("description", "")
                                val userName = notification.optString("userName", "")
                                val fileUrl = notification.optString("fileUrl", "")
                                
                                // Отправляем уведомление в систему
                                if (fileId.isNotEmpty() && fileName.isNotEmpty()) {
                                    Log.d(TAG, "Sending local notification for file: $fileName by $userName")
                                    sendLocalNotification(fileId, fileName, description, userName, fileUrl)
                                }
                            }
                        } else if (type == "error") {
                            // Обрабатываем ошибку от сервера
                            val errorMsg = json.optString("message", "Unknown error")
                            Log.e(TAG, "Server WebSocket error: $errorMsg")
                            
                            // Если это ошибка аутентификации, пробуем переподключиться
                            if (errorMsg.contains("auth") || errorMsg.contains("authentication") || errorMsg.contains("unauthorized")) {
                                Log.d(TAG, "Authentication error, trying to reconnect...")
                                handler.postDelayed({
                                    if (shouldReconnect && currentServerUrl != null && currentUserId != null) {
                                        connectToWebSocket(currentServerUrl!!, currentUserId!!)
                                    }
                                }, 5000)
                            }
                        } else if (type == "connect" || type == "connected") {
                            Log.d(TAG, "Connection confirmation received from server")
                            
                            // Подтверждаем соединение, отправляем info о клиенте
                            val deviceInfo = JSONObject().apply {
                                put("type", "client_info")
                                put("userId", currentUserId)
                                put("deviceModel", Build.MODEL)
                                put("deviceName", Build.DEVICE)
                                put("appVersion", APP_VERSION)
                            }
                            webSocket.send(deviceInfo.toString())
                            Log.d(TAG, "Sent client info: ${deviceInfo.toString()}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing WebSocket message", e)
                    }
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket error with URL $wsUrl", t)
                    Log.e(TAG, "Response: ${response?.code}, ${response?.message}")
                    
                    // Пробуем следующий путь
                    handler.post { tryNextPath() }
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code, $reason")
                    
                    // Если соединение было закрыто нормально, не пытаемся переподключиться
                    if (code == 1000) {
                        isConnecting = false
                    } else if (shouldReconnect) {
                        // Иначе пробуем следующий путь
                        handler.post { tryNextPath() }
                    }
                }
            })
        }
        
        // Начинаем пробовать пути
        tryNextPath()
    }
    
    private fun startPingScheduler(userId: String) {
        thread {
            try {
                while (webSocket != null && shouldReconnect) {
                    Thread.sleep(30000) // Каждые 30 секунд
                    
                    val pingMessage = JSONObject().apply {
                        put("type", "ping")
                        put("userId", userId)
                        put("timestamp", System.currentTimeMillis())
                    }
                    webSocket?.send(pingMessage.toString())
                    Log.d(TAG, "Sent ping to server: ${pingMessage.toString()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in ping scheduler", e)
            }
        }
    }
    
    private fun updateForegroundNotification(status: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, RootActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Provita")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(FOREGROUND_SERVICE_ID, notification)
        }
    }
    
    /**
     * Отключение от WebSocket сервера
     */
    fun disconnectFromWebSocket() {
        webSocket?.close(1000, "Closed by user")
        webSocket = null
        
        // Обновляем уведомление
        updateForegroundNotification("Ожидание подключения к серверу")
    }
    
    /**
     * Отправка локального уведомления
     */
    private fun sendLocalNotification(fileId: String, fileName: String, description: String, userName: String, fileUrl: String) {
        Log.d(TAG, "Sending local notification: file $fileName, fileId: $fileId, fileUrl: $fileUrl")
        
        try {
            // 1. Отправляем через broadcast
            val intent = Intent("com.practicum.playlist_maker.FILE_NOTIFICATION").apply {
                putExtra("fileId", fileId)
                putExtra("fileName", fileName)
                putExtra("description", description)
                putExtra("userName", userName)
                putExtra("fileUrl", fileUrl)
            }
            
            sendBroadcast(intent)
            Log.d(TAG, "Local notification broadcast sent")
            
            // 2. Также пробуем напрямую показать уведомление
            try {
                val receiverContext = applicationContext
                val receiver = FileNotificationReceiver()
                receiver.onReceive(receiverContext, intent)
                Log.d(TAG, "Direct notification also attempted")
            } catch (e: Exception) {
                Log.e(TAG, "Error showing direct notification", e)
                
                // Если прямой вызов не удался, пробуем через Handler на главном потоке
                handler.post {
                    try {
                        val receiver = FileNotificationReceiver()
                        receiver.onReceive(applicationContext, intent)
                        Log.d(TAG, "Direct notification on main thread attempted")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing direct notification on main thread", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification", e)
        }
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        
        // Останавливаем переподключение
        shouldReconnect = false
        
        // Отключаемся от WebSocket
        disconnectFromWebSocket()
        
        // Освобождаем WakeLock, если он удерживается
        if (wakeLock != null && wakeLock!!.isHeld) {
            wakeLock!!.release()
        }
        
        super.onDestroy()
        
        // Пробуем перезапустить сервис
        val intent = Intent(this, NotificationWebSocketService::class.java)
        startService(intent)
    }
} 