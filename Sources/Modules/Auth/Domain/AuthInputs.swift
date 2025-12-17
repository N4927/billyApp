import Foundation

// DOMAIN LAYER - INPUTS
// Defines the data structures required for domain operations.

/// @struct LoginInput
/// @desc Data Transfer Object (DTO) for User Login.
///       Encapsulates the credentials required to authenticate a user.
///       Immutable and strictly typed.
public struct LoginInput {
    /// The user's email address.
    public let email: EmailAddress
    /// The user's password.
    public let password: Password

    public init(email: EmailAddress, password: Password) {
        self.email = email
        self.password = password
    }
}

/// @struct RegisterInput
/// @desc Data Transfer Object (DTO) for User Registration.
///       Encapsulates the data required to create a new user account.
///       Immutable and strictly typed.
public struct RegisterInput {
    /// The desired username.
    public let username: Username
    /// The user's email address.
    public let email: EmailAddress
    /// The user's chosen password.
    public let password: Password

    public init(username: Username, email: EmailAddress, password: Password) {
        self.username = username
        self.email = email
        self.password = password
    }
}
