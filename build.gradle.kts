import org.apache.tools.ant.filters.FixCrLfFilter

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
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
    dependsOn("installGitHooks")
}

// --- DEV EXPERIENCE (DX) AUTOMATION ---
// Installs the Git Pre-Push hook automatically.
// Run `./gradlew installGitHooks` to set up your local environment.
tasks.register("installGitHooks", Copy::class) {
    description = "Installs the pre-push git hook from scripts/pre-push.sh"
    group = "help"
    from(layout.projectDirectory.dir("scripts/pre-push.sh"))
    into(layout.projectDirectory.dir(".git/hooks"))
    rename("pre-push.sh", "pre-push")
    fileMode = 0b111101101 // 755 (rwxr-xr-x)
    // Force LF line endings for Windows compatibility (Git Bash requires LF)
    filter(FixCrLfFilter::class, "eol" to FixCrLfFilter.CrLf.newInstance("lf"))
}

// Ensure hooks are installed when syncing gradle (optional, but aggressive DX)
tasks.named("prepareKotlinBuildScriptModel") { dependsOn("installGitHooks") }

// Ensure hooks are installed when running build in any project
allprojects {
    tasks.matching { it.name == "build" }.configureEach {
        dependsOn(rootProject.tasks.named("installGitHooks"))
    }
}

// --- RELEASE AUTOMATION ---
// Automatically creates and pushes a git tag for the current version.
// Usage: ./gradlew createReleaseTag
tasks.register("createReleaseTag") {
    group = "publishing"
    description = "Creates and pushes a git tag matching the current shared module version"

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
