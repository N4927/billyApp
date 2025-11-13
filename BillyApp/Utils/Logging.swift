import Foundation
import os

struct AppLogger {
    static func make(category: String) -> Logger {
        Logger(subsystem: "com.acme.billyapp", category: category)
    }
}
