import Combine
import Foundation

/// @desc Defines the precise states of the User Session.
/// @invariant Only one state can exist at a time.
enum SessionState: Equatable {
    case startup
    case unauthenticated
    case authenticated
}

/// @desc Manages the global user session lifecycle using a Finite State Machine.
/// @architecture Domain Service (Application Layer)
@MainActor
final class SessionManager: ObservableObject {

    // MARK: - State

    /// The Source of Truth for the app's navigation structure.
    @Published private(set) var state: SessionState = .startup

    // MARK: - Dependencies

    private let storage: SessionStorage

    // MARK: - Initialization

    init(storage: SessionStorage) {
        self.storage = storage
    }

    // MARK: - API

    /**
     * @desc Bootstraps the session by verifying persistence.
     * @note This must be called immediately upon App Launch.
     */
    func bootstrap() {
        guard state == .startup else { return }

        // Check for existence of valid tokens.
        // In a stricter implementation, we would validate the JWT signature here.
        if let token = storage.getAccessToken(), !token.isEmpty {
            self.state = .authenticated
        } else {
            self.state = .unauthenticated
        }
    }

    /**
     * @desc Transitions state to Authenticated upon successful login/registration.
     */
    func authenticate() {
        self.state = .authenticated
    }

    /**
     * @desc Destroys the session and transitions to Unauthenticated.
     */
    func logout() {
        storage.clearTokens()
        self.state = .unauthenticated
    }
}
