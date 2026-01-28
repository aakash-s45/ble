package com.local.passover.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreManager @Inject constructor(
    @ApplicationContext ctx: Context,
){
    private val TAG = "KeystoreManager"
    companion object{
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SIZE = 12
        private const val MASTER_KEY_ALIAS = "passover_master_key"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun generateAedKey(alias: String): Boolean{
        if (keyExists(alias)) return  false

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        keyGenerator.generateKey()
        return true
    }

    fun getOrGenerateMasterKey(): Boolean{
        if(!keyExists(MASTER_KEY_ALIAS)){
            return generateAedKey(MASTER_KEY_ALIAS)
        }
        return true
    }

    fun wrapKey(secretKey: String): String{
        removeKey(MASTER_KEY_ALIAS)
        getOrGenerateMasterKey()
        val keyBytes = Base64.decode(secretKey, Base64.DEFAULT)
        val encryptedKeyBytes =  encrypt(MASTER_KEY_ALIAS, keyBytes)
        return Base64.encodeToString(encryptedKeyBytes, Base64.DEFAULT)
    }

    fun unwrapKey(wrappedKey: String): SecretKey {
        val wrappedKeyData = Base64.decode(wrappedKey, Base64.DEFAULT)
        getOrGenerateMasterKey()
        val keyBytes =  decrypt(MASTER_KEY_ALIAS, wrappedKeyData)
        return SecretKeySpec(keyBytes, 0, keyBytes.size, "AES")
    }

    fun keyExists(alias: String): Boolean {
        return try {
            keyStore.containsAlias(alias)
        } catch (t: Throwable) {
            Timber.tag(TAG).e("Failed to check if key exists: $t")
            false
        }
    }

    fun removeKey(alias: String): Boolean{
        if(!keyExists(alias))return false
        try{
            keyStore.deleteEntry(alias)
            return true
        }catch (t: Throwable){
            Timber.tag(TAG).e("Failed to remove key: $t")
            return false
        }
    }

    fun encrypt(alias: String, data: ByteArray): ByteArray {
        val key = getKey(alias) ?: throw IllegalArgumentException("Key not found for alias")
        return encrypt(key, data)
    }

    fun getKeyFromString(keyString: String): SecretKey {
        val keyBytes = Base64.decode(keyString, Base64.DEFAULT)
        return SecretKeySpec(keyBytes, 0, keyBytes.size, "AES")
    }

    fun encrypt(key: SecretKey, data: ByteArray): ByteArray {
        try {
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv ?: throw IllegalStateException("IV is null")
            val encryptedData = cipher.doFinal(data)

            val buffer = ByteBuffer.allocate(iv.size + encryptedData.size)
            buffer.put(iv)
            buffer.put(encryptedData)
            return buffer.array()
        }catch (t: Throwable){
            Timber.tag(TAG).e("Failed to encrypt data: $t")
            throw t
        }
    }

    fun decrypt(alias: String, data: ByteArray): ByteArray {
        val key = getKey(alias) ?: throw IllegalArgumentException("Key not found for alias")
        return decrypt(key, data)
    }

    fun decrypt(key: SecretKey, data: ByteArray): ByteArray {
        try {
            if (data.size < IV_SIZE) throw IllegalArgumentException("Invalid data length")
            val iv = data.copyOfRange(0, IV_SIZE)

            val cipherText = data.copyOfRange(IV_SIZE, data.size)
            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            return cipher.doFinal(cipherText)
        }catch (t: Throwable){
            Timber.tag(TAG).e("Failed to decrypt data: $t")
            throw t
        }
    }

    fun getKey(alias: String): SecretKey?{
        val key = keyStore.getKey(alias, null)?: return null
        return key as? SecretKey
    }

    fun saveKey(){

    }

}