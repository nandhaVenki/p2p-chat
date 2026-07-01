package com.example.p2pchat.signaling

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@Singleton
class SignalingClient @Inject constructor(
    private val client: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private val _signalingMessages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val signalingMessages: SharedFlow<JSONObject> = _signalingMessages

    private val scope = CoroutineScope(Dispatchers.IO)
    private var retryJob: Job? = null
    private var currentUrl: String? = null
    private var connectCallback: (() -> Unit)? = null

    fun connect(url: String, onConnect: (() -> Unit)? = null) {
        currentUrl = url
        connectCallback = onConnect
        retryJob?.cancel()
        performConnect()
    }

    private fun performConnect() {
        val url = currentUrl ?: return
        Log.d("SignalingClient", "Connecting to WebSocket: $url")
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SignalingClient", "WebSocket connection opened successfully.")
                retryJob?.cancel()
                connectCallback?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _signalingMessages.tryEmit(JSONObject(text))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SignalingClient", "WebSocket connection failure: ${t.localizedMessage}. Retrying in 5 seconds...", t)
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("SignalingClient", "WebSocket closed: $reason. Reconnecting...")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(5000)
            performConnect()
        }
    }

    fun sendMessage(message: JSONObject) {
        webSocket?.let {
            val success = it.send(message.toString())
            if (!success) {
                Log.w("SignalingClient", "Failed to send message over WebSocket. Queue full or socket closed.")
            }
        } ?: Log.w("SignalingClient", "Cannot send message. WebSocket is null.")
    }

    fun disconnect() {
        retryJob?.cancel()
        webSocket?.close(1000, "App closing")
        webSocket = null
    }
}
