plugins {
    // Android Plugins (necessari per i moduli androidApp e shared)
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false

    // Kotlin Multiplatform
    alias(libs.plugins.kotlinMultiplatform) apply false

    // Tools aggiuntivi (Database & JSON)
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

// Configurazione opzionale ma consigliata per pulire la build
tasks.register("clean", Delete::class) { delete(rootProject.buildDir) }
