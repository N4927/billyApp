import Foundation

/// @desc Defines the strict configuration schema for the application.
/// @responsibility Holds validated environment variables and build settings.
/// @pattern Immutable Configuration Object
public struct AppConfig {
    public let appName: String
    public let bundleIdentifier: String
    public let environment: AppEnvironment

    // Add other config values here (e.g., API Base URL, Feature Flags)

    public enum AppEnvironment: String {
        case debug
        case release
        case staging
    }

    /// @desc Validates and loads configuration from the Bundle.
    /// @throws ConfigError if critical values are missing.
    public static func load() throws -> AppConfig {
        let bundle = Bundle.main

        guard let name = bundle.object(forInfoDictionaryKey: "CFBundleName") as? String else {
            throw ConfigError.missingKey("CFBundleName")
        }

        guard let bundleId = bundle.bundleIdentifier else {
            throw ConfigError.missingKey("CFBundleIdentifier")
        }

        // In a real app, this might come from a specific Info.plist key or build setting
        #if DEBUG
            let env = AppEnvironment.debug
        #else
            let env = AppEnvironment.release
        #endif

        return AppConfig(
            appName: name,
            bundleIdentifier: bundleId,
            environment: env
        )
    }
}

public enum ConfigError: Error, LocalizedError {
    case missingKey(String)

    public var errorDescription: String? {
        switch self {
        case .missingKey(let key):
            return "CRITICAL: Missing required configuration key: \(key)"
        }
    }
}
