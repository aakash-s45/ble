package com.local.passover.core

import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import com.google.protobuf.ByteString
import com.local.passover.MessageOuterClass
import com.local.passover.network.DnsServiceManager
import com.local.passover.network.WebSocketClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

data class DiscoveredPeer(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val deviceId: String?,
)

/**
 * Owns the pairing flow so UI state holders do not manipulate sockets directly.
 */
class PairingCoordinator @Inject constructor(
    private val discoverer: DnsServiceManager,
    private val webSocketClient: WebSocketClient,
    private val crypto: CryptoEngine,
    private val keyMaterialStore: KeyMaterialStore,
    private val deviceIdentityStore: DeviceIdentityStore,
    private val trustedPeerStore: TrustedPeerStore,
) {
    private val TAG = "PairingCoordinator"
    private val messageCodec = MessageCodec(crypto, webSocketClient)

    suspend fun discoverPeers(): List<DiscoveredPeer> {
        reset()
        val trustedIds = trustedPeerStore.getAllPeers().map { it.deviceId }.toSet()
        val services = discoverer.discoverServices()

        return services.mapNotNull { service ->
            val deviceIdBytes = service.attributes["deviceId"]
            val deviceId = deviceIdBytes?.let { String(it, Charsets.UTF_8) }
            if (deviceId != null && trustedIds.contains(deviceId)) {
                return@mapNotNull null
            }

            val hostAddress = getHostAddress(service) ?: return@mapNotNull null
            DiscoveredPeer(
                serviceName = service.serviceName,
                hostAddress = hostAddress,
                port = service.port,
                deviceId = deviceId,
            )
        }
    }

    suspend fun connectAndExchangeIdentity(peer: DiscoveredPeer): Pair<MessageOuterClass.Identity, String>? {
        reset()

        val url = "ws://${peer.hostAddress}:${peer.port}"
        webSocketClient.connect(url)

        val connected = withTimeoutOrNull(10_000) {
            webSocketClient.connectionState.first { it }
        }

        if (connected != true) {
            Timber.tag(TAG).e("WebSocket connection failed to ${peer.serviceName}")
            reset()
            return null
        }

        val localPublicKey = keyMaterialStore.getPublicKeyAgreement()
        val ourIdentity = MessageOuterClass.Identity.newBuilder()
            .setDeviceId(deviceIdentityStore.getDeviceId())
            .setDeviceName(Build.MODEL)
            .setPublicKeyAgreement(ByteString.copyFrom(localPublicKey))
            .setPublicKeySignature(ByteString.copyFrom(deviceIdentityStore.getPublicKeySignature()))
            .build()

        val identityMsg = MessageOuterClass.Message.newBuilder()
            .setTimestampMs(System.currentTimeMillis())
            .setIdentity(ourIdentity)
            .build()

        messageCodec.sendMessage(identityMsg, null)

        val peerIdentityMsg = withTimeoutOrNull(15_000) {
            messageCodec.incomingMessages.first { it.hasIdentity() }
        }

        if (peerIdentityMsg == null) {
            Timber.tag(TAG).e("Did not receive peer identity")
            reset()
            return null
        }

        val peerIdentity = peerIdentityMsg.identity
        val sasCode = crypto.computeSasCode(
            localPublicKey,
            peerIdentity.publicKeyAgreement.toByteArray()
        )

        return peerIdentity to sasCode
    }

    suspend fun confirmPeer(identity: MessageOuterClass.Identity): Boolean {
        return try {
            val sharedSecret = crypto.computeSharedSecret(
                keyMaterialStore.getKeyAgreementPrivateKey(),
                identity.publicKeyAgreement.toByteArray()
            )
            val sessionKey = crypto.deriveSessionKey(sharedSecret)

            val handshake = MessageOuterClass.HandshakeMessage.newBuilder()
                .setSasConfirmed(true)
                .build()

            val message = MessageOuterClass.Message.newBuilder()
                .setTimestampMs(System.currentTimeMillis())
                .setHandshake(handshake)
                .build()

            messageCodec.sendMessage(message, null)

            val peerHandshakeMsg = withTimeoutOrNull(15_000) {
                messageCodec.incomingMessages.first { it.hasHandshake() }
            }

            if (peerHandshakeMsg == null || peerHandshakeMsg.handshake.encryptedGroupKey.isEmpty) {
                Timber.tag(TAG).e("Server handshake missing GroupKey or timed out")
                false
            } else {
                val groupKeyBytes = crypto.decrypt(
                    sessionKey,
                    peerHandshakeMsg.handshake.encryptedGroupKey.toByteArray()
                )
                val groupKey = SecretKeySpec(groupKeyBytes, "AES")
                deviceIdentityStore.saveGroupKey(groupKey)

                trustedPeerStore.addPeer(
                    TrustedPeerStore.TrustedPeer(
                        deviceId = identity.deviceId,
                        deviceName = identity.deviceName,
                        publicKeyAgreement = identity.publicKeyAgreement.toByteArray(),
                        publicKeySignature = identity.publicKeySignature.toByteArray(),
                        trustedAt = System.currentTimeMillis(),
                    )
                )

                Timber.tag(TAG).d("Pairing completed with ${identity.deviceName}")
                true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "SAS confirmation failed")
            false
        } finally {
            reset()
        }
    }

    fun rejectPairing() {
        reset()
    }

    fun reset() {
        messageCodec.setSessionKey(null)
        messageCodec.clearBufferedMessages()
        webSocketClient.disconnect()
    }

    fun close() {
        reset()
        messageCodec.close()
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
