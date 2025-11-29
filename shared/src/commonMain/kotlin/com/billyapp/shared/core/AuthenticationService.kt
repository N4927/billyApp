package com.billyapp.shared.core

import com.billyapp.shared.domain.model.AppError
import com.billyapp.shared.domain.repository.NetworkDataSource

/**
 * Authentication Domain Service.
 *
 * Represents the "Identity" Vertical Slice of the application.
 * This service acts as the primary entry point for all user authentication flows,
 * abstracting the underlying network complexity from the UI/ViewModel layer.
 *
 * ARCHITECTURE NOTE:
 * While this service currently acts as a Facade over the [NetworkDataSource],
 * it serves as the designated place for future business logic such as:
 * - Input validation (e.g., password strength rules)
 * - Analytics tracking for sign-up funnels
 * - Local session management orchestration
 */
class AuthenticationService(
    private val apiClient: NetworkDataSource,
) {
    // Exposes Response DTOs for the UI.
    // In a purer architecture, we would map these DTOs to Domain Models,
    // but for a lean mobile SDK, using network DTOs is acceptable (Pragmatism over Dogma).

    /**
     * Authenticates an existing user with the backend.
     *
     * Delegates the network request to the API client and returns the session tokens.
     *
     * @param email The user's email address.
     * @param password The user's plain-text password.
     * @return [Result] containing [NetworkDataSource.AuthResponse] or [AppError].
     */
    suspend fun login(
        email: String,
        password: String,
    ): Result<NetworkDataSource.AuthResponse, AppError> {
        return apiClient.login(email, password)
    }

    /**
     * Registers a new user account.
     *
     * Creates a new identity on the backend and automatically logs the user in
     * upon successful creation.
     *
     * @param username The desired display name for the user.
     * @param email The user's email address.
     * @param password The user's plain-text password.
     * @return [Result] containing [NetworkDataSource.AuthResponse] or [AppError].
     */
    suspend fun register(
        username: String,
        email: String,
        password: String,
    ): Result<NetworkDataSource.AuthResponse, AppError> {
        return apiClient.register(username, email, password)
    }
}
