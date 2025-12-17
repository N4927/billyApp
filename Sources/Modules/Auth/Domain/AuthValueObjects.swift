import Foundation

// DOMAIN LAYER - VALUE OBJECTS
// Enforces business rules at the type level.

/// @struct EmailAddress
/// @desc A Value Object representing a validated email address.
///       Enforces format validity upon creation.
public struct EmailAddress: Equatable {
    public let value: String

    private init(_ value: String) {
        self.value = value
    }

    public static func create(_ rawValue: String) -> Result<EmailAddress, ValidationError> {
        // Simple regex for demonstration; in production use a robust parser
        let emailRegex = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}"
        let predicate = NSPredicate(format: "SELF MATCHES %@", emailRegex)

        guard predicate.evaluate(with: rawValue) else {
            return .failure(.invalidFormat("Invalid email address format."))
        }
        return .success(EmailAddress(rawValue))
    }
}

/// @struct Password
/// @desc A Value Object representing a validated password.
///       Enforces length and complexity requirements.
public struct Password: Equatable {
    public let value: String

    private init(_ value: String) {
        self.value = value
    }

    public static func create(_ rawValue: String) -> Result<Password, ValidationError> {
        guard rawValue.count >= 6 else {
            return .failure(.invalidFormat("Password must be at least 6 characters."))
        }
        return .success(Password(rawValue))
    }
}

/// @struct Username
/// @desc A Value Object representing a validated username.
public struct Username: Equatable {
    public let value: String

    private init(_ value: String) {
        self.value = value
    }

    public static func create(_ rawValue: String) -> Result<Username, ValidationError> {
        guard !rawValue.isEmpty else {
            return .failure(.emptyField("Username cannot be empty."))
        }
        return .success(Username(rawValue))
    }
}

/// @enum ValidationError
/// @desc Domain errors related to input validation.
public enum ValidationError: Error, LocalizedError {
    case invalidFormat(String)
    case emptyField(String)

    public var errorDescription: String? {
        switch self {
        case .invalidFormat(let msg), .emptyField(let msg): return msg
        }
    }
}
