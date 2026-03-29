package com.local.passover.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.passover.MessageOuterClass
import com.local.passover.core.DiscoveredPeer
import com.local.passover.core.PairingCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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
    private val pairingCoordinator: PairingCoordinator,
) : ViewModel() {

    private val TAG = "PairingViewModel"
    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state = _state.asStateFlow()

    // Transient pairing state
    private var pendingPeerIdentity: MessageOuterClass.Identity? = null

    fun startDiscovery() {
        _state.value = PairingState.Discovering
        viewModelScope.launch {
            try {
                val discovered = pairingCoordinator.discoverPeers()
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
                val paired = pairingCoordinator.confirmPeer(identity)
                if (paired) {
                    pendingPeerIdentity = null
                    _state.value = PairingState.Paired
                } else {
                    _state.value = PairingState.Error("Server did not send GroupKey")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "SAS confirmation failed")
                _state.value = PairingState.Error("Pairing failed: ${e.message}")
            }
        }
    }

    fun rejectSas() {
        pendingPeerIdentity = null
        pairingCoordinator.rejectPairing()
        _state.value = PairingState.Idle
    }

    fun reset() {
        pendingPeerIdentity = null
        pairingCoordinator.reset()
        _state.value = PairingState.Idle
    }

    private suspend fun connectAndExchangeIdentity(peer: DiscoveredPeer): Pair<MessageOuterClass.Identity, String>? {
        return pairingCoordinator.connectAndExchangeIdentity(peer)
    }

    override fun onCleared() {
        pairingCoordinator.close()
        super.onCleared()
    }
}
