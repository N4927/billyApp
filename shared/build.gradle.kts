import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
}

group = "com.billyapp.sdk"
version = "1.0.0"

kotlin {
    // --- ANDROID TARGET CONFIGURATION ---
    // Configures the JVM target for Android compatibility.
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    // --- IOS TARGET CONFIGURATION (XCFramework) ---
    // This configuration generates a universal XCFramework that can be consumed by Xcode.
    // It bundles the shared logic for all iOS architectures (Device + Simulator).
    val xcf = XCFramework("BillySDK")

    listOf(
        // Simulator (Intel)
        iosX64(),
        // Device (Apple Silicon)
        iosArm64(),
        // Simulator (Apple Silicon)
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "BillySDK"

            // Static Framework: Reduces app launch time and avoids dynamic linking issues.
            // Essential for stability in complex iOS dependency graphs.
            isStatic = true

            // Add this binary to the XCFramework bundle
            xcf.add(this)
        }
    }

    // --- SOURCE SETS & DEPENDENCIES ---
    // Defines the dependency graph for each platform layer.
    sourceSets {
        // Common Main: The core business logic shared across all platforms.
        // Depends only on pure Kotlin libraries or multiplatform abstractions.
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        // Android Main: Platform-specific implementations for Android.
        // Injects the OkHttp engine and Android SQL driver.
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
        }

        // iOS Main: Platform-specific implementations for iOS.
        // Injects the Darwin (NSURLSession) engine and Native SQL driver.
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }

        // Common Test: Unit tests shared across platforms.
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test) // For testing coroutines
            implementation(libs.ktor.client.mock) // For mocking Ktor client in tests
        }

        // Dependencies for Android Unit Tests
        // Required to run SQLite tests on the JVM (Robolectric/JUnit).
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
    }
}

android {
    namespace = "com.billyapp.shared"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

// --- SQLDELIGHT CONFIGURATION ---
// Generates type-safe Kotlin APIs from .sq files.
sqldelight {
    databases {
        create("BillyDatabase") {
            packageName.set("com.billyapp.shared.cache")
        }
    }
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt") // Ignora file generati

        ktlint("1.0.1") // Versione motore Lint

        // Regole Corporate
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.0.1")
    }
}
