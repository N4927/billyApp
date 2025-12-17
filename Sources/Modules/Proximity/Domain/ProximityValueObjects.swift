import Foundation

// DOMAIN LAYER - VALUE OBJECTS
// Enforces "Parse, Don't Validate" and Type Safety.

/// @desc Represents a validated Display Name for a user.
/// @pattern Value Object
public struct DisplayName: Equatable, Hashable {
    public let value: String

    private init(_ value: String) {
        self.value = value
    }

    /// @desc Factory method to create a DisplayName.
    /// Enforces validation rules (e.g., not empty).
    /// @returns Result<DisplayName, ProximityValidationError>
    public static func create(_ rawValue: String) -> Result<DisplayName, ProximityValidationError> {
        let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !trimmed.isEmpty else {
            return .failure(.emptyName)
        }

        // Add more rules here (e.g., max length, allowed characters)

        return .success(DisplayName(trimmed))
    }
}

/// @desc Domain-specific validation errors.
public enum ProximityValidationError: Error {
    case emptyName
}
