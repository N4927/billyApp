import Foundation

// APP LAYER - USE CASE
// Orchestrates the flow of data between the Domain and Infrastructure.

/// @desc Use Case for User Login.
/// @responsibility Orchestrates the login process.
public struct LoginUseCase {
    private let repository: AuthRepository

    public init(repository: AuthRepository) {
        self.repository = repository
    }

    /// @desc Executes the login logic.
    /// @param input The validated login input.
    /// @throws Error if the operation fails.
    public func execute(input: LoginInput) async throws {
        // Here we could add application-level logic (logging, analytics, etc.)
        // For now, it delegates to the repository.
        try await repository.login(input: input)
    }
}
