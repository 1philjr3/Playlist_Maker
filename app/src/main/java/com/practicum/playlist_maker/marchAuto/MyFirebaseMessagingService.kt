package com.practicum.playlist_maker.marchAuto

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.e("FCM", "New token: $token")
        // Send the token to your server if needed
    }

//    override fun onMessageReceived(remoteMessage: RemoteMessage) {
//        super.onMessageReceived(remoteMessage)
//        Log.d("FCM", "Message received from: ${remoteMessage.from}")
//        remoteMessage.notification?.let {
//            Log.d("FCM", "Message Notification Body: ${it.body}")
//        }
//    }
}
