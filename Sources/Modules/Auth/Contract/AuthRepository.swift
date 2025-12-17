import Foundation

// CONTRACT LAYER - REPOSITORY INTERFACE
// Defines the contract for Authentication operations.
// This belongs to the Domain Layer (Core) but is exposed via Contract.

/// @desc The Domain Interface for Authentication operations.
/// Abstracts the underlying implementation (KMM, API, Mock) from the application logic.
public protocol AuthRepository {

    /**
     * @desc Authenticates a user with the provided credentials.
     * @param input - The login credentials (email, password).
     * @throws Error - If authentication fails (network, invalid credentials, etc.).
     */
    func login(input: LoginInput) async throws

    /**
     * @desc Registers a new user with the provided details.
     * @param input - The registration details (username, email, password).
     * @throws Error - If registration fails (user exists, network, etc.).
     */
    func register(input: RegisterInput) async throws
}
