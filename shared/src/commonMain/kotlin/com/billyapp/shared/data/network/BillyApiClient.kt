package com.billyapp.shared.data.network

import com.billyapp.shared.domain.model.BatchResponse
import com.billyapp.shared.domain.model.ResolveResponse
import com.billyapp.shared.domain.repository.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Smart Network Infrastructure Layer.
 *
 * RESPONSIBILITIES:
 * 1. Raw HTTP communication with the BillyApp Backend.
 * 2. Automatic JWT Token Injection (Bearer).
 * 3. **Silent Token Refresh**: Intercepts 401s, refreshes the token, and retries the request.
 * 4. JSON Serialization/Deserialization.
 *
 * @property baseUrl The root API URL (e.g., https://api.billyapp.com/api/v1/).
 * @property tokenStorage Interface to the native secure storage (Keychain/Keystore) to read/write tokens.
 */
class BillyApiClient(
    private val baseUrl: String,
    private val tokenStorage: TokenStorage
) {
    private companion object {
        // --- Endpoints ---
        const val ENDPOINT_BATCHES = "proximity/batches/"
        const val ENDPOINT_RESOLVE = "proximity/resolve/"
        const val ENDPOINT_LOGIN = "auth/token/"
        const val ENDPOINT_REGISTER = "auth/register/"
        const val ENDPOINT_REFRESH = "auth/token/refresh/"

        // --- Configuration ---
        const val TIMEOUT_MILLIS = 15_000L // 15s timeout for mobile networks
    }

    /**
     * 1. PUBLIC CLIENT (Unauthenticated)
     * Used for Login, Register, and Refresh calls.
     * It does NOT have the Auth interceptor to prevent circular dependencies/loops during refresh.
     */
    private val publicClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MILLIS
            connectTimeoutMillis = TIMEOUT_MILLIS
            socketTimeoutMillis = TIMEOUT_MILLIS
        }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * 2. MAIN CLIENT (Authenticated)
     * Used for business logic calls (Proximity).
     * Automatically handles Bearer headers and 401 Retries.
     */
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }

        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MILLIS
            connectTimeoutMillis = TIMEOUT_MILLIS
        }

        // [CORPORATE FEATURE] Automatic Token Management
        install(Auth) {
            bearer {
                // A. Load tokens from native storage for every request
                loadTokens {
                    val access = tokenStorage.getAccessToken()
                    val refresh = tokenStorage.getRefreshToken()
                    if (access != null && refresh != null) {
                        BearerTokens(access, refresh)
                    } else {
                        null
                    }
                }

                // B. Auto-Refresh Logic (Triggered on 401 Unauthorized)
                refreshTokens {
                    val oldRefreshToken = this.oldTokens?.refreshToken ?: return@refreshTokens null

                    try {
                        // 1. Call Refresh Endpoint using the PUBLIC client
                        // (SimpleJWT standard: send 'refresh', get new 'access')
                        val refreshResponse: RefreshResponse = publicClient.post(ENDPOINT_REFRESH) {
                            setBody(RefreshRequest(refresh = oldRefreshToken))
                        }.body()

                        val newAccess = refreshResponse.access
                        // If backend rotates refresh tokens, use new one, otherwise keep old
                        val newRefresh = refreshResponse.refresh ?: oldRefreshToken

                        // 2. Persist new tokens to Native Storage immediately
                        tokenStorage.saveTokens(newAccess, newRefresh)

                        // 3. Return new tokens to Ktor to retry the failed request
                        BearerTokens(newAccess, newRefresh)
                    } catch (e: Exception) {
                        // Refresh failed (Token expired/revoked).
                        // Clear local storage so the UI knows to show the Login Screen.
                        tokenStorage.clearTokens()
                        null
                    }
                }
            }
        }

        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
    }

    // ============================================================================================
    // DTOs (Data Transfer Objects)
    // ============================================================================================

    // --- Private Request DTOs (Encapsulated) ---

    @Serializable
    private data class ResolveRequest(val b_id: String)

    @Serializable
    private data class LoginRequest(val email: String, val password: String)

    @Serializable
    private data class RegisterRequest(val username: String, val email: String, val password: String)

    @Serializable
    private data class RefreshRequest(val refresh: String)

    // --- Internal Response DTOs ---

    @Serializable
    private data class RefreshResponse(
        val access: String,
        val refresh: String? = null // Optional: depending on backend rotation policy
    )

    // --- Public Response DTOs (Exposed to Domain) ---

    @Serializable
    data class AuthResponse(
        @SerialName("access") val accessToken: String,
        @SerialName("refresh") val refreshToken: String,
        @SerialName("user_id") val userId: Int,
        @SerialName("username") val username: String
    )

    // ============================================================================================
    // BUSINESS LOGIC CALLS (Authenticated)
    // ============================================================================================

    suspend fun downloadBatch(): BatchResponse {
        return client.get(ENDPOINT_BATCHES).body()
    }

    suspend fun resolveContact(bidHex: String): ResolveResponse {
        return client.post(ENDPOINT_RESOLVE) {
            setBody(ResolveRequest(b_id = bidHex))
        }.body()
    }

    // ============================================================================================
    // AUTHENTICATION CALLS (Unauthenticated / Public)
    // ============================================================================================

    /**
     * Performs login and persists the tokens securely.
     */
    suspend fun login(email: String, password: String): AuthResponse {
        // Use publicClient to avoid Auth interceptor interference
        val response: AuthResponse = publicClient.post(ENDPOINT_LOGIN) {
            setBody(LoginRequest(email = email, password = password))
        }.body()

        // Side Effect: Save tokens to Keychain immediately upon success
        tokenStorage.saveTokens(response.accessToken, response.refreshToken)

        return response
    }

    /**
     * Registers a new user and persists the tokens securely.
     */
    suspend fun register(username: String, email: String, password: String): AuthResponse {
        val response: AuthResponse = publicClient.post(ENDPOINT_REGISTER) {
            setBody(RegisterRequest(username = username, email = email, password = password))
        }.body()

        // Side Effect: Save tokens to Keychain immediately upon success
        tokenStorage.saveTokens(response.accessToken, response.refreshToken)

        return response
    }
}