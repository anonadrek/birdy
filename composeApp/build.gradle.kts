plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    // Screenshot rig (release 1.3.0 Plan 2 Task 1) — only applied here (not at the
    // root), so a plain version suffices; no other module needs this plugin.
    id("io.github.takahirom.roborazzi") version "1.43.1"
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

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    // composeApp's own binaries — notably its iOS *test* executable, a standalone
    // linked Mach-O binary (unlike the static framework above, which defers full
    // symbol resolution to Xcode's final app-link stage) — need explicit native
    // linkage for libraries composeApp only touches transitively: sqlite3 (via
    // shared:data's SQLDelight native driver) and the vendored TensorFlowLiteC
    // xcframework (via shared:ml, consumed as `api(...)` in commonMain). Pattern
    // mirrors shared/ml/build.gradle.kts's per-target `binaries.all { linkerOpts(...) }`.
    val fwRoot = "$projectDir/../iosApp/Frameworks/TensorFlowLiteC.xcframework"
    iosArm64 {
        binaries.all { linkerOpts("-lsqlite3", "-F$fwRoot/ios-arm64", "-framework", "TensorFlowLiteC") }
    }
    iosSimulatorArm64 {
        binaries.all {
            linkerOpts("-lsqlite3", "-F$fwRoot/ios-arm64_x86_64-simulator", "-framework", "TensorFlowLiteC")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(project(":shared:domain"))
            implementation(project(":shared:data"))
            implementation(project(":shared:pdf"))
            api(project(":shared:ml"))
            api(project(":shared:content"))
            implementation(libs.sqldelight.coroutines)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.coil.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kaml)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.datetime)
            implementation(project(":shared:datastore"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.exifinterface)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.work.runtime.ktx)
            implementation("com.android.billingclient:billing-ktx:8.0.0")
            implementation(libs.osmdroid.android)
        }
        androidUnitTest.dependencies {
            implementation("junit:junit:4.13.2")
            // Screenshot rig (release 1.3.0 Plan 2 Task 1): renders commonMain Compose
            // screens to PNG on the JVM via Robolectric, in SV + EN, without a device.
            // Gated behind -Pbirdy.screenshots=true (see testOptions below) so the
            // normal gate (:composeApp:testDebugUnitTest) stays fast.
            implementation("org.robolectric:robolectric:4.17")
            implementation("io.github.takahirom.roborazzi:roborazzi:1.43.1")
            implementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.1")
            implementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.43.1")
            implementation("androidx.compose.ui:ui-test-junit4:1.8.2")
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        iosTest.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

afterEvaluate {
    tasks
        .matching {
            it.name.startsWith("assemble") ||
                (it.name.startsWith("merge") && it.name.contains("Asset")) ||
                it.name.startsWith("copyNonXmlValueResources") ||
                it.name.startsWith("convertXmlValueResources") ||
                it.name.startsWith("prepareComposeResources")
        }.configureEach {
            dependsOn(":shared:content:buildSpeciesDb")
            dependsOn(":shared:content:validateBadgesYaml")
            dependsOn(":shared:content:validateBadgeStrings")
            dependsOn(":shared:content:validateModelMapping")
        }
}

// Read once at configuration time (release 1.3.0 Plan 2 Task 1) so the testOptions
// lambda below only ever closes over a plain Boolean, not `project` itself.
val runScreenshotTests = project.hasProperty("birdy.screenshots")

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
        buildConfigField(
            "String",
            "MAPTILER_API_KEY",
            "\"${project.findProperty("MAPTILER_API_KEY") ?: ""}\"",
        )
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        // kotlinx.datetime (LocalDateTime/DayOfWeek) är java.time-backat på Android och
        // kräver core library desugaring på minSdk 24 (annars NewApi-lint + runtime-krasch
        // på API 24/25). Phase-B-notiskoden använder DayOfWeek.SUNDAY som triggade det.
        isCoreLibraryDesugaringEnabled = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Screenshot tests (release 1.3.0 Plan 2 Task 1) are slow and
            // memory-hungry; run them only on request (-Pbirdy.screenshots=true)
            // so the normal :composeApp:testDebugUnitTest gate stays fast.
            if (!runScreenshotTests) {
                test.filter.excludeTestsMatching("se.birdy.app.screenshots.*")
            }
            test.maxHeapSize = "3g"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // Lets createComposeRule() host a ComponentActivity under the testDebugUnitTest
    // variant (release 1.3.0 Plan 2 Task 1 screenshot rig).
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.2")
}
