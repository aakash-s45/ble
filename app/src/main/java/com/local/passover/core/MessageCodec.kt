package com.local.passover.core

import com.local.passover.MessageOuterClass
import com.local.passover.network.WebSocketClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okio.ByteString
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.SecretKey

/**
 * Translates raw WebSocket bytes to [MessageOuterClass.Message] and back (optional AES-GCM).
 */
class MessageCodec(
    private val crypto: CryptoEngine,
    private val webSocketClient: WebSocketClient,
) {
    private val TAG = "MessageCodec"

    private val sessionKeyRef = AtomicReference<SecretKey?>(null)

    private val _incomingMessages = MutableSharedFlow<MessageOuterClass.Message>(replay = 1, extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            webSocketClient.messages.collect { byteString ->
                decodeIncoming(byteString)
            }
        }
    }

    fun setSessionKey(key: SecretKey?) {
        sessionKeyRef.set(key)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearBufferedMessages() {
        _incomingMessages.resetReplayCache()
    }

    fun close() {
        clearBufferedMessages()
        scope.cancel()
    }

    fun sendMessage(message: MessageOuterClass.Message, key: SecretKey?) {
        if (key == null) {
            webSocketClient.send(message.toByteArray())
        } else {
            val encrypted = crypto.encrypt(key, message.toByteArray())
            webSocketClient.send(encrypted)
        }
    }

    private suspend fun decodeIncoming(byteString: ByteString) {
        Timber.tag(TAG).d("Got message over websocket")
        val key = sessionKeyRef.get()
        try {
            if (key != null) {
                val decrypted = crypto.decrypt(key, byteString.toByteArray())
                val message = MessageOuterClass.Message.parseFrom(decrypted)
                _incomingMessages.emit(message)
            } else {
                try {
                    val message = MessageOuterClass.Message.parseFrom(byteString.toByteArray())
                    _incomingMessages.emit(message)
                } catch (e: Exception) {
                    Timber.tag(TAG).w("No active key and couldn't parse raw message")
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to decrypt message")
        }
    }
}
