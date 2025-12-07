package com.example.shared

import kotlinx.cinter.*
import platform.CommonCrypto.*
import platform.Security.kCCSuccess
import platform.Security.kCCKeySizeAES128
import platform.Security.kCCKeySizeAES256

actual class CryptographyManager actual constructor(private val keyBytes: ByteArray) {

    actual fun encryptRotatingIdentifier(plain16: ByteArray): ByteArray {
        require(plain16.size == 16) { "Plaintext must be exactly 16 bytes" }
        require(keyBytes.size == 16 || keyBytes.size == 32) { "Key must be 16 or 32 bytes" }

        val out = ByteArray(16)
        val keySize = if (keyBytes.size == 32) kCCKeySizeAES256 else kCCKeySizeAES128

        val cryptStatus = plain16.usePinned { plainPin ->
            out.usePinned { outPin ->
                keyBytes.usePinned { keyPin ->
                    CCCrypt(
                        op = kCCEncrypt,
                        alg = kCCAlgorithmAES,
                        options = kCCOptionECBMode,
                        key = keyPin.addressOf(0),
                        keyLength = keySize,
                        iv = null, // ECB doesn't use IV
                        dataIn = plainPin.addressOf(0),
                        dataInLength = 16uL,
                        dataOut = outPin.addressOf(0),
                        dataOutAvailable = 16uL,
                        dataOutMoved = null
                    )
                }
            }
        }

        if (cryptStatus != kCCSuccess) {
            throw SecurityException("AES encryption failed with status: $cryptStatus")
        }

        return out
    }

    actual fun decryptRotatingIdentifier(cipher16: ByteArray): ByteArray {
        require(cipher16.size == 16) { "Ciphertext must be exactly 16 bytes" }
        require(keyBytes.size == 16 || keyBytes.size == 32) { "Key must be 16 or 32 bytes" }

        val out = ByteArray(16)
        val keySize = if (keyBytes.size == 32) kCCKeySizeAES256 else kCCKeySizeAES128

        val cryptStatus = cipher16.usePinned { cipherPin ->
            out.usePinned { outPin ->
                keyBytes.usePinned { keyPin ->
                    CCCrypt(
                        op = kCCDecrypt,
                        alg = kCCAlgorithmAES,
                        options = kCCOptionECBMode,
                        key = keyPin.addressOf(0),
                        keyLength = keySize,
                        iv = null, // ECB doesn't use IV
                        dataIn = cipherPin.addressOf(0),
                        dataInLength = 16uL,
                        dataOut = outPin.addressOf(0),
                        dataOutAvailable = 16uL,
                        dataOutMoved = null
                    )
                }
            }
        }

        if (cryptStatus != kCCSuccess) {
            throw SecurityException("AES decryption failed with status: $cryptStatus")
        }

        return out
    }
}