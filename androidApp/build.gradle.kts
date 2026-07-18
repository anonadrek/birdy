import java.net.URI
import java.security.MessageDigest

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
            // LiteRT (f.d. TFLite) needed so Kotlin compiler can resolve Interpreter.Options
            // when calling AndroidTfliteRunner(modelBytes, info) with default options param
            // in buildClassifier. LiteRT = 16 KB-aligned core, same org.tensorflow.lite.* API.
            implementation("com.google.ai.edge.litert:litert:1.4.1")
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.appcompat)
            // WorkManager needed for debug-only dev-trigger lambdas in MainActivity.
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.osmdroid.android)
            // Google Play In-App Review API (ratings velocity; Play-mediated, no analytics).
            implementation("com.google.android.play:review:2.0.2")
        }
    }
}

// ---- 16 KB Flex override (BirdNET select-tf-ops) — i2a 2026-07-16 -----------------
// No official 16 KB select-tf-ops exists: TFLite 2.16.1 (4 KB .so) was the final
// org.tensorflow release and LiteRT publishes no select-tf-ops artifact. The Maven AAR's
// libtensorflowlite_flex_jni.so is replaced at build time with a 16 KB-aligned build from
// github.com/arxdeus/tflite_flex_16kb_android (Apache-2.0 TF build, not Google-signed),
// pinned by release tag + SHA-256 (computed + ELF-verified by us 2026-07-16). Provenance,
// decision + fallbacks: docs/superpowers/specs/2026-07-16-i2a-android-litert-16kb-design.md
val flexReleaseTag = "tf-a95156b81d38"
val flexSha256ByAbi =
    mapOf(
        "arm64-v8a" to "bb63006c3cc8b924c1c83749be39ece51a5dbb2ec1e57a4545f6df1e94e38692",
        "armeabi-v7a" to "de7c9c41d1207a4d24e7148438e48a56eda22c91d35f4fd71e60dd97e0189109",
        "x86_64" to "b066a3a9671d06c6d7d54507c5f6f00f167a08b537cafdb3e62fb23c39235446",
    )
val flexJniLibsDir = layout.buildDirectory.dir("flex16k/jniLibs")

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
        versionCode = 125
        versionName = "1.2.0-rc3"
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
        // x86 (32-bit) excluded: no 16 KB flex build exists for it and we never ship a
        // split with a missing or 4 KB flex lib (i2a spec §2). Real devices are arm64/v7a;
        // x86_64 covers emulators.
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
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

    packaging {
        jniLibs {
            // Prefer our 16 KB copy (project jniLibs, from downloadFlex16kJniLibs) over
            // the select-tf-ops AAR's 4 KB copy. Verified by tools/check_16kb_alignment.py.
            pickFirsts += "**/libtensorflowlite_flex_jni.so"
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
        jniLibs.srcDir(flexJniLibsDir)
    }

    // Debug-only: include asset-pack images directly so device-verify on debug-APK
    // shows bird photos. Install-time asset packs only ship with AAB, so debug builds
    // installed via `installDebug` otherwise serve empty `/android_asset/images/*` paths.
    sourceSets["debug"].assets.srcDirs("../asset-pack/src/main/assets")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

// :shared:content:buildSpeciesDb writes the species image set into
// :asset-pack/src/main/assets/images, which the asset-pack pre-bundle tasks consume.
// Without a declared dependency, `bundleRelease` hits Gradle's implicit-dependency
// validation and fails whenever species.db needs (re)building. Declare it explicitly so
// the images are always fresh before they're packed.
tasks
    .matching { it.name.startsWith("assetPack") && it.name.endsWith("PreBundleTask") }
    .configureEach {
        dependsOn(":shared:content:buildSpeciesDb")
    }

val downloadFlex16kJniLibs by tasks.registering {
    description = "Downloads the 16 KB libtensorflowlite_flex_jni.so per ABI (SHA-256-pinned)."
    // Store all values as named inputs so the doLast action reads from inputs.properties
    // only, with no closure capture of script-scope vals (which are not CC-serializable).
    inputs.property("releaseTag", flexReleaseTag)
    flexSha256ByAbi.forEach { (abi, sha) -> inputs.property("sha256.$abi", sha) }
    outputs.dir(flexJniLibsDir)
    doLast(
        Action {
            // Inline SHA-256 helper (no script-scope refs — configuration cache compatibility).
            fun fileDigest(f: java.io.File): String {
                val md = MessageDigest.getInstance("SHA-256")
                f.inputStream().use { s ->
                    val buf = ByteArray(65536)
                    var n = s.read(buf)
                    while (n >= 0) {
                        md.update(buf, 0, n)
                        n = s.read(buf)
                    }
                }
                return md.digest().joinToString("") { b -> "%02x".format(b) }
            }
            val tag = inputs.properties["releaseTag"] as String
            val abis =
                inputs.properties.keys
                    .filter { it.startsWith("sha256.") }
                    .map { it.removePrefix("sha256.") }
            val outputDir = outputs.files.singleFile
            abis.forEach { abi ->
                val expectedSha = inputs.properties["sha256.$abi"] as String
                val target = outputDir.resolve("$abi/libtensorflowlite_flex_jni.so")
                if (target.exists() && fileDigest(target) == expectedSha) return@forEach
                target.parentFile.mkdirs()
                val url =
                    "https://github.com/arxdeus/tflite_flex_16kb_android/releases/download/" +
                        "$tag/libtensorflowlite_flex_jni.so-$abi"
                logger.lifecycle("Downloading 16 KB flex lib for $abi (~100 MB)...")
                URI(url).toURL().openStream().use { ins ->
                    target.outputStream().use { out -> ins.copyTo(out) }
                }
                val actualSha = fileDigest(target)
                if (actualSha != expectedSha) {
                    target.delete()
                    error("SHA-256 mismatch for $abi: expected $expectedSha, got $actualSha")
                }
            }
        },
    )
}

tasks.matching { it.name.endsWith("JniLibFolders") }.configureEach {
    dependsOn(downloadFlex16kJniLibs)
}
