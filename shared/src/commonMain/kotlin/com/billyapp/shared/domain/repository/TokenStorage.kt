package com.billyapp.shared.domain.repository

/**
 * Abstraction for Secure Token Storage.
 *
 * This interface allows the Shared Module to persist sensitive credentials
 * (Access & Refresh Tokens) without knowing the platform-specific implementation details.
 *
 * Implementations:
 * - iOS: Wrapper around Keychain Services.
 * - Android: Wrapper around EncryptedSharedPreferences.
 */
interface TokenStorage {
    /**
     * Retrieves the current Access Token.
     * @return The token string, or null if not logged in.
     */
    fun getAccessToken(): String?

    /**
     * Retrieves the current Refresh Token.
     * Used to obtain a new Access Token when the current one expires.
     * @return The token string, or null if not logged in.
     */
    fun getRefreshToken(): String?

    /**
     * Persists the token pair securely.
     * Overwrites any existing tokens.
     * @param access The new Access Token.
     * @param refresh The new Refresh Token.
     */
    fun saveTokens(
        access: String,
        refresh: String,
    )

    /**
     * Wipes all stored tokens.
     * Used during Logout or when a Refresh Token is invalidated (Force Logout).
     */
    fun clearTokens()
}
