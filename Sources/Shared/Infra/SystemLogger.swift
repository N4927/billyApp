import Foundation
import os.log

// SHARED INFRASTRUCTURE - LOGGER IMPLEMENTATION
// Implements the Logger contract using Apple's Unified Logging System (OSLog).

/// @desc Concrete implementation of the Logger interface.
/// Adapts the abstract Logger calls to the `os.Logger` subsystem.
public struct SystemLogger: AppLogger {
    private let logger: os.Logger
    private let subsystem: String
    private let category: String

    /// @desc Initializes the SystemLogger.
    /// @param subsystem The subsystem identifier (e.g., "com.billyapp.ios").
    /// @param category The category for filtering (e.g., "Auth", "Proximity").
    public init(subsystem: String, category: String) {
        self.subsystem = subsystem
        self.category = category
        self.logger = os.Logger(subsystem: subsystem, category: category)
    }

    public func debug(_ message: String, context: [String: String]?) {
        let contextStr = formatContext(context)
        logger.debug("\(message, privacy: .public) \(contextStr, privacy: .public)")
        capture(level: .debug, message: message, context: context)
    }

    public func info(_ message: String, context: [String: String]?) {
        let contextStr = formatContext(context)
        logger.info("\(message, privacy: .public) \(contextStr, privacy: .public)")
        capture(level: .info, message: message, context: context)
    }

    public func warning(_ message: String, context: [String: String]?) {
        let contextStr = formatContext(context)
        logger.warning("\(message, privacy: .public) \(contextStr, privacy: .public)")
        capture(level: .warning, message: message, context: context)
    }

    public func error(_ message: String, error: Error?, context: [String: String]?) {
        let contextStr = formatContext(context)
        let errorStr = error.map { "Error: \($0.localizedDescription)" } ?? ""
        logger.error(
            "\(message, privacy: .public) \(errorStr, privacy: .public) \(contextStr, privacy: .public)"
        )
        capture(level: .error, message: message, error: error, context: context)
    }

    private func formatContext(_ context: [String: String]?) -> String {
        guard let context = context, !context.isEmpty else { return "" }
        let pairs = context.map { "\($0.key)=\($0.value)" }
        return "[\(pairs.joined(separator: ", "))]"
    }

    private func capture(
        level: LogLevel, message: String, error: Error? = nil, context: [String: String]? = nil
    ) {
        let entry = LogEntry(
            level: level,
            subsystem: subsystem,
            category: category,
            message: message,
            error: error,
            context: context
        )

        Task { @MainActor in
            DebugLogger.shared.log(entry)
        }
    }
}
