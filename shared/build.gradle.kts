/*
 * ==============================================================================
 *  SHARED MODULE CONFIGURATION (CORE SDK)
 * ==============================================================================
 *  This module contains the core business logic, data layer, and platform-specific
 *  implementations for Android and iOS. It produces the final SDK artifacts.
 */

import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import java.io.FileOutputStream
import java.net.URL

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
}

group = "com.billyapp.sdk"
version = "1.2.0"

kotlin {
    // --- ANDROID TARGET (JVM) ---
    // Configures the Android library target with Java 8 compatibility.
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    // --- IOS TARGET (NATIVE) ---
    // Configures the iOS targets to generate a universal XCFramework.
    // This artifact bundles architectures for both Simulators and Physical Devices.
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
            // Static Framework: Optimizes launch time and simplifies linking.
            isStatic = true
            xcf.add(this)
        }
    }

    // --- SOURCE SETS & DEPENDENCY GRAPH ---
    // Defines the hierarchical structure of the multiplatform project.
    sourceSets {
        // [COMMON] Core Business Logic
        // Pure Kotlin dependencies shared across all platforms.
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
            implementation(libs.kermit)
            implementation(libs.ktor.client.logging)
        }

        // [ANDROID] Platform Implementation
        // Android-specific drivers and engines (OkHttp, Android SQLite).
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
        }

        // [iOS] Platform Implementation
        // Native drivers and engines (Darwin/NSURLSession, Native SQLite).
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }

        // [TESTING] Shared Unit Tests
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        // [TESTING] Android Unit Tests
        // Requires SQLite driver for JVM (Robolectric/JUnit) execution.
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

// --- PERSISTENCE LAYER (SQLDelight) ---
// Configures the type-safe SQL generator.
sqldelight {
    databases {
        create("BillyDatabase") {
            packageName.set("com.billyapp.shared.cache")
        }
    }
}

// --- STATIC ANALYSIS (Spotless) ---
// Enforces strict coding standards and formatting rules.
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt") // Exclude generated files

        ktlint("1.0.1") // Linting Engine

        // Corporate Style Guidelines
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.0.1")
    }
}

// --- CODE COVERAGE (Kover) ---
// Configures coverage reporting and verification thresholds.
kover {
    reports {
        filters {
            excludes {
                // Exclude generated code (DB, DI) from coverage metrics.
                classes("com.billyapp.shared.cache.*")
                classes("com.billyapp.shared.BillyDatabase*")
            }
        }

        verify {
            rule {
                minBound(80) // Minimum acceptable coverage percentage.
            }
        }
    }
}

// --- DOCUMENTATION & METRICS ---
// Generates a coverage badge locally for README integration.
tasks.register("generateCoverageBadge") {
    group = "documentation"
    description = "Generates the coverage badge SVG based on Kover reports."
    dependsOn("koverXmlReport") // Ensure report exists

    val buildDir = layout.buildDirectory
    val rootDir = rootProject.layout.projectDirectory

    doLast {
        val reportFile = buildDir.file("reports/kover/report.xml").get().asFile
        if (!reportFile.exists()) {
            println("Kover report not found. Skipping badge generation.")
            return@doLast
        }

        val xml = reportFile.readText()
        // Parse the last INSTRUCTION counter (summary)
        val instructionCounterRegex = "<counter type=\"INSTRUCTION\" missed=\"(\\d+)\" covered=\"(\\d+)\"/>".toRegex()
        val match = instructionCounterRegex.findAll(xml).lastOrNull()

        if (match != null) {
            val missed = match.groupValues[1].toLong()
            val covered = match.groupValues[2].toLong()
            val total = missed + covered
            val percentage = (covered * 100) / total

            val color =
                when {
                    percentage >= 90 -> "brightgreen"
                    percentage >= 80 -> "green"
                    else -> "red"
                }

            // Download badge from shields.io
            val url = "https://img.shields.io/badge/Coverage-$percentage%25-$color?style=flat-square&logo=kotlin"
            val badgeFile = rootDir.file("coverage.svg").asFile

            try {
                val connection = URL(url).openConnection()
                connection.getInputStream().use { input ->
                    FileOutputStream(badgeFile).use { output ->
                        input.copyTo(output)
                    }
                }
                println("✅ Coverage badge updated: $percentage% -> ${badgeFile.absolutePath}")
            } catch (e: Exception) {
                println("⚠️ Failed to download badge: ${e.message}")
            }
        }
    }
}

// Hook badge generation into the check task
tasks.named("check") {
    finalizedBy("generateCoverageBadge")
}
