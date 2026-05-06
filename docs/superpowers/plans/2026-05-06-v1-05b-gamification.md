# Plan 5b — Gamification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lägg ett gamification-skikt ovanpå Plan 5a:s diary: ~25 märken via YAML-katalog, två parallella streak-spår (vecka + månad), `RecalculateBadgesUseCase` (pure) som körs vid Save, `UnlockBottomSheet` med subtil koppar-glöd, `BadgesScreen` med hero + carousel + silhouett-grid, tyst app-start-backfill vid katalog-version-bump. Tag `v0.5.0b-gamification`.

**Architecture:** Ny `badge_unlock`-tabell i samma `BirdyData`-DB som Plan 5a:s `observation`-tabell. Domän-typer + `BadgeRepository`-interface + `BadgeCatalog`-typer i `shared/domain`. SQLDelight-impl i `shared/data`. YAML-katalog i `composeApp/composeResources/files/badges.yaml` laddas runtime via kaml + `Res.readBytes`. `RecalculateBadgesUseCase` i `composeApp/badges/` (avsteg från spec — se Avvikelse 1). Save-flow utökas: `SaveObservationUseCase.save()` returnerar nu `SaveResult(observationId, newUnlocks)`. `ClassificationResultViewModel` enqueue:ar nya unlocks i `UnlockQueue` → bottom-sheets pop:ar i sekvens. `BadgesScreen` kombinerar `obsRepo.observeAll() + badgeRepo.observeUnlocks() + speciesRepo.observeTotalCount()` via `combine()` + `stateIn`.

**Tech Stack:** Kotlin Multiplatform 2.1.20 · Compose Multiplatform 1.7.3 · SQLDelight 2.0.2 · sqldelight-coroutines 2.0.2 · kotlinx.datetime 0.6.1 · kotlinx.coroutines 1.9.0 · kaml 0.65.0 (utökas till commonMain) · Turbine 1.1.0 · AndroidX Lifecycle 2.8.4.

**Spec:** `docs/superpowers/specs/2026-05-06-plan-5b-gamification-design.md` (commit `7714523`).

---

## Avvikelse 1 — `RecalculateBadgesUseCase` flyttar från `shared/domain` till `composeApp/badges/`

Specen (§3) placerar `RecalculateBadgesUseCase` i `shared/domain/.../badge/`. Men evaluator-funktionen behöver `Species.family` och `Species.abundance` från `se.birdy.content.Species` — och `shared/domain` har idag inget beroende på `shared/content` (samma mönster som Plan 5a:s `Observation.speciesId: String` istället för `SpeciesId`). Att lägga till `shared/domain → shared/content` skulle invertera modul-grafens nuvarande riktning där bara `shared/data` och `composeApp` aggregerar båda.

Lösning: `RecalculateBadgesUseCase` flyttar till `composeApp/src/commonMain/kotlin/se/birdy/app/badges/` där både `shared/domain` (för `Observation`, `BadgeRule`) och `shared/content` (för `Species`, `Abundance`) redan är tillgängliga. `Badge`, `BadgeRule`, `BadgeUnlock`, `BadgeProgress`, `BadgeCatalog` (typer + sortering), `BadgeCategory`, `BadgeSeason`, `BadgeAbundance`, `BadgeRepository`-interface och streak-helpers stannar i `shared/domain` (rena typer + pure functions utan content-beroende).

Konsekvens: `BadgesViewModel`, `BadgeBackfillOnAppStart` och `SaveObservationUseCase` (alla i `composeApp`) injicerar `RecalculateBadgesUseCase` direkt — inget delat-modul-beroende.

## Avvikelse 2 — `BadgeCatalogLoader` flyttar från `shared/content` till `composeApp/badges/`

Specen (§3) placerar `BadgeCatalogLoader` i `shared/content/.../badges/`. Men runtime-YAML-parsning kräver `Res.readBytes("files/badges.yaml")` (compose-multiplatform-resources) som bara är tillgänglig i `composeApp` — `shared/content` har inte compose-resources på dependency-grafen. Och kaml 0.65.0 lever idag bara i `shared/content/jvmMain` (build-time-pipeline för species-DB), inte i commonMain.

Lösning: `badges.yaml` läggs i `composeApp/src/commonMain/composeResources/files/badges.yaml`. `BadgeCatalogLoader` läggs i `composeApp/src/commonMain/kotlin/se/birdy/app/badges/`. kaml 0.65.0 utökas till `composeApp/commonMain.dependencies` (kaml stödjer KMP-common sedan 0.55+). `BadgeCatalog`-typen själv (data class + `findById`) lever i `shared/domain` så `BadgeRepository` och `RecalculateBadgesUseCase` kan ta in den utan circular dep.

Konsekvens: `validateBadgesYaml`-gradle-task (Task 14) körs på `composeApp/composeResources/files/badges.yaml`, inte `shared/content/...`. Mönstret matchar `shared/content`s existerande `validateSpeciesData` JavaExec-task.

## Avvikelse 3 — `BadgeVersionStore` ligger i `composeApp` med `expect/actual`, inte `shared/data`

Specen (§7.2) säger `expect class i common, actual med vanlig SharedPreferences på Android`. Vi väljer `composeApp` snarare än `shared/data` eftersom (a) `BadgeBackfillOnAppStart` också ligger i `composeApp` och (b) `shared/data` idag bara har SQLDelight-driver-`expect/actual`, inte SharedPreferences. Plan 4a:s `expect`-mönster för Android-only Composables matchar.

---

## File Structure

### Skapas

| Fil | Ansvar |
|---|---|
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/Badge.kt` | Data class — domän-modell |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeCategory.kt` | Enum med `order: Int` för UI-sortering |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeSeason.kt` | Enum WINTER/SPRING/SUMMER/AUTUMN |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeAbundance.kt` | Enum ALLMÄN/MINDRE_ALLMÄN/OVANLIG/SÄLLSYNT (mirror av `content.Abundance`) |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeRule.kt` | Sealed interface med 6 rule-typer |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeCatalog.kt` | Data class + `findById` |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeUnlock.kt` | Data class |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeProgress.kt` | Data class |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeRepository.kt` | Flow-baserat repository-interface |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/StreakHelpers.kt` | `weekKey`/`monthKey`/`seasonOf`/`longestConsecutive`/`longestWeeklyStreak`/`longestMonthlyStreak` (pure) |
| `shared/domain/src/jvmTest/kotlin/se/birdy/domain/badge/StreakHelpersTest.kt` | Tabell-driven test |
| `shared/data/src/commonMain/sqldelight/se/birdy/data/db/BadgeUnlock.sq` | SQLDelight-schema + queries |
| `shared/data/src/commonMain/kotlin/se/birdy/data/badge/BadgeRepositoryImpl.kt` | Wraps `BirdyDatabase.badgeUnlockQueries` |
| `shared/data/src/jvmTest/kotlin/se/birdy/data/badge/BadgeRepositoryImplTest.kt` | In-memory SQLDelight + Turbine |
| `composeApp/src/commonMain/composeResources/files/badges.yaml` | Katalog över 25 märken (version 1) |
| `composeApp/src/commonMain/kotlin/se/birdy/app/badges/BadgeCatalogLoader.kt` | `Res.readBytes` + kaml + validation |
| `composeApp/src/commonTest/kotlin/se/birdy/app/badges/BadgeCatalogLoaderTest.kt` | Parse valid + invalid YAML, unknown rule-type, saknade fields |
| `composeApp/src/commonMain/kotlin/se/birdy/app/badges/RecalculateBadgesUseCase.kt` | Pure function — `newUnlocks(...)` + `currentValue(...)` |
| `composeApp/src/commonTest/kotlin/se/birdy/app/badges/RecalculateBadgesUseCaseTest.kt` | Exhaustive över 6 rule-typer + edge cases |
| `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveResult.kt` | Data class — return-typ för `SaveObservationUseCase.save()` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/bootstrap/BadgeVersionStore.kt` | `expect class` |
| `composeApp/src/androidMain/kotlin/se/birdy/app/bootstrap/BadgeVersionStore.android.kt` | `actual` med `SharedPreferences` |
| `composeApp/src/iosMain/kotlin/se/birdy/app/bootstrap/BadgeVersionStore.ios.kt` | `actual`-skelett med `NSUserDefaults` (kompilerar, ej använd i v1) |
| `composeApp/src/jvmMain/kotlin/se/birdy/app/bootstrap/BadgeVersionStore.jvm.kt` | `actual` med in-memory `Int` för tester |
| `composeApp/src/commonMain/kotlin/se/birdy/app/bootstrap/BadgeBackfillOnAppStart.kt` | Tyst version-bump-recalc |
| `composeApp/src/commonTest/kotlin/se/birdy/app/bootstrap/BadgeBackfillOnAppStartTest.kt` | Tester över version-gating + recalc + persist |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/UnlockQueue.kt` | StateFlow-baserad queue |
| `composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/UnlockQueueTest.kt` | enqueue/pop/observe-tester |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/UnlockBottomSheet.kt` | `ModalBottomSheet` med glow-animation |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesUiState.kt` | Loading/Loaded/Error sealed interface |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt` | `combine()` över 3 Flows |
| `composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgesViewModelTest.kt` | Tester över Loading/Loaded/Error + implicit-opt-in |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt` | UI — hero + carousel + grid |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeStatHero.kt` | Mossbädd-gradient-hero |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeCard.kt` | Låst/upplåst-cell |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeRecentCard.kt` | Carousel-card |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/formatRelativeBadgeDate.kt` | "3 maj" / "May 3" helper, sv + en |
| `composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeBadgeRepository.kt` | Test-fixture, `withDefaults()` |
| `tools/badges/CheckBadgesYaml.kt` | Standalone validator för `validateBadgesYaml`-gradle-task |
| `tools/badges/CheckBadgeStrings.kt` | Standalone validator för `validateBadgeStrings`-gradle-task |
| `docs/superpowers/screenshots/2026-05-XX-05b-badges-empty.png` | Märken-flik, 0 unlocks |
| `docs/superpowers/screenshots/2026-05-XX-05b-badges-loaded.png` | Hero + 3 senaste + grid |
| `docs/superpowers/screenshots/2026-05-XX-05b-badges-streak-grown.png` | Streak-piller efter 2v |
| `docs/superpowers/screenshots/2026-05-XX-05b-unlock-bottomsheet.png` | Glow-animation, "Skådare" |
| `docs/superpowers/screenshots/2026-05-XX-05b-locked-detail.png` | Tap-on-silhouette-snackbar |
| `docs/superpowers/screenshots/2026-05-XX-05b-unlocked-detail.png` | Tap-on-upplåst-bottom-sheet |
| `docs/superpowers/screenshots/2026-05-XX-05b-save-with-unlock.png` | ResultScreen + bottom-sheet |

### Modifieras

| Fil | Ändring |
|---|---|
| `shared/data/src/commonMain/sqldelight/se/birdy/data/db/Database.sq` *(om existerar — sannolikt schema är per-fil i `db/`-katalogen)* | (Inget — ny tabell i ny `BadgeUnlock.sq`) |
| `shared/data/build.gradle.kts` | (Bara om path:ar behöver bumpas — sannolikt orörd om SQLDelight-pluginen pickar `db/*.sq` automatiskt) |
| `shared/content/src/commonMain/kotlin/se/birdy/content/SpeciesRepository.kt` | Lägg till `observeTotalCount(): Flow<Int>` + `suspend fun allByQid(): Map<SpeciesId, Species>` |
| `shared/content/src/commonMain/kotlin/se/birdy/content/SqlDelightSpeciesRepository.kt` *(eller motsv. impl)* | Implementera de två nya metoderna |
| `composeApp/build.gradle.kts` | Lägg `kaml 0.65.0` till `commonMain.dependencies`; lägg `composeResources` om saknas |
| `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt` | Returnerar `SaveResult` istället för `String`; injicerar `BadgeRepository`, `BadgeCatalog`, `RecalculateBadgesUseCase`, `SpeciesRepository` |
| `composeApp/src/commonTest/kotlin/se/birdy/app/usecase/SaveObservationUseCaseTest.kt` | Utöka med recalc-paths (lyckat 0/1/3 unlocks + recalc-fail) |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/result/ClassificationResultUiState.kt` | Lägg till `unlockQueueSize: Int` (eller hämta via separat StateFlow); CTA-disabled-flagga |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/result/ClassificationResultViewModel.kt` | Använd `SaveResult`; expose `UnlockQueue`; disable Save-CTA mid-queue |
| `composeApp/src/commonTest/kotlin/se/birdy/app/ui/result/ClassificationResultViewModelTest.kt` | Tester för enqueue + disabled-CTA |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/result/ClassificationResultScreen.kt` | Renderar `UnlockBottomSheet` ovanpå Save-snackbar; använder `unlockQueue.current` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt` | Wiring för `BadgeRepository`, `BadgeCatalogLoader`, `RecalculateBadgesUseCase`, `BadgeBackfillOnAppStart`, `BadgesViewModel` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt` | Pekar `Märken`-fliken på `BadgesScreen` istället för `BadgesStubScreen` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BadgesStubScreen.kt` | **Tas bort** |
| `composeApp/src/commonMain/kotlin/se/birdy/app/App.kt` *(eller motsv. composable rot)* | `LaunchedEffect(Unit) { backfill.runIfNeeded() }` |
| `androidApp/build.gradle.kts` | Lägg `implementation(project(":shared:domain"))` om saknas (kontrolleras i Task 1 — `shared/domain` redan transitive via Plan 5a, men nya badge-typer kan kräva direkt ref); inga andra deps |
| `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` *(eller App.kt)* | Init `BadgeVersionStore` med Context |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | Alla `badges_*`, `unlock_*`, `badge_name_*`, `badge_desc_*` (sv) |
| `composeApp/src/commonMain/composeResources/values-en/strings.xml` | Mirror EN |
| `composeApp/build.gradle.kts` | Registrera `validateBadgesYaml` + `validateBadgeStrings` JavaExec-tasks; binda till `preBuild` |
| `CLAUDE.md` | Status-rad till `v0.5.0b-gamification`; plan-of-plans-tabell; "Avslutade planer (referens)"-entry |

---

## Task 1: Domän-typer + `BadgeRepository`-interface

**Files:**
- Create: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/Badge.kt`
- Create: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeCategory.kt`
- Create: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeSeason.kt`
- Create: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeAbundance.kt`
- Create: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeRule.kt`
- Create: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeCatalog.kt`
- Create: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeUnlock.kt`
- Create: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeProgress.kt`
- Create: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/BadgeRepository.kt`
- Test: `shared/domain/src/jvmTest/kotlin/se/birdy/domain/badge/BadgeProgressTest.kt`

- [ ] **Step 1: Verifiera nuvarande `shared/domain/build.gradle.kts`**

Run: `cat shared/domain/build.gradle.kts`
Expected: kotlinx.datetime + kotlinx.coroutines.core finns redan i `commonMain.dependencies` (lades till i Plan 5a Task 1).

Om saknas — lägg till:

```kotlin
commonMain.dependencies {
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
}
```

- [ ] **Step 2: Skapa `Badge.kt`**

```kotlin
package se.birdy.domain.badge

data class Badge(
    val id: String,
    val category: BadgeCategory,
    val rule: BadgeRule,
)
```

- [ ] **Step 3: Skapa `BadgeCategory.kt`**

```kotlin
package se.birdy.domain.badge

enum class BadgeCategory(val order: Int) {
    PROGRESSION(0),
    STREAK_WEEKLY(1),
    STREAK_MONTHLY(2),
    SEASON(3),
    FAMILY(4),
    RARE(5),
}
```

- [ ] **Step 4: Skapa `BadgeSeason.kt`**

```kotlin
package se.birdy.domain.badge

enum class BadgeSeason {
    WINTER,
    SPRING,
    SUMMER,
    AUTUMN,
}
```

- [ ] **Step 5: Skapa `BadgeAbundance.kt`**

```kotlin
package se.birdy.domain.badge

/**
 * Mirror av se.birdy.content.Abundance. shared/domain har medvetet
 * inget beroende på shared/content; mappning sker i composeApp/badges/
 * RecalculateBadgesUseCase vid evaluator-anropet.
 */
enum class BadgeAbundance {
    ALLMÄN,
    MINDRE_ALLMÄN,
    OVANLIG,
    SÄLLSYNT,
}
```

- [ ] **Step 6: Skapa `BadgeRule.kt`**

```kotlin
package se.birdy.domain.badge

sealed interface BadgeRule {
    val target: Int

    data class CountUniqueSpecies(override val target: Int) : BadgeRule
    data class WeeklyStreak(override val target: Int) : BadgeRule
    data class MonthlyStreak(override val target: Int) : BadgeRule
    data class ObservedInSeason(val season: BadgeSeason, override val target: Int) : BadgeRule
    data class ObservedInFamily(val family: String, override val target: Int) : BadgeRule
    data class ObservedWithAbundance(val abundance: BadgeAbundance, override val target: Int) : BadgeRule
}
```

- [ ] **Step 7: Skapa `BadgeCatalog.kt`**

```kotlin
package se.birdy.domain.badge

data class BadgeCatalog(
    val version: Int,
    val badges: List<Badge>,
) {
    private val byId: Map<String, Badge> = badges.associateBy { it.id }
    fun findById(id: String): Badge? = byId[id]
}
```

- [ ] **Step 8: Skapa `BadgeUnlock.kt`**

```kotlin
package se.birdy.domain.badge

import kotlinx.datetime.Instant

data class BadgeUnlock(
    val badgeId: String,
    val unlockedAt: Instant,
)
```

- [ ] **Step 9: Skapa `BadgeProgress.kt`**

```kotlin
package se.birdy.domain.badge

data class BadgeProgress(
    val badge: Badge,
    val current: Int,
    val target: Int,
    val unlock: BadgeUnlock?,
) {
    val isUnlocked: Boolean get() = unlock != null
    val progressFraction: Float get() = (current.toFloat() / target).coerceAtMost(1f)
}
```

- [ ] **Step 10: Skapa `BadgeRepository.kt`**

```kotlin
package se.birdy.domain.badge

import kotlinx.coroutines.flow.Flow

interface BadgeRepository {
    fun observeUnlocks(): Flow<List<BadgeUnlock>>
    suspend fun persist(unlocks: List<BadgeUnlock>)
    suspend fun deleteAll()
}
```

- [ ] **Step 11: Skapa `BadgeProgressTest.kt`**

```kotlin
package se.birdy.domain.badge

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BadgeProgressTest {
    private val sampleBadge = Badge(
        id = "novice",
        category = BadgeCategory.PROGRESSION,
        rule = BadgeRule.CountUniqueSpecies(target = 5),
    )

    @Test
    fun `isUnlocked false when unlock is null`() {
        val p = BadgeProgress(sampleBadge, current = 2, target = 5, unlock = null)
        assertFalse(p.isUnlocked)
    }

    @Test
    fun `isUnlocked true when unlock is set`() {
        val p = BadgeProgress(
            sampleBadge,
            current = 5,
            target = 5,
            unlock = BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_700_000_000_000)),
        )
        assertTrue(p.isUnlocked)
    }

    @Test
    fun `progressFraction half when current is half of target`() {
        val p = BadgeProgress(sampleBadge, current = 2, target = 4, unlock = null)
        assertEquals(0.5f, p.progressFraction)
    }

    @Test
    fun `progressFraction caps at 1 when current exceeds target`() {
        val p = BadgeProgress(sampleBadge, current = 10, target = 5, unlock = null)
        assertEquals(1f, p.progressFraction)
    }
}
```

- [ ] **Step 12: Kör testet**

Run:
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :shared:domain:jvmTest
```
Expected: `BadgeProgressTest > 4 tests passed`. Övriga Plan 5a-tester förblir gröna.

- [ ] **Step 13: ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 14: Commit**

```bash
git add shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/ \
        shared/domain/src/jvmTest/kotlin/se/birdy/domain/badge/
git commit -m "feat(domain): Plan 5b Task 1 — badge domain types + repo interface"
```

---

## Task 2: SQLDelight `badge_unlock` + `BadgeRepositoryImpl` + tester

**Files:**
- Create: `shared/data/src/commonMain/sqldelight/se/birdy/data/db/BadgeUnlock.sq`
- Create: `shared/data/src/commonMain/kotlin/se/birdy/data/badge/BadgeRepositoryImpl.kt`
- Test: `shared/data/src/jvmTest/kotlin/se/birdy/data/badge/BadgeRepositoryImplTest.kt`
- Modify (om nödvändigt): `shared/data/build.gradle.kts`

- [ ] **Step 1: Skriv failing test för persist + observe**

```kotlin
package se.birdy.data.badge

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import se.birdy.data.BirdyData
import se.birdy.domain.badge.BadgeUnlock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BadgeRepositoryImplTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: BirdyData
    private lateinit var repo: BadgeRepositoryImpl

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BirdyData.Schema.create(driver)
        db = BirdyData(driver)
        repo = BadgeRepositoryImpl(db)
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun `persist then observe emits inserted unlock`() = runTest {
        val unlock = BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_700_000_000_000))
        repo.persist(listOf(unlock))

        repo.observeUnlocks().test {
            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals("novice", emitted[0].badgeId)
            assertEquals(unlock.unlockedAt, emitted[0].unlockedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `persist is idempotent (upsert)`() = runTest {
        val first = BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_700_000_000_000))
        val second = BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_700_000_009_999))
        repo.persist(listOf(first))
        repo.persist(listOf(second))

        repo.observeUnlocks().test {
            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals(second.unlockedAt, emitted[0].unlockedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeUnlocks sorts DESC by unlockedAt`() = runTest {
        val older = BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_700_000_000_000))
        val newer = BadgeUnlock("birder_bronze", Instant.fromEpochMilliseconds(1_700_000_999_999))
        repo.persist(listOf(older, newer))

        repo.observeUnlocks().test {
            val emitted = awaitItem()
            assertEquals("birder_bronze", emitted[0].badgeId)
            assertEquals("novice", emitted[1].badgeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAll clears table`() = runTest {
        repo.persist(listOf(BadgeUnlock("novice", Instant.fromEpochMilliseconds(1L))))
        repo.deleteAll()

        repo.observeUnlocks().test {
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Kör testet — det failar för att schemat saknas**

Run: `./gradlew :shared:data:jvmTest --tests "*BadgeRepositoryImplTest*"`
Expected: FAIL — `Cannot resolve symbol 'BadgeRepositoryImpl'` eller liknande.

- [ ] **Step 3: Skapa SQLDelight-schemat**

Skapa `shared/data/src/commonMain/sqldelight/se/birdy/data/db/BadgeUnlock.sq`:

```sql
CREATE TABLE badge_unlock (
    badge_id        TEXT NOT NULL PRIMARY KEY,
    unlocked_at_ms  INTEGER NOT NULL
);

CREATE INDEX badge_unlock_unlocked_at_idx ON badge_unlock(unlocked_at_ms DESC);

selectAll:
SELECT * FROM badge_unlock ORDER BY unlocked_at_ms DESC;

upsert:
INSERT OR REPLACE INTO badge_unlock(badge_id, unlocked_at_ms) VALUES (?, ?);

deleteAll:
DELETE FROM badge_unlock;
```

- [ ] **Step 4: Skapa `BadgeRepositoryImpl.kt`**

```kotlin
package se.birdy.data.badge

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import se.birdy.data.BirdyData
import se.birdy.data.db.BadgeUnlock as DbBadgeUnlock
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeUnlock

class BadgeRepositoryImpl(
    private val db: BirdyData,
) : BadgeRepository {

    override fun observeUnlocks(): Flow<List<BadgeUnlock>> =
        db.badgeUnlockQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun persist(unlocks: List<BadgeUnlock>) {
        if (unlocks.isEmpty()) return
        db.transaction {
            unlocks.forEach { u ->
                db.badgeUnlockQueries.upsert(
                    badge_id = u.badgeId,
                    unlocked_at_ms = u.unlockedAt.toEpochMilliseconds(),
                )
            }
        }
    }

    override suspend fun deleteAll() {
        db.badgeUnlockQueries.deleteAll()
    }

    private fun toDomain(row: DbBadgeUnlock): BadgeUnlock = BadgeUnlock(
        badgeId = row.badge_id,
        unlockedAt = Instant.fromEpochMilliseconds(row.unlocked_at_ms),
    )
}
```

- [ ] **Step 5: Verifiera SQLDelight-genererings-paket**

Run: `./gradlew :shared:data:generateCommonMainBirdyDataInterface`
Expected: BUILD SUCCESSFUL. Verifiera att `build/generated/sqldelight/code/BirdyData/.../db/BadgeUnlock.kt` finns.

Om importen `se.birdy.data.db.BadgeUnlock` inte matchar den genererade paket-strukturen — uppdatera importen i `BadgeRepositoryImpl.kt` baserat på vad som genererats (kontrollera namespace i existerande Plan 5a-fil `Observation.sq`).

- [ ] **Step 6: Kör testet — det ska passa nu**

Run: `./gradlew :shared:data:jvmTest --tests "*BadgeRepositoryImplTest*"`
Expected: PASS — alla 4 tests gröna.

- [ ] **Step 7: Kör hela `:shared:data:jvmTest`-suite**

Run: `./gradlew :shared:data:jvmTest`
Expected: Plan 5a:s `ObservationRepositoryImplTest` förblir grön + nya badge-tester gröna.

- [ ] **Step 8: ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add shared/data/src/commonMain/sqldelight/se/birdy/data/db/BadgeUnlock.sq \
        shared/data/src/commonMain/kotlin/se/birdy/data/badge/ \
        shared/data/src/jvmTest/kotlin/se/birdy/data/badge/
git commit -m "feat(data): Plan 5b Task 2 — badge_unlock table + repository impl"
```

---

## Task 3: `SpeciesRepository`-utökningar — `observeTotalCount()` + `allByQid()`

`BadgesViewModel` behöver totalantal-arter för "X / 700"-progress. `SaveObservationUseCase` och `BadgeBackfillOnAppStart` behöver `Map<SpeciesId, Species>` för rule-evaluering. Båda metoderna utökas på existing `SpeciesRepository`-interface i `shared/content`.

**Files:**
- Modify: `shared/content/src/commonMain/kotlin/se/birdy/content/SpeciesRepository.kt`
- Modify: `shared/content/src/commonMain/kotlin/se/birdy/content/SqlDelightSpeciesRepository.kt` *(eller motsv. impl-fil — verifiera namnet via Grep)*
- Modify: `shared/content/src/jvmTest/kotlin/se/birdy/content/SqlDelightSpeciesRepositoryTest.kt` *(om existerar — annars test sker via `BadgesViewModelTest`)*

- [ ] **Step 1: Hitta nuvarande `SpeciesRepository`-impl-filen**

Run: Grep `SqlDelight.*SpeciesRepository|class.*: SpeciesRepository` i `shared/content/src`.
Expected: En impl-fil hittas (sannolikt `SqlDelightSpeciesRepository.kt`).

- [ ] **Step 2: Skriv failing test för `observeTotalCount`**

Skapa eller utöka `SqlDelightSpeciesRepositoryTest.kt`:

```kotlin
@Test
fun `observeTotalCount returns species count`() = runTest {
    val repo = createRepoWithSpecies(/* 5 species in fixture */)
    repo.observeTotalCount().test {
        assertEquals(5, awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `allByQid returns map keyed by SpeciesId`() = runTest {
    val repo = createRepoWithSpecies()
    val map = repo.allByQid()
    assertEquals(5, map.size)
    assertEquals("Talgoxe", map[SpeciesId("Q25612")]?.commonName?.value)
}
```

(Om impl-tester saknas i nuvarande shared/content — hoppa över Step 2 och förlita dig på Task 11 `BadgesViewModelTest` för indirekt täckning.)

- [ ] **Step 3: Lägg till metoderna i `SpeciesRepository`-interface**

```kotlin
interface SpeciesRepository {
    fun getById(id: SpeciesId, locale: Locale): Flow<Species?>
    fun search(query: String, locale: Locale, filters: SpeciesFilter = SpeciesFilter()): Flow<List<SpeciesSummary>>
    fun listByFamily(familyKey: String, locale: Locale): Flow<List<SpeciesSummary>>
    fun all(locale: Locale): Flow<List<SpeciesSummary>>

    /** Antal arter i katalogen (oberoende av locale). Används av Badges-fliken. */
    fun observeTotalCount(): Flow<Int>

    /**
     * Engångs-snapshot av hela katalogen för rule-engine-evaluering.
     * Locale påverkar inte resultaten — `Species.family` och `Species.abundance` är språkneutrala.
     */
    suspend fun allByQid(): Map<SpeciesId, Species>
}
```

- [ ] **Step 4: Implementera i `SqlDelightSpeciesRepository`**

Lägg till SQLDelight-query i species-schemat (verifiera filnamn — sannolikt `Species.sq` eller `Content.sq`):

```sql
selectCount:
SELECT COUNT(*) FROM species;
```

Implementera:

```kotlin
override fun observeTotalCount(): Flow<Int> =
    queries.selectCount()
        .asFlow()
        .mapToOne(Dispatchers.Default)
        .map { it.toInt() }

override suspend fun allByQid(): Map<SpeciesId, Species> = withContext(Dispatchers.Default) {
    all(Locale.SV)  // species är locale-neutral i `family` + `abundance`
        .first()
        .associateBy({ SpeciesId(it.id.raw) }) { /* hydrate full Species via getById eller direkt query */ }
}
```

OBS: Om `all(...)` returnerar `SpeciesSummary` (lättviktig) men `allByQid` behöver full `Species` (med family + abundance) — gör direkt query med all kolumner inkluderade. Se existing `getById`-impl för mönster, men returnera batch.

- [ ] **Step 5: Kör testet**

Run: `./gradlew :shared:content:jvmTest`
Expected: PASS (om Step 2 lades till) eller fortsatt grönt (om Step 2 hoppades).

- [ ] **Step 6: ktlint + detekt + smoke-test downstream**

Run:
```bash
./gradlew ktlintCheck detekt
./gradlew :shared:content:compileKotlinJvm :shared:data:compileKotlinJvm :composeApp:compileKotlinJvm
```
Expected: BUILD SUCCESSFUL — interface-utökning bryter inte downstream.

- [ ] **Step 7: Commit**

```bash
git add shared/content/src/commonMain/kotlin/se/birdy/content/SpeciesRepository.kt \
        shared/content/src/commonMain/kotlin/se/birdy/content/ \
        shared/content/src/commonMain/sqldelight/
git commit -m "feat(content): Plan 5b Task 3 — observeTotalCount + allByQid for badges"
```

---

## Task 4: Streak/Season-helpers + `StreakHelpersTest`

Pure functions för rule-evaluering. Lever i `shared/domain` (inget content-beroende). ISO 8601 vecka (måndag-baserad), meteorologisk säsong (SMHI). Cross-year boundary (v53/2026 + v01/2027) hanteras av `WeekFields.ISO`.

**Files:**
- Create: `shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/StreakHelpers.kt`
- Test: `shared/domain/src/jvmTest/kotlin/se/birdy/domain/badge/StreakHelpersTest.kt`

> **OBS:** `WeekFields.ISO`-API:t är JVM-only (`java.time.temporal.WeekFields`). För KMP common: använd `kotlinx.datetime.LocalDate` + manuell ISO-vecka-beräkning. Implementationen nedan är ren `kotlinx.datetime` (inget JVM-beroende), så fungerar i common.

- [ ] **Step 1: Skriv `StreakHelpers.kt` med ISO-vecka-beräkning i ren `kotlinx.datetime`**

```kotlin
package se.birdy.domain.badge

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * ISO 8601-veckonyckel (måndag-baserad). v01 = veckan som innehåller årets första torsdag.
 * `next()` hanterar år-skifte (v53→v01-cross).
 */
data class WeekKey(val isoYear: Int, val isoWeek: Int) : Comparable<WeekKey> {
    override fun compareTo(other: WeekKey): Int =
        compareValuesBy(this, other, { it.isoYear }, { it.isoWeek })

    fun next(): WeekKey {
        val maxWeek = isoWeeksInYear(isoYear)
        return if (isoWeek >= maxWeek) WeekKey(isoYear + 1, 1)
        else WeekKey(isoYear, isoWeek + 1)
    }
}

data class MonthKey(val year: Int, val month: Int) : Comparable<MonthKey> {
    override fun compareTo(other: MonthKey): Int =
        compareValuesBy(this, other, { it.year }, { it.month })

    fun next(): MonthKey =
        if (month >= 12) MonthKey(year + 1, 1)
        else MonthKey(year, month + 1)
}

fun weekKey(instant: Instant, zone: TimeZone): WeekKey {
    val date = instant.toLocalDateTime(zone).date
    return weekKey(date)
}

internal fun weekKey(date: LocalDate): WeekKey {
    // ISO 8601: måndag = veckans start. Veckan tilldelas det år dess torsdag faller i.
    val weekStart = date.weekStartMonday()
    val thursday = weekStart.plusDays(3)
    val isoYear = thursday.year
    // Hitta första måndag i ISO-året (måndag i vecka 1, dvs. månd. före/på 4 jan).
    val jan4 = LocalDate(isoYear, 1, 4)
    val firstWeekStart = jan4.weekStartMonday()
    val daysFromFirst = firstWeekStart.daysUntil(weekStart)
    val isoWeek = (daysFromFirst / 7) + 1
    return WeekKey(isoYear, isoWeek)
}

fun monthKey(instant: Instant, zone: TimeZone): MonthKey {
    val ldt = instant.toLocalDateTime(zone)
    return MonthKey(ldt.year, ldt.monthNumber)
}

fun seasonOf(instant: Instant, zone: TimeZone): BadgeSeason {
    val month = instant.toLocalDateTime(zone).monthNumber
    return when (month) {
        12, 1, 2 -> BadgeSeason.WINTER
        3, 4, 5 -> BadgeSeason.SPRING
        6, 7, 8 -> BadgeSeason.SUMMER
        9, 10, 11 -> BadgeSeason.AUTUMN
        else -> error("unreachable month=$month")
    }
}

/**
 * Längsta consecutive-kedja i en sorted ascending sekvens. T är komparabel
 * och har en `next: (T) -> T`-funktion som genererar nästa förväntade nyckel.
 */
fun <T : Comparable<T>> longestConsecutive(sorted: List<T>, next: (T) -> T): Int {
    if (sorted.isEmpty()) return 0
    var longest = 1
    var current = 1
    for (i in 1 until sorted.size) {
        current = if (sorted[i] == next(sorted[i - 1])) current + 1 else 1
        if (current > longest) longest = current
    }
    return longest
}

fun longestWeeklyStreak(instants: List<Instant>, zone: TimeZone): Int {
    val keys = instants.map { weekKey(it, zone) }.toSortedSet().toList()
    return longestConsecutive(keys) { it.next() }
}

fun longestMonthlyStreak(instants: List<Instant>, zone: TimeZone): Int {
    val keys = instants.map { monthKey(it, zone) }.toSortedSet().toList()
    return longestConsecutive(keys) { it.next() }
}

// ===== Internal helpers =====

internal fun LocalDate.weekStartMonday(): LocalDate {
    // DayOfWeek: MONDAY=1..SUNDAY=7 i kotlinx.datetime
    val dow = dayOfWeek.isoDayNumber
    return plusDays(-(dow - 1))
}

internal fun LocalDate.plusDays(days: Int): LocalDate {
    val epoch = LocalDate(1970, 1, 1)
    val daysSinceEpoch = epoch.daysUntil(this) + days
    return epoch.plusDaysAbsolute(daysSinceEpoch)
}

internal fun LocalDate.plusDaysAbsolute(daysSinceEpoch: Int): LocalDate {
    // Använd kotlinx.datetime's inbyggda Date-aritmetik via period.
    return LocalDate.fromEpochDays(daysSinceEpoch)
}

internal val DayOfWeek.isoDayNumber: Int
    get() = when (this) {
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
        DayOfWeek.SUNDAY -> 7
        else -> error("unknown day=$this")
    }

/**
 * Antal ISO-veckor i ett år: 52 normalt, 53 när 1 jan är torsdag, eller om skottår med 1 jan onsdag.
 */
internal fun isoWeeksInYear(year: Int): Int {
    val jan1 = LocalDate(year, 1, 1).dayOfWeek
    val isLeap = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    val long = jan1 == DayOfWeek.THURSDAY || (isLeap && jan1 == DayOfWeek.WEDNESDAY)
    return if (long) 53 else 52
}
```

OBS: `LocalDate.plusDays`/`fromEpochDays` finns i kotlinx-datetime 0.6.1 — verifiera via Read av nuvarande `formatRelativeDate.kt` (Plan 5a) som använder samma API.

- [ ] **Step 2: Skriv `StreakHelpersTest.kt`**

```kotlin
package se.birdy.domain.badge

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakHelpersTest {
    private val utc = TimeZone.UTC

    private fun instant(y: Int, m: Int, d: Int): Instant =
        LocalDateTime(y, m, d, 12, 0).toInstant(utc)

    @Test
    fun `weekKey for thursday returns iso year and week`() {
        // 2026-05-07 är torsdag, vecka 19
        assertEquals(WeekKey(2026, 19), weekKey(instant(2026, 5, 7), utc))
    }

    @Test
    fun `weekKey crosses year — 2026-12-29 is iso week 53 of 2026 not week 1 of 2027`() {
        // 2026-12-29 är tisdag, ISO-vecka 53 (2026 har 53 veckor eftersom 1 jan 2026 är torsdag)
        val key = weekKey(instant(2026, 12, 29), utc)
        assertEquals(2026, key.isoYear)
        assertEquals(53, key.isoWeek)
    }

    @Test
    fun `weekKey for jan 1 2027 (friday) is iso week 53 of 2026`() {
        // 2027-01-01 är fredag, men ISO-veckan tilldelas det år som torsdagen i den veckan ligger i.
        // Veckan 2026-12-28 → 2027-01-03 har torsdag = 2026-12-31, så det är ISO 2026/v53.
        val key = weekKey(instant(2027, 1, 1), utc)
        assertEquals(2026, key.isoYear)
        assertEquals(53, key.isoWeek)
    }

    @Test
    fun `weekKey next handles year-wrap`() {
        val k53 = WeekKey(2026, 53)
        assertEquals(WeekKey(2027, 1), k53.next())
    }

    @Test
    fun `monthKey simple`() {
        assertEquals(MonthKey(2026, 5), monthKey(instant(2026, 5, 7), utc))
    }

    @Test
    fun `monthKey next wraps year`() {
        assertEquals(MonthKey(2027, 1), MonthKey(2026, 12).next())
    }

    @Test
    fun `seasonOf meteorological — march is spring`() {
        assertEquals(BadgeSeason.SPRING, seasonOf(instant(2026, 3, 1), utc))
    }

    @Test
    fun `seasonOf meteorological — december is winter`() {
        assertEquals(BadgeSeason.WINTER, seasonOf(instant(2026, 12, 1), utc))
    }

    @Test
    fun `seasonOf — all 12 months tabular`() {
        val expected = mapOf(
            1 to BadgeSeason.WINTER,
            2 to BadgeSeason.WINTER,
            3 to BadgeSeason.SPRING,
            4 to BadgeSeason.SPRING,
            5 to BadgeSeason.SPRING,
            6 to BadgeSeason.SUMMER,
            7 to BadgeSeason.SUMMER,
            8 to BadgeSeason.SUMMER,
            9 to BadgeSeason.AUTUMN,
            10 to BadgeSeason.AUTUMN,
            11 to BadgeSeason.AUTUMN,
            12 to BadgeSeason.WINTER,
        )
        for ((month, season) in expected) {
            assertEquals(season, seasonOf(instant(2026, month, 15), utc), "month=$month")
        }
    }

    @Test
    fun `longestWeeklyStreak — empty list returns 0`() {
        assertEquals(0, longestWeeklyStreak(emptyList(), utc))
    }

    @Test
    fun `longestWeeklyStreak — single observation returns 1`() {
        assertEquals(1, longestWeeklyStreak(listOf(instant(2026, 5, 7)), utc))
    }

    @Test
    fun `longestWeeklyStreak — same week twice counts as 1`() {
        // Båda 2026-05-04 (mån) och 2026-05-07 (tor) är v19/2026
        val obs = listOf(instant(2026, 5, 4), instant(2026, 5, 7))
        assertEquals(1, longestWeeklyStreak(obs, utc))
    }

    @Test
    fun `longestWeeklyStreak — three consecutive weeks returns 3`() {
        val obs = listOf(
            instant(2026, 5, 4),  // v19
            instant(2026, 5, 11), // v20
            instant(2026, 5, 18), // v21
        )
        assertEquals(3, longestWeeklyStreak(obs, utc))
    }

    @Test
    fun `longestWeeklyStreak — gap breaks streak`() {
        val obs = listOf(
            instant(2026, 5, 4),  // v19
            instant(2026, 5, 11), // v20
            // hoppa över v21
            instant(2026, 5, 25), // v22
            instant(2026, 6, 1),  // v23
        )
        assertEquals(2, longestWeeklyStreak(obs, utc))
    }

    @Test
    fun `longestWeeklyStreak — cross-year v53 to v01`() {
        val obs = listOf(
            instant(2026, 12, 22), // v52/2026
            instant(2026, 12, 29), // v53/2026
            instant(2027, 1, 5),   // v01/2027
        )
        assertEquals(3, longestWeeklyStreak(obs, utc))
    }

    @Test
    fun `longestMonthlyStreak — three consecutive months`() {
        val obs = listOf(
            instant(2026, 3, 15),
            instant(2026, 4, 15),
            instant(2026, 5, 15),
        )
        assertEquals(3, longestMonthlyStreak(obs, utc))
    }

    @Test
    fun `longestMonthlyStreak — cross-year`() {
        val obs = listOf(
            instant(2026, 11, 15),
            instant(2026, 12, 15),
            instant(2027, 1, 15),
        )
        assertEquals(3, longestMonthlyStreak(obs, utc))
    }

    @Test
    fun `longestConsecutive — generic helper handles empty`() {
        assertEquals(0, longestConsecutive(emptyList<Int>()) { it + 1 })
    }

    @Test
    fun `isoWeeksInYear — 2026 has 53 weeks`() {
        // 1 jan 2026 = torsdag → 53 ISO-veckor
        assertEquals(53, isoWeeksInYear(2026))
    }

    @Test
    fun `isoWeeksInYear — 2025 has 52 weeks`() {
        assertEquals(52, isoWeeksInYear(2025))
    }
}
```

- [ ] **Step 3: Kör testerna**

Run: `./gradlew :shared:domain:jvmTest --tests "*StreakHelpersTest*"`
Expected: PASS — alla tests gröna. Om någon test failar (sannolikt isoWeeksInYear-tabellen), verifiera mot kalender:
- 2025: 1 jan 2025 = onsdag, 2024 är skottår men 1 jan 2025 ≠ onsdag *i skottåret* → 52 veckor (verifiera).
- 2026: 1 jan 2026 = torsdag → 53 veckor.

Justera `isoWeeksInYear`-implementationen om kalendarisk verifiering avslöjar bug (regelverket: 53 veckor IFF 1 jan är torsdag, ELLER skottår med 1 jan onsdag).

- [ ] **Step 4: Kör hela `:shared:domain:jvmTest`-suite**

Run: `./gradlew :shared:domain:jvmTest`
Expected: Plan 5a-tester förblir gröna + nya StreakHelpersTest gröna.

- [ ] **Step 5: ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/StreakHelpers.kt \
        shared/domain/src/jvmTest/kotlin/se/birdy/domain/badge/StreakHelpersTest.kt
git commit -m "feat(domain): Plan 5b Task 4 — streak helpers (ISO weeks, months, seasons)"
```

---

## Task 5: `BadgeCatalogLoader` + `badges.yaml` + 25 märken + tester

Lägger kaml till `composeApp/commonMain.dependencies`. YAML laddas via `Res.readBytes("files/badges.yaml")`. Kataloget validerar vid load (kastar `BadgeCatalogException` vid malformed/unknown rule-type).

**Files:**
- Modify: `composeApp/build.gradle.kts`
- Create: `composeApp/src/commonMain/composeResources/files/badges.yaml`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/badges/BadgeCatalogLoader.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/badges/BadgeCatalogLoaderTest.kt`

- [ ] **Step 1: Verifiera kaml-version i `gradle/libs.versions.toml`**

Run: Grep `kaml` i `gradle/libs.versions.toml`.
Expected: `kaml = "0.65.0"` redan finns (från Plan 2a). Om saknas — lägg till:

```toml
[versions]
kaml = "0.65.0"

[libraries]
kaml = { module = "com.charleskorn.kaml:kaml", version.ref = "kaml" }
```

- [ ] **Step 2: Lägg kaml till `composeApp/commonMain.dependencies`**

I `composeApp/build.gradle.kts`:

```kotlin
sourceSets {
    commonMain.dependencies {
        // ... existerande
        implementation(libs.kaml)
        implementation(libs.kotlinx.serialization.core) // om saknas — kaml bygger på den
    }
}
```

- [ ] **Step 3: Skapa `badges.yaml` med 25 märken**

`composeApp/src/commonMain/composeResources/files/badges.yaml`:

```yaml
version: 1
badges:
  # ===== Progression (3) =====
  - id: novice
    category: progression
    rule: { type: count_unique_species, target: 5 }
  - id: birder_bronze
    category: progression
    rule: { type: count_unique_species, target: 25 }
  - id: birder_silver
    category: progression
    rule: { type: count_unique_species, target: 100 }

  # ===== Streaks veckor (4) =====
  - id: weekly_streak_4
    category: streak_weekly
    rule: { type: weekly_streak, target: 4 }
  - id: weekly_streak_12
    category: streak_weekly
    rule: { type: weekly_streak, target: 12 }
  - id: weekly_streak_26
    category: streak_weekly
    rule: { type: weekly_streak, target: 26 }
  - id: weekly_streak_52
    category: streak_weekly
    rule: { type: weekly_streak, target: 52 }

  # ===== Streaks månader (3) =====
  - id: monthly_streak_3
    category: streak_monthly
    rule: { type: monthly_streak, target: 3 }
  - id: monthly_streak_6
    category: streak_monthly
    rule: { type: monthly_streak, target: 6 }
  - id: monthly_streak_12
    category: streak_monthly
    rule: { type: monthly_streak, target: 12 }

  # ===== Säsong (4) =====
  - id: season_winter
    category: season
    rule: { type: observed_in_season, season: winter, target: 10 }
  - id: season_spring
    category: season
    rule: { type: observed_in_season, season: spring, target: 5 }
  - id: season_summer
    category: season
    rule: { type: observed_in_season, season: summer, target: 5 }
  - id: season_autumn
    category: season
    rule: { type: observed_in_season, season: autumn, target: 5 }

  # ===== Familjer (8) =====
  - id: family_anatidae
    category: family
    rule: { type: observed_in_family, family: anatidae, target: 1 }
  - id: family_paridae
    category: family
    rule: { type: observed_in_family, family: paridae, target: 1 }
  - id: family_accipitridae
    category: family
    rule: { type: observed_in_family, family: accipitridae, target: 1 }
  - id: family_corvidae
    category: family
    rule: { type: observed_in_family, family: corvidae, target: 1 }
  - id: family_fringillidae
    category: family
    rule: { type: observed_in_family, family: fringillidae, target: 1 }
  - id: family_turdidae
    category: family
    rule: { type: observed_in_family, family: turdidae, target: 1 }
  - id: family_sylviidae
    category: family
    rule: { type: observed_in_family, family: sylviidae, target: 1 }
  - id: family_picidae
    category: family
    rule: { type: observed_in_family, family: picidae, target: 1 }

  # ===== Sällsynt (3) =====
  - id: rare_first
    category: rare
    rule: { type: observed_with_abundance, abundance: sällsynt, target: 1 }
  - id: rare_5
    category: rare
    rule: { type: observed_with_abundance, abundance: sällsynt, target: 5 }
  - id: rare_10
    category: rare
    rule: { type: observed_with_abundance, abundance: sällsynt, target: 10 }
```

Räkna: 3 + 4 + 3 + 4 + 8 + 3 = **25 märken**.

- [ ] **Step 4: Skriv failing test för `BadgeCatalogLoader`**

`composeApp/src/commonTest/kotlin/se/birdy/app/badges/BadgeCatalogLoaderTest.kt`:

```kotlin
package se.birdy.app.badges

import se.birdy.domain.badge.BadgeAbundance
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeSeason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BadgeCatalogLoaderTest {

    @Test
    fun `parse valid yaml returns 25 badges with version 1`() {
        val yaml = validYaml()
        val catalog = BadgeCatalogLoader.parse(yaml)
        assertEquals(1, catalog.version)
        assertEquals(25, catalog.badges.size)
        // Spot-check
        val novice = catalog.findById("novice")!!
        assertEquals(BadgeCategory.PROGRESSION, novice.category)
        assertEquals(BadgeRule.CountUniqueSpecies(5), novice.rule)
    }

    @Test
    fun `parse maps all 6 rule types correctly`() {
        val yaml = validYaml()
        val catalog = BadgeCatalogLoader.parse(yaml)

        assertEquals(BadgeRule.CountUniqueSpecies(5), catalog.findById("novice")!!.rule)
        assertEquals(BadgeRule.WeeklyStreak(4), catalog.findById("weekly_streak_4")!!.rule)
        assertEquals(BadgeRule.MonthlyStreak(3), catalog.findById("monthly_streak_3")!!.rule)
        assertEquals(
            BadgeRule.ObservedInSeason(BadgeSeason.WINTER, 10),
            catalog.findById("season_winter")!!.rule,
        )
        assertEquals(
            BadgeRule.ObservedInFamily("anatidae", 1),
            catalog.findById("family_anatidae")!!.rule,
        )
        assertEquals(
            BadgeRule.ObservedWithAbundance(BadgeAbundance.SÄLLSYNT, 1),
            catalog.findById("rare_first")!!.rule,
        )
    }

    @Test
    fun `parse fails on unknown rule type`() {
        val yaml = """
            version: 1
            badges:
              - id: bad
                category: progression
                rule: { type: count_pizzas, target: 5 }
        """.trimIndent()
        assertFailsWith<BadgeCatalogException> { BadgeCatalogLoader.parse(yaml) }
    }

    @Test
    fun `parse fails on unknown category`() {
        val yaml = """
            version: 1
            badges:
              - id: bad
                category: bogus
                rule: { type: count_unique_species, target: 5 }
        """.trimIndent()
        assertFailsWith<BadgeCatalogException> { BadgeCatalogLoader.parse(yaml) }
    }

    @Test
    fun `parse fails on missing required field`() {
        val yaml = """
            version: 1
            badges:
              - id: bad
                category: progression
                # missing rule
        """.trimIndent()
        assertFailsWith<BadgeCatalogException> { BadgeCatalogLoader.parse(yaml) }
    }

    @Test
    fun `parse fails on duplicate id`() {
        val yaml = """
            version: 1
            badges:
              - id: dup
                category: progression
                rule: { type: count_unique_species, target: 5 }
              - id: dup
                category: progression
                rule: { type: count_unique_species, target: 25 }
        """.trimIndent()
        assertFailsWith<BadgeCatalogException> { BadgeCatalogLoader.parse(yaml) }
    }

    @Test
    fun `parse fails on syntax error`() {
        val yaml = "not: valid: yaml: ::"
        assertFailsWith<BadgeCatalogException> { BadgeCatalogLoader.parse(yaml) }
    }

    private fun validYaml(): String = """
        version: 1
        badges:
          - id: novice
            category: progression
            rule: { type: count_unique_species, target: 5 }
          - id: birder_bronze
            category: progression
            rule: { type: count_unique_species, target: 25 }
          - id: birder_silver
            category: progression
            rule: { type: count_unique_species, target: 100 }
          - id: weekly_streak_4
            category: streak_weekly
            rule: { type: weekly_streak, target: 4 }
          - id: weekly_streak_12
            category: streak_weekly
            rule: { type: weekly_streak, target: 12 }
          - id: weekly_streak_26
            category: streak_weekly
            rule: { type: weekly_streak, target: 26 }
          - id: weekly_streak_52
            category: streak_weekly
            rule: { type: weekly_streak, target: 52 }
          - id: monthly_streak_3
            category: streak_monthly
            rule: { type: monthly_streak, target: 3 }
          - id: monthly_streak_6
            category: streak_monthly
            rule: { type: monthly_streak, target: 6 }
          - id: monthly_streak_12
            category: streak_monthly
            rule: { type: monthly_streak, target: 12 }
          - id: season_winter
            category: season
            rule: { type: observed_in_season, season: winter, target: 10 }
          - id: season_spring
            category: season
            rule: { type: observed_in_season, season: spring, target: 5 }
          - id: season_summer
            category: season
            rule: { type: observed_in_season, season: summer, target: 5 }
          - id: season_autumn
            category: season
            rule: { type: observed_in_season, season: autumn, target: 5 }
          - id: family_anatidae
            category: family
            rule: { type: observed_in_family, family: anatidae, target: 1 }
          - id: family_paridae
            category: family
            rule: { type: observed_in_family, family: paridae, target: 1 }
          - id: family_accipitridae
            category: family
            rule: { type: observed_in_family, family: accipitridae, target: 1 }
          - id: family_corvidae
            category: family
            rule: { type: observed_in_family, family: corvidae, target: 1 }
          - id: family_fringillidae
            category: family
            rule: { type: observed_in_family, family: fringillidae, target: 1 }
          - id: family_turdidae
            category: family
            rule: { type: observed_in_family, family: turdidae, target: 1 }
          - id: family_sylviidae
            category: family
            rule: { type: observed_in_family, family: sylviidae, target: 1 }
          - id: family_picidae
            category: family
            rule: { type: observed_in_family, family: picidae, target: 1 }
          - id: rare_first
            category: rare
            rule: { type: observed_with_abundance, abundance: sällsynt, target: 1 }
          - id: rare_5
            category: rare
            rule: { type: observed_with_abundance, abundance: sällsynt, target: 5 }
          - id: rare_10
            category: rare
            rule: { type: observed_with_abundance, abundance: sällsynt, target: 10 }
    """.trimIndent()
}
```

- [ ] **Step 5: Implementera `BadgeCatalogLoader.kt` med kaml**

```kotlin
package se.birdy.app.badges

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import kotlinx.serialization.Serializable
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeAbundance
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeSeason

class BadgeCatalogException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

object BadgeCatalogLoader {

    fun parse(yamlText: String): BadgeCatalog {
        val raw = try {
            Yaml.default.decodeFromString(RawCatalog.serializer(), yamlText)
        } catch (e: YamlException) {
            throw BadgeCatalogException("YAML parse error: ${e.message}", e)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw BadgeCatalogException("Schema mismatch: ${e.message}", e)
        }

        val seenIds = mutableSetOf<String>()
        val badges = raw.badges.map { rb ->
            if (!seenIds.add(rb.id)) {
                throw BadgeCatalogException("Duplicate badge id: ${rb.id}")
            }
            Badge(
                id = rb.id,
                category = parseCategory(rb.category),
                rule = parseRule(rb.id, rb.rule),
            )
        }
        return BadgeCatalog(version = raw.version, badges = badges)
    }

    private fun parseCategory(raw: String): BadgeCategory = when (raw) {
        "progression" -> BadgeCategory.PROGRESSION
        "streak_weekly" -> BadgeCategory.STREAK_WEEKLY
        "streak_monthly" -> BadgeCategory.STREAK_MONTHLY
        "season" -> BadgeCategory.SEASON
        "family" -> BadgeCategory.FAMILY
        "rare" -> BadgeCategory.RARE
        else -> throw BadgeCatalogException("Unknown category: $raw")
    }

    private fun parseRule(badgeId: String, raw: RawRule): BadgeRule = when (raw.type) {
        "count_unique_species" -> BadgeRule.CountUniqueSpecies(raw.target ?: missing(badgeId, "target"))
        "weekly_streak" -> BadgeRule.WeeklyStreak(raw.target ?: missing(badgeId, "target"))
        "monthly_streak" -> BadgeRule.MonthlyStreak(raw.target ?: missing(badgeId, "target"))
        "observed_in_season" -> BadgeRule.ObservedInSeason(
            season = parseSeason(raw.season ?: missing(badgeId, "season")),
            target = raw.target ?: missing(badgeId, "target"),
        )
        "observed_in_family" -> BadgeRule.ObservedInFamily(
            family = raw.family ?: missing(badgeId, "family"),
            target = raw.target ?: missing(badgeId, "target"),
        )
        "observed_with_abundance" -> BadgeRule.ObservedWithAbundance(
            abundance = parseAbundance(raw.abundance ?: missing(badgeId, "abundance")),
            target = raw.target ?: missing(badgeId, "target"),
        )
        else -> throw BadgeCatalogException("Unknown rule type for $badgeId: ${raw.type}")
    }

    private fun parseSeason(raw: String): BadgeSeason = when (raw.lowercase()) {
        "winter" -> BadgeSeason.WINTER
        "spring" -> BadgeSeason.SPRING
        "summer" -> BadgeSeason.SUMMER
        "autumn" -> BadgeSeason.AUTUMN
        else -> throw BadgeCatalogException("Unknown season: $raw")
    }

    private fun parseAbundance(raw: String): BadgeAbundance = when (raw.lowercase()) {
        "allmän" -> BadgeAbundance.ALLMÄN
        "mindre_allmän" -> BadgeAbundance.MINDRE_ALLMÄN
        "ovanlig" -> BadgeAbundance.OVANLIG
        "sällsynt" -> BadgeAbundance.SÄLLSYNT
        else -> throw BadgeCatalogException("Unknown abundance: $raw")
    }

    private fun missing(badgeId: String, field: String): Nothing =
        throw BadgeCatalogException("Badge $badgeId missing required field: $field")

    @Serializable
    private data class RawCatalog(
        val version: Int,
        val badges: List<RawBadge>,
    )

    @Serializable
    private data class RawBadge(
        val id: String,
        val category: String,
        val rule: RawRule,
    )

    @Serializable
    private data class RawRule(
        val type: String,
        val target: Int? = null,
        val season: String? = null,
        val family: String? = null,
        val abundance: String? = null,
    )
}
```

- [ ] **Step 6: Lägg `loadFromResources`-suspend-funktion i samma fil**

```kotlin
import birdy_bird_scanner.composeapp.generated.resources.Res

object BadgeCatalogLoader {
    // ... ovan parse(yamlText)

    @org.jetbrains.compose.resources.ExperimentalResourceApi
    suspend fun loadFromResources(): BadgeCatalog {
        val bytes = Res.readBytes("files/badges.yaml")
        return parse(bytes.decodeToString())
    }
}
```

(Verifiera package name `birdy_bird_scanner.composeapp.generated.resources` — Plan 5a:s `formatRelativeDate.kt` eller `DiaryScreen.kt` använder samma `Res`-import, kolla där.)

- [ ] **Step 7: Kör testerna — alla ska passa**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*BadgeCatalogLoaderTest*"`
Expected: PASS — alla 7 tests gröna.

- [ ] **Step 8: Verifiera att riktiga `badges.yaml` parsar via integration**

Lägg till en extra test som läser från resources (om JVM-tester har resource-access):

```kotlin
@Test
fun `real badges yaml from resources has 25 badges`() = runBlocking {
    val catalog = BadgeCatalogLoader.loadFromResources()
    assertEquals(25, catalog.badges.size)
    assertEquals(1, catalog.version)
}
```

(Kan hoppas över om compose-resources Res-API inte stödjer JVM-test-context — då verifieras via device-run i Task 14.)

- [ ] **Step 9: ktlint + detekt + assembleDebug**

Run:
```bash
./gradlew ktlintCheck detekt
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add composeApp/build.gradle.kts \
        composeApp/src/commonMain/composeResources/files/badges.yaml \
        composeApp/src/commonMain/kotlin/se/birdy/app/badges/BadgeCatalogLoader.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/badges/BadgeCatalogLoaderTest.kt
git commit -m "feat(badges): Plan 5b Task 5 — badges.yaml + BadgeCatalogLoader (kaml in commonMain)"
```

---

## Task 6: `RecalculateBadgesUseCase` + exhaustive tester

Pure function. Lever i `composeApp/badges/` (avsteg från spec — se Avvikelse 1). Läser `Observation` (shared/domain), `Species` (shared/content), `BadgeCatalog` (shared/domain), returnerar `List<BadgeUnlock>`. Inga side-effects. `Clock.fixed`-injection för testbarhet.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/badges/RecalculateBadgesUseCase.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/badges/RecalculateBadgesUseCaseTest.kt`

- [ ] **Step 1: Skriv failing test — count_unique_species rule**

```kotlin
package se.birdy.app.badges

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import se.birdy.app.testing.FakeClock
import se.birdy.content.Locale
import se.birdy.content.Species
import se.birdy.content.SpeciesId
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeAbundance
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeSeason
import se.birdy.domain.observation.Observation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecalculateBadgesUseCaseTest {
    private val utc = TimeZone.UTC
    private val fixedNow = Instant.fromEpochMilliseconds(1_800_000_000_000)
    private val clock = FakeClock(fixedNow)
    private val recalc = RecalculateBadgesUseCase(zone = utc, clock = clock)

    @Test
    fun `count_unique_species — 5 unique observations unlocks novice`() {
        val obs = (1..5).map { obs(speciesId = "Q$it", day = it) }
        val catalog = catalogOf(badge("novice", BadgeRule.CountUniqueSpecies(5)))
        val newUnlocks = recalc.newUnlocks(obs, emptyMap(), catalog, emptySet())
        assertEquals(1, newUnlocks.size)
        assertEquals("novice", newUnlocks[0].badgeId)
        assertEquals(fixedNow, newUnlocks[0].unlockedAt)
    }

    @Test
    fun `count_unique_species — 4 unique does not unlock 5-target`() {
        val obs = (1..4).map { obs(speciesId = "Q$it", day = it) }
        val catalog = catalogOf(badge("novice", BadgeRule.CountUniqueSpecies(5)))
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()))
    }

    @Test
    fun `count_unique_species — duplicates do not double-count`() {
        val obs = listOf(
            obs(speciesId = "Q1", day = 1),
            obs(speciesId = "Q1", day = 2),
            obs(speciesId = "Q1", day = 3),
        )
        val catalog = catalogOf(badge("novice", BadgeRule.CountUniqueSpecies(2)))
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()))
    }

    @Test
    fun `existing unlocks are excluded`() {
        val obs = (1..5).map { obs(speciesId = "Q$it", day = it) }
        val catalog = catalogOf(badge("novice", BadgeRule.CountUniqueSpecies(5)))
        val existing = setOf("novice")
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalog, existing))
    }

    @Test
    fun `weekly_streak — 3 consecutive weeks unlocks streak_3`() {
        val obs = listOf(
            obs("Q1", 2026, 5, 4),  // v19
            obs("Q1", 2026, 5, 11), // v20
            obs("Q1", 2026, 5, 18), // v21
        )
        val catalog = catalogOf(badge("ws3", BadgeRule.WeeklyStreak(3)))
        assertEquals(listOf("ws3"), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()).map { it.badgeId })
    }

    @Test
    fun `weekly_streak — gap breaks streak`() {
        val obs = listOf(
            obs("Q1", 2026, 5, 4),
            obs("Q1", 2026, 5, 11),
            obs("Q1", 2026, 5, 25),  // hopp över v21
            obs("Q1", 2026, 6, 1),
        )
        val catalog = catalogOf(badge("ws3", BadgeRule.WeeklyStreak(3)))
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()))
    }

    @Test
    fun `weekly_streak — cross-year v53 to v01 counts`() {
        val obs = listOf(
            obs("Q1", 2026, 12, 22),
            obs("Q1", 2026, 12, 29),
            obs("Q1", 2027, 1, 5),
        )
        val catalog = catalogOf(badge("ws3", BadgeRule.WeeklyStreak(3)))
        assertEquals(listOf("ws3"), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()).map { it.badgeId })
    }

    @Test
    fun `monthly_streak — 3 consecutive months unlocks`() {
        val obs = listOf(
            obs("Q1", 2026, 3, 15),
            obs("Q1", 2026, 4, 15),
            obs("Q1", 2026, 5, 15),
        )
        val catalog = catalogOf(badge("ms3", BadgeRule.MonthlyStreak(3)))
        assertEquals(listOf("ms3"), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()).map { it.badgeId })
    }

    @Test
    fun `observed_in_season — winter requires 10 in dec-feb`() {
        val obs = (1..10).map { obs("Q$it", 2026, 12, it) }
        val catalog = catalogOf(badge("winter10", BadgeRule.ObservedInSeason(BadgeSeason.WINTER, 10)))
        assertEquals(listOf("winter10"), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()).map { it.badgeId })
    }

    @Test
    fun `observed_in_season — only december counts as winter (meteorological)`() {
        val obs = listOf(
            obs("Q1", 2026, 11, 30),  // november = autumn
            obs("Q1", 2026, 12, 1),   // december = winter
        )
        val catalog = catalogOf(badge("winter1", BadgeRule.ObservedInSeason(BadgeSeason.WINTER, 1)))
        assertEquals(listOf("winter1"), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()).map { it.badgeId })
    }

    @Test
    fun `observed_in_family — paridae match`() {
        val species = mapOf(
            SpeciesId("Q25612") to fakeSpecies("Q25612", family = "paridae"),
            SpeciesId("Q1") to fakeSpecies("Q1", family = "corvidae"),
        )
        val obs = listOf(obs("Q25612", 2026, 5, 4), obs("Q1", 2026, 5, 5))
        val catalog = catalogOf(badge("fam_paridae", BadgeRule.ObservedInFamily("paridae", 1)))
        assertEquals(listOf("fam_paridae"), recalc.newUnlocks(obs, species, catalog, emptySet()).map { it.badgeId })
    }

    @Test
    fun `observed_in_family — no match when family missing in species map`() {
        val obs = listOf(obs("Q-unknown", 2026, 5, 4))
        val catalog = catalogOf(badge("fam_paridae", BadgeRule.ObservedInFamily("paridae", 1)))
        assertEquals(emptyList(), recalc.newUnlocks(obs, emptyMap(), catalog, emptySet()))
    }

    @Test
    fun `observed_with_abundance — sällsynt 1 match`() {
        val species = mapOf(SpeciesId("Q1") to fakeSpecies("Q1", abundance = BadgeAbundance.SÄLLSYNT))
        val obs = listOf(obs("Q1", 2026, 5, 4))
        val catalog = catalogOf(badge("rare1", BadgeRule.ObservedWithAbundance(BadgeAbundance.SÄLLSYNT, 1)))
        assertEquals(listOf("rare1"), recalc.newUnlocks(obs, species, catalog, emptySet()).map { it.badgeId })
    }

    @Test
    fun `observed_with_abundance — null abundance does not count`() {
        val species = mapOf(SpeciesId("Q1") to fakeSpecies("Q1", abundance = null))
        val obs = listOf(obs("Q1", 2026, 5, 4))
        val catalog = catalogOf(badge("rare1", BadgeRule.ObservedWithAbundance(BadgeAbundance.SÄLLSYNT, 1)))
        assertEquals(emptyList(), recalc.newUnlocks(obs, species, catalog, emptySet()))
    }

    @Test
    fun `multiple unlocks in one call returned in deterministic order`() {
        val obs = (1..5).map { obs("Q$it", 2026, 5, it) }
        val catalog = BadgeCatalog(
            version = 1,
            badges = listOf(
                badge("novice", BadgeRule.CountUniqueSpecies(5)),
                badge("five_obs", BadgeRule.CountUniqueSpecies(3)),
            ),
        )
        val unlocks = recalc.newUnlocks(obs, emptyMap(), catalog, emptySet())
        // Ordning matchar catalog.badges-listan
        assertEquals(listOf("novice", "five_obs"), unlocks.map { it.badgeId })
    }

    @Test
    fun `currentValue returns count for count_unique`() {
        val obs = (1..3).map { obs("Q$it", 2026, 5, it) }
        assertEquals(3, recalc.currentValue(BadgeRule.CountUniqueSpecies(5), obs, emptyMap()))
    }

    @Test
    fun `currentValue caps at target`() {
        val obs = (1..10).map { obs("Q$it", 2026, 5, it) }
        assertEquals(5, recalc.currentValue(BadgeRule.CountUniqueSpecies(5), obs, emptyMap()))
    }

    @Test
    fun `empty observations return empty unlocks`() {
        val catalog = catalogOf(badge("novice", BadgeRule.CountUniqueSpecies(5)))
        assertTrue(recalc.newUnlocks(emptyList(), emptyMap(), catalog, emptySet()).isEmpty())
    }

    // ===== helpers =====

    private fun obs(
        speciesId: String,
        year: Int = 2026,
        month: Int = 5,
        day: Int = 1,
    ): Observation {
        val capturedAt = LocalDateTime(year, month, day, 12, 0).toInstant(utc)
        return Observation(
            id = "obs-$speciesId-$year-$month-$day",
            speciesId = speciesId,
            capturedAt = capturedAt,
            savedAt = capturedAt,
            photoPath = "/tmp/photo.jpg",
            note = "",
            confidence = 0.9f,
            latitude = null, longitude = null, locationLabel = null,
        )
    }

    private fun obs(speciesId: String, day: Int): Observation = obs(speciesId, 2026, 5, day)

    private fun badge(id: String, rule: BadgeRule): Badge =
        Badge(id, BadgeCategory.PROGRESSION, rule)

    private fun catalogOf(vararg badges: Badge): BadgeCatalog =
        BadgeCatalog(version = 1, badges = badges.toList())

    private fun fakeSpecies(
        qid: String,
        family: String = "unknown",
        abundance: BadgeAbundance? = null,
    ): Species {
        // Bygg minimal Species — verifiera fält-namn via shared/content/Species.kt
        // Anta att Species har `family: String` och `abundance: Abundance?`-fält.
        // Använd en faktisk constructor här när du vet exakta fält-namn.
        TODO("byggs i Step 5 efter att Species-typen verifierats via Read")
    }
}
```

OBS: `fakeSpecies` är en `TODO` tills Step 5 verifierar Species-konstruktorn — då fyller man i.

- [ ] **Step 2: Verifiera `Species`-typen i `shared/content`**

Run: Read `shared/content/src/commonMain/kotlin/se/birdy/content/Species.kt`
Expected: Lista alla fält i konstruktorn så `fakeSpecies` kan kompileras.

- [ ] **Step 3: Verifiera `Abundance`-mappning**

Run: Read `shared/content/src/commonMain/kotlin/se/birdy/content/Abundance.kt` (eller var den ligger).
Expected: Bekräfta enum-värden så `BadgeAbundance ↔ Abundance`-mappning matchar.

- [ ] **Step 4: Implementera `RecalculateBadgesUseCase.kt`**

```kotlin
package se.birdy.app.badges

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import se.birdy.content.Abundance as ContentAbundance
import se.birdy.content.Species
import se.birdy.content.SpeciesId
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeAbundance
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeSeason
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.badge.longestMonthlyStreak
import se.birdy.domain.badge.longestWeeklyStreak
import se.birdy.domain.badge.seasonOf
import se.birdy.domain.observation.Observation

class RecalculateBadgesUseCase(
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {

    fun newUnlocks(
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
        catalog: BadgeCatalog,
        existingUnlocks: Set<String>,
    ): List<BadgeUnlock> {
        val now = clock.now()
        return catalog.badges
            .filter { it.id !in existingUnlocks }
            .filter { evaluate(it.rule, observations, speciesByQid) }
            .map { BadgeUnlock(it.id, now) }
    }

    fun currentValue(
        rule: BadgeRule,
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
    ): Int {
        val raw = when (rule) {
            is BadgeRule.CountUniqueSpecies -> observations.map { it.speciesId }.toSet().size
            is BadgeRule.WeeklyStreak -> longestWeeklyStreak(observations.map { it.capturedAt }, zone)
            is BadgeRule.MonthlyStreak -> longestMonthlyStreak(observations.map { it.capturedAt }, zone)
            is BadgeRule.ObservedInSeason ->
                observations.count { seasonOf(it.capturedAt, zone) == rule.season }
            is BadgeRule.ObservedInFamily ->
                observations.count { speciesByQid[SpeciesId(it.speciesId)]?.family == rule.family }
            is BadgeRule.ObservedWithAbundance ->
                observations.count {
                    val s = speciesByQid[SpeciesId(it.speciesId)] ?: return@count false
                    val mapped = mapAbundance(s.abundance) ?: return@count false
                    mapped == rule.abundance
                }
        }
        return raw.coerceAtMost(rule.target)
    }

    private fun evaluate(
        rule: BadgeRule,
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
    ): Boolean {
        return when (rule) {
            is BadgeRule.CountUniqueSpecies ->
                observations.map { it.speciesId }.toSet().size >= rule.target
            is BadgeRule.WeeklyStreak ->
                longestWeeklyStreak(observations.map { it.capturedAt }, zone) >= rule.target
            is BadgeRule.MonthlyStreak ->
                longestMonthlyStreak(observations.map { it.capturedAt }, zone) >= rule.target
            is BadgeRule.ObservedInSeason ->
                observations.count { seasonOf(it.capturedAt, zone) == rule.season } >= rule.target
            is BadgeRule.ObservedInFamily ->
                observations.count { speciesByQid[SpeciesId(it.speciesId)]?.family == rule.family } >= rule.target
            is BadgeRule.ObservedWithAbundance ->
                observations.count {
                    val s = speciesByQid[SpeciesId(it.speciesId)] ?: return@count false
                    val mapped = mapAbundance(s.abundance) ?: return@count false
                    mapped == rule.abundance
                } >= rule.target
        }
    }

    private fun mapAbundance(content: ContentAbundance?): BadgeAbundance? = when (content) {
        ContentAbundance.ALLMÄN -> BadgeAbundance.ALLMÄN
        ContentAbundance.MINDRE_ALLMÄN -> BadgeAbundance.MINDRE_ALLMÄN
        ContentAbundance.OVANLIG -> BadgeAbundance.OVANLIG
        ContentAbundance.SÄLLSYNT -> BadgeAbundance.SÄLLSYNT
        null -> null
    }
}
```

OBS: Justera `ContentAbundance`-import-värden om enum:n i shared/content har andra namn.

- [ ] **Step 5: Fyll i `fakeSpecies`-helper i testet baserat på verifierad Species-typ**

(Step 2's verifiering ger fält-namn. Förslag baserat på vanlig design:)

```kotlin
private fun fakeSpecies(
    qid: String,
    family: String = "unknown",
    abundance: BadgeAbundance? = null,
): Species {
    val contentAbundance = abundance?.let {
        when (it) {
            BadgeAbundance.ALLMÄN -> se.birdy.content.Abundance.ALLMÄN
            BadgeAbundance.MINDRE_ALLMÄN -> se.birdy.content.Abundance.MINDRE_ALLMÄN
            BadgeAbundance.OVANLIG -> se.birdy.content.Abundance.OVANLIG
            BadgeAbundance.SÄLLSYNT -> se.birdy.content.Abundance.SÄLLSYNT
        }
    }
    return Species(
        id = SpeciesId(qid),
        family = family,
        abundance = contentAbundance,
        // ... fyll i andra required-fält enligt Species.kt
        // (commonName, scientificName, locale, description, images, etc.)
    )
}
```

(Subagent fyller i exakt baserat på Read-resultatet från Step 2.)

- [ ] **Step 6: Kör testerna**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*RecalculateBadgesUseCaseTest*"`
Expected: PASS — alla ~17 tests gröna.

- [ ] **Step 7: Kör hela `:composeApp:testDebugUnitTest`-suite**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: Plan 5a-tester förblir gröna.

- [ ] **Step 8: ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/badges/RecalculateBadgesUseCase.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/badges/RecalculateBadgesUseCaseTest.kt
git commit -m "feat(badges): Plan 5b Task 6 — RecalculateBadgesUseCase (pure, exhaustive tests)"
```

---

## Task 7: i18n — `badges_*`, `unlock_*`, `badge_name_*`, `badge_desc_*` (sv + en)

Strängarna är *runtime-källan* för badge-namn/beskrivningar; YAML har bara `id` + struktur. UI läser via `stringResource(Res.string.badge_name_<id>)`.

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Verifiera nuvarande `strings.xml`-struktur**

Run: Read `composeApp/src/commonMain/composeResources/values/strings.xml` (avläs bara sista 30 rader för struktur).
Expected: XML med `<string name="..."> </string>`-format, sv-text. Plan 5a la till `diary_*`-keys.

- [ ] **Step 2: Lägg till alla badge-keys i sv-strings.xml**

Inkluderar UI-strängar (badges_*, unlock_*) + 25 × 2 badge-text-keys (`badge_name_<id>` + `badge_desc_<id>`).

```xml
<!-- ===== Plan 5b: Badges UI ===== -->
<string name="badges_title">Märken</string>
<string name="badges_label_species_seen">Arter sedda</string>
<string name="badges_label_badges">Märken</string>
<string name="badges_label_weekly_streak">V-streak</string>
<string name="badges_label_monthly_streak">M-streak</string>
<string name="badges_section_recently_unlocked">Senast upptäckta</string>
<string name="badges_section_to_discover">Att upptäcka · %1$d kvar</string>
<string name="badges_locked_tooltip">Hemligt — fortsätt skåda</string>
<string name="badges_progress_format">%1$d / %2$d</string>
<string name="badges_load_error">Kunde inte ladda märken — försök igen</string>
<string name="badges_load_error_retry">Försök igen</string>
<string name="badges_catalog_error">Märken kunde inte laddas — installera om appen</string>

<string name="unlock_label">MÄRKE UPPLÅST</string>
<string name="unlock_button_dismiss">Härligt</string>
<string name="unlock_unlocked_at">Upplåst %1$s</string>

<!-- ===== Badge names + descriptions ===== -->
<!-- Progression -->
<string name="badge_name_novice">Nybörjare</string>
<string name="badge_desc_novice">Du har sett dina första 5 fågelarter.</string>
<string name="badge_name_birder_bronze">Skådare</string>
<string name="badge_desc_birder_bronze">25 olika arter sedda — fältbiologi-brons.</string>
<string name="badge_name_birder_silver">Erfaren skådare</string>
<string name="badge_desc_birder_silver">100 olika arter sedda — du börjar bli kunnig.</string>

<!-- Streaks veckor -->
<string name="badge_name_weekly_streak_4">Månads-rytm</string>
<string name="badge_desc_weekly_streak_4">Du har skådat fåglar fyra veckor i rad.</string>
<string name="badge_name_weekly_streak_12">Kvartals-trogen</string>
<string name="badge_desc_weekly_streak_12">12 veckor i rad — du har hittat din rutin.</string>
<string name="badge_name_weekly_streak_26">Halvårs-skådare</string>
<string name="badge_desc_weekly_streak_26">26 veckor i rad — året halvgångert.</string>
<string name="badge_name_weekly_streak_52">Årets skådare</string>
<string name="badge_desc_weekly_streak_52">52 veckor i rad — ett helt år utan paus.</string>

<!-- Streaks månader -->
<string name="badge_name_monthly_streak_3">Säsong-skådare</string>
<string name="badge_desc_monthly_streak_3">Tre månader i rad — minst en obs varje månad.</string>
<string name="badge_name_monthly_streak_6">Halvårs-månader</string>
<string name="badge_desc_monthly_streak_6">Sex månader med obs varje.</string>
<string name="badge_name_monthly_streak_12">Året runt</string>
<string name="badge_desc_monthly_streak_12">Tolv månader med minst en obs varje.</string>

<!-- Säsong -->
<string name="badge_name_season_winter">Vinter-skådare</string>
<string name="badge_desc_season_winter">10 obs i december-februari — kall men givande.</string>
<string name="badge_name_season_spring">Vårfågel</string>
<string name="badge_desc_season_spring">5 obs i mars-maj — flyttfåglar i sikte.</string>
<string name="badge_name_season_summer">Sommarsångare</string>
<string name="badge_desc_season_summer">5 obs i juni-augusti.</string>
<string name="badge_name_season_autumn">Höstskådare</string>
<string name="badge_desc_season_autumn">5 obs i september-november.</string>

<!-- Familjer -->
<string name="badge_name_family_anatidae">Andfågel-vän</string>
<string name="badge_desc_family_anatidae">Du har sett en andfågel.</string>
<string name="badge_name_family_paridae">Mes-vän</string>
<string name="badge_desc_family_paridae">Du har sett en mes (talgoxe, blåmes, etc.).</string>
<string name="badge_name_family_accipitridae">Rovfågel-spanare</string>
<string name="badge_desc_family_accipitridae">Du har sett en hökfågel.</string>
<string name="badge_name_family_corvidae">Kråk-bekant</string>
<string name="badge_desc_family_corvidae">Du har sett en kråkfågel.</string>
<string name="badge_name_family_fringillidae">Fink-vän</string>
<string name="badge_desc_family_fringillidae">Du har sett en fink.</string>
<string name="badge_name_family_turdidae">Trast-vän</string>
<string name="badge_desc_family_turdidae">Du har sett en trast.</string>
<string name="badge_name_family_sylviidae">Sångar-vän</string>
<string name="badge_desc_family_sylviidae">Du har sett en sångare.</string>
<string name="badge_name_family_picidae">Hackspett-spanare</string>
<string name="badge_desc_family_picidae">Du har sett en hackspett.</string>

<!-- Sällsynt -->
<string name="badge_name_rare_first">Första sällsynta</string>
<string name="badge_desc_rare_first">Din första sällsynta art — minnesvärt.</string>
<string name="badge_name_rare_5">Sällsynt-jägare</string>
<string name="badge_desc_rare_5">Fem sällsynta arter sedda.</string>
<string name="badge_name_rare_10">Mästar-skådare</string>
<string name="badge_desc_rare_10">Tio sällsynta arter — imponerande.</string>
```

OBS: Apostrof och `%`-escape — Plan 5a-lärdomar:
- `%1$d` / `%1$s` är format-args (compose-resources processerar dem korrekt)
- `%%` processeras inte som `%`-escape; om literal procent behövs i strängen, sätt det istället via Kotlin call-site (`"${value}%"`)
- `\'` unescape:as inte; använd raw `'` direkt

- [ ] **Step 3: Mirror EN i `values-en/strings.xml`**

Speglar varje key. Översättningar:

```xml
<string name="badges_title">Badges</string>
<string name="badges_label_species_seen">Species seen</string>
<string name="badges_label_badges">Badges</string>
<string name="badges_label_weekly_streak">W-streak</string>
<string name="badges_label_monthly_streak">M-streak</string>
<string name="badges_section_recently_unlocked">Recently discovered</string>
<string name="badges_section_to_discover">To discover · %1$d left</string>
<string name="badges_locked_tooltip">Secret — keep birding</string>
<string name="badges_progress_format">%1$d / %2$d</string>
<string name="badges_load_error">Could not load badges — try again</string>
<string name="badges_load_error_retry">Try again</string>
<string name="badges_catalog_error">Badges could not load — reinstall the app</string>

<string name="unlock_label">BADGE UNLOCKED</string>
<string name="unlock_button_dismiss">Wonderful</string>
<string name="unlock_unlocked_at">Unlocked %1$s</string>

<!-- Progression -->
<string name="badge_name_novice">Novice</string>
<string name="badge_desc_novice">You have spotted your first 5 bird species.</string>
<string name="badge_name_birder_bronze">Birder</string>
<string name="badge_desc_birder_bronze">25 species seen — bronze field-birding tier.</string>
<string name="badge_name_birder_silver">Seasoned birder</string>
<string name="badge_desc_birder_silver">100 species seen — you are getting skilled.</string>

<!-- Streaks weekly -->
<string name="badge_name_weekly_streak_4">Monthly rhythm</string>
<string name="badge_desc_weekly_streak_4">You have birded four weeks in a row.</string>
<string name="badge_name_weekly_streak_12">Quarter-faithful</string>
<string name="badge_desc_weekly_streak_12">12 weeks in a row — you have a rhythm.</string>
<string name="badge_name_weekly_streak_26">Half-year birder</string>
<string name="badge_desc_weekly_streak_26">26 weeks in a row — halfway through the year.</string>
<string name="badge_name_weekly_streak_52">Year-round birder</string>
<string name="badge_desc_weekly_streak_52">52 weeks in a row — a full year unbroken.</string>

<!-- Streaks monthly -->
<string name="badge_name_monthly_streak_3">Season birder</string>
<string name="badge_desc_monthly_streak_3">Three months in a row — at least one obs each month.</string>
<string name="badge_name_monthly_streak_6">Half-year months</string>
<string name="badge_desc_monthly_streak_6">Six months with observations each.</string>
<string name="badge_name_monthly_streak_12">All year</string>
<string name="badge_desc_monthly_streak_12">Twelve months with at least one observation each.</string>

<!-- Season -->
<string name="badge_name_season_winter">Winter birder</string>
<string name="badge_desc_season_winter">10 obs in December-February — cold but rewarding.</string>
<string name="badge_name_season_spring">Spring birder</string>
<string name="badge_desc_season_spring">5 obs in March-May — migrants in sight.</string>
<string name="badge_name_season_summer">Summer singer</string>
<string name="badge_desc_season_summer">5 obs in June-August.</string>
<string name="badge_name_season_autumn">Autumn birder</string>
<string name="badge_desc_season_autumn">5 obs in September-November.</string>

<!-- Family -->
<string name="badge_name_family_anatidae">Waterfowl friend</string>
<string name="badge_desc_family_anatidae">You have seen a waterfowl.</string>
<string name="badge_name_family_paridae">Tit friend</string>
<string name="badge_desc_family_paridae">You have seen a tit (great tit, blue tit, etc.).</string>
<string name="badge_name_family_accipitridae">Raptor scout</string>
<string name="badge_desc_family_accipitridae">You have seen a hawk-family raptor.</string>
<string name="badge_name_family_corvidae">Corvid friend</string>
<string name="badge_desc_family_corvidae">You have seen a corvid.</string>
<string name="badge_name_family_fringillidae">Finch friend</string>
<string name="badge_desc_family_fringillidae">You have seen a finch.</string>
<string name="badge_name_family_turdidae">Thrush friend</string>
<string name="badge_desc_family_turdidae">You have seen a thrush.</string>
<string name="badge_name_family_sylviidae">Warbler friend</string>
<string name="badge_desc_family_sylviidae">You have seen a warbler.</string>
<string name="badge_name_family_picidae">Woodpecker scout</string>
<string name="badge_desc_family_picidae">You have seen a woodpecker.</string>

<!-- Rare -->
<string name="badge_name_rare_first">First rarity</string>
<string name="badge_desc_rare_first">Your first rare species — memorable.</string>
<string name="badge_name_rare_5">Rarity hunter</string>
<string name="badge_desc_rare_5">Five rare species seen.</string>
<string name="badge_name_rare_10">Master birder</string>
<string name="badge_desc_rare_10">Ten rare species — impressive.</string>
```

- [ ] **Step 4: Verifiera build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL — compose-resources genererar `Res.string.badge_name_novice` etc.

- [ ] **Step 5: Verifiera key-paritet sv vs en (manuell scan)**

Run: Grep `name="` i båda strings.xml-filerna; klistra båda räkningarna.
Expected: Antal `name="..."`-keys identiskt mellan sv och en.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(badges): Plan 5b Task 7 — i18n strings (sv + en) for 25 badges"
```

---

## Task 8: `SaveObservationUseCase` utökas → `SaveResult` + recalc-flow

API-brytande ändring (locked beslut #13): `save()` returnerar nu `SaveResult` istället för `String`. Bara en call-site (`ClassificationResultViewModel`) behöver uppdateras — det görs i Task 13.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveResult.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt`
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/usecase/SaveObservationUseCaseTest.kt`
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeBadgeRepository.kt`

- [ ] **Step 1: Skapa `SaveResult.kt`**

```kotlin
package se.birdy.app.usecase

import se.birdy.domain.badge.BadgeUnlock

data class SaveResult(
    val observationId: String,
    val newUnlocks: List<BadgeUnlock>,
)
```

- [ ] **Step 2: Skapa `FakeBadgeRepository.kt`**

```kotlin
package se.birdy.app.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeUnlock

class FakeBadgeRepository : BadgeRepository {
    private val _unlocks = MutableStateFlow<List<BadgeUnlock>>(emptyList())

    override fun observeUnlocks(): StateFlow<List<BadgeUnlock>> = _unlocks.asStateFlow()

    override suspend fun persist(unlocks: List<BadgeUnlock>) {
        if (unlocks.isEmpty()) return
        val byId = (_unlocks.value + unlocks).associateBy { it.badgeId }  // upsert: senaste vinner
        _unlocks.value = byId.values.sortedByDescending { it.unlockedAt }
    }

    override suspend fun deleteAll() {
        _unlocks.value = emptyList()
    }

    fun seedUnlocks(unlocks: List<BadgeUnlock>) {
        _unlocks.value = unlocks
    }

    companion object {
        fun withDefaults(): FakeBadgeRepository = FakeBadgeRepository().apply {
            seedUnlocks(
                listOf(
                    BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_700_000_000_000)),
                    BadgeUnlock("family_paridae", Instant.fromEpochMilliseconds(1_700_001_000_000)),
                    BadgeUnlock("season_spring", Instant.fromEpochMilliseconds(1_700_002_000_000)),
                ),
            )
        }
    }
}
```

- [ ] **Step 3: Skriv failing test för utökad `SaveObservationUseCase`**

Modifiera `SaveObservationUseCaseTest.kt` (utöka existerande från Plan 5a):

```kotlin
@Test
fun `save returns SaveResult with empty unlocks when no rule matches`() = runTest {
    val obsRepo = FakeObservationRepository()
    val badgeRepo = FakeBadgeRepository()
    val photoStorage = FakePhotoStorage()
    val clock = FakeClock(Instant.fromEpochMilliseconds(1_800_000_000_000))
    val catalog = BadgeCatalog(
        version = 1,
        badges = listOf(Badge("novice", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(5))),
    )
    val recalc = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock)
    val useCase = SaveObservationUseCase(
        repo = obsRepo,
        badgeRepo = badgeRepo,
        photoStorage = photoStorage,
        clock = clock,
        catalog = catalog,
        recalculate = recalc,
        speciesByQid = { emptyMap() },
    )

    val result = useCase.save(
        speciesId = "Q25612",
        capturedAt = clock.now(),
        confidence = 0.9f,
        rawJpegBytes = ByteArray(10),
        note = "",
    )

    assertEquals(emptyList(), result.newUnlocks)
    assertEquals(1, obsRepo.observeAll().first().size)
}

@Test
fun `save returns single unlock when first observation matches novice rule with target 1`() = runTest {
    val catalog = BadgeCatalog(
        version = 1,
        badges = listOf(Badge("first_obs", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(1))),
    )
    val obsRepo = FakeObservationRepository()
    val badgeRepo = FakeBadgeRepository()
    val photoStorage = FakePhotoStorage()
    val clock = FakeClock(Instant.fromEpochMilliseconds(1_800_000_000_000))
    val recalc = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock)
    val useCase = SaveObservationUseCase(
        obsRepo, badgeRepo, photoStorage, clock, catalog, recalc, { emptyMap() },
    )

    val result = useCase.save("Q25612", clock.now(), 0.9f, ByteArray(10), "")

    assertEquals(1, result.newUnlocks.size)
    assertEquals("first_obs", result.newUnlocks[0].badgeId)
    assertEquals(1, badgeRepo.observeUnlocks().first().size)
}

@Test
fun `save with multiple matching badges returns all unlocks in catalog order`() = runTest {
    val catalog = BadgeCatalog(
        version = 1,
        badges = listOf(
            Badge("first_obs", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(1)),
            Badge("first_in_family", BadgeCategory.FAMILY, BadgeRule.ObservedInFamily("paridae", 1)),
            Badge("first_spring", BadgeCategory.SEASON, BadgeRule.ObservedInSeason(BadgeSeason.SPRING, 1)),
        ),
    )
    val species = mapOf(SpeciesId("Q25612") to fakeSpecies("Q25612", family = "paridae"))
    val obsRepo = FakeObservationRepository()
    val badgeRepo = FakeBadgeRepository()
    val photoStorage = FakePhotoStorage()
    val capturedAt = LocalDateTime(2026, 5, 7, 12, 0).toInstant(TimeZone.UTC)
    val clock = FakeClock(capturedAt)
    val recalc = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock)
    val useCase = SaveObservationUseCase(
        obsRepo, badgeRepo, photoStorage, clock, catalog, recalc, { species },
    )

    val result = useCase.save("Q25612", capturedAt, 0.9f, ByteArray(10), "")

    assertEquals(listOf("first_obs", "first_in_family", "first_spring"), result.newUnlocks.map { it.badgeId })
}

@Test
fun `recalc failure after successful insert returns SaveResult with empty unlocks`() = runTest {
    val obsRepo = FakeObservationRepository()
    val badgeRepo = object : BadgeRepository {
        override fun observeUnlocks(): Flow<List<BadgeUnlock>> = flowOf(emptyList())
        override suspend fun persist(unlocks: List<BadgeUnlock>) = error("simulated DB failure")
        override suspend fun deleteAll() = Unit
    }
    val catalog = BadgeCatalog(version = 1, badges = listOf(
        Badge("first_obs", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(1)),
    ))
    val photoStorage = FakePhotoStorage()
    val clock = FakeClock(Instant.fromEpochMilliseconds(1L))
    val recalc = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock)
    val useCase = SaveObservationUseCase(
        obsRepo, badgeRepo, photoStorage, clock, catalog, recalc, { emptyMap() },
    )

    val result = useCase.save("Q25612", clock.now(), 0.9f, ByteArray(10), "")

    assertEquals(emptyList(), result.newUnlocks)
    assertEquals(1, obsRepo.observeAll().first().size)  // observation finns kvar
}

@Test
fun `existing unlocks are not re-emitted`() = runTest {
    val catalog = BadgeCatalog(
        version = 1,
        badges = listOf(Badge("first_obs", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(1))),
    )
    val obsRepo = FakeObservationRepository()
    val badgeRepo = FakeBadgeRepository().apply {
        seedUnlocks(listOf(BadgeUnlock("first_obs", Instant.fromEpochMilliseconds(1L))))
    }
    val photoStorage = FakePhotoStorage()
    val clock = FakeClock(Instant.fromEpochMilliseconds(2L))
    val recalc = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock)
    val useCase = SaveObservationUseCase(
        obsRepo, badgeRepo, photoStorage, clock, catalog, recalc, { emptyMap() },
    )

    val result = useCase.save("Q25612", clock.now(), 0.9f, ByteArray(10), "")

    assertEquals(emptyList(), result.newUnlocks)
}
```

- [ ] **Step 4: Implementera `SaveObservationUseCase` (modifierad)**

```kotlin
package se.birdy.app.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.photo.PhotoStorage
import se.birdy.content.Species
import se.birdy.content.SpeciesId
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SaveObservationUseCase(
    private val repo: ObservationRepository,
    private val badgeRepo: BadgeRepository,
    private val photoStorage: PhotoStorage,
    private val clock: Clock,
    private val catalog: BadgeCatalog,
    private val recalculate: RecalculateBadgesUseCase,
    private val speciesByQid: suspend () -> Map<SpeciesId, Species>,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun save(
        speciesId: String,
        capturedAt: Instant,
        confidence: Float,
        rawJpegBytes: ByteArray,
        note: String,
    ): SaveResult {
        val id = Uuid.random().toString()
        val photoPath = photoStorage.persistJpeg(rawJpegBytes)
        try {
            repo.insert(
                Observation(
                    id = id,
                    speciesId = speciesId,
                    capturedAt = capturedAt,
                    savedAt = clock.now(),
                    photoPath = photoPath,
                    note = note,
                    confidence = confidence,
                    latitude = null,
                    longitude = null,
                    locationLabel = null,
                ),
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            runCatching { photoStorage.delete(photoPath) }
            throw t
        }

        val newUnlocks = runCatching {
            val allObs = repo.observeAll().first()
            val species = speciesByQid()
            val existing = badgeRepo.observeUnlocks().first().map { it.badgeId }.toSet()
            val computed = recalculate.newUnlocks(allObs, species, catalog, existing)
            badgeRepo.persist(computed)
            computed
        }.onFailure {
            if (it is CancellationException) throw it
        }.getOrDefault(emptyList())

        return SaveResult(observationId = id, newUnlocks = newUnlocks)
    }
}
```

- [ ] **Step 5: Lägg till imports + helpers i testet**

Säkerställ att test-filen importerar:
- `kotlinx.coroutines.flow.Flow`, `flowOf`, `first`
- `kotlinx.datetime.LocalDateTime`, `TimeZone`, `toInstant`
- `se.birdy.app.badges.RecalculateBadgesUseCase`
- `se.birdy.domain.badge.*` (Badge, BadgeCatalog, BadgeRule, BadgeCategory, BadgeSeason, BadgeUnlock, BadgeRepository)
- `se.birdy.content.Species`, `SpeciesId`

`fakeSpecies`-helper kopieras från Task 6's testfil.

- [ ] **Step 6: Kör testerna**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SaveObservationUseCaseTest*"`
Expected: PASS — alla nya tests gröna; existerande Plan 5a-tester uppdaterade till nya signaturen.

(OBS: existerande Plan 5a-tester använde `assertEquals("expected-id", useCase.save(...))` — uppdatera till `result.observationId`. Lägg till `catalog`, `recalc`, `badgeRepo`, `speciesByQid` till alla tidigare test-konstruktoranrop med tomma defaults.)

- [ ] **Step 7: Kör hela `:composeApp:testDebugUnitTest`-suite**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: Alla gröna inkl. Plan 5a-tester.

- [ ] **Step 8: ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/usecase/ \
        composeApp/src/commonTest/kotlin/se/birdy/app/usecase/ \
        composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeBadgeRepository.kt
git commit -m "feat(badges): Plan 5b Task 8 — SaveObservationUseCase returns SaveResult + recalc"
```

---

## Task 9: `BadgeVersionStore` (expect/actual) + `BadgeBackfillOnAppStart` + tester

`BadgeVersionStore` är `expect class` i common, `actual` med vanlig `SharedPreferences` på Android (ej `EncryptedSharedPreferences` — värdet är ett ofarligt katalog-`Int`). `BadgeBackfillOnAppStart.runIfNeeded()` kör tyst recalc + persist när `versionStore.lastSeen < catalog.version`, sätt sedan `versionStore.lastSeen = catalog.version`.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/bootstrap/BadgeVersionStore.kt`
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/bootstrap/BadgeVersionStore.android.kt`
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/bootstrap/BadgeVersionStore.ios.kt`
- Create: `composeApp/src/jvmMain/kotlin/se/birdy/app/bootstrap/BadgeVersionStore.jvm.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/bootstrap/BadgeBackfillOnAppStart.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/bootstrap/BadgeBackfillOnAppStartTest.kt`

- [ ] **Step 1: Skapa `expect class BadgeVersionStore`**

```kotlin
package se.birdy.app.bootstrap

expect class BadgeVersionStore {
    var lastSeen: Int
}
```

- [ ] **Step 2: Skapa Android `actual`**

```kotlin
package se.birdy.app.bootstrap

import android.content.Context

actual class BadgeVersionStore(context: Context) {
    private val prefs = context.getSharedPreferences("birdy_badges", Context.MODE_PRIVATE)
    actual var lastSeen: Int
        get() = prefs.getInt(KEY_LAST_SEEN, 0)
        set(value) {
            prefs.edit().putInt(KEY_LAST_SEEN, value).apply()
        }

    private companion object {
        const val KEY_LAST_SEEN = "catalog_version_last_seen"
    }
}
```

- [ ] **Step 3: Skapa iOS `actual`-skelett**

```kotlin
package se.birdy.app.bootstrap

import platform.Foundation.NSUserDefaults

actual class BadgeVersionStore {
    actual var lastSeen: Int
        get() = NSUserDefaults.standardUserDefaults.integerForKey(KEY).toInt()
        set(value) {
            NSUserDefaults.standardUserDefaults.setInteger(value.toLong(), KEY)
        }

    private companion object {
        const val KEY = "birdy_badges_catalog_version"
    }
}
```

- [ ] **Step 4: Skapa JVM `actual` (in-memory för tester)**

```kotlin
package se.birdy.app.bootstrap

actual class BadgeVersionStore {
    actual var lastSeen: Int = 0
}
```

- [ ] **Step 5: Skriv failing test för `BadgeBackfillOnAppStart`**

```kotlin
package se.birdy.app.bootstrap

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.content.Species
import se.birdy.content.SpeciesId
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeUnlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BadgeBackfillOnAppStartTest {

    @Test
    fun `no-op when versionStore lastSeen equals catalog version`() = runTest {
        val versionStore = BadgeVersionStore().apply { lastSeen = 1 }
        val obsRepo = FakeObservationRepository()
        val badgeRepo = FakeBadgeRepository()
        val backfill = makeBackfill(catalogVersion = 1, versionStore, obsRepo, badgeRepo)

        backfill.runIfNeeded()

        assertEquals(1, versionStore.lastSeen)
        assertTrue(badgeRepo.observeUnlocks().first().isEmpty())
    }

    @Test
    fun `runs recalc when versionStore is behind catalog`() = runTest {
        val versionStore = BadgeVersionStore().apply { lastSeen = 0 }
        val obsRepo = FakeObservationRepository().apply {
            seedObservation(speciesId = "Q25612", capturedAt = Instant.fromEpochMilliseconds(1L))
        }
        val badgeRepo = FakeBadgeRepository()
        val backfill = makeBackfill(catalogVersion = 1, versionStore, obsRepo, badgeRepo)

        backfill.runIfNeeded()

        assertEquals(1, versionStore.lastSeen)
        val unlocks = badgeRepo.observeUnlocks().first()
        assertEquals(1, unlocks.size)
        assertEquals("first_obs", unlocks[0].badgeId)
    }

    @Test
    fun `empty observations result in no-op (but updates version)`() = runTest {
        val versionStore = BadgeVersionStore().apply { lastSeen = 0 }
        val backfill = makeBackfill(
            catalogVersion = 1,
            versionStore = versionStore,
            obsRepo = FakeObservationRepository(),
            badgeRepo = FakeBadgeRepository(),
        )

        backfill.runIfNeeded()

        assertEquals(1, versionStore.lastSeen)
    }

    @Test
    fun `existing unlocks are not duplicated`() = runTest {
        val versionStore = BadgeVersionStore().apply { lastSeen = 0 }
        val obsRepo = FakeObservationRepository().apply {
            seedObservation("Q1", Instant.fromEpochMilliseconds(1L))
        }
        val badgeRepo = FakeBadgeRepository().apply {
            seedUnlocks(listOf(BadgeUnlock("first_obs", Instant.fromEpochMilliseconds(0L))))
        }
        val backfill = makeBackfill(catalogVersion = 1, versionStore, obsRepo, badgeRepo)

        backfill.runIfNeeded()

        assertEquals(1, badgeRepo.observeUnlocks().first().size)
    }

    private fun makeBackfill(
        catalogVersion: Int,
        versionStore: BadgeVersionStore,
        obsRepo: FakeObservationRepository,
        badgeRepo: FakeBadgeRepository,
    ): BadgeBackfillOnAppStart {
        val catalog = BadgeCatalog(
            version = catalogVersion,
            badges = listOf(
                Badge("first_obs", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(1)),
            ),
        )
        val recalc = RecalculateBadgesUseCase(
            zone = TimeZone.UTC,
            clock = FakeClock(Instant.fromEpochMilliseconds(1_800_000_000_000)),
        )
        val species: suspend () -> Map<SpeciesId, Species> = { emptyMap() }
        return BadgeBackfillOnAppStart(
            recalc = recalc,
            obsRepo = obsRepo,
            speciesByQid = species,
            badgeRepo = badgeRepo,
            catalog = catalog,
            versionStore = versionStore,
        )
    }
}
```

- [ ] **Step 6: Implementera `BadgeBackfillOnAppStart.kt`**

```kotlin
package se.birdy.app.bootstrap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.content.Species
import se.birdy.content.SpeciesId
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.observation.ObservationRepository

class BadgeBackfillOnAppStart(
    private val recalc: RecalculateBadgesUseCase,
    private val obsRepo: ObservationRepository,
    private val speciesByQid: suspend () -> Map<SpeciesId, Species>,
    private val badgeRepo: BadgeRepository,
    private val catalog: BadgeCatalog,
    private val versionStore: BadgeVersionStore,
) {
    suspend fun runIfNeeded() {
        if (versionStore.lastSeen >= catalog.version) return
        runCatching {
            val obs = obsRepo.observeAll().first()
            val species = speciesByQid()
            val existing = badgeRepo.observeUnlocks().first().map { it.badgeId }.toSet()
            val backfill = recalc.newUnlocks(obs, species, catalog, existing)
            badgeRepo.persist(backfill)
            versionStore.lastSeen = catalog.version
        }.onFailure {
            if (it is CancellationException) throw it
            // Logga warning — Plan 5a/4a-mönster. Vid nästa app-start eller Save körs recalc igen.
        }
    }
}
```

- [ ] **Step 7: Lägg till `seedObservation`-helper i `FakeObservationRepository`**

Verifiera att Plan 5a:s `FakeObservationRepository` redan har en `seedObservation`-eller-liknande-helper. Om inte:

```kotlin
fun seedObservation(
    speciesId: String,
    capturedAt: Instant,
    id: String = "obs-${capturedAt.toEpochMilliseconds()}",
) {
    val obs = Observation(
        id = id,
        speciesId = speciesId,
        capturedAt = capturedAt,
        savedAt = capturedAt,
        photoPath = "/tmp/$id.jpg",
        note = "",
        confidence = 0.9f,
        latitude = null, longitude = null, locationLabel = null,
    )
    // Lägg in i intern state — verifiera mot existerande Plan 5a-impl
}
```

- [ ] **Step 8: Kör testerna**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*BadgeBackfillOnAppStartTest*"`
Expected: PASS — alla 4 tests gröna.

- [ ] **Step 9: Verifiera att Android-actual kompilerar**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/bootstrap/ \
        composeApp/src/androidMain/kotlin/se/birdy/app/bootstrap/ \
        composeApp/src/iosMain/kotlin/se/birdy/app/bootstrap/ \
        composeApp/src/jvmMain/kotlin/se/birdy/app/bootstrap/ \
        composeApp/src/commonTest/kotlin/se/birdy/app/bootstrap/
git commit -m "feat(badges): Plan 5b Task 9 — BadgeVersionStore + BadgeBackfillOnAppStart"
```

---

## Task 10: `UnlockQueue` + `UnlockBottomSheet`

Queue-baserad sekventiell visning av flera unlocks. `ClassificationResultViewModel` enqueue:ar via `unlockQueue.enqueue(newUnlocks)` efter Save. `UnlockBottomSheet` visas när `unlockQueue.current.collectAsState()` är non-null. `onDismiss` → `pop()`. Save-CTA disabled så länge queue.size > 0.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/UnlockQueue.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/UnlockQueueTest.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/UnlockBottomSheet.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/formatRelativeBadgeDate.kt`

- [ ] **Step 1: Skriv failing test för `UnlockQueue`**

```kotlin
package se.birdy.app.ui.badges

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import se.birdy.domain.badge.BadgeUnlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnlockQueueTest {

    @Test
    fun `current is null when queue is empty`() = runTest {
        val q = UnlockQueue()
        assertNull(q.current.first())
        assertEquals(0, q.size.first())
    }

    @Test
    fun `enqueue then current emits first unlock`() = runTest {
        val q = UnlockQueue()
        val u1 = BadgeUnlock("a", Instant.fromEpochMilliseconds(1L))
        val u2 = BadgeUnlock("b", Instant.fromEpochMilliseconds(2L))
        q.enqueue(listOf(u1, u2))

        assertEquals(u1, q.current.first())
        assertEquals(2, q.size.first())
    }

    @Test
    fun `pop advances to next`() = runTest {
        val q = UnlockQueue()
        val u1 = BadgeUnlock("a", Instant.fromEpochMilliseconds(1L))
        val u2 = BadgeUnlock("b", Instant.fromEpochMilliseconds(2L))
        q.enqueue(listOf(u1, u2))
        q.pop()
        assertEquals(u2, q.current.first())
        assertEquals(1, q.size.first())
    }

    @Test
    fun `pop on empty queue is no-op`() = runTest {
        val q = UnlockQueue()
        q.pop()
        assertNull(q.current.first())
    }

    @Test
    fun `enqueue empty list is no-op`() = runTest {
        val q = UnlockQueue()
        q.enqueue(emptyList())
        assertNull(q.current.first())
    }

    @Test
    fun `enqueue concatenates onto existing queue`() = runTest {
        val q = UnlockQueue()
        q.enqueue(listOf(BadgeUnlock("a", Instant.fromEpochMilliseconds(1L))))
        q.enqueue(listOf(BadgeUnlock("b", Instant.fromEpochMilliseconds(2L))))
        assertEquals(2, q.size.first())
        assertEquals("a", q.current.first()?.badgeId)
        q.pop()
        assertEquals("b", q.current.first()?.badgeId)
    }

    @Test
    fun `current emits new value when pop changes head`() = runTest {
        val q = UnlockQueue()
        q.enqueue(listOf(
            BadgeUnlock("a", Instant.fromEpochMilliseconds(1L)),
            BadgeUnlock("b", Instant.fromEpochMilliseconds(2L)),
        ))
        q.current.test {
            assertEquals("a", awaitItem()?.badgeId)
            q.pop()
            assertEquals("b", awaitItem()?.badgeId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Implementera `UnlockQueue.kt`**

```kotlin
package se.birdy.app.ui.badges

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import se.birdy.domain.badge.BadgeUnlock

class UnlockQueue {
    private val _queue = MutableStateFlow<List<BadgeUnlock>>(emptyList())

    val size: StateFlow<Int> = run {
        // Lokal CoroutineScope: vi använder enkel map utan scope för synkron access.
        // För kompositionsenklhet — gör beräkningen på read.
        kotlinx.coroutines.flow.MutableStateFlow(0).also { sizeFlow ->
            // Ingen scope-bunden hot-flow här; expose `_queue.map { it.size }` istället nedan.
        }
        TODO_REPLACE  // ersätts nedan
    }

    // Korrekt impl utan scope:
    val queueState: StateFlow<List<BadgeUnlock>> get() = _queue.asStateFlow()
    val current: kotlinx.coroutines.flow.Flow<BadgeUnlock?> = _queue.map { it.firstOrNull() }
    val sizeAsFlow: kotlinx.coroutines.flow.Flow<Int> = _queue.map { it.size }

    fun enqueue(unlocks: List<BadgeUnlock>) {
        if (unlocks.isEmpty()) return
        _queue.update { it + unlocks }
    }

    fun pop() {
        _queue.update { if (it.isEmpty()) it else it.drop(1) }
    }
}
```

> **OBS för subagent:** Refactor:a denna placeholder till en ren impl med Flow + `update`-extension. Den slutgiltiga koden ska se ut så här:

```kotlin
package se.birdy.app.ui.badges

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import se.birdy.domain.badge.BadgeUnlock

class UnlockQueue {
    private val _queue = MutableStateFlow<List<BadgeUnlock>>(emptyList())
    val queueState = _queue.asStateFlow()

    val current: Flow<BadgeUnlock?> = _queue.map { it.firstOrNull() }
    val size: Flow<Int> = _queue.map { it.size }

    fun enqueue(unlocks: List<BadgeUnlock>) {
        if (unlocks.isEmpty()) return
        _queue.update { it + unlocks }
    }

    fun pop() {
        _queue.update { if (it.isEmpty()) it else it.drop(1) }
    }
}
```

(Anpassa testerna — `q.current.first()` fungerar fortfarande med `Flow<T>`, och `q.size.first()` likadant. Om subagent vill ha `StateFlow` istället: ta in `CoroutineScope` i konstruktorn och `stateIn(scope, SharingStarted.Eagerly, null)` — men då måste VM:n ge sin scope.)

- [ ] **Step 3: Kör test — alla ska passa**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*UnlockQueueTest*"`
Expected: PASS.

- [ ] **Step 4: Skapa `formatRelativeBadgeDate.kt` (kort datum-format för bottom-sheet + carousel)**

```kotlin
package se.birdy.app.ui.badges

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.content.Locale

private val swedishMonths = listOf(
    "jan", "feb", "mar", "apr", "maj", "jun",
    "jul", "aug", "sep", "okt", "nov", "dec",
)
private val englishMonths = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

fun formatBadgeShortDate(instant: Instant, zone: TimeZone, locale: Locale): String {
    val ldt: LocalDateTime = instant.toLocalDateTime(zone)
    val months = if (locale == Locale.SV) swedishMonths else englishMonths
    val month = months[ldt.monthNumber - 1]
    return when (locale) {
        Locale.SV -> "${ldt.dayOfMonth} $month"
        Locale.EN -> "$month ${ldt.dayOfMonth}"
    }
}

fun formatBadgeFullDate(instant: Instant, zone: TimeZone, locale: Locale): String {
    val ldt = instant.toLocalDateTime(zone)
    val months = if (locale == Locale.SV) swedishMonths else englishMonths
    val month = months[ldt.monthNumber - 1]
    return when (locale) {
        Locale.SV -> "${ldt.dayOfMonth} $month ${ldt.year}"
        Locale.EN -> "$month ${ldt.dayOfMonth}, ${ldt.year}"
    }
}
```

- [ ] **Step 5: Skapa `UnlockBottomSheet.kt`**

```kotlin
package se.birdy.app.ui.badges

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.unlock_button_dismiss
import birdy_bird_scanner.composeapp.generated.resources.unlock_label
import birdy_bird_scanner.composeapp.generated.resources.unlock_unlocked_at
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroDeep
import se.birdy.app.ui.theme.OnHero
import se.birdy.app.ui.theme.TextPrimary
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockBottomSheet(
    badge: Badge,
    unlockedAt: Instant,
    isCelebration: Boolean,
    locale: Locale,
    zone: TimeZone,
    nameRes: StringResource,
    descriptionRes: StringResource,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    var animationDone by rememberSaveable(badge.id) { mutableStateOf(!isCelebration) }
    LaunchedEffect(badge.id, isCelebration) {
        if (isCelebration) {
            delay(3_000)
            animationDone = true
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "unlock-glow")
    val glowAlpha = if (isCelebration && !animationDone) {
        val anim by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glow-alpha",
        )
        anim
    } else 0f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(AccentCopper)
                    .drawBehind {
                        if (glowAlpha > 0f) {
                            drawCircle(
                                color = AccentCopper.copy(alpha = glowAlpha),
                                radius = size.minDimension / 1.4f,
                                center = Offset(size.width / 2f, size.height / 2f),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge.id.firstOrNull()?.uppercase() ?: "★",
                    color = OnHero,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(Res.string.unlock_label),
                fontSize = 11.sp,
                color = HeroDeep,
                letterSpacing = 1.5.sp,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(nameRes),
                fontSize = 26.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Normal,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(descriptionRes),
                fontSize = 15.sp,
                color = TextPrimary,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(
                    Res.string.unlock_unlocked_at,
                    formatBadgeFullDate(unlockedAt, zone, locale),
                ),
                fontSize = 12.sp,
                color = HeroDeep,
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCopper,
                    contentColor = OnHero,
                ),
            ) {
                Text(stringResource(Res.string.unlock_button_dismiss))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
```

OBS: `AccentCopper`, `HeroDeep`, `OnHero`, `TextPrimary` är teman-tokens i `composeApp/.../ui/theme/Color.kt`. Verifiera namn — Plan 5a refererade samma. Om saknas, importera från `MaterialTheme.colorScheme.primary` / `surface` etc.

- [ ] **Step 6: Skapa `BadgeStringMap.kt` (helper för name/description-resource-lookup)**

```kotlin
package se.birdy.app.ui.badges

import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.*  // alla badge_name_*, badge_desc_*
import org.jetbrains.compose.resources.StringResource

object BadgeStringMap {
    fun nameFor(badgeId: String): StringResource = when (badgeId) {
        "novice" -> Res.string.badge_name_novice
        "birder_bronze" -> Res.string.badge_name_birder_bronze
        "birder_silver" -> Res.string.badge_name_birder_silver
        "weekly_streak_4" -> Res.string.badge_name_weekly_streak_4
        "weekly_streak_12" -> Res.string.badge_name_weekly_streak_12
        "weekly_streak_26" -> Res.string.badge_name_weekly_streak_26
        "weekly_streak_52" -> Res.string.badge_name_weekly_streak_52
        "monthly_streak_3" -> Res.string.badge_name_monthly_streak_3
        "monthly_streak_6" -> Res.string.badge_name_monthly_streak_6
        "monthly_streak_12" -> Res.string.badge_name_monthly_streak_12
        "season_winter" -> Res.string.badge_name_season_winter
        "season_spring" -> Res.string.badge_name_season_spring
        "season_summer" -> Res.string.badge_name_season_summer
        "season_autumn" -> Res.string.badge_name_season_autumn
        "family_anatidae" -> Res.string.badge_name_family_anatidae
        "family_paridae" -> Res.string.badge_name_family_paridae
        "family_accipitridae" -> Res.string.badge_name_family_accipitridae
        "family_corvidae" -> Res.string.badge_name_family_corvidae
        "family_fringillidae" -> Res.string.badge_name_family_fringillidae
        "family_turdidae" -> Res.string.badge_name_family_turdidae
        "family_sylviidae" -> Res.string.badge_name_family_sylviidae
        "family_picidae" -> Res.string.badge_name_family_picidae
        "rare_first" -> Res.string.badge_name_rare_first
        "rare_5" -> Res.string.badge_name_rare_5
        "rare_10" -> Res.string.badge_name_rare_10
        else -> error("No name resource for badgeId=$badgeId")
    }

    fun descriptionFor(badgeId: String): StringResource = when (badgeId) {
        "novice" -> Res.string.badge_desc_novice
        "birder_bronze" -> Res.string.badge_desc_birder_bronze
        "birder_silver" -> Res.string.badge_desc_birder_silver
        "weekly_streak_4" -> Res.string.badge_desc_weekly_streak_4
        "weekly_streak_12" -> Res.string.badge_desc_weekly_streak_12
        "weekly_streak_26" -> Res.string.badge_desc_weekly_streak_26
        "weekly_streak_52" -> Res.string.badge_desc_weekly_streak_52
        "monthly_streak_3" -> Res.string.badge_desc_monthly_streak_3
        "monthly_streak_6" -> Res.string.badge_desc_monthly_streak_6
        "monthly_streak_12" -> Res.string.badge_desc_monthly_streak_12
        "season_winter" -> Res.string.badge_desc_season_winter
        "season_spring" -> Res.string.badge_desc_season_spring
        "season_summer" -> Res.string.badge_desc_season_summer
        "season_autumn" -> Res.string.badge_desc_season_autumn
        "family_anatidae" -> Res.string.badge_desc_family_anatidae
        "family_paridae" -> Res.string.badge_desc_family_paridae
        "family_accipitridae" -> Res.string.badge_desc_family_accipitridae
        "family_corvidae" -> Res.string.badge_desc_family_corvidae
        "family_fringillidae" -> Res.string.badge_desc_family_fringillidae
        "family_turdidae" -> Res.string.badge_desc_family_turdidae
        "family_sylviidae" -> Res.string.badge_desc_family_sylviidae
        "family_picidae" -> Res.string.badge_desc_family_picidae
        "rare_first" -> Res.string.badge_desc_rare_first
        "rare_5" -> Res.string.badge_desc_rare_5
        "rare_10" -> Res.string.badge_desc_rare_10
        else -> error("No description resource for badgeId=$badgeId")
    }
}
```

(Build-time `validateBadgeStrings`-task i Task 14 säkerställer att alla 25 IDs har keys.)

- [ ] **Step 7: assembleDebug + ktlint**

Run:
```bash
./gradlew :composeApp:assembleDebug
./gradlew ktlintCheck detekt
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/UnlockQueue.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/UnlockBottomSheet.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/formatRelativeBadgeDate.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeStringMap.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/UnlockQueueTest.kt
git commit -m "feat(badges): Plan 5b Task 10 — UnlockQueue + UnlockBottomSheet + glow animation"
```

---

## Task 11: `BadgesViewModel` + `BadgesUiState` + tester

`BadgesViewModel` = `combine(observations, unlocks, totalSpecies)` → `BadgesUiState.Loaded`. Implicit-opt-in: streak < 2 → `null`. `currentValue`-helper för låsta märkens progress. `Locked.sortedWith(compareBy(category.order, target))`.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesUiState.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgesViewModelTest.kt`

- [ ] **Step 1: Skapa `BadgesUiState.kt`**

```kotlin
package se.birdy.app.ui.badges

import kotlinx.datetime.Instant
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeProgress

sealed interface BadgesUiState {
    data object Loading : BadgesUiState

    data class Loaded(
        val speciesProgress: SpeciesProgress,
        val unlockedCount: Int,
        val totalBadges: Int,
        val weeklyStreak: Int?,                          // null = dölj pillet
        val monthlyStreak: Int?,
        val recentlyUnlocked: List<BadgeWithUnlock>,     // upp till 5 senaste, DESC
        val locked: List<BadgeProgress>,                 // alla låsta, sorterade
    ) : BadgesUiState

    data class Error(val kind: BadgeErrorKind) : BadgesUiState
}

data class SpeciesProgress(val seen: Int, val total: Int)
data class BadgeWithUnlock(val badge: Badge, val unlockedAt: Instant)

enum class BadgeErrorKind { CatalogParseFailed, LoadFailed }
```

- [ ] **Step 2: Skriv failing test för `BadgesViewModel`**

```kotlin
package se.birdy.app.ui.badges

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.domain.badge.*
import se.birdy.domain.observation.Observation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BadgesViewModelTest {

    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `empty state — 0 unlocks 0 obs`() = runTest {
        val vm = makeVm(observations = emptyList(), unlocks = emptyList(), totalSpecies = 700)
        vm.state.test {
            // Skip Loading
            while (awaitItem() is BadgesUiState.Loading) { /* spin */ }
            val loaded = expectMostRecentItem() as BadgesUiState.Loaded
            assertEquals(SpeciesProgress(seen = 0, total = 700), loaded.speciesProgress)
            assertEquals(0, loaded.unlockedCount)
            assertEquals(emptyList(), loaded.recentlyUnlocked)
            assertNull(loaded.weeklyStreak)
            assertNull(loaded.monthlyStreak)
            assertTrue(loaded.locked.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recentlyUnlocked sorted DESC and capped to 5`() = runTest {
        val unlocks = (1..7).map { i ->
            BadgeUnlock("u$i", Instant.fromEpochMilliseconds(1_000L + i))
        }
        val catalog = BadgeCatalog(
            version = 1,
            badges = unlocks.map { Badge(it.badgeId, BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(1)) },
        )
        val vm = makeVm(observations = emptyList(), unlocks = unlocks, totalSpecies = 700, catalog = catalog)

        vm.state.test {
            while (awaitItem() is BadgesUiState.Loading) { /* spin */ }
            val loaded = expectMostRecentItem() as BadgesUiState.Loaded
            assertEquals(5, loaded.recentlyUnlocked.size)
            assertEquals("u7", loaded.recentlyUnlocked[0].badge.id)
            assertEquals("u3", loaded.recentlyUnlocked[4].badge.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `weekly streak hidden below 2`() = runTest {
        val obs = listOf(observation("Q1", 2026, 5, 7))
        val vm = makeVm(observations = obs, unlocks = emptyList(), totalSpecies = 700)
        vm.state.test {
            while (awaitItem() is BadgesUiState.Loading) { /* spin */ }
            val loaded = expectMostRecentItem() as BadgesUiState.Loaded
            assertNull(loaded.weeklyStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `weekly streak visible at 2 (implicit opt-in)`() = runTest {
        val obs = listOf(
            observation("Q1", 2026, 5, 4),    // v19
            observation("Q1", 2026, 5, 11),   // v20
        )
        val vm = makeVm(observations = obs, unlocks = emptyList(), totalSpecies = 700)
        vm.state.test {
            while (awaitItem() is BadgesUiState.Loading) { /* spin */ }
            val loaded = expectMostRecentItem() as BadgesUiState.Loaded
            assertEquals(2, loaded.weeklyStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `error state when observe flow throws`() = runTest {
        // Skapa repo som throws — använd vm direkt mockade Flows
        TODO("skapa minimal test för Error.LoadFailed via Flow.catch — använd flowOf-throw eller anpassad fake")
    }

    private fun observation(speciesId: String, year: Int, month: Int, day: Int): Observation {
        val capturedAt = LocalDateTime(year, month, day, 12, 0).toInstant(TimeZone.UTC)
        return Observation(
            id = "obs-$speciesId-$year$month$day",
            speciesId = speciesId,
            capturedAt = capturedAt,
            savedAt = capturedAt,
            photoPath = "/tmp/$speciesId.jpg",
            note = "",
            confidence = 0.9f,
            latitude = null, longitude = null, locationLabel = null,
        )
    }

    private fun makeVm(
        observations: List<Observation>,
        unlocks: List<BadgeUnlock>,
        totalSpecies: Int,
        catalog: BadgeCatalog = BadgeCatalog(
            version = 1,
            badges = listOf(
                Badge("novice", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(5)),
                Badge("birder_bronze", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(25)),
            ),
        ),
    ): BadgesViewModel {
        val obsRepo = FakeObservationRepository().apply { observations.forEach(::seedDirect) }
        val badgeRepo = FakeBadgeRepository().apply { seedUnlocks(unlocks) }
        val recalc = se.birdy.app.badges.RecalculateBadgesUseCase(zone = TimeZone.UTC)
        return BadgesViewModel(
            obsRepo = obsRepo,
            badgeRepo = badgeRepo,
            speciesByQid = { emptyMap() },
            speciesTotalCount = flowOf(totalSpecies),
            catalog = catalog,
            recalc = recalc,
            zone = TimeZone.UTC,
            locale = Locale.SV,
        )
    }
}

// FakeObservationRepository.seedDirect helper — om saknas, lägg till
```

(Subagent ska ersätta `TODO("...")` med en konkret error-test eller flagga för uppföljning.)

- [ ] **Step 3: Implementera `BadgesViewModel.kt`**

```kotlin
package se.birdy.app.ui.badges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.content.Locale
import se.birdy.content.Species
import se.birdy.content.SpeciesId
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeProgress
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.badge.longestMonthlyStreak
import se.birdy.domain.badge.longestWeeklyStreak
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository

class BadgesViewModel(
    private val obsRepo: ObservationRepository,
    private val badgeRepo: BadgeRepository,
    private val speciesByQid: suspend () -> Map<SpeciesId, Species>,
    private val speciesTotalCount: Flow<Int>,
    private val catalog: BadgeCatalog,
    private val recalc: RecalculateBadgesUseCase,
    private val zone: TimeZone,
    private val locale: Locale,
) : ViewModel() {

    val state: StateFlow<BadgesUiState> = combine(
        obsRepo.observeAll(),
        badgeRepo.observeUnlocks(),
        speciesTotalCount,
    ) { observations, unlocks, totalSpecies ->
        val species = speciesByQid()
        buildLoaded(observations, unlocks, totalSpecies, species)
    }
        .map<BadgesUiState, BadgesUiState> { it }
        .onStart { emit(BadgesUiState.Loading) }
        .catch { emit(BadgesUiState.Error(BadgeErrorKind.LoadFailed)) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BadgesUiState.Loading,
        )

    private fun buildLoaded(
        observations: List<Observation>,
        unlocks: List<BadgeUnlock>,
        totalSpecies: Int,
        speciesMap: Map<SpeciesId, Species>,
    ): BadgesUiState.Loaded {
        val seenSpecies = observations.map { it.speciesId }.toSet().size
        val unlockedIds = unlocks.map { it.badgeId }.toSet()
        val capturedInstants = observations.map { it.capturedAt }

        val recentlyUnlocked = unlocks
            .sortedByDescending { it.unlockedAt }
            .take(5)
            .mapNotNull { u ->
                catalog.findById(u.badgeId)?.let { b -> BadgeWithUnlock(b, u.unlockedAt) }
            }

        val locked = catalog.badges
            .filter { it.id !in unlockedIds }
            .map { b ->
                BadgeProgress(
                    badge = b,
                    current = recalc.currentValue(b.rule, observations, speciesMap),
                    target = b.rule.target,
                    unlock = null,
                )
            }
            .sortedWith(compareBy({ it.badge.category.order }, { it.badge.rule.target }))

        val weeklyStreak = longestWeeklyStreak(capturedInstants, zone).takeIf { it >= 2 }
        val monthlyStreak = longestMonthlyStreak(capturedInstants, zone).takeIf { it >= 2 }

        return BadgesUiState.Loaded(
            speciesProgress = SpeciesProgress(seen = seenSpecies, total = totalSpecies),
            unlockedCount = unlocks.size,
            totalBadges = catalog.badges.size,
            weeklyStreak = weeklyStreak,
            monthlyStreak = monthlyStreak,
            recentlyUnlocked = recentlyUnlocked,
            locked = locked,
        )
    }
}
```

- [ ] **Step 4: Lägg till `seedDirect`-helper i `FakeObservationRepository` om saknas**

```kotlin
fun seedDirect(obs: Observation) {
    // Append direkt utan trip via insert() — för test-setup där VM combine subscribar.
    // Use existing internal mutable-state-flow.
}
```

- [ ] **Step 5: Kör testerna**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*BadgesViewModelTest*"`
Expected: PASS — minst Empty/RecentlyUnlocked/WeeklyStreak-tester gröna. Error-test får vara `Ignore` om Flow-throw är komplicerat — då noteras i Task 13's review.

- [ ] **Step 6: ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesUiState.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgesViewModelTest.kt
git commit -m "feat(badges): Plan 5b Task 11 — BadgesViewModel + UiState + combine flows"
```

---

## Task 12: `BadgesScreen` UI (hero + carousel + grid + locked-tap-snackbar)

Plan 5b §6.1 + §6.2-spec. Bottom-nav-fliken `Märken` ska peka på en riktig `BadgesScreen` (ersätter `BadgesStubScreen` — sker i Task 13). Den här tasken levererar UI-komponenterna och själva `BadgesScreen`-screenen som konsumerar `BadgesUiState` från Task 11. Tap-on-locked → `Snackbar` med `badges_locked_tooltip`. Tap-on-unlocked → `UnlockBottomSheet(isCelebration = false)`.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeStatHero.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeCard.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeRecentCard.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/formatRelativeBadgeDate.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt`
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/FormatRelativeBadgeDateTest.kt`

- [ ] **Step 1: Skapa `formatRelativeBadgeDate.kt` med tester**

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/formatRelativeBadgeDate.kt`:

```kotlin
package se.birdy.app.ui.badges

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.content.Locale

/**
 * "3 maj" (sv) / "May 3" (en). Helper för Recent-cards och bottom-sheet-datum.
 * Stannar på samma år: visar "3 maj 2025" om året skiljer sig från `now`.
 */
fun formatRelativeBadgeDate(
    instant: Instant,
    now: Instant,
    zone: TimeZone,
    locale: Locale,
): String {
    val ldt = instant.toLocalDateTime(zone)
    val nowLdt = now.toLocalDateTime(zone)
    val day = ldt.dayOfMonth
    val month = monthShort(ldt.monthNumber, locale)
    val sameYear = ldt.year == nowLdt.year
    return when (locale) {
        Locale.SV -> if (sameYear) "$day $month" else "$day $month ${ldt.year}"
        Locale.EN -> if (sameYear) "$month $day" else "$month $day, ${ldt.year}"
    }
}

private fun monthShort(month: Int, locale: Locale): String = when (locale) {
    Locale.SV -> listOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "aug", "sep", "okt", "nov", "dec")[month - 1]
    Locale.EN -> listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[month - 1]
}
```

`composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/FormatRelativeBadgeDateTest.kt`:

```kotlin
package se.birdy.app.ui.badges

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import se.birdy.content.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatRelativeBadgeDateTest {
    private val utc = TimeZone.UTC

    @Test
    fun `sv same year returns dag mån`() {
        val now = LocalDateTime(2026, 5, 6, 12, 0).toInstant(utc)
        val target = LocalDateTime(2026, 5, 3, 12, 0).toInstant(utc)
        assertEquals("3 maj", formatRelativeBadgeDate(target, now, utc, Locale.SV))
    }

    @Test
    fun `sv different year includes year`() {
        val now = LocalDateTime(2027, 1, 5, 12, 0).toInstant(utc)
        val target = LocalDateTime(2026, 12, 30, 12, 0).toInstant(utc)
        assertEquals("30 dec 2026", formatRelativeBadgeDate(target, now, utc, Locale.SV))
    }

    @Test
    fun `en same year returns Mon Day`() {
        val now = LocalDateTime(2026, 5, 6, 12, 0).toInstant(utc)
        val target = LocalDateTime(2026, 5, 3, 12, 0).toInstant(utc)
        assertEquals("May 3", formatRelativeBadgeDate(target, now, utc, Locale.EN))
    }

    @Test
    fun `en different year appends comma year`() {
        val now = LocalDateTime(2027, 1, 5, 12, 0).toInstant(utc)
        val target = LocalDateTime(2026, 12, 30, 12, 0).toInstant(utc)
        assertEquals("Dec 30, 2026", formatRelativeBadgeDate(target, now, utc, Locale.EN))
    }
}
```

- [ ] **Step 2: Kör testet**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*FormatRelativeBadgeDateTest*"`
Expected: PASS — alla 4 tester gröna.

- [ ] **Step 3: Skapa `BadgeStatHero.kt`**

```kotlin
package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badges_label_badges
import birdy_bird_scanner.composeapp.generated.resources.badges_label_monthly_streak
import birdy_bird_scanner.composeapp.generated.resources.badges_label_species_seen
import birdy_bird_scanner.composeapp.generated.resources.badges_label_weekly_streak
import birdy_bird_scanner.composeapp.generated.resources.badges_progress_format
import org.jetbrains.compose.resources.stringResource

@Composable
fun BadgeStatHero(
    seenSpecies: Int,
    totalSpecies: Int,
    unlockedCount: Int,
    totalBadges: Int,
    weeklyStreak: Int?,
    monthlyStreak: Int?,
    modifier: Modifier = Modifier,
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF5C6E48), Color(0xFF3F4F30)),
    )
    val onHero = Color(0xFFF0EAD8)
    val labelColor = Color(0xFFC5BC9F)
    val accent = Color(0xFF8C5A3C)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(gradient)
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 22.dp),
    ) {
        Text(
            text = stringResource(Res.string.badges_label_species_seen).uppercase(),
            color = labelColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$seenSpecies",
                color = onHero,
                fontSize = 42.sp,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Normal),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "/ $totalSpecies",
                color = labelColor,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (totalSpecies > 0) (seenSpecies.toFloat() / totalSpecies).coerceAtMost(1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = accent,
            trackColor = onHero.copy(alpha = 0.2f),
        )
        Spacer(Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            HeroPill(
                value = stringResource(Res.string.badges_progress_format, unlockedCount, totalBadges),
                label = stringResource(Res.string.badges_label_badges),
                modifier = Modifier.weight(1f),
            )
            if (weeklyStreak != null) {
                HeroPill(
                    value = "${weeklyStreak}v",
                    label = stringResource(Res.string.badges_label_weekly_streak),
                    modifier = Modifier.weight(1f),
                )
            }
            if (monthlyStreak != null) {
                HeroPill(
                    value = "${monthlyStreak}m",
                    label = stringResource(Res.string.badges_label_monthly_streak),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val onHero = Color(0xFFF0EAD8)
    val labelColor = Color(0xFFC5BC9F)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(onHero.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
    ) {
        Text(value, color = onHero, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            label.uppercase(),
            color = labelColor,
            fontSize = 8.sp,
            letterSpacing = 0.8.sp,
        )
    }
}

// ===== Width helper =====
private fun Modifier.width(dp: androidx.compose.ui.unit.Dp) = this.then(Modifier.padding(start = dp))
```

OBS: `Spacer(Modifier.width(...))` — föredra `androidx.compose.foundation.layout.width`-importen. Subagent ska byta `private fun Modifier.width` till `import androidx.compose.foundation.layout.width` om det är konventionen i projektets övriga filer (Plan 5a/4a). Verifiera via Grep `import androidx.compose.foundation.layout.width` i `composeApp/`.

- [ ] **Step 4: Skapa `BadgeCard.kt`**

```kotlin
package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeProgress

@Composable
fun BadgeCard(
    progress: BadgeProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUnlocked = progress.isUnlocked
    val bg = if (isUnlocked) Color(0xFF8C5A3C) else Color(0xFFD8D0BC)
    val fg = if (isUnlocked) Color(0xFFF0EAD8) else Color(0xFF6B6F5C)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = badgeGlyph(progress.badge, isUnlocked),
            color = fg,
            fontSize = if (isUnlocked) 22.sp else 18.sp,
        )
    }
}

private fun badgeGlyph(badge: Badge, unlocked: Boolean): String {
    if (!unlocked) return "?"
    // Minimalt: kategori-glyf. Plan 6 byter mot fulla bird-ikoner.
    return when (badge.category) {
        se.birdy.domain.badge.BadgeCategory.PROGRESSION -> "★"
        se.birdy.domain.badge.BadgeCategory.STREAK_WEEKLY -> "▲"
        se.birdy.domain.badge.BadgeCategory.STREAK_MONTHLY -> "▼"
        se.birdy.domain.badge.BadgeCategory.SEASON -> "❅"
        se.birdy.domain.badge.BadgeCategory.FAMILY -> "●"
        se.birdy.domain.badge.BadgeCategory.RARE -> "◆"
    }
}
```

OBS: `androidx.compose.ui.unit.dp`-importen krävs för `RoundedCornerShape(12.dp)`. Subagent ska lägga till `import androidx.compose.ui.unit.dp`.

- [ ] **Step 5: Skapa `BadgeRecentCard.kt`**

```kotlin
package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge

@Composable
fun BadgeRecentCard(
    badge: Badge,
    unlockedAt: Instant,
    locale: Locale,
    zone: TimeZone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    nowProvider: () -> Instant = { Clock.System.now() },
) {
    val labelColor = Color(0xFF6B6F5C)
    val textColor = Color(0xFF2A3525)

    Column(
        modifier = modifier
            .width(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFD8D0BC))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF8C5A3C)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "★", color = Color(0xFFF0EAD8), fontSize = 22.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = badge.id, // ersätts med stringResource(badge_name_<id>) i UI-anropssite
            color = textColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatRelativeBadgeDate(unlockedAt, nowProvider(), zone, locale),
            color = labelColor,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

OBS: `badge.id`-text ovan ersätts av `stringResource` på call-site i `BadgesScreen` — `BadgeRecentCard` tar in en pre-resolverad `localizedName: String`-param istället. Refactor i Step 6.

- [ ] **Step 6: Skapa `BadgesScreen.kt` (huvud-screen + Snackbar-host + grid)**

```kotlin
package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badges_load_error
import birdy_bird_scanner.composeapp.generated.resources.badges_load_error_retry
import birdy_bird_scanner.composeapp.generated.resources.badges_locked_tooltip
import birdy_bird_scanner.composeapp.generated.resources.badges_section_recently_unlocked
import birdy_bird_scanner.composeapp.generated.resources.badges_section_to_discover
import birdy_bird_scanner.composeapp.generated.resources.badges_title
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeUnlock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreen(
    state: BadgesUiState,
    locale: Locale,
    zone: TimeZone,
    onBadgeClick: (Badge, BadgeUnlock?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lockedTooltip = stringResource(Res.string.badges_locked_tooltip)
    val heroBg = Color(0xFF5C6E48)
    val onHero = Color(0xFFF0EAD8)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.badges_title),
                        fontFamily = FontFamily.Serif,
                        fontSize = 22.sp,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = heroBg,
                    titleContentColor = onHero,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (state) {
            is BadgesUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("…", color = MaterialTheme.colorScheme.onBackground)
            }

            is BadgesUiState.Error -> ErrorState(
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            is BadgesUiState.Loaded -> LoadedContent(
                state = state,
                locale = locale,
                zone = zone,
                contentPadding = padding,
                onUnlockedClick = { badge, unlock -> onBadgeClick(badge, unlock) },
                onLockedClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar(message = lockedTooltip)
                    }
                },
            )
        }
    }
}

@Composable
private fun LoadedContent(
    state: BadgesUiState.Loaded,
    locale: Locale,
    zone: TimeZone,
    contentPadding: PaddingValues,
    onUnlockedClick: (Badge, BadgeUnlock) -> Unit,
    onLockedClick: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            BadgeStatHero(
                seenSpecies = state.speciesProgress.seen,
                totalSpecies = state.speciesProgress.total,
                unlockedCount = state.unlockedCount,
                totalBadges = state.totalBadges,
                weeklyStreak = state.weeklyStreak,
                monthlyStreak = state.monthlyStreak,
            )
        }

        if (state.recentlyUnlocked.isNotEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 12.dp)) {
                    SectionLabel(stringResource(Res.string.badges_section_recently_unlocked))
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.recentlyUnlocked.size) { i ->
                            val r = state.recentlyUnlocked[i]
                            BadgeRecentCard(
                                badge = r.badge,
                                unlockedAt = r.unlockedAt,
                                locale = locale,
                                zone = zone,
                                onClick = { onUnlockedClick(r.badge, BadgeUnlock(r.badge.id, r.unlockedAt)) },
                            )
                        }
                    }
                }
            }
        }

        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 14.dp)) {
                SectionLabel(stringResource(Res.string.badges_section_to_discover, state.locked.size))
                Spacer(Modifier.height(6.dp))
            }
        }

        items(state.locked.size) { i ->
            val p = state.locked[i]
            BadgeCard(progress = p, onClick = onLockedClick)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFF6B6F5C),
        fontSize = 9.sp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Text(stringResource(Res.string.badges_load_error))
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.TextButton(onClick = onRetry) {
            Text(stringResource(Res.string.badges_load_error_retry))
        }
    }
}
```

OBS: `LazyRow.items(count) { ... }` är korrekt API i Compose 1.7.3 (`androidx.compose.foundation.lazy.items`). Subagent kan behöva lägga till `import androidx.compose.foundation.lazy.items`.

- [ ] **Step 7: Verifiera att `BadgesScreen` renderar — bygg `:composeApp:assembleDebug`**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL. Inga unresolved-references.

- [ ] **Step 8: ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeStatHero.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeCard.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeRecentCard.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/formatRelativeBadgeDate.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/FormatRelativeBadgeDateTest.kt
git commit -m "feat(badges): Plan 5b Task 12 — BadgesScreen UI (hero + carousel + grid)"
```

---

## Task 13: Wire navigation, AppGraph, Save-flow + ClassificationResultScreen unlocks

Plan 5a Task 11-mönstret återanvänds. `Märken`-fliken pekar på `BadgesScreen` (ersätter `BadgesStubScreen`). `App.kt` (eller motsv. composable rot) kör `LaunchedEffect(Unit) { backfill.runIfNeeded() }` en gång per app-process. `ClassificationResultViewModel` använder nu `SaveResult` och enqueue:ar nya unlocks i `UnlockQueue`. `ClassificationResultScreen` renderar `UnlockBottomSheet` ovanpå Save-snackbar:n. Save-CTA är disabled så länge `unlockQueue.size > 0`.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`
- Delete: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BadgesStubScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/App.kt` *(eller motsv. composable rot)*
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/result/ClassificationResultUiState.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/result/ClassificationResultViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/result/ClassificationResultScreen.kt`
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/result/ClassificationResultViewModelTest.kt`
- Modify: `androidApp/src/main/kotlin/.../MainActivity.kt` *(eller App.kt)*
- Modify: `androidApp/build.gradle.kts` *(om nya transitive deps krävs)*

- [ ] **Step 1: Hitta nuvarande AppGraph/AppScaffold/App-rot**

Run (Grep): `class AppGraph|fun rememberAppGraph|interface AppGraph` i `composeApp/`.
Expected: en eller flera filer hittas. Verifiera nuvarande wiring av `SaveObservationUseCase` + `ObservationRepository` (Plan 5a). Anteckna pattern.

Run (Grep): `BadgesStubScreen` i `composeApp/`.
Expected: en eller flera referenser i `AppScaffold.kt` (sannolikt en `when (selectedTab)`-branch).

- [ ] **Step 2: Uppdatera `ClassificationResultUiState.kt`**

Lägg till `pendingUnlocks` + `saveCtaEnabled`:

```kotlin
sealed interface ClassificationResultUiState {
    data class Loaded(
        // ... befintliga fields från Plan 4a/5a
        val saveState: SaveState = SaveState.Idle,
        val pendingUnlock: BadgeUnlock? = null,   // NYTT — current i queue
        val unlockQueueSize: Int = 0,             // NYTT — disable Save-CTA om > 0
    ) : ClassificationResultUiState
    // ... Loading / Error / FrameUnavailable
}

enum class SaveState { Idle, Saving, Saved, Failed }
```

- [ ] **Step 3: Uppdatera `ClassificationResultViewModel.kt`**

```kotlin
class ClassificationResultViewModel(
    private val saveUseCase: SaveObservationUseCase,
    // ... existerande Plan 4a/5a-deps
) : ViewModel() {

    private val unlockQueue = UnlockQueue()
    private val saveStateFlow = MutableStateFlow(SaveState.Idle)

    val state: StateFlow<ClassificationResultUiState> = combine(
        // ... existerande Flows
        saveStateFlow,
        unlockQueue.current,
        unlockQueue.size,
    ) { /* args */ ->
        // ... existerande logik
        Loaded(..., saveState = ..., pendingUnlock = ..., unlockQueueSize = ...)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)

    fun saveToDiary(input: SaveInput) {
        viewModelScope.launch {
            saveStateFlow.value = SaveState.Saving
            val result = runCatching { saveUseCase.save(input) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            if (result != null) {
                saveStateFlow.value = SaveState.Saved
                if (result.newUnlocks.isNotEmpty()) {
                    unlockQueue.enqueue(result.newUnlocks)
                }
            } else {
                saveStateFlow.value = SaveState.Failed
            }
        }
    }

    fun dismissUnlock() = unlockQueue.pop()
}
```

OBS: signaturen `saveUseCase.save(input)` returnerar nu `SaveResult` (Task 8). Plan 5a använde `String`. Subagent ska läsa nuvarande call-site och ersätta `val id = saveUseCase.save(...)` med `val result = saveUseCase.save(...)` + använd `result.observationId` där `id` användes.

- [ ] **Step 4: Skriv test för enqueue + disabled-CTA**

`composeApp/src/commonTest/kotlin/se/birdy/app/ui/result/ClassificationResultViewModelTest.kt` (utöka):

```kotlin
@Test
fun `saveToDiary enqueues new unlocks and disables CTA`() = runTest {
    val savedId = "obs-1"
    val unlocks = listOf(BadgeUnlock("novice", Instant.fromEpochMilliseconds(1_700_000_000_000)))
    val fakeUseCase = object : SaveObservationUseCase(/* deps */) {
        override suspend fun save(input: SaveInput): SaveResult =
            SaveResult(observationId = savedId, newUnlocks = unlocks)
    }
    val vm = ClassificationResultViewModel(saveUseCase = fakeUseCase, /* ... */)

    vm.saveToDiary(/* sample input */)

    vm.state.test {
        val loaded = expectMostRecentItem() as ClassificationResultUiState.Loaded
        assertEquals(SaveState.Saved, loaded.saveState)
        assertEquals(1, loaded.unlockQueueSize)
        assertEquals("novice", loaded.pendingUnlock?.badgeId)
        cancelAndIgnoreRemainingEvents()
    }

    vm.dismissUnlock()
    vm.state.test {
        val loaded = expectMostRecentItem() as ClassificationResultUiState.Loaded
        assertEquals(0, loaded.unlockQueueSize)
        assertNull(loaded.pendingUnlock)
        cancelAndIgnoreRemainingEvents()
    }
}
```

- [ ] **Step 5: Kör testet**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ClassificationResultViewModelTest*"`
Expected: PASS — nya tester gröna.

- [ ] **Step 6: Uppdatera `ClassificationResultScreen.kt`**

Lägg till `UnlockBottomSheet` ovanpå Save-snackbar. Save-CTA är disabled när `state.unlockQueueSize > 0`.

```kotlin
@Composable
fun ClassificationResultScreen(
    state: ClassificationResultUiState,
    onSave: (SaveInput) -> Unit,
    onDismissUnlock: () -> Unit,
    locale: Locale,
    zone: TimeZone,
    /* ... existing nav callbacks */
) {
    /* ... existing scaffold/snackbar */

    when (state) {
        is ClassificationResultUiState.Loaded -> {
            /* ... existing Loaded UI */

            // Save-CTA — disabled mid-queue
            Button(
                onClick = { onSave(buildSaveInput()) },
                enabled = state.saveState == SaveState.Idle && state.unlockQueueSize == 0,
            ) { Text(stringResource(Res.string.result_save_cta)) }

            // UnlockBottomSheet ovanpå allt
            state.pendingUnlock?.let { unlock ->
                UnlockBottomSheet(
                    badge = catalog.findById(unlock.badgeId) ?: return@let,
                    unlockedAt = unlock.unlockedAt,
                    isCelebration = true,
                    locale = locale,
                    zone = zone,
                    onDismiss = onDismissUnlock,
                )
            }
        }
        /* ... other states */
    }
}
```

OBS: `catalog.findById(...)` förutsätter att UI:n får `BadgeCatalog` injicerat (via composition local eller param). Subagent passar in via `BadgesViewModel`-style — eller hellre via en shared `BadgeCatalogProvider`-composition-local i `App.kt`. Verifiera Plan 4a-mönster för "saved" vs "active" snackbar-overlay (`Box`-stack från Plan 5a Task 11 Step 7).

- [ ] **Step 7: Uppdatera `AppScaffold.kt`**

```kotlin
when (selectedTab) {
    AppTab.Scan -> ScanRoute(...)
    AppTab.Photo -> PhotoAnalyzeRoute(...)
    AppTab.Encyclopedia -> EncyclopediaRoute(...)
    AppTab.Diary -> DiaryRoute(...)
    AppTab.Badges -> BadgesRoute(
        viewModel = appGraph.badgesViewModel(),
        locale = locale,
        zone = zone,
        catalog = appGraph.badgeCatalog,
    )
}
```

`BadgesRoute` är ny composable wrapper:

```kotlin
@Composable
fun BadgesRoute(
    viewModel: BadgesViewModel,
    locale: Locale,
    zone: TimeZone,
    catalog: BadgeCatalog,
) {
    val state by viewModel.state.collectAsState()
    var bottomSheetUnlock by remember { mutableStateOf<Pair<Badge, Instant>?>(null) }

    BadgesScreen(
        state = state,
        locale = locale,
        zone = zone,
        onBadgeClick = { badge, unlock ->
            unlock?.let { bottomSheetUnlock = badge to it.unlockedAt }
        },
        onRetry = { /* trigger re-collect — viewModel.state är en hot Flow så bara wait */ },
    )

    bottomSheetUnlock?.let { (badge, at) ->
        UnlockBottomSheet(
            badge = badge,
            unlockedAt = at,
            isCelebration = false,
            locale = locale,
            zone = zone,
            onDismiss = { bottomSheetUnlock = null },
        )
    }
}
```

- [ ] **Step 8: Ta bort `BadgesStubScreen.kt`**

```bash
rm composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BadgesStubScreen.kt
```

Bekräfta att inga referenser återstår — Grep på `BadgesStubScreen`.

- [ ] **Step 9: Uppdatera `AppGraph.kt`**

Lägg till `BadgeRepository`, `BadgeCatalog`, `BadgeCatalogLoader`, `RecalculateBadgesUseCase`, `BadgeBackfillOnAppStart`, `BadgesViewModel`-factory + utöka `SaveObservationUseCase`-konstruktor:

```kotlin
class AppGraph(
    /* existing */
    private val context: Any,  // platform-specific (Context på Android)
) {
    val badgeCatalog: BadgeCatalog by lazy { runBlocking { BadgeCatalogLoader().load() } }

    val badgeRepository: BadgeRepository = BadgeRepositoryImpl(birdyData)
    val badgeVersionStore: BadgeVersionStore = BadgeVersionStore(context)

    val recalculateBadges: RecalculateBadgesUseCase by lazy { RecalculateBadgesUseCase() }

    val saveObservationUseCase: SaveObservationUseCase by lazy {
        SaveObservationUseCase(
            observationRepository = observationRepository,
            photoStorage = photoStorage,
            badgeRepository = badgeRepository,
            speciesRepository = speciesRepository,
            badgeCatalog = badgeCatalog,
            recalculate = recalculateBadges,
        )
    }

    val badgeBackfill: BadgeBackfillOnAppStart by lazy {
        BadgeBackfillOnAppStart(
            recalc = recalculateBadges,
            obsRepo = observationRepository,
            speciesRepo = speciesRepository,
            badgeRepo = badgeRepository,
            catalog = badgeCatalog,
            versionStore = badgeVersionStore,
        )
    }

    fun badgesViewModel(): BadgesViewModel = BadgesViewModel(
        obsRepo = observationRepository,
        badgeRepo = badgeRepository,
        speciesByQid = { speciesRepository.allByQid() },
        speciesTotalCount = speciesRepository.observeTotalCount(),
        catalog = badgeCatalog,
        recalc = recalculateBadges,
        zone = TimeZone.currentSystemDefault(),
        locale = currentLocale,
    )
}
```

- [ ] **Step 10: Uppdatera `App.kt` (composable rot) — backfill on launch**

```kotlin
@Composable
fun App(appGraph: AppGraph) {
    LaunchedEffect(Unit) {
        runCatching { appGraph.badgeBackfill.runIfNeeded() }
            .onFailure { if (it is CancellationException) throw it }
        // tyst fail — nästa Save eller nästa app-start gör om
    }
    /* existing scaffold */
}
```

- [ ] **Step 11: Initiera `BadgeVersionStore` i `MainActivity.kt`**

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appGraph = AppGraph.create(applicationContext)  // sätter Android Context på AppGraph
        setContent { App(appGraph) }
    }
}
```

`BadgeVersionStore.android.kt` använder `applicationContext.getSharedPreferences("birdy_badges", Context.MODE_PRIVATE)`.

- [ ] **Step 12: Verifiera att `androidApp/build.gradle.kts` har transitive deps**

Plan 5a-lärdom: `:androidApp` saknar transitiva deps eftersom composeApp använder `implementation()` inte `api()`. Kolla om Plan 5b adderade typer (Badge*, BadgeUnlock, BadgeRule) som androidApp behöver direkt-referera.

Run: `./gradlew :androidApp:installDebug`
Expected: BUILD SUCCESSFUL. Om unresolved — lägg till explicit `implementation(project(":shared:domain"))` i `androidApp/build.gradle.kts`.

- [ ] **Step 13: Kör hela test-suiten**

Run:
```bash
./gradlew :shared:domain:jvmTest :shared:data:jvmTest :shared:content:jvmTest :composeApp:testDebugUnitTest
```
Expected: alla gröna inklusive Plan 5a:s `ObservationRepositoryImplTest`, `DiaryViewModelTest`, `ObservationDetailViewModelTest`.

- [ ] **Step 14: ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 15: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/App.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/result/ \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/result/ClassificationResultViewModelTest.kt \
        androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt
git rm composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BadgesStubScreen.kt
git commit -m "feat(app): Plan 5b Task 13 — wire BadgesScreen + Save-unlock-flow + backfill"
```

---

## Task 14: Build-time validators — `validateBadgesYaml` + `validateBadgeStrings`

Två JavaExec gradle-tasks som körs som `dependsOn` på `preBuild`:
1. `validateBadgesYaml` — parsar `composeApp/composeResources/files/badges.yaml`, failar om malformed eller okända rule-typer.
2. `validateBadgeStrings` — för varje `id` i `badges.yaml`, kontrollera att `badge_name_<id>` och `badge_desc_<id>` finns i `composeResources/values/strings.xml` + `values-en/strings.xml`.

Mönster matchar `shared/content`s existerande `validateSpeciesData` JavaExec-task.

**Files:**
- Create: `tools/badges/CheckBadgesYaml.kt` (standalone main)
- Create: `tools/badges/CheckBadgeStrings.kt` (standalone main)
- Create: `tools/badges/build.gradle.kts` (om ny submodul) ELLER lägg till i existing `tools/`-modul
- Modify: `composeApp/build.gradle.kts` (registrera JavaExec-tasks)

- [ ] **Step 1: Hitta nuvarande `validateSpeciesData`-pattern**

Run: Grep `validateSpeciesData|JavaExec` i `shared/content/build.gradle.kts` och `tools/`.
Expected: hittar JavaExec + `tools/`-modul med `main { kotlin { srcDir(...) } }`.

- [ ] **Step 2: Skapa `tools/badges/CheckBadgesYaml.kt`**

```kotlin
// tools/badges/src/main/kotlin/se/birdy/tools/badges/CheckBadgesYaml.kt
package se.birdy.tools.badges

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val path = args.getOrNull(0) ?: error("usage: CheckBadgesYaml <yaml-path>")
    val file = File(path)
    require(file.exists()) { "YAML file not found: $path" }

    val text = file.readText()
    val node = Yaml.default.parseToYamlNode(text)
    val map = node.yamlMap

    val version = map.getScalar("version")?.toLong()
        ?: error("$path: missing 'version'")
    val badges = map.getList("badges")
        ?: error("$path: missing 'badges'")

    val ids = mutableSetOf<String>()
    val validRuleTypes = setOf(
        "count_unique_species", "weekly_streak", "monthly_streak",
        "observed_in_season", "observed_in_family", "observed_with_abundance",
    )
    val validSeasons = setOf("winter", "spring", "summer", "autumn")
    val validAbundance = setOf("allmän", "mindre_allmän", "ovanlig", "sällsynt")
    val validCategories = setOf("progression", "streak_weekly", "streak_monthly", "season", "family", "rare")

    var errors = 0
    badges.items.forEachIndexed { i, item ->
        val b = (item as? YamlNode)?.yamlMap ?: run { println("badges[$i]: not a map"); errors++; return@forEachIndexed }
        val id = b.getScalar("id")?.content
        val category = b.getScalar("category")?.content
        val rule = b.getMap("rule")
        when {
            id == null -> { println("badges[$i]: missing id"); errors++ }
            !ids.add(id) -> { println("badges[$i]: duplicate id $id"); errors++ }
            category == null || category !in validCategories ->
                { println("$id: invalid category '$category' (allowed: $validCategories)"); errors++ }
            rule == null -> { println("$id: missing rule"); errors++ }
            else -> {
                val type = rule.getScalar("type")?.content
                if (type !in validRuleTypes) {
                    println("$id: invalid rule type '$type'"); errors++
                }
                if (type == "observed_in_season") {
                    val season = rule.getScalar("season")?.content
                    if (season !in validSeasons) { println("$id: invalid season '$season'"); errors++ }
                }
                if (type == "observed_with_abundance") {
                    val abu = rule.getScalar("abundance")?.content
                    if (abu !in validAbundance) { println("$id: invalid abundance '$abu'"); errors++ }
                }
            }
        }
    }

    if (errors > 0) {
        println("VALIDATE FAILED: $errors errors in $path")
        exitProcess(1)
    }
    println("validateBadgesYaml OK — version=$version, ${badges.items.size} badges")
}

// kaml-API helpers
private fun com.charleskorn.kaml.YamlMap.getScalar(key: String): com.charleskorn.kaml.YamlScalar? =
    entries.entries.find { (k, _) -> k.content == key }?.value as? com.charleskorn.kaml.YamlScalar

private fun com.charleskorn.kaml.YamlMap.getList(key: String): com.charleskorn.kaml.YamlList? =
    entries.entries.find { (k, _) -> k.content == key }?.value as? com.charleskorn.kaml.YamlList

private fun com.charleskorn.kaml.YamlMap.getMap(key: String): com.charleskorn.kaml.YamlMap? =
    entries.entries.find { (k, _) -> k.content == key }?.value as? com.charleskorn.kaml.YamlMap
```

- [ ] **Step 3: Skapa `tools/badges/CheckBadgeStrings.kt`**

```kotlin
// tools/badges/src/main/kotlin/se/birdy/tools/badges/CheckBadgeStrings.kt
package se.birdy.tools.badges

import com.charleskorn.kaml.Yaml
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val yamlPath = args.getOrNull(0) ?: error("usage: CheckBadgeStrings <yaml-path> <strings-sv> <strings-en>")
    val svPath = args.getOrNull(1) ?: error("usage: CheckBadgeStrings <yaml-path> <strings-sv> <strings-en>")
    val enPath = args.getOrNull(2) ?: error("usage: CheckBadgeStrings <yaml-path> <strings-sv> <strings-en>")

    val ids = parseBadgeIds(File(yamlPath))
    val svKeys = parseStringsXmlKeys(File(svPath))
    val enKeys = parseStringsXmlKeys(File(enPath))

    var errors = 0
    ids.forEach { id ->
        val expected = listOf("badge_name_$id", "badge_desc_$id")
        expected.forEach { key ->
            if (key !in svKeys) { println("strings.xml (sv): missing $key"); errors++ }
            if (key !in enKeys) { println("strings.xml (en): missing $key"); errors++ }
        }
    }
    if (errors > 0) {
        println("VALIDATE FAILED: $errors missing keys")
        exitProcess(1)
    }
    println("validateBadgeStrings OK — ${ids.size} badges × 2 keys × 2 locales = ${ids.size * 4} keys verified")
}

private fun parseBadgeIds(file: File): List<String> {
    val node = Yaml.default.parseToYamlNode(file.readText()).yamlMap
    val list = node.entries.entries.find { (k, _) -> k.content == "badges" }?.value
        as? com.charleskorn.kaml.YamlList ?: return emptyList()
    return list.items.mapNotNull { item ->
        val map = (item as? com.charleskorn.kaml.YamlMap) ?: return@mapNotNull null
        val idScalar = map.entries.entries.find { (k, _) -> k.content == "id" }?.value
            as? com.charleskorn.kaml.YamlScalar
        idScalar?.content
    }
}

private fun parseStringsXmlKeys(file: File): Set<String> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    val nodes = doc.getElementsByTagName("string")
    return (0 until nodes.length).mapNotNull { i ->
        nodes.item(i).attributes.getNamedItem("name")?.nodeValue
    }.toSet()
}
```

- [ ] **Step 4: Lägg till `tools/badges/build.gradle.kts` (eller utöka existing `tools/`)**

Om existerande `tools/`-modul finns: lägg till `validateBadgesYaml`-target. Annars:

```kotlin
// tools/badges/build.gradle.kts
plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("com.charleskorn.kaml:kaml:0.65.0")
    implementation("xerces:xercesImpl:2.12.2")  // för XML-parsing
}
```

Verifiera mot existing `validateSpeciesData`-task — sannolikt finns redan en `tools/`-modul med kaml-deps.

- [ ] **Step 5: Registrera JavaExec-tasks i `composeApp/build.gradle.kts`**

```kotlin
val validateBadgesYaml = tasks.register<JavaExec>("validateBadgesYaml") {
    group = "verification"
    description = "Validate badges.yaml structure and rule types"
    mainClass.set("se.birdy.tools.badges.CheckBadgesYamlKt")
    classpath = project(":tools:badges").extensions.getByType<SourceSetContainer>().getByName("main").runtimeClasspath
    args = listOf(
        "$projectDir/src/commonMain/composeResources/files/badges.yaml",
    )
}

val validateBadgeStrings = tasks.register<JavaExec>("validateBadgeStrings") {
    group = "verification"
    description = "Validate badge_name_*/badge_desc_* keys in strings.xml"
    mainClass.set("se.birdy.tools.badges.CheckBadgeStringsKt")
    classpath = project(":tools:badges").extensions.getByType<SourceSetContainer>().getByName("main").runtimeClasspath
    args = listOf(
        "$projectDir/src/commonMain/composeResources/files/badges.yaml",
        "$projectDir/src/commonMain/composeResources/values/strings.xml",
        "$projectDir/src/commonMain/composeResources/values-en/strings.xml",
    )
}

tasks.named("preBuild") {
    dependsOn(validateBadgesYaml)
    dependsOn(validateBadgeStrings)
}
```

OBS: SourceSetContainer-API:t kan variera. Subagent verifierar mot existing `validateSpeciesData`-task i `shared/content/build.gradle.kts` (sannolikt mer korrekt syntax).

- [ ] **Step 6: Kör validators manuellt**

Run:
```bash
./gradlew :composeApp:validateBadgesYaml
./gradlew :composeApp:validateBadgeStrings
```
Expected: båda BUILD SUCCESSFUL och OK-meddelande i console.

- [ ] **Step 7: Verifiera att fail-fast-fall fungerar**

Manuellt:
- Lägg en bogus rule-typ i `badges.yaml` (`rule: { type: not_a_real_type, target: 5 }`).
- Run `:composeApp:validateBadgesYaml` → ska FAILA med tydligt felmeddelande.
- Återställ ändringen.

Manuellt:
- Ta bort en `badge_desc_<id>`-key från `values/strings.xml`.
- Run `:composeApp:validateBadgeStrings` → ska FAILA.
- Återställ ändringen.

(Subagent ska göra detta som sanity-check men inte committa de bogus ändringarna.)

- [ ] **Step 8: Verifiera `assembleDebug` triggar validators automatiskt**

Run: `./gradlew :composeApp:assembleDebug --info | grep -E "(validateBadgesYaml|validateBadgeStrings)"`
Expected: båda tasks listas som executerade pre-build.

- [ ] **Step 9: ktlint + detekt**

Run: `./gradlew ktlintCheck detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add tools/badges/ composeApp/build.gradle.kts
git commit -m "feat(build): Plan 5b Task 14 — validateBadgesYaml + validateBadgeStrings tasks"
```

---

## Task 15: Device verification + screenshots + CLAUDE.md + memory + tag

Plan 5a Task 12-mönstret. Bygger + installerar på Galaxy S23 Ultra (SM-S918B) + verifierar 10 flöden + tar 7 screenshots + uppdaterar CLAUDE.md + skriver auto-memory + taggar `v0.5.0b-gamification`.

**Files:**
- Modify: `CLAUDE.md`
- Modify: `~/.claude/projects/.../memory/project_plan_5b_status.md` (uppdatera till DONE-state)
- Modify: `~/.claude/projects/.../memory/MEMORY.md` (rad-summa)
- Create: 7 PNGs i `docs/superpowers/screenshots/`

- [ ] **Step 1: Bygg + installera + starta**

```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

- [ ] **Step 2: Verifiera empty-state Märken-fliken**

`adb shell pm clear se.birdy.android` → starta appen → tappa Märken-fliken.

Expected:
- Hero "ARTER SEDDA · 0 / 700".
- Märken-pille "0 / 25" (eller motsvarande från `badges_progress_format`).
- Streak-piller dolda (implicit opt-in).
- Sektion "ATT UPPTÄCKA · 25 KVAR" + 5×5 silhouett-grid med "?".

Skärmdump:
```bash
ADB="/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/2026-05-06-05b-badges-empty.png
```

- [ ] **Step 3: Verifiera locked-tap → snackbar**

Tap på en silhouett (frågetecken) → snackbar "Hemligt — fortsätt skåda" visas i 3 sek.

Skärmdump (timing kritiskt — använd `--repeat 5` om snackbar går snabbt):
```bash
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/2026-05-06-05b-locked-detail.png
```

- [ ] **Step 4: Verifiera Save-flow med unlocks**

Sätt `FakeBirdClassifier` att returnera 5 olika arter (eller manuellt simulera via 5 tap-to-freeze + Save i ScanScreen):

1. Scanna första arten → Save → snackbar "Sparad i dagboken" + INGEN bottom-sheet (inga unlocks ännu).
2. Scanna 4 till olika arter → Save 5:e → bottom-sheet "Nybörjare" pop:ar med koppar-glöd-animation. Tap "Härligt" → om "Familje-X" också unlocked, ny bottom-sheet pop:ar.

Skärmdump (mid-glow):
```bash
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/2026-05-06-05b-unlock-bottomsheet.png
```

Skärmdump (ResultScreen med snackbar + bottom-sheet samtidigt):
```bash
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/2026-05-06-05b-save-with-unlock.png
```

- [ ] **Step 5: Verifiera BadgesScreen efter unlock**

Tappa Märken-fliken → hero "ARTER SEDDA · 5 / 700" + märken-pille "1 / 25" + sektion "SENAST UPPTÄCKTA" med "Nybörjare"-card + sektion "ATT UPPTÄCKA · 24 KVAR" + grid med 1 koppar-cell + 24 silhouetter.

Skärmdump:
```bash
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/2026-05-06-05b-badges-loaded.png
```

- [ ] **Step 6: Verifiera implicit opt-in streak**

Manipulera tid (eller använd 2 olika veckor) → spara obs i v18 + v19 → öppna Märken-fliken.

Expected: V-streak-pille "2v" syns nu (var dolt innan 2 veckor).

Skärmdump:
```bash
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/2026-05-06-05b-badges-streak-grown.png
```

OBS: timezone-manipulation på riktig device är klumpigt. Alternativ: skapa en debug-only `seedFakeObservations`-utility-button (inte committad) eller använd `adb shell date` för att flytta klockan.

- [ ] **Step 7: Verifiera unlocked-tap → bottom-sheet utan glow**

Tappa "Nybörjare"-cell i grid:n eller carousel → `UnlockBottomSheet` pop:ar UTAN glow-animation (statisk koppar-cirkel). Datum visas korrekt.

Skärmdump:
```bash
"$ADB" exec-out screencap -p > docs/superpowers/screenshots/2026-05-06-05b-unlocked-detail.png
```

- [ ] **Step 8: Verifiera re-launch-persistence**

```bash
"$ADB" shell am force-stop se.birdy.android
"$ADB" shell am start -n se.birdy.android/.MainActivity
```

Tappa Märken-fliken → "Nybörjare"-märket finns kvar. Hero stats persisterade.

(Ingen skärmdump.)

- [ ] **Step 9: Verifiera Save-CTA disabled mid-queue**

Trigga 2+ unlocks samtidigt (om pre-conditions inte nås naturligt — manuell DB-manipulation eller debug-utility). Verifiera att Save-CTA är greyed-out så länge 2 bottom-sheets återstår i kön.

(Ingen skärmdump — verifieras via observation.)

- [ ] **Step 10: Committa screenshots**

```bash
git add docs/superpowers/screenshots/2026-05-06-05b-*.png
git commit -m "docs(plan-5b): device-verification screenshots"
```

- [ ] **Step 11: Uppdatera CLAUDE.md**

a) **Status-rad** (rad ~9):

```
**Status (2026-05-06):** Plan 1 ✅ (`v0.1.0-foundation`). Plan 2a ✅ (`v0.2.0a-pipeline`). Plan 2b ⏸ pausad vid 173/700, nästa familj = ardeidae. Plan 3 ✅ (`v0.3.0-encyclopedia`). Plan 4a ✅ (`v0.4.0a-camera-ui`). Plan 4b deferrad. Plan 5a ✅ (`v0.5.0a-diary`). **Plan 5b ✅ (`v0.5.0b-gamification`).** Nästa = Plan 6 (i18n + polish + Play release) eller fortsätt Plan 2b. Detaljer i auto-memory `project_plan_5b_status.md`.
```

b) **Plan-of-plans-tabell**: rad 5b → ✅ `v0.5.0b-gamification`.

c) **Avslutade planer (referens)** — lägg till entry:

```
- **Plan 5b (Gamification, `v0.5.0b-gamification`, 2026-05-06):** YAML-DSL-katalog (~25 märken i 6 kategorier) + `RecalculateBadgesUseCase` (pure, push-vid-Save) + ISO-8601-vecko-streaks + meteorologiska säsonger (SMHI) + `UnlockBottomSheet` med subtil koppar-glöd + `BadgesScreen` (hero + carousel + silhouett-grid). `BadgeBackfillOnAppStart` är tyst för retroaktiva unlocks. Plan: `2026-05-06-v1-05b-gamification.md`. **Återanvändbara mönster:** kaml-i-commonMain via `Res.readBytes` (compose-multiplatform-resources) ger runtime-YAML i KMP utan platform-specifik file-API; pure rule-engine över `(observations, speciesByQid, catalog, existing)` minimerar test-friction (alla rule-typer tabular-testas); `WeekKey/MonthKey`-ISO-helpers utan JVM-`WeekFields`-beroende (ren `kotlinx.datetime`); `UnlockQueue`-StateFlow med `MutableStateFlow<List<>>`-update + `current.firstOrNull()` ger sekventiella bottom-sheets utan race; `INSERT OR REPLACE` för upsert-semantik på `badge_unlock`-tabellen är trivialt idempotent; `BadgeVersionStore`-`expect/actual`-pattern för platform-state utan `EncryptedSharedPreferences` (värdet är ofarligt); `validateBadgesYaml`/`validateBadgeStrings`-gradle-tasks fångar i18n-drift i CI. **Bug-fix-lärdomar:** *(fylls in vid Task 15 review)*.
```

- [ ] **Step 12: Uppdatera auto-memory `project_plan_5b_status.md`**

Skriv över existerande `~/.claude/projects/C--Users-abbea-dev-birdy-bird-scanner/memory/project_plan_5b_status.md`:

```markdown
---
name: Plan 5b (Gamification) — DONE, tagged v0.5.0b-gamification
description: Plan 5b shipped 2026-05-06 as v0.5.0b-gamification. ~25-badge YAML catalog + push-at-Save recalc + UnlockBottomSheet; reusable patterns for Plan 6.
type: project
---

**Status (2026-05-06):** Plan 5b ✅ shipped as `v0.5.0b-gamification`. 7 screenshots committed. Working tree clean.

**Why:** Plan 5b lägger gamification-skiktet ovanpå Plan 5a:s diary. Ger entusiasterna progression-feedback utan att stressa nybörjare (hybrid tonad sport, dolda silhouetter, implicit opt-in streaks).

**How to apply:** When user says "continue with birdy" / "fortsätt":
1. Default next step = Plan 6 (i18n + polish + Play release) — needs new brainstorm/plan.
2. Alt next step = Plan 2b family-by-family (ardeidae) — runbook at `docs/superpowers/runbooks/2026-05-02-plan-2b-content-backfill.md`.
3. Verify `git log --oneline -3` shows the v0.5.0b-gamification tag.

**Locked architecture decisions (Plan 6 should reuse):**

- `BadgeRule` är `sealed interface` med 6 rule-typer + `target: Int`-property. Pure `evaluate(rule, obs, speciesMap): Boolean` + `currentValue(rule, obs, speciesMap): Int`. Alla rule-typer kan tabular-testas.
- ISO-8601 `WeekKey/MonthKey` i ren `kotlinx.datetime` — ingen JVM `WeekFields`-binding, fungerar i common.
- `RecalculateBadgesUseCase` lever i `composeApp/badges/` (avsteg från spec §3) — kräver `Species.family`/`Species.abundance` från `shared/content` som `shared/domain` inte importerar.
- `BadgeCatalogLoader` läser `composeApp/composeResources/files/badges.yaml` via `Res.readBytes` (compose-multiplatform-resources). kaml 0.65.0 i `composeApp/commonMain.dependencies`.
- `UnlockQueue` = `MutableStateFlow<List<BadgeUnlock>>` + `current = stateFlow.map { it.firstOrNull() }`. enqueue/pop-API. Ej persistat över app-restart.
- `SaveObservationUseCase.save(input): SaveResult` (ersätter Plan 5a:s `String`). `SaveResult(observationId, newUnlocks)`.
- `BadgeBackfillOnAppStart` är tyst (inga UnlockBottomSheets). Triggas av version-bump i `BadgeVersionStore` (SharedPreferences-`Int` på Android).
- Save-CTA disabled när `unlockQueueSize > 0` — race-prevention.
- Build-time `validateBadgesYaml` + `validateBadgeStrings` JavaExec-tasks. Fail-fast i CI om YAML-schema eller i18n-drift.
- `BadgeRepositoryImpl` använder `INSERT OR REPLACE` för idempotent persist — recalc kan re-emit:a redan-unlockade ID:n utan race.

**Post-tag pending follow-ups:**

- *(fylls i vid Task 15 review)*
```

- [ ] **Step 13: Uppdatera `MEMORY.md`-indexrad**

Ändra raden:
```
- [Plan 5b (Gamification) — IN PROGRESS](project_plan_5b_status.md) — spec done 2026-05-06 commit 7714523; 16 locked decisions; next = writing-plans-skill
```
till:
```
- [Plan 5b (Gamification) — DONE](project_plan_5b_status.md) — shipped v0.5.0b-gamification 2026-05-06; YAML-katalog + push-vid-Save + UnlockBottomSheet
```

- [ ] **Step 14: Final commit + tag**

```bash
git add CLAUDE.md
git commit -m "docs(plan-5b): mark Plan 5b done in CLAUDE.md"
git tag v0.5.0b-gamification
git push origin main
git push origin v0.5.0b-gamification
```

- [ ] **Step 15: Verifiera i `git log` + GitHub**

Run: `git log --oneline -10 && git tag -l "v0.5.0b*"`
Expected: tag finns + senaste commits matchar plan (15 tasks). GitHub Actions visar grönt på CI.

---

## Self-Review

**1. Spec coverage:**

| Spec-sektion | Task |
|---|---|
| §1 Bakgrund + klar-villkor | Hela planen + Task 15 (tag + screenshots) |
| §2 Låsta beslut #1-#16 | Alla incorporerade i Task 1-15 |
| §3 Arkitektur + moduler (3 avvikelser dokumenterade) | Task 1, 2, 3, 5, 6, 9, 11, 12, 13 |
| §4 Datamodell (`badges.yaml` + `badge_unlock` + domain-typer) | Task 1, 2, 5 |
| §5 Regelmotor + `RecalculateBadgesUseCase` + Save-flow | Task 4, 6, 8 |
| §6.1 BadgesScreen | Task 11 (VM) + Task 12 (UI) |
| §6.2 UnlockBottomSheet | Task 10 |
| §6.3 Navigation | Task 13 |
| §6.4 i18n-strängar (sv + en) | Task 7 |
| §7.1 Save-flow med firande | Task 8 + Task 13 |
| §7.2 App-start-backfill | Task 9 + Task 13 (Step 10) |
| §7.3 BadgesScreen-state-flow | Task 11 |
| §8 Felhantering | Task 6 (use-case) + Task 8 (recalc-fail) + Task 11 (Error.LoadFailed) |
| §9 Test-strategi | Task 1, 2, 4, 5, 6, 8, 9, 10, 11, 12 |
| §10 Återanvända Plan 4a + 5a-mönster | Genomgående: `Error.Kind`-enum, `runCatching`, `combine()`, `expect/actual`, build-deps |
| §11 Out of scope | Inga task — uttryckligen ej-implementerade features |
| §12 Risker | YAML-drift täcks av Task 14, performance täcks av Task 6 micro-bench (om relevant) |
| §13 Definition of Done | Task 15 |

**2. Placeholder scan:**

- "TODO" finns medvetet i Task 11 Step 2 (`error-test för Flow-throw`) — markerat för subagent att ersätta med konkret test eller flagga för uppföljning. Inte en spec-blocker eftersom resten av Task 11 är konkret.
- "*(om existerar — annars test sker via Task 11)*" i Task 3 är medveten branching baserat på vad subagent hittar i kodbasen — exakta filsökvägar givna.
- "*(eller motsv. impl-fil — verifiera namnet via Grep)*" i Task 3, 13 — implementer:n ska exec:a Grep-kommandot innan beslut tas. Acceptabelt.
- Inga "TBD" / "fill in details" / "implement later" / "add appropriate error handling".

**3. Type consistency:**

- `Badge(id, category, rule)` matchar Task 1, 5, 11, 12.
- `BadgeRule.target: Int` (override på alla 6 sub-classer) matchar Task 1, 6, 11.
- `BadgeUnlock(badgeId, unlockedAt: Instant)` matchar Task 1, 2, 6, 8, 11, 13.
- `BadgeRepository.observeUnlocks(): Flow<List<BadgeUnlock>>` + `persist(unlocks)` + `deleteAll()` konsistent i Task 1, 2, 8, 9, 11, 13.
- `RecalculateBadgesUseCase.newUnlocks(observations, speciesByQid, catalog, existingUnlocks): List<BadgeUnlock>` matchar Task 6, 8, 9.
- `RecalculateBadgesUseCase.currentValue(rule, observations, speciesByQid): Int` matchar Task 6, 11.
- `SaveResult(observationId: String, newUnlocks: List<BadgeUnlock>)` matchar Task 8, 13.
- `BadgeCatalog.findById(id: String): Badge?` matchar Task 1, 11, 13.
- `BadgesUiState.Loaded(speciesProgress, unlockedCount, totalBadges, weeklyStreak, monthlyStreak, recentlyUnlocked, locked)` matchar Task 11 + Task 12.
- `UnlockQueue.enqueue(unlocks)` + `pop()` + `current: StateFlow<BadgeUnlock?>` matchar Task 10 + Task 13.
- `BadgeVersionStore.lastSeen: Int` (read/write) matchar Task 9 + Task 13.

**4. Ambiguity check:**

- Task 5 Step 5 (specifika 25 märken-IDs) är låsta i kod; subagent får inte tweaka familje-listan utan att uppdatera CLAUDE.md (Task 15 Step 11 c).
- Task 11 Step 2 `TODO("...")`-noteringen — medvetet kvar för subagent att ersätta. Markerad som "ej spec-blocker".
- Task 13 Step 7 (`AppGraph.create(applicationContext)`-mönster) — exakt API beror på Plan 5a:s nuvarande wiring. Subagent ska Grep-och-anpassa.
- Task 14 Step 4-5 (gradle-syntax för JavaExec) — instruktionen säger "verifiera mot existing `validateSpeciesData`-task" istället för att hardcoda exakt syntax. Acceptabelt eftersom Plan 2a redan har mönstret.
- Task 15 Step 6 (timezone-manipulation för streak-test) — alternativ givna (`adb shell date` eller debug-utility); subagent väljer det som funkar utan att committa debug-kod.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-06-v1-05b-gamification.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration. Modell-strategi: Sonnet 4.6 implementer + Opus 4.7 reviewer/controller (samma som Plan 3 + 4a + 5a).

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
