# Birdy Bird Scanner — arbetsguide för Claude Code

> **Den här filen läses automatiskt av Claude Code i varje session.** Den ger sammanhang för projektet, var saker ligger, och hur vi arbetar.

## Vad är detta?

AI-driven Android-app för fågelidentifiering. Realtidsskanning via kamera + foto-upload + uppslagsverk över ~700 europeiska arter. Kotlin Multiplatform + Compose Multiplatform. v1 = Android-only ("Skanna & lär"); senare faser lägger till dagbok, gamification, karta, push, community, iOS.

**Status (2026-05-06):** Plan 1 ✅ (`v0.1.0-foundation`). Plan 2a ✅ (`v0.2.0a-pipeline`). Plan 2b ⏸ pausad vid 173/700, nästa familj = ardeidae. Plan 3 ✅ (`v0.3.0-encyclopedia`). Plan 4a ✅ (`v0.4.0a-camera-ui`). Plan 4b deferrad. **Plan 5a (Diary) ✅ (`v0.5.0a-diary`).** Nästa plan är Plan 5b (Gamification) — kommer kräva ny brainstorm/plan. Detaljer i auto-memory `project_plan_5a_status.md`.

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
| 2b | Content backfill family-by-family (5 → ~700 arter) | ⏸ 173/700 (apodidae) |
| 3 | Encyclopedia (browse + species profile) | ✅ `v0.3.0-encyclopedia` |
| 4a | ML & Camera UI (FakeClassifier + UI + CameraX 3 fps) | ✅ `v0.4.0a-camera-ui` |
| 4b | Real TFLite-modell | ⏸ separat brainstorm senare |
| 5a | Diary (browse + detail + save flow) | ✅ `v0.5.0a-diary` |
| 5b | Gamification (badges, streaks) | |
| 6 | Polish + Play Store-release | |

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

## Plan 2b status (PAUSAD)

Runbook (autoritativ källa för per-familj-lärdomar): `docs/superpowers/runbooks/2026-05-02-plan-2b-content-backfill.md`. Auto-memory: `project_plan_2b_status.md`.

| Datum | Familj | Δ | Total | Commit |
|---|---|---|---|---|
| 2026-05-02 | (walking skeleton) | +5 | 5 | `d973e31` |
| 2026-05-04 | paridae | +8 | 13 | `f8cc17f` |
| 2026-05-04 | accipitridae | +38 | 51 | `1ed1895` |
| 2026-05-04 | acrocephalidae | +19 | 70 | `3609b98` |
| 2026-05-04 | alaudidae | +27 | 97 | `d945e1f` |
| 2026-05-06 | anatidae | +52 | 149 | `1a99a63` |
| 2026-05-06 | aegithalidae | +1 | 150 | `acb3249` |
| 2026-05-06 | alcedinidae | +6 | 156 | `e193080` |
| 2026-05-06 | alcidae | +7 | 163 | `d296bdd` |
| 2026-05-06 | anhingidae | +1 | 164 | `0461611` |
| 2026-05-06 | apodidae | +9 | 173 | `89f2ca3` |
| _(next)_ | ardeidae | | | |

**Återupptas-trigger:** användaren säger "fortsätt Plan 2b" / "kör nästa familj". Pure-data — kan köras parallellt med Plan 5+.

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

## Roadmap post-v1.0 (referens)

Tagits in från v1-design-spec så vi inte tappar bort dem. Inget byggs här innan v1.0 är ute.

- **v1.5 — "Karta & moln":** Konton, molnsynk av dagboken, karta med fynd från publika datakällor, push-notiser om sällsynta arter nära användaren. **Plan 5a-koppling:** `Observation`-schemat har nullable kolumner `latitude` / `longitude` / `location_label` från start så v1.5 bara fyller i nya rader (ingen migration behövs).
- **v2 — "Community":** Delning av fynd, kommentarer, flöde, moderering.
- **v2.x:** Quiz/utbildningsläge, ljud-ID, fullt offline-läge för längre exkursioner.

## Avslutade planer (referens)

- **Plan 1 (Foundation, `v0.1.0-foundation`):** KMP-bootstrap, Compose, CI, Mossbädd-tema. Plan: `docs/superpowers/plans/` (sök 01-foundation).
- **Plan 2a (Pipeline + walking skeleton, `v0.2.0a-pipeline`):** Python-baserad content-pipeline (Wikidata + Wikipedia + Claude + Commons → YAML → SQLDelight); 5 arter committade som walking skeleton. Plan: `2026-05-02-v1-02a-content-pipeline.md`. Pipeline-mönster: constructor-injection + content-hash i cache-nyckel + atomic-write + mypy strict + ruff (detaljer i `project_plan_2b_status.md`).
- **Plan 3 (Encyclopedia, `v0.3.0-encyclopedia`):** Browse-lista (sök + filter-bottom-sheet) + species-profile (collapsing toolbar + sparse-data-fallbacks + Coil + hero-image-toolbar). Plan: `2026-05-04-v1-03-encyclopedia.md`. Återanvändbara mönster + post-tag bug-fixar i auto-memory `project_plan_3_strategy.md`. 6/7 device-screenshots committade — saknar `profile-sparse` (tas vid nästa device-session).
- **Plan 5a (Diary, `v0.5.0a-diary`, 2026-05-06):** SQLDelight `Observation`-tabell (med nullable lat/long/location_label för v1.5 cloud-sync) + `SqlDelightObservationRepository` (Flow-baserade queries) + DiaryViewModel/Screen (månadsgrupperad LazyColumn, empty-state CTA) + ObservationDetailViewModel/Screen (collapsing toolbar, edit-note, delete-confirm) + Save-flow från ClassificationResultScreen (snackbar overlay i Box, AccentCopper Save CTA). Plan: `2026-05-05-v1-05a-diary.md`. 7 device-screenshots committade. **Bug-fix-lärdomar från Task 12 device-verify (commit `785dc99`):** (1) `:androidApp` saknar transitiva deps eftersom composeApp använder `implementation()` inte `api()` — varje ny shared/library-referens från `:composeApp` måste få egen `implementation()` i `:androidApp/build.gradle.kts`; (2) compose-resources unescape:ar **inte** Android-style `\'` — använd raw `'` i strings.xml; (3) compose-resources processar **inte** `%%` som `%`-escape — format-strängar med procenttecken måste använda `%1$s` och call-sites passar pre-formatterad `"${value}%"` från Kotlin; (4) `ImageProxy.imageInfo.timestamp` returnerar nanos sedan device-boot, **inte** Unix-epoch — använd `System.currentTimeMillis()` i CameraX-analyzer för wall-clock observation-timestamps. **Process-lärdom:** quality-review:n för Task 11 missade alla fyra eftersom den bara körde `:composeApp:assembleDebug`, inte `:androidApp:installDebug`, och inte testade på fysisk enhet — framtida quality-reviews för Android-screens måste kräva båda.
- **Plan 4a (ML & Camera UI, `v0.4.0a-camera-ui`, 2026-05-06):** FakeBirdClassifier bakom `BirdClassifier`-interface + CameraX 3 fps `ImageAnalysis` + auto-throttle till 1.5 fps (p95 > 333ms) + ScanScreen variant C (top-chip + crosshair + tap-to-freeze) + PhotoAnalyzeScreen (galleri + systemkamera) + ClassificationResultScreen variant A (top-3 + freeze-frame). Plan: `2026-05-05-v1-04a-camera-ui.md`. 9/10 screenshots committade (skip #5 livescan-throttled — FakeClassifier latency p95 stannar under tröskeln). **Återanvändbara mönster (full version i auto-memory `project_plan_4a_status.md`):** `MutableSharedFlow(replay=0, extraBufferCapacity=1, DROP_OLDEST)` + `MutableStateFlow<Long>`-period + `flatMapLatest { period -> sink.sample(period) }` för dynamisk re-sampling (Channel(CONFLATED) fungerade inte över flatMapLatest-restarts); `onCleared`-cleanup via `GlobalScope.launch(Dispatchers.Default + NonCancellable)` eftersom `viewModelScope` är cancellerad; `@Composable expect fun X()` + actual-i-androidMain-mönster för Android-only Composables; `ActivityCompat.shouldShowRequestPermissionRationale` + `LifecycleEventObserver(ON_RESUME)` för permission Denied-vs-NotAsked + Settings-recheck; CameraX gate-on-StateFlow (`AndroidCameraSource` håller `ImageAnalysis` i `MutableStateFlow<ImageAnalysis?>` så `frames()`-callbackFlow kan suspenda på `filterNotNull().first()` istället för att race:a mot `ProcessCameraProvider.getInstance(...)`); single-CameraSource-ownership (VM äger native-resursen, host läser `viewModel.cameraSource`); pre-VM IO-pipeline (`LaunchedEffect(pendingDecodeUri.value) { markAnalyzing(); withContext(IO) { decode } ; analyze() / decodeFailed() }` med `rememberSaveable(uriSaver())` för config-change-survival); CSV nav-arg parsing (parse-and-validate i ViewModel `init`, `runCatching { ... }.onFailure { if (it is CancellationException) throw it }` för structured-concurrency, cap runners-up i VM inte UI). **Post-tag pending follow-ups (Plan 5+ kan adressa när det berör):** Task 6 — Minor #3 "Analysera ett foto"-knapp kan trigga tap-overlay simultant (overlay använder `awaitPointerEvent()` utan `isConsumed`-check — ändra till `detectTapGestures` eller filter `!isConsumed`); Task 8 — Minor #2 Bytes→ImageInput-pipeline untested (refactor till commonMain helper); Task 8 — Minor #4 `<queries>` saknas i manifest (låg prio för API 30+ + PickVisualMedia); Task 8 — Minor #5 `@Composable expect`-konsistens (ScanScreenHost vs PhotoAnalyzeHost); Task 9 — Minor #2 `frameJpegPath`-banner placeholder utan Coil/AsyncImage; Task 9 — Minor #3 `Loaded`-Column saknar `verticalScroll`; Task 9 — Minor #4 "kunde inte resolveras" anglicism + EN-pluralhantering; Task 9 — Minor #9 `CircularProgressIndicator` saknar token-färg; Plan 4a navigation-footgun: `LaunchedEffect(state)` i ScanScreen/PhotoAnalyzeScreen för terminal-state nav — säkrare med `Channel<Effect>`/`SharedFlow<Effect>` om det biter i praktiken.
