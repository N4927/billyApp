import Foundation

// CONTRACT LAYER - PROXIMITY ENGINE INTERFACE
// Defines the contract for the Proximity Engine.
// This belongs to the Domain Layer (Core) but is exposed via Contract.

/// @desc The Domain Interface for the Proximity Engine.
/// Defines the contract for starting/stopping the engine and observing detected users.
@MainActor
public protocol ProximityEngine {

    /**
     * @desc Starts the proximity detection engine.
     * Should initiate scanning and advertising processes.
     */
    func start()

    /**
     * @desc Stops the proximity detection engine.
     * Should release resources and stop background tasks.
     */
    func stop()

    /// Indicates whether the engine is currently running.
    var isRunning: Bool { get }

    /**
     * @desc Returns an asynchronous stream of detected users.
     * @returns An `AsyncStream` that emits an array of `User` entities whenever the set of nearby users changes.
     */
    func observeUsers() -> AsyncStream<[User]>
}
