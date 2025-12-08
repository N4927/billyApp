package com.example.billyapp.kmm

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.billyapp.shared.cache.BillyDatabase
import com.billyapp.shared.core.BillyCore
import com.billyapp.shared.data.network.BillyApiClient
import com.billyapp.shared.data.repository.ResolvedRepositoryImpl
import com.billyapp.shared.data.repository.SecureRepositoryImpl
import com.billyapp.shared.domain.repository.NetworkDataSource
import com.billyapp.shared.domain.repository.ResolvedRepository
import com.billyapp.shared.domain.repository.SecureRepository
import com.billyapp.shared.domain.repository.TokenStorage
import com.billyapp.shared.domain.model.ResolvedUser
import kotlinx.coroutines.flow.StateFlow


/**
 * Punto unico in cui inizializziamo tutto ciò che serve al Core KMM.
 *
 * Espone una singleton [core] che userai da BleService / ViewModel:
 *   KmmEnvironment.core.getCurrentBid()
 *   KmmEnvironment.core.ingestPacket(...)
 *   KmmEnvironment.core.ensureAdvertisingBatch()
 *   KmmEnvironment.core.syncQueue()
 */
object KmmEnvironment {

    @Volatile
    private var coreInternal: BillyCore? = null

    @Volatile
    private var resolvedRepoInternal: ResolvedRepository? = null


    private const val API_BASE_URL = "https://104.236.127.137.nip.io/api/"

    val core: BillyCore
        get() = coreInternal
            ?: error("KmmEnvironment non inizializzato. Chiama KmmEnvironment.init(context) da Application.onCreate().")

    val resolvedUsersFlow: StateFlow<List<ResolvedUser>>
        get() = resolvedRepoInternal?.activeSet
            ?: error("KmmEnvironment non inizializzato o ResolvedRepository mancante.")


    /**
     * Va chiamato una sola volta (tipicamente in Application.onCreate()).
     */
    fun init(appContext: Context) {
        if (coreInternal != null) return

        synchronized(this) {
            if (coreInternal != null) return

            val context = appContext.applicationContext

            // --- 1) Database SQLDelight (persistenza sicura) ---
            val driver = AndroidSqliteDriver(
                schema = BillyDatabase.Schema,
                context = context,
                name = "billy.db"
            )
            val database = BillyDatabase(driver)
            val secureRepo: SecureRepository = SecureRepositoryImpl(database)

            // --- 2) Repository in-memory per lo stato UI (radar utenti) ---
            val resolvedRepo: ResolvedRepository = ResolvedRepositoryImpl()

            // --- 3) Token storage (Access/Refresh) ---
            val tokenStorage: TokenStorage = AndroidTokenStorage(context)

            // --- 4) Client HTTP verso il backend Billy ---
            val network: NetworkDataSource =
                BillyApiClient(
                    baseUrl = API_BASE_URL,
                    tokenStorage = tokenStorage
                    // engine = null  -> Ktor sceglie il motore corretto per Android
                )

            resolvedRepoInternal = resolvedRepo

            // --- 5) Core KMM vero e proprio ---
            coreInternal =
                BillyCore(
                    secureRepo = secureRepo,
                    resolvedRepo = resolvedRepo,
                    apiClient = network
                    // ioDispatcher e clock usano i default definiti nel KMM
                )
        }
    }
}
