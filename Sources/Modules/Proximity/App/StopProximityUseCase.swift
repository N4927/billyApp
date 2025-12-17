import Foundation

// APP LAYER - USE CASE

/// @desc Use Case for Stopping Proximity Detection.
@MainActor
public struct StopProximityUseCase {
    private let engine: ProximityEngine

    public init(engine: ProximityEngine) {
        self.engine = engine
    }

    public func execute() {
        engine.stop()
    }
}
