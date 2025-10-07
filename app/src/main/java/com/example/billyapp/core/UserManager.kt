package com.example.billyapp.core

import android.content.Context
import android.util.Log

class UserManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "user_data"
        private const val KEY_ID = "user_id"
        private const val KEY_NAME = "user_name"
        private const val KEY_AGE = "user_age"
        private const val KEY_BIO = "user_bio"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var cryptographyManager: CryptographyManager? = null

    fun saveUser(user: User) {
        prefs.edit()
            .putString(KEY_ID, user.id)
            .putString(KEY_NAME, user.displayName)
            .putInt(KEY_AGE, user.age ?: -1)
            .putString(KEY_BIO, user.bio)
            .apply()

        // Initialize crypto manager when user is saved
        initCryptographyManager()

        Log.i("UserManager", "💾 Saved user: ${user.displayName}")
    }

    fun getUser(): User? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val age = prefs.getInt(KEY_AGE, -1).takeIf { it >= 0 }
        val bio = prefs.getString(KEY_BIO, null)

        return User(id, name, age, bio)
    }

    fun clearUser() {
        prefs.edit().clear().apply()
        cryptographyManager = null
        Log.i("UserManager", "🧹 Cleared user data")
    }

    fun getUserName(): String {
        val name = prefs.getString(KEY_NAME, "Anonimo") ?: "Anonimo"
        Log.i("UserManager", "📖 Loaded user name: $name")
        return name
    }

    fun getSecretForBle(length: Int = 16): ByteArray {
        val name = getUserName()
        val secret = name.lowercase().toByteArray().copyOf(length)
        Log.d("UserManager", "🔐 Secret for BLE: ${secret.joinToString(" ") { "%02x".format(it) }} (from name=$name)")
        return secret
    }

    /**
     * Get CryptographyManager for this user
     */
    fun getCryptographyManager(): CryptographyManager {
        if (cryptographyManager == null) {
            initCryptographyManager()
        }
        return cryptographyManager!!
    }

    private fun initCryptographyManager() {
        val secret = getSecretForBle(16) // 16 bytes for AES-128
        cryptographyManager = CryptographyManager(secret)
    }

    /**
     * Get user's personal identifier (their user ID)
     */
    fun getPersonalIdentifier(): String {
        return getUser()?.id ?: "anonymous"
    }
}
