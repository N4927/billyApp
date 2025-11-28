/*
 * ==============================================================================
 *  ROOT BUILD CONFIGURATION
 * ==============================================================================
 *  This script configures the build environment for the entire workspace.
 *  It manages plugin versions, global tasks, and developer experience (DX) automation.
 */

import org.apache.tools.ant.filters.FixCrLfFilter

plugins {
    // --- ANDROID ECOSYSTEM ---
    // Plugins required for Android Application and Library modules.
    // 'apply false' ensures versions are managed here but applied only in subprojects.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false

    // --- KOTLIN MULTIPLATFORM ---
    // The core plugin enabling cross-platform development (JVM, Android, Native).
    alias(libs.plugins.kotlinMultiplatform) apply false

    // --- DATA & SERIALIZATION ---
    // SQLDelight: Type-safe database generation.
    // Serialization: JSON parsing and encoding.
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kotlinSerialization) apply false

    // --- QUALITY ASSURANCE ---
    // Kover: Code coverage reporting and verification.
    alias(libs.plugins.kover) apply false
}

// --- BUILD MAINTENANCE ---
// Registers a root-level 'clean' task to sanitize the build environment.
// Automatically triggers git hook installation to ensure compliance.
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
    dependsOn("installGitHooks")
}

// --- DEVOPS & DX AUTOMATION ---
// Enforces local quality gates by installing Git hooks.
// This ensures linting and tests run before code is pushed.
tasks.register("installGitHooks", Copy::class) {
    description = "Installs the pre-push git hook from scripts/pre-push.sh to enforce quality gates."
    group = "help"
    from(layout.projectDirectory.dir("scripts/pre-push.sh"))
    into(layout.projectDirectory.dir(".git/hooks"))
    rename("pre-push.sh", "pre-push")
    fileMode = 0b111101101 // 755 (rwxr-xr-x)
    // Force LF line endings for Windows compatibility (Git Bash requires LF)
    filter(FixCrLfFilter::class, "eol" to FixCrLfFilter.CrLf.newInstance("lf"))
}

// Hook installation into Gradle Sync for seamless onboarding.
tasks.named("prepareKotlinBuildScriptModel") { dependsOn("installGitHooks") }

// Enforce hook installation on every build execution across all projects.
allprojects {
    tasks.matching { it.name == "build" }.configureEach {
        dependsOn(rootProject.tasks.named("installGitHooks"))
    }
}

// --- RELEASE ENGINEERING ---
// Automates the semantic versioning release process.
// Creates and pushes a git tag matching the current shared module version.
tasks.register("createReleaseTag") {
    group = "publishing"
    description = "Creates and pushes a git tag matching the current shared module version."

    doLast {
        val sharedProject = project(":shared")
        val version = sharedProject.version.toString()
        
        // Validate version format
        if (version == "unspecified") {
            throw GradleException("❌ Version is unspecified in shared/build.gradle.kts")
        }

        val tagName = "v$version"
        println("🏷️  Processing Release: $tagName")

        // 1. Check if tag exists locally
        val checkTag = ProcessBuilder("git", "tag", "-l", tagName)
            .directory(layout.projectDirectory.asFile)
            .start()
        val tagExists = checkTag.inputStream.bufferedReader().readText().trim().isNotBlank()
        checkTag.waitFor()

        if (tagExists) {
            println("⚠️  Tag $tagName already exists locally. Skipping creation.")
        } else {
            // 2. Create Tag
            println("🚀 Creating local tag: $tagName")
            val createTag = ProcessBuilder("git", "tag", "-a", tagName, "-m", "Release $version")
                .directory(layout.projectDirectory.asFile)
                .inheritIO()
                .start()
            val createResult = createTag.waitFor()
            
            if (createResult != 0) {
                throw GradleException("❌ Failed to create git tag")
            }
        }

        // 3. Push Tag
        println("⬆️  Pushing tag to remote...")
        val pushTag = ProcessBuilder("git", "push", "origin", tagName)
            .directory(layout.projectDirectory.asFile)
            .inheritIO()
            .start()
        val pushResult = pushTag.waitFor()

        if (pushResult == 0) {
            println("✅ Release $tagName successfully published!")
        } else {
            throw GradleException("❌ Failed to push tag to origin")
        }
    }
}
