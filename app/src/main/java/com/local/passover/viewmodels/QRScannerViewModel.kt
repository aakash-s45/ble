package com.local.passover.viewmodels

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.passover.MessageOuterClass
import com.local.passover.core.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface QRScannerState{
    object Scanning: QRScannerState
    object Loading: QRScannerState
    data class Error(val message: String): QRScannerState
    object Success: QRScannerState
}

@HiltViewModel
class QRScannerViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
): ViewModel() {

    val TAG = "QRScannerViewmodel"
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
        _uiState.value = QRScannerState.Loading

        viewModelScope.launch {
            val qrData = parseQRValue(qrValue)

            if (qrData.deviceId == null || qrData.key == null){
                _uiState.value = QRScannerState.Error("Invalid QR code")
                return@launch
            }

            val success = connectionRepository.makeTestConnection(qrData.deviceId, qrData.key)

            if(success){
                connectionRepository.saveCredentials(qrData.deviceId, qrData.key)
            } else{
                Timber.tag(TAG).e("Failed to connect")
                _uiState.value = QRScannerState.Error("Failed to connect")
            }
        }
    }

    fun resetScanner(){
        _uiState.value = QRScannerState.Scanning
    }

    private fun parseQRValue(qrValue: String): MessageOuterClass.QRPayload{
        val rawBytes = Base64.decode(qrValue, Base64.DEFAULT)
        val message = MessageOuterClass.Message.parseFrom(rawBytes)
        return message.qrPayload
    }
}