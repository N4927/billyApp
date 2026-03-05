package com.billyapp.shared.data.network

import co.touchlab.kermit.Logger
import com.billyapp.shared.core.KtorKermitLogger
import com.billyapp.shared.core.Result
import com.billyapp.shared.domain.model.AppError
import com.billyapp.shared.domain.model.BatchResponse
import com.billyapp.shared.domain.model.ResolveResponse
import com.billyapp.shared.domain.repository.NetworkDataSource
import com.billyapp.shared.domain.repository.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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
 * - Error Mapping (HTTP -> Domain Error)
 */
class BillyApiClient(
    private val baseUrl: String,
    private val tokenStorage: TokenStorage,
    private val engine: HttpClientEngine? = null,
    private val enableLogging: Boolean = false,
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

            if (enableLogging) {
                install(Logging) {
                    logger = KtorKermitLogger()
                    level = LogLevel.ALL
                }
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

    // --- Implementation ---

    /**
     * Downloads a batch of cryptographic keys for advertising.
     * Authenticated request.
     */
    override suspend fun downloadBatch(): Result<BatchResponse, AppError> =
        safeRequest {
            client.get(ENDPOINT_BATCHES).body()
        }

    /**
     * Resolves a discovered BID to a user profile.
     * Authenticated request.
     */
    override suspend fun resolveContact(bidHex: String): Result<ResolveResponse, AppError> =
        safeRequest {
            client.post(ENDPOINT_RESOLVE) {
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
    ): Result<NetworkDataSource.AuthResponse, AppError> =
        safeRequest {
            // Ktor deserializes directly into the Interface DTO
            val response: NetworkDataSource.AuthResponse =
                publicClient.post(ENDPOINT_LOGIN) {
                    setBody(LoginRequest(email = email, password = password))
                }.body()

            // Side Effect: Save tokens immediately upon success
            tokenStorage.saveTokens(response.accessToken, response.refreshToken)
            response
        }

    /**
     * Registers a new user.
     * Public request (No Auth header).
     *
     * NOTE: The backend Register endpoint does NOT return tokens.
     * We must chain a Login call immediately after registration to fulfill the contract.
     */
    override suspend fun register(
        username: String,
        email: String,
        password: String,
    ): Result<NetworkDataSource.AuthResponse, AppError> {
        val registerResult =
            safeRequest<Unit> {
                publicClient.post(ENDPOINT_REGISTER) {
                    setBody(RegisterRequest(username, email, password))
                }
                Unit // ← Esplicito: ignora la risposta, ritorna Unit
            }

        if (registerResult is Result.Failure) {
            return Result.Failure(registerResult.error)
        }

        return login(email, password)
    }

    /**
     * Wraps Ktor calls in a Result Monad, mapping exceptions to Domain Errors.
     */
    private suspend inline fun <reified T> safeRequest(block: () -> T): Result<T, AppError> {
        return try {
            Result.Success(block())
        } catch (e: ClientRequestException) {
            // 4xx Errors
            Logger.withTag("BillyAPI").w { "Client Error: ${e.response.status.value} - ${e.message}" }
            when (e.response.status.value) {
                401 -> Result.Failure(AppError.Network.Unauthorized)
                404 -> Result.Failure(AppError.Business.UserNotFound)
                else -> Result.Failure(AppError.Network.ServerError(e.response.status.value, e.message))
            }
        } catch (e: ServerResponseException) {
            // 5xx Errors
            Logger.withTag("BillyAPI").e { "Server Error: ${e.response.status.value} - ${e.message}" }
            Result.Failure(AppError.Network.ServerError(e.response.status.value, e.message))
        } catch (e: SerializationException) {
            Logger.withTag("BillyAPI").e { "Serialization Error: ${e.message}" }
            Result.Failure(AppError.Network.Serialization(e.message))
        } catch (e: JsonConvertException) {
            Logger.withTag("BillyAPI").e { "JSON Error: ${e.message}" }
            Result.Failure(AppError.Network.Serialization(e.message))
        } catch (e: Exception) {
            // Network/Unknown
            Logger.withTag("BillyAPI").e(e) { "Unknown Network Error" }
            Result.Failure(AppError.Network.NoInternet)
        }
    }
}
