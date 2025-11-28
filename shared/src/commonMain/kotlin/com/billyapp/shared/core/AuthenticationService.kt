package com.billyapp.shared.core

import com.billyapp.shared.data.network.BillyApiClient

/**
 * Authentication Domain Service.
 * Represents the "Identity" Vertical Slice of the application.
 */
class AuthenticationService(
    private val apiClient: BillyApiClient
) {
    // Espone i DTO di risposta per la UI
    // In un'architettura ancora più pura, mapperemmo questi DTO in Domain Models,
    // ma per un SDK mobile snello, usare i DTO di rete va bene (Pragmatism over Dogma).

    @Throws(Exception::class) // Importante per Swift error handling
    suspend fun login(email: String, password: String): BillyApiClient.AuthResponse {
        return apiClient.login(email, password)
    }

    @Throws(Exception::class)
    suspend fun register(username: String, email: String, password: String): BillyApiClient.AuthResponse {
        return apiClient.register(username, email, password)
    }
}