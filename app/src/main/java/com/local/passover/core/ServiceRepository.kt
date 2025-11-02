package com.local.passover.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ServiceRepository @Inject constructor(
    private val ds: DataStore<Preferences>,
    private val keystoreManager: KeystoreManager
){
    private val kDeviceId = stringPreferencesKey("deviceId")
    private val kEncryptionkey = stringPreferencesKey("encryptionKey")

    val deviceId: Flow<String?> = ds.data.map { prefs ->
        prefs[kDeviceId]
    }

    val encryptionKey: Flow<String?> = ds.data.map { prefs ->
        prefs[kEncryptionkey]
    }

    suspend fun saveId(deviceId: String) = ds.edit { prefs ->
        prefs[kDeviceId] = deviceId
    }

    suspend fun saveKey(key: String) = ds.edit { prefs ->
        prefs[kEncryptionkey] = key
    }

    suspend fun clear() = ds.edit { it.clear() }

    private fun saveCredentials(deviceId: String, key: String){
        // TODO: save deviceId in datastore and key in keystore, also on boot get these from there

    }
}