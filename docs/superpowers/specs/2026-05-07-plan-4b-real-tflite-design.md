# Birdy Bird Scanner — Plan 4b: Real TFLite-modell Design Spec

**Datum:** 2026-05-07
**Status:** Utkast — pivoterad 2026-05-07 (AIY Birds V1 ersätter iNat2021 efter att Task 2 visade att iNat2021 Aves inte finns publikt distribuerad som off-the-shelf TFLite)
**Spec-typ:** Plan 4b av 6 (split: 4a = UI + FakeClassifier + CameraX ✅ klart; 4b = off-the-shelf riktig modell; 4c = custom finetune, separat brainstorm senare)
**Föregående:** Plan 5b (Gamification) klar — `v0.5.0b-gamification`. Plan 4a klar — `v0.4.0a-camera-ui`. Plan 2b (content backfill) pausad vid 190/700 arter, kan köra parallellt med 4b.
**Författare:** Albin Lindblom + Claude Code (brainstormingsession 2026-05-07)

---

## 1. Bakgrund och syfte

Plan 4 i v1-specen är "ML & Camera". Den splittades i 4a (UI + FakeClassifier + CameraX) och 4b (riktig modell). 4a är klar — `BirdClassifier`-interfacet är låst, alla ViewModels och screens är wired, demoläge funkar med `FakeBirdClassifier` som production-of-record.

Plan 4b's mission: byta ut `FakeBirdClassifier` mot en riktig TFLite-implementation av samma `BirdClassifier`-interface, så att v1.0 kan släppas med "riktig" identifiering — utan att finetuna någonting själva. Vi tar en off-the-shelf-modell (Google AIY Birds V1) och accepterar dess accuracy-begränsningar för v1.

Brainstormingen identifierade tre möjliga vägar för 4b: (A) off-the-shelf direkt, (B) custom finetune från start, (C) hybrid — off-the-shelf nu, custom finetune som separat plan när vi vet mer. Vi valde **C**. Plan 4c (custom finetune) väntar tills Plan 2b är klar och vi har komplett 700-arts-vokabulär att finetuna mot. 4b's job är "first real model in users' hands, no training required".

**Pivot 2026-05-07:** Ursprungsspecen pekade på iNat2021 Aves. Efter Task 1 var klar (generisk SPARQL-plumbing committad som `inat_mapping.py`, fortsatt återanvändbar) visade Task 2:s artefakt-jakt att iNat2021 Aves **inte finns publikt distribuerad** som off-the-shelf TFLite — bara iNaturalist Small (32 fåglar, för smal). Vi pivoterade till **Google AIY Birds V1** (964 arter, 3.5 MB MobileNetV2-quantized, hostat på TFHub). AIY V1 har skew mot Nord-/Sydamerika men täcker Europa rimligt — Plan 4c (custom finetune) kommer ändå städa upp coverage-gap. Decision 2 + 5 + 10 reflekterar pivoten.

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
| 2 | **Google AIY Birds V1 som modell-familj.** ~~iNat2021 Aves~~ pivoterad 2026-05-07 efter att off-the-shelf TFLite visade sig inte finnas publikt. | AIY V1 är publikt hostad (TFHub, 3.5 MB MobileNetV2-quantized), 964 fågelarter med scientific-name labels (binomial Latin). Skew mot Nord-/Sydamerika accepteras för v1; Plan 4c (custom finetune) städar upp coverage-gap. |
| 3 | **Acceptance-bar = byggd ml-eval-modul + hand-curated demo-set.** Top-3 ≥ 70% bar; per-art + per-familj-rapport; **skip** confusion matrix + held-out-split (det är 4c). | (B+C-hybrid från brainstormingen.) Bygger återanvändbar grund för 4c utan att blow-uppa scope. |
| 4 | **Distribution: bundla i AAB.** AIY V1 är 3.5 MB → ryms enkelt under 80 MB-bar; ingen PAD-komplexitet behövs. | iNat2021-pivoten gjorde PAD-frågan moot. AIY V1 i compose-resources/files/ml/. |
| 5 | **Cross-walk: Wikidata SPARQL P225 (taxon name).** Eskalera till manuell `overrides.yaml` om coverage < 90% per `validateModelMapping`-rapport. | AIY V1's labelmap är scientific names (t.ex. "Cyanistes caeruleus"), inte iNat-taxon-IDs eller eBird-koder. P225 är taxon-namnet i Wikidata; SPARQL `?item wdt:P225 "Cyanistes caeruleus"` ger Q-ID direkt. P3151-jobbet i Task 1 är fortsatt värdefullt som generisk SPARQL-plumbing. |
| 6 | **ml-eval split: Python för accuracy + Android debug-screen för latency.** | Accuracy är numeriskt deterministiskt över runtime (TFLite-Python = TFLite-Android). Latency MÅSTE vara on-device (333 ms-bar är device-realitet). |
| 7 | **Modell-fallback i prod: TFLite-init failure → `FakeBirdClassifier`.** Crashlytics-event "ml.init.failed" + debug-banner "DEMO" på ScanScreen. | Vi vill inte förlora gamification/diary om TFLite går sönder. Bättre degraderad scan än krasch. |
| 8 | **Repeated inference failure (3 i rad) → degradera till FakeClassifier för sessionen.** | Aggressivt nog att inte loop:a på trasig hardware; konservativt nog att överleva enstaka transient errors. |
| 9 | **`BenchmarkScreen` i `composeApp/androidMain`, gated på `BuildConfig.DEBUG`.** Inte separat debug-flavor. | Enklare. Debug-build är vad utvecklaren använder; release ser inget. |
| 10 | **`aiy_to_qid.json` bor i `:shared:ml`** (inte `:shared:content`). | Tightly coupled till modellen, inte språkindependent som species-DB. Versioneras med modell-artefakten. |
| 11 | **`FakeBirdClassifier` kvar i commonMain.** Återanvänds som test-fixture + fallback. Inte deletad. | Test-värde + fallback-värde. |
| 12 | **TFLite test-double = interface-wrap för commonTest + mikro-modell (1-class, ~100 KB) för jvmTest end-to-end.** | Snabba unit-tests utan modell-bagage; en riktig TFLite-validering att Interpreter-koden funkar. AIY V1 (3.5 MB) skulle räcka för test-resources men 1-class mikro-modell håller jvmTest snabb. |
| 13 | **Confidence-tröskel 0.35 ärvs från 4a, men ml-eval rapporterar bästa tröskel.** Om Python-rapporten säger 0.42 ger bättre top-3 ≥ 70% — uppdatera siffran i en separat task. | Off-the-shelf-modeller har olika softmax-fördelning än FakeClassifier; 0.35 är inte sacred. |
| 14 | **Versionering: `model_metadata.json` separat fil**, inte hardcodad i Kotlin. Plan 4c kan byta modell-artefakt utan kodförändring i `:shared:ml`. | Decoupla data från kod. |
| 15 | **Inget tiny smoke-test-modell vid PAD-fail.** Om PAD-download failar och offline → scan-flik visar Error-state, resten av appen funkar. | Två modeller att underhålla = för komplext för v1. |

---

## 3. Arkitektur och moduler

Plan 4b lägger till komponenter i tre orelaterade lager:

1. **Runtime (`:shared:ml` + `:composeApp:androidMain`)** — TFLite Interpreter, preprocessor, label-mapper, fallback-hantering, debug-benchmark-screen.
2. **Bygg-tid (`tools/content-pipeline/` + `:shared:content` Gradle-task)** — SPARQL P225-fetcher som bygger `aiy_to_qid.json`; `validateModelMapping` som checkar konsistens. (`inat_mapping.py` från Task 1 finns kvar som generisk SPARQL-plumbing-historik.)
3. **Eval (`tools/ml-eval/` Python-modul)** — corpus-loader, TFLite-runner, metrics, Markdown-rapport. Output checkat in i `docs/superpowers/eval/`.

Boundaries:
- `:shared:ml` exponerar fortsatt bara `BirdClassifier`-interface + datatyper. ViewModels och screens i `:composeApp` är **orörda**. AppGraph byter konstruktor-arg `classifier = FakeBirdClassifier()` → `classifier = createBirdClassifier(context)` (factory som väljer Tflite eller fallback).
- iOS-actuals för `TfLiteBirdClassifier` är stubbar som kastar `NotImplementedError` — iOS är Plan 6+.
- Modell-artefakten (`aiy_birds_v1.tflite`, ~3.5 MB) shippas via compose-resources i AAB. Filsystem-path-detection-logik abstraheras bakom `ModelArtifactProvider`.

```
:shared:ml
  ├── commonMain/
  │   ├── BirdClassifier.kt              (oförändrad — 4a)
  │   ├── FakeBirdClassifier.kt          (oförändrad — 4a)
  │   ├── TfLiteBirdClassifier.kt        (NY — expect class)
  │   ├── BirdClassifierModelInfo.kt     (NY — data class)
  │   ├── ImagePreprocessor.kt           (NY — expect)
  │   ├── AiyLabelMapper.kt              (NY — commonMain)
  │   ├── ModelArtifactProvider.kt       (NY — expect, returns File path)
  │   └── BirdClassifierFactory.kt       (NY — factory + fallback-logic)
  └── androidMain/
      ├── TfLiteBirdClassifier.android.kt   (NY — actual, TFLite-Java)
      ├── ImagePreprocessor.android.kt       (NY — actual, JPEG/YUV/RGBA-konvertering)
      └── ModelArtifactProvider.android.kt   (NY — actual, compose-resources)

:shared:ml/src/commonMain/composeResources/files/ml/
  ├── aiy_birds_v1.tflite                (NY — bundlad, ~3.5 MB)
  ├── aiy_to_qid.json                    (NY — cross-walk-mapping, alltid bundlad)
  └── model_metadata.json                (NY — version, input-shape, normalization)

:shared:ml/src/jvmTest/resources/test-models/
  └── micro_classifier.tflite            (NY — 1-class fake-modell, ~100 KB, för end-to-end-test)

tools/content-pipeline/birdy_fetcher/
  ├── inat_mapping.py                    (Task 1 ✅ — generisk SPARQL P3151-plumbing, behålls som referens)
  └── name_mapping.py                    (NY — Task 1b — SPARQL P225 för scientific-name → Q-ID)
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

**`AiyLabelMapper`** (commonMain)
- `lazy { loadFromResource("ml/aiy_to_qid.json") }`
- API: `mapToQid(classIndex: Int): String?` — null om missing eller om classIndex == 964 (background-class).
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
- Android-actual: läser från compose-resources till cache och returnerar path. (PAD-grenen är inte aktuell efter pivot — AIY V1 är 3.5 MB.)
- Kastar `ModelArtifactNotAvailableException` om resource-load fail (defensivt — should never happen i bundlad AAB).

### 4.2 Bygg-tid-komponenter

**`birdy-fetcher build-mapping`** (`tools/content-pipeline/birdy_fetcher/name_mapping.py`)
- Subcommand. Läser AIY V1's labelmap CSV (`labelmap.csv` med kolumner `id,name`), kör SPARQL mot Wikidata-property P225 för scientific names i batchar om 200.
- Output: `shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json`. Format:
  ```json
  {
    "_meta": {
      "generated_for_model_version": "aiy_birds_v1",
      "generated_at": "2026-05-07T12:00:00Z",
      "coverage_pct": 78.5,
      "mapped_classes": 757,
      "total_classes": 964
    },
    "mappings": {
      "0": "Q1226346",
      "1": "Q913049"
    }
  }
  ```
- String-keys i `mappings` (JSON-spec kräver det), parsas till `Int` i Kotlin/Python.
- Class-index 964 (background) skippas tyst — det är inte en art.
- Loggar coverage-rapport: "757/964 scientific names mappade, 207 saknar Wikidata P225-match" — flag:ar coverage-bar.
- Deterministic ordering (sorted by `class_index` numeriskt) för git-diff-vänlig output.
- Återanvänder generisk SPARQL-plumbing från `inat_mapping.py` (Task 1): `chunked`, `USER_AGENT`, `MappingResult`, `render_mapping_json`, `_default_run_sparql`.

**`validateModelMapping`** (`:shared:content/build.gradle.kts`, `JavaExec`-task)
- Mönster identiskt med `validateBadgesYaml` / `validateBadgeStrings` från Plan 5b.
- Parsar `aiy_to_qid.json`, korsrefererar `mappings.values` mot `species_list.yaml` Q-IDs.
- Failar build om: ogiltig JSON, coverage < 50% (mätt mot AIY V1's 964-class vokabulär), version-mismatch (`_meta.generated_for_model_version` ≠ `model_metadata.json::modelVersion`).
- Warnar coverage 50–90% med action item "kör birdy-fetcher build-mapping igen / eskalera till manuell overrides.yaml".
- **Tolererar** Q-IDs i mapping som inte finns i species-DB — det är förväntat eftersom AIY V1 har 964 arter och vår species-DB växer från 5 → 700. Hooks 4a's `unresolved`-pill om model träffar en Q-ID utanför DB.
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
AIY V1 labelmap.csv (id,scientific_name) — 964 rows
        │
        ▼
  birdy-fetcher build-mapping
        │  ─────► SPARQL.wikidata.org (200 names/batch, P225)
        │  ◄───── { scientific_name: Q-ID, ... }
        │
        ▼
  aiy_to_qid.json    ← { "_meta": {...}, "mappings": { "class_idx_str": "Q-ID" } }
        │
        ▼
  validateModelMapping  ← Gradle-task, fail om ogiltig / coverage < 50%
        │
        ▼
  shared/ml/.../composeResources/files/ml/aiy_to_qid.json   (committat)
```

**Trigger:** en gång per modell-version. AIY V1 är frusen → cross-walk är frusen tills vi byter modell.

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
   │  AiyLabelMapper.mapToQid(classIndex)      ◄── null = drop (inkl. background-class 964)
   │  Classification(results, frameTimestamp)
   ▼
ScanViewModel.classification: StateFlow<UiState>
   ▼
ScanScreen: top-chip
```

`AiyLabelMapper`-null droppas inuti `classify` (UI ser bara mappade Q-IDs). Auto-throttle 3→1.5 fps på p95 > 333 ms är 4a-existing.

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
birdy-eval run --model shared/ml/.../aiy_birds_v1.tflite
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
- TFLite-fil saknas / korrupt / Interpreter-konstruktor kastar / `aiy_to_qid.json` ogiltig → `BirdClassifierInitException`. `BirdClassifierFactory` fångar, loggar Crashlytics-event "ml.init.failed", returnerar `FakeBirdClassifier()`. Debug-builds visar "DEMO"-badge i ScanScreen-top-chip.

### 6.2 Preprocessing
- `BitmapFactory.decodeByteArray` returnerar null / YUV→RGB fail / dim ≤ 0 → `PreprocessingException`. `TfLiteBirdClassifier.classify` fångar, returnerar tom `Classification`. UI hanterar redan tom result-list (4a).

### 6.3 Label-mapping
- AIY-class från modell saknar Q-ID i mapping → drop tyst (null från `mapToQid`).
- Class-index 964 (background) → drop tyst (special-cased i `mapToQid`).
- Q-ID från mapping finns inte i species-DB → `unresolved`-pill (4a-existing). Förväntat ofta för AIY V1's icke-europeiska arter.

### 6.4 Inferens-runtime
- `Interpreter.run()` kastar (OOM / hardware-bugg / GPU-delegate-hang) → fångas av Mutex-skyddad körning, tom `Classification` returneras. **Tre på rad** triggar Crashlytics "ml.inference.repeated_failure" + degradera till `FakeClassifier` för sessionen.

### 6.5 PAD-download
Inte aktuellt efter pivot — AIY V1 är 3.5 MB och bundlas i AAB. Sektionen kvar som referens om Plan 4c kräver större finetune-modell.

### 6.6 Versionsmismatch
- `model_metadata.json::modelVersion` ≠ `aiy_to_qid.json::_meta.generated_for_model_version` → `validateModelMapping` failar build. Bygg-tid, inte runtime.

---

## 7. Testning

### 7.1 Unit tests (snabba)

**`:shared:ml/src/commonTest`:**
- `AiyLabelMapperTest` — JSON-laddning från test-resource, träffar/missar, dubbletter, malformed-JSON, background-class (964) drop.
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

**`tools/content-pipeline/tests/test_inat_mapping.py`** (Task 1, ✅):
- Mock-SPARQL-respons. Happy path, batchning, P3151 saknas, dubbletter (within-batch + cross-batch). Validerar JSON-output-format + deterministic ordering.

**`tools/content-pipeline/tests/test_name_mapping.py`** (Task 1b, NY):
- Mock-SPARQL-respons. Happy path, batchning, P225-namn saknas, dubbletter, scientific-name-escaping (apostrof, bindestreck), background-class skip. Validerar `aiy_to_qid.json`-output-format.

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

- AIY V1-accuracy på sällsynta arter (corpus täcker 5–10 vanliga; sällsynta dokumenteras som "låg coverage")
- Native TFLite C++-crashes (Crashlytics-tracked, inte testat)
- Andra Android-enheter än SM-S918B (Plan 6 kan addera Pixel-emulator)
- BenchmarkScreen Compose-UI har ingen unit-test (debug-only — manuell räcker)

---

## 8. Bygg-tid validering och versionering

### 8.1 Versionerings-policy

`model_metadata.json` (separat fil, inte hardcodad) är source-of-truth för aktuell modell-version. Format:

```json
{
  "modelVersion": "aiy_birds_v1",
  "inputSize": 224,
  "normalizationMean": [0.0, 0.0, 0.0],
  "normalizationStd": [255.0, 255.0, 255.0],
  "source": "https://tfhub.dev/google/lite-model/aiy/vision/classifier/birds_V1/3",
  "downloadedAt": "2026-05-XX",
  "tfliteFileBytes": 3492096,
  "outputClasses": 965,
  "backgroundClassIndex": 964
}
```

(Normalization-konstanter ovan är AIY V1-default `[0,255]→[0,1]`. Verifieras i Task 2 via TFLite metadata-extractor.)

`aiy_to_qid.json::_meta.generated_for_model_version` MÅSTE matcha. Om vi byter modell-artefakt:
1. Ladda ner ny TFLite + uppdatera `model_metadata.json`.
2. Kör `birdy-fetcher build-mapping` igen (ny labelmap = ny vokabulär).
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
| ~~iNat2021 TFLite-artefakt finns inte publik~~ | — | — | **Realiserades 2026-05-07.** Pivoterade till AIY V1. |
| AIY V1 accuracy < 70% top-3 på svenska arter (Nord-/Sydamerika-skew) | Medel-Hög | Hög | Plan 4c (custom finetune) är planerad backup. v1.0 kan släppas med lägre bar om vi dokumenterar tydligt. ml-eval-rapport flaggar gap. |
| AIY V1 background-class (964) får hög softmax på vanliga svenska scener (skog, träd) | Medel | Medel | `AiyLabelMapper` droppar 964 tyst; threshold 0.35 hjälper; ml-eval mäter false-background-rate. |
| P225-coverage < 90% (AIY scientific names matchar inte Wikidata canonical names) | Medel | Medel | Eskalera till manuell `overrides.yaml`. Synonyms (P1843?) som fallback. |
| Latens p95 > 333 ms på SM-S918B | Låg | Hög | AIY V1 är MobileNetV2-quantized (3.5 MB) — historiskt < 100 ms på modern hårdvara. Auto-throttle (4a-existing) som backup. |
| Native TFLite-crashes (delegate-buggar) | Låg | Medel | Fallback-policy + Crashlytics-tracking. |
| Cross-walk version-drift | Låg | Låg | AIY V1 är frusen. Mapping byggs en gång, validateModelMapping verifierar version-match. |

---

## 10. Avgränsningar — vad som INTE är 4b

- **Custom finetune** (Plan 4c)
- **Held-out test/train-split** (Plan 4c)
- **Confusion matrix** (Plan 4c)
- **iOS-actuals** för `TfLiteBirdClassifier` (Plan 6+)
- **AIY V1-eval på sällsynta svenska arter** med stort corpus (Plan 4c kan utöka corpus)
- **Audio-baserad fågel-ID** (BirdNET, etc) — utanför v1-scope
- **A/B-test mellan modell-varianter i prod** (AIY V1 är levererat, iN2021/finetune kommer i 4c)
- **Play Asset Delivery** — moot efter pivot (AIY V1 = 3.5 MB)

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

- **AIY V1 normalization:** verifiera i Task 2 om det är `[0,1]` (default) eller `[-1,1]` via TFLite metadata-extractor. Påverkar `model_metadata.json::normalizationMean/Std`.
- **TFLite-runtime-bibliotek:** `org.tensorflow:tensorflow-lite-task-vision` (med inbyggd preprocessing) eller `org.tensorflow:tensorflow-lite-support` (mer flexibel) eller plain `org.tensorflow:tensorflow-lite` (manuell preprocessing). Beslut per Task 4.
- **GPU-delegate eller CPU-only?** GPU snabbare men hardware-flaky; CPU robust. Default: CPU. GPU testas i benchmark som follow-up-task.
- **Eager vs lazy modell-init.** Default: eager vid app-launch (undviker first-frame-latency-spike i scan). Verifieras i benchmark.
- **Confidence-tröskel:** börja med 0.35 från 4a; uppdatera om ml-eval rekommenderar annan. AIY V1's softmax kan se annorlunda ut än FakeClassifier.
- **Corpus-storlek (20 vs 30 foton):** ta vad vi har av reference-images i `shared/content/images/` + 5–10 egna. Mål: minst 1 foto per familj som finns i 2b's progress.
- **Background-class threshold:** ska vi droppa class 964 hårt eller bara dimma confidence? Default: hård drop i `mapToQid`; beteende verifieras i ml-eval.
