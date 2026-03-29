package com.local.passover.core

import android.os.Build
import android.os.ext.SdkExtensions
import com.local.passover.MessageOuterClass
import com.local.passover.network.DnsServiceManager
import com.local.passover.network.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionState { IDLE, DISCOVERING, CONNECTING, CONNECTED, FAILED }

/**
 * Maintains connection to a trusted peer: discovery, WebSocket, session key, and reconnect backoff.
 */
@Singleton
class SyncOrchestrator @Inject constructor(
    private val discoverer: DnsServiceManager,
    private val webSocketClient: WebSocketClient,
    crypto: CryptoEngine,
    private val deviceIdentityStore: DeviceIdentityStore,
    private val trustedPeerStore: TrustedPeerStore,
) {
    val TAG = "SyncOrchestrator"

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState = _connectionState.asStateFlow()

    private var activeGroupKey: SecretKey? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectionJob: Job? = null
    private var paused = false
    private val observersStarted = AtomicBoolean(false)
    private val disconnectRequested = AtomicBoolean(false)
    private val messageCodec = MessageCodec(crypto, webSocketClient)

    val hasTrustedPeers: Flow<Boolean> = trustedPeerStore.allPeersFlow.map { it.isNotEmpty() }

    val clipboardEvents: Flow<MessageOuterClass.ClipboardMessage> =
        messageCodec.incomingMessages.mapNotNull { msg ->
            if (msg.hasClipboard()) msg.clipboard else null
        }

    init {
        observeWebSocketState()
    }

    /** Start watching trusted peers and connection failures. Call once from [android.app.Service.onCreate]. */
    fun start() {
        if (observersStarted.compareAndSet(false, true)) {
            scope.launch {
                hasTrustedPeers.collect { hasPeers ->
                    if (paused) return@collect
                    if (hasPeers) {
                        Timber.tag(TAG).d("Trusted peer found, connecting")
                        try {
                            ensureConnectedIfNeeded()
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Connection attempt failed")
                        }
                    } else {
                        Timber.tag(TAG).d("No trusted peers found, waiting for pairing")
                        closeConnection()
                    }
                }
            }
            scope.launch {
                connectionState.collect { state ->
                    if (paused) return@collect
                    when (state) {
                        ConnectionState.FAILED -> {
                            Timber.tag(TAG).d("Connection failed, scheduling reconnect")
                            scheduleReconnection()
                        }
                        ConnectionState.CONNECTED, ConnectionState.IDLE -> {
                            reconnectionJob?.cancel()
                        }
                        else -> { /* DISCOVERING / CONNECTING */ }
                    }
                }
            }
        }

        paused = false
        scope.launch {
            ensureConnectedIfNeeded()
        }
    }

    fun pause() {
        paused = true
        reconnectionJob?.cancel()
        closeConnection()
    }

    fun resume() {
        paused = false
        scope.launch {
            try {
                ensureConnectedIfNeeded(forceReconnect = true)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Resume connection failed")
            }
        }
    }

    private fun observeWebSocketState() {
        scope.launch {
            webSocketClient.connectionState.collect { connected ->
                if (connected) {
                    disconnectRequested.set(false)
                    return@collect
                }

                val intentionalDisconnect = disconnectRequested.getAndSet(false)
                if (!intentionalDisconnect && _connectionState.value == ConnectionState.CONNECTED) {
                    Timber.tag(TAG).d("WebSocket disconnected unexpectedly, transitioning to FAILED")
                    clearActiveSession()
                    _connectionState.value = ConnectionState.FAILED
                }
            }
        }
    }

    suspend fun connectWithTrustedPeer(deviceId: String): Boolean {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.DISCOVERING
        ) {
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
            deviceIdentityStore.getOrCreateGroupKey()
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
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7
        ) {
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

        activeGroupKey = groupKey
        messageCodec.clearBufferedMessages()
        messageCodec.setSessionKey(groupKey)
        webSocketClient.connect(url)
        val connected = withTimeoutOrNull(10_000) {
            webSocketClient.connectionState.first { it }
        }

        if (connected != true) {
            disconnectTransport(intentional = true)
            _connectionState.value = ConnectionState.FAILED
            Timber.tag(TAG).e("Failed to connect to ${peer.deviceName}")
            return false
        }

        _connectionState.value = ConnectionState.CONNECTED
        Timber.tag(TAG).d("Connected to trusted peer: ${peer.deviceName}")
        return true
    }

    fun sendMessage(message: MessageOuterClass.Message): Boolean {
        val key = activeGroupKey ?: run {
            Timber.tag(TAG).w("Cannot send: no active key (not connected)")
            return false
        }
        return try {
            messageCodec.sendMessage(message, key)
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to encrypt/send message")
            false
        }
    }

    fun closeConnection() {
        reconnectionJob?.cancel()
        disconnectTransport(intentional = true)
        _connectionState.value = ConnectionState.IDLE
    }

    private fun scheduleReconnection() {
        if (paused) return
        if (reconnectionJob?.isActive == true) return
        reconnectionJob = scope.launch {
            Timber.tag(TAG).d("Scheduling reconnect in 5s")
            delay(5000)
            try {
                if (!paused && trustedPeerStore.getAllPeers().isNotEmpty()) {
                    connectToFirstTrustedPeer()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Reconnection attempt failed")
            }
        }
    }

    suspend fun connectToFirstTrustedPeer() {
        val peer = trustedPeerStore.getAllPeers().firstOrNull()
        if (peer != null) {
            connectWithTrustedPeer(peer.deviceId)
        } else {
            Timber.tag(TAG).d("No trusted peers available to connect")
        }
    }

    private suspend fun ensureConnectedIfNeeded(forceReconnect: Boolean = false) {
        if (paused) return
        val hasPeers = trustedPeerStore.getAllPeers().isNotEmpty()
        if (!hasPeers) {
            closeConnection()
            return
        }

        if (!forceReconnect && reconnectionJob?.isActive == true) return
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.DISCOVERING
        ) {
            return
        }

        connectToFirstTrustedPeer()
    }

    private fun disconnectTransport(intentional: Boolean) {
        disconnectRequested.set(intentional)
        webSocketClient.disconnect()
        clearActiveSession()
    }

    private fun clearActiveSession() {
        activeGroupKey = null
        messageCodec.setSessionKey(null)
        messageCodec.clearBufferedMessages()
    }
}
