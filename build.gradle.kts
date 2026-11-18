// Top-level build file. Module configuration lives in each module's own
// build.gradle.kts. AGP 9 has Kotlin built in, so there is deliberately no
// org.jetbrains.kotlin.android plugin here.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
}
