package com.local.passover.core

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.security.KeyPair
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent device identity and wrapped keys in DataStore.
 */
@Singleton
class DeviceIdentityStore @Inject constructor(
    private val ds: DataStore<Preferences>,
    private val crypto: CryptoEngine,
    private val keyMaterialStore: KeyMaterialStore,
) {
    private val TAG = "DeviceIdentityStore"

    companion object {
        private val kDeviceId = stringPreferencesKey("deviceId")
        private val kWrappedGroupKey = stringPreferencesKey("wrappedGroupKey")
        private val kWrappedSigningKey = stringPreferencesKey("wrappedEd25519PrivateKey")
        private val kSigningPublicKey = stringPreferencesKey("ed25519PublicKey")
    }

    suspend fun getDeviceId(): String {
        val prefs = ds.data.first()
        val existing = prefs[kDeviceId]
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        ds.edit { it[kDeviceId] = newId }
        Timber.tag(TAG).d("Generated new device ID: $newId")
        return newId
    }

    suspend fun getOrCreateGroupKey(): SecretKey {
        val prefs = ds.data.first()
        val wrappedKey = prefs[kWrappedGroupKey]
        if (wrappedKey != null) {
            return try {
                unwrapKey(wrappedKey)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to unwrap group key, generating new one")
                val newKey = crypto.generateGroupKey()
                saveGroupKey(newKey)
                newKey
            }
        }
        val newKey = crypto.generateGroupKey()
        saveGroupKey(newKey)
        return newKey
    }

    suspend fun saveGroupKey(key: SecretKey) {
        val wrapped = wrapBytes(key.encoded)
        ds.edit { prefs -> prefs[kWrappedGroupKey] = wrapped }
        Timber.tag(TAG).d("Saved group key")
    }

    suspend fun hasGroupKey(): Boolean {
        val prefs = ds.data.first()
        return prefs[kWrappedGroupKey] != null
    }

    suspend fun clearGroupKey() {
        ds.edit { prefs -> prefs.remove(kWrappedGroupKey) }
    }

    suspend fun getOrCreateSigningPair(): KeyPair {
        val prefs = ds.data.first()
        val wrappedPrivate = prefs[kWrappedSigningKey]
        val storedPublic = prefs[kSigningPublicKey]

        if (wrappedPrivate != null && storedPublic != null) {
            return try {
                val privateKeyBytes = unwrapBytes(wrappedPrivate)
                val publicKeyBytes = Base64.decode(storedPublic, Base64.DEFAULT)
                crypto.buildSigningKeyPairFromBytes(privateKeyBytes, publicKeyBytes)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load signing pair, regenerating")
                generateAndStoreSigningPair()
            }
        }

        return generateAndStoreSigningPair()
    }

    suspend fun getPublicKeySignature(): ByteArray {
        val keyPair = getOrCreateSigningPair()
        return keyPair.public.encoded
    }

    private suspend fun generateAndStoreSigningPair(): KeyPair {
        val keyPair = crypto.generateSoftwareSigningKeyPair()

        val wrappedPrivate = wrapBytes(keyPair.private.encoded)
        val publicKeyBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT)

        ds.edit { prefs ->
            prefs[kWrappedSigningKey] = wrappedPrivate
            prefs[kSigningPublicKey] = publicKeyBase64
        }

        Timber.tag(TAG).d("Generated and stored P-256 signing pair")
        return keyPair
    }

    private fun wrapBytes(data: ByteArray): String {
        val encrypted = crypto.encrypt(keyMaterialStore.getOrCreateMasterKey(), data)
        return Base64.encodeToString(encrypted, Base64.DEFAULT)
    }

    private fun unwrapBytes(wrapped: String): ByteArray {
        val encrypted = Base64.decode(wrapped, Base64.DEFAULT)
        return crypto.decrypt(keyMaterialStore.getOrCreateMasterKey(), encrypted)
    }

    private fun unwrapKey(wrappedKey: String): SecretKey {
        val keyBytes = unwrapBytes(wrappedKey)
        return SecretKeySpec(keyBytes, 0, keyBytes.size, "AES")
    }
}
