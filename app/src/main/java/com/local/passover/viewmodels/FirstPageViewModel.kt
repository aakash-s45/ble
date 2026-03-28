package com.local.passover.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.passover.core.ConnectionRepository
import com.local.passover.core.KeystoreManager
import com.local.passover.core.TrustedPeerStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FirstPageViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val trustedPeerStore: TrustedPeerStore,
    private val keystoreManager: KeystoreManager,
): ViewModel() {
    val TAG = "FirstPageViewModel"
    val connectionState = connectionRepository.connectionState
    val trustedPeers: Flow<List<TrustedPeerStore.TrustedPeer>> = trustedPeerStore.allPeersFlow

    fun removeTrustedPeer(deviceId: String) {
        viewModelScope.launch {
            trustedPeerStore.removePeer(deviceId)
            // TODO: trigger GroupKey rotation when implemented in Phase 2
        }
    }
}