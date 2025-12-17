import Foundation

// SHARED KERNEL - LOGGER INTERFACE
// Adheres to DIP (Dependency Inversion Principle).
// Domain layers depend on this interface, not on os.Logger or print.

/// @desc Defines the contract for structured logging across the application.
/// @pattern Interface/Port
public protocol AppLogger {
    /**
     * @desc Logs a debug message. Used for development details.
     * @param message The message to log.
     * @param context Optional key-value pairs for structured context.
     */
    func debug(_ message: String, context: [String: String]?)

    /**
     * @desc Logs an informational message. Used for happy-path events.
     * @param message The message to log.
     * @param context Optional key-value pairs for structured context.
     */
    func info(_ message: String, context: [String: String]?)

    /**
     * @desc Logs a warning. Something unexpected happened but flow continues.
     * @param message The message to log.
     * @param context Optional key-value pairs for structured context.
     */
    func warning(_ message: String, context: [String: String]?)

    /**
     * @desc Logs an error. An operation failed.
     * @param message The message to log.
     * @param error The actual error object (optional).
     * @param context Optional key-value pairs for structured context.
     */
    func error(_ message: String, error: Error?, context: [String: String]?)
}

// Default extension to make context optional
extension AppLogger {
    public func debug(_ message: String) { self.debug(message, context: nil) }
    public func info(_ message: String) { self.info(message, context: nil) }
    public func warning(_ message: String) { self.warning(message, context: nil) }
    public func error(_ message: String, error: Error? = nil) {
        self.error(message, error: error, context: nil)
    }
}
