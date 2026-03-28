package com.local.passover.core

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.local.passover.MessageOuterClass
import com.local.passover.network.DnsServiceManager
import com.local.passover.network.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

enum class ConnectionState {IDLE, DISCOVERING, CONNECTING, CONNECTED, FAILED}

@Singleton
class ConnectionRepository @Inject constructor(
    private val discoverer: DnsServiceManager,
    private val webSocketClient: WebSocketClient,
    private val keyStoreManager: KeystoreManager,
    private val trustedPeerStore: TrustedPeerStore,
    private val ds: DataStore<Preferences>,
    ) {
    val TAG = "ConnectionRepository"
    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState = _connectionState.asStateFlow()

    private val kDeviceId = stringPreferencesKey("deviceId")
    private var activeKey: SecretKey? = null

    private val _incomingMessages = MutableSharedFlow<MessageOuterClass.Message>(replay = 1)
    val incomingMessages = _incomingMessages.asSharedFlow()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val hasTrustedPeers: Flow<Boolean> = trustedPeerStore.allPeersFlow.map { it.isNotEmpty() }

    init {
        startMessageProcessing()
        observeWebSocketState()
    }

    // ── Device identity ───────────────────────────────────────────────────

    suspend fun getDeviceId(): String {
        val prefs = ds.data.first()
        val existing = prefs[kDeviceId]
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        ds.edit { it[kDeviceId] = newId }
        Timber.tag(TAG).d("Generated new device ID: $newId")
        return newId
    }

    fun getDeviceName(): String = Build.MODEL

    // ── WebSocket state observation ────────────────────────────────────────

    private fun observeWebSocketState() {
        scope.launch {
            webSocketClient.connectionState.collect { connected ->
                if (!connected && _connectionState.value == ConnectionState.CONNECTED) {
                    Timber.tag(TAG).d("WebSocket disconnected unexpectedly, transitioning to FAILED")
                    _connectionState.value = ConnectionState.FAILED
                    activeKey = null
                }
            }
        }
    }

    // ── Message processing ────────────────────────────────────────────────

    private fun startMessageProcessing(){
        scope.launch {
            webSocketClient.messages.collect{ byteString ->
                Timber.tag(TAG).d("Got message over websocket")
                try {
                    activeKey?.let { key ->
                        val decryptedMessage = keyStoreManager.decrypt(key, byteString.toByteArray())
                        val message = MessageOuterClass.Message.parseFrom(decryptedMessage)
                        _incomingMessages.emit(message)
                    }?: run {
                        // During pairing, messages may be unencrypted Identity blobs
                        try {
                            val message = MessageOuterClass.Message.parseFrom(byteString.toByteArray())
                            _incomingMessages.emit(message)
                        } catch (e: Exception) {
                            Timber.tag(TAG).w("No active key and couldn't parse raw message")
                        }
                    }
                } catch (e: Exception){
                        Timber.tag(TAG).e(e, "Failed to decrypt message")
                }
            }
        }
    }

    // ── Connection with trusted peers using GroupKey ───────────────────────

    suspend fun connectWithTrustedPeer(deviceId: String): Boolean {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            Timber.tag(TAG).d("Already connected/connecting. Ignoring request.")
            return true
        }

        return try {
            connectWithTrustedPeerInternal(deviceId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "connectWithTrustedPeer failed unexpectedly")
            _connectionState.value = ConnectionState.FAILED
            false
        }
    }

    private suspend fun connectWithTrustedPeerInternal(deviceId: String): Boolean {
        val peer = trustedPeerStore.getPeerByDeviceId(deviceId) ?: run {
            Timber.tag(TAG).e("No trusted peer found for deviceId: $deviceId")
            return false
        }

        val groupKey = try {
            keyStoreManager.getOrCreateGroupKey()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get group key")
            _connectionState.value = ConnectionState.FAILED
            return false
        }

        _connectionState.value = ConnectionState.DISCOVERING
        val serviceInfo = discoverer.findService(deviceId)
        if (serviceInfo == null) {
            _connectionState.value = ConnectionState.FAILED
            Timber.tag(TAG).e("Failed to find service for peer: ${peer.deviceName}")
            return false
        }

        _connectionState.value = ConnectionState.CONNECTING
        val inetAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            android.os.ext.SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7) {
            serviceInfo.hostAddresses.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            serviceInfo.host
        }
        val hostAddress = inetAddress?.hostAddress?.removePrefix("/")
        if (hostAddress.isNullOrEmpty()) {
            Timber.tag(TAG).e("No host address for peer: ${peer.deviceName}")
            _connectionState.value = ConnectionState.FAILED
            return false
        }
        val url = "ws://${hostAddress}:${serviceInfo.port}"

        activeKey = groupKey
        webSocketClient.connect(url)
        val connected = withTimeoutOrNull(10_000) {
            webSocketClient.connectionState.first { it }
        }

        if (connected != true) {
            _connectionState.value = ConnectionState.FAILED
            closeConnection()
            Timber.tag(TAG).e("Failed to connect to ${peer.deviceName}")
            return false
        }

        _connectionState.value = ConnectionState.CONNECTED
        Timber.tag(TAG).d("Connected to trusted peer: ${peer.deviceName}")
        return true
    }

    // ── Send / close ──────────────────────────────────────────────────────

    fun send(data: ByteArray): Boolean {
        val key = activeKey ?: run {
            Timber.tag(TAG).w("Cannot send: no active key (not connected)")
            return false
        }
        return try {
            val encryptedData = keyStoreManager.encrypt(key, data)
            webSocketClient.send(encryptedData)
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to encrypt/send data")
            false
        }
    }

    fun closeConnection(){
        webSocketClient.disconnect()
        _connectionState.value = ConnectionState.IDLE
        activeKey = null
    }
}
