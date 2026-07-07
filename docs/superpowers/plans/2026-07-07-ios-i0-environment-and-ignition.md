# iOS i0 — Environment + iOS Ignition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Task 1 is interactive** (App Store install, sudo, Apple ID) and must run in the main session with the user — do NOT dispatch it to a subagent. Tasks 2–10 are subagent-safe.

**Goal:** The Birdy app boots in the iOS simulator (onboarding renders, encyclopedia shows real species data, scanning is a FakeClassifier stub), with the Mac toolchain installed, iOS targets on every KMP module, and CI guarding the iOS build.

**Architecture:** Add `iosArm64` + `iosSimulatorArm64` targets to each shared module in dependency order (content → domain → datastore/data/ml/pdf → composeApp), writing iosMain actuals as we go — real where trivial, documented stubs where the capability lands in a later plan (camera i2, audio i3, map/PDF i4, StoreKit i5). composeApp exports a static `ComposeApp` framework; a minimal xcodegen-generated Xcode project embeds it. Spec: `docs/superpowers/specs/2026-07-07-birdy-ios-v2-design.md`.

**Tech Stack:** Kotlin 2.1.20, Compose Multiplatform 1.7.3, SQLDelight 2.0.2 (`native-driver` on iOS), kaml 0.65.0 (iOS artifacts verified on Maven Central), xcodegen, Temurin JDK 21, Android SDK 35 (to keep Android builds green on this Mac).

## Global Constraints

- Minimum iOS version: **16.0** (spec §6).
- **No new user-facing features** — parity work only; stubs must say the feature is coming, not fake it (spec §6.3).
- **Android stays shippable after every commit**: `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest` and `:androidApp:assembleDebug` must stay green (spec §6.4).
- **No telemetry** of any kind (spec §6.2).
- All user-visible strings via compose-resources (`Res.string.*`) — never hardcoded (trap catalog).
- `./gradlew ktlintCheck detekt` must pass before every commit.
- Working directory: `/Users/albinabrahamsson/dev/birdy`. Commit style: small commits per task, English messages, work on `main`.
- Xcode version contingency: Kotlin 2.1.20's Kotlin/Native officially supports Xcode 16.x. If `linkDebugFrameworkIosSimulatorArm64` fails with an "unsupported Xcode version" konan error, STOP and surface to Albin with two options: (a) bump `kotlin` in `gradle/libs.versions.toml` to the newest 2.1.x/2.2.x patch and re-run the full Android test suite, or (b) install an older Xcode side-by-side. Do not silently pick one.

---

### Task 1: Mac toolchain (Xcode, JDK 21, Android SDK) + green Android baseline

**INTERACTIVE — run in main session with the user.**

**Files:**
- Create: `~/.local/java21/` (JDK, outside repo)
- Create: `~/Library/Android/sdk/` (SDK, outside repo)
- Create: `local.properties` (repo root, gitignored — verify with `git check-ignore local.properties`)
- Modify: `~/.zshrc` (JAVA_HOME, ANDROID_HOME)

**Interfaces:**
- Produces: working `java` (21), `xcodebuild`, `adb`/`sdkmanager`, and a green Android baseline every later task depends on. `JAVA_HOME=$HOME/.local/java21/Contents/Home` — Task 8's Xcode build script hardcodes this path.

- [ ] **Step 1: USER ACTION — install Xcode.** Ask Albin to install **Xcode** from the Mac App Store (free, ~12 GB, needs his Apple ID), then run `! sudo xcodebuild -license accept` and `! sudo xcode-select -s /Applications/Xcode.app/Contents/Developer` in the session. Wait until done.

- [ ] **Step 2: Verify Xcode + download iOS simulator runtime**

Run: `xcodebuild -version && xcodebuild -downloadPlatform iOS && xcrun simctl list devices available | head -20`
Expected: an Xcode version line (note the major version for the konan contingency), platform download completes, at least one available iPhone simulator listed.

- [ ] **Step 3: Install Temurin JDK 21 (no admin needed)**

```bash
mkdir -p ~/.local/java21 && cd ~/.local \
  && curl -fL -o temurin21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/mac/aarch64/jdk/hotspot/normal/eclipse" \
  && tar -xzf temurin21.tar.gz --strip-components=1 -C ~/.local/java21 \
  && rm temurin21.tar.gz
~/.local/java21/Contents/Home/bin/java -version
```
Expected: `openjdk version "21.0.x"`.

- [ ] **Step 4: Persist JAVA_HOME + ANDROID_HOME in ~/.zshrc**

```bash
cat >> ~/.zshrc <<'EOF'
export JAVA_HOME="$HOME/.local/java21/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
EOF
export JAVA_HOME="$HOME/.local/java21/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
java -version
```
Expected: `openjdk version "21.0.x"`. (Re-export in every non-login shell; Bash tool sessions inherit the profile.)

- [ ] **Step 5: Install Android SDK command-line tools + packages**

```bash
mkdir -p ~/Library/Android/sdk/cmdline-tools && cd ~/Library/Android/sdk/cmdline-tools \
  && curl -fL -o tools.zip "https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip" \
  && unzip -q tools.zip && rm tools.zip && mv cmdline-tools latest
yes | ~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null
~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```
Expected: licenses accepted, three packages install. (If the zip URL 404s, look up the current one at https://developer.android.com/studio#command-line-tools-only.)

- [ ] **Step 6: Point the repo at the SDK**

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > /Users/albinabrahamsson/dev/birdy/local.properties
git -C /Users/albinabrahamsson/dev/birdy check-ignore local.properties
```
Expected: `local.properties` echoed back (i.e. it IS gitignored — if not, add it to `.gitignore` before proceeding).

- [ ] **Step 7: Green Android baseline on this Mac** (first run downloads Gradle + deps, 5–10 min)

Run: `cd /Users/albinabrahamsson/dev/birdy && ./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug --stacktrace`
Expected: `BUILD SUCCESSFUL`. This is the baseline every later task must preserve. No commit (no repo changes).

- [ ] **Step 8: USER ACTION (parallel, non-blocking) — Apple Developer enrollment.** Ask Albin to start enrollment as an **individual** at https://developer.apple.com/programs/enroll/ (99 USD/yr; approval can take days — needed for on-device installs beyond 7-day free provisioning and for all StoreKit work in i5, not for this plan's simulator goal). Record status; do not block on it.

---

### Task 2: `shared/content` iOS targets + `normalizeSearch` iOS actual

**Files:**
- Modify: `shared/content/build.gradle.kts` (kotlin block, ~line 26)
- Create: `shared/content/src/iosMain/kotlin/se/birdy/content/search/SearchNormalize.ios.kt`

**Interfaces:**
- Consumes: Task 1's toolchain.
- Produces: `:shared:content` compiles for `iosArm64`/`iosSimulatorArm64`; `actual fun normalizeSearch(input: String): String` behaving exactly like the JVM actual (NFD → strip combining marks → strip `'’ʼ\`` → lowercase → collapse whitespace → trim). Later tasks add iOS targets to modules that depend on this one.

- [ ] **Step 1: Add iOS targets**

In `shared/content/build.gradle.kts`, at the top of the existing `kotlin {` block (the convention plugin already adds `androidTarget()` + `jvm()`):

```kotlin
kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
```

- [ ] **Step 2: Run the existing common tests on iOS — verify they FAIL to compile**

Run: `./gradlew :shared:content:iosSimulatorArm64Test --stacktrace 2>&1 | tail -20`
Expected: FAIL — `Expected function 'normalizeSearch' has no actual declaration in module <shared:content> for Native` (or similar konan error).

- [ ] **Step 3: Write the iOS actual**

`shared/content/src/iosMain/kotlin/se/birdy/content/search/SearchNormalize.ios.kt`:

```kotlin
package se.birdy.content.search

import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.decomposedStringWithCanonicalMapping

/**
 * iOS actual. Mirrors the JVM actual exactly: NFD-decompose (Foundation),
 * strip combining marks (Kotlin CharCategory = JVM \p{Mn}), strip apostrophes,
 * lowercase, collapse whitespace.
 */
actual fun normalizeSearch(input: String): String {
    @Suppress("CAST_NEVER_SUCCEEDS")
    val decomposed = (NSString.create(string = input) as NSString).decomposedStringWithCanonicalMapping
    return decomposed
        .filterNot { it.category == CharCategory.NON_SPACING_MARK }
        .replace(Regex("['’ʼ`]"), "")
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
}
```

- [ ] **Step 4: Run iOS tests — verify PASS**

Run: `./gradlew :shared:content:iosSimulatorArm64Test --stacktrace`
Expected: PASS — the existing commonTest search tests (cross-locale, apostrophe, diacritics) run green on the simulator. If any commonTest file fails to *compile* on iOS because it uses JVM-only test helpers, move that single test file from `commonTest` to `jvmTest` and note it in the commit message.

- [ ] **Step 5: Android regression + lint, then commit**

Run: `./gradlew :shared:content:jvmTest :shared:domain:jvmTest ktlintCheck detekt --stacktrace`
Expected: PASS.

```bash
git add shared/content && git commit -m "feat(ios): add iOS targets to shared:content + normalizeSearch actual"
```

---

### Task 3: iOS targets on `shared/domain`, `shared/datastore`, `shared/pdf`

**Files:**
- Modify: `shared/domain/build.gradle.kts`, `shared/datastore/build.gradle.kts`, `shared/pdf/build.gradle.kts` (same 2-line target addition as Task 2 Step 1 in each)
- Create: `shared/pdf/src/iosMain/kotlin/se/birdy/pdf/JournalPdfRenderer.ios.kt`

**Interfaces:**
- Consumes: `:shared:content` iOS targets (domain depends on content).
- Produces: three more modules compiling for iOS. `shared/datastore`'s existing iosMain throw-stubs (`UserPreferencesStore.ios.kt`, `PremiumStateStore.ios.kt`) start compiling — they stay as throw-stubs in i0: the iOS graph builder (Task 7) bypasses them with `InMemoryUserPreferences`; real persistence lands in plan i1. `JournalPdfRenderer` iOS actual returns `Failed` (real renderer lands in i4).

- [ ] **Step 1: Add the two iOS target lines** to the top of the `kotlin {` block in each of the three build files (identical to Task 2 Step 1):

```kotlin
kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
```

- [ ] **Step 2: Compile — expect ONLY shared/pdf to fail**

Run: `./gradlew :shared:domain:compileKotlinIosSimulatorArm64 :shared:datastore:compileKotlinIosSimulatorArm64 --stacktrace`
Expected: BUILD SUCCESSFUL (domain has no expects; datastore's iosMain throw-stubs already exist and compile).

Run: `./gradlew :shared:pdf:compileKotlinIosSimulatorArm64 --stacktrace 2>&1 | tail -10`
Expected: FAIL — `Expected class 'JournalPdfRenderer' has no actual declaration`.

- [ ] **Step 3: Write the pdf iOS actual** (mirror of the JVM stub; real UIGraphicsPDFRenderer comes in plan i4)

`shared/pdf/src/iosMain/kotlin/se/birdy/pdf/JournalPdfRenderer.ios.kt`:

```kotlin
package se.birdy.pdf

actual class JournalPdfRenderer actual constructor() {
    actual suspend fun render(
        input: JournalPdfInput,
        outputPath: String,
    ): JournalPdfRenderResult {
        if (input.observations.isEmpty()) return JournalPdfRenderResult.Empty
        return JournalPdfRenderResult.Failed("PDF export lands on iOS in plan i4")
    }
}
```

- [ ] **Step 4: Compile + run iOS tests where they exist**

Run: `./gradlew :shared:pdf:compileKotlinIosSimulatorArm64 :shared:domain:iosSimulatorArm64Test :shared:datastore:iosSimulatorArm64Test --stacktrace`
Expected: PASS (same jvm-only-test-helper escape hatch as Task 2 Step 4 if needed).

- [ ] **Step 5: Android regression + lint, then commit**

Run: `./gradlew :shared:domain:jvmTest ktlintCheck detekt --stacktrace`
Expected: PASS.

```bash
git add shared/domain shared/datastore shared/pdf \
  && git commit -m "feat(ios): add iOS targets to shared:domain, shared:datastore, shared:pdf"
```

---

### Task 4: `shared/data` iOS target + native SQLDelight driver

**Files:**
- Modify: `shared/data/build.gradle.kts` (targets + iosMain dependency)
- Modify: `gradle/libs.versions.toml` (add `sqldelight-native-driver` library)
- Create: `shared/data/src/iosMain/kotlin/se/birdy/data/DatabaseFactory.kt`

**Interfaces:**
- Consumes: `:shared:domain` iOS targets.
- Produces: `actual class DatabaseFactory` with a **no-arg constructor** on iOS (Android's takes `Context`; the expect declares no constructor so each platform picks its own — common code never constructs it) and `actual fun createDriver(): SqlDriver` returning a `NativeSqliteDriver` for the observations DB (`BirdyData.Schema`, file `birdy-observations.db`). Task 7 calls `DatabaseFactory().createDriver()`.

- [ ] **Step 1: Add the library to the version catalog**

In `gradle/libs.versions.toml` under `[libraries]`, next to the other sqldelight entries:

```toml
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
```

- [ ] **Step 2: Add targets + iosMain dependency**

In `shared/data/build.gradle.kts`: add the two target lines (as in Task 2 Step 1), and inside `sourceSets` next to the existing `androidMain`/`jvmMain` blocks:

```kotlin
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
```

- [ ] **Step 3: Compile — verify missing-actual failure**

Run: `./gradlew :shared:data:compileKotlinIosSimulatorArm64 --stacktrace 2>&1 | tail -10`
Expected: FAIL — `Expected class 'DatabaseFactory' has no actual declaration`.

- [ ] **Step 4: Write the iOS actual**

`shared/data/src/iosMain/kotlin/se/birdy/data/DatabaseFactory.kt`:

```kotlin
package se.birdy.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import se.birdy.data.db.BirdyData

/**
 * iOS actual. No-arg constructor (Android's takes a Context). The native driver
 * runs BirdyData.Schema.create/migrate automatically, matching AndroidSqliteDriver —
 * the committed .sqm migrations (1–4) apply on version bumps exactly as on Android.
 */
actual class DatabaseFactory {
    actual fun createDriver(): SqlDriver = NativeSqliteDriver(BirdyData.Schema, "birdy-observations.db")
}
```

- [ ] **Step 5: Compile + iOS tests + Android regression, then commit**

Run: `./gradlew :shared:data:compileKotlinIosSimulatorArm64 :shared:data:iosSimulatorArm64Test :shared:data:jvmTest ktlintCheck detekt --stacktrace`
Expected: PASS (jvm-only-test escape hatch as before if a commonTest won't compile for Native).

```bash
git add shared/data gradle/libs.versions.toml \
  && git commit -m "feat(ios): add iOS target + NativeSqliteDriver DatabaseFactory to shared:data"
```

---

### Task 5: `shared/ml` iOS target + preprocessor actuals

**Files:**
- Modify: `shared/ml/build.gradle.kts` (targets only — no new deps; TFLite stays androidMain)
- Create: `shared/ml/src/iosMain/kotlin/se/birdy/ml/ImagePreprocessor.ios.kt`
- Create: `shared/ml/src/iosMain/kotlin/se/birdy/ml/AudioPreprocessor.ios.kt`

**Interfaces:**
- Consumes: `:shared:domain` iOS targets.
- Produces: `:shared:ml` compiles for iOS. `ImagePreprocessor` throws (real Accelerate/CoreGraphics impl lands in i2 with the camera); `normalize(pcm: ShortArray): FloatArray` is real (pure math). The common `FakeBirdClassifier`, `CameraSource`, `ClassifierBootstrap`, `ClassifierMode` become available to iOS code — Task 7 wires `ClassifierBootstrap(buildClassifier = { Triple(FakeBirdClassifier(), ClassifierMode.DEMO, null) })`.

- [ ] **Step 1: Add the two iOS target lines** to `shared/ml/build.gradle.kts` (as in Task 2 Step 1).

- [ ] **Step 2: Compile — verify the two missing actuals**

Run: `./gradlew :shared:ml:compileKotlinIosSimulatorArm64 --stacktrace 2>&1 | tail -12`
Expected: FAIL naming `ImagePreprocessor` and `normalize`.

- [ ] **Step 3: Write the actuals**

`shared/ml/src/iosMain/kotlin/se/birdy/ml/ImagePreprocessor.ios.kt`:

```kotlin
package se.birdy.ml

/**
 * iOS actual. Real CoreGraphics-based preprocessing lands in plan i2 together with
 * the AVFoundation camera; until then nothing on iOS produces ImageInput frames
 * (the scan flow is stubbed), so this must never be reached.
 */
actual class ImagePreprocessor actual constructor() {
    actual fun preprocess(
        input: ImageInput,
        outHeight: Int,
        outWidth: Int,
        normalizationMean: FloatArray,
        normalizationStd: FloatArray,
    ): FloatArray = throw UnsupportedOperationException("ImagePreprocessor lands on iOS in plan i2")
}
```

`shared/ml/src/iosMain/kotlin/se/birdy/ml/AudioPreprocessor.ios.kt` (mirror of the Android actual — pure math):

```kotlin
package se.birdy.ml

actual fun normalize(pcm: ShortArray): FloatArray = FloatArray(pcm.size) { i -> pcm[i] / 32768f }
```

- [ ] **Step 4: Run iOS tests (FakeBirdClassifierTest et al. run on simulator) + Android regression**

Run: `./gradlew :shared:ml:iosSimulatorArm64Test :shared:ml:jvmTest ktlintCheck detekt --stacktrace`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/ml && git commit -m "feat(ios): add iOS target + preprocessor actuals to shared:ml"
```

---

### Task 6: `composeApp` iOS target + all compile-level actuals

One task because the compile gate only exists once **every** expect in the module has an iOS actual. Each step is one small file.

**Files:**
- Modify: `composeApp/build.gradle.kts` (targets + framework + iosMain deps)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` and `values-en/strings.xml` (2 stub strings)
- Create (all under `composeApp/src/iosMain/kotlin/se/birdy/app/`): `SpeciesRepositoryProvider.ios.kt`, `photo/PhotoStorageProvider.ios.kt`, `util/SpeciesImageUri.ios.kt`, `ui/settings/SettingsLauncher.ios.kt`, `ui/settings/LocaleApplier.ios.kt`, `ui/components/PlatformBackHandler.ios.kt`, `ui/components/IosComingSoonPanel.kt`, `ui/scan/ScanScreenHost.ios.kt`, `ui/scan/CameraPreviewHost.ios.kt`, `ui/scan/IosNoopCameraSource.kt`, `ui/audio/AudioScanScreenHost.ios.kt`, `ui/audio/AudioPermissionController.ios.kt`, `ui/photoanalyze/PhotoAnalyzeHost.ios.kt`, `ui/map/MapScreenHost.ios.kt`, `data/premium/PremiumBillingClient.ios.kt`

**Interfaces:**
- Consumes: iOS targets of all shared modules (Tasks 2–5); `BirdyContent.Schema` + `SqlDelightSpeciesRepository` from `:shared:content`; `PhotoStorage`/`FrameUnavailableException` from commonMain.
- Produces: `:composeApp` compiles + links a static framework named **`ComposeApp`**. `SpeciesRepositoryProvider.get()` returns the real 839-species repo (bundle copy). Task 7 consumes all of these plus `IosNoopCameraSource`.

- [ ] **Step 1: Build config.** In `composeApp/build.gradle.kts`, replace the target declaration:

```kotlin
kotlin {
    androidTarget()

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
```

and add, next to the existing `androidMain.dependencies` block:

```kotlin
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
```

- [ ] **Step 2: Verify the expected wall of missing actuals**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 --stacktrace 2>&1 | grep "has no actual" | sort`
Expected: FAIL listing exactly: `AudioPermissionController`, `AudioScanScreenHost`, `CameraPreviewHost`, `MapScreenHost`, `PhotoAnalyzeHost`, `PlatformBackHandler`, `PremiumBillingClient`, `ScanScreenHost`, `SpeciesRepositoryProvider`, `PhotoStorageProvider`, `applyLocale`, `openExternalUrl`, `openMailto`, `openPlayStoreListing`, `shareApp`, `shareJournalPdf`, `speciesImageUri`.

- [ ] **Step 3: Stub strings.** In `composeApp/src/commonMain/composeResources/values/strings.xml` add inside `<resources>`:

```xml
    <string name="ios_coming_soon_title">Kommer snart till iOS</string>
    <string name="ios_coming_soon_body">Den här delen av Birdy byggs för iOS just nu. Uppslagsverket och dagboken funkar redan!</string>
```

and in `values-en/strings.xml`:

```xml
    <string name="ios_coming_soon_title">Coming soon on iOS</string>
    <string name="ios_coming_soon_body">This part of Birdy is being built for iOS right now. The encyclopedia and journal already work!</string>
```

- [ ] **Step 4: Species repository (real data).** `composeApp/src/iosMain/kotlin/se/birdy/app/SpeciesRepositoryProvider.ios.kt`:

```kotlin
package se.birdy.app

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import birdy_bird_scanner.composeapp.generated.resources.Res
import co.touchlab.sqliter.DatabaseConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy
import se.birdy.content.SpeciesRepository
import se.birdy.content.SqlDelightSpeciesRepository
import se.birdy.content.db.BirdyContent

private const val APPLICATION_ID_OFFSET = 68

/**
 * iOS actual. Copies the prebuilt species.db compose-resource into Documents/databases
 * on first launch (or when the bundled SQLite application_id differs — same re-copy
 * heuristic as the Android actual) and opens it with the native driver.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalForeignApi::class)
actual object SpeciesRepositoryProvider {
    private var instance: SpeciesRepository? = null

    actual fun get(): SpeciesRepository {
        instance?.let { return it }
        val dir = databasesDir()
        val dbPath = "$dir/species.db"
        val bundled = runBlocking { Res.readBytes("files/species.db") }
        if (needsCopy(dbPath, bundled)) {
            NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
            bundled.toNSData().writeToFile(dbPath, true)
        }
        val driver: SqlDriver =
            NativeSqliteDriver(
                DatabaseConfiguration(
                    name = "species.db",
                    version = BirdyContent.Schema.version.toInt(),
                    create = { conn -> co.touchlab.sqliter.interop.wrapConnection(conn) { } },
                    upgrade = { _, _, _ -> },
                    extendedConfig = DatabaseConfiguration.Extended(basePath = dir),
                ),
            )
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        instance = repo
        return repo
    }

    private fun databasesDir(): String {
        val docs =
            NSFileManager.defaultManager
                .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
                .first() as platform.Foundation.NSURL
        return docs.path + "/databases"
    }

    private fun needsCopy(
        dbPath: String,
        bundled: ByteArray,
    ): Boolean {
        val existing = NSData.dataWithContentsOfFile(dbPath) ?: return true
        if (existing.length.toInt() < APPLICATION_ID_OFFSET + 4) return true
        val existingBytes = existing.toByteArray()
        return applicationId(existingBytes) != applicationId(bundled)
    }

    private fun applicationId(header: ByteArray): Int {
        val o = APPLICATION_ID_OFFSET
        return ((header[o].toInt() and 0xFF) shl 24) or
            ((header[o + 1].toInt() and 0xFF) shl 16) or
            ((header[o + 2].toInt() and 0xFF) shl 8) or
            (header[o + 3].toInt() and 0xFF)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData =
    kotlinx.cinterop.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) {
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
    return out
}
```

Import note for the implementer: `NSData.create`, `usePinned`, `addressOf` need `import kotlinx.cinterop.addressOf`, `import kotlinx.cinterop.usePinned`, `import platform.Foundation.create`. If `wrapConnection`'s package differs in SQLDelight 2.0.2 (`co.touchlab.sqliter` vs `app.cash.sqldelight.driver.native.wrapConnection`), use the one the compiler resolves — the `create` callback is a safety net that never runs against the prebuilt file. If the simpler two-arg `NativeSqliteDriver(BirdyContent.Schema, "species.db")` + copying into the driver's default directory proves cleaner, that is an acceptable equivalent as long as the copy lands where the driver opens.

- [ ] **Step 5: Photo storage.** `composeApp/src/iosMain/kotlin/se/birdy/app/photo/PhotoStorageProvider.ios.kt`:

```kotlin
package se.birdy.app.photo

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import se.birdy.app.toNSData

actual object PhotoStorageProvider {
    private val storage = IosPhotoStorage()

    actual fun get(): PhotoStorage = storage
}

/**
 * iOS impl. Persists JPEG bytes as-is (the Android contract's 1024px rescale is
 * deferred to plan i2 — nothing on iOS produces photos until the camera lands there).
 */
@OptIn(ExperimentalForeignApi::class)
class IosPhotoStorage : PhotoStorage {
    override suspend fun persistJpeg(bytes: ByteArray): String {
        if (bytes.isEmpty()) throw FrameUnavailableException("Empty JPEG bytes")
        val docs =
            NSFileManager.defaultManager
                .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
                .first() as NSURL
        val dir = docs.path + "/observations"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        val path = "$dir/${NSUUID().UUIDString}.jpg"
        bytes.toNSData().writeToFile(path, true)
        return path
    }

    override suspend fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}
```

- [ ] **Step 6: Image URIs.** `composeApp/src/iosMain/kotlin/se/birdy/app/util/SpeciesImageUri.ios.kt`:

```kotlin
package se.birdy.app.util

import platform.Foundation.NSBundle

/**
 * iOS actual. Plate images are NOT bundled in i0 (they live in the Android asset
 * pack); Coil shows its error placeholder. Plan i1 bundles them and this path
 * starts resolving.
 */
actual fun speciesImageUri(relativePath: String): String = "file://${NSBundle.mainBundle.resourcePath}/images/$relativePath"
```

- [ ] **Step 7: Settings launchers.** `composeApp/src/iosMain/kotlin/se/birdy/app/ui/settings/SettingsLauncher.ios.kt`:

```kotlin
package se.birdy.app.ui.settings

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual fun openExternalUrl(url: String) {
    NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
}

actual fun openMailto(
    address: String,
    subject: String,
) {
    val encoded = subject.replace(" ", "%20")
    openExternalUrl("mailto:$address?subject=$encoded")
}

actual fun shareApp(text: String) = presentShareSheet(listOf(text))

/** App Store listing does not exist until plan i6 ships; falls back to the website. */
actual fun openPlayStoreListing(packageName: String) = openExternalUrl("https://birdy.community")

actual fun shareJournalPdf(pdfPath: String) {
    presentShareSheet(listOf(NSURL.fileURLWithPath(pdfPath)))
}

private fun presentShareSheet(items: List<*>) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
    root.presentViewController(controller, animated = true, completion = null)
}
```

(`keyWindow` is deprecated but functional on iOS 16+; good enough for i0, revisit in i4 polish.)

- [ ] **Step 8: Locale + back handler.** `LocaleApplier.ios.kt`:

```kotlin
package se.birdy.app.ui.settings

/** iOS follows the system language in i0; in-app override lands in plan i4. */
actual fun applyLocale(tag: String) = Unit
```

`PlatformBackHandler.ios.kt` (package `se.birdy.app.ui.components`):

```kotlin
package se.birdy.app.ui.components

import androidx.compose.runtime.Composable

/** iOS uses swipe-back/on-screen back buttons; no system back event to intercept. */
@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
```

- [ ] **Step 9: Shared "coming soon" panel.** `composeApp/src/iosMain/kotlin/se/birdy/app/ui/components/IosComingSoonPanel.kt`:

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.ios_coming_soon_body
import birdy_bird_scanner.composeapp.generated.resources.ios_coming_soon_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun IosComingSoonPanel(onBack: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(Res.string.ios_coming_soon_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.ios_coming_soon_body), textAlign = TextAlign.Center)
        onBack?.let { Button(onClick = it) { Text("←") } }
    }
}
```

- [ ] **Step 10: The four host stubs.** Each is a thin actual delegating to the panel.

`ui/scan/ScanScreenHost.ios.kt`:

```kotlin
package se.birdy.app.ui.scan

import androidx.compose.runtime.Composable
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.components.IosComingSoonPanel

@Composable
actual fun ScanScreenHost(
    graph: AppGraph,
    onPhotoAnalyzeClick: () -> Unit,
    onFrozen: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) = IosComingSoonPanel(onBack = onBack)
```

`ui/audio/AudioScanScreenHost.ios.kt`:

```kotlin
package se.birdy.app.ui.audio

import androidx.compose.runtime.Composable
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.components.IosComingSoonPanel

@Composable
actual fun AudioScanScreenHost(
    graph: AppGraph,
    onNavigateToMatch: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) = IosComingSoonPanel(onBack = onBack)
```

`ui/photoanalyze/PhotoAnalyzeHost.ios.kt`:

```kotlin
package se.birdy.app.ui.photoanalyze

import androidx.compose.runtime.Composable
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.components.IosComingSoonPanel

@Composable
actual fun PhotoAnalyzeHost(
    graph: AppGraph,
    onLoaded: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) = IosComingSoonPanel(onBack = onBack)
```

`ui/map/MapScreenHost.ios.kt`:

```kotlin
package se.birdy.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import se.birdy.app.ui.components.IosComingSoonPanel

@Composable
actual fun MapScreenHost(
    pins: List<MapPin>,
    onPinClick: (String) -> Unit,
    modifier: Modifier,
) = IosComingSoonPanel()
```

`ui/scan/CameraPreviewHost.ios.kt`:

```kotlin
package se.birdy.app.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import se.birdy.ml.CameraSource

/** Never composed in i0 — ScanScreenHost is stubbed above it. Real AVFoundation preview lands in i2. */
@Composable
actual fun CameraPreviewHost(
    cameraSource: CameraSource,
    modifier: Modifier,
) = Unit
```

Note the default-parameter rule: `actual` declarations must NOT restate default values (`modifier: Modifier = Modifier` lives on the expect only). If the compiler complains about the reverse, match what the expect declares.

- [ ] **Step 11: Camera source + permission interface.** `ui/scan/IosNoopCameraSource.kt`:

```kotlin
package se.birdy.app.ui.scan

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import se.birdy.ml.CameraSource
import se.birdy.ml.ImageInput

/** Emits no frames; the scan UI is stubbed in i0. Real AVFoundation source lands in i2. */
class IosNoopCameraSource : CameraSource {
    override fun frames(): Flow<ImageInput> = emptyFlow()

    override suspend fun start() = Unit

    override suspend fun stop() = Unit
}
```

`ui/audio/AudioPermissionController.ios.kt`:

```kotlin
package se.birdy.app.ui.audio

import kotlinx.coroutines.flow.StateFlow

actual interface AudioPermissionController {
    actual val state: StateFlow<PermissionState>

    actual fun request()

    actual fun openSettings()

    actual fun recheck()
}
```

- [ ] **Step 12: Billing stub.** `data/premium/PremiumBillingClient.ios.kt`:

```kotlin
package se.birdy.app.data.premium

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

/** iOS stub. Real StoreKit 2 implementation lands in plan i5. */
actual class PremiumBillingClient {
    actual val state: StateFlow<PremiumState> = MutableStateFlow(PremiumState.Free)
    actual val formattedPrices: StateFlow<FormattedPrices> = MutableStateFlow(FormattedPrices())

    actual suspend fun connect() = Unit

    actual suspend fun queryPurchases() = Unit

    actual suspend fun launchPurchase(
        activityContext: Any,
        tier: PremiumTier,
    ): PurchaseResult = PurchaseResult.Error("Purchases land on iOS in plan i5 (StoreKit)")

    actual fun dispose() = Unit
}
```

- [ ] **Step 13: Compile the iOS target — verify PASS**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 --stacktrace`
Expected: BUILD SUCCESSFUL. Fix any interop-signature drift (exact Foundation/cinterop names) until green — the shapes above are the contract; imports may need adjusting against Kotlin 2.1.20's platform stubs.

- [ ] **Step 14: Android regression + lint, then commit**

Run: `./gradlew :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt --stacktrace`
Expected: PASS.

```bash
git add composeApp gradle/libs.versions.toml \
  && git commit -m "feat(ios): composeApp iOS target + compile-level iosMain actuals"
```

---

### Task 7: iOS app graph + MainViewController + linked framework

**Files:**
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt`
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/MainViewController.kt`

**Interfaces:**
- Consumes: everything Task 6 produced; `AppGraph` (only its 10 required params — all platform extras stay null/default); `InMemoryUserPreferences` (shared:datastore commonMain); `FakeBirdClassifier`/`ClassifierMode`/`ClassifierBootstrap` (shared:ml); `BadgeCatalogLoader` (commonMain); `BadgeVersionStore` (commonMain interface).
- Produces: `fun MainViewController(): UIViewController` — the single symbol the Swift layer calls (exported as `MainViewControllerKt.MainViewController()` in Swift).

- [ ] **Step 1: Graph builder.** `IosAppGraph.kt`:

```kotlin
package se.birdy.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import se.birdy.app.badges.BadgeCatalogLoader
import se.birdy.app.bootstrap.BadgeVersionStore
import se.birdy.app.di.AppGraph
import se.birdy.app.photo.PhotoStorageProvider
import se.birdy.app.ui.scan.IosNoopCameraSource
import se.birdy.data.DatabaseFactory
import se.birdy.data.badge.BadgeRepositoryImpl
import se.birdy.data.db.BirdyData
import se.birdy.data.observation.SqlDelightObservationRepository
import se.birdy.datastore.InMemoryUserPreferences
import se.birdy.domain.premium.PremiumRepository
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import se.birdy.ml.ClassifierBootstrap
import se.birdy.ml.ClassifierMode
import se.birdy.ml.FakeBirdClassifier

/**
 * iOS composition root — the iOS counterpart of MainActivity.buildAppGraph().
 *
 * i0 limitations (each lifted by its owning plan):
 * - InMemoryUserPreferences: onboarding state is NOT persisted across launches (i1).
 * - FakeBirdClassifier in DEMO mode: scanning is stubbed (i2).
 * - premiumOverride Active(LIFETIME): launch-parity with Android's
 *   PREMIUM_OPEN_FOR_LAUNCH; real StoreKit gating lands in i5.
 */
fun buildIosAppGraph(): AppGraph {
    val birdyData = BirdyData(DatabaseFactory().createDriver())
    val observationRepo = SqlDelightObservationRepository(birdyData.observationQueries)
    val badgeRepo = BadgeRepositoryImpl(birdyData.badgeUnlockQueries)
    val badgeCatalog = runBlocking { BadgeCatalogLoader.loadFromResources() }
    val classifierBootstrap =
        ClassifierBootstrap(
            buildClassifier = { Triple(FakeBirdClassifier(), ClassifierMode.DEMO, null) },
        )
    return AppGraph(
        repository = SpeciesRepositoryProvider.get(),
        classifierBootstrap = classifierBootstrap,
        cameraSourceFactory = { IosNoopCameraSource() },
        observationRepository = observationRepo,
        photoStorage = PhotoStorageProvider.get(),
        badgeRepository = badgeRepo,
        badgeCatalog = badgeCatalog,
        badgeVersionStore = InMemoryBadgeVersionStore(),
        userPreferences = InMemoryUserPreferences(),
        premiumRepository = IosStubPremiumRepository(),
        premiumOverride = PremiumState.Active(PremiumTier.LIFETIME, Clock.System.now()),
        versionName = "1.2.0-ios-i0",
    )
}

/** In-memory: badge backfill re-runs each launch — harmless against i0's fresh DB. Real store in i1. */
internal class InMemoryBadgeVersionStore : BadgeVersionStore {
    override var lastSeen: Int = 0
}

/** Local premium state only; real StoreKit repository lands in plan i5. */
internal class IosStubPremiumRepository : PremiumRepository {
    private val _state = MutableStateFlow<PremiumState>(PremiumState.Free)
    override val state: StateFlow<PremiumState> = _state.asStateFlow()

    override suspend fun markPurchased(tier: PremiumTier) {
        _state.value = PremiumState.Active(tier, Clock.System.now())
    }

    override suspend fun restore() = Unit
}
```

- [ ] **Step 2: Entry point.** `MainViewController.kt`:

```kotlin
package se.birdy.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

private val graph by lazy { buildIosAppGraph() }

@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController { App(graph) }
```

- [ ] **Step 3: Link the framework — the real gate for this task**

Run: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --stacktrace`
Expected: BUILD SUCCESSFUL, framework at `composeApp/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework`. **If konan rejects the installed Xcode version, STOP — apply the Global Constraints contingency (surface to Albin).**

- [ ] **Step 4: Android regression + lint, then commit**

Run: `./gradlew :composeApp:testDebugUnitTest ktlintCheck detekt --stacktrace`
Expected: PASS.

```bash
git add composeApp && git commit -m "feat(ios): iOS app graph + MainViewController entry point"
```

---

### Task 8: Xcode project (xcodegen) + simulator boot

**Files:**
- Create: `~/.local/bin/xcodegen` (tool, outside repo)
- Create: `iosApp/project.yml`
- Modify: `iosApp/iosApp/iOSApp.swift`, `iosApp/iosApp/ContentView.swift` (delete), `iosApp/iosApp/Info.plist`
- Create (generated, committed): `iosApp/Birdy.xcodeproj/`
- Modify: `iosApp/README.md`

**Interfaces:**
- Consumes: `ComposeApp` framework + `MainViewControllerKt.MainViewController()` (Task 7); `JAVA_HOME=$HOME/.local/java21/Contents/Home` (Task 1).
- Produces: `iosApp/Birdy.xcodeproj`, scheme `Birdy`, bundle id `se.birdy.ios` — the app running in the simulator. Plan i1+ reuses this project unchanged.

- [ ] **Step 1: Install xcodegen** (single binary, no Homebrew — same pattern as `gh`)

```bash
cd /tmp && curl -fL -o xcodegen.zip "https://github.com/yonaskolb/XcodeGen/releases/latest/download/xcodegen.zip" \
  && unzip -q xcodegen.zip && mkdir -p ~/.local/bin \
  && cp xcodegen/bin/xcodegen ~/.local/bin/ && rm -rf xcodegen xcodegen.zip
~/.local/bin/xcodegen --version
```
Expected: `Version: 2.x.x` (adjust the copy path if the zip layout differs — inspect with `unzip -l`).

- [ ] **Step 2: Project spec.** `iosApp/project.yml`:

```yaml
name: Birdy
options:
  deploymentTarget:
    iOS: "16.0"
targets:
  Birdy:
    type: application
    platform: iOS
    sources: [iosApp]
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: se.birdy.ios
        INFOPLIST_FILE: iosApp/Info.plist
        FRAMEWORK_SEARCH_PATHS: "$(inherited) $(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)"
        OTHER_LDFLAGS: "$(inherited) -framework ComposeApp"
        ENABLE_USER_SCRIPT_SANDBOXING: "NO"
        CODE_SIGN_STYLE: Automatic
    preBuildScripts:
      - name: Compile Kotlin Framework
        script: |
          export JAVA_HOME="$HOME/.local/java21/Contents/Home"
          cd "$SRCROOT/.."
          ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
        basedOnDependencyAnalysis: false
```

- [ ] **Step 3: Swift entry.** Replace `iosApp/iosApp/iOSApp.swift` with:

```swift
import SwiftUI
import ComposeApp

@main
struct BirdyApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

Delete `iosApp/iosApp/ContentView.swift` (its placeholder content is superseded).

- [ ] **Step 4: Info.plist.** Add inside the existing `<dict>` in `iosApp/iosApp/Info.plist`:

```xml
    <key>CFBundleShortVersionString</key>
    <string>1.2.0</string>
    <key>UILaunchScreen</key>
    <dict/>
```

- [ ] **Step 5: Generate + build**

```bash
cd /Users/albinabrahamsson/dev/birdy/iosApp && ~/.local/bin/xcodegen generate
xcodebuild -project Birdy.xcodeproj -scheme Birdy -configuration Debug \
  -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -5
```
Expected: `** BUILD SUCCEEDED **`. (First build compiles the Kotlin framework inside the script phase — slow. `embedAndSignAppleFrameworkForXcode` requires the Xcode-provided env vars, which is why it runs inside xcodebuild, not standalone.)

- [ ] **Step 6: Boot the simulator, install, launch, screenshot**

```bash
DEVICE=$(xcrun simctl list devices available | grep -m1 -o 'iPhone [^(]*' | xargs)
xcrun simctl boot "$DEVICE" || true
APP=$(find ~/Library/Developer/Xcode/DerivedData -type d -name "Birdy.app" -path "*iphonesimulator*" | head -1)
xcrun simctl install booted "$APP"
xcrun simctl launch booted se.birdy.ios
sleep 8 && xcrun simctl io booted screenshot /tmp/birdy-ios-boot.png
```
Then **Read `/tmp/birdy-ios-boot.png`** and verify visually: the Birdy splash or the 7-scene onboarding renders (Field Journal paper background + DM Serif headline). If it crashes on launch, get the crash reason via `xcrun simctl spawn booted log show --last 2m --predicate 'process == "Birdy"' | tail -50`.

- [ ] **Step 7: Smoke-walk the app** (onboarding → main scaffold): complete onboarding by scripted taps or ask Albin to click through; screenshot the encyclopedia tab and verify the 839-species list renders (names + groups; images show Coil placeholders — expected until i1). Screenshot → Read → verify.

- [ ] **Step 8: Update `iosApp/README.md`**

```markdown
# Birdy iOS

Generated Xcode project — edit `project.yml`, then regenerate with `xcodegen generate`
(binary in `~/.local/bin`). The `Compile Kotlin Framework` build phase builds the
`ComposeApp` framework via Gradle (needs JDK 21 at `~/.local/java21`).

Open `Birdy.xcodeproj`, scheme `Birdy`, and run on a simulator (iOS 16+).
Status: plan i0 — boots with real encyclopedia data, FakeClassifier scanning,
in-memory preferences. See `docs/superpowers/specs/2026-07-07-birdy-ios-v2-design.md`.
```

- [ ] **Step 9: Commit**

```bash
git add iosApp && git commit -m "feat(ios): Xcode project (xcodegen) — Birdy boots in the iOS simulator"
```

---

### Task 9: CI — macOS job for the iOS build

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: all iOS gradle tasks from Tasks 2–7.
- Produces: a `ios` CI job failing the build if the iOS framework or iOS unit tests break. Existing `build` job untouched.

- [ ] **Step 1: Add the job** at the end of `jobs:` in `.github/workflows/ci.yml` (public repo — macOS runners are free):

```yaml
  ios:
    name: iOS framework + tests
    runs-on: macos-15
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Set up Gradle cache
        uses: gradle/actions/setup-gradle@v4

      - name: iOS unit tests (shared modules)
        run: ./gradlew :shared:content:iosSimulatorArm64Test :shared:domain:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test :shared:ml:iosSimulatorArm64Test --stacktrace

      - name: Link iOS framework
        run: ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --stacktrace
```

- [ ] **Step 2: Commit, push, watch**

```bash
git add .github/workflows/ci.yml && git commit -m "ci: add macOS job — iOS simulator tests + framework link" && git push
gh run watch --exit-status || gh run view --log-failed
```
Expected: both jobs green. If the macOS runner hits a konan/Xcode mismatch that local didn't, pin the runner's Xcode with a `sudo xcode-select -s /Applications/Xcode_16.4.app` step (list available versions in the runner image docs) — mirror whatever resolution Task 7's contingency chose locally.

---

### Task 10: Documentation — CLAUDE.md status + Mac environment

**Files:**
- Modify: `CLAUDE.md` (Status section + Lokal utvecklingsmiljö section)

**Interfaces:**
- Consumes: outcomes of Tasks 1–9 (record what actually happened, incl. any contingency taken).
- Produces: the canonical project guide reflects the iOS track so the next session starts oriented.

- [ ] **Step 1: Add a Status bullet** at the top of the Status list in `CLAUDE.md` (adjust to reality):

```markdown
- **iOS-spår i0 KLART (2026-07-XX):** Alla KMP-moduler har iosArm64/iosSimulatorArm64-targets; appen bootar i iOS-simulatorn (riktig species.db, FakeClassifier DEMO, in-memory prefs, scan/audio/karta stubbat med "kommer snart"). Xcode-projekt i `iosApp/` (xcodegen; regenerera med `xcodegen generate`). CI har macOS-jobb (iOS-tester + framework-link). Spec: `docs/superpowers/specs/2026-07-07-birdy-ios-v2-design.md`. Nästa: plan i1 (uppslagsverk + dagbok på fysisk iPhone — bilder, riktig persistens, device-install).
```

- [ ] **Step 2: Add a Mac environment table** after the existing "Lokal utvecklingsmiljö (Windows + Galaxy S23 Ultra)" section:

```markdown
## Lokal utvecklingsmiljö (Mac, från 2026-07-07)

| Vad | Var |
|---|---|
| JDK 21 (Temurin) | `~/.local/java21/Contents/Home` (JAVA_HOME i ~/.zshrc) |
| Android SDK | `~/Library/Android/sdk` |
| Xcode | App Store-installerad; `xcodebuild -downloadPlatform iOS` för simulator-runtime |
| xcodegen | `~/.local/bin/xcodegen` — `iosApp/project.yml` är källan, `.xcodeproj` genereras |
| gh CLI | `~/.local/bin/gh` (inloggad som anonadrek) |

Inga Windows-prefix behövs — `./gradlew` funkar direkt. iOS-bygge: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`; hela appen via Xcode/`xcodebuild` (scheme `Birdy`).
```

- [ ] **Step 3: Lint + commit + push**

Run: `./gradlew ktlintCheck detekt --stacktrace` (docs-only change; belt-and-suspenders)

```bash
git add CLAUDE.md && git commit -m "docs: record iOS i0 status + Mac dev environment in CLAUDE.md" && git push
```

---

## Exit criteria (plan i0 done — matches spec §3 row i0)

1. Simulator boots Birdy: onboarding renders, encyclopedia lists 839 species (placeholder images), journal/badges/settings navigate; scan/audio/map show the coming-soon panel.
2. `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug` green (Android untouched).
3. CI green with the new macOS job.
4. Apple Developer enrollment started (user; may still be pending approval).
