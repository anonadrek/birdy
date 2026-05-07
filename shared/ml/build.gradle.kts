plugins {
    id("birdy.kmp-android-lib")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// Exclude generated Compose resource accessors from ktlint.
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { element ->
        element.file.invariantSeparatorsPath.contains("/build/generated/")
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:domain"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.runtime)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation("org.tensorflow:tensorflow-lite:2.16.1")
            implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
        }
        androidUnitTest.dependencies {
            implementation("org.robolectric:robolectric:4.13")
            implementation("junit:junit:4.13.2")
        }
    }
}

android {
    namespace = "se.birdy.ml"
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
