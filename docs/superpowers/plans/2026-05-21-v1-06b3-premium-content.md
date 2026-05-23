# Plan 6b3 — Premium Content (PDF-export + Season-statistics + 10 fält-märken) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Leverera de tre Premium-värdena (PDF Field Journal-export, Season statistics, 10 fält-märken) som låser upp när användaren har aktiv Premium, så att v1.0 kan tagga `v0.9.0c-premium-content` och därefter `v1.0.0`.

**Architecture:** Tre funktioner i en sprint, med en gemensam `effectivePremiumActive: StateFlow<Boolean>` som single source of truth (Plan 7e). PDF-renderingen får en ny KMP-modul `:shared:pdf` (Android-actual använder `android.graphics.pdf.PdfDocument` + `Typeface.createFromAsset`). Stats är en Compose-skärm med Canvas-baserade chart-komponenter (0 deps). De 10 fält-märkena förlängs på det befintliga `BadgeRule`/`BadgeEvaluator`-systemet — utom #10 (`premium_field_member`) som unlock:as manuellt via en bootstrap-scoped `PremiumActivationListener`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, `android.graphics.pdf.PdfDocument` (Android-only, in-platform), `Typeface.createFromAsset` (DM Serif Display Italic + Caveat-Regular embedded via SIL OFL), `FileProvider` + `ACTION_SEND` för share-sheet, SQLDelight, kaml-baserad YAML-parsing, kotlinx.datetime.

**Spec:** `docs/superpowers/specs/2026-05-21-v1-06b3-premium-content-design.md`

---

## File Structure

Före tasks: nedan är de filer som skapas eller ändras, grupperade per ansvarsområde.

**Nya KMP-modulen `:shared:pdf`:**
- `shared/pdf/build.gradle.kts` (ny)
- `shared/pdf/src/commonMain/kotlin/se/birdy/pdf/JournalPdfRenderer.kt` (expect class)
- `shared/pdf/src/commonMain/kotlin/se/birdy/pdf/JournalPdfInput.kt` (data class)
- `shared/pdf/src/androidMain/kotlin/se/birdy/pdf/JournalPdfRenderer.android.kt` (actual)
- `shared/pdf/src/androidMain/kotlin/se/birdy/pdf/PdfFontProvider.android.kt` (`Typeface.createFromAsset`)
- `shared/pdf/src/iosMain/kotlin/se/birdy/pdf/JournalPdfRenderer.ios.kt` (actual — stubbed `NotImplementedError`)
- `shared/pdf/src/commonTest/kotlin/se/birdy/pdf/JournalPdfRendererTest.kt`
- `shared/pdf/src/androidUnitTest/kotlin/se/birdy/pdf/JournalPdfRendererAndroidTest.kt` (Robolectric)
- `settings.gradle.kts` (lägg till `:shared:pdf`)

**Premium-aktivering + manuella märken:**
- `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeRepository.kt` (lägg till `unlockManualBadge`)
- `shared/data/src/commonMain/kotlin/se/birdy/data/badge/BadgeRepositoryImpl.kt` (impl + Mutex)
- `shared/data/src/commonMain/sqldelight/se/birdy/data/db/BadgeUnlock.sq` (lägg till `insertIfMissing`)
- `composeApp/src/commonMain/kotlin/se/birdy/app/bootstrap/PremiumActivationListener.kt` (ny klass)
- `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt` (wira listener + journalExport-lambda)
- `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` (init listener + journal export)

**PDF Field Journal-export (UC + integration):**
- `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/ExportJournalUseCase.kt` (data-assembly + render)
- `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/JournalExportResult.kt` (sealed)
- `composeApp/src/commonTest/kotlin/se/birdy/app/usecase/ExportJournalUseCaseTest.kt`
- `androidApp/src/main/kotlin/se/birdy/android/share/JournalPdfShareLauncher.kt` (FileProvider + ACTION_SEND)
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PremiumTeaserCard.kt` (lägg till `onClick`)
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveScreen.kt` (wira export-onClick)
- `composeApp/src/main/res/xml/file_paths.xml` (FileProvider-path för `journal_*.pdf`)
- `androidApp/src/main/AndroidManifest.xml` (lägg till FileProvider authority)
- `composeApp/src/commonMain/composeResources/values/strings.xml` (export-strängar, SV+EN)
- `composeApp/src/androidMain/assets/fonts/DMSerifDisplay-Italic.ttf` (asset)
- `composeApp/src/androidMain/assets/fonts/Caveat-Regular.ttf` (asset)

**Season statistics:**
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/SeasonStatsViewModel.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/SeasonStatsUiState.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/SeasonStatsScreen.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/charts/JournalBarChart.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/charts/JournalLineChart.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/charts/JournalDonutChart.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/LiveStatsPreview.kt` (ersätter LockedStatsPreview när Premium)
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt` (lägg till `SeasonStats`)
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt` (NavGraph entry för SeasonStats)
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/LifelistScreen.kt` (Live/Locked-swap)
- `composeApp/src/commonTest/kotlin/se/birdy/app/ui/stats/SeasonStatsViewModelTest.kt`

**10 fält-märken (catalog + rules + UI):**
- `composeApp/src/commonMain/composeResources/files/premium_badges.yaml` (schema-bump v1→v2)
- `composeApp/src/commonMain/kotlin/se/birdy/app/badges/BadgeCatalogLoader.kt` (merge två filer + parse 5 nya rules)
- `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeRule.kt` (5 nya rule-varianter)
- `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeCatalog.kt` (lägg till `isPremium: Boolean`)
- `composeApp/src/commonMain/kotlin/se/birdy/app/badges/RecalculateBadgesUseCase.kt` (evaluera 5 nya rules)
- `composeApp/src/commonMain/composeResources/values/strings.xml` (10 nameSv/nameEn + 10 descSv/descEn = 40 nya strängar)
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt` (ersätt `PremiumBadgesRow` med `PremiumBadgesSection`)
- `composeApp/src/commonMain/kotlin/se/birdy/app/badges/BadgeStringMap.kt` (extend för 10 premium-IDs)
- `composeApp/src/commonMain/kotlin/se/birdy/app/bootstrap/BadgeBackfillOnAppStart.kt` (trigger även vid Premium-flip)

**Audit-cleanup (samkört i denna sprint):**
- B1: `androidApp/build.gradle.kts` versionName-bump
- B3: `androidApp/proguard-rules.pro` BillingClient keeps
- B6: `androidApp/build.gradle.kts` Java/Kotlin target alignment

---

## Bite-Sized Task Granularity

Sprint är delad i **5 faser** med totalt **25 tasks**. Varje task: write failing test → run → impl → run → commit. Småa commits per task.

---

## Phase 1 — Foundation

### Task 1: Create `:shared:pdf` module skeleton

**Files:**
- Create: `shared/pdf/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Create: `shared/pdf/src/commonMain/kotlin/se/birdy/pdf/JournalPdfInput.kt`

- [ ] **Step 1: Write the failing test** — module-existence check

Create `shared/pdf/src/commonTest/kotlin/se/birdy/pdf/JournalPdfInputTest.kt`:

```kotlin
package se.birdy.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class JournalPdfInputTest {
    @Test
    fun construct_empty_input_has_zero_observations() {
        val input = JournalPdfInput(
            displayName = "Albin",
            generatedAtMs = 1716220800000L,
            observations = emptyList(),
            speciesByQid = emptyMap(),
            stats = JournalPdfInput.Stats(
                speciesSeenThisYear = 0,
                totalObservationsThisYear = 0,
                topSpecies = emptyList(),
            ),
            unlockedPremiumBadges = emptyList(),
        )
        assertEquals(0, input.observations.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:pdf:compileKotlinMetadata` (expected: module unknown)

- [ ] **Step 3: Add module to settings**

Append to `settings.gradle.kts`:

```kotlin
include(
    ":composeApp", ":androidApp",
    ":shared:domain", ":shared:data", ":shared:datastore",
    ":shared:ml", ":shared:content",
    ":shared:pdf",
)
```

- [ ] **Step 4: Write `shared/pdf/build.gradle.kts`** (mirror `shared/ml/build.gradle.kts`)

```kotlin
plugins {
    id("birdy.kmp-android-lib")
    alias(libs.plugins.kotlin.serialization)
}

tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { it.file.invariantSeparatorsPath.contains("/build/generated/") }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:domain"))
            implementation(project(":shared:content"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidUnitTest.dependencies {
            implementation("org.robolectric:robolectric:4.13")
            implementation("junit:junit:4.13.2")
        }
    }
}

android {
    namespace = "se.birdy.pdf"
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
```

- [ ] **Step 5: Write `JournalPdfInput.kt`**

```kotlin
package se.birdy.pdf

import kotlinx.datetime.Instant
import se.birdy.content.model.Species
import se.birdy.domain.observation.Observation

data class JournalPdfInput(
    val displayName: String,
    val generatedAtMs: Long,
    val observations: List<Observation>,
    val speciesByQid: Map<String, Species>,
    val stats: Stats,
    val unlockedPremiumBadges: List<BadgeRef>,
) {
    data class Stats(
        val speciesSeenThisYear: Int,
        val totalObservationsThisYear: Int,
        val topSpecies: List<Pair<String, Int>>,
    )

    data class BadgeRef(
        val id: String,
        val nameLocalized: String,
        val descriptionLocalized: String,
        val unlockedAt: Instant,
    )
}
```

- [ ] **Step 6: Run test to verify pass**

Run: `./gradlew :shared:pdf:jvmTest` → 1 test, PASS.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts shared/pdf/
git commit -m "feat(plan-6b3/t1): scaffold :shared:pdf module with JournalPdfInput"
```

---

### Task 2: `JournalPdfRenderer` expect/actual contract

**Files:**
- Create: `shared/pdf/src/commonMain/kotlin/se/birdy/pdf/JournalPdfRenderer.kt`
- Create: `shared/pdf/src/androidMain/kotlin/se/birdy/pdf/JournalPdfRenderer.android.kt`
- Create: `shared/pdf/src/iosMain/kotlin/se/birdy/pdf/JournalPdfRenderer.ios.kt`
- Create: `shared/pdf/src/commonTest/kotlin/se/birdy/pdf/JournalPdfRendererContractTest.kt`

- [ ] **Step 1: Write the failing contract test**

```kotlin
package se.birdy.pdf

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class JournalPdfRendererContractTest {
    @Test
    fun renderer_returns_failure_when_observations_empty() = runTest {
        val renderer = JournalPdfRenderer()
        val emptyInput = JournalPdfInput(
            displayName = "Albin",
            generatedAtMs = 1716220800000L,
            observations = emptyList(),
            speciesByQid = emptyMap(),
            stats = JournalPdfInput.Stats(0, 0, emptyList()),
            unlockedPremiumBadges = emptyList(),
        )
        val result = renderer.render(emptyInput, outputPath = "/tmp/x.pdf")
        assertTrue(result is JournalPdfRenderResult.Empty, "got: $result")
    }
}
```

- [ ] **Step 2: Write expect class + sealed result**

```kotlin
package se.birdy.pdf

expect class JournalPdfRenderer() {
    suspend fun render(input: JournalPdfInput, outputPath: String): JournalPdfRenderResult
}

sealed interface JournalPdfRenderResult {
    data class Success(val pageCount: Int, val sizeBytes: Long) : JournalPdfRenderResult
    data object Empty : JournalPdfRenderResult
    data class Failed(val message: String, val cause: Throwable? = null) : JournalPdfRenderResult
}
```

- [ ] **Step 3: Android actual (skeleton — full render in T5)**

`JournalPdfRenderer.android.kt`:

```kotlin
package se.birdy.pdf

actual class JournalPdfRenderer actual constructor() {
    actual suspend fun render(input: JournalPdfInput, outputPath: String): JournalPdfRenderResult {
        if (input.observations.isEmpty()) return JournalPdfRenderResult.Empty
        return JournalPdfRenderResult.Failed("not yet implemented — see T5")
    }
}
```

- [ ] **Step 4: iOS actual (stub)**

`JournalPdfRenderer.ios.kt`:

```kotlin
package se.birdy.pdf

actual class JournalPdfRenderer actual constructor() {
    actual suspend fun render(input: JournalPdfInput, outputPath: String): JournalPdfRenderResult =
        JournalPdfRenderResult.Failed("PDF export is Android-only in v1")
}
```

- [ ] **Step 5: Run test**

Run: `./gradlew :shared:pdf:jvmTest :shared:pdf:testDebugUnitTest`
Expected: PASS (Empty branch handled).

- [ ] **Step 6: Commit**

```bash
git add shared/pdf/
git commit -m "feat(plan-6b3/t2): JournalPdfRenderer expect/actual contract + sealed result"
```

---

### Task 3: `BadgeRepository.unlockManualBadge` interface + SQL

**Files:**
- Modify: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeRepository.kt`
- Modify: `shared/data/src/commonMain/sqldelight/se/birdy/data/db/BadgeUnlock.sq`
- Modify: `shared/data/src/commonMain/kotlin/se/birdy/data/badge/BadgeRepositoryImpl.kt`
- Test: `shared/data/src/jvmTest/kotlin/se/birdy/data/badge/BadgeRepositoryImplTest.kt` (new or extend existing)

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun unlockManualBadge_returns_true_first_time_and_false_when_already_unlocked() = runTest {
    val repo = newRepo()
    val firstAt = Instant.fromEpochMilliseconds(1_000_000)
    val secondAt = Instant.fromEpochMilliseconds(2_000_000)

    assertTrue(repo.unlockManualBadge("premium_field_member", firstAt))
    assertFalse(repo.unlockManualBadge("premium_field_member", secondAt))

    val unlocks = repo.observeUnlocks().first()
    val pfm = unlocks.first { it.badgeId == "premium_field_member" }
    assertEquals(firstAt, pfm.unlockedAt) // first-write wins
}
```

- [ ] **Step 2: Add SQL `insertIfMissing` and `selectOne`**

Append to `BadgeUnlock.sq`:

```sql
insertIfMissing:
INSERT OR IGNORE INTO badge_unlock(badge_id, unlocked_at_ms) VALUES (?, ?);

selectOne:
SELECT * FROM badge_unlock WHERE badge_id = ?;
```

- [ ] **Step 3: Extend `BadgeRepository` interface**

```kotlin
interface BadgeRepository {
    fun observeUnlocks(): Flow<List<BadgeUnlock>>
    suspend fun persist(unlocks: List<BadgeUnlock>)
    suspend fun deleteAll()
    /**
     * Idempotent manual unlock for badges that bypass the rule-engine
     * (currently: premium_field_member). Returns true if a row was inserted
     * (i.e. first unlock), false if a row already existed.
     */
    suspend fun unlockManualBadge(badgeId: String, unlockedAt: Instant): Boolean
}
```

- [ ] **Step 4: Implement in `BadgeRepositoryImpl`**

```kotlin
private val manualUnlockMutex = kotlinx.coroutines.sync.Mutex()

override suspend fun unlockManualBadge(badgeId: String, unlockedAt: Instant): Boolean =
    manualUnlockMutex.withLock {
        val existing = queries.selectOne(badgeId).executeAsOneOrNull()
        if (existing != null) return@withLock false
        queries.insertIfMissing(badgeId, unlockedAt.toEpochMilliseconds())
        true
    }
```

- [ ] **Step 5: Run test**

Run: `./gradlew :shared:data:jvmTest --tests "*BadgeRepositoryImplTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/domain shared/data
git commit -m "feat(plan-6b3/t3): BadgeRepository.unlockManualBadge (idempotent manual unlock)"
```

---

### Task 4: `PremiumActivationListener` (bootstrap-scoped, unlocks `premium_field_member`)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/bootstrap/PremiumActivationListener.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/bootstrap/PremiumActivationListenerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package se.birdy.app.bootstrap

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class PremiumActivationListenerTest {
    @Test
    fun first_active_state_unlocks_premium_field_member_exactly_once() = runTest {
        val premiumActive = MutableStateFlow(false)
        val repo = FakeBadgeRepository()
        val queue = FakeUnlockQueue()
        val listener = PremiumActivationListener(
            premiumActiveFlow = premiumActive,
            badgeRepo = repo,
            unlockQueue = queue,
            clock = Clock.System,
        )
        val job = listener.start(this)
        premiumActive.value = true
        runCurrent()
        premiumActive.value = false
        premiumActive.value = true
        runCurrent()
        assertEquals(1, repo.manualCalls.size)
        assertEquals("premium_field_member", repo.manualCalls.single().badgeId)
        assertEquals(1, queue.pushedIds.count { it == "premium_field_member" })
        job.cancel()
    }
}
```

- [ ] **Step 2: Implement `PremiumActivationListener`**

```kotlin
package se.birdy.app.bootstrap

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.datetime.Clock
import se.birdy.app.badges.UnlockQueue
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeUnlock

private const val FIELD_MEMBER_BADGE_ID = "premium_field_member"

class PremiumActivationListener(
    private val premiumActiveFlow: StateFlow<Boolean>,
    private val badgeRepo: BadgeRepository,
    private val unlockQueue: UnlockQueue,
    private val clock: Clock = Clock.System,
) {
    fun start(scope: CoroutineScope): Job =
        premiumActiveFlow
            .distinctUntilChanged()
            .filter { it }
            .onEach {
                val now = clock.now()
                val inserted = badgeRepo.unlockManualBadge(FIELD_MEMBER_BADGE_ID, now)
                if (inserted) unlockQueue.push(BadgeUnlock(FIELD_MEMBER_BADGE_ID, now))
            }
            .launchIn(scope)
}
```

- [ ] **Step 3: Run test**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*PremiumActivationListenerTest*"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/bootstrap/PremiumActivationListener.kt composeApp/src/commonTest/
git commit -m "feat(plan-6b3/t4): PremiumActivationListener — manual unlock for field_member on Premium flip"
```

---

## Phase 2 — PDF Field Journal-Export

### Task 5: Implement `JournalPdfRenderer.android` (real PDF rendering)

**Files:**
- Modify: `shared/pdf/src/androidMain/kotlin/se/birdy/pdf/JournalPdfRenderer.android.kt`
- Create: `shared/pdf/src/androidMain/kotlin/se/birdy/pdf/PdfFontProvider.android.kt`
- Create: `shared/pdf/src/androidMain/kotlin/se/birdy/pdf/JournalPdfLayout.kt`
- Create: `shared/pdf/src/androidUnitTest/kotlin/se/birdy/pdf/JournalPdfRendererAndroidTest.kt`

- [ ] **Step 1: Write the failing test (Robolectric)**

```kotlin
package se.birdy.pdf

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class JournalPdfRendererAndroidTest {
    @Test
    fun render_two_observations_emits_non_empty_pdf() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        PdfFontProvider.init(ctx)
        val outFile = File(ctx.cacheDir, "journal_test.pdf")
        val renderer = JournalPdfRenderer()
        val input = TestFixtures.twoObsInput()
        val result = renderer.render(input, outFile.absolutePath)
        assertTrue(result is JournalPdfRenderResult.Success, "got $result")
        assertTrue(outFile.length() > 0, "PDF file empty")
    }
}
```

- [ ] **Step 2: Implement `PdfFontProvider`**

```kotlin
package se.birdy.pdf

import android.content.Context
import android.graphics.Typeface

object PdfFontProvider {
    @Volatile private var dmSerifItalic: Typeface? = null
    @Volatile private var caveat: Typeface? = null

    fun init(context: Context) {
        dmSerifItalic = Typeface.createFromAsset(context.assets, "fonts/DMSerifDisplay-Italic.ttf")
        caveat = Typeface.createFromAsset(context.assets, "fonts/Caveat-Regular.ttf")
    }

    fun dmSerifItalic(): Typeface = dmSerifItalic ?: error("PdfFontProvider.init not called")
    fun caveat(): Typeface = caveat ?: error("PdfFontProvider.init not called")
}
```

- [ ] **Step 3: Implement `JournalPdfRenderer.android` — full rendering**

Pages (A4 portrait, 595×842pt):
1. Title page (DM Serif Italic display, ornament rule, Caveat sub-line)
2. Stats summary (year totals + top 5 species bars — pure Canvas drawing)
3. Per-species spread (max 50 species — one row per species: thumbnail-placeholder rect + name + counts + first-seen-date)
4. Unlocked Premium Badges page (max 10 badges, only if any unlocked)
5. Colophon (generated timestamp + version)

Implementation uses `android.graphics.pdf.PdfDocument`, `Canvas`, `Paint`. PaperBg color `#EFE7D6`, MarginaliaInk `#3F4F30`, AccentCopper `#A8552D`.

- [ ] **Step 4: Add font assets**

Copy already-bundled compose font resources into `composeApp/src/androidMain/assets/fonts/DMSerifDisplay-Italic.ttf` + `Caveat-Regular.ttf` (or symlink via gradle task — check `composeApp/.../composeResources/font/`).

- [ ] **Step 5: Run Robolectric test**

Run: `./gradlew :shared:pdf:testDebugUnitTest`
Expected: PASS. PDF file > 0 bytes.

- [ ] **Step 6: Commit**

```bash
git add shared/pdf/ composeApp/src/androidMain/assets/fonts/
git commit -m "feat(plan-6b3/t5): real JournalPdfRenderer with title/stats/species/badges/colophon pages"
```

---

### Task 6: `ExportJournalUseCase` (data-assembly orchestrator)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/ExportJournalUseCase.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/JournalExportResult.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/usecase/ExportJournalUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun export_with_no_observations_returns_NothingToExport() = runTest {
    val uc = ExportJournalUseCase(
        observationRepo = FakeObservationRepo(empty = true),
        speciesRepo = FakeSpeciesRepo(),
        badgeRepo = FakeBadgeRepo(),
        catalog = FakeCatalog(),
        renderer = FakeRenderer(),
        userPreferences = FakeUserPrefs(name = "Albin"),
        outputPathFactory = { "/tmp/x.pdf" },
        clock = Clock.System,
        locale = Locale.SV,
    )
    val r = uc.run()
    assertTrue(r is JournalExportResult.NothingToExport)
}

@Test
fun export_with_observations_returns_Success_with_path() = runTest { /* ... */ }
```

- [ ] **Step 2: Implement the UC**

```kotlin
class ExportJournalUseCase(
    private val observationRepo: ObservationRepository,
    private val speciesRepo: SpeciesRepository,
    private val badgeRepo: BadgeRepository,
    private val catalog: BadgeCatalog,
    private val renderer: JournalPdfRenderer,
    private val userPreferences: UserPreferences,
    private val outputPathFactory: (timestampMs: Long) -> String,
    private val clock: Clock,
    private val locale: Locale,
) {
    suspend fun run(): JournalExportResult {
        val obs = observationRepo.observeAll().first()
        if (obs.isEmpty()) return JournalExportResult.NothingToExport
        val speciesByQid = speciesRepo.allByQid()
        val displayName = userPreferences.displayName.first() ?: "Birdy"
        val now = clock.now()
        val stats = JournalPdfInput.Stats(
            speciesSeenThisYear = computeSpeciesSeenThisYear(obs, now),
            totalObservationsThisYear = computeTotalObsThisYear(obs, now),
            topSpecies = computeTopSpecies(obs, speciesByQid, locale, limit = 5),
        )
        val unlocks = badgeRepo.observeUnlocks().first()
        val premiumBadges = unlocks
            .filter { catalog.badgeOrNull(it.badgeId)?.isPremium == true }
            .mapNotNull { u ->
                val badge = catalog.badgeOrNull(u.badgeId) ?: return@mapNotNull null
                JournalPdfInput.BadgeRef(
                    id = u.badgeId,
                    nameLocalized = badge.localizedName(locale),
                    descriptionLocalized = badge.localizedDescription(locale),
                    unlockedAt = u.unlockedAt,
                )
            }
        val input = JournalPdfInput(
            displayName = displayName,
            generatedAtMs = now.toEpochMilliseconds(),
            observations = obs,
            speciesByQid = speciesByQid.mapKeys { it.key.value },
            stats = stats,
            unlockedPremiumBadges = premiumBadges,
        )
        val outPath = outputPathFactory(now.toEpochMilliseconds())
        return when (val r = renderer.render(input, outPath)) {
            is JournalPdfRenderResult.Success -> JournalExportResult.Success(outPath, r.pageCount, r.sizeBytes)
            JournalPdfRenderResult.Empty -> JournalExportResult.NothingToExport
            is JournalPdfRenderResult.Failed -> JournalExportResult.Failed(r.message)
        }
    }
}
```

- [ ] **Step 3: Run test**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ExportJournalUseCaseTest*"`
Expected: PASS (both tests).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/usecase/ composeApp/src/commonTest/
git commit -m "feat(plan-6b3/t6): ExportJournalUseCase — assemble data + invoke renderer"
```

---

### Task 7: `AppGraph.journalExport` lambda + Android wiring + share-sheet

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- Create: `androidApp/src/main/kotlin/se/birdy/android/share/JournalPdfShareLauncher.kt`
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Create: `androidApp/src/main/res/xml/file_paths.xml`

- [ ] **Step 1: Add `file_paths.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="journal_exports" path="journal_exports/" />
</paths>
```

- [ ] **Step 2: Add FileProvider to manifest**

Inside `<application>`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="se.birdy.android.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

- [ ] **Step 3: Add `journalExport` lambda to `AppGraph`**

```kotlin
val journalExport: (suspend () -> JournalExportResult)? = null,
```

(Document analogously to existing lambda-fields.)

- [ ] **Step 4: Implement `JournalPdfShareLauncher`**

```kotlin
package se.birdy.android.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object JournalPdfShareLauncher {
    fun launch(context: Context, pdfPath: String) {
        val file = File(pdfPath)
        val uri = FileProvider.getUriForFile(context, "se.birdy.android.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Field Journal").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
```

- [ ] **Step 5: Wire `journalExport` in `MainActivity.buildAppGraph()`**

```kotlin
val journalExportUseCase = ExportJournalUseCase(
    observationRepo = observationRepo,
    speciesRepo = SpeciesRepositoryProvider.get(),
    badgeRepo = badgeRepo,
    catalog = badgeCatalog,
    renderer = JournalPdfRenderer().also { PdfFontProvider.init(applicationContext) },
    userPreferences = userPreferences,
    outputPathFactory = { ms ->
        val dir = File(cacheDir, "journal_exports").apply { mkdirs() }
        File(dir, "birdy_field_journal_$ms.pdf").absolutePath
    },
    clock = Clock.System,
    locale = resolvedLocale,
)
// Pass `journalExport = { journalExportUseCase.run() }` to AppGraph(...)
```

After receiving `JournalExportResult.Success`, the calling Composable triggers `JournalPdfShareLauncher.launch(...)`. The lambda itself just returns the result; share-sheet is invoked from the UI layer (next task).

- [ ] **Step 6: Manual build verify (no new test — integration deferred to T8)**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add androidApp/ composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt
git commit -m "feat(plan-6b3/t7): AppGraph.journalExport lambda + FileProvider + share-launcher"
```

---

### Task 8: PremiumTeaserCard onClick + Archive integration + DEMO-disabled

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/PremiumTeaserCard.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-sv/strings.xml`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/components/PremiumTeaserCardTest.kt`

- [ ] **Step 1: Write failing test (Compose ui test for onClick state)**

```kotlin
@Test
fun teaser_with_premium_active_shows_export_label_and_invokes_onExport() {
    composeTestRule.setContent {
        PremiumTeaserCard(
            premiumActive = true,
            isExporting = false,
            onExport = { exportCount++ },
            onUnlock = { fail("should not call unlock when active") },
        )
    }
    composeTestRule.onNodeWithText("Export Field Journal").performClick()
    assertEquals(1, exportCount)
}
```

- [ ] **Step 2: Add strings**

`values/strings.xml`:

```xml
<string name="premium_teaser_export_active">Export Field Journal</string>
<string name="premium_teaser_export_busy">Preparing PDF…</string>
<string name="premium_teaser_export_empty">No observations yet</string>
<string name="premium_teaser_export_failed">Couldn't make the PDF — try again</string>
```

`values-sv/strings.xml`:

```xml
<string name="premium_teaser_export_active">Exportera fältdagbok</string>
<string name="premium_teaser_export_busy">Förbereder PDF…</string>
<string name="premium_teaser_export_empty">Inga observationer ännu</string>
<string name="premium_teaser_export_failed">Kunde inte skapa PDF — försök igen</string>
```

- [ ] **Step 3: Update `PremiumTeaserCard` signature**

```kotlin
@Composable
fun PremiumTeaserCard(
    premiumActive: Boolean,
    isExporting: Boolean = false,
    onUnlock: () -> Unit,
    onExport: () -> Unit = {},
    // ... existing args
) {
    // Conditionally swap CTA copy + invoke onExport when premiumActive.
}
```

- [ ] **Step 4: Wire `ArchiveScreen` — `LaunchedEffect` triggers `appGraph.journalExport` + share**

```kotlin
val scope = rememberCoroutineScope()
var isExporting by remember { mutableStateOf(false) }
val context = LocalContext.current  // Android-only via expect/actual
PremiumTeaserCard(
    premiumActive = state.premiumActive,
    isExporting = isExporting,
    onUnlock = onPremium,
    onExport = {
        if (isExporting) return@PremiumTeaserCard
        isExporting = true
        scope.launch {
            val result = appGraph.journalExport?.invoke()
            isExporting = false
            when (result) {
                is JournalExportResult.Success -> ShareLauncher.share(result.path)
                JournalExportResult.NothingToExport -> snackbar(empty)
                is JournalExportResult.Failed -> snackbar(failed)
                null -> {}
            }
        }
    },
)
```

Use an expect/actual `ShareLauncher` to invoke `JournalPdfShareLauncher` from common code.

- [ ] **Step 5: Run test**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*PremiumTeaserCardTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/
git commit -m "feat(plan-6b3/t8): PremiumTeaserCard onExport CTA + Archive share-sheet integration"
```

---

## Phase 3 — Season Statistics

### Task 9: `SeasonStatsUiState` + `SeasonStatsViewModel` (logic only)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/SeasonStatsUiState.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/SeasonStatsViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/stats/SeasonStatsViewModelTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun loaded_state_includes_12_months_of_observations() = runTest {
    val obs = listOf(
        obs(qid = "Q1", yearMonth = "2026-01"),
        obs(qid = "Q1", yearMonth = "2026-01"),
        obs(qid = "Q2", yearMonth = "2026-05"),
    )
    val vm = SeasonStatsViewModel(
        observationRepo = FakeObsRepo(obs),
        speciesRepo = FakeSpeciesRepo(),
        clock = FixedClock("2026-05-22T08:00:00Z"),
        zone = TimeZone.UTC,
        locale = Locale.SV,
    )
    vm.onEnter()
    runCurrent()
    val state = vm.state.value as SeasonStatsUiState.Loaded
    assertEquals(12, state.monthBars.size)
    assertEquals(2, state.monthBars.first { it.label == "JAN" }.observationCount)
    assertEquals(1, state.monthBars.first { it.label == "MAY" }.observationCount)
}
```

- [ ] **Step 2: Implement state**

```kotlin
sealed interface SeasonStatsUiState {
    data object Loading : SeasonStatsUiState
    data object Empty : SeasonStatsUiState
    data class Loaded(
        val totalSpeciesThisYear: Int,
        val totalObservationsThisYear: Int,
        val monthBars: List<MonthBar>,
        val seasonDonut: SeasonBreakdown,
        val topSpecies: List<TopSpeciesRow>,
        val cumulativeLine: List<CumulativePoint>,
    ) : SeasonStatsUiState

    data class MonthBar(val label: String, val observationCount: Int, val isCurrent: Boolean)
    data class SeasonBreakdown(val winter: Int, val spring: Int, val summer: Int, val autumn: Int)
    data class TopSpeciesRow(val qid: String, val nameLocalized: String, val count: Int)
    data class CumulativePoint(val month: Int, val uniqueSpeciesByEndOfMonth: Int)
}
```

- [ ] **Step 3: Implement VM**

Three aggregations: month-bars (12 buckets), season-donut (winter/spring/summer/autumn — reuse `seasonOf` from `BadgeRule.kt` helpers), top-5 species, cumulative-line. All derived from `observationRepo.observeAll().first()` then `withContext(Dispatchers.Default)`.

- [ ] **Step 4: Run test**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SeasonStatsViewModelTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/
git commit -m "feat(plan-6b3/t9): SeasonStatsViewModel — aggregates month/season/top-species/cumulative"
```

---

### Task 10: Canvas-based chart composables (bar/line/donut)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/charts/JournalBarChart.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/charts/JournalLineChart.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/charts/JournalDonutChart.kt`

- [ ] **Step 1: Implement `JournalBarChart`**

```kotlin
@Composable
fun JournalBarChart(
    bars: List<SeasonStatsUiState.MonthBar>,
    modifier: Modifier = Modifier,
    barColor: Color = AccentCopper,
    currentMonthColor: Color = MarginaliaInk,
    height: Dp = 140.dp,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val maxCount = (bars.maxOfOrNull { it.observationCount } ?: 1).coerceAtLeast(1)
        val barWidth = size.width / (bars.size * 1.5f)
        val gap = barWidth * 0.5f
        bars.forEachIndexed { i, b ->
            val x = i * (barWidth + gap)
            val h = (b.observationCount / maxCount.toFloat()) * size.height
            drawRect(
                color = if (b.isCurrent) currentMonthColor else barColor,
                topLeft = Offset(x, size.height - h),
                size = Size(barWidth, h),
            )
        }
    }
}
```

Then `JournalLineChart` (similar, draw `Path` with cubic Bezier between cumulative points) and `JournalDonutChart` (draw 4 `drawArc` sectors with `useCenter=false` and stroke ~24dp).

- [ ] **Step 2: Smoke-test composables (preview)**

No commonTest for visuals — these get device-verified in T22.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/charts/
git commit -m "feat(plan-6b3/t10): Canvas-based bar/line/donut chart composables (0 deps)"
```

---

### Task 11: `SeasonStatsScreen` layout

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/SeasonStatsScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-sv/strings.xml`

- [ ] **Step 1: Add strings**

```xml
<!-- EN -->
<string name="stats_title">Season Statistics</string>
<string name="stats_intro_headline">A *year* in the field.</string>
<string name="stats_intro_sub">Patterns from your observations.</string>
<string name="stats_section_months">Observations by month</string>
<string name="stats_section_seasons">By season</string>
<string name="stats_section_top">Most-seen species</string>
<string name="stats_section_cumulative">New species over time</string>
<string name="stats_empty_headline">Nothing to *chart* yet.</string>
<string name="stats_empty_sub">Save a few observations to unlock your year.</string>

<!-- SV -->
<string name="stats_title">Säsongsstatistik</string>
<string name="stats_intro_headline">Ett *år* i fält.</string>
<string name="stats_intro_sub">Mönster från dina observationer.</string>
<string name="stats_section_months">Observationer per månad</string>
<string name="stats_section_seasons">Per säsong</string>
<string name="stats_section_top">Mest sedda arter</string>
<string name="stats_section_cumulative">Nya arter över tid</string>
<string name="stats_empty_headline">Inget att *kartlägga* ännu.</string>
<string name="stats_empty_sub">Spara några observationer för att låsa upp ditt år.</string>
```

- [ ] **Step 2: Implement screen**

```kotlin
@Composable
fun SeasonStatsScreen(viewModel: SeasonStatsViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.onEnter() }
    val state by viewModel.state.collectAsState()
    JournalScaffold(
        topBar = { JournalTopBar(title = stringResource(Res.string.stats_title), onBack = onBack) },
    ) { padding ->
        when (val s = state) {
            SeasonStatsUiState.Loading -> JournalLoading()
            SeasonStatsUiState.Empty -> EmptyState(
                headlineRes = Res.string.stats_empty_headline,
                subRes = Res.string.stats_empty_sub,
            )
            is SeasonStatsUiState.Loaded -> StatsContent(s, Modifier.padding(padding))
        }
    }
}

@Composable
private fun StatsContent(s: SeasonStatsUiState.Loaded, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().paperBackground()) {
        item { JournalIntro(...) }
        item { OrnamentRule() }
        item { SectionLabel(text = stringResource(Res.string.stats_section_months)) }
        item { JournalBarChart(bars = s.monthBars) }
        item { OrnamentRule() }
        item { SectionLabel(text = stringResource(Res.string.stats_section_seasons)) }
        item { JournalDonutChart(breakdown = s.seasonDonut) }
        item { OrnamentRule() }
        item { SectionLabel(text = stringResource(Res.string.stats_section_top)) }
        items(s.topSpecies) { row -> TopSpeciesRow(row) }
        item { OrnamentRule() }
        item { SectionLabel(text = stringResource(Res.string.stats_section_cumulative)) }
        item { JournalLineChart(points = s.cumulativeLine) }
    }
}
```

- [ ] **Step 3: Build verify**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/
git commit -m "feat(plan-6b3/t11): SeasonStatsScreen — JournalScaffold + LazyColumn chart sections"
```

---

### Task 12: `AppRoute.SeasonStats` + navigation wiring + LockedStatsPreview swap

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt` (factory)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/LifelistScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/stats/LiveStatsPreview.kt`

- [ ] **Step 1: Add `AppRoute.SeasonStats`**

```kotlin
@Serializable data object SeasonStats : AppRoute
```

- [ ] **Step 2: Add `AppGraph.seasonStatsViewModel()` factory**

```kotlin
fun seasonStatsViewModel(): SeasonStatsViewModel = SeasonStatsViewModel(
    observationRepo = observationRepository,
    speciesRepo = repository,
    clock = clock,
    zone = timeZone,
    locale = defaultLocale,
)
```

- [ ] **Step 3: Wire NavGraph composable in `AppScaffold`**

```kotlin
composable<AppRoute.SeasonStats> {
    SeasonStatsScreen(viewModel = appGraph.seasonStatsViewModel(), onBack = { nav.popBackStack() })
}
```

- [ ] **Step 4: Implement `LiveStatsPreview`** (mini preview shown on Lifelist when Premium active — top-3 month bars + species-count chip + "Open stats →" link)

```kotlin
@Composable
fun LiveStatsPreview(state: SeasonStatsUiState.Loaded, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp)) {
        Text(stringResource(Res.string.stats_intro_headline_short), ...)
        JournalBarChart(bars = state.monthBars.take(6), height = 70.dp)
        Text("Open stats →", style = caveatItalic, color = AccentCopper)
    }
}
```

- [ ] **Step 5: Swap in `LifelistScreen`**

```kotlin
if (premiumActive) {
    LiveStatsPreview(state = stats, onOpen = { onNavigate(AppRoute.SeasonStats) })
} else {
    LockedStatsPreview(onUnlock = onPremium)
}
```

(Add a `livePreviewState: SeasonStatsUiState.Loaded?` field to `LifelistViewModel`, loaded conditionally when `premiumActive=true`.)

- [ ] **Step 6: Build verify**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/
git commit -m "feat(plan-6b3/t12): AppRoute.SeasonStats + Live/Locked stats-preview swap on Lifelist"
```

---

## Phase 4 — 10 Premium Field Badges

### Task 13: Extend `BadgeRule` with 5 new rule types + tests

**Files:**
- Modify: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeRule.kt`
- Modify: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeCatalog.kt`
- Test: `shared/domain/src/commonTest/kotlin/se/birdy/domain/badge/BadgeRuleTest.kt` (new or extend)

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun observed_before_hour_subtype_exists_and_carries_hour() {
    val rule: BadgeRule = BadgeRule.ObservedBeforeHour(hour = 6, target = 5)
    assertEquals(6, rule.hour)
    assertEquals(5, rule.target)
}

@Test
fun species_across_seasons_exists() {
    val rule: BadgeRule = BadgeRule.SpeciesAcrossSeasons(seasons = 4, target = 1)
    assertEquals(4, rule.seasons)
}

@Test
fun audio_observation_count_exists() {
    val rule: BadgeRule = BadgeRule.AudioObservationCount(target = 5)
    assertEquals(5, rule.target)
}
// ...etc for ObservationsWithNote, ObservedInAllSeasons
```

- [ ] **Step 2: Add the 5 new sealed variants to `BadgeRule.kt`**

```kotlin
sealed interface BadgeRule {
    val target: Int
    // ... existing 6 variants ...

    data class ObservedBeforeHour(val hour: Int, override val target: Int) : BadgeRule
    data class SpeciesAcrossSeasons(val seasons: Int, override val target: Int) : BadgeRule
    data class AudioObservationCount(override val target: Int) : BadgeRule
    data class ObservationsWithNote(val minLength: Int, override val target: Int) : BadgeRule
    data class ObservedInAllSeasons(override val target: Int) : BadgeRule
}
```

- [ ] **Step 3: Add `isPremium` to `Badge` + `BadgeCatalog.badgeOrNull`**

```kotlin
data class Badge(
    val id: String,
    val category: BadgeCategory,
    val rule: BadgeRule,
    val isPremium: Boolean = false,
)

data class BadgeCatalog(val version: Int, val badges: List<Badge>) {
    fun badgeOrNull(id: String): Badge? = badges.firstOrNull { it.id == id }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :shared:domain:jvmTest`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/domain
git commit -m "feat(plan-6b3/t13): 5 new BadgeRule variants + Badge.isPremium flag"
```

---

### Task 14: Extend `RecalculateBadgesUseCase` to evaluate 5 new rules

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/badges/RecalculateBadgesUseCase.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/badges/RecalculateBadgesUseCaseTest.kt` (extend)

- [ ] **Step 1: Write failing tests**

```kotlin
@Test
fun observed_before_hour_counts_observations_with_local_hour_lt_threshold() {
    val obs = listOf(
        obs(qid = "Q1", instant = "2026-05-22T05:30:00Z"),  // 07:30 in CEST (UTC+2)
        obs(qid = "Q1", instant = "2026-05-22T05:00:00Z"),  // 07:00 in CEST
        obs(qid = "Q2", instant = "2026-05-22T03:30:00Z"),  // 05:30 in CEST — qualifies
    )
    val rule = BadgeRule.ObservedBeforeHour(hour = 6, target = 1)
    val uc = RecalculateBadgesUseCase(zone = TimeZone.of("Europe/Stockholm"))
    val unlocks = uc.newUnlocks(
        observations = obs,
        speciesByQid = emptyMap(),
        catalog = catalog(rule, "dawn"),
        existingUnlocks = emptySet(),
    )
    assertEquals(setOf("dawn"), unlocks.map { it.badgeId }.toSet())
}

@Test
fun audio_observation_count_only_counts_audio_source_observations() {
    val obs = listOf(
        obs(qid = "Q1", source = ObservationSource.Photo),
        obs(qid = "Q1", source = ObservationSource.Audio),
        obs(qid = "Q1", source = ObservationSource.Audio),
    )
    val rule = BadgeRule.AudioObservationCount(target = 2)
    /* ... */
}

@Test
fun species_across_seasons_unlocks_when_same_qid_seen_in_N_seasons() { /* ... */ }

@Test
fun observations_with_note_counts_obs_where_note_length_gte_minLength() { /* ... */ }

@Test
fun observed_in_all_seasons_unlocks_when_all_4_seasons_have_obs() { /* ... */ }
```

- [ ] **Step 2: Extend `rawValue` `when` branch**

```kotlin
is BadgeRule.ObservedBeforeHour ->
    observations.count { o ->
        val localHour = o.capturedAt.toLocalDateTime(zone).hour
        localHour < rule.hour
    }
is BadgeRule.SpeciesAcrossSeasons ->
    observations
        .filter { it.speciesId != null }
        .groupBy { it.speciesId!! }
        .values
        .count { ofSpecies ->
            ofSpecies.mapNotNull { seasonOf(it.capturedAt, zone) }.toSet().size >= rule.seasons
        }
is BadgeRule.AudioObservationCount ->
    observations.count { it.sourceType == ObservationSource.Audio }
is BadgeRule.ObservationsWithNote ->
    observations.count { it.note.length >= rule.minLength }
is BadgeRule.ObservedInAllSeasons -> {
    val seasonsSeen = observations.mapNotNull { seasonOf(it.capturedAt, zone) }.toSet()
    if (seasonsSeen.size >= 4) 1 else 0
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*RecalculateBadgesUseCaseTest*"`
Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add composeApp/
git commit -m "feat(plan-6b3/t14): RecalculateBadgesUseCase evaluates 5 new premium rules"
```

---

### Task 15: Schema-bump `premium_badges.yaml` v1→v2 + parser

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/files/premium_badges.yaml`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/badges/BadgeCatalogLoader.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/badges/BadgeCatalogLoaderTest.kt`

- [ ] **Step 1: Write the failing test (parse new file)**

```kotlin
@Test
fun premium_yaml_v2_parses_with_rules_and_isPremium_set_true() = runTest {
    val yaml = """
        version: 2
        badges:
          - id: premium_dawn_chorus
            category: rare
            rule:
              type: observed_before_hour
              hour: 6
              target: 5
        """.trimIndent()
    val catalog = BadgeCatalogLoader.parsePremium(yaml)
    assertEquals(1, catalog.badges.size)
    val b = catalog.badges.single()
    assertEquals("premium_dawn_chorus", b.id)
    assertTrue(b.isPremium)
    assertEquals(BadgeRule.ObservedBeforeHour(hour = 6, target = 5), b.rule)
}
```

- [ ] **Step 2: Rewrite `premium_badges.yaml` (10 badges, schema v2)**

```yaml
# Premium-only badges. Schema v2 = adds rule + descriptions per badge.
# IDs 1-9 evaluate via BadgeEvaluator; #10 (premium_field_member) unlocks
# manually via PremiumActivationListener (rule:type=manual is a marker only).
version: 2
badges:
  - id: premium_dawn_chorus
    category: rare
    rule:
      type: observed_before_hour
      hour: 6
      target: 5
  - id: premium_winter_wanderer
    category: season
    rule:
      type: observed_in_season
      season: winter
      target: 10
  - id: premium_migration_mapper
    category: rare
    rule:
      type: species_across_seasons
      seasons: 4
      target: 1
  - id: premium_song_scholar
    category: rare
    rule:
      type: audio_observation_count
      target: 5
  - id: premium_field_journalist
    category: rare
    rule:
      type: observations_with_note
      minLength: 30
      target: 25
  - id: premium_archive_curator
    category: progression
    rule:
      type: count_unique_species
      target: 100
  - id: premium_seasonal_steward
    category: season
    rule:
      type: observed_in_all_seasons
      target: 1
  - id: premium_lifelist_legend
    category: progression
    rule:
      type: count_unique_species
      target: 250
  - id: premium_rare_seeker
    category: rare
    rule:
      type: observed_with_abundance
      abundance: sällsynt
      target: 3
  - id: premium_field_member
    category: rare
    rule:
      type: manual
      target: 1
```

- [ ] **Step 3: Extend `BadgeCatalogLoader`**

Add `parsePremium(yamlText)` that re-uses `parse(...)` but sets `isPremium = true` on every returned badge. Extend `parseRule` to handle 5 new rule types + `"manual"` (returns a sentinel rule that evaluates to 0 always — `data object Manual : BadgeRule { override val target = 1 }`).

```kotlin
"observed_before_hour" -> BadgeRule.ObservedBeforeHour(
    hour = raw.hour ?: missing(badgeId, "hour"),
    target = raw.target ?: missing(badgeId, "target"),
)
"species_across_seasons" -> BadgeRule.SpeciesAcrossSeasons(
    seasons = raw.seasons ?: missing(badgeId, "seasons"),
    target = raw.target ?: missing(badgeId, "target"),
)
"audio_observation_count" -> BadgeRule.AudioObservationCount(
    target = raw.target ?: missing(badgeId, "target"),
)
"observations_with_note" -> BadgeRule.ObservationsWithNote(
    minLength = raw.minLength ?: missing(badgeId, "minLength"),
    target = raw.target ?: missing(badgeId, "target"),
)
"observed_in_all_seasons" -> BadgeRule.ObservedInAllSeasons(
    target = raw.target ?: missing(badgeId, "target"),
)
"manual" -> BadgeRule.Manual
```

Add `loadFromResources()` to also load `files/premium_badges.yaml` and merge:

```kotlin
@OptIn(ExperimentalResourceApi::class)
suspend fun loadFromResources(): BadgeCatalog {
    val regular = parse(Res.readBytes("files/badges.yaml").decodeToString())
    val premium = parsePremium(Res.readBytes("files/premium_badges.yaml").decodeToString())
    return BadgeCatalog(
        version = regular.version * 100 + premium.version,
        badges = regular.badges + premium.badges,
    )
}
```

- [ ] **Step 4: Skip `Manual` rule in `RecalculateBadgesUseCase`**

```kotlin
.filter { it.rule !is BadgeRule.Manual }
.filter { evaluate(...) }
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*BadgeCatalogLoaderTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/
git commit -m "feat(plan-6b3/t15): premium_badges.yaml v2 schema + BadgeCatalogLoader merge"
```

---

### Task 16: Add 40 new strings (10 names + 10 descriptions × SV + EN) + BadgeStringMap

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-sv/strings.xml`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/badges/BadgeStringMap.kt`

- [ ] **Step 1: Add 10 nameSv + 10 nameEn (mirror existing convention)**

EN excerpt:

```xml
<string name="badge_premium_dawn_chorus_name">Dawn chorus</string>
<string name="badge_premium_dawn_chorus_desc">Spot 5 birds before 6 a.m.</string>
<string name="badge_premium_winter_wanderer_name">Winter wanderer</string>
<string name="badge_premium_winter_wanderer_desc">Save 10 winter observations.</string>
<string name="badge_premium_migration_mapper_name">Migration mapper</string>
<string name="badge_premium_migration_mapper_desc">See one species across all four seasons.</string>
<string name="badge_premium_song_scholar_name">Song scholar</string>
<string name="badge_premium_song_scholar_desc">Identify 5 birds by sound.</string>
<string name="badge_premium_field_journalist_name">Field journalist</string>
<string name="badge_premium_field_journalist_desc">Write 25 notes of 30 characters or more.</string>
<string name="badge_premium_archive_curator_name">Archive curator</string>
<string name="badge_premium_archive_curator_desc">Reach 100 unique species.</string>
<string name="badge_premium_seasonal_steward_name">Seasonal steward</string>
<string name="badge_premium_seasonal_steward_desc">Save at least one bird in every season.</string>
<string name="badge_premium_lifelist_legend_name">Lifelist legend</string>
<string name="badge_premium_lifelist_legend_desc">Reach 250 unique species.</string>
<string name="badge_premium_rare_seeker_name">Rare seeker</string>
<string name="badge_premium_rare_seeker_desc">Spot 3 rare species.</string>
<string name="badge_premium_field_member_name">Field member</string>
<string name="badge_premium_field_member_desc">Welcome to Birdy Premium.</string>
```

SV (same shape, translated). Use Unicode `’` (U+2019) for apostrophes.

- [ ] **Step 2: Extend `BadgeStringMap`**

```kotlin
fun nameFor(badgeId: String): StringResource = when (badgeId) {
    // ... existing 25 ...
    "premium_dawn_chorus" -> Res.string.badge_premium_dawn_chorus_name
    // ... + 9 more
    else -> error("No name mapping for $badgeId")
}

fun descriptionFor(badgeId: String): StringResource = when (badgeId) {
    // ... existing 25 (or add this map alongside) ...
    "premium_dawn_chorus" -> Res.string.badge_premium_dawn_chorus_desc
    // ... + 9 more
    else -> error("No description mapping for $badgeId")
}
```

- [ ] **Step 3: Build verify (and `compose-resources` regenerate)**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/
git commit -m "feat(plan-6b3/t16): 40 premium-badge strings + BadgeStringMap extension"
```

---

### Task 17: Replace `PremiumBadgesRow` with `PremiumBadgesSection` (locked-grid ↔ real-grid)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt` (add `premiumBadges` to state)

- [ ] **Step 1: Update `BadgesViewModel`**

Add to `BadgesUiState.Loaded`:

```kotlin
val premiumBadges: List<BadgeProgress>,   // 10 entries, locked or unlocked
val premiumActive: Boolean,
```

Populate from the merged catalog (filter `isPremium = true`).

- [ ] **Step 2: Implement `PremiumBadgesSection`**

```kotlin
@Composable
private fun PremiumBadgesSection(
    premiumActive: Boolean,
    badges: List<BadgeProgress>,
    onPremiumClick: () -> Unit,
    onUnlockedClick: (BadgeUnlock) -> Unit,
    onLockedClick: (BadgeProgress) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(stringResource(Res.string.premium_badges_section), ...)
        OrnamentRule()
        if (premiumActive) {
            // 2x5 real StampSeal-grid using normal BadgeGridCell.
            LazyVerticalGrid(columns = GridCells.Fixed(5), userScrollEnabled = false) {
                items(badges, key = { it.badge.id }) { bp ->
                    BadgeGridCell(progress = bp, onClick = {
                        if (bp.unlock != null) onUnlockedClick(bp.unlock) else onLockedClick(bp)
                    })
                }
            }
        } else {
            // 5-ghost-stamp row + copper CTA (existing PremiumBadgesRow visuals).
            // ...
        }
    }
}
```

- [ ] **Step 3: Build + lint verify**

Run: `./gradlew :composeApp:assembleDebug ktlintCheck`
Expected: BUILD + LINT GREEN.

- [ ] **Step 4: Commit**

```bash
git add composeApp/
git commit -m "feat(plan-6b3/t17): PremiumBadgesSection — locked-row vs real 10-stamp grid swap"
```

---

### Task 18: Trigger `BadgeBackfillOnAppStart` recalc on premium-flip

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/bootstrap/BadgeBackfillOnAppStart.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`

- [ ] **Step 1: Add `runIfPremiumNewlyActive(premiumActive: Boolean)` method**

```kotlin
suspend fun runIfPremiumNewlyActive() {
    // Always safe to re-run — newUnlocks is idempotent against existingUnlocks.
    val obs = obsRepo.observeAll().first()
    val newUnlocks = recalc.newUnlocks(
        observations = obs,
        speciesByQid = speciesByQid().mapKeys { it.key.value },
        catalog = catalog,
        existingUnlocks = badgeRepo.observeUnlocks().first().map { it.badgeId }.toSet(),
    )
    if (newUnlocks.isNotEmpty()) badgeRepo.persist(newUnlocks)
}
```

- [ ] **Step 2: Wire from `MainActivity`**

After `appGraph.badgeBackfill` setup, observe `effectivePremiumActive` and call `runIfPremiumNewlyActive()` on false→true:

```kotlin
lifecycleScope.launch {
    appGraph.effectivePremiumActive
        .distinctUntilChanged()
        .filter { it }
        .collect { appGraph.badgeBackfill.runIfPremiumNewlyActive() }
}
```

(`effectivePremiumActive` was already wired in Plan 7e — verify and expose if not already public on AppGraph.)

- [ ] **Step 3: Build verify**

Run: `./gradlew :composeApp:assembleDebug :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/ androidApp/
git commit -m "feat(plan-6b3/t18): re-run badge backfill on Premium-active flip"
```

---

## Phase 5 — Polish, Audit-cleanup, Tag

### Task 19: Wire `PremiumActivationListener` from `MainActivity`

**Files:**
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`

- [ ] **Step 1: Expose `effectivePremiumActive: StateFlow<Boolean>` on `AppGraph`**

```kotlin
val effectivePremiumActive: StateFlow<Boolean> by lazy {
    premiumRepository.state
        .map { it is PremiumState.Active || premiumOverride is PremiumState.Active }
        .stateIn(MainScope(), SharingStarted.Eagerly, false)
}

val unlockQueue: UnlockQueue by lazy { UnlockQueue() }

val premiumActivationListener: PremiumActivationListener by lazy {
    PremiumActivationListener(
        premiumActiveFlow = effectivePremiumActive,
        badgeRepo = badgeRepository,
        unlockQueue = unlockQueue,
        clock = clock,
    )
}
```

(Reconcile with the existing `UnlockQueue` instance if Plan 5b already exposes one on AppGraph — replace local construction with the shared field.)

- [ ] **Step 2: Start the listener in `MainActivity.onCreate`**

```kotlin
appGraph.premiumActivationListener.start(lifecycleScope)
```

- [ ] **Step 3: Build verify**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add androidApp/ composeApp/
git commit -m "feat(plan-6b3/t19): wire PremiumActivationListener + effectivePremiumActive on AppGraph"
```

---

### Task 20: Audit-cleanup B1, B3, B6 (versionName bump, ProGuard keeps, Java target)

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `androidApp/proguard-rules.pro`

- [ ] **Step 1: Verify B3 (Billing v8 ProGuard keeps)**

Confirm `proguard-rules.pro` contains:

```
-keep class com.android.billingclient.api.** { *; }
-keep class com.android.vending.billing.** { *; }
-dontwarn com.android.billingclient.api.**
```

If missing, add.

- [ ] **Step 2: Bump versionName to `1.0.0-rc3` + versionCode `+1`**

In `androidApp/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = 111   // was 110
    versionName = "1.0.0-rc3"
}
```

- [ ] **Step 3: Verify B6 (Java/Kotlin target alignment)**

Ensure `compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }` matches `kotlin { jvmToolchain(17) }` across `:composeApp`, `:androidApp`, all `:shared:*`. Fix any mismatches.

- [ ] **Step 4: Run lint + assemble**

Run: `./gradlew ktlintCheck detekt :androidApp:assembleDebug`
Expected: GREEN.

- [ ] **Step 5: Commit**

```bash
git add androidApp/build.gradle.kts androidApp/proguard-rules.pro
git commit -m "chore(plan-6b3/t20): bump versionName 1.0.0-rc3 + Billing ProGuard keeps + Java17 align"
```

---

### Task 21: Device-verify on SM-S918B + acceptance criteria runthrough

**Files:**
- Create: `docs/superpowers/screenshots/2026-05-22-v0.9.0c-premium-content/` (new directory)
- Create: `docs/superpowers/runbooks/2026-05-22-v0.9.0c-device-verify.md`

- [ ] **Step 1: Build release-debug APK + install**

Run: `./gradlew :androidApp:installDebug`
Then: `"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity`

- [ ] **Step 2: Set `PREMIUM_DEBUG_FORCE_ACTIVE=true`, rebuild + install**

Verify behavior (each as one screenshot or smoke-step):
1. `01-archive-export-cta.png` — Archive shows "Export Field Journal"
2. `02-export-pdf-share-sheet.png` — Tap → PDF generated + share-sheet opens
3. `03-pdf-content-title.pdf` (saved sample) — open the generated PDF and visually check 5 pages (title/stats/species/badges/colophon)
4. `04-stats-from-lifelist.png` — Lifelist shows LiveStatsPreview
5. `05-season-stats-month-bars.png` — Stats screen shows month bars
6. `06-season-stats-donut.png` — Donut chart
7. `07-season-stats-cumulative-line.png` — Cumulative line
8. `08-badges-premium-section.png` — Badges shows 2×5 grid (locked stamps)
9. `09-premium-unlock-bottomsheet.png` — Premium flip triggers `premium_field_member` welcome bottom-sheet
10. `10-pdf-contains-unlocked-badges-page.pdf` — Re-export, verify page 4 lists unlocked badges

- [ ] **Step 3: Acceptance criteria from spec — runthrough**

From `2026-05-21-v1-06b3-premium-content-design.md` §9:
- ✅/❌ A1-A12 — record results in runbook.

- [ ] **Step 4: Commit screenshots + runbook**

```bash
git add docs/superpowers/screenshots/2026-05-22-v0.9.0c-premium-content/ docs/superpowers/runbooks/2026-05-22-v0.9.0c-device-verify.md
git commit -m "test(plan-6b3/t21): device-verify v0.9.0c on SM-S918B + 10 screenshots"
```

---

### Task 22: Smoke-test signed AAB v1.0.0-rc3

**Files:**
- Output: `androidApp/build/outputs/bundle/release/androidApp-release.aab`

- [ ] **Step 1: Ensure release signing key is set in `~/.gradle/gradle.properties`**

Verify `BIRDY_UPLOAD_STORE_FILE`, `BIRDY_UPLOAD_STORE_PASSWORD`, `BIRDY_UPLOAD_KEY_ALIAS`, `BIRDY_UPLOAD_KEY_PASSWORD`, and `BIRDY_PLAY_LICENSE_KEY` are set.

- [ ] **Step 2: Build signed release AAB**

```bash
./gradlew :androidApp:bundleRelease
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install via bundletool**

```bash
java -jar bundletool-all-1.18.1.jar build-apks \
    --bundle=androidApp/build/outputs/bundle/release/androidApp-release.aab \
    --output=birdy-release.apks --connected-device
java -jar bundletool-all-1.18.1.jar install-apks --apks=birdy-release.apks
```

- [ ] **Step 4: Smoke-test on device**

Cold start → no `PremiumBillingClient` init crash; navigate Archive→Lifelist→Badges; no R8/TFLite crash in logcat.

- [ ] **Step 5: Commit smoke-test log**

```bash
git add docs/superpowers/runbooks/2026-05-22-v0.9.0c-device-verify.md
git commit -m "test(plan-6b3/t22): signed release-AAB v1.0.0-rc3 smoke-test PASS"
```

---

### Task 23: Tag `v0.9.0c-premium-content`

- [ ] **Step 1: Final lint**

Run: `./gradlew ktlintCheck detekt :composeApp:testDebugUnitTest :shared:domain:jvmTest :shared:data:jvmTest :shared:pdf:testDebugUnitTest`
Expected: ALL GREEN.

- [ ] **Step 2: Tag**

```bash
git tag -a v0.9.0c-premium-content -m "Plan 6b3: PDF Field Journal-export + Season Statistics + 10 Premium fält-märken"
git push origin main --tags
```

- [ ] **Step 3: Update `CLAUDE.md`**

Move 6b3 row to ✅ in plan-of-plans table; add status paragraph mirroring 6b2.

- [ ] **Step 4: Commit CLAUDE.md update**

```bash
git add CLAUDE.md
git commit -m "docs(plan-6b3): tag v0.9.0c-premium-content + mark plan complete in CLAUDE.md"
git push origin main
```

---

### Task 24: Bump to `1.0.0` + tag `v1.0.0`

**Files:**
- Modify: `androidApp/build.gradle.kts`

- [ ] **Step 1: Final versionName bump**

```kotlin
defaultConfig {
    versionCode = 112
    versionName = "1.0.0"
}
```

- [ ] **Step 2: Build + smoke-test signed AAB v1.0.0**

Run release-bundle build and install via bundletool, as in T22.

- [ ] **Step 3: Tag**

```bash
git add androidApp/build.gradle.kts
git commit -m "release(v1.0.0): bump versionName to 1.0.0"
git tag -a v1.0.0 -m "Birdy Bird Scanner v1.0 — ready for Internal Testing"
git push origin main --tags
```

- [ ] **Step 4: Update CLAUDE.md status — replace "v0.9.0c" with "v1.0.0" in headline status**

```bash
git add CLAUDE.md
git commit -m "docs(v1.0.0): mark v1.0.0 tagged + ready for Internal Testing"
git push origin main
```

---

### Task 25: Hand-off / Internal Testing prep

**Files:**
- Create: `docs/superpowers/runbooks/2026-05-22-v1.0.0-internal-testing.md`

- [ ] **Step 1: Document Internal Testing upload procedure**

Capture step-by-step:
1. Go to Play Console → Birdy → Internal Testing → Create new release
2. Upload `androidApp/build/outputs/bundle/release/androidApp-release.aab`
3. Set release name: `1.0.0 (112)`
4. Add release notes (copy from spec §15 or write fresh)
5. Save → Review release → Roll out to Internal Testing
6. Add license testers (your own email + colleagues)
7. Verify Billing v8 IPC runtime by purchasing YEARLY + LIFETIME + testing Restore Purchases

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/runbooks/2026-05-22-v1.0.0-internal-testing.md
git commit -m "docs(v1.0.0): internal-testing hand-off runbook"
git push origin main
```

---

## Self-Review

**1. Spec coverage:**
- §3 PDF design → T1, T2, T5, T6, T7, T8 ✅
- §4 Stats design → T9, T10, T11, T12 ✅
- §5 10 badges + manual rule → T3, T4, T13, T14, T15, T16, T17, T18, T19 ✅
- §6 Integration & gating (`effectivePremiumActive`, listener, queue) → T4, T18, T19 ✅
- §7 Test strategy → tests live in every task ✅
- §8 Audit overlap (B1/B3/B6) → T20 ✅
- §9 Acceptance criteria → T21 ✅
- §10 Risks (PDF font licensing, manual unlock idempotency, PremiumState observer scope) → addressed in T3 (Mutex + INSERT OR IGNORE), T4 (distinctUntilChanged().filter), T5 (SIL OFL fonts) ✅
- §11 iOS stub → T2 step 4 ✅
- §12 Tag sequence → T23, T24 ✅
- §15 Out-of-scope (CSV export, custom badges, leaderboards) — not touched ✅

**2. Placeholder scan:**
- T17 step 2 references "existing PremiumBadgesRow visuals" — reuse of existing code, not a placeholder for new logic.
- T13/T14/T15 reference `seasonOf` — already exists per `BadgeRule.kt` (imported in RecalculateBadgesUseCase).
- T19 step 1 — `effectivePremiumActive` mentions reconciliation with existing AppGraph wiring; verify Plan 7e left this on AppGraph and either reuse or expose. Concrete code shown.
- T6 step 2 uses `Locale` from `se.birdy.content` (confirmed via existing imports). `BadgeCatalog.badgeOrNull(...)` defined in T13.

**3. Type consistency:**
- `JournalPdfRenderer` — same name throughout T1-T8.
- `JournalPdfRenderResult` (sealed) — `Success/Empty/Failed` consistent.
- `JournalExportResult` — `Success/NothingToExport/Failed` consistent.
- `unlockManualBadge(badgeId: String, unlockedAt: Instant): Boolean` consistent in T3, T4.
- `PremiumActivationListener.start(scope: CoroutineScope): Job` consistent T4, T19.
- `BadgeCatalog.badgeOrNull(id)` defined T13, used T6.
- `Badge.isPremium` defined T13, used T6 and T17 (`filter { it.isPremium }`).
- `effectivePremiumActive: StateFlow<Boolean>` consistent T18, T19.
- `ExportJournalUseCase.run()` consistent T6, T7.

Plan complete and self-reviewed.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-21-v1-06b3-premium-content.md`. Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, two-stage review (spec + quality) between tasks, optimal for a 25-task multi-phase sprint.
2. **Inline Execution** — Execute tasks in this session with checkpoints. Faster but burns more context for the bigger plan.

Which approach?
