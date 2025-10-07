package com.example.billyapp.core

import android.util.Log

object FakeServer {

    private val TAG = "FakeServer"

    private val registeredUsers = mapOf(
        "alice" to User("ecb73c72", "Alice", 25, "Loves hiking"),
        "bob" to User("33f24d0c", "Bob", 30, "Software developer"),
        "charlie" to User("bcb37548", "Charlie", 28, "Photographer"),
        "luca" to User("90895e9d", "Luca", 32, "Teacher"),
        "marco" to User("8b44a291", "Marco", 27, "Designer"),
        "giulia" to User("f8507096", "Giulia", 29, "Doctor")
    )

    fun resolveRotatingId(encryptedPayload: ByteArray, receivedTimestamp: Long): User? {
        for ((username, user) in registeredUsers) {
            val secret = username.lowercase().toByteArray().copyOf(16)
            val cryptoManager = CryptographyManager(secret)
            val expectedPayload = cryptoManager.encryptRotatingIdentifier(user.id, receivedTimestamp)
            if (expectedPayload.contentEquals(encryptedPayload)) {
                Log.d(TAG, "✅ Resolved encrypted ID to user: ${user.displayName}")
                return user
            }
        }
        Log.w(TAG, "❌ Could not resolve encrypted payload to any registered user")
        return null
    }
}
