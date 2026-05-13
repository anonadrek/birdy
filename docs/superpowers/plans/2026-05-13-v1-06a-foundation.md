# Plan 6a — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Göra Birdy Bird Scanner submit-redo som v1.0 free-only på Google Play Store genom UX-polish, a11y/i18n-fix, delade komponenter och release-mekanik. Tag `v0.8.0-rc1` vid slut.

**Architecture:** 15 tasks som täcker fyra kategorier — (1) release-mekanik (icon, splash, signing, ProGuard, manifest), (2) cold-start + locale-arkitektur (AppGate state-machine, locale-derivation, Language-picker), (3) delade UI-komponenter (`JournalLoading`/`Empty`/`Dialog`/`Scaffold`), (4) UX-fixar A1-A13 + a11y bumps + Settings-rader. Premium-skärmen lever oförändrad genom 6a.

**Tech Stack:** Kotlin Multiplatform 2.1 + Compose Multiplatform + AndroidX Core SplashScreen 1.0.1 + AndroidX AppCompat (för `setApplicationLocales`) + SQLDelight 2.x + GitHub Pages för privacy/terms-hosting + R8/ProGuard.

**Spec:** `docs/superpowers/specs/2026-05-13-v1-06a-foundation-design.md`

**Parallelliserings-hint:** T1, T2, T3, T6, T8, T11, T12, T14 är oberoende — kan starta parallellt. T4 ← T3. T5 ← T4. T7 ← T6. T9, T10 ← T8. T13 ← T12 + T14. T15 efter allt.

---

## Pre-flight

Innan första task:

- [ ] Läs spec: `docs/superpowers/specs/2026-05-13-v1-06a-foundation-design.md`
- [ ] Bekräfta att `./gradlew :androidApp:assembleDebug ktlintCheck detekt :composeApp:testDebugUnitTest :shared:domain:jvmTest :shared:ml:jvmTest` är grön på `main` (baseline från Plan 7e)
- [ ] Verifiera JAVA_HOME-prefix i bash:
  ```bash
  export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```
- [ ] Säkerställ SM-S918B är ansluten och ADB-auktoriserad

---

## Task 1: ProGuard/R8 + keep-rules

**Spec ref:** §2.6 R4, §3.4
**Files:**
- Modify: `androidApp/build.gradle.kts:54-62`
- Create: `androidApp/proguard-rules.pro`

- [ ] **Step 1: Skapa `proguard-rules.pro` med keep-rules**

Skapa `androidApp/proguard-rules.pro`:

```
# TensorFlow Lite — kritisk för classifier-init i release-build.
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.nnapi.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.**

# kotlinx.serialization — kaml använder reflection mot @Serializable-klasser.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class se.birdy.**$$serializer { *; }
-keepclassmembers class se.birdy.** {
    *** Companion;
}
-keepclasseswithmembers class se.birdy.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# SQLDelight — runtime kräver klasser i app.cash.sqldelight.
-keep class app.cash.sqldelight.** { *; }
-keep class se.birdy.data.db.** { *; }

# Coil — image loader använder reflection för fetcher-discovery.
-keep class coil.** { *; }
-dontwarn coil.**

# AndroidX Lifecycle ViewModel — Compose-integration.
-keep class androidx.lifecycle.** { *; }

# CameraX.
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Compose Multiplatform resources — runtime resource-loading.
-keep class org.jetbrains.compose.resources.** { *; }

# Birdy specific — säkra alla ViewModels och datamodeller.
-keep class se.birdy.app.ui.**.ViewModel { *; }
-keep class se.birdy.domain.** { *; }
-keep class se.birdy.content.** { *; }
-keep class se.birdy.ml.** { *; }
```

- [ ] **Step 2: Aktivera minify i `build.gradle.kts`**

Ersätt `androidApp/build.gradle.kts:54-62` med:

```kotlin
buildTypes {
    getByName("debug") {
        buildConfigField("Boolean", "PREMIUM_DEBUG_FORCE_ACTIVE", "false")
    }
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
        buildConfigField("Boolean", "PREMIUM_DEBUG_FORCE_ACTIVE", "false")
    }
}
```

- [ ] **Step 3: Bygga release-AAB**

```bash
./gradlew :androidApp:assembleRelease
```

Förväntat: `BUILD SUCCESSFUL`. AAB ligger på `androidApp/build/outputs/bundle/release/androidApp-release.aab`. **Den är osignerad än** (signing wires upp i Task 2).

- [ ] **Step 4: Verifiera storleksminskning**

```bash
ls -la composeApp/build/outputs/apk/debug/*.apk androidApp/build/outputs/apk/release/*.apk 2>/dev/null || \
  ls -la androidApp/build/outputs/bundle/release/*.aab
```

Förväntat: release-AAB är minst 30% mindre än debug-APK.

- [ ] **Step 5: Commit**

```bash
git add androidApp/build.gradle.kts androidApp/proguard-rules.pro
git commit -m "$(cat <<'EOF'
build(release): enable R8/ProGuard with keep-rules (Plan 6a Task 1)

Keep-rules cover TensorFlow Lite (critical for classifier init in release
build), kotlinx.serialization (kaml + @Serializable types), SQLDelight,
Coil, CameraX, AndroidX Lifecycle, and Birdy domain types.

Device-run verification of signed AAB is performed in Task 15.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Signing config + keystore

**Spec ref:** §2.6 R3
**Files:**
- Modify: `androidApp/build.gradle.kts`
- Create: `gradle.properties` entries (template, real values in `~/.gradle/gradle.properties`)
- Create: `keystore/birdy-upload.jks` (sparas utanför repo, läggs till `.gitignore`)

- [ ] **Step 1: Skapa upload-keystore**

Kör (utanför repo, t.ex. i `~/keys/`):

```bash
keytool -genkeypair \
  -keystore birdy-upload.jks \
  -alias birdy-upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass <STRONG_PASSWORD> \
  -keypass <STRONG_PASSWORD> \
  -dname "CN=Albin Lindblom, OU=Birdy, O=Birdy AB, L=Stockholm, ST=Stockholm, C=SE"
```

Spara keystore-filen på säker plats (utanför repo, t.ex. `C:/Users/abbea/keys/birdy-upload.jks`).

- [ ] **Step 2: Lägg till keystore-properties i `~/.gradle/gradle.properties`**

Edita `C:/Users/abbea/.gradle/gradle.properties` (skapa om saknas):

```properties
BIRDY_KEYSTORE_PATH=C:/Users/abbea/keys/birdy-upload.jks
BIRDY_KEYSTORE_PASSWORD=<DITT_LÖSENORD>
BIRDY_KEY_ALIAS=birdy-upload
BIRDY_KEY_PASSWORD=<DITT_LÖSENORD>
```

- [ ] **Step 3: Lägg till signing-config i `androidApp/build.gradle.kts`**

Sätt in före `buildTypes`-blocket:

```kotlin
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
```

Och i `release { ... }`-blocket:

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
    )
    signingConfig = signingConfigs.getByName("release")
    buildConfigField("Boolean", "PREMIUM_DEBUG_FORCE_ACTIVE", "false")
}
```

- [ ] **Step 4: Lägg keystore-filer i `.gitignore`**

Bekräfta att `.gitignore` innehåller:

```
keystore/
*.jks
*.keystore
```

Om inte, lägg till.

- [ ] **Step 5: Bygg signerad AAB**

```bash
./gradlew :androidApp:bundleRelease
```

Förväntat: `BUILD SUCCESSFUL`. AAB i `androidApp/build/outputs/bundle/release/androidApp-release.aab` är nu signerad. Verifiera med:

```bash
"$JAVA_HOME/bin/jarsigner" -verify -verbose -certs androidApp/build/outputs/bundle/release/androidApp-release.aab | head -20
```

Förväntat: `jar verified` (eller liknande).

- [ ] **Step 6: Commit**

```bash
git add androidApp/build.gradle.kts .gitignore
git commit -m "$(cat <<'EOF'
build(release): add signing config from gradle.properties (Plan 6a Task 2)

Upload keystore stored outside repo; password/path read from
~/.gradle/gradle.properties. Play App Signing wired in Play Console
during T15.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Adaptive icon-koncept (3 skisser → 1 vald)

**Spec ref:** §2.6 R1
**Files:**
- Create: `docs/superpowers/icon-concepts/2026-05-13-concept-A.svg` (samt B, C)
- Create: `docs/superpowers/icon-concepts/README.md` (jämförelse + val)

**Note:** Detta är en design-task. Plan-executorn presenterar 3 koncept som vector-SVGs i `docs/superpowers/icon-concepts/`; användaren väljer 1. Konceptet implementeras i T4.

- [ ] **Step 1: Skapa concept A — "Stämpel-B"**

`docs/superpowers/icon-concepts/2026-05-13-concept-A.svg`:

432×432 viewport (Asset Studio-standard för adaptive icon foreground). Centralt: en stiliserad versal "B" i `DM Serif Italic` (eller close fallback) i `AccentCopper #8C5A3C`, omsluten av en koppar-cirkel (stroke 12px), placerad inom adaptive icon safe-zone (66×66dp center).

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 432 432">
  <circle cx="216" cy="216" r="130" fill="none" stroke="#8C5A3C" stroke-width="12"/>
  <text x="216" y="270" text-anchor="middle" font-family="DM Serif Display, serif"
        font-style="italic" font-size="180" fill="#8C5A3C">B</text>
</svg>
```

Background: `PaperBg #EFE7D6`.

- [ ] **Step 2: Skapa concept B — "Fjäder-monogram"**

`docs/superpowers/icon-concepts/2026-05-13-concept-B.svg`:

Stiliserad fjäder i `HeroMossMid #3F4F30` med kopparkontur, vinkel ~-15°, placerad mitt i icon. Background `PaperBg #EFE7D6`. Återanvänder Field Journal-temats organisk-naturalist-känsla.

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 432 432">
  <path d="M150 350 Q 180 250 220 180 Q 260 110 290 90 Q 280 140 250 200 Q 220 270 180 320 Z"
        fill="#3F4F30" stroke="#8C5A3C" stroke-width="6"/>
  <line x1="150" y1="350" x2="290" y2="90" stroke="#8C5A3C" stroke-width="3"/>
</svg>
```

- [ ] **Step 3: Skapa concept C — "Fältboks-stämpel"**

`docs/superpowers/icon-concepts/2026-05-13-concept-C.svg`:

Cirkulär naturalist-stämpel-look — kopparring med en stiliserad fågel-silhuett i mitten (typ flygande tärna eller annan välkänd kontur). Bakgrund `PaperBg #EFE7D6`. Den ekar StampSeal-komponenten i appen — visuell kontinuitet från ikon till in-app-stamps.

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 432 432">
  <circle cx="216" cy="216" r="130" fill="none" stroke="#8C5A3C" stroke-width="14"/>
  <circle cx="216" cy="216" r="148" fill="none" stroke="#8C5A3C" stroke-width="2"
          stroke-dasharray="6 8"/>
  <!-- Bird silhouette (placeholder — refine with vector tool) -->
  <path d="M170 230 Q 200 200 230 210 Q 260 200 280 220 L 270 240 Q 230 250 200 245 Q 180 245 170 230 Z"
        fill="#3F4F30"/>
</svg>
```

- [ ] **Step 4: Skriv jämförelse-README**

`docs/superpowers/icon-concepts/README.md`:

```markdown
# Birdy app-ikon-koncept — 2026-05-13

Tre koncept för adaptive icon (foreground SVG + background `#EFE7D6` paper-creme +
monochrome-variant för Android 13+ tema-icon).

## Concept A — "Stämpel-B"
Versal "B" i DM Serif Italic, kopparcirkel. Stark "vi är en fältbok"-signal,
ekar Field Journal-typografin. Risk: liknar generisk monogram-app.

## Concept B — "Fjäder-monogram"
Stiliserad fjäder i mossgrönt + koppar. Subtilt fågel-tema utan att vara
plump. Risk: utan domän-kontext kan det läsas som penna eller löv.

## Concept C — "Fältboks-stämpel"
Cirkulär stämpel med fågel-silhuett — ekar StampSeal-komponenten i appen.
Stark visuell kontinuitet från launcher till in-app. Risk: silhuett i
432×432-zoom-out kan bli otydlig på telefonens 48dp launcher-cell.

## Val
[**TBD efter användarinput** — fyll i här innan T4]

## Monokrom-variant
Vald koncept måste också ha en monokrom-variant där `AccentCopper` byts mot
solid svart och `HeroMossMid` byts mot solid svart. Verifieras i T4.
```

- [ ] **Step 5: Commit + be om val**

```bash
git add docs/superpowers/icon-concepts/
git commit -m "$(cat <<'EOF'
docs(icon): 3 adaptive icon concepts for selection (Plan 6a Task 3)

Concepts A (Stämpel-B), B (Fjäder-monogram), C (Fältboks-stämpel) as
SVG sketches. User picks one before T4; chosen concept gets refined
+ monochrome variant + Asset Studio export.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

Pausa här och be användaren välja A/B/C. När valt — uppdatera `README.md`'s "## Val"-sektion och commit med `docs(icon): chosen concept X`.

---

## Task 4: Adaptive icon + Splash Screen API 31+

**Spec ref:** §2.6 R1, R2
**Files:**
- Create: `androidApp/src/main/res/drawable/ic_launcher_foreground.xml` (vector från vald koncept)
- Create: `androidApp/src/main/res/drawable/ic_launcher_monochrome.xml`
- Create: `androidApp/src/main/res/values/colors.xml` (paper-creme + copper)
- Create: `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `androidApp/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` (fallback för API < 26)
- Create: `androidApp/src/main/res/values-v31/themes.xml` (splash-screen-tema)
- Create: `androidApp/src/main/res/values/themes.xml` (fallback-tema)
- Modify: `androidApp/src/main/AndroidManifest.xml` (icon-ref + theme-ref)
- Modify: `androidApp/build.gradle.kts` (lägg `androidx.core:core-splashscreen:1.0.1`)
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` (`installSplashScreen()`)
- Modify: `gradle/libs.versions.toml` (lägg splashscreen-dependency)

- [ ] **Step 1: Lägg `core-splashscreen` i version-catalog**

Edita `gradle/libs.versions.toml` (eller wherever versions definieras), lägg till:

```toml
[versions]
androidx-core-splashscreen = "1.0.1"

[libraries]
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "androidx-core-splashscreen" }
```

I `androidApp/build.gradle.kts` lägg `implementation(libs.androidx.core.splashscreen)`.

- [ ] **Step 2: Skapa vector drawables för vald icon-koncept**

Konvertera vald SVG från `docs/superpowers/icon-concepts/` till Android vector drawable. Använd Android Studio Asset Studio (File → New → Image Asset → Launcher Icons) eller manuellt:

`androidApp/src/main/res/drawable/ic_launcher_foreground.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="432" android:viewportHeight="432">
    <!-- Innehåll baserat på vald koncept (A/B/C) -->
</vector>
```

`androidApp/src/main/res/drawable/ic_launcher_monochrome.xml`: samma form, men alla färger → `?attr/colorOnSurface` eller solid svart.

- [ ] **Step 3: Skapa colors.xml**

`androidApp/src/main/res/values/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="paper_bg">#EFE7D6</color>
    <color name="accent_copper">#8C5A3C</color>
    <color name="hero_moss_mid">#3F4F30</color>
    <color name="ic_launcher_background">#EFE7D6</color>
</resources>
```

- [ ] **Step 4: Skapa adaptive icon-XML**

`androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
```

Samma för `ic_launcher_round.xml` (kopiera).

- [ ] **Step 5: Generera PNG fallbacks**

För API < 26 (minSdk täcker väl detta — men säkert): generera `ic_launcher.png` i 48, 72, 96, 144, 192 dp via Asset Studio. Spara i `mipmap-mdpi/`, `mipmap-hdpi/`, `mipmap-xhdpi/`, `mipmap-xxhdpi/`, `mipmap-xxxhdpi/`.

- [ ] **Step 6: Skapa splash-screen-tema (API 31+)**

`androidApp/src/main/res/values-v31/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Birdy.Starting" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/paper_bg</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
        <item name="postSplashScreenTheme">@style/Theme.Birdy</item>
    </style>
    <style name="Theme.Birdy" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/paper_bg</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

- [ ] **Step 7: Skapa fallback-tema (API < 31)**

`androidApp/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Birdy.Starting" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/paper_bg</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
        <item name="postSplashScreenTheme">@style/Theme.Birdy</item>
    </style>
    <style name="Theme.Birdy" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/paper_bg</item>
    </style>
</resources>
```

- [ ] **Step 8: Uppdatera AndroidManifest.xml**

Edita `androidApp/src/main/AndroidManifest.xml`:

```xml
<application
    android:label="@string/app_name"
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:supportsRtl="true"
    android:theme="@style/Theme.Birdy.Starting">
    <activity
        android:name=".MainActivity"
        android:exported="true"
        android:theme="@style/Theme.Birdy.Starting"
        android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|uiMode">
        <!-- ... -->
    </activity>
    <!-- ... -->
</application>
```

- [ ] **Step 9: `installSplashScreen()` i MainActivity**

I `MainActivity.kt:40-52` (innan `super.onCreate`):

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    androidx.core.splashscreen.SplashScreen.installSplashScreen(this)
    enableEdgeToEdge(
        navigationBarStyle = SystemBarStyle.light(
            AndroidColor.TRANSPARENT,
            AndroidColor.TRANSPARENT,
        ),
    )
    super.onCreate(savedInstanceState)
    // ...
}
```

- [ ] **Step 10: Bygg och device-verify**

```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Förväntat: launcher visar Birdy-ikonet (inte default Android-robot); cold-start visar paper-creme splash med ikon innan AppGate mountas; ingen vit flash.

- [ ] **Step 11: Commit**

```bash
git add androidApp/src/main/res/ androidApp/src/main/AndroidManifest.xml \
        androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt \
        androidApp/build.gradle.kts gradle/libs.versions.toml
git commit -m "$(cat <<'EOF'
feat(android): adaptive app icon + Splash Screen API 31+ (Plan 6a Task 4)

Adaptive icon with foreground + paper-creme background + monochrome
variant for Android 13+ themed icons. Splash screen renders paper-bg
+ icon between launcher tap and AppGate mount via core-splashscreen
1.0.1. Eliminates the white flash that preceded paper-bg in cold-start.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Cold-start TFLite-flytt till AppGate

**Spec ref:** §2.1 A1, §3.1
**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppGate.kt`
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`
- Create: `shared/ml/src/commonMain/kotlin/se/birdy/ml/ClassifierBootstrap.kt`
- Create: `shared/ml/src/jvmTest/kotlin/se/birdy/ml/ClassifierBootstrapTest.kt`

- [ ] **Step 1: Skriv failing test för ClassifierBootstrap state-machine**

`shared/ml/src/jvmTest/kotlin/se/birdy/ml/ClassifierBootstrapTest.kt`:

```kotlin
package se.birdy.ml

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassifierBootstrapTest {
    @Test
    fun emits_initializing_then_ready_on_success() = runTest {
        val bootstrap = ClassifierBootstrap(
            buildClassifier = { Triple(FakeBirdClassifier(), ClassifierMode.REAL, "v1.0") },
        )
        val states = bootstrap.state.take(2).toList()
        assertTrue(states[0] is ClassifierBootstrapState.Initializing)
        val ready = states[1] as ClassifierBootstrapState.Ready
        assertEquals(ClassifierMode.REAL, ready.mode)
        assertEquals("v1.0", ready.modelVersion)
    }

    @Test
    fun emits_failed_when_build_throws() = runTest {
        val bootstrap = ClassifierBootstrap(
            buildClassifier = { throw RuntimeException("boom") },
        )
        val states = bootstrap.state.take(2).toList()
        val failed = states[1] as ClassifierBootstrapState.Failed
        assertEquals("boom", failed.cause.message)
    }
}
```

- [ ] **Step 2: Kör testet för att verifiera fail**

```bash
./gradlew :shared:ml:jvmTest --tests "se.birdy.ml.ClassifierBootstrapTest"
```

Förväntat: FAIL — `ClassifierBootstrap` är inte definierad.

- [ ] **Step 3: Implementera `ClassifierBootstrap`**

`shared/ml/src/commonMain/kotlin/se/birdy/ml/ClassifierBootstrap.kt`:

```kotlin
package se.birdy.ml

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ClassifierBootstrapState {
    data object Initializing : ClassifierBootstrapState
    data class Ready(
        val classifier: BirdClassifier,
        val mode: ClassifierMode,
        val modelVersion: String?,
    ) : ClassifierBootstrapState
    data class Failed(val cause: Throwable) : ClassifierBootstrapState
}

class ClassifierBootstrap(
    private val buildClassifier: suspend () -> Triple<BirdClassifier, ClassifierMode, String?>,
    private val scope: CoroutineScope = MainScope(),
) {
    private val _state = MutableStateFlow<ClassifierBootstrapState>(ClassifierBootstrapState.Initializing)
    val state: StateFlow<ClassifierBootstrapState> = _state.asStateFlow()

    init {
        scope.launch {
            try {
                val (clf, mode, version) = withContext(Dispatchers.Default) { buildClassifier() }
                _state.value = ClassifierBootstrapState.Ready(clf, mode, version)
            } catch (t: Throwable) {
                _state.value = ClassifierBootstrapState.Failed(t)
            }
        }
    }

    fun retry() {
        if (_state.value !is ClassifierBootstrapState.Failed) return
        _state.value = ClassifierBootstrapState.Initializing
        scope.launch {
            try {
                val (clf, mode, version) = withContext(Dispatchers.Default) { buildClassifier() }
                _state.value = ClassifierBootstrapState.Ready(clf, mode, version)
            } catch (t: Throwable) {
                _state.value = ClassifierBootstrapState.Failed(t)
            }
        }
    }
}
```

- [ ] **Step 4: Kör testet för att verifiera pass**

```bash
./gradlew :shared:ml:jvmTest --tests "se.birdy.ml.ClassifierBootstrapTest"
```

Förväntat: PASS (2/2).

- [ ] **Step 5: Refactor `AppGraph` till lazy classifier**

I `AppGraph.kt`, ändra `classifier`-parametern till lazy via en factory + state:

```kotlin
class AppGraph(
    val repository: SpeciesRepository,
    val classifierBootstrap: ClassifierBootstrap,  // NYTT — ersätter classifier + classifierMode + modelVersion
    val cameraSourceFactory: () -> CameraSource,
    // ... (övriga oförändrade)
) {
    /** Convenience accessors — kasta om bootstrap inte är Ready ännu. */
    val classifier: BirdClassifier
        get() = (classifierBootstrap.state.value as? ClassifierBootstrapState.Ready)?.classifier
            ?: error("Classifier not ready — AppGate should gate on bootstrap state")
    val classifierMode: ClassifierMode
        get() = (classifierBootstrap.state.value as? ClassifierBootstrapState.Ready)?.mode
            ?: ClassifierMode.DEMO
    val modelVersion: String?
        get() = (classifierBootstrap.state.value as? ClassifierBootstrapState.Ready)?.modelVersion
    // ... rest unchanged
}
```

- [ ] **Step 6: Uppdatera AppGate för bootstrap-gating**

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppGate.kt`:

```kotlin
@Composable
fun AppGate(graph: AppGraph) {
    val bootstrapState by graph.classifierBootstrap.state.collectAsState()
    val hasSeen by graph.userPreferences.hasSeenOnboarding.collectAsState(initial = null)

    when (bootstrapState) {
        is ClassifierBootstrapState.Initializing -> JournalLoading(label = stringResource(Res.string.bootstrap_loading))
        is ClassifierBootstrapState.Failed -> BootstrapFailedView(
            onRetry = { graph.classifierBootstrap.retry() },
        )
        is ClassifierBootstrapState.Ready -> {
            when (hasSeen) {
                null -> JournalLoading()
                true -> AppScaffold(graph)
                false -> { /* onboarding-block oförändrat */ }
            }
        }
    }
}

@Composable
private fun BootstrapFailedView(onRetry: () -> Unit) {
    JournalDialog(
        title = stringResource(Res.string.bootstrap_failed_title),
        body = stringResource(Res.string.bootstrap_failed_body),
        confirmLabel = stringResource(Res.string.bootstrap_failed_retry),
        onConfirm = onRetry,
        dismissLabel = null,
    )
}
```

`JournalLoading` och `JournalDialog` levereras i Task 8 — denna task creates dem som stubs tills T8.

- [ ] **Step 7: Uppdatera MainActivity**

I `MainActivity.kt:71`, byt ut `runBlocking { buildClassifier() }` mot `ClassifierBootstrap`-konstruktion:

```kotlin
val classifierBootstrap = ClassifierBootstrap(
    buildClassifier = { buildClassifier() },
)
val graph = AppGraph(
    repository = SpeciesRepositoryProvider.get(),
    classifierBootstrap = classifierBootstrap,
    // ... (övriga oförändrade — ta bort `classifier`, `classifierMode`, `modelVersion`-parametrar)
)
setContent { App(graph) }
```

- [ ] **Step 8: Lägg till strängar**

`composeApp/src/commonMain/composeResources/values/strings.xml`:

```xml
<string name="bootstrap_loading">Förbereder fältboken…</string>
<string name="bootstrap_failed_title">Något gick fel</string>
<string name="bootstrap_failed_body">Kunde inte ladda fågel-arkivet. Försök igen om en stund.</string>
<string name="bootstrap_failed_retry">Försök igen</string>
```

`values-en/strings.xml`:

```xml
<string name="bootstrap_loading">Preparing the field journal…</string>
<string name="bootstrap_failed_title">Something went wrong</string>
<string name="bootstrap_failed_body">Couldn't load the bird archive. Try again in a moment.</string>
<string name="bootstrap_failed_retry">Try again</string>
```

- [ ] **Step 9: Bygg + device-verify**

```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am force-stop se.birdy.android
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Förväntat: launcher → splash → paper-bg med "Förbereder fältboken…" (1-3 frames) → AppScaffold. Ingen synlig frusen launcher.

- [ ] **Step 10: Commit**

```bash
git add shared/ml/src/commonMain/kotlin/se/birdy/ml/ClassifierBootstrap.kt \
        shared/ml/src/jvmTest/kotlin/se/birdy/ml/ClassifierBootstrapTest.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppGate.kt \
        androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "$(cat <<'EOF'
refactor(ml): move cold-start TFLite init off UI thread (Plan 6a Task 5)

ClassifierBootstrap exposes a StateFlow<Initializing | Ready | Failed>
that AppGate gates on, replacing the blocking runBlocking { buildClassifier() }
call in MainActivity.onCreate. UI mounts immediately; classifier init
runs in parallel on Dispatchers.Default. Failed state offers retry via
JournalDialog. Tests cover success + failure transitions.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Locale-handling (AppGraph + month-array + plurals)

**Spec ref:** §2.3 I1, I2, I4, §3.2
**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchView.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/NoBirdView.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`
- Create: `composeApp/src/jvmTest/kotlin/se/birdy/app/ui/LocaleResolverTest.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/i18n/LocaleResolver.kt`

- [ ] **Step 1: Skriv failing test för LocaleResolver**

`composeApp/src/jvmTest/kotlin/se/birdy/app/ui/LocaleResolverTest.kt`:

```kotlin
package se.birdy.app.i18n

import se.birdy.content.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleResolverTest {
    @Test
    fun override_wins_over_system() {
        val result = LocaleResolver.resolve(override = "en", systemTag = "sv-SE")
        assertEquals(Locale.EN, result)
    }

    @Test
    fun system_locale_used_when_no_override() {
        assertEquals(Locale.SV, LocaleResolver.resolve(override = null, systemTag = "sv-SE"))
        assertEquals(Locale.EN, LocaleResolver.resolve(override = null, systemTag = "en-US"))
    }

    @Test
    fun fallback_to_sv_for_unknown_system_locale() {
        assertEquals(Locale.SV, LocaleResolver.resolve(override = null, systemTag = "de-DE"))
    }
}
```

- [ ] **Step 2: Kör testet — verifiera FAIL**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.i18n.LocaleResolverTest"
```

Förväntat: FAIL — `LocaleResolver` inte definierad.

- [ ] **Step 3: Implementera LocaleResolver**

`composeApp/src/commonMain/kotlin/se/birdy/app/i18n/LocaleResolver.kt`:

```kotlin
package se.birdy.app.i18n

import se.birdy.content.Locale

object LocaleResolver {
    /**
     * Returns the Birdy [Locale] to use given an explicit user override and the
     * system's BCP-47 locale tag. Override always wins; otherwise system tag is
     * parsed; unknown languages fall back to SV.
     */
    fun resolve(override: String?, systemTag: String): Locale {
        val candidate = override ?: systemTag.substringBefore('-').lowercase()
        return when (candidate.substringBefore('-').lowercase()) {
            "sv", "se" -> Locale.SV
            "en" -> Locale.EN
            else -> Locale.SV
        }
    }
}
```

- [ ] **Step 4: Kör testet — verifiera PASS**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.i18n.LocaleResolverTest"
```

Förväntat: PASS (3/3).

- [ ] **Step 5: Wirea LocaleResolver i MainActivity**

I `MainActivity.kt`, ersätt `defaultLocale = Locale.SV` med:

```kotlin
val override = runBlocking { userPreferences.languageOverride.first() } // ny prefs-field, läggs till i T7
val systemTag = resources.configuration.locales[0].toLanguageTag()
val resolvedLocale = LocaleResolver.resolve(override, systemTag)
// ...
val graph = AppGraph(
    // ...
    defaultLocale = resolvedLocale,
    // ...
)
```

- [ ] **Step 6: Skapa locale-aware månadsnamn-array**

`composeApp/src/commonMain/composeResources/values/strings.xml` lägg till:

```xml
<string-array name="months_short_uppercase">
    <item>JAN</item><item>FEB</item><item>MAR</item><item>APR</item>
    <item>MAJ</item><item>JUN</item><item>JUL</item><item>AUG</item>
    <item>SEP</item><item>OKT</item><item>NOV</item><item>DEC</item>
</string-array>
```

`values-en/strings.xml`:

```xml
<string-array name="months_short_uppercase">
    <item>JAN</item><item>FEB</item><item>MAR</item><item>APR</item>
    <item>MAY</item><item>JUN</item><item>JUL</item><item>AUG</item>
    <item>SEP</item><item>OCT</item><item>NOV</item><item>DEC</item>
</string-array>
```

- [ ] **Step 7: Byt ut hårdkodade månadsnamn i MatchView + NoBirdView**

I `MatchView.kt:306-317`, ersätt `when (month) { 1 -> "JAN" ...}` med:

```kotlin
import org.jetbrains.compose.resources.stringArrayResource
import birdy_bird_scanner.composeapp.generated.resources.months_short_uppercase

@Composable
private fun monthShortUppercase(month: Int): String {
    val months = stringArrayResource(Res.array.months_short_uppercase)
    return months[(month - 1).coerceIn(0, 11)]
}

// och anropa `monthShortUppercase(month)` istället för `when`-blocket
```

Samma transformation i `NoBirdView.kt:58-69`.

- [ ] **Step 8: Lägg `<plurals>` för filter_apply**

`values/strings.xml` — ersätt:
```xml
<string name="filter_apply">Visa %1$d arter</string>
```
med:
```xml
<plurals name="filter_apply">
    <item quantity="one">Visa %1$d art</item>
    <item quantity="other">Visa %1$d arter</item>
</plurals>
```

`values-en/strings.xml`:
```xml
<plurals name="filter_apply">
    <item quantity="one">Show %1$d species</item>
    <item quantity="other">Show %1$d species</item>
</plurals>
```

- [ ] **Step 9: Uppdatera filter_apply call-site**

Hitta `stringResource(Res.string.filter_apply, count)` i `ArchiveScreen.kt` (eller filterns bottom-sheet) och byt till:

```kotlin
import org.jetbrains.compose.resources.pluralStringResource
// ...
text = pluralStringResource(Res.plurals.filter_apply, count, count)
```

- [ ] **Step 10: Bygg + jvmTest + manuell EN/SV-switch**

```bash
./gradlew :composeApp:testDebugUnitTest :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell cmd locale set-app-locales se.birdy.android --locales en-US
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am force-stop se.birdy.android
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Förväntat: UI är på engelska; månadsnamn visar "MAY" inte "MAJ"; alla strängar översatta.

```bash
# Återställ till svenska
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell cmd locale set-app-locales se.birdy.android --locales sv-SE
```

- [ ] **Step 11: Commit**

```bash
git add composeApp/ androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt
git commit -m "$(cat <<'EOF'
feat(i18n): locale-aware defaultLocale + months array + plurals (Plan 6a Task 6)

LocaleResolver derives Birdy Locale from system tag (override-first
chain). MatchView/NoBirdView use stringArrayResource for month
abbreviations instead of hardcoded sv when-blocks. filter_apply now uses
pluralStringResource ("Visa 1 art" vs "Visa 5 arter").

Language picker wiring lands in Task 7; this task makes the system
locale finally respected.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Language-picker wireup

**Spec ref:** §2.3 I3
**Files:**
- Modify: `shared/datastore/.../UserPreferences.kt` (nya `languageOverride`-property)
- Modify: `composeApp/.../ui/settings/SettingsViewModel.kt`
- Modify: `composeApp/.../ui/settings/SettingsScreen.kt`
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`
- Modify: `androidApp/build.gradle.kts` (lägg `androidx.appcompat:appcompat`)
- Create: `composeApp/.../ui/settings/SettingsEffect.kt`
- Create: `composeApp/src/jvmTest/kotlin/se/birdy/app/ui/settings/SettingsViewModelLanguageTest.kt`

- [ ] **Step 1: Lägg AppCompat-dependency**

I `gradle/libs.versions.toml`:
```toml
[versions]
androidx-appcompat = "1.7.0"
[libraries]
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "androidx-appcompat" }
```

I `androidApp/build.gradle.kts`:
```kotlin
implementation(libs.androidx.appcompat)
```

- [ ] **Step 2: Lägg `languageOverride` i UserPreferences**

I `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt` (eller motsv.), lägg:

```kotlin
val languageOverride: Flow<String?>
suspend fun setLanguageOverride(tag: String?)
```

I `UserPreferencesStore.kt` (androidMain implementation), lägg DataStore-Preferences-key:

```kotlin
private val LANGUAGE_OVERRIDE = stringPreferencesKey("language_override")

override val languageOverride: Flow<String?> =
    dataStore.data.map { it[LANGUAGE_OVERRIDE] }

override suspend fun setLanguageOverride(tag: String?) {
    dataStore.edit { prefs ->
        if (tag == null) prefs.remove(LANGUAGE_OVERRIDE)
        else prefs[LANGUAGE_OVERRIDE] = tag
    }
}
```

- [ ] **Step 3: Skriv failing test för SettingsViewModel.changeLanguage**

`composeApp/src/jvmTest/kotlin/se/birdy/app/ui/settings/SettingsViewModelLanguageTest.kt`:

```kotlin
package se.birdy.app.ui.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsViewModelLanguageTest {
    @Test
    fun changeLanguage_sv_writes_override_and_emits_recreate_effect() = runTest {
        val fakePrefs = FakeUserPreferences()
        val vm = SettingsViewModel(fakePrefs, FakePremiumRepository())

        vm.changeLanguage(AppLanguage.SV)

        assertEquals("sv", fakePrefs.languageOverride.first())
        val effect = vm.effects.first()
        assertEquals(SettingsEffect.RestartForLocale("sv"), effect)
    }

    @Test
    fun changeLanguage_system_clears_override() = runTest {
        val fakePrefs = FakeUserPreferences(initialOverride = "en")
        val vm = SettingsViewModel(fakePrefs, FakePremiumRepository())

        vm.changeLanguage(AppLanguage.SYSTEM)

        assertEquals(null, fakePrefs.languageOverride.first())
    }
}
```

(Lägg `FakeUserPreferences` + `FakePremiumRepository`-test-fakes om de inte redan finns; återanvänd från Plan 7e:s tests.)

- [ ] **Step 4: Kör testet — verifiera FAIL**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.settings.SettingsViewModelLanguageTest"
```

Förväntat: FAIL — `SettingsEffect` saknas eller `changeLanguage`-impl skickar inte effect.

- [ ] **Step 5: Skapa SettingsEffect + implementera changeLanguage**

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsEffect.kt`:

```kotlin
package se.birdy.app.ui.settings

sealed interface SettingsEffect {
    data class RestartForLocale(val tag: String) : SettingsEffect
    data object OpenPrivacyUrl : SettingsEffect
    data object OpenTermsUrl : SettingsEffect
    data object RateOnPlayStore : SettingsEffect
    data object ShareApp : SettingsEffect
    data object SendFeedback : SettingsEffect
    data object OpenAbout : SettingsEffect
}
```

I `SettingsViewModel.kt`, lägg:

```kotlin
private val _effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

fun changeLanguage(lang: AppLanguage) {
    viewModelScope.launch {
        val tag: String? = when (lang) {
            AppLanguage.SV -> "sv"
            AppLanguage.EN -> "en"
            AppLanguage.SYSTEM -> null
        }
        prefs.setLanguageOverride(tag)
        if (tag != null) _effects.tryEmit(SettingsEffect.RestartForLocale(tag))
        else _effects.tryEmit(SettingsEffect.RestartForLocale(""))  // empty = follow system
    }
}
```

- [ ] **Step 6: Kör test — verifiera PASS**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.settings.SettingsViewModelLanguageTest"
```

Förväntat: PASS (2/2).

- [ ] **Step 7: Wirea effect-collector i SettingsScreen + MainActivity**

I `SettingsScreen.kt`, lägg `LaunchedEffect`:

```kotlin
val context = LocalContext.current
LaunchedEffect(Unit) {
    viewModel.effects.collect { effect ->
        when (effect) {
            is SettingsEffect.RestartForLocale -> {
                val locales = if (effect.tag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(effect.tag)
                }
                AppCompatDelegate.setApplicationLocales(locales)
            }
            // andra effects wireas i T13
            else -> { /* TBD i T13 */ }
        }
    }
}
```

(Import: `androidx.appcompat.app.AppCompatDelegate`, `androidx.core.os.LocaleListCompat`.)

**Note:** Eftersom `setApplicationLocales` är androidx-only, måste denna `LaunchedEffect` flyttas till androidMain via expect/actual om annan platform stöds. För 6a (Android-only) räcker direct import.

- [ ] **Step 8: Bygg + device-verify locale-switch**

```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
# Navigera till Settings → Language → välj "English" → bekräfta att UI byts utan kill
```

Förväntat: ≤ 500 ms recreate-flicker; UI är engelska; bottom-bar labels på engelska; Premium-skärmen på engelska.

- [ ] **Step 9: Commit**

```bash
git add shared/datastore/ composeApp/ androidApp/build.gradle.kts \
        gradle/libs.versions.toml
git commit -m "$(cat <<'EOF'
feat(settings): wire Language picker via AppCompatDelegate (Plan 6a Task 7)

SettingsViewModel.changeLanguage writes languageOverride to DataStore and
emits SettingsEffect.RestartForLocale. SettingsScreen collects effects
and calls AppCompatDelegate.setApplicationLocales — Activity recreates,
LocaleResolver picks new override on cold-boot.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Delade komponenter (JournalLoading/Empty/Dialog/Scaffold)

**Spec ref:** §2.2
**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalLoading.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalDialog.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalScaffold.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/EmptyState.kt` (utöka)
- Migrera 8+ call-sites — se Step 5

- [ ] **Step 1: Skapa JournalLoading**

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalLoading.kt`:

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.journal_loading_default
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat

@Composable
fun JournalLoading(
    modifier: Modifier = Modifier,
    label: String = stringResource(Res.string.journal_loading_default),
) {
    Box(
        modifier = modifier.fillMaxSize().paperBackground(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CircularProgressIndicator(color = AccentCopper)
            androidx.compose.material3.Text(
                text = label,
                fontFamily = rememberCaveat(),
                fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp),
                color = MarginaliaInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}
```

Lägg sträng `journal_loading_default = "Bläddrar i fältboken…"` (sv) och `"Browsing the field journal…"` (en) i `strings.xml`.

- [ ] **Step 2: Skapa JournalDialog**

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalDialog.kt`:

```kotlin
package se.birdy.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

@Composable
fun JournalDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = onConfirm,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = rememberDmSerifDisplay(), color = TextOnCreme) },
        text = { Text(body, fontFamily = rememberCaveat(), color = TextOnCreme) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = AccentCopper, fontFamily = rememberCaveat())
            }
        },
        dismissButton = dismissLabel?.let {
            {
                TextButton(onClick = onDismiss) {
                    Text(it, color = TextOnCreme.copy(alpha = 0.6f), fontFamily = rememberCaveat())
                }
            }
        },
        containerColor = PaperTop,
    )
}
```

- [ ] **Step 3: Skapa JournalScaffold**

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/JournalScaffold.kt`:

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import se.birdy.app.ui.theme.paperBackground

@Composable
fun JournalScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().paperBackground(),
        containerColor = Color.Transparent,
        topBar = topBar,
        bottomBar = bottomBar,
        content = content,
    )
}
```

- [ ] **Step 4: Utöka EmptyState med action-slot**

I `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/EmptyState.kt`, lägg `action: (@Composable () -> Unit)? = null` som parameter och renderera den under body.

```kotlin
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // ... befintlig title + body
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}
```

- [ ] **Step 5: Migrera call-sites**

Sök efter `CircularProgressIndicator(` + `Text("Laddar"` + `Text("Laddar...")` + `AlertDialog(` i `composeApp/src/commonMain`:

```bash
# Använd Grep-tool inte bash
```

Förväntade migration-sites:
- `EncyclopediaScreen.kt:180` → `JournalLoading()`
- `SpeciesProfileScreen.kt:88-92` → `JournalLoading()`
- `LifelistScreen.kt` (laddar-state) → `JournalLoading()`
- `BadgesScreen.kt` (loading) → `JournalLoading()`
- `ObservationDetailScreen.kt` (CircularProgressIndicator) → `JournalLoading()`
- `ObservationDetailScreen.kt:260-277` AlertDialog → `JournalDialog`
- `SettingsScreen.kt` NameEditDialog → behåller egen, men `containerColor = PaperTop` (redan på plats)
- AppGate `SplashLoading` → byta till `JournalLoading()`

Sätt `JournalScaffold` istället för `Scaffold(containerColor=Color.Transparent)` på alla huvudskärmar (Encyclopedia, Lifelist, Badges, Settings, SpeciesProfile, ObservationDetail).

- [ ] **Step 6: Bygg + smoke-test**

```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
# Manuellt klicka genom alla skärmar; verifiera att loading-states ser likadana ut
```

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/ \
        composeApp/src/commonMain/composeResources/values*/strings.xml \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/
git commit -m "$(cat <<'EOF'
refactor(ui): shared JournalLoading/Dialog/Scaffold + EmptyState action (Plan 6a Task 8)

Replace ad-hoc Text("Laddar") + naked CircularProgressIndicator with
JournalLoading(label). Replace 2 M3 AlertDialogs with JournalDialog
(PaperTop bg, Caveat body, AccentCopper buttons). JournalScaffold
encapsulates Scaffold(transparent) + paperBackground for all main
screens. EmptyState gets optional action slot.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: A11y bumps (contentDescription + semantics + kontrast)

**Spec ref:** §2.3 I5, I6, I7
**Files:**
- Modify: `composeApp/.../ui/theme/Color.kt` (MarginaliaInk-bump)
- Modify: 6 AsyncImage call-sites (se §2.3 I5)
- Modify: `composeApp/.../ui/components/StampSeal.kt`
- Modify: `composeApp/.../ui/components/PlateFrame.kt`
- Modify: `composeApp/.../ui/components/JournalHeadline.kt`
- Modify: `composeApp/.../ui/components/JournalSubLine.kt`
- Modify: `composeApp/.../ui/scaffold/BottomNavBar.kt:127`

- [ ] **Step 1: Bumpa MarginaliaInk-kontrast**

I `Color.kt:63`:
```kotlin
val MarginaliaInk = Color(0xFF3F4F30)  // var 0xFF5C6E48 — bumpa till HeroMossMid för WCAG AA
```

(Behåll `HeroMossMid` separat om den används elsewhere.) Verifiera visuellt på paper-bg att text fortfarande läses som "marginalia" + inte konkurrerar med rubriker.

- [ ] **Step 2: Lägg contentDescription på 6 AsyncImage-call-sites**

Varje call-site får en `contentDescription`-parameter (eller via wrapping `Modifier.semantics`):

`HeroImage.kt:43`:
```kotlin
AsyncImage(
    model = url,
    contentDescription = species?.commonName?.let { "Foto av $it" },  // null för dekorativ
    // ...
)
```

Samma princip på `PremiumHeroCard.kt:61`, `SpeciesProfileScreen.kt:150`, `ObservationDetailScreen.kt:212`, `ArchiveScreen.kt:347`, `CircularThumb.kt:35`.

- [ ] **Step 3: Lägg `Modifier.semantics` på custom-components**

`StampSeal.kt`: Sätt på rot-`Box`:
```kotlin
.semantics {
    role = Role.Button
    contentDescription = when (state) {
        is StampSealState.Locked -> "$badgeName, låst märke. Tryck för mer info."
        is StampSealState.InProgress -> "$badgeName, ${state.progress}% klart."
        is StampSealState.Unlocked -> "$badgeName, upplåst märke."
    }
    mergeDescendants = true
}
```

`PlateFrame.kt`: `mergeDescendants = true` + contentDescription = stamp-number + species name.
`JournalHeadline.kt`, `JournalSubLine.kt`: `Modifier.semantics { mergeDescendants = true }`.

`BottomNavBar.kt:127`: `Icon(tab.icon, contentDescription = stringResource(tab.labelRes))`.

- [ ] **Step 4: Bygg + TalkBack-test**

```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Klicka manuellt genom: scan → match → save → archive → species → badges → settings. Verifiera att varje interaktiv komponent annonseras meningsfullt.

Stäng av TalkBack efter testet:
```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell settings put secure enabled_accessibility_services ""
```

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/
git commit -m "$(cat <<'EOF'
a11y: contentDescription + semantics + AA contrast bump (Plan 6a Task 9)

- MarginaliaInk #5C6E48 → #3F4F30 (HeroMossMid) for WCAG AA 4.5:1
- contentDescription on 6 AsyncImage call-sites (species photo context)
- Modifier.semantics on StampSeal (role=Button + state-aware label),
  PlateFrame, JournalHeadline, JournalSubLine (mergeDescendants)
- BottomNavBar Icon contentDescription = tab label

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: UX-fixar A1-A13

**Spec ref:** §2.1 (alla 13 items)
**Files:** många — se per-item nedan
**Note:** Detta är den största task:en. Dela upp i 5 commits — en per kategori.

### Subtask 10.1: Cold-start-tipset i AppGate (A1)

Klar i Task 5 — verifiera att `Initializing` visar "Förbereder fältboken…" via JournalLoading (T8). Inget extra arbete.

### Subtask 10.2: Encyclopedia (A2)

**Files:** `ArchiveScreen.kt`, `ArchiveViewModel.kt`, `ArchiveUiState.kt`

- [ ] **Skeleton-loader:** Ersätt `Text("Laddar...")` i Loading-state med en LazyColumn av 8 `SpeciesRowSkeleton`-komponenter (en ny lokal composable som renderar grå rektanglar i `MarginaliaInk.alpha=.1f` i samma höjd som `SpeciesRow`).

- [ ] **✕-knapp i sökfältet:** I `OutlinedTextField`:
```kotlin
trailingIcon = if (state.query.isNotEmpty()) {
    {
        IconButton(onClick = { viewModel.setQuery("") }) {
            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.archive_search_clear))
        }
    }
} else null
```

- [ ] **Sticky family-headers:** I `LazyColumn` vid `ArchiveSort.FAMILY`, gruppera efter `species.family` och använd `stickyHeader { JournalSubLine(family.name) }`.

- [ ] **Error-state:** Lägg `Error(message)`-branch i `ArchiveUiState`; rendera med retry-CTA.

### Subtask 10.3: Diary empty-state (A3) + månadsgruppering

**Files:** `LifelistScreen.kt:118-140`

- [ ] **Empty-state-redesign:**
```kotlin
if (state.observations.isEmpty()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StampSeal(
            state = StampSealState.Locked,
            badgeName = stringResource(Res.string.lifelist_empty_stamp_name),
            size = 88.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(Res.string.lifelist_empty_marginalia),
            fontFamily = rememberCaveat(),
            color = MarginaliaInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onScanFirst, colors = ButtonDefaults.buttonColors(AccentCopper)) {
            Text(stringResource(Res.string.lifelist_empty_cta))
        }
    }
    return
}
```

Lägg strängar:
- `lifelist_empty_stamp_name = "Första fyndet"` / `"First sighting"`
- `lifelist_empty_marginalia = "Din fältbok är tom — / skanna för att skriva första sidan."` / `"Your field journal is empty — / scan to write the first page."`
- `lifelist_empty_cta = "Skanna en fågel"` / `"Scan a bird"`

- [ ] **Månadsgruppering:** I observations-LazyColumn vid `ArchiveSort.RECENT`, gruppera observations efter `LocalDate(savedAt).month` + `year`. Använd `stickyHeader { JournalSubLine("maj 2026 · 12 fynd") }`. Använd locale-aware-månadsnamn.

- [ ] **Fejk year/month-stats dölj:** I `StatColumn(stat3)`, dölj `SPECIES_THIS_YEAR` och `SPECIES_THIS_MONTH` cycle-states. Bara `SPECIES_TOTAL` blir kvar. Lägg `TODO` med ref till Plan 6c.

### Subtask 10.4: Locked-badge progress-hint (A4)

**Files:** `BadgesScreen.kt:102-104`, ny `LockedBadgeBottomSheet.kt`

- [ ] Ersätt nuvarande snackbar-on-locked-tap med en `ModalBottomSheet`:

```kotlin
if (selectedLocked != null) {
    ModalBottomSheet(onDismissRequest = { selectedLocked = null }) {
        Column(Modifier.padding(24.dp)) {
            JournalHeadline(text = selectedLocked.name, ...)
            Spacer(Modifier.height(16.dp))
            when (val s = state.gridStateFor(selectedLocked.id)) {
                is BadgeGridState.Locked -> Text(stringResource(Res.string.badge_hint_locked))
                is BadgeGridState.InProgress -> {
                    LinearProgressIndicator(progress = s.progress, color = AccentCopper)
                    Text("${(s.progress * 100).toInt()}% — ${s.hint}")
                }
                BadgeGridState.Hidden -> Text(stringResource(Res.string.badge_hint_hidden))
                else -> Unit
            }
        }
    }
}
```

Lägg helpfully `BadgeGridState.InProgress(val progress: Float, val hint: String)` med data hämtad från badge-rule-evaluation. För `Hidden`-state behålls "Hemligt — fortsätt skåda".

### Subtask 10.5: NoBird actionable tips + top-1-hint (A5)

**Files:** `NoBirdView.kt:128-148`, `MatchResultViewModel.kt`

- [ ] Lägg en topPrediction-fält i `NoBirdView`-state:
```kotlin
data class NoBird(
    val topPrediction: TopPrediction?,  // null om all conf < 0.15
    val frameJpegPath: String?,
) : MatchUiState
```

- [ ] Rendera under tre marginalia-tipsen:
```kotlin
state.topPrediction?.let { p ->
    Text(
        stringResource(Res.string.nobird_top_guess_hint, p.commonName, "${(p.confidence * 100).toInt()}%"),
        fontFamily = rememberCaveat(),
        fontStyle = FontStyle.Italic,
        color = MarginaliaInk,
    )
}
```

`nobird_top_guess_hint` = `"modellen tyckte den såg en *möjlig %1$s* (%2$s) — kom närmare?"` på SV; EN matchande.

- [ ] Lägg sub-text per tip (Inter, 11sp):
```kotlin
Row { 
    Text(tip.title, fontFamily = rememberCaveat())
    Spacer(...)
    Text(tip.subText, fontSize = 11.sp, fontFamily = FontFamily.Default)
}
```

### Subtask 10.6: Disambig "Spara som okänd" (A6) + DB-migration

**Files:** `DisambigView.kt:110-119`, `MatchResultViewModel.kt`, SQLDelight migrations

- [ ] **DB-migration:** I `shared/data/src/commonMain/sqldelight/.../migrations/`, lägg `2.sqm`:

```sql
ALTER TABLE Observation RENAME TO Observation_old;
CREATE TABLE Observation (
    id TEXT NOT NULL PRIMARY KEY,
    species_id TEXT,
    saved_at INTEGER NOT NULL,
    confidence REAL NOT NULL,
    photo_path TEXT,
    note TEXT,
    latitude REAL,
    longitude REAL,
    location_label TEXT
);
INSERT INTO Observation (id, species_id, saved_at, confidence, photo_path, note, latitude, longitude, location_label)
SELECT id, species_id, saved_at, confidence, photo_path, note, latitude, longitude, location_label
FROM Observation_old;
DROP TABLE Observation_old;
```

Bumpa DB-version. Uppdatera ev. queries som tidigare antog NOT NULL.

- [ ] **DisambigView UI:** Lägg tredje knapp:
```kotlin
TextButton(
    onClick = { viewModel.saveAsUnknown() },
    modifier = Modifier.fillMaxWidth(),
) {
    Text(stringResource(Res.string.disambig_save_unknown), color = AccentCopper)
}
```

Sträng `disambig_save_unknown = "Spara som okänd"` / `"Save as unknown"`.

- [ ] **ViewModel:** `MatchResultViewModel.saveAsUnknown()` skapar en observation med `species_id = null`. UnlockQueue triggar inte (eftersom inget species).

### Subtask 10.7: Save-flow inline edit-note (A7)

**Files:** `MatchView.kt`, `MatchResultViewModel.kt:179`

- [ ] Lägg `OutlinedTextField` ovanför Save-knappen i `MatchView.kt`:

```kotlin
var note by rememberSaveable { mutableStateOf("") }
OutlinedTextField(
    value = note,
    onValueChange = { note = it },
    label = { Text(stringResource(Res.string.match_note_label), fontFamily = rememberCaveat()) },
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions = KeyboardActions(onDone = {
        viewModel.save(note = note.trim())
    }),
    modifier = Modifier.fillMaxWidth(),
    colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AccentCopper,
        cursorColor = AccentCopper,
    ),
)
```

Uppdatera `MatchResultViewModel.save()` att ta `note: String = ""`-parameter och skicka till `SaveObservationUseCase`.

### Subtask 10.8: DEMO-banner recovery + tap-to-freeze fix (A8, A9)

**Files:** `ScanScreen.kt`

- [ ] **DEMO-banner tap:** Wrap bannern i `Modifier.clickable { showDemoBottomSheet = true }`. Bottom-sheet:
```kotlin
if (showDemoBottomSheet) {
    ModalBottomSheet(onDismissRequest = { showDemoBottomSheet = false }) {
        Column(Modifier.padding(24.dp)) {
            JournalHeadline(stringResource(Res.string.demo_sheet_title))
            Text(stringResource(Res.string.demo_sheet_body))
            Row {
                Button(onClick = { viewModel.retryClassifierInit() }) {
                    Text(stringResource(Res.string.demo_sheet_retry))
                }
                Button(onClick = { /* mailto */ }) {
                    Text(stringResource(Res.string.demo_sheet_report))
                }
            }
        }
    }
}
```

- [ ] **Tap-to-freeze fix:** I `ScanScreen.kt:79-90` ersätt `awaitPointerEvent()` med `detectTapGestures { offset -> ... }`. Verifiera att tap på "Analysera ett foto"-knappen inte längre fryser frame.

### Subtask 10.9: Permission flow (A10) + Onboarding (A11) + Strings (A12, A13)

**Files:** `ScanScreen.kt`, `OnboardingScreen.kt`, `strings.xml`

- [ ] **PermissionRequiredView hero:**
```kotlin
@Composable
fun PermissionRequiredView(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(96.dp)
                .border(2.dp, AccentCopper, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AccentCopper)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(Res.string.permission_hero_caveat),
            fontFamily = rememberCaveat(),
            color = MarginaliaInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(AccentCopper)) {
            Text(stringResource(Res.string.permission_grant_cta))
        }
    }
}
```

Strängar: `permission_hero_caveat = "Birdy ser bara fåglar — / inga foton sparas utan ditt val."` / EN matchande.

- [ ] **PermissionDeniedView foto-fallback:**

```kotlin
OutlinedButton(onClick = onAnalyzePhotoInstead) {
    Text(stringResource(Res.string.permission_denied_photo_fallback))
}
```

Navigera till `AppRoute.PhotoAnalyze`.

- [ ] **Onboarding BackHandler + Skip-fix:**

```kotlin
BackHandler(enabled = state.pageIndex > 0) {
    onPageChange(state.pageIndex - 1)
}

// Skip-knapp:
TextButton(onClick = onComplete) {  // var: onPageChange(PAGE_COUNT - 1)
    Text(stringResource(Res.string.onboarding_skip))
}
```

Uppdatera `onboarding_p3_input_helper` till "Lämna tomt om du vill — vi kallar dig 'Fältornitolog'." / EN matchande.

- [ ] **"OBS #N · FÖRSTA SÅGS"-copy:** Uppdatera `match_sub_repeat`-strängen i `values/strings.xml`:
```xml
<string name="match_sub_repeat">OBS #%1$d · FÖRSTA SÅGS: %2$s</string>
```
EN:
```xml
<string name="match_sub_repeat">SIGHT #%1$d · FIRST SEEN: %2$s</string>
```

- [ ] **Throttle-indikator bort:** I `TopChip.kt:91-94`, ta bort `"1.5 fps"`-rendering. Lämna chip helt enkelt eller visa bara emoji-text.

### Subtask 10.10: ObservationDetail edit-note rememberSaveable

**Files:** `ObservationDetailScreen.kt:167`

- [ ] Ersätt `var noteText by remember(state.observation.id) { mutableStateOf(state.observation.note) }` med `rememberSaveable`. Lägg "Avbryt"-knapp bredvid "Spara".

### Step: Bygg + commit subtask-grouped

Eftersom T10 är så stor, gör 5 commits:

```bash
git add <subtask 10.2 files>
git commit -m "feat(encyclopedia): skeleton + clear button + sticky family headers (Plan 6a Task 10.2)"

git add <subtask 10.3 files>
git commit -m "feat(diary): empty-state redesign + month grouping + hide fake stats (Plan 6a Task 10.3)"

git add <subtask 10.4 files>
git commit -m "feat(badges): locked-badge progress bottom-sheet (Plan 6a Task 10.4)"

git add <subtask 10.5+10.6+10.7 files>
git commit -m "feat(match): NoBird hints + Disambig save-as-unknown + inline note (Plan 6a Task 10.5-7)"

git add <subtask 10.8+10.9+10.10 files>
git commit -m "feat(scan): DEMO recovery + tap-fix + permission hero + onboarding back + copy fixes (Plan 6a Task 10.8-10)"
```

---

## Task 11: Onboarding system-back + Skip-fix

Klar inom T10.9 — verifiera att `BackHandler` och `Skip → onComplete()` fungerar via:

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell pm clear se.birdy.android
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
# Navigera till sida 2; tryck system-back; förvänta sida 1
# Tryck Skip på sida 1; förvänta direkt till AppScaffold
```

---

## Task 12: Manifest (queries + backup-rules + cleartext)

**Spec ref:** §2.6 R5, R6, R7
**Files:**
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Create: `androidApp/src/main/res/xml/data_extraction_rules.xml`
- Create: `androidApp/src/main/res/xml/backup_rules.xml`

- [ ] **Step 1: Skapa backup_rules.xml**

`androidApp/src/main/res/xml/backup_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <include domain="database" path="."/>
    <include domain="sharedpref" path="."/>
    <include domain="file" path="datastore/"/>
    <include domain="file" path="observations/"/>
</full-backup-content>
```

- [ ] **Step 2: Skapa data_extraction_rules.xml**

`androidApp/src/main/res/xml/data_extraction_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <include domain="database" path="."/>
        <include domain="sharedpref" path="."/>
        <include domain="file" path="datastore/"/>
        <include domain="file" path="observations/"/>
    </cloud-backup>
    <device-transfer>
        <include domain="database" path="."/>
        <include domain="sharedpref" path="."/>
        <include domain="file" path="datastore/"/>
        <include domain="file" path="observations/"/>
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Step 3: Uppdatera AndroidManifest**

`androidApp/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera.any" android:required="false" />

    <queries>
        <intent>
            <action android:name="android.media.action.IMAGE_CAPTURE" />
        </intent>
        <intent>
            <action android:name="android.intent.action.SENDTO" />
            <data android:scheme="mailto" />
        </intent>
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="https" />
        </intent>
    </queries>

    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:usesCleartextTraffic="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:theme="@style/Theme.Birdy.Starting">
        <!-- ... rest oförändrad -->
    </application>
</manifest>
```

- [ ] **Step 4: Verifiera manifest-parse**

```bash
./gradlew :androidApp:assembleDebug
# Kontrollera att build går grön
```

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/main/AndroidManifest.xml androidApp/src/main/res/xml/
git commit -m "$(cat <<'EOF'
build(android): manifest queries + backup rules + cleartext=false (Plan 6a Task 12)

<queries> for camera + mailto + https intents (API 30+ requirement).
Backup rules include DataStore, SQLDelight DB, observations photo dir
— users keep their field journal across device transfers.
usesCleartextTraffic=false matches our offline-only architecture.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Settings-rader (Privacy/Terms/About/Rate/Share/Feedback)

**Spec ref:** §2.5
**Files:**
- Modify: `composeApp/.../ui/settings/SettingsScreen.kt`
- Modify: `composeApp/.../ui/settings/SettingsViewModel.kt`
- Create: `composeApp/.../ui/settings/AboutScreen.kt`
- Create: `composeApp/.../ui/settings/AboutViewModel.kt`
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` (effect-handler)
- Modify: `composeApp/.../navigation/AppRoute.kt` (lägg `Settings.About`)
- Modify: `composeApp/src/commonMain/composeResources/values*/strings.xml`

- [ ] **Step 1: Lägg About-route**

I `AppRoute.kt`:
```kotlin
sealed interface AppRoute {
    // ...
    data object About : AppRoute
}
```

I `Navigation.kt`, registrera:
```kotlin
composable("about") { AboutScreen(onBack = { navController.popBackStack() }, version = appGraph.versionName) }
```

- [ ] **Step 2: Skapa AboutScreen**

`composeApp/.../ui/settings/AboutScreen.kt`:

```kotlin
@Composable
fun AboutScreen(onBack: () -> Unit, version: String) {
    JournalScaffold(topBar = { /* back-row */ }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState())) {
            JournalIntro(
                eyebrow = stringResource(Res.string.about_eyebrow),
                headline = stringResource(Res.string.about_headline),
            )
            OrnamentRule()
            Text("Birdy v$version", fontFamily = rememberDmSerifDisplay())
            Spacer(Modifier.height(16.dp))
            MicroLabel(stringResource(Res.string.about_credits_label))
            Text(stringResource(Res.string.about_credits_body))
            Spacer(Modifier.height(16.dp))
            MicroLabel(stringResource(Res.string.about_licenses_label))
            Text(LICENSE_TEXT_LONG_MANUAL_LIST)
        }
    }
}

private val LICENSE_TEXT_LONG_MANUAL_LIST = """
    Kotlin, Compose Multiplatform — Apache 2.0
    SQLDelight — Apache 2.0
    Coil — Apache 2.0
    AndroidX — Apache 2.0
    kaml — Apache 2.0
    TensorFlow Lite (AIY Birds V1) — Apache 2.0
    DM Serif Display Italic — SIL Open Font License 1.1
    Caveat — SIL Open Font License 1.1
""".trimIndent()
```

Strängar:
- `about_eyebrow = "OM"` / `"ABOUT"`
- `about_headline = "*Birdy* — / *fältornitologens* / digitala bok."` / `"*Birdy* — / a *field birder's* / digital companion."`
- `about_credits_label = "INNEHÅLL & DATA"` / `"CONTENT & DATA"`
- `about_credits_body = "Fågeldata från Wikidata + Wikipedia under CC BY-SA. Foton från Wikimedia Commons. ML-modell: AIY Birds V1 (Google)."` / EN matchande
- `about_licenses_label = "OPEN SOURCE"` / `"OPEN SOURCE"`

- [ ] **Step 3: Wire effect-handler för 5 rader**

I `SettingsScreen.kt`-`LaunchedEffect` (utöka från T7):

```kotlin
viewModel.effects.collect { effect ->
    when (effect) {
        is SettingsEffect.RestartForLocale -> { /* T7 */ }
        SettingsEffect.OpenPrivacyUrl -> {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://anonadrek.github.io/birdy/privacy")))
        }
        SettingsEffect.OpenTermsUrl -> {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://anonadrek.github.io/birdy/terms")))
        }
        SettingsEffect.RateOnPlayStore -> {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=se.birdy.android"))
            runCatching { context.startActivity(intent) }.onFailure {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=se.birdy.android")))
            }
        }
        SettingsEffect.ShareApp -> {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_copy_resolved))
            }
            context.startActivity(Intent.createChooser(send, null))
        }
        SettingsEffect.SendFeedback -> {
            val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:feedback@birdy.app"))
            mail.putExtra(Intent.EXTRA_SUBJECT, "Birdy v${BuildConfig.VERSION_NAME} feedback")
            context.startActivity(mail)
        }
        SettingsEffect.OpenAbout -> {
            onNavigate(AppRoute.About)
        }
    }
}
```

- [ ] **Step 4: Wire UI-rader**

I `SettingsScreen.kt`, ändra varje `SettingsRow` `onClick`:

```kotlin
SettingsRow(label = stringResource(Res.string.settings_rate), onClick = { viewModel.rateOnPlayStore() })
SettingsRow(label = stringResource(Res.string.settings_share), onClick = { viewModel.shareApp() })
SettingsRow(label = stringResource(Res.string.settings_feedback), onClick = { viewModel.sendFeedback() })
SettingsRow(label = stringResource(Res.string.settings_about), onClick = { viewModel.openAbout() })
SettingsRow(label = stringResource(Res.string.settings_privacy), onClick = { viewModel.openPrivacy() })
SettingsRow(label = stringResource(Res.string.settings_terms), onClick = { viewModel.openTerms() })
```

Och i `SettingsViewModel`, lägg `rateOnPlayStore() { _effects.tryEmit(SettingsEffect.RateOnPlayStore) }` osv (6 metoder).

- [ ] **Step 5: Bygg + manuell test**

```bash
./gradlew :androidApp:installDebug
# Klicka varje rad. Verifiera:
# - Rate → Play Store öppnar (eller browser om Play saknas på enheten)
# - Share → systemets share-sheet
# - Feedback → mailto-app öppnar
# - About → AboutScreen renderas; tillbaka funkar
# - Privacy → browser öppnar GitHub Pages URL
# - Terms → samma
```

- [ ] **Step 6: Commit**

```bash
git add composeApp/ androidApp/
git commit -m "$(cat <<'EOF'
feat(settings): wire all 6 rows + AboutScreen (Plan 6a Task 13)

Rate (Play Store deeplink + web fallback), Share (ACTION_SEND), Feedback
(mailto with version subject), About (new screen with credits + license
list), Privacy + Terms (ACTION_VIEW to GitHub Pages URLs). Effects emitted
from ViewModel; collected in SettingsScreen LaunchedEffect.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Play Store-artefakter + GitHub Pages

**Spec ref:** §2.7
**Files:**
- Create: `docs/play-store/privacy-policy.md`
- Create: `docs/play-store/terms.md`
- Create: `docs/play-store/store-listing-sv.md`
- Create: `docs/play-store/store-listing-en.md`
- Create: `docs/play-store/data-safety-form.md`
- Create: `.github/workflows/pages.yml`

- [ ] **Step 1: Skriv privacy-policy.md**

Basera på Google's Play Store template; täck:
- Vad samlas in: ingen användardata (on-device only)
- Camera permission: ström för identifiering, inga frames sparas utan användarval
- Photos permission: bara läs användarval; sparade observation-foton i `filesDir/observations/`
- SQLDelight DB: lokal lagring av observations, badges, premium-state
- Tredjeparts-trackers: inga
- Data-radering: app uninstall raderar alla data; backup till Google Drive om aktiverat
- Kontakt: feedback@birdy.app (eller mailto-rad i Settings)

- [ ] **Step 2: Skriv terms.md**

Korta villkor:
- Åldersgräns: 13+
- Licens: personlig användning, inte commercial redistribution
- Disclaimer: ML-classification är ungefärlig, inte ersättning för fältornitolog
- Inga garantier för 100% accuracy
- Tvist: svensk rätt, Stockholms tingsrätt

- [ ] **Step 3: Skriv store-listing-sv.md + store-listing-en.md**

Mallar:
```
# Birdy — Fågelskanner

## Kort beskrivning (80 tecken)
Skanna fåglar med kameran, lär dig om dem, samla i din fältbok.

## Lång beskrivning (4000 tecken)
Birdy är en AI-driven fältbok för fågelskådare...

## ASO-nyckelord
fågel, fågelskanner, fågelguide, fågelid, AI-fågel, naturapp, ornitologi...

## What's new (rc1)
- Första Play Store-versionen
- Skanna ~700 europeiska fågelarter
- ...
```

- [ ] **Step 4: Skriv data-safety-form.md**

Svaren till Play Console Data Safety:
- Data collected: None
- Data shared: None
- Data encrypted: N/A
- User control: Uninstall to delete
- Permissions: Camera (foreground only), Photos (READ_MEDIA_IMAGES via PickVisualMedia — kräver inget explicit permission på API 33+)

- [ ] **Step 5: Sätt upp GitHub Pages-workflow**

`.github/workflows/pages.yml`:

```yaml
name: Deploy GitHub Pages

on:
  push:
    branches: [main]
    paths: ['docs/play-store/**']
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - uses: actions/checkout@v4
      - name: Build site
        run: |
          mkdir -p _site
          # Konvertera md till html (enkel mall)
          for f in docs/play-store/*.md; do
            name=$(basename "$f" .md)
            echo "<!doctype html><html><body><pre>$(cat $f)</pre></body></html>" > "_site/$name.html"
          done
          # Index
          echo "<a href='privacy-policy.html'>Privacy</a> · <a href='terms.html'>Terms</a>" > _site/index.html
      - uses: actions/configure-pages@v4
      - uses: actions/upload-pages-artifact@v3
        with:
          path: _site
      - id: deployment
        uses: actions/deploy-pages@v4
```

- [ ] **Step 6: Push till main och aktivera Pages**

```bash
git add docs/play-store/ .github/workflows/pages.yml
git commit -m "docs(play-store): privacy + terms + listing + data-safety + Pages workflow (Plan 6a Task 14)"
git push
```

Aktivera Pages i GitHub Settings → Pages → Source: GitHub Actions. Verifiera att `https://anonadrek.github.io/birdy/privacy-policy.html` är live.

- [ ] **Step 7: Bekräfta URLer**

URLerna som används i T13 (`https://anonadrek.github.io/birdy/privacy`) — bestäm:
- Om filename i Pages = `privacy-policy.html`, byt T13:s Intent till `.../privacy-policy.html`
- Eller lägg ett redirect-html `privacy.html` som pekar mot `privacy-policy.html`

Använd `privacy.html` som final URL för enkelhet — uppdatera Pages workflow att producera bägge namn.

---

## Task 15: Device-verify + screenshot-pass + tag v0.8.0-rc1

**Spec ref:** §5 (alla 10 success criteria)
**Files:** screenshot directory + tag

- [ ] **Step 1: Final build + signed AAB**

```bash
./gradlew clean :androidApp:bundleRelease ktlintCheck detekt :composeApp:testDebugUnitTest :shared:domain:jvmTest :shared:ml:jvmTest
```

Förväntat: allt grönt.

- [ ] **Step 2: Installera signed AAB på SM-S918B**

```bash
# Convert AAB to APKs and install
"$JAVA_HOME/bin/java" -jar ~/tools/bundletool-all.jar build-apks \
  --bundle=androidApp/build/outputs/bundle/release/androidApp-release.aab \
  --output=androidApp/build/outputs/bundle/release/birdy-release.apks \
  --connected-device

"$JAVA_HOME/bin/java" -jar ~/tools/bundletool-all.jar install-apks \
  --apks=androidApp/build/outputs/bundle/release/birdy-release.apks
```

- [ ] **Step 3: Smoke-test allt på release-AAB**

Kör igenom: cold-start (verifiera splash), Scan → Match → Save (med inline-note), DEMO-banner tap (artificiellt forcera DEMO via test_species.txt om möjligt), Archive search + clear, Diary empty (clear data först), Badge locked-tap (progress visas), Disambig "Spara som okänd", Settings → alla 6 rader, Language-byte SV→EN→System.

- [ ] **Step 4: Ta canonical-screenshots**

Skapa `docs/superpowers/screenshots/2026-XX-XX-v0.8.0-rc1/` och fånga minst:

1. `01-cold-start-splash.png` (paper-bg + icon under 1s init)
2. `02-onboarding-page-1.png` (med nya BackHandler-flow)
3. `03-listen-launcher.png` (uppdaterad med ev. layout-justeringar)
4. `04-encyclopedia-skeleton.png` (kort moment under load)
5. `05-encyclopedia-search-clear.png` (✕ visible)
6. `06-diary-empty-redesign.png` (stamp + Caveat-marginalia)
7. `07-badges-locked-progress.png` (bottom-sheet med progress-bar)
8. `08-match-with-inline-note.png` (textfield + save)
9. `09-disambig-save-unknown.png` (tre val visible)
10. `10-settings-all-rows.png` (alla 6 rader synliga)
11. `11-about-screen.png` (ny AboutScreen)
12. `12-locale-en-archive.png` (locale-byte fungerar)

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" exec-out screencap -p > "C:/Users/abbea/dev/birdy-bird-scanner/docs/superpowers/screenshots/2026-XX-XX-v0.8.0-rc1/01-cold-start-splash.png"
# ... repeat för varje screenshot
```

- [ ] **Step 5: Commit screenshots**

```bash
git add docs/superpowers/screenshots/
git commit -m "docs(screenshots): v0.8.0-rc1 device-verify pass (Plan 6a Task 15)"
```

- [ ] **Step 6: Bump versionCode + versionName**

I `androidApp/build.gradle.kts`:
```kotlin
versionCode = 100
versionName = "1.0.0-rc1"
```

```bash
git add androidApp/build.gradle.kts
git commit -m "chore(release): bump to 1.0.0-rc1 (versionCode 100) (Plan 6a Task 15)"
```

- [ ] **Step 7: Tag + push**

```bash
git tag -a v0.8.0-rc1 -m "Plan 6a Foundation — UX-polish + release-mekanik klar"
git push origin main
git push origin v0.8.0-rc1
```

- [ ] **Step 8: Uppdatera CLAUDE.md status**

I `CLAUDE.md`, ändra status-raden:
```
**Status (2026-XX-XX):** ... Plan 7e ✅. Plan 6a (Foundation) ✅ (`v0.8.0-rc1`, 2026-XX-XX). **Nästa: Plan 6b (Billing) — Google Play Billing v6.**
```

Lägg in `| 6a | Foundation — UX-polish + release-mekanik | ✅ \`v0.8.0-rc1\` |` i plan-of-plans-tabellen.

```bash
git add CLAUDE.md
git commit -m "docs(claude): Plan 6a shipped — bump status + plan table (Plan 6a Task 15)"
git push
```

- [ ] **Step 9: Spec self-review verifierad**

Bekräfta att alla 10 success criteria från spec §5 är uppfyllda. Skapa en summary-comment med utfallet i runbook eller commit-message.

---

## Slutfas — efter alla 15 tasks

Plan 6a klar när:
- ✅ Tag `v0.8.0-rc1` skapad och pushad
- ✅ 12+ screenshots committade i `docs/superpowers/screenshots/2026-XX-XX-v0.8.0-rc1/`
- ✅ `docs/play-store/`-mappen är klar och Pages är live
- ✅ Signed release AAB klar på `androidApp/build/outputs/bundle/release/`
- ✅ Alla `./gradlew`-test-targets gröna
- ✅ Premium-skärm + mock-purchase oförändrad
- ✅ CLAUDE.md uppdaterad

Nästa plan: **Plan 6b — Google Play Billing v6** (skapas via brainstorming när 6a är klar).
