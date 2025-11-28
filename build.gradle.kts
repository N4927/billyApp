plugins {
    // Android Plugins (Required for androidApp and shared modules)
    // Applied false here to allow subprojects to apply them with specific versions.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false

    // Kotlin Multiplatform
    // The core plugin for KMP development.
    alias(libs.plugins.kotlinMultiplatform) apply false

    // Additional Tools (Database & JSON)
    // SQLDelight for type-safe DB access and Serialization for JSON parsing.
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kotlinSerialization) apply false

    // Code coverage
    // Kover provides test coverage reports for Kotlin projects.
    alias(libs.plugins.kover) apply false
}

// Optional but recommended configuration to clean the build
// Registers a root-level 'clean' task to wipe the build directory.
tasks.register("clean", Delete::class) { delete(rootProject.buildDir) }
