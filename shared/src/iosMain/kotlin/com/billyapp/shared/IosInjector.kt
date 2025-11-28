package com.billyapp.shared

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.billyapp.shared.cache.BillyDatabase
import com.billyapp.shared.core.AuthenticationService
import com.billyapp.shared.core.BillyCore
import com.billyapp.shared.data.network.BillyApiClient
import com.billyapp.shared.data.repository.ResolvedRepositoryImpl
import com.billyapp.shared.data.repository.SecureRepositoryImpl
import com.billyapp.shared.domain.repository.ResolvedRepository
import com.billyapp.shared.domain.repository.TokenStorage
import kotlin.native.concurrent.ThreadLocal

/**
 * BillySDK Entry Point for iOS.
 * * ARCHITECTURAL PATTERN: Composition Root with Strict Initialization.
 * * DESIGN DECISION:
 * We do not use a "Dummy" storage. We require the iOS app to inject
 * the Keychain wrapper immediately. If the API is accessed before initialization,
 * the app will crash intentionally (Fail Fast) to alert the developer.
 */
object BillySDK {

    // ============================================================================================
    // 1. STATE & INITIALIZATION
    // ============================================================================================

    private var tokenStorage: TokenStorage? = null
    private var isInitialized: Boolean = false

    /**
     * MUST be called in `App.init()` or `AppDelegate.didFinishLaunching`.
     * Injects the platform-specific dependencies (Keychain) into the Shared Module.
     *
     * @param storage The Swift implementation of TokenStorage (wrapping Keychain).
     */
    fun initialize(storage: TokenStorage) {
        if (isInitialized) {
            println("⚠️ BillySDK Warning: Already initialized.")
            return
        }
        this.tokenStorage = storage
        this.isInitialized = true
        
        // Force database creation on init to catch SQL errors early
        try {
            val db = database
            println("✅ BillySDK Database initialized successfully.")
        } catch(e:Exception) {
            println("❌ BillySDK Database Init Failed: ${e.message}")
        }
    }

    private fun requireStorage(): TokenStorage {
        return tokenStorage ?: throw IllegalStateException(
            "FATAL ERROR: BillySDK.initialize(storage) was not called. " +
            "You must inject the Keychain implementation before accessing the SDK."
        )
    }

    // ============================================================================================
    // 2. INFRASTRUCTURE (Lazy Loaded but Safe)
    // ============================================================================================

    private val database: BillyDatabase by lazy {
        val driver = NativeSqliteDriver(BillyDatabase.Schema, "billy.db")
        BillyDatabase(driver)
    }

    private val api: BillyApiClient by lazy {
        BillyApiClient(
            baseUrl = "https://104.236.127.137.nip.io/api/v1/",
            // Dependency Injection via Lambda:
            tokenStorage = object : TokenStorage {
                override fun getAccessToken() = requireStorage().getAccessToken()
                override fun getRefreshToken() = requireStorage().getRefreshToken()
                override fun saveTokens(access: String, refresh: String) = requireStorage().saveTokens(access, refresh)
                override fun clearTokens() = requireStorage().clearTokens()
            }
        )
    }

    // ============================================================================================
    // 3. FACADES (Vertical Slices)
    // ============================================================================================

    private val secureRepo by lazy { SecureRepositoryImpl(database) }
    private val resolvedRepo by lazy { ResolvedRepositoryImpl() }

    val core: BillyCore by lazy {
        BillyCore(secureRepo, resolvedRepo, api)
    }

    val auth: AuthenticationService by lazy {
        AuthenticationService(api)
    }

    fun getUIState(): ResolvedRepository = resolvedRepo
}