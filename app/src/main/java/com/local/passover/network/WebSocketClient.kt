package com.local.passover.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient
){
    private var webSocket: WebSocket? = null
    private val _messages = MutableSharedFlow<ByteString>(replay = 1, extraBufferCapacity = 64)
    val messages = _messages.asSharedFlow()
    private val _connectionState = MutableSharedFlow<Boolean>(replay = 1)
    val  connectionState = _connectionState.asSharedFlow()

    private val listener = object : WebSocketListener(){
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionState.tryEmit(true)
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.tryEmit(false)
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connectionState.tryEmit(false)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            _messages.tryEmit(bytes)
        }
    }
    fun connect(url: String){
        if (webSocket != null) disconnect()
        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    fun send(data: ByteArray){
        val _data = ByteString.of(*data)
        webSocket?.send(_data)
    }
    fun disconnect(code: Int = 1000, reason: String = "Client disconnected"){
        webSocket?.close(code, reason)
        webSocket = null
        _connectionState.tryEmit(false)
    }
}
