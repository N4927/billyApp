pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Nome del progetto SDK
rootProject.name = "spotmi-kmm"

// Includiamo SOLO il modulo condiviso.
// Niente androidApp, niente iosApp.
include(":shared")