# Birdy Bird Scanner — arbetsguide för Claude Code

> **Den här filen läses automatiskt av Claude Code i varje session.** Den ger sammanhang för projektet, var saker ligger, och hur vi arbetar.

## Vad är detta?

AI-driven Android-app för fågelidentifiering. Realtidsskanning via kamera + foto-upload + uppslagsverk över ~700 europeiska arter. Kotlin Multiplatform + Compose Multiplatform. v1 = Android-only ("Skanna & lär"); senare faser lägger till dagbok, gamification, karta, push, community, iOS.

**Status (2026-05-05):** Plan 1 ✅ (`v0.1.0-foundation`). Plan 2a ✅ (`v0.2.0a-pipeline`). Plan 2b ⏸ pausad vid 97/700, nästa familj = anatidae. Plan 3 ✅ (`v0.3.0-encyclopedia`). **Plan 4a 🟡 PÅGÅR — Tasks 1–9 ✅ device-verified på SM-S918B; nästa = Task 10 (polish + tag v0.4.0a-ml-camera).** Plan 4b deferrad (separat brainstorm).

## Var hittar du saker

| Vad | Var |
|---|---|
| Designspec för v1 | `docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md` |
| Implementationsplaner | `docs/superpowers/plans/YYYY-MM-DD-v1-NN-<phase>.md` |
| Skärmdumpar per milstolpe | `docs/superpowers/screenshots/` |
| Milstolpe-review-runbook | `docs/superpowers/runbooks/milstolpe-review.md` |
| Plan 2b family-by-family-runbook | `docs/superpowers/runbooks/2026-05-02-plan-2b-content-backfill.md` |
| Visuellt språk (Mossbädd) | sammanfattat nedan + auto-memory `visual_language_birdy_v1.md` |
| Auto-memory (lokalt, inte i repo) | `~/.claude/projects/C--Users-abbea-dev-birdy-bird-scanner/memory/` |

## Plan-of-plans (v1)

| # | Plan | Status |
|---|---|---|
| 1 | Foundation — KMP-bootstrap, Compose, CI, Mossbädd-tema | ✅ `v0.1.0-foundation` |
| 2a | Content pipeline + walking skeleton (5 arter) | ✅ `v0.2.0a-pipeline` |
| 2b | Content backfill family-by-family (5 → ~700 arter) | ⏸ 97/700 (alaudidae `d945e1f`) |
| 3 | Encyclopedia (browse + species profile) | ✅ `v0.3.0-encyclopedia` |
| 4a | ML & Camera UI (FakeClassifier + UI + CameraX 3 fps) | 🟡 Tasks 1–9 ✅ device-verified; nästa = Task 10 |
| 4b | Real TFLite-modell | ⏸ separat brainstorm senare |
| 5 | Diary & Gamification | |
| 6 | i18n, polish, Play Store-release | |

Varje plan ska lämna projektet i ett byggbart, testbart tillstånd: `./gradlew build` ska gå grönt.

## Hur vi jobbar

### När du börjar en ny session
1. Säg "Vi fortsätter med birdy-bird-scanner" eller liknande.
2. Be om statusöversikt: "Var står vi?" → kolla git log + senaste commit.
3. Bestäm nästa steg utifrån status.

### Behövs superpowers?
- **Brainstorming, ny plan, plan-execution med review** → `superpowers:brainstorming` / `:writing-plans` / `:subagent-driven-development`.
- **Vanliga frågor, snabba bugfixar, mindre refactoring** → bara prata; ingen skill.

Tumregeln: större än ett samtal eller kräver disciplin (TDD, plan-tracking) → skill. Annars inte.

### Modell-strategi
| Uppgift | Modell |
|---|---|
| Brainstorming, design, arkitektur, code review | Opus 4.7 |
| Implementer-subagents i `subagent-driven-development` | Sonnet 4.6 |
| Snabba lookups | Haiku 4.5 |

Vid avbrott: all progress är committad i git. Nästa session fortsätter från senaste commit utan tappad kontext.

## Visuellt språk (Mossbädd)

Färgpalett (locked 2026-04-30):

| Token | Hex | Roll |
|---|---|---|
| Background | `#E8E2D2` | Pale moss-creme |
| Hero top / deep / shadow | `#5C6E48` / `#3F4F30` / `#2A3520` | Mossgrön gradient |
| Accent | `#8C5A3C` | Koppar (CTA, aktiv flik, stat-siffror) |
| Stat surface | `#D8D0BC` | Sand-creme |
| Text primary | `#2A3525` | Djup skog |
| Text on hero/accent | `#F0EAD8` | Varm offwhite |

**Typografi:** Crimson Pro (serif) för rubriker/siffror; system sans för UI. UPPERCASE-etiketter med spärr.

**Layout:** hero är en *zon* (vertikal gradient mot bg), inte ett kort. CTA i koppar ekas i siffror + aktiv flik. Bottom-bar 72dp, ikon + textetikett per flik. Tema-tokens i `composeApp/.../ui/theme/Color.kt` + `Type.kt`.

## Tekniska val (en rad var)

- **Stack:** KMP + Compose Multiplatform (Android första, iOS-skelett)
- **DB:** SQLDelight 2.x med Flow-baserade queries
- **ML:** TensorFlow Lite (on-device); 4a använder `FakeBirdClassifier` bakom `BirdClassifier`-interface
- **Kamera:** CameraX (Android), 3 fps streaming, confidence threshold 0.35, auto-throttle till 1.5 fps vid p95-latens > 333ms
- **Språk:** SV + EN, Sverige först; alla UI-strängar via compose-resources
- **Distribution:** Play Asset Delivery för stora bundles
- **CI:** GitHub Actions (ktlint, detekt, unit tests, assembleDebug, APK-artefakt)
- **Statisk analys:** ktlint 12.1.2 + detekt 1.23.7

## Lokal utvecklingsmiljö (Windows + Galaxy S23 Ultra)

| Vad | Var |
|---|---|
| JDK 21 (Temurin) | `C:\Java\OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10\jdk-21.0.11+10\` |
| Android SDK | `C:\Users\abbea\AppData\Local\Android\Sdk` |
| ADB | `C:\Users\abbea\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| Telefon | SM-S918B (Galaxy S23 Ultra), USB-felsökning på, RSA-auktoriserad |

**Standard-prefix för bash-`./gradlew`-kommandon** (annars hittar Gradle inte Java):

```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

## Vanliga kommandon

```bash
# Bygga + installera + starta på ansluten enhet
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity

# Snabba unit-tests (delade moduler på JVM)
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest

# Lint + statisk analys
./gradlew ktlintCheck detekt
./gradlew ktlintFormat   # autofix
```

## Repo

GitHub: https://github.com/anonadrek/birdy. Branch: `main` är default; plan-arbete sker på `main` med små commits per task; tagga milstolpar (`v0.1.0-foundation` osv).

## Beslut & ramar

- **Scope:** v1 = "Skanna & lär" + uppslagsverk. Inget mer (dagbok, mål osv kommer i Plan 5+).
- **Geografi:** Norden/Europa, ~700 arter.
- **Användare:** Bred två-lager (nybörjare som vill lära sig + entusiaster i fält).
- **AI:** On-device, ingen backend för inference. Migrationsdata + sannolikhet är art-nivå statisk i v1.
- **Solo-utvecklare:** användaren bygger via Claude Code; granskning sker av användaren mellan tasks.

## Frågor + autonomi

- Otydlig task eller spec-motsägelse? **Stoppa och fråga / lyft upp** istället för att gissa.
- Annars: **"Don't ask me for permission to run anything"** — kör commits, push, gradle, file-edits enligt plan utan bekräftelse. Vid scope-creep i review: fixa autonomt (soft-reset + re-commit). Det inkluderar INTE blockerare som kräver fysisk åtkomst (telefon, emulator) eller tredjepartsbeslut (CI-resultat) — där rapporterar man status. Det inkluderar INTE heller att hoppa över granskning — två-stegs-review (spec → kvalitet) körs alltid mellan tasks.

## Plan 4a status (PÅGÅR)

Plan: `docs/superpowers/plans/2026-05-05-v1-04a-camera-ui.md`. Spec: `docs/superpowers/specs/2026-05-05-plan-4a-ml-camera-design.md`. Workflow: `superpowers:subagent-driven-development`. Plan 4b (real TFLite) deferrad.

**Branch + working tree:** `main`, synkad med origin efter Task 9 push (`16dd1e0`). Tasks 1–9 ✅ device-verified på SM-S918B. Working tree clean.

| # | Task | Status | Commit |
|---|---|---|---|
| 1 | shared/ml refactor — `BirdClassifier` interface + `FakeBirdClassifier` | ✅ | `045a40e` |
| 2 | AppGraph wiring + `CameraSource` interface | ✅ | `42a4a0e` (+ `c79d760`) |
| 3 | Nav-routes + ScanScreen-placeholder + default-flik = Skanna | ✅ | `7916386` |
| 4 | Camera permission helper (JIT + ON_RESUME-recheck) | ✅ | `2202907` |
| 5 | `ScanViewModel` + `MutableSharedFlow(DROP_OLDEST)` + auto-throttle + `FakeCameraSource` | ✅ | `71668ee` (impl), `68476cc` (review-fix) |
| 6 | ScanScreen UI variant C — top-chip + crosshair + tap-to-freeze | ✅ | `79c3a3f` (impl), `e63d2fa` (i18n) |
| 7 | AndroidCameraSource — CameraX 3 fps `ImageAnalysis` + `lastJpegBytes()` capture | ✅ | `457ef6d` (impl), `727d4e0` (race-fix), `158af3c` (single-source-fix) |
| 8 | `PhotoAnalyzeViewModel` + screen + Android host (gallerival → klassificera) | ✅ | `d7e7ce0` (impl), `b12e0ad` (review-fix) |
| 9 | `ClassificationResultScreen` + ViewModel (variant A — top-3 + freeze-frame) | ✅ | `b018296` (impl), `16dd1e0` (review-fix) |
| 10 | Polish — theme tokens, cache cleanup, CI green, screenshots, tag `v0.4.0a-ml-camera` | ⬜ | |

**Låst arkitektur (Tasks 1–8 — utförlig version i auto-memory `project_plan_4a_status.md`):**

- `BirdClassifier`/`CameraSource`-interfaces i `shared/ml/commonMain`. `FakeBirdClassifier` är "production of record" i 4a; Plan 4b byter implementation utan att röra UI/VM.
- Frame-pipeline: `MutableSharedFlow(replay=0, extraBufferCapacity=1, DROP_OLDEST)` + `MutableStateFlow<Long>`-period + `flatMapLatest { period -> sink.sample(period) }` ger dynamisk auto-throttle. `Channel(CONFLATED)` fungerade inte — kan inte recolectas över `flatMapLatest`-restarts.
- `onCleared`-cleanup: `viewModelScope` är cancellerad → `GlobalScope.launch(Dispatchers.Default + NonCancellable) { ... }` för fire-and-forget.
- Compose-seam för Android-only Composables: `@Composable expect fun X(...)` + actual i androidMain (`CameraPreviewHost`, `ScanScreenHost`).
- Permission-disambiguering: `ActivityCompat.shouldShowRequestPermissionRationale(activity, ...)` skiljer Denied vs NotAsked. `DisposableEffect` + `LifecycleEventObserver(ON_RESUME)` re-checkar när användaren returnerar från Inställningar.
- i18n-disciplin (`e63d2fa`): `ScanUiState.Error` carryar `ScanErrorKind`-enum, inte `String` — UI-lager mapper till `stringResource`. ViewModel förblir Composable-context-fri.
- **CameraX gate-on-StateFlow (`727d4e0`):** `AndroidCameraSource` håller `ImageAnalysis` i `MutableStateFlow<ImageAnalysis?>(null)`. `frames()`-callbackFlow suspendar på `analysisFlow.filterNotNull().first()` för att fånga use-casen som lokal innan `setAnalyzer(...)` + `awaitClose { local.clearAnalyzer() }`. Eliminerar dispatch-race där `frames().collect` annars vinner mot `start()` (som suspendar i `ProcessCameraProvider.getInstance(...)`'s `ListenableFuture`). Captured-local pattern undviker även symmetric race mot `stop()`.
- **Single-CameraSource-ownership (`158af3c`):** ViewModel äger native-resursen (inte fabriken). `ScanViewModel` exponerar `val cameraSource: CameraSource = cameraSourceFactory()` som public val; `ScanScreenHost` läser `viewModel.cameraSource` istället för att kalla fabriken själv. Symtom när det är fel: PreviewView blir svart eftersom hostens source har surface-providern men ingen camera-binding, medans VM:ens source har kameran men ingen surface. Återanvänd-mönster: när en native-resurs har flera consumers, äger VM:en instansen och consumers läser från VM.
- **Photo-analyze IO-pipeline (`b12e0ad`):** `PhotoAnalyzeHost.android.kt` decode/scale/encode körs i `withContext(Dispatchers.IO)` via en `LaunchedEffect(pendingDecodeUri.value)` som först kallar `viewModel.markAnalyzing()` (spinner före tung IO), sen `decodeAndScale(...)`, sen `viewModel.analyze(...)` eller `viewModel.decodeFailed()`. Bitmap-recycle med identity-guards (`if (it !== raw) raw.recycle()`). `pendingTakeUri` använder `rememberSaveable(uriSaver())` så camera-intent-URI överlever config-change. `runCatching` i VM rethrowar `CancellationException` för korrekt structured-concurrency. Återanvänd när Android-host måste göra tung pre-VM-IO på user-input.

**Task 10 startpunkt:** Läs Task 10-spec i plan-doc (rad 3102). Polish-task med fyra kategorier:
1. **i18n-cleanup** — flytta hardcoded svenska strängar i TopChip/ScanScreen till `strings.xml` (många `result_*`/`photo_*`/`scan_*`-nycklar finns redan).
2. **Theme-token cleanup** — Task 6 — Important #1 (inlined `Color(0xFF...)` i ScanScreen.kt + TopChip.kt + Crosshair.kt → tokens från `ui/theme/Color.kt`).
3. **Cache-cleanup** — `MainActivity.cleanOldCacheFrames()` i `onCreate` rensar `cacheDir/scan-frames` + `cacheDir/photo-input` >1h gamla filer (Task 6 — Minor #5 + Task 8 — Minor #3).
4. **Övriga follow-ups** — se tabellen nedan för Task 6/8/9 minor-items som ska bockas av här.

Avsluta med screenshots (10 st för 4a) + tag `v0.4.0a-ml-camera`.

**Uppskjutna follow-ups (gör i Task 10 polish):**

| Sev | Sak | Var |
|---|---|---|
| Task 6 — Important #1 | Inlined `Color(0xFF...)`-hex → tokens från `ui/theme/Color.kt` | `ScanScreen.kt`, `TopChip.kt`, `Crosshair.kt` |
| Task 6 — Minor #3 | "Analysera ett foto"-knappen kan dubbel-navigera under FrozenAt | `ScanScreen.kt` |
| Task 6 — Minor #5 | `cacheDir/scan-frames` städas aldrig — purge-strategi | `ScanScreenHost.android.kt` |
| Task 8 — Minor #2 | Bytes→ImageInput-pipeline (rotation/scale/encode) untested — kan testas separat om refaktorerad till commonMain | `PhotoAnalyzeHost.android.kt` |
| Task 8 — Minor #3 | `cacheDir/photo-input` städas aldrig — samma purge-strategi som scan-frames | `PhotoAnalyzeHost.android.kt` |
| Task 8 — Minor #4 | `<queries>` saknas i manifest — låg prioritet för API 30+ + PickVisualMedia | `AndroidManifest.xml` |
| Task 8 — Minor #5 | `@Composable expect`-konsistens — ScanScreenHost använder `expect fun X()`, PhotoAnalyzeHost använder `@Composable expect fun X()` | `ScanScreenHost.kt` |
| Task 9 — Minor #2 | `frameJpegPath`-banner är creme-platshållare utan riktig bild — ladda via Coil/AsyncImage | `ClassificationResultScreen.kt` |
| Task 9 — Minor #3 | `Loaded`-Column saknar `verticalScroll` — risk att CTA klipps i landskap / liten skärm | `ClassificationResultScreen.kt` |
| Task 9 — Minor #4 | "kunde inte resolveras" är anglicism — ändra till "kunde inte matchas" + EN-pluralhantering | `values/strings.xml`, `values-en/strings.xml` |
| Task 9 — Minor #9 | `CircularProgressIndicator` på Loading saknar explicit token-färg | `ClassificationResultScreen.kt` |

(Task 6 — Important #2 i18n ✅ stängd i `e63d2fa`. Task 6 — Minor #4 Bitmap-leak ✅ stängd implicit i `457ef6d`.)

## Plan 2b status (PAUSAD)

Runbook (autoritativ källa för per-familj-lärdomar): `docs/superpowers/runbooks/2026-05-02-plan-2b-content-backfill.md`. Auto-memory: `project_plan_2b_status.md`.

| Datum | Familj | Δ | Total | Commit |
|---|---|---|---|---|
| 2026-05-02 | (walking skeleton) | +5 | 5 | `d973e31` |
| 2026-05-04 | paridae | +8 | 13 | `f8cc17f` |
| 2026-05-04 | accipitridae | +38 | 51 | `1ed1895` |
| 2026-05-04 | acrocephalidae | +19 | 70 | `3609b98` |
| 2026-05-04 | alaudidae | +27 | 97 | `d945e1f` |
| _(next)_ | anatidae | | | |

**Återupptas-trigger:** användaren säger "fortsätt Plan 2b" / "kör anatidae". Pure-data — kan köras parallellt med Plan 4a.

**Workflow per familj** (sammanfattat — full version i runbook):
1. Identifiera Q-IDs för familjen i `species_list.yaml`.
2. Sätt `abundance: allmän` på rader för arter som genuint är vanliga i Sverige (default = `ovanlig`).
3. `uv run birdy-fetcher refresh --species Q... --max-cost 0.30`.
4. Öppna `tools/content-pipeline/hero_review/{Q-ID}.html` per allmän-art; godkänn/override hero-pick.
5. Sätt `review_status: approved` + `review_notes` i godkända YAMLs.
6. Bumpa `shared/content/expected-species-count.txt`.
7. `./gradlew :shared:content:validateSpeciesData :shared:content:buildSpeciesDb :composeApp:assembleDebug`.
8. Commit (`data(content): family <name> — N species (M approved, K auto)`) + push.

**Öppna icke-blockerande prereqs:** few-shot-exempel i `description-v1.md` (bara Talgoxe komplett — fyll Koltrast + Blåmes om kvalitet sjunker); Task 8 follow-ups I1/I2/I4/I5 (intressant först om en familj kräver djup pipeline-debug).

## Avslutade planer (referens)

- **Plan 1 (Foundation, `v0.1.0-foundation`):** KMP-bootstrap, Compose, CI, Mossbädd-tema. Plan: `docs/superpowers/plans/` (sök 01-foundation).
- **Plan 2a (Pipeline + walking skeleton, `v0.2.0a-pipeline`):** Python-baserad content-pipeline (Wikidata + Wikipedia + Claude + Commons → YAML → SQLDelight); 5 arter committade som walking skeleton. Plan: `2026-05-02-v1-02a-content-pipeline.md`. Pipeline-mönster: constructor-injection + content-hash i cache-nyckel + atomic-write + mypy strict + ruff (detaljer i `project_plan_2b_status.md`).
- **Plan 3 (Encyclopedia, `v0.3.0-encyclopedia`):** Browse-lista (sök + filter-bottom-sheet) + species-profile (collapsing toolbar + sparse-data-fallbacks + Coil + hero-image-toolbar). Plan: `2026-05-04-v1-03-encyclopedia.md`. Återanvändbara mönster + post-tag bug-fixar i auto-memory `project_plan_3_strategy.md`. 6/7 device-screenshots committade — saknar `profile-sparse` (tas vid nästa device-session).
