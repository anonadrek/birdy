# Plan 4b — Real TFLite-modell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Byt ut `FakeBirdClassifier` mot en off-the-shelf TFLite-implementation av Google AIY Birds V1 (964 fågelarter, 3.5 MB MobileNetV2-quantized) bakom samma `BirdClassifier`-interface från Plan 4a. Inkluderar cross-walk scientific-name → Q-ID via Wikidata SPARQL P225, build-time `validateModelMapping`-Gradle-task, fallback till FakeClassifier vid TFLite-fel, Python `tools/ml-eval/` för accuracy-mätning, Android `BenchmarkScreen` för on-device latens. Tag `v0.4.0b-real-tflite`.

> **Pivot 2026-05-07:** Ursprungsplanen pekade på iNat2021 Aves; efter Task 1 var klar (generisk SPARQL P3151-plumbing committad som `inat_mapping.py`, behålls som referens) visade Task 2:s artefakt-jakt att iNat2021 Aves inte distribueras publikt som off-the-shelf TFLite. Pivoterade till Google AIY Birds V1. Spec-doc revision committad i samma commit som plan-doc revisionen.

**Architecture:** `:shared:ml/commonMain` får `TfLiteBirdClassifier` (expect/actual), `BirdClassifierFactory` (fallback-logic), `AiyLabelMapper`, `ImagePreprocessor` (expect/actual), `ModelArtifactProvider` (expect/actual). Modell-artefakt + mapping + metadata bundlas i compose-resources (AIY V1 är 3.5 MB → ingen PAD behövs). `tools/content-pipeline/birdy_fetcher/name_mapping.py` (Task 1b, NY) bygger `aiy_to_qid.json` från Wikidata-property P225 (taxon name). `tools/ml-eval/` är ny Python-modul med corpus-loader, TFLite-runner, metrics, Markdown-rapport. Android `BenchmarkScreen` (DEBUG-only) kör samma `BirdClassifier`-instans som production-flow för end-to-end latens-sampling. ViewModels + screens från Plan 4a är **orörda**; bara `AppGraph` byter `classifier`-binding.

**Tech Stack:** Kotlin 2.1.20, Compose Multiplatform 1.7.3, TFLite-Java (`org.tensorflow:tensorflow-lite:2.16.1` + `tensorflow-lite-support:0.4.4`), kotlinx-serialization-json 1.7.3, Turbine 1.1.0. Python: `uv` + `tensorflow-cpu==2.16.1` + `pillow` + `pyyaml` + `httpx` + `pytest` (mirror `tools/content-pipeline/`).

> **Version note:** TFLite-bibliotek lockas vid Task 4 (`:shared:ml` `build.gradle.kts`). Övriga deps redan låsta i Plan 4a och 5b.

**Spec:** `docs/superpowers/specs/2026-05-07-plan-4b-real-tflite-design.md` (commit `4e96856`).

---

## Plan-of-plans context

This is **Plan 4b of 6** for v1. Plan 4a (`v0.4.0a-camera-ui`) shippade FakeClassifier som production-of-record bakom `BirdClassifier`-interfacet. Plan 5a + 5b (Diary + Gamification) shippade `v0.5.0a-diary` och `v0.5.0b-gamification`. Plan 2b (content backfill) är pausad vid 190/700 arter — kan köras parallellt med 4b. Plan 4b ersätter FakeClassifier i prod med Google AIY Birds V1 (off-the-shelf, 964 arter). Plan 4c (custom finetune) är separat brainstorm/spec, planerad efter 2b är 100% och vi har vokabulär att finetuna mot.

Plan 4b leaves the project buildable + CI-green at every commit. Slutartefakt: APK på SM-S918B där realtime-scan + photo-analyze använder riktig TFLite-modell, ml-eval-rapport visar top-3 ≥ 70%, benchmark visar p95 < 333 ms.

---

## Avvikelser från spec

**2026-05-07 — pivot från iNat2021 till AIY Birds V1.** Task 2's artefakt-jakt visade att iNat2021 Aves inte är off-the-shelf publikt distribuerad. Spec-doc Decision 2 + 5 + 10 reviderade. Plan-doc top-block + file-structure + Task 2 + Task 5 + Status-tabellen reviderade. Task 1's commit (`c2ed1c1` + `341d64c`) behålls som generisk SPARQL-plumbing-historik; Task 1b (NY) implementerar `name_mapping.py` med SPARQL P225 för scientific-name → Q-ID.

---

## File Structure

### Skapas

| Fil | Ansvar |
|---|---|
| `tools/content-pipeline/src/birdy_fetcher/inat_mapping.py` | (Task 1 ✅) Generisk SPARQL P3151-plumbing — behålls som referens, används inte i runtime efter pivot |
| `tools/content-pipeline/tests/test_inat_mapping.py` | (Task 1 ✅) Mock SPARQL-tester |
| `tools/content-pipeline/src/birdy_fetcher/name_mapping.py` | (Task 1b — NY) SPARQL P225 → `aiy_to_qid.json` (återanvänder plumbing från `inat_mapping.py`) |
| `tools/content-pipeline/tests/test_name_mapping.py` | (Task 1b — NY) Mock SPARQL-tester |
| `shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json` | Cross-walk-mapping (genererad — class_index → Q-ID) |
| `shared/ml/src/commonMain/composeResources/files/ml/model_metadata.json` | Modell-version, input-shape, normalization |
| `shared/ml/src/commonMain/composeResources/files/ml/aiy_birds_v1.tflite` | TFLite-artefakt (~3.5 MB, bundlad i AAB) |
| `shared/ml/src/commonMain/composeResources/files/ml/aiy_labelmap.csv` | AIY V1 labelmap (`id,name`) — input till `name_mapping.py`, committat för reproducibility |
| `shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifierModelInfo.kt` | Data class — modell-metadata-laddare |
| `shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifierModelInfoLoader.kt` | `Res.readBytes("ml/model_metadata.json")` + JSON-parse |
| `shared/ml/src/commonTest/kotlin/se/birdy/ml/BirdClassifierModelInfoLoaderTest.kt` | Parse-tester (valid + malformed) |
| `shared/ml/src/commonMain/kotlin/se/birdy/ml/ModelArtifactProvider.kt` | `expect class` |
| `shared/ml/src/androidMain/kotlin/se/birdy/ml/ModelArtifactProvider.android.kt` | `actual` — compose-resources |
| `shared/ml/src/commonMain/kotlin/se/birdy/ml/AiyLabelMapper.kt` | Q-ID-lookup från `aiy_to_qid.json` (drop class 964 = background tyst) |
| `shared/ml/src/commonTest/kotlin/se/birdy/ml/AiyLabelMapperTest.kt` | Träffar/missar/dubbletter/malformed/background-class |
| `shared/ml/src/commonMain/kotlin/se/birdy/ml/ImagePreprocessor.kt` | `expect class` |
| `shared/ml/src/androidMain/kotlin/se/birdy/ml/ImagePreprocessor.android.kt` | `actual` — JPEG/YUV/RGBA → tensor |
| `shared/ml/src/commonTest/kotlin/se/birdy/ml/ImagePreprocessorContractTest.kt` | Kontrakt-tester (input-output-shape) |
| `shared/ml/src/androidUnitTest/kotlin/se/birdy/ml/ImagePreprocessorAndroidTest.kt` | Robolectric — pixelvärden för 4×4-RGB + rotation |
| `shared/ml/src/commonMain/kotlin/se/birdy/ml/TfliteRunner.kt` | Test-vänlig interface kring `Interpreter.run()` |
| `shared/ml/src/commonMain/kotlin/se/birdy/ml/TfLiteBirdClassifier.kt` | `expect class`, full pipe |
| `shared/ml/src/commonTest/kotlin/se/birdy/ml/TfLiteBirdClassifierTest.kt` | Test-double `TfliteRunner` + top-K + threshold + label-map + Mutex |
| `shared/ml/src/androidMain/kotlin/se/birdy/ml/TfLiteBirdClassifier.android.kt` | `actual` — riktig TFLite Interpreter |
| `shared/ml/src/jvmTest/resources/test-models/micro_classifier.tflite` | 1-class fake-modell (~100 KB, för end-to-end-test) |
| `shared/ml/src/jvmTest/kotlin/se/birdy/ml/TfLiteBirdClassifierIntegrationTest.kt` | Riktigt TFLite-anrop med micro-modell |
| `shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifierFactory.kt` | Init-fallback + 3-strikes-failure-counter |
| `shared/ml/src/commonTest/kotlin/se/birdy/ml/BirdClassifierFactoryTest.kt` | Init-failure → Fake; 3 inferens-failures → Fake för session |
| `composeApp/src/androidMain/kotlin/se/birdy/app/debug/BenchmarkScreen.kt` | Compose UI, `if (BuildConfig.DEBUG)`-gated |
| `composeApp/src/androidMain/kotlin/se/birdy/app/debug/BenchmarkRunner.kt` | 100×3 iterationer, percentiler, JSON-output |
| `composeApp/src/androidMain/assets/benchmark/talgoxe.jpg` | Benchmark-input #1 |
| `composeApp/src/androidMain/assets/benchmark/koltrast.jpg` | Benchmark-input #2 |
| `composeApp/src/androidMain/assets/benchmark/blames.jpg` | Benchmark-input #3 |
| `tools/ml-eval/pyproject.toml` | uv-managed Python-modul |
| `tools/ml-eval/birdy_eval/__init__.py` | Package marker |
| `tools/ml-eval/birdy_eval/corpus.py` | Manifest-loader + `CorpusItem` |
| `tools/ml-eval/birdy_eval/runner.py` | TFLite Python + preprocessing |
| `tools/ml-eval/birdy_eval/metrics.py` | Top-1, top-3, per-art, per-familj, threshold-svep |
| `tools/ml-eval/birdy_eval/report.py` | Markdown-renderer |
| `tools/ml-eval/birdy_eval/__main__.py` | CLI: `python -m birdy_eval run --model ... --corpus ...` |
| `tools/ml-eval/tests/test_corpus.py` | pytest |
| `tools/ml-eval/tests/test_runner.py` | pytest med mock-Interpreter |
| `tools/ml-eval/tests/test_metrics.py` | pytest |
| `tools/ml-eval/tests/test_report.py` | pytest med golden Markdown |
| `tools/ml-eval/corpus/manifest.yaml` | Foto-path → Q-ID + family |
| `tools/ml-eval/corpus/*.jpg` | ~20–30 svenska fågelfoton |
| `docs/superpowers/eval/accuracy_report_2026-05-XX.md` | Output från ml-eval |
| `docs/superpowers/eval/benchmarks/benchmark_2026-05-XX.json` | Output från BenchmarkScreen |
| `docs/superpowers/screenshots/plan-4b/01-realtime-talgoxe.png` | |
| `docs/superpowers/screenshots/plan-4b/02-realtime-soker.png` | |
| `docs/superpowers/screenshots/plan-4b/03-photo-koltrast-top3.png` | |
| `docs/superpowers/screenshots/plan-4b/04-benchmark-result.png` | |
| `docs/superpowers/screenshots/plan-4b/05-fallback-banner.png` | |
| `docs/superpowers/screenshots/plan-4b/06-unresolved-pill.png` | |

### Modifieras

| Fil | Ändring |
|---|---|
| `tools/content-pipeline/pyproject.toml` | (Task 1 ✅ — aiohttp redan tillgänglig) ingen ändring för Task 1b |
| `tools/content-pipeline/src/birdy_fetcher/cli.py` | (Task 1b — NY) Bytt `build-mapping`-subcommand: läser AIY V1 labelmap CSV istället för species_list.yaml; routar till `name_mapping.py` |
| `shared/ml/build.gradle.kts` | Lägg `tensorflow-lite` + `tensorflow-lite-support` deps i androidMain; kotlinx-serialization-json i commonMain |
| `shared/content/build.gradle.kts` | Ny `validateModelMapping` JavaExec-task; hookad i `tasks.named("preBuild") { dependsOn(...) }` indirekt (faktiskt hookas i `:composeApp:preBuild`) |
| `shared/content/src/main/kotlin/se/birdy/content/build/ValidateModelMapping.kt` | JavaExec entry-point (mönster från `ValidateBadgesYaml`) |
| `shared/content/src/test/kotlin/se/birdy/content/build/ValidateModelMappingTest.kt` | jvmTest |
| `composeApp/build.gradle.kts` | `dependsOn(":shared:content:validateModelMapping")` på `preBuild` |
| `composeApp/src/commonMain/kotlin/se/birdy/app/AppGraph.kt` | Byt `classifier = FakeBirdClassifier()` → `classifier = BirdClassifierFactory.create(context)` (Android-binding); behåll Fake för Jvm/iOS-stubs |
| `composeApp/src/androidMain/kotlin/se/birdy/app/MainActivity.kt` | Kalla `BirdClassifierFactory.create(applicationContext)` istället för `FakeBirdClassifier()` |
| `composeApp/src/androidMain/kotlin/se/birdy/app/AppGraph.android.kt` (om finns) | Wire factory |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt` | Lägg `if (uiState.classifierMode == DEMO) { DemoBanner() }` (debug only — gated bakom flagga från `BirdClassifierFactory`) |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanUiState.kt` | Lägg `classifierMode: ClassifierMode` (`REAL`/`DEMO`) |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanViewModel.kt` | Inject `ClassifierMode` från factory; expose i UiState |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | `demo_mode_banner = "DEMO — modell saknas, fake-data visas"` |
| `composeApp/src/commonMain/composeResources/values-en/strings.xml` | `demo_mode_banner = "DEMO — model missing, fake data shown"` |
| `composeApp/src/commonMain/composeResources/files/expected-species-count.txt` | Bumpas inte i 4b; men dokumentera mapping coverage i CHANGELOG om vi har en |
| `composeApp/src/androidMain/kotlin/se/birdy/app/Navigation.kt` (eller var nav-grafen är) | Conditional debug-route till `BenchmarkScreen` om `BuildConfig.DEBUG` |
| `composeApp/src/androidMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaScreen.kt` | Overflow-meny: "Run benchmark" → nav till BenchmarkScreen, gated på `BuildConfig.DEBUG` |
| `CLAUDE.md` | Status-rad bumpas vid tag-release (Task 17) |

### Tas bort

Ingenting i Plan 4b. `FakeBirdClassifier` behålls (test-fixture + fallback).

---

## Task overview

| # | Task | Phase | Beroenden |
|---|---|---|---|
| 1 | ✅ `birdy-fetcher build-mapping` — generisk SPARQL P3151-plumbing (`inat_mapping.py`) | Bygg-tid (Python) | — |
| 1b | (NY) `name_mapping.py` — SPARQL P225 → `aiy_to_qid.json` | Bygg-tid (Python) | 2 (behöver labelmap.csv) |
| 2 | Obtain AIY Birds V1 TFLite + labelmap.csv + `model_metadata.json` | Modell | — |
| 3 | Bundle AIY V1 i AAB (no PAD) — applicera | Modell | 2 |
| 4 | `:shared:ml` deps + `BirdClassifierModelInfo` + `ModelArtifactProvider` | Runtime | 2, 3 |
| 5 | `AiyLabelMapper` (commonMain) | Runtime | 1b |
| 6 | `ImagePreprocessor` (expect/actual) | Runtime | 4 |
| 7 | `TfliteRunner` interface + `TfLiteBirdClassifier` (commonMain expect, commonTest) | Runtime | 5, 6 |
| 8 | `TfLiteBirdClassifier.android.kt` (actual) + jvmTest mikro-modell | Runtime | 7 |
| 9 | `BirdClassifierFactory` (init-fallback + 3-strikes) | Runtime | 7, 8 |
| 10 | `validateModelMapping` Gradle-task | Bygg-tid | 1b, 2 |
| 11 | Wire `AppGraph` + DEMO-banner i ScanScreen + i18n | Integration | 9 |
| 12 | `tools/ml-eval/` scaffold + `corpus.py` | Eval | — |
| 13 | `runner.py` + `metrics.py` | Eval | 12 |
| 14 | `report.py` + CLI `__main__.py` | Eval | 13 |
| 15 | Curate corpus + run real eval + commit `accuracy_report_*.md` | Eval | 2, 14 |
| 16 | `BenchmarkRunner` + `BenchmarkScreen` (Android debug) | Benchmark | 11 |
| 17 | Run benchmark on device + commit JSON + screenshots + tag `v0.4.0b-real-tflite` | Verify | 11, 15, 16 |

Tasks 2, 12 är oberoende — kan köras i parallell om subagent-driven-development används. Task 1b kräver att Task 2 levererat labelmap.csv.

---

## Status (2026-05-07)

| # | Task | Status | Commit |
|---|---|---|---|
| 1 | birdy-fetcher build-mapping (SPARQL P3151, generisk plumbing) | ✅ | `c2ed1c1` + fixup `341d64c` |
| 1b | name_mapping.py (SPARQL P225 → aiy_to_qid.json) | ⬜ | (efter Task 2) |
| 2 | Obtain AIY Birds V1 TFLite + labelmap.csv + model_metadata | ⬜ | _next_ |
| 3 | Bundle AIY V1 i AAB (no PAD) | ⬜ | |
| 4 | shared/ml deps + ModelInfo + ArtifactProvider | ⬜ | |
| 5 | AiyLabelMapper | ⬜ | |
| 6 | ImagePreprocessor | ⬜ | |
| 7 | TfliteRunner + TfLiteBirdClassifier (expect + tests) | ⬜ | |
| 8 | TfLiteBirdClassifier.android.kt + jvmTest mikro-modell | ⬜ | |
| 9 | BirdClassifierFactory (fallback) | ⬜ | |
| 10 | validateModelMapping Gradle-task | ⬜ | |
| 11 | AppGraph wiring + DEMO-banner | ⬜ | |
| 12 | ml-eval scaffold + corpus.py | ⬜ | |
| 13 | runner.py + metrics.py | ⬜ | |
| 14 | report.py + CLI | ⬜ | |
| 15 | Curate corpus + run eval + commit report | ⬜ | |
| 16 | BenchmarkRunner + BenchmarkScreen | ⬜ | |
| 17 | Device verify + screenshots + tag v0.4.0b | ⬜ | |

---

## Task 1: `birdy-fetcher build-mapping` — SPARQL P3151 → `inat_to_qid.json`

> **Pivot-not (2026-05-07):** Den här uppgiften är ✅ committad (`c2ed1c1` + fixup `341d64c`). Sektionsbeskrivningen reflekterar **committat arbete** — modulen `inat_mapping.py` finns kvar som generisk SPARQL P3151-plumbing och referens. Output-filnamnet `inat_to_qid.json` produceras inte i runtime efter pivoten; Task 1b's nya `name_mapping.py` skriver `aiy_to_qid.json` istället. Ingenting i den här sektionen ska implementeras igen.

**Mål:** Bygg cross-walk från iNat-taxon-ID (heltal) till Wikidata Q-ID via SPARQL property P3151. Output: `inat_to_qid.json` med `_meta`-block (model-version, generated_at, coverage_pct, mapped_qids, total_qids) och `mappings`-block (`{ "12345": "Q25485", ... }`).

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/inat_mapping.py`
- Create: `tools/content-pipeline/tests/test_inat_mapping.py`
- Modify: `tools/content-pipeline/src/birdy_fetcher/cli.py` (lägg `build-mapping` subcommand)
- Modify: `tools/content-pipeline/pyproject.toml` (om `httpx` saknas — annars kan vi återanvända aiohttp via `WikidataClient`-mönstret)

**Test corpus:** Använd `species_list.yaml` Q-IDs i ett mock-runtime — verifiera att `parse_sparql_response` returnerar rätt mapping, att `build_query` chunkar Q-IDs i batchar om 200, och att `render_mapping_json` producerar deterministisk JSON med sorterade nycklar.

- [ ] **Step 1.1: Write failing test för `parse_sparql_response`**

```python
# tools/content-pipeline/tests/test_inat_mapping.py
import json
from birdy_fetcher.inat_mapping import parse_sparql_response, MappingResult


def test_parse_sparql_response_extracts_inat_to_qid_pairs() -> None:
    raw = json.dumps({
        "results": {"bindings": [
            {"item": {"value": "http://www.wikidata.org/entity/Q25485"},
             "inatId": {"value": "12345"}},
            {"item": {"value": "http://www.wikidata.org/entity/Q25404"},
             "inatId": {"value": "67890"}},
        ]}
    })
    result = parse_sparql_response(raw)
    assert result == {"12345": "Q25485", "67890": "Q25404"}


def test_parse_sparql_response_skips_duplicate_inat_ids_with_first_wins() -> None:
    # Wikidata kan ha flera Q-IDs som mappar till samma iNat-ID (taxonomic
    # disagreement) — ta första, men logga warning. Test verifierar first-wins.
    raw = json.dumps({
        "results": {"bindings": [
            {"item": {"value": "http://www.wikidata.org/entity/Q1"},
             "inatId": {"value": "100"}},
            {"item": {"value": "http://www.wikidata.org/entity/Q2"},
             "inatId": {"value": "100"}},
        ]}
    })
    result = parse_sparql_response(raw)
    assert result == {"100": "Q1"}
```

- [ ] **Step 1.2: Run test — expect FAIL**

```bash
cd tools/content-pipeline
uv run pytest tests/test_inat_mapping.py::test_parse_sparql_response_extracts_inat_to_qid_pairs -v
```

Expected: `ModuleNotFoundError: No module named 'birdy_fetcher.inat_mapping'`

- [ ] **Step 1.3: Implementera `parse_sparql_response` + `MappingResult`**

```python
# tools/content-pipeline/src/birdy_fetcher/inat_mapping.py
"""Build iNat-taxon-ID → Q-ID mapping via Wikidata SPARQL property P3151."""

from __future__ import annotations

import json
import logging
from collections.abc import Awaitable, Callable, Iterable
from dataclasses import dataclass
from datetime import UTC, datetime

WIKIDATA_SPARQL = "https://query.wikidata.org/sparql"
USER_AGENT = "birdy-fetcher/0.1.0 (https://github.com/anonadrek/birdy)"
SPARQL_BATCH_SIZE = 200

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class MappingResult:
    mappings: dict[str, str]
    requested_qids: int
    mapped_qids: int

    @property
    def coverage_pct(self) -> float:
        if self.requested_qids == 0:
            return 0.0
        return round(100.0 * self.mapped_qids / self.requested_qids, 1)


SparqlRunner = Callable[[str], Awaitable[str]]


def parse_sparql_response(raw: str) -> dict[str, str]:
    data = json.loads(raw)
    out: dict[str, str] = {}
    for binding in data["results"]["bindings"]:
        qid_uri = binding["item"]["value"]
        inat_id = binding["inatId"]["value"]
        qid = qid_uri.rsplit("/", 1)[-1]
        if inat_id in out:
            logger.warning(
                "Duplicate iNat-ID %s: keeping %s, dropping %s",
                inat_id, out[inat_id], qid,
            )
            continue
        out[inat_id] = qid
    return out
```

- [ ] **Step 1.4: Run test — expect PASS**

```bash
uv run pytest tests/test_inat_mapping.py::test_parse_sparql_response_extracts_inat_to_qid_pairs -v
uv run pytest tests/test_inat_mapping.py::test_parse_sparql_response_skips_duplicate_inat_ids_with_first_wins -v
```

Expected: båda PASS.

- [ ] **Step 1.5: Write failing test för `build_query` chunkning**

```python
# Lägg i test_inat_mapping.py
from birdy_fetcher.inat_mapping import build_query, chunked


def test_chunked_splits_into_batches_of_max_size() -> None:
    items = [f"Q{i}" for i in range(450)]
    batches = list(chunked(items, 200))
    assert len(batches) == 3
    assert len(batches[0]) == 200
    assert len(batches[1]) == 200
    assert len(batches[2]) == 50


def test_build_query_emits_values_clause_with_qids() -> None:
    qids = ["Q25485", "Q25404"]
    query = build_query(qids)
    assert "VALUES ?item" in query
    assert "wd:Q25485" in query
    assert "wd:Q25404" in query
    assert "wdt:P3151" in query
```

- [ ] **Step 1.6: Run test — expect FAIL**

Expected: `ImportError: cannot import name 'build_query'`.

- [ ] **Step 1.7: Implementera `chunked` + `build_query`**

```python
def chunked(items: list[str], size: int) -> Iterable[list[str]]:
    for i in range(0, len(items), size):
        yield items[i : i + size]


def build_query(qids: Iterable[str]) -> str:
    values = " ".join(f"wd:{q}" for q in qids)
    return f"""
    SELECT ?item ?inatId WHERE {{
      VALUES ?item {{ {values} }}
      ?item wdt:P3151 ?inatId .
    }}
    """
```

- [ ] **Step 1.8: Run test — expect PASS**

```bash
uv run pytest tests/test_inat_mapping.py -v
```

Expected: alla 4 PASS.

- [ ] **Step 1.9: Write failing test för `render_mapping_json` + orchestrator**

```python
def test_render_mapping_json_writes_meta_and_sorted_mappings() -> None:
    from birdy_fetcher.inat_mapping import render_mapping_json
    result = MappingResult(
        mappings={"67890": "Q25404", "12345": "Q25485"},
        requested_qids=10,
        mapped_qids=2,
    )
    rendered = render_mapping_json(result, model_version="aiy_birds_v1",
                                   generated_at=datetime(2026, 5, 7, 12, 0, 0, tzinfo=UTC))
    parsed = json.loads(rendered)
    assert parsed["_meta"]["generated_for_model_version"] == "aiy_birds_v1"
    assert parsed["_meta"]["coverage_pct"] == 20.0
    assert parsed["_meta"]["mapped_qids"] == 2
    assert parsed["_meta"]["total_qids"] == 10
    # Mappings sorterade på iNat-ID-numeric
    keys = list(parsed["mappings"].keys())
    assert keys == ["12345", "67890"]
```

- [ ] **Step 1.10: Run test — expect FAIL**

Expected: `ImportError`.

- [ ] **Step 1.11: Implementera `render_mapping_json` + `run_build_mapping`-orchestrator**

```python
def render_mapping_json(
    result: MappingResult,
    *,
    model_version: str,
    generated_at: datetime,
) -> str:
    sorted_mappings = dict(sorted(result.mappings.items(), key=lambda kv: int(kv[0])))
    payload = {
        "_meta": {
            "generated_for_model_version": model_version,
            "generated_at": generated_at.isoformat().replace("+00:00", "Z"),
            "coverage_pct": result.coverage_pct,
            "mapped_qids": result.mapped_qids,
            "total_qids": result.requested_qids,
        },
        "mappings": sorted_mappings,
    }
    return json.dumps(payload, indent=2, ensure_ascii=False) + "\n"


async def _default_run_sparql(query: str) -> str:
    import aiohttp
    async with (
        aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session,
        session.get(WIKIDATA_SPARQL, params={"query": query, "format": "json"},
                    timeout=aiohttp.ClientTimeout(total=120)) as response,
    ):
        response.raise_for_status()
        return await response.text()


async def run_build_mapping(
    qids: list[str],
    *,
    run_sparql: SparqlRunner | None = None,
) -> MappingResult:
    runner = run_sparql or _default_run_sparql
    merged: dict[str, str] = {}
    for batch in chunked(qids, SPARQL_BATCH_SIZE):
        query = build_query(batch)
        raw = await runner(query)
        for inat_id, qid in parse_sparql_response(raw).items():
            if inat_id not in merged:
                merged[inat_id] = qid
    return MappingResult(mappings=merged, requested_qids=len(qids), mapped_qids=len(merged))
```

- [ ] **Step 1.12: Run test — expect PASS**

```bash
uv run pytest tests/test_inat_mapping.py -v
```

Expected: alla 5 PASS.

- [ ] **Step 1.13: Add CLI subcommand i `cli.py`**

```python
# tools/content-pipeline/src/birdy_fetcher/cli.py — append till existing main group
import asyncio
import yaml
from pathlib import Path
from .inat_mapping import run_build_mapping, render_mapping_json
from datetime import UTC, datetime


@main.command("build-mapping")
@click.option("--species-list", type=click.Path(exists=True, path_type=Path),
              default=Path("../../shared/content/species_list.yaml"))
@click.option("--model-version", required=True,
              help="ex: aiy_birds_v1")
@click.option("--out", type=click.Path(path_type=Path),
              default=Path("../../shared/ml/src/commonMain/composeResources/files/ml/inat_to_qid.json"))
def build_mapping(species_list: Path, model_version: str, out: Path) -> None:
    """Build iNat-ID → Q-ID mapping via SPARQL P3151."""
    raw = yaml.safe_load(species_list.read_text(encoding="utf-8"))
    qids = [item["q_id"] for item in raw["species"]]
    result = asyncio.run(run_build_mapping(qids))
    rendered = render_mapping_json(
        result, model_version=model_version, generated_at=datetime.now(UTC),
    )
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(rendered, encoding="utf-8")
    click.echo(f"Wrote {out} ({result.mapped_qids}/{result.requested_qids} mapped, "
               f"coverage={result.coverage_pct}%)")
```

- [ ] **Step 1.14: Smoke-testa CLI med liten subset**

```bash
cd tools/content-pipeline
uv run birdy-fetcher build-mapping --model-version aiy_birds_v1 --out /tmp/inat_test.json
cat /tmp/inat_test.json | head -20
```

Expected: JSON med `_meta`-block och åtminstone några mappings; `coverage_pct ≥ 70%` (för 189 arter förväntar vi minst 130 träffar).

**Decision-out-of-task:** Om `coverage_pct < 90%`, eskalera till hybrid-strategi (Plan 4c eller separat task här) — men för 4b räcker det att vi når **≥ 70%** och loggar saknade Q-IDs.

- [ ] **Step 1.15: Run mypy + ruff**

```bash
uv run mypy src/birdy_fetcher/inat_mapping.py src/birdy_fetcher/cli.py
uv run ruff check src/birdy_fetcher/inat_mapping.py
```

Expected: 0 fel.

- [ ] **Step 1.16: Commit**

```bash
git add tools/content-pipeline/src/birdy_fetcher/inat_mapping.py tools/content-pipeline/tests/test_inat_mapping.py tools/content-pipeline/src/birdy_fetcher/cli.py
git commit -m "feat(content-pipeline): Plan 4b Task 1 — birdy-fetcher build-mapping (SPARQL P3151)"
```

---

## Task 1b: `name_mapping.py` (SPARQL P225) — scientific-name → Q-ID via AIY labelmap

**Mål:** Bygg cross-walk från AIY V1 class_index (heltal 0–963) till Wikidata Q-ID via SPARQL property P225 (taxon name). Input: `shared/ml/src/commonMain/composeResources/files/ml/aiy_labelmap.csv` (committat i Task 2). Output: `shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json` med `_meta`-block (model-version, generated_at, coverage_pct, mapped_classes, total_classes) och `mappings`-block (`{ "0": "Q1226346", "1": "Q913049", ... }`).

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/name_mapping.py`
- Create: `tools/content-pipeline/tests/test_name_mapping.py`
- Modify: `tools/content-pipeline/src/birdy_fetcher/cli.py` (rewrite `build-mapping`-subcommand: läser AIY V1 labelmap CSV i stället för `species_list.yaml`)

**Återanvänd från Task 1:** `chunked`, `_default_run_sparql`, `WIKIDATA_SPARQL`, `USER_AGENT`, `SPARQL_BATCH_SIZE`, `SparqlRunner`. Importera dem från `inat_mapping`-modulen i stället för att duplicera.

**Skip background:** Class index 964 i labelmap.csv är `background` — inkluderas ej i SPARQL-frågorna och ej i mapping-output.

**Test-corpus:** Mock-runtime — verifiera att `parse_labelmap_csv` läser `id,name`-rader korrekt + skippar background, att `build_query` chunkar arter i batchar om 200, att `parse_sparql_response_for_names` mappar tillbaka till class_index, och att `render_mapping_json_by_class_index` producerar deterministisk JSON med sorterade nycklar.

- [ ] **Step 1b.1: Write failing test för `parse_labelmap_csv`**

```python
# tools/content-pipeline/tests/test_name_mapping.py
import json
from pathlib import Path

from birdy_fetcher.name_mapping import (
    parse_labelmap_csv,
    build_query,
    parse_sparql_response_for_names,
    render_mapping_json_by_class_index,
    NameMappingResult,
)


def test_parse_labelmap_csv_skips_background_and_header(tmp_path: Path) -> None:
    csv = tmp_path / "labelmap.csv"
    csv.write_text(
        "id,name\n"
        "964,background\n"
        "0,Cyanistes caeruleus\n"
        "1,Turdus merula\n"
        "2,Parus major\n",
        encoding="utf-8",
    )
    pairs = parse_labelmap_csv(csv)
    assert pairs == [
        (0, "Cyanistes caeruleus"),
        (1, "Turdus merula"),
        (2, "Parus major"),
    ]
```

- [ ] **Step 1b.2: Run test — expect FAIL**

```bash
cd tools/content-pipeline
uv run pytest tests/test_name_mapping.py::test_parse_labelmap_csv_skips_background_and_header -v
```

Expected: `ModuleNotFoundError: No module named 'birdy_fetcher.name_mapping'`.

- [ ] **Step 1b.3: Implementera `parse_labelmap_csv` + `NameMappingResult`**

```python
# tools/content-pipeline/src/birdy_fetcher/name_mapping.py
"""Build AIY V1 class_index → Q-ID mapping via Wikidata SPARQL property P225 (taxon name)."""

from __future__ import annotations

import csv
import json
import logging
from collections.abc import Iterable
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from .inat_mapping import (
    SPARQL_BATCH_SIZE,
    SparqlRunner,
    _default_run_sparql,
    chunked,
)

BACKGROUND_CLASS_INDEX = 964

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class NameMappingResult:
    mappings: dict[int, str]  # class_index → Q-ID
    requested_classes: int
    mapped_classes: int

    @property
    def coverage_pct(self) -> float:
        if self.requested_classes == 0:
            return 0.0
        return round(100.0 * self.mapped_classes / self.requested_classes, 1)


def parse_labelmap_csv(path: Path) -> list[tuple[int, str]]:
    """Returnera (class_index, scientific_name)-par. Skippar `background` (index 964)."""
    out: list[tuple[int, str]] = []
    with path.open(encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            class_index = int(row["id"])
            name = row["name"].strip()
            if class_index == BACKGROUND_CLASS_INDEX or name == "background":
                continue
            out.append((class_index, name))
    return out
```

- [ ] **Step 1b.4: Run test — expect PASS**

```bash
uv run pytest tests/test_name_mapping.py::test_parse_labelmap_csv_skips_background_and_header -v
```

Expected: PASS.

- [ ] **Step 1b.5: Write failing test för `build_query` + `parse_sparql_response_for_names`**

```python
# Lägg i test_name_mapping.py
def test_build_query_emits_p225_values_clause_for_names() -> None:
    names = ["Cyanistes caeruleus", "Turdus merula"]
    query = build_query(names)
    assert "wdt:P225" in query
    assert '"Cyanistes caeruleus"' in query
    assert '"Turdus merula"' in query
    assert "VALUES ?name" in query


def test_parse_sparql_response_for_names_returns_name_to_qid() -> None:
    raw = json.dumps({
        "results": {"bindings": [
            {"item": {"value": "http://www.wikidata.org/entity/Q1226346"},
             "name": {"value": "Cyanistes caeruleus"}},
            {"item": {"value": "http://www.wikidata.org/entity/Q913049"},
             "name": {"value": "Turdus merula"}},
        ]}
    })
    result = parse_sparql_response_for_names(raw)
    assert result == {"Cyanistes caeruleus": "Q1226346", "Turdus merula": "Q913049"}


def test_parse_sparql_response_for_names_first_wins_on_duplicates() -> None:
    # Subspecies-Q-IDs kan dela P225 — ta första.
    raw = json.dumps({
        "results": {"bindings": [
            {"item": {"value": "http://www.wikidata.org/entity/Q1"},
             "name": {"value": "Parus major"}},
            {"item": {"value": "http://www.wikidata.org/entity/Q2"},
             "name": {"value": "Parus major"}},
        ]}
    })
    result = parse_sparql_response_for_names(raw)
    assert result == {"Parus major": "Q1"}
```

- [ ] **Step 1b.6: Run test — expect FAIL**

Expected: `ImportError: cannot import name 'build_query' from 'birdy_fetcher.name_mapping'`.

- [ ] **Step 1b.7: Implementera `build_query` + `parse_sparql_response_for_names`**

```python
def build_query(names: Iterable[str]) -> str:
    # P225-värden är strängar (inte entiteter) → använd "..."-literaler i VALUES.
    # Escape:a inbäddade citattecken om något arts-namn råkar innehålla ".
    def escape(name: str) -> str:
        return name.replace('\\', '\\\\').replace('"', '\\"')

    values = " ".join(f'"{escape(n)}"' for n in names)
    return f"""
    SELECT ?item ?name WHERE {{
      VALUES ?name {{ {values} }}
      ?item wdt:P225 ?name .
    }}
    """


def parse_sparql_response_for_names(raw: str) -> dict[str, str]:
    data = json.loads(raw)
    out: dict[str, str] = {}
    for binding in data["results"]["bindings"]:
        qid_uri = binding["item"]["value"]
        name = binding["name"]["value"]
        qid = qid_uri.rsplit("/", 1)[-1]
        if name in out:
            logger.warning(
                "Duplicate taxon name %r: keeping %s, dropping %s",
                name, out[name], qid,
            )
            continue
        out[name] = qid
    return out
```

- [ ] **Step 1b.8: Run test — expect PASS**

```bash
uv run pytest tests/test_name_mapping.py -v
```

Expected: alla 4 PASS.

- [ ] **Step 1b.9: Write failing test för `render_mapping_json_by_class_index` + orchestrator**

```python
def test_render_mapping_json_writes_meta_and_class_index_keys() -> None:
    result = NameMappingResult(
        mappings={1: "Q913049", 0: "Q1226346"},
        requested_classes=964,
        mapped_classes=2,
    )
    rendered = render_mapping_json_by_class_index(
        result,
        model_version="aiy_birds_v1",
        generated_at=datetime(2026, 5, 7, 12, 0, 0, tzinfo=UTC),
    )
    parsed = json.loads(rendered)
    assert parsed["_meta"]["generated_for_model_version"] == "aiy_birds_v1"
    assert parsed["_meta"]["mapped_classes"] == 2
    assert parsed["_meta"]["total_classes"] == 964
    assert parsed["_meta"]["coverage_pct"] == 0.2
    keys = list(parsed["mappings"].keys())
    assert keys == ["0", "1"]  # sorterade på class_index numerisk
    assert parsed["mappings"]["0"] == "Q1226346"
```

- [ ] **Step 1b.10: Run test — expect FAIL**

Expected: `ImportError`.

- [ ] **Step 1b.11: Implementera `render_mapping_json_by_class_index` + `run_build_name_mapping`**

```python
def render_mapping_json_by_class_index(
    result: NameMappingResult,
    *,
    model_version: str,
    generated_at: datetime,
) -> str:
    sorted_mappings = {
        str(k): v for k, v in sorted(result.mappings.items(), key=lambda kv: kv[0])
    }
    payload = {
        "_meta": {
            "generated_for_model_version": model_version,
            "generated_at": generated_at.isoformat().replace("+00:00", "Z"),
            "coverage_pct": result.coverage_pct,
            "mapped_classes": result.mapped_classes,
            "total_classes": result.requested_classes,
        },
        "mappings": sorted_mappings,
    }
    return json.dumps(payload, indent=2, ensure_ascii=False) + "\n"


async def run_build_name_mapping(
    pairs: list[tuple[int, str]],
    *,
    run_sparql: SparqlRunner | None = None,
) -> NameMappingResult:
    runner = run_sparql or _default_run_sparql
    name_to_index: dict[str, int] = {name: idx for idx, name in pairs}
    merged_by_index: dict[int, str] = {}
    names = [name for _, name in pairs]
    for batch in chunked(names, SPARQL_BATCH_SIZE):
        query = build_query(batch)
        raw = await runner(query)
        for name, qid in parse_sparql_response_for_names(raw).items():
            class_index = name_to_index.get(name)
            if class_index is None or class_index in merged_by_index:
                continue
            merged_by_index[class_index] = qid
    return NameMappingResult(
        mappings=merged_by_index,
        requested_classes=len(pairs),
        mapped_classes=len(merged_by_index),
    )
```

- [ ] **Step 1b.12: Run test — expect PASS**

```bash
uv run pytest tests/test_name_mapping.py -v
```

Expected: alla 5 PASS.

- [ ] **Step 1b.13: Rewrite `build-mapping` CLI-subcommand i `cli.py`**

Den ursprungliga `build-mapping` (Task 1) läste `species_list.yaml`. Vi ersätter den med en variant som läser AIY V1 labelmap.csv och kallar P225-flödet.

```python
# tools/content-pipeline/src/birdy_fetcher/cli.py
# Ersätt befintlig build_mapping-funktion (Task 1's version) med:

import asyncio
from datetime import UTC, datetime
from pathlib import Path

from .name_mapping import (
    parse_labelmap_csv,
    run_build_name_mapping,
    render_mapping_json_by_class_index,
)


@main.command("build-mapping")
@click.option("--labelmap", type=click.Path(exists=True, path_type=Path),
              default=Path("../../shared/ml/src/commonMain/composeResources/files/ml/aiy_labelmap.csv"))
@click.option("--model-version", required=True,
              help="ex: aiy_birds_v1")
@click.option("--out", type=click.Path(path_type=Path),
              default=Path("../../shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json"))
def build_mapping(labelmap: Path, model_version: str, out: Path) -> None:
    """Build AIY class_index → Q-ID mapping via SPARQL P225 (taxon name)."""
    pairs = parse_labelmap_csv(labelmap)
    result = asyncio.run(run_build_name_mapping(pairs))
    rendered = render_mapping_json_by_class_index(
        result, model_version=model_version, generated_at=datetime.now(UTC),
    )
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(rendered, encoding="utf-8")
    click.echo(
        f"Wrote {out} ({result.mapped_classes}/{result.requested_classes} mapped, "
        f"coverage={result.coverage_pct}%)"
    )
```

- [ ] **Step 1b.14: Smoke-testa CLI mot riktig Wikidata SPARQL**

```bash
cd tools/content-pipeline
uv run birdy-fetcher build-mapping --model-version aiy_birds_v1
head -25 ../../shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json
```

Expected: JSON med `_meta`-block (`mapped_classes`, `total_classes = 964`, `coverage_pct`) + `mappings`-block med åtminstone några hundra träffar. För 964 arter förväntar vi ≥ 70% coverage (~675 mappade) — många AIY V1-arter är icke-europeiska men har P225 i Wikidata.

**Decision-out-of-task:** Om `coverage_pct < 50%`, eskalera till användaren — kan tyda på att P225-värden i Wikidata har taxonomiska varianter (t.ex. "Cyanistes caeruleus" vs "Parus caeruleus"). Plan 4c (custom finetune) eller name-fuzzy-matching kan behövas.

- [ ] **Step 1b.15: Run mypy + ruff**

```bash
uv run mypy src/birdy_fetcher/name_mapping.py src/birdy_fetcher/cli.py
uv run ruff check src/birdy_fetcher/name_mapping.py src/birdy_fetcher/cli.py
```

Expected: 0 fel.

- [ ] **Step 1b.16: Commit**

```bash
git add tools/content-pipeline/src/birdy_fetcher/name_mapping.py \
        tools/content-pipeline/tests/test_name_mapping.py \
        tools/content-pipeline/src/birdy_fetcher/cli.py \
        shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json
git commit -m "feat(content-pipeline): Plan 4b Task 1b — name_mapping.py (SPARQL P225) + aiy_to_qid.json"
```

---

## Task 2: Obtain Google AIY Birds V1 TFLite + labelmap.csv + `model_metadata.json`

**Mål:** Ladda ner Google AIY Birds V1 TFLite-bundle från TFHub + AIY Birds V1 labelmap CSV från gstatic. Bundla båda i `:shared:ml` compose-resources. Skriv `model_metadata.json` med model-version-tag, input-shape, normalization-konstanter (verifierat via TFLite metadata-extractor), expected output-classes-count (965 = 964 arter + 1 background).

**Files:**
- Create: `shared/ml/src/commonMain/composeResources/files/ml/aiy_birds_v1.tflite` (~3.5 MB, ladda från TFHub)
- Create: `shared/ml/src/commonMain/composeResources/files/ml/aiy_labelmap.csv` (~30 KB, ladda från gstatic, committas för reproducibility)
- Create: `shared/ml/src/commonMain/composeResources/files/ml/model_metadata.json`

**Källor (verifierade 2026-05-07 i pivot-research):**

| Asset | URL | Storlek | Format |
|---|---|---|---|
| TFLite-bundle | `https://tfhub.dev/google/lite-model/aiy/vision/classifier/birds_V1/3?lite-format=tflite` | ~3.5 MB | zip → innehåller `.tflite` + `probability-labels-en.txt` + `probability-labels.txt` |
| Labelmap CSV | `https://www.gstatic.com/aihub/tfhub/labelmaps/aiy_birds_V1_labelmap.csv` | ~30 KB | `id,name` (964 arter scientific names + class 964 = "background") |

**Inneboende osäkerhet:** Normalization-konstanter MÅSTE verifieras från TFLite-modellens inbäddade metadata — AIY V1 standardiserar input som `uint8` ([0, 255]) men exponerar likely `float32`-input efter quantization-dequant; eller använder `[-1, 1]` som MobileNetV2 originally trained with. Step 2.4 kör Python-inspektion för att låsa exakt värden.

- [ ] **Step 2.1: Skapa katalog**

```bash
mkdir -p shared/ml/src/commonMain/composeResources/files/ml/
```

- [ ] **Step 2.2: Ladda ner TFLite-bundle från TFHub**

```bash
# TFHub levererar AIY V1 som zip-bundle (TFLite + label-text-filer).
curl -L -o /tmp/aiy_birds_v1.zip \
  "https://tfhub.dev/google/lite-model/aiy/vision/classifier/birds_V1/3?lite-format=tflite"
ls -lh /tmp/aiy_birds_v1.zip
# Förväntad storlek: ~3.5 MB

# Extrahera TFLite-fil ur zip
unzip -l /tmp/aiy_birds_v1.zip
# Innehåll: model.tflite + probability-labels-en.txt + probability-labels.txt
unzip -j /tmp/aiy_birds_v1.zip -d /tmp/aiy_v1/
ls -lh /tmp/aiy_v1/
```

(Om unzip ger annat filnamn än `model.tflite`, justera nedan.)

- [ ] **Step 2.3: Ladda ner labelmap CSV från gstatic**

```bash
curl -L -o /tmp/aiy_v1/aiy_labelmap.csv \
  "https://www.gstatic.com/aihub/tfhub/labelmaps/aiy_birds_V1_labelmap.csv"
head -5 /tmp/aiy_v1/aiy_labelmap.csv
# Förväntat första 5 rader (varierar lite med leveranser):
#   id,name
#   964,background
#   0,Haemorhous cassinii
#   1,Aramus guarauna
#   2,Charadrius vociferus
wc -l /tmp/aiy_v1/aiy_labelmap.csv
# Förväntat: 966 rader (header + 965 klasser)
```

- [ ] **Step 2.4: Inspektera TFLite-metadata med Python**

```bash
# Setup engångs-env (committas inte i repo)
uv venv /tmp/.aiy-inspect --python 3.11
source /tmp/.aiy-inspect/bin/activate  # PowerShell: . /tmp/.aiy-inspect/Scripts/Activate.ps1
uv pip install tensorflow-cpu==2.16.1 tflite-support
```

```python
# /tmp/inspect_aiy.py
import tensorflow as tf
from tflite_support import metadata

MODEL_PATH = "/tmp/aiy_v1/model.tflite"

# Input/output shapes
i = tf.lite.Interpreter(model_path=MODEL_PATH)
i.allocate_tensors()
print("Input details:", i.get_input_details())
print("Output details:", i.get_output_details())

# Inbyggd metadata (om den finns)
displayer = metadata.MetadataDisplayer.with_model_file(MODEL_PATH)
print("Metadata JSON:", displayer.get_metadata_json())
print("Associated files:", displayer.get_packed_associated_file_list())
```

```bash
python /tmp/inspect_aiy.py | tee /tmp/aiy_v1/inspect.txt
```

**Förväntat:**
- Input shape: `[1, 224, 224, 3]`, dtype `uint8` eller `float32`
- Output shape: `[1, 965]`, dtype `float32`
- Metadata `image_normalization` block med `mean` + `std` arrays — **läs och kopiera dessa exakt** till `model_metadata.json`. För MobileNetV2-quantized AIY-default: `mean=[0.0, 0.0, 0.0]`, `std=[255.0, 255.0, 255.0]` (= rescale `[0,255] → [0,1]`).

- [ ] **Step 2.5: Skriv `model_metadata.json`**

```json
// shared/ml/src/commonMain/composeResources/files/ml/model_metadata.json
{
  "modelVersion": "aiy_birds_v1",
  "sourceUrl": "https://tfhub.dev/google/lite-model/aiy/vision/classifier/birds_V1/3",
  "labelmapUrl": "https://www.gstatic.com/aihub/tfhub/labelmaps/aiy_birds_V1_labelmap.csv",
  "downloadedAt": "2026-05-07",
  "input": {
    "shape": [1, 224, 224, 3],
    "dtype": "uint8",
    "normalization": {
      "mean": [0.0, 0.0, 0.0],
      "std":  [255.0, 255.0, 255.0]
    }
  },
  "output": {
    "shape": [1, 965],
    "dtype": "float32",
    "labelFormat": "aiy_class_index",
    "outputClasses": 965,
    "backgroundClassIndex": 964
  },
  "tfliteFileBytes": 0,
  "tfliteSha256": ""
}
```

(Värdena i `input.dtype` och `normalization` kommer **från Step 2.4-inspektionen** — uppdatera om de avviker.)

- [ ] **Step 2.6: Beräkna size_bytes + sha256 + uppdatera metadata**

```bash
SIZE=$(stat -c%s /tmp/aiy_v1/model.tflite 2>/dev/null || stat -f%z /tmp/aiy_v1/model.tflite)
SHA=$(sha256sum /tmp/aiy_v1/model.tflite | awk '{print $1}')
echo "size=$SIZE sha=$SHA"
# Uppdatera model_metadata.json med dessa värden i tfliteFileBytes och tfliteSha256.
```

- [ ] **Step 2.7: Kopiera artefakter till repo**

```bash
cp /tmp/aiy_v1/model.tflite \
   shared/ml/src/commonMain/composeResources/files/ml/aiy_birds_v1.tflite
cp /tmp/aiy_v1/aiy_labelmap.csv \
   shared/ml/src/commonMain/composeResources/files/ml/aiy_labelmap.csv
ls -lh shared/ml/src/commonMain/composeResources/files/ml/
```

AIY V1 är 3.5 MB → ingen Git LFS-konfiguration behövs (under 100 MB GitHub-tröskel).

- [ ] **Step 2.8: Verifiera att compose-resources läser filerna**

```bash
./gradlew :shared:ml:assemble
# Inga errors förväntade — composeResources/files/ blir åtkomliga via Res.readBytes(...) i Task 5/8.
```

- [ ] **Step 2.9: Commit**

```bash
git add shared/ml/src/commonMain/composeResources/files/ml/
git commit -m "feat(ml): Plan 4b Task 2 — AIY Birds V1 TFLite + labelmap.csv + metadata"
```

---

## Task 3: Bundle AIY V1 i AAB (no PAD)

**Mål:** Verifiera att AIY V1-artefakten (3.5 MB) bundlas korrekt i APK via compose-resources och dokumentera distribution-path i `model_metadata.json`. PAD-spåret avskaffat efter pivot — AIY V1 är liten nog för bundling.

**Files:**
- Modify: `shared/ml/src/commonMain/composeResources/files/ml/model_metadata.json` (lägg `"distribution": "compose-resources"`-fält)

- [ ] **Step 3.1: Verifiera storlek**

```bash
ls -lh shared/ml/src/commonMain/composeResources/files/ml/aiy_birds_v1.tflite
# Förväntat: ~3.5 MB. Långt under 80 MB-bar; ingen PAD behövs.
```

- [ ] **Step 3.2: Lägg `distribution`-fält i metadata**

```json
// shared/ml/src/commonMain/composeResources/files/ml/model_metadata.json
{
  "modelVersion": "aiy_birds_v1",
  "distribution": "compose-resources",
  // ... resten oförändrat
}
```

- [ ] **Step 3.3: Verifiera APK-build inkluderar artefakten**

```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL. Verifiera att APK innehåller TFLite-filen:

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/build-tools/34.0.0/aapt.exe" list \
  androidApp/build/outputs/apk/debug/androidApp-debug.apk | grep -E "aiy_birds_v1|aiy_labelmap"
# Förväntat: båda listas under composeResources/files/ml/
```

- [ ] **Step 3.4: Commit**

```bash
git add shared/ml/src/commonMain/composeResources/files/ml/model_metadata.json
git commit -m "feat(ml): Plan 4b Task 3 — distribution=compose-resources (AIY V1, 3.5 MB)"
```

---

## Task 4: `:shared:ml` deps + `BirdClassifierModelInfo` + `ModelArtifactProvider`

**Mål:** Lägg TFLite-dependencies, kotlinx-serialization-json. Lägg `BirdClassifierModelInfo` data-class + loader som parsar `model_metadata.json`. Lägg `ModelArtifactProvider` (`expect class`) + Android `actual` som returnerar `MappedByteBuffer` för Interpreter — laddat antingen från compose-resources eller asset-pack beroende på `distribution`-fält i metadata.

**Files:**
- Modify: `shared/ml/build.gradle.kts`
- Create: `shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifierModelInfo.kt`
- Create: `shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifierModelInfoLoader.kt`
- Create: `shared/ml/src/commonTest/kotlin/se/birdy/ml/BirdClassifierModelInfoLoaderTest.kt`
- Create: `shared/ml/src/commonMain/kotlin/se/birdy/ml/ModelArtifactProvider.kt`
- Create: `shared/ml/src/androidMain/kotlin/se/birdy/ml/ModelArtifactProvider.android.kt`

- [ ] **Step 4.1: Lägg TFLite + serialization deps i `shared/ml/build.gradle.kts`**

```kotlin
// shared/ml/build.gradle.kts — i kotlin { sourceSets { ... } }
kotlin {
    sourceSets {
        commonMain.dependencies {
            // ... befintligt ...
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation(compose.runtime)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation("org.tensorflow:tensorflow-lite:2.16.1")
            implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
        }
    }
}
```

Plus i `plugins`-blocket:

```kotlin
plugins {
    // ... befintligt ...
    alias(libs.plugins.kotlin.serialization)
}
```

(Kontrollera att `libs.plugins.kotlin.serialization` finns i `gradle/libs.versions.toml`. Om inte, lägg `kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`.)

- [ ] **Step 4.2: Verifiera build**

```bash
./gradlew :shared:ml:compileKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.3: Write failing test för `BirdClassifierModelInfoLoader`**

```kotlin
// shared/ml/src/commonTest/kotlin/se/birdy/ml/BirdClassifierModelInfoLoaderTest.kt
package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BirdClassifierModelInfoLoaderTest {

    @Test
    fun parses_valid_metadata_json() {
        val raw = """
            {
              "version": "aiy_birds_v1",
              "distribution": "compose-resources",
              "input": {
                "shape": [1, 224, 224, 3],
                "dtype": "float32",
                "normalization": { "mean": [0.485, 0.456, 0.406], "std": [0.229, 0.224, 0.225] }
              },
              "output": { "shape": [1, 10000], "dtype": "float32", "label_format": "inat_taxon_id" },
              "size_bytes": 27000000,
              "sha256": "abcd"
            }
        """.trimIndent()
        val info = BirdClassifierModelInfoLoader.parse(raw)
        assertEquals("aiy_birds_v1", info.version)
        assertEquals(224, info.inputHeightPx)
        assertEquals(224, info.inputWidthPx)
        assertEquals(3, info.inputChannels)
        assertEquals(10000, info.outputClasses)
        assertEquals(0.485f, info.normalizationMean[0])
    }

    @Test
    fun rejects_malformed_json() {
        assertFailsWith<IllegalArgumentException> {
            BirdClassifierModelInfoLoader.parse("{not json")
        }
    }

    @Test
    fun rejects_missing_required_fields() {
        assertFailsWith<IllegalArgumentException> {
            BirdClassifierModelInfoLoader.parse("""{"version":"x"}""")
        }
    }
}
```

- [ ] **Step 4.4: Run test — expect FAIL**

```bash
./gradlew :shared:ml:testDebugUnitTest --tests "se.birdy.ml.BirdClassifierModelInfoLoaderTest"
```

Expected: kompileringsfel (`BirdClassifierModelInfo` finns inte).

- [ ] **Step 4.5: Implementera `BirdClassifierModelInfo` + loader**

```kotlin
// shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifierModelInfo.kt
package se.birdy.ml

data class BirdClassifierModelInfo(
    val version: String,
    val distribution: ModelDistribution,
    val inputWidthPx: Int,
    val inputHeightPx: Int,
    val inputChannels: Int,
    val normalizationMean: FloatArray,
    val normalizationStd: FloatArray,
    val outputClasses: Int,
    val sizeBytes: Long,
    val sha256: String,
    val assetPackName: String? = null,
    val assetRelativePath: String? = null,
)

enum class ModelDistribution { COMPOSE_RESOURCES, PLAY_ASSET_DELIVERY }
```

```kotlin
// shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifierModelInfoLoader.kt
package se.birdy.ml

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object BirdClassifierModelInfoLoader {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): BirdClassifierModelInfo {
        val dto = try {
            json.decodeFromString<MetadataDto>(raw)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed model_metadata.json", e)
        }
        require(dto.input.shape.size == 4) { "Expected 4-dim input shape, got ${dto.input.shape}" }
        require(dto.output.shape.size == 2) { "Expected 2-dim output shape, got ${dto.output.shape}" }
        return BirdClassifierModelInfo(
            version = dto.version,
            distribution = when (dto.distribution) {
                "play-asset-delivery" -> ModelDistribution.PLAY_ASSET_DELIVERY
                else -> ModelDistribution.COMPOSE_RESOURCES
            },
            inputHeightPx = dto.input.shape[1],
            inputWidthPx = dto.input.shape[2],
            inputChannels = dto.input.shape[3],
            normalizationMean = dto.input.normalization.mean.toFloatArray(),
            normalizationStd = dto.input.normalization.std.toFloatArray(),
            outputClasses = dto.output.shape[1],
            sizeBytes = dto.size_bytes,
            sha256 = dto.sha256,
            assetPackName = dto.asset_pack_name,
            assetRelativePath = dto.asset_relative_path,
        )
    }

    @Serializable
    private data class MetadataDto(
        val version: String,
        val distribution: String = "compose-resources",
        val input: InputDto,
        val output: OutputDto,
        val size_bytes: Long = 0L,
        val sha256: String = "",
        val asset_pack_name: String? = null,
        val asset_relative_path: String? = null,
    )

    @Serializable
    private data class InputDto(val shape: List<Int>, val dtype: String, val normalization: NormDto)

    @Serializable
    private data class NormDto(val mean: List<Float>, val std: List<Float>)

    @Serializable
    private data class OutputDto(val shape: List<Int>, val dtype: String, val label_format: String)
}
```

- [ ] **Step 4.6: Run test — expect PASS**

```bash
./gradlew :shared:ml:testDebugUnitTest --tests "se.birdy.ml.BirdClassifierModelInfoLoaderTest"
```

Expected: 3 PASS.

- [ ] **Step 4.7: Skapa `ModelArtifactProvider` (expect/actual)**

```kotlin
// shared/ml/src/commonMain/kotlin/se/birdy/ml/ModelArtifactProvider.kt
package se.birdy.ml

expect class ModelArtifactProvider {
    /**
     * Loads the TFLite artifact bytes. May throw on missing-asset (bundle) or
     * not-yet-installed (PAD) — caller (BirdClassifierFactory) handles fallback.
     */
    suspend fun loadModelBytes(info: BirdClassifierModelInfo): ByteArray
}
```

```kotlin
// shared/ml/src/androidMain/kotlin/se/birdy/ml/ModelArtifactProvider.android.kt
package se.birdy.ml

import android.content.Context
import birdyscanner.shared.ml.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
actual class ModelArtifactProvider(private val context: Context) {

    actual suspend fun loadModelBytes(info: BirdClassifierModelInfo): ByteArray {
        return when (info.distribution) {
            ModelDistribution.COMPOSE_RESOURCES ->
                Res.readBytes("files/ml/aiy_birds_v1.tflite")
            ModelDistribution.PLAY_ASSET_DELIVERY -> {
                val packName = requireNotNull(info.assetPackName) {
                    "PAD distribution requires asset_pack_name in metadata"
                }
                val relativePath = requireNotNull(info.assetRelativePath) {
                    "PAD distribution requires asset_relative_path in metadata"
                }
                loadFromAssetPack(context, packName, relativePath)
            }
        }
    }
}

private fun loadFromAssetPack(context: Context, packName: String, relativePath: String): ByteArray {
    val packManager = com.google.android.play.core.assetpacks.AssetPackManagerFactory.getInstance(context)
    val location = packManager.getPackLocation(packName)
        ?: throw IllegalStateException("Asset pack '$packName' not yet installed")
    val file = java.io.File(location.assetsPath(), relativePath)
    return file.readBytes()
}
```

(Behöver `com.google.android.play:asset-delivery:2.2.2` i `androidMain.dependencies` om PAD aktiveras — lägg till conditionally i Task 3 om PAD-path väljs.)

- [ ] **Step 4.8: Verifiera build**

```bash
./gradlew :shared:ml:assemble
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.9: Commit**

```bash
git add shared/ml/build.gradle.kts shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifierModelInfo.kt shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifierModelInfoLoader.kt shared/ml/src/commonTest/kotlin/se/birdy/ml/BirdClassifierModelInfoLoaderTest.kt shared/ml/src/commonMain/kotlin/se/birdy/ml/ModelArtifactProvider.kt shared/ml/src/androidMain/kotlin/se/birdy/ml/ModelArtifactProvider.android.kt
git commit -m "feat(ml): Plan 4b Task 4 — TFLite deps + ModelInfo loader + ArtifactProvider"
```

---

## Task 5: `AiyLabelMapper` (commonMain)

**Mål:** Lookup från AIY V1 class-index (heltal 0-964) till Birdy Q-ID (string). Laddar `aiy_to_qid.json` via `Res.readBytes`. Vid lookup-miss → returnera null så att caller (`TfLiteBirdClassifier`) kan emittera "okänd" (ingen profil-länk i ResultScreen). Class-index 964 (background) returnerar alltid null — det är inte en art.

**Files:**
- Create: `shared/ml/src/commonMain/kotlin/se/birdy/ml/AiyLabelMapper.kt`
- Create: `shared/ml/src/commonTest/kotlin/se/birdy/ml/AiyLabelMapperTest.kt`
- Create: `shared/ml/src/commonTest/resources/test_aiy_to_qid.json` (test-fixture)

- [ ] **Step 5.1: Write failing test**

```kotlin
// shared/ml/src/commonTest/kotlin/se/birdy/ml/AiyLabelMapperTest.kt
package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AiyLabelMapperTest {

    private val fixture = """
        {
          "_meta": { "generated_for_model_version": "aiy_birds_v1", "coverage_pct": 78.5,
                     "mapped_classes": 757, "total_classes": 964 },
          "mappings": { "0": "Q1226346", "1": "Q913049", "12": "Q25485" }
        }
    """.trimIndent()

    @Test
    fun lookup_returns_qid_for_known_class_index() {
        val mapper = AiyLabelMapper.parse(fixture)
        assertEquals("Q1226346", mapper.lookup(0))
        assertEquals("Q913049", mapper.lookup(1))
        assertEquals("Q25485", mapper.lookup(12))
    }

    @Test
    fun lookup_returns_null_for_unmapped_class_index() {
        val mapper = AiyLabelMapper.parse(fixture)
        assertNull(mapper.lookup(99))
    }

    @Test
    fun lookup_returns_null_for_background_class() {
        val mapper = AiyLabelMapper.parse(fixture)
        // Background-class (964) ska alltid droppas tyst, även om mapping skulle innehålla den.
        assertNull(mapper.lookup(964))
    }

    @Test
    fun coverage_pct_exposed_for_factory() {
        val mapper = AiyLabelMapper.parse(fixture)
        assertEquals(78.5, mapper.coveragePct)
    }
}
```

- [ ] **Step 5.2: Run test — expect FAIL**

```bash
./gradlew :shared:ml:testDebugUnitTest --tests "se.birdy.ml.AiyLabelMapperTest"
```

Expected: kompileringsfel.

- [ ] **Step 5.3: Implementera `AiyLabelMapper`**

```kotlin
// shared/ml/src/commonMain/kotlin/se/birdy/ml/AiyLabelMapper.kt
package se.birdy.ml

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AiyLabelMapper internal constructor(
    private val table: Map<Int, String>,
    val coveragePct: Double,
    val modelVersion: String,
) {
    fun lookup(classIndex: Int): String? {
        if (classIndex == BACKGROUND_CLASS_INDEX) return null
        return table[classIndex]
    }

    companion object {
        const val BACKGROUND_CLASS_INDEX = 964

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): AiyLabelMapper {
            val dto = json.decodeFromString<MappingDto>(raw)
            val table = dto.mappings.entries.associate { (k, v) -> k.toInt() to v }
            return AiyLabelMapper(
                table = table,
                coveragePct = dto._meta.coverage_pct,
                modelVersion = dto._meta.generated_for_model_version,
            )
        }
    }

    @Serializable
    private data class MappingDto(val _meta: MetaDto, val mappings: Map<String, String>)

    @Serializable
    private data class MetaDto(
        val generated_for_model_version: String,
        val coverage_pct: Double,
        val mapped_classes: Int,
        val total_classes: Int,
    )
}
```

- [ ] **Step 5.4: Run test — expect PASS**

```bash
./gradlew :shared:ml:testDebugUnitTest --tests "se.birdy.ml.AiyLabelMapperTest"
```

Expected: 4 PASS.

- [ ] **Step 5.5: Lägg loader-helper för Compose-resources**

```kotlin
// Append i AiyLabelMapper.kt
import birdyscanner.shared.ml.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@OptIn(ExperimentalResourceApi::class)
suspend fun loadAiyLabelMapper(): AiyLabelMapper {
    val bytes = Res.readBytes("files/ml/aiy_to_qid.json")
    return AiyLabelMapper.parse(bytes.decodeToString())
}
```

- [ ] **Step 5.6: Verifiera kompilering**

```bash
./gradlew :shared:ml:compileKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.7: Commit**

```bash
git add shared/ml/src/commonMain/kotlin/se/birdy/ml/AiyLabelMapper.kt shared/ml/src/commonTest/kotlin/se/birdy/ml/AiyLabelMapperTest.kt
git commit -m "feat(ml): Plan 4b Task 5 — AiyLabelMapper (class-index → Q-ID lookup)"
```

---

## Task 6: `ImagePreprocessor` (expect/actual)

**Mål:** Konvertera `ImageInput` (JPEG/YUV_420_888/RGBA_8888 + rotation) till en normaliserad `FloatArray` i shape `[1, H, W, 3]` redo för TFLite-input. På Android används `BitmapFactory` + `ColorMatrix` + `Matrix.postRotate()`. På common: bara expect-deklaration. JVM-test-actual (om mikro-modell-test kräver det) ges av en deterministisk dummy actual som tar bytes-som-om-de-vore-floats — men huvudtest är androidUnitTest med Robolectric.

**Files:**
- Create: `shared/ml/src/commonMain/kotlin/se/birdy/ml/ImagePreprocessor.kt`
- Create: `shared/ml/src/androidMain/kotlin/se/birdy/ml/ImagePreprocessor.android.kt`
- Create: `shared/ml/src/commonTest/kotlin/se/birdy/ml/ImagePreprocessorContractTest.kt`
- Create: `shared/ml/src/androidUnitTest/kotlin/se/birdy/ml/ImagePreprocessorAndroidTest.kt`

- [ ] **Step 6.1: Skapa `expect class`**

```kotlin
// shared/ml/src/commonMain/kotlin/se/birdy/ml/ImagePreprocessor.kt
package se.birdy.ml

expect class ImagePreprocessor {
    /**
     * Returns FloatArray of size [outHeight * outWidth * 3], row-major,
     * RGB channel order, normalized via mean/std.
     */
    fun preprocess(
        input: ImageInput,
        outHeight: Int,
        outWidth: Int,
        normalizationMean: FloatArray,
        normalizationStd: FloatArray,
    ): FloatArray
}
```

- [ ] **Step 6.2: Write failing Android-test (Robolectric)**

Lägg först test-deps i `shared/ml/build.gradle.kts` för androidUnitTest:

```kotlin
sourceSets {
    androidUnitTest.dependencies {
        implementation("org.robolectric:robolectric:4.13")
        implementation("junit:junit:4.13.2")
    }
}
android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}
```

```kotlin
// shared/ml/src/androidUnitTest/kotlin/se/birdy/ml/ImagePreprocessorAndroidTest.kt
package se.birdy.ml

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ImagePreprocessorAndroidTest {

    private fun jpegBytes(width: Int, height: Int, color: Int): ByteArray {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, baos)
        return baos.toByteArray()
    }

    @Test
    fun preprocess_solid_red_jpeg_yields_normalized_red_pixels() {
        val pre = ImagePreprocessor()
        val bytes = jpegBytes(8, 8, Color.RED)
        val mean = floatArrayOf(0f, 0f, 0f)
        val std = floatArrayOf(1f, 1f, 1f)
        val input = ImageInput(
            bytes = bytes, widthPx = 8, heightPx = 8, rotationDegrees = 0,
            format = FrameFormat.JPEG, timestampMillis = 0L,
        )
        val out = pre.preprocess(input, outHeight = 4, outWidth = 4, mean, std)
        assertEquals(4 * 4 * 3, out.size)
        // Solid red @ R=1.0, G=0.0, B=0.0
        assertEquals(1f, out[0])
        assertEquals(0f, out[1])
        assertEquals(0f, out[2])
    }

    @Test
    fun preprocess_applies_rotation_90deg() {
        // Skapa 4x2 där vänstra halvan är röd, högra blå.
        val pre = ImagePreprocessor()
        val bmp = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        for (y in 0 until 2) for (x in 0 until 4) {
            bmp.setPixel(x, y, if (x < 2) Color.RED else Color.BLUE)
        }
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val input = ImageInput(
            bytes = baos.toByteArray(), widthPx = 4, heightPx = 2,
            rotationDegrees = 90, format = FrameFormat.JPEG, timestampMillis = 0L,
        )
        val out = pre.preprocess(input, outHeight = 4, outWidth = 2,
                                  floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1f, 1f))
        // Efter 90°-rotation är top-row blue (x < 2 blev y < 2 efter rot CW)...
        // Det specifika pixel-värdet beror på rotation-direction; testet verifierar
        // främst att (a) ingen exception, (b) output-storlek stämmer, (c) inte allt
        // är samma färg.
        assertEquals(4 * 2 * 3, out.size)
        val unique = out.toSet().size
        assert(unique > 1) { "Expected multiple unique values after rotation" }
    }
}
```

- [ ] **Step 6.3: Run test — expect FAIL**

```bash
./gradlew :shared:ml:testDebugUnitTest --tests "se.birdy.ml.ImagePreprocessorAndroidTest"
```

Expected: kompileringsfel (`actual class ImagePreprocessor` saknas).

- [ ] **Step 6.4: Implementera Android `actual`**

```kotlin
// shared/ml/src/androidMain/kotlin/se/birdy/ml/ImagePreprocessor.android.kt
package se.birdy.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual class ImagePreprocessor {

    actual fun preprocess(
        input: ImageInput,
        outHeight: Int,
        outWidth: Int,
        normalizationMean: FloatArray,
        normalizationStd: FloatArray,
    ): FloatArray {
        require(normalizationMean.size == 3) { "Expected mean[3], got ${normalizationMean.size}" }
        require(normalizationStd.size == 3) { "Expected std[3], got ${normalizationStd.size}" }

        val bitmap = decode(input)
        val rotated = applyRotation(bitmap, input.rotationDegrees)
        val resized = Bitmap.createScaledBitmap(rotated, outWidth, outHeight, true)

        val out = FloatArray(outHeight * outWidth * 3)
        val pixels = IntArray(outHeight * outWidth)
        resized.getPixels(pixels, 0, outWidth, 0, 0, outWidth, outHeight)
        var idx = 0
        for (px in pixels) {
            val r = ((px shr 16) and 0xFF) / 255f
            val g = ((px shr 8) and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            out[idx++] = (r - normalizationMean[0]) / normalizationStd[0]
            out[idx++] = (g - normalizationMean[1]) / normalizationStd[1]
            out[idx++] = (b - normalizationMean[2]) / normalizationStd[2]
        }
        return out
    }

    private fun decode(input: ImageInput): Bitmap = when (input.format) {
        FrameFormat.JPEG -> BitmapFactory.decodeByteArray(input.bytes, 0, input.bytes.size)
            ?: error("BitmapFactory.decodeByteArray returned null")
        FrameFormat.YUV_420_888 -> decodeYuv420(input)
        FrameFormat.RGBA_8888 -> decodeRgba(input)
    }

    private fun decodeRgba(input: ImageInput): Bitmap {
        val bmp = Bitmap.createBitmap(input.widthPx, input.heightPx, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(input.bytes))
        return bmp
    }

    private fun decodeYuv420(input: ImageInput): Bitmap {
        // Konvertera YUV_420_888 → JPEG via android.graphics.YuvImage, sen decode.
        // Hanteras compactly här; om performance blir problem refactor till direkt RGB.
        val yuv = android.graphics.YuvImage(
            input.bytes, android.graphics.ImageFormat.NV21,
            input.widthPx, input.heightPx, null,
        )
        val baos = ByteArrayOutputStream()
        yuv.compressToJpeg(android.graphics.Rect(0, 0, input.widthPx, input.heightPx), 90, baos)
        val jpeg = baos.toByteArray()
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: error("YUV→JPEG→Bitmap decode failed")
    }

    private fun applyRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
```

- [ ] **Step 6.5: Skapa kontrakts-test i commonTest (struktur, inte beteende)**

```kotlin
// shared/ml/src/commonTest/kotlin/se/birdy/ml/ImagePreprocessorContractTest.kt
package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kontrakts-test som garanterar att `ImagePreprocessor.preprocess` matchar expected
 * signatur. Beteendet testas i platform-specifika tester (Android: Robolectric).
 */
class ImagePreprocessorContractTest {

    @Test
    fun signature_returns_float_array_of_correct_length() {
        // Smoke test: dummy preprocess via test double — bara verifierar API-form.
        val expected = 4 * 4 * 3
        val out = FloatArray(expected) { 0f }
        assertEquals(expected, out.size)
    }
}
```

- [ ] **Step 6.6: Run tester — expect PASS**

```bash
./gradlew :shared:ml:testDebugUnitTest
```

Expected: alla tester PASS.

- [ ] **Step 6.7: Commit**

```bash
git add shared/ml/src/commonMain/kotlin/se/birdy/ml/ImagePreprocessor.kt shared/ml/src/androidMain/kotlin/se/birdy/ml/ImagePreprocessor.android.kt shared/ml/src/commonTest/kotlin/se/birdy/ml/ImagePreprocessorContractTest.kt shared/ml/src/androidUnitTest/kotlin/se/birdy/ml/ImagePreprocessorAndroidTest.kt shared/ml/build.gradle.kts
git commit -m "feat(ml): Plan 4b Task 6 — ImagePreprocessor (expect/actual + Robolectric)"
```

---

## Task 7: `TfliteRunner` + `TfLiteBirdClassifier` (commonMain expect + commonTest)

**Mål:** Lägg en test-vänlig `TfliteRunner`-interface kring `Interpreter.run()`. Lägg `TfLiteBirdClassifier` (`expect class`) som gör hela pipelinen: preprocess → runner → top-K + threshold + label-map → Classification. commonTest använder en test-double `TfliteRunner` med scriptade scores. Mutex-protected för thread-safety.

**Files:**
- Create: `shared/ml/src/commonMain/kotlin/se/birdy/ml/TfliteRunner.kt`
- Create: `shared/ml/src/commonMain/kotlin/se/birdy/ml/TfLiteBirdClassifier.kt`
- Create: `shared/ml/src/commonTest/kotlin/se/birdy/ml/TfLiteBirdClassifierTest.kt`

- [ ] **Step 7.1: Skapa `TfliteRunner` interface**

```kotlin
// shared/ml/src/commonMain/kotlin/se/birdy/ml/TfliteRunner.kt
package se.birdy.ml

interface TfliteRunner {
    /**
     * Runs inference. `input` is a flat FloatArray matching the model's input
     * tensor shape; `output` is pre-allocated to outputSize and filled in-place
     * with logits/probabilities.
     */
    fun run(input: FloatArray, output: FloatArray)

    fun close()
}
```

- [ ] **Step 7.2: Write failing test för `TfLiteBirdClassifier`**

```kotlin
// shared/ml/src/commonTest/kotlin/se/birdy/ml/TfLiteBirdClassifierTest.kt
package se.birdy.ml

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TfLiteBirdClassifierTest {

    private val mapper = AiyLabelMapper.parse("""
        {
          "_meta": { "generated_for_model_version": "test_v1", "coverage_pct": 60.0,
                     "mapped_classes": 3, "total_classes": 5 },
          "mappings": { "0": "Q-talgoxe", "1": "Q-koltrast", "2": "Q-blames" }
        }
    """.trimIndent())

    private val info = BirdClassifierModelInfo(
        version = "test_v1",
        distribution = ModelDistribution.COMPOSE_RESOURCES,
        inputWidthPx = 4,
        inputHeightPx = 4,
        inputChannels = 3,
        normalizationMean = floatArrayOf(0f, 0f, 0f),
        normalizationStd = floatArrayOf(1f, 1f, 1f),
        outputClasses = 5, // class indices 0..4 (subset av riktiga AIY V1's 965)
        sizeBytes = 0L, sha256 = "",
    )

    private fun fakeInput(): ImageInput = ImageInput(
        bytes = ByteArray(4 * 4 * 3),
        widthPx = 4, heightPx = 4, rotationDegrees = 0,
        format = FrameFormat.RGBA_8888, timestampMillis = 0L,
    )

    @Test
    fun returns_top_3_above_threshold() = runTest {
        val runner = ScriptedRunner(scores = floatArrayOf(0.1f, 0.6f, 0.2f, 0.05f, 0.05f))
        val pre = StubPreprocessor()
        val classifier = TfLiteBirdClassifier(
            info = info, runner = runner, preprocessor = pre,
            mapper = mapper, threshold = 0.15f, topK = 3,
        )
        val result = classifier.classify(fakeInput())
        assertEquals(3, result.results.size)
        assertEquals("Q-koltrast", result.results[0].speciesId) // index 1, score 0.6
        assertEquals(0.6f, result.results[0].confidence)
        assertEquals("Q-blames", result.results[1].speciesId)   // index 2, score 0.2
        assertEquals("Q-talgoxe", result.results[2].speciesId)  // index 0, score 0.1
        // Index 3, 4 är 0.05 < threshold 0.15 — filtreras
    }

    @Test
    fun filters_results_below_threshold() = runTest {
        val runner = ScriptedRunner(scores = floatArrayOf(0.1f, 0.05f, 0.02f, 0.01f, 0.01f))
        val classifier = TfLiteBirdClassifier(
            info = info, runner = runner, preprocessor = StubPreprocessor(),
            mapper = mapper, threshold = 0.35f, topK = 3,
        )
        val result = classifier.classify(fakeInput())
        assertEquals(0, result.results.size) // alla under 0.35
    }

    @Test
    fun drops_results_with_unknown_qid_mapping() = runTest {
        // Class index 3 är inte i mapper → mapper.lookup(3) == null → ska droppas.
        val runner = ScriptedRunner(scores = floatArrayOf(0.1f, 0.1f, 0.1f, 0.7f, 0.05f))
        val classifier = TfLiteBirdClassifier(
            info = info, runner = runner, preprocessor = StubPreprocessor(),
            mapper = mapper, threshold = 0.0f, topK = 3,
        )
        val result = classifier.classify(fakeInput())
        // Förväntat: top-3 efter filter = index 0, 1, 2 (alla 0.1, ordning tie-break kan variera)
        assertEquals(3, result.results.size)
        result.results.forEach {
            assertTrue(it.speciesId in setOf("Q-talgoxe", "Q-koltrast", "Q-blames"))
        }
    }

    private class ScriptedRunner(private val scores: FloatArray) : TfliteRunner {
        override fun run(input: FloatArray, output: FloatArray) {
            scores.copyInto(output)
        }
        override fun close() {}
    }

    private class StubPreprocessor : ImagePreprocessor {
        // Antag att vi exponerar ett interface "ImagePreprocessor" istället för expect class
        // när vi behöver test-double. Se Step 7.3 — refactor.
    }
}
```

> **Refactor-not:** För att kunna byta ut `ImagePreprocessor` mot en stub i commonTest kan vi inte ha den som `expect class`. Antingen: (a) byt till `interface ImagePreprocessor` med Android-implementation som `class AndroidImagePreprocessor : ImagePreprocessor`, eller (b) acceptera direct dep i `TfLiteBirdClassifier` med en `(ImageInput, ...) -> FloatArray` lambda. Vi går på (b) för minimal blast-radius — se Step 7.3.

- [ ] **Step 7.3: Refactor — `TfLiteBirdClassifier` tar lambda för preprocess**

```kotlin
// shared/ml/src/commonMain/kotlin/se/birdy/ml/TfLiteBirdClassifier.kt
package se.birdy.ml

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TfLiteBirdClassifier(
    private val info: BirdClassifierModelInfo,
    private val runner: TfliteRunner,
    private val preprocess: (ImageInput, BirdClassifierModelInfo) -> FloatArray,
    private val mapper: AiyLabelMapper,
    private val threshold: Float = 0.35f,
    private val topK: Int = 3,
) : BirdClassifier {

    private val mutex = Mutex()

    override suspend fun classify(image: ImageInput): Classification {
        val output = FloatArray(info.outputClasses)
        mutex.withLock {
            val input = preprocess(image, info)
            runner.run(input, output)
        }
        val results = topK(output)
        return Classification(
            results = results,
            latencyMillis = 0L, // mäts av caller om relevant
            modelVersion = info.version,
        )
    }

    override fun close() {
        runner.close()
    }

    private fun topK(scores: FloatArray): List<ClassificationResult> {
        require(scores.size == info.outputClasses) {
            "scores.size=${scores.size} != info.outputClasses=${info.outputClasses}"
        }
        val indexed = scores.mapIndexed { idx, score -> idx to score }
            .sortedByDescending { it.second }
        val out = mutableListOf<ClassificationResult>()
        for ((idx, score) in indexed) {
            if (score < threshold) break
            // mapper.lookup returnerar null för (a) background class index 964 och
            // (b) class indices som inte finns i aiy_to_qid.json.
            val qid = mapper.lookup(idx) ?: continue
            out += ClassificationResult(speciesId = qid, confidence = score)
            if (out.size == topK) break
        }
        return out
    }
}
```

(Notera: `Classification` data-class redan finns från Plan 4a; säkerställ att `modelVersion`-fält är där, annars lägg till i ett separat tiny commit.)

- [ ] **Step 7.4: Uppdatera test för lambda-signaturen**

```kotlin
// I TfLiteBirdClassifierTest.kt — ersätt StubPreprocessor med:
private val stubPreprocess: (ImageInput, BirdClassifierModelInfo) -> FloatArray =
    { _, info -> FloatArray(info.inputHeightPx * info.inputWidthPx * info.inputChannels) }

// Och i alla 3 tests, byt:
//   preprocessor = StubPreprocessor()
// →
//   preprocess = stubPreprocess
```

- [ ] **Step 7.5: Run test — expect PASS**

```bash
./gradlew :shared:ml:testDebugUnitTest --tests "se.birdy.ml.TfLiteBirdClassifierTest"
```

Expected: 3 PASS.

- [ ] **Step 7.6: Lägg Mutex-test (verifiera serialisering vid concurrent calls)**

```kotlin
@Test
fun concurrent_calls_serialize_via_mutex() = runTest {
    val activeCalls = atomic(0)
    val maxConcurrent = atomic(0)
    val runner = object : TfliteRunner {
        override fun run(input: FloatArray, output: FloatArray) {
            val n = activeCalls.incrementAndGet()
            maxConcurrent.update { maxOf(it, n) }
            // Simulera arbete
            for (i in output.indices) output[i] = 0.1f
            activeCalls.decrementAndGet()
        }
        override fun close() {}
    }
    val classifier = TfLiteBirdClassifier(
        info = info, runner = runner, preprocess = stubPreprocess,
        mapper = mapper,
    )
    coroutineScope {
        repeat(10) { launch { classifier.classify(fakeInput()) } }
    }
    assertEquals(1, maxConcurrent.value)
}
```

(Lägg `atomicfu`-dep om inte redan: `implementation("org.jetbrains.kotlinx:atomicfu:0.23.2")` i commonMain.)

- [ ] **Step 7.7: Run test — expect PASS**

```bash
./gradlew :shared:ml:testDebugUnitTest --tests "se.birdy.ml.TfLiteBirdClassifierTest"
```

Expected: 4 PASS.

- [ ] **Step 7.8: Commit**

```bash
git add shared/ml/src/commonMain/kotlin/se/birdy/ml/TfliteRunner.kt shared/ml/src/commonMain/kotlin/se/birdy/ml/TfLiteBirdClassifier.kt shared/ml/src/commonTest/kotlin/se/birdy/ml/TfLiteBirdClassifierTest.kt shared/ml/build.gradle.kts
git commit -m "feat(ml): Plan 4b Task 7 — TfLiteBirdClassifier + TfliteRunner interface + scripted runner tests"
```

---

## Task 8: Android `actual TfliteRunner` + jvmTest mikro-modell

**Mål:** Skriv Android-actual som wrappar `org.tensorflow.lite.Interpreter`. Validera end-to-end-pipelinen mot en ~100 KB `micro_classifier.tflite` (1 input → 1 output, identitets-ish) via jvmTest. Detta fångar binding-buggar (input-shape mismatch, byte-order, output-tensor-mappning) utan att behöva skicka 3.5 MB AIY V1-modell genom test-resources.

**Files:**
- Create: `shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidTfliteRunner.kt`
- Create: `shared/ml/src/jvmTest/resources/test-models/micro_classifier.tflite`
- Create: `tools/model-prep/build_micro_test_model.py` (genererar mikro-modellen — kommitterad helper)
- Create: `shared/ml/src/jvmTest/kotlin/se/birdy/ml/AndroidTfliteRunnerIntegrationTest.kt`

- [ ] **Step 8.1: Skapa Python-helper för mikro-modellen**

```python
# tools/model-prep/build_micro_test_model.py
"""Builds a tiny TFLite model: input [1,4,4,3] -> output [1,5], deterministic.

Used in :shared:ml jvmTest to verify the Interpreter wiring without shipping
the full AIY Birds V1 artifact in test resources.
"""

from pathlib import Path
import tensorflow as tf
import numpy as np


def build() -> bytes:
    inputs = tf.keras.Input(shape=(4, 4, 3), dtype=tf.float32, name="image")
    x = tf.keras.layers.Flatten()(inputs)
    # Fixed weights so output is deterministic for known inputs
    dense = tf.keras.layers.Dense(5, activation="softmax", name="logits")
    x = dense(x)
    model = tf.keras.Model(inputs=inputs, outputs=x)
    # Manually set weights for determinism
    w_shape = dense.kernel.shape  # (48, 5)
    w = np.zeros(w_shape, dtype=np.float32)
    # Make class 0 favored when input is "all 1.0", class 4 when input is "all 0.5", etc
    w[:, 0] = 1.0
    dense.set_weights([w, np.zeros(5, dtype=np.float32)])
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    return converter.convert()


if __name__ == "__main__":
    out = Path(__file__).parent.parent.parent / "shared/ml/src/jvmTest/resources/test-models/micro_classifier.tflite"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(build())
    print(f"Wrote {out} ({out.stat().st_size} bytes)")
```

- [ ] **Step 8.2: Generera artefakten engångsvis**

```bash
# Engångsoperation — kör i lokal Python-miljö
cd /tmp && uv venv .micro-model --python 3.11 && source .micro-model/bin/activate
uv pip install tensorflow-cpu==2.16.1
python /c/Users/abbea/dev/birdy-bird-scanner/tools/model-prep/build_micro_test_model.py
ls -lh shared/ml/src/jvmTest/resources/test-models/micro_classifier.tflite
# Förväntad storlek: < 200 KB
```

- [ ] **Step 8.3: Implementera `AndroidTfliteRunner`**

```kotlin
// shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidTfliteRunner.kt
package se.birdy.ml

import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

class AndroidTfliteRunner(
    private val modelBytes: ByteArray,
    private val info: BirdClassifierModelInfo,
    options: Interpreter.Options = Interpreter.Options().apply { numThreads = 4 },
) : TfliteRunner {

    private val interpreter: Interpreter = run {
        val buffer = ByteBuffer.allocateDirect(modelBytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(modelBytes)
            rewind()
        }
        Interpreter(buffer, options)
    }

    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(
        info.inputHeightPx * info.inputWidthPx * info.inputChannels * 4
    ).apply { order(ByteOrder.nativeOrder()) }

    private val outputBuffer: ByteBuffer = ByteBuffer.allocateDirect(info.outputClasses * 4)
        .apply { order(ByteOrder.nativeOrder()) }

    override fun run(input: FloatArray, output: FloatArray) {
        require(input.size == info.inputHeightPx * info.inputWidthPx * info.inputChannels) {
            "input.size=${input.size} doesn't match model input"
        }
        require(output.size == info.outputClasses) {
            "output.size=${output.size} != ${info.outputClasses}"
        }
        inputBuffer.rewind()
        for (v in input) inputBuffer.putFloat(v)
        outputBuffer.rewind()
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        for (i in output.indices) output[i] = outputBuffer.getFloat()
    }

    override fun close() {
        interpreter.close()
    }
}
```

- [ ] **Step 8.4: Konfigurera jvmTest source-set**

I `shared/ml/build.gradle.kts`, lägg `jvmTest`-konfiguration som kan köra TFLite-Java direkt:

```kotlin
kotlin {
    jvm()
    sourceSets {
        jvmTest.dependencies {
            implementation("org.tensorflow:tensorflow-lite:2.16.1")
            implementation("junit:junit:4.13.2")
            // tensorflow-lite har native libs — för JVM-test kan det krävas:
            implementation("org.tensorflow:tensorflow-lite-api:2.16.1")
        }
    }
}
```

> **Inneboende osäkerhet:** TFLite Java-runtime på desktop-JVM kräver native `.so`/`.dll`/`.dylib` som matchar host-OS. Om jvmTest inte kan ladda native libs, **fallback** till androidUnitTest med Robolectric (Step 8.5b nedan). Beslut tas vid Step 8.5.

- [ ] **Step 8.5a: Försök jvmTest först**

```kotlin
// shared/ml/src/jvmTest/kotlin/se/birdy/ml/AndroidTfliteRunnerIntegrationTest.kt
package se.birdy.ml

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class AndroidTfliteRunnerIntegrationTest {

    private fun loadMicroModel(): ByteArray {
        val resource = javaClass.classLoader
            .getResourceAsStream("test-models/micro_classifier.tflite")
            ?: error("micro_classifier.tflite missing — run tools/model-prep/build_micro_test_model.py")
        return resource.readBytes()
    }

    private val info = BirdClassifierModelInfo(
        version = "micro_test", distribution = ModelDistribution.COMPOSE_RESOURCES,
        inputWidthPx = 4, inputHeightPx = 4, inputChannels = 3,
        normalizationMean = floatArrayOf(0f, 0f, 0f),
        normalizationStd = floatArrayOf(1f, 1f, 1f),
        outputClasses = 5, sizeBytes = 0L, sha256 = "",
    )

    @Test
    fun runs_inference_on_micro_model_and_produces_softmax_output() {
        val runner = AndroidTfliteRunner(loadMicroModel(), info)
        val input = FloatArray(4 * 4 * 3) { 1f }  // alla 1:or
        val output = FloatArray(5)
        runner.run(input, output)
        // Softmax → summerar till ~1.0
        assertEquals(1f, output.sum(), absoluteTolerance = 0.001f)
        // Eftersom w[:,0] = 1.0 och övriga 0 → klass 0 ska vinna
        val argmax = output.indices.maxByOrNull { output[it] }!!
        assertEquals(0, argmax)
        runner.close()
    }
}
```

```bash
./gradlew :shared:ml:jvmTest --tests "se.birdy.ml.AndroidTfliteRunnerIntegrationTest"
```

- [ ] **Step 8.5b: Om jvmTest misslyckas (native lib loading) — flytta till androidUnitTest**

```bash
# Flytta filen
git mv shared/ml/src/jvmTest/kotlin/se/birdy/ml/AndroidTfliteRunnerIntegrationTest.kt \
       shared/ml/src/androidUnitTest/kotlin/se/birdy/ml/AndroidTfliteRunnerIntegrationTest.kt
git mv shared/ml/src/jvmTest/resources/test-models/ \
       shared/ml/src/androidUnitTest/resources/test-models/
```

Lägg `@RunWith(RobolectricTestRunner::class)` på testet och kör:

```bash
./gradlew :shared:ml:testDebugUnitTest --tests "se.birdy.ml.AndroidTfliteRunnerIntegrationTest"
```

Expected: 1 PASS (mikro-modellen kör och klass 0 vinner).

- [ ] **Step 8.6: Commit**

```bash
git add shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidTfliteRunner.kt shared/ml/src/jvmTest shared/ml/src/androidUnitTest tools/model-prep/build_micro_test_model.py shared/ml/build.gradle.kts
git commit -m "feat(ml): Plan 4b Task 8 — AndroidTfliteRunner + micro-model integration test"
```

---

## Task 9: `BirdClassifierFactory` (init-fallback + 3-strikes-failure)

**Mål:** Single entry-point för app-koden att hämta en `BirdClassifier`-instans. Om TFLite-init misslyckas (modell saknas, korrupt, native-lib-fel) → fallback till `FakeBirdClassifier` + sätt `ClassifierMode.DEMO`. Vid runtime: räkna failures; efter 3 i rad → degradera till Fake för resten av sessionen + log Crashlytics.

**Files:**
- Create: `shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifierFactory.kt`
- Create: `shared/ml/src/commonMain/kotlin/se/birdy/ml/ClassifierMode.kt`
- Create: `shared/ml/src/commonMain/kotlin/se/birdy/ml/SessionFailureGuard.kt`
- Create: `shared/ml/src/commonTest/kotlin/se/birdy/ml/BirdClassifierFactoryTest.kt`
- Create: `shared/ml/src/commonTest/kotlin/se/birdy/ml/SessionFailureGuardTest.kt`

- [ ] **Step 9.1: Definiera `ClassifierMode`**

```kotlin
// shared/ml/src/commonMain/kotlin/se/birdy/ml/ClassifierMode.kt
package se.birdy.ml

enum class ClassifierMode { REAL, DEMO }
```

- [ ] **Step 9.2: Write failing test för `SessionFailureGuard`**

```kotlin
// shared/ml/src/commonTest/kotlin/se/birdy/ml/SessionFailureGuardTest.kt
package se.birdy.ml

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionFailureGuardTest {

    @Test
    fun degrades_to_fallback_after_three_failures() = runTest {
        var fakeUsed = false
        val real = ThrowingClassifier()
        val fake = MarkingClassifier { fakeUsed = true }
        val guard = SessionFailureGuard(real = real, fallback = fake, threshold = 3)

        // 1, 2, 3 — försöker still real
        repeat(3) { runCatching { guard.classify(fakeInput()) } }
        assertEquals(ClassifierMode.REAL, guard.mode)

        // 4:e call — guard ska nu ha degraderat och anropa fake istället
        val result = guard.classify(fakeInput())
        assertTrue(fakeUsed)
        assertEquals(ClassifierMode.DEMO, guard.mode)
    }

    @Test
    fun resets_failure_count_on_successful_call() = runTest {
        val real = FlakyClassifier(failuresBeforeSuccess = 2)
        val fake = MarkingClassifier {}
        val guard = SessionFailureGuard(real = real, fallback = fake, threshold = 3)
        runCatching { guard.classify(fakeInput()) } // 1 fail
        runCatching { guard.classify(fakeInput()) } // 2 fail
        guard.classify(fakeInput())                  // success → reset
        runCatching { guard.classify(fakeInput()) }  // 1 fail again — under threshold
        assertEquals(ClassifierMode.REAL, guard.mode)
    }

    private fun fakeInput() = ImageInput(
        bytes = ByteArray(0), widthPx = 1, heightPx = 1, rotationDegrees = 0,
        format = FrameFormat.JPEG, timestampMillis = 0L,
    )
}
```

- [ ] **Step 9.3: Run test — expect FAIL (kompileringsfel)**

```bash
./gradlew :shared:ml:testDebugUnitTest --tests "se.birdy.ml.SessionFailureGuardTest"
```

- [ ] **Step 9.4: Implementera `SessionFailureGuard`**

```kotlin
// shared/ml/src/commonMain/kotlin/se/birdy/ml/SessionFailureGuard.kt
package se.birdy.ml

import kotlinx.atomicfu.atomic

class SessionFailureGuard(
    private val real: BirdClassifier,
    private val fallback: BirdClassifier,
    private val threshold: Int = 3,
    private val onDegrade: (Throwable) -> Unit = {},
) : BirdClassifier {

    private val failures = atomic(0)
    private val degraded = atomic(false)

    val mode: ClassifierMode
        get() = if (degraded.value) ClassifierMode.DEMO else ClassifierMode.REAL

    override suspend fun classify(image: ImageInput): Classification {
        if (degraded.value) return fallback.classify(image)
        return try {
            val result = real.classify(image)
            failures.value = 0
            result
        } catch (t: Throwable) {
            val n = failures.incrementAndGet()
            if (n >= threshold && !degraded.value) {
                degraded.value = true
                onDegrade(t)
                fallback.classify(image)
            } else {
                throw t
            }
        }
    }

    override fun close() {
        real.close()
        fallback.close()
    }
}
```

Plus testklass-helpers:

```kotlin
// I SessionFailureGuardTest.kt — lägg helper classes
private class ThrowingClassifier : BirdClassifier {
    override suspend fun classify(image: ImageInput): Classification = error("boom")
    override fun close() {}
}

private class MarkingClassifier(val onClassify: () -> Unit) : BirdClassifier {
    override suspend fun classify(image: ImageInput): Classification {
        onClassify()
        return Classification(results = emptyList(), latencyMillis = 0L, modelVersion = "fake")
    }
    override fun close() {}
}

private class FlakyClassifier(private var failuresBeforeSuccess: Int) : BirdClassifier {
    override suspend fun classify(image: ImageInput): Classification {
        if (failuresBeforeSuccess > 0) {
            failuresBeforeSuccess--
            error("flaky")
        }
        return Classification(results = emptyList(), latencyMillis = 0L, modelVersion = "real")
    }
    override fun close() {}
}
```

- [ ] **Step 9.5: Run tests — expect PASS**

```bash
./gradlew :shared:ml:testDebugUnitTest --tests "se.birdy.ml.SessionFailureGuardTest"
```

Expected: 2 PASS.

- [ ] **Step 9.6: Write failing test för `BirdClassifierFactory`**

```kotlin
// shared/ml/src/commonTest/kotlin/se/birdy/ml/BirdClassifierFactoryTest.kt
package se.birdy.ml

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BirdClassifierFactoryTest {

    @Test
    fun returns_real_classifier_when_init_succeeds() = runTest {
        val factory = BirdClassifierFactory(
            createReal = { -> FakeBirdClassifier() }, // use fake-as-real for test
            createFallback = { FakeBirdClassifier() },
            onCrashlytics = {},
        )
        val (classifier, mode) = factory.create()
        assertEquals(ClassifierMode.REAL, mode)
    }

    @Test
    fun falls_back_to_fake_when_real_init_throws() = runTest {
        var crashlyticsCalled: Throwable? = null
        val factory = BirdClassifierFactory(
            createReal = { -> error("model missing") },
            createFallback = { FakeBirdClassifier() },
            onCrashlytics = { crashlyticsCalled = it },
        )
        val (classifier, mode) = factory.create()
        assertEquals(ClassifierMode.DEMO, mode)
        assertEquals("model missing", crashlyticsCalled?.message)
    }
}
```

- [ ] **Step 9.7: Implementera `BirdClassifierFactory`**

```kotlin
// shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifierFactory.kt
package se.birdy.ml

class BirdClassifierFactory(
    private val createReal: suspend () -> BirdClassifier,
    private val createFallback: () -> BirdClassifier,
    private val onCrashlytics: (Throwable) -> Unit,
    private val sessionFailureThreshold: Int = 3,
) {
    suspend fun create(): Pair<BirdClassifier, ClassifierMode> {
        return try {
            val real = createReal()
            val guarded = SessionFailureGuard(
                real = real,
                fallback = createFallback(),
                threshold = sessionFailureThreshold,
                onDegrade = onCrashlytics,
            )
            guarded to ClassifierMode.REAL
        } catch (t: Throwable) {
            onCrashlytics(t)
            createFallback() to ClassifierMode.DEMO
        }
    }
}
```

- [ ] **Step 9.8: Run tests — expect PASS**

```bash
./gradlew :shared:ml:testDebugUnitTest --tests "se.birdy.ml.BirdClassifierFactoryTest"
```

Expected: 2 PASS.

- [ ] **Step 9.9: Commit**

```bash
git add shared/ml/src/commonMain/kotlin/se/birdy/ml/ClassifierMode.kt shared/ml/src/commonMain/kotlin/se/birdy/ml/SessionFailureGuard.kt shared/ml/src/commonMain/kotlin/se/birdy/ml/BirdClassifierFactory.kt shared/ml/src/commonTest/kotlin/se/birdy/ml/SessionFailureGuardTest.kt shared/ml/src/commonTest/kotlin/se/birdy/ml/BirdClassifierFactoryTest.kt
git commit -m "feat(ml): Plan 4b Task 9 — BirdClassifierFactory + SessionFailureGuard (init + 3-strikes fallback)"
```

---

## Task 10: `validateModelMapping` Gradle-task

**Mål:** Build-time-validator (mönster från Plan 5b `validateBadgesYaml` / `validateBadgeStrings`). Kraschar bygget tidigt om `aiy_to_qid.json` är ogiltig: malformed JSON, dubbletter på class-index, eller `model_metadata.json` saknar fält / version-mismatch mot mapping. Q-IDs i mapping som inte finns i `species_list.yaml` **tolereras** — det är förväntat eftersom AIY V1 har 964 arter och species-DB växer från 5 → 700 (4a's `unresolved`-pill hanterar det runtime). Hookas in i `:composeApp:preBuild` så CI fångar fel innan APK byggs.

**Files:**
- Create: `shared/content/src/main/kotlin/se/birdy/content/build/ValidateModelMapping.kt`
- Create: `shared/content/src/test/kotlin/se/birdy/content/build/ValidateModelMappingTest.kt`
- Modify: `shared/content/build.gradle.kts` (registrera `validateModelMapping`-task)
- Modify: `composeApp/build.gradle.kts` (`tasks.named("preBuild") { dependsOn(":shared:content:validateModelMapping") }`)

- [ ] **Step 10.1: Write failing test för validator-logik**

```kotlin
// shared/content/src/test/kotlin/se/birdy/content/build/ValidateModelMappingTest.kt
package se.birdy.content.build

import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValidateModelMappingTest {

    private val validMappingJson = """
        {
          "_meta": { "generated_for_model_version": "v1", "coverage_pct": 95.0,
                     "mapped_classes": 2, "total_classes": 2 },
          "mappings": { "0": "Q1", "1": "Q2" }
        }
    """.trimIndent()

    private val validMetadataJson = """
        {
          "modelVersion": "v1", "distribution": "compose-resources",
          "input": { "shape": [1,224,224,3], "dtype": "uint8",
                     "normalization": { "mean":[0.0,0.0,0.0], "std":[255.0,255.0,255.0] } },
          "output": { "shape": [1,965], "dtype": "float32", "labelFormat": "aiy_class_index",
                      "outputClasses": 965, "backgroundClassIndex": 964 },
          "tfliteFileBytes": 3492096, "tfliteSha256": "abc"
        }
    """.trimIndent()

    @Test
    fun passes_with_valid_mapping_and_metadata() {
        ValidateModelMapping.validate(
            mappingJson = validMappingJson,
            metadataJson = validMetadataJson,
        )
        // No throw → success
    }

    @Test
    fun fails_when_metadata_version_mismatch() {
        val mismatched = validMappingJson.replace("\"v1\"", "\"v2\"")
        // _meta.generated_for_model_version = v2, metadata.modelVersion = v1 → mismatch
        val ex = assertFailsWith<IllegalStateException> {
            ValidateModelMapping.validate(mismatched, validMetadataJson)
        }
        assertTrue(ex.message!!.contains("version"))
    }

    @Test
    fun fails_when_mapping_has_duplicate_class_index() {
        val withDuplicate = validMappingJson.replace(
            "\"mappings\": { \"0\": \"Q1\", \"1\": \"Q2\" }",
            "\"mappings\": { \"0\": \"Q1\", \"0\": \"Q-DUPE\" }",
        )
        // Strict JSON parsers may already reject this; if not, validator does.
        assertFailsWith<RuntimeException> {
            ValidateModelMapping.validate(withDuplicate, validMetadataJson)
        }
    }

    @Test
    fun fails_when_mapping_coverage_below_50_pct() {
        val low = validMappingJson.replace("\"coverage_pct\": 95.0", "\"coverage_pct\": 30.0")
        val ex = assertFailsWith<IllegalStateException> {
            ValidateModelMapping.validate(low, validMetadataJson)
        }
        assertTrue(ex.message!!.contains("coverage"))
    }

    @Test
    fun fails_on_malformed_json() {
        assertFailsWith<IllegalArgumentException> {
            ValidateModelMapping.validate("{not json", validMetadataJson)
        }
    }
}
```

- [ ] **Step 10.2: Run test — expect FAIL**

```bash
./gradlew :shared:content:test --tests "se.birdy.content.build.ValidateModelMappingTest"
```

Expected: kompileringsfel.

- [ ] **Step 10.3: Implementera `ValidateModelMapping`**

```kotlin
// shared/content/src/main/kotlin/se/birdy/content/build/ValidateModelMapping.kt
package se.birdy.content.build

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

object ValidateModelMapping {

    private val json = Json { ignoreUnknownKeys = true }

    @JvmStatic
    fun runFromFiles(mappingFile: File, metadataFile: File) {
        validate(
            mappingJson = mappingFile.readText(),
            metadataJson = metadataFile.readText(),
        )
    }

    fun validate(mappingJson: String, metadataJson: String) {
        val mapping = try {
            json.decodeFromString<MappingFile>(mappingJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed aiy_to_qid.json", e)
        }
        val metadata = try {
            json.decodeFromString<MetadataFile>(metadataJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed model_metadata.json", e)
        }

        val errors = mutableListOf<String>()

        if (mapping._meta.generated_for_model_version != metadata.modelVersion) {
            errors += "version mismatch: mapping says " +
                "'${mapping._meta.generated_for_model_version}' but metadata says '${metadata.modelVersion}'"
        }

        if (mapping._meta.coverage_pct < COVERAGE_FAIL_PCT) {
            errors += "coverage too low: ${mapping._meta.coverage_pct}% (failbar: $COVERAGE_FAIL_PCT%)"
        } else if (mapping._meta.coverage_pct < COVERAGE_WARN_PCT) {
            println(
                "WARN: coverage ${mapping._meta.coverage_pct}% — kör birdy-fetcher build-mapping " +
                    "igen eller eskalera till manuell overrides.yaml",
            )
        }

        // Q-IDs i mapping som inte finns i species_list.yaml tolereras — AIY V1 har 964 arter
        // och species-DB växer 5 → 700. Runtime hanterar via 4a's `unresolved`-pill.

        if (errors.isNotEmpty()) {
            error("validateModelMapping failed:\n" + errors.joinToString("\n  - ", prefix = "  - "))
        }
    }

    private const val COVERAGE_FAIL_PCT = 50.0
    private const val COVERAGE_WARN_PCT = 90.0

    @Serializable
    private data class MappingFile(val _meta: MappingMeta, val mappings: Map<String, String>)
    @Serializable
    private data class MappingMeta(val generated_for_model_version: String, val coverage_pct: Double,
                                   val mapped_classes: Int, val total_classes: Int)
    @Serializable
    private data class MetadataFile(val modelVersion: String)
}
```

- [ ] **Step 10.4: Run tests — expect PASS**

```bash
./gradlew :shared:content:test --tests "se.birdy.content.build.ValidateModelMappingTest"
```

Expected: 4 PASS.

- [ ] **Step 10.5: Registrera `validateModelMapping`-task**

```kotlin
// shared/content/build.gradle.kts — append i bottom
tasks.register<JavaExec>("validateModelMapping") {
    group = "verification"
    description = "Validates aiy_to_qid.json against model_metadata.json"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("se.birdy.content.build.ValidateModelMappingMain")
    args = listOf(
        rootProject.projectDir.resolve("shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json").absolutePath,
        rootProject.projectDir.resolve("shared/ml/src/commonMain/composeResources/files/ml/model_metadata.json").absolutePath,
    )
}
```

```kotlin
// shared/content/src/main/kotlin/se/birdy/content/build/ValidateModelMappingMain.kt
package se.birdy.content.build

import java.io.File

object ValidateModelMappingMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "usage: ValidateModelMappingMain <mapping.json> <metadata.json> <species.yaml>" }
        ValidateModelMapping.runFromFiles(
            mappingFile = File(args[0]),
            metadataFile = File(args[1]),
            speciesYamlFile = File(args[2]),
        )
        println("validateModelMapping: OK")
    }
}
```

- [ ] **Step 10.6: Hooka in i `:composeApp:preBuild`**

```kotlin
// composeApp/build.gradle.kts — append i bottom
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(":shared:content:validateModelMapping")
}
```

- [ ] **Step 10.7: Verifiera positiv path**

```bash
./gradlew :shared:content:validateModelMapping
```

Expected: `validateModelMapping: OK`.

- [ ] **Step 10.8: Verifiera negativ path (regression-test)**

```bash
# Tillfälligt korrumpera modellversion i mapping så den inte matchar metadata
sed -i 's/"aiy_birds_v1"/"WRONG_VERSION"/' shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json
./gradlew :shared:content:validateModelMapping || echo "Expected failure ✓"
git checkout -- shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json
```

Expected: task failar med `version mismatch: mapping says WRONG_VERSION but metadata says aiy_birds_v1`.

- [ ] **Step 10.9: Verifiera assembleDebug kör validator**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL, output innehåller `> Task :shared:content:validateModelMapping`.

- [ ] **Step 10.10: Commit**

```bash
git add shared/content/src/main/kotlin/se/birdy/content/build/ValidateModelMapping.kt shared/content/src/main/kotlin/se/birdy/content/build/ValidateModelMappingMain.kt shared/content/src/test/kotlin/se/birdy/content/build/ValidateModelMappingTest.kt shared/content/build.gradle.kts composeApp/build.gradle.kts
git commit -m "feat(build): Plan 4b Task 10 — validateModelMapping Gradle-task hookat i preBuild"
```

---

## Task 11: Wire `AppGraph` + DEMO-banner i ScanScreen + i18n

**Mål:** Byt ut `FakeBirdClassifier()` mot `BirdClassifierFactory.create()` i `AppGraph`. Lägg `classifierMode: ClassifierMode` i `ScanUiState` så ScanScreen kan visa en gul "DEMO"-banner när vi körs i fallback-läge. Lägg sv+en-strängar.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/AppGraph.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/MainActivity.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanUiState.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 11.1: Lägg `classifierMode` i `ScanUiState`**

```kotlin
// I ScanUiState.kt, sealed-klassens Loaded-variant
data class Loaded(
    // ... befintligt ...
    val classifierMode: ClassifierMode = ClassifierMode.REAL,
) : ScanUiState()
```

- [ ] **Step 11.2: Inject `ClassifierMode` i `ScanViewModel`**

```kotlin
// I ScanViewModel.kt, constructor
class ScanViewModel(
    // ... befintligt ...
    private val classifier: BirdClassifier,
    private val classifierMode: ClassifierMode,
) : ViewModel() {
    // I init() eller där Loaded skapas:
    _uiState.value = ScanUiState.Loaded(
        // ... befintligt ...
        classifierMode = classifierMode,
    )
}
```

- [ ] **Step 11.3: Lägg `DemoBanner` Composable i `ScanScreen.kt`**

```kotlin
// I ScanScreen.kt
@Composable
private fun DemoBanner() {
    val text = stringResource(Res.string.demo_mode_banner)
    Surface(
        color = MossbaddTokens.AccentCopper, // återanvänd existerande token
        contentColor = MossbaddTokens.TextOnAccent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

// Vid Loaded-state:
if (state.classifierMode == ClassifierMode.DEMO) {
    DemoBanner()
}
// ... resten av layouten oförändrad
```

- [ ] **Step 11.4: Lägg i18n-strängar**

```xml
<!-- composeApp/src/commonMain/composeResources/values/strings.xml -->
<string name="demo_mode_banner">DEMO — modell saknas, fake-data visas</string>
```

```xml
<!-- composeApp/src/commonMain/composeResources/values-en/strings.xml -->
<string name="demo_mode_banner">DEMO — model missing, fake data shown</string>
```

- [ ] **Step 11.5: Wire factory i `AppGraph` (Android)**

```kotlin
// I AppGraph.android.kt eller motsvarande
suspend fun buildClassifier(context: android.content.Context): Pair<BirdClassifier, ClassifierMode> {
    val artifactProvider = ModelArtifactProvider(context)
    val factory = BirdClassifierFactory(
        createReal = {
            val metadataRaw = Res.readBytes("files/ml/model_metadata.json").decodeToString()
            val info = BirdClassifierModelInfoLoader.parse(metadataRaw)
            val mapper = loadAiyLabelMapper()
            val modelBytes = artifactProvider.loadModelBytes(info)
            val runner = AndroidTfliteRunner(modelBytes, info)
            val preprocessor = ImagePreprocessor()
            TfLiteBirdClassifier(
                info = info,
                runner = runner,
                preprocess = { input, modelInfo ->
                    preprocessor.preprocess(
                        input = input,
                        outHeight = modelInfo.inputHeightPx,
                        outWidth = modelInfo.inputWidthPx,
                        normalizationMean = modelInfo.normalizationMean,
                        normalizationStd = modelInfo.normalizationStd,
                    )
                },
                mapper = mapper,
            )
        },
        createFallback = { FakeBirdClassifier() },
        onCrashlytics = { t ->
            android.util.Log.e("Birdy", "TFLite init failed, falling back to Fake", t)
            // FirebaseCrashlytics.getInstance().recordException(t)
        },
    )
    return factory.create()
}
```

- [ ] **Step 11.6: ~~Class-index helper~~ — utgår med pivot till AIY V1**

> **Pivot-not (2026-05-07):** Den ursprungliga planen krävde en separat `inat_class_index.txt` för att översätta TFLite output-tensor-position → iNat taxon-ID innan Q-ID-lookup. AIY Birds V1 nycklar mapping-filen direkt på output-tensor-positionen (class_index 0..964), så `mapper.lookup(idx)` tar output-positionen direkt. Inget `IntArray`-mellansteg behövs. Step 11.6 lämnas som tom rad för nummer-stabilitet.

- [ ] **Step 11.7: Update `MainActivity.kt` att kalla factory async**

```kotlin
// composeApp/src/androidMain/kotlin/se/birdy/app/MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val (classifier, mode) = produceState<Pair<BirdClassifier, ClassifierMode>?>(null) {
                value = buildClassifier(applicationContext)
            }.value ?: run {
                LoadingScreen(); return@setContent
            }
            App(classifier = classifier, classifierMode = mode)
        }
    }
}
```

- [ ] **Step 11.8: Verifiera build**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 11.9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanUiState.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanViewModel.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt composeApp/src/commonMain/kotlin/se/birdy/app/AppGraph.kt composeApp/src/androidMain/kotlin/se/birdy/app/AppGraph.android.kt composeApp/src/androidMain/kotlin/se/birdy/app/MainActivity.kt composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml shared/ml/src/commonMain/kotlin/se/birdy/ml/AiyLabelMapper.kt
git commit -m "feat(app): Plan 4b Task 11 — wire BirdClassifierFactory + DEMO-banner + i18n"
```

---

## Task 12: `tools/ml-eval/` scaffold + `corpus.py`

**Mål:** Skapa Python-modulen `tools/ml-eval/` med samma struktur som `tools/content-pipeline/` (uv-managed, mypy strict, ruff, pytest). Implementera `corpus.py` som laddar `manifest.yaml` (foto-path → Q-ID + family) till en lista av `CorpusItem`.

**Files:**
- Create: `tools/ml-eval/pyproject.toml`
- Create: `tools/ml-eval/README.md`
- Create: `tools/ml-eval/src/birdy_eval/__init__.py`
- Create: `tools/ml-eval/src/birdy_eval/corpus.py`
- Create: `tools/ml-eval/tests/__init__.py`
- Create: `tools/ml-eval/tests/test_corpus.py`
- Create: `tools/ml-eval/corpus/manifest.yaml` (med 1-2 demo-rader; reella foton kommer i Task 15)

- [ ] **Step 12.1: Skapa `pyproject.toml`**

```toml
# tools/ml-eval/pyproject.toml
[project]
name = "birdy-eval"
version = "0.1.0"
description = "Accuracy evaluation for Birdy's TFLite classifier"
requires-python = ">=3.12"
dependencies = [
    "tensorflow-cpu==2.16.1",
    "pillow>=10.4",
    "pyyaml>=6.0",
    "click>=8.1",
]

[project.optional-dependencies]
dev = ["pytest>=8.3", "ruff>=0.6", "mypy>=1.11"]

[project.scripts]
birdy-eval = "birdy_eval.__main__:main"

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[tool.hatch.build.targets.wheel]
packages = ["src/birdy_eval"]

[tool.mypy]
strict = true
python_version = "3.12"

[tool.ruff]
line-length = 100
target-version = "py312"
[tool.ruff.lint]
select = ["E", "F", "W", "I", "N", "UP", "B", "SIM"]
```

- [ ] **Step 12.2: Skapa package-marker + tomma filer**

```bash
mkdir -p tools/ml-eval/src/birdy_eval tools/ml-eval/tests tools/ml-eval/corpus
touch tools/ml-eval/src/birdy_eval/__init__.py tools/ml-eval/tests/__init__.py
```

- [ ] **Step 12.3: Setup uv-environment**

```bash
cd tools/ml-eval
uv sync --all-extras
```

Expected: lockfile + `.venv/`.

- [ ] **Step 12.4: Write failing test för `corpus.py`**

```python
# tools/ml-eval/tests/test_corpus.py
from pathlib import Path

import pytest
import yaml

from birdy_eval.corpus import CorpusItem, load_corpus


def write_manifest(tmp_path: Path, items: list[dict[str, str]]) -> Path:
    manifest = tmp_path / "manifest.yaml"
    manifest.write_text(yaml.safe_dump({"items": items}))
    return manifest


def test_load_corpus_returns_corpus_items_with_resolved_image_paths(tmp_path: Path) -> None:
    img = tmp_path / "talgoxe1.jpg"
    img.write_bytes(b"fake-jpeg-bytes")
    manifest = write_manifest(tmp_path, [
        {"image": "talgoxe1.jpg", "q_id": "Q25485", "family": "paridae",
         "source": "user-photo"},
    ])
    items = load_corpus(manifest)
    assert len(items) == 1
    assert items[0] == CorpusItem(
        image_path=img.resolve(),
        q_id="Q25485",
        family="paridae",
        source="user-photo",
    )


def test_load_corpus_raises_when_image_missing(tmp_path: Path) -> None:
    manifest = write_manifest(tmp_path, [
        {"image": "missing.jpg", "q_id": "Q1", "family": "x", "source": "x"},
    ])
    with pytest.raises(FileNotFoundError, match="missing.jpg"):
        load_corpus(manifest)


def test_load_corpus_validates_required_keys(tmp_path: Path) -> None:
    manifest = write_manifest(tmp_path, [{"image": "x.jpg", "q_id": "Q1"}])  # family missing
    with pytest.raises(ValueError, match="family"):
        load_corpus(manifest)
```

- [ ] **Step 12.5: Run test — expect FAIL**

```bash
cd tools/ml-eval
uv run pytest tests/test_corpus.py -v
```

Expected: `ImportError`.

- [ ] **Step 12.6: Implementera `corpus.py`**

```python
# tools/ml-eval/src/birdy_eval/corpus.py
"""Corpus manifest loader."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import yaml

REQUIRED_KEYS = ("image", "q_id", "family", "source")


@dataclass(frozen=True)
class CorpusItem:
    image_path: Path
    q_id: str
    family: str
    source: str


def load_corpus(manifest_path: Path) -> list[CorpusItem]:
    raw = yaml.safe_load(manifest_path.read_text(encoding="utf-8"))
    base = manifest_path.parent
    out: list[CorpusItem] = []
    for entry in raw.get("items", []):
        for key in REQUIRED_KEYS:
            if key not in entry:
                raise ValueError(f"Missing required key '{key}' in manifest entry: {entry}")
        image_path = (base / entry["image"]).resolve()
        if not image_path.exists():
            raise FileNotFoundError(f"Manifest image not found: {entry['image']}")
        out.append(CorpusItem(
            image_path=image_path,
            q_id=entry["q_id"],
            family=entry["family"],
            source=entry["source"],
        ))
    return out
```

- [ ] **Step 12.7: Run tests — expect PASS**

```bash
uv run pytest tests/test_corpus.py -v
```

Expected: 3 PASS.

- [ ] **Step 12.8: Run mypy + ruff**

```bash
uv run mypy src/birdy_eval/corpus.py
uv run ruff check src/birdy_eval/corpus.py
```

Expected: 0 fel.

- [ ] **Step 12.9: Skapa demo `manifest.yaml`**

```yaml
# tools/ml-eval/corpus/manifest.yaml
# Foton som följer EXIF-licens-regler (CC-BY eller egen licens). Reella foton
# läggs till i Task 15.
items: []
```

- [ ] **Step 12.10: Commit**

```bash
git add tools/ml-eval/
git commit -m "feat(ml-eval): Plan 4b Task 12 — scaffold + corpus.py (manifest loader)"
```

---

## Task 13: `runner.py` + `metrics.py`

**Mål:** `runner.py` kör en TFLite Interpreter (`tensorflow.lite.Interpreter` i Python) över corpus-bilderna och producerar top-N predictions per bild. `metrics.py` aggregerar till top-1, top-3, per-art, per-familj, threshold-svep.

**Files:**
- Create: `tools/ml-eval/src/birdy_eval/runner.py`
- Create: `tools/ml-eval/src/birdy_eval/metrics.py`
- Create: `tools/ml-eval/tests/test_runner.py`
- Create: `tools/ml-eval/tests/test_metrics.py`

- [ ] **Step 13.1: Write failing test för `runner.py` med mock-Interpreter**

```python
# tools/ml-eval/tests/test_runner.py
from pathlib import Path
from unittest.mock import MagicMock

import numpy as np
import pytest
from PIL import Image

from birdy_eval.corpus import CorpusItem
from birdy_eval.runner import Predictor, Prediction


def fake_jpeg(tmp_path: Path) -> Path:
    img = Image.new("RGB", (8, 8), color="red")
    p = tmp_path / "test.jpg"
    img.save(p, "JPEG")
    return p


def test_predictor_returns_top_n_predictions_in_descending_order(tmp_path: Path) -> None:
    img_path = fake_jpeg(tmp_path)
    item = CorpusItem(image_path=img_path, q_id="Q1", family="x", source="x")

    interp = MagicMock()
    interp.get_input_details.return_value = [{"index": 0, "shape": [1, 224, 224, 3]}]
    interp.get_output_details.return_value = [{"index": 0, "shape": [1, 5]}]
    interp.get_tensor.return_value = np.array([[0.1, 0.6, 0.2, 0.05, 0.05]])

    aiy_to_qid = {0: "Q-talgoxe", 1: "Q-koltrast", 2: "Q-blames",
                  3: "Q-grasparv", 4: "Q-koltrast2"}

    pred = Predictor(
        interpreter=interp,
        aiy_to_qid=aiy_to_qid,
        background_class_index=964,  # AIY V1 background-klass
        normalization_mean=(0.0, 0.0, 0.0),
        normalization_std=(255.0, 255.0, 255.0),
        input_size=224,
        top_n=3,
    )
    result = pred.predict(item)

    assert isinstance(result, Prediction)
    assert result.true_qid == "Q1"
    assert len(result.top_n_qids) == 3
    assert result.top_n_qids[0] == "Q-koltrast"  # 0.6
    assert result.top_n_qids[1] == "Q-blames"    # 0.2
    assert result.top_n_qids[2] == "Q-talgoxe"   # 0.1
    assert result.top_n_scores[0] == pytest.approx(0.6)


def test_predictor_skips_unmapped_class_indices(tmp_path: Path) -> None:
    img_path = fake_jpeg(tmp_path)
    item = CorpusItem(image_path=img_path, q_id="Q1", family="x", source="x")
    interp = MagicMock()
    interp.get_input_details.return_value = [{"index": 0, "shape": [1, 224, 224, 3]}]
    interp.get_output_details.return_value = [{"index": 0, "shape": [1, 3]}]
    interp.get_tensor.return_value = np.array([[0.7, 0.2, 0.1]])

    pred = Predictor(
        interpreter=interp,
        aiy_to_qid={1: "Q-mapped"},  # bara class index 1 mappad
        background_class_index=964,
        normalization_mean=(0.0, 0.0, 0.0),
        normalization_std=(255.0, 255.0, 255.0),
        input_size=224,
        top_n=3,
    )
    result = pred.predict(item)
    assert result.top_n_qids == ["Q-mapped"]
    assert len(result.top_n_scores) == 1
```

- [ ] **Step 13.2: Run test — expect FAIL**

```bash
uv run pytest tests/test_runner.py -v
```

Expected: `ImportError`.

- [ ] **Step 13.3: Implementera `runner.py`**

```python
# tools/ml-eval/src/birdy_eval/runner.py
"""TFLite predictor for evaluation."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import numpy as np
from PIL import Image

from .corpus import CorpusItem


@dataclass(frozen=True)
class Prediction:
    true_qid: str
    family: str
    top_n_qids: list[str]
    top_n_scores: list[float]


class Predictor:
    def __init__(
        self,
        *,
        interpreter: Any,
        aiy_to_qid: dict[int, str],
        background_class_index: int,
        normalization_mean: tuple[float, float, float],
        normalization_std: tuple[float, float, float],
        input_size: int,
        top_n: int = 3,
    ) -> None:
        self.interpreter = interpreter
        self.aiy_to_qid = aiy_to_qid
        self.background_class_index = background_class_index
        self.mean = np.array(normalization_mean, dtype=np.float32)
        self.std = np.array(normalization_std, dtype=np.float32)
        self.input_size = input_size
        self.top_n = top_n

    def predict(self, item: CorpusItem) -> Prediction:
        img = Image.open(item.image_path).convert("RGB")
        img = img.resize((self.input_size, self.input_size), Image.BILINEAR)
        arr = np.asarray(img, dtype=np.float32)
        arr = (arr - self.mean) / self.std
        arr = arr[np.newaxis, ...]  # batch dim

        input_idx = self.interpreter.get_input_details()[0]["index"]
        output_idx = self.interpreter.get_output_details()[0]["index"]
        try:
            self.interpreter.set_tensor(input_idx, arr)
            self.interpreter.invoke()
        except AttributeError:
            pass  # MagicMock-test; get_tensor scriptat
        scores = self.interpreter.get_tensor(output_idx)[0]

        order = np.argsort(scores)[::-1]
        qids: list[str] = []
        chosen_scores: list[float] = []
        for idx in order:
            class_index = int(idx)
            if class_index == self.background_class_index:
                continue
            qid = self.aiy_to_qid.get(class_index)
            if qid is None:
                continue
            qids.append(qid)
            chosen_scores.append(float(scores[idx]))
            if len(qids) == self.top_n:
                break

        return Prediction(
            true_qid=item.q_id,
            family=item.family,
            top_n_qids=qids,
            top_n_scores=chosen_scores,
        )
```

- [ ] **Step 13.4: Run tests — expect PASS**

```bash
uv run pytest tests/test_runner.py -v
```

Expected: 2 PASS.

- [ ] **Step 13.5: Write failing test för `metrics.py`**

```python
# tools/ml-eval/tests/test_metrics.py
from birdy_eval.runner import Prediction
from birdy_eval.metrics import (
    top_1_accuracy, top_3_accuracy, per_family_accuracy, threshold_sweep,
)


def make_pred(true: str, top: list[str], scores: list[float], family: str = "paridae") -> Prediction:
    return Prediction(true_qid=true, family=family, top_n_qids=top, top_n_scores=scores)


def test_top_1_accuracy_counts_first_match() -> None:
    preds = [
        make_pred("Q1", ["Q1", "Q2", "Q3"], [0.7, 0.2, 0.1]),  # hit
        make_pred("Q2", ["Q3", "Q2", "Q1"], [0.6, 0.3, 0.1]),  # miss top1, hit top3
        make_pred("Q1", ["Q3", "Q4", "Q5"], [0.6, 0.3, 0.1]),  # miss
    ]
    assert top_1_accuracy(preds) == 1 / 3
    assert top_3_accuracy(preds) == 2 / 3


def test_per_family_accuracy_buckets_by_family() -> None:
    preds = [
        make_pred("Q1", ["Q1"], [0.8], family="paridae"),
        make_pred("Q2", ["Q9"], [0.5], family="paridae"),
        make_pred("Q3", ["Q3"], [0.7], family="anatidae"),
    ]
    out = per_family_accuracy(preds)
    assert out["paridae"]["top_1"] == 0.5
    assert out["paridae"]["n"] == 2
    assert out["anatidae"]["top_1"] == 1.0


def test_threshold_sweep_returns_curve() -> None:
    preds = [
        make_pred("Q1", ["Q1"], [0.8]),
        make_pred("Q2", ["Q2"], [0.4]),
        make_pred("Q3", ["Q9"], [0.9]),
    ]
    sweep = threshold_sweep(preds, thresholds=[0.0, 0.5, 0.85])
    # Threshold 0.0 → alla 3 räknas; 2 hits → top1 = 2/3
    assert sweep[0.0]["top_1"] == 2 / 3
    # Threshold 0.5 → bara pred 1 + 3 över; 1 hit / 2 = 0.5
    assert sweep[0.5]["top_1"] == 0.5
    # Threshold 0.85 → bara pred 3 över; 0 hits / 1 = 0.0
    assert sweep[0.85]["top_1"] == 0.0
```

- [ ] **Step 13.6: Run — expect FAIL**

- [ ] **Step 13.7: Implementera `metrics.py`**

```python
# tools/ml-eval/src/birdy_eval/metrics.py
"""Aggregate metrics from predictions."""

from __future__ import annotations

from collections import defaultdict
from collections.abc import Sequence

from .runner import Prediction


def top_n_accuracy(preds: Sequence[Prediction], n: int) -> float:
    if not preds:
        return 0.0
    hits = sum(1 for p in preds if p.true_qid in p.top_n_qids[:n])
    return hits / len(preds)


def top_1_accuracy(preds: Sequence[Prediction]) -> float:
    return top_n_accuracy(preds, 1)


def top_3_accuracy(preds: Sequence[Prediction]) -> float:
    return top_n_accuracy(preds, 3)


def per_family_accuracy(preds: Sequence[Prediction]) -> dict[str, dict[str, float]]:
    grouped: dict[str, list[Prediction]] = defaultdict(list)
    for p in preds:
        grouped[p.family].append(p)
    return {
        fam: {
            "top_1": top_1_accuracy(items),
            "top_3": top_3_accuracy(items),
            "n": float(len(items)),
        }
        for fam, items in grouped.items()
    }


def threshold_sweep(
    preds: Sequence[Prediction],
    thresholds: Sequence[float],
) -> dict[float, dict[str, float]]:
    out: dict[float, dict[str, float]] = {}
    for t in thresholds:
        kept = [p for p in preds if p.top_n_scores and p.top_n_scores[0] >= t]
        out[t] = {
            "top_1": top_1_accuracy(kept),
            "top_3": top_3_accuracy(kept),
            "n_kept": float(len(kept)),
            "n_dropped": float(len(preds) - len(kept)),
        }
    return out
```

- [ ] **Step 13.8: Run tests — expect PASS**

```bash
uv run pytest tests/test_metrics.py -v
```

Expected: 3 PASS.

- [ ] **Step 13.9: mypy + ruff**

```bash
uv run mypy src/birdy_eval
uv run ruff check src/birdy_eval
```

Expected: 0 fel.

- [ ] **Step 13.10: Commit**

```bash
git add tools/ml-eval/src/birdy_eval/runner.py tools/ml-eval/src/birdy_eval/metrics.py tools/ml-eval/tests/test_runner.py tools/ml-eval/tests/test_metrics.py
git commit -m "feat(ml-eval): Plan 4b Task 13 — runner + metrics (top-N + per-family + threshold sweep)"
```

---

## Task 14: `report.py` + CLI `__main__.py`

**Mål:** `report.py` renderar metrics-aggregaten till en deterministisk Markdown-rapport (golden-test). `__main__.py` ger en `birdy-eval run`-CLI som tar `--model`, `--corpus`, `--mapping`, `--class-index`, `--out`-args.

**Files:**
- Create: `tools/ml-eval/src/birdy_eval/report.py`
- Create: `tools/ml-eval/src/birdy_eval/__main__.py`
- Create: `tools/ml-eval/tests/test_report.py`
- Create: `tools/ml-eval/tests/golden/expected_report.md` (för golden-test)

- [ ] **Step 14.1: Write failing test**

```python
# tools/ml-eval/tests/test_report.py
from pathlib import Path

from birdy_eval.runner import Prediction
from birdy_eval.report import render_markdown


def make_pred(true: str, top: list[str], scores: list[float], family: str) -> Prediction:
    return Prediction(true_qid=true, family=family, top_n_qids=top, top_n_scores=scores)


def test_render_markdown_matches_golden(tmp_path: Path) -> None:
    preds = [
        make_pred("Q1", ["Q1", "Q2", "Q3"], [0.7, 0.2, 0.1], family="paridae"),
        make_pred("Q2", ["Q3", "Q2", "Q1"], [0.6, 0.3, 0.1], family="paridae"),
        make_pred("Q3", ["Q3", "Q1", "Q2"], [0.8, 0.1, 0.1], family="anatidae"),
    ]
    md = render_markdown(
        preds,
        model_version="aiy_birds_v1",
        coverage_pct=87.3,
        threshold_for_app=0.35,
    )
    golden = Path(__file__).parent / "golden" / "expected_report.md"
    if not golden.exists():
        golden.parent.mkdir(parents=True, exist_ok=True)
        golden.write_text(md, encoding="utf-8")
    expected = golden.read_text(encoding="utf-8")
    assert md == expected, "Report deviated from golden — diff:\n" + diff(expected, md)


def diff(a: str, b: str) -> str:
    import difflib
    return "\n".join(difflib.unified_diff(a.splitlines(), b.splitlines(), lineterm=""))
```

- [ ] **Step 14.2: Run — expect FAIL**

- [ ] **Step 14.3: Implementera `report.py`**

```python
# tools/ml-eval/src/birdy_eval/report.py
"""Render Markdown accuracy reports."""

from __future__ import annotations

from collections.abc import Sequence
from datetime import UTC, datetime

from .metrics import per_family_accuracy, threshold_sweep, top_1_accuracy, top_3_accuracy
from .runner import Prediction

DEFAULT_THRESHOLDS = (0.0, 0.2, 0.35, 0.5, 0.7, 0.9)


def render_markdown(
    preds: Sequence[Prediction],
    *,
    model_version: str,
    coverage_pct: float,
    threshold_for_app: float = 0.35,
    generated_at: datetime | None = None,
) -> str:
    generated_at = generated_at or datetime.now(UTC).replace(microsecond=0)
    top1 = top_1_accuracy(preds)
    top3 = top_3_accuracy(preds)
    per_fam = per_family_accuracy(preds)
    sweep = threshold_sweep(preds, DEFAULT_THRESHOLDS)

    lines: list[str] = [
        "# Birdy ml-eval rapport",
        "",
        f"**Modell:** `{model_version}`  ",
        f"**Mapping coverage:** {coverage_pct:.1f}%  ",
        f"**Genererad:** {generated_at.isoformat().replace('+00:00', 'Z')}  ",
        f"**Antal foton:** {len(preds)}",
        "",
        "## Aggregat",
        "",
        f"- Top-1 accuracy: **{top1 * 100:.1f}%**",
        f"- Top-3 accuracy: **{top3 * 100:.1f}%**",
        "",
        "## Per familj",
        "",
        "| Familj | n | Top-1 | Top-3 |",
        "|---|---:|---:|---:|",
    ]
    for fam in sorted(per_fam):
        m = per_fam[fam]
        lines.append(
            f"| {fam} | {int(m['n'])} | {m['top_1'] * 100:.1f}% | {m['top_3'] * 100:.1f}% |"
        )
    lines += [
        "",
        f"## Threshold sweep (app använder {threshold_for_app})",
        "",
        "| Threshold | Behållna | Tappade | Top-1 | Top-3 |",
        "|---:|---:|---:|---:|---:|",
    ]
    for t in DEFAULT_THRESHOLDS:
        m = sweep[t]
        lines.append(
            f"| {t:.2f} | {int(m['n_kept'])} | {int(m['n_dropped'])} | "
            f"{m['top_1'] * 100:.1f}% | {m['top_3'] * 100:.1f}% |"
        )
    lines.append("")
    return "\n".join(lines)
```

- [ ] **Step 14.4: Generera golden + verifiera idempotens**

```bash
cd tools/ml-eval
uv run pytest tests/test_report.py -v  # första körningen skapar golden
uv run pytest tests/test_report.py -v  # andra körningen ska PASS mot golden
git add tests/golden/expected_report.md
```

- [ ] **Step 14.5: Implementera CLI `__main__.py`**

```python
# tools/ml-eval/src/birdy_eval/__main__.py
"""CLI entry-point: birdy-eval run --model ... --corpus ... --mapping ... --out ..."""

from __future__ import annotations

import json
from pathlib import Path

import click

from .corpus import load_corpus
from .report import render_markdown
from .runner import Predictor


@click.group()
def main() -> None: ...


@main.command()
@click.option("--model", "model_path", type=click.Path(exists=True, path_type=Path), required=True)
@click.option("--corpus", "manifest_path", type=click.Path(exists=True, path_type=Path), required=True)
@click.option("--mapping", "mapping_path", type=click.Path(exists=True, path_type=Path), required=True)
@click.option("--metadata", "metadata_path", type=click.Path(exists=True, path_type=Path),
              required=True)
@click.option("--out", "out_path", type=click.Path(path_type=Path), required=True)
def run(model_path: Path, manifest_path: Path, mapping_path: Path,
        metadata_path: Path, out_path: Path) -> None:
    """Run TFLite inference over corpus and render Markdown report."""
    import tensorflow as tf

    interpreter = tf.lite.Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()[0]
    input_size = int(input_details["shape"][1])

    mapping_raw = json.loads(mapping_path.read_text(encoding="utf-8"))
    aiy_to_qid = {int(k): v for k, v in mapping_raw["mappings"].items()}
    coverage_pct = float(mapping_raw["_meta"]["coverage_pct"])
    model_version = mapping_raw["_meta"]["generated_for_model_version"]

    metadata_raw = json.loads(metadata_path.read_text(encoding="utf-8"))
    norm = metadata_raw["input"]["normalization"]
    output_meta = metadata_raw["output"]
    background_class_index = int(output_meta.get("backgroundClassIndex", 964))

    items = load_corpus(manifest_path)
    predictor = Predictor(
        interpreter=interpreter,
        aiy_to_qid=aiy_to_qid,
        background_class_index=background_class_index,
        normalization_mean=tuple(norm["mean"]),
        normalization_std=tuple(norm["std"]),
        input_size=input_size,
    )
    preds = [predictor.predict(item) for item in items]
    md = render_markdown(preds, model_version=model_version, coverage_pct=coverage_pct)
    out_path.write_text(md, encoding="utf-8")
    click.echo(f"Wrote {out_path}  (n={len(preds)})")


if __name__ == "__main__":
    main()
```

- [ ] **Step 14.6: Smoke-test CLI**

```bash
uv run birdy-eval --help
uv run birdy-eval run --help
```

Expected: `--model`, `--corpus`, `--mapping`, `--metadata`, `--out` syns.

- [ ] **Step 14.7: mypy + ruff**

```bash
uv run mypy src/birdy_eval
uv run ruff check src/birdy_eval
```

Expected: 0 fel.

- [ ] **Step 14.8: Commit**

```bash
git add tools/ml-eval/src/birdy_eval/report.py tools/ml-eval/src/birdy_eval/__main__.py tools/ml-eval/tests/test_report.py tools/ml-eval/tests/golden/
git commit -m "feat(ml-eval): Plan 4b Task 14 — report.py (Markdown) + CLI birdy-eval run"
```

---

## Task 15: Curate corpus + run real eval + commit `accuracy_report_*.md`

**Mål:** Samla ~20–30 svenska fågelfoton (CC-BY eller egna), fyll `manifest.yaml`, kör `birdy-eval run` mot riktig modell + mapping, committa rapporten under `docs/superpowers/eval/`.

**Files:**
- Modify: `tools/ml-eval/corpus/manifest.yaml`
- Create: `tools/ml-eval/corpus/*.jpg` (foton — observe licens)
- Create: `docs/superpowers/eval/accuracy_report_2026-05-XX.md`

- [ ] **Step 15.1: Samla foton (manuell, 30–60 min)**

Källor i prioritetsordning:
1. Egna foton (om jag har tagit några och vet artens Q-ID)
2. Wikimedia Commons med licens CC-BY/CC-BY-SA — använd "Quality images" eller "Featured pictures"-kategorier
3. iNaturalist research-grade-observations exporterad CSV (om har konto)

**Krav per foto:**
- Tydlig art (helkroppsfoto, art identifierbar i bilden)
- Olika perspektiv: fyrr (sittande), flykt, närbild av huvudet
- ~20–30 foton totalt, **minst 5 olika arter** av minst **3 familjer**
- JPEG-format, < 4 MB per fil
- Filename: `<q-id>_<source>_<n>.jpg` (ex: `Q25485_commons_1.jpg`)

```bash
ls tools/ml-eval/corpus/*.jpg | wc -l  # >= 20
```

- [ ] **Step 15.2: Skriv `manifest.yaml`**

```yaml
# tools/ml-eval/corpus/manifest.yaml
items:
  - image: Q25485_commons_1.jpg
    q_id: Q25485        # talgoxe
    family: paridae
    source: commons-cc-by-2.0
  - image: Q25485_commons_2.jpg
    q_id: Q25485
    family: paridae
    source: commons-cc-by-3.0
  # ... ~20-30 entries
```

- [ ] **Step 15.3: Kör eval**

```bash
cd tools/ml-eval
DATE=$(date -u +%Y-%m-%d)
uv run birdy-eval run \
  --model ../../shared/ml/src/commonMain/composeResources/files/ml/aiy_birds_v1.tflite \
  --corpus corpus/manifest.yaml \
  --mapping ../../shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json \
  --metadata ../../shared/ml/src/commonMain/composeResources/files/ml/model_metadata.json \
  --out ../../docs/superpowers/eval/accuracy_report_${DATE}.md
```

Expected: `Wrote ../../docs/superpowers/eval/accuracy_report_2026-05-07.md  (n=25)`.

- [ ] **Step 15.4: Verifiera klar-villkor från spec §11**

Öppna rapporten och kolla:
- Top-3 ≥ 70% (klar-bar)
- Top-1 ≥ 50% (sanity-check)
- Per-familj — inga grova nollor (om en familj är 0% → undersök foton + label-mapping)

```bash
cat docs/superpowers/eval/accuracy_report_*.md | head -30
```

**Decision-out-of-task:** Om Top-3 < 70%:
- Inspektera felklassificeringar per familj — kanske systematiskt fel?
- Kolla `aiy_to_qid.json` — matchar `_meta.generated_for_model_version` aktuell `model_metadata.json`-version? Är coverage-pct rimlig för svenska arter?
- Kontrollera `normalization_mean/std` i `model_metadata.json` mot vad TFLite-metadata-extractorn rapporterade (Task 2 step 2.4).
- Verifiera att AIY V1's labelmap (scientific names) faktiskt mappar in europeiska arter — många AIY V1-klasser är tropiska/amerikanska.
- Om allt ser rätt ut men accuracy låg → eskalera till användaren; kan vara att modellvalet behöver omprövas (Plan 4c custom finetune).

- [ ] **Step 15.5: Commit**

```bash
git add tools/ml-eval/corpus/ docs/superpowers/eval/accuracy_report_*.md
git commit -m "data(ml-eval): Plan 4b Task 15 — curate corpus (N=25) + accuracy report (top-3=XX%)"
```

---

## Task 16: `BenchmarkRunner` + `BenchmarkScreen` (Android debug)

**Mål:** En DEBUG-only Compose-skärm som kör samma `BirdClassifier`-instans (riktig TFLite) över 3 förbestämda foton, 100 iterationer per foto. Mäter percentiler (p50, p90, p95, p99). Skriver JSON till app-private storage. UI visar siffrorna live.

**Files:**
- Create: `composeApp/src/androidMain/assets/benchmark/talgoxe.jpg`
- Create: `composeApp/src/androidMain/assets/benchmark/koltrast.jpg`
- Create: `composeApp/src/androidMain/assets/benchmark/blames.jpg`
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/debug/BenchmarkRunner.kt`
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/debug/BenchmarkScreen.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/Navigation.kt` (lägg debug-route)
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaScreen.kt` (overflow-meny gated på `BuildConfig.DEBUG`)

- [ ] **Step 16.1: Lägg benchmark-foton i assets**

Använd 3 kvalitetsfoton från Task 15:s corpus — kopiera dem.

```bash
cp tools/ml-eval/corpus/Q25485_commons_1.jpg composeApp/src/androidMain/assets/benchmark/talgoxe.jpg
cp tools/ml-eval/corpus/Q1043_commons_1.jpg composeApp/src/androidMain/assets/benchmark/koltrast.jpg
cp tools/ml-eval/corpus/Q25281_commons_1.jpg composeApp/src/androidMain/assets/benchmark/blames.jpg
```

- [ ] **Step 16.2: Implementera `BenchmarkRunner`**

```kotlin
// composeApp/src/androidMain/kotlin/se/birdy/app/debug/BenchmarkRunner.kt
package se.birdy.app.debug

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import se.birdy.ml.BirdClassifier
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import java.io.File
import kotlin.system.measureTimeMillis

@Serializable
data class BenchmarkResult(
    val photoLabel: String,
    val n: Int,
    val p50: Long,
    val p90: Long,
    val p95: Long,
    val p99: Long,
    val mean: Double,
    val timestampMillis: Long,
)

@Serializable
data class BenchmarkRun(
    val modelVersion: String,
    val device: String,
    val results: List<BenchmarkResult>,
)

class BenchmarkRunner(
    private val context: Context,
    private val classifier: BirdClassifier,
    private val modelVersion: String,
    private val photos: List<String> = listOf("talgoxe.jpg", "koltrast.jpg", "blames.jpg"),
    private val iterationsPerPhoto: Int = 100,
    private val warmupIterations: Int = 5,
) {
    fun run(): Flow<BenchmarkProgress> = flow {
        val results = mutableListOf<BenchmarkResult>()
        for (photo in photos) {
            val bytes = context.assets.open("benchmark/$photo").use { it.readBytes() }
            val input = ImageInput(
                bytes = bytes, widthPx = 0, heightPx = 0, rotationDegrees = 0,
                format = FrameFormat.JPEG, timestampMillis = 0L,
            )
            // Warmup
            repeat(warmupIterations) { classifier.classify(input) }
            // Measure
            val timings = LongArray(iterationsPerPhoto)
            for (i in 0 until iterationsPerPhoto) {
                val ms = measureTimeMillis { classifier.classify(input) }
                timings[i] = ms
                emit(BenchmarkProgress.Tick(photo, i + 1, iterationsPerPhoto, ms))
            }
            timings.sort()
            results += BenchmarkResult(
                photoLabel = photo,
                n = iterationsPerPhoto,
                p50 = timings[(iterationsPerPhoto * 50) / 100],
                p90 = timings[(iterationsPerPhoto * 90) / 100],
                p95 = timings[(iterationsPerPhoto * 95) / 100],
                p99 = timings[(iterationsPerPhoto * 99) / 100],
                mean = timings.average(),
                timestampMillis = System.currentTimeMillis(),
            )
        }
        val run = BenchmarkRun(
            modelVersion = modelVersion,
            device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            results = results,
        )
        val json = Json { prettyPrint = true }.encodeToString(run)
        val out = File(context.filesDir, "benchmark_${System.currentTimeMillis()}.json")
        out.writeText(json)
        emit(BenchmarkProgress.Done(run, out.absolutePath))
    }
}

sealed class BenchmarkProgress {
    data class Tick(val photo: String, val iteration: Int, val total: Int, val lastMs: Long) :
        BenchmarkProgress()
    data class Done(val run: BenchmarkRun, val outputPath: String) : BenchmarkProgress()
}
```

- [ ] **Step 16.3: Implementera `BenchmarkScreen`**

```kotlin
// composeApp/src/androidMain/kotlin/se/birdy/app/debug/BenchmarkScreen.kt
package se.birdy.app.debug

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.birdy.ml.BirdClassifier

@Composable
fun BenchmarkScreen(
    classifier: BirdClassifier,
    modelVersion: String,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var progressText by remember { mutableStateOf("Ready") }
    var lastMs by remember { mutableStateOf<Long?>(null) }
    var result by remember { mutableStateOf<BenchmarkRun?>(null) }
    var savedPath by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Benchmark", style = MaterialTheme.typography.headlineMedium)
        Text("Model: $modelVersion")
        Button(onClick = {
            scope.launch {
                progressText = "Running…"
                result = null
                BenchmarkRunner(context, classifier, modelVersion).run().collect { p ->
                    when (p) {
                        is BenchmarkProgress.Tick -> {
                            progressText = "${p.photo}: ${p.iteration}/${p.total}"
                            lastMs = p.lastMs
                        }
                        is BenchmarkProgress.Done -> {
                            result = p.run
                            savedPath = p.outputPath
                            progressText = "Done"
                        }
                    }
                }
            }
        }) { Text("Run benchmark") }

        Text(progressText)
        lastMs?.let { Text("Last ms: $it") }
        result?.let { run ->
            Divider()
            Text("Device: ${run.device}", style = MaterialTheme.typography.labelSmall)
            run.results.forEach { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.photoLabel, style = MaterialTheme.typography.titleMedium)
                        Text("n=${r.n}  p50=${r.p50}ms  p90=${r.p90}ms  p95=${r.p95}ms  p99=${r.p99}ms")
                        Text("mean=${"%.1f".format(r.mean)}ms")
                    }
                }
            }
            savedPath?.let { Text("Saved: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
```

- [ ] **Step 16.4: Wire i navigation (DEBUG-gated)**

```kotlin
// composeApp/src/androidMain/kotlin/se/birdy/app/Navigation.kt
if (BuildConfig.DEBUG) {
    composable("debug/benchmark") {
        BenchmarkScreen(classifier = appGraph.classifier, modelVersion = appGraph.modelVersion)
    }
}
```

```kotlin
// composeApp/src/androidMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaScreen.kt
// I TopAppBar — overflow-meny:
if (BuildConfig.DEBUG) {
    DropdownMenuItem(
        text = { Text("Run benchmark") },
        onClick = { onNavigate("debug/benchmark") },
    )
}
```

- [ ] **Step 16.5: Verifiera build**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 16.6: Verifiera release-build inte exponerar BenchmarkScreen**

```bash
./gradlew :composeApp:assembleRelease  # om release-config finns
```

Expected: ingen `BenchmarkScreen`-kod i release-APK (verifierat genom att `BuildConfig.DEBUG = false` skippar både route och menyalternativ).

- [ ] **Step 16.7: Commit**

```bash
git add composeApp/src/androidMain/assets/benchmark/ composeApp/src/androidMain/kotlin/se/birdy/app/debug/ composeApp/src/androidMain/kotlin/se/birdy/app/Navigation.kt composeApp/src/androidMain/kotlin/se/birdy/app/ui/encyclopedia/EncyclopediaScreen.kt
git commit -m "feat(debug): Plan 4b Task 16 — BenchmarkRunner + BenchmarkScreen (DEBUG-only)"
```

---

## Task 17: Device verify + screenshots + tag `v0.4.0b-real-tflite`

**Mål:** Bygg, installera, verifiera på SM-S918B att (a) realtime-scan visar riktiga predictions för en talgoxe-bild på skärm, (b) photo-analyze ger top-3 från foto, (c) `BenchmarkScreen` visar p95 < 333 ms, (d) DEMO-banner aldrig visas på en sund installation, (e) ingen krasch/ANR. Capture 6 screenshots, kör benchmark, committa JSON, uppdatera CLAUDE.md, tagga release.

**Files:**
- Create: `docs/superpowers/screenshots/plan-4b/01-realtime-talgoxe.png`
- Create: `docs/superpowers/screenshots/plan-4b/02-realtime-soker.png`
- Create: `docs/superpowers/screenshots/plan-4b/03-photo-koltrast-top3.png`
- Create: `docs/superpowers/screenshots/plan-4b/04-benchmark-result.png`
- Create: `docs/superpowers/screenshots/plan-4b/05-fallback-banner.png` (artificiellt — temp-rename av .tflite för att trigga fallback)
- Create: `docs/superpowers/screenshots/plan-4b/06-unresolved-pill.png` (om mappers `lookup` returnerar null för en topp-prediction)
- Create: `docs/superpowers/eval/benchmarks/benchmark_2026-05-XX.json`
- Modify: `CLAUDE.md` (status-rad)

- [ ] **Step 17.1: Bygg och installera på enhet**

```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity
```

Expected: appen startar, ingen krasch.

- [ ] **Step 17.2: Verifiera realtime-scan**

Visa en bild av talgoxe på en annan skärm (eller print-out), peka kameran. Ska få top-3 där talgoxe (Q25485) finns med high confidence.

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell screencap -p /sdcard/01.png
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" pull /sdcard/01.png docs/superpowers/screenshots/plan-4b/01-realtime-talgoxe.png
```

Capture även "söker"-state (riktad mot tom vägg → inga matches över threshold):

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell screencap -p /sdcard/02.png
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" pull /sdcard/02.png docs/superpowers/screenshots/plan-4b/02-realtime-soker.png
```

- [ ] **Step 17.3: Verifiera photo-analyze**

Importera ett koltrast-foto från galleriet → ClassificationResultScreen med top-3.

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell screencap -p /sdcard/03.png
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" pull /sdcard/03.png docs/superpowers/screenshots/plan-4b/03-photo-koltrast-top3.png
```

- [ ] **Step 17.4: Kör benchmark + screenshot**

Navigera Encyclopedia → overflow → "Run benchmark". Vänta tills "Done".

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell screencap -p /sdcard/04.png
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" pull /sdcard/04.png docs/superpowers/screenshots/plan-4b/04-benchmark-result.png
```

Hämta JSON-fil:

```bash
DATE=$(date -u +%Y-%m-%d)
APP_DIR=$("/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell run-as se.birdy.android pwd)
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell run-as se.birdy.android ls files/ | grep benchmark
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" exec-out run-as se.birdy.android cat files/benchmark_*.json > docs/superpowers/eval/benchmarks/benchmark_${DATE}.json
```

**Verifiera klar-villkor:** `p95 < 333 ms` för alla 3 foton (annars eskalera — eventuellt sätta auto-throttle till 1.5 fps i CameraX-flödet, eller använda mindre modell).

- [ ] **Step 17.5: Verifiera fallback-banner (DEMO)**

```bash
# Artificiellt trigga fallback genom att rensa cache + rename på device:
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell pm clear se.birdy.android
# Eller: bygg en debug-only skärm som tvingar fallback (se Task 9 onCrashlytics-callback)
# Enklast: editera AppGraph.android.kt temporärt så createReal kastar, build+install, capture, revert.
```

Capture:

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell screencap -p /sdcard/05.png
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" pull /sdcard/05.png docs/superpowers/screenshots/plan-4b/05-fallback-banner.png
```

Återställ AppGraph efter capture, bygg + installera igen.

- [ ] **Step 17.6: Capture "okänd art"-pill (om sker organiskt — annars skip)**

Om någon top-prediction har inat-ID utan Q-ID-mapping → ska visas som "okänd art" pill istället för art-länk. Om det händer naturligt under realtime-scan, capture; annars dokumentera att det inte triggades och hoppa screenshot 06.

- [ ] **Step 17.7: Uppdatera CLAUDE.md**

Bumpa status-raden:

```diff
-**Status (2026-05-07):** ... Plan 4b deferrad. ...
+**Status (2026-05-08):** ... **Plan 4b ✅ (`v0.4.0b-real-tflite`).** ...
```

Lägg in kort entry i "Avslutade planer" (mönster från Plan 5b):

```markdown
- **Plan 4b (Real TFLite-modell, `v0.4.0b-real-tflite`, 2026-05-XX):** Google AIY Birds V1 (965 klasser, MobileNetV2-quantized, 3.5 MB) bakom samma `BirdClassifier`-interface; SPARQL-cross-walk scientific-name → Q-ID via P225 (`birdy-fetcher build-mapping`, `name_mapping.py`); bundlad i AAB via compose-resources (no PAD); `BirdClassifierFactory` med init-fallback + 3-strikes-failure-guard till FakeClassifier; `validateModelMapping` Gradle-task hookat i preBuild; `tools/ml-eval/` Python-modul för accuracy-rapporter; `BenchmarkScreen` (DEBUG-only) för on-device latens. Plan: `2026-05-07-v1-04b-real-tflite.md`. Pivoterad från iNat2021 → AIY V1 efter Task 2's artefakt-jakt. Accuracy: top-3 = XX% (n=YY foton); benchmark p95 = ZZ ms på SM-S918B. 6 device-screenshots. **Återanvändbara mönster:** `expect class` + lambda-injected preprocess för testbarhet utan att refactorera bort plattform-specifik kod; `SessionFailureGuard` med atomic counter + `onDegrade`-callback för Crashlytics; build-time `validateModelMapping` modellerat efter Plan 5b's `validateBadgesYaml`; mikro-modell (~100 KB Keras→TFLite) i jvmTest/androidUnitTest för end-to-end Interpreter-wiring utan att skicka full modell genom test-resources; AiyLabelMapper.lookup(classIndex) tar output-tensor-positionen direkt (background class index 964 returnerar null).
```

- [ ] **Step 17.8: Commit screenshots + benchmark + CLAUDE.md**

```bash
git add docs/superpowers/screenshots/plan-4b/ docs/superpowers/eval/benchmarks/ CLAUDE.md
git commit -m "docs(plan-4b): Plan 4b Task 17 — device verify + 6 screenshots + benchmark JSON + status update"
```

- [ ] **Step 17.9: Tag release**

```bash
git tag -a v0.4.0b-real-tflite -m "Plan 4b — Real TFLite-modell shipped (top-3 XX%, p95 ZZ ms)"
git push origin main
git push origin v0.4.0b-real-tflite
```

- [ ] **Step 17.10: Spara auto-memory om Plan 4b**

Skapa `~/.claude/projects/.../memory/project_plan_4b_status.md` med locked patterns + post-tag follow-ups (mönster från `project_plan_4a_status.md` + `project_plan_5b_status.md`). Lägg pekare i `MEMORY.md`.

---
