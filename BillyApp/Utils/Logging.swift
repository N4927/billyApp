import Foundation
import os

enum BLELog {
    static let subsystem = "com.acme.billyapp"

    // Abilita log verbosi SOLO in Debug o se abiliti la chiave runtime.
    static var verbose: Bool {
        #if DEBUG
            return true
        #else
            return UserDefaults.standard.bool(forKey: "BLEVerboseLogging")
        #endif
    }

    static func logger(_ category: String) -> Logger {
        Logger(subsystem: subsystem, category: category)
    }
}

extension Data {
    var hex: String { map { String(format: "%02X", $0) }.joined() }
}
