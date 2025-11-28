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
}
