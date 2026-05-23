# Birdy Bird Scanner — projektöversikt

> En komplett genomgång av projektets syfte, arkitektur, design, pipelines, felhantering och arbetsflöde. Tänkt som onboarding-dokument för en ny utvecklare (eller en framtida session) som vill förstå helheten utan att läsa alla specs och planer i `docs/superpowers/`.
>
> **Status (2026-05-09):** v1 under utveckling. 8 av 10 planer levererade (Foundation → Real TFLite → Diary → Gamification → Redesign Foundation/Skärmar). Plan 7c (Field Journal) är skriven och redo att exekveras. Plan 6 (Polish + Play Store) pausad till redesign är klar.

---

## 1. Vad är Birdy?

**Birdy Bird Scanner** är en AI-driven Android-app för fågelidentifiering med två syften:

1. **Skanna & identifiera** — peka kameran på en fågel, app:en svarar i realtid med top-3-arter och konfidens. Foto-upload-flöde finns parallellt.
2. **Lära & samla** — uppslagsverk över ~700 europeiska arter, fältdagbok över egna fynd, badges som belönar progression och säsongsbeteende.

**Målgrupp (två-lager):** nybörjare som vill lära sig + entusiaster som vill ha snabb fält-ID. Geografi: Norden/Europa, ~700 arter.

**v1 scope:** "Skanna & lär" + uppslagsverk + dagbok + gamification, Android-only, on-device ML, ingen backend för inferens. iOS-skelett finns men aktiveras först i v2. Konton, molnsynk, karta, push, community kommer i v1.5+.

**Solo-projekt:** byggs av en utvecklare via Claude Code med specs och planer i `docs/superpowers/` som källa till sanning.

---

## 2. Arkitektur

### 2.1 Plattform och stack

| Område | Val |
|---|---|
| **Språk** | Kotlin 2.x |
| **Multiplatform** | Kotlin Multiplatform (KMP) |
| **UI** | Compose Multiplatform (delad mellan Android och framtida iOS) |
| **Databas** | SQLDelight 2.x (Flow-baserade queries) |
| **Settings** | AndroidX DataStore (Preferences) |
| **ML** | TensorFlow Lite (LiteRT) on-device |
| **Kamera** | CameraX (Android), 3 fps streaming |
| **Bilder** | Coil 3.x (Compose Multiplatform) |
| **Lint / static analysis** | ktlint 12.1.2, detekt 1.23.7 |
| **Build** | Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`) |
| **CI** | GitHub Actions (ktlintCheck + detekt + unit tests + assembleDebug + APK-artefakt) |
| **Distribution** | Play Asset Delivery för stora bundles (artdata + bilder) |

### 2.2 Modulkarta

```
birdy-bird-scanner/
├── composeApp/              # Compose Multiplatform UI (delad)
│   └── src/commonMain/kotlin/se/birdy/app/
│       ├── badges/          # Badge unlock-flow + UnlockQueue
│       ├── bootstrap/       # AppGate, BadgeBackfillOnAppStart
│       ├── di/              # AppGraph (manuell DI)
│       ├── photo/           # Foto-pipeline för upload-flöde
│       ├── ui/
│       │   ├── badges/      # Badges-skärm
│       │   ├── components/  # HeroZone, ItalicMixed, delade komponenter
│       │   ├── diary/       # Fältdagbok (lista + detalj)
│       │   ├── encyclopedia/# Browse + species-profile
│       │   ├── listen/      # Listen-launcher (skanna-startskärm)
│       │   ├── onboarding/  # Onboarding-flöde
│       │   ├── photoanalyze/# Galleri/kamera-foto → klassning
│       │   ├── profile/     # Lifelist (artprofil-översikt)
│       │   ├── result/      # ClassificationResultScreen
│       │   ├── scaffold/    # AppShell, navigation, bottom-bar
│       │   ├── scan/        # Live-skanning (CameraX-host)
│       │   ├── settings/    # Inställningar
│       │   └── theme/       # Color.kt, Type.kt, tokens
│       └── usecase/         # SaveObservationUseCase m.fl.
├── shared/
│   ├── domain/              # Use cases, modeller, business rules (ren Kotlin)
│   ├── data/                # SQLDelight, repositories, content providers
│   ├── ml/                  # BirdClassifier expect/actual, preprocessing, AIY-mapping
│   ├── content/             # Artdatabas-loading, badges-katalog, build-time validators
│   └── datastore/           # DataStore-wrappers (KMP) — settings, onboarding-flagga
├── androidApp/              # Android entry point (MainActivity, plattforms-actuals)
├── iosApp/                  # iOS-skelett (aktiveras i v2)
├── tools/
│   ├── content-pipeline/    # Python/uv: birdy-fetcher CLI (Wikidata + Wikipedia + Claude + Commons → YAML)
│   └── ml-eval/             # Python/uv: birdy-eval CLI (TFLite top-1/top-3 mot corpus)
├── docs/
│   └── superpowers/         # specs/, plans/, runbooks/, screenshots/, benchmarks/
├── config/                  # detekt.yml m.fl.
├── buildSrc/                # custom Gradle conventions
└── CLAUDE.md                # arbetsguide för Claude Code-sessioner
```

### 2.3 Beroendeflöde

```
androidApp ──► composeApp ──► shared/domain
                          ├──► shared/data
                          ├──► shared/ml
                          ├──► shared/content
                          └──► shared/datastore
```

`composeApp` använder `implementation()` (inte `api()`) — varje ny shared-referens från `composeApp` måste få egen `implementation()` i `androidApp/build.gradle.kts` (transitiv-deps-fälla från Plan 5a).

### 2.4 Dependency injection

Manuell DI via en `AppGraph`-klass i `composeApp/src/commonMain/kotlin/se/birdy/app/di/`. Konstrueras i `MainActivity` (`runBlocking { buildClassifier() }: Triple<...>`) och passas ner via Composables/`CompositionLocal`. Inget Hilt/Koin — solo-projekt, manuell graf är billigare än reflektions-baserade ramverk.

---

## 3. Visuellt språk — "Mossbädd"

### 3.1 Färgpalett (locked 2026-04-30)

| Token | Hex | Roll |
|---|---|---|
| `Background` | `#E8E2D2` | Pale moss-creme — appens grundbakgrund |
| `HeroTop` | `#5C6E48` | Hero-zon top — ljus mossgrön |
| `HeroDeep` | `#3F4F30` | Hero-zon mid — djup mossa |
| `HeroShadow` | `#2A3520` | Hero-zon bottom — skuggad skog |
| `Accent` | `#8C5A3C` | Koppar — CTA, aktiv flik, stat-siffror |
| `StatSurface` | `#D8D0BC` | Sand-creme — bakgrund för stat-kort |
| `TextPrimary` | `#2A3525` | Djup skog — body-text |
| `TextOnHero` | `#F0EAD8` | Varm offwhite — text mot hero/accent |

Tokens definieras i `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/Color.kt`.

### 3.2 Typografi

| Roll | Font | Tokens |
|---|---|---|
| Rubriker, siffror | **Crimson Pro** (serif) | `Type.kt` |
| UI-text | System sans | `Type.kt` |
| Etiketter | UPPERCASE med spärr | `letterSpacing = 1.5.sp` |

**Plan 7c (Field Journal-redesign, pågående):** introducerar **DM Serif Italic** + **Caveat** (handskriven) som temaspecifik typografi för dagbokssektionen — paper-bg + stamp-collector-metafor + marginalia. Resten av app:en behåller Crimson Pro.

### 3.3 Layoutprinciper

- **Hero är en zon, inte ett kort.** Vertikal gradient `HeroTop → HeroDeep → HeroShadow → Background`. Komponent: `HeroZone` (`composeApp/.../ui/components/`).
- **Koppar-eko:** CTA-knappar i `Accent` ekas i stat-siffror och aktiv flik i bottom-bar.
- **Bottom-bar 72dp** med ikon + textetikett per flik (Listen / Field Journal / Lifelist / Badges / Settings).
- **ItalicMixed:** custom Composable som tillåter blandad italic/upright text utan font-byte (Plan 7a).

### 3.4 Designspecar

- v1-design: `docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md`
- Redesign: `docs/superpowers/specs/2026-05-08-birdy-bird-scanner-redesign-design.md`
- Field Journal-refresh: `docs/superpowers/specs/2026-05-09-field-journal-refresh-design.md`

---

## 4. Pipelines

### 4.1 Content pipeline — `tools/content-pipeline/`

**Syfte:** producera `shared/content/species/**/*.yaml` + `shared/content/images/**/*.jpg` från externa källor, så att SQLDelight-databasen kan byggas vid app-build.

**Teknik:** Python 3.12 + [uv](https://github.com/astral-sh/uv) + Anthropic Claude API.

**Källor:**

| Källa | Roll |
|---|---|
| IOC World Bird List v14.1 (`sources/ioc-14.1.xlsx`) | Auktoritativ artlista |
| BirdLife Sverige TK Västpalearktis-lista v11 (`sources/vp11.pdf`) | Sveriges/Nordens art-status |
| Wikidata (`Q-IDs`) | Strukturerad data: familj, ordning, IUCN-status |
| Wikipedia (sv + en) | Beskrivning, biotop, vana |
| Wikimedia Commons | Hero-bild + galleri (CC-licensierat) |
| Anthropic Claude (`claude-opus-4-x`) | Sammanfattning + kvalitetskontroll av text |

**CLI (`birdy-fetcher`):**

```bash
uv run birdy-fetcher doctor                        # verifiera env + källor
uv run birdy-fetcher init                          # bygg species_list.yaml
uv run birdy-fetcher refresh --species Q25485      # en art end-to-end
uv run birdy-fetcher refresh --all --max-cost 5    # full refresh med kostnadstak
uv run birdy-fetcher refresh --dry-run --species Q25485  # plan only
uv run birdy-fetcher status                        # täckningsrapport
```

**Pipeline-mönster (Plan 2a/2b — locked):**

- **Constructor-injection** för alla externa klienter (testbarhet)
- **Content-hash i cache-nyckel** så cacherade resultat invalideras vid pipeline-bump
- **Atomic-write:** skriv till `*.tmp` + `os.replace` för crash-säker output
- **mypy strict** + **ruff** check + **uv run pytest**
- **Hero-review HTML:** `tools/content-pipeline/hero_review/{Q-ID}.html` genereras per art; manuell godkännande/override innan `review_status: approved` sätts i YAML
- **`max-cost`-cap** i alla `refresh`-kommandon — abortar om Claude-API-kostnaden överstiger taket
- **Family-by-family workflow** för backfill (Plan 2b runbook): identifiera Q-IDs → sätt `abundance` → fetch → review → bumpa `expected-species-count.txt` → commit + push

**Validators (Gradle):**

- `:shared:content:validateSpeciesData` — alla YAML-filer parsbara, schema OK, hero-bild finns
- `:shared:content:validateBadgesYaml` — badges-katalog korrekt
- `:shared:content:validateBadgeStrings` — alla badge-strängar finns i sv+en
- `:shared:content:buildSpeciesDb` — bygg `species.db` från YAMLs

### 4.2 ML pipeline — `tools/ml-eval/` + `shared/ml/`

**Modell:** AIY Birds V1 (MobileNetV2, uint8-quantized, 224×224, 965 klasser → 964 efter att bg-class droppas, ~3.5 MB) bundlad i AAB.

**Mappning Q-ID ↔ AIY-class:** `shared/ml/.../files/ml/aiy_to_qid.json`. Validering:

- Build-time `validateModelMapping`-task (version + coverage + duplikat)
- `:shared:content:validateSpeciesData` säkerställer att alla AIY-mappade Q-IDs finns i artdata

**Runtime-arkitektur (Plan 4b):**

```
ScanScreen / PhotoAnalyzeScreen
    │  ImageInput (bytes + width/height + rotation)
    ▼
ImagePreprocessor (expect/actual; YUV→NV21→Bitmap→224×224 uint8)
    │  ByteBuffer (direct, nativeOrder)
    ▼
TfLiteBirdClassifier  ←── BirdClassifier (interface)
    │  (Mutex-serialiserad, top-3)        ▲
    ▼                                     │
AndroidTfliteRunner               FakeBirdClassifier (DEMO/test)
    │  (Interpreter + roundToInt-quant)   │
    ▼                                     │
AiyLabelMapper (964→Q-ID)         ────────┘
    │
    ▼
Classification(species: List<Pair<Species, Float>>, latencyMs: Long)
```

**Eval CLI (`birdy-eval`):**

```bash
cd tools/ml-eval
uv sync --all-extras
uv run birdy-eval run \
  --model ../../shared/ml/.../files/ml/aiy_birds_v1.tflite \
  --corpus corpus/manifest.yaml \
  --mapping ../../shared/ml/.../files/ml/aiy_to_qid.json \
  --out report.md
```

Corpus byggs deterministiskt round-robin från content-pipelines hero-photos (`build_corpus.py`) — eval beror **inte** på användar-foton.

**Resultat (2026-05-08, S23 Ultra):** top-1 = 52%, top-3 = 72%, ≥70%-spec MET. p95-latens 14–18 ms (talgoxe/koltrast/blames) — långt under 333 ms-throttle.

### 4.3 Camera pipeline — Plan 4a

**Realtidsskanning (3 fps):**

```
CameraX ImageAnalysis
    │  ImageProxy (YUV)
    ▼
AndroidCameraSource
    │  MutableSharedFlow(replay=0, buffer=1, DROP_OLDEST)
    ▼
ScanViewModel
    │  flatMapLatest { period -> sink.sample(period) }   ← dynamisk re-sampling
    ▼  (period: 333ms vid normal, 666ms vid throttle)
ImageInput (bytes + rotation)
    │
    ▼
BirdClassifier.classify(ImageInput) → Classification
    │
    ▼
ScanUiState.Loaded(top3, latencyMs, throttle)
```

**Auto-throttle:** om p95-latens > 333 ms, bytt period till 666 ms (1.5 fps). Backas av om p95 åter går under tröskeln. Implementerat via `MutableStateFlow<Long>` + `flatMapLatest` (Channel(CONFLATED) fungerade inte över flatMapLatest-restarts).

**Confidence threshold:** 0.35 — under det visas ingen Match-skärm (Plan 7d implementerar Match-flow).

**Tap-to-freeze:** tap på preview fryser senaste frame + visar top-3 inline.

**Foto-upload-flöde (`PhotoAnalyzeScreen`):** galleri (PickVisualMedia) eller systemkamera → decode i bakgrund (`Dispatchers.IO` + `rememberSaveable(uriSaver())`) → klassning → `ClassificationResultScreen`.

**Single-CameraSource-ownership:** `ScanViewModel` äger `AndroidCameraSource`, host-Composable läser `viewModel.cameraSource`. CameraX-init sker via `MutableStateFlow<ImageAnalysis?>` så `frames()`-callbackFlow kan suspenda på `filterNotNull().first()` istället för att race:a mot `ProcessCameraProvider.getInstance(...)`.

---

## 5. Datalagring

### 5.1 SQLDelight-tabeller (`shared/data/`)

| Tabell | Syfte | Plan |
|---|---|---|
| `species` | Artdata (namn, familj, beskrivning, biotop, hero-bild) — laddas från `species.db` byggd av content-pipeline | 2a/3 |
| `observation` | Användarens fynd (species_qid, timestamp, confidence, photo_path, note, **lat/long/location_label nullable**) — lat/long förbereder v1.5 cloud-sync utan migration | 5a |
| `badge_unlock` | Vilka badges som låsts upp + när | 5b |

Alla queries är Flow-baserade (`asFlow().mapToList(Dispatchers.IO)`).

### 5.2 DataStore (`shared/datastore/`)

KMP-wrapper runt AndroidX DataStore (Preferences). Innehåller:

- Onboarding-completion-flagga
- Settings (språk, mått-system, etc — Plan 7a)
- Badges-katalog-version (för `BadgeBackfillOnAppStart`)

### 5.3 Filsystem

- **TFLite-modell:** bundlad i AAB via compose-resources (`shared/ml/.../files/ml/aiy_birds_v1.tflite`)
- **Foto från observationer:** Android `filesDir/observations/{uuid}.jpg`
- **Benchmark-rapporter (DEBUG):** `filesDir/benchmark_${ts}.json`

---

## 6. Felhantering

Felhantering är medvetet **lager-vis** — varje lager har en definerad fallback för att app:en aldrig ska krascha eller fastna.

### 6.1 ML — `SessionFailureGuard` (Plan 4b)

```
TfLiteBirdClassifier.classify()
  └─ try
     └─ AndroidTfliteRunner.run() (Mutex-serialiserad)
  └─ catch
     └─ SessionFailureGuard.onFailure()
        ├─ strike < 3 → reinit interpreter, retry next call
        └─ strike == 3 → onDegrade() en gång → fallback till FakeBirdClassifier
```

- **Init-fallback i `BirdClassifierFactory`:** om TFLite-init misslyckas vid app-start, returnera `Pair<FakeBirdClassifier, ClassifierMode.DEMO>` istället för krasch.
- **DEMO-banner** visas i `ScanScreen` om `ClassifierMode.DEMO` (sv/en).
- **Idempotent close:** `try/finally` runt `Interpreter.close()` så reinit aldrig läcker.

### 6.2 Camera — graceful degradation (Plan 4a)

| Scenario | Hantering |
|---|---|
| Permission Denied | `ActivityCompat.shouldShowRequestPermissionRationale` → rationale-dialog; `LifecycleEventObserver(ON_RESUME)` checkar igen om användaren gick till Settings |
| Permission permanent denied | Inline CTA "Öppna inställningar" i ScanScreen |
| Latens p95 > 333 ms | Auto-throttle till 1.5 fps |
| `ImageProxy.imageInfo.timestamp` är nanos-since-boot, inte epoch | Använd `System.currentTimeMillis()` i analyzer (Plan 5a-fix) |
| Decode misslyckas i foto-flöde | `decodeFailed()`-state med retry-CTA |

### 6.3 ViewModel-state — guarded mutations (Plan 5b)

`ClassificationResultViewModel.unlockQueue.queue.collect { list -> ... }` med Loaded-guard så Error/Loading-states inte muteras (collector emittar emptyList vid subscription före `resolve()` hinner sätta Loaded).

### 6.4 Build-time validators

Många "felhantering" är egentligen **build-time validators** som hindrar trasig data från att nå runtime:

- `validateSpeciesData` — alla YAMLs parsbara, schema OK, hero-bild finns
- `validateBadgesYaml` + `validateBadgeStrings` — badge-katalog komplett i sv+en
- `validateModelMapping` — AIY ↔ Q-ID-mappning korrekt
- ktlint + detekt — kodstil + statisk analys
- compose-resources unescape:ar **inte** Android-style `\'` — använd raw `'` i `strings.xml` (Plan 5a-fix)
- compose-resources processar **inte** `%%` → `%` — format-strängar med procenttecken måste använda `%1$s` och pre-formatterad sträng från Kotlin (Plan 5a-fix)

### 6.5 Content pipeline — fail-fast med kostnadstak

- `--max-cost <SEK>` aborterar pipeline om Claude-API-kostnaden överstiger taket
- `--dry-run` visar plan + kostnadsestimat utan att exekvera
- Atomic-write säkerställer att avbruten pipeline inte lämnar halv-skrivna YAMLs
- Hero-review-HTML kräver manuellt `approved`-status innan art läggs till databasen

### 6.6 Observability (in-app)

- DEBUG-only `BenchmarkScreen` (3 photos × 100 iter + 5 warmup → JSON i `filesDir/benchmark_${ts}.json`) gated via `AppGraph.benchmarkScreen` lambda + `AppRoute.DebugBenchmark` + EncyclopediaScreen overflow-meny.
- Inga crash-reporters i v1 — läggs till i Plan 6 (Polish + Play Store-release).

---

## 7. Internationalisering

- **Språk i v1:** svenska (sv) + engelska (en)
- **Format:** alla UI-strängar via compose-resources (`strings.xml` per språk)
- **Geografi:** Sverige-först — abundance + säsong-data optimerade för svensk avifauna
- **Pluralhantering:** EN-pluralisering måste hanteras manuellt (compose-resources stöder inte ICU pluralrules out-of-the-box i v1) — Plan 4a-followup för anglicismer som "kunde inte resolveras"

---

## 8. Build, test, CI

### 8.1 Lokal utveckling (Windows)

```bash
# Standard-prefix för bash + ./gradlew (annars hittar Gradle inte Java)
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"

# Bygg + installera + starta på ansluten enhet
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity

# Snabba unit-tests (delade moduler på JVM)
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest

# Lint + statisk analys
./gradlew ktlintCheck detekt
./gradlew ktlintFormat   # autofix
```

### 8.2 Test-strategi

| Lager | Test-typ | Var |
|---|---|---|
| Domain (use cases, modeller) | Pure JVM unit tests | `shared/domain/src/jvmTest/` |
| Data (repositories, queries) | SQLDelight in-memory | `shared/data/src/jvmTest/` |
| ML (preprocessing, mapping) | JVM unit tests | `shared/ml/src/jvmTest/` |
| Content (validators, badge-evaluator) | JVM unit tests | `shared/content/src/jvmTest/` |
| ComposeApp (ViewModels) | testDebugUnitTest | `composeApp/src/commonTest/` |
| Pipeline (Python) | pytest | `tools/content-pipeline/tests/` |
| ML eval (Python) | pytest | `tools/ml-eval/tests/` |
| Device-verify | Manuell, screenshots committade per milstolpe | `docs/superpowers/screenshots/` |

**Viktigt (Plan 5a-lärdom):** Quality-reviews för Android-skärmar måste köra **både** `:composeApp:assembleDebug` **och** `:androidApp:installDebug` på fysisk enhet — ren module-build täcker inte transitiva-deps + compose-resources-buggar.

### 8.3 CI (GitHub Actions)

På varje push till `main`:
1. ktlintCheck
2. detekt
3. shared unit tests
4. composeApp test
5. assembleDebug
6. APK uploadad som artefakt

### 8.4 Versionering

- **Git-taggar per milstolpe:** `v0.1.0-foundation`, `v0.2.0a-pipeline`, `v0.3.0-encyclopedia`, `v0.4.0a-camera-ui`, `v0.4.0b-real-tflite`, `v0.5.0a-diary`, `v0.5.0b-gamification`, `v0.7.0a-foundation`, `v0.7.0b-screens`.
- **`main` är default branch.** Plan-arbete sker direkt på `main` med små commits per task. Inga long-lived feature branches.
- **Commit-stil:** semantisk (`feat:`, `fix:`, `docs:`, `data(content):`, `chore:`).

---

## 9. Vägkarta

### 9.1 Plan-of-plans (v1)

| # | Plan | Status | Tag |
|---|---|---|---|
| 1 | Foundation — KMP-bootstrap, Compose, CI, Mossbädd-tema | ✅ | `v0.1.0-foundation` |
| 2a | Content pipeline + walking skeleton (5 arter) | ✅ | `v0.2.0a-pipeline` |
| 2b | Content backfill family-by-family (5 → ~700) | ⏸ 254/700 (corvidae nästa) | — |
| 3 | Encyclopedia (browse + species profile) | ✅ | `v0.3.0-encyclopedia` |
| 4a | ML & Camera UI (FakeClassifier + UI + CameraX 3 fps) | ✅ | `v0.4.0a-camera-ui` |
| 4b | Real TFLite-modell (AIY Birds V1) | ✅ | `v0.4.0b-real-tflite` |
| 5a | Diary (browse + detail + save flow) | ✅ | `v0.5.0a-diary` |
| 5b | Gamification (badges, streaks, unlock-queue) | ✅ | `v0.5.0b-gamification` |
| 7a | Redesign Foundation — tokens, ItalicMixed, HeroZone, DataStore, Onboarding, Settings | ✅ | `v0.7.0a-foundation` |
| 7b | Redesign Skärmar — Listen, Archive, Lifelist, Badges, polish | ✅ | `v0.7.0b-screens` |
| 7c | Field Journal — DM Serif Italic + Caveat + paper-bg + stamp-collector | ⏳ skriven, redo att köras | — |
| 7d | Match-flow — threshold-logik, Match-skärm, Disambig | ⏳ skrivs efter 7c | — |
| 6 | Polish + Play Store-release | ⏸ pausad tills Plan 7 klar | — |

Varje plan ska lämna projektet i ett byggbart, testbart tillstånd: `./gradlew build` ska gå grönt.

### 9.2 Post-v1.0 roadmap

| Version | Fokus |
|---|---|
| **v1.5 — "Karta & moln"** | Konton, molnsynk av dagboken, karta med fynd från publika datakällor, push-notiser om sällsynta arter nära användaren. (`Observation`-schemat har redan nullable lat/long/location_label från Plan 5a → ingen migration behövs.) |
| **v2 — "Community"** | Delning av fynd, kommentarer, flöde, moderering. iOS aktiveras. |
| **v2.x** | Quiz/utbildningsläge, ljud-ID, fullt offline-läge för längre exkursioner. |

---

## 10. Arbetsflöde — hur projektet byggs

### 10.1 Solo-dev + Claude Code

Användaren bygger projektet via Claude Code med **specs och planer i Markdown** som källa till sanning. Pattern per faslevels:

```
Brainstorm  →  Spec (.md)  →  Implementation Plan (.md)  →  Subagent-driven execution  →  Review  →  Tag
   (Opus)       docs/specs/      docs/plans/                     (Sonnet workers)            (Opus)
```

### 10.2 Skill-användning

| Uppgift | Modell | Skill |
|---|---|---|
| Brainstorming, ny plan, design | Opus 4.7 | `superpowers:brainstorming` / `:writing-plans` |
| Plan-execution med review | Opus 4.7 (orchestrator) + Sonnet 4.6 (workers) | `superpowers:subagent-driven-development` |
| Code review | Opus 4.7 | `superpowers:requesting-code-review` |
| Vanliga frågor, snabba bugfixar | Opus 4.7 | ingen skill |
| Snabba lookups | Haiku 4.5 | — |

### 10.3 Beslut & autonomi

- **Otydlig task eller spec-motsägelse:** stoppa och fråga, gissa inte.
- **Annars:** "Don't ask me for permission to run anything" — kör commits, push, gradle, file-edits enligt plan utan bekräftelse.
- **Vid scope-creep i review:** fixa autonomt (soft-reset + re-commit).
- **Två-stegs-review** (spec → kvalitet) körs alltid mellan tasks.
- **Inkluderar inte** blockerare som kräver fysisk åtkomst (telefon, emulator) eller tredjepartsbeslut (CI-resultat) — där rapporterar man status.

---

## 11. Var hittar du vad

| Vad | Var |
|---|---|
| Designspec v1 | `docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md` |
| Implementationsplaner | `docs/superpowers/plans/YYYY-MM-DD-v1-NN-<phase>.md` |
| Skärmdumpar per milstolpe | `docs/superpowers/screenshots/` |
| Milstolpe-review-runbook | `docs/superpowers/runbooks/milstolpe-review.md` |
| Plan 2b family-by-family-runbook | `docs/superpowers/runbooks/2026-05-02-plan-2b-content-backfill.md` |
| Tema-tokens | `composeApp/src/commonMain/kotlin/se/birdy/app/ui/theme/` |
| Content-pipeline-källor | `tools/content-pipeline/sources/` (IOC + VP11) |
| TFLite-modell | `shared/ml/src/commonMain/composeResources/files/ml/aiy_birds_v1.tflite` |
| AIY ↔ Q-ID-mappning | `shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json` |
| Badge-katalog | `composeApp/src/commonMain/composeResources/files/badges.yaml` |
| Auto-memory (Claude Code, lokalt) | `~/.claude/projects/C--Users-abbea-dev-birdy-bird-scanner/memory/` |
| Arbetsguide för Claude Code-sessioner | `CLAUDE.md` |

---

## 12. Repo

- **GitHub:** https://github.com/anonadrek/birdy
- **Default branch:** `main`
- **Licens:** TBD (lägg till `LICENSE` innan första publika release).
