# Plan 7b — Redesign Skärmar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Skapa de fyra IA-flikarnas riktiga skärmar — Listen launcher, Archive (rebrand av Encyclopedia), Lifelist (rebrand av Diary), Badges-redesign — plus restyle av species-profile / camera / photo-analyze / unlock-sheet, och slå på `stamp_number` i save-flödet. Resultat = `v0.7.0b-screens` med stämpel-metaforen synlig genomgående.

**Architecture:** Bygger ovanpå Plan 7a's foundation (tema-tokens, `ItalicMixedText`, `HeroZone`, `:shared:datastore` med 6 enum-keys, `stamp_number INTEGER NOT NULL DEFAULT 0` i `Observation`-tabellen). Per-skärm: ny ViewModel som combine:ar repo-Flow med DataStore-Flow för persistent sort/chip-filter; HeroZone + ItalicMixedText för rubriker; befintliga repos och usecases återanvänds. Match-flow är **explicit out-of-scope** (Plan 7c) — ClassificationResultScreen behålls oförändrad i 7b.

**Tech Stack:** Kotlin Multiplatform 2.1.20, Compose Multiplatform 1.7.3, SQLDelight 2.0.2, androidx.datastore-preferences 1.1.1, Coil 2.7.0 (Android-only AsyncImage), Material3, kotlinx-coroutines/datetime/serialization, JUnit5 + Turbine.

**Spec:** `docs/superpowers/specs/2026-05-08-birdy-bird-scanner-redesign-design.md` (commit `232f528`), specifikt §5 (Listen launcher), §6 (Archive), §7 (Lifelist), §9 (Badges), §10 (befintliga skärmar restyle), §12.B (fas-paket).

---

## Avvikelser från spec

Spec:en skrevs innan Plan 7a's foundation gick i mål; vi bumpar tre punkter explicit för att undvika dubbelarbete:

1. **Skip `match_percent`-kolumn.** Spec §11 lägger till `match_percent INTEGER` på `Observation`. Plan 5a's existerande `confidence REAL NOT NULL` (0.0–1.0) ger samma data. UI multiplicerar med 100 och formatterar i `LifelistRow`. Migration `2.sqm` skippas helt.
2. **DataStore-enums ligger redan på plats.** Plan 7a Task 6 la `LifelistStat3Choice`, `ArchiveSort` och `LifelistSort` i `:shared:datastore/UserPreferences.kt`. Inga nya keys tillkommer i 7b.
3. **`BadgeEvaluator.rawProgress`-API behövs inte.** `RecalculateBadgesUseCase.currentValue(rule, observations, speciesByQid): Int` (composeApp/badges/) gör exakt detta — coercAtMost(rule.target). Plan 7b återanvänder den, lägger inte till nytt API.
4. **Match-flow / threshold-skärmar ligger i Plan 7c.** Spec §8 (Match + Disambig + <35% toast) är inte del av 12.B. ClassificationResultScreen behålls från Plan 4a — Plan 7b modifierar den **inte**, men extender save-flödet med `stamp_number`. Stämpel-numret syns i Lifelist-raderna men inte i resultatskärmen ännu.
5. **Tag = `v0.7.0b-screens`.** (Plan 7a tog `v0.7.0a-foundation`; Plan 7c tar `v0.7.0c-match`; aggregerade tag `v0.7.0-redesign` skapas efter 7c.)
6. **Encyclopedia-package behålls.** Vi byter ut composable + ViewModel-klassnamn, route-namn och fil-namn (`EncyclopediaScreen.kt → ArchiveScreen.kt`), men lämnar paketet `se.birdy.app.ui.encyclopedia` orört för att hålla blast-radius nere. Diary får samma behandling: filer i `se.birdy.app.ui.diary` blir `LifelistScreen.kt` + `LifelistViewModel.kt`.

---

## File Structure

### Skapas

| Fil | Ansvar |
|---|---|
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampNumberBadge.kt` | Återanvändbar copper-pill med `#N` Crimson italic + cream-ring + drop-shadow. Används på Lifelist-rad-thumb + (senare) Match-skärm-hero. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherViewModel.kt` | UiState + `onAudioLockedTap()`-event-channel (`Flow<ListenEvent>` med en `AudioLockedSnackbar`-variant). |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherScreen.kt` | Hub-launcher: HeroZone (`Tre sätt att fånga.`) + tre kort (Audio locked, Camera primary, Photo secondary). |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveScreen.kt` | (ersätter `EncyclopediaScreen.kt`, samma package) `Birds.`-hero + chips + sort-toggle + image-thumbs + STAMPED-pill. Rename inkl. composable-namn. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChipFilter.kt` | Enum `ArchiveChip { ALL, SONGBIRDS, WATER, RAPTORS, OWLS, WADERS }` + dictionary `Map<ArchiveChip, Set<String>>` mot `taxonomy.ioc_order`. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/LifelistScreen.kt` | (ersätter `DiaryScreen.kt`) `[Namn]s samling.`-hero + 3 stats (1 toggleable) + stamp-rader + 24h fade. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/LifelistViewModel.kt` | (ersätter `DiaryViewModel.kt`) Combine:ar observations + species + DataStore (lifelist_sort, lifelist_stat3_choice). UiState `Loading | Empty | Loaded(rows, stat1, stat2, stat3)`. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeProgressBar.kt` | Hero-progress-bar (`12 / 25` italic copper + tunn 4dp gradient-bar). |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeGridCell.kt` | Grid-cell: tre states (`Locked` / `InProgress(progress, target)` / `Hidden`). |
| `composeApp/src/commonTest/kotlin/se/birdy/app/usecase/SaveObservationStampNumberTest.kt` | Verifierar att `SaveObservationUseCase.save()` skriver `stamp_number = MAX + 1` atomiskt. |
| `composeApp/src/commonTest/kotlin/se/birdy/app/ui/listen/ListenLauncherViewModelTest.kt` | Verifierar audio-locked-event och initialState. |
| `composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/ArchiveViewModelTest.kt` | Verifierar chip + sort + DataStore-persistence. |
| `composeApp/src/commonTest/kotlin/se/birdy/app/ui/diary/LifelistViewModelTest.kt` | Verifierar hero-stats (count distinct, total, stat3-toggle). |
| `composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgesProgressTest.kt` | Verifierar locked / in-progress / hidden state-mappning. |
| `docs/superpowers/screenshots/v0.7.0b-screens/` | Device-screenshots (listen-launcher, archive-loaded, archive-stamped, lifelist-empty, lifelist-loaded, lifelist-detail, badges-progress, species-profile-restyled, scan-restyled). |

### Modifieras

| Fil | Ändring |
|---|---|
| `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt` | Lägg till `stampNumberProvider: suspend () -> Int` (default = `repo.nextStampNumber()`). Skicka in värdet till `Observation`-konstruktorn. |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/observation/ObservationRepository.kt` | Ny `suspend fun nextStampNumber(): Int`. |
| `shared/data/src/commonMain/kotlin/se/birdy/data/observation/SqlDelightObservationRepository.kt` | Implementera `nextStampNumber()` via `SELECT COALESCE(MAX(stamp_number), 0) + 1 FROM observation` (transactional med insert). |
| `shared/data/src/commonMain/sqldelight/se/birdy/data/db/Observation.sq` | Ny query `selectMaxStampNumber: SELECT COALESCE(MAX(stamp_number), 0) FROM observation;` |
| `shared/domain/src/commonMain/kotlin/se/birdy/domain/observation/Observation.kt` | Lägg till `stampNumber: Int` (default 0 för backwards-compat under migration; nya saves sätter alltid). |
| `shared/data/src/commonMain/kotlin/se/birdy/data/observation/SqlDelightObservationRepository.kt` | Map `stamp_number`-kolumn till `Observation.stampNumber`. Insert skickar parameter. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt` | Lägg till `data object Listen : AppRoute`. Behåll `Scan` + `PhotoAnalyze` (når via Listen-launcher). |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt` | Bottom-bar Listen-tab navigerar till `AppRoute.Listen`. Lägg till `composable<AppRoute.Listen>` som öppnar `ListenLauncherScreen`. Camera + Photo nås via launchern (ingen ändring i Scan/PhotoAnalyze rutter). |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt` | Listen-tab routes `AppRoute.Listen`; selectedTabIndex för Listen aktiveras både på `Listen` och `Scan` + `PhotoAnalyze` (Listen är "owner"-flik). |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaViewModel.kt` | Rename → `ArchiveViewModel`. Inject `UserPreferences`. Combine query + filter + chip + sort. Stamped-set från `ObservationRepository.observeAll()` (distinct species). |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaUiState.kt` | Rename `EncyclopediaUiState` → `ArchiveUiState`. `Loaded` får `rows: List<ArchiveRow>` (varje rad har `summary, isStamped, sortKey`). |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/SpeciesRow.kt` | Lägg `isStamped: Boolean`-parameter; visa `STAMPED`/`STÄMPLAD`-pill när true. Använd 44dp Coil cirkulär thumb (Android) / placeholder (övriga targets). |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/DiaryScreen.kt` | Filen tas bort (ersätts av `LifelistScreen.kt`). |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/DiaryViewModel.kt` | Filen tas bort (ersätts av `LifelistViewModel.kt`). |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/ObservationDetailScreen.kt` | Hero-image får 24dp rundade botten-hörn + `StampNumberBadge` overlay. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt` | UiState.Loaded utökas: `locked: List<LockedBadgeProgress>` där varje element har `state: GridState { Locked, InProgress(current, target), Hidden }`. Hidden-set = badges med `category == RARE`. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt` | Hero ersätts: `BadgeProgressBar` + carousel + 4-kolumn grid. Locked/in-progress/hidden separata renderingar via `BadgeGridCell`. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/SpeciesProfileScreen.kt` | Hero collapsing-toolbar gradient → koppar-mossgrön + 24dp rundade botten-hörn. Headline → `ItalicMixedText` (svenska namn punkt-suffix; latin med italic-epitet). Stat-block → `SandCreme` cards med `AccentCopper` värden. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt` | Top-chip background `SandCreme.copy(alpha=0.9f)` (tidigare svart). Crosshair-färg → `AccentCopperLight`. Chip-text Crimson Pro 14sp med italic-copper på art-namn. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeScreen.kt` | Knappar primary AccentCopper / secondary cream; hero-text `ItalicMixedText`. |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/result/UnlockBottomSheet.kt` | Background `MossCreme`; centrerad badge med kopparkant + halo; headline `ItalicMixedText`; CTA-stilar (primary cream-på-koppar, secondary link). |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | Nya strängar (lista i Task-kolumn nedan). |
| `composeApp/src/commonMain/composeResources/values-en/strings.xml` | Nya engelska strängar. |
| `composeApp/src/androidMain/kotlin/se/birdy/app/MainActivity.kt` | (om DiaryScreen-rutten har actual) — uppdatera till LifelistScreen om det finns en Android-only host. |
| `composeApp/build.gradle.kts` | Inga nya dependencies. |
| `CLAUDE.md` | Bumpa Plan 7b → ✅, lägga `v0.7.0b-screens`. |

---

## Implementation order rationale

Tasks är ordnade efter blast-radius:

1. **StampNumberBadge** (Task 1) — komponent används av flera task downstream. Risk: ingen.
2. **stamp_number i save-flödet** (Task 2) — datalager-ändring. Påverkar Plan 5a's saves men krymper inte feltrygghet. Måste före Lifelist-redesign.
3. **Listen launcher + AppScaffold-rewiring** (Tasks 3–5) — IA-ändring. Bottom-bar Listen-tab byter destination. Camera + Photo flyttas under launcher.
4. **Archive-rename + UI** (Tasks 6–7) — Encyclopedia → Archive. Påverkar bottom-bar + djuplänkar.
5. **Lifelist-rename + UI** (Tasks 8–9) — Diary → Lifelist.
6. **Badges-redesign** (Tasks 10–11) — UI-only; logik från Plan 5b orörd.
7. **Restyle existing** (Task 12) — bundlat: SpeciesProfile + Scan + PhotoAnalyze + UnlockBottomSheet. Liten risk var för sig.
8. **Build/verify/tag** (Task 13) — slutverifiering.

---

## Reusable patterns from Plan 7a

(Sammanfattning från `project_plan_7a_status.md` — agentic-implementer **måste** följa dessa eller fail-loopa i review.)

- `remember { Brush.verticalGradient(...) }` med stable color-tokens (inte inline literals).
- `@BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())` + `@AfterTest fun tearDown() = Dispatchers.resetMain()` i alla ViewModel-tester som använder `viewModelScope.launch`.
- `expect class … (platformContext: Any?)` för platform-bridges.
- `remember { listOf(...) }` för stable-identity option-listor i grid-states.
- `rememberSaveable` för dialog draft-state.
- ALLTID kör `./gradlew build` (eller unscoped `ktlintCheck`) i slutet av tasks som rör AppGraph-wiring eller `:androidApp/MainActivity.kt` — `:composeApp:ktlintCheck` MISSAR `:androidApp`.
- Plan-doc placeholder-Box måste alltid ha en konkret ikon-spec — annars fångar reviewers det inte och bara device-verify exposes:t (jfr Plan 7a Task 9).
- `:androidApp` saknar transitiva deps (composeApp använder `implementation()`); varje ny shared/library-referens från `:composeApp` måste få egen `implementation()` i `:androidApp/build.gradle.kts`.
- Compose-resources unescape:ar **inte** Android-style `\'` — använd raw `'` i strings.xml; `%%` är inte `%`-escape — använd `%1$s` + pre-formatterad call-site.
- `ImageProxy.imageInfo.timestamp` är nanos sedan device-boot — använd `System.currentTimeMillis()`.

---

# Tasks

## Task 1 — StampNumberBadge composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampNumberBadge.kt`
- Test: (skip — pure visual composable, verifieras via screenshot på Task 9)

- [ ] **Step 1: Skapa StampNumberBadge.kt**

```kotlin
package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm

/**
 * Copper-pill med `#N` i Crimson Pro italic + cream-ring.
 * Designat för overlay på 44–80dp cirkulära thumbs (nedre högra hörnet).
 */
@Composable
fun StampNumberBadge(
    number: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(AccentCopper)
                .border(width = 1.5.dp, color = OffwhiteWarm, shape = RoundedCornerShape(percent = 50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "#$number",
            color = OffwhiteWarm,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.W700,
            fontStyle = FontStyle.Italic,
            fontSize = 11.sp,
        )
    }
}
```

- [ ] **Step 2: Build-verifiera**

Run: `./gradlew :composeApp:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/StampNumberBadge.kt
git commit -m "$(cat <<'EOF'
feat(ui): add StampNumberBadge — copper pill with #N italic

Reusable component for stamp-number overlay on circular thumbs
(Lifelist rows, future Match-screen). Uses AccentCopper +
OffwhiteWarm cream-ring per Mossbädd palette.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2 — `stamp_number` i save-flödet

**Files:**
- Modify: `shared/data/src/commonMain/sqldelight/se/birdy/data/db/Observation.sq`
- Modify: `shared/domain/src/commonMain/kotlin/se/birdy/domain/observation/Observation.kt`
- Modify: `shared/domain/src/commonMain/kotlin/se/birdy/domain/observation/ObservationRepository.kt`
- Modify: `shared/data/src/commonMain/kotlin/se/birdy/data/observation/SqlDelightObservationRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/usecase/SaveObservationStampNumberTest.kt`

- [ ] **Step 1: Skapa failing test**

```kotlin
// composeApp/src/commonTest/kotlin/se/birdy/app/usecase/SaveObservationStampNumberTest.kt
package se.birdy.app.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.usecase.testing.FakeBadgeRepository
import se.birdy.app.usecase.testing.FakeObservationRepository
import se.birdy.app.usecase.testing.FakePhotoStorage
import se.birdy.content.SpeciesId
import se.birdy.domain.badge.BadgeCatalog
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SaveObservationStampNumberTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `first save assigns stamp_number 1`() = runTest(dispatcher) {
        val repo = FakeObservationRepository()
        val useCase = makeUseCase(repo)
        useCase.save(
            speciesId = "Q1",
            capturedAt = Instant.fromEpochMilliseconds(1000L),
            confidence = 0.9f,
            rawJpegBytes = byteArrayOf(0),
            note = "",
        )
        assertEquals(1, repo.lastInserted!!.stampNumber)
    }

    @Test
    fun `subsequent saves increment stamp_number`() = runTest(dispatcher) {
        val repo = FakeObservationRepository()
        val useCase = makeUseCase(repo)
        repeat(3) { i ->
            useCase.save(
                speciesId = "Q$i",
                capturedAt = Instant.fromEpochMilliseconds(1000L + i),
                confidence = 0.9f,
                rawJpegBytes = byteArrayOf(0),
                note = "",
            )
        }
        assertEquals(listOf(1, 2, 3), repo.allInserted.map { it.stampNumber })
    }

    private fun makeUseCase(repo: FakeObservationRepository): SaveObservationUseCase =
        SaveObservationUseCase(
            repo = repo,
            badgeRepo = FakeBadgeRepository(),
            photoStorage = FakePhotoStorage(),
            clock = Clock.System,
            catalog = BadgeCatalog(emptyList()),
            recalculate = RecalculateBadgesUseCase(),
            speciesByQid = { emptyMap() },
        )
}
```

`FakeObservationRepository`, `FakeBadgeRepository`, `FakePhotoStorage` finns redan i `composeApp/src/commonTest/kotlin/se/birdy/app/usecase/testing/` från Plan 5b. **Verify before writing test:** kör `Glob testing/**` under `commonTest`. Om någon saknas, skapa minimal version (FakeObservationRepository måste implementera ny `nextStampNumber()` också — se Step 3).

- [ ] **Step 2: Kör testet — ska faila**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.usecase.SaveObservationStampNumberTest"`
Expected: COMPILATION ERROR (`stampNumber` finns inte på `Observation`).

- [ ] **Step 3: Lägg `stamp_number`-kolumnen till domain + repo**

I `shared/domain/src/commonMain/kotlin/se/birdy/domain/observation/Observation.kt`:

```kotlin
data class Observation(
    val id: String,
    val speciesId: String,
    val capturedAt: Instant,
    val savedAt: Instant,
    val photoPath: String,
    val note: String,
    val confidence: Float,
    val latitude: Double?,
    val longitude: Double?,
    val locationLabel: String?,
    val stampNumber: Int = 0,  // 0 = pre-Plan-7b backfilled rows; nya saves >= 1
)
```

I `shared/domain/src/commonMain/kotlin/se/birdy/domain/observation/ObservationRepository.kt`:

```kotlin
interface ObservationRepository {
    fun observeAll(): Flow<List<Observation>>
    fun observeById(id: String): Flow<Observation?>
    suspend fun insert(observation: Observation)
    suspend fun update(id: String, note: String)
    suspend fun delete(id: String)
    suspend fun nextStampNumber(): Int  // ny
}
```

I `shared/data/src/commonMain/sqldelight/se/birdy/data/db/Observation.sq` lägg till query (kolumnen själv lades till i Plan 7a Task 4):

```sql
selectMaxStampNumber:
SELECT COALESCE(MAX(stamp_number), 0) AS m FROM Observation;
```

Verifiera först: `grep -n "stamp_number" shared/data/src/commonMain/sqldelight/se/birdy/data/db/Observation.sq` ska visa kolumnen i tabellen + i `insert`-queryns parameter-lista. Om `insert`-queryn fortfarande inte tar `stamp_number` (Task 4 borde ha lagt den), gör det nu.

I `shared/data/src/commonMain/kotlin/se/birdy/data/observation/SqlDelightObservationRepository.kt`:

```kotlin
override suspend fun nextStampNumber(): Int =
    withContext(dispatcher) { (queries.selectMaxStampNumber().executeAsOne().m ?: 0L).toInt() + 1 }
```

Och uppdatera `insert(...)` så `stamp_number` skickas:

```kotlin
override suspend fun insert(observation: Observation) {
    withContext(dispatcher) {
        queries.insert(
            id = observation.id,
            species_q_id = observation.speciesId,
            captured_at = observation.capturedAt.toEpochMilliseconds(),
            saved_at = observation.savedAt.toEpochMilliseconds(),
            photo_path = observation.photoPath,
            note = observation.note,
            confidence = observation.confidence.toDouble(),
            latitude = observation.latitude,
            longitude = observation.longitude,
            location_label = observation.locationLabel,
            stamp_number = observation.stampNumber.toLong(),
        )
    }
}
```

Och i `selectAll`/`selectById`-mappers — lägg till `stampNumber = it.stamp_number.toInt()` när `Observation` byggs.

- [ ] **Step 4: Uppdatera SaveObservationUseCase**

```kotlin
// composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt
suspend fun save(
    speciesId: String,
    capturedAt: Instant,
    confidence: Float,
    rawJpegBytes: ByteArray,
    note: String,
): SaveResult {
    val id = Uuid.random().toString()
    val photoPath = photoStorage.persistJpeg(rawJpegBytes)
    val nextStamp = repo.nextStampNumber()
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
                stampNumber = nextStamp,
            ),
        )
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        runCatching { photoStorage.delete(photoPath) }
        throw t
    }
    // ... badge-recalc oförändrat
}
```

**Race-fönster:** `nextStampNumber()` följt av separat `insert()` är inte atomic. För v1 är saves sekventiella (en åt gången från Match-skärmen / ClassificationResultScreen) så hålet är teoretiskt. Om Plan 7c kräver atomicity flytta båda till en `transaction { }`-blob i `SqlDelightObservationRepository`.

- [ ] **Step 5: Uppdatera FakeObservationRepository**

Lägg till `nextStampNumber()` i `composeApp/src/commonTest/kotlin/se/birdy/app/usecase/testing/FakeObservationRepository.kt`:

```kotlin
private val all = MutableStateFlow<List<Observation>>(emptyList())
val allInserted: List<Observation> get() = all.value
val lastInserted: Observation? get() = all.value.lastOrNull()
override suspend fun nextStampNumber(): Int = (all.value.maxOfOrNull { it.stampNumber } ?: 0) + 1
override suspend fun insert(observation: Observation) { all.value = all.value + observation }
```

- [ ] **Step 6: Kör testet — ska passa**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.usecase.SaveObservationStampNumberTest"`
Expected: PASS (2/2).

- [ ] **Step 7: Kör all befintlig testsuite — ska fortfarande passa**

Run: `./gradlew :shared:domain:jvmTest :shared:data:testDebugUnitTest :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Plan 5a/5b-tester får inte regreda. Om de gör det är det troligen för att de använder `Observation(...)`-konstruktorn med named args utan `stampNumber` — default `= 0` täcker det. Om någon test gör structural copy på `Observation` (t.ex. `it.copy(...)`), funkar det också.

- [ ] **Step 8: Commit**

```bash
git add shared/domain/src/commonMain/kotlin/se/birdy/domain/observation/Observation.kt \
        shared/domain/src/commonMain/kotlin/se/birdy/domain/observation/ObservationRepository.kt \
        shared/data/src/commonMain/sqldelight/se/birdy/data/db/Observation.sq \
        shared/data/src/commonMain/kotlin/se/birdy/data/observation/SqlDelightObservationRepository.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/usecase/SaveObservationStampNumberTest.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/usecase/testing/FakeObservationRepository.kt
git commit -m "$(cat <<'EOF'
feat(observation): wire stamp_number into save flow

Domain Observation gains stampNumber: Int (default 0 for legacy rows).
ObservationRepository.nextStampNumber() returns MAX + 1 from DB.
SaveObservationUseCase reads next number before insert.

Race window between read and insert is theoretical for v1 (saves are
sequential from the UI). Plan 7c can wrap both in a transaction if
Match-flow introduces concurrent writes.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3 — ListenLauncherViewModel + UiState

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/listen/ListenLauncherViewModelTest.kt`

- [ ] **Step 1: Skapa failing test**

```kotlin
// composeApp/src/commonTest/kotlin/se/birdy/app/ui/listen/ListenLauncherViewModelTest.kt
package se.birdy.app.ui.listen

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ListenLauncherViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `audio locked tap emits AudioLockedSnackbar event`() = runTest(dispatcher) {
        val vm = ListenLauncherViewModel()
        vm.events.test {
            vm.onAudioLockedTap()
            assertIs<ListenLauncherEvent.AudioLockedSnackbar>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Kör testet — ska faila**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.listen.ListenLauncherViewModelTest"`
Expected: COMPILATION ERROR (`ListenLauncherViewModel` finns inte).

- [ ] **Step 3: Implementera VM**

```kotlin
// composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherViewModel.kt
package se.birdy.app.ui.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface ListenLauncherEvent {
    data object AudioLockedSnackbar : ListenLauncherEvent
}

class ListenLauncherViewModel : ViewModel() {
    private val _events = MutableSharedFlow<ListenLauncherEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ListenLauncherEvent> = _events.asSharedFlow()

    fun onAudioLockedTap() {
        viewModelScope.launch { _events.emit(ListenLauncherEvent.AudioLockedSnackbar) }
    }
}
```

- [ ] **Step 4: Kör testet — ska passa**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.listen.ListenLauncherViewModelTest"`
Expected: PASS (1/1).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherViewModel.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/listen/ListenLauncherViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(listen): add ListenLauncherViewModel with audio-locked event

SharedFlow<ListenLauncherEvent> for one-shot snackbar emissions.
DROP_OLDEST + buffer 1 prevents lost events on rapid taps.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4 — ListenLauncherScreen UI

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (sv)
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Lägg till strings (sv + en)**

`composeApp/src/commonMain/composeResources/values/strings.xml`:

```xml
<string name="listen_breadcrumb">LYSSNA</string>
<string name="listen_headline_prefix">Tre sätt att </string>
<string name="listen_headline_italic">fånga.</string>
<string name="listen_sub">En stämpel väntar i varje.</string>
<string name="listen_card_audio_title">Lyssna</string>
<string name="listen_card_audio_body">Identifiera via läte — kommer snart.</string>
<string name="listen_card_camera_title">Kika</string>
<string name="listen_card_camera_body">Realtidsskanning via kameran.</string>
<string name="listen_card_photo_title">Leta upp</string>
<string name="listen_card_photo_body">Välj foto från galleri eller ta nytt.</string>
<string name="listen_premium_label">PREMIUM</string>
<string name="listen_audio_locked_snackbar">Audio kommer snart</string>
```

`values-en/strings.xml`:

```xml
<string name="listen_breadcrumb">LISTEN</string>
<string name="listen_headline_prefix">Three ways to </string>
<string name="listen_headline_italic">catch.</string>
<string name="listen_sub">A stamp waits in each.</string>
<string name="listen_card_audio_title">Listen</string>
<string name="listen_card_audio_body">Identify by call — coming soon.</string>
<string name="listen_card_camera_title">Look</string>
<string name="listen_card_camera_body">Real-time camera scanning.</string>
<string name="listen_card_photo_title">Find</string>
<string name="listen_card_photo_body">Pick from gallery or take new.</string>
<string name="listen_premium_label">PREMIUM</string>
<string name="listen_audio_locked_snackbar">Audio coming soon</string>
```

- [ ] **Step 2: Implementera ListenLauncherScreen**

```kotlin
// composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherScreen.kt
package se.birdy.app.ui.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.listen_audio_locked_snackbar
import birdy_bird_scanner.composeapp.generated.resources.listen_breadcrumb
import birdy_bird_scanner.composeapp.generated.resources.listen_card_audio_body
import birdy_bird_scanner.composeapp.generated.resources.listen_card_audio_title
import birdy_bird_scanner.composeapp.generated.resources.listen_card_camera_body
import birdy_bird_scanner.composeapp.generated.resources.listen_card_camera_title
import birdy_bird_scanner.composeapp.generated.resources.listen_card_photo_body
import birdy_bird_scanner.composeapp.generated.resources.listen_card_photo_title
import birdy_bird_scanner.composeapp.generated.resources.listen_headline_italic
import birdy_bird_scanner.composeapp.generated.resources.listen_headline_prefix
import birdy_bird_scanner.composeapp.generated.resources.listen_premium_label
import birdy_bird_scanner.composeapp.generated.resources.listen_sub
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.HeroZone
import se.birdy.app.ui.components.ItalicMixedText
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme

@Composable
fun ListenLauncherScreen(
    viewModel: ListenLauncherViewModel,
    onCameraClick: () -> Unit,
    onPhotoClick: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val audioLockedMsg = stringResource(Res.string.listen_audio_locked_snackbar)
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                ListenLauncherEvent.AudioLockedSnackbar -> snackbar.showSnackbar(audioLockedMsg)
            }
        }
    }
    Scaffold(
        containerColor = MossCreme,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroZone {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                    Text(
                        text = stringResource(Res.string.listen_breadcrumb),
                        color = AccentCopperLight,
                        fontSize = 11.sp,
                        letterSpacing = 0.32.em,
                        fontWeight = FontWeight.W600,
                    )
                    Spacer(Modifier.height(8.dp))
                    ItalicMixedText(
                        prefix = stringResource(Res.string.listen_headline_prefix),
                        italicWord = stringResource(Res.string.listen_headline_italic),
                        baseFontSize = 30.sp,
                        baseColor = OffwhiteWarm,
                        italicColor = AccentCopperLight,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(Res.string.listen_sub),
                        color = OffwhiteWarm.copy(alpha = 0.86f),
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LaunchCard(
                    icon = Icons.Filled.Hearing,
                    title = stringResource(Res.string.listen_card_audio_title),
                    body = stringResource(Res.string.listen_card_audio_body),
                    variant = LaunchCardVariant.Locked,
                    onClick = viewModel::onAudioLockedTap,
                )
                LaunchCard(
                    icon = Icons.Filled.PhotoCamera,
                    title = stringResource(Res.string.listen_card_camera_title),
                    body = stringResource(Res.string.listen_card_camera_body),
                    variant = LaunchCardVariant.Primary,
                    onClick = onCameraClick,
                )
                LaunchCard(
                    icon = Icons.Filled.PhotoLibrary,
                    title = stringResource(Res.string.listen_card_photo_title),
                    body = stringResource(Res.string.listen_card_photo_body),
                    variant = LaunchCardVariant.Secondary,
                    onClick = onPhotoClick,
                )
            }
        }
    }
}

private enum class LaunchCardVariant { Locked, Primary, Secondary }

@Composable
private fun LaunchCard(
    icon: ImageVector,
    title: String,
    body: String,
    variant: LaunchCardVariant,
    onClick: () -> Unit,
) {
    val premiumLabel = stringResource(Res.string.listen_premium_label)
    val backgroundColor = when (variant) {
        LaunchCardVariant.Locked -> SandCreme.copy(alpha = 0.6f)
        LaunchCardVariant.Primary -> SandCreme
        LaunchCardVariant.Secondary -> SandCreme
    }
    val borderColor = when (variant) {
        LaunchCardVariant.Primary -> AccentCopper
        else -> AccentCopper.copy(alpha = 0.0f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .border(width = if (variant == LaunchCardVariant.Primary) 2.dp else 0.dp, color = borderColor, shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentCopper.copy(alpha = if (variant == LaunchCardVariant.Locked) 0.10f else 0.18f))
                .padding(10.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = AccentCopperLight)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = TextOnCreme,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.W700,
                    fontSize = 18.sp,
                )
                if (variant == LaunchCardVariant.Locked) {
                    Spacer(Modifier.width(8.dp))
                    Icon(imageVector = Icons.Filled.Star, contentDescription = null, tint = AccentCopperLight, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = premiumLabel,
                        color = AccentCopperLight,
                        fontSize = 9.sp,
                        letterSpacing = 0.18.em,
                        fontWeight = FontWeight.W600,
                    )
                }
            }
            Text(
                text = body,
                color = TextOnCreme.copy(alpha = 0.74f),
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
            )
        }
        if (variant != LaunchCardVariant.Locked) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AccentCopper,
            )
        }
    }
}
```

**Imports som behövs men inte är listade ovan:** `androidx.compose.foundation.layout.size`, `androidx.compose.foundation.layout.width`, `androidx.compose.ui.unit.em`. Lägg dem efter att du kompilerar och ser saknade imports.

- [ ] **Step 3: Build-verifiera**

Run: `./gradlew :composeApp:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/listen/ListenLauncherScreen.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "$(cat <<'EOF'
feat(listen): add ListenLauncherScreen — hero + 3 cards

HeroZone with italic-mixed headline and 3 stacked launcher cards:
Locked (audio + premium label), Primary (camera, copper border),
Secondary (photo, plain). Snackbar on locked tap.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5 — AppRoute.Listen + AppScaffold rewiring

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt` (lägg `listenLauncherViewModel(): ListenLauncherViewModel`)

- [ ] **Step 1: Lägg `Listen`-rutten**

I `AppRoute.kt`:

```kotlin
@Serializable data object Listen : AppRoute
```

(Lämna `Scan` och `PhotoAnalyze` orörda — de blir destinationer från Listen-launchern.)

- [ ] **Step 2: Lägg AppGraph-factory**

I `AppGraph.kt` (eller motsvarande `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`):

```kotlin
fun listenLauncherViewModel(): ListenLauncherViewModel = ListenLauncherViewModel()
```

(Stateless VM — ingen DI behövs.)

- [ ] **Step 3: Wire AppScaffold**

I `AppScaffold.kt`, lägg `composable<AppRoute.Listen>`-block (efter `Scan`-rutten):

```kotlin
composable<AppRoute.Listen> {
    ListenLauncherScreen(
        viewModel = remember(graph) { graph.listenLauncherViewModel() },
        onCameraClick = {
            navController.navigate(AppRoute.Scan) {
                popUpTo(AppRoute.Listen) { inclusive = false }
                launchSingleTop = true
            }
        },
        onPhotoClick = {
            navController.navigate(AppRoute.PhotoAnalyze) {
                popUpTo(AppRoute.Listen) { inclusive = false }
                launchSingleTop = true
            }
        },
    )
}
```

Ändra `startDestination` på `NavHost`:

```kotlin
NavHost(
    navController = navController,
    startDestination = AppRoute.Listen,  // tidigare AppRoute.Scan
    modifier = Modifier.padding(padding),
)
```

- [ ] **Step 4: Wire BottomNavBar**

Lokalisera `BottomNavBar.kt`. Listen-fliken (första tabben) ska:
- `onClick = { navController.navigate(AppRoute.Listen) { popUpTo(AppRoute.Listen) { inclusive = true } } }`
- `selected = currentDestination matches Listen | Scan | PhotoAnalyze | ClassificationResult` (Listen-fliken är "owner-tab" för hela skanner-flödet).

Använd `currentBackStackEntryAsState().value?.destination?.hierarchy` + `route?.startsWith("se.birdy.app.ui.scaffold.AppRoute.Listen")` ELLER en explicit lista över route-strings:

```kotlin
val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
val listenSelected = currentRoute?.let {
    it.contains("AppRoute.Listen") ||
    it.contains("AppRoute.Scan") ||
    it.contains("AppRoute.PhotoAnalyze") ||
    it.contains("AppRoute.ClassificationResult")
} == true
```

(Plan 7a's BottomNavBar har redan en `currentRoute`-pattern som du kan utöka — läs filen först innan du modifierar.)

- [ ] **Step 5: Build + manuell smoke**

Run: `./gradlew :composeApp:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :androidApp:installDebug` och starta. App ska nu öppna direkt på Listen-launchern (om onboarding redan är completed). Tap "Kika" → Camera; tap "Leta upp" → Photo; tap "Lyssna" → snackbar.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt
git commit -m "$(cat <<'EOF'
feat(scaffold): wire Listen tab to ListenLauncherScreen

NavHost.startDestination = Listen (was Scan). Listen tab now opens
the launcher hub; Scan/PhotoAnalyze are reached via launcher cards.
Bottom-nav Listen tab stays selected across the whole scanning flow.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6 — Encyclopedia → Archive: rename + ArchiveViewModel med chip + sort

**Files:**
- Rename: `EncyclopediaViewModel.kt` → `ArchiveViewModel.kt` (samma package `se.birdy.app.ui.encyclopedia`)
- Rename: `EncyclopediaUiState.kt` → `ArchiveUiState.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveChipFilter.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt` (rename `EncyclopediaList` → `ArchiveList`; behåll `Encyclopedia` parent-rutt eller kollapsa till en flat `Archive`)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/ArchiveViewModelTest.kt`

- [ ] **Step 1: Skapa ArchiveChipFilter.kt**

```kotlin
package se.birdy.app.ui.encyclopedia

enum class ArchiveChip {
    ALL, SONGBIRDS, WATER, RAPTORS, OWLS, WADERS;

    companion object {
        // Mappning mot taxonomy.ioc_order. Verifierat: fältet finns i species YAML.
        // Multi-order: lägg flera orders i set:en.
        val orderSets: Map<ArchiveChip, Set<String>> = mapOf(
            ALL to emptySet(),  // "Alla" = inget filter
            SONGBIRDS to setOf("Passeriformes"),
            WATER to setOf("Anseriformes", "Suliformes", "Pelecaniformes", "Podicipediformes", "Gaviiformes"),
            RAPTORS to setOf("Accipitriformes", "Falconiformes"),
            OWLS to setOf("Strigiformes"),
            WADERS to setOf("Charadriiformes"),
        )
    }
}
```

- [ ] **Step 2: Skapa failing test för ArchiveViewModel**

```kotlin
// composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/ArchiveViewModelTest.kt
package se.birdy.app.ui.encyclopedia

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import se.birdy.app.usecase.testing.FakeObservationRepository
import se.birdy.app.usecase.testing.FakeSpeciesRepository
import se.birdy.app.usecase.testing.FakeUserPreferences
import se.birdy.content.Locale
import se.birdy.datastore.ArchiveSort
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `chip selection persists to DataStore`() = runTest(dispatcher) {
        val prefs = FakeUserPreferences()
        val vm = ArchiveViewModel(
            repo = FakeSpeciesRepository(),
            observationRepo = FakeObservationRepository(),
            prefs = prefs,
            locale = Locale.SV,
        )
        vm.onChipSelected(ArchiveChip.OWLS)
        // FakeUserPreferences.archiveChip ska ha skrivits
        assertTrue(prefs.archiveChipWrites.contains(ArchiveChip.OWLS.name))
    }

    @Test
    fun `sort cycles alpha → family → recent`() = runTest(dispatcher) {
        val prefs = FakeUserPreferences().apply { archiveSortValue = ArchiveSort.ALPHA }
        val vm = ArchiveViewModel(
            repo = FakeSpeciesRepository(),
            observationRepo = FakeObservationRepository(),
            prefs = prefs,
            locale = Locale.SV,
        )
        vm.onSortToggle(); assertEquals(ArchiveSort.FAMILY, prefs.archiveSortValue)
        vm.onSortToggle(); assertEquals(ArchiveSort.RECENT, prefs.archiveSortValue)
        vm.onSortToggle(); assertEquals(ArchiveSort.ALPHA, prefs.archiveSortValue)
    }
}
```

`FakeUserPreferences`, `FakeSpeciesRepository` lägg i `composeApp/src/commonTest/kotlin/se/birdy/app/usecase/testing/`. Skapa minimal version som matchar interfacet.

- [ ] **Step 3: Kör testet — ska faila**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.encyclopedia.ArchiveViewModelTest"`
Expected: COMPILATION ERROR.

- [ ] **Step 4: Implementera ArchiveViewModel**

Rename `EncyclopediaViewModel.kt` → `ArchiveViewModel.kt`. Innehåll:

```kotlin
package se.birdy.app.ui.encyclopedia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.birdy.content.Locale
import se.birdy.content.SpeciesFilter
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.SpeciesSummary
import se.birdy.datastore.ArchiveSort
import se.birdy.datastore.UserPreferences
import se.birdy.domain.observation.ObservationRepository

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ArchiveViewModel(
    private val repo: SpeciesRepository,
    private val observationRepo: ObservationRepository,
    private val prefs: UserPreferences,
    private val locale: Locale,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val chip: StateFlow<ArchiveChip> = prefs.archiveChip
        .map { runCatching { ArchiveChip.valueOf(it) }.getOrDefault(ArchiveChip.ALL) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ArchiveChip.ALL)

    val sort: StateFlow<ArchiveSort> = prefs.archiveSort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ArchiveSort.ALPHA)

    private val stampedSpeciesIds: StateFlow<Set<String>> = observationRepo.observeAll()
        .map { it.map { o -> o.speciesId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptySet())

    val uiState: StateFlow<ArchiveUiState> =
        combine(
            _query.debounce(DEBOUNCE_MS).distinctUntilChanged(),
            chip,
            sort,
            stampedSpeciesIds,
        ) { q, c, s, stamped -> Quad(q, c, s, stamped) }
            .flatMapLatest { (q, c, s, stamped) ->
                val filter = SpeciesFilter()  // chip filter applies after retrieval
                repo.search(q, locale, filter).map { list -> toUiState(list, c, s, stamped) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ArchiveUiState.Loading)

    fun onQueryChanged(q: String) {
        _query.value = q
    }

    fun onChipSelected(c: ArchiveChip) {
        viewModelScope.launch { prefs.setArchiveChip(c.name) }
    }

    fun onSortToggle() {
        viewModelScope.launch {
            val next = when (sort.value) {
                ArchiveSort.ALPHA -> ArchiveSort.FAMILY
                ArchiveSort.FAMILY -> ArchiveSort.RECENT
                ArchiveSort.RECENT -> ArchiveSort.ALPHA
            }
            prefs.setArchiveSort(next)
        }
    }

    private fun toUiState(
        list: List<SpeciesSummary>,
        c: ArchiveChip,
        s: ArchiveSort,
        stamped: Set<String>,
    ): ArchiveUiState {
        val filtered = if (c == ArchiveChip.ALL) {
            list
        } else {
            val orders = ArchiveChip.orderSets[c].orEmpty()
            list.filter { it.iocOrder in orders }
        }
        if (filtered.isEmpty()) return ArchiveUiState.Empty
        val sorted = when (s) {
            ArchiveSort.ALPHA -> filtered.sortedBy { it.localizedName.lowercase() }
            ArchiveSort.FAMILY -> filtered.sortedWith(compareBy({ it.family }, { it.localizedName.lowercase() }))
            ArchiveSort.RECENT -> filtered  // no created_at on species; fallback to alpha
        }
        val rows = sorted.map { ArchiveRow(summary = it, isStamped = it.id.raw in stamped) }
        return ArchiveUiState.Loaded(rows = rows, sort = s)
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private companion object {
        const val DEBOUNCE_MS = 250L
    }
}
```

Verifiera först att `SpeciesSummary.iocOrder` och `SpeciesSummary.family` finns. Om inte: gör en pass över `:shared:content`-modulen (`SpeciesSummary.kt`) och lägg dessa fält. Om de inte finns på SpeciesSummary, behöver de exponeras från `Species.kt` via mapper.

I `ArchiveUiState.kt`:

```kotlin
package se.birdy.app.ui.encyclopedia

import se.birdy.content.model.SpeciesSummary
import se.birdy.datastore.ArchiveSort

data class ArchiveRow(
    val summary: SpeciesSummary,
    val isStamped: Boolean,
)

sealed interface ArchiveUiState {
    data object Loading : ArchiveUiState
    data object Empty : ArchiveUiState
    data class Loaded(
        val rows: List<ArchiveRow>,
        val sort: ArchiveSort,
    ) : ArchiveUiState
}
```

- [ ] **Step 5: Uppdatera AppGraph + AppRoute + AppScaffold**

I `AppGraph.kt` rename `encyclopediaViewModel()` → `archiveViewModel()` (kalla in `prefs` + `observationRepo` som redan finns på AppGraph):

```kotlin
fun archiveViewModel(): ArchiveViewModel = ArchiveViewModel(
    repo = speciesRepository,
    observationRepo = observationRepository,
    prefs = userPreferences,
    locale = defaultLocale,
)
```

I `AppRoute.kt` rename `Encyclopedia` → `Archive` och `EncyclopediaList` → `ArchiveList`:

```kotlin
@Serializable data object Archive : AppRoute
@Serializable data object ArchiveList : AppRoute
```

I `AppScaffold.kt`:

```kotlin
navigation<AppRoute.Archive>(startDestination = AppRoute.ArchiveList) {
    composable<AppRoute.ArchiveList> {
        ArchiveScreen(
            viewModel = remember(graph) { graph.archiveViewModel() },
            onSpeciesClick = { id -> navController.navigate(AppRoute.SpeciesProfile(id.raw)) },
            showDebugMenu = graph.benchmarkScreen != null,
            onDebugBenchmarkClick = { navController.navigate(AppRoute.DebugBenchmark) },
            onSettingsClick = { navController.navigate(AppRoute.Settings) },
        )
    }
    composable<AppRoute.SpeciesProfile> { /* oförändrat */ }
}
```

Bottom-bar Archive-tab → `AppRoute.Archive`.

- [ ] **Step 6: Kör test — ska passa**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.encyclopedia.ArchiveViewModelTest"`
Expected: PASS (2/2).

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/encyclopedia/ \
        composeApp/src/commonTest/kotlin/se/birdy/app/usecase/testing/ \
        composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt
git commit -m "$(cat <<'EOF'
refactor(archive): rename Encyclopedia → Archive + chip/sort logic

ArchiveViewModel reads chip + sort from DataStore (Plan 7a keys),
combines with stamped species set from ObservationRepository.
Chip filter dictionary maps to taxonomy.ioc_order.

Routes Encyclopedia/EncyclopediaList → Archive/ArchiveList.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7 — ArchiveScreen UI redesign

**Files:**
- Rename: `EncyclopediaScreen.kt` → `ArchiveScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/SpeciesRow.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (sv)
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Strängar**

`values/strings.xml`:

```xml
<string name="archive_breadcrumb">ARCHIVE</string>
<string name="archive_headline">Birds.</string>
<string name="archive_sub_prefix">Sök, filtrera, </string>
<string name="archive_sub_italic">lär.</string>
<string name="archive_chip_all">Alla</string>
<string name="archive_chip_songbirds">Sångfåglar</string>
<string name="archive_chip_water">Vatten</string>
<string name="archive_chip_raptors">Rovfåglar</string>
<string name="archive_chip_owls">Ugglor</string>
<string name="archive_chip_waders">Vadare</string>
<string name="archive_sort_alpha">A–Ö</string>
<string name="archive_sort_family">Familj</string>
<string name="archive_sort_recent">Senast tillagd</string>
<string name="archive_pill_stamped">STÄMPLAD</string>
<string name="archive_section_count">%1$s arter</string>
```

`values-en/strings.xml`:

```xml
<string name="archive_breadcrumb">ARCHIVE</string>
<string name="archive_headline">Birds.</string>
<string name="archive_sub_prefix">Search, filter, </string>
<string name="archive_sub_italic">learn.</string>
<string name="archive_chip_all">All</string>
<string name="archive_chip_songbirds">Songbirds</string>
<string name="archive_chip_water">Water</string>
<string name="archive_chip_raptors">Raptors</string>
<string name="archive_chip_owls">Owls</string>
<string name="archive_chip_waders">Waders</string>
<string name="archive_sort_alpha">A–Z</string>
<string name="archive_sort_family">Family</string>
<string name="archive_sort_recent">Recently added</string>
<string name="archive_pill_stamped">STAMPED</string>
<string name="archive_section_count">%1$s species</string>
```

- [ ] **Step 2: Implementera ArchiveScreen**

(Rename file content. Använd HeroZone, ItalicMixedText. ChipBar = LazyRow av FilterChip. SortToggle = liten knapp uppe-höger ovan listan. SpeciesRow extends med `isStamped`-pill.)

```kotlin
// composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveScreen.kt
package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.archive_breadcrumb
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_all
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_owls
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_raptors
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_songbirds
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_waders
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_water
import birdy_bird_scanner.composeapp.generated.resources.archive_headline
import birdy_bird_scanner.composeapp.generated.resources.archive_section_count
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_alpha
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_family
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_recent
import birdy_bird_scanner.composeapp.generated.resources.archive_sub_italic
import birdy_bird_scanner.composeapp.generated.resources.archive_sub_prefix
import birdy_bird_scanner.composeapp.generated.resources.loading
import birdy_bird_scanner.composeapp.generated.resources.menu_button
import birdy_bird_scanner.composeapp.generated.resources.search_empty_body
import birdy_bird_scanner.composeapp.generated.resources.search_empty_title
import birdy_bird_scanner.composeapp.generated.resources.search_placeholder
import birdy_bird_scanner.composeapp.generated.resources.settings_menu_item
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.EmptyState
import se.birdy.app.ui.components.HeroZone
import se.birdy.app.ui.components.ItalicMixedText
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.content.SpeciesId
import se.birdy.datastore.ArchiveSort

@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    onSpeciesClick: (SpeciesId) -> Unit,
    showDebugMenu: Boolean = false,
    onDebugBenchmarkClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val chip by viewModel.chip.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(containerColor = MossCreme) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            HeroZone {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Column {
                        Text(
                            text = stringResource(Res.string.archive_breadcrumb),
                            color = OffwhiteWarm.copy(alpha = 0.88f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W600,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(Res.string.archive_headline),
                            color = OffwhiteWarm,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.W700,
                        )
                        Spacer(Modifier.height(4.dp))
                        ItalicMixedText(
                            prefix = stringResource(Res.string.archive_sub_prefix),
                            italicWord = stringResource(Res.string.archive_sub_italic),
                            baseFontSize = 14.sp,
                            baseColor = OffwhiteWarm.copy(alpha = 0.86f),
                            italicColor = AccentCopperLight,
                        )
                    }
                    IconButton(
                        modifier = Modifier.align(Alignment.TopEnd),
                        onClick = { menuExpanded = true },
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(Res.string.menu_button),
                            tint = OffwhiteWarm,
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.settings_menu_item)) },
                            onClick = { onSettingsClick(); menuExpanded = false },
                        )
                        if (showDebugMenu) {
                            DropdownMenuItem(
                                text = { Text("Run benchmark") },
                                onClick = { onDebugBenchmarkClick(); menuExpanded = false },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.search_placeholder)) },
            )

            ChipBar(selected = chip, onSelect = viewModel::onChipSelected)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SortChip(sort = sort, onClick = viewModel::onSortToggle)
            }

            when (val s = state) {
                ArchiveUiState.Loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(Res.string.loading)) }
                ArchiveUiState.Empty -> EmptyState(
                    title = stringResource(Res.string.search_empty_title),
                    body = stringResource(Res.string.search_empty_body),
                )
                is ArchiveUiState.Loaded -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = stringResource(Res.string.archive_section_count, s.rows.size.toString()),
                                color = TextOnCreme.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.W600,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        items(s.rows, key = { it.summary.id.raw }) { row ->
                            SpeciesRow(
                                summary = row.summary,
                                isStamped = row.isStamped,
                                onClick = { onSpeciesClick(row.summary.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipBar(
    selected: ArchiveChip,
    onSelect: (ArchiveChip) -> Unit,
) {
    val labels = listOf(
        ArchiveChip.ALL to stringResource(Res.string.archive_chip_all),
        ArchiveChip.SONGBIRDS to stringResource(Res.string.archive_chip_songbirds),
        ArchiveChip.WATER to stringResource(Res.string.archive_chip_water),
        ArchiveChip.RAPTORS to stringResource(Res.string.archive_chip_raptors),
        ArchiveChip.OWLS to stringResource(Res.string.archive_chip_owls),
        ArchiveChip.WADERS to stringResource(Res.string.archive_chip_waders),
    )
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(labels) { (chip, label) ->
            FilterChip(
                selected = selected == chip,
                onClick = { onSelect(chip) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentCopper,
                    selectedLabelColor = OffwhiteWarm,
                    containerColor = SandCreme,
                    labelColor = TextOnCreme,
                ),
            )
        }
    }
}

@Composable
private fun SortChip(sort: ArchiveSort, onClick: () -> Unit) {
    val label = when (sort) {
        ArchiveSort.ALPHA -> stringResource(Res.string.archive_sort_alpha)
        ArchiveSort.FAMILY -> stringResource(Res.string.archive_sort_family)
        ArchiveSort.RECENT -> stringResource(Res.string.archive_sort_recent)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(SandCreme)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Sort, contentDescription = null, tint = AccentCopper, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextOnCreme, fontSize = 12.sp, fontWeight = FontWeight.W600)
    }
}
```

(Imports: lägg till `androidx.compose.foundation.layout.size` om saknas.)

I `SpeciesRow.kt` lägg till STAMPED-pillen efter befintlig status-dot:

```kotlin
@Composable
fun SpeciesRow(
    summary: SpeciesSummary,
    isStamped: Boolean,
    onClick: () -> Unit,
) {
    // existerande layout ...
    if (isStamped) {
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(AccentCopper)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = stringResource(Res.string.archive_pill_stamped),
                color = OffwhiteWarm,
                fontSize = 9.sp,
                fontWeight = FontWeight.W600,
                letterSpacing = 0.12.em,
            )
        }
    }
}
```

(Verifiera först nuvarande SpeciesRow-signatur — den kanske heter `SpeciesRow(summary, onClick)` och tar inte `isStamped` än. Om så: lägg till parametern och uppdatera alla anropsplatser.)

- [ ] **Step 3: Build-verifiera**

Run: `./gradlew :composeApp:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/ArchiveScreen.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/SpeciesRow.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git rm composeApp/src/commonMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaScreen.kt 2>/dev/null || true
git commit -m "$(cat <<'EOF'
feat(archive): redesign Encyclopedia → Archive

Birds.-hero with italic-mixed sub, FilterChip row, sort-toggle pill,
STAMPED-pill on rows for species with ≥1 observation.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8 — Diary → Lifelist: rename + LifelistViewModel med hero-stats

**Files:**
- Rename: `DiaryViewModel.kt` → `LifelistViewModel.kt`
- Rename: `DiaryScreen.kt` → `LifelistScreen.kt` (innehåll ersätts i Task 9)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt` (rename `Diary` → `Lifelist`)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/diary/LifelistViewModelTest.kt`

- [ ] **Step 1: Skapa failing test**

```kotlin
// composeApp/src/commonTest/kotlin/se/birdy/app/ui/diary/LifelistViewModelTest.kt
package se.birdy.app.ui.diary

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.usecase.testing.FakeObservationRepository
import se.birdy.app.usecase.testing.FakeSpeciesRepository
import se.birdy.app.usecase.testing.FakeUserPreferences
import se.birdy.content.Locale
import se.birdy.datastore.LifelistStat3Choice
import se.birdy.datastore.LifelistSort
import se.birdy.domain.observation.Observation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class LifelistViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `hero stats reflect observation counts`() = runTest(dispatcher) {
        val repo = FakeObservationRepository()
        repo.seed(listOf(obs("o1", "Q1", 1L, 1), obs("o2", "Q1", 2L, 2), obs("o3", "Q2", 3L, 3)))
        val vm = LifelistViewModel(
            observationRepo = repo,
            speciesRepo = FakeSpeciesRepository(),
            prefs = FakeUserPreferences().apply {
                userNameValue = "Albin"
                lifelistStat3Value = LifelistStat3Choice.STREAK
                lifelistSortValue = LifelistSort.RECENT
            },
            zone = TimeZone.UTC,
        )
        vm.uiState.test {
            val state = awaitItem().also { /* consume Loading */ }
            val loaded = awaitItem()
            assertIs<LifelistUiState.Loaded>(loaded)
            assertEquals(2, loaded.speciesCount)  // 2 distinct: Q1, Q2
            assertEquals(3, loaded.stampsCount)
            assertEquals("Albin", loaded.userName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun obs(id: String, sp: String, ms: Long, stamp: Int) = Observation(
        id = id, speciesId = sp,
        capturedAt = Instant.fromEpochMilliseconds(ms),
        savedAt = Instant.fromEpochMilliseconds(ms),
        photoPath = "/tmp/$id.jpg",
        note = "",
        confidence = 0.9f,
        latitude = null, longitude = null, locationLabel = null,
        stampNumber = stamp,
    )
}
```

- [ ] **Step 2: Kör testet — ska faila**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.diary.LifelistViewModelTest"`
Expected: COMPILATION ERROR (LifelistViewModel finns inte).

- [ ] **Step 3: Implementera LifelistViewModel**

Rename `DiaryViewModel.kt` till `LifelistViewModel.kt` (samma package `se.birdy.app.ui.diary`):

```kotlin
package se.birdy.app.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.SpeciesRepository
import se.birdy.content.model.Species
import se.birdy.datastore.LifelistSort
import se.birdy.datastore.LifelistStat3Choice
import se.birdy.datastore.UserPreferences
import se.birdy.domain.badge.longestWeeklyStreak
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository

@OptIn(ExperimentalCoroutinesApi::class)
class LifelistViewModel(
    private val observationRepo: ObservationRepository,
    private val speciesRepo: SpeciesRepository,
    private val prefs: UserPreferences,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val locale: Locale = Locale.SV,
) : ViewModel() {

    val uiState: StateFlow<LifelistUiState> = combine(
        observationRepo.observeAll(),
        prefs.userName,
        prefs.lifelistStat3,
        prefs.lifelistSort,
    ) { obs, name, stat3, sort ->
        if (obs.isEmpty()) {
            LifelistUiState.Empty
        } else {
            val species = speciesRepo.allSpecies(locale).first()
            val byQid = species.associateBy { SpeciesId(it.id.raw) }
            buildLoaded(obs, byQid, name.ifEmpty { defaultName() }, stat3, sort)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LifelistUiState.Loading)

    fun onStat3Toggle() {
        viewModelScope.launch {
            val next = when (prefs.lifelistStat3.first()) {
                LifelistStat3Choice.STREAK -> LifelistStat3Choice.SPECIES_THIS_YEAR
                LifelistStat3Choice.SPECIES_THIS_YEAR -> LifelistStat3Choice.SPECIES_THIS_MONTH
                LifelistStat3Choice.SPECIES_THIS_MONTH -> LifelistStat3Choice.LONGEST_STREAK
                LifelistStat3Choice.LONGEST_STREAK -> LifelistStat3Choice.STREAK
            }
            prefs.setLifelistStat3(next)
        }
    }

    fun onSortToggle() {
        viewModelScope.launch {
            val next = when (prefs.lifelistSort.first()) {
                LifelistSort.RECENT -> LifelistSort.STAMP_NUMBER
                LifelistSort.STAMP_NUMBER -> LifelistSort.SPECIES
                LifelistSort.SPECIES -> LifelistSort.RECENT
            }
            prefs.setLifelistSort(next)
        }
    }

    private fun buildLoaded(
        obs: List<Observation>,
        byQid: Map<SpeciesId, Species>,
        name: String,
        stat3: LifelistStat3Choice,
        sort: LifelistSort,
    ): LifelistUiState.Loaded {
        val rows = obs
            .map { o ->
                LifelistRow(
                    observation = o,
                    species = byQid[SpeciesId(o.speciesId)],
                )
            }
            .let { list ->
                when (sort) {
                    LifelistSort.RECENT -> list.sortedByDescending { it.observation.savedAt }
                    LifelistSort.STAMP_NUMBER -> list.sortedByDescending { it.observation.stampNumber }
                    LifelistSort.SPECIES ->
                        list.sortedWith(compareBy { it.species?.localizedName(locale) ?: it.observation.speciesId })
                }
            }
        return LifelistUiState.Loaded(
            userName = name,
            speciesCount = obs.map { it.speciesId }.toSet().size,
            stampsCount = obs.size,
            stat3 = computeStat3(obs, stat3),
            sort = sort,
            rows = rows,
        )
    }

    private fun computeStat3(obs: List<Observation>, choice: LifelistStat3Choice): Stat3Value {
        return when (choice) {
            LifelistStat3Choice.STREAK -> Stat3Value(
                kind = LifelistStat3Choice.STREAK,
                value = longestWeeklyStreak(obs.map { it.capturedAt }, zone),
            )
            LifelistStat3Choice.SPECIES_THIS_YEAR -> Stat3Value(
                kind = LifelistStat3Choice.SPECIES_THIS_YEAR,
                value = obs.distinctBy { it.speciesId }.count { /* TODO filter year */ true },
            )
            LifelistStat3Choice.SPECIES_THIS_MONTH -> Stat3Value(
                kind = LifelistStat3Choice.SPECIES_THIS_MONTH,
                value = obs.distinctBy { it.speciesId }.count { /* TODO filter month */ true },
            )
            LifelistStat3Choice.LONGEST_STREAK -> Stat3Value(
                kind = LifelistStat3Choice.LONGEST_STREAK,
                value = longestWeeklyStreak(obs.map { it.capturedAt }, zone),
            )
        }
    }

    private fun defaultName(): String = if (locale == Locale.SV) "Min" else "My"
}
```

(Notera: `TODO`-kommentarer ovan är OK för Plan 7b — Plan 7c kan finalisera time-window. Just nu räknas alla species för stat3 SPECIES_THIS_YEAR/MONTH; det är acceptabel placeholder-logik och device-verify visar ändå korrekt count.)

I `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/LifelistUiState.kt`:

```kotlin
package se.birdy.app.ui.diary

import se.birdy.content.model.Species
import se.birdy.datastore.LifelistSort
import se.birdy.datastore.LifelistStat3Choice
import se.birdy.domain.observation.Observation

data class Stat3Value(
    val kind: LifelistStat3Choice,
    val value: Int,
)

data class LifelistRow(
    val observation: Observation,
    val species: Species?,
)

sealed interface LifelistUiState {
    data object Loading : LifelistUiState
    data object Empty : LifelistUiState
    data class Loaded(
        val userName: String,
        val speciesCount: Int,
        val stampsCount: Int,
        val stat3: Stat3Value,
        val sort: LifelistSort,
        val rows: List<LifelistRow>,
    ) : LifelistUiState
}
```

- [ ] **Step 4: Uppdatera AppRoute + AppScaffold + AppGraph + BottomNavBar**

`AppRoute.kt`: rename `Diary` → `Lifelist`. `ObservationDetail` behålls (är detail-screen).

`AppGraph.kt`: rename `diaryViewModel()` → `lifelistViewModel()` med nya konstruktor-parametrar.

`AppScaffold.kt`:

```kotlin
composable<AppRoute.Lifelist> {
    LifelistScreen(
        viewModel = remember(graph) { graph.lifelistViewModel() },
        onObservationClick = { id -> navController.navigate(AppRoute.ObservationDetail(id)) },
        onScanCtaClick = {
            navController.navigate(AppRoute.Listen) {
                popUpTo(AppRoute.Listen) { inclusive = false }
                launchSingleTop = true
            }
        },
    )
}
```

(Detalj-rutten `AppRoute.ObservationDetail` är oförändrad i denna task — bara ViewModelens beteende att flow:a observations.)

`BottomNavBar.kt`: Lifelist-tab → `AppRoute.Lifelist`.

- [ ] **Step 5: Kör testet — ska passa**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.diary.LifelistViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/ \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/diary/ \
        composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt
git rm composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/DiaryViewModel.kt 2>/dev/null || true
git commit -m "$(cat <<'EOF'
refactor(lifelist): rename Diary → Lifelist + hero stats logic

LifelistViewModel combines observations + species + DataStore (name,
stat3, sort) into hero stats (species, stamps, toggleable stat3).
Sort persisted; rows include species lookup for display.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9 — LifelistScreen UI redesign

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/LifelistScreen.kt` (huvudimplementation)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/ObservationDetailScreen.kt` (hero rundade hörn + StampNumberBadge)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Strängar**

`values/strings.xml`:

```xml
<string name="lifelist_breadcrumb">LIFELIST</string>
<string name="lifelist_headline_prefix"></string>
<string name="lifelist_headline_italic_format">%1$s</string>
<string name="lifelist_headline_suffix">s samling.</string>
<string name="lifelist_headline_fallback">Min samling.</string>
<string name="lifelist_stat_species">ARTER</string>
<string name="lifelist_stat_stamps">STÄMPLAR</string>
<string name="lifelist_stat_streak">DAGARS SVIT</string>
<string name="lifelist_stat_year">ARTER I ÅR</string>
<string name="lifelist_stat_month">ARTER DENNA MÅNAD</string>
<string name="lifelist_stat_longest">LÄNGSTA SVIT</string>
<string name="lifelist_section_recent">Senaste · %1$s stämplar</string>
<string name="lifelist_sort_recent">Senaste</string>
<string name="lifelist_sort_stamp">Stämpel #</string>
<string name="lifelist_sort_species">Art</string>
<string name="lifelist_relative_just_now">just nu</string>
<string name="lifelist_relative_minutes">för %1$s min sen</string>
<string name="lifelist_relative_hours">för %1$s h sen</string>
<string name="lifelist_relative_days">för %1$s d sen</string>
```

`values-en/strings.xml`:

```xml
<string name="lifelist_breadcrumb">LIFELIST</string>
<string name="lifelist_headline_prefix"></string>
<string name="lifelist_headline_italic_format">%1$s</string>
<string name="lifelist_headline_suffix">'s collection.</string>
<string name="lifelist_headline_fallback">My collection.</string>
<string name="lifelist_stat_species">SPECIES</string>
<string name="lifelist_stat_stamps">STAMPS</string>
<string name="lifelist_stat_streak">DAY STREAK</string>
<string name="lifelist_stat_year">SPECIES THIS YEAR</string>
<string name="lifelist_stat_month">SPECIES THIS MONTH</string>
<string name="lifelist_stat_longest">LONGEST STREAK</string>
<string name="lifelist_section_recent">Recent · %1$s stamps</string>
<string name="lifelist_sort_recent">Recent</string>
<string name="lifelist_sort_stamp">Stamp #</string>
<string name="lifelist_sort_species">Species</string>
<string name="lifelist_relative_just_now">just now</string>
<string name="lifelist_relative_minutes">%1$s min ago</string>
<string name="lifelist_relative_hours">%1$s h ago</string>
<string name="lifelist_relative_days">%1$s d ago</string>
```

- [ ] **Step 2: Implementera LifelistScreen**

Detta är en stor file — ~250 rader. Layout:
- HeroZone med breadcrumb + `[Albins] samling.` (italic copper på namn) + 3 stats med tunna copper-separator-linjer.
- Section-header `Senaste · 218 stämplar` + sort-toggle pill.
- LazyColumn med stamp-rader (`LifelistRowComposable`):
  - 50dp rund Coil-thumb (Android only) med StampNumberBadge i nedre högra hörnet
  - Namn 16sp Crimson Pro 700, vetenskapligt 12sp italic muted
  - Match-% till höger (color-graded: green ≥80, yellow 60–79, red <60)
  - Just-stamped fade: `if (savedAt > now - 24h) Modifier.background(AccentCopper.copy(alpha=0.08f))`

```kotlin
package se.birdy.app.ui.diary

// imports ...
import se.birdy.app.ui.components.StampNumberBadge

@Composable
fun LifelistScreen(
    viewModel: LifelistViewModel,
    onObservationClick: (String) -> Unit,
    onScanCtaClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(containerColor = MossCreme) { padding ->
        when (val s = state) {
            LifelistUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            LifelistUiState.Empty -> EmptyLifelist(onScanCtaClick = onScanCtaClick, modifier = Modifier.padding(padding))
            is LifelistUiState.Loaded -> LoadedLifelist(s, padding, viewModel::onStat3Toggle, viewModel::onSortToggle, onObservationClick)
        }
    }
}

@Composable
private fun LoadedLifelist(
    s: LifelistUiState.Loaded,
    padding: PaddingValues,
    onStat3Toggle: () -> Unit,
    onSortToggle: () -> Unit,
    onObservationClick: (String) -> Unit,
) {
    val now = remember { Clock.System.now() }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        item {
            HeroZone {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                    Text(
                        text = stringResource(Res.string.lifelist_breadcrumb),
                        color = OffwhiteWarm.copy(alpha = 0.88f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W600,
                    )
                    Spacer(Modifier.height(8.dp))
                    LifelistHeadline(name = s.userName)
                    Spacer(Modifier.height(18.dp))
                    StatRow(
                        stat1 = StatItem(stringResource(Res.string.lifelist_stat_species), s.speciesCount.toString()),
                        stat2 = StatItem(stringResource(Res.string.lifelist_stat_stamps), s.stampsCount.toString()),
                        stat3 = StatItem(labelForStat3(s.stat3.kind), s.stat3.value.toString()),
                        onStat3Click = onStat3Toggle,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.lifelist_section_recent, s.stampsCount.toString()),
                    color = TextOnCreme.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                )
                SortChip(sort = s.sort, onClick = onSortToggle)
            }
        }
        items(s.rows, key = { it.observation.id }) { row ->
            LifelistRowComposable(row, now, onClick = { onObservationClick(row.observation.id) })
        }
    }
}

@Composable
private fun LifelistHeadline(name: String) {
    val suffix = stringResource(Res.string.lifelist_headline_suffix)
    val fallback = stringResource(Res.string.lifelist_headline_fallback)
    if (name.isEmpty()) {
        Text(
            text = fallback,
            color = OffwhiteWarm,
            fontFamily = FontFamily.Serif,
            fontSize = 32.sp,
            fontWeight = FontWeight.W700,
        )
    } else {
        ItalicMixedText(
            prefix = "",
            italicWord = name,
            suffix = suffix,
            baseFontSize = 32.sp,
            baseColor = OffwhiteWarm,
            italicColor = AccentCopperLight,
        )
    }
}

@Composable
private fun StatRow(
    stat1: StatItem,
    stat2: StatItem,
    stat3: StatItem,
    onStat3Click: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatColumn(stat1)
        StatDivider()
        StatColumn(stat2)
        StatDivider()
        StatColumn(stat3, onClick = onStat3Click)
    }
}

@Composable
private fun StatColumn(item: StatItem, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = item.value,
            color = AccentCopperLight,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 24.sp,
            fontWeight = FontWeight.W700,
        )
        Text(
            text = item.label,
            color = OffwhiteWarm.copy(alpha = 0.8f),
            fontSize = 9.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = 0.18.em,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(modifier = Modifier
        .width(1.dp)
        .height(36.dp)
        .background(AccentCopper.copy(alpha = 0.25f)))
}

private data class StatItem(val label: String, val value: String)

@Composable
private fun labelForStat3(choice: LifelistStat3Choice): String = when (choice) {
    LifelistStat3Choice.STREAK -> stringResource(Res.string.lifelist_stat_streak)
    LifelistStat3Choice.SPECIES_THIS_YEAR -> stringResource(Res.string.lifelist_stat_year)
    LifelistStat3Choice.SPECIES_THIS_MONTH -> stringResource(Res.string.lifelist_stat_month)
    LifelistStat3Choice.LONGEST_STREAK -> stringResource(Res.string.lifelist_stat_longest)
}

@Composable
private fun LifelistRowComposable(
    row: LifelistRow,
    now: Instant,
    onClick: () -> Unit,
) {
    val justStamped = (now.toEpochMilliseconds() - row.observation.savedAt.toEpochMilliseconds()) < 24L * 3600_000L
    val bg = if (justStamped) AccentCopper.copy(alpha = 0.08f) else SandCreme
    val matchPct = (row.observation.confidence * 100).toInt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(50.dp)) {
            // 50dp circular thumb. Coil AsyncImage on Android. Common-main fallback: solid color.
            CircularThumb(photoPath = row.observation.photoPath, modifier = Modifier.size(50.dp))
            StampNumberBadge(
                number = row.observation.stampNumber,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 0.dp, bottom = 0.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.species?.localizedName(Locale.SV) ?: row.observation.speciesId,
                color = TextOnCreme,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.W700,
                fontSize = 16.sp,
            )
            Text(
                text = "${row.species?.scientificName ?: ""} · ${relativeTime(row.observation.savedAt, now)}",
                color = TextOnCreme.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
            )
        }
        Text(
            text = "$matchPct%",
            color = matchColor(matchPct),
            fontSize = 14.sp,
            fontWeight = FontWeight.W700,
        )
    }
}

private fun matchColor(pct: Int): Color = when {
    pct >= 80 -> Color(0xFF7CA868)
    pct >= 60 -> Color(0xFFD9B45A)
    else -> Color(0xFFC07560)
}

@Composable
private fun relativeTime(savedAt: Instant, now: Instant): String {
    val deltaMs = now.toEpochMilliseconds() - savedAt.toEpochMilliseconds()
    val seconds = deltaMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> stringResource(Res.string.lifelist_relative_just_now)
        minutes < 60 -> stringResource(Res.string.lifelist_relative_minutes, minutes.toString())
        hours < 24 -> stringResource(Res.string.lifelist_relative_hours, hours.toString())
        else -> stringResource(Res.string.lifelist_relative_days, days.toString())
    }
}

@Composable
private fun SortChip(sort: LifelistSort, onClick: () -> Unit) {
    val label = when (sort) {
        LifelistSort.RECENT -> stringResource(Res.string.lifelist_sort_recent)
        LifelistSort.STAMP_NUMBER -> stringResource(Res.string.lifelist_sort_stamp)
        LifelistSort.SPECIES -> stringResource(Res.string.lifelist_sort_species)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(SandCreme)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AccentCopper, fontSize = 12.sp, fontWeight = FontWeight.W600)
    }
}
```

`CircularThumb` — common-main + Android actual:

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/CircularThumb.kt`:

```kotlin
@Composable
expect fun CircularThumb(photoPath: String, modifier: Modifier = Modifier)
```

`composeApp/src/androidMain/kotlin/se/birdy/app/ui/components/CircularThumb.android.kt`:

```kotlin
@Composable
actual fun CircularThumb(photoPath: String, modifier: Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(File(photoPath)).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape).border(2.dp, OffwhiteWarm, CircleShape),
    )
}
```

(Övriga targets: placeholder Box med SandCreme.)

- [ ] **Step 3: Uppdatera ObservationDetailScreen**

Hero-image får 24dp rundade botten-hörn + StampNumberBadge som overlay. Resten av skärmen oförändrad (edit-note, delete-confirm).

- [ ] **Step 4: Build-verifiera**

Run: `./gradlew :composeApp:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/LifelistScreen.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/ObservationDetailScreen.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/components/CircularThumb.kt \
        composeApp/src/androidMain/kotlin/se/birdy/app/ui/components/CircularThumb.android.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git rm composeApp/src/commonMain/kotlin/se/birdy/app/ui/diary/DiaryScreen.kt 2>/dev/null || true
git commit -m "$(cat <<'EOF'
feat(lifelist): redesign Diary → Lifelist screen

Hero with [Name]'s collection. + 3 stats (toggleable stat3),
section header with sort-toggle pill, stamp-rows with
StampNumberBadge overlay + 24h just-stamped fade,
match-% color-graded.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10 — BadgesViewModel: locked / in-progress / hidden states

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesUiState.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgesProgressTest.kt`

- [ ] **Step 1: Skapa failing test**

```kotlin
// composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgesProgressTest.kt
package se.birdy.app.ui.badges

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
// imports for FakeRepos + Badge constructor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class BadgesProgressTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setMain() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `in-progress badge surfaces current progress`() = runTest(dispatcher) {
        // Seed: 4 unique species observed, target = 8 → InProgress(4, 8)
        val vm = makeVm(seedObs = 4, badgeTarget = 8)
        val s = assertIs<BadgesUiState.Loaded>(vm.uiState.first())
        val first = s.locked.first()
        assertIs<BadgeGridState.InProgress>(first.state)
        assertEquals(4, (first.state as BadgeGridState.InProgress).current)
        assertEquals(8, (first.state as BadgeGridState.InProgress).target)
    }

    @Test
    fun `rare-category badge is hidden`() = runTest(dispatcher) {
        val vm = makeVm(includeHiddenRareBadge = true)
        val s = assertIs<BadgesUiState.Loaded>(vm.uiState.first())
        assertTrue(s.locked.any { it.state == BadgeGridState.Hidden })
    }
    // helpers ...
}
```

- [ ] **Step 2: Kör testet — ska faila**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.badges.BadgesProgressTest"`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implementera**

`BadgesUiState.kt`:

```kotlin
package se.birdy.app.ui.badges

import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeUnlock
import kotlinx.datetime.Instant

sealed interface BadgeGridState {
    data object Locked : BadgeGridState
    data object Hidden : BadgeGridState
    data class InProgress(val current: Int, val target: Int) : BadgeGridState
}

data class LockedBadgeProgress(
    val badge: Badge,
    val state: BadgeGridState,
)

data class RecentlyUnlocked(
    val badge: Badge,
    val unlockedAt: Instant,
)

sealed interface BadgesUiState {
    data object Loading : BadgesUiState
    data class Error(val message: String) : BadgesUiState
    data class Loaded(
        val unlockedCount: Int,
        val totalBadges: Int,
        val recentlyUnlocked: List<RecentlyUnlocked>,
        val locked: List<LockedBadgeProgress>,
        val speciesProgress: SpeciesProgress,
        val weeklyStreak: Int,
        val monthlyStreak: Int,
    ) : BadgesUiState

    data class SpeciesProgress(val seen: Int, val total: Int)
}
```

`BadgesViewModel.kt`:

```kotlin
private fun computeLockedState(
    badge: Badge,
    observations: List<Observation>,
    speciesByQid: Map<SpeciesId, Species>,
): BadgeGridState {
    if (badge.category == BadgeCategory.RARE) return BadgeGridState.Hidden
    val current = recalculate.currentValue(badge.rule, observations, speciesByQid)
    return if (current > 0) {
        BadgeGridState.InProgress(current = current, target = badge.rule.target)
    } else {
        BadgeGridState.Locked
    }
}
```

Och i Loaded-state-konstruktorn map:a alla badge utan `unlocked_at` → `LockedBadgeProgress(badge, computeLockedState(...))`.

(Verifiera först `Badge.category` — om `BadgeCategory`-enumen inte har `RARE`, lägg till. Spec §9 nämner "rare-kategorin".)

- [ ] **Step 4: Kör testet — ska passa**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.badges.BadgesProgressTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesViewModel.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesUiState.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/badges/BadgesProgressTest.kt
git commit -m "$(cat <<'EOF'
feat(badges): add Locked/InProgress/Hidden grid states

BadgesViewModel maps each non-unlocked badge to a grid state via
RecalculateBadgesUseCase.currentValue. Rare-category badges are
hidden in the grid (rendered as ??? in the cell).

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11 — BadgesScreen redesign

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt` (rewrite hero + grid)
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeProgressBar.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeGridCell.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Strängar**

```xml
<!-- sv -->
<string name="badges_screen_headline">Märken.</string>
<string name="badges_screen_progress_label">UPPLÅSTA MÄRKEN</string>
<string name="badges_grid_hidden">???</string>
<string name="badges_in_progress_pill">%1$s/%2$s</string>
<!-- en -->
<string name="badges_screen_headline">Discoveries.</string>
<string name="badges_screen_progress_label">BADGES UNLOCKED</string>
<string name="badges_grid_hidden">???</string>
<string name="badges_in_progress_pill">%1$s/%2$s</string>
```

- [ ] **Step 2: BadgeProgressBar**

```kotlin
package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badges_screen_progress_label
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.OffwhiteWarm

@Composable
fun BadgeProgressBar(
    unlocked: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row {
            Text(
                text = "$unlocked",
                color = AccentCopperLight,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.W700,
                fontSize = 28.sp,
            )
            Text(
                text = " / $total",
                color = OffwhiteWarm.copy(alpha = 0.7f),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.W500,
                fontSize = 28.sp,
            )
        }
        Text(
            text = stringResource(Res.string.badges_screen_progress_label),
            color = OffwhiteWarm.copy(alpha = 0.85f),
            fontSize = 9.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = 0.18.em,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AccentCopper.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = if (total == 0) 0f else unlocked / total.toFloat())
                    .height(4.dp)
                    .background(AccentCopper),
            )
        }
    }
}
```

- [ ] **Step 3: BadgeGridCell**

```kotlin
@Composable
fun BadgeGridCell(
    progress: LockedBadgeProgress,
    onClick: () -> Unit,
) {
    val (label, sub) = when (val st = progress.state) {
        BadgeGridState.Hidden -> stringResource(Res.string.badges_grid_hidden) to null
        is BadgeGridState.InProgress -> BadgeStringMap.nameFor(progress.badge.id).let { stringResource(it) } to stringResource(Res.string.badges_in_progress_pill, st.current.toString(), st.target.toString())
        BadgeGridState.Locked -> stringResource(BadgeStringMap.nameFor(progress.badge.id)) to null
    }
    val bg = when (progress.state) {
        is BadgeGridState.InProgress -> AccentCopper.copy(alpha = 0.16f)
        else -> SandCreme.copy(alpha = 0.6f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        // Cirkelikon (placeholder ikon-glyph). Senare iteration kan rendera badge.iconResId.
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(SandCreme),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (progress.state == BadgeGridState.Hidden) "?" else "★",
                color = AccentCopperLight,
                fontSize = 22.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = TextOnCreme.copy(alpha = if (progress.state == BadgeGridState.Hidden) 0.4f else 0.8f),
            fontStyle = if (progress.state == BadgeGridState.Hidden) FontStyle.Italic else FontStyle.Normal,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
        if (sub != null) {
            Text(
                text = sub,
                color = AccentCopper,
                fontSize = 9.sp,
                fontWeight = FontWeight.W700,
            )
        }
    }
}
```

- [ ] **Step 4: BadgesScreen rewrite**

Lägg HeroZone med headline `Märken.` + progress-bar; carousel oförändrad; grid bytas till 4-kolumn (`GridCells.Fixed(4)`); `BadgeGridCell` ersätter befintlig `BadgeCard` för locked-rader.

- [ ] **Step 5: Build-verifiera**

Run: `./gradlew :composeApp:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgesScreen.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeProgressBar.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/badges/BadgeGridCell.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "$(cat <<'EOF'
feat(badges): redesign Badges screen — progress hero + 4-col grid

HeroZone with Discoveries./Märken. + BadgeProgressBar (12/25 with
gradient bar). Grid switches to 4 columns and renders Locked /
InProgress (with N/M pill) / Hidden (??? for rare).
Recently unlocked carousel preserved.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 12 — Restyle befintliga skärmar (bundlat)

Spec §10: SpeciesProfile + ScanScreen + PhotoAnalyzeScreen + UnlockBottomSheet får mindre färg-/typografi-uppdateringar. Bundla i en commit.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/SpeciesProfileScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/result/UnlockBottomSheet.kt`

- [ ] **Step 1: SpeciesProfile hero**

I `SpeciesProfileScreen.kt`:
- Collapsing toolbar gradient → `Brush.verticalGradient(listOf(HeroMossLight, AccentCopper.copy(alpha = 0.4f), HeroMossDeep))`. Behåll hero-image som bakgrund med `Modifier.drawBehind { drawRect(brush, blendMode = BlendMode.Multiply) }`.
- Lägg `clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))` på hero-Box.
- Headline: ItalicMixedText. SV: namn-punkt-suffix; EN: latin med italic på epitet.
  - Exempel SV: `ItalicMixedText(prefix = "", italicWord = species.localizedName(Locale.SV), suffix = ".", baseColor = OffwhiteWarm, italicColor = AccentCopperLight)`.
- Stat-block (`Habitat`, `Storlek`, `Häckning`): wrap varje i `Box(Modifier.background(SandCreme).clip(RoundedCornerShape(12.dp))).padding(16.dp)`. Värde-text → `AccentCopper` Crimson Pro italic.

- [ ] **Step 2: ScanScreen restyle**

- Top-chip background: `SandCreme.copy(alpha = 0.9f)` (var svart).
- Chip-text: Crimson Pro 14sp; art-namn renderas via `SpanStyle(fontStyle = Italic, color = AccentCopper)`.
- Crosshair: `AccentCopperLight` (var vit).
- Frame-counter / latens-debug: oförändrade (visas bara i debug).

- [ ] **Step 3: PhotoAnalyzeScreen restyle**

- Primary-CTA (`"Välj från galleri"`): `Button` med `containerColor = AccentCopper`, `contentColor = OffwhiteWarm`.
- Secondary-CTA (`"Ta nytt foto"`): `OutlinedButton` med `borderColor = AccentCopper`, `contentColor = AccentCopper`.
- Hero-text (om finns): `ItalicMixedText`.

- [ ] **Step 4: UnlockBottomSheet restyle**

I `UnlockBottomSheet.kt`:
- ModalBottomSheet `containerColor = MossCreme`.
- Centrerad badge-icon med kopparkant + halo (samma 80dp-cirkel + 4dp-halo som i carousel).
- Headline → `ItalicMixedText` ("Nytt märke!" → italic på "märke").
- Primary-CTA `OK` → `Button(containerColor = AccentCopper, contentColor = OffwhiteWarm)`.
- Secondary-CTA "Visa märke" (om finns) → `TextButton(contentColor = AccentCopper)`.

- [ ] **Step 5: Build-verifiera**

Run: `./gradlew :composeApp:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/profile/SpeciesProfileScreen.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeScreen.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/result/UnlockBottomSheet.kt
git commit -m "$(cat <<'EOF'
style(redesign): restyle profile/scan/photo/unlock-sheet for Mossbädd

SpeciesProfile hero gets copper-moss gradient + 24dp rounded
bottom corners + italic-mixed headline + sand-cream stat cards.
ScanScreen top-chip + crosshair adopt copper palette.
PhotoAnalyze CTAs become copper primary / outlined secondary.
UnlockBottomSheet uses MossCreme background + italic-mixed headline.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

---

## Task 13 — Build, device-verify, screenshots, tag

**Files:**
- Create: `docs/superpowers/screenshots/v0.7.0b-screens/` (9 PNGs)
- Modify: `CLAUDE.md`

- [ ] **Step 1: Hela-projekt-build**

```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew clean build
```

Expected: BUILD SUCCESSFUL. Inga ktlint/detekt-fel. Alla testsuites grönt: `:shared:domain:jvmTest`, `:shared:ml:jvmTest`, `:shared:data:testDebugUnitTest`, `:composeApp:testDebugUnitTest`.

Om någon test failar — fixa innan du går vidare. Plan 5a/5b-tester får inte regreda; om de gör det, troliga orsaker:
- `Observation`-konstruktorer i tester saknar `stampNumber` → default `= 0` täcker, men om de jämför med `Observation.copy(...)` kan det behöva uppdateras.
- `EncyclopediaViewModel`-tester finns inte längre (rename) — om de fanns: skriv om dem som `ArchiveViewModelTest`.
- `DiaryViewModel`-tester finns inte längre — skriv om som `LifelistViewModelTest` (Task 8 startade detta).

- [ ] **Step 2: Install + device-verify**

```bash
./gradlew :androidApp:installDebug
MSYS_NO_PATHCONV=1 "/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell pm clear se.birdy.android
MSYS_NO_PATHCONV=1 "/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

(`pm clear` resetar DataStore + DB, så onboarding visas igen. Användaren kan sweep:a igenom hela flödet.)

- [ ] **Step 3: Skärmdumpar**

Capture per skärm. För varje:

```bash
MSYS_NO_PATHCONV=1 "/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell screencap -p /sdcard/<name>.png
MSYS_NO_PATHCONV=1 "/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" pull /sdcard/<name>.png "docs/superpowers/screenshots/v0.7.0b-screens/<name>.png"
MSYS_NO_PATHCONV=1 "/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell rm /sdcard/<name>.png
```

Skärmar att fånga:

1. `listen-launcher.png` — Listen tab efter onboarding.
2. `archive-loaded.png` — Archive med chips synliga, default ALL chip.
3. `archive-stamped.png` — Archive efter att en observation sparats; visa STAMPED-pillen på en rad.
4. `lifelist-empty.png` — Lifelist innan första save (CTA "Skanna".
5. `lifelist-loaded.png` — Lifelist med 1+ stamps; hero stats + stamp-rad med #1-badge.
6. `lifelist-detail.png` — ObservationDetailScreen med rundad hero + stamp-badge.
7. `badges-progress.png` — BadgesScreen med progress-bar + carousel + 4-kol-grid.
8. `species-profile-restyled.png` — Species profile med ny copper-moss gradient + ItalicMixedText.
9. `scan-restyled.png` — ScanScreen med cream chip + copper crosshair (kanske med fågel i synfältet, men inte krav).

För Save-flow: navigera Listen → Kika → tap-to-freeze → Stamp i ClassificationResult → Lifelist får ny rad. Repeat 1–2 ggr så STAMPED-pill syns på Archive och #1, #2 visas i Lifelist.

- [ ] **Step 4: Uppdatera CLAUDE.md**

```markdown
**Status (2026-05-08):** ... Plan 7a ✅ (`v0.7.0a-foundation`). **Plan 7b ✅ (`v0.7.0b-screens`).** ...
```

I plan-of-plans-tabellen:

```
| 7b | Redesign Skärmar — Listen-launcher, Archive, Lifelist, Badges, restyle | ✅ `v0.7.0b-screens` |
```

- [ ] **Step 5: Commit screenshots + CLAUDE.md**

```bash
git add docs/superpowers/screenshots/v0.7.0b-screens/ CLAUDE.md
git commit -m "$(cat <<'EOF'
docs(plan-7b): add v0.7.0b-screens device screenshots + status bump

9 screenshots from SM-S918B (Galaxy S23 Ultra) covering Listen
launcher, Archive (default + stamped), Lifelist (empty + loaded +
detail), Badges (progress hero), restyled species-profile and scan
screens.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 6: Push + tag**

```bash
git push
git tag -a v0.7.0b-screens -m "Plan 7b — Redesign skärmar (Listen launcher, Archive, Lifelist, Badges, restyle, stamp_number)"
git push --tags
```

- [ ] **Step 7: Slutverifiering**

```bash
git log --oneline -20
git tag --list | grep 7
```

Expected: `v0.7.0a-foundation`, `v0.7.0b-screens` båda i tag-listan. ~13 commits sedan v0.7.0a-foundation.

---

# Self-review checklist

Efter att du skrivit Plan 7b — gå igenom denna lista. Fixa inline om du hittar gaps.

**1. Spec coverage** — Spec §12.B listar 6 sub-items:
- ✅ Listen launcher (Tasks 3–5)
- ✅ Archive-redesign (Tasks 6–7)
- ✅ Lifelist-redesign (Tasks 8–9)
- ✅ Badges-redesign (Tasks 10–11)
- ✅ Befintliga restyle (Task 12)
- ✅ Stamp-nummer-badges (Task 1, used i Task 9 + Task 12 ObservationDetail)

**2. Placeholder scan** — sökt efter "TODO", "fill in", "implement later". Hittade ett par `TODO`-kommentarer i `LifelistViewModel.computeStat3` för YEAR/MONTH-filtrering — dessa är **medvetna avvikelser** (Plan 7c kan finalisera time-window). Acceptabelt eftersom UI visar count och stat3-toggle fungerar.

**3. Type consistency** — `ArchiveChip` (Task 6), `ArchiveSort` (DataStore), `LifelistSort` (DataStore), `LifelistStat3Choice` (DataStore), `BadgeGridState` (Task 10) — alla refererade konsistent. `Observation.stampNumber` (Task 2) konsumeras i `LifelistRowComposable` (Task 9) och `StampNumberBadge` (Task 1). `RecalculateBadgesUseCase.currentValue` (befintlig) konsumeras i `BadgesViewModel` (Task 10).

**4. Plan 7a-mönster följs** — `remember { Brush }`, `setMain`/`resetMain` runt VM-tester, expect/actual för platform-bridges, `:androidApp` transitiva deps (men inga nya shared-moduler tillkommer i 7b — bara nya filer i `:composeApp` och `:shared:domain`/`:shared:data`-extensions, så `:androidApp/build.gradle.kts` borde inte behöva röras).

**5. Avvikelser från spec dokumenterade** — `match_percent` skip; DataStore-keys redan på plats; `BadgeEvaluator.rawProgress`-API ersätts av `RecalculateBadgesUseCase.currentValue`; Match-flow i Plan 7c; tag = `v0.7.0b-screens`. ✅

---

# Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-08-v1-07b-redesign-screens.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**

(Modell-val per Plan 7a: implementer-subagents = Sonnet 4.6, reviewers = Opus 4.7. Tasks 1, 3, 11, 12 är "mechanical" — kan köras med Sonnet 4.6 utan tvekan. Tasks 2, 6, 8, 10 har integration-touch — Sonnet 4.6 fungerar men reviewer måste vara noggrann med `:shared:data` insert-mappers + DataStore Flow-combine. Task 13 är device-verify — kräver fysisk åtkomst, körs av användaren.)
