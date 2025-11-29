package com.billyapp.shared.domain.repository

import com.billyapp.shared.core.Result
import com.billyapp.shared.domain.model.AppError
import com.billyapp.shared.domain.model.BatchResponse
import com.billyapp.shared.domain.model.ResolveResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Network Contract (Port).
 *
 * Defines the capabilities required from the network layer by the Domain Layer.
 * This interface follows the Dependency Inversion Principle (DIP):
 * High-level modules (Core) depend on this abstraction, not on the low-level Ktor implementation.
 */
interface NetworkDataSource {
    // --- Proximity Feature ---

    /**
     * Downloads a batch of cryptographic keys for future time slots.
     * @return [Result] containing [BatchResponse] or [AppError].
     */
    suspend fun downloadBatch(): Result<BatchResponse, AppError>

    /**
     * Resolves an anonymous BID to a user identity.
     * @param bidHex The 32-char hex string of the discovered ID.
     * @return [Result] containing [ResolveResponse] or [AppError].
     */
    suspend fun resolveContact(bidHex: String): Result<ResolveResponse, AppError>

    // --- Identity Feature ---

    /**
     * Authenticates a user and retrieves session tokens.
     * @return [Result] containing [AuthResponse] or [AppError].
     */
    suspend fun login(
        email: String,
        password: String,
    ): Result<AuthResponse, AppError>

    /**
     * Registers a new user and automatically logs them in.
     * @return [Result] containing [AuthResponse] or [AppError].
     */
    suspend fun register(
        username: String,
        email: String,
        password: String,
    ): Result<AuthResponse, AppError>

    // --- Shared DTOs ---
    // Defined here so both Implementation and Consumer can use it without coupling.

    /**
     * Standard Authentication Response.
     * Contains the JWT pair and basic user info.
     */
    @Serializable
    data class AuthResponse(
        @SerialName("access") val accessToken: String,
        @SerialName("refresh") val refreshToken: String,
        @SerialName("user_id") val userId: Int,
        @SerialName("username") val username: String,
    )
}
