package com.local.passover.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface QRScannerState{
    object Scanning: QRScannerState
    object Loading: QRScannerState
    data class Error(val message: String): QRScannerState
    object Success: QRScannerState

}

@HiltViewModel
class QRScannerViewModel @Inject constructor(
    private val serviceRepository: ServiceRepository,
    private val connectionRepository: ConnectionRepository,
): ViewModel() {
    private val _uiState = MutableStateFlow<QRScannerState>(QRScannerState.Scanning)
    val uiState = _uiState.asStateFlow()

    fun processQRCode(qrValue: String){
        /*
        * TODO:
        *  - decode ->
        *  - check if we can make message out of it->
        *  - extract key and id ->
        *  - verify ->
        *  - get response ->
        *  - encrypt and store em
        * */
        if  (_uiState.value != QRScannerState.Scanning)return

        viewModelScope.launch {
            val (deviceId, key) = parseQRValue(qrValue)
            if (deviceId == null || key == null){
                _uiState.value = QRScannerState.Error("Invalid QR code")
                return@launch
            }

            val success = connectionRepository.makeTestConnection(deviceId, key)

            if(success){
                serviceRepository.saveId(deviceId)
//                TODO: encrypt the key first
                serviceRepository.saveKey(key)
                _uiState.value = QRScannerState.Success
            } else{
                _uiState.value = QRScannerState.Error("Failed to connect")
            }
        }
    }

    fun resetScanner(){
        _uiState.value = QRScannerState.Scanning
    }

    private fun parseQRValue(qrValue: String): Pair<String?, String?>{
//        TODO: add parding logic
        val parts = qrValue.split(",")
        if (parts.size != 2) return null to null
        return  parts[0] to parts[1]
    }
}