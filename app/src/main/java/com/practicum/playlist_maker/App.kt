package com.practicum.playlist_maker

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.practicum.playlist_maker.marchAuto.MyFirebaseMessagingService
import com.practicum.playlist_maker.mediaLibrary.data.NotificationService
import com.practicum.playlist_maker.mediaLibrary.data.NotificationWebSocketService
import com.practicum.playlist_maker.mediaLibrary.di.mediaLibraryModule
import com.practicum.playlist_maker.mediaLibrary.di.playlistFragmentModule
import com.practicum.playlist_maker.mediaLibrary.ui.fragment.DirectoryContentFragment
import com.practicum.playlist_maker.mediaLibrary.ui.fragment.FileNotificationReceiver
import com.practicum.playlist_maker.player.data.db.AppDatabase
import com.practicum.playlist_maker.settings.di.settingsInteractorModule
import com.practicum.playlist_maker.settings.di.settingsRepositoryModule
import com.practicum.playlist_maker.settings.di.settingsViewModelModule
import com.practicum.playlist_maker.settings.domain.api.SettingsInteractor
import com.practicum.playlist_maker.network.AppContextProvider
import com.practicum.playlist_maker.network.ServerUrlUpdater
import dagger.hilt.android.HiltAndroidApp
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import android.app.NotificationManager
import android.app.NotificationChannel
import android.Manifest
import androidx.annotation.RequiresApi
import org.json.JSONObject

const val KEY_FOR_SWITCH = "key_for_switch"
const val SETTINGS_PREFERENCES = "settings_preferences"

@HiltAndroidApp
class App : Application() {

    companion object {
        private const val TAG = "App"
        var appContext: Context? = null
            private set

        // Предоставление доступа к базе данных через геттер
        lateinit var database: AppDatabase
            private set
    }

    private var webSocketService: NotificationWebSocketService? = null
    private var fileNotificationReceiver: FileNotificationReceiver? = null
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            webSocketService = (service as? NotificationWebSocketService.NotificationBinder)?.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            webSocketService = null
        }
    }

    private lateinit var appDatabase: com.practicum.playlist_maker.player.data.db.AppDatabase
    
    // Пул потоков для выполнения тяжелых задач без блокировки основного потока
    private val executor = Executors.newCachedThreadPool()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        
        // Установка статического контекста
        appContext = applicationContext
        
        // Инициализация AppContextProvider - должна быть самой первой!
        Log.d("App", "Initializing AppContextProvider")
        AppContextProvider.appContext = applicationContext
        
        // Инициализируем ServerUrlUpdater для обновления URL сервера
        initServerUrl()
        
        // Инициализация Firebase без блокировки основного потока
        FirebaseApp.initializeApp(this)
        
        // Инициализация PDFBox
        PDFBoxResourceLoader.init(this.applicationContext)

        // Инициализация базы данных Room
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "file_manager_db"
        ).fallbackToDestructiveMigration()
            .build()

        // Запуск Koin для внедрения зависимостей
        startKoin {
            androidContext(this@App)
            modules(
                settingsInteractorModule,
                settingsRepositoryModule,
                settingsViewModelModule,
                mediaLibraryModule,
                playlistFragmentModule,
            )
        }

        val settingsInteractor: SettingsInteractor by inject()
        settingsInteractor.switchTheme(settingsInteractor.isDarkTheme())
        
        // Регистрируем BroadcastReceiver для уведомлений
        registerFileNotificationReceiver()
        
        // Выполняем тяжелые операции в фоновом потоке
        executor.submit {
            // Проверяем разрешения на уведомления
            checkNotificationPermission()
            
            // Инициализация FCM в фоновом потоке
            initFirebaseMessaging()
            
            // Инициализируем сервис уведомлений
            initNotificationService()
        }
    }
    
    private fun initFirebaseMessaging() {
        try {
            Log.d(TAG, "Initializing Firebase Messaging in background thread")
            
            // Получаем FCM токен асинхронно
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        Log.e(TAG, "FCM TOKEN ПРИ ЗАПУСКЕ: $token")
                        
                        // Отправляем токен на сервер после получения
                        val notificationService = NotificationService()
                        notificationService.updateFcmToken(applicationContext, token)
                        
                        // Подписываемся на топики после получения токена
                        subscribeToTopics()
                    } else {
                        Log.e(TAG, "Failed to get FCM token on startup", task.exception)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase Messaging", e)
        }
    }
    
    private fun subscribeToTopics() {
        try {
            // Подписываемся на топик "all" для получения общих уведомлений
            FirebaseMessaging.getInstance().subscribeToTopic("all")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Subscribed to topic 'all' successfully!")
                    } else {
                        Log.e(TAG, "Failed to subscribe to topic 'all'", task.exception)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to topics", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun registerFileNotificationReceiver() {
        try {
            // Создаем и регистрируем BroadcastReceiver динамически
            fileNotificationReceiver = FileNotificationReceiver()
            val filter = IntentFilter("com.practicum.playlist_maker.FILE_NOTIFICATION")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(fileNotificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(fileNotificationReceiver, filter, RECEIVER_NOT_EXPORTED)
            }
            
            Log.d(TAG, "FileNotificationReceiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register FileNotificationReceiver", e)
        }
    }
    
    private fun checkNotificationPermission() {
        try {
            Log.d(TAG, "Checking notification permission")
            
            // Проверяем разрешение на уведомления для Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val areEnabled = notificationManager.areNotificationsEnabled()
                Log.d(TAG, "Notifications enabled: $areEnabled")
                
                if (!areEnabled) {
                    Log.d(TAG, "Notifications are disabled - requesting permission")
                    // Запрашиваем разрешение через флаг активности
                    val prefs = getSharedPreferences("PROVITA_SETTINGS", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("SHOULD_REQUEST_NOTIFICATION_PERMISSION", true).apply()
                }
            }
            
            // Проверяем доступность сервера
            executor.submit {
                checkServerAvailability()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification permission", e)
        }
    }
    
    // Проверяет доступность сервера
    private fun checkServerAvailability() {
        try {
            // Используем локальный URL для подключения
            val serverUrl = com.practicum.playlist_maker.network.ServerUrlUpdater.getDuckDnsUrl()
            Log.d(TAG, "Проверка доступности сервера: $serverUrl")
            
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                
                // Сначала проверяем новый маршрут с информацией о сервере
                val serverInfoRequest = Request.Builder()
                    .url("$serverUrl/api/server-info")
                    .get()
                    .build()
                
                Log.d(TAG, "Проверка информации о сервере: $serverUrl/api/server-info")
                
                try {
                    val serverInfoResponse = client.newCall(serverInfoRequest).execute()
                    if (serverInfoResponse.isSuccessful) {
                        Log.d(TAG, "✅ Сервер доступен!")
                        saveServerUrl(serverUrl)
                        serverInfoResponse.close()
                        return
                    } else {
                        Log.d(TAG, "❌ Проверка информации о сервере не удалась: ${serverInfoResponse.code}")
                        serverInfoResponse.close()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка при проверке информации о сервере", e)
                }
                
                // Если информацию о сервере не удалось получить, проверяем /health
                val healthRequest = Request.Builder()
                    .url("$serverUrl/health")
                    .get()
                    .build()
                
                Log.d(TAG, "Проверка состояния сервера: $serverUrl/health")
                
                val response = client.newCall(healthRequest).execute()
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Проверка состояния сервера успешна")
                    saveServerUrl(serverUrl)
                    response.close()
                    return
                }
                response.close()
                
                Log.e(TAG, "❌ Сервер недоступен")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Сервер недоступен: $serverUrl", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка при проверке доступности сервера", e)
        }
    }
    
    private fun saveServerUrl(url: String?) {
        try {
            if (url == null) {
                Log.e(TAG, "Cannot save null server URL")
                return
            }
            
            val prefs = getSharedPreferences("PROVITA_SETTINGS", Context.MODE_PRIVATE)
            val oldUrl = prefs.getString("SERVER_URL", "")
            
            if (oldUrl != url) {
                Log.d(TAG, "Saving new server URL: $url (was: $oldUrl)")
                prefs.edit().putString("SERVER_URL", url).apply()
            } else {
                Log.d(TAG, "Server URL unchanged: $url")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving server URL", e)
        }
    }
    
    private fun initNotificationService() {
        try {
            // Получаем данные пользователя из SharedPreferences
            val prefs = getSharedPreferences("PROVITA_SETTINGS", Context.MODE_PRIVATE)
            val isLoggedIn = prefs.getBoolean("IS_LOGGED_IN", false)
            
            if (isLoggedIn) {
                // Запускаем сервис в основном потоке, но без блокировки
                Handler(Looper.getMainLooper()).post {
                    val intent = Intent(this, NotificationWebSocketService::class.java)
                    startService(intent)
                    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                    Log.d(TAG, "NotificationWebSocketService started and bound")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize notification service", e)
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        
        // Отвязываемся от сервиса при завершении приложения
        try {
            unbindService(serviceConnection)
            Log.d(TAG, "Service unbound")
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding service", e)
        }
        
        // Отменяем регистрацию BroadcastReceiver
        try {
            if (fileNotificationReceiver != null) {
                unregisterReceiver(fileNotificationReceiver)
                Log.d(TAG, "FileNotificationReceiver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        
        // Завершаем выполнение задач в пуле потоков
        executor.shutdown()
    }

    // Инициализирует URL сервера
    private fun initServerUrl() {
        try {
            Log.d(TAG, "⏳ Инициализация URL сервера...")
            
            // Получаем локальный URL из ServerUrlUpdater
            val localUrl = com.practicum.playlist_maker.network.ServerUrlUpdater.getDuckDnsUrl()
            
            // Устанавливаем локальный URL в качестве URL сервера
            val prefs = getSharedPreferences("PROVITA_SETTINGS", Context.MODE_PRIVATE)
            prefs.edit().putString("SERVER_URL", localUrl).apply()
            Log.d(TAG, "⚠️ URL сервера установлен: $localUrl")
            
            // Проверяем доступность сервера через локальный URL
            executor.submit {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .build()
                    
                    val request = Request.Builder()
                        .url("$localUrl/health")
                        .get()
                        .build()
                    
                    Log.d(TAG, "⏳ Проверка доступности сервера: $localUrl/health")
                    
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        Log.d(TAG, "✅ Сервер доступен!")
                        
                        // Если сервер доступен, обновляем FCM токен
                        Handler(Looper.getMainLooper()).post {
                            val notificationService = NotificationService()
                            notificationService.updateFcmToken(applicationContext, "")
                        }
                    } else {
                        Log.e(TAG, "❌ Сервер недоступен! Проверьте работу сервера")
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка при проверке доступности сервера: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка при инициализации URL сервера: ${e.message}", e)
        }
    }
}
