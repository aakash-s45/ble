package com.local.passover.viewmodels

import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.passover.MessageOuterClass
import com.local.passover.core.ConnectionRepository
import com.local.passover.core.ConnectionState
import com.local.passover.core.KeystoreManager
import com.local.passover.core.TrustedPeerStore
import com.local.passover.network.DnsServiceManager
import com.local.passover.network.WebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

data class DiscoveredPeer(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val deviceId: String?,
)

sealed interface PairingState {
    object Idle : PairingState
    object Discovering : PairingState
    data class PeersFound(val peers: List<DiscoveredPeer>) : PairingState
    data class Connecting(val peerName: String) : PairingState
    data class SasConfirmation(val sasCode: String, val peerName: String, val peerDeviceId: String) : PairingState
    object Paired : PairingState
    data class Error(val message: String) : PairingState
}

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val discoverer: DnsServiceManager,
    private val webSocketClient: WebSocketClient,
    private val keystoreManager: KeystoreManager,
    private val trustedPeerStore: TrustedPeerStore,
    private val connectionRepository: ConnectionRepository,
) : ViewModel() {

    private val TAG = "PairingViewModel"
    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state = _state.asStateFlow()

    // Transient pairing state
    private var pendingPeerIdentity: MessageOuterClass.Identity? = null
    private var pairingSessionKey: javax.crypto.SecretKey? = null

    fun startDiscovery() {
        _state.value = PairingState.Discovering
        viewModelScope.launch {
            try {
                val discovered = discoverAllPeers()
                if (discovered.isEmpty()) {
                    _state.value = PairingState.Error("No devices found on the network")
                } else {
                    _state.value = PairingState.PeersFound(discovered)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Discovery failed")
                _state.value = PairingState.Error("Discovery failed: ${e.message}")
            }
        }
    }

    fun selectPeer(peer: DiscoveredPeer) {
        _state.value = PairingState.Connecting(peer.serviceName)
        viewModelScope.launch {
            try {
                val result = connectAndExchangeIdentity(peer)
                if (result != null) {
                    val (identity, sasCode) = result
                    pendingPeerIdentity = identity
                    _state.value = PairingState.SasConfirmation(
                        sasCode = sasCode,
                        peerName = identity.deviceName.ifBlank { peer.serviceName },
                        peerDeviceId = identity.deviceId,
                    )
                } else {
                    _state.value = PairingState.Error("Failed to connect to peer")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Connection failed")
                _state.value = PairingState.Error("Connection failed: ${e.message}")
            }
        }
    }

    fun confirmSas() {
        val identity = pendingPeerIdentity ?: return
        viewModelScope.launch {
            try {
                val sharedSecret = keystoreManager.computeSharedSecret(identity.publicKeyAgreement.toByteArray())
                val sessionKey = keystoreManager.deriveSessionKey(sharedSecret)

                // Send our handshake as plain protobuf (only encryptedGroupKey field is ciphertext)
                val handshake = MessageOuterClass.HandshakeMessage.newBuilder()
                    .setSasConfirmed(true)
                    .build()

                val message = MessageOuterClass.Message.newBuilder()
                    .setTimestampMs(System.currentTimeMillis())
                    .setHandshake(handshake)
                    .build()

                webSocketClient.send(message.toByteArray())

                // Wait for Mac's handshake (plain protobuf, already parsed by ConnectionRepository)
                val peerHandshakeMsg = withTimeoutOrNull(15_000) {
                    connectionRepository.incomingMessages.first { it.hasHandshake() }
                }

                if (peerHandshakeMsg != null && peerHandshakeMsg.handshake.encryptedGroupKey.size() > 0) {
                    val groupKeyBytes = keystoreManager.decrypt(
                        sessionKey,
                        peerHandshakeMsg.handshake.encryptedGroupKey.toByteArray()
                    )
                    val groupKey = javax.crypto.spec.SecretKeySpec(groupKeyBytes, "AES")
                    keystoreManager.saveGroupKey(groupKey)
                    Timber.tag(TAG).d("Received and saved GroupKey from server")
                } else {
                    Timber.tag(TAG).e("Server handshake missing GroupKey or timed out")
                    _state.value = PairingState.Error("Server did not send GroupKey")
                    return@launch
                }

                // Save trusted peer
                trustedPeerStore.addPeer(
                    TrustedPeerStore.TrustedPeer(
                        deviceId = identity.deviceId,
                        deviceName = identity.deviceName,
                        publicKeyAgreement = identity.publicKeyAgreement.toByteArray(),
                        publicKeySignature = identity.publicKeySignature.toByteArray(),
                        trustedAt = System.currentTimeMillis(),
                    )
                )

                _state.value = PairingState.Paired
                Timber.tag(TAG).d("Pairing completed with ${identity.deviceName}")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "SAS confirmation failed")
                _state.value = PairingState.Error("Pairing failed: ${e.message}")
            }
        }
    }

    fun rejectSas() {
        pendingPeerIdentity = null
        pairingSessionKey = null
        webSocketClient.disconnect()
        _state.value = PairingState.Idle
    }

    fun reset() {
        pendingPeerIdentity = null
        pairingSessionKey = null
        _state.value = PairingState.Idle
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private suspend fun discoverAllPeers(): List<DiscoveredPeer> {
        val trustedIds = trustedPeerStore.getAllPeers().map { it.deviceId }.toSet()
        val services = discoverer.discoverServices()

        return services
            .mapNotNull { service ->
                val deviceIdBytes = service.attributes["deviceId"]
                val deviceId = deviceIdBytes?.let { String(it, Charsets.UTF_8) }

                // Filter out already-trusted peers
                if (deviceId != null && trustedIds.contains(deviceId)) return@mapNotNull null

                val hostAddress = getHostAddress(service) ?: return@mapNotNull null

                DiscoveredPeer(
                    serviceName = service.serviceName,
                    hostAddress = hostAddress,
                    port = service.port,
                    deviceId = deviceId,
                )
            }
    }

    private suspend fun connectAndExchangeIdentity(peer: DiscoveredPeer): Pair<MessageOuterClass.Identity, String>? {
        val url = "ws://${peer.hostAddress}:${peer.port}"
        webSocketClient.connect(url)

        val connected = withTimeoutOrNull(10_000) {
            webSocketClient.connectionState.first { it }
        }

        if (connected != true) {
            Timber.tag(TAG).e("WebSocket connection failed to ${peer.serviceName}")
            webSocketClient.disconnect()
            return null
        }

        val ourIdentity = MessageOuterClass.Identity.newBuilder()
            .setDeviceId(connectionRepository.getDeviceId())
            .setDeviceName(Build.MODEL)
            .setPublicKeyAgreement(com.google.protobuf.ByteString.copyFrom(keystoreManager.getPublicKeyAgreement()))
            .setPublicKeySignature(com.google.protobuf.ByteString.copyFrom(keystoreManager.getPublicKeySignature()))
            .build()

        val identityMsg = MessageOuterClass.Message.newBuilder()
            .setTimestampMs(System.currentTimeMillis())
            .setIdentity(ourIdentity)
            .build()

        webSocketClient.send(identityMsg.toByteArray())


        val peerIdentityMsg = withTimeoutOrNull(15_000) {
            connectionRepository.incomingMessages.first { it.hasIdentity() }
        }

        if (peerIdentityMsg == null) {
            Timber.tag(TAG).e("Did not receive peer identity")
            webSocketClient.disconnect()
            return null
        }

        val peerIdentity = peerIdentityMsg.identity

        // Compute SAS code
        val sasCode = keystoreManager.computeSasCode(
            keystoreManager.getPublicKeyAgreement(),
            peerIdentity.publicKeyAgreement.toByteArray()
        )

        return Pair(peerIdentity, sasCode)
    }

    private fun getHostAddress(service: NsdServiceInfo): String? {
        val inetAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7
        ) {
            service.hostAddresses.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            service.host
        }
        return inetAddress?.hostAddress?.removePrefix("/")
    }
}
