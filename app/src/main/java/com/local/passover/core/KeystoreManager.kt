package com.local.passover.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.nio.ByteBuffer
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreManager @Inject constructor(
    @ApplicationContext ctx: Context,
    private val ds: DataStore<Preferences>,
){
    private val TAG = "KeystoreManager"
    companion object{
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SIZE = 12
        private const val MASTER_KEY_ALIAS = "passover_master_key"
        private const val KEY_AGREEMENT_ALIAS = "passover_key_agreement"
        private val kWrappedGroupKey = stringPreferencesKey("wrappedGroupKey")
        private val kWrappedSigningKey = stringPreferencesKey("wrappedEd25519PrivateKey")
        private val kSigningPublicKey = stringPreferencesKey("ed25519PublicKey")
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    // ── AES key generation (existing, used for master key) ────────────────

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

    // ── P-256 key agreement pair ──────────────────────────────────────────

    fun getOrCreateKeyAgreementPair(): KeyPair {
        if (keyExists(KEY_AGREEMENT_ALIAS)) {
            val privateKey = keyStore.getKey(KEY_AGREEMENT_ALIAS, null) as PrivateKey
            val publicKey = keyStore.getCertificate(KEY_AGREEMENT_ALIAS).publicKey
            return KeyPair(publicKey, privateKey)
        }

        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_AGREEMENT_ALIAS,
            KeyProperties.PURPOSE_AGREE_KEY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .build()

        kpg.initialize(spec)
        return kpg.generateKeyPair().also {
            Timber.tag(TAG).d("Generated P-256 key agreement pair")
        }
    }

    fun getPublicKeyAgreement(): ByteArray {
        val keyPair = getOrCreateKeyAgreementPair()
        return keyPair.public.encoded
    }

    // ── P-256 ECDSA signing pair ──────────────────────────────────────────
    // Software-backed P-256 key (not in AndroidKeyStore so it's exportable).
    // Private key is wrapped with the master key and stored in DataStore.

    suspend fun getOrCreateSigningPair(): KeyPair {
        val prefs = ds.data.first()
        val wrappedPrivate = prefs[kWrappedSigningKey]
        val storedPublic = prefs[kSigningPublicKey]

        if (wrappedPrivate != null && storedPublic != null) {
            return try {
                val privateKeyBytes = unwrapRawBytes(wrappedPrivate)
                val publicKeyBytes = Base64.decode(storedPublic, Base64.DEFAULT)
                val keyFactory = KeyFactory.getInstance("EC")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
                KeyPair(publicKey, privateKey)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load signing pair, regenerating")
                generateAndStoreSigningPair()
            }
        }

        return generateAndStoreSigningPair()
    }

    private suspend fun generateAndStoreSigningPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()

        // Wrap private key with master key and store in DataStore
        getOrGenerateMasterKey()
        val wrappedPrivate = wrapKeyBytes(keyPair.private.encoded)
        val publicKeyBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT)

        ds.edit { prefs ->
            prefs[kWrappedSigningKey] = wrappedPrivate
            prefs[kSigningPublicKey] = publicKeyBase64
        }

        Timber.tag(TAG).d("Generated and stored P-256 signing pair")
        return keyPair
    }

    suspend fun getPublicKeySignature(): ByteArray {
        val keyPair = getOrCreateSigningPair()
        return keyPair.public.encoded
    }

    private fun unwrapRawBytes(wrapped: String): ByteArray {
        val wrappedData = Base64.decode(wrapped, Base64.DEFAULT)
        getOrGenerateMasterKey()
        return decrypt(MASTER_KEY_ALIAS, wrappedData)
    }

    // ── Shared secret computation ─────────────────────────────────────────

    fun computeSharedSecret(peerPublicKeyBytes: ByteArray): ByteArray {
        val keyPair = getOrCreateKeyAgreementPair()
        val keyFactory = KeyFactory.getInstance("EC")
        val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(keyPair.private)
        keyAgreement.doPhase(peerPublicKey, true)

        return keyAgreement.generateSecret()
    }

    // ── Session key derivation (HKDF-SHA256) ──────────────────────────────

    fun deriveSessionKey(sharedSecret: ByteArray, info: ByteArray = "passover-pairing".toByteArray()): SecretKey {
        val derivedBytes = hkdfSha256(sharedSecret, salt = ByteArray(32), info = info, outputLength = 32)
        return SecretKeySpec(derivedBytes, "AES")
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")

        // Extract
        val saltKey = if (salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(saltKey, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        // Expand
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val result = ByteArray(outputLength)
        var t = ByteArray(0)
        var offset = 0
        var counter: Byte = 1

        while (offset < outputLength) {
            mac.update(t)
            mac.update(info)
            mac.update(counter)
            t = mac.doFinal()

            val toCopy = minOf(t.size, outputLength - offset)
            System.arraycopy(t, 0, result, offset, toCopy)
            offset += toCopy
            counter++
        }
        return result
    }

    // ── GroupKey management (wrap/unwrap via master key, stored in DataStore) ──

    fun wrapKey(secretKey: String): String{
        removeKey(MASTER_KEY_ALIAS)
        getOrGenerateMasterKey()
        val keyBytes = Base64.decode(secretKey, Base64.DEFAULT)
        val encryptedKeyBytes =  encrypt(MASTER_KEY_ALIAS, keyBytes)
        return Base64.encodeToString(encryptedKeyBytes, Base64.DEFAULT)
    }

    fun wrapKeyBytes(keyBytes: ByteArray): String {
        getOrGenerateMasterKey()
        val encryptedKeyBytes = encrypt(MASTER_KEY_ALIAS, keyBytes)
        return Base64.encodeToString(encryptedKeyBytes, Base64.DEFAULT)
    }

    fun unwrapKey(wrappedKey: String): SecretKey {
        val wrappedKeyData = Base64.decode(wrappedKey, Base64.DEFAULT)
        getOrGenerateMasterKey()
        val keyBytes =  decrypt(MASTER_KEY_ALIAS, wrappedKeyData)
        return SecretKeySpec(keyBytes, 0, keyBytes.size, "AES")
    }

    fun generateGroupKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }

    suspend fun getOrCreateGroupKey(): SecretKey {
        val prefs = ds.data.first()
        val wrappedKey = prefs[kWrappedGroupKey]
        if (wrappedKey != null) {
            return try {
                unwrapKey(wrappedKey)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to unwrap group key, generating new one")
                val newKey = generateGroupKey()
                saveGroupKey(newKey)
                newKey
            }
        }
        val newKey = generateGroupKey()
        saveGroupKey(newKey)
        return newKey
    }

    suspend fun saveGroupKey(key: SecretKey) {
        val keyBytes = key.encoded
        val wrapped = wrapKeyBytes(keyBytes)
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

    // ── SAS computation ───────────────────────────────────────────────────

    fun computeSasCode(localPubKey: ByteArray, remotePubKey: ByteArray): String {
        val sorted = if (localPubKey.toList() < remotePubKey.toList()) {
            localPubKey + remotePubKey
        } else {
            remotePubKey + localPubKey
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(sorted)

        // Take first 4 bytes, convert to unsigned int, mod 1_000_000 for 6 digits
        val num = ((digest[0].toLong() and 0xFF) shl 24) or
                  ((digest[1].toLong() and 0xFF) shl 16) or
                  ((digest[2].toLong() and 0xFF) shl 8) or
                  (digest[3].toLong() and 0xFF)

        return (num % 1_000_000).toString().padStart(6, '0')
    }

    private operator fun List<Byte>.compareTo(other: List<Byte>): Int {
        for (i in 0 until minOf(this.size, other.size)) {
            val cmp = (this[i].toInt() and 0xFF).compareTo(other[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return this.size.compareTo(other.size)
    }

    // ── Existing utility methods ──────────────────────────────────────────

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
}