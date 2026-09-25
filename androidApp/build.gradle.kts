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

// ---- Release build safety guards (Task 12a, final review of Plan 1, 2026-09-25) ----
// vC128 (purchase-test build, uploaded to internal testing only) is built with
// -Pbirdy.grandfatherCutoffMs=0 -Pbirdy.billingTestBuild=true so nobody is
// grandfathered and purchases can be tested; vC129 (production) is the plain default.
// providers.gradleProperty(name) resolves the SAME effective value whether it came
// from -P on the command line, gradle.properties (project root or GRADLE_USER_HOME),
// an ORG_GRADLE_PROJECT_* env var, or a -D system property — it cannot tell the
// sources apart. A billing-test flag left behind in any non-CLI source (e.g. forgotten
// in ~/.gradle/gradle.properties after a purchase-test session, or an IDE run config)
// would silently turn the NEXT build on this machine — including a production
// bundleRelease — into a purchase-test build with cutoff 0, taking Premium away from
// every already-grandfathered early user. gradle.startParameter.projectProperties
// holds ONLY what -P actually put on THIS invocation's command line, so every read
// below is checked against it and configuration fails on any mismatch.
fun releaseFlagFromCommandLineOnly(name: String): String? {
    val resolved = providers.gradleProperty(name).orNull
    val fromCommandLine = gradle.startParameter.projectProperties[name]
    if (resolved != fromCommandLine) {
        error(
            "$name must be passed on the command line with -P, never set in " +
                "gradle.properties: a lingering value would turn a production build into " +
                "a purchase-test build.",
        )
    }
    return fromCommandLine
}

val releaseVersionCode = 128
val releaseVersionNameBase = "1.3.0"

// A cutoff override requires BOTH -Pbirdy.grandfatherCutoffMs=<ms> AND
// -Pbirdy.billingTestBuild=true — the second flag exists so a cutoff override can
// never slip into a production upload by accident; such a build gets a "-koptest"
// versionName suffix and must never be promoted to production.
val cutoffOverride = releaseFlagFromCommandLineOnly("birdy.grandfatherCutoffMs")
val billingTestBuild = releaseFlagFromCommandLineOnly("birdy.billingTestBuild") == "true"
if (cutoffOverride != null && !billingTestBuild) {
    error(
        "birdy.grandfatherCutoffMs may only be set together with -Pbirdy.billingTestBuild=true " +
            "(billing-test builds are never promoted to production).",
    )
}
val releaseVersionName = releaseVersionNameBase + (if (billingTestBuild) "-koptest" else "")

// Early-user cutoff (spec §5.1): installs before this instant keep Premium forever.
// Default = planned go-live + 48 h = 2026-10-02T00:00 Europe/Stockholm
// (2026-10-01T22:00:00Z). NEVER change it after 1.3.0 ships.
val grandfatherCutoffMs = cutoffOverride ?: "1790892000000"

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
        versionCode = releaseVersionCode
        versionName = releaseVersionNameBase
        buildConfigField(
            "String",
            "PLAY_LICENSE_KEY",
            "\"${project.findProperty("BIRDY_PLAY_LICENSE_KEY") ?: ""}\"",
        )
        // Monetisation is live from 1.3.0 (spec 2026-09-24): no launch-period override.
        buildConfigField("Boolean", "PREMIUM_OPEN_FOR_LAUNCH", "false")
        // Cutoff/test-build override logic + the "NEVER change it after ships" rule live
        // in the "Release build safety guards" block above defaultConfig, so both this
        // block and verifyReleaseKeys/verifyProductionRelease share one computation.
        if (billingTestBuild) versionNameSuffix = "-koptest"
        buildConfigField("long", "GRANDFATHER_CUTOFF_MS", "${grandfatherCutoffMs}L")
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

// Release builds must never ship without the Play licensing key (billing signature
// verification) or the MapTiler key (map tiles) — blank values only make sense for
// local debug builds. Only presence (a boolean) is recorded as a task input, never the
// secret itself. Spec 2026-09-24 §3 A3. Also prints the effective release configuration
// (Task 12a, final review of Plan 1, 2026-09-25) so a wrong cutoff/version is visible
// in the build log instead of only discoverable by inspecting the shipped artifact.
val verifyReleaseKeys by tasks.registering {
    description = "Fails release builds when BIRDY_PLAY_LICENSE_KEY or MAPTILER_API_KEY is blank."
    listOf("BIRDY_PLAY_LICENSE_KEY", "MAPTILER_API_KEY").forEach { key ->
        inputs.property(
            "present.$key",
            providers.gradleProperty(key).map { it.isNotBlank() }.orElse(false),
        )
    }
    inputs.property("releaseVersionCode", releaseVersionCode)
    inputs.property("releaseVersionName", releaseVersionName)
    inputs.property("grandfatherCutoffMs", grandfatherCutoffMs)
    inputs.property("billingTestBuild", billingTestBuild)
    doLast(
        Action {
            logger.lifecycle(
                "Birdy release config: versionCode=${inputs.properties["releaseVersionCode"]} " +
                    "versionName=${inputs.properties["releaseVersionName"]} " +
                    "GRANDFATHER_CUTOFF_MS=${inputs.properties["grandfatherCutoffMs"]} " +
                    "billingTestBuild=${inputs.properties["billingTestBuild"]}",
            )
            val missing =
                inputs.properties
                    .filter { (name, present) -> name.startsWith("present.") && present == false }
                    .keys
                    .map { it.removePrefix("present.") }
            if (missing.isNotEmpty()) {
                error(
                    "Release build blocked: blank ${missing.joinToString()}. " +
                        "Set the real values in ~/.gradle/gradle.properties.",
                )
            }
        },
    )
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseKeys)
}

// Guards ONLY the actual production package (bundleRelease/assembleRelease) — never
// preReleaseBuild/lintRelease/compilation, which must keep working on this machine even
// before MAPTILER_API_KEY is rotated (Task 12a, final review of Plan 1, 2026-09-25). A
// purchase-test build (billingTestBuild=true, e.g. vC128) is exempt: it is uploaded to
// internal testing only, never production, and may legitimately still carry the leaked
// key. Only booleans are ever recorded as task inputs or logged — never the key itself,
// never a hash computed from it.
val verifyProductionRelease by tasks.registering {
    description =
        "Fails a PRODUCTION bundle/APK when MAPTILER_API_KEY is the key that leaked in " +
        "git history, or the release keystore isn't configured."
    inputs.property("billingTestBuild", billingTestBuild)
    inputs.property(
        "mapTilerKeyIsLeaked",
        providers.gradleProperty("MAPTILER_API_KEY").orElse("").map { raw ->
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { b -> "%02x".format(b) }
            // Confirmed 2026-09-25 against the key that leaked via
            // docs/superpowers/plans/2026-06-08-website-coverage-map.md — that key is
            // already public in git history, so committing its hash here is safe.
            hex == "1ee223f405416fb3a1cf3f19b5598a2a20029854136812379b109b6b4ea8657b"
        },
    )
    inputs.property(
        "keystoreConfigured",
        providers.gradleProperty("BIRDY_KEYSTORE_PATH").map { it.isNotBlank() }.orElse(false),
    )
    doLast(
        Action {
            if (inputs.properties["billingTestBuild"] as Boolean) {
                logger.lifecycle("verifyProductionRelease: skipped (billing-test build, not production).")
            } else {
                if (inputs.properties["mapTilerKeyIsLeaked"] as Boolean) {
                    error(
                        "MAPTILER_API_KEY is the key that leaked in git history; create a new key " +
                            "in MapTiler Cloud and put it in ~/.gradle/gradle.properties (spec §8.4)",
                    )
                }
                if (!(inputs.properties["keystoreConfigured"] as Boolean)) {
                    error("production bundles must be signed")
                }
            }
        },
    )
}

tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    dependsOn(verifyProductionRelease)
}
