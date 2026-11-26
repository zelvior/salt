// Root project build file. Plugin versions live in gradle/libs.versions.toml.
// Plugins are declared here with `apply false` and applied per-module in app/build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
