/*
 * ==============================================================================
 *  PROJECT SETTINGS & REPOSITORY CONFIGURATION
 * ==============================================================================
 *  This file defines the project structure and centralized repository management.
 *  It serves as the entry point for the Gradle build initialization.
 */

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // Enforce centralized repository declaration for all subprojects.
    // This prevents individual modules from defining conflicting repositories.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

// --- PROJECT IDENTITY ---
// Defines the root project name, visible in IDEs, CI/CD pipelines, and build reports.
rootProject.name = "billyapp-kmm"

// --- MODULE STRUCTURE ---
// This is a standalone Kotlin Multiplatform (KMP) SDK project.
// We exclusively include the ':shared' module, which produces the cross-platform
// artifacts (Android AAR / iOS XCFramework) for consumption by client applications.
include(":shared")