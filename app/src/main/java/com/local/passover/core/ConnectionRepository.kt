package com.local.passover.core

import android.os.Build
import android.os.ext.SdkExtensions
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
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionState {IDLE, DISCOVERING, CONNECTING, CONNECTED, FAILED}

@Singleton
class ConnectionRepository @Inject constructor(
    private val discoverer: DnsServiceManager,
    private val webSocketClient: WebSocketClient,
    private val keyStoreManager: KeystoreManager,
) {
    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState = _connectionState.asStateFlow()

    private var activeKey: SecretKey? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun makeTestConnection(deviceId: String, key: String): Boolean{
        if (_connectionState.value == ConnectionState.CONNECTED) return true
        _connectionState.value = ConnectionState.DISCOVERING
        val serviceInfo = discoverer.findService(deviceId)
        if(serviceInfo == null){
            _connectionState.value = ConnectionState.FAILED
            return false
        }

        _connectionState.value = ConnectionState.CONNECTING
        val hostAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7) {
            serviceInfo.hostAddresses.first()
        } else {
            serviceInfo.host
        }
        val url = "ws://${hostAddress}:${serviceInfo.port}"
        webSocketClient.connect(url)
        val connected = withTimeoutOrNull(10_000){
            webSocketClient.connectionState.first{it}
        }
        if (connected != true){
            _connectionState.value = ConnectionState.FAILED
            return false
        }

        val identityMessage = MessageOuterClass.Identity.newBuilder().setDeviceId(deviceId).build()
        val secretKey = keyStoreManager.getKeyFromString(key)
        val encryptedMessage = keyStoreManager.encrypt(secretKey, identityMessage.toByteArray())
        webSocketClient.send(encryptedMessage)

        val response = withTimeoutOrNull(5_000){
            webSocketClient.messages.first()
        }

        if (response == null){
            _connectionState.value = ConnectionState.FAILED
            webSocketClient.disconnect()
            return false
        }

        return try {
            val decryptedResponse = keyStoreManager.decrypt(secretKey, response.toByteArray())
            val identityResponse = MessageOuterClass.Identity.parseFrom(decryptedResponse)
//            TODO: save credentials
//            if(identityResponse.deviceId == deviceId) saveCredentials(deviceId, key)
//            else throw Exception("Invalid response")
            return  true
        }catch (e: Exception){
            _connectionState.value = ConnectionState.FAILED
            webSocketClient.disconnect()
            false
        }
    }

    fun send(data: ByteArray){
        if(_connectionState.value != ConnectionState.CONNECTED || activeKey == null) return

        val encryptedData = keyStoreManager.encrypt(activeKey!!, data)
        webSocketClient.send(encryptedData)
    }
}
