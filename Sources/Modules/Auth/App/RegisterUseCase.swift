import Foundation

// APP LAYER - USE CASE
// Orchestrates the flow of data between the Domain and Infrastructure.

/// @desc Use Case for User Registration.
/// @responsibility Orchestrates the registration process.
public struct RegisterUseCase {
    private let repository: AuthRepository

    public init(repository: AuthRepository) {
        self.repository = repository
    }

    /// @desc Executes the registration logic.
    /// @param input The validated registration input.
    /// @throws Error if the operation fails.
    public func execute(input: RegisterInput) async throws {
        // Here we could add application-level logic (logging, analytics, etc.)
        // For now, it delegates to the repository.
        try await repository.register(input: input)
    }
}
