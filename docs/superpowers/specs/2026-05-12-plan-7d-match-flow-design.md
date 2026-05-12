# Plan 7d — Match-flow — Design Spec

> Threshold-baserad routing av klassificeringsresultat till tre Field Journal-styled skärmar: Match (stämpel-ögonblick), Disambig (lugn väljare) och NoBird (tom-sidans-poesi). Bygger ovanpå Plan 7c:s designsystem.

**Datum:** 2026-05-12
**Status:** Brainstormad och godkänd. Implementationsplan skrivs härnäst.
**Pipeline-position:** Direkt efter Plan 7c (Field Journal, taggad `v0.7.0c-field-journal` 2026-05-10). Sista steget i Plan 7-redesign innan Plan 6 (Polish + Play Store) återupptas.

---

## 1. Varför

Idag (post-Plan 7c) landar både live-skanning och foto-analys på samma `ClassificationResultScreen`, oavsett confidence. Tre konkreta problem:

1. **Ingen threshold-logik.** En 12%-gissning visas med samma layout som en 92%-gissning. Stämpel-collector-metaforen från Plan 7c devalveras när varje fynd "räknas" lika mycket.
2. **Ingen Disambig-väg.** v1-design §6.2 säger "Top-1 < 0.50 men top-3 finns: visa alla tre, låt användaren välja" — men idag är top1 alltid framlyft som vinnare även när modellen är osäker.
3. **Ingen tom-sidans-state.** Confidence < 0.35 från ett foto resulterar fortfarande i en gissning-stack, inte ett ärligt "ingen tydlig fågel".

Plan 7c byggde StampSeal, PlateFrame, JournalIntro, marginalia-systemet. 7d använder dem för att göra match-momentet betydelsefullt och differentiera de tre confidence-nivåerna visuellt.

## 2. Goal

Lägga till threshold-routing + tre nya skärmvy:er (Match med two varianter, Disambig, NoBird) ovanpå Plan 7c:s designsystem. Specifikt:

- Stämpel-ögonblicket (Match) är **bara** för bevisade fynd (top1 ≥ 0.50).
- Disambig är ett **lugnt val** mellan 2–3 likvärdiga kandidater (top1 0.35–0.50).
- NoBird är **tomma sidans** ärlighet (top1 < 0.35), med en tydlig retry-CTA.
- Match-skärmen särskiljer **first sighting** (NY ART, animerad stämpel) från **repeat sighting** (statisk stämpel, "GÅNG N · första: <datum>").

## 3. Locked design-beslut

Brainstormade fram via 7 forks. Alla bekräftade.

| # | Fork | Beslut |
|---|---|---|
| 1 | Tonalitet för Match vs Disambig | **A** — Match = stämpel-ögonblick (celebration), Disambig = lugn väljare |
| 2 | Threshold-bands | **A** — `<0.35` NoBird, `0.35–0.50` Disambig, `≥0.50` Match. Same live + photo. |
| 3 | Efter Disambig-pick | **A** — Routar till Match-skärm för vald art, marginalia-not "ditt val". En save-path. |
| 4 | First vs repeat sighting | **A** — Två Match-varianter: first (animerad stamp + "NY ART"), repeat (statisk + "GÅNG N · första: 12 mar") |
| 5 | NoBird-state | **A** — Blank-sida + JournalIntro + marginalia-tips + retry-CTA. Ingen manuell-entry. |
| 6 | Save-trigger | **A** — Manuell save-knapp. Ghost-stämpel innan tap, animation efter. |
| 7 | Threshold-edit-skärm | **A** — Out of scope. `private const val` i `MatchThresholds.kt`. |

**Arkitektur:** Approach 3 — en route (`AppRoute.MatchResult`), en VM (`MatchResultViewModel`), en thin shell-screen (`MatchResultScreen`) som väljer sub-Composable (`MatchView` / `DisambigView` / `NoBirdView`) baserat på sealed UiState. Disambig→Match är intern state-mutation, inte nav-event.

## 4. Arkitektur

### 4.1 Filer

```
composeApp/src/commonMain/kotlin/se/birdy/app/
├── ui/match/                                  [NEW directory — migrerad från ui/result/]
│   ├── MatchResultScreen.kt                   [NEW — thin shell, paper-bg, state-switch]
│   ├── MatchResultViewModel.kt                [RENAME från ClassificationResultViewModel + utöka]
│   ├── MatchResultUiState.kt                  [RENAME från ClassificationResultUiState + utöka]
│   ├── MatchView.kt                           [NEW — Match-skärmen, first/repeat-varianter]
│   ├── DisambigView.kt                        [NEW — three-candidate-picker]
│   ├── NoBirdView.kt                          [NEW — blank-sida + retry CTA]
│   └── MatchThresholds.kt                     [NEW — `const val MATCH_CONFIDENCE = 0.50f`, `DISAMBIG_CONFIDENCE = 0.35f`]
├── ui/scan/ScanScreenHost.kt                  [EDIT — nav-target byter Result → MatchResult]
├── ui/photoanalyze/PhotoAnalyzeScreen.kt      [EDIT — samma nav-target-byte]
├── ui/scaffold/Navigation.kt                  [EDIT — AppRoute.ClassificationResult → AppRoute.MatchResult]
└── di/AppGraph.kt                             [EDIT — addera ObservationRepository-dep till MatchResultViewModel-factory]

composeApp/src/commonTest/kotlin/se/birdy/app/
├── ui/match/MatchResultViewModelTest.kt       [RENAME från ClassificationResultViewModelTest + utöka]
└── ui/match/MatchThresholdsTest.kt            [NEW]

composeApp/src/commonMain/composeResources/values{,-en}/strings.xml
                                               [EDIT — nya strängar för Disambig + NoBird + repeat-sighting]

androidApp/build.gradle.kts                    [VERIFY — ObservationRepository-dep transitiv från :composeApp]
```

`ui/result/`-mappen tas bort efter migration. Test-filen flyttas också.

### 4.2 Dataflöde

```
ScanScreen tap-freeze   ┐
                        ├──► AppRoute.MatchResult(predictionsCsv, frameJpegPath, capturedAtMs)
PhotoAnalyzeScreen done ┘                              │
                                                       ▼
                              MatchResultViewModel.init { resolve() }
                                                       │
                                  parseCsv → resolveSpecies (SpeciesRepo) → applyThreshold
                                                       │
                              ┌────────────────────────┼────────────────────────┐
                              ▼                        ▼                        ▼
                       NoBird state            Disambig state            Match state
                       (alla < 0.35)           (top1 0.35–0.50)          (top1 ≥ 0.50)
                                                       │                        │
                                                       │ user picks one         │ (auto-arrival)
                                                       └──── mutate to ────────►│
                                                                                │
                                                              isFirstSighting = ObservationRepo.countByQid(speciesId) == 0
                                                              prevObservedAt = ObservationRepo.firstByQid(speciesId)
                                                                                │
                                                                                ▼
                                                Match(species, confidence, isManualPick, isFirstSighting, prevObservedAt?,
                                                      sightingCount, frameJpegPath, capturedAtMs, saveStatus = NotSaved)
                                                                                │
                                                                                │ user tap "Spara observation"
                                                                                ▼
                                                                  SaveObservationUseCase.save() → unlock-queue → snackbar
                                                                                │
                                                                                │ user tap "Avbryt"
                                                                                ▼
                                                                  popBackStack() → källan (scan eller photo-analyze)
```

### 4.3 Routes-ändring (`Navigation.kt`)

Tas bort:

```kotlin
data class ClassificationResult(
    val predictionsCsv: String,
    val frameJpegPath: String?,
    val capturedAtMs: Long,
) : AppRoute(…)
```

Läggs till:

```kotlin
data class MatchResult(
    val predictionsCsv: String,
    val frameJpegPath: String?,
    val capturedAtMs: Long,
) : AppRoute("match_result?p=$predictionsCsv&f=$frameJpegPath&t=$capturedAtMs")
```

Arg-shape identisk — bara namnet byter. Två call-sites måste uppdateras (`ScanScreenHost`, `PhotoAnalyzeScreen`).

### 4.4 Threshold-konstanter (`MatchThresholds.kt`)

```kotlin
internal object MatchThresholds {
    const val MATCH_CONFIDENCE = 0.50f      // ≥ → Match
    const val DISAMBIG_CONFIDENCE = 0.35f   // 0.35–0.50 → Disambig
    // < 0.35 → NoBird
}
```

`internal` så tests i `:composeApp` kommer åt; inte exporterad ut.

**Not om dubbel-tröskel:** `ScanViewModel.confidenceThreshold = 0.35f` är *fortfarande* "söker…"-gaten i ScanScreen-topchipet (gömmer gissning under skanning). När user tap-freezes triggar `MatchResultViewModel.resolve()` en ANDRA threshold-applikation för att route:a Match/Disambig/NoBird. Olika beslut, olika ställen — medvetet.

## 5. UiState-modell

```kotlin
sealed interface MatchResultUiState {
    data object Loading : MatchResultUiState

    /** All predictions < DISAMBIG_CONFIDENCE (0.35). Inget bevisat fågel-fynd. */
    data class NoBird(
        val frameJpegPath: String?,
    ) : MatchResultUiState

    /** Top-1 i 0.35–0.50-bandet. Användaren får välja bland 2-3 kandidater. */
    data class Disambig(
        val candidates: List<ResolvedPrediction>,     // 2-3 stk, sorterade
        val stampNumber: Int,                          // för JournalIntro eyebrow (visas innan pick)
        val frameJpegPath: String?,
        val capturedAtMs: Long,
    ) : MatchResultUiState

    /** Top-1 ≥ 0.50 ELLER användaren just pickade från Disambig. */
    data class Match(
        val species: Species,
        val confidence: Float,                         // original ML conf (eller pickad conf om från Disambig)
        val isManualPick: Boolean,                     // false = auto, true = från Disambig-pick
        val isFirstSighting: Boolean,                  // true = nytt artfynd → animera stamp
        val prevObservedAt: Instant?,                  // null om first sighting, annars för "FÖRSTA: 12 mar"
        val sightingCount: Int,                        // 1 för first, N+1 för repeat
        val stampNumber: Int,                          // observationRepo.nextStampNumber() — visas i JournalIntro eyebrow
        val frameJpegPath: String?,
        val capturedAtMs: Long,
        val saveStatus: SaveStatus = SaveStatus.NotSaved,
        val pendingUnlock: BadgeUnlock? = null,
        val pendingBadge: Badge? = null,
        val unlockQueueSize: Int = 0,
    ) : MatchResultUiState

    /** Klassning misslyckades (parse-fel eller ingen art-resolution). */
    data class Error(val kind: Kind) : MatchResultUiState {
        enum class Kind { NoPredictions, ParseFailed }
    }

    sealed interface SaveStatus {
        data object NotSaved : SaveStatus
        data object Saving : SaveStatus
        data object Saved : SaveStatus
        data class Failed(val kind: Kind) : SaveStatus {
            enum class Kind { PhotoEncodeFailed, StorageFull, DatabaseFailed, FrameUnavailable }
        }
    }
}
```

**Notes:**

- `Match.isFirstSighting` + `prevObservedAt` + `sightingCount` är pre-beräknade i `resolve()` resp. `pickFromDisambig()` så Composable inte måste re-query.
- Gamla `Error.Kind.NoMatches` ersätts av `NoBird`-state. Tydligare semantik: NoBird är lågkonfidens-medvetet, Error.ParseFailed är art-resolutions-fel.
- `Disambig.candidates`-storlek är typiskt 3, kan vara 2 om bara 2 av top-3 resolvades i species-repo. Aldrig 1 (då routar `Error.ParseFailed`).
- Plan 5a:s `SaveStatus`-shape är identisk — direkt reuse.

## 6. VM-funktioner

```kotlin
class MatchResultViewModel(
    private val speciesRepo: SpeciesRepository,
    private val observationRepo: ObservationRepository,    // NEW dep
    private val saveUseCase: SaveObservationUseCase,
    private val catalog: BadgeCatalog,
    private val predictionsCsv: String,
    private val frameJpegPath: String?,
    private val capturedAtMs: Long,
    private val locale: Locale,
) : ViewModel() {

    val state: StateFlow<MatchResultUiState>

    // Init: launch resolve() + spawna unlock-queue-collector (Plan 5b-mönstret).

    private suspend fun resolve()                                  // parsa CSV → applyThreshold → set state
    fun pickFromDisambig(speciesId: SpeciesId)                     // Disambig → Match(isManualPick = true)
    fun saveToDiary()                                              // Match.saveStatus: NotSaved → Saving → Saved/Failed
    fun dismissUnlock()                                            // unlockQueue.pop() — Plan 5b-flow oförändrat
}
```

**`resolve()`-logik:**

1. Parsa `predictionsCsv` (Plan 5a-format `Q123:85/100,Q456:42/100,…`).
2. Om empty → `Error(NoPredictions)`.
3. Resolvera varje Q-ID via `SpeciesRepo` → `ResolvedPrediction`-lista.
4. Om alla resolutions failar → `Error(ParseFailed)`.
5. Filtrera till conf ≥ `DISAMBIG_CONFIDENCE` för Disambig-band.
6. Hämta `stampNumber = observationRepo.nextStampNumber()` (för eyebrow på Match + Disambig — användbart även innan save).
7. Routing:
   - Top-1 conf ≥ `MATCH_CONFIDENCE` → bygg `Match`-state (kalla `observationRepo.countByQid` + `firstByQid`).
   - Top-1 conf ≥ `DISAMBIG_CONFIDENCE` (≥0.35, <0.50) → `Disambig(candidates = resolved.filter { it.conf ≥ DISAMBIG_CONFIDENCE }.take(3))`.
   - Annars → `NoBird(frameJpegPath)`.

**`pickFromDisambig()`-logik:**

```kotlin
fun pickFromDisambig(speciesId: SpeciesId) {
    val current = _state.value as? Disambig ?: return
    val picked = current.candidates.firstOrNull { it.species.id == speciesId } ?: return
    viewModelScope.launch {
        val count = observationRepo.countByQid(speciesId)
        val prev = if (count > 0) observationRepo.firstByQid(speciesId) else null
        _state.value = Match(
            species = picked.species,
            confidence = picked.confidence,
            isManualPick = true,
            isFirstSighting = count == 0,
            prevObservedAt = prev,
            sightingCount = count + 1,
            stampNumber = current.stampNumber,           // re-use från Disambig — samma framtid-obs
            frameJpegPath = current.frameJpegPath,
            capturedAtMs = current.capturedAtMs,
        )
    }
}
```

`saveToDiary()` återanvänder Plan 5a:s flow med en ändring: VM:n kollar `state is Match` (inte `Loaded`) som save-precondition.

## 7. Match-skärmen (`MatchView.kt`)

### 7.1 Layout

```
┌─────────────────────────────────────────┐
│ paper-bg (PaperBg #EFE7D6 + dot-texture)│
│                                         │
│ ┌─ JournalIntro ─────────────────────┐ │
│ │  MATCH · NO {stampNumber}           │ │  ← eyebrow MicroLabel
│ │  *Talgoxe*                          │ │  ← headline DM Serif Italic, NO question mark
│ │  En 87% match.                      │ │  ← sub Caveat (confidence alltid synlig)
│ └────────────────────────────────────┘ │
│                                         │
│  ❦  ────────────                         │  ← OrnamentRule
│                                         │
│ ┌─ PlateFrame ───────────────────────┐ │
│ │  [frame-jpeg 16:10]                 │ │
│ │  ❦                                  │ │
│ │  Talgoxe                            │ │
│ │  Parus major                        │ │
│ │  [StampSeal — large, ghost or solid]│ │  ← THE stamp moment
│ └────────────────────────────────────┘ │
│                                         │
│ ┌─ Marginalia (Caveat italic) ───────┐ │
│ │  *NY ART*  (first sighting)          │ │  ← eller "GÅNG 4 · första: 12 mar 2026" (repeat)
│ │  *ditt val*  (om isManualPick)      │ │  ← liten copper-tint not
│ └────────────────────────────────────┘ │
│                                         │
│  [Spara observation]      ← copper CTA  │
│  Avbryt                   ← text-only   │
└─────────────────────────────────────────┘
```

### 7.2 StampSeal-states

| Tillstånd | First sighting | Repeat |
|---|---|---|
| `saveStatus = NotSaved` (innan tap) | Ghost-kontur, copper-stroke 1.5dp, outline-only, inuti: serif "?" eller dot. Caption: *väntar på din signatur* (Caveat). | Solid statisk stämpel, monogram (art-id eller familje-glyf), copper fill. Caption: `GÅNG N · FÖRSTA: 12 MAR 2026` (MicroLabel). |
| `saveStatus = Saving` | Ghost + `CircularProgressIndicator` overlay i copper. | Solid + samma overlay. |
| `saveStatus = Saved` | Animation: ghost-stroke fyller med copper, scale 1.0→1.15→1.0 över 320ms + rotation -4°, optional `LocalHapticFeedback.LONG_PRESS`. | Pulse-glow alpha 0.6→1.0 över 200ms. Mer återhållsamt. |
| `saveStatus = Failed` | Ghost återgår, snackbar visar Plan 5a-felmeddelande. | Solid kvar, snackbar samma. |

Animation via `animateFloatAsState` + `Modifier.rotate` + `Modifier.scale`. Plan 7c:s motion-policy ("low key") följs.

### 7.3 JournalIntro per variant

| Fält | First sighting | Repeat |
|---|---|---|
| eyebrow | `MATCH · NO {stampNumber}` | `MATCH · NO {stampNumber}` |
| headline | `*{speciesName}*` (DM Serif Italic, no question mark) | `*{speciesName}*` |
| sub | `En {pct}% match.` (Caveat) | `En {pct}% match.` |

`stampNumber` hämtas från `observationRepo.nextStampNumber()` — det löpnummer denna observation kommer att få efter save. Reuse av existerande Plan 7c-mönster (`StampNumberBadge` + `observeAllByStampNumber()`). `isManualPick` påverkar INTE sub-raden; manuell-pick signaleras enbart i marginalia (§7.4) så att confidence alltid är synlig på sub-raden.

### 7.4 Marginalia-rader

- First sighting: `*NY ART*` (Caveat italic, copper-tint, 18sp, rotation -3°).
- Repeat sighting: `GÅNG 4 · FÖRSTA: 12 MAR 2026` (MicroLabel uppercase Inter, 11sp, copper-accent på `4` via `BodyTextWithCaveatAccents`-mönstret).
- Om `isManualPick = true`: lägg till en separat rad `*ditt val*` (Caveat italic, 13sp, copper-tint, rotation -6°), höger-justerad i marginalia-zonen.

### 7.5 CTAs

1. `Spara observation` — primär copper-button, 52dp (Plan 7c-mönstret).
2. `Avbryt` — text-only, MarginaliaInk 14sp, navigerar `popBackStack()` utan att spara.

`saveStatus = Saving`: Spara-knappen byter till `CircularProgressIndicator`, Avbryt disabled.
`saveStatus = Saved`: båda knappar ersätts av text "Sparad i fältdagboken — gå tillbaka" + snackbar (Plan 5a) + UnlockBottomSheet (Plan 5b) over allt om relevant.

## 8. Disambig-skärmen (`DisambigView.kt`)

### 8.1 Layout

```
┌─────────────────────────────────────────┐
│ paper-bg                                │
│                                         │
│ ┌─ JournalIntro ─────────────────────┐ │
│ │  TRE KANDIDATER · NO {stampNumber}  │ │
│ │  *Vilken matchar?*                   │ │  ← question mark KVAR
│ │  Modellen är inte säker.             │ │
│ └────────────────────────────────────┘ │
│                                         │
│  ❦  ────────────                         │
│                                         │
│ ┌─ Frame-thumbnail (small) ──────────┐ │
│ │  [72×72]  *vad du såg*              │ │  ← Caveat-italic caption
│ └────────────────────────────────────┘ │
│                                         │
│ ┌─ Candidate 1 (top1) ───────────────┐ │
│ │  [hero-bild 16:10]                  │ │  ← PlateFrame mini
│ │  *Talgoxe*                          │ │
│ │  Parus major · 47% match            │ │
│ └────────────────────────────────────┘ │
│                                         │
│ ┌─ Candidate 2 ──────────────────────┐ │
│ │  ...                                │ │
│ └────────────────────────────────────┘ │
│                                         │
│ ┌─ Candidate 3 ──────────────────────┐ │
│ │  ...                                │ │
│ └────────────────────────────────────┘ │
│                                         │
│  Tryck för att välja                    │  ← hint, MarginaliaInk italic 12sp
│  Avbryt                                 │  ← text-only
└─────────────────────────────────────────┘
```

### 8.2 Candidate-card

- Hela kortet `clickable { vm.pickFromDisambig(candidate.species.id) }` — inga separata knappar.
- Bakgrund: `Color.White.copy(alpha = 0.4f)`.
- Border: `AccentCopper.copy(alpha = 0.3f)`, 1dp, `RoundedCornerShape(12dp)`.
- Hero-bild: 16:10 via Coil. Saknad bild → familje-silhouette-placeholder (Plan 5b-mönstret).
- Padding 12dp. species.name DM Serif Italic 20sp; scientificName italic 13sp MarginaliaInk; confidence Caveat copper 14sp.
- Touch-feedback: scale 1.0→0.97 pressed → 1.0 (100ms `animateFloatAsState`), default ripple.

### 8.3 Edge-case: 2 kandidater

Eyebrow blir `TVÅ KANDIDATER · NO {stampNumber}`. headline + sub samma. Visa 2 kort. Aldrig 1 (det skulle vara `Error.ParseFailed`).

### 8.4 CTAs

Bara `Avbryt` (text-only) — pick är candidate-tap. Ingen "Spara" på Disambig (sparar inte här).

## 9. NoBird-skärmen (`NoBirdView.kt`)

### 9.1 Layout

```
┌─────────────────────────────────────────┐
│ paper-bg                                │
│                                         │
│ ┌─ JournalIntro ─────────────────────┐ │
│ │  INGEN MATCH · NO {date}            │ │  ← `12 MAJ 2026` (NOT stampNumber)
│ │  *Ingen art kunde tecknas*           │ │
│ │  En obestämd skugga, en vingfladder. │ │
│ └────────────────────────────────────┘ │
│                                         │
│  ❦  ────────────                         │
│                                         │
│ ┌─ Frame-thumbnail (om vi har) ──────┐ │
│ │  [120×120, rotation -3°]            │ │  ← "tejpad" look
│ └────────────────────────────────────┘ │
│                                         │
│ ┌─ Marginalia (Caveat italic) ───────┐ │
│ │  *kom närmare*    (rot -2°)         │ │
│ │  *centrera fågeln* (rot +3°)        │ │
│ │  *mer ljus*       (rot -4°)         │ │
│ └────────────────────────────────────┘ │
│                                         │
│  [Försök igen]              ← copper CTA│
└─────────────────────────────────────────┘
```

### 9.2 JournalIntro-fält

| Fält | sv | en |
|---|---|---|
| eyebrow | `INGEN MATCH · NO {date}` | `NO MATCH · NO {date}` |
| headline | `*Ingen art kunde tecknas*` | `*No species could be sketched*` |
| sub | `En obestämd skugga, en vingfladder.` | `A blur, a flicker of wings.` |

`{date}` = `formattedDate(capturedAtMs)`, t.ex. `12 MAJ 2026` / `12 MAY 2026`. Inte stampNumber eftersom NoBird inte loggas i dagboken.

### 9.3 Frame-thumbnail

- 120×120, paper-edge-border, `Modifier.rotate(-3f)`.
- Ingen caption — vi vill inte säga "vad du såg" när modellen sagt att det inte var en fågel.
- Om `frameJpegPath = null`: skip hela thumbnail-blocket.

### 9.4 Marginalia-tips

Tre Caveat-italic-rader, MarginaliaInk-färg, 18sp, olika rotation (−2°, +3°, −4°) och horisontell offset (left, center-right, left-indent) för "handskriven i marginalen"-känsla.

| sv | en |
|---|---|
| *kom närmare* | *get closer* |
| *centrera fågeln* | *center the bird* |
| *mer ljus* | *more light* |

Texten lånar formulering från v1-design §6.2 men presenteras som marginal-anteckningar istället för error-toast.

### 9.5 CTA

`Försök igen` (copper, 52dp). `popBackStack()` → tillbaka till källan (scan eller photo-analyze, naturligt via back-stack).

Ingen sekundär CTA — locked A i fråga 5.

### 9.6 Edge-case: live tap-freeze under "söker…"

`ScanViewModel.onFreeze()` skapar `FrozenAt` även med null/lågkonf-top1. `MatchResultViewModel.resolve()` routar till NoBird. Användaren förstår snabbt. Mindre kod än att disable tap.

## 10. Routing-ändringar

### 10.1 `ScanScreenHost.kt`

```kotlin
LaunchedEffect(state) {
    if (state is ScanUiState.FrozenAt) {
        nav.navigate(
            AppRoute.MatchResult(
                predictionsCsv = state.predictionsCsvString(),
                frameJpegPath = state.frameJpegPath,
                capturedAtMs = state.timestampMillis,
            ),
        )
    }
}
```

Bara route-namnet byter.

### 10.2 `PhotoAnalyzeScreen.kt`

Identisk single-line-change.

### 10.3 Composable-host (`Navigation.kt`)

```kotlin
composable(route = "match_result?p={p}&f={f}&t={t}", arguments = [...]) { entry ->
    val csv = entry.arguments?.getString("p") ?: ""
    val frame = entry.arguments?.getString("f")?.takeIf { it != "null" }
    val capturedAtMs = entry.arguments?.getString("t")?.toLongOrNull() ?: 0L
    val vm = viewModel {
        MatchResultViewModel(
            speciesRepo = appGraph.speciesRepository,
            observationRepo = appGraph.observationRepository,    // NEW
            saveUseCase = appGraph.saveObservationUseCase,
            catalog = appGraph.badgeCatalog,
            predictionsCsv = csv,
            frameJpegPath = frame,
            capturedAtMs = capturedAtMs,
            locale = appGraph.locale,
        )
    }
    MatchResultScreen(viewModel = vm, …)
}
```

### 10.4 `AppGraph` + transitiv-deps

- `AppGraph` exponerar redan `observationRepository` (Plan 5a). Bara koppla in den i factory.
- **Plan 5a-fälla:** `:composeApp` använder `implementation()` (inte `api()`). Verifiera att `:androidApp/build.gradle.kts` redan har `implementation(project(":shared:data"))` eller motsvarande. Om inte: addera.
- Bygg `:androidApp:assembleDebug` tidigt i implementations-planen för att fånga dependencies-fel.

### 10.5 Back-stack-beteende

Alla MatchResult-states `popBackStack()` till källan. Inga special-cases. Disambig→Match-transitionen (intern state-mutation) gör att back-from-Match går till källan, inte tillbaka till Disambig — locked Approach 3 tradeoff.

**Risk:** Plan 4a-followup-spår: `LaunchedEffect(state)` for terminal-state-nav kan ge double-nav vid recomposition. Pre-existerande issue, inte specifikt för 7d. Inte lyfts här.

## 11. Tester

### 11.1 Unit-tester

**`MatchResultViewModelTest.kt`:**

| Test | Asserterar |
|---|---|
| `resolve_top1_above_match_threshold_first_sighting` | `Match(isFirstSighting=true, sightingCount=1, prevObservedAt=null)` |
| `resolve_top1_above_match_threshold_repeat` | `Match(isFirstSighting=false, sightingCount=N+1, prevObservedAt=<Instant>)` (3 seedade obs) |
| `resolve_top1_in_disambig_band` | `Disambig(candidates.size in 2..3)` |
| `resolve_top1_below_nobird_threshold` | `NoBird` även om top2/top3 finns |
| `resolve_empty_predictions` | `Error(NoPredictions)` |
| `resolve_no_species_resolved_in_repo` | `Error(ParseFailed)` |
| `pickFromDisambig_resolves_to_match` | `Disambig.candidates[1].pick()` → `Match(isManualPick=true)` |
| `pickFromDisambig_unknown_species_id_is_noop` | state oförändrad |
| `saveToDiary_from_match_success` | `SaveStatus.Saved` + `pendingUnlock` propagerat |
| `saveToDiary_from_match_4_failure_kinds` | Plan 5a-paritet |
| `saveToDiary_from_disambig_state_is_noop` | bara Match får spara |
| `saveToDiary_from_nobird_state_is_noop` | samma |
| `unlockQueue_only_mutates_match_state` | Plan 5b-guard kvar |

**`MatchThresholdsTest.kt`:**

| Test | Asserterar |
|---|---|
| `applyThreshold_at_exactly_0_50_routes_to_match` | tröskel inklusiv |
| `applyThreshold_at_0_4999_routes_to_disambig` | precision |
| `applyThreshold_at_exactly_0_35_routes_to_disambig` | undre Disambig-gräns inklusiv |
| `applyThreshold_at_0_3499_routes_to_nobird` | precision |
| `applyThreshold_filters_candidates_below_disambig_in_disambig_band` | bara conf ≥ 0.35 visas som Disambig-kandidat |

### 11.2 Snapshot-tester (lågprio, parkeras)

Trevliga-att-ha men inte blockerande. Plan 6 kan addera:

- `MatchView_firstSighting_loaded`
- `MatchView_firstSighting_saved_animated`
- `MatchView_repeat_loaded`
- `MatchView_manualPick_marginalia`
- `DisambigView_three_candidates`
- `DisambigView_two_candidates`
- `NoBirdView_with_frame_thumbnail`
- `NoBirdView_no_frame`

### 11.3 Device-verify (S23 Ultra / SM-S918B)

Plan 5a-lärdom: `:composeApp:assembleDebug` täcker inte transitiva-deps + compose-resources. Device-verify körs `:androidApp:installDebug` + ADB-launch.

| Scenario | Path | Förväntat |
|---|---|---|
| Live scan + tap-freeze på koltrast (conf > 0.80) | Match (auto, first) | Ghost → tap → animation → snackbar |
| Live scan + tap-freeze samma art igen | Match (auto, repeat) | "GÅNG 2 · första: <tidigare datum>" |
| Photo med osäker fågel (conf 0.40) | Disambig | Tre kort, tap → Match (manual pick + "ditt val") |
| Photo med tomt himmel | NoBird | "Ingen art kunde tecknas" + retry |
| Live tap-freeze under "söker…" | NoBird | Samma från scan-källan |
| Disambig-pick → save → badge unlock fyrar | Match → Saved → UnlockBottomSheet | Plan 5b-flowet kvar |
| Avbryt-CTA på Match | popBackStack | Tillbaka till scan, ingen obs sparad |
| Force-quit mitt i Disambig | — | Ingen partial state läcker |

Screenshots: 8 stk (en per path + en per save-state + animation-frame).

## 12. Acceptanskriterier

Plan 7d är klar när:

- [ ] `MatchResultUiState` sealed med 5 cases + Match-fält.
- [ ] `MatchThresholds.kt` definierar 2 konstanter.
- [ ] `MatchResultViewModel.resolve()` + `pickFromDisambig()` + reuse av `saveToDiary()` + `dismissUnlock()`.
- [ ] `MatchView.kt` renderar first sighting + repeat med StampSeal-states.
- [ ] `DisambigView.kt` renderar 2–3 candidates med PlateFrame-mini.
- [ ] `NoBirdView.kt` renderar marginalia-tipsen + retry CTA.
- [ ] `ScanScreenHost.kt` + `PhotoAnalyzeScreen.kt` navigerar till `AppRoute.MatchResult`.
- [ ] `ui/result/` borttagen.
- [ ] Alla unit-tester i §11.1 går grönt.
- [ ] `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest` grönt.
- [ ] `./gradlew ktlintCheck detekt` grönt.
- [ ] `./gradlew :androidApp:installDebug` lyckas.
- [ ] 8 device-verify-scenarios passerar på S23 Ultra.
- [ ] 8 screenshots committade i `docs/superpowers/screenshots/v0.7.0d-match-flow/`.
- [ ] Tag `v0.7.0d-match-flow` på `main`.
- [ ] CLAUDE.md status-rad uppdaterad: Plan 7d ✅.

## 13. Out of scope

- **Threshold-edit-skärm** — locked A i fork 7. Plan 6 eller v1.5.
- **Manuell art-entry från NoBird** — locked A i fork 5. v1.5.
- **Back från Disambig→Match → tillbaka till Disambig** — locked tradeoff i Approach 3. Plan 6 om feedback kräver.
- **Snapshot-tester (compose-test)** — trevlig-att-ha, Plan 6.
- **Animation-polish utöver stamp scale/rotate/glow** — inga partikel-effekter, ingen sound design, inga page-flip-transitions. Plan 6.
- **iOS-stöd** — Plan 7d är commonMain men aktiveras först i v2. Haptic via `LocalHapticFeedback` är cross-platform OK.
- **Disambig-pick-confirmation-dialog** — locked i fork 3 (auto-transition, Avbryt på Match är ångermöjligheten).
- **Frame-thumbnail click-to-fullscreen i Disambig** — nice-to-have, Plan 6.
- **Confidence-distribution-telemetri** — ingen analytics-pipeline i v1. Plan 6 eller v1.5.

## 14. Plan-dependencies

| Förutsätter | Status |
|---|---|
| Plan 7c (Field Journal-tokens, StampSeal, PlateFrame, JournalIntro, MicroLabel, OrnamentRule, marginalia, BodyTextWithCaveatAccents) | ✅ `v0.7.0c-field-journal` 2026-05-10 |
| Plan 5a (ObservationRepository, SaveObservationUseCase, SaveStatus-flow) | ✅ `v0.5.0a-diary` |
| Plan 5b (UnlockQueue, UnlockBottomSheet, BadgeCatalog) | ✅ `v0.5.0b-gamification` |
| Plan 4b (Real TFLite — riktiga confidence-värden från AIY V1 driver state-routing) | ✅ `v0.4.0b-real-tflite` |

**Efter 7d:** Plan 6 (Polish + Play Store-release) återupptas.

## 15. Risker

| Risk | Sannolikhet | Impact | Mitigation |
|---|---|---|---|
| AIY V1 confidence-distribution gör Disambig dominerande (top-1=52% accuracy → många 0.35–0.50) | Hög | Medel | Acceptera — Disambig är *bra* när modellen är osäker. Eyebrow "TRE KANDIDATER" är ärlig. |
| Repeat-sighting-detection (count-query före save) är race-condition-känslig vid parallel save | Låg | Låg | Save är single-threaded via VM-state-machine. Query körs på Main efter classifier. |
| `popBackStack()` återgår till stale ScanScreen med kvarvarande FrozenAt-state → infinite nav-loop | Medel | Hög | Plan 4a-mönstret: `ScanViewModel.onResumeAfterFreeze()` redan implementerat, måste anropas explicit på back. Verifiera. |
| StampSeal-animation ljuger om save misslyckas mitt i | Låg | Låg | Animation fyrar EFTER `SaveStatus.Saved` (inte `Saving`). Vid Failed återgår ghost. |
| Plan 5a-fälla: nya VM-dep (`ObservationRepository`) saknas i `:androidApp/build.gradle.kts` | Hög | Medel | Task 1: verifiera transitiv dep + bygg `:androidApp:assembleDebug` innan vidare arbete. |
| compose-resources unescape-bugg (Plan 5a) på nya strängar | Medel | Låg | Audit nya strings.xml-rader för `'` (raw) och `%%` (`%1$s`). |
| `observationRepo.countByQid` + `firstByQid` saknas i ObservationRepository-interface | Hög | Låg | Task 2: addera båda metoderna med Flow-baserad query (SQLDelight reuse). |

## 16. Implementations-plan (kommer separat)

Plan-doc skrivs härnäst via `superpowers:writing-plans`. Förväntad struktur: ~9–12 tasks i ordning:

1. Verifiera `:androidApp`-deps + bygg-state.
2. Addera `ObservationRepository.countByQid` + `firstByQid` (om saknas).
3. Skapa `MatchThresholds.kt` + tester.
4. Skapa `MatchResultUiState.kt` + flytta `ui/result/` → `ui/match/`.
5. Utöka `MatchResultViewModel.resolve()` med threshold-routing + sighting-detection.
6. Lägg till `pickFromDisambig()` + tester.
7. Bygg `MatchView.kt` (first + repeat + StampSeal-states + animation).
8. Bygg `DisambigView.kt`.
9. Bygg `NoBirdView.kt`.
10. Wira `MatchResultScreen.kt` shell + thread routing.
11. Uppdatera ScanScreenHost + PhotoAnalyzeScreen + Navigation route.
12. Device-verify, screenshots, tag, CLAUDE.md-update.
