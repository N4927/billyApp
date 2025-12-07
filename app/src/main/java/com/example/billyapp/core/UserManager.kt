package com.example.billyapp.core

import android.content.Context
import android.util.Log
import com.example.shared.ClientBidManager
import com.example.shared.User

class UserManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "user_data"
        private const val KEY_ID = "user_id"
        private const val KEY_NAME = "user_name"
        private const val KEY_AGE = "user_age"
        private const val KEY_BIO = "user_bio"
        private const val KEY_BLE_ONLINE = "ble_online"
        private const val KEY_BATCH_FIRST_SLOT = "batch_first_slot"
        private const val KEY_BATCH_BIDS = "batch_bids_json" // Store batch as JSON
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var bidManager: ClientBidManager? = null

    fun saveUser(user: User) {
        prefs.edit()
            .putString(KEY_ID, user.id)
            .putString(KEY_NAME, user.name) // ✅ Fixed: was user.displayName
            .putInt(KEY_AGE, user.age ?: -1)
            .putString(KEY_BIO, user.bio)
            .apply()

        // ✅ Initialize BID manager when user is saved
        initBidManager()

        Log.i("UserManager", "💾 Saved user: ${user.name}")
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
        bidManager = null
        Log.i("UserManager", "🧹 Cleared user data")
    }

    fun getUserName(): String {
        val name = prefs.getString(KEY_NAME, "Anonymous") ?: "Anonymous"
        Log.i("UserManager", "📖 Loaded user name: $name")
        return name
    }

    // ✅ REMOVED: getSecretForBle() - No more client-side encryption!
    // ✅ REMOVED: getCryptographyManager() - No more client-side encryption!
    // ✅ REMOVED: initCryptographyManager() - No more client-side encryption!

    fun getPersonalIdentifier(): String {
        return getUser()?.id ?: "anonymous"
    }

    fun isBleOnline(): Boolean {
        return prefs.getBoolean(KEY_BLE_ONLINE, false)
    }

    fun setBleOnline(isOnline: Boolean) {
        prefs.edit().putBoolean(KEY_BLE_ONLINE, isOnline).apply()
        Log.i("UserManager", "🌐 BLE online state set to: $isOnline")
    }

    // ✅ NEW: BID Manager for server-side batch system
    fun getBidManager(): ClientBidManager {
        if (bidManager == null) {
            initBidManager()
        }
        return bidManager!!
    }

    private fun initBidManager() {
        val userName = getUserName()
        bidManager = ClientBidManager(userName)
        Log.i("UserManager", "🔄 Initialized BID Manager for user: $userName")
    }

    // ✅ NEW: Batch management methods
    fun refreshBatch(): Boolean {
        return try {
            val success = getBidManager().refreshBatch()
            if (success) {
                Log.i("UserManager", "✅ Successfully refreshed BID batch")
            } else {
                Log.w("UserManager", "⚠️ Failed to refresh BID batch")
            }
            success
        } catch (e: Exception) {
            Log.e("UserManager", "❌ Error refreshing batch: ${e.message}")
            false
        }
    }

    fun isBatchValid(): Boolean {
        return getBidManager().isBatchValid()
    }

    fun getBatchStatus(): String {
        return getBidManager().getBatchStatus()
    }

    // ✅ NEW: Get current BID for BLE advertising
    fun getCurrentBidBytes(): ByteArray? {
        return getBidManager().getCurrentBidBytes()
    }

    // ✅ NEW: Get current BID as hex string (for debugging)
    fun getCurrentBidHex(): String? {
        return getBidManager().getCurrentBid()
    }

    // ✅ NEW: User status summary
    fun getUserStatus(): String {
        val user = getUser()
        return if (user != null) {
            val batchStatus = if (isBatchValid()) "Valid batch" else "No batch"
            val bleStatus = if (isBleOnline()) "BLE Online" else "BLE Offline"
            "${user.name} | $batchStatus | $bleStatus"
        } else {
            "No user registered"
        }
    }
    // Add to UserManager.kt
    fun clearAllUserData() {
        // ✅ Clear shared preferences
        prefs.edit().clear().apply()

        // ✅ Clear BID manager
        bidManager = null

        Log.i("UserManager", "🧹 Cleared ALL user data and settings")
    }

}