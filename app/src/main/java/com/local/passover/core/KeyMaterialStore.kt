package com.local.passover.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns Android Keystore-backed key material and alias lifecycle.
 */
@Singleton
class KeyMaterialStore @Inject constructor() {
    private val TAG = "KeyMaterialStore"

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "passover_master_key"
        private const val KEY_AGREEMENT_ALIAS = "passover_key_agreement"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun getOrCreateMasterKey(): SecretKey {
        if (!keyExists(MASTER_KEY_ALIAS)) {
            generateAesKey(MASTER_KEY_ALIAS)
        }
        return getKey(MASTER_KEY_ALIAS)
            ?: throw IllegalStateException("Master key unavailable")
    }

    fun getOrCreateKeyAgreementPair(): KeyPair {
        if (keyExists(KEY_AGREEMENT_ALIAS)) {
            val privateKey = keyStore.getKey(KEY_AGREEMENT_ALIAS, null) as PrivateKey
            val publicKey = keyStore.getCertificate(KEY_AGREEMENT_ALIAS).publicKey
            return KeyPair(publicKey, privateKey)
        }

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_AGREEMENT_ALIAS,
            KeyProperties.PURPOSE_AGREE_KEY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .build()

        generator.initialize(spec)
        return generator.generateKeyPair().also {
            Timber.tag(TAG).d("Generated P-256 key agreement pair")
        }
    }

    fun getPublicKeyAgreement(): ByteArray = getOrCreateKeyAgreementPair().public.encoded

    fun getKeyAgreementPrivateKey(): PrivateKey = getOrCreateKeyAgreementPair().private as PrivateKey

    private fun generateAesKey(alias: String) {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    private fun keyExists(alias: String): Boolean {
        return try {
            keyStore.containsAlias(alias)
        } catch (t: Throwable) {
            Timber.tag(TAG).e("Failed to check if key exists: $t")
            false
        }
    }

    private fun getKey(alias: String): SecretKey? {
        val key = keyStore.getKey(alias, null) ?: return null
        return key as? SecretKey
    }
}
