import Foundation

// APP LAYER - USE CASE

/// @desc Use Case for Starting Proximity Detection.
@MainActor
public struct StartProximityUseCase {
    private let engine: ProximityEngine

    public init(engine: ProximityEngine) {
        self.engine = engine
    }

    public func execute() {
        engine.start()
    }
}
