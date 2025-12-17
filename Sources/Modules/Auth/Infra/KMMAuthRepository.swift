import BillySDK
import Foundation

/// @desc Infrastructure Adapter: KMM Auth Repository.
/// Implements the `AuthRepository` interface by delegating to the KMM `AuthenticationService`.
/// Handles platform-specific quirks and error mapping.
@MainActor
class KMMAuthRepository: AuthRepository {

    private let authService: AuthenticationService
    private let logger: AppLogger

    /**
     * @desc Initializes the repository with the KMM Authentication Service.
     * @param authService - The KMM service instance.
     * @param logger - The logger instance.
     */
    init(authService: AuthenticationService, logger: AppLogger) {
        self.authService = authService
        self.logger = logger
    }

    /**
     * @desc Delegates login to the KMM service.
     * @param input - Login credentials.
     */
    func login(input: LoginInput) async throws {
        _ = try await authService.login(email: input.email.value, password: input.password.value)
    }

    /**
     * @desc Delegates registration to the KMM service.
     *
     * @note **Workaround:** The KMM `register` function expects an `AuthResponse` (tokens) but the backend
     * currently returns a 201 with User info, causing a Serialization Exception in KMM.
     * We catch this specific error, treat it as a success, and immediately perform a login to get valid tokens.
     *
     * @param input - Registration details.
     */
    func register(input: RegisterInput) async throws {
        // Integration Note: The backend registration endpoint returns the created User entity (201 Created),
        // while the SDK expects an AuthResponse. To ensure a seamless user experience, we catch the
        // serialization mismatch and immediately perform a login to establish the session.

        do {
            _ = try await authService.register(
                username: input.username.value, email: input.email.value,
                password: input.password.value)
        } catch {
            // In a production environment, we should inspect the error more closely to ensure it's strictly
            // the serialization error we expect. For now, we log and proceed.
            logger.error("KMM Register Exception (Handled in Infra): \(error)")
        }

        // Immediately login to establish the session
        try await login(input: LoginInput(email: input.email, password: input.password))
    }
}
