# Birdy Bird Scanner — arbetsguide för Claude Code

> **Den här filen läses automatiskt av Claude Code i varje session.** Den ger sammanhang för projektet, var saker ligger, och hur vi arbetar.

## Vad är detta?

AI-driven Android-app för fågelidentifiering. Realtidsskanning via kamera + foto-upload + uppslagsverk över ~700 europeiska arter. Kotlin Multiplatform + Compose Multiplatform. v1 = Android-only ("Skanna & lär"); senare faser lägger till dagbok, gamification, karta, push, community, iOS.

**Status (2026-05-14):** Plan 1 ✅ (`v0.1.0-foundation`). Plan 2a ✅ (`v0.2.0a-pipeline`). Plan 2b ⏸ pausad vid 273/700, nästa familj = cuculidae. Plan 3 ✅ (`v0.3.0-encyclopedia`). Plan 4a ✅ (`v0.4.0a-camera-ui`). Plan 4b (Real TFLite) ✅ (`v0.4.0b-real-tflite`). Plan 5a (Diary) ✅ (`v0.5.0a-diary`). Plan 5b (Gamification) ✅ (`v0.5.0b-gamification`). Plan 7a–e ✅ (foundation/screens/field-journal/match-flow/premium — alla taggade `v0.7.0a..e`). **Plan 6a (Foundation = UX-polish + release-mekanik) ⏳ IN PROGRESS — 7/15 tasks done (HEAD `3dda784`):** T1 R8/ProGuard, T2 signing config + upload-keystore, T3 icon-koncept (användaren gjorde eget Field Journal-set i `docs/superpowers/icon-concepts/final/`), T4 adaptive launcher + Splash API 31+ + AccentCopper-token retintad `#8C5A3C` → `#A8552D`, T5 cold-start TFLite-flytt → `ClassifierBootstrap` + `AppGate` state-gating + `MainActivity.onDestroy.close()`, T6 Locale-handling: `LocaleResolver` + `months_short_uppercase` string-array + `<plurals>`, device-verifierat EN↔SV på SM-S918B. **T7 Language-picker wireup: `LanguagePickerDialog` (Material3 AlertDialog med Svenska/English/System-radio) + `SettingsViewModel.saveLanguage()` skriver DataStore + emittar `SettingsEffect.RestartForLocale` via Channel(UNLIMITED) → LaunchedEffect-collector i `SettingsScreen` anropar `applyLocale(tag)`. `LocaleApplier.android.kt` använder `LocaleManager.applicationLocales` direkt på API 33+ (AppCompatDelegate.setApplicationLocales är no-op när MainActivity extends ComponentActivity — `sActivityDelegates` är tom så `getLocaleManagerForApplication()` returnerar null; reviewer's I2 var blocking). `AppLocaleApplier.init(applicationContext)` wirad i `MainActivity.onCreate` bredvid Species/PhotoStorage. `AppLanguage → BCP-47 tag` mapping centraliserad i `composeApp/.../i18n/LanguageTag.kt` (`toLocaleTagOrNull()` + `toLocaleTagOrEmpty()`). Device-verified på SM-S918B (API 35): Settings → Språk → English → `cmd locale get-app-locales` returnerar `[en]` + UI flips; tap Svenska → `[sv]` + UI flips till "Inställningar"/"KONTO"/"Namn"/"Språk". Bottom-nav tabs (Listen/Archive/Lifelist/Badges) stannar engelska by design — Field Journal bilingual-konvention (matchar `onboarding_p2_archive_name = "Archive"`).** Nästa = Task 8 (Shared components: JournalLoading/Empty/Dialog/Scaffold). Plan 6b (Billing + Premium-feature-leverans) följer mot `v1.0.0`.

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
| 2b | Content backfill family-by-family (5 → ~700 arter) | ⏸ 273/700 (corvidae) |
| 3 | Encyclopedia (browse + species profile) | ✅ `v0.3.0-encyclopedia` |
| 4a | ML & Camera UI (FakeClassifier + UI + CameraX 3 fps) | ✅ `v0.4.0a-camera-ui` |
| 4b | Real TFLite-modell (AIY Birds V1) | ✅ `v0.4.0b-real-tflite` |
| 5a | Diary (browse + detail + save flow) | ✅ `v0.5.0a-diary` |
| 5b | Gamification (badges, streaks, unlock-queue) | ✅ `v0.5.0b-gamification` |
| 7a | Redesign Foundation — tokens, ItalicMixed, HeroZone, DataStore, Onboarding, Settings, bottom-bar-rename | ✅ `v0.7.0a-foundation` |
| 7b | Redesign Skärmar — Listen-launcher, Archive, Lifelist, Badges, polish | ✅ `v0.7.0b-screens` |
| 7c | Field Journal redesign — DM Serif Italic + Caveat fonttema, paper-bg, stamp-collector, marginalia | ✅ `v0.7.0c-field-journal` |
| 7d | Match-flow — threshold-logik, Match-skärm, Disambig (i Field Journal-stil) | ✅ `v0.7.0d-match-flow` |
| 7e | Premium tier — Premium-skärm, Settings-rewrite, per-tab-markers, cold-start-modal, DataStore-state | ✅ `v0.7.0e-premium` |
| 6a | Foundation — UX-polish + release-mekanik (R8, signing, icon, splash, locale, a11y) | ⏳ 6/15 done, mål `v0.8.0-rc1` |
| 6b | Billing + Premium-feature-leverans (Google Play Billing v6, audio, PDF, stats) | ⏸ efter 6a, mål `v1.0.0` |

**Föreslagen ordning för utförande:** Plan 6a → tag rc1 → Plan 6b → tag v1.0 → Play Store.

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
| Accent | `#A8552D` | Koppar (CTA, aktiv flik, stat-siffror) |
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
| 2026-05-07 | ardeidae | +16 | 189 | `103c76c` |
| 2026-05-07 | bombycillidae | +1 | 190 | `7c29d4f` |
| 2026-05-07 | bucerotidae | +1 | 191 | `356996b` |
| 2026-05-07 | burhinidae | +4 | 195 | `acaedae` |
| 2026-05-08 | calcariidae | +2 | 197 | `29cc339` |
| 2026-05-08 | caprimulgidae | +8 | 205 | `092484d` |
| 2026-05-08 | certhiidae | +2 | 207 | `725cd49` |
| 2026-05-08 | charadriidae | +17 | 224 | `96512e5` |
| 2026-05-08 | cettiidae | +2 | 226 | `24311b1` |
| 2026-05-08 | ciconiidae+cinclidae+cisticolidae+columbidae+coraciidae | +28 | 254 | `6fbd4e7` |
| 2026-05-12 | corvidae | +19 | 273 | `1e5843a` |
| _(next)_ | _cuculidae — kolla species_list.yaml_ | | | |

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
- **Plan 5b (Gamification, `v0.5.0b-gamification`, 2026-05-07):** 25 badges (3 progression + 4 weekly streaks + 3 monthly streaks + 4 season + 8 family + 3 rare) i `composeApp/src/commonMain/composeResources/files/badges.yaml` + i18n via `compose-resources` (sv + en) + `BadgeCatalog` runtime-modell + `BadgeRule` evaluator (`BadgeEvaluator`) + SQLDelight `badge_unlock`-tabell + `BadgeRepositoryImpl` (Flow-queries) + `SaveObservationUseCase.save()` returnerar `SaveResult(observation, newUnlocks)` efter recalc + `UnlockQueue` (FIFO med `pop()`) + `UnlockBottomSheet` (M3 ModalBottomSheet, glow-animation) + `BadgesViewModel`/`BadgesScreen` (hero med `species_seen / total` + `badges_unlocked / total`, "RECENTLY DISCOVERED" carousel, "TO DISCOVER · N LEFT" 5×5-grid) + `BadgeBackfillOnAppStart` (kör recalc över alla observationer om YAML-version > stored). Plan: `2026-05-06-v1-05b-gamification.md`. 6 device-screenshots committade (badges-empty, badges-loaded, locked-detail, save-with-unlock, unlock-bottomsheet, unlocked-detail). **Återanvändbara mönster:** Build-time YAML-validators som `JavaExec`-tasks i `:shared:content` (`validateBadgesYaml` + `validateBadgeStrings`) modellerade efter `validateSpeciesData` — `kaml`-baserad parsing + early-exit på alla samlade fel; `ClassificationResultViewModel.unlockQueue.queue.collect { list -> ... }` med Loaded-guard så Error/Loading-states inte muteras (collector emittar emptyList vid subscription före `resolve()` hinner sätta Loaded); deterministisk device-verify-hack via `File(filesDir, "test_species.txt")` som påverkar `FakeBirdClassifier(cycle = ...)`-init i `MainActivity` (filsystemsignalering är säkrare än timing-baserad ADB-driving för Save × 5 × unika arter); UnlockQueue-serialisering via `MutableStateFlow<List<BadgeUnlock>>` + `pop()` som tar bort head ger naturlig back-to-back bottom-sheet-presentation när ett save triggar flera unlocks (verifierat i device: Novice + Spring birder cyklar correct). **Post-tag pending follow-ups (Plan 6 kan adressa):** streak grown-screenshot kräver tidsmanipulation (skip — kostnad/värde-balans dålig); locked-badge-snackbar overlap med bottom-sheet inte testad i UI (visat i isolation, inte under unlock-flow); BadgesScreen scroll-position lost vid `restoreState`-navigation från Diary tillbaka till Badges (`saveState`-mönster fungerar för andra flikar; BadgesScreen LazyColumn `key = { ... }` redan på plats men test saknas).
- **Plan 4b (Real TFLite, `v0.4.0b-real-tflite`, 2026-05-08):** AIY Birds V1 (965 klasser, MobileNetV2 uint8-quantized, 3.5 MB) bundlad i AAB; `:shared:ml` med `BirdClassifierModelInfoLoader` + `AiyLabelMapper` (964→Q-ID via `aiy_to_qid.json`, bg-class droppad) + `ImagePreprocessor` (expect/actual; YUV→NV21→Bitmap→224×224 uint8) + `TfLiteBirdClassifier` (Mutex-serialiserad, top-3) + `AndroidTfliteRunner` (direct ByteBuffer + nativeOrder + roundToInt-quant + idempotent close); `BirdClassifierFactory` med `SessionFailureGuard` (init-fallback + 3-strikes-degradering till FakeBirdClassifier) + `ClassifierMode { REAL, DEMO }`; `validateModelMapping` Gradle-task (version + coverage + duplikat); `AppGraph(modelVersion, classifierMode, benchmarkScreen?)` wirad från `MainActivity.runBlocking { buildClassifier() }: Triple<...>`; DEMO-banner i `ScanScreen` (sv/en); DEBUG-only `BenchmarkScreen` (3 photos × 100 iter + 5 warmup → JSON i `filesDir/benchmark_${ts}.json`) gated via `AppGraph.benchmarkScreen` lambda + `AppRoute.DebugBenchmark` + EncyclopediaScreen overflow-meny. Python `tools/ml-eval/` (uv-managed, `ai-edge-litert` runtime) + `build_corpus.py` (round-robin över Wikimedia hero-photos från content-pipeline) + `accuracy_report_2026-05-08.md` (top-1=52%, top-3=72%, ≥70%-spec MET, 25 bilder över 13 familjer). Device-verify på SM-S918B (Galaxy S23 Ultra): REAL-läget loadar (`Model: aiy_birds_v1` på Benchmark-skärm), ingen DEMO-banner, ~14 ms per inference (talgoxe/koltrast p95=14ms, blames p95=18ms — långt under 333ms-throttle). 6 device-screenshots + benchmark JSON committade. Plan: `2026-05-07-v1-04b-real-tflite.md`. **Återanvändbara mönster** (auto-memory `project_plan_4b_status.md`): build-time JSON-validators (samma shape som `validateBadgesYaml`); kvantiserad TFLite-wrapping (direct ByteBuffer + nativeOrder + roundToInt + buffers-before-Interpreter); 3-strikes failure-guard (Mutex + onDegrade-once + try/finally close); init-fallback factory (Pair<BirdClassifier, ClassifierMode>); DEBUG-only screen-plumbing via `AppGraph` lambda (undviker expect/actual för one-off debug-skärmar); `MainActivity.runBlocking` för synkron classifier-init; eval-corpus från content-pipeline (deterministisk round-robin, ej beroende av användar-foton). **Plan-doc-trap-katalog** (10+ kända fel — `Classification`-fält, atomicfu, `ScanUiState.Loaded`, `:androidApp` transitiva deps, `Navigation.kt`-path, `appGraph.modelVersion`, `buildConfig=true`-JVM-target-twist, AIY V1 input-dtype uint8, nested `aiy_to_qid.json`-shape, `tensorflow-cpu` Python 3.12-bug). **Post-tag pending follow-ups (Plan 6 kan adressa när det berör):** ersätt 3 placeholder-JPEG i `composeApp/src/androidMain/assets/benchmark/` med riktiga foton från `tools/ml-eval/corpus/` (snabb fix); live-bird scan + classification screenshots i fält (visuell verifikation att riktiga arter klassas korrekt — tekniskt verifierat via Benchmark men aldrig sett en faktisk fågel-klassning på device); `BenchmarkScreen` saknar export-via-Share-Sheet (just nu pulls JSON via `adb run-as`); `ScanScreen` snackbar saknas vid initial DEMO-fallback (banner finns men inget toast som varnar att model-load failade — bara permanent banner).
- **Plan 7e (Premium tier, `v0.7.0e-premium`, 2026-05-12):** `PremiumScreen` (full-width great-tit hero med viewfinder L-bracket-corners + gradient-fade till paper-bg + Caveat-italic close-X i vit cirkel; stacked headline "A *field birder's* / year." på EN och "Hela året som / *fältornitolog.*" på SV via `premium_headline_suffix`-strängen; ornament + sub-line; 4 stamp-bullets — 26dp ring + Caveat-✓ — för audio/PDF/stats/badges; två `TierCard`-rader med radio-button + DM-Serif-Italic-titel + monospace pris-sub + roterad "spara 60%"/"save 60%" pill-stamp på selected; copper "Continue/Fortsätt" CTA + "Cancel anytime/Avbryt när som helst." Caveat-sub) + `PremiumViewModel` med `selectTier` + `purchase` + `PremiumTier.YEARLY/LIFETIME`. `SettingsScreen` (paper-bg + back-arrow + Caveat-rubrik "Settings" + `PremiumHeroCard` 16:9 great-tit-foto-kort med dark gradient + "Become a field member" + "Premium →" pill — bara renderat när `!state.premiumActive`; ACCOUNT-grupp: Name + Language; ABOUT BIRDY-grupp: Rate / Share / Feedback / About / Privacy / Terms) — Language-pickern är placeholder (TODO Plan 6), per-app locale fungerar via `adb shell cmd locale set-app-locales` för screenshot-verifiering. Per-tab PremiumTeasers (`PremiumTeaserCard` i Archive med "Export & back up" + Caveat "→ Unlock"; `LockedStatsPreview` i Lifelist; PremiumTeaserCard i SpeciesProfile; `PremiumBadgesRow` i Badges-bottom med "Field marks · PREMIUM" + 5 ghost stamps + "Unlock 10 field marks →" copper-CTA). `GearButton` i Listen-launcher (top-right circle 56dp) + Badges. `EntryFlowDecider.shouldShowPremiumModal()` cold-start-logik (visa max 1×/dag, ej om premiumActive) wirad i AppScaffold `LaunchedEffect(Unit)`; `userPreferences.premiumModalLastShown` persisterar dato. `✕-dismiss → CaveatToast("Find it in Settings →")` snackbar; `welcome_toast("Welcome, field member.")` post-purchase. `PREMIUM_DEBUG_FORCE_ACTIVE` BuildConfig-flag + `graph.premiumOverride: PremiumState?` (null → använd backendState, else override) appliceras i AppScaffold `effectivePremiumActive`-derivedState. `premium_badges.yaml` placeholder (10 stämplar för Plan 6-content). Plan: `docs/superpowers/plans/2026-05-12-v1-07e-premium-tier.md`. 6 canonical device-screenshots committade på SM-S918B + 5 supplementary (premium-en/sv, settings, listen-with-gear, archive-premium, badges-premium-row + onboarding 1-3, cold-start modal, lifelist-empty). **Återanvändbara mönster:** stacked-headline + suffix-string ger samma `PremiumHeadline`-composable cover båda locale-layouts (EN: plain + accent på rad 1, suffix på rad 2 / SV: plain på rad 1, accent på rad 2 — accent alltid Caveat-italic + rotate -3deg); `BoxScope.CornerBracket(align: Alignment)` med 28dp L-shape kan återanvändas för andra "framing" UIs; `Brush.verticalGradient(0f to Transparent, 0.55f to Transparent, 0.85f to PaperTop.copy(0.55f), 1f to PaperTop)` ger smooth photo-to-bg-fade; debug-only state-override via `graph: AppGraph`-flag (BuildConfig-gating) — undviker prod-only `if (BuildConfig.DEBUG)`-runtime-checks. **Bug-fix-lärdomar:** compose-resources unescape:ar fortfarande inte Android-style `\'` (sett återigen i Plan 5a-lärdom — i `premium_headline_accent`, `premium_species_title`); använd Unicode `’` (U+2019 RIGHT SINGLE QUOTATION MARK) direkt i strings.xml. Hardcoded localized strings (t.ex. `stampLabel = "spara 60%"`) bryter EN-läget — alltid `stringResource(Res.string.premium_tier_yearly_stamp)`. **Process-lärdom:** ADB-tap på upper screen region (y < 300) kan trigga notification-drawer pull-down istället för UI-element — använd `KEYCODE_BACK` för recovery + `uiautomator dump` för exact bounds när screenshots-pipeline körs. Per-app locale-change kräver `adb shell cmd locale set-app-locales se.birdy.android --locales sv-SE` + `am force-stop` + relaunch — Settings-pickern är placeholder. **Post-tag pending follow-ups (Plan 6 kan adressa):** Language-pickern i SettingsScreen.kt är bara en placeholder — implementera via `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(...))` med callback från SettingsViewModel; Settings övriga rader (Rate Birdy / Share the app / Feedback / About / Privacy / Terms) är `TODO: wire row-actions in Plan 6`-stubs; cold-start premium-modal triggrades inte i sista device-session (notification-drawer accident interfererade med LaunchedEffect — verifierat i tidigare session, screenshot `04-premium-modal-cold-start.png`); real billing-integration (Google Play Billing v6) deferred till Plan 6 — just nu är `purchase()` en mock som flippar `PremiumState.Active` lokalt.
- **Plan 7d (Match-flow, `v0.7.0d-match-flow`, 2026-05-12):** Threshold-routing (`<0.35` NoBird / `0.35–0.50` Disambig / `≥0.50` Match) i `MatchResultViewModel.resolve()` + `pickFromDisambig()` (Disambig→Match-mutation) + first-vs-repeat sighting-detection via nya `ObservationRepository.countByQid()` + `firstByQid()`. Tre nya Field Journal-screens i `composeApp/.../ui/match/`: `MatchView` (StampSeal ghost→solid-animation, marginalia "NY ART" / "GÅNG N · första: DATUM" / "ditt val"), `DisambigView` (2-3 candidate-cards, hela kortet klickbart), `NoBirdView` (tilted thumbnail + tre Caveat-italic marginalia-tips + retry-CTA). Gamla `ui/result/`-mappen borttagen — VM/State/Screen migrerade till `ui/match/` med nya namn (`MatchResultViewModel` etc). `AppRoute.ClassificationResult` → `AppRoute.MatchResult`. Plan: `docs/superpowers/plans/2026-05-12-v1-07d-match-flow.md`. 6/8 device-screenshots committade på SM-S918B (Match auto first-sighting, scrolled Save CTA, Match saved-with-unlock, Cancel CTA, force-quit resilience, launcher). **Saknas (kräver test-image-infra för deterministisk trigger):** Disambig 3-cand + Disambig→Match manual pick + NoBird + Match-repeat — real TFLite ger inte 0.35–0.50-band utan riktade test-foton; Plan 5b `test_species.txt`-hacket är inte wirad till confidence-override här. **Återanvändbara mönster:** threshold-routing via `enum class MatchRoute` + `MatchThresholds.routeFor(conf)` (testbar isolerat); pre-resolved sighting-context i UiState (VM gör count + firstObservedAt i `resolve()`; UI hämtar bara från state); sealed UiState där save bara opererar på Match-state (precondition `as? Match`); stamp-animation via `animateFloatAsState` på wrapping Box runt StampSeal (StampSeal exponerar inte alpha-prop i v1). **Bug-fix-lärdom:** `%%` percent-escape-buggen från Plan 5a kom tillbaka i `match_sub_confidence` + `disambig_candidate_confidence` — fix: byt till `%1$s` och passa pre-formatterad `"${pct}%"` från Kotlin-call-site (commit `0c48e17`). **Process-lärdom:** Disambig + NoBird + Match-repeat går **inte** att auto-driva via ADB utan deterministisk classifier-hook — framtida `ui/match`-features bör inkludera en debug-only `MATCH_CONFIDENCE_OVERRIDE`-mekanism (test_species.txt-style) för att kunna ta full screenshot-set på en device-session.
- **Plan 7c (Field Journal redesign, `v0.7.0c-field-journal`, 2026-05-10):** App-bred redesign från Mossbädd-tema till Field Journal-tema. Bundlade fonts (`DM Serif Display Italic` + `Caveat`) via `compose-resources` + `rememberDmSerifDisplay()` / `rememberCaveat()` i `Type.kt`; Field Journal color tokens (`PaperBg #EFE7D6`, `PaperEdge #E5DCC7`, `MarginaliaInk #4A3F2A`, `StampNavy #1F3A5F`, m.fl.) i `Color.kt`; `Modifier.paperBackground()` med dot-texture; nya komponenter i `ui/components/`: `MicroLabel` (uppercase serif eyebrow), `OrnamentRule` (❦ + horisontellt streck), `JournalHeadline` (parsar `*ord*`-syntax → `HeadlineSegment.Plain` / `Accent` med Caveat-italic-rotation), `JournalSubLine` + `JournalIntro`-wrapper, `StampSeal` (locked/in-progress/unlocked-state med glow), `MiniStamp` + `StampTrack` (inline + Badges-hero), `PlateFrame` (naturalist-photo-frame med ornament + stamp-number), `BodyTextWithCaveatAccents` (FlowRow med `*ord*`-stöd för body-text). SQLDelight `species_text(species_id, locale, kind, text)`-tabell + `kind = "marginalia"`-rader för Species Profile. Restylade skärmar: Listen-launcher (paper-bg + JournalIntro + circle-icon-cards), Archive (paper-bg + JournalIntro + Caveat-search + serif-chips), Lifelist (loaded + empty med MiniStamp-rader + serif-stats), Badges (JournalIntro + StampTrack + StampSeal-grid), Species Profile (PlateFrame + drop-cap + marginalia-fält), Observation Detail (paper-bg + JournalIntro + PlateFrame), Onboarding (3 sidor: paper-bg + JournalIntro + Inter-body), ClassificationResult (Match-intro), BottomNavBar (paper-toned bg + AccentCopper-pill för aktiv), AppGate SplashLoading (paper-bg). Cleanup: `HeroZone.kt` borttagen (Plan 7a-artefakt), `StampNumberBadge` uppdaterad till DM Serif Italic. Plan: `2026-05-09-v1-07c-field-journal.md`. 8/9 device-screenshots committade på SM-S918B (saknar `lifelist-empty.png` — tas vid nästa device-session). **Återanvändbara mönster** (auto-memory `project_plan_7c_status.md`): `*word*`-parsing-konvention för accent-segment (delad av JournalHeadline + BodyTextWithCaveatAccents); `paperBackground()` som default `Modifier.fillMaxSize()`-bas på alla skärmar (ersätter MaterialTheme.colorScheme.background); ktlint-regel filename-must-match-class — sealed `interface HeadlineSegment` måste bo i `HeadlineSegment.kt` inte `JournalHeadlineParser.kt` (auto-rename via `git mv` när reglens första bryter mot fil-strukturen från plan-doc). **Lessons från device-verify:** Skip-link tap via ADB är opålitlig på Galaxy S23 Ultra — alternativ swipe-through-onboarding (`input swipe 900 1500 100 1500 250` ×2 + tap CTA); bottom-nav y=2150 (system-gesture-overlap kräver inte y=2200); ADB-kommandon till `screencap` måste forward-slasha sökvägar (`exec-out screencap -p > "C:/path/to.png"`); efter `pm clear` + relaunch, vänta minst 6 s innan screencap (annars riskerar man fånga annan app — privacy-incident i tidigare session).

- **Plan 4a (ML & Camera UI, `v0.4.0a-camera-ui`, 2026-05-06):** FakeBirdClassifier bakom `BirdClassifier`-interface + CameraX 3 fps `ImageAnalysis` + auto-throttle till 1.5 fps (p95 > 333ms) + ScanScreen variant C (top-chip + crosshair + tap-to-freeze) + PhotoAnalyzeScreen (galleri + systemkamera) + ClassificationResultScreen variant A (top-3 + freeze-frame). Plan: `2026-05-05-v1-04a-camera-ui.md`. 9/10 screenshots committade (skip #5 livescan-throttled — FakeClassifier latency p95 stannar under tröskeln). **Återanvändbara mönster (full version i auto-memory `project_plan_4a_status.md`):** `MutableSharedFlow(replay=0, extraBufferCapacity=1, DROP_OLDEST)` + `MutableStateFlow<Long>`-period + `flatMapLatest { period -> sink.sample(period) }` för dynamisk re-sampling (Channel(CONFLATED) fungerade inte över flatMapLatest-restarts); `onCleared`-cleanup via `GlobalScope.launch(Dispatchers.Default + NonCancellable)` eftersom `viewModelScope` är cancellerad; `@Composable expect fun X()` + actual-i-androidMain-mönster för Android-only Composables; `ActivityCompat.shouldShowRequestPermissionRationale` + `LifecycleEventObserver(ON_RESUME)` för permission Denied-vs-NotAsked + Settings-recheck; CameraX gate-on-StateFlow (`AndroidCameraSource` håller `ImageAnalysis` i `MutableStateFlow<ImageAnalysis?>` så `frames()`-callbackFlow kan suspenda på `filterNotNull().first()` istället för att race:a mot `ProcessCameraProvider.getInstance(...)`); single-CameraSource-ownership (VM äger native-resursen, host läser `viewModel.cameraSource`); pre-VM IO-pipeline (`LaunchedEffect(pendingDecodeUri.value) { markAnalyzing(); withContext(IO) { decode } ; analyze() / decodeFailed() }` med `rememberSaveable(uriSaver())` för config-change-survival); CSV nav-arg parsing (parse-and-validate i ViewModel `init`, `runCatching { ... }.onFailure { if (it is CancellationException) throw it }` för structured-concurrency, cap runners-up i VM inte UI). **Post-tag pending follow-ups (Plan 5+ kan adressa när det berör):** Task 6 — Minor #3 "Analysera ett foto"-knapp kan trigga tap-overlay simultant (overlay använder `awaitPointerEvent()` utan `isConsumed`-check — ändra till `detectTapGestures` eller filter `!isConsumed`); Task 8 — Minor #2 Bytes→ImageInput-pipeline untested (refactor till commonMain helper); Task 8 — Minor #4 `<queries>` saknas i manifest (låg prio för API 30+ + PickVisualMedia); Task 8 — Minor #5 `@Composable expect`-konsistens (ScanScreenHost vs PhotoAnalyzeHost); Task 9 — Minor #2 `frameJpegPath`-banner placeholder utan Coil/AsyncImage; Task 9 — Minor #3 `Loaded`-Column saknar `verticalScroll`; Task 9 — Minor #4 "kunde inte resolveras" anglicism + EN-pluralhantering; Task 9 — Minor #9 `CircularProgressIndicator` saknar token-färg; Plan 4a navigation-footgun: `LaunchedEffect(state)` i ScanScreen/PhotoAnalyzeScreen för terminal-state nav — säkrare med `Channel<Effect>`/`SharedFlow<Effect>` om det biter i praktiken.
