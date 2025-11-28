pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

// Defines the root project name, visible in IDEs and build reports.
rootProject.name = "spotmi-kmm"

// This is a standalone KMP SDK project. We only include the 'shared' module
// which produces the artifacts (AAR/Framework) for consumption by client apps.
include(":shared")