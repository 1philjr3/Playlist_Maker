package com.practicum.playlist_maker.mediaLibrary.ui.fragment

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.pdfreader.ui.activity.reader.PdfReaderActivity

/**
 * Получатель уведомлений о новых файлах
 */
class FileNotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "FileNotificationReceiver"
        private const val CHANNEL_ID = "file_notifications"
        private const val NOTIFICATION_ID_BASE = 1000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast action: $action")
        
        if (action == "com.practicum.playlist_maker.FILE_NOTIFICATION") {
            try {
                val fileId = intent.getStringExtra("fileId") ?: return
                val fileName = intent.getStringExtra("fileName") ?: "Новый файл"
                val description = intent.getStringExtra("description") ?: ""
                val userName = intent.getStringExtra("userName") ?: "Пользователь"
                val fileUrl = intent.getStringExtra("fileUrl") ?: return
                
                Log.d(TAG, "Showing notification for file: $fileName, from: $userName, description: $description")
                showNotification(context, fileId, fileName, description, userName, fileUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification", e)
            }
        } else {
            Log.w(TAG, "Unknown action: $action")
        }
    }

    private fun showNotification(
        context: Context,
        fileId: String,
        fileName: String,
        description: String,
        userName: String,
        fileUrl: String
    ) {
        val notificationManager = NotificationManagerCompat.from(context)

        // Создаем канал для уведомлений на Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channel")
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Уведомления о файлах",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                this.description = "Уведомления о новых файлах в системе"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Создаем интент для открытия файла
        val openIntent = Intent(context, PdfReaderActivity::class.java).apply {
            putExtra(PdfReaderActivity.EXTRA_PDF_FILE_PATH, fileUrl)
            putExtra(PdfReaderActivity.EXTRA_PDF_FILE_NAME, fileName)
            putExtra(PdfReaderActivity.EXTRA_PDF_ID, fileId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            fileId.hashCode(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Формируем текст уведомления
        val title = "Новый файл: $fileName"
        val text = if (description.isNotEmpty()) {
            "$userName: $description"
        } else {
            "Пользователь $userName добавил новый файл"
        }

        // Получаем цвет для иконки согласно текущей теме
        val iconColor = ContextCompat.getColor(context, R.color.colorPrimary)

        // Создаем уведомление
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(iconColor)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        // Проверяем разрешения и показываем уведомление
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No notification permission!")
                return
            }
        }
        
        try {
            val notificationId = NOTIFICATION_ID_BASE + fileId.hashCode()
            Log.d(TAG, "Showing notification with ID: $notificationId")
            notificationManager.notify(notificationId, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification", e)
        }
    }
} 