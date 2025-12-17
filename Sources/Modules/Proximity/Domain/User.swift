import Foundation

// DOMAIN ENTITY
// Represents a user detected by the proximity engine.
// Pure Swift, no KMM dependencies.

/// @desc Domain Entity representing a User detected via Proximity.
/// Contains the core data required by the business logic, decoupled from UI or Infra concerns.
public struct User: Equatable {
    /// The user's display name.
    /// @rule Value Object used instead of primitive String.
    public let name: DisplayName

    /// The timestamp when this user was last detected.
    public let lastSeen: Date

    public init(name: DisplayName, lastSeen: Date) {
        self.name = name
        self.lastSeen = lastSeen
    }
}
