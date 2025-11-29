package com.billyapp.shared.core

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter

/**
 * Global Logging Configuration.
 *
 * ARCHITECTURE NOTE:
 * We use Kermit for cross-platform logging.
 * This entry point allows the host application (Android/iOS) to configure
 * the logging level based on the build type (Debug/Release).
 */
object BillyLog {
    /**
     * Initializes the logging subsystem.
     * Must be called at Application Startup.
     *
     * @param isDebug If true, enables verbose logging (Severity.Debug).
     *                If false, restricts to Warnings and Errors only.
     */
    fun init(isDebug: Boolean) {
        val severity = if (isDebug) Severity.Debug else Severity.Warn

        // Use platform-specific log writer (Logcat on Android, OSLog on iOS)
        Logger.setLogWriters(platformLogWriter())
        Logger.setMinSeverity(severity)

        if (isDebug) {
            Logger.withTag("BillySDK").d { "🚀 BillySDK Initialized in DEBUG mode" }
        }
    }
}

/**
 * Ktor Logger Adapter.
 * Bridges Ktor's internal logging to our Kermit infrastructure.
 */
class KtorKermitLogger : io.ktor.client.plugins.logging.Logger {
    override fun log(message: String) {
        Logger.withTag("HTTP").d { message }
    }
}
