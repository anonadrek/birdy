# Plan 6b3 — Premium content (PDF-export · Säsongs-statistik · 10 fält-märken)

> **Skapad:** 2026-05-21 · **Mål-tag:** `v0.9.0c-premium-content` → `v1.0.0` · **Föregångare:** `v0.9.0b-audio` (Plan 6b2)
>
> Spec efter `superpowers:brainstorming` med soloutvecklaren. Skall bli implementations-plan via `superpowers:writing-plans` därefter.

---

## 1. Sammanfattning

Plan 6b3 levererar de tre Premium-features som Plan 7e teaser-promised:

1. **PDF-export** av användarens fältdagbok i Field Journal-stil (paper-bg, DM Serif Italic, Caveat-marginalia, embedded fonts)
2. **Säsongs-statistik** på en egen `SeasonStatsScreen` med fyra widgets (custom Canvas charts)
3. **10 premium-badges ("fält-märken")** med riktiga regler, evaluator-integration, och progressspårning

Efter 6b3 ger `PremiumState.Active` konkret värde över hela appen, inte bara mock-knapp och låsta widgets. Tag `v0.9.0c-premium-content` följt av `v1.0.0` (audit-cleanup + versionName-bump mellan dem).

**Budget (från `2026-05-15-play-store-launch/00-launch-roadmap.md`):** 5–7d.

**Avgörande val (besluttade under brainstorm 2026-05-21):**

| # | Beslut | Val |
|---|---|---|
| 1 | PDF ambitionsnivå | Field Journal-pdf med embedded fonts |
| 2 | PDF innehåll | Komplett "Fältdagbok"-bok (Cover → ToC → Lifelist → Diary/månad → Säsongsöversikt) |
| 3 | PDF distribution | Share-sheet via `ACTION_SEND` + FileProvider |
| 4 | Stats scope | Egen `SeasonStatsScreen` med 4 widgets |
| 5 | Stats teknik | Custom Canvas (+0 deps) |
| 6 | Badge rules | Föreslagen mapping (4 nya `BadgeRule` + 1 specialfall) |
| 7 | Badge design | Emoji + befintliga `StampSeal` (ingen custom illustration i v1) |

---

## 2. Status — vad finns redan

**Scaffoldat i Plan 7e (kommer återanvändas):**

| Komponent | Var | Roll i 6b3 |
|---|---|---|
| `PremiumTeaserCard` | `composeApp/.../ui/components/PremiumTeaserCard.kt` | Archive-instans byter `onClick` till PDF-export när `premiumActive=true` |
| `LockedStatsPreview` | `composeApp/.../ui/components/LockedStatsPreview.kt` | Conditional-bytt mot `LiveStatsPreview` i Lifelist när active |
| `PremiumBadgesRow` | `composeApp/.../ui/badges/BadgesScreen.kt` (`private` i samma fil) | Ersätts med `PremiumBadgesSection` som har två states (locked/active) |
| `premium_badges.yaml` | `composeApp/src/commonMain/composeResources/files/` | Schema-utvidgas med `rule` + `descriptionSv/En` |
| `PremiumState` + `effectivePremiumActive` | `AppGraph` (Plan 7e) | Single source of truth för gating |
| `BadgeCatalog` + `BadgeEvaluator` + `BadgeBackfillOnAppStart` | `shared/domain/badge/` + `composeApp/.../bootstrap/` | Utökas med premium-stöd |
| `JournalLoading`, `JournalDialog`, `JournalScaffold`, `EmptyState` | `composeApp/.../ui/components/` (Plan 6a T8) | Återanvänds i nya skärmar/flows |
| `SettingsLauncher` (`expect/actual`) | Plan 6a T13 | Mönster för ny `JournalExportLauncher` |
| `ObservationRepository` (Flow-baserade queries) | `shared/data/` | Källa för PDF-data + stats-aggregat |
| Bundlade fonts DM Serif Display Italic + Caveat-Regular | `composeApp/src/commonMain/composeResources/font/` | Återanvänds i PDF utan dubblettbundling |

**Saknas helt:**
- Ingen PDF-pipeline (ingen modul, ingen `expect/actual`, ingen layout)
- Ingen Canvas-chart-implementation (inga charts finns i appen ännu)
- Premium-badges har bara id+icon+name — ingen `rule:`, ingen `description:`, ingen evaluator-mappning, ingen unlock-mekanism

---

## 3. Modul-struktur

Tre nya områden i koden:

```
shared/pdf/                                # NY KMP-modul
├─ build.gradle.kts                        # KMP setup, expect deklareras commonMain
├─ src/commonMain/kotlin/se/birdy/pdf/
│  ├─ JournalSpec.kt                       # data classes för layout-input
│  ├─ JournalPdfRenderer.kt                # expect class
│  └─ JournalSpecBuilder.kt                # bygger spec från repos (testbar)
├─ src/androidMain/kotlin/se/birdy/pdf/
│  └─ JournalPdfRenderer.android.kt        # actual via PdfDocument + Canvas
└─ src/iosMain/kotlin/se/birdy/pdf/
   └─ JournalPdfRenderer.ios.kt            # stub: NotImplementedError

composeApp/src/commonMain/kotlin/se/birdy/app/
├─ ui/stats/
│  ├─ SeasonStatsScreen.kt                 # NY skärm
│  ├─ SeasonStatsViewModel.kt              # aggregat-transform
│  ├─ SeasonStatsState.kt                  # sealed UiState
│  └─ canvas/
│     ├─ BarChartCanvas.kt                 # NY custom Canvas chart
│     ├─ LineChartCanvas.kt                # NY
│     ├─ DonutChartCanvas.kt               # NY
│     └─ TopSpeciesWidget.kt               # NY (vanlig Column, ej canvas)
├─ premium/
│  ├─ JournalExportLauncher.kt             # expect (share-sheet)
│  └─ androidMain: JournalExportLauncher.android.kt + Setup-singleton
└─ navigation/AppRoute.kt                  # ny: SeasonStats

shared/domain/src/commonMain/kotlin/se/birdy/domain/badge/
├─ BadgeRule.kt                            # +4 nya varianter
├─ BadgeEvaluator.kt                       # +4 nya when-grenar
└─ BadgeRepository.kt                      # +unlockManualBadge()
```

**KMP-rationale:** PDF-rendering har distinkt platform-bindning (Android `PdfDocument`, iOS `CGContext` långt fram). Egen modul håller `:composeApp` lean och tillåter iOS-stub. Stats-canvas däremot är ren Compose (inga platform-bindningar) → bor i `composeApp`.

---

## 4. Feature 1 — PDF-export ("Field Journal"-pdf)

### 4.1 KMP-kontrakt

```kotlin
// shared/pdf/src/commonMain/kotlin/se/birdy/pdf/JournalPdfRenderer.kt
expect class JournalPdfRenderer {
    /** Renders to cache file, returns absolute path on success. */
    suspend fun render(spec: JournalSpec): Result<String>
}

data class JournalSpec(
    val ownerName: String?,                            // optional, från DataStore-settings
    val generatedAt: LocalDate,
    val dateRangeStart: LocalDate?,                    // null → "från första obs"
    val dateRangeEnd: LocalDate?,                      // null → till generatedAt
    val lifelist: List<LifelistEntry>,
    val diaryByMonth: Map<YearMonth, List<JournalObservation>>,  // sorted desc
    val seasonSummary: SeasonSummary,
    val locale: BirdyLocale,                           // SV / EN
)

data class LifelistEntry(
    val stampNumber: Int,
    val speciesNameLocal: String,
    val speciesNameScientific: String,
    val firstObservedAt: LocalDate,
    val totalObservations: Int,
)

data class JournalObservation(
    val capturedAt: LocalDateTime,
    val speciesNameLocal: String,
    val photoPath: String?,                            // null om fil saknas
    val locationLabel: String?,
    val note: String,
)

data class SeasonSummary(
    val spring: Int, val summer: Int, val autumn: Int, val winter: Int,
    val topSpecies: List<Pair<String, Int>>,           // top 5
)
```

`JournalSpecBuilder.build(repos, locale): JournalSpec` är pure-data och testas i `commonMain`-tests.

### 4.2 Android-implementation

- **Format:** A4 (595×842pt @72dpi), portrait, marginal 25mm = ~71pt
- **Font-loading:** `Typeface.createFromAsset(ctx.assets, "fonts/DMSerifDisplay-Italic.ttf")` + `Caveat-Regular.ttf`. Båda redan i `composeResources/font/` (delade med appen — ingen dubblett-bundling, ingen APK-bloat).
- **Embedding i pdf:** `PdfDocument`s `Paint`-instans bär `Typeface`. Embedded fonts garanteras genom Android-systemets PDF-printer.
- **Bg-färg:** `Canvas.drawColor(PaperBg #EFE7D6)` per sida. **Ingen** texture (overkill för pdf-skala; solid färg + ornament-rule räcker för Field Journal-feel).
- **Bilder:** `BitmapFactory.Options.inSampleSize` så bilder maxar 800px bredd före `canvas.drawBitmap(bm, src, dst, paint)`. Mål: 50-obs-pdf ≤ 8 MB.
- **Paginering:** Greedy. För varje innehållsblock — beräkna höjd, om `y + height > pageBottom` → `pdfDoc.finishPage(); page = pdfDoc.startPage()`; om enskilt block > sidhöjd (extremt lång note) → soft-clip + "..." (edge-case, log warning).
- **Text wrap:** `StaticLayout.Builder(...).setWidth(maxWidth).build()` → mät + rita på Canvas.

### 4.3 Sidstruktur

| Sida | Innehåll | Komponenter |
|---|---|---|
| 1 | **Cover** | "Fältdagbok" / "Field Journal" DM Serif Italic 48pt (centered), ornament-rule ❦, Caveat-italic "Sammanställd 2026-05-21" / "Compiled 2026-05-21", ägar-namn DM Serif Italic 20pt om satt, footer Caveat-italic "Birdy · birdy.app" |
| 2 | **Innehållsförteckning** | DM Serif Italic-rubrik "Innehåll" / "Contents", sektion + sidnummer-par, Caveat-italic |
| 3–6 | **Lifelist** | Tvåspalts-lista: stamp # (Caveat copper) · art-namn (DM Serif Italic 14pt) · första obs (mono 10pt) · total-count (mono 10pt) |
| 7+ | **Diary, per månad** | Månads-header DM Serif Italic 28pt ("Maj 2026" / "May 2026") + ornament-rule. Per obs: foto vänster 80×80pt + meta-block höger (datum Caveat 14pt, plats sans 10pt, note serif 11pt). 2 obs per sida. |
| Sist | **Säsongsöversikt** | 4 siffer-block (vår/sommar/höst/vinter), DM Serif Italic-tal 36pt + Caveat-label, top-5-arter-lista nedanför |

### 4.4 Share-flow

- **Filnamn:** `birdy-faltdagbok-2026-05-21.pdf` (SV) / `birdy-field-journal-2026-05-21.pdf` (EN). Locale-stable datum.
- **Skrivlöp:** `context.cacheDir/exports/<filename>` — OS rensar automatiskt, ingen `WRITE_EXTERNAL_STORAGE` krävs.
- **Provider:** `<provider android:name="androidx.core.content.FileProvider" android:authorities="se.birdy.android.fileprovider" android:exported="false" android:grantUriPermissions="true">` + `res/xml/file_paths.xml` med `<cache-path name="exports" path="exports/" />`.
- **Intent:**
  ```kotlin
  val uri = FileProvider.getUriForFile(ctx, "se.birdy.android.fileprovider", file)
  val intent = Intent(Intent.ACTION_SEND).apply {
      type = "application/pdf"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  ctx.startActivity(Intent.createChooser(intent, null))
  ```
- **Launcher-mönster:** Ny `JournalExportLauncher` `expect class` med `share(filePath: String)`-metod. Android-actual använder `SettingsLauncherSetup`-stil singleton init:ad från `MainActivity` (samma mönster som Plan 6a T13).

### 4.5 Performance + error

- Render i `Dispatchers.IO` (synchron `PdfDocument`-API). UI-tråd visar `JournalLoading` overlay (befintlig komponent från Plan 6a T8).
- **Cancellation:** Scope = `viewModelScope`; om användaren navigerar bort → renderingen cancellas, fil-stub städas i `finally`.
- **Foto saknas:** Rita `[Foto saknas]`-frame (boxed Caveat-italic), log warning. Fortsätter med resten.
- **Hela render-fel:** `Result.failure(e)` propageras → ViewModel emittar `Snackbar`-state med `pdf_error_generic`-sträng.
- **Estimat:** 50 obs-dagbok → ~2–4s render, ~3–8 MB pdf.

### 4.6 Trigger-point

`ArchiveScreen.PremiumTeaserCard` får ny `onClick`:
```kotlin
onClick = {
    if (effectivePremiumActive) onExportClick() else nav.navigate(AppRoute.Premium)
}
```
`onExportClick` är en lambda från `AppGraph` (samma `MainActivity.runBlocking { ... }`-bootstrap som Plan 6a):
1. Hämta `JournalSpec` via `JournalSpecBuilder` (suspend)
2. Kalla `JournalPdfRenderer.render(spec)` (suspend)
3. På success → `JournalExportLauncher.share(path)`
4. På fail → emittera `Effect.Snackbar(error)`

---

## 5. Feature 2 — Säsongs-statistik (`SeasonStatsScreen`)

### 5.1 Skärm-layout

```
┌─────────────────────────────────────┐
│  ←  (back)                          │
│                                     │
│  Ditt år / Your year         (Caveat 32sp, AccentCopper)
│  Allt du sett — sammanfattat. (Caveat-italic 14sp)
│                                     │
│  ─────── ❦ ───────                  │
│                                     │
│  [BarChartCanvas: 12 mån]           │
│                                     │
│  [LineChartCanvas: lifelist-tillväxt]
│                                     │
│  [DonutChartCanvas: 4 säsonger]    │
│                                     │
│  [TopSpeciesWidget: top 5]          │
│                                     │
│  ─────── ❦ ───────                  │
└─────────────────────────────────────┘
```

`paperBackground()`-modifier + `JournalScaffold` (Plan 6a T8). Scroll-bar Lazy column.

### 5.2 Widget-specs

**`BarChartCanvas` (Obs per månad, senaste 12 mån)**
- Höjd 140dp
- 12 staplar AccentCopper, paddingstart 24dp, paddingEnd 24dp
- Stapelbredd: `(width - 11×4dp gap) / 12`
- Max-höjd-stapel = `(maxHeight - labelHeight - 8dp)` baserat på `max(values)`
- Värde ovanför stapel om > 0, Caveat-italic 10sp (centered över stapel)
- Tom månad: 2dp-dot vid bas
- X-axel: månads-förkortning (jan/feb...), Caveat-italic 10sp `MarginaliaInk`

**`LineChartCanvas` (Lifelist-tillväxt, kumulativt per månad)**
- Höjd 160dp
- 2dp AccentCopper-stroke (`Path` med `moveTo`/`lineTo`)
- Dots 4dp på varje datapunkt
- Y-axel vänster: 0 + max-värde, Caveat 10sp
- Subtitel under (utanför Canvas): *"Du har sett **N arter** — var på **M** för 12 månader sedan"*

**`DonutChartCanvas` (Säsongs-fördelning)**
- Höjd 200dp (inkl. legend)
- 4 slices, inner radius 60% (riktig donut)
  - Vår: `HeroMossMid`
  - Sommar: `AccentCopper`
  - Höst: `StampNavy`
  - Vinter: `SandCreme`
- Centrum-text: total obs-count DM Serif Italic 24sp + Caveat-label "totalt" / "total"
- Legend 4 rader under: 8dp-dot + säsongsnamn + count, Caveat 12sp

**`TopSpeciesWidget` (Top 5 arter)**
- Inte Canvas — vanlig `Column` med 5 `Row`s
- Per rad: stamp # Caveat copper 14sp + art-namn DM Serif Italic 16sp + obs-count mono 12sp
- Klickbar → `nav.navigate(AppRoute.SpeciesProfile(speciesId))`
- Spacer 8dp mellan rader

### 5.3 Aggregat-källa

`SeasonStatsViewModel` har:
```kotlin
val state: StateFlow<SeasonStatsUiState> =
    observationRepo.observeAll()
        .combine(speciesRepo.observeAll()) { obs, species -> ... }
        .map { (obs, species) -> SeasonStatsUiState.Loaded(
            monthlyCounts = aggregateMonthlyCounts(obs, last12Months = true),
            lifelistGrowth = aggregateLifelistGrowth(obs),
            seasonalSplit = aggregateBySeasons(obs),
            topSpecies = aggregateTopSpecies(obs, species, limit = 5),
        )}
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, SeasonStatsUiState.Loading)
```

Inga nya SQL-queries. In-memory aggregat — ≤10k obs/användare i v1 är försumbart.

### 5.4 Empty + sparse states

- **0 obs:** `EmptyState`-card *"Skanna din första fågel"* + CTA till Scan-tab. Ingen widget renderas.
- **1–3 obs:** Alla widgets renderas med low-data. Värden visas ovanför staplar (även "1") så det inte ser fake-tomt ut.

### 5.5 Lifelist-byte

```kotlin
// LifelistScreen.kt
if (effectivePremiumActive) {
    LiveStatsPreview(
        last6Months = state.monthlyCounts.takeLast(6),
        onClick = { nav.navigate(AppRoute.SeasonStats) }
    )
} else {
    LockedStatsPreview(...)
}
```

`LiveStatsPreview` är ny komponent — kopia av `LockedStatsPreview` men:
- Inga `blur(3.5.dp)` + `graphicsLayer(alpha = 0.55f)`
- Inget lock-overlay
- CTA byts: *"Se hela ditt år →"* / *"See your full year →"* (Caveat AccentCopper)
- Onclick: navigate till `SeasonStats`

---

## 6. Feature 3 — 10 premium-badges ("fält-märken")

### 6.1 `BadgeRule.kt` — 4 nya varianter

```kotlin
sealed interface BadgeRule {
    val target: Int
    // ... existing CountUniqueSpecies, WeeklyStreak, MonthlyStreak,
    //     ObservedInSeason, ObservedInFamily, ObservedWithAbundance

    /** Obs taggad med capturedAt < given hour (local time). */
    data class ObservedBeforeHour(val hour: Int, override val target: Int) : BadgeRule

    /** Unique species observed in >= N different BadgeSeason buckets. */
    data class SpeciesAcrossSeasons(val seasons: Int, override val target: Int) : BadgeRule

    /** Count of obs with sourceType == Audio. */
    data class AudioObservationCount(override val target: Int) : BadgeRule

    /** Count of obs where note.length >= minLength. */
    data class ObservationsWithNote(val minLength: Int, override val target: Int) : BadgeRule

    /** target=1 if all four BadgeSeason buckets are non-empty, else 0. */
    data class ObservedInAllSeasons(override val target: Int) : BadgeRule
}
```

### 6.2 `BadgeEvaluator` — 4 nya `when`-grenar

Pseudo:
```kotlin
fun evaluate(rule: BadgeRule, obs: List<Observation>): Int = when (rule) {
    // ... existing
    is ObservedBeforeHour -> obs.count {
        it.capturedAt.toLocalDateTime(TimeZone.currentSystemDefault()).hour < rule.hour
    }.coerceAtMost(rule.target)

    is SpeciesAcrossSeasons -> obs
        .groupBy { it.speciesId ?: return@groupBy "unknown" }
        .values
        .count { it.map(::seasonOf).toSet().size >= rule.seasons }
        .coerceAtMost(rule.target)

    is AudioObservationCount -> obs
        .count { it.sourceType == ObservationSource.Audio }
        .coerceAtMost(rule.target)

    is ObservationsWithNote -> obs
        .count { it.note.length >= rule.minLength }
        .coerceAtMost(rule.target)

    is ObservedInAllSeasons -> if (obs.map(::seasonOf).toSet().size == 4) 1 else 0
}
```

`seasonOf(obs)` är befintlig helper från Plan 5b.

### 6.3 `premium_badges.yaml` schema v2

```yaml
version: 2
badges:
  - id: premium_dawn_chorus
    icon: "🌅"
    nameSv: "Gryningskör"
    nameEn: "Dawn chorus"
    descriptionSv: "Skanna en fågel före kl 06:00, fem gånger."
    descriptionEn: "Scan a bird before 6 AM, five times."
    rule:
      type: observed_before_hour
      hour: 6
      target: 5
  - id: premium_winter_wanderer
    rule: { type: observed_in_season, season: winter, target: 15 }
    ...
  - id: premium_migration_mapper
    rule: { type: species_across_seasons, seasons: 3, target: 5 }
    ...
  - id: premium_song_scholar
    rule: { type: audio_observation_count, target: 10 }
    ...
  - id: premium_field_journalist
    rule: { type: observations_with_note, min_length: 1, target: 20 }
    ...
  - id: premium_archive_curator
    rule: { type: count_unique_species, target: 100 }
    ...
  - id: premium_seasonal_steward
    rule: { type: observed_in_all_seasons, target: 1 }
    ...
  - id: premium_lifelist_legend
    rule: { type: count_unique_species, target: 200 }
    ...
  - id: premium_rare_seeker
    rule: { type: observed_with_abundance, abundance: rare, target: 5 }
    ...
  - id: premium_field_member
    rule: { type: manual, target: 1 }   # Specialfall — unlockas av PremiumViewModel
    ...
```

Version bumpas `1 → 2` → befintlig `BadgeBackfillOnAppStart` triggar recalc på alla obs.

**Validerings-task (`validatePremiumBadgesYaml`):** Samma `JavaExec` + kaml-mönster som Plan 5b's `validateBadgesYaml`. Kontrollerar: version-fält, alla badges har `rule`, alla `rule.type` mappar till känd enum, `target > 0` (utom `manual`).

### 6.4 Catalog-integration

- `BadgeCatalogLoader.loadFromResources()` läser nu **båda** yaml-filerna och mergar
- `BadgeCatalog.Entry` får `isPremium: Boolean`-flagga
- `BadgeEvaluator.evaluate(...)` signaturen utökas: `evaluate(rule, obs, isPremiumActive: Boolean)` — om badge är premium och `!isPremiumActive` → return `0` (skippa)
- Detta betyder att en användare som köper premium **får backfill av all historik** automatiskt: `BadgeBackfillOnAppStart` kör om över alla obs, premium-badges får riktiga counts, unlocks emittas via `UnlockQueue`

### 6.5 Specialfall #10 — `premium_field_member`

INTE evaluator-baserad, INTE i en kort-livs-VM. Unlockas direkt från en **bootstrap-lyssnare** (`PremiumActivationListener`) som lever i AppGraph-scope (samma livslängd som processen). Detta är samma listener som triggar BadgeBackfill vid premium-flip — den får två jobb:

```kotlin
// composeApp/.../bootstrap/PremiumActivationListener.kt
class PremiumActivationListener(
    private val effectivePremiumActive: StateFlow<Boolean>,
    private val badgeRepo: BadgeRepository,
    private val backfill: BadgeBackfillOnAppStart,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            var prev = effectivePremiumActive.value
            effectivePremiumActive.collect { active ->
                if (active && !prev) {
                    badgeRepo.unlockManualBadge("premium_field_member", Clock.System.now())
                    backfill.recalcPremium()
                }
                prev = active
            }
        }
    }
}
```

Init:as från `MainActivity.onCreate` post-`AppGraph`-bootstrap.

**Ny metod i `BadgeRepository`:**
```kotlin
/** Returns true if the badge was newly unlocked, false if already present. */
suspend fun unlockManualBadge(badgeId: String, unlockedAt: Instant): Boolean
```

Idempotent via SQLDelight `INSERT OR IGNORE` på `badge_unlock(badge_id, unlocked_at)`. När `true` returneras → emittas via `UnlockQueue` (samma flöde som vanliga unlocks).

### 6.6 Backfill-triggers

`BadgeBackfillOnAppStart` (befintlig Plan 5b) får **två triggers**:
1. ~~YAML-version bumpad~~ (befintligt)
2. **NYTT:** `effectivePremiumActive`-flip från `false → true` (oavsett orsak: köp, restore, debug-flag-toggle). Implementation: dedicated `PremiumActivationListener` i bootstrap som observerar `effectivePremiumActive` och kör backfill när det flippar uppåt.

### 6.7 `BadgesScreen` — `PremiumBadgesSection`

Ersätter befintliga `PremiumBadgesRow` (5 ghost stamps + CTA):

```
─────── FÄLT-MÄRKEN ───────  (uppercase, MarginaliaInk)
   10 premium-stämplar       (Caveat-italic, AccentCopper)

  [🌅]  [❄]  [🗺]  [♪]  [✒]
  [📚]  [🍂]  [★]  [✦]  [❦]
```

2×5 grid med 10 `StampSeal`-instanser:
- **`!premiumActive`:** alla `StampSeal(state=Locked)` (ghost-state), tap → navigate `PremiumScreen`
- **`premiumActive`:** real state per badge (Locked/InProgress/Unlocked) baserat på `badge_unlock`-tabellen + evaluator-resultat
- Tap Unlocked → existing `BadgeDetailSheet` med description
- Tap Locked premium-active → progress bottom-sheet "N/M obs" (samma mönster som Plan 6a T10:s A3 locked-tap)

---

## 7. Integration & gating

### 7.1 Premium-gating-konsolidering

All gating läser **enbart** `AppGraph.effectivePremiumActive: StateFlow<Boolean>` (Plan 7e:s `premiumOverride ?: backendState`-pattern).

**Tre call-sites byts:**

| Skärm | Komponent | Före (Plan 7e) | Efter (6b3) |
|---|---|---|---|
| Archive | `PremiumTeaserCard` | `onClick → PremiumScreen` | `if (active) onExportClick() else nav.navigate(Premium)` |
| Lifelist | `LockedStatsPreview` | Alltid lock-state | `if (active) LiveStatsPreview() else LockedStatsPreview()` |
| Badges | `PremiumBadgesRow` | 5 ghost stamps + CTA | `PremiumBadgesSection` med två states (locked/active) |

**`SpeciesProfileScreen.PremiumTeaserCard`** behålls oförändrad — den teasar fortfarande "Field marks-badges" som lever i Badges-fliken; ingen separat onClick-logik behövs här.

### 7.2 `AppGraph`-wiring

`AppGraph` får två nya fält (samma lambda-injection-mönster som Plan 7e:s `benchmarkScreen`):

```kotlin
class AppGraph(
    // ... existing
    val journalExport: suspend () -> Result<Unit>,    // ny
)
```

`MainActivity` bygger `journalExport`:
```kotlin
journalExport = {
    runCatching {
        val spec = JournalSpecBuilder.build(observationRepo, speciesRepo, settingsRepo, locale)
        val path = JournalPdfRenderer().render(spec).getOrThrow()
        JournalExportLauncher.share(path)
    }
}
```

**Ny route:** `AppRoute.SeasonStats` registreras i `Navigation.kt` med `composable<SeasonStats> { SeasonStatsScreen(...) }`.

### 7.3 Per-tab Premium-state-markers (oförändrade)

- Settings `PremiumHeroCard` döljs när active (Plan 7e — inget byte)
- Listen-launcher carousel "Listen"-kort `LaunchCardVariant.Locked` → `Active` (Plan 6b2 — inget byte)

---

## 8. Test-strategi

### 8.1 Unit-tests (`commonMain`)

- `BadgeRule`-evaluator per ny variant — konstruerat obs-set → väntad progress:
  - `ObservedBeforeHour(6, 5)`: 8 obs varav 3 kl 05:00, 2 kl 07:00 → progress=3
  - `SpeciesAcrossSeasons(3, 5)`: art X med obs i vår+sommar+höst → räknas, art Y med obs i bara vår → räknas inte
  - `AudioObservationCount(10)`: 12 photo + 4 audio → progress=4
  - `ObservationsWithNote(1, 20)`: 15 obs varav 8 tomma notes → progress=7
  - `ObservedInAllSeasons`: 4 obs (en per säsong) → 1; 3 obs (saknar vinter) → 0
- `SeasonStatsViewModel`-aggregat: mock obs → väntad `Loaded`-state-shape
- `JournalSpecBuilder.build()` — repos mockade → väntad `JournalSpec` med rätt månadsgruppering, lifelist-sortering, säsongsfördelning

### 8.2 Build-time validators (JVM-tasks i `:shared:content`)

- `validatePremiumBadgesYaml` — version + rule + target + type-mapping
- `validatePremiumBadgeStrings` — descriptionSv + descriptionEn finns för alla 10

### 8.3 Device-verify-sekvens (manuell, SM-S918B)

Toggle `PREMIUM_DEBUG_FORCE_ACTIVE=true` i `androidApp/build.gradle.kts`, `:androidApp:installDebug`, sedan:

1. **PDF-flow:** Skanna 5 obs (var fakt-classifier-cykel via `test_species.txt`) → Archive-flik → tryck PremiumTeaserCard → `JournalLoading` overlay ~2–4s → share-sheet öppnas → välj "Save to Files" → öppna pdf manuellt → verifiera: cover-sida, ToC, lifelist (5 arter), diary-månad (maj 2026 med 5 obs), säsongs-översikt-sida
2. **Stats-flow:** Lifelist-flik → tryck `LiveStatsPreview` CTA → `SeasonStatsScreen` öppnas → verifiera 4 widgets renderar: bar (1 stapel vid maj), line (start på 0, slut på 5), donut (vår+sommar slices), top-5-lista
3. **Badge-flow:** Ändra device-tid till 04:30 → scan en fågel × 5 (`adb shell date 020104302026.00` + foto-loop) → vänta `BadgeBackfill`-recalc (eller relaunch) → verifiera `premium_dawn_chorus` unlocks med UnlockBottomSheet
4. **Member-badge-flow:** Flippa `PREMIUM_DEBUG_FORCE_ACTIVE=false` → starta om → flippa tillbaka till `true` → starta om → verifiera `premium_field_member` unlocks via PremiumState-listener

### 8.4 Canonical screenshots (committas i `docs/superpowers/screenshots/2026-05-21-v0.9.0c-premium-content/`)

Minimum 8:
1. `01-archive-export-active.png` — Archive-flik med Premium-banner aktiverad
2. `02-pdf-loading.png` — `JournalLoading` overlay under render
3. `03-share-sheet.png` — Android share-sheet med pdf-uri
4. `04-pdf-page-cover.png` — pdf-sida 1 (cover)
5. `05-pdf-page-diary.png` — pdf-månads-diary
6. `06-stats-screen-sv.png` — `SeasonStatsScreen` full med 4 widgets (sv)
7. `07-stats-screen-en.png` — samma i EN-locale
8. `08-badges-premium-active.png` — Badges-flik med `PremiumBadgesSection` i active-state, minst en unlocked

Tar bonus: `09-pdf-page-stats.png` (sista pdf-sidan), `10-onboarding-still-works.png` (regression-check).

---

## 9. Acceptance criteria för tag `v0.9.0c-premium-content`

- [ ] `./gradlew build` grön
- [ ] `./gradlew ktlintCheck detekt` grön
- [ ] 4 nya BadgeRule-evaluator-tester gröna
- [ ] `JournalSpecBuilder`-test grön
- [ ] `SeasonStatsViewModel`-aggregat-test grön
- [ ] `validatePremiumBadgesYaml` + `validatePremiumBadgeStrings` Gradle-tasks gröna
- [ ] Plan 5b:s vanliga 25 badge-tester fortfarande gröna (regression-skydd)
- [ ] Device-verify-sekvens (§8.3) komplett passerad på SM-S918B (API 35)
- [ ] Minst 8 canonical screenshots committade
- [ ] Inga R8/TFLite-relaterade crashes i `logcat` under flowet
- [ ] PDF för 50-obs-dagbok ≤ 8 MB

---

## 10. Risk + rollback

| Risk | Mitigation |
|---|---|
| **A.** PDF-rendering buggar på edge-cases (foto saknas, månad utan obs, namn > 60 tecken, single obs med 5000-teckens note) | Defensive draw-helpers + `StaticLayout` för wrap + golden-file-tester för layout-pipeline + log warnings för missade fil-paths |
| **B.** Stats-charts ser fake-tomt ut vid 1–3 obs | Visa siffran ovanför staplar även vid 1 (förslag); minimum-padding så small-data ändå renderar igenkännligt |
| **C.** `unlockManualBadge` race condition vid samtidig PremiumState-flip × BadgeBackfill | Mutex i `BadgeRepositoryImpl.unlockManualBadge` + idempotens via `INSERT OR IGNORE` |
| **D.** Fonts laddas inte i pdf trots `Typeface.createFromAsset` | Tidigt golden-test: render single-page pdf med båda fonts, öppna manuellt, verifiera embedding via Adobe Reader Properties → Fonts |
| **E.** FileProvider-config fel (URI-grant misslyckas) | Manuellt verifiera `<provider>` deklareras `android:exported="false"` + `android:grantUriPermissions="true"` + `<cache-path>` matchar exakt sub-path |
| **F.** Audit-style sen-upptäckt-bugg blockerar tag | Tagga `v0.9.0c-rc1` istället, gå till Internal Testing, fixa i patch |

**Rollback-plan om tag misslyckas:**
- Sätt `PREMIUM_DEBUG_FORCE_ACTIVE=false` i AAB → Premium-features blir oåtkomliga men appen funkar normalt
- Befintliga `LockedStatsPreview` + `PremiumBadgesRow` ghost-states fungerar som fallback

---

## 11. Audit-överlapp (från 2026-05-20-audit)

**Inkluderas i 6b3-scope** (touchar samma kod):
- **B1.** Bumpa `versionName "1.0.0-rc2" → "1.0.0"` i separat commit *före* `v1.0.0`-tag (efter 6b3-content är klart)
- **B3.** Lägg till Billing v8 `-keep`-regler i `proguard-rules.pro` — touchar release-build som 6b3 också gör

**Batchas i en separat "audit-cleanup"-commit före `v1.0.0`-tag** (inte del av feature-arbetet):
- B2 (alpha-pinnad navigation), B4 (runBlocking på main-tråden), B5 (ocheckad cast i Billing), B6 (Java-version-mismatch), B7 (`PREMIUM_DEBUG_FORCE_ACTIVE` invariant-check)

**Skipped (post-launch v1.0.1)**:
- Crashlytics-integration
- Lint-block i androidApp
- CI bundleRelease med signing-secrets

---

## 12. Föreslagen tag-sekvens

```
... (Plan 6b2) ─── v0.9.0b-audio
        │
        ├── 6b3 feature-work (PDF + Stats + Badges)
        │   commits per feature, screenshots, device-verify
        │
        ├── tag v0.9.0c-premium-content   ← 6b3 complete
        │
        ├── audit-cleanup-commit (B1, B2, B4–B7)
        │
        ├── versionName bump → "1.0.0", versionCode 120
        │
        └── tag v1.0.0   ← Internal Testing kandidat
```

Tagging `v1.0.0` startar 14-dagars Closed Testing-klockan.

---

## 13. Beroenden + förutsättningar

- Plan 6b1 (Billing v8) skall vara stable — `PremiumState.Active` triggers används av specialfall #10 ✅ (shipped `v0.9.0a-billing`)
- Plan 6b2 (Audio-ID) skall ha `ObservationSource.Audio` schema ✅ (shipped `v0.9.0b-audio`)
- Plan 6a (UX-polish) tokens + `JournalScaffold`/`JournalLoading`/`JournalDialog`/`EmptyState`-komponenter ✅ (shipped `v0.8.0-rc1`)
- Bundlade fonts i `composeApp/src/commonMain/composeResources/font/` ✅ (shipped Plan 7c)

---

## 14. Out-of-scope (medvetet)

- iOS PDF-implementation (`shared/pdf/iosMain` är stub)
- Custom badge-illustrations (emoji + StampSeal i v1, illustrations i v1.1 om data säger det)
- User-väljer-scope-picker för PDF (allt-på-en-gång i v1)
- Save-to-Downloads-knapp (share-sheet täcker)
- Print Framework integration (share-sheet ger "Print"-val ändå)
- Tids-period-väljare i Stats-skärm (always all-time i v1)
- Snapshot-tester för canvas-charts (overkill för v1; device-verify räcker)
- Crashlytics + lint-block + CI bundleRelease (post-launch v1.0.1)

---

## 15. Nästa steg

Efter user-review av denna spec:
1. Invoke `superpowers:writing-plans` med denna spec som input
2. Plan-fil läggs i `docs/superpowers/plans/2026-05-21-v1-06b3-premium-content.md`
3. Plan exekveras via `superpowers:executing-plans` eller `superpowers:subagent-driven-development`
