# Release 1.3.0 · Plan 1: API 36 + betalning (grund) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ett Android-bygge med targetSdk 36 där betalningen är påslagen, tidiga användare behåller Premium för alltid (med tack-skärm), priser bara kommer från Google Play, Premium-copy är sann på svenska och engelska, och köpflödet inte längre "lyckas" innan köpet är klart. Planen slutar med ett köp-testbygge (vC128) till internt test-spår.

**Architecture:** Byggkedjan lyfts minimalt (AGP 8.9.1, Gradle 8.11.1, compile/targetSdk 36). Grandfather-regeln är en ren funktion i `composeApp/commonMain` (`GrandfatherPolicy`) som räknas om vid varje start från två stabila källor (DataStore `firstInstallTimestamp` + Androids `PackageInfo.firstInstallTime`) mot en fast brytpunkt i `BuildConfig`; inget beslut sparas, så ett köp-testbygge kan köras med brytpunkt 0 utan att förstöra något. Premium-override-logiken flyttas ut ur `MainActivity` till en testbar `PremiumOverrideResolver`. Tack-skärmen visas en gång via en ny preferens och ersätter köpskärmen för tidiga användare.

**Tech Stack:** Kotlin 2.1.20 Multiplatform, Compose Multiplatform 1.8.2, AGP 8.9.1, Google Play Billing v8, DataStore Preferences, kotlin.test + JUnit4 (androidUnitTest).

**Spec:** `docs/superpowers/specs/2026-09-24-v1-3-release-design.md` (§3 spår A, §5 spår B, §6.1).
**Efterföljare:** Plan 2 (utseendet) och Plan 3 (QA + release) skrivs separat.

---

## Förutsättningar och konventioner (läs först)

- **Windows-prefix för varje Gradle-kommando** (annars hittar Gradle inte Java):
  ```bash
  export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```
- **Full gate** (körs där planen säger "full gate"):
  ```bash
  ./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :shared:datastore:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
  ```
  Förväntat: `BUILD SUCCESSFUL`.
- **Arbetskatalog:** allt arbete sker i worktreen `C:/w/birdy-130` på grenen `release/1.3.0` (Task 1). En annan session redigerar webbplatsen i huvudkatalogen `C:/Users/abbea/dev/1-mina-projekt/birdy` — rör aldrig filer där och kör aldrig `git add -A`; lägg alltid till exakta sökvägar.
- **iOS:** Windows kan inte kompilera iOS. Varje ändring i `commonMain` eller i `UserPreferences` måste också göras i iOS-actuals (`shared/datastore/src/iosMain/...`). CI:s macOS-jobb körs på PR:en och är iOS-vakten — kontrollera den efter varje push.
- **Språk:** appen har svenska (`composeApp/src/commonMain/composeResources/values/strings.xml`, default) och engelska (`.../values-en/strings.xml`). Varje ny/ändrad sträng skrivs i BÅDA filerna i samma commit. Inga tankstreck (—) i ny användartext.
- **Testnamn:** backtick-namn får inte innehålla `(`, `)` eller `,` (ogiltiga ObjC-selektorer när commonTest kör på iOS).
- **Commits:** svenska eller engelska konventionell stil, avsluta med raden `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.

## Filkarta

| Fil | Ansvar | Task |
|---|---|---|
| `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties` | AGP/Gradle/SDK-nivåer | 2 |
| `androidApp/build.gradle.kts` | version, `GRANDFATHER_CUTOFF_MS`, `PREMIUM_OPEN_FOR_LAUNCH=false`, nyckelvakt | 3, 6 |
| `composeApp/src/commonMain/kotlin/se/birdy/app/premium/GrandfatherPolicy.kt` (ny) | ren grandfather-regel | 4 |
| `composeApp/src/commonMain/kotlin/se/birdy/app/premium/PremiumOverrideResolver.kt` (ny) | ren override-upplösning | 6 |
| `composeApp/src/commonMain/kotlin/se/birdy/app/premium/EntryFlowDecider.kt` | + `shouldShowGrandfatherThanks` | 11 |
| `shared/datastore/src/commonMain/.../UserPreferences.kt` + 4 impl. | `grandfatherThanksShown`, `debugForceGrandfathered` | 5 |
| `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` | källor → `isGrandfathered` → override; debugverktyg | 6, 11 |
| `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt` | `isGrandfathered`-fält | 6 |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumUiState.kt`, `PremiumViewModel.kt`, `PremiumScreen.kt` | köpbekräftelse, priser från Play | 7, 8 |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumThankYouScreen.kt` (ny) | tack-skärm | 11 |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt` | tack-routing | 11 |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/debug/DiagnosticsScreen.kt` | debugverktyg | 11 |
| `composeApp/src/androidUnitTest/kotlin/se/birdy/app/strings/StringsXml.kt` (ny) | XML-läsare för tester | 9 |
| `composeApp/src/androidUnitTest/kotlin/se/birdy/app/strings/StringsParityTest.kt` (ny) | SV↔EN-paritet | 9 |
| `composeApp/src/androidUnitTest/kotlin/se/birdy/app/strings/PremiumCopyTruthGuardTest.kt` (ny) | sann Premium-copy | 10 |
| `docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md` | köp-test med grandfather | 13 |

---

### Task 1: Worktree, gren, utkast-PR och grön baslinje

**Files:** inga kodändringar.

- [ ] **Step 1: Skapa worktree på kort sökväg** (långa sökvägar under scratchpad failar på Windows)

```bash
cd /c/Users/abbea/dev/1-mina-projekt/birdy
git fetch origin
git worktree add -b release/1.3.0 C:/w/birdy-130 origin/main
cp local.properties C:/w/birdy-130/local.properties
cd C:/w/birdy-130 && git log -1 --oneline
```
Förväntat: senaste commit är `docs: spec för release 1.3.0 ...` (`5ff6e6e2`) eller senare.

- [ ] **Step 2: Kör full gate som baslinje (före ändringar)**

Kör full gate (se konventioner). Första bygget laddar ner 16 KB-flex-biblioteken (~300 MB) och kan ta 10+ minuter. Förväntat: `BUILD SUCCESSFUL`. Om något är rött redan nu: STOPPA och rapportera (det är inte vår regression).

- [ ] **Step 3: Pusha grenen och öppna utkast-PR så CI (inkl. macOS/iOS-jobbet) körs på varje push**

```bash
cd C:/w/birdy-130
git push -u origin release/1.3.0
gh pr create --draft --base main --head release/1.3.0 --title "Release 1.3.0: API 36, betalning, utseendelyft" --body "$(cat <<'EOF'
Spec: docs/superpowers/specs/2026-09-24-v1-3-release-design.md
Plan 1: docs/superpowers/plans/2026-09-24-v1-3-01-foundation-api36-billing.md

Utkast tills plan 1–3 är klara. CI:s macOS-jobb är iOS-vakten för delad kod.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
Förväntat: en PR-URL skrivs ut.

---

### Task 2: API 36 (compileSdk/targetSdk) + AGP 8.9.1 + Gradle 8.11.1

**Files:**
- Modify: `gradle/libs.versions.toml` (rad `agp`, `android-compileSdk`, `android-targetSdk`)
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `buildSrc/build.gradle.kts` (`com.android.tools.build:gradle:8.7.3` → `8.9.1`) — **tillagt under körningen 2026-09-24:** rot-bygget applicerar Android-pluginen utan version, så den faktiska AGP-versionen kommer från `buildSrc`s classpath; katalogens `agp`-nyckel ensam gör ingenting (AGP:s egen varning visade fortfarande 8.7.3). Commit `0521d9ef`.

- [ ] **Step 1: Uppdatera versionskatalogen**

I `gradle/libs.versions.toml`, ändra exakt dessa tre rader:
```toml
agp = "8.9.1"
android-compileSdk = "36"
android-targetSdk = "36"
```

- [ ] **Step 2: Uppdatera Gradle-wrappern**

I `gradle/wrapper/gradle-wrapper.properties`, ersätt `distributionUrl`-raden med:
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
```

- [ ] **Step 3: Kör full gate**

Kör full gate. AGP laddar själv ner `platforms;android-36` om SDK-licenserna är accepterade.
- Om felet är `Failed to find target with hash string 'android-36'` eller licensfel: STOPPA och rapportera BLOCKED ("Albin: installera Android 16 (API 36) i Android Studio → SDK Manager, sedan kör om"). Försök inte ladda ner SDK:n manuellt.
- Om KGP klagar på AGP-versionen som *varning*: ok, fortsätt. Om det är ett *fel* som kräver Kotlin-uppgradering: STOPPA och rapportera (Kotlin-bump påverkar CMP och iOS och kräver ett eget beslut).
- Om AGP 8.9.1 inte kan kompilera mot 36: prova `agp = "8.10.1"` en gång, annars STOPPA och rapportera.

Förväntat: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Kör release-lint (nya lintregler för API 36 fångas bara här)**

```bash
./gradlew :androidApp:lintRelease
```
Förväntat: `BUILD SUCCESSFUL`. Om lint hittar *fel* (inte varningar) som beror på API 36: fixa dem med minsta möjliga ändring i den fil lint pekar ut, och lista varje fix i commit-meddelandet. Lägg INTE till `lint { disable += ... }` utan motivering.

- [ ] **Step 5: Kontrollera att ingen gammal tillbaka-hantering finns (predictive back är på som standard vid targetSdk 36)**

```bash
grep -rn "onBackPressed\|KEYCODE_BACK\|OnBackPressedCallback" --include=*.kt androidApp composeApp/src shared | grep -v "/build/"
```
Förväntat: ingen träff. (Verifierat 2026-09-24; om det dyker upp träffar: rapportera dem i task-rapporten, de testas i Plan 3.)

- [ ] **Step 6: Commit + push + kontrollera CI**

```bash
git add gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties
git commit -m "build: targetSdk/compileSdk 36, AGP 8.9.1, Gradle 8.11.1

Play avvisar uppdateringar under API 36 sedan 31 aug 2026.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
git push
gh pr checks --watch
```
Förväntat: båda CI-jobben (`Build & test` och `iOS framework + tests`) gröna. Om macOS-jobbet failar på grund av SDK/AGP: STOPPA och rapportera loggutdraget.

---

### Task 3: Version 1.3.0 / vC128 + vakt mot tomma release-nycklar

**Files:**
- Modify: `androidApp/build.gradle.kts` (defaultConfig `versionCode`/`versionName`; ny task efter `downloadFlex16kJniLibs`)

- [ ] **Step 1: Bumpa version**

I `androidApp/build.gradle.kts` → `defaultConfig`, ersätt:
```kotlin
        versionCode = 127
        versionName = "1.2.2"
```
med:
```kotlin
        versionCode = 128
        versionName = "1.3.0"
```

- [ ] **Step 2: Lägg till nyckelvakten** — längst ner i `androidApp/build.gradle.kts`, efter blocket `tasks.matching { it.name.endsWith("JniLibFolders") } ...`:

```kotlin
// Release builds must never ship without the Play licensing key (billing signature
// verification) or the MapTiler key (map tiles) — blank values only make sense for
// local debug builds. Only presence (a boolean) is recorded as a task input, never the
// secret itself. Spec 2026-09-24 §3 A3.
val verifyReleaseKeys by tasks.registering {
    description = "Fails release builds when BIRDY_PLAY_LICENSE_KEY or MAPTILER_API_KEY is blank."
    listOf("BIRDY_PLAY_LICENSE_KEY", "MAPTILER_API_KEY").forEach { key ->
        inputs.property(
            "present.$key",
            providers.gradleProperty(key).map { it.isNotBlank() }.orElse(false),
        )
    }
    doLast(
        Action {
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
```

- [ ] **Step 3: Verifiera att vakten failar på tom nyckel**

```bash
./gradlew :androidApp:verifyReleaseKeys -PMAPTILER_API_KEY=
```
Förväntat: `FAILED` med `Release build blocked: blank MAPTILER_API_KEY`.

- [ ] **Step 4: Verifiera att vakten passerar med de lokala nycklarna** (de ligger i `~/.gradle/gradle.properties`)

```bash
./gradlew :androidApp:verifyReleaseKeys
```
Förväntat: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Verifiera att debug-bygget inte berörs**

```bash
./gradlew :androidApp:assembleDebug -PMAPTILER_API_KEY= -PBIRDY_PLAY_LICENSE_KEY=
```
Förväntat: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add androidApp/build.gradle.kts
git commit -m "build: version 1.3.0 (vC128) + stoppa release-bygge med tom licens- eller MapTiler-nyckel

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: `GrandfatherPolicy` (ren regel, TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/premium/GrandfatherPolicy.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/premium/GrandfatherPolicyTest.kt`

- [ ] **Step 1: Skriv det fallerande testet**

```kotlin
package se.birdy.app.premium

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrandfatherPolicyTest {
    private val cutoff = 1_790_892_000_000L // 2026-10-02T00:00 Europe/Stockholm

    @Test
    fun `no install data means not grandfathered`() {
        assertFalse(GrandfatherPolicy.isGrandfathered(null, null, cutoff))
    }

    @Test
    fun `stored first install before cutoff is grandfathered`() {
        assertTrue(GrandfatherPolicy.isGrandfathered(cutoff - 1, null, cutoff))
    }

    @Test
    fun `package first install before cutoff is grandfathered even if stored is after`() {
        assertTrue(GrandfatherPolicy.isGrandfathered(cutoff + 5_000, cutoff - 86_400_000, cutoff))
    }

    @Test
    fun `both sources after cutoff is not grandfathered`() {
        assertFalse(GrandfatherPolicy.isGrandfathered(cutoff + 1, cutoff + 2, cutoff))
    }

    @Test
    fun `install exactly at cutoff is not grandfathered`() {
        assertFalse(GrandfatherPolicy.isGrandfathered(cutoff, cutoff, cutoff))
    }

    @Test
    fun `zero or negative timestamps are treated as unknown`() {
        assertFalse(GrandfatherPolicy.isGrandfathered(0L, -1L, cutoff))
    }

    @Test
    fun `cutoff zero grandfathers nobody so purchases can be tested`() {
        assertFalse(GrandfatherPolicy.isGrandfathered(1_750_000_000_000L, 1_750_000_000_000L, 0L))
    }
}
```

- [ ] **Step 2: Kör testet och se det faila**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.premium.GrandfatherPolicyTest"
```
Förväntat: kompileringsfel `Unresolved reference: GrandfatherPolicy`.

- [ ] **Step 3: Implementera**

```kotlin
package se.birdy.app.premium

/**
 * Early-user ("grandfather") rule — decided by Albin 2026-06-17, specced 2026-09-24 §5.1:
 * everyone who installed Birdy before monetisation went live keeps Premium forever.
 *
 * Recomputed on every app start from stable inputs; nothing is persisted. That keeps a
 * billing-verify build (cutoff 0) from poisoning later builds, and the stored
 * `firstInstallTimestamp` travels with Google backup to a new phone.
 *
 * The cutoff passed in by the app MUST NEVER change after 1.3.0 ships — moving it would
 * silently grant or revoke Premium for real users.
 */
object GrandfatherPolicy {
    fun isGrandfathered(
        storedFirstInstallMs: Long?,
        packageFirstInstallMs: Long?,
        cutoffMs: Long,
    ): Boolean =
        listOfNotNull(storedFirstInstallMs, packageFirstInstallMs)
            .any { it > 0 && it < cutoffMs }
}
```

- [ ] **Step 4: Kör testet och se det passera**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.premium.GrandfatherPolicyTest"
```
Förväntat: 7 tester PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/premium/GrandfatherPolicy.kt composeApp/src/commonTest/kotlin/se/birdy/app/premium/GrandfatherPolicyTest.kt
git commit -m "feat(premium): GrandfatherPolicy — tidiga användare behåller Premium

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Två nya preferenser i ALLA `UserPreferences`-implementationer

**Files:**
- Modify: `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt`
- Modify: `shared/datastore/src/androidMain/kotlin/se/birdy/datastore/UserPreferencesStore.android.kt`
- Modify: `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/InMemoryUserPreferences.kt`
- Modify: `shared/datastore/src/iosMain/kotlin/se/birdy/datastore/NsUserDefaultsUserPreferences.kt`
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeUserPreferences.kt`
- Test: `shared/datastore/src/jvmTest/kotlin/se/birdy/datastore/GrandfatherPrefsTest.kt` (ny)

- [ ] **Step 1: Skriv det fallerande testet**

```kotlin
package se.birdy.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrandfatherPrefsTest {
    @Test
    fun `thanks shown defaults to false and persists true`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            assertFalse(prefs.grandfatherThanksShown.first())
            prefs.setGrandfatherThanksShown(true)
            assertTrue(prefs.grandfatherThanksShown.first())
        }

    @Test
    fun `debug force grandfathered defaults to false and can be toggled`() =
        runTest {
            val prefs = InMemoryUserPreferences()
            assertFalse(prefs.debugForceGrandfathered.first())
            prefs.setDebugForceGrandfathered(true)
            assertTrue(prefs.debugForceGrandfathered.first())
        }
}
```
Kontrollera att `InMemoryUserPreferences` har en konstruktor utan argument: `grep -n "class InMemoryUserPreferences" shared/datastore/src/commonMain/kotlin/se/birdy/datastore/InMemoryUserPreferences.kt`. Har den obligatoriska argument, anropa den som befintliga tester i `InMemoryUserPreferencesTest.kt` gör.

- [ ] **Step 2: Kör och se det faila**

```bash
./gradlew :shared:datastore:jvmTest --tests "se.birdy.datastore.GrandfatherPrefsTest"
```
Förväntat: `Unresolved reference: grandfatherThanksShown`.

- [ ] **Step 3: Lägg till i interfacet** — i `UserPreferences.kt`, direkt efter `val inAppReviewRequested: Flow<Boolean>`:

```kotlin
    /** One-shot: true once the early-user thank-you screen has been shown (release 1.3.0). */
    val grandfatherThanksShown: Flow<Boolean>

    /**
     * DEBUG-only QA toggle: treat this install as an early (grandfathered) user so the
     * thank-you screen can be tested. MainActivity only reads it when BuildConfig.DEBUG.
     */
    val debugForceGrandfathered: Flow<Boolean>
```
och direkt efter `suspend fun setInAppReviewRequested(value: Boolean)`:
```kotlin
    suspend fun setGrandfatherThanksShown(value: Boolean)

    suspend fun setDebugForceGrandfathered(value: Boolean)
```

- [ ] **Step 4: Android DataStore** — i `UserPreferencesStore.android.kt`:

I `private object Keys`, efter `IN_APP_REVIEW_REQUESTED`:
```kotlin
        val GRANDFATHER_THANKS_SHOWN = booleanPreferencesKey("grandfather_thanks_shown")
        val DEBUG_FORCE_GRANDFATHERED = booleanPreferencesKey("debug_force_grandfathered")
```
Efter `override val inAppReviewRequested ...`:
```kotlin
    override val grandfatherThanksShown: Flow<Boolean> =
        safeData.map { it[Keys.GRANDFATHER_THANKS_SHOWN] ?: false }
    override val debugForceGrandfathered: Flow<Boolean> =
        safeData.map { it[Keys.DEBUG_FORCE_GRANDFATHERED] ?: false }
```
Efter `override suspend fun setInAppReviewRequested ...`:
```kotlin
    override suspend fun setGrandfatherThanksShown(value: Boolean) {
        store.edit { it[Keys.GRANDFATHER_THANKS_SHOWN] = value }
    }

    override suspend fun setDebugForceGrandfathered(value: Boolean) {
        store.edit { it[Keys.DEBUG_FORCE_GRANDFATHERED] = value }
    }
```

- [ ] **Step 5: InMemory** — i `InMemoryUserPreferences.kt`, efter `private val _inAppReviewRequested = MutableStateFlow(false)`:
```kotlin
    private val _grandfatherThanksShown = MutableStateFlow(false)
    private val _debugForceGrandfathered = MutableStateFlow(false)
```
efter `override val inAppReviewRequested ...`:
```kotlin
    override val grandfatherThanksShown: Flow<Boolean> = _grandfatherThanksShown.asStateFlow()
    override val debugForceGrandfathered: Flow<Boolean> = _debugForceGrandfathered.asStateFlow()
```
efter `setInAppReviewRequested`-funktionen:
```kotlin
    override suspend fun setGrandfatherThanksShown(value: Boolean) {
        _grandfatherThanksShown.value = value
    }

    override suspend fun setDebugForceGrandfathered(value: Boolean) {
        _debugForceGrandfathered.value = value
    }
```

- [ ] **Step 6: Fake i composeApp** — i `composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeUserPreferences.kt`, exakt samma tre tillägg som Step 5 (samma namn, samma kod), på motsvarande ställen efter `_inAppReviewRequested`/`inAppReviewRequested`/`setInAppReviewRequested`.

- [ ] **Step 7: iOS NSUserDefaults** — i `NsUserDefaultsUserPreferences.kt`:

I `private object Keys`, efter `const val IN_APP_REVIEW_REQUESTED = "in_app_review_requested"`:
```kotlin
        const val GRANDFATHER_THANKS_SHOWN = "grandfather_thanks_shown"
        const val DEBUG_FORCE_GRANDFATHERED = "debug_force_grandfathered"
```
Efter `private val _inAppReviewRequested = MutableStateFlow(getBool(Keys.IN_APP_REVIEW_REQUESTED, false))`:
```kotlin
    private val _grandfatherThanksShown = MutableStateFlow(getBool(Keys.GRANDFATHER_THANKS_SHOWN, false))
    private val _debugForceGrandfathered = MutableStateFlow(getBool(Keys.DEBUG_FORCE_GRANDFATHERED, false))
```
Efter `override val inAppReviewRequested ...`:
```kotlin
    override val grandfatherThanksShown: Flow<Boolean> = _grandfatherThanksShown.asStateFlow()
    override val debugForceGrandfathered: Flow<Boolean> = _debugForceGrandfathered.asStateFlow()
```
Efter `setInAppReviewRequested`-funktionen:
```kotlin
    override suspend fun setGrandfatherThanksShown(value: Boolean) {
        putBool(Keys.GRANDFATHER_THANKS_SHOWN, value)
        _grandfatherThanksShown.value = value
    }

    override suspend fun setDebugForceGrandfathered(value: Boolean) {
        putBool(Keys.DEBUG_FORCE_GRANDFATHERED, value)
        _debugForceGrandfathered.value = value
    }
```

- [ ] **Step 8: Leta efter andra implementationer** (varje `UserPreferences`-implementation måste få medlemmarna)

```bash
grep -rn ": UserPreferences\b\|: UserPreferences {" --include=*.kt . | grep -v "/build/\|\.worktrees/"
```
Förväntat: bara de fyra klasserna ovan (Android, InMemory, NsUserDefaults, Fake). Fler träffar → lägg till samma medlemmar där.

- [ ] **Step 9: Kör testet + full gate**

```bash
./gradlew :shared:datastore:jvmTest --tests "se.birdy.datastore.GrandfatherPrefsTest"
```
Förväntat: 2 PASS. Kör sedan full gate → `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit + push (CI:s macOS-jobb kompilerar iOS-actualen)**

```bash
git add shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt shared/datastore/src/androidMain/kotlin/se/birdy/datastore/UserPreferencesStore.android.kt shared/datastore/src/commonMain/kotlin/se/birdy/datastore/InMemoryUserPreferences.kt shared/datastore/src/iosMain/kotlin/se/birdy/datastore/NsUserDefaultsUserPreferences.kt composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeUserPreferences.kt shared/datastore/src/jvmTest/kotlin/se/birdy/datastore/GrandfatherPrefsTest.kt
git commit -m "feat(prefs): grandfatherThanksShown + debugForceGrandfathered (Android, iOS, InMemory, Fake)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
git push
gh pr checks --watch
```
Förväntat: båda CI-jobben gröna.

---

### Task 6: `PremiumOverrideResolver` + koppla grandfather i `MainActivity`, betalningen på

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/premium/PremiumOverrideResolver.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/premium/PremiumOverrideResolverTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt:72` (nytt fält)
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` (buildAppGraph)
- Modify: `androidApp/build.gradle.kts` (defaultConfig)

- [ ] **Step 1: Skriv det fallerande testet**

```kotlin
package se.birdy.app.premium

import kotlinx.datetime.Instant
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PremiumOverrideResolverTest {
    private val now = Instant.fromEpochMilliseconds(1_790_000_000_000L)

    private fun resolve(
        grandfathered: Boolean = false,
        debugSkip: Boolean = false,
        openForLaunch: Boolean = false,
        debugForceYearly: Boolean = false,
    ) = PremiumOverrideResolver.resolve(
        isGrandfathered = grandfathered,
        debugSkipOverride = debugSkip,
        premiumOpenForLaunch = openForLaunch,
        debugForceYearly = debugForceYearly,
        now = now,
    )

    @Test
    fun `new user after monetisation gets no override so billing decides`() {
        assertNull(resolve())
    }

    @Test
    fun `grandfathered user gets lifetime premium`() {
        val state = resolve(grandfathered = true)
        assertIs<PremiumState.Active>(state)
        assertEquals(PremiumTier.LIFETIME, state.tier)
    }

    @Test
    fun `debug skip wins over grandfathered so the paywall can be tested`() {
        assertNull(resolve(grandfathered = true, debugSkip = true))
    }

    @Test
    fun `open for launch still grants lifetime when enabled`() {
        assertIs<PremiumState.Active>(resolve(openForLaunch = true))
    }

    @Test
    fun `debug force yearly grants yearly`() {
        val state = resolve(debugForceYearly = true)
        assertIs<PremiumState.Active>(state)
        assertEquals(PremiumTier.YEARLY, state.tier)
    }
}
```

- [ ] **Step 2: Kör och se det faila**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.premium.PremiumOverrideResolverTest"
```
Förväntat: `Unresolved reference: PremiumOverrideResolver`.

- [ ] **Step 3: Implementera**

```kotlin
package se.birdy.app.premium

import kotlinx.datetime.Instant
import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

/**
 * Decides the app-wide premium override (null = let Play Billing decide).
 * Order matters: the DEBUG billing-verify skip wins over everything so the paywall can be
 * exercised on a developer device that is itself grandfathered. Callers pass
 * `debugSkipOverride`/`debugForceYearly` already AND-ed with BuildConfig.DEBUG.
 */
object PremiumOverrideResolver {
    fun resolve(
        isGrandfathered: Boolean,
        debugSkipOverride: Boolean,
        premiumOpenForLaunch: Boolean,
        debugForceYearly: Boolean,
        now: Instant,
    ): PremiumState? =
        when {
            debugSkipOverride -> null
            isGrandfathered -> PremiumState.Active(PremiumTier.LIFETIME, now)
            premiumOpenForLaunch -> PremiumState.Active(PremiumTier.LIFETIME, now)
            debugForceYearly -> PremiumState.Active(PremiumTier.YEARLY, now)
            else -> null
        }
}
```

- [ ] **Step 4: Kör testet** — samma kommando som Step 2. Förväntat: 5 PASS.

- [ ] **Step 5: `AppGraph`-fält** — i `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`, direkt efter raden `val premiumOverride: PremiumState? = null,`:
```kotlin
    /** True for early users who keep Premium forever (spec 2026-09-24 §5.1). Android-only source. */
    val isGrandfathered: Boolean = false,
```

- [ ] **Step 6: BuildConfig i `androidApp/build.gradle.kts`** — i `defaultConfig`, ersätt hela blocket från kommentaren `// Launch-period flag: while true, MainActivity forces ...` till och med raden `buildConfigField("Boolean", "PREMIUM_OPEN_FOR_LAUNCH", "true")` med:

```kotlin
        // Monetisation is live from 1.3.0 (spec 2026-09-24): no launch-period override.
        buildConfigField("Boolean", "PREMIUM_OPEN_FOR_LAUNCH", "false")
        // Early-user cutoff (spec §5.1): installs before this instant keep Premium forever.
        // Default = planned go-live + 48 h = 2026-10-02T00:00 Europe/Stockholm
        // (2026-10-01T22:00:00Z). NEVER change it after 1.3.0 ships. Billing-verify builds
        // pass -Pbirdy.grandfatherCutoffMs=0 so nobody is grandfathered and purchases can
        // be tested; such a build must never be promoted to production.
        val grandfatherCutoffMs =
            providers.gradleProperty("birdy.grandfatherCutoffMs").orElse("1790892000000").get()
        buildConfigField("long", "GRANDFATHER_CUTOFF_MS", "${grandfatherCutoffMs}L")
```

- [ ] **Step 7: `MainActivity` — beräkna grandfather**

a) Lägg till import högst upp bland de andra `se.birdy.app`-importerna:
```kotlin
import se.birdy.app.premium.GrandfatherPolicy
import se.birdy.app.premium.PremiumOverrideResolver
```
Lägg också till `import android.content.pm.PackageManager` och `import android.os.Build` om de saknas (`grep -n "^import android.os.Build\|^import android.content.pm.PackageManager" androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`).

b) I `buildAppGraph()`, direkt EFTER `runBlocking { ... setFirstInstallTimestamp(installMs) ... }`-blocket (migreringen, kring rad 318–330), lägg till:
```kotlin
        val isGrandfathered = computeGrandfathered(userPreferences)
```

c) Ersätt hela `val premiumOverride: PremiumState? = when { ... }`-blocket (inklusive kommentaren `// PREMIUM_OPEN_FOR_LAUNCH (defaultConfig=true) forces ...` ovanför) med:
```kotlin
        val premiumOverride: PremiumState? =
            PremiumOverrideResolver.resolve(
                isGrandfathered = isGrandfathered,
                debugSkipOverride = skipPremiumOverride,
                premiumOpenForLaunch = BuildConfig.PREMIUM_OPEN_FOR_LAUNCH,
                debugForceYearly = BuildConfig.DEBUG && BuildConfig.PREMIUM_DEBUG_FORCE_ACTIVE,
                now = Clock.System.now(),
            )
```

d) I `return AppGraph(...)`, direkt efter `premiumOverride = premiumOverride,`:
```kotlin
            isGrandfathered = isGrandfathered,
```

e) Lägg till två privata funktioner i klassen, direkt före `private fun buildBenchmarkScreen`:
```kotlin
    /** Spec 2026-09-24 §5.1. DEBUG builds can force it on via DiagnosticsScreen for QA. */
    private fun computeGrandfathered(userPreferences: UserPreferences): Boolean {
        val debugForce = BuildConfig.DEBUG && runBlocking { userPreferences.debugForceGrandfathered.first() }
        if (debugForce) return true
        return GrandfatherPolicy.isGrandfathered(
            storedFirstInstallMs = runBlocking { userPreferences.firstInstallTimestamp.first() },
            packageFirstInstallMs = packageFirstInstallTimeOrNull(),
            cutoffMs = BuildConfig.GRANDFATHER_CUTOFF_MS,
        )
    }

    /** Android's own install time survives "clear data", unlike our DataStore timestamp. */
    private fun packageFirstInstallTimeOrNull(): Long? =
        try {
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0)
                }
            info.firstInstallTime.takeIf { it > 0 }
        } catch (e: PackageManager.NameNotFoundException) {
            android.util.Log.w("Birdy", "firstInstallTime unavailable", e)
            null
        }
```

- [ ] **Step 8: Full gate**

Förväntat: `BUILD SUCCESSFUL`. Kontrollera även att `PREMIUM_OPEN_FOR_LAUNCH` inte längre är `true` någonstans:
```bash
grep -rn "PREMIUM_OPEN_FOR_LAUNCH\", \"true\"" androidApp/build.gradle.kts
```
Förväntat: ingen träff.

- [ ] **Step 9: Commit + push**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/premium/PremiumOverrideResolver.kt composeApp/src/commonTest/kotlin/se/birdy/app/premium/PremiumOverrideResolverTest.kt composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt androidApp/build.gradle.kts
git commit -m "feat(premium): betalningen på, tidiga användare behåller Premium (brytpunkt 2026-10-02)

PREMIUM_OPEN_FOR_LAUNCH=false i samma bygge som grandfather-regeln, enligt
runbookens hårda grind (§5). Override-logiken utbruten till en testbar resolver.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
git push
```

---

### Task 6b (tillägg från granskningen 2026-09-24): Inställningar visar tidiga användares Premium

**Bakgrund:** `SettingsViewModel` läser bara `premiumRepository.state` (Play-köp) och ignorerar `premiumOverride`. En grandfathered användare ser därför "Skaffa Premium"-kortet i Inställningar, och "Återställ köp" svarar "inga köp hittades".

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt` (`settingsViewModel()`)
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Fallerande tester** (följ befintliga `SettingsViewModelTest`s konstruktion av VM:en):
  - `override active with free billing shows premium as active` — `premiumOverride = PremiumState.Active(LIFETIME, now)`, `FakePremiumRepository(PremiumState.Free)` → `state.value.premiumActive == true`.
  - `restore with override active reports success` — samma uppsättning, `restorePurchases()` → effekten `ShowToast(Res.string.settings_restore_purchases_success)`.
  - `no override keeps billing state` — ingen override, Free → `premiumActive == false`.
- [ ] **Step 2:** Kör → kompileringsfel (parametern finns inte).
- [ ] **Step 3:** `SettingsViewModel` får konstruktorparametern `private val premiumOverride: PremiumState? = null` (sist, med default). I `combine`: `premiumActive = (premiumOverride ?: premium) !is PremiumState.Free`. I `restorePurchases()`: `val currentPremium = premiumOverride ?: premiumRepository.state.value`.
- [ ] **Step 4:** `AppGraph.settingsViewModel()` skickar `premiumOverride = premiumOverride`.
- [ ] **Step 5:** Testerna gröna + full gate.
- [ ] **Step 6: Commit** (utan push): exakta sökvägar till de tre filerna, meddelande `fix(settings): tidiga användares Premium syns i Inställningar och vid återställning` + Co-Authored-By-raden.

---

### Task 6c (tillägg från granskningen 2026-09-24): Ingen automatisk betalvägg förrän Play har svarat

**Bakgrund:** `PremiumBillingClient` startar som `Free` och frågar Play först efter start. `AppScaffold` läser läget en gång vid kallstart, så en betalande prenumerant kan få dag-0- eller 7-dagars-betalväggen om Play inte hunnit svara. Dessutom ignorerar `queryPurchases()` svarskoden: misslyckas frågan (Play ej nåbar) blir en betalande användare `Free`.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.kt` (expect: ny `purchasesQueried`)
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/data/premium/PremiumBillingClient.ios.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/premium/BillingAnswer.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/premium/BillingAnswerTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`, `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`, `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`

- [ ] **Step 1: Fallerande test**

```kotlin
package se.birdy.app.premium

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BillingAnswerTest {
    @Test
    fun `already answered returns true at once`() =
        runTest { assertTrue(awaitBillingAnswer(MutableStateFlow(true), timeoutMs = 5_000)) }

    @Test
    fun `answer arriving before timeout returns true`() =
        runTest {
            val queried = MutableStateFlow(false)
            val result = async { awaitBillingAnswer(queried, timeoutMs = 5_000) }
            advanceTimeBy(1_000)
            queried.value = true
            assertTrue(result.await())
        }

    @Test
    fun `no answer before timeout returns false`() =
        runTest { assertFalse(awaitBillingAnswer(MutableStateFlow(false), timeoutMs = 5_000)) }
}
```
Kör `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.premium.BillingAnswerTest"` → `Unresolved reference: awaitBillingAnswer`.

- [ ] **Step 2: `BillingAnswer.kt`**

```kotlin
package se.birdy.app.premium

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Waits until Play Billing has answered the purchase query. Automatic paywalls must never be
 * shown on a guess: a paying subscriber looks Free until Play answers. Returns false on timeout
 * (Play unreachable) so the caller skips the paywall for this launch.
 */
suspend fun awaitBillingAnswer(
    purchasesQueried: StateFlow<Boolean>,
    timeoutMs: Long,
): Boolean = withTimeoutOrNull(timeoutMs) { purchasesQueried.first { it } } != null
```

- [ ] **Step 3: expect/actual.** I expect-klassen, efter `formattedPrices`: `/** True once Play has answered a purchase query successfully (never on a failed query). */ val purchasesQueried: StateFlow<Boolean>`. Android: `private val _purchasesQueried = MutableStateFlow(false)` + `actual val purchasesQueried: StateFlow<Boolean> = _purchasesQueried.asStateFlow()`; i `queryPurchases()` fångas `BillingResult` i båda callbackarna (`{ result, list -> if (cont.isActive) cont.resume(result to list) }`); **bara om båda är `BillingResponseCode.OK`** sätts `_state.value` och `_purchasesQueried.value = true`, annars loggas koderna med `Log.w(TAG, ...)` och befintligt state lämnas orört. iOS: `actual val purchasesQueried: StateFlow<Boolean> = MutableStateFlow(true)` (iOS har ingen automatisk betalvägg före i5).
- [ ] **Step 4:** `AppGraph` får `val premiumQueried: StateFlow<Boolean> = MutableStateFlow(true),` efter `formattedPricesFlow`; `MainActivity` skickar `premiumQueried = billingClient.purchasesQueried`.
- [ ] **Step 5: AppScaffold** — i första `LaunchedEffect(Unit)`, efter grandfather-blocket och före dag-0-kontrollen:
```kotlin
        // Never show an automatic paywall on a guess — a paying user looks Free until Play answers.
        if (graph.premiumOverride == null && !awaitBillingAnswer(graph.premiumQueried, timeoutMs = 5_000)) {
            return@LaunchedEffect
        }
```
och flytta `val premiumState = graph.premiumOverride ?: graph.premiumRepository.state.value` till direkt efter det blocket.
- [ ] **Step 6:** Full gate. Commit (utan push) med exakta sökvägar, meddelande `fix(premium): ingen automatisk betalvägg förrän Play svarat, misslyckad fråga nollar inte köp` + Co-Authored-By-raden.

---

### Task 6d (tillägg från granskningen av 6c, 2026-09-24): Säkra köpflödet före go-live

**Bakgrund (Opus-granskning av 6c):** Ett befintligt fel i `PremiumBillingClient.android.kt` blockerar att vi börjar ta betalt: `queryPurchases()` ger `Active` för ett verifierat köp men kvitterar (acknowledge) det aldrig — bara lyssnarvägen gör det. Dör appen efter köpet innan kvitteringen, eller slutförs ett väntande köp (t.ex. kontant) medan appen är stängd, återbetalar Play automatiskt efter 3 dagar och användaren tappar Premium. Tre viktiga fynd till: betalväggen kunde öppnas sent ovanpå skärmen användaren gått till; produktpriserna hämtades före köpfrågan och åt upp väntetiden; och "Återställ köp" svarade "inga köp" när Play inte gick att nå.

**Ändringar:**
1. **Kvittering:** `queryPurchases()` kvitterar varje köpt + signaturverifierat köp som inte är kvitterat (samma kvitteringshjälpare som lyssnarvägen). `Active` ges oavsett kvitteringsutfall (Play ger 3 dagar; vi försöker igen vid varje fråga); misslyckad kvittering loggas.
2. **Ingen sen betalvägg:** efter väntan på Play fortsätter `AppScaffold` bara om användaren fortfarande står på startskärmen (`AppRoute.Listen`); annars avbryts utan att "visad"-flaggorna sätts, så försöket görs vid nästa start.
3. **Köp före priser:** `connect()` returnerar direkt när anslutningen är klar; produktpriserna hämtas i klientens egen coroutine-scope (avslutas i `dispose()`).
4. **Ärlig återställning + återanslutning:** `enableAutoServiceReconnection()` på Billing-klienten (eller återanslutning i `queryPurchases()` om API:t saknas); `queryPurchases()` returnerar `Boolean` (expect + Android + iOS `true`); `BillingPremiumRepository.restore()` kastar `BillingUnavailableException` när Play inte svarade; Inställningar visar då ny sträng `settings_restore_purchases_unavailable` (SV: "Kunde inte nå Google Play. Kontrollera anslutningen och försök igen." / EN: "Couldn’t reach Google Play. Check your connection and try again.") — utom när användaren har override (tidig användare), då "lyckades".
5. **Småsaker:** testfältet `FakePremiumBillingClient.purchasesQueried` döps om till `queryPurchasesCalls`; en 140-teckensrad i `BillingAnswerTest` bryts.

**Runbook:** köp-testet (Task 13) får en extra ruta: köp → döda appen direkt (`adb shell am force-stop`) → starta igen → köpet ska vara kvitterat (syns i Play Console → Beställningar som "Kvitterad"/ingen återbetalning efter 3 dagar).

---

### Task 7: Köpet räknas som klart först när Premium faktiskt är aktivt (bugg)

**Bakgrund:** `PremiumScreen` anropar idag `viewModel.purchase(); onPurchaseComplete()` i samma klick. `onPurchaseComplete` stänger skärmen och visar "Välkommen, fältmedlem." direkt, även om användaren avbryter Googles köpruta. Maskerat hittills av att alla hade Premium gratis.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumUiState.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt` (CTA-klicket)
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/premium/PremiumViewModelTest.kt`

- [ ] **Step 1: Skriv de fallerande testerna** — lägg till i `PremiumViewModelTest` (inuti klassen, efter befintliga tester), plus importen `import kotlinx.coroutines.flow.MutableStateFlow` och `import se.birdy.app.data.premium.FormattedPrices` högst upp:

```kotlin
    private val prices = MutableStateFlow(FormattedPrices(yearly = "199 kr", lifetime = "499 kr"))

    @Test
    fun `cancelled purchase does not complete`() =
        runTest {
            val repo = FakePremiumRepository()
            val vm = PremiumViewModel(repo, launchPurchase = { /* user cancels: nothing happens */ }, formattedPricesFlow = prices)
            vm.purchase()
            assertEquals(false, vm.state.first().purchaseCompleted)
        }

    @Test
    fun `purchase completes only when premium becomes active`() =
        runTest {
            val repo = FakePremiumRepository()
            val vm = PremiumViewModel(repo, launchPurchase = { /* async: result arrives later */ }, formattedPricesFlow = prices)
            vm.purchase()
            assertEquals(false, vm.state.first().purchaseCompleted)
            repo.markPurchased(PremiumTier.YEARLY)
            assertEquals(true, vm.state.first().purchaseCompleted)
        }

    @Test
    fun `already active user opening the screen is not treated as a completed purchase`() =
        runTest {
            val repo = FakePremiumRepository(PremiumState.Active(PremiumTier.LIFETIME, kotlinx.datetime.Clock.System.now()))
            val vm = PremiumViewModel(repo, launchPurchase = {}, formattedPricesFlow = prices)
            assertEquals(false, vm.state.first().purchaseCompleted)
        }
```

- [ ] **Step 2: Kör och se dem faila**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.premium.PremiumViewModelTest"
```
Förväntat: `Unresolved reference: purchaseCompleted`.

- [ ] **Step 3: UI-state** — ersätt hela `PremiumUiState.kt`-innehållet med:

```kotlin
package se.birdy.app.ui.premium

import se.birdy.domain.premium.PremiumState
import se.birdy.domain.premium.PremiumTier

data class PremiumUiState(
    val selectedTier: PremiumTier = PremiumTier.YEARLY,
    val purchaseInFlight: Boolean = false,
    val backendState: PremiumState = PremiumState.Free,
    val formattedYearlyPrice: String? = null,
    val formattedLifetimePrice: String? = null,
    /** A purchase was launched from this screen and we're waiting for Play to confirm it. */
    val awaitingActivation: Boolean = false,
    /** Play confirmed the purchase (backend flipped to Active after [awaitingActivation]). */
    val purchaseCompleted: Boolean = false,
)
```

- [ ] **Step 4: ViewModel** — i `PremiumViewModel.kt`, ersätt den första `viewModelScope.launch { repository.state.collect { ... } }` i `init` med:

```kotlin
        viewModelScope.launch {
            repository.state.collect { backend ->
                _state.update {
                    val justActivated = it.awaitingActivation && backend is PremiumState.Active
                    it.copy(
                        backendState = backend,
                        purchaseCompleted = it.purchaseCompleted || justActivated,
                        awaitingActivation = it.awaitingActivation && !justActivated,
                    )
                }
            }
        }
```
och i `purchase()`, ersätt raden `_state.update { it.copy(purchaseInFlight = true) }` med:
```kotlin
        _state.update { it.copy(purchaseInFlight = true, awaitingActivation = true) }
```
Lägg till `import se.birdy.domain.premium.PremiumState` om den saknas.

- [ ] **Step 5: Skärmen** — i `PremiumScreen.kt`:

a) Direkt efter `val state by viewModel.state.collectAsState()`:
```kotlin
    LaunchedEffect(state.purchaseCompleted) {
        if (state.purchaseCompleted) onPurchaseComplete()
    }
```
(lägg till `import androidx.compose.runtime.LaunchedEffect` om den saknas)

b) I `PrimaryCta(...)`-anropet, ersätt
```kotlin
                    onClick = {
                        viewModel.purchase()
                        onPurchaseComplete()
                    },
```
med
```kotlin
                    onClick = { viewModel.purchase() },
```

- [ ] **Step 6: Kör testerna** — samma kommando som Step 2. Förväntat: alla PASS (inklusive de tre gamla).

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumUiState.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumViewModel.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/premium/PremiumViewModelTest.kt
git commit -m "fix(premium): stäng köpskärmen först när Play bekräftat köpet

Tidigare stängdes skärmen och välkomsttexten visades direkt vid klick,
även om användaren avbröt köpet.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Alla priser från Google Play (inga hårdkodade priser)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumUiState.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` och `values-en/strings.xml`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/premium/PremiumViewModelTest.kt`

- [ ] **Step 1: Skriv de fallerande testerna** (lägg till i `PremiumViewModelTest`):

```kotlin
    @Test
    fun `purchase is blocked until the selected price is loaded from Play`() =
        runTest {
            val noPrices = MutableStateFlow(FormattedPrices())
            var launched = false
            val vm = PremiumViewModel(FakePremiumRepository(), launchPurchase = { launched = true }, formattedPricesFlow = noPrices)
            assertEquals(false, vm.state.first().canPurchase)
            vm.purchase()
            assertEquals(false, launched)
        }

    @Test
    fun `lifetime can be bought when only the lifetime price is loaded`() =
        runTest {
            val onlyLifetime = MutableStateFlow(FormattedPrices(yearly = null, lifetime = "499 kr"))
            val vm = PremiumViewModel(FakePremiumRepository(), launchPurchase = {}, formattedPricesFlow = onlyLifetime)
            assertEquals(false, vm.state.first().canPurchase)
            vm.selectTier(PremiumTier.LIFETIME)
            assertEquals(true, vm.state.first().canPurchase)
        }
```
Uppdatera också det befintliga testet `purchase emits Active state via explicit launchPurchase stub` så att det skickar med `formattedPricesFlow = prices` (annars blockerar den nya vakten köpet):
```kotlin
            val vm = PremiumViewModel(repo, launchPurchase = { tier -> repo.markPurchased(tier) }, formattedPricesFlow = prices)
```

- [ ] **Step 2: Kör och se dem faila**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.premium.PremiumViewModelTest"
```
Förväntat: `Unresolved reference: canPurchase`.

- [ ] **Step 3: UI-state** — lägg till i `PremiumUiState` (inuti data-klassen, efter sista fältet — gör klassen till `data class PremiumUiState(...) { ... }`):

```kotlin
{
    /** Price for the currently selected tier, straight from Play. Null until loaded. */
    val selectedPrice: String?
        get() =
            when (selectedTier) {
                PremiumTier.YEARLY -> formattedYearlyPrice
                PremiumTier.LIFETIME -> formattedLifetimePrice
            }

    /** Never let the user buy something whose price we haven't shown them. */
    val canPurchase: Boolean
        get() = selectedPrice != null && !purchaseInFlight
}
```

- [ ] **Step 4: ViewModel-vakt** — i `purchase()`, ersätt `if (_state.value.purchaseInFlight) return` med:
```kotlin
        if (!_state.value.canPurchase) return
```

- [ ] **Step 5: Kör testerna** — Förväntat: alla PASS.

- [ ] **Step 6: Strängar (SV)** — i `values/strings.xml`:
  - Ta bort raderna med `premium_tier_yearly_price`, `premium_tier_yearly_sub`, `premium_tier_lifetime_price`, `premium_cta_subtext`.
  - Ersätt `premium_auto_renew_disclosure`-raden med:
    ```xml
    <string name="premium_auto_renew_disclosure">Förnyas årligen till %1$s. Avsluta när som helst i Google Play.</string>
    ```
  - Lägg till direkt efter den:
    ```xml
    <string name="premium_lifetime_note">Engångsköp. Ingen prenumeration.</string>
    <string name="premium_price_loading">Hämtar pris…</string>
    ```

- [ ] **Step 7: Strängar (EN)** — i `values-en/strings.xml`, samma fyra borttagningar, och:
    ```xml
    <string name="premium_auto_renew_disclosure">Renews yearly at %1$s. Cancel anytime in Google Play.</string>
    <string name="premium_lifetime_note">One-time purchase. No subscription.</string>
    <string name="premium_price_loading">Loading price…</string>
    ```

- [ ] **Step 8: Hitta andra användare av borttagna nycklar**

```bash
grep -rn "premium_tier_yearly_price\|premium_tier_yearly_sub\|premium_tier_lifetime_price\|premium_cta_subtext" composeApp/src iosApp androidApp 2>/dev/null | grep -v "/build/"
```
Förväntat: bara träffar i `PremiumScreen.kt` (import + användning). Andra träffar: ersätt med `premium_price_loading`-mönstret nedan.

- [ ] **Step 9: Skärmen** — i `PremiumScreen.kt`:

a) Ta bort importerna för de fyra borttagna nycklarna; lägg till:
```kotlin
import birdy_bird_scanner.composeapp.generated.resources.premium_lifetime_note
import birdy_bird_scanner.composeapp.generated.resources.premium_price_loading
```
b) Ersätt yearly-`TierCard`-anropets `price`/`sub`:
```kotlin
                    price = state.formattedYearlyPrice ?: stringResource(Res.string.premium_price_loading),
                    sub = null,
```
c) Ta bort hela `item { if (state.selectedTier == PremiumTier.YEARLY) { Text(text = stringResource(Res.string.premium_auto_renew_disclosure), ...) } }`-blocket under yearly-kortet.
d) Ersätt lifetime-`TierCard`-anropets `price`:
```kotlin
                    price = state.formattedLifetimePrice ?: stringResource(Res.string.premium_price_loading),
```
e) I `PrimaryCta`-anropet, ersätt `inFlight = state.purchaseInFlight,` med:
```kotlin
                    inFlight = state.purchaseInFlight,
                    enabled = state.canPurchase,
```
f) I `private fun PrimaryCta(...)`: lägg till parametern `enabled: Boolean,` efter `inFlight: Boolean,`, ändra `.clickable(enabled = !inFlight, onClick = onClick)` till `.clickable(enabled = enabled, onClick = onClick)`, och ändra `.background(AccentCopper)` till `.background(if (enabled || inFlight) AccentCopper else AccentCopper.copy(alpha = 0.45f))`.
g) Ersätt hela `item { Text(text = stringResource(Res.string.premium_cta_subtext), ...) }` efter CTA:n med:
```kotlin
            item {
                val note =
                    when (state.selectedTier) {
                        PremiumTier.YEARLY ->
                            state.formattedYearlyPrice?.let {
                                stringResource(Res.string.premium_auto_renew_disclosure, it)
                            }
                        PremiumTier.LIFETIME -> stringResource(Res.string.premium_lifetime_note)
                    }
                if (note != null) {
                    Text(
                        text = note,
                        color = MarginaliaInk,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
```

- [ ] **Step 10: Full gate** — Förväntat: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/ composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml composeApp/src/commonTest/kotlin/se/birdy/app/ui/premium/PremiumViewModelTest.kt
git commit -m "fix(premium): priser bara från Google Play, köp spärrat tills priset visats

Hårdkodade 199/499 kr och ~17 kr/mån borttagna (fel i andra valutor och
när priset ändras i Console). Förnyelsetexten visar Plays riktiga pris.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Paritetstest svenska ↔ engelska

**Files:**
- Create: `composeApp/src/androidUnitTest/kotlin/se/birdy/app/strings/StringsXml.kt`
- Create: `composeApp/src/androidUnitTest/kotlin/se/birdy/app/strings/StringsParityTest.kt`

- [ ] **Step 1: Hjälpare för att läsa strängfilerna**

```kotlin
package se.birdy.app.strings

import java.io.File

/** Minimal reader for compose-resources strings.xml files, for JVM guard tests. */
internal object StringsXml {
    private val stringRegex =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val arrayRegex =
        Regex("""<string-array name="([^"]+)"[^>]*>(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL)
    private val itemRegex = Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    val placeholderRegex = Regex("""%\d+\$[sd]""")

    fun strings(file: File): Map<String, String> =
        stringRegex.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }

    fun arrays(file: File): Map<String, List<String>> =
        arrayRegex.findAll(file.readText()).associate { m ->
            m.groupValues[1] to itemRegex.findAll(m.groupValues[2]).map { it.groupValues[1] }.toList()
        }

    /** `values/` is Swedish (default), `values-en/` is English. */
    fun swedish(): File = File(resourcesDir(), "values/strings.xml")

    fun english(): File = File(resourcesDir(), "values-en/strings.xml")

    private fun resourcesDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "src/commonMain/composeResources")
            if (File(candidate, "values/strings.xml").isFile) return candidate
            val nested = File(dir, "composeApp/src/commonMain/composeResources")
            if (File(nested, "values/strings.xml").isFile) return nested
            dir = dir.parentFile
        }
        error("composeResources not found from ${System.getProperty("user.dir")}")
    }
}
```

- [ ] **Step 2: Paritetstestet**

```kotlin
package se.birdy.app.strings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app ships in Swedish and English (spec 2026-09-24 D7). Every key must exist in both
 * languages with the same format placeholders, or one language shows a raw key / crashes.
 */
class StringsParityTest {
    private val sv = StringsXml.strings(StringsXml.swedish())
    private val en = StringsXml.strings(StringsXml.english())

    @Test
    fun `both languages define exactly the same string keys`() {
        assertTrue("Parsed zero strings — path or regex broken", sv.size > 100 && en.size > 100)
        assertEquals("Only in Swedish", emptySet<String>(), sv.keys - en.keys)
        assertEquals("Only in English", emptySet<String>(), en.keys - sv.keys)
    }

    @Test
    fun `placeholders match between languages`() {
        val mismatches =
            (sv.keys intersect en.keys).filter { key ->
                StringsXml.placeholderRegex.findAll(sv.getValue(key)).map { it.value }.toSet() !=
                    StringsXml.placeholderRegex.findAll(en.getValue(key)).map { it.value }.toSet()
            }
        assertEquals("Placeholder mismatch", emptyList<String>(), mismatches.sorted())
    }

    @Test
    fun `string arrays have the same keys and item counts`() {
        val svArrays = StringsXml.arrays(StringsXml.swedish())
        val enArrays = StringsXml.arrays(StringsXml.english())
        assertEquals(svArrays.keys, enArrays.keys)
        svArrays.forEach { (key, items) -> assertEquals("Array $key", items.size, enArrays.getValue(key).size) }
    }
}
```

- [ ] **Step 3: Kör testet**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.strings.StringsParityTest"
```
Förväntat: 3 PASS (pariteten var fullständig 2026-09-24, 572 nycklar per språk). Om något failar: det är en riktig lucka — lägg till den saknade översättningen, gissa inte bort den.

- [ ] **Step 4: Kanarie-kontroll (testet måste kunna faila)** — lägg tillfälligt till `<string name="parity_canary">x</string>` sist i `values/strings.xml`, kör testet → förväntat FAIL "Only in Swedish [parity_canary]". Ta bort raden igen och kör → PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidUnitTest/kotlin/se/birdy/app/strings/StringsXml.kt composeApp/src/androidUnitTest/kotlin/se/birdy/app/strings/StringsParityTest.kt
git commit -m "test(i18n): paritetsvakt svenska↔engelska (nycklar, platshållare, arrayer)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Sann Premium-copy på båda språken + vakt

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`, `values-en/strings.xml`
- Create: `composeApp/src/androidUnitTest/kotlin/se/birdy/app/strings/PremiumCopyTruthGuardTest.kt`

Premium låser upp exakt: **Fynd-kartan, PDF-export av fältdagboken, Säsongsstatistik, 7 premiummärken.** Ljud-ID är alltid gratis (BirdNET-licensen).

- [ ] **Step 1: Skriv det fallerande vakttestet**

```kotlin
package se.birdy.app.strings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Once people pay, every Premium promise must be true (spec 2026-09-24 §5.4). Premium unlocks
 * the finds map, PDF export, season statistics and 7 premium badges — nothing else. Audio-ID is
 * free forever (BirdNET CC BY-NC-SA) and must never be sold as Premium.
 */
class PremiumCopyTruthGuardTest {
    private val forbidden =
        listOf(
            "molnsynk", "cloud sync", "flera foton", "multiple photos", "migrationskarta",
            "migration map", "läten", "songs", "säkerhetskopi", "back up", "backup", "ljud-id", "audio",
        )

    @Test
    fun `premium strings never promise features that premium does not unlock`() {
        val offenders =
            listOf(StringsXml.swedish(), StringsXml.english()).flatMap { file ->
                StringsXml.strings(file)
                    .filterKeys { it.startsWith("premium_") }
                    .flatMap { (key, value) ->
                        forbidden.filter { value.lowercase().contains(it) }.map { "${file.parentFile.name}/$key: '$it'" }
                    }
            }
        assertEquals("Untrue Premium promises", emptyList<String>(), offenders)
    }
}
```

- [ ] **Step 2: Kör och se det faila**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.strings.PremiumCopyTruthGuardTest"
```
Förväntat: FAIL som listar minst `premium_archive_title`, `premium_archive_subtitle`, `premium_species_subtitle` i båda språken.

- [ ] **Step 3: Rätta svenska** — ersätt dessa rader i `values/strings.xml`:
```xml
    <string name="premium_subline">Stöd Birdy och få fler verktyg i fält.</string>
    <string name="premium_archive_title">Exportera fältdagboken</string>
    <string name="premium_archive_subtitle">Hela dagboken som PDF, redo att dela</string>
    <string name="premium_lifelist_preview_caption">Exempel: Vår 2026 · 23 nya arter</string>
    <string name="premium_species_title">Fler verktyg i fält</string>
    <string name="premium_species_subtitle">Fynd-karta · PDF-export · säsongsstatistik</string>
```

- [ ] **Step 4: Rätta engelska** — ersätt i `values-en/strings.xml`:
```xml
    <string name="premium_subline">Support Birdy and get more tools in the field.</string>
    <string name="premium_archive_title">Export your field journal</string>
    <string name="premium_archive_subtitle">Your whole journal as a PDF, ready to share</string>
    <string name="premium_lifelist_preview_caption">Example: Spring 2026 · 23 new species</string>
    <string name="premium_species_title">More tools in the field</string>
    <string name="premium_species_subtitle">Finds map · PDF export · season stats</string>
```

- [ ] **Step 5: Manuell granskning av resten** — lista alla Premium-strängar på båda språken:
```bash
grep -n "name=\"premium_" composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
```
Gå igenom varje rad mot listan (karta, PDF, statistik, 7 premiummärken). Rätta allt som lovar något annat, och ta bort tankstreck (—) ur de rader du ändå rör. Skriv i task-rapporten vilka rader du granskade och vilka du ändrade.

- [ ] **Step 6: Kör vakten + paritetstestet**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.strings.*"
```
Förväntat: alla PASS.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml composeApp/src/androidUnitTest/kotlin/se/birdy/app/strings/PremiumCopyTruthGuardTest.kt
git commit -m "fix(premium): sann Premium-copy på svenska och engelska + vakt

Tog bort löften om molnsynk, flera foton, migrationskarta och läten —
inget av det finns bakom Premium.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: Tack-skärm för tidiga användare + routing + debugverktyg

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/premium/EntryFlowDecider.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/premium/EntryFlowDeciderGrandfatherTest.kt` (ny)
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumThankYouScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt` (gör funktionslistan `internal`)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/debug/DiagnosticsScreen.kt`
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` (`buildDiagnosticsScreen`)
- Modify: `values/strings.xml`, `values-en/strings.xml`

- [ ] **Step 1: Fallerande test för beslutet**

```kotlin
package se.birdy.app.premium

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntryFlowDeciderGrandfatherTest {
    @Test
    fun `grandfathered user sees thanks once`() {
        assertTrue(EntryFlowDecider.shouldShowGrandfatherThanks(isGrandfathered = true, alreadyShown = false))
    }

    @Test
    fun `thanks is never shown twice`() {
        assertFalse(EntryFlowDecider.shouldShowGrandfatherThanks(isGrandfathered = true, alreadyShown = true))
    }

    @Test
    fun `new users never see thanks`() {
        assertFalse(EntryFlowDecider.shouldShowGrandfatherThanks(isGrandfathered = false, alreadyShown = false))
    }
}
```
Kör `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.premium.EntryFlowDeciderGrandfatherTest"` → förväntat: `Unresolved reference: shouldShowGrandfatherThanks`.

- [ ] **Step 2: Implementera** — lägg till i `object EntryFlowDecider` (sist i objektet):

```kotlin
    /** Early users see the thank-you screen exactly once after updating to 1.3.0. */
    fun shouldShowGrandfatherThanks(
        isGrandfathered: Boolean,
        alreadyShown: Boolean,
    ): Boolean = isGrandfathered && !alreadyShown
```
Kör testet igen → 3 PASS.

- [ ] **Step 3: Strängar SV** — lägg till i `values/strings.xml` efter `premium_price_loading`:
```xml
    <string name="premium_thanks_kicker">Tidig fältmedlem</string>
    <string name="premium_thanks_headline">Premium är ditt. *För alltid.*</string>
    <string name="premium_thanks_body">Du var med innan Birdy började ta betalt. Som tack behåller du allt i Premium utan kostnad, så länge du har appen.</string>
    <string name="premium_thanks_signoff">Tack för att du var med från början.</string>
    <string name="premium_thanks_continue">Fortsätt</string>
```
**EN** — i `values-en/strings.xml` efter `premium_price_loading`:
```xml
    <string name="premium_thanks_kicker">Early field member</string>
    <string name="premium_thanks_headline">Premium is yours. *Forever.*</string>
    <string name="premium_thanks_body">You joined before Birdy started charging. As a thank you, you keep everything in Premium for free, for as long as you have the app.</string>
    <string name="premium_thanks_signoff">Thank you for being here from the start.</string>
    <string name="premium_thanks_continue">Continue</string>
```

- [ ] **Step 4: Gör funktionslistan återanvändbar** — i `PremiumScreen.kt`, ändra `private data class PremiumFeatureItem(` till `internal data class PremiumFeatureItem(` och `private val premiumFeatures =` till `internal val premiumFeatures =`.

- [ ] **Step 5: Tack-skärmen** (funktionell version i nuvarande stil; Plan 2 ger den den nya looken)

```kotlin
package se.birdy.app.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_body
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_continue
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_headline
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_kicker
import birdy_bird_scanner.composeapp.generated.resources.premium_thanks_signoff
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.PlatformBackHandler
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat

/**
 * Shown to early (grandfathered) users: once automatically after updating to 1.3.0, and
 * instead of the purchase screen whenever they open Premium. Spec 2026-09-24 §5.2.
 */
@Composable
fun PremiumThankYouScreen(onClose: () -> Unit) {
    PlatformBackHandler(enabled = true, onBack = onClose)
    LazyColumn(
        modifier = Modifier.fillMaxSize().paperBackground(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            JournalIntro(
                label = stringResource(Res.string.premium_thanks_kicker),
                headline = stringResource(Res.string.premium_thanks_headline),
                sub = stringResource(Res.string.premium_thanks_body),
                topPadding = 56,
            )
        }
        items(premiumFeatures) { feature ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = AccentCopper, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(text = stringResource(feature.title), color = TextOnCreme, fontSize = 16.sp)
            }
        }
        item {
            Text(
                text = stringResource(Res.string.premium_thanks_signoff),
                fontFamily = rememberCaveat(),
                color = MarginaliaInk,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            )
        }
        item {
            Text(
                text = stringResource(Res.string.premium_thanks_continue),
                color = SandCreme,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(AccentCopper)
                        .clickable(onClick = onClose)
                        .padding(vertical = 14.dp),
            )
        }
    }
}
```
Kontrollera att `JournalIntro` har parametern `topPadding: Int` (verifierat 2026-09-24: `topPadding: Int = 28`).

- [ ] **Step 6: Routing i `AppScaffold.kt`**

a) Import: `import se.birdy.app.ui.premium.PremiumThankYouScreen`.

b) Överst i den första `LaunchedEffect(Unit) { ... }` (direkt efter `val premiumState = graph.premiumOverride ?: graph.premiumRepository.state.value`):
```kotlin
        // 1.3.0: early users get a one-time thank-you instead of any paywall (spec §5.2).
        if (graph.isGrandfathered) {
            val thanksShown = graph.userPreferences.grandfatherThanksShown.first()
            if (EntryFlowDecider.shouldShowGrandfatherThanks(isGrandfathered = true, alreadyShown = thanksShown)) {
                graph.userPreferences.setGrandfatherThanksShown(true)
                navController.navigate(AppRoute.Premium)
            }
            return@LaunchedEffect
        }
```
c) Ersätt `composable<AppRoute.Premium> { PremiumScreen(...) }` med:
```kotlin
            composable<AppRoute.Premium> {
                if (graph.isGrandfathered) {
                    PremiumThankYouScreen(onClose = { navController.popBackStack() })
                } else {
                    PremiumScreen(
                        viewModel = remember(graph) { graph.premiumViewModel() },
                        onClose = {
                            navController.popBackStack()
                            scope.launch { snackbarHostState.showSnackbar(dismissToast) }
                        },
                        onPurchaseComplete = {
                            navController.popBackStack(AppRoute.Premium, inclusive = true)
                            scope.launch { snackbarHostState.showSnackbar(welcomeToast) }
                        },
                    )
                }
            }
```

- [ ] **Step 7: Debugverktyg i `DiagnosticsScreen.kt`**

a) Lägg till parametrar efter `onSetSkipPremiumOverride`:
```kotlin
    forceGrandfathered: Flow<Boolean> = flowOf(false),
    onSetForceGrandfathered: suspend (Boolean) -> Unit = {},
    onResetGrandfatherThanks: suspend () -> Unit = {},
```
b) Direkt efter `val skip by skipPremiumOverride.collectAsState(initial = false)`:
```kotlin
    val forceGf by forceGrandfathered.collectAsState(initial = false)
```
c) Direkt före den första `HorizontalDivider()` (efter skip-switchens `Row`):
```kotlin
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Simulate early user (restart to apply)", modifier = Modifier.weight(1f))
            Switch(
                checked = forceGf,
                onCheckedChange = { value -> scope.launch { onSetForceGrandfathered(value) } },
            )
        }
        Button(onClick = { scope.launch { onResetGrandfatherThanks() } }) {
            Text("Show thank-you screen again (restart)")
        }
```
d) I `MainActivity.buildDiagnosticsScreen`, i `DiagnosticsScreen(...)`-anropet efter `onSetSkipPremiumOverride = ...`:
```kotlin
                    forceGrandfathered = userPreferences.debugForceGrandfathered,
                    onSetForceGrandfathered = { userPreferences.setDebugForceGrandfathered(it) },
                    onResetGrandfatherThanks = { userPreferences.setGrandfatherThanksShown(false) },
```

- [ ] **Step 8: Full gate + strängtester**

Kör full gate. Förväntat: `BUILD SUCCESSFUL` (inkl. `StringsParityTest`, `PremiumCopyTruthGuardTest`, `BirdNetLicenseGuardTest`).

- [ ] **Step 9: Enhets-/emulatorcheck om en enhet är ansluten** (`adb devices` visar en rad med `device`):

```bash
./gradlew :androidApp:installDebug
ADB="/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe"
$ADB shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```
Öppna Arkiv → Diagnostics → slå på "Simulate early user" → tryck "Show thank-you screen again" → `adb shell am force-stop se.birdy.android.debug` → starta igen. Förväntat: tack-skärmen visas en gång på det valda språket; tillbaka stänger den; vid nästa omstart visas den inte. Ta skärmdump (`$ADB exec-out screencap -p > docs/superpowers/screenshots/v1.3-plan1-thanks.png`). Ingen enhet ansluten: skriv "enhetscheck uppskjuten till Plan 3" i task-rapporten.

- [ ] **Step 10: Commit + push**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/premium/EntryFlowDecider.kt composeApp/src/commonTest/kotlin/se/birdy/app/premium/EntryFlowDeciderGrandfatherTest.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumThankYouScreen.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/premium/PremiumScreen.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/debug/DiagnosticsScreen.kt androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(premium): tack-skärm för tidiga användare (en gång, sedan i stället för köpskärmen)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
git push
gh pr checks --watch
```
Förväntat: båda CI-jobben gröna.

---

### Task 12: Plan 1-avslut — slutgate, merge till main, synka dokumenten

**Files:**
- Modify: `CLAUDE.md` (statusposten för 1.3.0), `AGENTS.md` (genereras)

- [ ] **Step 1: Slutgate lokalt**

Kör full gate + `./gradlew :androidApp:lintRelease`. Förväntat: båda `BUILD SUCCESSFUL`.

- [ ] **Step 2: Uppdatera CLAUDE.md** — i posten `🚀 RELEASE 1.3.0 / vC128 PÅGÅR`, byt slutet `**NÄSTA:** implementationsplan → ...` mot en rad som säger: Plan 1 klar (commit-intervall `<första>..<sista>` från `git log --oneline origin/main..HEAD`), vad som är kvar (Plan 2 utseendet, Plan 3 QA/release, Albins köp-test), samt för Macen: AGP 8.9.1/Gradle 8.11.1/compileSdk 36 kräver `platforms;android-36` i Macens Android SDK, nya `UserPreferences`-medlemmar finns i `NsUserDefaultsUserPreferences`. Kör sedan:
```bash
python tools/sync_agents_md.py
```

- [ ] **Step 3: Commit + push + merge**

```bash
git add CLAUDE.md AGENTS.md
git commit -m "docs: Plan 1 för 1.3.0 klar (API 36, betalning, tack-skärm)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
git push
gh pr checks --watch
```
Förväntat: gröna. Gör sedan en separat merge-PR bara för Plan 1 så att main får grunden även om Plan 2 drar ut:
```bash
gh pr ready
gh pr merge --merge --delete-branch=false
git fetch origin && git checkout release/1.3.0 && git merge --ff-only origin/main || git merge origin/main
```
(Om PR:en redan används för hela releasen och du inte ska merga än: hoppa över merge-stegen och notera det i rapporten. **Standard: merga** — main ska alltid vara den senaste gröna sanningen för Macen.)

---

### Task 13: Köp-testbygge (vC128, brytpunkt 0) till internt test-spår + runbook

**Files:**
- Modify: `docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md`

- [ ] **Step 1: Bygg köp-test-AAB:n**

```bash
./gradlew :androidApp:bundleRelease -Pbirdy.grandfatherCutoffMs=0 -Pbirdy.billingTestBuild=true
cp androidApp/build/outputs/bundle/release/androidApp-release.aab "$HOME/Desktop/birdy-1.3.0-vc128-KOPTEST-EJ-PRODUKTION.aab"
```
Förväntat: `BUILD SUCCESSFUL` (nyckelvakten passerar med de lokala nycklarna).

- [ ] **Step 2: 16 KB-grind + zipalign**

```bash
python tools/check_16kb_alignment.py "$HOME/Desktop/birdy-1.3.0-vc128-KOPTEST-EJ-PRODUKTION.aab"
"/c/Users/abbea/AppData/Local/Android/Sdk/build-tools/36.1.0/zipalign.exe" -c -P 16 -v 4 "$HOME/Desktop/birdy-1.3.0-vc128-KOPTEST-EJ-PRODUKTION.aab" | tail -1
```
Förväntat: alignment-skriptet rapporterar alla `.so` ≥ 0x4000; zipalign `Verification successful`.

- [ ] **Step 3: Uppdatera runbooken** — i `docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md`, lägg till överst efter titelraden:

```markdown
> **Uppdatering 2026-09-24 (release 1.3.0):** Appen ligger nu på AB:s utvecklarkonto. `PREMIUM_OPEN_FOR_LAUNCH=false` och grandfather-regeln (§5) är implementerade i 1.3.0 (`GrandfatherPolicy`, brytpunkt `GRANDFATHER_CUTOFF_MS` = 2026-10-02 00:00 Stockholm). Köp-testet körs med **vC128 byggt med `-Pbirdy.grandfatherCutoffMs=0 -Pbirdy.billingTestBuild=true`** (versionsnamnet blir `1.3.0-koptest`) (ingen är grandfathered, så betalväggen syns även på Albins gamla installation). **vC128 får ALDRIG befordras till produktion** — produktionsbygget blir vC129 med standardbrytpunkten.
>
> **Förberedelser i Play Console (AB):** (1) skapa `premium_yearly_v1` (prenumeration, årlig bas-plan) och `premium_lifetime_v1` (engångsköp), sätt priser, aktivera; (2) kontrollera att licensnyckeln under Monetization setup → Licensing är samma som `BIRDY_PLAY_LICENSE_KEY` i `~/.gradle/gradle.properties` (klistra in den på nytt om du är osäker, och bygg om); (3) lägg till ditt Google-konto som licenstestare; (4) ladda upp `birdy-1.3.0-vc128-KOPTEST-EJ-PRODUKTION.aab` till **Intern testning**.
>
> **Extra rutor för 1.3.0:** [ ] köp → döda appen direkt (`adb shell am force-stop se.birdy.android`) → starta igen → köpet ska bli kvitterat (Play Console → Beställningar; ingen automatisk återbetalning efter 3 dagar); [ ] flygplansläge → Inställningar → Återställ köp → "Kunde inte nå Google Play"; [ ] Premium-skärmen visar Plays priser (inga "Hämtar pris…" kvar efter några sekunder); [ ] köpknappen är grå tills priset syns; [ ] avbrutet köp → skärmen står kvar, ingen välkomsttext; [ ] genomfört köp → skärmen stänger + "Välkommen, fältmedlem."; [ ] texten under knappen visar rätt årspris respektive "Engångsköp. Ingen prenumeration."; [ ] allt ovan på både svenska och engelska (Inställningar → Språk).
```

- [ ] **Step 4: Commit + push**

```bash
git add docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md
git commit -m "docs(runbook): köp-test för 1.3.0 med vC128 (brytpunkt 0) på AB-kontot

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
git push
```

- [ ] **Step 5: Överlämning till Albin** — rapportera: AAB-filens sökväg på skrivbordet, de fyra Console-stegen från runbookens uppdatering, och att MapTiler-nyckeln i `~/.gradle/gradle.properties` är den läckta (se nedan). **MapTiler-rotation utan att döda kartan i live-appen:** skapa en NY nyckel i MapTiler Cloud nu → lägg den i `~/.gradle/gradle.properties` före produktionsbygget (Plan 3) → **radera den gamla nyckeln först när 1.3.0 nått de flesta användare (~2 veckor efter release)**, annars blir kartan tom i vC125 som folk fortfarande kör.
