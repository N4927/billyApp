package com.example.billyapp.kmm

import android.content.Context
import androidx.core.content.edit
import com.billyapp.shared.domain.repository.TokenStorage  // ← Interfaccia dal KMM

class AndroidTokenStorage(context: Context) : TokenStorage {
    
    private val prefs = context.getSharedPreferences("billy_tokens", Context.MODE_PRIVATE)

    override fun getAccessToken(): String? = prefs.getString("access", null)
    override fun getRefreshToken(): String? = prefs.getString("refresh", null)
    
    override fun saveTokens(access: String, refresh: String) {
        prefs.edit(commit = true) {
            putString("access", access)
            putString("refresh", refresh)
        }
    }
    
    override fun clearTokens() {
        prefs.edit(commit = true) {
            remove("access")
            remove("refresh")
        }
    }
}