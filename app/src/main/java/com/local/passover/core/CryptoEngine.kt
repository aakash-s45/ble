package com.local.passover.core

import android.util.Log
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
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

/**
 * Pure cryptographic operations. No network, persistence, or keystore ownership.
 */
@Singleton
class CryptoEngine @Inject constructor() {

    private val TAG = "CryptoEngine"

    companion object {
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SIZE = 12
    }

    fun computeSharedSecret(privateKey: PrivateKey, peerPublicKeyBytes: ByteArray): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC")
        val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)

        return keyAgreement.generateSecret()
    }

    fun deriveSessionKey(sharedSecret: ByteArray, info: ByteArray = "passover-pairing".toByteArray()): SecretKey {
        val derivedBytes = hkdfSha256(sharedSecret, salt = ByteArray(32), info = info, outputLength = 32)
        return SecretKeySpec(derivedBytes, "AES")
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")

        val saltKey = if (salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(saltKey, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

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

    fun generateGroupKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }

    fun computeSasCode(localPubKey: ByteArray, remotePubKey: ByteArray): String {
        val sorted = if (localPubKey.toList() < remotePubKey.toList()) {
            localPubKey + remotePubKey
        } else {
            remotePubKey + localPubKey
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(sorted)

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
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to encrypt data: $t")
            throw t
        }
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
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to decrypt data: $t")
            throw t
        }
    }

    fun buildSigningKeyPairFromBytes(privateKeyBytes: ByteArray, publicKeyBytes: ByteArray): KeyPair {
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
        return KeyPair(publicKey, privateKey)
    }

    fun generateSoftwareSigningKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }
}
