import Combine
import Foundation

// PRESENTATION LAYER - STATE MACHINE
// Enforces strict state management and eliminates invalid UI states.

/// @desc Represents the finite states of the Home Screen.
/// @pattern State Machine
enum HomeViewState: Equatable {
    case idle
    case scanning(users: [UserUIModel])
    case error(message: String)
}

/// @desc ViewModel for the Home Screen.
/// Manages the list of active users and controls the Proximity Engine.
@MainActor
class HomeViewModel: ObservableObject {

    /// The current state of the view.
    @Published private(set) var state: HomeViewState = .idle

    private let startUseCase: StartProximityUseCase
    private let stopUseCase: StopProximityUseCase
    private let observeUseCase: ObserveProximityUseCase
    private let logger: AppLogger

    /**
     * @desc Initializes the ViewModel and starts observing the engine.
     * @param startUseCase - Use Case to start scanning.
     * @param stopUseCase - Use Case to stop scanning.
     * @param observeUseCase - Use Case to observe users.
     * @param logger - Structured logger.
     */
    init(
        startUseCase: StartProximityUseCase,
        stopUseCase: StopProximityUseCase,
        observeUseCase: ObserveProximityUseCase,
        logger: AppLogger
    ) {
        self.startUseCase = startUseCase
        self.stopUseCase = stopUseCase
        self.observeUseCase = observeUseCase
        self.logger = logger

        // Start observing immediately, but state transitions happen via commands
        startObserving()
    }

    deinit {
        let stop = stopUseCase
        Task { @MainActor in
            stop.execute()
        }
    }

    // MARK: - Commands

    /**
     * @desc Starts the Proximity Engine.
     * Transitions state to .scanning.
     */
    func startScanning() {
        logger.info("Proximity Engine start command received")
        startUseCase.execute()

        // If we were idle, we transition to scanning with empty list (or keep current list if we had one)
        if case .idle = state {
            state = .scanning(users: [])
        }
    }

    /**
     * @desc Stops the Proximity Engine.
     * Transitions state to .idle.
     */
    func stopScanning() {
        logger.info("Proximity Engine stop command received")
        stopUseCase.execute()
        state = .idle
    }

    // MARK: - Internal Logic

    /**
     * @desc Subscribes to the Proximity Engine's user stream.
     * Updates the state whenever the engine emits a new set of users.
     */
    private func startObserving() {
        Task {
            for await users in observeUseCase.execute() {
                let userDTOs = users.map { self.mapToDTO($0) }

                // Only update state if we are supposed to be scanning
                // (Though engine should technically not emit if stopped, defensive coding here)
                if case .scanning = state {
                    self.state = .scanning(users: userDTOs)
                }
                // We don't have direct access to engine.isRunning anymore,
                // so we rely on the state machine.
            }
        }
    }

    /**
     * @desc Maps a Domain User entity to a UI Model.
     * @param user - The domain user.
     * @returns A formatted UserUIModel.
     */
    private func mapToDTO(_ user: User) -> UserUIModel {
        return UserUIModel(
            name: user.name.value,  // Unwrap Value Object
            lastSeenText: formatLastSeen(user.lastSeen)
        )
    }

    /**
     * @desc Formats a Date into a relative string (e.g., "5 min ago").
     * @param date - The date to format.
     * @returns A localized relative date string.
     */
    private func formatLastSeen(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
