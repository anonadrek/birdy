# Birdy Bird Scanner — Plan 4b: Real TFLite-modell Design Spec

**Datum:** 2026-05-07
**Status:** Utkast — väntar på användargranskning
**Spec-typ:** Plan 4b av 6 (split: 4a = UI + FakeClassifier + CameraX ✅ klart; 4b = off-the-shelf riktig modell; 4c = custom finetune, separat brainstorm senare)
**Föregående:** Plan 5b (Gamification) klar — `v0.5.0b-gamification`. Plan 4a klar — `v0.4.0a-camera-ui`. Plan 2b (content backfill) pausad vid 189/700 arter, kan köra parallellt med 4b.
**Författare:** Albin Lindblom + Claude Code (brainstormingsession 2026-05-07)

---

## 1. Bakgrund och syfte

Plan 4 i v1-specen är "ML & Camera". Den splittades i 4a (UI + FakeClassifier + CameraX) och 4b (riktig modell). 4a är klar — `BirdClassifier`-interfacet är låst, alla ViewModels och screens är wired, demoläge funkar med `FakeBirdClassifier` som production-of-record.

Plan 4b's mission: byta ut `FakeBirdClassifier` mot en riktig TFLite-implementation av samma `BirdClassifier`-interface, så att v1.0 kan släppas med "riktig" identifiering — utan att finetuna någonting själva. Vi tar en off-the-shelf-modell (iNat2021 Aves) och accepterar dess accuracy-begränsningar för v1.

Brainstormingen identifierade tre möjliga vägar för 4b: (A) off-the-shelf direkt, (B) custom finetune från start, (C) hybrid — off-the-shelf nu, custom finetune som separat plan när vi vet mer. Vi valde **C**. Plan 4c (custom finetune) väntar tills Plan 2b är klar och vi har komplett 700-arts-vokabulär att finetuna mot. 4b's job är "first real model in users' hands, no training required".

**Plan 4b är klar när:**

- `TfLiteBirdClassifier` ersätter `FakeBirdClassifier` i `AppGraph` på release-builds (debug-builds kan toggla via `BuildConfig.USE_FAKE_CLASSIFIER`).
- Modell-artefakt + label-mapping + metadata bundlade (i AAB om < 80 MB, annars via Play Asset Delivery).
- `tools/ml-eval/` Python-modul finns; senaste `accuracy_report_*.md` visar **top-3 ≥ 70%** på hand-curated 20–30-foton-corpus av svenska arter.
- Android `BenchmarkScreen` (DEBUG-only) finns; senaste `benchmark_*.json` på SM-S918B visar **p95 < 333 ms** för end-to-end `BirdClassifier.classify()`.
- `validateModelMapping` Gradle-task hookad in i `:composeApp:preBuild`; failar om coverage < 50%, warnar 50–90%.
- Cross-walk-pipeline `birdy-fetcher build-mapping` finns och dokumenterad — kan köras efter varje 2b-familj-tillägg.
- Manuell device-verifiering på SM-S918B (5 testfoton i realtime + samma 5 i photo-analyze + benchmark + fallback-flow).
- Screenshots committade till `docs/superpowers/screenshots/plan-4b/`.
- Tag `v0.4.0b-real-tflite` pushad.

Plan 4c (custom finetune) kan starta efter 4b, gärna efter Plan 2b är klar.

---

## 2. Låsta beslut från brainstormingen

| # | Beslut | Motivering |
|---|---|---|
| 1 | **Off-the-shelf-spår, inte finetune.** Custom finetune deferreras till Plan 4c. | Off-the-shelf är dagar; finetune är veckor + GPU-compute. v1 ska kunna släppas utan att vänta på custom-träning. |
| 2 | **iNat2021 Aves som modell-familj.** AIY Birds V1 övervägdes men avfärdades — för stark skew mot Nord/Sydamerika. | iNat har bra europeisk användarbas → bättre coverage på svenska arter. Output-labels är iNat-taxon-IDs (heltal). |
| 3 | **Acceptance-bar = byggd ml-eval-modul + hand-curated demo-set.** Top-3 ≥ 70% bar; per-art + per-familj-rapport; **skip** confusion matrix + held-out-split (det är 4c). | (B+C-hybrid från brainstormingen.) Bygger återanvändbar grund för 4c utan att blow-uppa scope. |
| 4 | **Distribution: decide-per-task.** Task 1–2 = ladda kandidat-varianter, mät storlek + accuracy. Bundla i AAB om < 80 MB, annars Play Asset Delivery (PAD) i Task 3. | Vi vet inte om Mini-varianten räcker accuracy-mässigt; PAD är arbete vi inte vill committa till blint. |
| 5 | **Cross-walk: Wikidata SPARQL P3151 primärt.** Eskalera till hybrid (taxonomy-dump + manuell `overrides.yaml`) om coverage < 90% per `validateModelMapping`-rapport. | content-pipeline har redan SPARQL-infrastruktur från Plan 2; P3151 är välkurerad för fågelarter. Hybrid är extra-arbete vi inte gör om vi slipper. |
| 6 | **ml-eval split: Python för accuracy + Android debug-screen för latency.** | Accuracy är numeriskt deterministiskt över runtime (TFLite-Python = TFLite-Android). Latency MÅSTE vara on-device (333 ms-bar är device-realitet). |
| 7 | **Modell-fallback i prod: TFLite-init failure → `FakeBirdClassifier`.** Crashlytics-event "ml.init.failed" + debug-banner "DEMO" på ScanScreen. | Vi vill inte förlora gamification/diary om TFLite går sönder. Bättre degraderad scan än krasch. |
| 8 | **Repeated inference failure (3 i rad) → degradera till FakeClassifier för sessionen.** | Aggressivt nog att inte loop:a på trasig hardware; konservativt nog att överleva enstaka transient errors. |
| 9 | **`BenchmarkScreen` i `composeApp/androidMain`, gated på `BuildConfig.DEBUG`.** Inte separat debug-flavor. | Enklare. Debug-build är vad utvecklaren använder; release ser inget. |
| 10 | **`inat_to_qid.json` bor i `:shared:ml`** (inte `:shared:content`). | Tightly coupled till modellen, inte språkindependent som species-DB. Versioneras med modell-artefakten. |
| 11 | **`FakeBirdClassifier` kvar i commonMain.** Återanvänds som test-fixture + fallback. Inte deletad. | Test-värde + fallback-värde. |
| 12 | **TFLite test-double = interface-wrap för commonTest + mikro-modell (1-class, ~100 KB) för jvmTest end-to-end.** | Snabba unit-tests utan modell-bagage; en riktig TFLite-validering att Interpreter-koden funkar. iNat2021 i test-resources skulle vara 30+ MB, för dyrt. |
| 13 | **Confidence-tröskel 0.35 ärvs från 4a, men ml-eval rapporterar bästa tröskel.** Om Python-rapporten säger 0.42 ger bättre top-3 ≥ 70% — uppdatera siffran i en separat task. | Off-the-shelf-modeller har olika softmax-fördelning än FakeClassifier; 0.35 är inte sacred. |
| 14 | **Versionering: `model_metadata.json` separat fil**, inte hardcodad i Kotlin. Plan 4c kan byta modell-artefakt utan kodförändring i `:shared:ml`. | Decoupla data från kod. |
| 15 | **Inget tiny smoke-test-modell vid PAD-fail.** Om PAD-download failar och offline → scan-flik visar Error-state, resten av appen funkar. | Två modeller att underhålla = för komplext för v1. |

---

## 3. Arkitektur och moduler

Plan 4b lägger till komponenter i tre orelaterade lager:

1. **Runtime (`:shared:ml` + `:composeApp:androidMain`)** — TFLite Interpreter, preprocessor, label-mapper, fallback-hantering, debug-benchmark-screen.
2. **Bygg-tid (`tools/content-pipeline/` + `:shared:content` Gradle-task)** — SPARQL P3151-fetcher som bygger `inat_to_qid.json`; `validateModelMapping` som checkar konsistens.
3. **Eval (`tools/ml-eval/` Python-modul)** — corpus-loader, TFLite-runner, metrics, Markdown-rapport. Output checkat in i `docs/superpowers/eval/`.

Boundaries:
- `:shared:ml` exponerar fortsatt bara `BirdClassifier`-interface + datatyper. ViewModels och screens i `:composeApp` är **orörda**. AppGraph byter konstruktor-arg `classifier = FakeBirdClassifier()` → `classifier = createBirdClassifier(context)` (factory som väljer Tflite eller fallback).
- iOS-actuals för `TfLiteBirdClassifier` är stubbar som kastar `NotImplementedError` — iOS är Plan 6+.
- Modell-artefakten (`inat2021_aves.tflite`) shippas via compose-resources om bundlad, eller via `com.google.android.play:asset-delivery` om PAD. Filsystem-path-detection-logik abstraheras bakom `ModelArtifactProvider`.

```
:shared:ml
  ├── commonMain/
  │   ├── BirdClassifier.kt              (oförändrad — 4a)
  │   ├── FakeBirdClassifier.kt          (oförändrad — 4a)
  │   ├── TfLiteBirdClassifier.kt        (NY — expect class)
  │   ├── BirdClassifierModelInfo.kt     (NY — data class)
  │   ├── ImagePreprocessor.kt           (NY — expect)
  │   ├── InatLabelMapper.kt             (NY — commonMain)
  │   ├── ModelArtifactProvider.kt       (NY — expect, returns File path)
  │   └── BirdClassifierFactory.kt       (NY — factory + fallback-logic)
  └── androidMain/
      ├── TfLiteBirdClassifier.android.kt   (NY — actual, TFLite-Java)
      ├── ImagePreprocessor.android.kt       (NY — actual, JPEG/YUV/RGBA-konvertering)
      └── ModelArtifactProvider.android.kt   (NY — actual, compose-resources OR PAD)

:shared:ml/src/commonMain/composeResources/files/ml/
  ├── inat2021_aves.tflite               (NY — om bundlad; OR via PAD)
  ├── inat_to_qid.json                   (NY — cross-walk-mapping, alltid bundlad)
  └── model_metadata.json                (NY — version, input-shape, normalization)

:shared:ml/src/jvmTest/resources/test-models/
  └── micro_classifier.tflite            (NY — 1-class fake-modell, ~100 KB, för end-to-end-test)

tools/content-pipeline/birdy_fetcher/
  └── inat_mapping.py                    (NY — SPARQL P3151)
  + utökat CLI: `birdy-fetcher build-mapping`

tools/ml-eval/                           (NY Python-modul, mirror content-pipeline-stilen)
  ├── pyproject.toml                     (uv-managed)
  ├── birdy_eval/
  │   ├── corpus.py
  │   ├── runner.py
  │   ├── metrics.py
  │   └── report.py
  └── corpus/
      ├── manifest.yaml                  (YAML: foto-path → ground_truth Q-ID + family)
      └── *.jpg                          (~20–30 svenska fågelfoton)

:composeApp
  ├── androidMain/.../debug/
  │   ├── BenchmarkScreen.kt             (NY — Compose UI, gated på BuildConfig.DEBUG)
  │   └── BenchmarkRunner.kt             (NY — kör 100 iterationer * 3 foton)
  └── androidMain/assets/benchmark/
      ├── talgoxe.jpg
      ├── koltrast.jpg
      └── blames.jpg

:shared:content/build.gradle.kts          (utökas med validateModelMapping-task)

docs/superpowers/eval/
  └── accuracy_report_2026-05-XX.md      (output från ml-eval)

docs/superpowers/screenshots/plan-4b/
  └── *.png                              (6 device-screenshots)
```

---

## 4. Komponenter

### 4.1 Runtime-komponenter (`:shared:ml`)

**`TfLiteBirdClassifier`** (commonMain expect / androidMain actual)
- Implementerar `BirdClassifier`. Konstruktor tar `Context` + `ModelArtifactProvider`.
- `classify(image: ImageInput): Classification` — full pipe: `bytes → preprocessor → TFLite Interpreter → softmax → top-N + threshold + label-mapping → Classification`.
- `close()` — releasar TFLite Interpreter.
- Internt: `Mutex` runt `Interpreter.run()` (TFLite-Java är inte thread-safe).

**`BirdClassifierFactory`** (commonMain)
- `createBirdClassifier(context, useFakeOverride: Boolean = false): BirdClassifier`
- Försöker `TfLiteBirdClassifier(context)`; om init kastar → loggar Crashlytics + returnerar `FakeBirdClassifier()`.
- Tre-failure-counter på `classify()`-failure (degradera till Fake för session).

**`InatLabelMapper`** (commonMain)
- `lazy { loadFromResource("ml/inat_to_qid.json") }`
- API: `mapToQid(inatTaxonId: Int): String?` — null om missing.
- Immutable `Map<Int, String>` cachad i memory.

**`ImagePreprocessor`** (commonMain expect / androidMain actual)
- Tar `ImageInput` → `FloatBuffer` (224×224×3, normalized).
- Tre kodvägar matchande `FrameFormat`: JPEG (BitmapFactory), YUV_420_888 (CameraX-realtid), RGBA_8888 (direkt-pack).
- Hanterar `rotationDegrees`.
- Normalization-konstanter från `model_metadata.json`.

**`BirdClassifierModelInfo`** (commonMain data class)
- `modelVersion: String`, `inputSize: Int`, `normalizationMean: FloatArray`, `normalizationStd: FloatArray`, `source: String`.

**`ModelArtifactProvider`** (commonMain expect / androidMain actual)
- `getModelFile(): File` (Android) — returnerar path till TFLite-artefakten.
- Android-actual: kollar PAD-paket först (om bundlat som PAD), annars läser från compose-resources till cache och returnerar path.
- Kastar `ModelArtifactNotAvailableException` om PAD-fail (offline first launch).

### 4.2 Bygg-tid-komponenter

**`birdy-fetcher build-mapping`** (`tools/content-pipeline/birdy_fetcher/inat_mapping.py`)
- Subcommand. Läser `species_list.yaml`, kör SPARQL mot Wikidata-property P3151 för Q-IDs i batchar om 50.
- Output: `shared/ml/src/commonMain/composeResources/files/ml/inat_to_qid.json`. Format:
  ```json
  {
    "_meta": {
      "generated_for_model_version": "inat2021_aves_quant_v1",
      "generated_at": "2026-05-07T12:00:00Z",
      "coverage_pct": 87.3,
      "mapped_qids": 165,
      "total_qids": 189
    },
    "mappings": {
      "12345": "Q25485",
      "67890": "Q25404"
    }
  }
  ```
- String-keys i `mappings` (JSON-spec kräver det), parsas till `Int` i Kotlin/Python.
- Loggar coverage-rapport: "165/189 Q-IDs mappade, 24 saknar P3151" — flag:ar coverage-bar.
- Deterministic ordering (sorted by `inat_id` numeriskt) för git-diff-vänlig output.

**`validateModelMapping`** (`:shared:content/build.gradle.kts`, `JavaExec`-task)
- Mönster identiskt med `validateBadgesYaml` / `validateBadgeStrings` från Plan 5b.
- Parsar `inat_to_qid.json`, korsrefererar `mappings.values` mot `species_list.yaml` Q-IDs.
- Failar build om: ogiltig JSON, Q-ID i mapping saknas i species-DB, coverage < 50%, version-mismatch (`_meta.generated_for_model_version` ≠ `model_metadata.json::modelVersion`).
- Warnar coverage 50–90% med action item "kör birdy-fetcher build-mapping igen / eskalera till hybrid cross-walk".
- Hooked i `:composeApp:preBuild`.

### 4.3 Python ml-eval (`tools/ml-eval/birdy_eval/`)

**`corpus.py`** — `load_corpus(path) -> List[CorpusItem]`. Parsar `corpus/manifest.yaml`. Validerar att alla foto-paths finns.

**`runner.py`** — `run_inference(model_path, items, model_metadata) -> List[Prediction]`. TFLite Python runtime (`tensorflow.lite.Interpreter`). Identisk preprocessing som Android-actual (samma normalization-konstanter, samma 224×224-resize-metod) — koden ska kunna copy-paste-jämföras med Kotlin-varianten.

**`metrics.py`** — `compute_metrics(predictions, items) -> MetricsReport`. Top-1, top-3, per-art, per-familj. Confidence-tröskel-svep (0.20–0.60 i steg om 0.05) som rapporterar bästa tröskel för top-3 ≥ 70%.

**`report.py`** — `render_markdown(report) -> str`. Bygger `accuracy_report_<date>.md` checkad in i `docs/superpowers/eval/`.

### 4.4 Android debug-komponenter (`:composeApp/androidMain`)

**`BenchmarkScreen`** (Compose, `if (BuildConfig.DEBUG)` gate)
- Nås från Encyclopedia-flikens overflow-meny. Releases ser ingen menyrad.
- UI: lista över "körningar" + "Run benchmark"-knapp + p50/p95/p99 + device-info.
- Output: `filesDir/benchmark_<timestamp>.json` med model-version, device-id, 300 latency-samples i nanos, percentiler.
- Visar `adb pull`-kommandon copy-paste-vänligt.

**`BenchmarkRunner`** (extra logic)
- Kör samma `BirdClassifier`-instans från AppGraph (production-flow).
- 100 iterationer × 3 demo-foton från `androidMain/assets/benchmark/`.
- Mäter end-to-end (`measureNanoTime { classifier.classify(image) }`) — preprocessor-tid ingår.

---

## 5. Dataflöden

### 5.1 Bygg-tid: cross-walk

```
species_list.yaml (Q-IDs)
        │
        ▼
  birdy-fetcher build-mapping
        │  ─────► SPARQL.wikidata.org (50 Q-IDs/batch, P3151)
        │  ◄───── { Q-ID: inat_taxon_id, ... }
        │
        ▼
  inat_to_qid.json    ← { "_meta": {...}, "mappings": { "inat_id_str": "Q-ID" } }
        │
        ▼
  validateModelMapping  ← Gradle-task, fail om ogiltig / coverage < 50%
        │
        ▼
  shared/ml/.../composeResources/files/ml/inat_to_qid.json   (committat)
```

**Trigger:** manuellt efter varje 2b-familj-tillägg, eller automatiserat senare.

### 5.2 Realtime scan (oförändrad pipe från 4a, ny endpoint)

```
CameraX 30 fps
   │ throttle till 3 fps (4a-existing)
   ▼
Frame (YUV_420_888, ImageProxy)
   │ ImageInput-konvertering
   ▼
TfLiteBirdClassifier.classify(image)
   │  ImagePreprocessor: YUV → RGB → resize → tensor
   │  Mutex.withLock { Interpreter.run(input, output) }
   │  TopK(softmax, 3, threshold=0.35)
   │  InatLabelMapper.mapToQid(inatId)         ◄── null = drop
   │  Classification(results, frameTimestamp)
   ▼
ScanViewModel.classification: StateFlow<UiState>
   ▼
ScanScreen: top-chip
```

`InatLabelMapper`-null droppas inuti `classify` (UI ser bara mappade Q-IDs). Auto-throttle 3→1.5 fps på p95 > 333 ms är 4a-existing.

### 5.3 Photo analyze (oförändrad till 4a)

```
PickVisualMedia → URI → bytes (IO-coroutine, 4a-existing)
   ▼
ImageInput(bytes, format=JPEG)
   ▼
TfLiteBirdClassifier.classify(image)
   │  Same TFLite-call som realtime; ingen throttle.
   ▼
ClassificationResultViewModel → ClassificationResultScreen (variant A, top-3)
```

### 5.4 Accuracy eval (Python)

```
tools/ml-eval/corpus/manifest.yaml + *.jpg
   ▼
birdy-eval run --model shared/ml/.../inat2021_aves.tflite
   │  load_corpus → run_inference → compute_metrics
   ▼
docs/superpowers/eval/accuracy_report_2026-05-XX.md
```

Rapporten är manuellt-läst, inte automatic gate. Plan 4b's "klar"-villkor: senaste rapporten visar top-3 ≥ 70%.

### 5.5 Latency benchmark (Android debug)

```
DEBUG-build → Encyclopedia overflow → "Run benchmark"
   ▼
BenchmarkRunner(birdClassifier from AppGraph)
   │  100 iterationer × 3 demo-foton
   ▼
filesDir/benchmark_<timestamp>.json
   ▼
adb pull → committera under docs/superpowers/eval/benchmarks/
```

**Bar:** p95 < 333 ms på SM-S918B.

---

## 6. Error handling och degraderings-policys

### 6.1 Modell-laddning
- TFLite-fil saknas / korrupt / Interpreter-konstruktor kastar / `inat_to_qid.json` ogiltig → `BirdClassifierInitException`. `BirdClassifierFactory` fångar, loggar Crashlytics-event "ml.init.failed", returnerar `FakeBirdClassifier()`. Debug-builds visar "DEMO"-badge i ScanScreen-top-chip.

### 6.2 Preprocessing
- `BitmapFactory.decodeByteArray` returnerar null / YUV→RGB fail / dim ≤ 0 → `PreprocessingException`. `TfLiteBirdClassifier.classify` fångar, returnerar tom `Classification`. UI hanterar redan tom result-list (4a).

### 6.3 Label-mapping
- iNat-class från modell saknar Q-ID → drop tyst (null från `mapToQid`).
- Q-ID från mapping finns inte i species-DB → `unresolved`-pill (4a-existing).

### 6.4 Inferens-runtime
- `Interpreter.run()` kastar (OOM / hardware-bugg / GPU-delegate-hang) → fångas av Mutex-skyddad körning, tom `Classification` returneras. **Tre på rad** triggar Crashlytics "ml.inference.repeated_failure" + degradera till `FakeClassifier` för sessionen.

### 6.5 PAD-download (om i PAD-spår)
- First-launch utan connectivity → modell saknas → `ModelArtifactNotAvailableException` → `BirdClassifierFactory` fallbackar till Fake.
- Scan-flik visar "Hämtar AI-modell (X MB) — kräver wifi"-state istället för kamera-preview.
- Encyclopedia + Diary funkar normalt.
- Retry-knapp + automatic retry on next app launch.
- **Inget tiny smoke-test-modell-fallback** — för komplext.

### 6.6 Versionsmismatch
- `model_metadata.json::modelVersion` ≠ `inat_to_qid.json::_meta.generated_for_model_version` → `validateModelMapping` failar build. Bygg-tid, inte runtime.

---

## 7. Testning

### 7.1 Unit tests (snabba)

**`:shared:ml/src/commonTest`:**
- `InatLabelMapperTest` — JSON-laddning från test-resource, träffar/missar, dubbletter, malformed-JSON.
- `ImagePreprocessorTest` — pre-bakade input-vägar (JPEG/YUV/RGBA) → förväntade tensor-outputs (float-tolerans). Rotation 90°/180°/270°.
- `TfLiteBirdClassifierTest` — `TfliteRunner`-interface-wrap; test-double returnerar pre-bakade output-tensorer. Top-K + threshold + label-mapping + multi-thread (Mutex) + tom-output + alla-under-threshold.

**`:shared:ml/src/jvmTest`:**
- `TfLiteBirdClassifierIntegrationTest` — riktigt TFLite-anrop med micro-test-modell (1-class, ~100 KB i `jvmTest/resources/test-models/`). Validerar att Interpreter-koden funkar end-to-end.

**`:shared:content/src/test`:**
- `ValidateModelMappingTaskTest` — felaktig JSON (failar), < 50% coverage (failar), 50–90% (warn), ≥ 90% (pass), version-mismatch (failar). Mönster identiskt med `ValidateBadgesYamlTest`.

### 7.2 Python tests (`tools/ml-eval/tests/`, pytest)

- `test_corpus.py` — manifest-loader, missing-file, ogiltig YAML.
- `test_metrics.py` — kända input-output för top-1/top-3, per-art, per-familj. Edge: art med 0 samples, familj med endast misclass.
- `test_runner.py` — mock-Interpreter, validerar preprocessing-konstanter matchar Android-actual.
- `test_report.py` — golden-master Markdown.

### 7.3 Cross-walk pipeline tests

**`tools/content-pipeline/tests/test_inat_mapping.py`:**
- Mock-SPARQL-respons (HTTPX-mock pattern från Plan 2). Happy path, batchning, P3151 saknas, dubbletter. Validerar JSON-output-format + deterministic ordering.

### 7.4 On-device verifiering (manuellt)

1. Realtime scan: rikta mot 5 fågelfoton på datorskärmen (Talgoxe, Koltrast, Blåmes, Gråsparv, Kaja). Top-3 träffar minst 3/5.
2. Photo analyze: välj samma 5 från galleriet. Top-3 + confidence rimliga.
3. Latency benchmark: `BenchmarkScreen` → "Run benchmark" → bekräfta p95 < 333 ms.
4. Auto-throttle: trigga p95 > 333 ms artificiellt → bekräfta period byter 333 → 666 ms.
5. Fallback-flow: rename:a TFLite-fil i app's filesDir → starta om → bekräfta `FakeClassifier`-fallback (debug-banner).
6. `unresolved`-pill: stage en mock som returnerar Q-ID utanför DB → bekräfta pill visas på ResultScreen.

### 7.5 Screenshots committade till `docs/superpowers/screenshots/plan-4b/`

- `01-realtime-talgoxe.png` — realtime, högconfidence-träff
- `02-realtime-soker.png` — realtime, all-under-threshold
- `03-photo-koltrast-top3.png` — photo-analyze top-3
- `04-benchmark-result.png` — BenchmarkScreen efter körning
- `05-fallback-banner.png` — FakeClassifier-fallback
- `06-unresolved-pill.png` — unresolved Q-ID pill

### 7.6 Inte testat (uttryckligen)

- iNat2021-accuracy på sällsynta arter (corpus täcker 5–10 vanliga; sällsynta dokumenteras som "låg coverage")
- Native TFLite C++-crashes (Crashlytics-tracked, inte testat)
- Andra Android-enheter än SM-S918B (Plan 6 kan addera Pixel-emulator)
- BenchmarkScreen Compose-UI har ingen unit-test (debug-only — manuell räcker)

---

## 8. Bygg-tid validering och versionering

### 8.1 Versionerings-policy

`model_metadata.json` (separat fil, inte hardcodad) är source-of-truth för aktuell modell-version. Format:

```json
{
  "modelVersion": "inat2021_aves_quant_v1",
  "inputSize": 224,
  "normalizationMean": [0.485, 0.456, 0.406],
  "normalizationStd": [0.229, 0.224, 0.225],
  "source": "https://tfhub.dev/.../inaturalist-2021-vision/1",
  "downloadedAt": "2026-05-XX",
  "tfliteFileBytes": 47834521
}
```

`inat_to_qid.json::_meta.generated_for_model_version` MÅSTE matcha. Om vi byter modell-artefakt:
1. Ladda ner ny TFLite + uppdatera `model_metadata.json`.
2. Kör `birdy-fetcher build-mapping` igen (modellen kan ha andra iNat-IDs i sin output-vokabulär).
3. `validateModelMapping` verifierar version-match.
4. Kör ml-eval-rapport igen, committera ny `accuracy_report_*.md`.

### 8.2 Validators (`:shared:content` Gradle)

- **Befintliga (Plan 5b):** `validateSpeciesData`, `validateBadgesYaml`, `validateBadgeStrings` — oförändrade.
- **Ny (Plan 4b):** `validateModelMapping` — JSON-schema, Q-ID-konsistens mot species-DB, coverage-rapport, version-matching mot `model_metadata.json`.

Alla körs i `:composeApp:preBuild`.

---

## 9. Risker

| Risk | Sannolikhet | Påverkan | Mitigering |
|---|---|---|---|
| iNat2021 TFLite-artefakt finns inte publik som färdig fil | Låg | Hög | Task 1 i implementationen är "obtain artifact" — om den inte finns, konvertera SavedModel→TFLite ourselves. |
| Modell > 80 MB även efter quantization | Medel | Medel | Eskalera till PAD. Decision-per-task. |
| iNat2021 accuracy < 70% top-3 på svenska arter | Medel | Hög | Plan 4c (custom finetune) är planerad backup. v1.0 kan släppas med lägre bar om vi dokumenterar tydligt. |
| P3151-coverage < 90% | Medel | Medel | Eskalera till hybrid cross-walk (taxonomy-dump). |
| Latens p95 > 333 ms på SM-S918B | Låg-Medel | Hög | Auto-throttle (4a-existing) faller tillbaka till 1.5 fps. Om även 666 ms missar — välj mindre modell-variant. |
| Native TFLite-crashes (delegate-buggar) | Låg | Medel | Fallback-policy + Crashlytics-tracking. |
| PAD-onboarding för komplex för v1 | Medel | Medel | Bundla i AAB om alls möjligt; PAD är fallback, inte default. |
| Cross-walk version-drift (mapping-fil ej uppdaterad efter Plan 2b-tillägg) | Hög | Låg | `validateModelMapping` failar build om Q-ID i mapping inte längre finns; trigger för rebuild. |

---

## 10. Avgränsningar — vad som INTE är 4b

- **Custom finetune** (Plan 4c)
- **Held-out test/train-split** (Plan 4c)
- **Confusion matrix** (Plan 4c)
- **iOS-actuals** för `TfLiteBirdClassifier` (Plan 6+)
- **iNat-eval på sällsynta svenska arter** med stort corpus (Plan 4c kan utöka corpus)
- **Audio-baserad fågel-ID** (BirdNET, etc) — utanför v1-scope
- **A/B-test mellan Mini och Full iNat2021-varianter i prod** (välj en, leverera, iterera senare)

---

## 11. Klar-villkor (sammanfattat)

- [ ] `TfLiteBirdClassifier` ersätter `FakeBirdClassifier` i prod-builds, debug-builds toggla:bar
- [ ] Modell-artefakt + mapping + metadata bundlade (AAB eller PAD)
- [ ] `tools/ml-eval/` Python-modul + senaste `accuracy_report_*.md` visar top-3 ≥ 70%
- [ ] `BenchmarkScreen` (DEBUG) + senaste `benchmark_*.json` på SM-S918B visar p95 < 333 ms
- [ ] `validateModelMapping` Gradle-task hookad i `:composeApp:preBuild`
- [ ] `birdy-fetcher build-mapping` finns och dokumenterad
- [ ] Manuell device-verifiering (5 testfoton i realtime + samma 5 i photo-analyze + benchmark + fallback-flow)
- [ ] 6 screenshots committade
- [ ] `./gradlew build` grön
- [ ] Tag `v0.4.0b-real-tflite` pushad

---

## 12. Öppna frågor

Dessa lyfts till implementationsplanen, inte design-tid:

- **Exakt iNat2021 TFLite-variant** (Mini vs Full, quantization-typ) — Task 1 i planen mäter och bestämmer.
- **TFLite-runtime-bibliotek:** `org.tensorflow:tensorflow-lite-task-vision` (med inbyggd preprocessing) eller `org.tensorflow:tensorflow-lite-support` (mer flexibel) eller plain `org.tensorflow:tensorflow-lite` (manuell preprocessing). Beslut per Task 1.
- **GPU-delegate eller CPU-only?** GPU snabbare men hardware-flaky; CPU robust. Default: CPU. GPU testas i benchmark som follow-up-task.
- **Eager vs lazy modell-init.** Default: eager vid app-launch (undviker first-frame-latency-spike i scan). Verifieras i benchmark.
- **Confidence-tröskel:** börja med 0.35 från 4a; uppdatera om ml-eval rekommenderar annan.
- **Corpus-storlek (20 vs 30 foton):** ta vad vi har av reference-images i `shared/content/images/` + 5–10 egna. Mål: minst 1 foto per familj som finns i 2b's progress.
- **Cross-walk auto-trigger:** ska `birdy-fetcher refresh` automatiskt köra `build-mapping` efter species-tillägg, eller hålls manuellt? Default: manuellt i 4b; automatisera om det blir friction.
