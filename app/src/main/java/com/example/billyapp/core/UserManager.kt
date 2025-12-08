package com.example.billyapp.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.UUID

/**
 * Gestisce:
 * - stato BLE online/offline
 * - profilo utente base (id, name, age, bio)
 *
 * Tutto viene salvato in SharedPreferences in modo semplice.
 */
class UserManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- BLE ONLINE / OFFLINE ----

    fun isBleOnline(): Boolean =
        prefs.getBoolean(KEY_BLE_ONLINE, false)

    fun setBleOnline(online: Boolean) {
        prefs.edit().putBoolean(KEY_BLE_ONLINE, online).apply()
    }

    fun getUserStatus(): String =
        if (isBleOnline()) "online" else "offline"

    // ---- PROFILO UTENTE ----

    fun getUser(): User? {
        val name = prefs.getString(KEY_USER_NAME, null) ?: return null

        val id = prefs.getString(KEY_USER_ID, null) ?: UUID.randomUUID().toString()
        val ageStored = prefs.getInt(KEY_USER_AGE, -1)
        val age = if (ageStored >= 0) ageStored else null
        val bio = prefs.getString(KEY_USER_BIO, null)

        return User(
            id = id,
            name = name,
            age = age,
            bio = bio
        )
    }

    fun saveUser(user: User) {
        prefs.edit()
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USER_NAME, user.name)
            .putInt(KEY_USER_AGE, user.age ?: -1)
            .putString(KEY_USER_BIO, user.bio)
            .apply()
    }

    /**
     * Per ora è solo un placeholder.
     * In futuro potrai chiamare qui il core KMM (ensureAdvertisingBatch, ecc.)
     */
    fun refreshBatch() {
        Log.d("UserManager", "refreshBatch() called (placeholder, hook KMM here later)")
    }

    fun clearAllUserData() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "billy_user_prefs"
        private const val KEY_BLE_ONLINE = "ble_online"

        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_AGE = "user_age"
        private const val KEY_USER_BIO = "user_bio"
    }
}
