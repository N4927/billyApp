import Foundation

/// @desc UI Model for a User in the Proximity List.
/// Optimized for display in the SwiftUI List.
struct UserUIModel: Equatable {
    /// The display name of the user.
    let name: String

    /// Formatted string representing when the user was last seen (e.g., "2 min ago").
    let lastSeenText: String
}
