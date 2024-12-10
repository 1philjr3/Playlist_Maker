//package com.practicum.playlist_maker.marchAuto
//
//import android.app.Activity
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.content.Context
//import android.content.Intent
//import android.os.Build
//import android.os.Bundle
//import android.util.Log
//import android.widget.TextView
//import android.widget.Toast
//import androidx.activity.viewModels
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.app.NotificationCompat
//import androidx.core.app.NotificationManagerCompat
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.database.FirebaseDatabase
//import com.google.firebase.database.DatabaseReference
//import com.google.firebase.messaging.FirebaseMessaging
//import com.practicum.playlist_maker.R
//import com.practicum.playlist_maker.databinding.ActivityProfileBinding
//import com.practicum.playlist_maker.marchAuto.model.AuthViewModel
//import com.practicum.playlist_maker.marchAuto.model.ModelUser
//
//class ActivityProfile : AppCompatActivity() {
//
//    private val authViewModel: AuthViewModel by viewModels()
//    private lateinit var binding: ActivityProfileBinding
//    private lateinit var database: DatabaseReference
//    private var userName: String = "Unknown User"
//    private val REQUEST_CODE = 1001
//    private var notificationStatus: TextView? = null
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityProfileBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        notificationStatus = binding.notificationsStatus
//
//        // Инициализация Firebase Database
//        database = FirebaseDatabase.getInstance().reference
//
//        // Проверка и запрос разрешений на уведомления
//        notificationStatus?.text = if (isNotificationPermissionAllowed(this)) {
//            getString(R.string.notifications_enabled)
//        } else {
//            requestNotificationPermission(this)
//            getString(R.string.notifications_disabled)
//        }
//
//        // Получение имени пользователя
//        authViewModel.userData.observe(this) { modelUser ->
//            userName = modelUser?.name ?: "Unknown User"
//        }
//
//        // Подписка на топик "global" для получения push-уведомлений
//        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
//            if (task.isSuccessful) {
//                val token = task.result
//                Log.d("FCM Token", "Device token: $token")
//            } else {
//                Log.e("FCM Token", "Failed to get FCM token", task.exception)
//            }
//        }
//
//        FirebaseMessaging.getInstance().subscribeToTopic("global")
//            .addOnCompleteListener { task ->
//                if (task.isSuccessful) {
//                    Log.d("FCM", "Successfully subscribed to topic: global")
//                } else {
//                    Log.e("FCM", "Failed to subscribe to topic", task.exception)
//                }
//            }
//
//        observeViewModel()
//
//        // Обработчик кнопки "Logout"
//        binding.logoutButton.setOnClickListener {
//            authViewModel.logout()
//            startActivity(Intent(this, ActivityLogin::class.java))
//        }
//
//        // Обработчик кнопки "Send Action"
//        binding.sendActionButton.setOnClickListener {
//            sendUserAction()
//            showLocalNotification("$userName нажал кнопку", "Действие выполнено!")
//        }
//    }
//
//    private fun isNotificationPermissionAllowed(context: Context): Boolean {
//        val notificationManagerCompat = NotificationManagerCompat.from(context)
//        return notificationManagerCompat.areNotificationsEnabled()
//    }
//
//    private fun requestNotificationPermission(activity: Activity) {
//        val notificationManagerCompat = NotificationManagerCompat.from(activity)
//        if (!notificationManagerCompat.areNotificationsEnabled()) {
//            val intent = Intent()
//            intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                intent.putExtra("android.provider.extra.APP_PACKAGE", activity.packageName)
//            } else {
//                intent.putExtra("app_package", activity.packageName)
//                intent.putExtra("app_uid", activity.applicationInfo.uid)
//            }
//            activity.startActivityForResult(intent, REQUEST_CODE)
//        }
//    }
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == REQUEST_CODE) {
//            notificationStatus?.text = if (isNotificationPermissionAllowed(this)) {
//                getString(R.string.notifications_enabled)
//            } else {
//                getString(R.string.notifications_disabled)
//            }
//        }
//    }
//
//    private fun observeViewModel() {
//        authViewModel.firebaseUser.observe(this) { firebaseUser ->
//            if (firebaseUser == null) {
//                startActivity(Intent(this, ActivityLogin::class.java))
//                finish()
//            } else {
//                authViewModel.getData(firebaseUser.uid)
//            }
//        }
//        authViewModel.userData.observe(this) { modelUser ->
//            modelUser?.let {
//                showUserData(it)
//            }
//        }
//        authViewModel.error.observe(this) { errorMessage ->
//            errorMessage?.let {
//                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
//                Log.e("ActivityProfile", "Error: $it")
//            }
//        }
//    }
//
//    private fun showUserData(model: ModelUser) {
//        binding.profileName.text = model.name
//        binding.profileEmail.text = model.email
//        binding.profileInstitute.text = model.institute
//    }
//
//    // Отправка действия в Firebase Realtime Database
//    private fun sendUserAction() {
//        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown_user"
//        val action = mapOf(
//            "action" to "button_pressed",
//            "timestamp" to System.currentTimeMillis(),
//            "userName" to userName
//        )
//
//        database.child("user_actions").push().setValue(action)
//            .addOnSuccessListener {
//                Log.d("ActivityProfile", "Action sent successfully!")
//                Toast.makeText(this, "Action sent successfully!", Toast.LENGTH_SHORT).show()
//            }
//            .addOnFailureListener { e ->
//                Log.e("ActivityProfile", "Failed to send action: ${e.message}")
//                Toast.makeText(this, "Failed to send action: ${e.message}", Toast.LENGTH_SHORT).show()
//            }
//    }
//
//    private fun showLocalNotification(title: String, body: String) {
//        val channelId = "button_action_channel"
//        val notificationManager =
//            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                channelId,
//                "Button Action Notifications",
//                NotificationManager.IMPORTANCE_HIGH
//            )
//            notificationManager.createNotificationChannel(channel)
//        }
//
//        val notification = NotificationCompat.Builder(this, channelId)
//            .setContentTitle(title)
//            .setContentText(body)
//            .setSmallIcon(R.mipmap.ic_launcher)
//            .setAutoCancel(true)
//            .build()
//
//        notificationManager.notify(1, notification)
//    }
//}
