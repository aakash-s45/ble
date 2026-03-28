package com.local.passover.core

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrustedPeerStore @Inject constructor(
    private val ds: DataStore<Preferences>,
) {
    private val TAG = "TrustedPeerStore"

    data class TrustedPeer(
        val deviceId: String,
        val deviceName: String,
        val publicKeyAgreement: ByteArray,
        val publicKeySignature: ByteArray,
        val trustedAt: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TrustedPeer) return false
            return deviceId == other.deviceId
        }

        override fun hashCode(): Int = deviceId.hashCode()
    }

    private val gson = Gson()
    private val kTrustedPeers = stringPreferencesKey("trusted_peers")

    private data class PeerListWrapper(val peers: List<PeerJson> = emptyList())
    private data class PeerJson(
        val deviceId: String,
        val deviceName: String,
        val publicKeyAgreement: String,
        val publicKeySignature: String,
        val trustedAt: Long,
    )

    private fun TrustedPeer.toJson(): PeerJson = PeerJson(
        deviceId = deviceId,
        deviceName = deviceName,
        publicKeyAgreement = Base64.encodeToString(publicKeyAgreement, Base64.NO_WRAP),
        publicKeySignature = Base64.encodeToString(publicKeySignature, Base64.NO_WRAP),
        trustedAt = trustedAt,
    )

    private fun PeerJson.toPeer(): TrustedPeer = TrustedPeer(
        deviceId = deviceId,
        deviceName = deviceName,
        publicKeyAgreement = Base64.decode(publicKeyAgreement, Base64.NO_WRAP),
        publicKeySignature = Base64.decode(publicKeySignature, Base64.NO_WRAP),
        trustedAt = trustedAt,
    )

    private suspend fun readPeers(): List<TrustedPeer> {
        val prefs = ds.data.first()
        val json = prefs[kTrustedPeers] ?: return emptyList()
        return try {
            gson.fromJson(json, PeerListWrapper::class.java).peers.map { it.toPeer() }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse trusted peers")
            emptyList()
        }
    }

    private suspend fun writePeers(peers: List<TrustedPeer>) {
        val json = gson.toJson(PeerListWrapper(peers.map { it.toJson() }))
        ds.edit { prefs -> prefs[kTrustedPeers] = json }
    }

    val allPeersFlow: Flow<List<TrustedPeer>>
        get() = ds.data.map { prefs ->
            val json = prefs[kTrustedPeers] ?: return@map emptyList()
            try {
                gson.fromJson(json, PeerListWrapper::class.java).peers.map { it.toPeer() }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun addPeer(peer: TrustedPeer) {
        val current = readPeers().toMutableList()
        current.removeAll { it.deviceId == peer.deviceId }
        current.add(peer)
        writePeers(current)
        Timber.tag(TAG).d("Added trusted peer: ${peer.deviceName} (${peer.deviceId})")
    }

    suspend fun getPeerByDeviceId(deviceId: String): TrustedPeer? {
        return readPeers().find { it.deviceId == deviceId }
    }

    suspend fun getAllPeers(): List<TrustedPeer> = readPeers()

    suspend fun removePeer(deviceId: String) {
        val current = readPeers().toMutableList()
        current.removeAll { it.deviceId == deviceId }
        writePeers(current)
        Timber.tag(TAG).d("Removed trusted peer: $deviceId")
    }

    suspend fun isTrusted(deviceId: String): Boolean {
        return readPeers().any { it.deviceId == deviceId }
    }

    suspend fun clearAll() {
        writePeers(emptyList())
    }
}
