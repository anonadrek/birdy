plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// ktlint-gradle 12.x with KMP + Compose Multiplatform pulls files under
// build/generated/ into its source sets, which makes KtLintCheckTask try to
// lint generated Kotlin (e.g. Compose resource accessors). Exclude anything
// under any build/generated/ directory until the plugin handles this natively.
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { element ->
        element.file.invariantSeparatorsPath.contains("/build/generated/")
    }
}

kotlin {
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(project(":shared:domain"))
            implementation(project(":shared:data"))
            implementation(project(":shared:ml"))
            implementation(project(":shared:content"))
        }
    }
}

android {
    namespace = "se.birdy.app"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
