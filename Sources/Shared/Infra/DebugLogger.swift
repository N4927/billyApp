import Combine
import Foundation
import os.log

// MARK: - Domain Models

public enum LogLevel: String, Codable, CaseIterable {
    case debug = "DEBUG"
    case info = "INFO"
    case warning = "WARN"
    case error = "ERROR"

    var color: String {
        switch self {
        case .debug: return "gray"
        case .info: return "blue"
        case .warning: return "yellow"
        case .error: return "red"
        }
    }
}

public struct LogEntry: Identifiable, Codable {
    public let id: UUID
    public let timestamp: Date
    public let level: LogLevel
    public let subsystem: String
    public let category: String
    public let message: String
    public let error: String?
    public let context: [String: String]?
    public let thread: String

    public init(
        level: LogLevel,
        subsystem: String,
        category: String,
        message: String,
        error: Error? = nil,
        context: [String: String]? = nil
    ) {
        self.id = UUID()
        self.timestamp = Date()
        self.level = level
        self.subsystem = subsystem
        self.category = category
        self.message = message
        self.error = error?.localizedDescription
        self.context = context
        self.thread = Thread.isMainThread ? "main" : Thread.current.name ?? "bg"
    }
}

// MARK: - In-Memory Logger (Singleton)

/// @desc Centralized In-Memory Logger for the Debug Console.
/// Captures logs from all layers for runtime inspection.
/// @pattern Singleton
@MainActor
public final class DebugLogger: ObservableObject {
    public static let shared = DebugLogger()

    @Published public var logs: [LogEntry] = []
    private let maxLogs = 1000

    private init() {}

    public func log(_ entry: LogEntry) {
        // Insert at top
        logs.insert(entry, at: 0)

        // Prune
        if logs.count > maxLogs {
            logs.removeLast()
        }
    }

    public func clear() {
        logs.removeAll()
    }

    public func export() -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601

        do {
            let data = try encoder.encode(logs)
            return String(data: data, encoding: .utf8) ?? "[]"
        } catch {
            return "Error exporting logs: \(error.localizedDescription)"
        }
    }
}
