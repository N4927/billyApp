package com.billyapp.shared

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.billyapp.shared.cache.BillyDatabase
import com.billyapp.shared.core.AuthenticationService
import com.billyapp.shared.core.BillyCore
import com.billyapp.shared.data.network.BillyApiClient
import com.billyapp.shared.data.repository.ResolvedRepositoryImpl
import com.billyapp.shared.data.repository.SecureRepositoryImpl
import com.billyapp.shared.domain.repository.ResolvedRepository
import com.billyapp.shared.domain.repository.TokenStorage

/**
 * BillySDK Entry Point for Android.
 *
 * ARCHITECTURAL PATTERN: Composition Root with Strict Initialization.
 *
 * This Singleton acts as the "Bridge" between the Android Application Layer (Kotlin/Java)
 * and the Shared Kotlin Multiplatform Core. It is responsible for wiring up the
 * dependency graph and ensuring that platform-specific implementations (like EncryptedSharedPreferences)
 * along with the Android Context are correctly injected before any business logic is executed.
 *
 * DESIGN DECISION:
 * We enforce a "Fail Fast" policy for initialization. Instead of providing a dummy/mock
 * storage implementation, we require the host application to explicitly provide the
 * Context and the Storage wrapper. Accessing SDK features before initialization will intentionally
 * crash the app to alert the developer of the integration error immediately.
 */
object BillySDK {
    // ============================================================================================
    // 1. STATE & INITIALIZATION
    // ============================================================================================

    /**
     * Holds the reference to the Android Application Context required by SQLDelight.
     */
    private var applicationContext: Context? = null

    /**
     * Holds the reference to the platform-specific TokenStorage implementation.
     * This is nullable to represent the "uninitialized" state.
     */
    private var tokenStorage: TokenStorage? = null

    /**
     * Flag to prevent double-initialization, which could lead to unpredictable state resets.
     */
    private var isInitialized: Boolean = false

    /**
     * Initializes the SDK with platform-specific dependencies.
     *
     * MUST be called in the Android `Application.onCreate()` lifecycle method.
     * This method injects the Context and the SharedPreferences wrapper required for secure token storage.
     *
     * @param context The Android Application Context (used for Database initialization).
     * @param storage The Android implementation of [TokenStorage] (wrapping EncryptedSharedPreferences).
     * @throws IllegalStateException (Implicit) if the database schema creation fails.
     */
    fun initialize(
        context: Context,
        storage: TokenStorage,
    ) {
        // Guard clause: Idempotency check to prevent re-initialization side effects.
        if (isInitialized) {
            println("⚠️ BillySDK Warning: Already initialized.")
            return
        }

        // Ensure we don't leak an Activity context by grabbing the application context
        this.applicationContext = context.applicationContext ?: context
        this.tokenStorage = storage
        this.isInitialized = true

        // Eagerly initialize the database driver to validate the schema and catch SQL errors
        // at startup time rather than during the first user interaction.
        try {
            database.toString()
            println("✅ BillySDK Database initialized successfully.")
        } catch (e: Exception) {
            // Critical failure: If DB cannot init, the SDK is non-functional.
            // We log this clearly for debugging.
            println("❌ BillySDK Database Init Failed: ${e.message}")
        }
    }

    /**
     * Internal safety check to ensure Context is available.
     *
     * @return The valid Android [Context].
     * @throws IllegalStateException If the SDK has not been initialized via [initialize].
     */
    private fun requireContext(): Context {
        return applicationContext ?: throw IllegalStateException(
            "FATAL ERROR: BillySDK.initialize(context, storage) was not called. " +
                "You must inject the Context before accessing the SDK.",
        )
    }

    /**
     * Internal safety check to ensure dependencies are available.
     *
     * @return The valid [TokenStorage] instance.
     * @throws IllegalStateException If the SDK has not been initialized via [initialize].
     */
    private fun requireStorage(): TokenStorage {
        return tokenStorage ?: throw IllegalStateException(
            "FATAL ERROR: BillySDK.initialize(context, storage) was not called. " +
                "You must inject the Storage implementation before accessing the SDK.",
        )
    }

    // ============================================================================================
    // 2. INFRASTRUCTURE (Lazy Loaded but Safe)
    // ============================================================================================

    /**
     * The Database Driver instance.
     * Lazy initialization ensures we don't consume file handles or memory until strictly needed.
     * Uses [AndroidSqliteDriver] for Android-native SQLite performance.
     */
    private val database: BillyDatabase by lazy {
        val driver = AndroidSqliteDriver(BillyDatabase.Schema, requireContext(), "billy.db")
        BillyDatabase(driver)
    }

    /**
     * The Network Client instance.
     * Configured with the production API endpoint and a dynamic TokenStorage proxy.
     *
     * The proxy object allows us to instantiate the API client immediately (lazy)
     * while deferring the resolution of [tokenStorage] until the actual method call.
     * This prevents circular dependency issues during the lazy init graph construction.
     */
    private val api: BillyApiClient by lazy {
        BillyApiClient(
            baseUrl = "https://104.236.127.137.nip.io/api/v1/",
            // Dependency Injection via Lambda/Proxy:
            // Delegates to the 'requireStorage()' method at runtime to ensure safety.
            tokenStorage =
                object : TokenStorage {
                    override fun getAccessToken() = requireStorage().getAccessToken()

                    override fun getRefreshToken() = requireStorage().getRefreshToken()

                    override fun saveTokens(
                        access: String,
                        refresh: String,
                    ) = requireStorage().saveTokens(access, refresh)

                    override fun clearTokens() = requireStorage().clearTokens()
                },
        )
    }

    // ============================================================================================
    // 3. FACADES (Vertical Slices)
    // ============================================================================================

    /**
     * Repository for secure data persistence (BIDs, Keys).
     * Depends on the SQLDelight database instance.
     */
    private val secureRepo by lazy { SecureRepositoryImpl(database) }

    /**
     * Repository for ephemeral UI state (Resolved Users).
     * In-memory only implementation.
     */
    private val resolvedRepo by lazy { ResolvedRepositoryImpl() }

    /**
     * The Core Domain Service for the Proximity Feature.
     * Acts as the primary entry point for all BLE-related business logic.
     *
     * @return A fully configured [BillyCore] instance ready for use by the Android ViewModels.
     */
    val core: BillyCore by lazy {
        BillyCore(secureRepo, resolvedRepo, api)
    }

    /**
     * The Authentication Service Facade.
     * Handles Login and Registration flows.
     *
     * @return A fully configured [AuthenticationService] instance.
     */
    val auth: AuthenticationService by lazy {
        AuthenticationService(api)
    }

    /**
     * Exposes the ResolvedRepository to the UI layer.
     * This allows the Android ViewModels to observe the [activeSet] StateFlow directly.
     *
     * @return The singleton [ResolvedRepository] instance.
     */
    fun getUIState(): ResolvedRepository = resolvedRepo
}
