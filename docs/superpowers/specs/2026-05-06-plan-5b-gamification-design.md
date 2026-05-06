# Birdy Bird Scanner — Plan 5b: Gamification Design Spec

**Datum:** 2026-05-06
**Status:** Utkast — väntar på användargranskning
**Spec-typ:** Plan 5b av 6 (split: 5a = Diary klar `v0.5.0a-diary`; 5b = Gamification denna spec)
**Föregående:** Plan 5a (Diary) klar — `v0.5.0a-diary`. Plan 2b (content backfill) på 149/700 arter (anatidae nyss klar), kan köra parallellt med 5b (pure-data).
**Författare:** Albin Lindblom + Claude Code (brainstormingsession 2026-05-06)

---

## 1. Bakgrund och syfte

Plan 5 i v1-specen är "Diary & Gamification". Plan 5a byggde diary-stacken (DB, save-flow, list, detail). 5b lägger till **gamification-skiktet ovanpå**: en katalog av ~25 märken som unlocks reaktivt när användarens observationer matchar regler, plus art-progression-räknare (X av ~700 arter) och två parallella streak-spår (veckovis + månadsvis).

Designen är en *hybrid tonad sport*: progression-feedback finns och firas (bottom-sheet vid unlock), men tonen är jordnära/dämpad i linje med Mossbädd-temat. Inga konfetti-explosioner, inga shame-streaks, inga "missade dagar"-notiser. Användaren ska känna att appen *upptäcker* deras milstolpar — inte gamifierar dem aggressivt.

**Plan 5b är klar när:**

- `Märken`-fliken visar `BadgesScreen` (ersätter `BadgesStubScreen`) med hero (X/Y arter + märken-räknare + opt-in streak-piller), "Senast upptäckta"-carousel och "Att upptäcka"-silhouett-grid.
- ~25 märken finns i `shared/content/.../badges.yaml`, organiserade i 6 kategorier: Progression (3), Streaks veckor (4), Streaks månader (3), Säsong (4), Familjer (8), Sällsynt (3).
- `RecalculateBadgesUseCase` är en pure function som tar in observationer + species-map + katalog + befintliga unlocks → returnerar nya unlocks.
- `SaveObservationUseCase` (utökas från Plan 5a) kör recalc efter Save och returnerar `SaveResult(observationId, newUnlocks)`.
- `UnlockBottomSheet` visas i sekvens efter Save-snackbar för varje nytt märke (queue-baserat). Subtil koppar-glöd-animation, ingen konfetti.
- Alla 25 märken har silhouett-representation i grid:n innan unlock; tap-on-locked → snackbar "Hemligt — fortsätt skåda".
- `BadgeBackfillOnAppStart` kör en gång efter version-bump av `badges.yaml` och persist:ar retroaktiva unlocks tyst (ingen bottom-sheet).
- Implicit opt-in på streak-piller: dolda när < 2 veckor/månader; växer fram naturligt.
- Alla strängar i sv + en. Repository-tester gröna med in-memory SQLDelight; VM- och use-case-tester gröna; rule-engine-tester exhaustive över alla 6 rule-typer + edge cases.
- Manuell device-verifiering på SM-S918B med skärmdumpar.
- Tag `v0.5.0b-gamification` pushad.

---

## 2. Låsta beslut från brainstormingen

| # | Beslut | Motivering |
|---|---|---|
| 1 | **Tonalitet:** Hybrid tonad sport — opt-in streak-räknare, progress-bars, diskret firande | Mossbädd-tonen tål inte full sport-känsla; men tonad sport ger entusiasterna något att bita i utan att stressa nybörjare |
| 2 | **Katalog-omfång:** ~25 märken med YAML-DSL i `shared/content/.../badges.yaml` | Bred täckning över 6 regeltyper; YAML är data-only-config så framtida tillägg är icke-kod-ändring |
| 3 | **Streaks:** Två parallella spår — veckovis (4/12/26/52v) OCH månadsvis (3/6/12m) | Veckovis fångar regelbundenhet, månadsvis är mer förlåtande för säsongsskådare; måndag = veckans start (ISO 8601) |
| 4 | **Synlighet:** Dolda silhouetter tills upplåsta | Skattjakts-känsla; hela poängen med en bred katalog är att ge "vad-finns-där?"-mysterium; tap-on-locked → snackbar "Hemligt — fortsätt skåda" |
| 5 | **Unlock-firande:** `ModalBottomSheet` direkt vid Save (ej snackbar, ej modal full-screen) | Dolda märken kräver avslöjande-moment; bottom-sheet är diskret nog för Mossbädd-tonen men ger märket sin moment |
| 6 | **Tiers:** Tiers-som-separata-märken (3 distinkta märken för 5/25/100 arter) | Mer firande-tillfällen; varje silhouett är ett mysterium; regelmotorn är enklare när varje rad är binär unlocked-status |
| 7 | **Streak opt-in:** Implicit — streak-piller dolda i hero tills användaren har ≥2 veckor/månader | Ingen scope-creep till Settings; noll-state-hero ser inte misslyckad ut; streaks dyker upp som upptäckt; ingen explicit shame |
| 8 | **Recalc-arkitektur:** Push-vid-Save direkt i `SaveObservationUseCase` | Save är enda mutations-punkten i v1 (ingen import, ingen edit); simplest-thing-that-works; noll race conditions; refactor till reactive Flow möjlig i Plan 6 utan breaking change |
| 9 | **Layout:** Hero (87/700 + 3 stat-piller) + body ("Senast upptäckta" carousel + "Att upptäcka"-silhouett-grid) | Kombinerar hero-tunghet (variant A) med upptäckts-fokus (variant C); UPPERCASE-section-labels matchar Mossbädd-typografi |
| 10 | **Glow-animation** på celebration: subtil koppar-glöd 1.5s loop, stannar efter ~3 sek | Avsiktlig friction efter Save — du har just nått en milstolpe; ingen konfetti/partiklar |
| 11 | **i18n-strategi för badge-text:** strings.xml är källa, YAML innehåller dokumentations-kopia av `name_sv`/`name_en` | Matchar projektets befintliga i18n-disciplin; build-time-validator cross-checkar YAML-id:n mot `badge_name_<id>`-keys |
| 12 | **Meteorologiska säsonger** (mar-maj=vår, jun-aug=sommar, sep-nov=höst, dec-feb=vinter) | Matchar SMHI-konvention för svenska användare |
| 13 | **`SaveResult` ersätter `String`-id** som return från `SaveObservationUseCase.save()` | Plan 5a:s API bryts; trivial refactor (en call-site i ClassificationResultViewModel) |
| 14 | **`BadgeBackfillOnAppStart` är tyst** — inga UnlockBottomSheets vid retroaktiva unlocks | Annars skulle app-update med nya märken trigga 5+ bottom-sheets vid första öppning |
| 15 | **Save-CTA disabled** så länge unlock-queue är non-empty | Förhindrar race där nya unlocks queue:as ovanpå pågående firande |
| 16 | **Familje-listan (8 familjer) finaliseras i Task 1** av implementation-plan | Default: anatidae, paridae, accipitridae, corvidae, fringillidae, turdidae, sylviidae, picidae — kan tweakas (t.ex. byta sylviidae mot laridae/måsar eller strigidae/ugglor) |

---

## 3. Arkitektur och moduler

### Moduler

```
shared/content
  ├─ commonMain/resources/se/birdy/content/
  │    └─ badges.yaml                        ← NY: regelfil för 25 märken
  └─ commonMain/kotlin/se/birdy/content/badges/
       ├─ BadgeRule.kt                       ← sealed: CountUnique, WeeklyStreak, ...
       ├─ BadgeCatalog.kt                    ← parsing av badges.yaml
       └─ BadgeCatalogLoader.kt              ← read-on-startup (cached)

shared/domain
  └─ commonMain/kotlin/se/birdy/domain/badge/
       ├─ Badge.kt                           ← data class
       ├─ BadgeUnlock.kt                     ← (badgeId, unlockedAt: Instant)
       ├─ BadgeProgress.kt                   ← (badgeId, current, target, unlock?)
       ├─ BadgeRepository.kt                 ← interface: observeUnlocks(), persist(unlocks)
       └─ RecalculateBadgesUseCase.kt        ← pure function

shared/data
  ├─ sqldelight/.../db/BadgeUnlock.sq        ← NY: badge_unlock-tabell
  └─ kotlin/se/birdy/data/
       └─ BadgeRepositoryImpl.kt             ← Flow-baserad

composeApp
  └─ commonMain/kotlin/se/birdy/app/
       ├─ ui/badges/
       │    ├─ BadgesScreen.kt               ← ersätter BadgesStubScreen
       │    ├─ BadgesViewModel.kt            ← combine(observations, unlocks, totalSpecies)
       │    ├─ BadgesUiState.kt
       │    ├─ BadgeCard.kt                  ← låst/upplåst-cell
       │    ├─ BadgeStatHero.kt              ← hero-sektion
       │    ├─ BadgeRecentCard.kt            ← carousel-card
       │    └─ UnlockBottomSheet.kt          ← firande-overlay
       ├─ usecase/
       │    └─ SaveObservationUseCase.kt     ← UTÖKAS: kör recalc, returnerar SaveResult
       └─ bootstrap/
            ├─ BadgeBackfillOnAppStart.kt    ← tyst version-bump-recalc
            └─ BadgeVersionStore.kt          ← expect/actual SharedPreferences-wrapper
```

### Beroende-riktning

```
composeApp → shared/data → shared/domain ← shared/content
```

- `shared/data` skriver till `badge_unlock`-tabell i samma SQLDelight-DB som `observation` (Plan 5a).
- `shared/content` är read-only-asset packad i APK (badges.yaml).
- Compose UI har bara `BadgeRepository`-interface från `shared/domain` + `BadgeCatalogLoader` från `shared/content` — ingen direkt SQLDelight- eller YAML-import.

### DI-utökning

`SaveObservationUseCase` får två nya beroenden:
- `BadgeRepository` (för att läsa befintliga unlocks + persist nya)
- `BadgeCatalogLoader` (för att hämta `BadgeCatalog`)

Manuell wiring i `App.kt` eller Koin-modul. Task 1 i implementation-plan verifierar.

### Vad ändras INTE

- `Observation.kt`, `ObservationRepository`, `DiaryViewModel`, `DiaryScreen` — orörda.
- Plan 5a:s SQLDelight-schema — vi *adderar* en tabell, ingen migration på `observation`.
- Bottom-nav — `Märken`-fliken finns redan som stub.

---

## 4. Datamodell

### `badges.yaml` (struktur)

YAML-filen är *katalogen*. Validering vid app-start (fail-fast om malformed) + CI-tid via `validateBadgesYaml`-gradle-task.

```yaml
# shared/content/src/commonMain/resources/se/birdy/content/badges.yaml
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
  # weekly_streak_12, weekly_streak_26, weekly_streak_52

  - id: monthly_streak_3
    category: streak_monthly
    rule: { type: monthly_streak, target: 3 }
  # monthly_streak_6, monthly_streak_12

  - id: season_winter
    category: season
    rule: { type: observed_in_season, season: winter, count: 10 }
  # season_summer (count: 5), season_spring (count: 5), season_autumn (count: 5)

  - id: family_anatidae
    category: family
    rule: { type: observed_in_family, family: anatidae, count: 1 }
  # family_paridae, accipitridae, corvidae, fringillidae, turdidae, sylviidae, picidae

  - id: rare_first
    category: rare
    rule: { type: observed_with_abundance, abundance: sällsynt, count: 1 }
  # rare_5 (count: 5), rare_10 (count: 10)
```

**Notes:**
- `version` bumpas när nya märken läggs till. Driver `BadgeBackfillOnAppStart`.
- Valfri `name_sv` / `name_en` i YAML är dokumentations-kopia (inte runtime-källan — den ligger i strings.xml som `badge_name_<id>` / `badge_desc_<id>`).
- Familje-id:n matchar species.yaml-konvention (lowercase scientific family name).
- Sortering i UI: kategori-ordning (`PROGRESSION → STREAK_WEEKLY → STREAK_MONTHLY → SEASON → FAMILY → RARE`) → target ASC.

### Domain-entiteter

```kotlin
// shared/domain
data class Badge(
    val id: String,
    val category: BadgeCategory,
    val rule: BadgeRule,
)

data class BadgeCatalog(
    val version: Int,
    val badges: List<Badge>,
) {
    private val byId: Map<String, Badge> = badges.associateBy { it.id }
    fun findById(id: String): Badge? = byId[id]
}

enum class BadgeCategory(val order: Int) {
    PROGRESSION(0),
    STREAK_WEEKLY(1),
    STREAK_MONTHLY(2),
    SEASON(3),
    FAMILY(4),
    RARE(5),
}

sealed interface BadgeRule {
    val target: Int
    data class CountUniqueSpecies(override val target: Int) : BadgeRule
    data class WeeklyStreak(override val target: Int) : BadgeRule
    data class MonthlyStreak(override val target: Int) : BadgeRule
    data class ObservedInSeason(val season: Season, override val target: Int) : BadgeRule
    data class ObservedInFamily(val family: String, override val target: Int) : BadgeRule
    data class ObservedWithAbundance(val abundance: Abundance, override val target: Int) : BadgeRule
}

enum class Season { WINTER, SPRING, SUMMER, AUTUMN }
enum class Abundance { ALLMÄN, MINDRE_ALLMÄN, OVANLIG, SÄLLSYNT }   // matchar species.yaml

data class BadgeUnlock(
    val badgeId: String,
    val unlockedAt: Instant,
)

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

### SQLDelight-schema (`badge_unlock`-tabell)

```sql
CREATE TABLE badge_unlock (
    badge_id        TEXT NOT NULL PRIMARY KEY,
    unlocked_at_ms  INTEGER NOT NULL
);

CREATE INDEX badge_unlock_unlocked_at_idx ON badge_unlock(unlocked_at_ms DESC);

selectAll: SELECT * FROM badge_unlock ORDER BY unlocked_at_ms DESC;
upsert:    INSERT OR REPLACE INTO badge_unlock(badge_id, unlocked_at_ms) VALUES (?, ?);
deleteAll: DELETE FROM badge_unlock;
```

**Mappnings-beslut:**
- `Instant` ↔ `Long`-millisekunder (samma som Plan 5a `observation`-tabell).
- Index på `unlocked_at_ms DESC` matchar default-sortering ("Senast upptäckta" first).
- `INSERT OR REPLACE` (upsert) — recalc kan gärna re-emit:a redan-unlockade idår; vi vill inte krasch:a på dupes.

---

## 5. Regelmotor (`RecalculateBadgesUseCase`)

Ren funktion. Inga sidoeffekter. Tar in observationer + species-map + katalog + befintliga unlocks → returnerar nya unlocks att persista.

### Signatur

```kotlin
class RecalculateBadgesUseCase(
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    fun newUnlocks(
        observations: List<Observation>,
        speciesByQid: Map<SpeciesId, Species>,
        catalog: BadgeCatalog,
        existingUnlocks: Set<String>,
    ): List<BadgeUnlock> = catalog.badges
        .filter { it.id !in existingUnlocks }
        .filter { evaluate(it.rule, observations, speciesByQid) }
        .map { BadgeUnlock(it.id, clock.now()) }
}
```

### Rule-evaluatorer (alla pure)

```kotlin
private fun evaluate(
    rule: BadgeRule,
    obs: List<Observation>,
    speciesByQid: Map<SpeciesId, Species>,
): Boolean = when (rule) {
    is CountUniqueSpecies     -> obs.map { it.speciesId }.toSet().size >= rule.target
    is WeeklyStreak           -> longestWeeklyStreak(obs) >= rule.target
    is MonthlyStreak          -> longestMonthlyStreak(obs) >= rule.target
    is ObservedInSeason       -> obs.count { seasonOf(it.capturedAt) == rule.season } >= rule.target
    is ObservedInFamily       -> obs.count { speciesByQid[it.speciesId]?.family == rule.family } >= rule.target
    is ObservedWithAbundance  -> obs.count { speciesByQid[it.speciesId]?.abundance == rule.abundance } >= rule.target
}
```

### `currentValue` (för UI-progress på låsta märken)

Samma evaluator-logik fast returnerar talet istället för boolean — används av `BadgesViewModel` för att rita progress på låsta märken (t.ex. "23 / 25").

```kotlin
internal fun currentValue(
    rule: BadgeRule,
    obs: List<Observation>,
    speciesByQid: Map<SpeciesId, Species>,
): Int = when (rule) {
    is CountUniqueSpecies     -> obs.map { it.speciesId }.toSet().size
    is WeeklyStreak           -> longestWeeklyStreak(obs)
    is MonthlyStreak          -> longestMonthlyStreak(obs)
    is ObservedInSeason       -> obs.count { seasonOf(it.capturedAt) == rule.season }
    is ObservedInFamily       -> obs.count { speciesByQid[it.speciesId]?.family == rule.family }
    is ObservedWithAbundance  -> obs.count { speciesByQid[it.speciesId]?.abundance == rule.abundance }
}.coerceAtMost(rule.target)
```

`evaluate(...)` blir en trivial wrapper: `currentValue(rule, obs, m) >= rule.target`.

### Streak-logik (deterministisk pure)

```kotlin
private fun longestWeeklyStreak(obs: List<Observation>): Int {
    val weeks = obs.map { weekKey(it.capturedAt) }.toSortedSet()
    return longestConsecutive(weeks) { it.next() }
}

private data class WeekKey(val isoYear: Int, val isoWeek: Int) : Comparable<WeekKey> {
    fun next(): WeekKey = /* ISO 8601 next-week-handling, hanterar v53→v01 cross-year */
}

private fun weekKey(instant: Instant): WeekKey {
    val date = instant.toLocalDateTime(zone).date.toJavaLocalDate()
    return WeekKey(
        isoYear = date.get(WeekFields.ISO.weekBasedYear()),
        isoWeek = date.get(WeekFields.ISO.weekOfWeekBasedYear()),
    )
}
```

ISO 8601: måndag-baserad vecka. Hanterar år-skifte (vecka 1 av 2027 kan börja i december 2026). `longestConsecutive` är generic helper (~15 rader) som tar sorted set + next-funktion → returnerar längsta consecutive-kedjan.

### Säsong-mappning

```kotlin
private fun seasonOf(instant: Instant): Season =
    when (instant.toLocalDateTime(zone).month.number) {
        12, 1, 2 -> Season.WINTER
        3, 4, 5  -> Season.SPRING
        6, 7, 8  -> Season.SUMMER
        9, 10, 11 -> Season.AUTUMN
        else     -> error("unreachable")
    }
```

Meteorologisk (SMHI-konvention).

### Recalc-flow vid Save

```kotlin
// SaveObservationUseCase (Plan 5a UTÖKAS):
suspend fun save(input: SaveInput): SaveResult {
    val photoPath = photoStorage.persistJpeg(scaleAndEncode(...))
    val obs = Observation(...)
    try {
        repo.insert(obs)
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        runCatching { photoStorage.delete(photoPath) }
        throw t
    }

    // NYTT i 5b — recalc + persist nya unlocks
    val newUnlocks = runCatching {
        val allObs = repo.observeAll().first()
        val species = speciesRepo.allByQid()
        val existing = badgeRepo.observeUnlocks().first().map { it.badgeId }.toSet()
        val unlocks = recalculate.newUnlocks(allObs, species, catalog, existing)
        badgeRepo.persist(unlocks)
        unlocks
    }.onFailure { if (it is CancellationException) throw it }
        .getOrDefault(emptyList())

    return SaveResult(observationId = obs.id, newUnlocks = newUnlocks)
}

data class SaveResult(
    val observationId: String,
    val newUnlocks: List<BadgeUnlock>,
)
```

Recalc-fail efter lyckad insert → log warning + return `emptyList()`. Användaren ser snackbar "Sparad" — observation finns kvar. Märke-firande hoppar över bara denna gång; återställs vid nästa Save eller app-start-backfill.

### `BadgeBackfillOnAppStart`

```kotlin
class BadgeBackfillOnAppStart(
    private val recalc: RecalculateBadgesUseCase,
    private val obsRepo: ObservationRepository,
    private val speciesRepo: SpeciesRepository,
    private val badgeRepo: BadgeRepository,
    private val catalog: BadgeCatalog,
    private val versionStore: BadgeVersionStore,
) {
    suspend fun runIfNeeded() {
        if (versionStore.lastSeen >= catalog.version) return
        val obs = obsRepo.observeAll().first()
        val species = speciesRepo.allByQid()
        val existing = badgeRepo.observeUnlocks().first().map { it.badgeId }.toSet()
        val backfill = recalc.newUnlocks(obs, species, catalog, existing)
        badgeRepo.persist(backfill)
        versionStore.lastSeen = catalog.version
        // INGA UnlockBottomSheets — backfill är tyst per design
    }
}
```

Anropas från `App.kt` i `LaunchedEffect(Unit)` — kör en gång per app-process.

### Performance

Worst case (heavy user efter 2 år):
- N = 1000 observationer, 25 regler, speciesMap-lookup O(1)
- WeeklyStreak/MonthlyStreak: O(N log N) sortering + O(N) scan
- Övriga regler: O(N)
- Total: ~25 × 1000 = 25 000 enkla operationer per Save → ~1ms på Android-CPU

`observeAll().first()` blockar suspension medan SQLDelight läser → ~5–50ms beroende på DB-storlek. Save-flow har redan I/O-latens (foto-skriv ~50ms) så ytterligare 50ms är osynligt. Total Save: ~150–200ms.

---

## 6. UI

### 6.1 `BadgesScreen`

Ersätter `BadgesStubScreen.kt`. Bottom-nav-fliken `Märken` pekar dit.

**Toolbar:** vanlig (icke-collapsing) `TopAppBar` med titel "Märken", Mossbädd hero-färg `#5C6E48`, text `#F0EAD8`, Crimson Pro.

**State (`BadgesUiState`):**

```kotlin
sealed interface BadgesUiState {
    data object Loading : BadgesUiState
    data class Loaded(
        val speciesProgress: SpeciesProgress,
        val unlockedCount: Int,
        val weeklyStreak: Int?,                // null = dölj pillet (implicit opt-in)
        val monthlyStreak: Int?,               // null = dölj pillet
        val recentlyUnlocked: List<BadgeWithUnlock>,    // upp till 5 senaste, DESC
        val locked: List<BadgeProgress>,                // alla låsta, sorterade
    ) : BadgesUiState
    data class Error(val kind: BadgeErrorKind) : BadgesUiState
}

data class SpeciesProgress(val seen: Int, val total: Int)
data class BadgeWithUnlock(val badge: Badge, val unlockedAt: Instant)

enum class BadgeErrorKind { CatalogParseFailed, LoadFailed }
```

**ViewModel:**

```kotlin
class BadgesViewModel(
    private val obsRepo: ObservationRepository,
    private val badgeRepo: BadgeRepository,
    private val speciesRepo: SpeciesRepository,
    private val catalog: BadgeCatalog,
    private val locale: Locale,
    private val zone: TimeZone,
) : ViewModel() {

    val state: StateFlow<BadgesUiState> = combine(
        obsRepo.observeAll(),
        badgeRepo.observeUnlocks(),
        speciesRepo.observeTotalCount(),
    ) { observations, unlocks, totalSpecies ->
        buildLoaded(observations, unlocks, totalSpecies)
    }
        .catch { emit(BadgesUiState.Error(BadgeErrorKind.LoadFailed)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgesUiState.Loading)
}
```

`weeklyStreak` / `monthlyStreak` beräknas live via `longestWeeklyStreak`-helpern. Implicit-opt-in: `if (streak >= 2) streak else null`.

**Body-layout:**

```
[ TopAppBar "Märken" ]
[ BadgeStatHero ]
   ARTER SEDDA
   87 / 700
   ▰▰▱▱▱▱▱▱▱▱▱▱  12%
   ┌──────┬──────┬──────┐
   │  3   │  4v  │  2m  │       ← stat-piller; streaks dolda om null
   │MÄRKEN│V-STR.│M-STR.│
   └──────┴──────┴──────┘
[ Section: "SENAST UPPTÄCKTA" ]    ← döljs om recentlyUnlocked tom
   ← horisontal LazyRow med BadgeRecentCard (ikon 44×44 + namn + datum)
[ Section: "ATT UPPTÄCKA · 22 KVAR" ]
   ← LazyVerticalGrid, 5 kolumner, silhouett-BadgeCard
```

**`BadgeStatHero` Composable:** Mossbädd-gradient `#5C6E48 → #3F4F30`, padding 18/16/22dp. När streak-piller är null tas de bort — "Märken"-pillet centreras.

**`BadgeCard` Composable** (för silhouetter och upplåsta):
- Storlek: aspect-ratio 1:1, `LazyVerticalGrid`-cell.
- Låst: bg `#D8D0BC` (sand-creme), ikon = kategori-glyf (frågetecken), text-färg `#6B6F5C`.
- Upplåst: bg `#8C5A3C` (koppar), ikon = full badge-ikon, text på `#F0EAD8`.
- Tap-on-locked → `Snackbar` "Hemligt — fortsätt skåda" (3 sek).
- Tap-on-unlocked → `UnlockBottomSheet` (utan glow-animation).

**`BadgeRecentCard` Composable** (horisontal carousel):
- Bredd 92dp, padding 10dp.
- Cirkulär koppar-bg-ikon 44×44dp, namn (Crimson Pro 11sp), datum-relativ ("3 maj").
- Tap → samma `UnlockBottomSheet`.

**Empty state (0 unlocks):**
- Hero: "0 / 700" + Märken-pille "0" (streak-piller dolda via implicit opt-in).
- "SENAST UPPTÄCKTA"-sektion döljs helt.
- "ATT UPPTÄCKA · 25 KVAR" + silhouett-grid.
- Inget extra hint-text — gridd:n med 25 frågetecken talar för sig självt.

### 6.2 `UnlockBottomSheet`

Komposable som visas från:
1. `ClassificationResultScreen` (efter Save, om nya unlocks finns) — med glow-animation.
2. `BadgesScreen` (vid tap på upplåst märke eller "Senast upptäckta"-card) — utan animation.

```kotlin
@Composable
fun UnlockBottomSheet(
    badge: Badge,
    unlockedAt: Instant,
    isCelebration: Boolean,
    locale: Locale,
    onDismiss: () -> Unit,
)
```

**Layout (ModalBottomSheet, ~50% skärm):**

```
   ▔▔
   ┌──────────────────────────────┐
   │         ┌──────┐             │
   │         │  ●   │             │   ← 96×96 ikon, koppar-bg, glow när celebration
   │         └──────┘             │
   │      MÄRKE UPPLÅST           │   ← UPPERCASE-label, #6B6F5C
   │       Skådare                │   ← Crimson Pro 26sp, #2A3525
   │  25 olika arter sedda — fält-│   ← description (16sp)
   │  biologi-brons.              │
   │       Upplåst 3 maj 2026     │   ← liten datum-text
   │       [   Härligt   ]        │   ← Button koppar #8C5A3C
   └──────────────────────────────┘
```

**Glow-animation** (när `isCelebration = true`):
- `infiniteTransition.animateFloat(0.6f, 1.0f, 0.6f)`, 1.5s loop, easing = `FastOutSlowInEasing`.
- `Modifier.drawBehind { drawCircle(color = AccentCopper.copy(alpha = glowAlpha), radius = ...) }`.
- Animering stannar efter ~3 sekunder, sen statisk. Inga partiklar, inga konfetti.

**Sequence-handling** (`UnlockQueue`):

```kotlin
class UnlockQueue {
    private val queue = MutableStateFlow<List<BadgeUnlock>>(emptyList())
    val current: StateFlow<BadgeUnlock?> = queue.map { it.firstOrNull() }...
    fun enqueue(unlocks: List<BadgeUnlock>) { queue.update { it + unlocks } }
    fun pop() { queue.update { it.drop(1) } }
}
```

`ClassificationResultScreen` håller en `UnlockQueue`-instans i `viewModel.scope`. När bottom-sheet `onDismiss` triggar → `pop()` → next visas. Användaren kan inte avbryta queue:n — om de backar ur ResultScreen försvinner kvarvarande queue (ej persistat). Save-CTA disabled så länge queue.size > 0.

### 6.3 Navigation

Inga nya routes. Bottom-nav-fliken `Märken` pekar på `BadgesScreen`. `UnlockBottomSheet` är `ModalBottomSheet` — ingen route, bara state.

### 6.4 Strängar (sv primary + en mirror)

```
badges_title                       "Märken"
badges_label_species_seen          "Arter sedda"
badges_label_badges                "Märken"
badges_label_weekly_streak         "V-streak"
badges_label_monthly_streak        "M-streak"
badges_section_recently_unlocked   "Senast upptäckta"
badges_section_to_discover         "Att upptäcka · %d kvar"
badges_locked_tooltip              "Hemligt — fortsätt skåda"
badges_progress_format             "%1$d / %2$d"
badges_load_error                  "Kunde inte ladda märken — försök igen"
badges_load_error_retry            "Försök igen"
badges_catalog_error               "Märken kunde inte laddas — installera om appen"

unlock_label                       "MÄRKE UPPLÅST"
unlock_button_dismiss              "Härligt"
unlock_unlocked_at                 "Upplåst %s"

badge_name_novice                  "Nybörjare"
badge_desc_novice                  "Du har sett dina första 5 fågelarter."
# ... 24 till
```

`BadgeErrorKind`-enum: `LoadFailed | CatalogParseFailed`. UI mapar enum → `stringResource`. Locked Plan 4a/5a-mönster (ingen Composable-context i VM:n).

---

## 7. Dataflöden

### 7.1 Save-flow med firande

```
[ClassificationResultScreen: tap "Spara i dagboken"]
       │
       ▼
[ClassificationResultViewModel.saveToDiary()]
       │
       ▼
[SaveObservationUseCase.save(input: SaveInput)]   // SaveInput = Plan 5a-typen, oförändrad
       │
       ├─ photoStorage.persistJpeg(scaled)
       ├─ observationRepo.insert(obs)
       │
       ├─ allObs = observationRepo.observeAll().first()
       ├─ speciesByQid = speciesRepo.allByQid()
       ├─ existing = badgeRepo.observeUnlocks().first().map { it.badgeId }.toSet()
       ├─ newUnlocks = recalculate.newUnlocks(allObs, speciesByQid, catalog, existing)
       ├─ badgeRepo.persist(newUnlocks)
       │
       ▼ returnerar SaveResult(observationId, newUnlocks)
       │
       ▼
[VM emitterar:
   - state = Saved (snackbar "Sparad i dagboken" → 4 sek)
   - om newUnlocks.isNotEmpty(): unlockQueue.enqueue(newUnlocks)
]
       │
       ▼
[ClassificationResultScreen]
   ┌─ Sticky Sparad ✓-state på Save-CTA (disabled)
   ├─ Snackbar "Sparad i dagboken" (4 sek)
   ▼
[UnlockBottomSheet (om queue.current != null)]
   ← celebration glow-animation
   ← swipe-down ELLER "Härligt"-knapp → onDismiss → queue.pop()
   ← om queue.next != null → ny bottom-sheet visas direkt
```

Snackbar visas först (Save-flödet bekräftas innan firande). Bottom-sheet pop:ar ovanpå snackbar:n när queue.current är non-null.

### 7.2 App-start-backfill

```
[App-Compose-rot LaunchedEffect(Unit)]
       │
       ▼
[BadgeBackfillOnAppStart.runIfNeeded()]
       │
       ├─ if versionStore.lastSeen >= catalog.version → return
       │
       ├─ allObs = observationRepo.observeAll().first()
       ├─ speciesByQid = speciesRepo.allByQid()
       ├─ existing = badgeRepo.observeUnlocks().first().map { it.badgeId }.toSet()
       ├─ retroactive = recalculate.newUnlocks(allObs, speciesByQid, catalog, existing)
       ├─ badgeRepo.persist(retroactive)
       ├─ versionStore.lastSeen = catalog.version
       │
       ▼ TYST — inga UnlockBottomSheets, ingen snackbar
   (retroactive märken visas i Märken-flikens grid med korrekt unlocked_at = nu)
```

`BadgeVersionStore` är SharedPreferences-wrapper (`expect class` i common, `actual` med vanlig `SharedPreferences` på Android — ingen `EncryptedSharedPreferences` eftersom värdet är ett ofarligt katalog-version-`Int`). Lagrar bara `Int`.

### 7.3 BadgesScreen-state-flow

```
[BadgesViewModel.state] = combine(
    obsRepo.observeAll(),
    badgeRepo.observeUnlocks(),
    speciesRepo.observeTotalCount(),
) { obs, unlocks, totalSpecies ->
    val seenSpecies = obs.map { it.speciesId }.toSet().size

    val recentlyUnlocked = unlocks
        .sortedByDescending { it.unlockedAt }
        .take(5)
        .mapNotNull { catalog.findById(it.badgeId)?.let { b -> BadgeWithUnlock(b, it.unlockedAt) } }

    val unlockedIds = unlocks.map { it.badgeId }.toSet()
    val locked = catalog.badges
        .filter { it.id !in unlockedIds }
        .map { BadgeProgress(it, currentValue(it.rule, obs, speciesByQid), it.rule.target, null) }
        .sortedWith(compareBy({ it.badge.category.order }, { it.badge.rule.target }))

    val weeklyStreak = longestWeeklyStreak(obs).takeIf { it >= 2 }
    val monthlyStreak = longestMonthlyStreak(obs).takeIf { it >= 2 }

    BadgesUiState.Loaded(...)
}
```

`speciesRepo.observeTotalCount()` är ny tunn Flow på existing `SpeciesRepository`: `SELECT COUNT(*) FROM species`.

### 7.4 Reaktivitet

SQLDelight Flows emit:ar vid varje DB-write. Save skriver till två tabeller (observation + badge_unlock) → båda Flows emit:ar. `combine()` re-evaluerar. Om Märken-fliken är öppen *när* unlock sker → grid uppdateras live (silhouett byts mot upplåst-kort).

---

## 8. Felhantering

### 8.1 Save-flow med recalc

| Fel | Hantering |
|---|---|
| Plan 5a-fel (foto-encode/storage/db) | Oförändrat — same Plan 5a behavior. Inga unlocks fired. |
| Recalc-fel efter lyckad insert | `runCatching { recalc + persist }.onFailure { logWarning + return SaveResult(id, emptyList()) }`. Användaren ser snackbar "Sparad". Märke-firande hoppar över denna gång; återställs vid nästa Save eller app-start-backfill. |
| `badgeRepo.persist` fail | Samma som ovan: log + return `emptyList()`. |
| `CancellationException` | Rethrow:as alltid (locked Plan 4a-mönster). |

### 8.2 BadgesScreen-load

| Fel | UI-tillstånd | Snackbar/text |
|---|---|---|
| Flow throws | `Error(LoadFailed)` | "Kunde inte ladda märken — försök igen" + Retry |
| `speciesRepo.observeTotalCount()` returnerar 0 | "0 / 0" + grå progress-bar | (Edge case — APK packar content-DB) |
| `catalog.findById(unlock.badgeId)` returnerar null (märke borttaget från katalog men finns i DB) | Filtrera bort silent + log | (Ej user-synligt) |

### 8.3 Catalog-parse-fel

| Fel | Hantering |
|---|---|
| YAML-syntax-fel | Hård krasch i debug + Crashlytics i release. Build-time `validateBadgesYaml`-task fångar i CI. |
| Unknown rule-type | Build-time CI-task failar. Runtime-fallback: skip okänd regel + log. |
| `name_sv` saknas i strings.xml men finns i YAML | Build-time `validateBadgeStrings`-task cross-checkar `badges.yaml` ID:n mot `badge_name_<id>` keys. Failar build. Runtime-fallback: visa raw `id`. |

### 8.4 Streak-edge-cases

| Fall | Hantering |
|---|---|
| Timezone-byte | `weekKey`/`monthKey` använder `TimeZone.currentSystemDefault()` vid eval-tid. Streak kan bli annorlunda efter byte — accepterad pragmatisk lösning. |
| Klocka manuellt bakåt | Inga streak-dekrementer (regler kollar längsta historiska kedja). Märken kan bli "framtida" om klocka justeras tillbaka. |
| DST-skifte | `Instant.toLocalDateTime(zone)` hanterar automatiskt. 23/25h-dygn räknas som vanlig vecka. |

### 8.5 Bottom-sheet-edge-cases

| Fall | Hantering |
|---|---|
| Användaren backar mid-queue | Queue töms (ej persistat). Märkena finns i grid — tap → bottom-sheet utan animation. |
| Inkommande samtal mid-bottom-sheet | Compose `ModalBottomSheet` lifecycle-aware. State bevaras. |
| 3+ unlocks samtidigt | Queue visar en åt gången. Inga summary-bottom-sheet i v1. |
| Ny Save mid-queue | Save-CTA disabled så länge queue.size > 0 (hint "Visa märken först"). |

### 8.6 Vad vi *inte* hanterar i v1

- Race conditions vid concurrent Save (single user, single device).
- Migration när `badges.yaml`-version *minskas* (ej möjligt i prod; defensive: behandla som lika).
- Migration när märken *tas bort* — orphan-row stays i DB. Filtreras bort i UI. Plan 6 cleanup-task.

---

## 9. Test-strategi

### 9.1 Unit-tester (JVM, snabba)

**`RecalculateBadgesUseCaseTest`** (`shared/domain/jvmTest`) — kärntestet, exhaustive:
- Tabel-driven över alla 6 rule-typer.
- Edge cases: cross-year-boundary streak (v53/2026 + v01/2027), DST-skifte, multiple obs samma vecka räknas som en, duplicates i count_unique, null-abundance i species (corrupt content) räknas inte.
- `Clock.fixed`-injection för deterministisk `unlocked_at`. `TimeZone.UTC` i tests.

**`BadgeCatalogLoaderTest`** (`shared/content/jvmTest`):
- Parse av valid YAML.
- Invalid YAML kastar `CatalogParseException`.
- Unknown rule-type kastar.
- Saknade required fields kastar.

**`BadgeRepositoryImplTest`** (`shared/data/jvmTest`):
- In-memory SQLDelight + Turbine.
- persist + observeUnlocks emits, persist är idempotent (upsert), deleteAll, sortering DESC.

**`BadgesViewModelTest`** (`composeApp/test`):
- `FakeObservationRepository.withDefaults()` (Plan 5a fixture) + ny `FakeBadgeRepository`.
- Loaded-state med korrekt `recentlyUnlocked.take(5)`, korrekt locked-sortering.
- Implicit-opt-in: `weeklyStreak = null` när < 2 veckor; `4` när ≥ 2.
- Empty-state: `unlockedCount = 0`, `recentlyUnlocked = emptyList()`.
- Error-state vid Flow-throw.
- `MainDispatcherRule` + `runTest`.

**`SaveObservationUseCaseTest`** (`composeApp/test`) — utökas från Plan 5a:
- Existerande Plan 5a-tester behålls.
- Lyckat Save utan rule-match → `SaveResult(id, emptyList())`.
- Lyckat Save som triggar 1 unlock → returneras + persistas.
- Lyckat Save som triggar 3 unlocks → alla i deterministisk ordning.
- Recalc-fail efter insert → `SaveResult(id, emptyList())`, observation finns kvar.

**`BadgeBackfillOnAppStartTest`** (`composeApp/test`):
- versionStore.lastSeen >= catalog.version → no-op.
- versionStore.lastSeen < catalog.version → recalc + persist + version uppdateras.
- Tom observations-lista → no-op.
- Ingen UnlockEvent emit:as.

**`UnlockQueueTest`** (`composeApp/test`):
- enqueue + observe `current` emits korrekt order.
- pop när tom är no-op.

**`StreakHelpersTest`** (`shared/domain/jvmTest`) — exhaustive: `weekKey`/`monthKey`/`longestConsecutive`.

### 9.2 Instrumented / device-verification (Galaxy S23 Ultra)

1. **First-Save-unlocks**: Tom DB → scanna 5 olika arter → spara den 5:e → bottom-sheet "Nybörjare" + "Familje-X" pop:ar i sekvens.
2. **Implicit opt-in streak**: 1 obs i v17 → ingen streak-pille. Lägg obs i v18 → "2v"-pille visas.
3. **Locked-tap**: tap på silhouett → snackbar "Hemligt — fortsätt skåda".
4. **Unlocked-tap**: tap på upplåst → bottom-sheet utan glow.
5. **Senast upptäckta-carousel**: Lås upp 5+ → carousel scrollbar.
6. **Re-launch persistence**: Lås upp märke → kill-app → re-launch → märket kvar.
7. **Backfill efter version-bump**: Manuellt bump:a `badges.yaml` version + nytt märke → re-launch → tyst unlock.
8. **Save-CTA disabled mid-queue**: Spara obs som triggar 2+ unlocks → CTA disabled tills alla bottom-sheets stängda.
9. **Glow-animation**: Subtilt koppar-pulser 1.5s loop. Stannar efter ~3 sek.
10. **Cross-screen reactivity**: Märken-flik öppen → triggera ny obs från Scan-flik → grid uppdateras live.

### 9.3 Screenshots

```
2026-05-XX-05b-badges-empty.png            Märken-flik, 0 unlocks
2026-05-XX-05b-badges-loaded.png           Hero + 3 senaste + grid
2026-05-XX-05b-badges-streak-grown.png     Streak-piller visad efter 2v-streak
2026-05-XX-05b-unlock-bottomsheet.png      Glow-animation, "Skådare" unlock
2026-05-XX-05b-locked-detail.png           Tap-on-silhouette → snackbar tooltip
2026-05-XX-05b-unlocked-detail.png         Tap-on-upplåst → bottom-sheet utan glow
2026-05-XX-05b-save-with-unlock.png        ResultScreen: snackbar + bottom-sheet
```

### 9.4 Ej i scope för 5b-tester

- iOS-tester (data-modulen kompileras för iOS men ingen aktiv testpath).
- Performance >10 000 obs (Plan 6).
- Animation-pixel-perfect-tests (Compose-animation testas via `composeRule.mainClock` om kritiskt).
- Build-time YAML-validering är gradle-task; testas via integration-test mot purposely-broken YAML.

---

## 10. Återanvända Plan 4a + 5a-mönster

- **`Error.Kind` enum** (inte `String`) i alla UiState — UI mapar enum → `stringResource`.
- **`runCatching { ... }.onFailure { if (it is CancellationException) throw it }`** för structured-concurrency.
- **`FakeRepository.withDefaults()`-fixturer** — ny `FakeBadgeRepository.withDefaults()` skapar 3 sample-unlocks.
- **i18n-disciplin:** sv primary, en mirror — ALLA keys i båda.
- **`stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ...)`** för UI-state-flows.
- **`combine()`-baserad VM-state** — Plan 5a `DiaryViewModel`-mönster.
- **`expect/actual`-pattern** för `BadgeVersionStore` (SharedPreferences på Android, NSUserDefaults i iOS-skelett).
- **build-deps-disciplin:** varje ny shared-modul-referens från `:composeApp` måste få egen `implementation()` i `:androidApp/build.gradle.kts` (Plan 5a Task 12-lärdom).

---

## 11. Out of scope för Plan 5b

- **Settings-skärm** (toggle för streak-visibility, manual-purge, theme) — Plan 6.
- **Export av märken/streaks** (CSV/JSON) — Plan 6.
- **Push-notiser vid streak-risk** ("Din streak bryts om 2 dagar") — v1.5.
- **Cloud-sync av märken** — v1.5 tillsammans med Diary-sync.
- **iOS-implementation** — v1.5+. Schemat + repo finns i KMP-common; iOS-actual för `BadgeVersionStore` är ren NSUserDefaults-bindning.
- **Filter / sortering på Märken-fliken** — v1.5/Plan 6.
- **Achievement-detail-route** — bottom-sheet räcker för v1.
- **Summary-bottom-sheet** ("Du fick 3 märken") — v1.5 om queue-flow visar sig vara annoying.
- **Manual badge-edit / unlock-undo** — användarens unlocks är read-only utom via "Återställ appen"-funktion (Plan 6).
- **Region-baserade märken** (`observed_in_region`) — kräver location-data → v1.5.

---

## 12. Risker och osäkerheter

- **YAML-DSL vs strings.xml drift**: om utvecklaren bumpar `badges.yaml` utan att uppdatera strings.xml → runtime-fallback visar raw `id`. Mitigation: build-time `validateBadgeStrings`-gradle-task failar build. Risk: noll-utveckling utan build = ingen feedback. Acceptabel för solo-dev.
- **Performance i recalc vid heavy user**: 1000+ obs × 25 regler är ~1ms men `observeAll().first()` kan blockera ~50ms. Inom acceptabel UI-respons för Save-flow. Plan 6 kan optimera om dataset:et växer.
- **Backfill-version-mismatch på reinstall**: ny installation → versionStore.lastSeen = 0 → catalog.version = 1 → backfill kör. För tom DB är detta no-op. Acceptabelt.
- **Bottom-sheet-queue tappas vid back-navigation**: medvetet val (no persistance). Edge case: användaren får 3 unlocks, ser 1, backar → 2 missade firanden. De ser märkena i grid:n men missar avslöjandet. Mitigation: små unlocks (familje-märken) firas inte särskilt firande ändå; rare unlocks (sällsynta märken) får sin moment om de är först.
- **Streak-anomalier vid timezone-byte**: accepterad. v1.5 om problematiskt.
- **Compose `ModalBottomSheet` på äldre Android-versioner** (pre-12): testar på SM-S918B (Android 14). Spec antar Material3 v1.2+ med stable bottom-sheet API.
- **Familje-listan (8) speglar inte ev. biological taxonomi-uppdateringar** — låst för v1; Plan 6 om relevant.
- **`speciesRepo.observeTotalCount()`** är en ny query — kan kräva refactor av befintlig SpeciesRepository-interface. Task 1 i implementation-plan undersöker.
- **DI-system osäkert**: vet inte om Plan 5a använder Koin eller manuell DI. Task 1 verifierar.

---

## 13. Definition of Done för Plan 5b

- [ ] `shared/content` har `badges.yaml` (25 märken, version=1) + `BadgeRule` + `BadgeCatalog` + `BadgeCatalogLoader`.
- [ ] `shared/domain` har `Badge`, `BadgeUnlock`, `BadgeProgress`, `BadgeRepository`-interface, `RecalculateBadgesUseCase` (pure).
- [ ] `shared/data` har `badge_unlock`-tabell + `BadgeRepositoryImpl`.
- [ ] `composeApp` har `BadgesScreen` + `BadgesViewModel` + `BadgeStatHero`, `BadgeCard`, `BadgeRecentCard`, `UnlockBottomSheet`, `UnlockQueue`.
- [ ] `composeApp` har `BadgeBackfillOnAppStart` + `expect/actual BadgeVersionStore`.
- [ ] `SaveObservationUseCase` utökad: returnerar `SaveResult(observationId, newUnlocks)`.
- [ ] `ClassificationResultScreen` visar `UnlockBottomSheet` i sekvens efter Save-snackbar; Save-CTA disabled mid-queue.
- [ ] `BadgesStubScreen` raderad; bottom-nav-fliken pekar på `BadgesScreen`.
- [ ] Alla strängar i sv + en. `badge_name_<id>` + `badge_desc_<id>`-keys för alla 25 märken.
- [ ] Build-time `validateBadgesYaml` + `validateBadgeStrings` gradle-tasks integrerade i CI.
- [ ] Repository-tester (in-memory SQLDelight) gröna.
- [ ] `RecalculateBadgesUseCaseTest` exhaustive över alla 6 rule-typer + edge cases.
- [ ] Use-case-tester (recalc-paths) gröna.
- [ ] VM-tester (Badges, UnlockQueue, BadgeBackfillOnAppStart) gröna.
- [ ] `StreakHelpersTest` gröna.
- [ ] Manuell device-verifiering på SM-S918B genomförd. 10 flöden från §9.2.
- [ ] 7 screenshots committade till `docs/superpowers/screenshots/`.
- [ ] CLAUDE.md uppdaterad: status-rad, plan-of-plans-tabell, "Avslutade planer (referens)"-entry för Plan 5b med återanvändbara mönster.
- [ ] Auto-memory `project_plan_5b_status.md` skriven.
- [ ] `./gradlew :shared:domain:jvmTest :shared:data:jvmTest :shared:content:jvmTest :composeApp:testDebugUnitTest ktlintCheck detekt :composeApp:assembleDebug :androidApp:installDebug` allt grönt.
- [ ] Tag `v0.5.0b-gamification` skapad och pushad. CLAUDE.md status-rad pekar på den.

---

## 14. Brainstormingens tidslinje

- **2026-05-06 (denna spec):** Plan 5b brainstorm direkt efter Plan 5a tagged som `v0.5.0a-diary`. 8 clarifying questions + 3-approach-arkitektur-jämförelse + visuell companion-mockup för BadgesScreen-layout (3 alternativ: hero-tungt flat-grid, sektionerad-kategori, senast-upptäckta-carousel — hybrid valt: hero från A + body från C).

---

## 15. Nästa steg

- Användargranskning av denna spec.
- Justeringar baserat på feedback.
- `superpowers:writing-plans` invokas för att skapa implementation-plan med 10–14 task-uppdelningar (typiskt: setup + DI-verify → schema/repo → catalog/loader → rule-engine → backfill → BadgesScreen + hero + cards → UnlockBottomSheet + queue → Save-integration → tests → polish + screenshots → tag).
- Plan 5b exekveras via `superpowers:subagent-driven-development` (Sonnet 4.6 implementer + Opus 4.7 reviewer/controller — samma modell som Plan 3 + 4a + 5a).
