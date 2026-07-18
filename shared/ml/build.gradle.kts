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
    val fwRoot = "$projectDir/../../iosApp/Frameworks/TensorFlowLiteC.xcframework"
    iosArm64 {
        compilations.getByName("main").cinterops.create("TensorFlowLiteC") {
            defFile("src/nativeInterop/cinterop/TensorFlowLiteC.def")
            compilerOpts("-F$fwRoot/ios-arm64")
        }
        binaries.all { linkerOpts("-F$fwRoot/ios-arm64", "-framework", "TensorFlowLiteC") }
    }
    iosSimulatorArm64 {
        compilations.getByName("main").cinterops.create("TensorFlowLiteC") {
            defFile("src/nativeInterop/cinterop/TensorFlowLiteC.def")
            compilerOpts("-F$fwRoot/ios-arm64_x86_64-simulator")
        }
        binaries.all { linkerOpts("-F$fwRoot/ios-arm64_x86_64-simulator", "-framework", "TensorFlowLiteC") }
    }

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
        iosTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            // LiteRT (f.d. TFLite) — 16 KB-alignat core, samma org.tensorflow.lite.*-klasser
            // som tensorflow-lite 2.16.1 (ren koordinat-swap, i2a 2026-07-16).
            implementation("com.google.ai.edge.litert:litert:1.4.1")
            // BirdNET-Lite uses FlexRFFT (TF Select op) for on-graph spectrogram
            // computation. Without this dep the audio model fails to prepare
            // node 29 with "Select TensorFlow op(s) not supported".
            // Only the Java FlexDelegate + auto-registration are used from this artifact —
            // its 4 KB native .so is replaced by a 16 KB build in androidApp
            // (see downloadFlex16kJniLibs). The excludes stop it from dragging the legacy
            // (4 KB) core + duplicate org.tensorflow.lite API classes onto the classpath.
            implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1") {
                exclude(group = "org.tensorflow", module = "tensorflow-lite")
                exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
            }
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
