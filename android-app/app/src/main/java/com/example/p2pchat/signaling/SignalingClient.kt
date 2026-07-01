package com.example.p2pchat.signaling

import okhttp3.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

@Singleton
class SignalingClient @Inject constructor(
    private val client: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private val _signalingMessages = MutableSharedFlow<JSONObject>()
    val signalingMessages: SharedFlow<JSONObject> = _signalingMessages

    fun connect(url: String, onConnect: (() -> Unit)? = null) {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onConnect?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _signalingMessages.tryEmit(JSONObject(text))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Handle failure
            }
        })
    }

    fun sendMessage(message: JSONObject) {
        webSocket?.send(message.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "App closing")
    }
}
