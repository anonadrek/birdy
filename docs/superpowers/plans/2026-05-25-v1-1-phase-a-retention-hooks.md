# v1.1 Phase A — Retention hooks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status (2026-05-26):** Albin har godkänt start av Phase A-implementation. Phase B-spec defereras till efter Phase A landar (se `~/.claude/projects/.../memory/project_v1_2_phase_b_hooks.md`). Kör tasks 1–31 i ordning.

**Goal:** Bygg "Dagens fågel"-kort på Identifiera-tab, 2 push-notiser (08:00 daglig + söndag 18:00 streak-risk), och 3 nya premium-badges — för att adressera "låg återbesöks-pull" + "tunt premium-value-prop" från 12-testar-feedbacken. Target: nästa Closed Testing-AAB efter v1.0.2.

**Architecture:** Allt landar i existerande moduler. `DailyBirdSelector` + `DailyBird`-modell + `NotificationScheduler` (expect) i `:shared:domain`. `DailyBirdHistory`-tabell i `:shared:data` (SQLDelight). `UserPreferences`-tillägg i `:shared:datastore`. WorkManager-workers + actual scheduler + UI-komponenter i `composeApp/androidMain` resp `commonMain`. Inget nytt Gradle-modul.

**Tech Stack:** KMP + Compose Multiplatform, SQLDelight 2.x, DataStore preferences, compose-resources strings, WorkManager 2.8.1 (ny dep), kotlinx.datetime, kotlin.random.Random (seedad).

**Spec:** `docs/superpowers/specs/2026-05-25-v1-1-phase-a-retention-hooks.md`

---

## File Structure

### Create

| Fil | Ansvar |
|---|---|
| `shared/data/src/commonMain/sqldelight/se/birdy/data/db/DailyBirdHistory.sq` | SQLDelight-tabell + queries för dagens-fågel-historik per dag. |
| `shared/data/src/commonMain/kotlin/se/birdy/data/dailybird/DailyBirdHistoryRepository.kt` | Interface för history-repo. |
| `shared/data/src/commonMain/kotlin/se/birdy/data/dailybird/DailyBirdHistoryRepositoryImpl.kt` | SQLDelight-backad implementation. |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/dailybird/DailyBird.kt` | Datamodell: `speciesId`, `nameKey`, `eyebrowKey`. |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/dailybird/DailyBirdSelector.kt` | Abundance-viktad deterministisk slump + region/säsongs-filter. |
| `shared/domain/src/jvmTest/kotlin/se/birdy/domain/dailybird/DailyBirdSelectorTest.kt` | Unit-tests för algoritmen. |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/notification/NotificationScheduler.kt` | `expect interface` med 4 metoder. |
| `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationSchedulerImpl.kt` | Android-actual via WorkManager. |
| `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationChannels.kt` | Skapar 2 notification channels. |
| `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/workers/DailyBirdWorker.kt` | WorkManager-worker, 24h periodic. |
| `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/workers/StreakRiskWorker.kt` | WorkManager-worker, 7d periodic. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/DailyBirdCard.kt` | Compose-component, HeroMoss-gradient. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PermissionPromptSheet.kt` | Compose ModalBottomSheet, Field Journal-stil. |

### Modify

| Fil | Vad ändras |
|---|---|
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeRule.kt` | 3 nya sealed variants: `ObservedInHourRange`, `SundayStreak`, `DailyBirdMatches`. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/badges/RecalculateBadgesUseCase.kt` | Stöd för 3 nya rule-typer + extra `dailyBirdMatchCount`-param. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/badges/BadgeCatalogLoader.kt` | Parsa 3 nya YAML rule-types. |
| `composeApp/src/commonMain/composeResources/files/badges.yaml` | 3 nya entries: `early_pilgrim`, `sunday_birder`, `daily_bird_hunter`. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeStringMap.kt` | Mappa 3 nya badge-ids till string-resources. |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | Lägg till ~14 svenska strängar. |
| `composeApp/src/commonMain/composeResources/values-en/strings.xml` | Lägg till motsvarande engelska. |
| `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt` | 3 nya `Flow<Boolean>`-fält + setters. |
| `shared/datastore/src/androidMain/kotlin/se/birdy/datastore/UserPreferencesStore.kt` | Android-impl med `booleanPreferencesKey`. |
| `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/InMemoryUserPreferences.kt` | Test-impl-tillägg. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt` | Lägg till `selectDailyBird`, `notificationScheduler`, `dailyBirdHistory` fields. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt` | Anropa `dailyBirdHistory.recordMatchIfAny()` efter `repo.insert()`. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherScreen.kt` | Slot `DailyBirdCard` mellan `JournalIntro` och launch-cards. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherViewModel.kt` | Ladda `DailyBird` i state. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt` | "Aviseringar"-sektion med 2 toggles + helpline. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsViewModel.kt` | Toggle-handlers. |
| `composeApp/build.gradle.kts` | Lägg `androidx.work:work-runtime-ktx:2.8.1`. |
| `androidApp/src/main/AndroidManifest.xml` | `POST_NOTIFICATIONS` permission + `singleTop` launchMode + `birdy://` intent-filter. |
| `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` | `onNewIntent` deep-link, re-schedule workers i `onCreate`, permission-sheet trigger. |
| `androidApp/build.gradle.kts` | Bumpa `versionCode` 115 → 116, `versionName` 1.0.2 → 1.1.0-rc1. |

---

## Phase 1 — Domain data layer

### Task 1: DailyBirdHistory SQLDelight-tabell

**Files:**
- Create: `shared/data/src/commonMain/sqldelight/se/birdy/data/db/DailyBirdHistory.sq`

- [ ] **Step 1: Skapa SQLDelight-fil med tabell + queries**

```sql
CREATE TABLE daily_bird_history (
    date TEXT NOT NULL PRIMARY KEY,
    species_id TEXT NOT NULL
);

selectByDate:
SELECT * FROM daily_bird_history WHERE date = ?;

upsert:
INSERT OR REPLACE INTO daily_bird_history(date, species_id) VALUES (?, ?);

countDistinctDates:
SELECT COUNT(*) FROM daily_bird_history;

deleteAll:
DELETE FROM daily_bird_history;
```

- [ ] **Step 2: Verifiera SQLDelight-codegen kör grönt**

Run: `./gradlew :shared:data:generateCommonMainSqlDelightInterface`
Expected: BUILD SUCCESSFUL, ny fil `BirdyDatabase.kt` har metoder för `daily_bird_history`.

- [ ] **Step 3: Commit**

```bash
git add shared/data/src/commonMain/sqldelight/se/birdy/data/db/DailyBirdHistory.sq
git commit -m "feat(data): add DailyBirdHistory SQLDelight table"
```

---

### Task 2: DailyBirdHistoryRepository interface + impl

**Files:**
- Create: `shared/data/src/commonMain/kotlin/se/birdy/data/dailybird/DailyBirdHistoryRepository.kt`
- Create: `shared/data/src/commonMain/kotlin/se/birdy/data/dailybird/DailyBirdHistoryRepositoryImpl.kt`
- Test: `shared/data/src/commonTest/kotlin/se/birdy/data/dailybird/DailyBirdHistoryRepositoryTest.kt`

- [ ] **Step 1: Skriv failing test**

```kotlin
package se.birdy.data.dailybird

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import se.birdy.data.db.BirdyDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyBirdHistoryRepositoryTest {
    private lateinit var driver: SqlDriver
    private lateinit var repo: DailyBirdHistoryRepository

    @BeforeTest
    fun setup() {
        driver = inMemoryDriver(BirdyDatabase.Schema)
        repo = DailyBirdHistoryRepositoryImpl(BirdyDatabase(driver))
    }

    @Test
    fun `recordToday persists species for date`() = runTest {
        val date = LocalDate(2026, 5, 25)
        repo.recordToday(date, "Q25485")
        assertEquals("Q25485", repo.speciesIdForDate(date))
    }

    @Test
    fun `speciesIdForDate returns null when no row`() = runTest {
        assertNull(repo.speciesIdForDate(LocalDate(2026, 5, 25)))
    }

    @Test
    fun `recordToday is idempotent for same date`() = runTest {
        val date = LocalDate(2026, 5, 25)
        repo.recordToday(date, "Q25485")
        repo.recordToday(date, "Q25485")
        assertEquals(1, repo.matchCountForObservation(speciesId = "Q25485", observedOn = date))
    }

    @Test
    fun `matchCountForObservation increments only when species matches`() = runTest {
        repo.recordToday(LocalDate(2026, 5, 25), "Q25485")
        repo.recordToday(LocalDate(2026, 5, 26), "Q25485")
        repo.recordToday(LocalDate(2026, 5, 27), "Q12345")
        repo.markMatch(LocalDate(2026, 5, 25), "Q25485")
        repo.markMatch(LocalDate(2026, 5, 26), "Q25485")
        repo.markMatch(LocalDate(2026, 5, 27), "Q99999") // no match
        assertEquals(2, repo.totalMatchCount())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:data:jvmTest --tests "*DailyBirdHistoryRepositoryTest*"`
Expected: FAIL — `DailyBirdHistoryRepository`, `DailyBirdHistoryRepositoryImpl` finns inte.

- [ ] **Step 3: Skriv interface**

Fil: `shared/data/src/commonMain/kotlin/se/birdy/data/dailybird/DailyBirdHistoryRepository.kt`

```kotlin
package se.birdy.data.dailybird

import kotlinx.datetime.LocalDate

interface DailyBirdHistoryRepository {
    suspend fun recordToday(date: LocalDate, speciesId: String)
    suspend fun speciesIdForDate(date: LocalDate): String?
    suspend fun markMatch(date: LocalDate, observedSpeciesId: String)
    suspend fun totalMatchCount(): Int
}
```

Vi använder två operationer: `recordToday` (worker/selector skriver dagens art) och `markMatch` (efter obs-save jämför vi mot dagens row och om match → bumpas counter).

För att hålla data-modellen enkel utökar vi `.sq`-filen — uppdatera den nu:

- [ ] **Step 4: Uppdatera SQLDelight-tabell för match-tracking**

Modifiera `shared/data/src/commonMain/sqldelight/se/birdy/data/db/DailyBirdHistory.sq` så den kan tracka antal matchningar:

```sql
CREATE TABLE daily_bird_history (
    date TEXT NOT NULL PRIMARY KEY,
    species_id TEXT NOT NULL,
    matched INTEGER NOT NULL DEFAULT 0
);

selectByDate:
SELECT * FROM daily_bird_history WHERE date = ?;

upsertCandidate:
INSERT INTO daily_bird_history(date, species_id, matched)
VALUES (?, ?, 0)
ON CONFLICT(date) DO NOTHING;

markMatched:
UPDATE daily_bird_history SET matched = 1 WHERE date = ? AND species_id = ? AND matched = 0;

countMatched:
SELECT COUNT(*) FROM daily_bird_history WHERE matched = 1;

deleteAll:
DELETE FROM daily_bird_history;
```

- [ ] **Step 5: Skriv impl**

Fil: `shared/data/src/commonMain/kotlin/se/birdy/data/dailybird/DailyBirdHistoryRepositoryImpl.kt`

```kotlin
package se.birdy.data.dailybird

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import se.birdy.data.db.BirdyDatabase

class DailyBirdHistoryRepositoryImpl(
    private val db: BirdyDatabase,
) : DailyBirdHistoryRepository {
    private val queries get() = db.dailyBirdHistoryQueries

    override suspend fun recordToday(date: LocalDate, speciesId: String) = withContext(Dispatchers.Default) {
        queries.upsertCandidate(date = date.toString(), species_id = speciesId)
    }

    override suspend fun speciesIdForDate(date: LocalDate): String? = withContext(Dispatchers.Default) {
        queries.selectByDate(date.toString()).executeAsOneOrNull()?.species_id
    }

    override suspend fun markMatch(date: LocalDate, observedSpeciesId: String) = withContext(Dispatchers.Default) {
        queries.markMatched(date = date.toString(), species_id = observedSpeciesId)
    }

    override suspend fun totalMatchCount(): Int = withContext(Dispatchers.Default) {
        queries.countMatched().executeAsOne().toInt()
    }
}
```

- [ ] **Step 6: Uppdatera test för nytt API**

Justera testen från Step 1 till att använda `upsertCandidate` + `markMatched`-flödet (testen ovan använder redan rätt API-namn — verifiera):

```kotlin
// Update only the third + fourth test to:
@Test
fun `recordToday is idempotent for same date`() = runTest {
    val date = LocalDate(2026, 5, 25)
    repo.recordToday(date, "Q25485")
    repo.recordToday(date, "DIFFERENT_ID") // ON CONFLICT DO NOTHING
    assertEquals("Q25485", repo.speciesIdForDate(date))
    assertEquals(0, repo.totalMatchCount())
}

@Test
fun `markMatch increments counter only on matching species`() = runTest {
    repo.recordToday(LocalDate(2026, 5, 25), "Q25485")
    repo.recordToday(LocalDate(2026, 5, 26), "Q25485")
    repo.recordToday(LocalDate(2026, 5, 27), "Q12345")
    repo.markMatch(LocalDate(2026, 5, 25), "Q25485")
    repo.markMatch(LocalDate(2026, 5, 26), "Q25485")
    repo.markMatch(LocalDate(2026, 5, 27), "Q99999") // species mismatch — no-op
    assertEquals(2, repo.totalMatchCount())
}
```

- [ ] **Step 7: Run test to verify pass**

Run: `./gradlew :shared:data:jvmTest --tests "*DailyBirdHistoryRepositoryTest*"`
Expected: PASS — 4 tests green.

- [ ] **Step 8: Commit**

```bash
git add shared/data/src/commonMain/sqldelight/se/birdy/data/db/DailyBirdHistory.sq \
        shared/data/src/commonMain/kotlin/se/birdy/data/dailybird/ \
        shared/data/src/commonTest/kotlin/se/birdy/data/dailybird/
git commit -m "feat(data): add DailyBirdHistoryRepository with match tracking"
```

---

### Task 3: UserPreferences-tillägg

**Files:**
- Modify: `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt`
- Modify: `shared/datastore/src/androidMain/kotlin/se/birdy/datastore/UserPreferencesStore.kt`
- Modify: `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/InMemoryUserPreferences.kt`

- [ ] **Step 1: Lägg till 3 nya fält i interfacet**

Modifiera `UserPreferences.kt` — lägg efter `val premiumModalLastShownAt: Flow<Long?>`:

```kotlin
    val pushPermissionAsked: Flow<Boolean>
    val dailyBirdPushEnabled: Flow<Boolean>
    val streakRiskPushEnabled: Flow<Boolean>
```

Och efter `suspend fun setPremiumModalLastShownAt(ms: Long)`:

```kotlin
    suspend fun setPushPermissionAsked(value: Boolean)
    suspend fun setDailyBirdPushEnabled(value: Boolean)
    suspend fun setStreakRiskPushEnabled(value: Boolean)
```

- [ ] **Step 2: Lägg till Android-impl**

I `UserPreferencesStore.kt`, lägg till keys (efter existerande):

```kotlin
private val PUSH_PERMISSION_ASKED = booleanPreferencesKey("push_permission_asked")
private val DAILY_BIRD_PUSH_ENABLED = booleanPreferencesKey("daily_bird_push_enabled")
private val STREAK_RISK_PUSH_ENABLED = booleanPreferencesKey("streak_risk_push_enabled")
```

Lägg till Flow-properties (efter existerande):

```kotlin
override val pushPermissionAsked: Flow<Boolean> =
    dataStore.data.map { it[PUSH_PERMISSION_ASKED] ?: false }.catch { emit(false) }

override val dailyBirdPushEnabled: Flow<Boolean> =
    dataStore.data.map { it[DAILY_BIRD_PUSH_ENABLED] ?: true }.catch { emit(true) }

override val streakRiskPushEnabled: Flow<Boolean> =
    dataStore.data.map { it[STREAK_RISK_PUSH_ENABLED] ?: true }.catch { emit(true) }

override suspend fun setPushPermissionAsked(value: Boolean) {
    dataStore.edit { it[PUSH_PERMISSION_ASKED] = value }
}

override suspend fun setDailyBirdPushEnabled(value: Boolean) {
    dataStore.edit { it[DAILY_BIRD_PUSH_ENABLED] = value }
}

override suspend fun setStreakRiskPushEnabled(value: Boolean) {
    dataStore.edit { it[STREAK_RISK_PUSH_ENABLED] = value }
}
```

- [ ] **Step 3: Uppdatera InMemoryUserPreferences för tester**

I `InMemoryUserPreferences.kt`, lägg till backing-StateFlows + override implementations enligt befintligt mönster (se hur t.ex. `hasSeenOnboarding` är gjord).

```kotlin
private val _pushPermissionAsked = MutableStateFlow(false)
override val pushPermissionAsked: Flow<Boolean> = _pushPermissionAsked.asStateFlow()
override suspend fun setPushPermissionAsked(value: Boolean) { _pushPermissionAsked.value = value }

private val _dailyBirdPushEnabled = MutableStateFlow(true)
override val dailyBirdPushEnabled: Flow<Boolean> = _dailyBirdPushEnabled.asStateFlow()
override suspend fun setDailyBirdPushEnabled(value: Boolean) { _dailyBirdPushEnabled.value = value }

private val _streakRiskPushEnabled = MutableStateFlow(true)
override val streakRiskPushEnabled: Flow<Boolean> = _streakRiskPushEnabled.asStateFlow()
override suspend fun setStreakRiskPushEnabled(value: Boolean) { _streakRiskPushEnabled.value = value }
```

- [ ] **Step 4: Verifiera build**

Run: `./gradlew :shared:datastore:build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add shared/datastore/
git commit -m "feat(datastore): add push permission + per-channel toggles"
```

---

### Task 4: DailyBird datamodell

**Files:**
- Create: `shared/domain/src/commonMain/kotlin/se/birdy/domain/dailybird/DailyBird.kt`

- [ ] **Step 1: Skapa data class**

```kotlin
package se.birdy.domain.dailybird

/**
 * The bird suggested for a given local date.
 *
 * @param speciesId Wikidata QID (e.g., "Q25485").
 * @param nameKeyOverride If null, callers should resolve display name via SpeciesNameMap[speciesId].
 *                       Reserved for future custom names.
 * @param seasonTag Raw season-tag from species.season[currentMonth]: "breeding" | "present" | "migrating".
 */
data class DailyBird(
    val speciesId: String,
    val seasonTag: SeasonTag,
)

enum class SeasonTag {
    PRESENT,
    BREEDING,
    MIGRATING,
}
```

Vi håller `DailyBird` minimal — UI:t resolvar namnet via existerande `SpeciesNameMap` baserat på `speciesId`, och eyebrow-string mappas från `seasonTag` på render-tid.

- [ ] **Step 2: Verifiera build**

Run: `./gradlew :shared:domain:build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/domain/src/commonMain/kotlin/se/birdy/domain/dailybird/DailyBird.kt
git commit -m "feat(domain): add DailyBird data model + SeasonTag enum"
```

---

### Task 5: DailyBirdSelector core algoritm

**Files:**
- Create: `shared/domain/src/commonMain/kotlin/se/birdy/domain/dailybird/DailyBirdSelector.kt`
- Test: `shared/domain/src/jvmTest/kotlin/se/birdy/domain/dailybird/DailyBirdSelectorTest.kt`

- [ ] **Step 1: Skriv failing test**

```kotlin
package se.birdy.domain.dailybird

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import se.birdy.content.Abundance
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesImage
import se.birdy.content.model.SpeciesTaxonomy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyBirdSelectorTest {

    private fun species(
        id: String,
        abundance: Abundance = Abundance.ALLMÄN,
        regions: List<String> = listOf("SE"),
        seasonByMonth: Map<String, String> = (1..12).associate { it.toString() to "present" },
    ): Pair<SpeciesId, Species> = SpeciesId(id) to Species(
        id = SpeciesId(id),
        scientificName = "Test $id",
        taxonomy = SpeciesTaxonomy("Testidae", "Testfåglar", "Testus", "Test"),
        name = id,
        abundance = abundance,
        iucnStatus = "LC",
        regions = regions,
        season = seasonByMonth,
        description = null,
        migration = null,
        images = emptyList<SpeciesImage>(),
    )

    @Test
    fun `selectFor is deterministic per date`() = runTest {
        val pool = mapOf(species("Q1"), species("Q2"), species("Q3"))
        val selector = DailyBirdSelector { pool }
        val date = LocalDate(2026, 5, 25)

        val results = (1..100).map { selector.selectFor(date) }
        assertEquals(1, results.toSet().size, "Same date must produce same bird across 100 calls")
    }

    @Test
    fun `selectFor produces variance across dates`() = runTest {
        val pool = (1..50).associate { species("Q$it").first to species("Q$it").second }
        val selector = DailyBirdSelector { pool }
        val results = (0L until 30L).map { selector.selectFor(LocalDate(2026, 5, 1).plusDays(it)) }
        val distinct = results.mapNotNull { it?.speciesId }.toSet()
        assertTrue(distinct.size >= 25, "Expected at least 25 distinct birds across 30 dates, got ${distinct.size}")
    }

    @Test
    fun `selectFor filters out species outside Nordic regions`() = runTest {
        val pool = mapOf(
            species("Q_UK", regions = listOf("UK")),
            species("Q_SE", regions = listOf("SE")),
        )
        val selector = DailyBirdSelector { pool }
        val results = (0L until 30L).map { selector.selectFor(LocalDate(2026, 1, 1).plusDays(it))?.speciesId }
        assertTrue(results.all { it == "Q_SE" }, "Expected only Nordic species, got ${results.toSet()}")
    }

    @Test
    fun `selectFor filters out species absent in current month`() = runTest {
        val mayMap = (1..12).associate { it.toString() to if (it == 5) "absent" else "present" }
        val alwaysMap = (1..12).associate { it.toString() to "present" }
        val pool = mapOf(
            species("Q_ABSENT_MAY", seasonByMonth = mayMap),
            species("Q_ALWAYS", seasonByMonth = alwaysMap),
        )
        val selector = DailyBirdSelector { pool }
        val mayResult = selector.selectFor(LocalDate(2026, 5, 15))
        assertEquals("Q_ALWAYS", mayResult?.speciesId)
    }

    @Test
    fun `selectFor weights common bucket around 75 percent`() = runTest {
        val common = (1..10).map { species("C$it", abundance = Abundance.ALLMÄN) }
        val rare = (1..10).map { species("R$it", abundance = Abundance.SÄLLSYNT) }
        val pool = (common + rare).toMap()
        val selector = DailyBirdSelector { pool }
        val results = (0L until 1000L).map {
            selector.selectFor(LocalDate(2026, 1, 1).plusDays(it))?.speciesId
        }
        val commonCount = results.count { it?.startsWith("C") == true }
        assertTrue(commonCount in 700..800, "Expected 70-80% common, got $commonCount/1000")
    }

    @Test
    fun `selectFor returns null when no candidates`() = runTest {
        val noNordic = mapOf(species("Q_UK", regions = listOf("UK")))
        val selector = DailyBirdSelector { noNordic }
        assertNull(selector.selectFor(LocalDate(2026, 5, 25)))
    }

    @Test
    fun `selectFor maps season tags correctly`() = runTest {
        val breedingMap = (1..12).associate { it.toString() to if (it == 5) "breeding" else "absent" }
        val migratingMap = (1..12).associate { it.toString() to if (it == 5) "migrating" else "absent" }
        val pool = mapOf(
            species("Q_BREED", seasonByMonth = breedingMap),
            species("Q_MIG", seasonByMonth = migratingMap),
        )
        val selector = DailyBirdSelector { pool }
        val result = selector.selectFor(LocalDate(2026, 5, 15))
        assertNotNull(result)
        assertTrue(result.seasonTag == SeasonTag.BREEDING || result.seasonTag == SeasonTag.MIGRATING)
    }
}

private fun LocalDate.plusDays(days: Long): LocalDate {
    val epoch = this.toEpochDays()
    return LocalDate.fromEpochDays((epoch + days).toInt())
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:domain:jvmTest --tests "*DailyBirdSelectorTest*"`
Expected: FAIL — `DailyBirdSelector` finns inte.

- [ ] **Step 3: Skriv implementation**

```kotlin
package se.birdy.domain.dailybird

import kotlinx.datetime.LocalDate
import se.birdy.content.Abundance
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import kotlin.random.Random

class DailyBirdSelector(
    private val speciesProvider: suspend () -> Map<SpeciesId, Species>,
    private val regionBucket: Set<String> = NORDIC_BUCKET,
    private val regionSeed: String = "NORDIC",
) {
    suspend fun selectFor(date: LocalDate): DailyBird? {
        val all = speciesProvider()
        val monthKey = date.monthNumber.toString()
        val candidates = all.values
            .mapNotNull { species ->
                val rawTag = species.season[monthKey] ?: return@mapNotNull null
                val tag = rawTag.toSeasonTag() ?: return@mapNotNull null
                val nordic = species.regions.any { it in regionBucket }
                if (!nordic) return@mapNotNull null
                species to tag
            }

        if (candidates.isEmpty()) return null

        val (common, rare) = candidates.partition { (s, _) ->
            s.abundance == Abundance.ALLMÄN || s.abundance == Abundance.MINDRE_ALLMÄN
        }

        val seed = "${date.year}-${date.monthNumber}-${date.dayOfMonth}-$regionSeed".hashCode().toLong()
        val rng = Random(seed)

        val pickCommon = rng.nextDouble() < COMMON_WEIGHT
        val bucket = when {
            pickCommon && common.isNotEmpty() -> common
            !pickCommon && rare.isNotEmpty() -> rare
            common.isNotEmpty() -> common
            else -> rare
        }
        val (picked, tag) = bucket[rng.nextInt(bucket.size)]
        return DailyBird(speciesId = picked.id.value, seasonTag = tag)
    }

    private fun String.toSeasonTag(): SeasonTag? = when (this.lowercase()) {
        "breeding" -> SeasonTag.BREEDING
        "present" -> SeasonTag.PRESENT
        "migrating" -> SeasonTag.MIGRATING
        else -> null
    }

    companion object {
        val NORDIC_BUCKET = setOf("SE", "NO", "FI", "DK")
        const val COMMON_WEIGHT = 0.75
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:domain:jvmTest --tests "*DailyBirdSelectorTest*"`
Expected: PASS — 7 tests green.

- [ ] **Step 5: Commit**

```bash
git add shared/domain/src/commonMain/kotlin/se/birdy/domain/dailybird/DailyBirdSelector.kt \
        shared/domain/src/jvmTest/kotlin/se/birdy/domain/dailybird/DailyBirdSelectorTest.kt
git commit -m "feat(domain): add DailyBirdSelector with abundance-weighted deterministic random"
```

---

### Task 6: 3 nya BadgeRule-variants

**Files:**
- Modify: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeRule.kt`

- [ ] **Step 1: Lägg till nya variants**

I `BadgeRule.kt`, lägg till efter `ObservedBeforeHour`:

```kotlin
    /** Observation captured locally within [startHour, endHourExclusive). */
    data class ObservedInHourRange(
        val startHour: Int,
        val endHourExclusive: Int,
        override val target: Int,
    ) : BadgeRule

    /** N consecutive Sundays with at least one observation each. */
    data class SundayStreak(
        override val target: Int,
    ) : BadgeRule

    /** N distinct dates where the saved observation matched that day's Daily Bird. */
    data class DailyBirdMatches(
        override val target: Int,
    ) : BadgeRule
```

- [ ] **Step 2: Verifiera build**

Run: `./gradlew :shared:domain:build`
Expected: BUILD SUCCESSFUL (kompilerings-fel kommer i Task 7 om vi inte hanterar dem där).

OBS: Buildet kommer fortfarande grönt eftersom `when`-expression i `RecalculateBadgesUseCase` använder en `sealed interface` — Kotlin kräver att vi hanterar alla varianter, så det blir "non-exhaustive when"-warning, men inte build-error om man inte returnerar något. Vi fixar i Task 7.

- [ ] **Step 3: Commit**

```bash
git add shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeRule.kt
git commit -m "feat(domain): add ObservedInHourRange + SundayStreak + DailyBirdMatches rule variants"
```

---

### Task 7: Wire nya rule-variants i RecalculateBadgesUseCase

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/badges/RecalculateBadgesUseCase.kt`
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/badges/RecalculateBadgesUseCaseTest.kt` (om existerar; annars skapa)

- [ ] **Step 1: Lägg till `dailyBirdMatchCount`-parameter**

Modifiera `RecalculateBadgesUseCase.newUnlocks` + `currentValue` + `evaluate` + `rawValue` så de tar emot en extra `dailyBirdMatchCount: Int` (default 0). Detta krävs eftersom `DailyBirdMatches` är externt state, inte härlett från observations.

```kotlin
fun newUnlocks(
    observations: List<Observation>,
    speciesByQid: Map<SpeciesId, Species>,
    catalog: BadgeCatalog,
    existingUnlocks: Set<String>,
    dailyBirdMatchCount: Int = 0,
): List<BadgeUnlock> {
    val now = clock.now()
    return catalog.badges
        .filter { it.id !in existingUnlocks }
        .filter { it.rule !is BadgeRule.Manual }
        .filter { evaluate(it.rule, observations, speciesByQid, dailyBirdMatchCount) }
        .map { BadgeUnlock(it.id, now) }
}

fun currentValue(
    rule: BadgeRule,
    observations: List<Observation>,
    speciesByQid: Map<SpeciesId, Species>,
    dailyBirdMatchCount: Int = 0,
): Int = rawValue(rule, observations, speciesByQid, dailyBirdMatchCount).coerceAtMost(rule.target)

private fun evaluate(
    rule: BadgeRule,
    observations: List<Observation>,
    speciesByQid: Map<SpeciesId, Species>,
    dailyBirdMatchCount: Int,
): Boolean = rawValue(rule, observations, speciesByQid, dailyBirdMatchCount) >= rule.target
```

- [ ] **Step 2: Lägg till `when`-grenar för 3 nya variants**

I `rawValue`, lägg till efter `BadgeRule.ObservedBeforeHour`-grenen:

```kotlin
        is BadgeRule.ObservedInHourRange ->
            observations.count { o ->
                val h = o.capturedAt.toLocalDateTime(zone).hour
                h >= rule.startHour && h < rule.endHourExclusive
            }
        is BadgeRule.SundayStreak ->
            consecutiveSundaysWithObservations(observations.map { it.capturedAt }, zone)
        is BadgeRule.DailyBirdMatches -> dailyBirdMatchCount
```

- [ ] **Step 3: Lägg till helper-funktion för consecutiveSundaysWithObservations**

I `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/StreakHelpers.kt`, lägg till:

```kotlin
/**
 * Counts the longest run of consecutive Sundays (by local date) that each have at least one observation.
 * Sundays are determined by ISO weekday == 7.
 */
fun consecutiveSundaysWithObservations(
    instants: List<Instant>,
    zone: TimeZone,
): Int {
    val sundays = instants
        .map { it.toLocalDateTime(zone).date }
        .filter { it.dayOfWeek == DayOfWeek.SUNDAY }
        .toSortedSet()
    if (sundays.isEmpty()) return 0
    var best = 1
    var current = 1
    val list = sundays.toList()
    for (i in 1 until list.size) {
        val gap = list[i].toEpochDays() - list[i - 1].toEpochDays()
        if (gap == 7) {
            current++
            best = maxOf(best, current)
        } else {
            current = 1
        }
    }
    return best
}
```

(Imports: `kotlinx.datetime.DayOfWeek`, `kotlinx.datetime.Instant`, `kotlinx.datetime.TimeZone`, `kotlinx.datetime.toLocalDateTime`.)

- [ ] **Step 4: Skriv unit-tests för 3 nya rule-typer**

I `composeApp/src/commonTest/kotlin/se/birdy/app/badges/RecalculateBadgesUseCaseTest.kt` (skapa om saknas, eller utöka), lägg till:

```kotlin
@Test
fun `ObservedInHourRange counts captures in window`() {
    val zone = TimeZone.UTC
    val obs = listOf(
        observationAt(hour = 4, zone),  // before
        observationAt(hour = 5, zone),  // in
        observationAt(hour = 6, zone),  // in
        observationAt(hour = 7, zone),  // out (exclusive)
    )
    val uc = RecalculateBadgesUseCase(zone = zone, clock = Clock.System)
    val rule = BadgeRule.ObservedInHourRange(startHour = 5, endHourExclusive = 7, target = 1)
    assertEquals(2, uc.currentValue(rule, obs, emptyMap()))
}

@Test
fun `SundayStreak counts consecutive Sundays`() {
    val zone = TimeZone.UTC
    val baseSunday = LocalDate(2026, 5, 24) // Sunday
    val obs = listOf(0, 7, 14, 28).map { offset ->
        observationOn(baseSunday.plusDaysL(offset), zone)
    }
    // 3 consecutive (May 24, 31, June 7), then skip, then 1 (June 21)
    val uc = RecalculateBadgesUseCase(zone = zone, clock = Clock.System)
    val rule = BadgeRule.SundayStreak(target = 4)
    assertEquals(3, uc.currentValue(rule, obs, emptyMap()))
}

@Test
fun `DailyBirdMatches reads external counter`() {
    val uc = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = Clock.System)
    val rule = BadgeRule.DailyBirdMatches(target = 3)
    assertEquals(2, uc.currentValue(rule, emptyList(), emptyMap(), dailyBirdMatchCount = 2))
    assertEquals(3, uc.currentValue(rule, emptyList(), emptyMap(), dailyBirdMatchCount = 5))
}

private fun observationAt(hour: Int, zone: TimeZone): Observation {
    val dt = LocalDateTime(2026, 5, 25, hour, 0, 0)
    return Observation(
        id = "obs-$hour", speciesId = "Q1",
        capturedAt = dt.toInstant(zone), savedAt = dt.toInstant(zone),
        photoPath = "", note = "", confidence = 1f,
        latitude = null, longitude = null, locationLabel = null, stampNumber = hour,
    )
}

private fun observationOn(date: LocalDate, zone: TimeZone): Observation {
    val dt = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 12, 0, 0)
    return Observation(
        id = "obs-$date", speciesId = "Q1",
        capturedAt = dt.toInstant(zone), savedAt = dt.toInstant(zone),
        photoPath = "", note = "", confidence = 1f,
        latitude = null, longitude = null, locationLabel = null, stampNumber = 0,
    )
}

private fun LocalDate.plusDaysL(days: Int): LocalDate =
    LocalDate.fromEpochDays(this.toEpochDays() + days)
```

- [ ] **Step 5: Run tester**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*RecalculateBadgesUseCaseTest*"` och `./gradlew :shared:domain:jvmTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/StreakHelpers.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/badges/RecalculateBadgesUseCase.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/badges/RecalculateBadgesUseCaseTest.kt
git commit -m "feat(badges): wire ObservedInHourRange + SundayStreak + DailyBirdMatches rules"
```

---

### Task 8: BadgeCatalogLoader — parsa nya YAML rule-types

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/badges/BadgeCatalogLoader.kt`

- [ ] **Step 1: Inspektera existerande parsing**

Read: `composeApp/src/commonMain/kotlin/se/birdy/app/badges/BadgeCatalogLoader.kt`

Hitta funktionen som mappar YAML `type:` → `BadgeRule`-variant (sannolikt en `when (rule.type)`-uttryck).

- [ ] **Step 2: Lägg till 3 nya `when`-grenar**

```kotlin
"observed_in_hour_range" -> BadgeRule.ObservedInHourRange(
    startHour = raw.start_hour ?: error("observed_in_hour_range requires start_hour"),
    endHourExclusive = raw.end_hour_exclusive ?: error("observed_in_hour_range requires end_hour_exclusive"),
    target = raw.target,
)
"sunday_streak" -> BadgeRule.SundayStreak(target = raw.target)
"daily_bird_matches" -> BadgeRule.DailyBirdMatches(target = raw.target)
```

Och i `RawRule` (eller motsvarande data-class):

```kotlin
val start_hour: Int? = null,
val end_hour_exclusive: Int? = null,
```

- [ ] **Step 3: Verifiera build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/badges/BadgeCatalogLoader.kt
git commit -m "feat(badges): parse new YAML rule types (hour_range, sunday_streak, daily_bird_matches)"
```

---

## Phase 2 — Notification infra

### Task 9: NotificationScheduler interface

**Files:**
- Create: `shared/domain/src/commonMain/kotlin/se/birdy/domain/notification/NotificationScheduler.kt`

- [ ] **Step 1: Skapa interface**

```kotlin
package se.birdy.domain.notification

interface NotificationScheduler {
    fun scheduleDailyBird()
    fun scheduleStreakRiskCheck()
    fun cancelDailyBird()
    fun cancelStreakRiskCheck()
}
```

- [ ] **Step 2: Verifiera build**

Run: `./gradlew :shared:domain:build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/domain/src/commonMain/kotlin/se/birdy/domain/notification/NotificationScheduler.kt
git commit -m "feat(domain): add NotificationScheduler interface"
```

---

### Task 10: WorkManager dep + notification channels

**Files:**
- Modify: `composeApp/build.gradle.kts`
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationChannels.kt`

- [ ] **Step 1: Lägg till WorkManager-dep**

I `composeApp/build.gradle.kts`, hitta `androidMain.dependencies { ... }` (eller motsvarande source-set-block) och lägg till:

```kotlin
implementation("androidx.work:work-runtime-ktx:2.8.1")
```

- [ ] **Step 2: Skapa notification-channels-init**

Fil: `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationChannels.kt`

```kotlin
package se.birdy.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {
    const val DAILY_BIRD = "daily_bird"
    const val STREAK_RISK = "streak_risk"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService<NotificationManager>() ?: return
        if (mgr.getNotificationChannel(DAILY_BIRD) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    DAILY_BIRD,
                    "Dagens fågel",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Daily curated bird suggestion." },
            )
        }
        if (mgr.getNotificationChannel(STREAK_RISK) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    STREAK_RISK,
                    "Streak-risk",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Sunday evening nudge when your streak is at risk." },
            )
        }
    }
}
```

- [ ] **Step 3: Verifiera build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/build.gradle.kts composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationChannels.kt
git commit -m "feat(notifications): add WorkManager dep + channel registration"
```

---

### Task 11: NotificationScheduler Android actual

**Files:**
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationSchedulerImpl.kt`

- [ ] **Step 1: Skriv implementation**

```kotlin
package se.birdy.app.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import se.birdy.app.notifications.workers.DailyBirdWorker
import se.birdy.app.notifications.workers.StreakRiskWorker
import se.birdy.domain.notification.NotificationScheduler
import java.util.concurrent.TimeUnit

class NotificationSchedulerImpl(
    private val context: Context,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : NotificationScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override fun scheduleDailyBird() {
        val initialDelay = millisUntilNext(hour = 8, minute = 0)
        val request = PeriodicWorkRequestBuilder<DailyBirdWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_DAILY_BIRD,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun scheduleStreakRiskCheck() {
        val initialDelay = millisUntilNextSunday(hour = 18, minute = 0)
        val request = PeriodicWorkRequestBuilder<StreakRiskWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_STREAK_RISK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancelDailyBird() {
        workManager.cancelUniqueWork(UNIQUE_DAILY_BIRD)
    }

    override fun cancelStreakRiskCheck() {
        workManager.cancelUniqueWork(UNIQUE_STREAK_RISK)
    }

    private fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = clock.now()
        val local = now.toLocalDateTime(zone)
        val targetToday = LocalDateTime(local.year, local.monthNumber, local.dayOfMonth, hour, minute)
            .toInstant(zone)
        val target = if (targetToday > now) targetToday else targetToday.plusDuration(24L * 3600 * 1000)
        return (target - now).inWholeMilliseconds
    }

    private fun millisUntilNextSunday(hour: Int, minute: Int): Long {
        val now = clock.now()
        val local = now.toLocalDateTime(zone)
        val daysToSunday = ((DayOfWeek.SUNDAY.isoDayNumber - local.dayOfWeek.isoDayNumber + 7) % 7).let {
            if (it == 0 && (local.hour > hour || (local.hour == hour && local.minute >= minute))) 7 else it
        }
        val targetDate = local.date.plus(daysToSunday, kotlinx.datetime.DateTimeUnit.DAY)
        val targetInstant = LocalDateTime(targetDate.year, targetDate.monthNumber, targetDate.dayOfMonth, hour, minute)
            .toInstant(zone)
        return (targetInstant - now).inWholeMilliseconds
    }

    private fun kotlinx.datetime.Instant.plusDuration(millis: Long): kotlinx.datetime.Instant =
        this.plus(kotlin.time.Duration.parse("PT${millis}MS"))

    companion object {
        const val UNIQUE_DAILY_BIRD = "birdy_daily_bird_worker"
        const val UNIQUE_STREAK_RISK = "birdy_streak_risk_worker"
    }
}
```

OBS: `Instant.plusDuration` är förenklat — om kotlinx.datetime API:t ändras, justera till `this + millis.milliseconds`.

- [ ] **Step 2: Verifiera build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL — kompileringsfel om Worker-klasser inte finns löses i Task 12-13.

- [ ] **Step 3: Commit (skjut till efter Task 13 om build failar)**

```bash
git add composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationSchedulerImpl.kt
# commit efter Task 13 om DailyBirdWorker/StreakRiskWorker behövs för build
```

---

### Task 12: DailyBirdWorker

**Files:**
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/workers/DailyBirdWorker.kt`

- [ ] **Step 1: Skriv worker**

```kotlin
package se.birdy.app.notifications.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.app.AndroidAppGraphHolder
import se.birdy.app.R
import se.birdy.app.notifications.NotificationChannels
import se.birdy.domain.dailybird.SeasonTag

class DailyBirdWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val graph = AndroidAppGraphHolder.current ?: return Result.success()
            val prefs = graph.userPreferences
            if (!prefs.dailyBirdPushEnabled.first()) return Result.success()

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val selector = graph.selectDailyBird ?: return Result.success()
            val bird = selector(today) ?: return Result.success()

            NotificationChannels.ensureCreated(applicationContext)

            val title = applicationContext.getString(R.string.notification_daily_bird_title_fmt, displayName(bird.speciesId, graph))
            val body = applicationContext.getString(seasonBodyResId(bird.seasonTag))

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("birdy://species/${bird.speciesId}"))
            val pi = PendingIntent.getActivity(
                applicationContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notif = NotificationCompat.Builder(applicationContext, NotificationChannels.DAILY_BIRD)
                .setSmallIcon(R.drawable.ic_stat_birdy)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()

            if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID_DAILY_BIRD, notif)
            }
            Result.success()
        } catch (t: Throwable) {
            Log.w("DailyBirdWorker", "fail", t)
            Result.retry()
        }
    }

    private fun displayName(speciesId: String, graph: se.birdy.app.di.AppGraph): String {
        // Best-effort lookup via species repository — fall back to QID if not resolvable.
        // Real implementation resolves via SpeciesNameMap in render path; for notifications
        // we use the repository's blocking-friendly name accessor.
        return runCatching {
            kotlinx.coroutines.runBlocking {
                graph.repository.getById(se.birdy.content.SpeciesId(speciesId), graph.defaultLocale).first()?.name ?: speciesId
            }
        }.getOrDefault(speciesId)
    }

    private fun seasonBodyResId(tag: SeasonTag): Int = when (tag) {
        SeasonTag.BREEDING -> R.string.notification_daily_bird_body_breeding
        SeasonTag.PRESENT -> R.string.notification_daily_bird_body_present
        SeasonTag.MIGRATING -> R.string.notification_daily_bird_body_migrating
    }

    companion object {
        const val NOTIF_ID_DAILY_BIRD = 1001
    }
}
```

OBS: `AndroidAppGraphHolder` finns inte — det skapas i Task 15 som en singleton-broker så att WorkManager kan komma åt AppGraph. Om buildet failar här, skapa stub-objektet inline:

```kotlin
// Temporär stub i workers/__placeholder.kt om Task 15 inte är klart:
package se.birdy.app
object AndroidAppGraphHolder { val current: se.birdy.app.di.AppGraph? = null }
```

- [ ] **Step 2: Verifiera build (kommer fail tills Task 15)**

Run: `./gradlew :composeApp:assembleDebug`
Expected: FAIL pga `AndroidAppGraphHolder` saknas — lös via temporär stub eller invänta Task 15.

- [ ] **Step 3: Commit (efter Task 15 är klar)**

---

### Task 13: StreakRiskWorker

**Files:**
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/workers/StreakRiskWorker.kt`

- [ ] **Step 1: Skriv worker**

```kotlin
package se.birdy.app.notifications.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import se.birdy.app.AndroidAppGraphHolder
import se.birdy.app.R
import se.birdy.app.notifications.NotificationChannels
import se.birdy.domain.badge.WeekKey
import se.birdy.domain.badge.longestWeeklyStreak
import se.birdy.domain.badge.weekKey

class StreakRiskWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val graph = AndroidAppGraphHolder.current ?: return Result.success()
            val prefs = graph.userPreferences
            if (!prefs.streakRiskPushEnabled.first()) return Result.success()

            val zone = TimeZone.currentSystemDefault()
            val observations = graph.observationRepository.observeAll().first()

            val nowKey = weekKey(Clock.System.now(), zone)
            val hasThisWeek = observations.any { weekKey(it.savedAt, zone) == nowKey }
            if (hasThisWeek) return Result.success()

            val streak = longestWeeklyStreak(observations.map { it.savedAt }, zone)
            if (streak < 2) return Result.success()

            NotificationChannels.ensureCreated(applicationContext)
            val title = applicationContext.getString(R.string.notification_streak_risk_title)
            val body = applicationContext.getString(R.string.notification_streak_risk_body)

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("birdy://identify"))
            val pi = PendingIntent.getActivity(
                applicationContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notif = NotificationCompat.Builder(applicationContext, NotificationChannels.STREAK_RISK)
                .setSmallIcon(R.drawable.ic_stat_birdy)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()

            if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID_STREAK_RISK, notif)
            }
            Result.success()
        } catch (t: Throwable) {
            Log.w("StreakRiskWorker", "fail", t)
            Result.retry()
        }
    }

    companion object {
        const val NOTIF_ID_STREAK_RISK = 1002
    }
}
```

- [ ] **Step 2: Verifiera build (efter Task 15)**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL när Task 15 är klar.

---

## Phase 3 — AppGraph DI + hooks

### Task 14: AppGraph fields

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`

- [ ] **Step 1: Lägg till 3 nya fält i constructor**

I `AppGraph`-konstruktorn (efter `val journalExport: ...`):

```kotlin
    /**
     * Selects the daily bird for a given local date. Null = no candidates available.
     * Wired in MainActivity.buildAppGraph() via DailyBirdSelector.
     */
    val selectDailyBird: (suspend (kotlinx.datetime.LocalDate) -> se.birdy.domain.dailybird.DailyBird?)? = null,

    /**
     * Schedules push notifications via WorkManager + Android 13+ permission.
     * Null in non-Android targets.
     */
    val notificationScheduler: se.birdy.domain.notification.NotificationScheduler? = null,

    /**
     * Tracks which "Daily Bird" suggestions the user has matched (for daily_bird_hunter badge).
     */
    val dailyBirdHistory: se.birdy.data.dailybird.DailyBirdHistoryRepository? = null,
```

- [ ] **Step 2: Verifiera build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt
git commit -m "feat(di): add Phase A lambdas + repos to AppGraph"
```

---

### Task 15: AndroidAppGraphHolder + MainActivity wiring

**Files:**
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/AndroidAppGraphHolder.kt`
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`

- [ ] **Step 1: Skapa AppGraph-holder för WorkManager**

```kotlin
package se.birdy.app

import se.birdy.app.di.AppGraph
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-global handle so WorkManager workers can access AppGraph.
 * Set from MainActivity.onCreate; cleared on Application.onTerminate.
 */
object AndroidAppGraphHolder {
    private val ref = AtomicReference<AppGraph?>(null)
    val current: AppGraph? get() = ref.get()
    fun set(graph: AppGraph) = ref.set(graph)
    fun clear() = ref.set(null)
}
```

- [ ] **Step 2: Modifiera MainActivity.buildAppGraph**

I `MainActivity.kt`, hitta `buildAppGraph()` och i konstruktor-kallet av `AppGraph(...)`, lägg till nya argument:

```kotlin
// In buildAppGraph(), after constructing observationRepo, userPreferences, etc:
val sqlDriver = /* existing driver */
val database = BirdyDatabase(sqlDriver)
val dailyBirdHistory = DailyBirdHistoryRepositoryImpl(database)
val dailyBirdSelector = DailyBirdSelector(
    speciesProvider = { repository.allByQid(Locale.SV) },
)
val notificationScheduler = NotificationSchedulerImpl(applicationContext)

return AppGraph(
    // existing fields ...
    selectDailyBird = { date -> dailyBirdSelector.selectFor(date) },
    notificationScheduler = notificationScheduler,
    dailyBirdHistory = dailyBirdHistory,
)
```

- [ ] **Step 3: Sätt AndroidAppGraphHolder.current i onCreate**

I `MainActivity.onCreate()`, efter `appGraph = buildAppGraph()`:

```kotlin
AndroidAppGraphHolder.set(appGraph)
```

- [ ] **Step 4: Verifiera build + smoke-test**

Run: `./gradlew :androidApp:installDebug`
Expected: BUILD SUCCESSFUL + app installs.

Run: `"$ADB" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity`
Expected: App startar utan crash. Verifiera via `adb logcat | grep Birdy`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/se/birdy/app/AndroidAppGraphHolder.kt \
        androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt
git commit -m "feat(di): wire Phase A dependencies in MainActivity + add WorkManager AppGraph holder"
```

---

### Task 16: Hook daily-bird-match recording i SaveObservationUseCase

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt`

- [ ] **Step 1: Lägg till lambda-param för match-recording**

I `SaveObservationUseCase`-konstruktorn, lägg till:

```kotlin
class SaveObservationUseCase(
    private val repo: ObservationRepository,
    private val badgeRepo: BadgeRepository,
    private val photoStorage: PhotoStorage,
    private val clock: Clock,
    private val catalog: BadgeCatalog,
    private val recalculate: RecalculateBadgesUseCase,
    private val speciesByQid: suspend () -> Map<SpeciesId, Species>,
    private val onObservationSaved: (suspend (Observation) -> Unit)? = null,
    private val dailyBirdMatchCount: suspend () -> Int = { 0 },
)
```

- [ ] **Step 2: Anropa onObservationSaved efter insert**

Efter `repo.insert(...)` och `try`-blocket (innan `newUnlocks`-runCatching):

```kotlin
onObservationSaved?.invoke(
    Observation(
        id = id, speciesId = speciesId, capturedAt = capturedAt, savedAt = clock.now(),
        photoPath = photoPath, note = note, confidence = confidence,
        latitude = null, longitude = null, locationLabel = null,
        stampNumber = nextStamp, audioPath = audioPath, sourceType = sourceType,
    )
)
```

- [ ] **Step 3: Skicka dailyBirdMatchCount till recalculate**

Modifiera `recalculate.newUnlocks(...)`-kallet:

```kotlin
val matchCount = runCatching { dailyBirdMatchCount() }.getOrDefault(0)
val computed = recalculate.newUnlocks(allObs, species, catalog, existing, dailyBirdMatchCount = matchCount)
```

- [ ] **Step 4: Wire i MainActivity.buildAppGraph**

I `MainActivity.kt`, där `SaveObservationUseCase` instansieras (sannolikt inom AppGraph eller en sub-graph), passera:

```kotlin
SaveObservationUseCase(
    // existing args ...
    onObservationSaved = { obs ->
        val today = kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val speciesId = obs.speciesId ?: return@SaveObservationUseCase
        val todayBird = dailyBirdHistory.speciesIdForDate(today)
        if (todayBird == speciesId) {
            dailyBirdHistory.markMatch(today, speciesId)
        }
    },
    dailyBirdMatchCount = { dailyBirdHistory.totalMatchCount() },
)
```

- [ ] **Step 5: Verifiera build + tester**

Run: `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL + tests PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt \
        androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt
git commit -m "feat(observations): hook daily-bird-match recording into SaveObservationUseCase"
```

---

## Phase 4 — Badges + strings

### Task 17: Lägg till 3 nya badges i badges.yaml

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/files/badges.yaml`

- [ ] **Step 1: Identifiera korrekt sektion**

Read: `composeApp/src/commonMain/composeResources/files/badges.yaml`

Hitta var existerande badges som `dawn_chorus` (om de finns i badges.yaml) eller `early_pilgrim` skulle passa kategori-mässigt.

- [ ] **Step 2: Lägg till 3 nya entries**

Lägg till i slutet av badges.yaml (eller relevant sektion):

```yaml
# ===== v1.1 Phase A retention badges =====
- id: early_pilgrim
  category: rare
  is_premium: true
  rule:
    type: observed_in_hour_range
    start_hour: 5
    end_hour_exclusive: 7
    target: 1

- id: sunday_birder
  category: streak_weekly
  is_premium: true
  rule:
    type: sunday_streak
    target: 4

- id: daily_bird_hunter
  category: progression
  is_premium: true
  rule:
    type: daily_bird_matches
    target: 3
```

Justera `category`-värdet att matcha existerande kategorier (kontrollera via grep `category:` i samma fil + `BadgeCategory.kt`). Sätt `is_premium` enligt befintliga premium-badges-mönster.

- [ ] **Step 3: Verifiera build-time-validator**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL — `BadgeCatalogLoader` parsar alla 3.

Om build failar på `BadgeStringMap`-validator → fortsätt till Task 18.

- [ ] **Step 4: Commit (efter Task 18)**

---

### Task 18: BadgeStringMap mappningar

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeStringMap.kt`

- [ ] **Step 1: Lägg till `nameFor`-grenar**

I `BadgeStringMap.nameFor`, lägg till före `else -> error(...)`:

```kotlin
"early_pilgrim" -> Res.string.badge_name_early_pilgrim
"sunday_birder" -> Res.string.badge_name_sunday_birder
"daily_bird_hunter" -> Res.string.badge_name_daily_bird_hunter
```

- [ ] **Step 2: Lägg till `descriptionFor`-grenar**

```kotlin
"early_pilgrim" -> Res.string.badge_desc_early_pilgrim
"sunday_birder" -> Res.string.badge_desc_sunday_birder
"daily_bird_hunter" -> Res.string.badge_desc_daily_bird_hunter
```

- [ ] **Step 3: Verifiera build (kommer fail tills strings finns)**

Run: `./gradlew :composeApp:assembleDebug`
Expected: FAIL — string-resources saknas. Löses i Task 19.

---

### Task 19: Strängar sv + en

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Lägg till svenska strängar**

Lägg till i slutet av `values/strings.xml` (innan `</resources>`):

```xml
<!-- v1.1 Phase A: Daily Bird + Streak Risk + 3 badges -->

<!-- Daily Bird card -->
<string name="daily_bird_card_eyebrow">DAGENS FÅGEL</string>
<string name="daily_bird_eyebrow_breeding">Häckar nu i Sverige</string>
<string name="daily_bird_eyebrow_present">Här just nu</string>
<string name="daily_bird_eyebrow_migrating">På sträck</string>
<string name="daily_bird_card_a11y">Dagens fågel: %1$s. %2$s. Tryck för att läsa mer.</string>

<!-- Permission prompt sheet -->
<string name="permission_prompt_eyebrow">AVISERINGAR</string>
<string name="permission_prompt_headline">Vill du få en *påminnelse* när vi tycker fågeln är värd att möta?</string>
<string name="permission_prompt_sub">Max en kort knuff om dagen. Stäng av när du vill.</string>
<string name="permission_prompt_cta_yes">Slå på</string>
<string name="permission_prompt_cta_no">Inte nu</string>

<!-- Settings — Aviseringar -->
<string name="settings_section_notifications">AVISERINGAR</string>
<string name="settings_toggle_daily_bird">Dagens fågel — daglig påminnelse</string>
<string name="settings_toggle_streak_risk">Streak-risk — söndag kväll</string>
<string name="settings_notifications_disabled_helpline">Aviseringar avstängda i Androids systeminställningar →</string>

<!-- Notifications -->
<string name="notification_daily_bird_title_fmt">Dagens fågel: %1$s</string>
<string name="notification_daily_bird_body_breeding">Häckar nu i Sverige.</string>
<string name="notification_daily_bird_body_present">Här just nu.</string>
<string name="notification_daily_bird_body_migrating">På sträck just nu.</string>
<string name="notification_streak_risk_title">Bara kvällen kvar</string>
<string name="notification_streak_risk_body">En talgoxe i parken räcker — fortsätt din streak.</string>

<!-- Badges -->
<string name="badge_name_early_pilgrim">Tidig pilgrim</string>
<string name="badge_desc_early_pilgrim">Spara en observation mellan 05:00 och 07:00.</string>
<string name="badge_name_sunday_birder">Söndagsskådare</string>
<string name="badge_desc_sunday_birder">Skåda 4 söndagar i rad.</string>
<string name="badge_name_daily_bird_hunter">Dagens fågel-jägare</string>
<string name="badge_desc_daily_bird_hunter">Spara dagens kuraterade fågel på 3 olika dagar.</string>
```

- [ ] **Step 2: Lägg till engelska strängar**

I `values-en/strings.xml`:

```xml
<!-- Daily Bird card -->
<string name="daily_bird_card_eyebrow">BIRD OF THE DAY</string>
<string name="daily_bird_eyebrow_breeding">Breeding in Sweden now</string>
<string name="daily_bird_eyebrow_present">Here right now</string>
<string name="daily_bird_eyebrow_migrating">On migration</string>
<string name="daily_bird_card_a11y">Bird of the day: %1$s. %2$s. Tap to read more.</string>

<!-- Permission prompt sheet -->
<string name="permission_prompt_eyebrow">NOTIFICATIONS</string>
<string name="permission_prompt_headline">Want a *reminder* when there\'s a bird worth meeting?</string>
<string name="permission_prompt_sub">Max one nudge a day. Turn off anytime.</string>
<string name="permission_prompt_cta_yes">Turn on</string>
<string name="permission_prompt_cta_no">Not now</string>

<!-- Settings -->
<string name="settings_section_notifications">NOTIFICATIONS</string>
<string name="settings_toggle_daily_bird">Bird of the day — daily reminder</string>
<string name="settings_toggle_streak_risk">Streak risk — Sunday evening</string>
<string name="settings_notifications_disabled_helpline">Notifications disabled in Android system settings →</string>

<!-- Notifications -->
<string name="notification_daily_bird_title_fmt">Bird of the day: %1$s</string>
<string name="notification_daily_bird_body_breeding">Breeding in Sweden right now.</string>
<string name="notification_daily_bird_body_present">Here right now.</string>
<string name="notification_daily_bird_body_migrating">Migrating right now.</string>
<string name="notification_streak_risk_title">Tonight\'s your last chance</string>
<string name="notification_streak_risk_body">A great tit in the park is enough — keep your streak.</string>

<!-- Badges -->
<string name="badge_name_early_pilgrim">Early Pilgrim</string>
<string name="badge_desc_early_pilgrim">Save an observation between 05:00 and 07:00.</string>
<string name="badge_name_sunday_birder">Sunday Birder</string>
<string name="badge_desc_sunday_birder">Observe on 4 consecutive Sundays.</string>
<string name="badge_name_daily_bird_hunter">Daily Bird Hunter</string>
<string name="badge_desc_daily_bird_hunter">Save today\'s curated bird on 3 different days.</string>
```

- [ ] **Step 3: Verifiera build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit (badges + strings + badge-string-map ihop)**

```bash
git add composeApp/src/commonMain/composeResources/files/badges.yaml \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeStringMap.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(badges): add 3 new Phase A premium badges + sv+en strings"
```

---

## Phase 5 — UI komponenter

### Task 20: DailyBirdCard Compose-component

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/DailyBirdCard.kt`

- [ ] **Step 1: Skriv component**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.domain.dailybird.DailyBird
import se.birdy.domain.dailybird.SeasonTag
import birdy.composeapp.generated.resources.Res
import birdy.composeapp.generated.resources.daily_bird_card_a11y
import birdy.composeapp.generated.resources.daily_bird_card_eyebrow
import birdy.composeapp.generated.resources.daily_bird_eyebrow_breeding
import birdy.composeapp.generated.resources.daily_bird_eyebrow_migrating
import birdy.composeapp.generated.resources.daily_bird_eyebrow_present

@Composable
fun DailyBirdCard(
    bird: DailyBird,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eyebrowText = stringResource(
        when (bird.seasonTag) {
            SeasonTag.BREEDING -> Res.string.daily_bird_eyebrow_breeding
            SeasonTag.PRESENT -> Res.string.daily_bird_eyebrow_present
            SeasonTag.MIGRATING -> Res.string.daily_bird_eyebrow_migrating
        }
    )
    val sectionLabel = stringResource(Res.string.daily_bird_card_eyebrow)
    val a11y = stringResource(Res.string.daily_bird_card_a11y, name, eyebrowText)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(HeroMossLight, HeroMossMid)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Left: small stamp-like circle marker
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AccentCopper.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "№1",
                color = AccentCopper,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = rememberCaveat(),
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                sectionLabel,
                color = AccentCopper,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Text(
                name,
                color = Color.White,
                fontSize = 22.sp,
                fontFamily = rememberDmSerifDisplay(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                eyebrowText,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontFamily = rememberCaveat(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
```

- [ ] **Step 2: Verifiera build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL. Om `rememberDmSerifDisplay`/`rememberCaveat` är annorlunda namngivna i Type.kt, justera importerna.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/DailyBirdCard.kt
git commit -m "feat(ui): add DailyBirdCard component (HeroMoss gradient + Field Journal typography)"
```

---

### Task 21: PermissionPromptSheet Compose-component

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PermissionPromptSheet.kt`

- [ ] **Step 1: Skriv component**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.rememberCaveat
import birdy.composeapp.generated.resources.Res
import birdy.composeapp.generated.resources.permission_prompt_cta_no
import birdy.composeapp.generated.resources.permission_prompt_cta_yes
import birdy.composeapp.generated.resources.permission_prompt_eyebrow
import birdy.composeapp.generated.resources.permission_prompt_headline
import birdy.composeapp.generated.resources.permission_prompt_sub

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionPromptSheet(
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = PaperTop,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                stringResource(Res.string.permission_prompt_eyebrow),
                color = AccentCopper,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(8.dp))
            // Headline with parsed *accent* — re-use existing italic-mix renderer if available.
            JournalHeadline(
                text = stringResource(Res.string.permission_prompt_headline),
                color = MarginaliaInk,
                fontSize = 22.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.permission_prompt_sub),
                color = MarginaliaInk.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontFamily = rememberCaveat(),
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.permission_prompt_cta_no), color = MarginaliaInk)
                }
                Button(
                    onClick = onTurnOn,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCopper, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(stringResource(Res.string.permission_prompt_cta_yes))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
```

OBS: `JournalHeadline` finns sannolikt redan i `components/`. Om signaturen är annorlunda (t.ex. kräver `Modifier` istället för `color`+`fontSize`), justera anropet.

- [ ] **Step 2: Verifiera build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PermissionPromptSheet.kt
git commit -m "feat(ui): add PermissionPromptSheet (paper bg, JournalHeadline, AccentCopper CTA)"
```

---

### Task 22: Insert DailyBirdCard i ListenLauncherScreen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherViewModel.kt`

- [ ] **Step 1: Ladda DailyBird i ViewModel**

I `ListenLauncherViewModel.kt`, lägg till state + load:

```kotlin
private val _dailyBird = MutableStateFlow<DailyBirdUi?>(null)
val dailyBird: StateFlow<DailyBirdUi?> = _dailyBird.asStateFlow()

data class DailyBirdUi(val speciesId: String, val name: String, val seasonTag: SeasonTag)

init {
    viewModelScope.launch {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val bird = appGraph.selectDailyBird?.invoke(today) ?: return@launch
        val name = appGraph.repository.getById(SpeciesId(bird.speciesId), appGraph.defaultLocale).first()?.name
            ?: return@launch
        _dailyBird.value = DailyBirdUi(bird.speciesId, name, bird.seasonTag)

        // Record today's bird for match tracking (idempotent)
        appGraph.dailyBirdHistory?.recordToday(today, bird.speciesId)
    }
}
```

- [ ] **Step 2: Insert i ListenLauncherScreen**

I `ListenLauncherScreen.kt`, mellan `JournalIntro(...)` och launch-cards-`Column`:

```kotlin
val dailyBirdState = viewModel.dailyBird.collectAsState().value
dailyBirdState?.let { ui ->
    DailyBirdCard(
        bird = DailyBird(speciesId = ui.speciesId, seasonTag = ui.seasonTag),
        name = ui.name,
        onClick = { onSpeciesProfileClick(ui.speciesId) },
    )
}
```

Lägg till `onSpeciesProfileClick: (String) -> Unit` i screen-funktionens parametrar och wire upp call-site i nav-grafen.

- [ ] **Step 3: Verifiera build + smoke**

Run: `./gradlew :androidApp:installDebug`
Run: `adb shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity`
Expected: App startar, Identifiera-tab visar kort om species-data finns.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/
git commit -m "feat(listen): show DailyBirdCard between JournalIntro and launch-cards"
```

---

### Task 23: Settings "Aviseringar"-sektion

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Expose toggles i ViewModel**

```kotlin
val dailyBirdPushEnabled = appGraph.userPreferences.dailyBirdPushEnabled
    .stateIn(viewModelScope, SharingStarted.Eagerly, true)
val streakRiskPushEnabled = appGraph.userPreferences.streakRiskPushEnabled
    .stateIn(viewModelScope, SharingStarted.Eagerly, true)

fun setDailyBirdPushEnabled(value: Boolean) {
    viewModelScope.launch {
        appGraph.userPreferences.setDailyBirdPushEnabled(value)
        if (value) appGraph.notificationScheduler?.scheduleDailyBird()
        else appGraph.notificationScheduler?.cancelDailyBird()
    }
}

fun setStreakRiskPushEnabled(value: Boolean) {
    viewModelScope.launch {
        appGraph.userPreferences.setStreakRiskPushEnabled(value)
        if (value) appGraph.notificationScheduler?.scheduleStreakRiskCheck()
        else appGraph.notificationScheduler?.cancelStreakRiskCheck()
    }
}
```

- [ ] **Step 2: Lägg till "Aviseringar"-sektion**

I `SettingsScreen.kt`, lägg in efter Premium-hero (eller där Albin föredrar):

```kotlin
item { SectionHeader(text = stringResource(Res.string.settings_section_notifications)) }
item {
    PaperCard {
        ToggleRow(
            label = stringResource(Res.string.settings_toggle_daily_bird),
            checked = viewModel.dailyBirdPushEnabled.collectAsState().value,
            onCheckedChange = viewModel::setDailyBirdPushEnabled,
        )
        DashedDivider()
        ToggleRow(
            label = stringResource(Res.string.settings_toggle_streak_risk),
            checked = viewModel.streakRiskPushEnabled.collectAsState().value,
            onCheckedChange = viewModel::setStreakRiskPushEnabled,
        )
        val ctx = LocalContext.current
        if (!areNotificationsEnabled(ctx)) {
            DashedDivider()
            SettingsRow(
                label = stringResource(Res.string.settings_notifications_disabled_helpline),
                onClick = { openAppNotificationSettings(ctx) },
            )
        }
    }
}
```

Lägg till hjälp-funktioner i `androidMain`:

```kotlin
// androidApp/src/main/kotlin/se/birdy/android/util/NotificationSettings.kt (eller liknande)
fun areNotificationsEnabled(context: android.content.Context): Boolean =
    androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()

fun openAppNotificationSettings(context: android.content.Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    context.startActivity(intent)
}
```

För `commonMain`-callsite-kompabilitet: definiera `expect fun areNotificationsEnabled(): Boolean` + `expect fun openAppNotificationSettings()` i `composeApp/src/commonMain/kotlin/se/birdy/app/notifications/PlatformNotificationSettings.kt` och actual i `androidMain`.

- [ ] **Step 3: Verifiera build + smoke**

Run: `./gradlew :androidApp:installDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/ \
        composeApp/src/commonMain/kotlin/se/birdy/app/notifications/PlatformNotificationSettings.kt \
        composeApp/src/androidMain/kotlin/se/birdy/app/notifications/PlatformNotificationSettings.android.kt
git commit -m "feat(settings): add Aviseringar section with toggles + helpline"
```

---

## Phase 6 — Android glue

### Task 24: AndroidManifest — permission + deep-link + singleTop

**Files:**
- Modify: `androidApp/src/main/AndroidManifest.xml`

- [ ] **Step 1: Lägg till permission**

I `<manifest>`-roten, efter existerande `<uses-permission>`-rader:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- [ ] **Step 2: Ändra MainActivity-attribut + lägg till intent-filter**

Hitta `<activity android:name=".MainActivity" ...>` och:
- Lägg till attribut: `android:launchMode="singleTop"`
- Lägg till ett nytt `<intent-filter>` efter den befintliga MAIN/LAUNCHER:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="birdy" />
</intent-filter>
```

- [ ] **Step 3: Verifiera build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/main/AndroidManifest.xml
git commit -m "feat(android): add POST_NOTIFICATIONS permission + birdy:// deep-link intent-filter"
```

---

### Task 25: MainActivity.onNewIntent + deep-link handler

**Files:**
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`

- [ ] **Step 1: Lägg till NavController-hållare**

I MainActivity, om `navController` inte är klassens member, lyft den till en `var navControllerRef: NavController? = null` som settas i Compose-roten:

```kotlin
private var navControllerRef: NavController? = null

// In setContent { ... }
val nav = rememberNavController()
LaunchedEffect(nav) { navControllerRef = nav }
```

- [ ] **Step 2: Override onNewIntent**

```kotlin
override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    intent ?: return
    setIntent(intent)
    handleDeepLink(intent)
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // existing setup ...
    intent?.let { handleDeepLink(it) }
}

private fun handleDeepLink(intent: Intent) {
    val uri = intent.data ?: return
    if (uri.scheme != "birdy") return
    when (uri.host) {
        "species" -> {
            val speciesId = uri.lastPathSegment ?: return
            navControllerRef?.navigate(AppRoute.SpeciesProfile(speciesId))
        }
        "identify" -> {
            navControllerRef?.popBackStack(AppRoute.Listen.route, inclusive = false)
        }
    }
}
```

(`AppRoute.Listen.route` / `AppRoute.SpeciesProfile(...)` namn — verifiera i existerande nav-graf.)

- [ ] **Step 3: Smoke-test deep-link**

Run: `"$ADB" shell am start -d 'birdy://species/Q25485'`
Expected: App öppnas direkt på SpeciesProfileScreen för Talgoxe.

Run: `"$ADB" shell am start -d 'birdy://identify'`
Expected: App öppnas på Identifiera-tab.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt
git commit -m "feat(android): handle birdy:// deep-links via onNewIntent + onCreate"
```

---

### Task 26: MainActivity.onCreate — re-schedule + permission-sheet trigger

**Files:**
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`

- [ ] **Step 1: Re-schedule workers i onCreate**

Efter `appGraph = buildAppGraph()` och `AndroidAppGraphHolder.set(appGraph)`:

```kotlin
lifecycleScope.launch {
    val prefs = appGraph.userPreferences
    val notificationsOn = NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
    if (notificationsOn) {
        if (prefs.dailyBirdPushEnabled.first()) appGraph.notificationScheduler?.scheduleDailyBird()
        if (prefs.streakRiskPushEnabled.first()) appGraph.notificationScheduler?.scheduleStreakRiskCheck()
    }
}
```

- [ ] **Step 2: Trigga PermissionPromptSheet efter första obs**

I `MainActivity` (eller AppRoot composable), läs av observationCount + pushPermissionAsked:

```kotlin
val showPermissionSheet = remember { mutableStateOf(false) }

LaunchedEffect(Unit) {
    appGraph.observationRepository.observeAll().collect { obs ->
        if (obs.isNotEmpty() && Build.VERSION.SDK_INT >= 33) {
            val asked = appGraph.userPreferences.pushPermissionAsked.first()
            if (!asked) showPermissionSheet.value = true
        }
    }
}

if (showPermissionSheet.value) {
    PermissionPromptSheet(
        onTurnOn = {
            requestPostNotificationsPermission()
            // pushPermissionAsked sätts efter permission-callback
            showPermissionSheet.value = false
        },
        onDismiss = {
            lifecycleScope.launch {
                appGraph.userPreferences.setPushPermissionAsked(true)
                showPermissionSheet.value = false
            }
        },
    )
}
```

`requestPostNotificationsPermission()`:

```kotlin
private val requestPermLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    lifecycleScope.launch {
        appGraph.userPreferences.setPushPermissionAsked(true)
        if (granted) {
            appGraph.notificationScheduler?.scheduleDailyBird()
            appGraph.notificationScheduler?.scheduleStreakRiskCheck()
        }
    }
}

private fun requestPostNotificationsPermission() {
    if (Build.VERSION.SDK_INT >= 33) {
        requestPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
```

- [ ] **Step 3: Verifiera build + smoke**

Run: `./gradlew :androidApp:installDebug`
Run: app on device — spara en första observation → sheet ska visas.

- [ ] **Step 4: Commit**

```bash
git add androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt
git commit -m "feat(android): re-schedule workers on launch + show PermissionPromptSheet after first obs"
```

---

### Task 27: Debug-only "DEV: Trigger Daily Bird push" Settings-rad

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Lägg till BuildConfig.DEBUG-gate**

I SettingsScreen, efter "Aviseringar"-sektionen:

```kotlin
if (BuildConfig.DEBUG) {
    item {
        PaperCard {
            SettingsRow(
                label = "DEV: Trigga Dagens fågel-push",
                onClick = { viewModel.devTriggerDailyBird() },
            )
            DashedDivider()
            SettingsRow(
                label = "DEV: Trigga Streak-risk-push",
                onClick = { viewModel.devTriggerStreakRisk() },
            )
        }
    }
}
```

- [ ] **Step 2: Lägg till handlers i ViewModel**

```kotlin
fun devTriggerDailyBird() {
    val ctx = appGraph.applicationContext as? Context ?: return  // requires exposing context
    WorkManager.getInstance(ctx).enqueue(
        OneTimeWorkRequestBuilder<DailyBirdWorker>().build()
    )
}
// motsvarande för Streak-risk
```

(Om `AppGraph` inte har `applicationContext`, lös via separat Android-actual injektion.)

- [ ] **Step 3: Verifiera**

Run: `./gradlew :androidApp:installDebug` → öppna Settings → klicka "DEV: Trigga Dagens fågel-push" → notification ska dyka upp inom 5 sekunder.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/
git commit -m "chore(settings): add debug-only force-run buttons for both workers"
```

---

## Phase 7 — Verifiering & release-prep

### Task 28: Run alla tester + lint

- [ ] **Step 1: Unit-tests**

Run:
```bash
./gradlew :shared:domain:jvmTest \
          :shared:data:jvmTest \
          :shared:datastore:jvmTest \
          :composeApp:testDebugUnitTest
```
Expected: PASS för alla suites.

- [ ] **Step 2: ktlint + detekt**

Run:
```bash
./gradlew ktlintCheck detekt
```
Expected: PASS. Om fel: `./gradlew ktlintFormat` + inspektera detekt-rapporter.

- [ ] **Step 3: Commit eventuella formatering-fixar**

```bash
git add -u
git commit -m "style: ktlintFormat phase-a-files"
```

---

### Task 29: Build debug-APK + smoke-test

- [ ] **Step 1: Build + install**

Run:
```bash
./gradlew :androidApp:installDebug
"$ADB" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```
Expected: App startar utan crash. Identifiera-tab visar DailyBirdCard.

- [ ] **Step 2: Smoke-checklist**

Verifiera manuellt:
- [ ] DailyBirdCard renderas högst upp på Identifiera-tab
- [ ] Tap på kortet → SpeciesProfileScreen öppnas
- [ ] Settings → "Aviseringar"-sektion finns med 2 toggles
- [ ] Spara en obs → PermissionPromptSheet visas (om första gången)
- [ ] DEV-knapp triggar notification
- [ ] Tap på notification → SpeciesProfile öppnas

---

### Task 30: Device-verify-screenshots på SM-S918B

- [ ] **Step 1: Förbered screenshot-mapp**

```bash
mkdir -p docs/superpowers/screenshots/v1.1-phase-a
```

- [ ] **Step 2: Ta 10-12 canonical screenshots**

ADB-driven där möjligt:

```bash
ADB="/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe"
SS_DIR="docs/superpowers/screenshots/v1.1-phase-a"

# 01: Identifiera-tab med DailyBirdCard
"$ADB" shell screencap -p /sdcard/01-identify-with-dailybird.png
"$ADB" pull /sdcard/01-identify-with-dailybird.png "$SS_DIR/"

# 02-12: följ device-verify-listan i spec
```

Screenshots enligt spec-listan (1-12).

- [ ] **Step 3: Committa screenshots**

```bash
git add docs/superpowers/screenshots/v1.1-phase-a/
git commit -m "docs(screenshots): v1.1 Phase A device-verify (12 captures on SM-S918B)"
```

---

### Task 31: Version bump + rc-tag

**Files:**
- Modify: `androidApp/build.gradle.kts`

- [ ] **Step 1: Bumpa versioner**

I `androidApp/build.gradle.kts`:
```kotlin
versionCode = 116        // 115 → 116
versionName = "1.1.0-rc1"
```

- [ ] **Step 2: Bygg signed AAB**

```bash
./gradlew :androidApp:bundleRelease
```
Expected: BUILD SUCCESSFUL + AAB i `androidApp/build/outputs/bundle/release/`.

- [ ] **Step 3: Commit + tag**

```bash
git add androidApp/build.gradle.kts
git commit -m "release: v1.1.0-rc1 (versionCode 116) — Phase A retention hooks"
git tag v1.1.0-rc1
git push origin main --tags
```

- [ ] **Step 4: Closed Testing-upload**

Manuell — följ `docs/superpowers/runbooks/2026-05-22-v1.0.0-internal-testing.md` (samma mönster).

---

## Self-Review (utförd 2026-05-25)

### 1. Spec coverage

Kontrollerar varje sektion i specen mot tasks:

| Spec-sektion | Coverage |
|---|---|
| §Bakgrund / Mål | (kontextuell — inga tasks behövs) ✓ |
| §Låsta designbeslut #1 (Yta: Identifiera-tab) | Task 22 ✓ |
| §Låsta designbeslut #2 (Abundance-viktad slump) | Task 5 ✓ |
| §Låsta designbeslut #3 (Pan-Nordic) | Task 5 (NORDIC_BUCKET) ✓ |
| §Låsta designbeslut #4 (Cadence ISO-datum) | Task 5 (deterministic seed) + Task 22 (LaunchedEffect today) ✓ |
| §Låsta designbeslut #5 (Tap → SpeciesProfile) | Task 22 (onSpeciesProfileClick) ✓ |
| §Låsta designbeslut #6 (Permission-sheet) | Task 21 + Task 26 ✓ |
| §Låsta designbeslut #7 (Streak-risk) | Task 13 + Task 11 (StreakRiskWorker scheduling) ✓ |
| §Låsta designbeslut #8 (Dagens fågel push) | Task 12 + Task 11 ✓ |
| §Låsta designbeslut #9 (3 premium-badges) | Task 17 + Task 18 ✓ |
| §Arkitektur (moduler) | Tasks 1-3, 9-13 ✓ |
| §Komponenter #1-11 | Tasks 5, 4, 1-2, 20, 21, 9, 11, 12, 13, 6+17, 3, 23, 25 ✓ |
| §Data flow A-G | Tasks 22, 26, 12, 13, 25, 16+15, 26 ✓ |
| §Error handling 1-10 | Inkorporerat i Tasks 5, 12, 13, 23, 25, 3, 2 ✓ |
| §Testing — unit | Tasks 2, 5, 7 ✓ |
| §Testing — device-verify | Task 30 ✓ |
| §Klart-kriterier (11 punkter) | Tasks 22, 22, 26, 11+12, 11+13, 17, 23, 25, 19, 28, 30 ✓ |

**Inga gaps.**

### 2. Placeholder-scan

Sökte efter "TBD", "TODO", "implement later", "fill in details", "etc.", "similar to" — inga träffar bortom kommentar-prosa som förklarar varför.

OBS: Två markeringar med `OBS:`-prefix i Task 11 (`Instant.plusDuration` kan behöva justeras beroende på kotlinx.datetime-version) och Task 12 (`AndroidAppGraphHolder` skapas i Task 15) är **medvetna pekare till uppströms-tasks**, inte placeholders. Båda har inline alternativ-lösning.

### 3. Type-konsistens

- `DailyBird.speciesId: String` — används konsekvent i Tasks 4, 5, 20, 22, 25 ✓
- `SeasonTag`-enum — använd i Tasks 4, 5, 20 ✓
- `BadgeRule.DailyBirdMatches` / `SundayStreak` / `ObservedInHourRange` — definierade i Task 6, använda i Tasks 7, 8, 17 ✓
- `NotificationScheduler.scheduleDailyBird()` / `scheduleStreakRiskCheck()` / cancels — konsistent i Tasks 9, 11, 23, 26 ✓
- `DailyBirdHistoryRepository.recordToday` / `markMatch` / `speciesIdForDate` / `totalMatchCount` — Tasks 2, 16, 22 ✓
- `UserPreferences.pushPermissionAsked` / `dailyBirdPushEnabled` / `streakRiskPushEnabled` — Tasks 3, 23, 26 ✓

**Inga signature-mismatches.**

### 4. Antal tasks: 31

Genomsnitt ~3-7 steg per task; ungefär 20-30 min per task → ~16-18 timmar totalt → 4-5 dev-dagar enligt spec-budgeten. ✓

---

## Relaterade dokument

- Spec: `docs/superpowers/specs/2026-05-25-v1-1-phase-a-retention-hooks.md`
- Plan 5b (Gamification — BadgeCatalog + UnlockQueue + StreakHelpers): `docs/superpowers/plans/2026-05-06-v1-05b-gamification.md`
- Plan 6b1 (Billing — lambda-DI + expect/actual mönster): `docs/superpowers/plans/2026-05-16-v1-06b1-billing-launch-prep.md`
- Plan 6b3 (Premium content — 10 nya badges-referens): `docs/superpowers/plans/2026-05-21-v1-06b3-premium-content.md`
- Onboarding v2 (v1.0.2 — närmast föregående release): `docs/superpowers/plans/2026-05-25-onboarding-v2-scroll-story.md`
- v1.1 workflow memory: `~/.claude/projects/.../memory/project_v1_1_workflow.md`
- v1.2 Phase B deferral memory: `~/.claude/projects/.../memory/project_v1_2_phase_b_hooks.md`
