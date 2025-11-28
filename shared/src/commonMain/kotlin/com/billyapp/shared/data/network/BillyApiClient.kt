package com.billyapp.shared.data.network

import com.billyapp.shared.domain.model.BatchResponse
import com.billyapp.shared.domain.model.ResolveResponse
import com.billyapp.shared.domain.repository.NetworkDataSource
import com.billyapp.shared.domain.repository.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Smart Network Infrastructure Layer.
 *
 * Implements [NetworkDataSource] using Ktor Client.
 * Handles low-level HTTP concerns such as:
 * - Serialization/Deserialization (JSON)
 * - Authentication (Bearer Token Injection)
 * - Token Refresh Logic (Automatic Retry)
 * - Timeouts and Connection Configuration
 */
class BillyApiClient(
    private val baseUrl: String,
    private val tokenStorage: TokenStorage,
    private val engine: HttpClientEngine? = null,
) : NetworkDataSource {
    private companion object {
        // API Endpoints
        const val ENDPOINT_BATCHES = "proximity/batches/"
        const val ENDPOINT_RESOLVE = "proximity/resolve/"
        const val ENDPOINT_LOGIN = "auth/token/"
        const val ENDPOINT_REGISTER = "auth/register/"
        const val ENDPOINT_REFRESH = "auth/token/refresh/"

        // 15 seconds timeout - aggressive enough for mobile, lenient enough for bad networks
        const val TIMEOUT_MILLIS = 15_000L
    }

    // JSON Configuration: Lenient to prevent crashes on minor API changes
    private val jsonConfig =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        }

    /**
     * Factory method for creating Ktor HttpClient instances.
     *
     * @param configAuth If true, installs the Auth plugin for Bearer token handling.
     * @return A configured [HttpClient].
     */
    private fun createClient(configAuth: Boolean): HttpClient {
        val configBlock: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) { json(jsonConfig) }
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MILLIS
                connectTimeoutMillis = TIMEOUT_MILLIS
                socketTimeoutMillis = TIMEOUT_MILLIS
            }

            if (configAuth) {
                install(Auth) {
                    bearer {
                        // 1. Load Tokens
                        loadTokens {
                            val access = tokenStorage.getAccessToken()
                            val refresh = tokenStorage.getRefreshToken()
                            if (access != null && refresh != null) BearerTokens(access, refresh) else null
                        }
                        // 2. Refresh Logic
                        // Automatically triggered on 401 Unauthorized
                        refreshTokens {
                            val oldRefreshToken = this.oldTokens?.refreshToken ?: return@refreshTokens null
                            try {
                                // Call refresh endpoint using the PUBLIC client (to avoid recursion)
                                val refreshResponse: RefreshResponse =
                                    publicClient.post(ENDPOINT_REFRESH) {
                                        setBody(RefreshRequest(refresh = oldRefreshToken))
                                    }.body()

                                val newAccess = refreshResponse.access
                                val newRefresh = refreshResponse.refresh ?: oldRefreshToken

                                // Persist new tokens
                                tokenStorage.saveTokens(newAccess, newRefresh)
                                BearerTokens(newAccess, newRefresh)
                            } catch (e: Exception) {
                                // If refresh fails, clear tokens to force re-login
                                tokenStorage.clearTokens()
                                null
                            }
                        }
                    }
                }
            }
            defaultRequest {
                url(baseUrl)
                contentType(ContentType.Application.Json)
            }
        }
        return if (engine != null) HttpClient(engine, configBlock) else HttpClient(configBlock)
    }

    // Lazy initialization to save resources until the first network call
    private val publicClient: HttpClient by lazy { createClient(configAuth = false) }
    private val client: HttpClient by lazy { createClient(configAuth = true) }

    // --- Private Request DTOs ---
    // Internal data structures for request bodies, hidden from the domain layer.
    @Serializable private data class ResolveRequest(val b_id: String)

    @Serializable private data class LoginRequest(val email: String, val password: String)

    @Serializable private data class RegisterRequest(val username: String, val email: String, val password: String)

    @Serializable private data class RefreshRequest(val refresh: String)

    @Serializable private data class RefreshResponse(val access: String, val refresh: String? = null)

    // NOTE: AuthResponse definition removed from here. We use NetworkDataSource.AuthResponse.

    // --- Implementation ---

    /**
     * Downloads a batch of cryptographic keys for advertising.
     * Authenticated request.
     */
    override suspend fun downloadBatch(): BatchResponse {
        return client.get(ENDPOINT_BATCHES).body()
    }

    /**
     * Resolves a discovered BID to a user profile.
     * Authenticated request.
     */
    override suspend fun resolveContact(bidHex: String): ResolveResponse {
        return client.post(ENDPOINT_RESOLVE) {
            setBody(ResolveRequest(b_id = bidHex))
        }.body()
    }

    /**
     * Performs user login.
     * Public request (No Auth header).
     */
    override suspend fun login(
        email: String,
        password: String,
    ): NetworkDataSource.AuthResponse {
        // Ktor deserializes directly into the Interface DTO
        val response: NetworkDataSource.AuthResponse =
            publicClient.post(ENDPOINT_LOGIN) {
                setBody(LoginRequest(email = email, password = password))
            }.body()

        // Side Effect: Save tokens immediately upon success
        tokenStorage.saveTokens(response.accessToken, response.refreshToken)
        return response
    }

    /**
     * Registers a new user.
     * Public request (No Auth header).
     */
    override suspend fun register(
        username: String,
        email: String,
        password: String,
    ): NetworkDataSource.AuthResponse {
        val response: NetworkDataSource.AuthResponse =
            publicClient.post(ENDPOINT_REGISTER) {
                setBody(RegisterRequest(username = username, email = email, password = password))
            }.body()

        // Side Effect: Save tokens immediately upon success
        tokenStorage.saveTokens(response.accessToken, response.refreshToken)
        return response
    }
}
