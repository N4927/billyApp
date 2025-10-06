package com.example.billyapp.core

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class UserManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    fun saveUser(user: User) {
        val json = Json.encodeToString(user)
        prefs.edit().putString("user_json", json).apply()
    }

    fun getUser(): User? {
        val json = prefs.getString("user_json", null)
        return json?.let { Json.decodeFromString<User>(it) }
    }

    fun clearUser() {
        prefs.edit().remove("user_json").apply()
    }
}
