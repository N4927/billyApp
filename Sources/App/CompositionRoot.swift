import BillySDK
import Foundation
import SwiftUI

/// @desc The Dependency Injection (DI) Container.
/// @responsibility Assembles the Object Graph.
/// @pattern Composition Root
@MainActor
final class CompositionRoot: ObservableObject {

    // MARK: - Core Services

    let sessionManager: SessionManager
    let config: AppConfig

    // MARK: - Private Dependencies (Held for graph retention)

    private let authService: AuthenticationService
    private let billyCore: BillyCore
    private let resolvedRepo: ResolvedRepository
    private let storage: SessionStorage

    // MARK: - Adapters

    private let authRepository: AuthRepository
    private let proximityEngine: ProximityEngine
    private let logger: AppLogger

    // MARK: - Use Cases (Application Layer)

    private let loginUseCase: LoginUseCase
    private let registerUseCase: RegisterUseCase

    private let startProximityUseCase: StartProximityUseCase
    private let stopProximityUseCase: StopProximityUseCase
    private let observeProximityUseCase: ObserveProximityUseCase

    // MARK: - Initialization

    init() {
        // 0. Configuration (Fail Fast)
        do {
            self.config = try AppConfig.load()
        } catch {
            fatalError("Failed to load application configuration: \(error.localizedDescription)")
        }

        // 1. Infrastructure (Leaf Nodes)
        let keychain = KeychainStorage()
        self.storage = keychain
        self.logger = SystemLogger(subsystem: "com.billyapp.ios", category: "App")

        // 2. KMM Bridge (Core)
        BillySDK.shared.initialize(storage: keychain)
        self.authService = BillySDK.shared.auth
        self.billyCore = BillySDK.shared.core
        self.resolvedRepo = BillySDK.shared.getUIState()

        // 3. Adapters (Interface Implementations)
        let authLogger = SystemLogger(subsystem: "com.billyapp.ios", category: "Auth")
        self.authRepository = KMMAuthRepository(authService: authService, logger: authLogger)

        // Inject specialized logger for BLE subsystem
        let bleLogger = SystemLogger(subsystem: "com.billyapp.ios", category: "BLE")
        self.proximityEngine = BleProximityEngine(
            core: billyCore, resolvedRepo: resolvedRepo, logger: bleLogger)

        // 4. Application Services (Use Cases)
        self.loginUseCase = LoginUseCase(repository: authRepository)
        self.registerUseCase = RegisterUseCase(repository: authRepository)

        self.startProximityUseCase = StartProximityUseCase(engine: proximityEngine)
        self.stopProximityUseCase = StopProximityUseCase(engine: proximityEngine)
        self.observeProximityUseCase = ObserveProximityUseCase(engine: proximityEngine)

        self.sessionManager = SessionManager(storage: storage)
    }

    // MARK: - ViewModel Factories

    /**
     * @desc Factory for AuthViewModel.
     * @injected Dependencies are explicitly passed, ensuring testability.
     */
    func makeAuthViewModel() -> AuthViewModel {
        return AuthViewModel(
            sessionManager: sessionManager,
            loginUseCase: loginUseCase,
            registerUseCase: registerUseCase
        )
    }

    func makeHomeViewModel() -> HomeViewModel {
        let uiLogger = SystemLogger(subsystem: "com.billyapp.ios", category: "UI")
        return HomeViewModel(
            startUseCase: startProximityUseCase,
            stopUseCase: stopProximityUseCase,
            observeUseCase: observeProximityUseCase,
            logger: uiLogger
        )
    }

    func makeDebugView() -> some View {
        let viewModel = DebugViewModel(
            core: billyCore,
            uiState: resolvedRepo,
            storage: storage,
            startProximityUseCase: startProximityUseCase,
            stopProximityUseCase: stopProximityUseCase
        )
        return DebugView(viewModel: viewModel)
    }
}
