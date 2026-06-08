plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget()

    sourceSets {
        androidMain.dependencies {
            implementation(project(":composeApp"))
            implementation(project(":shared:domain"))
            implementation(project(":shared:data"))
            implementation(project(":shared:ml"))
            implementation(project(":shared:datastore"))
            implementation(project(":shared:pdf"))
            // Plan 6b3 T7: MainActivity calls getString(StringResource) to localize
            // premium-badge names for the PDF renderer via BadgeStringMap.
            implementation(compose.components.resources)
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.core.ktx)
            implementation("androidx.activity:activity-compose:1.9.3")
            // TFLite needed so Kotlin compiler can resolve Interpreter.Options when calling
            // AndroidTfliteRunner(modelBytes, info) with default options param in buildClassifier.
            implementation("org.tensorflow:tensorflow-lite:2.16.1")
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.appcompat)
            // WorkManager needed for debug-only dev-trigger lambdas in MainActivity.
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.osmdroid.android)
        }
    }
}

android {
    namespace = "se.birdy.android"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    // Plan 6b3 T22b: Install-time asset pack carrying ~320 MB of WebP species images.
    // Without this the base APK download exceeds the 150 MB Play Store limit.
    assetPacks += listOf(":asset-pack")

    defaultConfig {
        applicationId = "se.birdy.android"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 123
        versionName = "1.2.0-rc1"
        buildConfigField(
            "String",
            "PLAY_LICENSE_KEY",
            "\"${project.findProperty("BIRDY_PLAY_LICENSE_KEY") ?: ""}\"",
        )
        // Launch-period flag: while true, MainActivity forces premiumOverride = Active(LIFETIME)
        // so closed-testing + initial production users get every Premium feature for free
        // (BirdNET audio-ID is already free per CC BY-NC-SA license; this opens PDF + season
        // stats + 10 field marks too). Flip to "false" + bump versionCode when Billing v8
        // monetization is enabled in a future release.
        buildConfigField("Boolean", "PREMIUM_OPEN_FOR_LAUNCH", "true")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        create("release") {
            val keystorePath = providers.gradleProperty("BIRDY_KEYSTORE_PATH").orNull
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = providers.gradleProperty("BIRDY_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("BIRDY_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("BIRDY_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            buildConfigField("Boolean", "PREMIUM_DEBUG_FORCE_ACTIVE", "false")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("Boolean", "PREMIUM_DEBUG_FORCE_ACTIVE", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        // Krävs på APK-nivå så kotlinx.datetime/java.time desugaras för minSdk 24.
        isCoreLibraryDesugaringEnabled = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }

    sourceSets["main"].apply {
        manifest.srcFile("src/main/AndroidManifest.xml")
        res.srcDirs("src/main/res")
    }

    // Debug-only: include asset-pack images directly so device-verify on debug-APK
    // shows bird photos. Install-time asset packs only ship with AAB, so debug builds
    // installed via `installDebug` otherwise serve empty `/android_asset/images/*` paths.
    sourceSets["debug"].assets.srcDirs("../asset-pack/src/main/assets")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
