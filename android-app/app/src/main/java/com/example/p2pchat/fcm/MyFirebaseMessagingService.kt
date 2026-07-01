package com.example.p2pchat.fcm

import android.content.Context
import android.util.Log
import com.example.p2pchat.data.repository.ChatRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var chatRepository: ChatRepository

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token generated: $token")
        // Store in SharedPreferences so ChatRepository can read and upload it on connection
        val sharedPrefs = getSharedPreferences("p2p_chat_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_token", token).apply()
        
        // Also upload dynamically if repository is initialized
        chatRepository.updateFcmToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "Message received from: ${remoteMessage.from}")
        
        // Extract signaling payload data
        if (remoteMessage.data.isNotEmpty()) {
            try {
                val dataJson = JSONObject(remoteMessage.data as Map<*, *>)
                chatRepository.handleIncomingFcmSignaling(dataJson)
            } catch (e: Exception) {
                Log.e("FCM", "Error parsing incoming FCM data", e)
            }
        }
    }
}
