package com.example.billyapp.core

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

object FakeServer {

    private const val TAG = "FakeServer"

    // ✅ Allineato con il server reale (https://19.hackathon.ethz.ch/api/users/list/)
    private val registeredUsers = mapOf(
        "alice" to User("ecb73c72d94f1a23", "Alice", 25, "Loves hiking"),
        "bob" to User("33f24d0c2ab9e78f", "Bob", 30, "Software developer"),
        "charlie" to User("bcb375489ee42a6d", "Charlie", 28, "Photographer"),
        "luca" to User("90895e9d21cb8fa6", "Luca", 32, "Teacher")
    )

    /**
     * 🔹 Versione principale: accetta payload completo da 16 byte
     * (timestampBytes + ciphertextBytes)
     */
    fun resolveRotatingId(encryptedPayload: ByteArray, receivedTimestamp: Long): User? {
        for ((username, user) in registeredUsers) {
            val secret = username.lowercase().toByteArray().copyOf(16)
            val cryptoManager = CryptographyManager(secret)

            // ✅ Tolleranza di finestra ±1 per orologi leggermente fuori sync
            for (delta in -1..1) {
                val tsAdjusted = receivedTimestamp + delta * Constants.ROTATION_SECONDS
                val expectedPayload = cryptoManager.encryptRotatingIdentifier(user.id, tsAdjusted)
                if (expectedPayload.contentEquals(encryptedPayload)) {
                    Log.d(TAG, "✅ Resolved encrypted ID to user: ${user.displayName} (Δ=$delta)")
                    return user
                }
            }
        }
        Log.w(TAG, "❌ Could not resolve encrypted payload to any registered user")
        return null
    }

    /**
     * 🔹 Variante per accettare timestamp + cipher8_hex separati (come nella richiesta HTTP)
     */
    fun resolveRotatingId(cipher8Hex: String, receivedTimestamp: Long): User? {
        return try {
            val timestampBytes = ByteBuffer.allocate(8)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(receivedTimestamp)
                .array()

            val cipherBytes = hexStringToByteArray(cipher8Hex)
            if (cipherBytes.size != 8) {
                Log.w(TAG, "⚠️ Invalid cipher8_hex length: ${cipherBytes.size}")
                return null
            }

            val fullPayload = timestampBytes + cipherBytes
            resolveRotatingId(fullPayload, receivedTimestamp)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reconstructing payload from fields", e)
            null
        }
    }

    // 🔹 Utility HEX -> ByteArray
    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4)
                    + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
