import Foundation

// APP LAYER - USE CASE

/// @desc Use Case for Observing Proximity Users.
@MainActor
public struct ObserveProximityUseCase {
    private let engine: ProximityEngine

    public init(engine: ProximityEngine) {
        self.engine = engine
    }

    public func execute() -> AsyncStream<[User]> {
        return engine.observeUsers()
    }
}
