// Glass library module: the reusable liquid glass library.
// Depends only on Compose UI, Foundation, Animation, coroutines,
// and Activity Compose. No Material 3, no navigation.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    // Must differ from the :app namespace: AGP rejects duplicate namespaces
    // across modules at manifest-merge time. The Kotlin package stays
    // com.vallxz.glass; only this build namespace is suffixed.
    namespace = "com.vallxz.glass.library"

    compileSdk = 37
    compileSdkMinor = 1

    sourceSets {
        named("main") {
            kotlin.directories.add("src/main/kotlin")
        }
    }

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        // Shaders are embedded as Kotlin strings, never as .agsl sources.
        shaders = false
    }
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        )
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)

    // api: the public glass API exposes types from these artifacts, so
    // consumers get them (and their BOM-managed versions) transitively.
    // Foundation is api because GlassSurface exposes BoxScope in its content
    // lambda and the lens accepts foundation shapes.
    api(composeBom)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.foundation)

    // implementation: press animations and the drag-motion engine live
    // inside components; no animation or coroutine type appears in the
    // public API.
    implementation(libs.androidx.compose.animation)
    implementation(libs.kotlinx.coroutines.android)
    // BackHandler for the modal overlay and the popup menu.
    implementation(libs.androidx.activity.compose)
}
