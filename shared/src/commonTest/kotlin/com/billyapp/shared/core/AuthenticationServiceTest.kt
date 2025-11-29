package com.billyapp.shared.core

import com.billyapp.shared.domain.model.AppError
import com.billyapp.shared.domain.model.BatchResponse
import com.billyapp.shared.domain.model.ResolveResponse
import com.billyapp.shared.domain.repository.NetworkDataSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit Tests for AuthenticationService.
 *
 * ARCHITECTURE NOTE:
 * This service is currently a thin Facade over the NetworkDataSource.
 * These tests verify that the delegation works correctly and that exceptions
 * are propagated to the UI layer (where they should be handled).
 */
class AuthenticationServiceTest {
    // --- FAKE IMPLEMENTATION ---

    /**
     * Fake NetworkDataSource for Auth testing.
     * Captures calls to login/register and allows simulating success/failure scenarios.
     */
    class FakeNetworkDataSource : NetworkDataSource {
        var loginCalled = false
        var registerCalled = false
        var shouldThrow = false

        // Stub response to return on success
        var authResponseStub = NetworkDataSource.AuthResponse("access", "refresh", 1, "user")

        override suspend fun login(
            email: String,
            password: String,
        ): Result<NetworkDataSource.AuthResponse, AppError> {
            loginCalled = true
            if (shouldThrow) return Result.Failure(AppError.Network.Unauthorized)
            return Result.Success(authResponseStub)
        }

        override suspend fun register(
            username: String,
            email: String,
            password: String,
        ): Result<NetworkDataSource.AuthResponse, AppError> {
            registerCalled = true
            if (shouldThrow) return Result.Failure(AppError.Business.ValidationFailed("Duplicate"))
            return Result.Success(authResponseStub)
        }

        // Irrelevant for Auth tests
        override suspend fun downloadBatch(): Result<BatchResponse, AppError> = throw NotImplementedError()

        override suspend fun resolveContact(bidHex: String): Result<ResolveResponse, AppError> = throw NotImplementedError()
    }

    // --- TESTS ---

    /**
     * Verifies that the login method correctly delegates to the underlying data source
     * and returns the expected AuthResponse.
     */
    @Test
    fun login_should_delegate_to_datasource() =
        runTest {
            val fakeSource = FakeNetworkDataSource()
            val service = AuthenticationService(fakeSource)

            val result = service.login("test@email.com", "pass")

            assertEquals(true, fakeSource.loginCalled)
            assertTrue(result is Result.Success)
            assertEquals("access", result.data.accessToken)
            assertEquals("user", result.data.username)
        }

    /**
     * Verifies that exceptions from the network layer (e.g., 401 Unauthorized)
     * are propagated up to the caller, allowing the UI to show appropriate error messages.
     */
    @Test
    fun login_should_propagate_failure() =
        runTest {
            val fakeSource = FakeNetworkDataSource()
            fakeSource.shouldThrow = true
            val service = AuthenticationService(fakeSource)

            val result = service.login("test", "pass")
            assertTrue(result is Result.Failure)
            assertTrue(result.error is AppError.Network.Unauthorized)
        }

    /**
     * Verifies that the register method correctly delegates to the underlying data source
     * and returns the expected AuthResponse (auto-login behavior).
     */
    @Test
    fun register_should_delegate_to_datasource() =
        runTest {
            val fakeSource = FakeNetworkDataSource()
            // Setup specific stub response
            fakeSource.authResponseStub = NetworkDataSource.AuthResponse("new_acc", "new_ref", 99, "new_user")

            val service = AuthenticationService(fakeSource)

            val result = service.register("new_user", "mail", "pass")

            assertEquals(true, fakeSource.registerCalled)
            assertTrue(result is Result.Success)
            assertEquals("new_user", result.data.username)
        }
}
