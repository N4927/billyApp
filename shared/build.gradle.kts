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

kover {
    reports {
        filters {
            excludes {
                // Exclude generated SQLDelight code and Dagger/Hilt if present
                classes("com.billyapp.shared.cache.*")
                classes("com.billyapp.shared.BillyDatabase*")
            }
        }

        verify {
            rule {
                minBound(80) // Enforce 80% coverage
            }
        }
    }
}

// --- LOCAL BADGE GENERATION ---
// Generates the coverage badge locally so it can be committed.
// This avoids the need for GitHub Secrets or Tokens.
tasks.register("generateCoverageBadge") {
    group = "documentation"
    description = "Generates the coverage badge SVG based on Kover reports"
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
