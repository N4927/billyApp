package com.billyapp.shared.core

import com.billyapp.shared.domain.model.BatchResponse
import com.billyapp.shared.domain.model.ResolveResponse
import com.billyapp.shared.domain.repository.NetworkDataSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit Tests for AuthenticationService.
 */
class AuthenticationServiceTest {
    // --- FAKE IMPLEMENTATION ---

    class FakeNetworkDataSource : NetworkDataSource {
        var loginCalled = false
        var registerCalled = false
        var shouldThrow = false

        // [FIX] Use NetworkDataSource.AuthResponse
        var authResponseStub = NetworkDataSource.AuthResponse("access", "refresh", 1, "user")

        override suspend fun login(
            email: String,
            password: String,
        ): NetworkDataSource.AuthResponse {
            loginCalled = true
            if (shouldThrow) throw Exception("Auth Failed")
            return authResponseStub
        }

        override suspend fun register(
            username: String,
            email: String,
            password: String,
        ): NetworkDataSource.AuthResponse {
            registerCalled = true
            if (shouldThrow) throw Exception("Register Failed")
            return authResponseStub
        }

        // Irrelevant for Auth tests
        override suspend fun downloadBatch(): BatchResponse = throw NotImplementedError()

        override suspend fun resolveContact(bidHex: String): ResolveResponse = throw NotImplementedError()
    }

    // --- TESTS ---

    @Test
    fun login_should_delegate_to_datasource() =
        runTest {
            val fakeSource = FakeNetworkDataSource()
            val service = AuthenticationService(fakeSource)

            val result = service.login("test@email.com", "pass")

            assertEquals(true, fakeSource.loginCalled)
            assertEquals("access", result.accessToken)
            assertEquals("user", result.username)
        }

    @Test
    fun login_should_propagate_exception() =
        runTest {
            val fakeSource = FakeNetworkDataSource()
            fakeSource.shouldThrow = true
            val service = AuthenticationService(fakeSource)

            assertFailsWith<Exception> {
                service.login("test", "pass")
            }
        }

    @Test
    fun register_should_delegate_to_datasource() =
        runTest {
            val fakeSource = FakeNetworkDataSource()
            // Setup specific stub response
            fakeSource.authResponseStub = NetworkDataSource.AuthResponse("new_acc", "new_ref", 99, "new_user")

            val service = AuthenticationService(fakeSource)

            val result = service.register("new_user", "mail", "pass")

            assertEquals(true, fakeSource.registerCalled)
            assertEquals("new_user", result.username)
        }
}
