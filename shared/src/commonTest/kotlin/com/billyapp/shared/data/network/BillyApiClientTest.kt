package com.billyapp.shared.data.network

import com.billyapp.shared.domain.repository.TokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Integration Tests for the Network Layer.
 *
 * TEST STRATEGY:
 * - Uses Ktor's [MockEngine] to simulate HTTP responses without hitting the real network.
 * - Verifies:
 *   1. Request Construction: Correct URLs, Headers (Auth), and Body.
 *   2. Response Parsing: JSON deserialization into Domain Objects.
 *   3. Auth Logic: Token injection, Refresh flow (401 handling), and Storage updates.
 *   4. Error Handling: Graceful degradation on network failures.
 */
class BillyApiClientTest {
    // Fake implementation of TokenStorage for testing
    private val fakeStorage =
        object : TokenStorage {
            override fun getAccessToken() = "fake_access_token"

            override fun getRefreshToken() = "fake_refresh_token"

            override fun saveTokens(
                access: String,
                refresh: String,
            ) {}

            override fun clearTokens() {}
        }

    /**
     * Verifies that the client correctly parses a valid JSON response into a [BatchResponse] object.
     * Also checks that the Authorization header is correctly injected.
     */
    @Test
    fun downloadBatch_should_parse_response_correctly() =
        runTest {
            // 1. ARRANGE: Setup Mock Engine
            // We assert that the client is hitting the correct endpoint with correct headers
            val mockEngine =
                MockEngine { request ->
                    assertEquals("https://api.test.com/proximity/batches/", request.url.toString())
                    assertEquals("Bearer fake_access_token", request.headers["Authorization"])

                    // Simulate a valid JSON response from the backend
                    respond(
                        content =
                            ByteReadChannel(
                                """
                                {
                                    "start_slot": 1000,
                                    "slot_duration": 600,
                                    "b_ids": ["aabbcc11223344556677889900aabbcc", "112233445566778899aabbccddeeff00"]
                                }
                                """.trimIndent(),
                            ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            // 2. Init Client with Mock Engine (Dependency Injection)
            val client =
                BillyApiClient(
                    baseUrl = "https://api.test.com/",
                    tokenStorage = fakeStorage,
                    // Inject the mock!
                    engine = mockEngine,
                )

            // 3. ACT: Execute the call
            val response = client.downloadBatch()

            // 4. ASSERT: Verify Domain Object structure
            assertEquals(1000L, response.startSlot)
            assertEquals(2, response.bIds.size)
            assertEquals("aabbcc11223344556677889900aabbcc", response.bIds[0])
        }

    /**
     * Verifies the Login Flow.
     * Ensures that upon successful login, the returned tokens are immediately persisted
     * to the secure storage.
     */
    @Test
    fun login_should_save_tokens_to_storage() =
        runTest {
            // 1. ARRANGE
            var savedAccess: String? = null
            var savedRefresh: String? = null

            // Spy Storage to verify side effects
            val spyStorage =
                object : TokenStorage {
                    override fun getAccessToken() = null

                    override fun getRefreshToken() = null

                    override fun saveTokens(
                        access: String,
                        refresh: String,
                    ) {
                        savedAccess = access
                        savedRefresh = refresh
                    }

                    override fun clearTokens() {}
                }

            val mockEngine =
                MockEngine { request ->
                    assertEquals("https://api.test.com/auth/token/", request.url.toString())

                    respond(
                        content =
                            ByteReadChannel(
                                """
                                {
                                    "access": "new_access_token",
                                    "refresh": "new_refresh_token",
                                    "user_id": 123,
                                    "username": "billy_user"
                                }
                                """.trimIndent(),
                            ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client =
                BillyApiClient(
                    baseUrl = "https://api.test.com/",
                    tokenStorage = spyStorage,
                    engine = mockEngine,
                )

            // 2. ACT
            val response = client.login("test@email.com", "password")

            // 3. ASSERT
            assertEquals("billy_user", response.username)
            // Verify the side-effect: Tokens MUST be saved to storage
            assertEquals("new_access_token", savedAccess)
            assertEquals("new_refresh_token", savedRefresh)
        }

    /**
     * Verifies that the Resolve Contact request is constructed correctly.
     */
    @Test
    fun resolveContact_should_send_correct_request() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    assertEquals("https://api.test.com/proximity/resolve/", request.url.toString())
                    assertEquals("Bearer fake_access_token", request.headers["Authorization"])

                    respond(
                        content = ByteReadChannel("""{"display_name": "John Doe"}"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client =
                BillyApiClient(
                    baseUrl = "https://api.test.com/",
                    tokenStorage = fakeStorage,
                    engine = mockEngine,
                )

            val response = client.resolveContact("aabbcc11223344556677889900aabbcc")
            assertEquals("John Doe", response.displayName)
        }

    /**
     * Verifies the Registration Flow.
     * Similar to login, successful registration should auto-login the user by saving tokens.
     */
    @Test
    fun register_should_save_tokens_on_success() =
        runTest {
            var savedAccess: String? = null
            var savedRefresh: String? = null
            val spyStorage =
                object : TokenStorage {
                    override fun getAccessToken() = null

                    override fun getRefreshToken() = null

                    override fun saveTokens(
                        access: String,
                        refresh: String,
                    ) {
                        savedAccess = access
                        savedRefresh = refresh
                    }

                    override fun clearTokens() {}
                }

            val mockEngine =
                MockEngine { request ->
                    assertEquals("https://api.test.com/auth/register/", request.url.toString())
                    respond(
                        content =
                            ByteReadChannel(
                                """
                                {
                                    "access": "reg_access",
                                    "refresh": "reg_refresh",
                                    "user_id": 456,
                                    "username": "new_user"
                                }
                                """.trimIndent(),
                            ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client = BillyApiClient("https://api.test.com/", spyStorage, mockEngine)
            val response = client.register("new_user", "new@email.com", "pass")

            assertEquals("new_user", response.username)
            assertEquals("reg_access", savedAccess)
            assertEquals("reg_refresh", savedRefresh)
        }

    /**
     * Verifies the Automatic Token Refresh Logic.
     * 1. Client sends request with old token.
     * 2. Server responds with 401 Unauthorized.
     * 3. Client catches 401, calls refresh endpoint.
     * 4. Client retries original request with NEW token.
     */
    @Test
    fun request_should_refresh_token_on_401() =
        runTest {
            var savedAccess: String? = "initial_access"
            var savedRefresh: String? = "initial_refresh"

            val spyStorage =
                object : TokenStorage {
                    override fun getAccessToken() = savedAccess

                    override fun getRefreshToken() = savedRefresh

                    override fun saveTokens(
                        access: String,
                        refresh: String,
                    ) {
                        savedAccess = access
                        savedRefresh = refresh
                    }

                    override fun clearTokens() {
                        savedAccess = null
                        savedRefresh = null
                    }
                }

            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/proximity/batches/" -> {
                            if (request.headers["Authorization"] == "Bearer initial_access") {
                                respond("", HttpStatusCode.Unauthorized)
                            } else {
                                assertEquals("Bearer new_access_token", request.headers["Authorization"])
                                respond(
                                    content = ByteReadChannel("""{"start_slot": 2000, "slot_duration": 600, "b_ids": []}"""),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }
                        }
                        "/auth/token/refresh/" -> {
                            respond(
                                content = ByteReadChannel("""{"access": "new_access_token", "refresh": "new_refresh_token"}"""),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                        else -> error("Unhandled ${request.url}")
                    }
                }

            val client = BillyApiClient("https://api.test.com/", spyStorage, mockEngine)

            val response = client.downloadBatch()

            assertEquals(2000L, response.startSlot)
            assertEquals("new_access_token", savedAccess)
            assertEquals("new_refresh_token", savedRefresh)
        }

    /**
     * Verifies that if the Refresh Token itself is expired (or invalid),
     * the client clears the local storage to force a fresh login.
     */
    @Test
    fun refresh_failure_should_clear_tokens() =
        runTest {
            var savedAccess: String? = "initial_access"
            var savedRefresh: String? = "initial_refresh"

            val spyStorage =
                object : TokenStorage {
                    override fun getAccessToken() = savedAccess

                    override fun getRefreshToken() = savedRefresh

                    override fun saveTokens(
                        access: String,
                        refresh: String,
                    ) {
                        savedAccess = access
                        savedRefresh = refresh
                    }

                    override fun clearTokens() {
                        savedAccess = null
                        savedRefresh = null
                    }
                }

            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/proximity/batches/" -> {
                            respond("", HttpStatusCode.Unauthorized)
                        }
                        "/auth/token/refresh/" -> {
                            respond("", HttpStatusCode.BadRequest)
                        }
                        else -> error("Unhandled ${request.url}")
                    }
                }

            val client = BillyApiClient("https://api.test.com/", spyStorage, mockEngine)

            try {
                client.downloadBatch()
            } catch (e: Exception) {
                // Expected
            }

            assertNull(savedAccess)
            assertNull(savedRefresh)
        }

    /**
     * Verifies partial token updates.
     * Some backends only return a new Access Token on refresh, keeping the old Refresh Token valid.
     * The client must handle this by preserving the old Refresh Token.
     */
    @Test
    fun refresh_should_reuse_old_refresh_token_if_server_omits_it() =
        runTest {
            var savedAccess: String? = "initial_access"
            var savedRefresh: String? = "initial_refresh"

            val spyStorage =
                object : TokenStorage {
                    override fun getAccessToken() = savedAccess

                    override fun getRefreshToken() = savedRefresh

                    override fun saveTokens(
                        access: String,
                        refresh: String,
                    ) {
                        savedAccess = access
                        savedRefresh = refresh
                    }

                    override fun clearTokens() {}
                }

            val mockEngine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/proximity/batches/" -> {
                            if (request.headers["Authorization"] == "Bearer initial_access") {
                                respond("", HttpStatusCode.Unauthorized)
                            } else {
                                respond(
                                    content = ByteReadChannel("""{"start_slot": 1000, "slot_duration": 600, "b_ids": []}"""),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                )
                            }
                        }
                        "/auth/token/refresh/" -> {
                            // Server returns ONLY access token
                            respond(
                                content = ByteReadChannel("""{"access": "new_access_token"}"""),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                        else -> error("Unhandled ${request.url}")
                    }
                }

            val client = BillyApiClient("https://api.test.com/", spyStorage, mockEngine)
            client.downloadBatch()

            assertEquals("new_access_token", savedAccess)
            assertEquals("initial_refresh", savedRefresh) // Should remain unchanged
        }

    /**
     * Verifies that unauthenticated requests (no tokens in storage) do not send the Authorization header.
     */
    @Test
    fun request_should_not_send_auth_header_if_tokens_missing() =
        runTest {
            val emptyStorage =
                object : TokenStorage {
                    override fun getAccessToken() = null

                    override fun getRefreshToken() = null

                    override fun saveTokens(
                        access: String,
                        refresh: String,
                    ) {}

                    override fun clearTokens() {}
                }

            val mockEngine =
                MockEngine { request ->
                    assertNull(request.headers["Authorization"])
                    respond(
                        content = ByteReadChannel("""{"start_slot": 1000, "slot_duration": 600, "b_ids": []}"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val client = BillyApiClient("https://api.test.com/", emptyStorage, mockEngine)
            client.downloadBatch()
        }
}
