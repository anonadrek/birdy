plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
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
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
