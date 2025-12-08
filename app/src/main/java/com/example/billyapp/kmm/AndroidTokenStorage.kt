package com.example.billyapp.kmm

import android.content.Context
import androidx.core.content.edit
import com.billyapp.shared.domain.repository.TokenStorage

/**
 * Implementazione Android di TokenStorage.
 * Per ora usa normali SharedPreferences (puoi passare a EncryptedSharedPreferences in futuro).
 */
class AndroidTokenStorage(context: Context) : TokenStorage {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getAccessToken(): String? =
        prefs.getString(KEY_ACCESS, null)

    override fun getRefreshToken(): String? =
        prefs.getString(KEY_REFRESH, null)

    override fun saveTokens(access: String, refresh: String) {
        prefs.edit(commit = true) {
            putString(KEY_ACCESS, access)
            putString(KEY_REFRESH, refresh)
        }
    }

    override fun clearTokens() {
        prefs.edit(commit = true) {
            remove(KEY_ACCESS)
            remove(KEY_REFRESH)
        }
    }

    private companion object {
        const val PREFS_NAME = "billy_tokens"
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"
    }
}
