package com.example.shared

/**
 * Real AES-256-CBC implementation.
 * - encryptRotatingIdentifier: 16 B plaintext → 16 B ciphertext (no padding needed)
 * - decryptRotatingIdentifier: 16 B ciphertext → 16 B plaintext
 */
expect class CryptographyManager(keyBytes: ByteArray) {
    fun encryptRotatingIdentifier(plain16: ByteArray): ByteArray
    fun decryptRotatingIdentifier(cipher16: ByteArray): ByteArray
}