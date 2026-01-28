package com.local.passover.core

import android.companion.DeviceId
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.local.passover.MessageOuterClass
import com.local.passover.network.DnsServiceManager
import com.local.passover.network.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.ByteString

enum class ConnectionState {IDLE, DISCOVERING, CONNECTING, CONNECTED, FAILED}

@Singleton
class ConnectionRepository @Inject constructor(
    private val discoverer: DnsServiceManager,
    private val webSocketClient: WebSocketClient,
    private val keyStoreManager: KeystoreManager,
    private val ds: DataStore<Preferences>,
    ) {
    val TAG = "ConnectionRepository"
    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState = _connectionState.asStateFlow()

    private val kDeviceId = stringPreferencesKey("deviceId")
    private val kEncryptionkey = stringPreferencesKey("encryptionKey")
    private var activeKey: SecretKey? = null
//    TODO: make activeKey thread safe

    private val _incomingMessages = MutableSharedFlow<MessageOuterClass.Message>(replay = 1)
    val incomingMessages = _incomingMessages.asSharedFlow()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val hasCredentials: Flow<Boolean> = ds.data.map { prefs ->
        prefs[kDeviceId]!=null && prefs[kEncryptionkey]!=null
    }

    init {
        startMessageProcessing()
    }


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
                        Timber.tag(TAG).w("No active key present to decrypt message")
                    }
                } catch (e: Exception){
                        Timber.tag(TAG).e(e, "Failed to decrypt message")
                }
            }
        }
    }


    suspend fun connectWithSavedCredentials(): Boolean{
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            Timber.tag(TAG).d("Already connected/connecting. Ignoring request.")
            return true
        }

        val prefs = ds.data.first()
        val deviceId = prefs[kDeviceId]
        val wrappedKey = prefs[kEncryptionkey]

        if(deviceId == null || wrappedKey == null)return false

        val secretKey = try {
            keyStoreManager.unwrapKey(wrappedKey)
        } catch (e: Exception){
            Timber.tag(TAG).e(e, "Failed to unwrap key")
            closeConnection()
            clearCredentials()
            return false
        }
        val success = executeConnectionSequence(deviceId, secretKey)
        if(success){
            _connectionState.value = ConnectionState.CONNECTED
        }
        else{
            closeConnection()
        }
        return success
    }

    suspend fun executeConnectionSequence(deviceId: String, secretKey: SecretKey): Boolean  = coroutineScope{
        if (_connectionState.value == ConnectionState.CONNECTED) return@coroutineScope true
        _connectionState.value = ConnectionState.DISCOVERING
        val serviceInfo = discoverer.findService(deviceId)
        if(serviceInfo == null){
            _connectionState.value = ConnectionState.FAILED
            Timber.tag(TAG).e("Failed to get serviceInfo")
            return@coroutineScope false
        }

        _connectionState.value = ConnectionState.CONNECTING
        val inetAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7) {
            serviceInfo.hostAddresses.first()
        } else {
            serviceInfo.host
        }
        val hostAddress = inetAddress.hostAddress?.removePrefix("/") ?: ""
        val url = "ws://${hostAddress}:${serviceInfo.port}"

        activeKey = secretKey
        val connectionStateFlow = webSocketClient.connectionState
        webSocketClient.connect(url)
        val connected = withTimeoutOrNull(10_000){
            connectionStateFlow.first{it}
        }
        if (connected != true){
            _connectionState.value = ConnectionState.FAILED
            closeConnection()
            Timber.tag(TAG).e("Failed to connect")
            return@coroutineScope false
        }

        val identityMessage = MessageOuterClass.Identity.newBuilder().setDeviceId(deviceId).build()
        val wrapperMessage = MessageOuterClass.Message.newBuilder()
            .setTimestampMs(System.currentTimeMillis())
            .setIdentity(identityMessage)
            .build()

        val encryptedMessage = keyStoreManager.encrypt(secretKey, wrapperMessage.toByteArray())

        val responseDeferred = async{
            incomingMessages.filter { it.hasIdentity() }.first()
        }

        webSocketClient.send(encryptedMessage)

        val response = withTimeoutOrNull(15_000){
            responseDeferred.await()
        }

        if (response == null){
            _connectionState.value = ConnectionState.FAILED
            closeConnection()
            Timber.tag(TAG).e("Failed to get response")
            return@coroutineScope false
        }

        return@coroutineScope try {
            val identityResponse = response.identity
            if(identityResponse.deviceId == deviceId){
                Timber.tag(TAG).d("Saving key, received identity response: ${identityResponse.deviceId}")
                true
            }
            else throw Exception("Invalid response, deviceId does not match")
        }catch (e: Exception){
            _connectionState.value = ConnectionState.FAILED
            closeConnection()
            Timber.tag(TAG).e(e, "Failed to decrypt test response")
            false
        }
    }

    suspend fun makeTestConnection(deviceId: String, key: String): Boolean {
        val secretKey = keyStoreManager.getKeyFromString(key)
        val response = executeConnectionSequence(deviceId, secretKey)
        closeConnection()
        return response
    }

    private suspend fun saveId(deviceId: String) = ds.edit { prefs ->
        prefs[kDeviceId] = deviceId
    }

    private suspend fun saveKey(key: String) = ds.edit { prefs ->
        val encryptedKey = keyStoreManager.wrapKey(key)
        prefs[kEncryptionkey] = encryptedKey
    }

    suspend fun clearCredentials() = ds.edit { it.clear() }

    suspend fun saveCredentials(deviceId: String, key: String){
        saveId(deviceId)
        saveKey(key)
        Timber.tag(TAG).d("Saved credentials for deviceId: $deviceId")
    }

    fun send(data: ByteArray){
        val encryptedData = activeKey?.let { key ->
            keyStoreManager.encrypt(key, data)
        } ?: throw Exception("No active key")
        webSocketClient.send(encryptedData)
    }

    fun closeConnection(){
        webSocketClient.disconnect()
        _connectionState.value = ConnectionState.IDLE
        activeKey = null
    }
}
