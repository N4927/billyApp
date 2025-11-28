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
