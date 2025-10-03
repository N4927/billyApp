package com.example.billyapp.core.api

import android.content.Context
import java.util.UUID

object ProfileManager {
    private const val PREFS_NAME = "user_profile"
    private const val KEY_UUID = "user_uuid"
    private const val KEY_NAME = "user_name"

    fun getOrCreateUUID(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var uuid = prefs.getString(KEY_UUID, null)
        if (uuid == null) {
            uuid = UUID.randomUUID().toString() // genera UUID unico
            prefs.edit().putString(KEY_UUID, uuid).apply()
        }
        return uuid
    }

    fun setUserName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_NAME, name).apply()
    }

    fun getUserName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NAME, "Anonimo") ?: "Anonimo"
    }
}
