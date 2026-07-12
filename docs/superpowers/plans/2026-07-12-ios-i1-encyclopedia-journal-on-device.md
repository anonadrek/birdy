# iOS i1 — Encyclopedia + Journal on Device Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Birdy's encyclopedia and field journal fully real on iOS — browse all 839 species *with their plate photos* and browse the journal — on the iOS simulator and on Albin's physical iPhone, with user preferences persisting across app relaunch.

**Architecture:** Almost every iOS platform seam for this surface was already implemented (and compiles) in i0 — both SQLite databases run on the SQLDelight native driver on-disk, photo storage, share/mailto/openUrl, back-handler, and the `speciesImageUri` Kotlin actual all exist and are real. i1 is therefore **not** about writing new actuals. It closes exactly three gaps, all isolated to `iosMain` / `iosTest` / the `iosApp` Xcode project so **Android is not touched at all**: (1) bundle the WebP plate images into the iOS app so `speciesImageUri` resolves; (2) replace the two in-memory `IosAppGraph` stubs (`InMemoryUserPreferences`, `InMemoryBadgeVersionStore`) with NSUserDefaults-backed persistent implementations; (3) prove the observation save→persist→read path works on the native driver with an automated iOS test (the user-facing "save" UI is deferred to i2 with the camera — decision 2026-07-12).

**Tech Stack:** Kotlin Multiplatform 2.1.20, Compose Multiplatform 1.8.2, SQLDelight 2.0.2 (native-driver + sqliter `DatabaseConfiguration`), Coil 3.0.4 (Skiko decoders on iOS), `platform.Foundation.NSUserDefaults`, xcodegen (`iosApp/project.yml`), Kotlin/Native `iosSimulatorArm64` tests (kotlin-test + kotlinx-coroutines-test + Turbine).

## Global Constraints

Copied from the v2 design spec (`docs/superpowers/specs/2026-07-07-birdy-ios-v2-design.md` §6) and the project working agreement. Every task implicitly includes these.

- **Android stays shippable after every commit.** These must stay green: `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug`. All i1 changes live in `iosMain`/`iosTest`/`iosApp` or additive build config — no `commonMain`/`androidMain`/production-Android file is edited.
- **Parity, not parity-plus.** No new user-facing features on this track. No manual "add a sighting" affordance is added in i1; the journal's save UI arrives naturally in i2 (camera). Ideas go to the backlog.
- **Privacy promise intact.** No telemetry, all data on device. NSUserDefaults holds only local preference values.
- **BirdNET-Lite is CC BY-NC-SA (audio-ID free forever).** Not touched in i1 (audio = i3), but never gate anything BirdNET-related behind premium.
- **Minimum iOS version: 16.0** (`iosApp/project.yml`). Bundle id `se.birdy.ios`.
- **Kotlin style gates:** new Kotlin files must pass `./gradlew ktlintCheck detekt` (run `ktlintFormat` first). Official style, 4-space indent.
- **Build prefix on this Mac:** if Gradle can't find Java, prepend `export JAVA_HOME="$HOME/.local/java21/Contents/Home"` to the command.
- **CLAUDE.md sync rule:** the plan is not "done" until CLAUDE.md's Status + the v2 Plan-of-plans table reflect i1 and are pushed.

---

## File Structure

New / modified files, grouped by responsibility:

**Preferences persistence (module `:shared:datastore`)**
- Modify `shared/datastore/build.gradle.kts` — add an `iosTest` dependencies block.
- Create `shared/datastore/src/iosMain/kotlin/se/birdy/datastore/NsUserDefaultsUserPreferences.kt` — the persistent `UserPreferences` impl (mirrors `AndroidUserPreferences`, same 18 keys + defaults, backed by NSUserDefaults).
- Modify `shared/datastore/src/iosMain/kotlin/se/birdy/datastore/UserPreferencesStore.ios.kt` — return the new impl instead of throwing.
- Create `shared/datastore/src/iosTest/kotlin/se/birdy/datastore/NsUserDefaultsUserPreferencesTest.kt` — persistence-across-instances tests.

**App-graph wiring + persistent badge store (module `:composeApp`)**
- Modify `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt` — swap the two in-memory stubs for persistent stores; bump `versionName`; update the i0-limitation comment.

**Save-path verification (module `:shared:data`)**
- Modify `shared/data/build.gradle.kts` — add an `iosTest` dependencies block.
- Create `shared/data/src/iosTest/kotlin/se/birdy/data/observation/SqlDelightObservationRepositoryIosTest.kt` — native-driver insert→read round-trip.

**Species image bundling (Xcode project)**
- Modify `iosApp/project.yml` — add a `type: folder` resource reference to `../shared/content/images`.
- Regenerate `iosApp/Birdy.xcodeproj/project.pbxproj` via `xcodegen generate` (generated; commit it).

**Docs**
- Modify `CLAUDE.md` — Status + v2 Plan-of-plans i1 row.
- Create screenshots under `docs/superpowers/screenshots/` during verification.

**What i1 does NOT touch** (already real from i0, verified): `SpeciesRepositoryProvider.ios.kt`, `shared/data/.../iosMain/DatabaseFactory.kt`, `PhotoStorageProvider.ios.kt`, `SpeciesImageUri.ios.kt` (Kotlin side already correct), `SettingsLauncher.ios.kt`, `PlatformBackHandler.ios.kt`, `FileBytes.kt`, `IoDispatcher.kt`.

---

### Task 1: Persistent iOS `UserPreferences` via NSUserDefaults (module `:shared:datastore`)

Replace the throwing `UserPreferencesStore.ios.kt` with a real NSUserDefaults-backed implementation so onboarding state, name, and all UI preferences survive relaunch. TDD: the test drives the persistence contract (a value written through one instance is visible through a fresh instance).

**Files:**
- Modify: `shared/datastore/build.gradle.kts` (add `iosTest.dependencies`)
- Create: `shared/datastore/src/iosMain/kotlin/se/birdy/datastore/NsUserDefaultsUserPreferences.kt`
- Modify: `shared/datastore/src/iosMain/kotlin/se/birdy/datastore/UserPreferencesStore.ios.kt`
- Test: `shared/datastore/src/iosTest/kotlin/se/birdy/datastore/NsUserDefaultsUserPreferencesTest.kt`

**Interfaces:**
- Consumes: `interface UserPreferences` (`shared/datastore/src/commonMain/.../UserPreferences.kt`) — 18 `Flow<T>` reads + 18 `suspend` setters; enums `AppLanguage`, `LifelistStat3Choice`, `ArchiveSort`, `LifelistSort`. Default values are defined by the Android actual (`AndroidUserPreferences`) and MUST match: `userName=""`, `hasSeenOnboarding=false`, `appLanguage=SYSTEM`, `lifelistStat3=STREAK`, `archiveChip="ALL"`, `archiveSort=ALPHA`, `lifelistSort=RECENT`, `firstInstallTimestamp=null`, `premiumModalLastShownAt=null`, `postOnboardingPremiumShown=false`, `pushPermissionAsked=false`, `dailyBirdPushEnabled=true`, `streakRiskPushEnabled=true`, `weeklyRecapPushEnabled=true`, `locationCaptureEnabled=false`, `weeklyTrophyPushEnabled=true`, `skipPremiumOverride=false`, `inAppReviewRequested=false`. The NSUserDefaults keys MUST be identical strings to the Android `Keys` object (so a future shared-format ever stays compatible).
- Produces: `internal class NsUserDefaultsUserPreferences(defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : UserPreferences`. `UserPreferencesStore.ios.kt` `preferences()` returns it.

- [ ] **Step 1: Add the `iosTest` dependency block to the datastore build**

Modify `shared/datastore/build.gradle.kts` — inside `sourceSets { }`, after the existing `jvmTest.dependencies { }` block (currently lines 21–25), add:

```kotlin
        iosTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
```

(`commonTest` already provides `libs.kotlin.test`; declaring it on `iosTest` is harmless and explicit. `kotlinx-coroutines-test` is multiplatform and provides `runTest` for the Kotlin/Native target.)

- [ ] **Step 2: Write the failing test**

Create `shared/datastore/src/iosTest/kotlin/se/birdy/datastore/NsUserDefaultsUserPreferencesTest.kt`:

```kotlin
package se.birdy.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NsUserDefaultsUserPreferencesTest {
    private val suite = "birdy-i1-prefs-test"

    private fun store() = NsUserDefaultsUserPreferences(NSUserDefaults(suiteName = suite)!!)

    @AfterTest
    fun cleanup() {
        NSUserDefaults(suiteName = suite)!!.removePersistentDomainForName(suite)
    }

    @Test
    fun defaults_match_android_when_nothing_persisted() =
        runTest {
            val prefs = store()
            assertEquals("", prefs.userName.first())
            assertEquals(false, prefs.hasSeenOnboarding.first())
            assertEquals(AppLanguage.SYSTEM, prefs.appLanguage.first())
            assertEquals("ALL", prefs.archiveChip.first())
            assertEquals(ArchiveSort.ALPHA, prefs.archiveSort.first())
            assertEquals(true, prefs.dailyBirdPushEnabled.first()) // true-by-default key
            assertEquals(false, prefs.locationCaptureEnabled.first())
            assertNull(prefs.firstInstallTimestamp.first())
        }

    @Test
    fun onboarding_flag_persists_across_instances() =
        runTest {
            store().setHasSeenOnboarding(true)
            // A brand-new instance (simulating an app relaunch) must read the persisted value.
            assertEquals(true, store().hasSeenOnboarding.first())
        }

    @Test
    fun true_default_boolean_survives_being_set_false() =
        runTest {
            store().setDailyBirdPushEnabled(false)
            assertEquals(false, store().dailyBirdPushEnabled.first())
        }

    @Test
    fun nullable_long_round_trips() =
        runTest {
            store().setFirstInstallTimestamp(1_700_000_000_000L)
            assertEquals(1_700_000_000_000L, store().firstInstallTimestamp.first())
        }

    @Test
    fun enum_round_trips() =
        runTest {
            store().setArchiveSort(ArchiveSort.FAMILY)
            assertEquals(ArchiveSort.FAMILY, store().archiveSort.first())
        }

    @Test
    fun string_round_trips() =
        runTest {
            store().setUserName("Albin")
            assertEquals("Albin", store().userName.first())
        }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `export JAVA_HOME="$HOME/.local/java21/Contents/Home"; ./gradlew :shared:datastore:iosSimulatorArm64Test --console=plain`
Expected: FAIL/compile-error — `NsUserDefaultsUserPreferences` does not exist yet.

- [ ] **Step 4: Implement the persistent store**

Create `shared/datastore/src/iosMain/kotlin/se/birdy/datastore/NsUserDefaultsUserPreferences.kt`:

```kotlin
package se.birdy.datastore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

/**
 * iOS actual backing store for [UserPreferences]. Mirrors [AndroidUserPreferences]
 * (DataStore) exactly — same 18 keys and same default values — but persists via
 * NSUserDefaults instead of DataStore. Each property is a MutableStateFlow seeded
 * from NSUserDefaults at construction and updated on every setter, so Flow consumers
 * react to changes exactly as on Android while the value survives app relaunch.
 *
 * Constructed once per process in the iOS AppGraph, so the in-memory flows are always
 * consistent with the persisted values (this instance is the only writer).
 */
internal class NsUserDefaultsUserPreferences(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : UserPreferences {
    private object Keys {
        const val USER_NAME = "user_name"
        const val HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        const val APP_LANGUAGE = "app_language"
        const val LIFELIST_STAT3 = "lifelist_stat3_choice"
        const val ARCHIVE_CHIP = "archive_chip"
        const val ARCHIVE_SORT = "archive_sort"
        const val LIFELIST_SORT = "lifelist_sort"
        const val FIRST_INSTALL_TIMESTAMP = "first_install_timestamp"
        const val PREMIUM_MODAL_LAST_SHOWN_AT = "premium_modal_last_shown_at_ms"
        const val POST_ONBOARDING_PREMIUM_SHOWN = "post_onboarding_premium_shown"
        const val PUSH_PERMISSION_ASKED = "push_permission_asked"
        const val DAILY_BIRD_PUSH_ENABLED = "daily_bird_push_enabled"
        const val STREAK_RISK_PUSH_ENABLED = "streak_risk_push_enabled"
        const val WEEKLY_RECAP_PUSH_ENABLED = "weekly_recap_push_enabled"
        const val LOCATION_CAPTURE_ENABLED = "location_capture_enabled"
        const val WEEKLY_TROPHY_PUSH_ENABLED = "weekly_trophy_push_enabled"
        const val SKIP_PREMIUM_OVERRIDE = "skip_premium_override"
        const val IN_APP_REVIEW_REQUESTED = "in_app_review_requested"
    }

    // ---- NSUserDefaults primitives ----
    private fun getString(key: String, default: String): String = defaults.stringForKey(key) ?: default

    private fun putString(key: String, value: String) = defaults.setObject(value, forKey = key)

    // objectForKey==null distinguishes "unset" from "explicitly false", so true-default keys work.
    private fun getBool(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    private fun putBool(key: String, value: Boolean) = defaults.setBool(value, forKey = key)

    // Nullable Longs are encoded as strings to keep "unset" == null (NSNumber can't express null).
    private fun getLongOrNull(key: String): Long? = defaults.stringForKey(key)?.toLongOrNull()

    private fun putLong(key: String, value: Long) = defaults.setObject(value.toString(), forKey = key)

    // ---- Backing flows, seeded from the persisted values ----
    private val _userName = MutableStateFlow(getString(Keys.USER_NAME, ""))
    private val _hasSeenOnboarding = MutableStateFlow(getBool(Keys.HAS_SEEN_ONBOARDING, false))
    private val _appLanguage =
        MutableStateFlow(
            AppLanguage.entries.firstOrNull { it.name == defaults.stringForKey(Keys.APP_LANGUAGE) }
                ?: AppLanguage.SYSTEM,
        )
    private val _lifelistStat3 =
        MutableStateFlow(
            LifelistStat3Choice.entries.firstOrNull { it.name == defaults.stringForKey(Keys.LIFELIST_STAT3) }
                ?: LifelistStat3Choice.STREAK,
        )
    private val _archiveChip = MutableStateFlow(getString(Keys.ARCHIVE_CHIP, "ALL"))
    private val _archiveSort =
        MutableStateFlow(
            ArchiveSort.entries.firstOrNull { it.name == defaults.stringForKey(Keys.ARCHIVE_SORT) }
                ?: ArchiveSort.ALPHA,
        )
    private val _lifelistSort =
        MutableStateFlow(
            LifelistSort.entries.firstOrNull { it.name == defaults.stringForKey(Keys.LIFELIST_SORT) }
                ?: LifelistSort.RECENT,
        )
    private val _firstInstallTimestamp = MutableStateFlow(getLongOrNull(Keys.FIRST_INSTALL_TIMESTAMP))
    private val _premiumModalLastShownAt = MutableStateFlow(getLongOrNull(Keys.PREMIUM_MODAL_LAST_SHOWN_AT))
    private val _postOnboardingPremiumShown = MutableStateFlow(getBool(Keys.POST_ONBOARDING_PREMIUM_SHOWN, false))
    private val _pushPermissionAsked = MutableStateFlow(getBool(Keys.PUSH_PERMISSION_ASKED, false))
    private val _dailyBirdPushEnabled = MutableStateFlow(getBool(Keys.DAILY_BIRD_PUSH_ENABLED, true))
    private val _streakRiskPushEnabled = MutableStateFlow(getBool(Keys.STREAK_RISK_PUSH_ENABLED, true))
    private val _weeklyRecapPushEnabled = MutableStateFlow(getBool(Keys.WEEKLY_RECAP_PUSH_ENABLED, true))
    private val _locationCaptureEnabled = MutableStateFlow(getBool(Keys.LOCATION_CAPTURE_ENABLED, false))
    private val _weeklyTrophyPushEnabled = MutableStateFlow(getBool(Keys.WEEKLY_TROPHY_PUSH_ENABLED, true))
    private val _skipPremiumOverride = MutableStateFlow(getBool(Keys.SKIP_PREMIUM_OVERRIDE, false))
    private val _inAppReviewRequested = MutableStateFlow(getBool(Keys.IN_APP_REVIEW_REQUESTED, false))

    override val userName: Flow<String> = _userName.asStateFlow()
    override val hasSeenOnboarding: Flow<Boolean> = _hasSeenOnboarding.asStateFlow()
    override val appLanguage: Flow<AppLanguage> = _appLanguage.asStateFlow()
    override val lifelistStat3: Flow<LifelistStat3Choice> = _lifelistStat3.asStateFlow()
    override val archiveChip: Flow<String> = _archiveChip.asStateFlow()
    override val archiveSort: Flow<ArchiveSort> = _archiveSort.asStateFlow()
    override val lifelistSort: Flow<LifelistSort> = _lifelistSort.asStateFlow()
    override val firstInstallTimestamp: Flow<Long?> = _firstInstallTimestamp.asStateFlow()
    override val premiumModalLastShownAt: Flow<Long?> = _premiumModalLastShownAt.asStateFlow()
    override val postOnboardingPremiumShown: Flow<Boolean> = _postOnboardingPremiumShown.asStateFlow()
    override val pushPermissionAsked: Flow<Boolean> = _pushPermissionAsked.asStateFlow()
    override val dailyBirdPushEnabled: Flow<Boolean> = _dailyBirdPushEnabled.asStateFlow()
    override val streakRiskPushEnabled: Flow<Boolean> = _streakRiskPushEnabled.asStateFlow()
    override val weeklyRecapPushEnabled: Flow<Boolean> = _weeklyRecapPushEnabled.asStateFlow()
    override val locationCaptureEnabled: Flow<Boolean> = _locationCaptureEnabled.asStateFlow()
    override val weeklyTrophyPushEnabled: Flow<Boolean> = _weeklyTrophyPushEnabled.asStateFlow()
    override val skipPremiumOverride: Flow<Boolean> = _skipPremiumOverride.asStateFlow()
    override val inAppReviewRequested: Flow<Boolean> = _inAppReviewRequested.asStateFlow()

    override suspend fun setUserName(name: String) {
        putString(Keys.USER_NAME, name)
        _userName.value = name
    }

    override suspend fun setHasSeenOnboarding(value: Boolean) {
        putBool(Keys.HAS_SEEN_ONBOARDING, value)
        _hasSeenOnboarding.value = value
    }

    override suspend fun setAppLanguage(value: AppLanguage) {
        putString(Keys.APP_LANGUAGE, value.name)
        _appLanguage.value = value
    }

    override suspend fun setLifelistStat3(value: LifelistStat3Choice) {
        putString(Keys.LIFELIST_STAT3, value.name)
        _lifelistStat3.value = value
    }

    override suspend fun setArchiveChip(value: String) {
        putString(Keys.ARCHIVE_CHIP, value)
        _archiveChip.value = value
    }

    override suspend fun setArchiveSort(value: ArchiveSort) {
        putString(Keys.ARCHIVE_SORT, value.name)
        _archiveSort.value = value
    }

    override suspend fun setLifelistSort(value: LifelistSort) {
        putString(Keys.LIFELIST_SORT, value.name)
        _lifelistSort.value = value
    }

    override suspend fun setFirstInstallTimestamp(ms: Long) {
        putLong(Keys.FIRST_INSTALL_TIMESTAMP, ms)
        _firstInstallTimestamp.value = ms
    }

    override suspend fun setPremiumModalLastShownAt(ms: Long) {
        putLong(Keys.PREMIUM_MODAL_LAST_SHOWN_AT, ms)
        _premiumModalLastShownAt.value = ms
    }

    override suspend fun setPostOnboardingPremiumShown(value: Boolean) {
        putBool(Keys.POST_ONBOARDING_PREMIUM_SHOWN, value)
        _postOnboardingPremiumShown.value = value
    }

    override suspend fun setPushPermissionAsked(value: Boolean) {
        putBool(Keys.PUSH_PERMISSION_ASKED, value)
        _pushPermissionAsked.value = value
    }

    override suspend fun setDailyBirdPushEnabled(value: Boolean) {
        putBool(Keys.DAILY_BIRD_PUSH_ENABLED, value)
        _dailyBirdPushEnabled.value = value
    }

    override suspend fun setStreakRiskPushEnabled(value: Boolean) {
        putBool(Keys.STREAK_RISK_PUSH_ENABLED, value)
        _streakRiskPushEnabled.value = value
    }

    override suspend fun setWeeklyRecapPushEnabled(value: Boolean) {
        putBool(Keys.WEEKLY_RECAP_PUSH_ENABLED, value)
        _weeklyRecapPushEnabled.value = value
    }

    override suspend fun setLocationCaptureEnabled(value: Boolean) {
        putBool(Keys.LOCATION_CAPTURE_ENABLED, value)
        _locationCaptureEnabled.value = value
    }

    override suspend fun setWeeklyTrophyPushEnabled(value: Boolean) {
        putBool(Keys.WEEKLY_TROPHY_PUSH_ENABLED, value)
        _weeklyTrophyPushEnabled.value = value
    }

    override suspend fun setSkipPremiumOverride(value: Boolean) {
        putBool(Keys.SKIP_PREMIUM_OVERRIDE, value)
        _skipPremiumOverride.value = value
    }

    override suspend fun setInAppReviewRequested(value: Boolean) {
        putBool(Keys.IN_APP_REVIEW_REQUESTED, value)
        _inAppReviewRequested.value = value
    }
}
```

- [ ] **Step 5: Wire the store's `preferences()` to the new impl**

Replace the whole body of `shared/datastore/src/iosMain/kotlin/se/birdy/datastore/UserPreferencesStore.ios.kt` with:

```kotlin
package se.birdy.datastore

/**
 * iOS actual. `platformContext` is unused (iOS needs no Context) — pass null.
 * Returns an NSUserDefaults-backed [UserPreferences] (persistent across relaunch).
 */
actual class UserPreferencesStore actual constructor(
    platformContext: Any?,
) {
    actual fun preferences(): UserPreferences = NsUserDefaultsUserPreferences()
}
```

- [ ] **Step 6: Run the test to verify it passes + lint**

Run: `export JAVA_HOME="$HOME/.local/java21/Contents/Home"; ./gradlew :shared:datastore:iosSimulatorArm64Test ktlintCheck detekt --console=plain`
Expected: PASS (6 tests) + ktlint/detekt clean. If ktlint complains, run `./gradlew :shared:datastore:ktlintFormat` and re-run.

- [ ] **Step 7: Commit**

```bash
git add shared/datastore
git commit -m "feat(ios): persistent UserPreferences via NSUserDefaults (i1)"
```

---

### Task 2: Wire `IosAppGraph` to the persistent stores (module `:composeApp`)

Swap the two in-memory i0 stubs in the iOS composition root for persistent implementations, so onboarding/settings/badge-backfill state survives relaunch on device.

**Files:**
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt`

**Interfaces:**
- Consumes: `UserPreferencesStore(platformContext: Any?).preferences(): UserPreferences` (from Task 1); `interface BadgeVersionStore { var lastSeen: Int }` (`composeApp/src/commonMain/.../bootstrap/BadgeVersionStore.kt`); `platform.Foundation.NSUserDefaults`.
- Produces: nothing new consumed by later tasks; this only changes runtime wiring. `AppGraph` constructor params `userPreferences` and `badgeVersionStore` are unchanged in type.

- [ ] **Step 1: Replace the in-memory stubs and bump the version marker**

In `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt`:

1. Update the imports block — remove `import se.birdy.datastore.InMemoryUserPreferences`, add `import platform.Foundation.NSUserDefaults` and `import se.birdy.datastore.UserPreferencesStore`.

2. Update the KDoc limitation comment (lines 25–33) — change the `InMemoryUserPreferences` bullet to reflect that i1 resolved it:

```kotlin
/**
 * iOS composition root — the iOS counterpart of MainActivity.buildAppGraph().
 *
 * Remaining stubs (each lifted by its owning plan):
 * - FakeBirdClassifier in DEMO mode: scanning is stubbed (i2).
 * - premiumOverride Active(LIFETIME): launch-parity with Android's
 *   PREMIUM_OPEN_FOR_LAUNCH; real StoreKit gating lands in i5.
 *
 * i1 resolved: UserPreferences + BadgeVersionStore now persist (NSUserDefaults).
 */
```

3. In `buildIosAppGraph()`, change the two stub arguments:

```kotlin
        badgeVersionStore = NsUserDefaultsBadgeVersionStore(),
        userPreferences = UserPreferencesStore(null).preferences(),
```

4. Bump the version marker:

```kotlin
        versionName = "1.2.0-ios-i1",
```

5. Replace the `InMemoryBadgeVersionStore` helper class (currently lines 59–62) with a persistent one:

```kotlin
/** Persists the last badge-catalog version we backfilled, so it does not re-run each launch. */
internal class NsUserDefaultsBadgeVersionStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : BadgeVersionStore {
    override var lastSeen: Int
        get() = defaults.integerForKey(KEY).toInt()
        set(value) {
            defaults.setInteger(value.toLong(), forKey = KEY)
        }

    private companion object {
        const val KEY = "birdy_badges.catalog_version_last_seen"
    }
}
```

(`integerForKey` returns 0 when unset — same default as `SharedPrefsBadgeVersionStore`, so first-launch backfill still runs exactly once.)

- [ ] **Step 2: Verify the iOS framework links and the app graph compiles**

Run: `export JAVA_HOME="$HOME/.local/java21/Contents/Home"; ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 ktlintCheck detekt --console=plain`
Expected: BUILD SUCCESSFUL, ktlint/detekt clean.

- [ ] **Step 3: Confirm Android is still shippable (should be untouched)**

Run: `export JAVA_HOME="$HOME/.local/java21/Contents/Home"; ./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL (no Android/common file changed).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt
git commit -m "feat(ios): wire AppGraph to persistent prefs + badge store (i1)"
```

---

### Task 3: Verify the observation save→persist→read path on the iOS native driver (module `:shared:data`)

Per the 2026-07-12 scope decision (browse-only on device; test-verify the save path), prove that inserting an observation and reading it back works on the SQLDelight **native** driver — the same driver the app uses on device. This is a verification/regression test; it exercises existing production code (`SqlDelightObservationRepository` + `BirdyData` native driver), not new code.

**Files:**
- Modify: `shared/data/build.gradle.kts` (add `iosTest.dependencies`)
- Test: `shared/data/src/iosTest/kotlin/se/birdy/data/observation/SqlDelightObservationRepositoryIosTest.kt`

**Interfaces:**
- Consumes: `SqlDelightObservationRepository(queries: ObservationQueries)` with `suspend fun insert(Observation)`, `fun observeAll(): Flow<List<Observation>>`, `fun observeById(id): Flow<Observation?>`; `BirdyData(driver).observationQueries`; `NativeSqliteDriver` + `co.touchlab.sqliter.DatabaseConfiguration` + `app.cash.sqldelight.driver.native.wrapConnection`; domain `Observation(id, speciesId, capturedAt, savedAt, photoPath, note, confidence, latitude, longitude, locationLabel)` (with defaulted `stampNumber`/`audioPath`/`sourceType`).
- Produces: nothing consumed downstream.

- [ ] **Step 1: Add the `iosTest` dependency block to the data build**

Modify `shared/data/build.gradle.kts` — inside `sourceSets { }`, after the existing `jvmTest.dependencies { }` block (currently lines 46–51), add:

```kotlin
        iosTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.sqldelight.native.driver)
        }
```

(Explicitly re-declaring `sqldelight.native.driver` on `iosTest` guarantees `NativeSqliteDriver`, `wrapConnection`, and the transitive `co.touchlab.sqliter.DatabaseConfiguration` resolve in the test source set, which does not inherit `iosMain`'s `implementation` deps.)

- [ ] **Step 2: Write the test**

Create `shared/data/src/iosTest/kotlin/se/birdy/data/observation/SqlDelightObservationRepositoryIosTest.kt`:

```kotlin
package se.birdy.data.observation

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import app.cash.turbine.test
import co.touchlab.sqliter.DatabaseConfiguration
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import se.birdy.data.db.BirdyData
import se.birdy.domain.observation.Observation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs the real observation repository against an in-memory instance of the
 * SQLDelight *native* driver — the driver the app uses on device — to prove the
 * save -> persist -> read round-trip works on iOS. (The user-facing save UI is
 * deferred to i2 with the camera; this locks the persistence layer in now.)
 */
class SqlDelightObservationRepositoryIosTest {
    private val drivers = mutableListOf<SqlDriver>()

    private fun newRepo(): SqlDelightObservationRepository {
        val driver =
            NativeSqliteDriver(
                DatabaseConfiguration(
                    name = "test-observations.db",
                    version = BirdyData.Schema.version.toInt(),
                    inMemory = true,
                    create = { conn -> wrapConnection(conn) { BirdyData.Schema.create(it) } },
                    upgrade = { _, _, _ -> },
                ),
            )
        drivers += driver
        return SqlDelightObservationRepository(BirdyData(driver).observationQueries)
    }

    @AfterTest
    fun closeDrivers() {
        drivers.forEach { it.close() }
        drivers.clear()
    }

    private fun sample(
        id: String,
        capturedAtMs: Long,
        speciesId: String = "Q25485",
    ) = Observation(
        id = id,
        speciesId = speciesId,
        capturedAt = Instant.fromEpochMilliseconds(capturedAtMs),
        savedAt = Instant.fromEpochMilliseconds(capturedAtMs + 1_000),
        photoPath = "/tmp/$id.jpg",
        note = "",
        confidence = 0.87f,
        latitude = null,
        longitude = null,
        locationLabel = null,
    )

    @Test
    fun insert_then_observeAll_emits_inserted_row_on_native_driver() =
        runTest {
            val repo = newRepo()
            repo.insert(sample("a", 1_000L))
            repo.observeAll().test {
                val rows = awaitItem()
                assertEquals(1, rows.size)
                assertEquals("a", rows[0].id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun insert_round_trips_optional_geotag_fields_on_native_driver() =
        runTest {
            val repo = newRepo()
            repo.insert(
                sample("c", 1_000L).copy(
                    latitude = 59.33,
                    longitude = 18.07,
                    locationLabel = "Stockholm",
                ),
            )
            repo.observeById("c").test {
                val row = awaitItem()!!
                assertEquals(59.33, row.latitude)
                assertEquals(18.07, row.longitude)
                assertEquals("Stockholm", row.locationLabel)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
```

- [ ] **Step 3: Run the test**

Run: `export JAVA_HOME="$HOME/.local/java21/Contents/Home"; ./gradlew :shared:data:iosSimulatorArm64Test ktlintCheck --console=plain`
Expected: PASS (2 tests). A failure here is a real finding about the native driver on iOS — investigate before proceeding, do not weaken the test.

- [ ] **Step 4: Commit**

```bash
git add shared/data/build.gradle.kts shared/data/src/iosTest
git commit -m "test(ios): observation save/persist/read round-trip on native driver (i1)"
```

---

### Task 4: Bundle the species plate images into the iOS app + agent-side verification (Xcode project)

Add the WebP plate-image tree to the iOS app bundle as a folder reference so `speciesImageUri.ios.kt` (`file://<mainBundle>/images/<QID>/<role>.webp`) resolves. Then build the app for the simulator, confirm the images are physically in the built `.app`, and confirm the app boots.

**RISK (the key unknown of i1):** Coil 3.0.4 on iOS decodes images via Skiko. WebP-from-`file://`-bundle has never actually rendered on iOS in this project. If the built bundle contains the files but images still show Coil's error placeholder in the interactive verify (Task 5), it is a *decode* problem, not a bundling problem — see the contingency at the end of this task.

**App size:** the bundle grows by ~323 MB (→ ~450 MB app, matching spec §10). Accepted for launch; On-Demand Resources is a later optimization.

**Files:**
- Modify: `iosApp/project.yml`
- Modify (generated): `iosApp/Birdy.xcodeproj/project.pbxproj` (via `xcodegen generate`)

**Interfaces:**
- Consumes: the committed image tree `shared/content/images/<QID>/{hero,secondary-*}.webp` (source of truth, 2064 files); the already-correct `speciesImageUri.ios.kt` which builds `file://<mainBundle.resourcePath>/images/<relativePath>`.
- Produces: an `images/` folder at the app-bundle root (`<Birdy.app>/images/<QID>/<role>.webp`).

- [ ] **Step 1: Add the images folder reference to the xcodegen spec**

In `iosApp/project.yml`, replace the target's `sources: [iosApp]` line (line 9) with a multi-line `sources` list that keeps the Swift/Info.plist source and adds the image tree as a **folder reference** (blue folder — preserves the `<QID>/` subtree; do NOT use an asset catalog, which would flatten paths and break the `file://` URL):

```yaml
    sources:
      - path: iosApp
      - path: ../shared/content/images
        name: images
        type: folder
        buildPhase: resources
```

Everything else in `project.yml` stays unchanged.

- [ ] **Step 2: Regenerate the Xcode project**

Run:
```bash
cd /Users/albinabrahamsson/dev/birdy/iosApp && ~/.local/bin/xcodegen generate
```
Expected: "Created project at .../Birdy.xcodeproj". Confirm the folder reference is present:
```bash
grep -c "images" /Users/albinabrahamsson/dev/birdy/iosApp/Birdy.xcodeproj/project.pbxproj
```
Expected: at least one match (the folder reference + build-file entry).

- [ ] **Step 3: Build the app for the simulator (this also links the Kotlin framework)**

Pick a booted-or-available simulator name (e.g. iPhone 16). Run:
```bash
cd /Users/albinabrahamsson/dev/birdy
export JAVA_HOME="$HOME/.local/java21/Contents/Home"
xcodebuild -project iosApp/Birdy.xcodeproj -scheme Birdy \
  -sdk iphonesimulator -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iosApp/build/dd build 2>&1 | tail -30
```
Expected: `** BUILD SUCCEEDED **`. (The `preBuildScripts` phase runs `:composeApp:embedAndSignAppleFrameworkForXcode` first — expect a few minutes on a cold build, plus time to copy ~323 MB of resources.)

- [ ] **Step 4: Confirm the images are physically in the built app bundle**

```bash
APP=$(find /Users/albinabrahamsson/dev/birdy/iosApp/build/dd/Build/Products -name "Birdy.app" -maxdepth 3 | head -1)
echo "App: $APP"
ls "$APP/images/Q25485/hero.webp" && echo "OK: onboarding hero image bundled"
find "$APP/images" -name "*.webp" | wc -l
```
Expected: `Q25485/hero.webp` exists (onboarding scene 2 hardcodes this path), and the webp count is ~2062. If the `images/` dir is missing, the folder reference did not attach to the Copy-Bundle-Resources phase — re-check Step 1 (`buildPhase: resources`) and regenerate.

- [ ] **Step 5: Boot a simulator, install, launch, and screenshot the boot screen**

```bash
xcrun simctl boot "iPhone 16" 2>/dev/null; sleep 3
xcrun simctl install booted "$APP"
xcrun simctl launch booted se.birdy.ios
sleep 4
mkdir -p docs/superpowers/screenshots
xcrun simctl io booted screenshot docs/superpowers/screenshots/i1-01-ios-boot.png
```
Expected: no crash; the screenshot shows the first onboarding scene (or the Listen tab if prefs already mark onboarding seen). This confirms the i1 build boots with images bundled. (Full visual browse + WebP-decode confirmation is the interactive step in Task 5 — `simctl` cannot inject taps, so the agent cannot navigate the UI here.)

- [ ] **Step 6: Commit the project change + boot screenshot**

```bash
git add iosApp/project.yml iosApp/Birdy.xcodeproj/project.pbxproj docs/superpowers/screenshots/i1-01-ios-boot.png
git commit -m "feat(ios): bundle species plate images into the app (i1)"
```

**Contingency (only if Task 5 shows placeholders despite files being present):** the files are bundled but Coil isn't decoding WebP on iOS. In order of preference: (1) confirm Coil 3.0.4's Skiko path actually handles WebP on `iosSimulatorArm64` (it should — Skia decodes WebP); (2) if a decoder is missing, configure the Coil `ImageLoader` used by the app with the Skiko/animated-WebP decoder factory rather than the default; (3) last resort (avoid — heavy, breaks parity with Android's exact files) transcode hero images. Capture findings and, if a code change is needed, add it as a follow-up task before declaring i1 done.

---

### Task 5: Interactive acceptance verify (Albin-in-the-loop) + Android regression + CLAUDE.md sync

This task contains the parts that need a human to interact with the UI (the simulator/device has no agent-side tap injection) plus the final Android shippability gate and the mandatory docs sync + push.

**Files:**
- Create: screenshots under `docs/superpowers/screenshots/`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: the i1 build from Tasks 1–4.
- Produces: the i1 exit-criteria evidence + synced project docs.

- [ ] **Step 1: Interactive acceptance on the simulator (Albin, via Simulator.app)**

With the app installed (Task 4), interact through the UI and confirm each — capture a screenshot for each into `docs/superpowers/screenshots/`:
1. Fresh launch → onboarding renders; scene 2 (Photo) shows the real **Q25485 hero photo** (proves WebP decode from the bundle). → `i1-02-onboarding-photo.png`
2. Complete onboarding → **hard-quit the app** (swipe up in the app switcher) → relaunch → onboarding does **NOT** repeat (proves persistent prefs). → `i1-03-relaunch-no-onboarding.png`
3. Encyclopedia (Archive tab): scroll the 839-species list; thumbnails show real plate photos, not placeholders. → `i1-04-encyclopedia.png`
4. Open a species profile: hero + secondary plate photos render. → `i1-05-species-profile.png`
5. Journal (Lifelist): opens, shows the empty-state (no saved finds yet — expected; save UI is i2), no crash. → `i1-06-journal-empty.png`
6. Settings → Export journal PDF: fails **gracefully** (error state, no crash — `JournalPdfRenderer.ios.kt` returns `Failed`; PDF export is i4). → `i1-07-pdf-graceful-fail.png`

If step 6 crashes instead of showing an error state, add a small guard in the common Settings PDF-export handling before finishing (the `JournalPdfRenderResult.Failed` path already exists for Android errors, so this is expected to be graceful — verify).

- [ ] **Step 2: Physical iPhone install + device verify (Albin — blocker: signing)**

Prerequisites (Albin, cannot be automated): a signing identity — a free Apple ID development team is sufficient for on-device installs (full Apple Developer enrollment is only required for StoreKit in i5). In Xcode: open `iosApp/Birdy.xcodeproj`, select the `Birdy` target → Signing & Capabilities → set the Team (this writes `DEVELOPMENT_TEAM`; if it should be committed, add it under `settings.base` in `project.yml` and regenerate). Connect the iPhone, trust it, select it as the run destination, Run.

On the iPhone, repeat the Task-5-Step-1 checks (browse 839 species with photos, browse journal, prefs persist across a hard relaunch). Capture at least `i1-08-iphone-encyclopedia.png` and `i1-09-iphone-species-profile.png`.

- [ ] **Step 3: Android shippability regression (agent)**

Run: `export JAVA_HOME="$HOME/.local/java21/Contents/Home"; ./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt --console=plain`
Expected: BUILD SUCCESSFUL. (i1 touched only iOS/test/build files, so this must be green.)

- [ ] **Step 4: Sync CLAUDE.md + Plan-of-plans (mandatory) and push**

Update `CLAUDE.md`:
- Status section: add an i1 entry (date 2026-07-12) summarizing what shipped (persistent prefs via NSUserDefaults; species images bundled; native-driver save-path test; encyclopedia+journal browse verified on simulator and — once done — iPhone), and note the scope decision (user-facing save deferred to i2).
- v2 Plan-of-plans table: change the i1 row Status from ⬜ to ✅ (or 🔄 if the physical-device step in Step 2 is still pending Albin's signing/device), and update the i0 "Kvar"/next-step line.

Then:
```bash
git add CLAUDE.md docs/superpowers/screenshots
git commit -m "docs: sync CLAUDE.md — iOS i1 encyclopedia+journal on device"
git push
```

---

## Exit criteria (plan i1 done — matches spec §3 row i1, as scoped 2026-07-12)

1. On the iOS simulator **and** Albin's physical iPhone: browse all 839 species **with their real plate photos**, open species profiles with hero + secondary photos, and browse the (empty) journal — no crashes.
2. User preferences persist across a hard app relaunch (onboarding does not repeat).
3. The observation save→persist→read path is proven on the native driver by `:shared:data:iosSimulatorArm64Test` (green), and the persistent-prefs contract by `:shared:datastore:iosSimulatorArm64Test` (green).
4. Android stays shippable: `:shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug` + `ktlintCheck detekt` green.
5. CLAUDE.md Status + v2 Plan-of-plans reflect i1 and are pushed.

Deferred to later plans (explicitly NOT in i1): user-facing journal save UI (i2, camera), photo/live scan (i2), audio (i3), map/notifications/PDF-export (i4), StoreKit (i5). The `applyLocale` no-op and `JournalPdfRenderer` `Failed` stub remain as-is until their owning plans.

## Self-review notes

- **Spec coverage:** spec §3 row i1 items — SQLDelight native driver (already real in i0; save path locked by Task 3 test), bundled species images (Task 4), light actuals (already real in i0; the one missing persistence actual is prefs — Tasks 1–2), device install (Task 5). ✓
- **No Android edits:** every changed file is under `shared/*/src/iosMain`, `shared/*/src/iosTest`, `composeApp/src/iosMain`, `iosApp/`, additive `iosTest.dependencies` build blocks, or docs. Android regression gate in Task 2 Step 3 and Task 5 Step 3. ✓
- **Type consistency:** `UserPreferencesStore(null).preferences()` returns `UserPreferences`; `NsUserDefaultsBadgeVersionStore : BadgeVersionStore`; both match the unchanged `AppGraph` param types. NSUserDefaults keys + default values mirror `AndroidUserPreferences` verbatim. ✓
- **Known risk carried explicitly:** WebP decode on iOS (Task 4 contingency). ✓
