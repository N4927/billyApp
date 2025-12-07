// androidMain/kotlin/com/example/shared/CryptographyManager.android.kt
package com.example.shared

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

actual class CryptographyManager actual constructor(private val keyBytes: ByteArray) {
    private val secretKey = SecretKeySpec(keyBytes, "AES")

    actual fun encryptRotatingIdentifier(plain16: ByteArray): ByteArray {
        require(plain16.size == 16) { "Plaintext must be 16 B" }
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(plain16)
    }

    actual fun decryptRotatingIdentifier(cipher16: ByteArray): ByteArray {
        require(cipher16.size == 16) { "Ciphertext must be 16 B" }
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        return cipher.doFinal(cipher16)
    }
}