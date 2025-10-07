package com.example.billyapp.core

import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class CryptographyManager(private val sharedSecret: ByteArray) {

    companion object {
        private const val TAG = "CryptographyManager"
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/ECB/NoPadding"
        private const val BLOCK_SIZE = 16
    }

    private val secretKey: SecretKeySpec

    init {
        val keyBytes = if (sharedSecret.size >= BLOCK_SIZE) {
            sharedSecret.copyOf(BLOCK_SIZE)
        } else {
            sharedSecret.copyOf(BLOCK_SIZE)
        }
        secretKey = SecretKeySpec(keyBytes, ALGORITHM)
        Log.d(TAG, "🔐 Initialized with key: ${keyBytes.joinToString("") { "%02x".format(it) }}")
    }

    fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
     * Encrypts personalId (as hex string) with rotating window
     * Output payload format: 8 byte timestamp + 8 byte truncated ciphertext (total 16 bytes)
     */
    fun encryptRotatingIdentifier(personalIdHex: String, timestamp: Long): ByteArray {
        try {
            val timeWindow = timestamp / Constants.ROTATION_SECONDS
            val timeWindowBytes = longToBytes(timeWindow)

            val personalIdBytes = hexStringToByteArray(personalIdHex).copyOf(8)

            val plaintext = ByteArray(BLOCK_SIZE)
            System.arraycopy(personalIdBytes, 0, plaintext, 0, 8)
            System.arraycopy(timeWindowBytes, 0, plaintext, 8, 8)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val fullCiphertext = cipher.doFinal(plaintext)

            val truncatedCiphertext = fullCiphertext.copyOf(8)

            val timestampBytes = longToBytes(timestamp)

            return timestampBytes + truncatedCiphertext
        } catch (e: Exception) {
            Log.e(TAG, "❌ Encryption exception", e)
            throw e
        }
    }

    // Decryption method remains unchanged

    private fun longToBytes(value: Long): ByteArray {
        val bytes = ByteArray(8)
        for (i in 0 until 8) {
            bytes[i] = (value shr (8 * (7 - i))).toByte()
        }
        return bytes
    }

    private fun bytesToLong(bytes: ByteArray): Long {
        var result = 0L
        for (b in bytes) {
            result = (result shl 8) or (b.toLong() and 0xFF)
        }
        return result
    }
}
