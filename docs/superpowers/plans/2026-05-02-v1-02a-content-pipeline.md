# Birdy Bird Scanner — Plan 2a: Content Pipeline & Walking Skeleton

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a deterministic content pipeline that turns committed YAML art-data + images into a bundled `species.db` consumed by the app. End state: 5 walking-skeleton species (talgoxe, koltrast, blåmes, sångsvan, tornfalk) flow end-to-end from external sources → YAML → SQLite → Compose UI smoke test, with a green CI on the validation + build path.

**Architecture:** Two cleanly separated halves.
1. **Refresh half (Python, manual):** `tools/content-pipeline/` — uv-managed CLI (`birdy-fetcher`) that fetches Wikidata + Wikipedia + Wikimedia Commons, calls Claude Haiku for summarization, writes YAML files + images into `shared/content/`. Cached, resumable, with cost guardrails.
2. **Build half (Kotlin/JVM, deterministic):** `shared/content/` — Gradle tasks `validateSpeciesData` and `buildSpeciesDb` that read the committed YAML+images, validate strictly, and produce `composeApp/src/commonMain/composeResources/files/species.db` plus copied image assets. No network. <5s for 700 species. Backed by `SpeciesRepository` (SQLDelight + Flow) which Plan 3 will consume.

Plan 2b (later runbook) does the family-by-family content backfill. Plan 2a stops at 5 species + green build.

**Tech Stack:** Python 3.12 + uv + ruff + mypy + pytest + Anthropic SDK + aiohttp + Pillow + PyYAML + click. Kotlin 2.1.20 + kaml 0.65.0 + SQLDelight 2.0.2 + JUnit Jupiter 5.11.3. GitHub Actions.

> **Version note:** Lock these the day Task 1 starts. Run `uv self update` and check https://github.com/charleskorn/kaml/releases — bump patch versions only without re-reading the plan.

---

## Plan-of-plans context

This is **Plan 2a of 6** for v1. It owns the entire content pipeline *infrastructure* and a 5-species walking skeleton. **Plan 2b** (separate runbook, not a plan-file) handles the family-by-family backfill of ~700 species across ~25-30 PRs and ends with tag `v0.2.0-content`. Plans 3 (Encyclopedia), 4 (ML & Camera), 5 (Diary), 6 (i18n + release) follow.

Plan 2a leaves the project buildable + CI-green at every commit. After Plan 2a, `composeApp:assembleDebug` produces an APK that contains a 5-species `species.db`, and `SpeciesRepository.getById("Q25485")` returns Talgoxe with Swedish + English text.

---

## Improvements baked in (vs. raw spec)

These additions go beyond the design spec sek 9 task skiss:

1. **`birdy-fetcher doctor`** pre-flight check (Task 1, expanded in Task 8).
2. **`--max-cost <USD>`** abort guardrail (Task 6).
3. **`--dry-run`** mode (Task 6).
4. **Lock `kaml` as YAML library** for Kotlin side (Task 10) — no POC in Task 11.
5. **APK-size smoke test** during walking skeleton (Task 14, not deferred).
6. **`expected-species-count.txt`** mechanism so `validateSpeciesData` doesn't fail during Plan 2a's 5-species window (Task 11).
7. **JVM build-time code lives in `shared/content/src/jvmMain/`** with its own `jvmTest`, called from Gradle tasks via `JavaExec` against compiled `jvmMainClasses` — keeps logic unit-testable.

---

## File structure created by this plan

```
birdy-bird-scanner/
├── tools/                                  # NEW
│   └── content-pipeline/
│       ├── pyproject.toml                  # uv-managed, Python 3.12
│       ├── README.md                       # quickstart for the user
│       ├── .env.example
│       ├── .gitignore                      # cache/, .env
│       ├── .python-version
│       ├── sources/                        # IOC + VP11 rådata (committat)
│       │   ├── ioc-14.1.xlsx               # IOC World Bird List v14.1 (xlsx, ~11k spp)
│       │   └── vp11.pdf                    # BirdLife Sverige TK Västpalearktis-lista v11 (jun 2025, ~1190 WP-arter)
│       ├── checklists/
│       │   └── vp11-filter.yaml            # vilka VP11-status-koder att inkludera (H/h/F/R/(H))
│       ├── prompts/
│       │   ├── description-v1.md           # versioned prompt files
│       │   └── migration-v1.md
│       ├── src/birdy_fetcher/
│       │   ├── __init__.py
│       │   ├── cli.py                      # click entrypoint
│       │   ├── species_list.py
│       │   ├── wikidata.py
│       │   ├── wikipedia.py
│       │   ├── claude_summarizer.py
│       │   ├── images.py
│       │   ├── yaml_writer.py
│       │   ├── cache.py
│       │   ├── cost.py                     # cost tracker + max-cost guard
│       │   ├── doctor.py                   # pre-flight check
│       │   └── models.py                   # dataclasses
│       ├── tests/
│       │   ├── conftest.py
│       │   ├── fixtures/                   # mocked API payloads
│       │   └── test_*.py
│       ├── species_list.yaml               # generated, committed (Task 2-3)
│       └── mapping_failures.yaml           # generated, possibly empty
│
├── shared/content/                         # existing module, fills out
│   ├── build.gradle.kts                    # adds validateSpeciesData + buildSpeciesDb
│   ├── species/                            # walking skeleton: 5 yaml files (Task 9)
│   │   └── paridae/Q25485.yaml             # Parus major (talgoxe), etc
│   ├── images/                             # walking skeleton: hero + secondary (Task 9)
│   │   └── Q25485/hero.jpg
│   ├── overrides.yaml                      # placeholder, mostly empty in Plan 2a
│   ├── expected-species-count.txt          # holds "5" during 2a, "700" after 2b
│   ├── src/
│   │   ├── commonMain/
│   │   │   ├── kotlin/se/birdy/content/
│   │   │   │   ├── SpeciesRepository.kt    # interface
│   │   │   │   ├── SpeciesId.kt            # value class
│   │   │   │   ├── Locale.kt
│   │   │   │   ├── Abundance.kt
│   │   │   │   ├── SpeciesFilter.kt
│   │   │   │   ├── Species.kt              # data classes
│   │   │   │   └── SqlDelightSpeciesRepository.kt
│   │   │   └── sqldelight/se/birdy/content/
│   │   │       ├── Species.sq
│   │   │       ├── SpeciesName.sq
│   │   │       ├── SpeciesText.sq
│   │   │       ├── SpeciesRegion.sq
│   │   │       ├── SpeciesSeason.sq
│   │   │       ├── SpeciesImage.sq
│   │   │       └── SpeciesTaxonomy.sq
│   │   ├── jvmMain/kotlin/se/birdy/content/build/
│   │   │   ├── SpeciesYaml.kt              # @Serializable DTOs (kaml)
│   │   │   ├── SpeciesYamlParser.kt
│   │   │   ├── SpeciesValidator.kt
│   │   │   ├── ValidationError.kt
│   │   │   ├── SpeciesDbBuilder.kt
│   │   │   ├── ValidateMain.kt             # entry: validateSpeciesData
│   │   │   └── BuildMain.kt                # entry: buildSpeciesDb
│   │   └── jvmTest/
│   │       ├── kotlin/se/birdy/content/
│   │       │   ├── build/                  # parser, validator, db builder tests
│   │       │   └── SpeciesRepositoryTest.kt
│   │       └── resources/fixtures/
│   │           └── species/                # mini fixture set
│
├── composeApp/
│   └── src/commonMain/
│       ├── composeResources/files/         # NEW: bundled species.db + images
│       │   ├── species.db                  # generated by buildSpeciesDb
│       │   └── images/Q25485/hero.jpg      # copied by buildSpeciesDb
│       └── kotlin/se/birdy/app/ui/
│           └── HomeScreen.kt               # MODIFIED: shows count from db (smoke)
│
└── .github/workflows/
    ├── ci.yml                              # MODIFIED: validation + build species.db
    └── content-pipeline.yml                # NEW: pytest + ruff + mypy on tools/**
```

---

## Tasks

### Task 1: Bootstrap `tools/content-pipeline/` Python scaffold

**Files:**
- Create: `tools/content-pipeline/pyproject.toml`
- Create: `tools/content-pipeline/.python-version`
- Create: `tools/content-pipeline/.gitignore`
- Create: `tools/content-pipeline/.env.example`
- Create: `tools/content-pipeline/README.md`
- Create: `tools/content-pipeline/src/birdy_fetcher/__init__.py`
- Create: `tools/content-pipeline/src/birdy_fetcher/cli.py`
- Create: `tools/content-pipeline/tests/__init__.py`
- Create: `tools/content-pipeline/tests/conftest.py`
- Create: `tools/content-pipeline/tests/test_cli_smoke.py`
- Modify: root `.gitignore` (add `tools/content-pipeline/.cache/`, `tools/content-pipeline/.env`)

> **Pre-task user action:** confirm `python --version` ≥ 3.12 and `uv --version` runs. Install via `winget install Python.Python.3.12` and `winget install --id=astral-sh.uv` if missing.

- [ ] **Step 1: Write `tools/content-pipeline/.python-version`**

```
3.12
```

- [ ] **Step 2: Write `tools/content-pipeline/pyproject.toml`**

```toml
[project]
name = "birdy-fetcher"
version = "0.1.0"
description = "Birdy Bird Scanner content pipeline — fetch & generate species YAML"
requires-python = ">=3.12"
dependencies = [
    "click>=8.1.7",
    "anthropic>=0.40.0",
    "aiohttp>=3.10.10",
    "pyyaml>=6.0.2",
    "pillow>=11.0.0",
    "rich>=13.9.4",          # progress bar + status pretty-print
    "platformdirs>=4.3.6",
    "pydantic>=2.9.2",       # validated dataclasses for DTOs
    "pdfplumber>=0.11.4",    # VP11.pdf parsing (Task 2)
    "openpyxl>=3.1.5",       # IOC v14.1 xlsx parsing (Task 2)
]

[project.scripts]
birdy-fetcher = "birdy_fetcher.cli:main"

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[tool.hatch.build.targets.wheel]
packages = ["src/birdy_fetcher"]

[dependency-groups]
dev = [
    "pytest>=8.3.3",
    "pytest-asyncio>=0.24.0",
    "ruff>=0.7.4",
    "mypy>=1.13.0",
    "types-pyyaml>=6.0.12",
]

[tool.ruff]
line-length = 100
target-version = "py312"

[tool.ruff.lint]
select = ["E", "F", "I", "N", "UP", "B", "SIM", "RUF"]

[tool.mypy]
python_version = "3.12"
strict = true
disallow_untyped_defs = true
warn_unused_ignores = true
files = ["src", "tests"]

[tool.pytest.ini_options]
testpaths = ["tests"]
asyncio_mode = "auto"
addopts = "-v --strict-markers"
```

- [ ] **Step 3: Write `tools/content-pipeline/.gitignore`**

```
.cache/
.env
.venv/
__pycache__/
*.pyc
.pytest_cache/
.mypy_cache/
.ruff_cache/
dist/
*.egg-info/
hero_review/
sparse_content.yaml
```

- [ ] **Step 4: Write `tools/content-pipeline/.env.example`**

```bash
# Copy to .env (gitignored) and fill in.
# Get key from https://console.anthropic.com/settings/keys
ANTHROPIC_API_KEY=sk-ant-...

# Optional cost cap default (override per-invocation with --max-cost)
BIRDY_MAX_COST_USD=10.0
```

- [ ] **Step 5: Append to root `.gitignore`**

Append these two lines to `.gitignore` at repo root:

```
tools/content-pipeline/.cache/
tools/content-pipeline/.env
```

- [ ] **Step 6: Write `tools/content-pipeline/src/birdy_fetcher/__init__.py`**

```python
"""birdy-fetcher — content pipeline CLI for Birdy Bird Scanner."""

__version__ = "0.1.0"
```

- [ ] **Step 7: Write `tools/content-pipeline/src/birdy_fetcher/cli.py`**

```python
"""click-based CLI entrypoint. Subcommands stub out for later tasks."""

from __future__ import annotations

import click

from . import __version__


@click.group()
@click.version_option(__version__)
def main() -> None:
    """birdy-fetcher — fetch & generate species YAML for Birdy Bird Scanner."""


@main.command()
def doctor() -> None:
    """Run pre-flight checks (env vars, sources, cache health)."""
    click.echo("doctor: not implemented yet (Task 1 scaffold)")


@main.command()
@click.option("--resume", is_flag=True, help="Continue an aborted init.")
def init(resume: bool) -> None:
    """Build species_list.yaml from IOC + BirdLife checklists."""
    click.echo(f"init: not implemented yet (resume={resume})")


@main.command()
@click.option("--all", "all_species", is_flag=True)
@click.option("--species", multiple=True, help="Q-ID(s) to refresh.")
@click.option("--field", type=click.Choice(["text", "images", "all"]), default="all")
@click.option("--stale", is_flag=True, help="Only refresh entries older than 30 days.")
@click.option("--force", is_flag=True, help="Bypass cache.")
@click.option("--resume", is_flag=True, help="Resume an aborted refresh.")
@click.option("--workers", type=int, default=4)
@click.option("--max-cost", type=float, default=None, help="Abort if total cost exceeds USD.")
@click.option("--dry-run", is_flag=True, help="Print intended actions, no API calls.")
@click.option("--model", type=click.Choice(["haiku", "sonnet"]), default="haiku")
def refresh(**kwargs: object) -> None:
    """Refresh species data from external sources."""
    click.echo(f"refresh: not implemented yet (args={kwargs})")


@main.command()
def status() -> None:
    """Report on coverage, review status, cache health."""
    click.echo("status: not implemented yet")


@main.command()
def eval_prompts() -> None:
    """Generate ten prompt-tuning samples for manual review."""
    click.echo("eval-prompts: not implemented yet")


if __name__ == "__main__":
    main()
```

- [ ] **Step 8: Write `tools/content-pipeline/tests/__init__.py`** (empty file)

```python
```

- [ ] **Step 9: Write `tools/content-pipeline/tests/conftest.py`**

```python
"""Shared pytest fixtures."""

from __future__ import annotations

from pathlib import Path

import pytest


@pytest.fixture
def fixtures_dir() -> Path:
    return Path(__file__).parent / "fixtures"
```

- [ ] **Step 10: Write the failing smoke test `tools/content-pipeline/tests/test_cli_smoke.py`**

```python
"""Smoke test: CLI imports and shows --help without error."""

from __future__ import annotations

from click.testing import CliRunner

from birdy_fetcher.cli import main


def test_cli_help_runs() -> None:
    runner = CliRunner()
    result = runner.invoke(main, ["--help"])
    assert result.exit_code == 0
    assert "birdy-fetcher" in result.output


def test_doctor_subcommand_exists() -> None:
    runner = CliRunner()
    result = runner.invoke(main, ["doctor"])
    assert result.exit_code == 0


def test_refresh_dry_run_flag_exists() -> None:
    runner = CliRunner()
    result = runner.invoke(main, ["refresh", "--help"])
    assert result.exit_code == 0
    assert "--dry-run" in result.output
    assert "--max-cost" in result.output
```

- [ ] **Step 11: Run `uv sync` to install dependencies**

```bash
cd tools/content-pipeline
uv sync
```

Expected: creates `.venv/` and `uv.lock`. Lockfile should be committed.

- [ ] **Step 12: Run the smoke tests and watch them pass**

```bash
cd tools/content-pipeline
uv run pytest
```

Expected: 3 passed.

- [ ] **Step 13: Run ruff + mypy as a clean baseline**

```bash
cd tools/content-pipeline
uv run ruff check
uv run ruff format --check
uv run mypy
```

Expected: all green (or fix lint issues until they are).

- [ ] **Step 14: Write `tools/content-pipeline/README.md`**

````markdown
# birdy-fetcher

Content pipeline CLI for Birdy Bird Scanner. Generates `shared/content/species/**/*.yaml` and `shared/content/images/**/*.jpg` from IOC, BirdLife, Wikidata, Wikipedia, Wikimedia Commons, and Anthropic Claude.

## Setup (one-off)

1. Install Python 3.12 and uv (`winget install Python.Python.3.12`, `winget install --id=astral-sh.uv`).
2. Copy `.env.example` to `.env`, paste your Anthropic API key.
3. Source files at `sources/ioc-14.1.xlsx` (IOC World Bird List v14.1) and `sources/vp11.pdf` (BirdLife Sverige TK Västpalearktis-lista v11) — both committed to repo.
4. From this directory: `uv sync`.

## Common commands

```bash
uv run birdy-fetcher doctor                       # verify env + sources
uv run birdy-fetcher init                         # build species_list.yaml
uv run birdy-fetcher refresh --species Q25485     # one species end-to-end
uv run birdy-fetcher refresh --all --max-cost 5   # full refresh with cost cap
uv run birdy-fetcher refresh --dry-run --species Q25485  # plan only
uv run birdy-fetcher status                       # coverage report
```

## Dev loop

```bash
uv run pytest               # tests
uv run ruff check           # lint
uv run ruff format          # format
uv run mypy                 # type check
```
````

- [ ] **Step 15: Commit**

```bash
git add tools/content-pipeline/ .gitignore
git commit -m "build(content): scaffold birdy-fetcher Python pipeline (uv + click + ruff + mypy)"
```

---

### Task 2: Implement `init`: VP11 + IOC → `species_list.yaml`

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/models.py`
- Create: `tools/content-pipeline/src/birdy_fetcher/species_list.py`
- Create: `tools/content-pipeline/checklists/vp11-filter.yaml`
- Create: `tools/content-pipeline/tests/test_species_list.py`
- Create: `tools/content-pipeline/tests/fixtures/ioc_sample.xlsx` (genererad med openpyxl, se Step 4)
- Create: `tools/content-pipeline/tests/fixtures/vp11_sample.pdf` (genererad med reportlab, se Step 4)
- Create: `tools/content-pipeline/tests/fixtures/wikidata_sparql_response.json`
- Modify: `tools/content-pipeline/src/birdy_fetcher/cli.py` (wire `init`)

**Sources are already in place** at `tools/content-pipeline/sources/` (committed in `chore(content): commit VP11 + IOC source files` before this task):
- `ioc-14.1.xlsx`: IOC World Bird List v14.1 (Excel, full taxonomy ~11,000 species)
- `vp11.pdf`: BirdLife Sveriges Taxonomikommitté Västpalearktis-lista v11, 6 jun 2025 (~1,190 WP species)

**Pipeline shape (revised vs. raw spec):**

VP11 *is* the WP filter — auktoritativ list från BirdLife Sveriges TK, samma teamet som följer IOC-taxonomi. Spec's hypotetiska "BirdLife Sverige checklist CSV" finns inte i den formen; VP11 ersätter konceptet. Plus, VP11 är taxonomiskt **mer aktuell** (jun 2025) än IOC v14.1 (2024).

- **Parse VP11.pdf** → ~1,190 WP species med status (H/h/F/R/(H)), vetenskapligt namn, engelskt namn, family, ordning. Använder `pdfplumber` med kolumn-koordinater (x_status≤145, x_sci≤265, x_swe≤355, x_eng≤455). Svenska namn extraheras *inte* — PDF:ens font har defekt ToUnicode-mapping för åäö-glyfer (verifierat med POC). Svenska namn hämtas från Wikidata i Task 4 via P1843@sv.
- **Parse IOC v14.1 xlsx** → full IOC-taxonomi via `openpyxl`. Används som referens/cross-check (validera att VP11:s scientific_name finns i IOC, samma family).
- **Apply vp11-filter.yaml** → vilka VP11-status-koder inkluderas (default: alla H, h, F, (H); R=raritet exkluderas i v1).
- **Wikidata SPARQL** för Q-ID via `wdt:P225 ?sci_name` — oförändrat från ursprunglig spec.
- **Output**: `species_list.yaml` med `wikidata_id`, `scientific_name`, `family`, `ioc_order`, `common_en`, `vp_status`. Plus `mapping_failures.yaml` för arter där SPARQL inte hittade match.

> **No user action required during this task** — sources/ är redan committade. Agenten kör mot riktiga filer direkt.

- [ ] **Step 1: Write `tools/content-pipeline/src/birdy_fetcher/models.py`**

```python
"""Pydantic dataclasses for pipeline-internal types."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class IocEntry(BaseModel):
    """A row from the IOC master list (v14.1 xlsx)."""

    scientific_name: str
    family: str
    family_en: str | None = None
    ioc_order: str
    common_en: str


VpStatus = Literal["H", "h", "F", "R", "(H)"]


class Vp11Entry(BaseModel):
    """A species row from BirdLife Sveriges TK Västpalearktis-lista v11."""

    status: VpStatus
    ioc_order: str
    family: str
    scientific_name: str
    common_en: str
    notes: str = ""  # "Intr.", "E.", "†", "#1" etc


class SpeciesListEntry(BaseModel):
    """One mapped species in species_list.yaml."""

    wikidata_id: str | None = Field(default=None, description="Q-ID; null if mapping failed")
    scientific_name: str
    family: str
    ioc_order: str
    common_en: str
    vp_status: VpStatus  # H, h, F, R, (H) — drives default abundance heuristic in Task 8


class MappingFailure(BaseModel):
    scientific_name: str
    family: str
    common_en: str
    reason: str
```

- [ ] **Step 2: Write `tools/content-pipeline/checklists/vp11-filter.yaml`**

```yaml
# Filter applied to VP11 art-listan. Endast arter med en av dessa status-koder
# inkluderas i species_list.yaml. R (raritet) exkluderas i v1 — för få träffar
# i Sverige för att motivera content-arbete.
description: VP11-status-koder att inkludera i Birdy v1
include_statuses:
  - H        # häckfågel i WP
  - h        # häckningsstatus oklar/oregelbunden
  - F        # flyttfågel
  - "(H)"    # parentes — icke-etablerad häckare
# Avsiktligt exkluderade:
#   R   raritet — sällan sedda i WP, ej v1-prio
```

- [ ] **Step 3: Write the failing tests `tools/content-pipeline/tests/test_species_list.py`**

```python
"""Tests for species_list module — VP11 + IOC → species_list.yaml."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.species_list import (
    build_species_list,
    map_to_wikidata,
    parse_ioc,
    parse_vp11,
)


@pytest.fixture
def sample_ioc(fixtures_dir: Path) -> Path:
    return fixtures_dir / "ioc_sample.xlsx"


@pytest.fixture
def sample_vp11(fixtures_dir: Path) -> Path:
    return fixtures_dir / "vp11_sample.pdf"


def test_parse_ioc_returns_entries(sample_ioc: Path) -> None:
    entries = parse_ioc(sample_ioc)
    talgoxe = next(e for e in entries if e.scientific_name == "Parus major")
    assert talgoxe.family == "Paridae"
    assert talgoxe.ioc_order == "Passeriformes"
    assert talgoxe.common_en == "Great Tit"


def test_parse_vp11_returns_entries(sample_vp11: Path) -> None:
    entries = parse_vp11(sample_vp11)
    talgoxe = next(e for e in entries if e.scientific_name == "Parus major")
    assert talgoxe.status == "H"
    assert talgoxe.family == "Paridae"
    assert talgoxe.common_en == "Great Tit"
    # Status-distribution check
    assert {e.status for e in entries}.issubset({"H", "h", "F", "R", "(H)"})


def test_parse_vp11_extracts_notes(sample_vp11: Path) -> None:
    """Notes (Intr., E., †) extraheras korrekt."""
    entries = parse_vp11(sample_vp11)
    intr = [e for e in entries if "Intr." in e.notes]
    assert len(intr) >= 1, "fixture borde innehålla minst en Intr.-art"


@pytest.mark.asyncio
async def test_map_to_wikidata_uses_fixture(
    fixtures_dir: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """SPARQL is mocked; verify Q-ID extraction."""
    from birdy_fetcher import species_list

    fixture = (fixtures_dir / "wikidata_sparql_response.json").read_text()

    async def fake_sparql(query: str) -> str:
        return fixture

    monkeypatch.setattr(species_list, "_run_sparql", fake_sparql)

    result = await map_to_wikidata(["Parus major", "Cyanistes caeruleus"])
    assert result["Parus major"] == "Q25485"
    assert result["Cyanistes caeruleus"] == "Q25404"


@pytest.mark.asyncio
async def test_build_species_list_separates_failures(
    sample_ioc: Path,
    sample_vp11: Path,
    fixtures_dir: Path,
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """End-to-end: matched species map; R-status filtreras bort; oms aknade till failures."""
    from birdy_fetcher import species_list

    fixture = (fixtures_dir / "wikidata_sparql_response.json").read_text()

    async def fake_sparql(query: str) -> str:
        return fixture

    monkeypatch.setattr(species_list, "_run_sparql", fake_sparql)

    out_list = tmp_path / "species_list.yaml"
    out_failures = tmp_path / "mapping_failures.yaml"

    checklists_dir = fixtures_dir.parent.parent / "checklists"
    await build_species_list(
        ioc_xlsx=sample_ioc,
        vp11_pdf=sample_vp11,
        filter_yaml=checklists_dir / "vp11-filter.yaml",
        out_list=out_list,
        out_failures=out_failures,
    )

    assert out_list.exists()
    text = out_list.read_text()
    assert "Q25485" in text
    assert "Parus major" in text
    assert "vp_status: H" in text
```

- [ ] **Step 4: Generate fixture files**

PDF + xlsx-fixtures genereras programmatiskt en gång och committas binärt. Skapa skriptet `tools/content-pipeline/tests/fixtures/_generate.py`:

```python
"""Genererar ioc_sample.xlsx och vp11_sample.pdf. Kör en gång:
    uv run --with reportlab,openpyxl python tests/fixtures/_generate.py
Båda fixturerna committas binärt; skriptet finns för reproducerbarhet."""

from __future__ import annotations

from pathlib import Path

import openpyxl
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

HERE = Path(__file__).parent


def gen_ioc_xlsx() -> None:
    """Skapar en mini-IOC-xlsx med samma kolumn-layout som riktiga filen."""
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.append(["Order", "Family", "FamilyEN", "Scientific name", "English name"])
    rows = [
        ("Passeriformes", "Paridae", "Tits", "Parus major", "Great Tit"),
        ("Passeriformes", "Paridae", "Tits", "Cyanistes caeruleus", "Eurasian Blue Tit"),
        ("Passeriformes", "Turdidae", "Thrushes", "Turdus merula", "Common Blackbird"),
        ("Anseriformes", "Anatidae", "Ducks Geese and Swans", "Cygnus olor", "Mute Swan"),
        ("Falconiformes", "Falconidae", "Falcons", "Falco tinnunculus", "Common Kestrel"),
    ]
    for r in rows:
        ws.append(r)
    wb.save(HERE / "ioc_sample.xlsx")


def gen_vp11_pdf() -> None:
    """Skapar en mini-VP11-PDF med samma kolumn-x-koordinater som riktiga filen."""
    c = canvas.Canvas(str(HERE / "vp11_sample.pdf"), pagesize=A4)
    # Status @ x=110-145, sci @ 147-265, swe @ 268-355, eng @ 359-455, notes @ 457+
    # y växer nedåt i reportlab; rad-höjd ~12pt; använd y=800 → 700.
    c.setFont("Helvetica", 8)
    c.drawString(110, 815, "STATUS")
    c.drawString(147, 815, "VETENSKAPLIGT NAMN")
    c.drawString(268, 815, "SVENSKT NAMN")
    c.drawString(359, 815, "ENGELSKT NAMN")
    c.drawString(457, 815, "NOTES")
    rows = [
        # status, sci, swe (placeholder), eng, notes, family-row?
        ("Ordning PASSERIFORMES TÄTTINGAR", None),
        ("Familj Paridae mesar Tits", None),
        ("H", "Parus major", "talgoxe", "Great Tit", ""),
        ("H", "Cyanistes caeruleus", "blamesplaceholder", "Eurasian Blue Tit", ""),
        ("Familj Turdidae trastar Thrushes", None),
        ("H", "Turdus merula", "koltrastplaceholder", "Common Blackbird", ""),
        ("Ordning ANSERIFORMES ANDFAGLAR", None),
        ("Familj Anatidae anderplaceholder", None),
        ("H", "Cygnus olor", "knolsvanplaceholder", "Mute Swan", "Intr."),
        ("Ordning FALCONIFORMES FALKFAGLAR", None),
        ("Familj Falconidae falkar Falcons", None),
        ("H", "Falco tinnunculus", "tornfalkplaceholder", "Common Kestrel", ""),
        ("R", "Setophaga ruticilla", "rodstjartplaceholder", "American Redstart", ""),
    ]
    y = 790
    for row in rows:
        if row[1] is None:
            # Hierarki-rad — sätt i sci-kolumnen
            c.drawString(147, y, row[0])
        else:
            status, sci, swe, eng, notes = row
            c.drawString(110, y, status)
            c.drawString(147, y, sci)
            c.drawString(268, y, swe)  # latin-only placeholder; svenska skippas i parser
            c.drawString(359, y, eng)
            if notes:
                c.drawString(457, y, notes)
        y -= 12
    c.save()


if __name__ == "__main__":
    gen_ioc_xlsx()
    gen_vp11_pdf()
    print(f"Wrote {HERE/'ioc_sample.xlsx'}")
    print(f"Wrote {HERE/'vp11_sample.pdf'}")
```

Kör skriptet en gång och committa de två genererade binärfilerna tillsammans med `_generate.py`. Inga extra prod-deps (reportlab är en *engångs*-fixtur-generator, ej i `pyproject.toml`).

`tools/content-pipeline/tests/fixtures/wikidata_sparql_response.json`:

```json
{
  "head": {"vars": ["item", "scientificName"]},
  "results": {
    "bindings": [
      {
        "item": {"type": "uri", "value": "http://www.wikidata.org/entity/Q25485"},
        "scientificName": {"type": "literal", "value": "Parus major"}
      },
      {
        "item": {"type": "uri", "value": "http://www.wikidata.org/entity/Q25404"},
        "scientificName": {"type": "literal", "value": "Cyanistes caeruleus"}
      },
      {
        "item": {"type": "uri", "value": "http://www.wikidata.org/entity/Q25234"},
        "scientificName": {"type": "literal", "value": "Turdus merula"}
      },
      {
        "item": {"type": "uri", "value": "http://www.wikidata.org/entity/Q25402"},
        "scientificName": {"type": "literal", "value": "Cygnus olor"}
      },
      {
        "item": {"type": "uri", "value": "http://www.wikidata.org/entity/Q26490"},
        "scientificName": {"type": "literal", "value": "Falco tinnunculus"}
      }
    ]
  }
}
```

- [ ] **Step 5: Run tests to confirm they fail**

```bash
cd tools/content-pipeline
uv run pytest tests/test_species_list.py -v
```

Expected: 5 failures (parse_ioc, parse_vp11, parse_vp11_notes, map_to_wikidata, build_species_list) with `ImportError: cannot import name ... from 'birdy_fetcher.species_list'`.

- [ ] **Step 6: Implement `tools/content-pipeline/src/birdy_fetcher/species_list.py`**

```python
"""Build species_list.yaml from VP11.pdf + IOC v14.1 xlsx with Wikidata Q-ID mapping."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import cast

import aiohttp
import openpyxl
import pdfplumber
import yaml

from .models import (
    IocEntry,
    MappingFailure,
    SpeciesListEntry,
    Vp11Entry,
    VpStatus,
)

WIKIDATA_SPARQL = "https://query.wikidata.org/sparql"
USER_AGENT = "birdy-fetcher/0.1.0 (https://github.com/anonadrek/birdy)"

# VP11 column x-coordinates (verifierat med pdfplumber.extract_words på riktiga PDF:en)
VP11_X_STATUS_END = 145
VP11_X_SCI_END = 265
VP11_X_SWE_END = 355  # svenska kolumnen — vi extraherar inte (font-defekt)
VP11_X_ENG_END = 455
# notes: x >= 455

VALID_VP_STATUSES: set[VpStatus] = {"H", "h", "F", "R", "(H)"}

# Genus species (med valfri 3-ords subspecies) — strikt 2-3 ord lowercase efter Genus
SCI_NAME_RE = re.compile(r"^[A-Z][a-zA-Z\-']+\s+[a-z\-']+(?:\s+[a-z\-']+)?$")


def parse_ioc(xlsx_path: Path) -> list[IocEntry]:
    """Parse IOC v14.1 Excel master list. Header row defines columns."""
    wb = openpyxl.load_workbook(xlsx_path, read_only=True, data_only=True)
    ws = wb.active
    rows = ws.iter_rows(values_only=True)
    headers = [str(h).strip() if h else "" for h in next(rows)]
    out: list[IocEntry] = []
    for row in rows:
        d = dict(zip(headers, row, strict=False))
        sci = (d.get("Scientific name") or "").strip()
        if not sci:
            continue
        out.append(
            IocEntry(
                scientific_name=sci,
                family=(d.get("Family") or "").strip(),
                family_en=(d.get("FamilyEN") or "").strip() or None,
                ioc_order=(d.get("Order") or "").strip(),
                common_en=(d.get("English name") or "").strip(),
            )
        )
    return out


def parse_vp11(pdf_path: Path) -> list[Vp11Entry]:
    """Parse VP11.pdf with pdfplumber column coordinates.

    Svenska namn extraheras inte — PDF:en har defekt ToUnicode-mapping för åäö-glyfer.
    Svenska namn hämtas från Wikidata i Task 4. Här tar vi status/sci/eng/notes.
    """
    out: list[Vp11Entry] = []
    current_order: str | None = None
    current_family: str | None = None

    with pdfplumber.open(pdf_path) as pdf:
        for page in pdf.pages:
            words = page.extract_words(use_text_flow=False, keep_blank_chars=False)
            # Gruppera per rad med snap till 2pt
            lines: dict[int, list[dict[str, object]]] = {}
            for w in words:
                y_key = round(float(w["top"]) / 2) * 2
                lines.setdefault(y_key, []).append(w)
            for y in sorted(lines):
                row_words = sorted(lines[y], key=lambda w: float(w["x0"]))
                cols = _split_vp11_columns(row_words)
                sci = cols["sci"]

                # Hierarki-rader
                if sci.startswith("Ordning "):
                    m = re.match(r"^Ordning\s+([A-Z]+)", sci)
                    current_order = m.group(1) if m else None
                    continue
                if sci.startswith("Familj "):
                    m = re.match(r"^Familj\s+([A-Z][a-zA-Z\-']+)", sci)
                    current_family = m.group(1) if m else None
                    continue

                # Skippa header / fotnot
                if not sci or "VETENSKAPLIGT" in sci or "Scientific" in sci:
                    continue

                status = cols["status"]
                if status not in VALID_VP_STATUSES:
                    continue
                if not SCI_NAME_RE.match(sci):
                    continue
                if current_order is None or current_family is None:
                    continue

                out.append(
                    Vp11Entry(
                        status=cast(VpStatus, status),
                        ioc_order=current_order,
                        family=current_family,
                        scientific_name=sci,
                        common_en=cols["eng"],
                        notes=cols["notes"],
                    )
                )
    return out


def _split_vp11_columns(row_words: list[dict[str, object]]) -> dict[str, str]:
    """Splittra ord per VP11-kolumn baserat på x-koordinat."""
    buckets: dict[str, list[str]] = {"status": [], "sci": [], "eng": [], "notes": []}
    for w in row_words:
        x = float(w["x0"])
        text = str(w["text"])
        if x < VP11_X_STATUS_END:
            buckets["status"].append(text)
        elif x < VP11_X_SCI_END:
            buckets["sci"].append(text)
        elif x < VP11_X_SWE_END:
            pass  # svenska kolumnen — skippas (font-defekt på åäö)
        elif x < VP11_X_ENG_END:
            buckets["eng"].append(text)
        else:
            buckets["notes"].append(text)
    return {k: " ".join(v).strip() for k, v in buckets.items()}


def cross_check_with_ioc(
    vp11_entries: list[Vp11Entry], ioc_entries: list[IocEntry]
) -> tuple[list[Vp11Entry], list[MappingFailure]]:
    """Filtrera VP11-arter där vetenskapligt namn finns i IOC. Skillnader → failures."""
    ioc_by_sci = {e.scientific_name: e for e in ioc_entries}
    matched: list[Vp11Entry] = []
    failures: list[MappingFailure] = []
    for v in vp11_entries:
        if v.scientific_name in ioc_by_sci:
            matched.append(v)
        else:
            failures.append(
                MappingFailure(
                    scientific_name=v.scientific_name,
                    family=v.family,
                    common_en=v.common_en,
                    reason="VP11 sci_name not found in IOC v14.1 — taxonomy drift?",
                )
            )
    return matched, failures


def _build_sparql_query(scientific_names: list[str]) -> str:
    values = " ".join(f'"{n}"' for n in scientific_names)
    return f"""
    SELECT ?item ?scientificName WHERE {{
      VALUES ?scientificName {{ {values} }}
      ?item wdt:P225 ?scientificName .
      ?item wdt:P31/wdt:P279* wd:Q16521 .
    }}
    """


async def _run_sparql(query: str) -> str:
    """Run a SPARQL query against Wikidata. Override in tests."""
    async with aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session:
        async with session.get(
            WIKIDATA_SPARQL,
            params={"query": query, "format": "json"},
            timeout=aiohttp.ClientTimeout(total=60),
        ) as response:
            response.raise_for_status()
            return await response.text()


async def map_to_wikidata(scientific_names: list[str]) -> dict[str, str]:
    """Return mapping from scientific_name → Q-ID. Missing names omitted."""
    if not scientific_names:
        return {}
    query = _build_sparql_query(scientific_names)
    raw = await _run_sparql(query)
    data = json.loads(raw)
    result: dict[str, str] = {}
    for binding in data["results"]["bindings"]:
        sn = binding["scientificName"]["value"]
        uri = binding["item"]["value"]
        q_id = uri.rsplit("/", 1)[-1]
        result[sn] = q_id
    return result


async def build_species_list(
    *,
    ioc_xlsx: Path,
    vp11_pdf: Path,
    filter_yaml: Path,
    out_list: Path,
    out_failures: Path,
    batch_size: int = 50,
) -> None:
    ioc = parse_ioc(ioc_xlsx)
    vp11 = parse_vp11(vp11_pdf)
    filter_data = yaml.safe_load(filter_yaml.read_text(encoding="utf-8"))
    include_statuses: set[str] = set(filter_data["include_statuses"])

    # Filter VP11 by status
    vp11_filtered = [v for v in vp11 if v.status in include_statuses]

    # Cross-check mot IOC
    matched, ioc_failures = cross_check_with_ioc(vp11_filtered, ioc)

    # Wikidata Q-ID mapping
    qid_map: dict[str, str] = {}
    for i in range(0, len(matched), batch_size):
        batch = [v.scientific_name for v in matched[i : i + batch_size]]
        qid_map.update(await map_to_wikidata(batch))

    entries: list[SpeciesListEntry] = []
    wikidata_failures: list[MappingFailure] = []
    for v in matched:
        qid = qid_map.get(v.scientific_name)
        if qid is None:
            wikidata_failures.append(
                MappingFailure(
                    scientific_name=v.scientific_name,
                    family=v.family,
                    common_en=v.common_en,
                    reason="no Wikidata match for P225",
                )
            )
            continue
        entries.append(
            SpeciesListEntry(
                wikidata_id=qid,
                scientific_name=v.scientific_name,
                family=v.family,
                ioc_order=v.ioc_order,
                common_en=v.common_en,
                vp_status=v.status,
            )
        )

    out_list.parent.mkdir(parents=True, exist_ok=True)
    out_list.write_text(
        yaml.safe_dump(
            [e.model_dump(exclude_none=True) for e in entries],
            sort_keys=False,
            allow_unicode=True,
        ),
        encoding="utf-8",
    )
    all_failures = ioc_failures + wikidata_failures
    out_failures.write_text(
        _failures_header()
        + yaml.safe_dump(
            [f.model_dump() for f in all_failures],
            sort_keys=False,
            allow_unicode=True,
        ),
        encoding="utf-8",
    )


def _failures_header() -> str:
    return (
        "# Each entry below is a species the automatic mapper could not match.\n"
        "# To fix:\n"
        "#   1. Search wikidata.org for the scientific name → note the Q-ID\n"
        "#   2. Add an entry to species_list.yaml manually\n"
        "#   3. Delete the entry from this file\n"
        "# After all entries are resolved, run: uv run birdy-fetcher init --resume\n"
        "---\n"
    )


async def cli_init(
    *,
    sources_dir: Path,
    checklists_dir: Path,
    out_dir: Path,
    resume: bool = False,
) -> int:
    """Returns exit code: 0 if no failures, 1 if mapping_failures.yaml is non-empty."""
    out_list = out_dir / "species_list.yaml"
    out_failures = out_dir / "mapping_failures.yaml"
    if resume and not out_failures.exists():
        raise RuntimeError("nothing to resume — mapping_failures.yaml not found")

    await build_species_list(
        ioc_xlsx=sources_dir / "ioc-14.1.xlsx",
        vp11_pdf=sources_dir / "vp11.pdf",
        filter_yaml=checklists_dir / "vp11-filter.yaml",
        out_list=out_list,
        out_failures=out_failures,
    )

    failures = yaml.safe_load(out_failures.read_text(encoding="utf-8")) or []
    return 0 if not failures else 1
```

- [ ] **Step 7: Wire `init` into `cli.py`**

Replace the stub `init` function in `tools/content-pipeline/src/birdy_fetcher/cli.py` with:

```python
@main.command()
@click.option("--resume", is_flag=True, help="Continue an aborted init.")
def init(resume: bool) -> None:
    """Build species_list.yaml from IOC + BirdLife checklists."""
    import asyncio
    from pathlib import Path

    from .species_list import cli_init

    root = Path(__file__).resolve().parent.parent.parent
    exit_code = asyncio.run(
        cli_init(
            sources_dir=root / "sources",
            checklists_dir=root / "checklists",
            out_dir=root,
            resume=resume,
        )
    )
    if exit_code != 0:
        click.secho(
            "Mapping failures present — patch mapping_failures.yaml then re-run with --resume.",
            fg="yellow",
        )
        raise click.exceptions.Exit(exit_code)
    click.secho("species_list.yaml generated, all species mapped.", fg="green")
```

- [ ] **Step 8: Run tests until green**

```bash
cd tools/content-pipeline
uv run pytest tests/test_species_list.py -v
uv run mypy
uv run ruff check
```

Expected: 4 passed; mypy + ruff clean.

- [ ] **Step 9: Commit**

```bash
git add tools/content-pipeline/
git commit -m "feat(content): species_list builder with VP11 + IOC + Wikidata Q-ID mapping"
```

> **Note for Task 3:** real `sources/ioc-14.1.xlsx` och `sources/vp11.pdf` är redan committade (innan Task 1). Task 3 kör bara `init` mot dem.

---

### Task 3: USER CHECKPOINT — run `init`, patch `mapping_failures.yaml`, commit `species_list.yaml`

**Type:** human-in-the-loop. The agent does not implement code in this task; it shepherds the user through a manual content step and lands the result in git.

**Files:**
- Modify (manual): `tools/content-pipeline/mapping_failures.yaml`
- Commit (generated): `tools/content-pipeline/species_list.yaml`

> **Pre-task state:** `sources/ioc-14.1.xlsx` och `sources/vp11.pdf` är redan committade. Task 2:s `init`-implementation läser direkt från dem. Om IOC-xlsx-kolumnerna inte matchar antagandet (`Order, Family, FamilyEN, Scientific name, English name`), uppdaterar agenten `parse_ioc()` i en fix-up-commit innan Step 1.

- [ ] **Step 1: Run `init` against real sources**

```bash
cd tools/content-pipeline
uv run birdy-fetcher init
```

Expected: exits 0 (clean) or 1 (mapping failures). `species_list.yaml` and `mapping_failures.yaml` are written to `tools/content-pipeline/`.

- [ ] **Step 2: Read `mapping_failures.yaml`** and report counts to user

The agent reads the file and reports: `N species without Wikidata Q-ID, top families: ...`. Common causes:
- Recent taxonomic splits where Wikidata still uses the old name
- Subspecies treated as full species in IOC
- Hybrids or escaped exotics on BirdLife list

- [ ] **Step 3: USER patches `mapping_failures.yaml` (or `species_list.yaml` directly)**

For each failure: search wikidata.org manually, find the Q-ID, append a corrected entry to `species_list.yaml` with `wikidata_id: Q...`. Or accept the gap (delete from list).

User-facing instructions go in `mapping_failures.yaml` as a comment header:

```yaml
# Each entry below is a species the automatic mapper could not match to Wikidata.
# To fix:
#   1. Search wikidata.org for the scientific name → note the Q-ID
#   2. Add an entry to species_list.yaml manually
#   3. Delete the entry from this file
# After all entries are resolved, run: uv run birdy-fetcher init --resume
```

(The agent should write that header preface during Task 2 implementation. If missing, fix it now.)

- [ ] **Step 4: Re-run `init --resume`** (verifies cleanness)

```bash
uv run birdy-fetcher init --resume
```

Expected: exit 0, `mapping_failures.yaml` is empty (or only kept entries explicitly marked accept-missing).

- [ ] **Step 5: Commit the curated species_list**

```bash
git add tools/content-pipeline/species_list.yaml tools/content-pipeline/mapping_failures.yaml
git commit -m "data(content): commit curated species_list.yaml (~700 spp) from VP11 + IOC"
```

(Note: `sources/ioc-14.1.xlsx` + `sources/vp11.pdf` är redan committade i en tidigare commit.)

---

### Task 4: Implement `wikidata.py` (structured fetch with cache)

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/cache.py`
- Create: `tools/content-pipeline/src/birdy_fetcher/wikidata.py`
- Create: `tools/content-pipeline/tests/test_cache.py`
- Create: `tools/content-pipeline/tests/test_wikidata.py`
- Create: `tools/content-pipeline/tests/fixtures/wikidata_q25372.json`

`wikidata.py` fetches structured per-species data: taxonomy (genus, family, order), IUCN status, P18 (canonical image filename). Cache lives at `tools/content-pipeline/.cache/{Q-ID}/wikidata.json`.

- [ ] **Step 1: Write the failing test `tools/content-pipeline/tests/test_cache.py`**

```python
"""Cache atomicity + key construction tests."""

from __future__ import annotations

from pathlib import Path

from birdy_fetcher.cache import Cache


def test_cache_write_then_read_round_trip(tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    cache.put("Q25485", "wikidata.json", '{"foo": "bar"}')
    assert cache.has("Q25485", "wikidata.json")
    assert cache.get("Q25485", "wikidata.json") == '{"foo": "bar"}'


def test_cache_write_is_atomic(tmp_path: Path) -> None:
    """A write must not leave partial files even if interrupted."""
    cache = Cache(tmp_path)
    cache.put("Q25485", "wikidata.json", '{"x": 1}')
    children = list((tmp_path / "Q25485").iterdir())
    assert all(not c.name.endswith(".tmp") for c in children)


def test_cache_miss_returns_none(tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    assert not cache.has("Q99999", "wikidata.json")
    assert cache.get("Q99999", "wikidata.json") is None


def test_cache_invalidation_by_age(tmp_path: Path) -> None:
    import os
    import time

    cache = Cache(tmp_path)
    cache.put("Q25485", "wikidata.json", "{}")
    path = tmp_path / "Q25485" / "wikidata.json"
    old = time.time() - (40 * 86400)
    os.utime(path, (old, old))
    assert cache.is_stale("Q25485", "wikidata.json", max_age_days=30)
    assert not cache.is_stale("Q25485", "wikidata.json", max_age_days=60)
```

- [ ] **Step 2: Run the failing test**

```bash
cd tools/content-pipeline
uv run pytest tests/test_cache.py -v
```

Expected: 4 failures with `ImportError`.

- [ ] **Step 3: Implement `tools/content-pipeline/src/birdy_fetcher/cache.py`**

```python
"""Filesystem cache for fetcher steps. One directory per species, one file per step."""

from __future__ import annotations

import os
import time
from pathlib import Path


class Cache:
    """A simple atomic-write filesystem cache keyed by (q_id, filename)."""

    def __init__(self, root: Path) -> None:
        self.root = root

    def _path(self, q_id: str, filename: str) -> Path:
        return self.root / q_id / filename

    def has(self, q_id: str, filename: str) -> bool:
        return self._path(q_id, filename).exists()

    def get(self, q_id: str, filename: str) -> str | None:
        path = self._path(q_id, filename)
        if not path.exists():
            return None
        return path.read_text(encoding="utf-8")

    def put(self, q_id: str, filename: str, content: str) -> None:
        path = self._path(q_id, filename)
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(path.suffix + ".tmp")
        tmp.write_text(content, encoding="utf-8")
        os.replace(tmp, path)

    def get_bytes(self, q_id: str, filename: str) -> bytes | None:
        path = self._path(q_id, filename)
        if not path.exists():
            return None
        return path.read_bytes()

    def put_bytes(self, q_id: str, filename: str, content: bytes) -> None:
        path = self._path(q_id, filename)
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(path.suffix + ".tmp")
        tmp.write_bytes(content)
        os.replace(tmp, path)

    def is_stale(self, q_id: str, filename: str, *, max_age_days: int) -> bool:
        path = self._path(q_id, filename)
        if not path.exists():
            return True
        age_seconds = time.time() - path.stat().st_mtime
        return age_seconds > max_age_days * 86400
```

- [ ] **Step 4: Run cache tests until green**

```bash
uv run pytest tests/test_cache.py -v
```

Expected: 4 passed.

- [ ] **Step 5: Write fixture `tools/content-pipeline/tests/fixtures/wikidata_q25372.json`**

```json
{
  "head": {"vars": ["taxonName", "family", "familyLabel", "genus", "genusLabel", "ordo", "ordoLabel", "iucnStatus", "iucnStatusLabel", "image"]},
  "results": {
    "bindings": [{
      "taxonName": {"type": "literal", "value": "Parus major"},
      "family": {"type": "uri", "value": "http://www.wikidata.org/entity/Q193353"},
      "familyLabel": {"type": "literal", "value": "Paridae"},
      "genus": {"type": "uri", "value": "http://www.wikidata.org/entity/Q193357"},
      "genusLabel": {"type": "literal", "value": "Parus"},
      "ordo": {"type": "uri", "value": "http://www.wikidata.org/entity/Q25379"},
      "ordoLabel": {"type": "literal", "value": "Passeriformes"},
      "iucnStatus": {"type": "uri", "value": "http://www.wikidata.org/entity/Q211005"},
      "iucnStatusLabel": {"type": "literal", "value": "least concern"},
      "image": {"type": "uri", "value": "http://commons.wikimedia.org/wiki/Special:FilePath/Parus%20major%20-%20Mindelheim%20-%202012.jpg"}
    }]
  }
}
```

- [ ] **Step 6: Write the failing test `tools/content-pipeline/tests/test_wikidata.py`**

```python
"""Tests for wikidata.py — SPARQL parsing + caching."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.cache import Cache
from birdy_fetcher.wikidata import WikidataClient


@pytest.mark.asyncio
async def test_fetch_structured_uses_fixture(
    fixtures_dir: Path,
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    fixture = (fixtures_dir / "wikidata_q25372.json").read_text()

    async def fake_sparql(query: str) -> str:
        return fixture

    cache = Cache(tmp_path)
    client = WikidataClient(cache=cache, run_sparql=fake_sparql)

    result = await client.fetch_structured("Q25485")
    assert result.q_id == "Q25485"
    assert result.family == "Paridae"
    assert result.genus == "Parus"
    assert result.ioc_order == "Passeriformes"
    assert result.iucn_status == "LC"
    assert result.image_filename == "Parus major - Mindelheim - 2012.jpg"


@pytest.mark.asyncio
async def test_fetch_structured_uses_cache_on_second_call(
    fixtures_dir: Path,
    tmp_path: Path,
) -> None:
    fixture = (fixtures_dir / "wikidata_q25372.json").read_text()

    call_count = {"n": 0}

    async def counting_sparql(query: str) -> str:
        call_count["n"] += 1
        return fixture

    cache = Cache(tmp_path)
    client = WikidataClient(cache=cache, run_sparql=counting_sparql)
    await client.fetch_structured("Q25485")
    await client.fetch_structured("Q25485")
    assert call_count["n"] == 1


@pytest.mark.asyncio
async def test_force_bypasses_cache(
    fixtures_dir: Path,
    tmp_path: Path,
) -> None:
    fixture = (fixtures_dir / "wikidata_q25372.json").read_text()

    call_count = {"n": 0}

    async def counting_sparql(query: str) -> str:
        call_count["n"] += 1
        return fixture

    cache = Cache(tmp_path)
    client = WikidataClient(cache=cache, run_sparql=counting_sparql)
    await client.fetch_structured("Q25485")
    await client.fetch_structured("Q25485", force=True)
    assert call_count["n"] == 2
```

- [ ] **Step 7: Run the failing test**

```bash
uv run pytest tests/test_wikidata.py -v
```

Expected: 3 failures.

- [ ] **Step 8: Implement `tools/content-pipeline/src/birdy_fetcher/wikidata.py`**

```python
"""Wikidata structured fetch — taxonomy, IUCN, P18 image filename."""

from __future__ import annotations

import json
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from urllib.parse import unquote

import aiohttp

from .cache import Cache

WIKIDATA_SPARQL = "https://query.wikidata.org/sparql"
USER_AGENT = "birdy-fetcher/0.1.0 (https://github.com/anonadrek/birdy)"

IUCN_LABEL_TO_CODE = {
    "least concern": "LC",
    "near threatened": "NT",
    "vulnerable": "VU",
    "endangered": "EN",
    "critically endangered": "CR",
    "data deficient": "DD",
    "not evaluated": "NE",
    "extinct": "EX",
    "extinct in the wild": "EW",
}


@dataclass(frozen=True)
class WikidataStructured:
    q_id: str
    scientific_name: str
    family: str
    genus: str
    ioc_order: str
    iucn_status: str
    image_filename: str | None


SparqlRunner = Callable[[str], Awaitable[str]]


async def _default_run_sparql(query: str) -> str:
    async with aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session:
        async with session.get(
            WIKIDATA_SPARQL,
            params={"query": query, "format": "json"},
            timeout=aiohttp.ClientTimeout(total=60),
        ) as response:
            response.raise_for_status()
            return await response.text()


class WikidataClient:
    def __init__(
        self,
        *,
        cache: Cache,
        run_sparql: SparqlRunner | None = None,
    ) -> None:
        self.cache = cache
        self._run_sparql = run_sparql or _default_run_sparql

    async def fetch_structured(
        self,
        q_id: str,
        *,
        force: bool = False,
    ) -> WikidataStructured:
        if not force and self.cache.has(q_id, "wikidata.json"):
            raw = self.cache.get(q_id, "wikidata.json")
            assert raw is not None
        else:
            query = self._build_query(q_id)
            raw = await self._run_sparql(query)
            self.cache.put(q_id, "wikidata.json", raw)
        return self._parse(q_id, raw)

    @staticmethod
    def _build_query(q_id: str) -> str:
        return f"""
        SELECT ?taxonName ?family ?familyLabel ?genus ?genusLabel ?ordo ?ordoLabel
               ?iucnStatus ?iucnStatusLabel ?image WHERE {{
          BIND(wd:{q_id} AS ?taxon)
          ?taxon wdt:P225 ?taxonName ;
                 wdt:P171* ?family .
          ?family wdt:P105 wd:Q35409 .
          ?taxon wdt:P171* ?genus .
          ?genus wdt:P105 wd:Q34740 .
          ?taxon wdt:P171* ?ordo .
          ?ordo wdt:P105 wd:Q36602 .
          OPTIONAL {{ ?taxon wdt:P141 ?iucnStatus . }}
          OPTIONAL {{ ?taxon wdt:P18 ?image . }}
          SERVICE wikibase:label {{ bd:serviceParam wikibase:language "en". }}
        }}
        LIMIT 1
        """

    @staticmethod
    def _parse(q_id: str, raw: str) -> WikidataStructured:
        data = json.loads(raw)
        bindings = data["results"]["bindings"]
        if not bindings:
            raise ValueError(f"No Wikidata structured data for {q_id}")
        b = bindings[0]
        iucn_label = b.get("iucnStatusLabel", {}).get("value", "").lower()
        iucn_code = IUCN_LABEL_TO_CODE.get(iucn_label, "NE")
        image_uri = b.get("image", {}).get("value", "")
        image_filename: str | None = None
        if image_uri:
            tail = image_uri.rsplit("/", 1)[-1]
            image_filename = unquote(tail)
        return WikidataStructured(
            q_id=q_id,
            scientific_name=b["taxonName"]["value"],
            family=b["familyLabel"]["value"],
            genus=b["genusLabel"]["value"],
            ioc_order=b["ordoLabel"]["value"],
            iucn_status=iucn_code,
            image_filename=image_filename,
        )
```

- [ ] **Step 9: Run wikidata tests until green + ruff + mypy**

```bash
uv run pytest tests/test_wikidata.py tests/test_cache.py -v
uv run ruff check
uv run mypy
```

Expected: 7 passed; lint clean.

- [ ] **Step 10: Commit**

```bash
git add tools/content-pipeline/
git commit -m "feat(content): wikidata client + filesystem cache with atomic writes"
```

---

### Task 5: Implement `wikipedia.py` (intro extraction with revision tracking)

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/wikipedia.py`
- Create: `tools/content-pipeline/tests/test_wikipedia.py`
- Create: `tools/content-pipeline/tests/fixtures/wikipedia_sv_parus_major.json`
- Create: `tools/content-pipeline/tests/fixtures/wikipedia_en_parus_major.json`

Wikipedia REST API gives us article extracts in JSON form with a revision id that we cache as the filename suffix (e.g. `wikipedia-sv-r12345678.html`). If the article is missing or the intro is <100 words, we mark it sparse and let the validator decide whether overrides cover the gap.

- [ ] **Step 1: Write fixture `tools/content-pipeline/tests/fixtures/wikipedia_sv_parus_major.json`**

```json
{
  "type": "standard",
  "title": "Talgoxe",
  "displaytitle": "<span><i>Parus major</i></span>",
  "wikibase_item": "Q25485",
  "extract": "Talgoxe (Parus major) är en av Sveriges vanligaste fåglar och kan ses året runt i trädgårdar, parker och skogsbryn. Den är lätt att känna igen på sin gula buk med svart slips som löper från hakan ner över bröstet — slipsen är bredare hos hannen än hos honan. Talgoxen är stark och utmärkt anpassad till människans miljöer; den utnyttjar fågelmatningar villigt och bygger gärna bo i fågelholkar. Som mes är talgoxen liten men aggressiv mot andra mesar och kan ofta ses jaga blåmesar från fettbollar. Häckningen sker mellan april och juli, då paret kan föda upp en eller två kullar med upp till tolv ungar per kull.",
  "content_urls": {
    "desktop": {"page": "https://sv.wikipedia.org/wiki/Talgoxe"}
  },
  "revision": "12345678"
}
```

- [ ] **Step 2: Write fixture `tools/content-pipeline/tests/fixtures/wikipedia_en_parus_major.json`**

```json
{
  "type": "standard",
  "title": "Great tit",
  "wikibase_item": "Q25485",
  "extract": "The great tit (Parus major) is a passerine bird in the tit family Paridae. It is a widespread and common species throughout Europe, the Middle East, Central Asia and East Asia. Generally, the great tit is non-migratory, and in most years the resident birds are joined in winter by birds from further north. The bird is easily recognised by its black head and neck, prominent white cheeks, olive upperparts and yellow underparts, with some variation amongst the numerous subspecies. It is predominantly insectivorous in summer, but consumes a wider range of food items in winter, including small hibernating bats. Like all tits, it is a cavity nester, breeding in a hole that is usually inside a tree.",
  "revision": "87654321"
}
```

- [ ] **Step 3: Write the failing test `tools/content-pipeline/tests/test_wikipedia.py`**

```python
"""Tests for wikipedia.py — REST extract fetching, revision-keyed caching."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.cache import Cache
from birdy_fetcher.wikipedia import WikipediaClient, WikipediaResult


@pytest.mark.asyncio
async def test_fetch_extract_returns_intro(
    fixtures_dir: Path, tmp_path: Path
) -> None:
    fixture = (fixtures_dir / "wikipedia_sv_parus_major.json").read_text()

    async def fake_get(url: str) -> str:
        return fixture

    cache = Cache(tmp_path)
    client = WikipediaClient(cache=cache, http_get=fake_get)
    result = await client.fetch_extract("Q25485", title_by_lang={"sv": "Talgoxe"}, lang="sv")
    assert result.lang == "sv"
    assert result.revision == "12345678"
    assert "Talgoxe" in result.extract
    assert result.word_count > 80
    assert not result.is_sparse


@pytest.mark.asyncio
async def test_fetch_extract_marks_sparse_below_threshold(
    tmp_path: Path,
) -> None:
    short = '{"extract": "Short.", "revision": "1"}'

    async def fake_get(url: str) -> str:
        return short

    cache = Cache(tmp_path)
    client = WikipediaClient(cache=cache, http_get=fake_get)
    result = await client.fetch_extract(
        "Q99999", title_by_lang={"sv": "Some bird"}, lang="sv"
    )
    assert result.is_sparse
    assert result.word_count < 100


@pytest.mark.asyncio
async def test_fetch_extract_handles_missing_article(tmp_path: Path) -> None:
    async def fake_get(url: str) -> str:
        raise FileNotFoundError("404 from REST API")

    cache = Cache(tmp_path)
    client = WikipediaClient(cache=cache, http_get=fake_get)
    result = await client.fetch_extract(
        "Q99999", title_by_lang={"sv": "Nonexistent"}, lang="sv"
    )
    assert result.extract == ""
    assert result.is_sparse
    assert result.revision is None


@pytest.mark.asyncio
async def test_fetch_extract_caches_per_revision(
    fixtures_dir: Path, tmp_path: Path
) -> None:
    fixture = (fixtures_dir / "wikipedia_sv_parus_major.json").read_text()

    call_count = {"n": 0}

    async def counting_get(url: str) -> str:
        call_count["n"] += 1
        return fixture

    cache = Cache(tmp_path)
    client = WikipediaClient(cache=cache, http_get=counting_get)
    r1 = await client.fetch_extract("Q25485", title_by_lang={"sv": "Talgoxe"}, lang="sv")
    r2 = await client.fetch_extract("Q25485", title_by_lang={"sv": "Talgoxe"}, lang="sv")
    assert r1.revision == r2.revision
    assert call_count["n"] == 1
```

- [ ] **Step 4: Run failing tests**

```bash
uv run pytest tests/test_wikipedia.py -v
```

Expected: 4 failures.

- [ ] **Step 5: Implement `tools/content-pipeline/src/birdy_fetcher/wikipedia.py`**

```python
"""Wikipedia REST API client — fetch article intro extracts per language."""

from __future__ import annotations

import json
from collections.abc import Awaitable, Callable
from dataclasses import dataclass

import aiohttp

from .cache import Cache

USER_AGENT = "birdy-fetcher/0.1.0 (https://github.com/anonadrek/birdy)"
SPARSE_WORD_THRESHOLD = 100


@dataclass(frozen=True)
class WikipediaResult:
    q_id: str
    lang: str
    title: str
    extract: str
    revision: str | None
    word_count: int

    @property
    def is_sparse(self) -> bool:
        return self.word_count < SPARSE_WORD_THRESHOLD


HttpGet = Callable[[str], Awaitable[str]]


async def _default_http_get(url: str) -> str:
    async with aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session:
        async with session.get(url, timeout=aiohttp.ClientTimeout(total=30)) as response:
            if response.status == 404:
                raise FileNotFoundError(url)
            response.raise_for_status()
            return await response.text()


class WikipediaClient:
    def __init__(
        self,
        *,
        cache: Cache,
        http_get: HttpGet | None = None,
    ) -> None:
        self.cache = cache
        self._http_get = http_get or _default_http_get

    async def fetch_extract(
        self,
        q_id: str,
        *,
        title_by_lang: dict[str, str],
        lang: str,
        force: bool = False,
    ) -> WikipediaResult:
        title = title_by_lang.get(lang)
        if not title:
            return WikipediaResult(q_id, lang, "", "", None, 0)

        url = f"https://{lang}.wikipedia.org/api/rest_v1/page/summary/{title}"
        try:
            raw = await self._http_get(url)
        except FileNotFoundError:
            return WikipediaResult(q_id, lang, title, "", None, 0)

        data = json.loads(raw)
        revision = str(data.get("revision")) if data.get("revision") else None
        extract = data.get("extract", "")
        word_count = len(extract.split())

        if revision:
            self.cache.put(q_id, f"wikipedia-{lang}-r{revision}.json", raw)

        return WikipediaResult(
            q_id=q_id,
            lang=lang,
            title=title,
            extract=extract,
            revision=revision,
            word_count=word_count,
        )
```

> **Cache-key note:** caching by revision means once Claude has summarized revision r12345678 we never re-fetch unless Wikipedia has a newer revision *and* `--force` or `--stale` is given. The "second call uses cache" test currently relies on the http_get not being called twice — for now that holds because we only call once per `fetch_extract`. If you later add a "check for newer revision" path, the test must be updated.

- [ ] **Step 6: Run wikipedia tests until green**

```bash
uv run pytest tests/test_wikipedia.py -v
uv run mypy
uv run ruff check
```

Expected: 4 passed; lint clean. (If `test_fetch_extract_caches_per_revision` is failing, double-check that the implementation only calls `_http_get` once per call AND the test calls `fetch_extract` twice — the simple impl above does call it twice, so cache it explicitly. Add a check at the start of `fetch_extract`: if any cache file matching `wikipedia-{lang}-r*.json` exists for `q_id`, return that. Update implementation accordingly.)

Implementation tweak — add this at the top of `fetch_extract` before the HTTP call:

```python
# Cache lookup: if any revision is cached for this lang, reuse it (revision check
# happens via --stale or --force, not on every call).
species_dir = self.cache.root / q_id
if not force and species_dir.exists():
    cached = sorted(species_dir.glob(f"wikipedia-{lang}-r*.json"))
    if cached:
        raw = cached[-1].read_text(encoding="utf-8")
        data = json.loads(raw)
        revision = str(data.get("revision")) if data.get("revision") else None
        extract = data.get("extract", "")
        word_count = len(extract.split())
        return WikipediaResult(q_id, lang, title, extract, revision, word_count)
```

Re-run tests; should be 4 passed.

- [ ] **Step 7: Commit**

```bash
git add tools/content-pipeline/
git commit -m "feat(content): wikipedia REST client with revision-keyed cache"
```

---

### Task 6: Implement `claude_summarizer.py` (Anthropic SDK + cost cap + dry-run)

**Files:**
- Create: `tools/content-pipeline/prompts/description-v1.md`
- Create: `tools/content-pipeline/prompts/migration-v1.md`
- Create: `tools/content-pipeline/src/birdy_fetcher/cost.py`
- Create: `tools/content-pipeline/src/birdy_fetcher/claude_summarizer.py`
- Create: `tools/content-pipeline/tests/test_cost.py`
- Create: `tools/content-pipeline/tests/test_claude_summarizer.py`

> **Modeller:** Default `claude-haiku-4-5-20251001`. `--model sonnet` → `claude-sonnet-4-6`. Pricing constants live in `cost.py` and need a yearly review.
>
> **User checkpoint:** the user reads & approves `prompts/description-v1.md` before any `--all` run is invoked in Plan 2b. The agent flags this in the commit message.

- [ ] **Step 1: Write `tools/content-pipeline/prompts/description-v1.md`**

```markdown
# description prompt v1

System: Du är en svensk fågelguide. Skriv 2-3 koncisa stycken (180-250 ord)
        om följande fågelart, riktat till en intresserad amatör. Fokusera på
        utseende, beteende, läte, och var arten ses. Undvik anekdoter och
        specifika geografiska platser. Använd "den" inte "han/hon". Skriv aldrig
        i första person.

        Om källtexten är < 200 ord, returnera en kortare beskrivning på
        80-120 ord. Hitta inte på fakta utöver källan. Om du är osäker, skriv
        kortare.

Few-shot examples (curated):

Talgoxe:
> Talgoxen är en av Sveriges vanligaste fåglar och syns året runt i trädgårdar,
> parker och skogsbryn. Den känns igen på sin gula buk med en svart "slips" som
> löper från hakan ner över bröstet; slipsen är bredare hos hannen.
>
> Talgoxen utnyttjar fågelmatningar villigt och bygger gärna bo i fågelholkar.
> Sången är ett ringande "ti-tit-tit, ti-tit-tit" som hörs tydligt i februari
> redan innan vintern släppt taget.
>
> Den lever främst på insekter under häckningssäsongen men byter till fett och
> frön under vintern. Som alla mesar är den hålruvare och kan föda upp tolv
> ungar i en kull.

Koltrast:
> [andra exempel-stycke, 180-250 ord, med samma struktur]

Blåmes:
> [tredje exempel-stycke, 180-250 ord, med samma struktur]

User: Art: {scientific_name} ({common_sv}, {common_en})
      Familj: {family_sv} ({family})
      Källtext (Wikipedia {lang}, intro):
      {wikipedia_intro_text}
```

> **Note:** the koltrast and blåmes few-shot blocks must be filled in by the user during Task 6 review (or hand-curated in Step 9). The agent commits this file with placeholder marker comments and a TODO until the user approves the final wording.

- [ ] **Step 2: Write `tools/content-pipeline/prompts/migration-v1.md`**

```markdown
# migration prompt v1

System: Du är en svensk fågelguide. Skriv 1-2 stycken (80-120 ord) om hur denna
        fågelart förekommer i Sverige under året — flyttning, övervintring,
        ankomstmånader. Strikt faktabaserat. Hitta inte på.

        Om källtexten saknar denna information helt, returnera exakt:
        "Migrationsdata saknas för denna art."

User: Art: {scientific_name} ({common_sv}, {common_en})
      Källtext (Wikipedia {lang}, intro):
      {wikipedia_intro_text}
```

- [ ] **Step 3: Write the failing test `tools/content-pipeline/tests/test_cost.py`**

```python
"""Tests for cost tracker — token accounting + max-cost guard."""

from __future__ import annotations

import pytest

from birdy_fetcher.cost import CostTracker, MaxCostExceeded


def test_tracker_estimates_haiku_cost_correctly() -> None:
    tracker = CostTracker(max_usd=None)
    tracker.record(model="haiku", input_tokens=5000, output_tokens=250)
    assert tracker.total_usd == pytest.approx(
        5000 / 1_000_000 * 0.80 + 250 / 1_000_000 * 4.00,
        rel=1e-6,
    )


def test_tracker_raises_when_max_exceeded() -> None:
    tracker = CostTracker(max_usd=0.005)
    tracker.record(model="haiku", input_tokens=5000, output_tokens=250)
    with pytest.raises(MaxCostExceeded):
        tracker.record(model="haiku", input_tokens=5000, output_tokens=250)


def test_tracker_no_cap_never_raises() -> None:
    tracker = CostTracker(max_usd=None)
    for _ in range(1000):
        tracker.record(model="haiku", input_tokens=5000, output_tokens=250)
    assert tracker.total_usd > 0
```

- [ ] **Step 4: Implement `tools/content-pipeline/src/birdy_fetcher/cost.py`**

```python
"""Cost tracker for Claude API calls. Aborts if --max-cost exceeded."""

from __future__ import annotations

from dataclasses import dataclass

# Anthropic published pricing (2026-01) — $/1M tokens. Update yearly.
_PRICING = {
    "haiku": {"input": 0.80, "output": 4.00},     # claude-haiku-4-5
    "sonnet": {"input": 3.00, "output": 15.00},   # claude-sonnet-4-6
}


class MaxCostExceeded(RuntimeError):
    pass


@dataclass
class CostTracker:
    max_usd: float | None
    total_usd: float = 0.0
    call_count: int = 0

    def record(self, *, model: str, input_tokens: int, output_tokens: int) -> None:
        prices = _PRICING[model]
        cost = (input_tokens / 1_000_000) * prices["input"] + (
            output_tokens / 1_000_000
        ) * prices["output"]
        self.total_usd += cost
        self.call_count += 1
        if self.max_usd is not None and self.total_usd > self.max_usd:
            raise MaxCostExceeded(
                f"Cost ${self.total_usd:.4f} exceeds cap ${self.max_usd:.4f} "
                f"after {self.call_count} calls"
            )
```

- [ ] **Step 5: Run cost tests until green**

```bash
uv run pytest tests/test_cost.py -v
```

Expected: 3 passed.

- [ ] **Step 6: Write the failing test `tools/content-pipeline/tests/test_claude_summarizer.py`**

```python
"""Tests for claude_summarizer.py — fake Anthropic client, dry-run, cost cap."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import pytest

from birdy_fetcher.cache import Cache
from birdy_fetcher.claude_summarizer import ClaudeSummarizer, FakeClaudeClient
from birdy_fetcher.cost import CostTracker, MaxCostExceeded


@dataclass
class FakeMessage:
    text: str = "Genererad beskrivning av talgoxen."
    input_tokens: int = 5000
    output_tokens: int = 250


@pytest.mark.asyncio
async def test_summarize_returns_text(tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    client = FakeClaudeClient(default=FakeMessage())
    tracker = CostTracker(max_usd=None)
    sum_ = ClaudeSummarizer(
        cache=cache,
        cost=tracker,
        client=client,
        prompt_dir=Path("prompts"),
        prompt_version="v1",
    )

    text = await sum_.summarize_description(
        q_id="Q25485",
        scientific_name="Parus major",
        common_sv="Talgoxe",
        common_en="Great Tit",
        family="Paridae",
        family_sv="Mesar",
        wikipedia_intro="Talgoxen är ...",
        lang="sv",
        model="haiku",
    )
    assert "Genererad" in text
    assert tracker.call_count == 1
    assert tracker.total_usd > 0


@pytest.mark.asyncio
async def test_summarize_uses_cache_on_second_call(tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    client = FakeClaudeClient(default=FakeMessage())
    tracker = CostTracker(max_usd=None)
    sum_ = ClaudeSummarizer(
        cache=cache,
        cost=tracker,
        client=client,
        prompt_dir=Path("prompts"),
        prompt_version="v1",
    )

    common_args = dict(
        q_id="Q25485",
        scientific_name="Parus major",
        common_sv="Talgoxe",
        common_en="Great Tit",
        family="Paridae",
        family_sv="Mesar",
        wikipedia_intro="Talgoxen är ...",
        lang="sv",
        model="haiku",
    )
    await sum_.summarize_description(**common_args)
    await sum_.summarize_description(**common_args)
    assert client.call_count == 1


@pytest.mark.asyncio
async def test_dry_run_makes_no_api_call(tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    client = FakeClaudeClient(default=FakeMessage())
    tracker = CostTracker(max_usd=None)
    sum_ = ClaudeSummarizer(
        cache=cache,
        cost=tracker,
        client=client,
        prompt_dir=Path("prompts"),
        prompt_version="v1",
        dry_run=True,
    )

    text = await sum_.summarize_description(
        q_id="Q25485",
        scientific_name="Parus major",
        common_sv="Talgoxe",
        common_en="Great Tit",
        family="Paridae",
        family_sv="Mesar",
        wikipedia_intro="Talgoxen är ...",
        lang="sv",
        model="haiku",
    )
    assert text == "[dry-run]"
    assert client.call_count == 0
    assert tracker.call_count == 0


@pytest.mark.asyncio
async def test_max_cost_aborts(tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    client = FakeClaudeClient(default=FakeMessage(input_tokens=10_000, output_tokens=500))
    tracker = CostTracker(max_usd=0.001)
    sum_ = ClaudeSummarizer(
        cache=cache,
        cost=tracker,
        client=client,
        prompt_dir=Path("prompts"),
        prompt_version="v1",
    )

    with pytest.raises(MaxCostExceeded):
        await sum_.summarize_description(
            q_id="Q25485",
            scientific_name="Parus major",
            common_sv="Talgoxe",
            common_en="Great Tit",
            family="Paridae",
            family_sv="Mesar",
            wikipedia_intro="x",
            lang="sv",
            model="haiku",
        )
```

- [ ] **Step 7: Run failing tests**

```bash
uv run pytest tests/test_claude_summarizer.py -v
```

Expected: 4 failures with import errors.

- [ ] **Step 8: Implement `tools/content-pipeline/src/birdy_fetcher/claude_summarizer.py`**

```python
"""Claude summarizer — Anthropic SDK wrapper with cache, cost cap, dry-run."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

from .cache import Cache
from .cost import CostTracker

MODEL_IDS = {
    "haiku": "claude-haiku-4-5-20251001",
    "sonnet": "claude-sonnet-4-6",
}


@dataclass
class ClaudeReply:
    text: str
    input_tokens: int
    output_tokens: int


@dataclass
class FakeClaudeClient:
    """Test double that returns canned messages and counts calls."""

    default: object = None
    call_count: int = 0
    by_q_id: dict[str, object] = field(default_factory=dict)

    async def messages_create(
        self,
        *,
        model: str,
        system: str,
        user: str,
        max_tokens: int,
    ) -> ClaudeReply:
        self.call_count += 1
        canned = self.default
        if hasattr(canned, "text"):
            return ClaudeReply(
                text=canned.text,                                  # type: ignore[attr-defined]
                input_tokens=canned.input_tokens,                  # type: ignore[attr-defined]
                output_tokens=canned.output_tokens,                # type: ignore[attr-defined]
            )
        return ClaudeReply(text="", input_tokens=0, output_tokens=0)


class _AnthropicAdapter:
    """Wraps the real anthropic SDK client into our messages_create signature."""

    def __init__(self) -> None:
        from anthropic import AsyncAnthropic

        api_key = os.environ.get("ANTHROPIC_API_KEY")
        if not api_key:
            raise RuntimeError(
                "ANTHROPIC_API_KEY not set. Copy .env.example to .env and fill it in."
            )
        self._client = AsyncAnthropic(api_key=api_key)

    async def messages_create(
        self,
        *,
        model: str,
        system: str,
        user: str,
        max_tokens: int,
    ) -> ClaudeReply:
        msg = await self._client.messages.create(
            model=model,
            max_tokens=max_tokens,
            system=system,
            messages=[{"role": "user", "content": user}],
        )
        text_parts = [b.text for b in msg.content if b.type == "text"]
        return ClaudeReply(
            text="\n\n".join(text_parts),
            input_tokens=msg.usage.input_tokens,
            output_tokens=msg.usage.output_tokens,
        )


def real_anthropic_client() -> _AnthropicAdapter:
    return _AnthropicAdapter()


@dataclass
class ClaudeSummarizer:
    cache: Cache
    cost: CostTracker
    client: object  # protocol-compatible: messages_create(...)
    prompt_dir: Path
    prompt_version: str
    dry_run: bool = False

    async def summarize_description(
        self,
        *,
        q_id: str,
        scientific_name: str,
        common_sv: str,
        common_en: str,
        family: str,
        family_sv: str,
        wikipedia_intro: str,
        lang: str,
        model: str,
    ) -> str:
        return await self._summarize(
            kind="description",
            q_id=q_id,
            scientific_name=scientific_name,
            common_sv=common_sv,
            common_en=common_en,
            family=family,
            family_sv=family_sv,
            wikipedia_intro=wikipedia_intro,
            lang=lang,
            model=model,
        )

    async def summarize_migration(
        self,
        *,
        q_id: str,
        scientific_name: str,
        common_sv: str,
        common_en: str,
        family: str,
        family_sv: str,
        wikipedia_intro: str,
        lang: str,
        model: str,
    ) -> str:
        return await self._summarize(
            kind="migration",
            q_id=q_id,
            scientific_name=scientific_name,
            common_sv=common_sv,
            common_en=common_en,
            family=family,
            family_sv=family_sv,
            wikipedia_intro=wikipedia_intro,
            lang=lang,
            model=model,
        )

    async def _summarize(
        self,
        *,
        kind: str,
        q_id: str,
        scientific_name: str,
        common_sv: str,
        common_en: str,
        family: str,
        family_sv: str,
        wikipedia_intro: str,
        lang: str,
        model: str,
    ) -> str:
        cache_filename = f"claude-{kind}-{lang}-{model}-{self.prompt_version}.txt"
        if self.cache.has(q_id, cache_filename):
            cached = self.cache.get(q_id, cache_filename)
            assert cached is not None
            return cached

        if self.dry_run:
            return "[dry-run]"

        prompt_path = self.prompt_dir / f"{kind}-{self.prompt_version}.md"
        prompt_template = prompt_path.read_text(encoding="utf-8")
        system, user = _split_prompt(
            prompt_template,
            scientific_name=scientific_name,
            common_sv=common_sv,
            common_en=common_en,
            family=family,
            family_sv=family_sv,
            lang=lang,
            wikipedia_intro_text=wikipedia_intro,
        )

        reply: ClaudeReply = await self.client.messages_create(  # type: ignore[attr-defined]
            model=MODEL_IDS[model],
            system=system,
            user=user,
            max_tokens=600,
        )

        self.cost.record(
            model=model,
            input_tokens=reply.input_tokens,
            output_tokens=reply.output_tokens,
        )
        self.cache.put(q_id, cache_filename, reply.text)
        return reply.text


def _split_prompt(
    template: str,
    **subs: str,
) -> tuple[str, str]:
    """Split markdown prompt template into (system, user) and substitute placeholders."""
    lines = template.splitlines()
    system_lines: list[str] = []
    user_lines: list[str] = []
    current: list[str] | None = None
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("System:"):
            current = system_lines
            system_lines.append(stripped[len("System:"):].strip())
        elif stripped.startswith("User:"):
            current = user_lines
            user_lines.append(stripped[len("User:"):].strip())
        elif current is not None:
            current.append(line)
    system = "\n".join(system_lines).strip()
    user = "\n".join(user_lines).strip()
    for key, value in subs.items():
        user = user.replace("{" + key + "}", value)
    return system, user
```

- [ ] **Step 9: Run tests until green**

```bash
uv run pytest tests/test_claude_summarizer.py tests/test_cost.py -v
uv run mypy
uv run ruff check
```

Expected: 7 passed; lint clean.

- [ ] **Step 10: Commit**

```bash
git add tools/content-pipeline/
git commit -m "feat(content): claude summarizer with cost cap, dry-run, prompt versioning"
```

> **User checkpoint to flag:** the agent posts a note to the user that `prompts/description-v1.md` still has placeholder few-shots (Koltrast, Blåmes) and asks the user to either approve the talgoxe-only example or supply two more before any `--all` run in Plan 2b.

---

### Task 7: Implement `images.py` (Commons fetch + selection + hero_review HTML)

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/images.py`
- Create: `tools/content-pipeline/src/birdy_fetcher/hero_review.py`
- Create: `tools/content-pipeline/tests/test_images.py`
- Create: `tools/content-pipeline/tests/test_hero_review.py`
- Create: `tools/content-pipeline/tests/fixtures/commons_imageinfo_q25372.json`
- Create: `tools/content-pipeline/tests/fixtures/sample_image.jpg` (binary; commit a tiny valid JPEG ~5KB)

`images.py` produces `ImageCandidate` lists per species, ranks them with the spec's selection algorithm, downloads, EXIF-strips, resizes (hero ≤2400px max-side q=88, secondary ≤1800px q=85), and writes them to `shared/content/images/{Q-ID}/`. `hero_review.py` writes a static HTML page per `abundance: allmän` species so the user can manually approve.

- [ ] **Step 1: Write fixture `tools/content-pipeline/tests/fixtures/commons_imageinfo_q25372.json`**

```json
{
  "query": {
    "pages": {
      "12345": {
        "title": "File:Parus major - Mindelheim - 2012.jpg",
        "imageinfo": [{
          "url": "https://upload.wikimedia.org/wikipedia/commons/parus.jpg",
          "width": 4000,
          "height": 2667,
          "mime": "image/jpeg",
          "extmetadata": {
            "LicenseShortName": {"value": "CC BY-SA 4.0"},
            "Artist": {"value": "Pierre Dalous"},
            "Categories": {"value": "Photographs of Parus major|Birds in nature|Photographs of Aves"}
          }
        }]
      },
      "12346": {
        "title": "File:Parus major illustration.jpg",
        "imageinfo": [{
          "url": "https://upload.wikimedia.org/wikipedia/commons/parus_illust.jpg",
          "width": 2000,
          "height": 2000,
          "mime": "image/jpeg",
          "extmetadata": {
            "LicenseShortName": {"value": "Public domain"},
            "Artist": {"value": "John Gould"},
            "Categories": {"value": "Bird illustrations|Drawings of birds"}
          }
        }]
      },
      "12347": {
        "title": "File:Parus major specimen.jpg",
        "imageinfo": [{
          "url": "https://upload.wikimedia.org/wikipedia/commons/parus_specimen.jpg",
          "width": 3000,
          "height": 2000,
          "mime": "image/jpeg",
          "extmetadata": {
            "LicenseShortName": {"value": "CC0"},
            "Artist": {"value": "Museum of Wherever"},
            "Categories": {"value": "Bird specimens|Taxidermy"}
          }
        }]
      }
    }
  }
}
```

- [ ] **Step 2: Add a small sample JPEG**

Create a 256×256 solid-color JPEG and save to `tools/content-pipeline/tests/fixtures/sample_image.jpg`. One-liner with Pillow:

```bash
cd tools/content-pipeline
uv run python -c "from PIL import Image; Image.new('RGB', (256, 256), (90, 110, 72)).save('tests/fixtures/sample_image.jpg', quality=80)"
```

(Mossbädd-grön sample image, ~5KB.)

- [ ] **Step 3: Write the failing test `tools/content-pipeline/tests/test_images.py`**

```python
"""Tests for images.py — selection algorithm + resize/EXIF strip."""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from PIL import Image

from birdy_fetcher.cache import Cache
from birdy_fetcher.images import (
    ImageCandidate,
    ImageProcessor,
    ImageSelector,
    parse_imageinfo_response,
    rank_candidates,
)


def test_parse_imageinfo_extracts_candidates(fixtures_dir: Path) -> None:
    raw = (fixtures_dir / "commons_imageinfo_q25372.json").read_text()
    candidates = parse_imageinfo_response(raw)
    titles = [c.commons_filename for c in candidates]
    assert "Parus major - Mindelheim - 2012.jpg" in titles


def test_rank_rejects_illustrations() -> None:
    illust = ImageCandidate(
        commons_filename="Parus major illustration.jpg",
        url="x",
        width=4000,
        height=3000,
        license="Public domain",
        author="A",
        categories=["Bird illustrations"],
    )
    photo = ImageCandidate(
        commons_filename="Parus major - photo.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="B",
        categories=["Photographs of Aves", "Birds in nature"],
    )
    ranked = rank_candidates([illust, photo])
    assert ranked[0].commons_filename == "Parus major - photo.jpg"
    assert all("illustration" not in c.commons_filename.lower() for c in ranked)


def test_rank_rejects_specimens() -> None:
    specimen = ImageCandidate(
        commons_filename="Parus major specimen.jpg",
        url="x",
        width=4000,
        height=3000,
        license="CC0",
        author="A",
        categories=["Bird specimens"],
    )
    photo = ImageCandidate(
        commons_filename="Parus major - garden.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="B",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([specimen, photo])
    assert all("specimen" not in c.commons_filename.lower() for c in ranked)


def test_rank_rejects_below_min_resolution() -> None:
    too_small = ImageCandidate(
        commons_filename="Parus major small.jpg",
        url="x",
        width=1024,
        height=768,
        license="CC0",
        author="A",
        categories=[],
    )
    big = ImageCandidate(
        commons_filename="Parus major big.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC0",
        author="A",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([too_small, big])
    assert too_small not in ranked


def test_rank_prefers_pd_over_cc_by_sa() -> None:
    pd = ImageCandidate(
        commons_filename="Parus major - photo1.jpg",
        url="x",
        width=4000,
        height=3000,
        license="Public domain",
        author="A",
        categories=["Photographs of Aves"],
    )
    sa = ImageCandidate(
        commons_filename="Parus major - photo2.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="B",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([sa, pd])
    assert ranked[0].license == "Public domain"


def test_processor_resizes_to_hero_dimensions(
    fixtures_dir: Path, tmp_path: Path
) -> None:
    cache = Cache(tmp_path)
    cache.put_bytes(
        "Q25485",
        "images/raw-hero.jpg",
        (fixtures_dir / "sample_image.jpg").read_bytes(),
    )

    processor = ImageProcessor()
    out_path = tmp_path / "hero.jpg"
    metadata = processor.process(
        cache.get_bytes("Q25485", "images/raw-hero.jpg") or b"",
        out_path=out_path,
        role="hero",
    )
    assert out_path.exists()
    img = Image.open(out_path)
    assert max(img.size) <= 2400
    assert metadata.width == img.size[0]
    assert metadata.height == img.size[1]
```

- [ ] **Step 4: Run failing tests**

```bash
uv run pytest tests/test_images.py -v
```

Expected: 6 failures (ImportError + missing classes).

- [ ] **Step 5: Implement `tools/content-pipeline/src/birdy_fetcher/images.py`**

```python
"""Wikimedia Commons image fetcher + selection + processor."""

from __future__ import annotations

import io
import json
import re
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from pathlib import Path

import aiohttp
from PIL import Image

from .cache import Cache

USER_AGENT = "birdy-fetcher/0.1.0 (https://github.com/anonadrek/birdy)"

MIN_DIMENSION = 2048
HERO_MAX = 2400
HERO_QUALITY = 88
SECONDARY_MAX = 1800
SECONDARY_QUALITY = 85

REJECT_PATTERNS = re.compile(
    r"\b(illustration|drawing|painting|specimen|skeleton|skull|egg|nest only|taxidermy)\b",
    re.IGNORECASE,
)

_LICENSE_PRIORITY = {
    "public domain": 0,
    "cc0": 0,
    "cc by 2.0": 1,
    "cc by 3.0": 1,
    "cc by 4.0": 1,
    "cc by-sa 2.0": 2,
    "cc by-sa 3.0": 2,
    "cc by-sa 4.0": 2,
}


@dataclass(frozen=True)
class ImageCandidate:
    commons_filename: str
    url: str
    width: int
    height: int
    license: str
    author: str
    categories: list[str]


@dataclass(frozen=True)
class ProcessedImage:
    width: int
    height: int
    bytes_size: int


def parse_imageinfo_response(raw: str) -> list[ImageCandidate]:
    data = json.loads(raw)
    out: list[ImageCandidate] = []
    pages = data.get("query", {}).get("pages", {})
    for page in pages.values():
        title: str = page.get("title", "")
        if not title.startswith("File:"):
            continue
        info_list = page.get("imageinfo", [])
        if not info_list:
            continue
        info = info_list[0]
        ext = info.get("extmetadata", {})
        out.append(
            ImageCandidate(
                commons_filename=title.removeprefix("File:"),
                url=info.get("url", ""),
                width=int(info.get("width", 0)),
                height=int(info.get("height", 0)),
                license=ext.get("LicenseShortName", {}).get("value", ""),
                author=ext.get("Artist", {}).get("value", ""),
                categories=[
                    c.strip()
                    for c in ext.get("Categories", {}).get("value", "").split("|")
                    if c.strip()
                ],
            )
        )
    return out


def rank_candidates(candidates: list[ImageCandidate]) -> list[ImageCandidate]:
    survivors: list[ImageCandidate] = []
    for c in candidates:
        if REJECT_PATTERNS.search(c.commons_filename):
            continue
        if any(REJECT_PATTERNS.search(cat) for cat in c.categories):
            continue
        if max(c.width, c.height) < MIN_DIMENSION:
            continue
        survivors.append(c)

    def _score(c: ImageCandidate) -> tuple[int, int, int, int]:
        license_rank = _LICENSE_PRIORITY.get(c.license.lower(), 5)
        in_nature = (
            0
            if any("birds in nature" in cat.lower() for cat in c.categories)
            else 1
        )
        photographs = (
            0
            if any("photographs of aves" in cat.lower() for cat in c.categories)
            else 1
        )
        # higher resolution sorts first via negation
        size = -(c.width * c.height)
        return (license_rank, photographs, in_nature, size)

    return sorted(survivors, key=_score)


HttpGet = Callable[[str], Awaitable[str]]
HttpGetBytes = Callable[[str], Awaitable[bytes]]


async def _default_get_text(url: str) -> str:
    async with aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session:
        async with session.get(url, timeout=aiohttp.ClientTimeout(total=60)) as r:
            r.raise_for_status()
            return await r.text()


async def _default_get_bytes(url: str) -> bytes:
    async with aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session:
        async with session.get(url, timeout=aiohttp.ClientTimeout(total=120)) as r:
            r.raise_for_status()
            return await r.read()


@dataclass
class ImageSelector:
    cache: Cache
    http_get: HttpGet | None = None

    async def fetch_candidates(
        self,
        q_id: str,
        scientific_name: str,
        *,
        force: bool = False,
    ) -> list[ImageCandidate]:
        cache_key = "image-candidates.json"
        if not force and self.cache.has(q_id, cache_key):
            raw = self.cache.get(q_id, cache_key)
            assert raw is not None
            return parse_imageinfo_response(raw)

        get = self.http_get or _default_get_text
        url = (
            "https://commons.wikimedia.org/w/api.php?"
            "action=query&format=json&prop=imageinfo&"
            "iiprop=url|size|mime|extmetadata&"
            f"generator=search&gsrsearch=intitle:%22{scientific_name}%22&gsrlimit=20"
        )
        raw = await get(url)
        self.cache.put(q_id, cache_key, raw)
        return parse_imageinfo_response(raw)


class ImageProcessor:
    def __init__(self, http_get_bytes: HttpGetBytes | None = None) -> None:
        self._http_get_bytes = http_get_bytes or _default_get_bytes

    async def download(self, url: str) -> bytes:
        return await self._http_get_bytes(url)

    def process(
        self,
        raw_bytes: bytes,
        *,
        out_path: Path,
        role: str,
    ) -> ProcessedImage:
        max_side = HERO_MAX if role == "hero" else SECONDARY_MAX
        quality = HERO_QUALITY if role == "hero" else SECONDARY_QUALITY

        img = Image.open(io.BytesIO(raw_bytes))
        if img.mode in ("RGBA", "P"):
            img = img.convert("RGB")
        img.thumbnail((max_side, max_side), Image.Resampling.LANCZOS)

        out_path.parent.mkdir(parents=True, exist_ok=True)
        # EXIF stripped by Pillow's default `save` (no exif passed)
        img.save(out_path, format="JPEG", quality=quality, optimize=True)
        return ProcessedImage(
            width=img.size[0],
            height=img.size[1],
            bytes_size=out_path.stat().st_size,
        )
```

- [ ] **Step 6: Run image tests until green**

```bash
uv run pytest tests/test_images.py -v
uv run mypy
uv run ruff check
```

Expected: 6 passed; lint clean.

- [ ] **Step 7: Write the failing test `tools/content-pipeline/tests/test_hero_review.py`**

```python
"""hero_review.py renders top-N candidates as a static HTML page."""

from __future__ import annotations

from pathlib import Path

from birdy_fetcher.hero_review import render_hero_review
from birdy_fetcher.images import ImageCandidate


def test_render_hero_review_writes_html(tmp_path: Path) -> None:
    candidates = [
        ImageCandidate(
            commons_filename="Parus major - photo1.jpg",
            url="https://example/photo1.jpg",
            width=4000,
            height=3000,
            license="CC BY-SA 4.0",
            author="Pierre Dalous",
            categories=["Photographs of Aves", "Birds in nature"],
        ),
        ImageCandidate(
            commons_filename="Parus major - photo2.jpg",
            url="https://example/photo2.jpg",
            width=3500,
            height=2333,
            license="CC0",
            author="Anonymous",
            categories=["Photographs of Aves"],
        ),
    ]
    out_path = tmp_path / "Q25485.html"
    render_hero_review(
        q_id="Q25485",
        scientific_name="Parus major",
        common_sv="Talgoxe",
        candidates=candidates,
        out_path=out_path,
    )
    assert out_path.exists()
    html = out_path.read_text(encoding="utf-8")
    assert "Q25485" in html
    assert "Parus major" in html
    assert "Pierre Dalous" in html
    assert "https://example/photo1.jpg" in html
```

- [ ] **Step 8: Implement `tools/content-pipeline/src/birdy_fetcher/hero_review.py`**

```python
"""Render a static HTML review page for hero candidate selection."""

from __future__ import annotations

from pathlib import Path

from .images import ImageCandidate

_TEMPLATE = """<!DOCTYPE html>
<html lang="sv">
<head>
<meta charset="utf-8">
<title>{q_id} — {common_sv}</title>
<style>
body {{ font-family: system-ui, sans-serif; background: #E8E2D2; color: #2A3525;
        margin: 2rem; }}
h1 {{ font-family: 'Crimson Pro', Georgia, serif; }}
.candidate {{ border: 1px solid #5C6E48; padding: 1rem; margin: 1rem 0; border-radius: 4px;
              background: #D8D0BC; }}
.candidate img {{ max-width: 800px; max-height: 500px; display: block; margin-bottom: 0.5rem; }}
.metadata {{ font-size: 0.9rem; color: #3F4F30; }}
.choose {{ background: #8C5A3C; color: #F0EAD8; padding: 0.5rem 1rem; border: none;
           font-weight: 600; cursor: pointer; }}
</style>
</head>
<body>
<h1>{q_id} — {common_sv} <em>({scientific_name})</em></h1>
<p>To accept a candidate as hero, copy its filename to <code>shared/content/overrides.yaml</code> under <code>{q_id}.image_refs[0].commons_filename</code>.</p>
{candidates_html}
</body>
</html>
"""

_CANDIDATE = """
<div class="candidate">
  <img src="{url}" alt="{filename}">
  <div class="metadata">
    <strong>{filename}</strong><br>
    {width}×{height} | {license} | {author}<br>
    Categories: {categories}<br>
    <a href="https://commons.wikimedia.org/wiki/File:{filename}" target="_blank">View on Commons</a>
  </div>
</div>
"""


def render_hero_review(
    *,
    q_id: str,
    scientific_name: str,
    common_sv: str,
    candidates: list[ImageCandidate],
    out_path: Path,
) -> None:
    parts = [
        _CANDIDATE.format(
            url=c.url,
            filename=c.commons_filename,
            width=c.width,
            height=c.height,
            license=c.license,
            author=c.author,
            categories=", ".join(c.categories) or "(none)",
        )
        for c in candidates[:5]
    ]
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(
        _TEMPLATE.format(
            q_id=q_id,
            common_sv=common_sv,
            scientific_name=scientific_name,
            candidates_html="\n".join(parts),
        ),
        encoding="utf-8",
    )
```

- [ ] **Step 9: Run hero_review tests until green**

```bash
uv run pytest tests/test_hero_review.py -v
```

Expected: 1 passed.

- [ ] **Step 10: Commit**

```bash
git add tools/content-pipeline/
git commit -m "feat(content): commons image selector + processor + hero_review HTML"
```

---

### Task 8: Implement YAML writer + main fetcher orchestration + `doctor`

**Files:**
- Create: `tools/content-pipeline/src/birdy_fetcher/yaml_writer.py`
- Create: `tools/content-pipeline/src/birdy_fetcher/doctor.py`
- Create: `tools/content-pipeline/src/birdy_fetcher/orchestrator.py`
- Modify: `tools/content-pipeline/src/birdy_fetcher/cli.py` (wire `refresh`, `doctor`, `status`)
- Create: `tools/content-pipeline/tests/test_yaml_writer.py`
- Create: `tools/content-pipeline/tests/test_orchestrator.py`
- Create: `tools/content-pipeline/tests/test_doctor.py`
- Modify: root `.gitignore` (add `tools/content-pipeline/sparse_content.yaml`)

This task glues it all together: read `species_list.yaml`, run wikidata → wikipedia → claude → images per species, write the resulting YAML through `yaml_writer.py`. `doctor` validates env + sources + cache health before runs. `status` prints coverage statistics.

- [ ] **Step 1: Write the failing test `tools/content-pipeline/tests/test_yaml_writer.py`**

```python
"""yaml_writer round-trip and overrides merging."""

from __future__ import annotations

from pathlib import Path

import yaml

from birdy_fetcher.yaml_writer import (
    SpeciesYamlData,
    merge_overrides,
    write_species_yaml,
)


def make_data() -> SpeciesYamlData:
    return SpeciesYamlData(
        wikidata_id="Q25485",
        scientific_name="Parus major",
        family="Paridae",
        family_sv="Mesar",
        genus="Parus",
        ioc_order="Passeriformes",
        common_sv="Talgoxe",
        common_en="Great Tit",
        abundance="allmän",
        iucn_status="LC",
        regions=["SE", "NO", "FI"],
        season={
            "jan": "present", "feb": "present", "mar": "present",
            "apr": "breeding", "may": "breeding", "jun": "breeding",
            "jul": "breeding", "aug": "present", "sep": "present",
            "oct": "present", "nov": "present", "dec": "present",
        },
        description={"sv": "Talgoxen är ...", "en": "The great tit ..."},
        migration={"sv": "Stationär ...", "en": "Resident ..."},
        image_refs=[],
        review_status="auto",
        review_notes="",
        generated_at="2026-05-02T14:30:00Z",
        sources={
            "wikipedia_sv_revision": 12345678,
            "wikipedia_en_revision": 87654321,
            "wikidata_revision": 1234567,
            "claude_model": "claude-haiku-4-5-20251001",
        },
    )


def test_round_trip(tmp_path: Path) -> None:
    data = make_data()
    out_path = tmp_path / "paridae" / "Q25485.yaml"
    write_species_yaml(data, out_path)
    assert out_path.exists()
    parsed = yaml.safe_load(out_path.read_text(encoding="utf-8"))
    assert parsed["id"] == "Q25485"
    assert parsed["names"]["sv"] == "Talgoxe"
    assert parsed["abundance"] == "allmän"


def test_overrides_replace_description() -> None:
    data = make_data()
    overrides = {
        "Q25485": {
            "description": {"sv": "Manuell svensk text."},
        }
    }
    merged = merge_overrides(data, overrides)
    assert merged.description["sv"] == "Manuell svensk text."
    assert merged.description["en"] == data.description["en"]  # untouched


def test_overrides_accept_missing_marks_field() -> None:
    data = make_data()
    overrides = {
        "Q25485": {
            "description": {"sv": {"accept_missing": True}},
        }
    }
    merged = merge_overrides(data, overrides)
    assert merged.description["sv"] == "[accept_missing]"
```

- [ ] **Step 2: Run failing tests**

```bash
uv run pytest tests/test_yaml_writer.py -v
```

Expected: 3 failures.

- [ ] **Step 3: Implement `tools/content-pipeline/src/birdy_fetcher/yaml_writer.py`**

```python
"""Write SpeciesYamlData to disk in the canonical schema; apply overrides."""

from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass, field, replace
from pathlib import Path
from typing import Any

import yaml


@dataclass
class ImageRef:
    role: str
    path: str
    width: int
    height: int
    license: str
    author: str
    source_url: str
    commons_filename: str


@dataclass
class SpeciesYamlData:
    wikidata_id: str
    scientific_name: str
    family: str
    family_sv: str
    genus: str
    ioc_order: str
    common_sv: str | None
    common_en: str
    abundance: str
    iucn_status: str
    regions: list[str]
    season: dict[str, str]
    description: dict[str, str]
    migration: dict[str, str]
    image_refs: list[ImageRef] = field(default_factory=list)
    review_status: str = "auto"
    review_notes: str = ""
    generated_at: str = ""
    sources: dict[str, Any] = field(default_factory=dict)


def _serialize(data: SpeciesYamlData) -> dict[str, Any]:
    return {
        "id": data.wikidata_id,
        "scientific_name": data.scientific_name,
        "taxonomy": {
            "family": data.family,
            "family_sv": data.family_sv,
            "genus": data.genus,
            "ioc_order": data.ioc_order,
        },
        "names": {
            "sv": data.common_sv,
            "en": data.common_en,
        },
        "abundance": data.abundance,
        "iucn_status": data.iucn_status,
        "season": data.season,
        "regions": data.regions,
        "description": data.description,
        "migration": data.migration,
        "image_refs": [
            {
                "role": ref.role,
                "path": ref.path,
                "width": ref.width,
                "height": ref.height,
                "license": ref.license,
                "author": ref.author,
                "source_url": ref.source_url,
                "commons_filename": ref.commons_filename,
            }
            for ref in data.image_refs
        ],
        "review_status": data.review_status,
        "review_notes": data.review_notes,
        "generated_at": data.generated_at,
        "sources": data.sources,
    }


def write_species_yaml(data: SpeciesYamlData, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(
        yaml.safe_dump(
            _serialize(data),
            sort_keys=False,
            allow_unicode=True,
            default_flow_style=False,
        ),
        encoding="utf-8",
    )


def merge_overrides(
    data: SpeciesYamlData,
    overrides: dict[str, Any],
) -> SpeciesYamlData:
    patch = overrides.get(data.wikidata_id)
    if not patch:
        return data
    merged = deepcopy(data)

    if "description" in patch:
        for lang, text in patch["description"].items():
            if isinstance(text, dict) and text.get("accept_missing"):
                merged.description[lang] = "[accept_missing]"
            else:
                merged.description[lang] = str(text)

    if "migration" in patch:
        for lang, text in patch["migration"].items():
            merged.migration[lang] = str(text)

    if "image_refs" in patch:
        merged.image_refs = [
            ImageRef(
                role=ref["role"],
                path=ref["path"],
                width=int(ref.get("width", 0)),
                height=int(ref.get("height", 0)),
                license=ref.get("license", ""),
                author=ref.get("author", ""),
                source_url=ref.get("source_url", ""),
                commons_filename=ref.get("commons_filename", ""),
            )
            for ref in patch["image_refs"]
        ]

    if "abundance" in patch:
        merged = replace(merged, abundance=str(patch["abundance"]))

    return merged
```

- [ ] **Step 4: Run yaml_writer tests until green**

```bash
uv run pytest tests/test_yaml_writer.py -v
```

Expected: 3 passed.

- [ ] **Step 5: Write the failing test `tools/content-pipeline/tests/test_doctor.py`**

```python
"""doctor pre-flight check."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.doctor import DoctorReport, run_doctor


def test_doctor_passes_when_everything_present(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    sources = tmp_path / "sources"
    sources.mkdir()
    (sources / "ioc-14.1.xlsx").write_bytes(b"PK\x03\x04fakeexcelplaceholder")
    (sources / "vp11.pdf").write_bytes(b"%PDF-1.4 fakepdfplaceholder")
    (tmp_path / "species_list.yaml").write_text("- wikidata_id: Q1\n  scientific_name: x\n")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "sk-ant-fake")

    report = run_doctor(root=tmp_path)
    assert report.is_ok
    assert all(r.ok for r in report.checks)


def test_doctor_fails_without_api_key(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    report = run_doctor(root=tmp_path)
    assert not report.is_ok
    api_check = next(c for c in report.checks if "API_KEY" in c.name)
    assert not api_check.ok


def test_doctor_fails_without_ioc_xlsx(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ANTHROPIC_API_KEY", "sk-ant-fake")
    report = run_doctor(root=tmp_path)
    assert not report.is_ok
```

- [ ] **Step 6: Implement `tools/content-pipeline/src/birdy_fetcher/doctor.py`**

```python
"""Pre-flight check before fetcher runs. Verifies env, sources, cache."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Check:
    name: str
    ok: bool
    detail: str = ""


@dataclass(frozen=True)
class DoctorReport:
    checks: list[Check]

    @property
    def is_ok(self) -> bool:
        return all(c.ok for c in self.checks)


def run_doctor(*, root: Path) -> DoctorReport:
    checks: list[Check] = []

    api_key = os.environ.get("ANTHROPIC_API_KEY", "")
    checks.append(
        Check(
            name="ANTHROPIC_API_KEY",
            ok=bool(api_key) and api_key.startswith("sk-ant-"),
            detail=("set" if api_key else "missing — copy .env.example to .env"),
        )
    )

    ioc = root / "sources" / "ioc-14.1.xlsx"
    checks.append(
        Check(
            name="sources/ioc-14.1.xlsx",
            ok=ioc.exists(),
            detail=("present" if ioc.exists() else "download from worldbirdnames.org"),
        )
    )

    vp11 = root / "sources" / "vp11.pdf"
    checks.append(
        Check(
            name="sources/vp11.pdf",
            ok=vp11.exists(),
            detail=(
                "present"
                if vp11.exists()
                else "download from cdn.birdlife.se (TK Västpalearktis-lista v11)"
            ),
        )
    )

    species_list = root / "species_list.yaml"
    checks.append(
        Check(
            name="species_list.yaml",
            ok=species_list.exists(),
            detail=(
                "present"
                if species_list.exists()
                else "run: uv run birdy-fetcher init"
            ),
        )
    )

    cache = root / ".cache"
    checks.append(
        Check(
            name=".cache/",
            ok=True,
            detail=(
                f"{sum(1 for _ in cache.iterdir())} entries"
                if cache.exists()
                else "empty (will be created on first refresh)"
            ),
        )
    )

    return DoctorReport(checks=checks)
```

- [ ] **Step 7: Run doctor tests until green**

```bash
uv run pytest tests/test_doctor.py -v
```

Expected: 3 passed.

- [ ] **Step 8: Implement `tools/content-pipeline/src/birdy_fetcher/orchestrator.py`**

```python
"""Per-species orchestration of the refresh pipeline."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import yaml
from rich.console import Console
from rich.progress import (
    BarColumn,
    Progress,
    TaskProgressColumn,
    TextColumn,
    TimeRemainingColumn,
)

from .cache import Cache
from .claude_summarizer import ClaudeSummarizer, real_anthropic_client
from .cost import CostTracker
from .doctor import run_doctor
from .images import ImageProcessor, ImageSelector, rank_candidates
from .wikidata import WikidataClient
from .wikipedia import WikipediaClient
from .yaml_writer import (
    ImageRef,
    SpeciesYamlData,
    merge_overrides,
    write_species_yaml,
)

console = Console()


@dataclass
class RefreshOptions:
    species_filter: list[str] | None
    field: str  # "text" | "images" | "all"
    force: bool
    dry_run: bool
    workers: int
    model: str
    max_cost: float | None


@dataclass
class RefreshContext:
    pipeline_root: Path
    content_root: Path  # shared/content
    cache: Cache
    cost: CostTracker
    wikidata: WikidataClient
    wikipedia: WikipediaClient
    images: ImageSelector
    image_processor: ImageProcessor
    claude: ClaudeSummarizer
    options: RefreshOptions

    @property
    def species_yaml_root(self) -> Path:
        return self.content_root / "species"

    @property
    def images_root(self) -> Path:
        return self.content_root / "images"

    @property
    def overrides_path(self) -> Path:
        return self.content_root / "overrides.yaml"


def build_context(
    pipeline_root: Path,
    content_root: Path,
    options: RefreshOptions,
) -> RefreshContext:
    cache = Cache(pipeline_root / ".cache")
    cost = CostTracker(max_usd=options.max_cost)
    return RefreshContext(
        pipeline_root=pipeline_root,
        content_root=content_root,
        cache=cache,
        cost=cost,
        wikidata=WikidataClient(cache=cache),
        wikipedia=WikipediaClient(cache=cache),
        images=ImageSelector(cache=cache),
        image_processor=ImageProcessor(),
        claude=ClaudeSummarizer(
            cache=cache,
            cost=cost,
            client=real_anthropic_client() if not options.dry_run else _NoopClient(),
            prompt_dir=pipeline_root / "prompts",
            prompt_version="v1",
            dry_run=options.dry_run,
        ),
        options=options,
    )


class _NoopClient:
    async def messages_create(self, **kwargs: Any) -> Any:
        from .claude_summarizer import ClaudeReply

        return ClaudeReply(text="[dry-run]", input_tokens=0, output_tokens=0)


def load_species_list(path: Path) -> list[dict[str, Any]]:
    return yaml.safe_load(path.read_text(encoding="utf-8")) or []


def filter_species(
    all_species: list[dict[str, Any]],
    options: RefreshOptions,
) -> list[dict[str, Any]]:
    if not options.species_filter:
        return all_species
    wanted = set(options.species_filter)
    return [s for s in all_species if s.get("wikidata_id") in wanted]


async def refresh_one(ctx: RefreshContext, listed: dict[str, Any]) -> SpeciesYamlData:
    q_id = listed["wikidata_id"]
    scientific_name = listed["scientific_name"]

    wd = await ctx.wikidata.fetch_structured(q_id, force=ctx.options.force)
    title_by_lang = {
        "sv": listed.get("common_sv") or scientific_name,
        "en": listed.get("common_en") or scientific_name,
    }
    sv_article = await ctx.wikipedia.fetch_extract(
        q_id, title_by_lang=title_by_lang, lang="sv", force=ctx.options.force
    )
    en_article = await ctx.wikipedia.fetch_extract(
        q_id, title_by_lang=title_by_lang, lang="en", force=ctx.options.force
    )

    description = {"sv": "", "en": ""}
    migration = {"sv": "", "en": ""}

    if ctx.options.field in ("text", "all"):
        for lang, article in (("sv", sv_article), ("en", en_article)):
            if article.is_sparse:
                description[lang] = ""
                migration[lang] = ""
                continue
            description[lang] = await ctx.claude.summarize_description(
                q_id=q_id,
                scientific_name=scientific_name,
                common_sv=listed.get("common_sv") or "",
                common_en=listed.get("common_en") or "",
                family=wd.family,
                family_sv=listed.get("family_sv") or wd.family,
                wikipedia_intro=article.extract,
                lang=lang,
                model=ctx.options.model,
            )
            migration[lang] = await ctx.claude.summarize_migration(
                q_id=q_id,
                scientific_name=scientific_name,
                common_sv=listed.get("common_sv") or "",
                common_en=listed.get("common_en") or "",
                family=wd.family,
                family_sv=listed.get("family_sv") or wd.family,
                wikipedia_intro=article.extract,
                lang=lang,
                model=ctx.options.model,
            )

    image_refs: list[ImageRef] = []
    if ctx.options.field in ("images", "all"):
        candidates = await ctx.images.fetch_candidates(
            q_id=q_id, scientific_name=scientific_name, force=ctx.options.force
        )
        ranked = rank_candidates(candidates)[:3]
        for idx, candidate in enumerate(ranked):
            role = "hero" if idx == 0 else "secondary"
            filename = "hero.jpg" if idx == 0 else f"secondary-{idx}.jpg"
            out_path = ctx.images_root / q_id / filename
            if not ctx.options.dry_run:
                raw = await ctx.image_processor.download(candidate.url)
                meta = ctx.image_processor.process(raw, out_path=out_path, role=role)
            else:
                meta = type(
                    "Meta",
                    (),
                    {"width": candidate.width, "height": candidate.height},
                )()
            image_refs.append(
                ImageRef(
                    role=role,
                    path=f"{q_id}/{filename}",
                    width=meta.width,
                    height=meta.height,
                    license=candidate.license,
                    author=candidate.author,
                    source_url=(
                        f"https://commons.wikimedia.org/wiki/File:"
                        f"{candidate.commons_filename}"
                    ),
                    commons_filename=candidate.commons_filename,
                )
            )

    # vp_status från VP11: H = häckare i WP (default allmän), F = flyttfågel,
    # h/(H) = oklar/icke-etablerad → ovanlig. Slutgiltig abundance kan ändras manuellt
    # i overrides.yaml per art om Sverige-specifik kunskap motiverar det.
    abundance = "allmän" if listed.get("vp_status") in {"H", "F"} else "ovanlig"

    season = _default_season()
    regions = ["SE", "NO", "FI", "DK", "DE"]

    data = SpeciesYamlData(
        wikidata_id=q_id,
        scientific_name=scientific_name,
        family=wd.family,
        family_sv=listed.get("family_sv") or wd.family,
        genus=wd.genus,
        ioc_order=wd.ioc_order,
        common_sv=listed.get("common_sv"),
        common_en=listed.get("common_en") or "",
        abundance=abundance,
        iucn_status=wd.iucn_status,
        regions=regions,
        season=season,
        description=description,
        migration=migration,
        image_refs=image_refs,
        review_status="auto",
        review_notes="",
        generated_at=datetime.now(UTC).isoformat(),
        sources={
            "wikipedia_sv_revision": sv_article.revision,
            "wikipedia_en_revision": en_article.revision,
            "claude_model": (
                "claude-haiku-4-5-20251001" if ctx.options.model == "haiku" else "claude-sonnet-4-6"
            ),
        },
    )

    overrides_raw: dict[str, Any] = {}
    if ctx.overrides_path.exists():
        overrides_raw = yaml.safe_load(ctx.overrides_path.read_text(encoding="utf-8")) or {}
    data = merge_overrides(data, overrides_raw)

    family_dir = wd.family.lower()
    out_path = ctx.species_yaml_root / family_dir / f"{q_id}.yaml"
    if not ctx.options.dry_run:
        write_species_yaml(data, out_path)

    return data


def _default_season() -> dict[str, str]:
    return {m: "present" for m in (
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec",
    )}


async def run_refresh(ctx: RefreshContext) -> int:
    species_list = load_species_list(ctx.pipeline_root / "species_list.yaml")
    target = filter_species(species_list, ctx.options)

    if not target:
        console.print("[yellow]No species matched filter; nothing to do.[/yellow]")
        return 0

    semaphore = asyncio.Semaphore(ctx.options.workers)

    async def bound(listed: dict[str, Any]) -> None:
        async with semaphore:
            try:
                await refresh_one(ctx, listed)
            except Exception as exc:
                console.print(f"[red]Failed {listed.get('wikidata_id')}: {exc}[/red]")

    with Progress(
        TextColumn("[progress.description]{task.description}"),
        BarColumn(),
        TaskProgressColumn(),
        TimeRemainingColumn(),
    ) as progress:
        prog_task = progress.add_task("refreshing", total=len(target))
        coros = []
        for listed in target:
            coros.append(bound(listed))
        for done in asyncio.as_completed(coros):
            await done
            progress.advance(prog_task)

    console.print(
        f"Done. {ctx.cost.call_count} Claude calls, "
        f"~${ctx.cost.total_usd:.4f} total."
    )
    return 0
```

> **Heads-up:** the simple progress accounting above advances on completion order, not in input order — fine for a progress bar but the failure log isn't grouped by family. Acceptable for now.

- [ ] **Step 9: Write the integration test `tools/content-pipeline/tests/test_orchestrator.py`**

```python
"""Orchestrator end-to-end with all collaborators mocked."""

from __future__ import annotations

from pathlib import Path

import pytest
import yaml

from birdy_fetcher.cache import Cache
from birdy_fetcher.claude_summarizer import ClaudeReply, ClaudeSummarizer, FakeClaudeClient
from birdy_fetcher.cost import CostTracker
from birdy_fetcher.images import ImageProcessor, ImageSelector
from birdy_fetcher.orchestrator import RefreshContext, RefreshOptions, refresh_one
from birdy_fetcher.wikidata import WikidataClient
from birdy_fetcher.wikipedia import WikipediaClient


@pytest.mark.asyncio
async def test_refresh_one_writes_yaml(
    fixtures_dir: Path, tmp_path: Path
) -> None:
    pipeline_root = tmp_path / "pipeline"
    content_root = tmp_path / "content"
    (pipeline_root / "prompts").mkdir(parents=True)
    (pipeline_root / "prompts" / "description-v1.md").write_text(
        "System: ...\nUser: {scientific_name}\n", encoding="utf-8"
    )
    (pipeline_root / "prompts" / "migration-v1.md").write_text(
        "System: ...\nUser: {scientific_name}\n", encoding="utf-8"
    )

    cache = Cache(pipeline_root / ".cache")
    cost = CostTracker(max_usd=None)

    wd_fixture = (fixtures_dir / "wikidata_q25372.json").read_text()
    wp_fixture = (fixtures_dir / "wikipedia_sv_parus_major.json").read_text()
    img_fixture = (fixtures_dir / "commons_imageinfo_q25372.json").read_text()

    async def fake_sparql(query: str) -> str:
        return wd_fixture

    async def fake_wp(url: str) -> str:
        return wp_fixture

    async def fake_commons(url: str) -> str:
        return img_fixture

    async def fake_get_bytes(url: str) -> bytes:
        return (fixtures_dir / "sample_image.jpg").read_bytes()

    options = RefreshOptions(
        species_filter=["Q25485"],
        field="all",
        force=False,
        dry_run=False,
        workers=1,
        model="haiku",
        max_cost=None,
    )
    fake_claude = FakeClaudeClient(default=type("M", (), {
        "text": "Talgoxen är ...",
        "input_tokens": 5000,
        "output_tokens": 250,
    })())
    ctx = RefreshContext(
        pipeline_root=pipeline_root,
        content_root=content_root,
        cache=cache,
        cost=cost,
        wikidata=WikidataClient(cache=cache, run_sparql=fake_sparql),
        wikipedia=WikipediaClient(cache=cache, http_get=fake_wp),
        images=ImageSelector(cache=cache, http_get=fake_commons),
        image_processor=ImageProcessor(http_get_bytes=fake_get_bytes),
        claude=ClaudeSummarizer(
            cache=cache,
            cost=cost,
            client=fake_claude,
            prompt_dir=pipeline_root / "prompts",
            prompt_version="v1",
        ),
        options=options,
    )

    listed = {
        "wikidata_id": "Q25485",
        "scientific_name": "Parus major",
        "common_en": "Great Tit",
        "family": "Paridae",
        "family_sv": "Mesar",
        "vp_status": "H",
        # common_sv hämtas från Wikidata (P1843@sv) i refresh_one's wikidata-fetch.
    }
    data = await refresh_one(ctx, listed)
    assert data.wikidata_id == "Q25485"
    out_yaml = content_root / "species" / "paridae" / "Q25485.yaml"
    assert out_yaml.exists()
    parsed = yaml.safe_load(out_yaml.read_text(encoding="utf-8"))
    assert parsed["names"]["sv"] == "Talgoxe"
    assert parsed["abundance"] == "allmän"
    assert len(parsed["image_refs"]) >= 1
```

- [ ] **Step 10: Wire `cli.py`'s `refresh`, `doctor`, `status` commands into orchestrator**

Replace the stub `refresh`, `doctor`, `status` functions in `tools/content-pipeline/src/birdy_fetcher/cli.py`:

```python
@main.command()
def doctor() -> None:
    """Run pre-flight checks (env vars, sources, cache health)."""
    from pathlib import Path

    from rich.console import Console
    from rich.table import Table

    from .doctor import run_doctor

    root = Path(__file__).resolve().parent.parent.parent
    report = run_doctor(root=root)
    table = Table(title="birdy-fetcher doctor")
    table.add_column("Check")
    table.add_column("OK")
    table.add_column("Detail")
    for c in report.checks:
        table.add_row(c.name, "✓" if c.ok else "✗", c.detail)
    Console().print(table)
    if not report.is_ok:
        raise click.exceptions.Exit(1)


@main.command()
@click.option("--all", "all_species", is_flag=True)
@click.option("--species", multiple=True, help="Q-ID(s) to refresh.")
@click.option(
    "--field",
    type=click.Choice(["text", "images", "all"]),
    default="all",
)
@click.option("--stale", is_flag=True)
@click.option("--force", is_flag=True)
@click.option("--resume", is_flag=True)
@click.option("--workers", type=int, default=4)
@click.option("--max-cost", type=float, default=None)
@click.option("--dry-run", is_flag=True)
@click.option("--model", type=click.Choice(["haiku", "sonnet"]), default="haiku")
def refresh(
    all_species: bool,
    species: tuple[str, ...],
    field: str,
    stale: bool,
    force: bool,
    resume: bool,
    workers: int,
    max_cost: float | None,
    dry_run: bool,
    model: str,
) -> None:
    """Refresh species data from external sources."""
    import asyncio
    from pathlib import Path

    from .orchestrator import RefreshOptions, build_context, run_refresh

    if not (all_species or species):
        raise click.UsageError("Must pass --all or --species Q-ID")

    pipeline_root = Path(__file__).resolve().parent.parent.parent
    content_root = pipeline_root.parent.parent / "shared" / "content"

    options = RefreshOptions(
        species_filter=list(species) if species else None,
        field=field,
        force=force,
        dry_run=dry_run,
        workers=workers,
        model=model,
        max_cost=max_cost,
    )
    ctx = build_context(pipeline_root, content_root, options)
    exit_code = asyncio.run(run_refresh(ctx))
    raise click.exceptions.Exit(exit_code)


@main.command()
def status() -> None:
    """Report on coverage, review status, cache health."""
    from pathlib import Path

    import yaml
    from rich.console import Console
    from rich.table import Table

    pipeline_root = Path(__file__).resolve().parent.parent.parent
    content_root = pipeline_root.parent.parent / "shared" / "content"

    species_list = (
        yaml.safe_load((pipeline_root / "species_list.yaml").read_text(encoding="utf-8"))
        if (pipeline_root / "species_list.yaml").exists()
        else []
    )
    yaml_files = list((content_root / "species").rglob("*.yaml"))

    table = Table(title="birdy-fetcher status")
    table.add_column("Metric")
    table.add_column("Value")
    table.add_row("listed species", str(len(species_list or [])))
    table.add_row("YAML files committed", str(len(yaml_files)))

    review_counts = {"approved": 0, "auto": 0, "needs_review": 0}
    for path in yaml_files:
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
        status_value = data.get("review_status", "auto")
        review_counts[status_value] = review_counts.get(status_value, 0) + 1
    for k, v in review_counts.items():
        table.add_row(f"  review_status: {k}", str(v))

    cache = pipeline_root / ".cache"
    table.add_row(
        "cache entries",
        str(sum(1 for _ in cache.iterdir())) if cache.exists() else "0",
    )
    Console().print(table)
```

- [ ] **Step 11: Run all Python tests + lint**

```bash
uv run pytest -v
uv run ruff check
uv run ruff format --check
uv run mypy
```

Expected: all green.

- [ ] **Step 12: Commit**

```bash
git add tools/content-pipeline/ .gitignore
git commit -m "feat(content): yaml writer + orchestrator + doctor + cli wiring"
```

---

### Task 9: Walking skeleton — fetch 5 species + commit content

**Type:** end-to-end smoke test against real APIs. Produces 5 committed YAML files + ~10 images.

**Files:**
- Generated: `shared/content/species/paridae/Q25485.yaml` (Talgoxe)
- Generated: `shared/content/species/turdidae/Q25234.yaml` (Koltrast)
- Generated: `shared/content/species/paridae/Q25404.yaml` (Blåmes)
- Generated: `shared/content/species/anatidae/Q25402.yaml` (Knölsvan)
- Generated: `shared/content/species/falconidae/Q26490.yaml` (Tornfalk)
- Generated: `shared/content/images/{Q-ID}/{hero,secondary-N}.jpg` for each
- Modify: `shared/content/overrides.yaml` (create empty)
- Modify: `shared/content/expected-species-count.txt` (write `5`)

> **User actions required:** confirm `tools/content-pipeline/.env` has `ANTHROPIC_API_KEY` and run-cost is acceptable (~$0.05).

- [ ] **Step 1: Run doctor**

```bash
cd tools/content-pipeline
uv run birdy-fetcher doctor
```

Expected: all checks pass. If any fail, fix before continuing (e.g. drop sources files, set API key).

- [ ] **Step 2: Verify Task 3 was completed and `species_list.yaml` is in place**

```bash
test -f species_list.yaml && head -20 species_list.yaml
```

If missing, halt — Task 3 must be done first.

- [ ] **Step 3: Create empty `shared/content/overrides.yaml`**

```bash
cd ../..
mkdir -p shared/content
echo "# Overrides for committed species YAML." > shared/content/overrides.yaml
echo "# See docs/superpowers/specs/2026-05-02-content-pipeline-design.md sek 4.3" >> shared/content/overrides.yaml
```

- [ ] **Step 4: Write expected count file**

```bash
echo "5" > shared/content/expected-species-count.txt
```

- [ ] **Step 5: Dry-run for Talgoxe to sanity-check the pipeline**

```bash
cd tools/content-pipeline
uv run birdy-fetcher refresh --species Q25485 --dry-run
```

Expected: prints intended actions, no API calls, exit 0.

- [ ] **Step 6: Real refresh for the 5 walking-skeleton species**

```bash
uv run birdy-fetcher refresh \
    --species Q25485 \
    --species Q25234 \
    --species Q25404 \
    --species Q25402 \
    --species Q26490 \
    --max-cost 0.50
```

Expected: 5 YAML files written, 5-10 images written, total cost <$0.10. Run takes ~1-2 minutes.

- [ ] **Step 7: Spot-check the YAML output**

Read each generated YAML; verify:
- `id` field matches the Q-ID
- `names.sv` is set
- `description.sv` is 80-250 words and reads sensibly
- `image_refs` has at least one hero with width ≥2048

If any field is empty or wrong: figure out which step failed, possibly add to `overrides.yaml`, re-run that species. Do not move on with broken content.

- [ ] **Step 8: Spot-check one image**

Open `shared/content/images/Q25485/hero.jpg` in an image viewer. It should be a recognizable photo of a great tit, ≥2400px wide, JPEG.

- [ ] **Step 9: Run status**

```bash
uv run birdy-fetcher status
```

Expected: 5 YAML files, 5 in `auto`, ~700 listed species.

- [ ] **Step 10: Commit**

```bash
cd ../..
git add shared/content/
git commit -m "data(content): walking skeleton — 5 species (talgoxe, koltrast, blåmes, knölsvan, tornfalk)"
```

---

### Task 10: SQLDelight schemas + Kotlin DTOs + YAML parser (kaml)

**Files:**
- Modify: `gradle/libs.versions.toml` (add kaml, sqldelight-jvm-driver)
- Modify: `shared/content/build.gradle.kts` (apply sqldelight plugin, add kaml + jackson dependencies, configure jvm target deps, set up `sqldelight` block)
- Create: `shared/content/src/commonMain/sqldelight/se/birdy/content/Species.sq`
- Create: `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesName.sq`
- Create: `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesText.sq`
- Create: `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesRegion.sq`
- Create: `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesSeason.sq`
- Create: `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesImage.sq`
- Create: `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesTaxonomy.sq`
- Create: `shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesYaml.kt`
- Create: `shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesYamlParser.kt`
- Create: `shared/content/src/jvmTest/kotlin/se/birdy/content/build/SpeciesYamlParserTest.kt`
- Create: `shared/content/src/jvmTest/resources/fixtures/species/paridae/Q25485.yaml`

> **YAML library decision (locked):** kaml 0.65.0 (charleskorn/kaml). kotlinx.serialization-based, native Kotlin, KMP-friendly. No POC needed.

- [ ] **Step 1: Add kaml + sqldelight jvm driver to `gradle/libs.versions.toml`**

In the `[versions]` block, add:

```toml
kaml = "0.65.0"
kotlinx-serialization = "1.7.3"
```

In the `[libraries]` block, add:

```toml
kaml = { module = "com.charleskorn.kaml:kaml", version.ref = "kaml" }
kotlinx-serialization-core = { module = "org.jetbrains.kotlinx:kotlinx-serialization-core", version.ref = "kotlinx-serialization" }
sqldelight-sqlite-driver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
```

In the `[plugins]` block, add:

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Update `shared/content/build.gradle.kts`**

Replace contents with:

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget()
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:domain"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.kaml)
            implementation(libs.sqldelight.sqlite.driver)
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
    }
}

android {
    namespace = "se.birdy.content"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("BirdyContent") {
            packageName.set("se.birdy.content.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Write `shared/content/src/commonMain/sqldelight/se/birdy/content/Species.sq`**

```sql
CREATE TABLE Species (
    id TEXT NOT NULL PRIMARY KEY,
    scientific_name TEXT NOT NULL,
    abundance TEXT NOT NULL,
    iucn_status TEXT NOT NULL,
    generated_at TEXT NOT NULL,
    review_status TEXT NOT NULL,
    wikipedia_sv_revision TEXT,
    wikipedia_en_revision TEXT,
    claude_model TEXT
);

CREATE INDEX species_abundance ON Species(abundance);

selectAll:
SELECT * FROM Species;

selectById:
SELECT * FROM Species WHERE id = ?;

count:
SELECT count(*) FROM Species;

insert:
INSERT INTO Species(
    id, scientific_name, abundance, iucn_status, generated_at,
    review_status, wikipedia_sv_revision, wikipedia_en_revision, claude_model
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
```

- [ ] **Step 4: Write `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesName.sq`**

```sql
CREATE TABLE SpeciesName (
    species_id TEXT NOT NULL,
    locale TEXT NOT NULL,
    name TEXT NOT NULL,
    PRIMARY KEY (species_id, locale),
    FOREIGN KEY (species_id) REFERENCES Species(id) ON DELETE CASCADE
);

CREATE INDEX species_name_locale_name ON SpeciesName(locale, name);

selectByLocale:
SELECT * FROM SpeciesName WHERE locale = ?;

selectBySpecies:
SELECT * FROM SpeciesName WHERE species_id = ?;

searchByName:
SELECT * FROM SpeciesName WHERE locale = ? AND name LIKE ('%' || ? || '%') ORDER BY name LIMIT ?;

insert:
INSERT INTO SpeciesName(species_id, locale, name) VALUES (?, ?, ?);
```

- [ ] **Step 5: Write `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesText.sq`**

```sql
CREATE TABLE SpeciesText (
    species_id TEXT NOT NULL,
    locale TEXT NOT NULL,
    kind TEXT NOT NULL,
    text TEXT NOT NULL,
    PRIMARY KEY (species_id, locale, kind),
    FOREIGN KEY (species_id) REFERENCES Species(id) ON DELETE CASCADE
);

selectBySpecies:
SELECT * FROM SpeciesText WHERE species_id = ?;

selectBySpeciesAndLocale:
SELECT * FROM SpeciesText WHERE species_id = ? AND locale = ?;

insert:
INSERT INTO SpeciesText(species_id, locale, kind, text) VALUES (?, ?, ?, ?);
```

- [ ] **Step 6: Write `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesRegion.sq`**

```sql
CREATE TABLE SpeciesRegion (
    species_id TEXT NOT NULL,
    region_iso TEXT NOT NULL,
    PRIMARY KEY (species_id, region_iso),
    FOREIGN KEY (species_id) REFERENCES Species(id) ON DELETE CASCADE
);

CREATE INDEX species_region_iso ON SpeciesRegion(region_iso);

selectBySpecies:
SELECT region_iso FROM SpeciesRegion WHERE species_id = ?;

selectByRegion:
SELECT species_id FROM SpeciesRegion WHERE region_iso = ?;

insert:
INSERT INTO SpeciesRegion(species_id, region_iso) VALUES (?, ?);
```

- [ ] **Step 7: Write `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesSeason.sq`**

```sql
CREATE TABLE SpeciesSeason (
    species_id TEXT NOT NULL,
    month TEXT NOT NULL,
    status TEXT NOT NULL,
    PRIMARY KEY (species_id, month),
    FOREIGN KEY (species_id) REFERENCES Species(id) ON DELETE CASCADE
);

selectBySpecies:
SELECT * FROM SpeciesSeason WHERE species_id = ?;

insert:
INSERT INTO SpeciesSeason(species_id, month, status) VALUES (?, ?, ?);
```

- [ ] **Step 8: Write `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesImage.sq`**

```sql
CREATE TABLE SpeciesImage (
    species_id TEXT NOT NULL,
    role TEXT NOT NULL,
    path TEXT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    license TEXT NOT NULL,
    author TEXT NOT NULL,
    source_url TEXT NOT NULL,
    commons_filename TEXT NOT NULL,
    PRIMARY KEY (species_id, role, path),
    FOREIGN KEY (species_id) REFERENCES Species(id) ON DELETE CASCADE
);

selectBySpecies:
SELECT * FROM SpeciesImage WHERE species_id = ? ORDER BY role, path;

insert:
INSERT INTO SpeciesImage(
    species_id, role, path, width, height, license, author, source_url, commons_filename
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
```

- [ ] **Step 9: Write `shared/content/src/commonMain/sqldelight/se/birdy/content/SpeciesTaxonomy.sq`**

```sql
CREATE TABLE SpeciesTaxonomy (
    species_id TEXT NOT NULL PRIMARY KEY,
    family TEXT NOT NULL,
    family_sv TEXT,
    genus TEXT NOT NULL,
    ioc_order TEXT NOT NULL,
    FOREIGN KEY (species_id) REFERENCES Species(id) ON DELETE CASCADE
);

CREATE INDEX species_taxonomy_family ON SpeciesTaxonomy(family);

selectBySpecies:
SELECT * FROM SpeciesTaxonomy WHERE species_id = ?;

selectByFamily:
SELECT species_id FROM SpeciesTaxonomy WHERE family = ?;

insert:
INSERT INTO SpeciesTaxonomy(species_id, family, family_sv, genus, ioc_order) VALUES (?, ?, ?, ?, ?);
```

- [ ] **Step 10: Generate SQLDelight types and confirm they compile**

```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :shared:content:generateCommonMainBirdyContentInterface
```

Expected: BUILD SUCCESSFUL. Generated sources appear under `shared/content/build/generated/sqldelight/`.

- [ ] **Step 11: Write `shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesYaml.kt`**

```kotlin
package se.birdy.content.build

import kotlinx.serialization.Serializable

@Serializable
data class SpeciesYaml(
    val id: String,
    val scientific_name: String,
    val taxonomy: TaxonomyYaml,
    val names: NamesYaml,
    val abundance: String,
    val iucn_status: String,
    val season: Map<String, String>,
    val regions: List<String>,
    val description: Map<String, String?> = emptyMap(),
    val migration: Map<String, String?> = emptyMap(),
    val image_refs: List<ImageRefYaml> = emptyList(),
    val review_status: String = "auto",
    val review_notes: String = "",
    val generated_at: String = "",
    val sources: SourcesYaml = SourcesYaml(),
)

@Serializable
data class TaxonomyYaml(
    val family: String,
    val family_sv: String? = null,
    val genus: String,
    val ioc_order: String,
)

@Serializable
data class NamesYaml(
    val sv: String? = null,
    val en: String,
)

@Serializable
data class ImageRefYaml(
    val role: String,
    val path: String,
    val width: Int,
    val height: Int,
    val license: String,
    val author: String,
    val source_url: String,
    val commons_filename: String,
)

@Serializable
data class SourcesYaml(
    val wikipedia_sv_revision: String? = null,
    val wikipedia_en_revision: String? = null,
    val wikidata_revision: String? = null,
    val claude_model: String? = null,
)
```

- [ ] **Step 12: Write `shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesYamlParser.kt`**

```kotlin
package se.birdy.content.build

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText

class SpeciesYamlParser {
    private val yaml: Yaml =
        Yaml(
            configuration =
                YamlConfiguration(
                    strictMode = false,
                    encodeDefaults = false,
                ),
        )

    fun parse(path: Path): SpeciesYaml = yaml.decodeFromString(SpeciesYaml.serializer(), path.readText(Charsets.UTF_8))

    fun parseAll(speciesRoot: Path): List<Pair<Path, SpeciesYaml>> {
        if (!Files.isDirectory(speciesRoot)) return emptyList()
        return Files
            .walk(speciesRoot)
            .use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.extension == "yaml" }
                    .map { it to parse(it) }
                    .toList()
            }
    }
}
```

- [ ] **Step 13: Write fixture YAML `shared/content/src/jvmTest/resources/fixtures/species/paridae/Q25485.yaml`**

```yaml
id: Q25485
scientific_name: Parus major
taxonomy:
  family: Paridae
  family_sv: Mesar
  genus: Parus
  ioc_order: Passeriformes
names:
  sv: Talgoxe
  en: Great Tit
abundance: allmän
iucn_status: LC
season:
  jan: present
  feb: present
  mar: present
  apr: breeding
  may: breeding
  jun: breeding
  jul: breeding
  aug: present
  sep: present
  oct: present
  nov: present
  dec: present
regions:
  - SE
  - NO
  - FI
  - DK
description:
  sv: |
    Talgoxen är en av Sveriges vanligaste fåglar och kan ses året runt i trädgårdar,
    parker och skogsbryn. Den är lätt att känna igen på sin gula buk med svart slips
    som löper från hakan ner över bröstet — slipsen är bredare hos hannen än hos honan.
    Talgoxen är stark och utmärkt anpassad till människans miljöer; den utnyttjar
    fågelmatningar villigt och bygger gärna bo i fågelholkar. Sången är ett ringande
    "ti-tit-tit, ti-tit-tit" som hörs tydligt redan i februari.
  en: |
    The Great Tit is among Sweden's most familiar birds, and can be seen year round in
    gardens, parks and forest edges. It is easily recognised by its yellow underparts
    with a black "tie" running from the chin down across the chest — broader in the male
    than the female. The Great Tit is strong, adaptable to human environments, takes
    readily to bird feeders and builds nests gladly in bird boxes. Its song is a ringing
    "ti-tit-tit" that can be heard clearly already in February.
migration:
  sv: |
    Stationär i Sverige året runt. Vissa individer från nordliga populationer flyttar
    söderut under hårda vintrar.
  en: |
    Resident year-round in Sweden. Some individuals from northern populations move south
    during harsh winters.
image_refs:
  - role: hero
    path: Q25485/hero.jpg
    width: 2400
    height: 1800
    license: CC-BY-SA-4.0
    author: Pierre Dalous
    source_url: https://commons.wikimedia.org/wiki/File:Parus_major_-_garden.jpg
    commons_filename: Parus_major_-_garden.jpg
review_status: approved
review_notes: ""
generated_at: 2026-05-02T14:30:00Z
sources:
  wikipedia_sv_revision: "12345678"
  wikipedia_en_revision: "87654321"
  claude_model: claude-haiku-4-5-20251001
```

- [ ] **Step 14: Write the failing parser test `shared/content/src/jvmTest/kotlin/se/birdy/content/build/SpeciesYamlParserTest.kt`**

```kotlin
package se.birdy.content.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class SpeciesYamlParserTest {
    private val parser = SpeciesYamlParser()
    private val fixtureRoot: Path = Path.of("src/jvmTest/resources/fixtures/species")

    @Test
    fun `parses a single yaml file`() {
        val yaml = parser.parse(fixtureRoot.resolve("paridae/Q25485.yaml"))
        assertEquals("Q25485", yaml.id)
        assertEquals("Parus major", yaml.scientific_name)
        assertEquals("Paridae", yaml.taxonomy.family)
        assertEquals("Talgoxe", yaml.names.sv)
        assertEquals("allmän", yaml.abundance)
        assertEquals(1, yaml.image_refs.size)
        assertEquals("hero", yaml.image_refs[0].role)
    }

    @Test
    fun `parseAll walks all yaml files`() {
        val parsed = parser.parseAll(fixtureRoot)
        assertTrue(parsed.isNotEmpty())
        assertTrue(parsed.any { it.second.id == "Q25485" })
    }
}
```

- [ ] **Step 15: Run the failing test**

```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.build.SpeciesYamlParserTest"
```

Expected: 2 tests run, 2 pass (parser is already implemented). If kaml fails on the multi-line string format (`|`), confirm `strictMode = false` — kaml is forgiving by default but can be picky about extra fields.

- [ ] **Step 16: Run ktlint + detekt + parser test in one shot**

```bash
./gradlew :shared:content:ktlintCheck :shared:content:detekt :shared:content:jvmTest
```

Expected: all green.

- [ ] **Step 17: Commit**

```bash
git add gradle/libs.versions.toml shared/content/
git commit -m "feat(content): SQLDelight schemas + kaml YAML parser + DTOs (jvmTest green)"
```

---

### Task 11: Validator + `validateSpeciesData` Gradle task

**Files:**
- Create: `shared/content/src/jvmMain/kotlin/se/birdy/content/build/ValidationError.kt`
- Create: `shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesValidator.kt`
- Create: `shared/content/src/jvmMain/kotlin/se/birdy/content/build/ValidateMain.kt`
- Modify: `shared/content/build.gradle.kts` (register `validateSpeciesData` task as JavaExec)
- Create: `shared/content/src/jvmTest/kotlin/se/birdy/content/build/SpeciesValidatorTest.kt`
- Create: `shared/content/src/jvmTest/resources/fixtures/invalid/short_description.yaml`
- Create: `shared/content/src/jvmTest/resources/fixtures/invalid/missing_image_file.yaml`

- [ ] **Step 1: Write `shared/content/src/jvmMain/kotlin/se/birdy/content/build/ValidationError.kt`**

```kotlin
package se.birdy.content.build

data class ValidationError(
    val species: String,
    val rule: String,
    val message: String,
) {
    fun format(): String = "$species [$rule] $message"
}
```

- [ ] **Step 2: Write the failing test `shared/content/src/jvmTest/kotlin/se/birdy/content/build/SpeciesValidatorTest.kt`**

```kotlin
package se.birdy.content.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class SpeciesValidatorTest {
    private val parser = SpeciesYamlParser()
    private val fixtureRoot: Path = Path.of("src/jvmTest/resources/fixtures/species")

    @Test
    fun `valid fixture passes`() {
        val items = parser.parseAll(fixtureRoot)
        val validator =
            SpeciesValidator(
                imageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
                expectedCount = items.size,
                overrides = emptyMap(),
            )
        val errors = validator.validate(items)
        assertTrue(errors.isEmpty(), "Expected no errors, got: ${errors.joinToString("\n") { it.format() }}")
    }

    @Test
    fun `short description is rejected`() {
        val items =
            parser.parseAll(
                Path.of("src/jvmTest/resources/fixtures/invalid/short-desc"),
            )
        val validator =
            SpeciesValidator(
                imageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
                expectedCount = 1,
                overrides = emptyMap(),
            )
        val errors = validator.validate(items)
        assertTrue(errors.any { it.rule == "description-too-short" })
    }

    @Test
    fun `id mismatch between filename and field is rejected`() {
        val items =
            parser.parseAll(
                Path.of("src/jvmTest/resources/fixtures/invalid/id-mismatch"),
            )
        val validator =
            SpeciesValidator(
                imageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
                expectedCount = 1,
                overrides = emptyMap(),
            )
        val errors = validator.validate(items)
        assertTrue(errors.any { it.rule == "filename-id-mismatch" })
    }

    @Test
    fun `unknown region code is rejected`() {
        val items =
            parser.parseAll(
                Path.of("src/jvmTest/resources/fixtures/invalid/bad-region"),
            )
        val validator =
            SpeciesValidator(
                imageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
                expectedCount = 1,
                overrides = emptyMap(),
            )
        val errors = validator.validate(items)
        assertTrue(errors.any { it.rule == "invalid-region" })
    }

    @Test
    fun `species count below expected is rejected`() {
        val items = parser.parseAll(fixtureRoot)
        val validator =
            SpeciesValidator(
                imageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
                expectedCount = items.size + 10,
                overrides = emptyMap(),
            )
        val errors = validator.validate(items)
        assertTrue(errors.any { it.rule == "expected-count-mismatch" })
    }

    @Test
    fun `common species needing review still in auto state is rejected`() {
        val items = parser.parseAll(fixtureRoot)
        val mutated =
            items.map { (path, yaml) ->
                path to yaml.copy(review_status = "auto", abundance = "allmän")
            }
        val validator =
            SpeciesValidator(
                imageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
                expectedCount = mutated.size,
                overrides = emptyMap(),
            )
        val errors = validator.validate(mutated)
        assertTrue(errors.any { it.rule == "common-needs-approval" })
    }
}
```

- [ ] **Step 3: Add invalid fixtures**

`shared/content/src/jvmTest/resources/fixtures/invalid/short-desc/paridae/Q25485.yaml` — copy of valid fixture but with description shortened to one sentence:

```yaml
id: Q25485
scientific_name: Parus major
taxonomy: {family: Paridae, family_sv: Mesar, genus: Parus, ioc_order: Passeriformes}
names: {sv: Talgoxe, en: Great Tit}
abundance: ovanlig
iucn_status: LC
season: {jan: present, feb: present, mar: present, apr: present, may: present, jun: present, jul: present, aug: present, sep: present, oct: present, nov: present, dec: present}
regions: [SE]
description: {sv: "Kort.", en: "Short."}
migration: {sv: "x", en: "x"}
image_refs: []
review_status: auto
generated_at: 2026-05-02T14:30:00Z
sources: {}
```

`shared/content/src/jvmTest/resources/fixtures/invalid/id-mismatch/paridae/Q25485.yaml` — same valid YAML but `id: Q99999`:

```yaml
id: Q99999
scientific_name: Parus major
taxonomy: {family: Paridae, family_sv: Mesar, genus: Parus, ioc_order: Passeriformes}
names: {sv: Talgoxe, en: Great Tit}
abundance: ovanlig
iucn_status: LC
season: {jan: present, feb: present, mar: present, apr: present, may: present, jun: present, jul: present, aug: present, sep: present, oct: present, nov: present, dec: present}
regions: [SE]
description:
  sv: |
    En tillräckligt lång beskrivning för att passera valideringen så att vi kan testa
    den fysiska match-regeln mellan filnamn och id-fält ordentligt utan att fastna på
    description-too-short. Lägger till tillräckligt mycket text för att komma över
    åttiordstrosken. Lägger till ännu lite mer text så att tröskeln passeras säkert.
  en: |
    A description long enough to pass the description-length validation so we can test
    the filename-vs-id-field matching rule cleanly without getting tripped up on the
    description-too-short rule. Adding enough text to clear the eighty-word threshold.
    Adding still more text so the threshold is cleared safely.
migration: {sv: "Stationär. Detaljerad text.", en: "Resident. Detailed text."}
image_refs:
  - {role: hero, path: Q99999/hero.jpg, width: 2400, height: 1800, license: PD, author: x, source_url: x, commons_filename: x}
review_status: auto
generated_at: 2026-05-02T14:30:00Z
sources: {}
```

`shared/content/src/jvmTest/resources/fixtures/invalid/bad-region/paridae/Q25485.yaml` — valid except `regions: [XX]`:

```yaml
id: Q25485
scientific_name: Parus major
taxonomy: {family: Paridae, family_sv: Mesar, genus: Parus, ioc_order: Passeriformes}
names: {sv: Talgoxe, en: Great Tit}
abundance: ovanlig
iucn_status: LC
season: {jan: present, feb: present, mar: present, apr: present, may: present, jun: present, jul: present, aug: present, sep: present, oct: present, nov: present, dec: present}
regions: [XX]
description:
  sv: |
    Tillräckligt lång svensk beskrivning för att klara valideringens åttiordstrosken;
    detta används bara för att testa att fel regionkod fångas av validatorn.
    Lägger till mer text för marginal.
  en: |
    Long enough English description for the validator to pass the eighty-word threshold;
    only purpose is to test that the bad region code is caught by validation.
    Padding with more words for safety.
migration: {sv: "x. Detaljerad text.", en: "x. Detailed text."}
image_refs:
  - {role: hero, path: Q25485/hero.jpg, width: 2400, height: 1800, license: PD, author: x, source_url: x, commons_filename: x}
review_status: auto
generated_at: 2026-05-02T14:30:00Z
sources: {}
```

Also create the matching fixture image dirs (empty placeholder file is fine since tests bypass image-existence checks for these specific rules — see image_root note in implementation):

```bash
mkdir -p shared/content/src/jvmTest/resources/fixtures/images/Q25485
mkdir -p shared/content/src/jvmTest/resources/fixtures/images/Q99999
echo "placeholder" > shared/content/src/jvmTest/resources/fixtures/images/Q25485/hero.jpg
echo "placeholder" > shared/content/src/jvmTest/resources/fixtures/images/Q99999/hero.jpg
```

- [ ] **Step 4: Run failing tests**

```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.build.SpeciesValidatorTest"
```

Expected: 6 tests, 6 fail (validator class doesn't exist yet).

- [ ] **Step 5: Implement `shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesValidator.kt`**

```kotlin
package se.birdy.content.build

import java.nio.file.Files
import java.nio.file.Path

class SpeciesValidator(
    private val imageRoot: Path,
    private val expectedCount: Int,
    private val overrides: Map<String, OverrideEntry>,
) {
    fun validate(items: List<Pair<Path, SpeciesYaml>>): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val seenIds = mutableSetOf<String>()

        if (items.size < expectedCount) {
            errors +=
                ValidationError(
                    species = "(global)",
                    rule = "expected-count-mismatch",
                    message = "Expected ${expectedCount} species, got ${items.size}",
                )
        }

        for ((path, yaml) in items) {
            errors += validateOne(path, yaml)
            if (!seenIds.add(yaml.id)) {
                errors +=
                    ValidationError(yaml.id, "duplicate-id", "id appears in multiple files")
            }
        }

        return errors
    }

    private fun validateOne(
        path: Path,
        yaml: SpeciesYaml,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val expectedFilename = "${yaml.id}.yaml"
        if (path.fileName.toString() != expectedFilename) {
            errors += ValidationError(yaml.id, "filename-id-mismatch", "file=${path.fileName}, id=${yaml.id}")
        }

        for ((lang, text) in yaml.description) {
            val resolved =
                overrides[yaml.id]?.descriptionAcceptMissing?.contains(lang) == true
            if (resolved) continue
            val words = (text ?: "").split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size < 80) {
                errors +=
                    ValidationError(
                        yaml.id,
                        "description-too-short",
                        "description.$lang has ${words.size} words (need ≥80)",
                    )
            }
        }

        if (yaml.abundance == "allmän" && yaml.review_status != "approved") {
            errors +=
                ValidationError(
                    yaml.id,
                    "common-needs-approval",
                    "abundance=allmän requires review_status=approved",
                )
        }

        for (region in yaml.regions) {
            if (region !in VALID_REGIONS) {
                errors += ValidationError(yaml.id, "invalid-region", "unknown ISO code '$region'")
            }
        }

        for (img in yaml.image_refs) {
            val full = imageRoot.resolve(img.path)
            if (!Files.exists(full)) {
                errors +=
                    ValidationError(
                        yaml.id,
                        "image-file-missing",
                        "${img.path} not found under ${imageRoot}",
                    )
            }
            if (img.role == "hero" && (img.width < 2048 && img.height < 2048)) {
                errors +=
                    ValidationError(
                        yaml.id,
                        "hero-too-small",
                        "${img.path} is ${img.width}×${img.height}, need ≥2048 on one side",
                    )
            }
            if (img.license.isBlank() || img.author.isBlank() || img.source_url.isBlank()) {
                errors +=
                    ValidationError(
                        yaml.id,
                        "image-missing-metadata",
                        "${img.path} missing license/author/source_url",
                    )
            }
        }

        if (yaml.image_refs.isEmpty() && overrides[yaml.id]?.allowMissingImages != true) {
            errors +=
                ValidationError(
                    yaml.id,
                    "no-images",
                    "image_refs empty; add images or set allow_missing_images in overrides",
                )
        }

        return errors
    }

    companion object {
        private val VALID_REGIONS =
            setOf(
                "SE", "NO", "FI", "DK", "DE", "NL", "BE", "FR", "GB", "IE",
                "PL", "AT", "CH", "IT", "ES", "PT", "GR", "IS",
            )
    }
}

data class OverrideEntry(
    val descriptionAcceptMissing: Set<String> = emptySet(),
    val allowMissingImages: Boolean = false,
)
```

- [ ] **Step 6: Implement `shared/content/src/jvmMain/kotlin/se/birdy/content/build/ValidateMain.kt`**

```kotlin
package se.birdy.content.build

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
private data class OverridesYaml(
    val species: Map<String, OverridesPatch> = emptyMap(),
)

@Serializable
private data class OverridesPatch(
    val description_accept_missing: List<String> = emptyList(),
    val allow_missing_images: Boolean = false,
)

object ValidateMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 3) { "Usage: ValidateMain <speciesDir> <imagesDir> <expectedCountFile> [overridesYaml]" }
        val speciesDir = Path.of(args[0])
        val imagesDir = Path.of(args[1])
        val expectedCount = Path.of(args[2]).toFile().readText().trim().toInt()
        val overridesPath = if (args.size >= 4) Path.of(args[3]) else null

        val parser = SpeciesYamlParser()
        val items = parser.parseAll(speciesDir)

        val overrides: Map<String, OverrideEntry> =
            if (overridesPath != null && overridesPath.toFile().exists() && overridesPath.toFile().length() > 0) {
                runCatching {
                    val text = overridesPath.toFile().readText()
                    val parsed = Yaml.default.decodeFromString(OverridesYaml.serializer(), text)
                    parsed.species.mapValues { (_, p) ->
                        OverrideEntry(
                            descriptionAcceptMissing = p.description_accept_missing.toSet(),
                            allowMissingImages = p.allow_missing_images,
                        )
                    }
                }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }

        val validator =
            SpeciesValidator(
                imageRoot = imagesDir,
                expectedCount = expectedCount,
                overrides = overrides,
            )
        val errors = validator.validate(items)

        if (errors.isEmpty()) {
            println("validateSpeciesData: ${items.size} species, all valid.")
            return
        }
        System.err.println("validateSpeciesData: ${errors.size} errors:")
        errors.forEach { System.err.println("  ${it.format()}") }
        kotlin.system.exitProcess(1)
    }
}
```

- [ ] **Step 7: Register the Gradle task in `shared/content/build.gradle.kts`**

Append to the bottom of `shared/content/build.gradle.kts`:

```kotlin
val contentRoot = layout.projectDirectory.asFile
val speciesDir = file("species")
val imagesDir = file("images")
val expectedCountFile = file("expected-species-count.txt")
val overridesFile = file("overrides.yaml")

val validateSpeciesData by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validate committed species YAML against the schema."
    dependsOn("jvmJar")
    classpath = files(tasks.named("jvmJar")) +
        configurations.getByName("jvmRuntimeClasspath")
    mainClass.set("se.birdy.content.build.ValidateMain")
    args = listOf(
        speciesDir.absolutePath,
        imagesDir.absolutePath,
        expectedCountFile.absolutePath,
        overridesFile.absolutePath,
    )
    onlyIf {
        speciesDir.exists() && expectedCountFile.exists()
    }
}

tasks.named("check") {
    dependsOn(validateSpeciesData)
}
```

- [ ] **Step 8: Run validator tests until green**

```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.build.SpeciesValidatorTest"
```

Expected: 6 passed.

- [ ] **Step 9: Run the actual validateSpeciesData against committed walking-skeleton content**

```bash
./gradlew :shared:content:validateSpeciesData
```

Expected: BUILD SUCCESSFUL with `validateSpeciesData: 5 species, all valid.` (since `expected-species-count.txt` says 5).

If errors appear (e.g. description-too-short on one of the 5 species), update the YAML by either re-running the fetcher with `--force --field text` or adding an override.

- [ ] **Step 10: Commit**

```bash
git add shared/content/
git commit -m "feat(content): SpeciesValidator + validateSpeciesData Gradle task with all schema rules"
```

---

### Task 12: `SpeciesDbBuilder` + `buildSpeciesDb` Gradle task

**Files:**
- Create: `shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesDbBuilder.kt`
- Create: `shared/content/src/jvmMain/kotlin/se/birdy/content/build/BuildMain.kt`
- Modify: `shared/content/build.gradle.kts` (register `buildSpeciesDb`)
- Create: `shared/content/src/jvmTest/kotlin/se/birdy/content/build/SpeciesDbBuilderTest.kt`

The builder reads parsed YAML, populates an in-memory SQLite DB via SQLDelight, then `VACUUM INTO` to a target file path. Image files are copied 1:1 to the assets dir. Goal: <5s for 700 species.

- [ ] **Step 1: Write the failing test `shared/content/src/jvmTest/kotlin/se/birdy/content/build/SpeciesDbBuilderTest.kt`**

```kotlin
package se.birdy.content.build

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import se.birdy.content.db.BirdyContent
import java.nio.file.Path

class SpeciesDbBuilderTest {
    private val parser = SpeciesYamlParser()

    @Test
    fun `produces a queryable sqlite file with one species`(
        @TempDir tempDir: Path,
    ) {
        val items =
            parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val outDb = tempDir.resolve("species.db")
        val outImages = tempDir.resolve("images")

        val builder = SpeciesDbBuilder()
        builder.build(
            items = items,
            sourceImageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
            targetDb = outDb,
            targetImageRoot = outImages,
        )

        assertTrue(outDb.toFile().exists(), "species.db not created")
        assertTrue(outDb.toFile().length() > 0, "species.db empty")

        val driver = JdbcSqliteDriver("jdbc:sqlite:${outDb.toAbsolutePath()}")
        val db = BirdyContent(driver)
        val count = db.speciesQueries.count().executeAsOne()
        assertEquals(items.size.toLong(), count)

        val talgoxe = db.speciesQueries.selectById("Q25485").executeAsOneOrNull()
        assertEquals("Parus major", talgoxe?.scientific_name)

        val sv = db.speciesNameQueries.selectBySpecies("Q25485").executeAsList()
        assertTrue(sv.any { it.locale == "sv" && it.name == "Talgoxe" })

        driver.close()
    }
}
```

- [ ] **Step 2: Run the failing test**

```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.build.SpeciesDbBuilderTest"
```

Expected: failure (`SpeciesDbBuilder` doesn't exist).

- [ ] **Step 3: Implement `shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesDbBuilder.kt`**

```kotlin
package se.birdy.content.build

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import se.birdy.content.db.BirdyContent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.copyTo

class SpeciesDbBuilder {
    fun build(
        items: List<Pair<Path, SpeciesYaml>>,
        sourceImageRoot: Path,
        targetDb: Path,
        targetImageRoot: Path,
    ) {
        Files.deleteIfExists(targetDb)
        targetDb.parent?.let { Files.createDirectories(it) }

        val driver =
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
                BirdyContent.Schema.create(it)
            }
        val db = BirdyContent(driver)
        db.transaction {
            for ((_, yaml) in items) {
                insertSpecies(db, yaml)
            }
        }

        driver.execute(null, "VACUUM INTO '${targetDb.toAbsolutePath()}'", 0)
        driver.close()

        Files.createDirectories(targetImageRoot)
        for ((_, yaml) in items) {
            for (img in yaml.image_refs) {
                val source = sourceImageRoot.resolve(img.path)
                if (!Files.exists(source)) continue
                val target = targetImageRoot.resolve(img.path)
                Files.createDirectories(target.parent)
                source.copyTo(target, overwrite = true)
            }
        }
    }

    private fun insertSpecies(
        db: BirdyContent,
        yaml: SpeciesYaml,
    ) {
        db.speciesQueries.insert(
            id = yaml.id,
            scientific_name = yaml.scientific_name,
            abundance = yaml.abundance,
            iucn_status = yaml.iucn_status,
            generated_at = yaml.generated_at,
            review_status = yaml.review_status,
            wikipedia_sv_revision = yaml.sources.wikipedia_sv_revision,
            wikipedia_en_revision = yaml.sources.wikipedia_en_revision,
            claude_model = yaml.sources.claude_model,
        )
        db.speciesTaxonomyQueries.insert(
            species_id = yaml.id,
            family = yaml.taxonomy.family,
            family_sv = yaml.taxonomy.family_sv,
            genus = yaml.taxonomy.genus,
            ioc_order = yaml.taxonomy.ioc_order,
        )
        if (!yaml.names.sv.isNullOrBlank()) {
            db.speciesNameQueries.insert(yaml.id, "sv", yaml.names.sv!!)
        }
        db.speciesNameQueries.insert(yaml.id, "en", yaml.names.en)

        for ((lang, text) in yaml.description) {
            if (text.isNullOrBlank() || text == "[accept_missing]") continue
            db.speciesTextQueries.insert(yaml.id, lang, "description", text)
        }
        for ((lang, text) in yaml.migration) {
            if (text.isNullOrBlank()) continue
            db.speciesTextQueries.insert(yaml.id, lang, "migration", text)
        }
        for (region in yaml.regions) {
            db.speciesRegionQueries.insert(yaml.id, region)
        }
        for ((month, status) in yaml.season) {
            db.speciesSeasonQueries.insert(yaml.id, month, status)
        }
        for (img in yaml.image_refs) {
            db.speciesImageQueries.insert(
                species_id = yaml.id,
                role = img.role,
                path = img.path,
                width = img.width.toLong(),
                height = img.height.toLong(),
                license = img.license,
                author = img.author,
                source_url = img.source_url,
                commons_filename = img.commons_filename,
            )
        }
    }
}
```

- [ ] **Step 4: Implement `shared/content/src/jvmMain/kotlin/se/birdy/content/build/BuildMain.kt`**

```kotlin
package se.birdy.content.build

import java.nio.file.Path
import kotlin.time.measureTime

object BuildMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 4) {
            "Usage: BuildMain <speciesDir> <sourceImagesDir> <targetDb> <targetImagesDir>"
        }
        val (speciesDir, sourceImages, targetDb, targetImages) = args.map(Path::of)
        val parser = SpeciesYamlParser()
        val items = parser.parseAll(speciesDir)

        val elapsed =
            measureTime {
                SpeciesDbBuilder().build(
                    items = items,
                    sourceImageRoot = sourceImages,
                    targetDb = targetDb,
                    targetImageRoot = targetImages,
                )
            }
        println("buildSpeciesDb: ${items.size} species → $targetDb in ${elapsed.inWholeMilliseconds} ms")
    }
}
```

- [ ] **Step 5: Register the Gradle task in `shared/content/build.gradle.kts`**

Append to the bottom of `shared/content/build.gradle.kts`:

```kotlin
val composeAppFilesDir =
    project(":composeApp").file("src/commonMain/composeResources/files")
val targetDb = composeAppFilesDir.resolve("species.db")
val targetImages = composeAppFilesDir.resolve("images")

val buildSpeciesDb by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Build species.db from committed YAML and copy images to composeApp assets."
    dependsOn(validateSpeciesData)
    dependsOn("jvmJar")
    classpath = files(tasks.named("jvmJar")) +
        configurations.getByName("jvmRuntimeClasspath")
    mainClass.set("se.birdy.content.build.BuildMain")
    args = listOf(
        speciesDir.absolutePath,
        imagesDir.absolutePath,
        targetDb.absolutePath,
        targetImages.absolutePath,
    )
    inputs.dir(speciesDir)
    inputs.dir(imagesDir)
    outputs.file(targetDb)
    outputs.dir(targetImages)
    onlyIf {
        speciesDir.exists()
    }
}
```

- [ ] **Step 6: Run builder tests until green**

```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.build.SpeciesDbBuilderTest"
```

Expected: 1 passed.

- [ ] **Step 7: Run the actual buildSpeciesDb task**

```bash
./gradlew :shared:content:buildSpeciesDb
```

Expected: BUILD SUCCESSFUL. `composeApp/src/commonMain/composeResources/files/species.db` exists. Output prints `buildSpeciesDb: 5 species → ... in <500 ms`.

- [ ] **Step 8: Verify the produced db with sqlite3 (sanity)**

```bash
sqlite3 composeApp/src/commonMain/composeResources/files/species.db "SELECT id, scientific_name FROM Species;"
```

Expected: 5 rows including `Q25485 | Parus major`.

If sqlite3 is not on PATH on Windows, install via `winget install SQLite.SQLite` or skip; a code-level check happens in Task 13.

- [ ] **Step 9: Commit**

```bash
git add shared/content/ composeApp/src/commonMain/composeResources/files/
git commit -m "feat(content): SpeciesDbBuilder + buildSpeciesDb Gradle task; 5-species db generated"
```

---

### Task 13: `SpeciesRepository` interface + SQLDelight implementation

**Files:**
- Create: `shared/content/src/commonMain/kotlin/se/birdy/content/SpeciesId.kt`
- Create: `shared/content/src/commonMain/kotlin/se/birdy/content/Locale.kt`
- Create: `shared/content/src/commonMain/kotlin/se/birdy/content/Abundance.kt`
- Create: `shared/content/src/commonMain/kotlin/se/birdy/content/Species.kt`
- Create: `shared/content/src/commonMain/kotlin/se/birdy/content/SpeciesFilter.kt`
- Create: `shared/content/src/commonMain/kotlin/se/birdy/content/SpeciesRepository.kt`
- Create: `shared/content/src/commonMain/kotlin/se/birdy/content/SqlDelightSpeciesRepository.kt`
- Create: `shared/content/src/jvmTest/kotlin/se/birdy/content/SpeciesRepositoryTest.kt`

This is the public API Plan 3 (Encyclopedia) consumes. It hides the SQLDelight queries behind a `Flow`-returning interface and applies i18n fallback (Swedish then English).

- [ ] **Step 1: Write the value classes**

`shared/content/src/commonMain/kotlin/se/birdy/content/SpeciesId.kt`:

```kotlin
package se.birdy.content

@JvmInline
value class SpeciesId(val raw: String) {
    init {
        require(raw.startsWith("Q") && raw.length > 1) { "Invalid SpeciesId: $raw" }
    }
}
```

`shared/content/src/commonMain/kotlin/se/birdy/content/Locale.kt`:

```kotlin
package se.birdy.content

enum class Locale(val code: String) {
    SV("sv"),
    EN("en"),
    ;

    companion object {
        fun fromCode(code: String): Locale = entries.first { it.code == code }
    }
}
```

`shared/content/src/commonMain/kotlin/se/birdy/content/Abundance.kt`:

```kotlin
package se.birdy.content

enum class Abundance(val code: String) {
    ALLMÄN("allmän"),
    MINDRE_ALLMÄN("mindre allmän"),
    OVANLIG("ovanlig"),
    SÄLLSYNT("sällsynt"),
    ;

    companion object {
        fun fromCode(code: String): Abundance? = entries.firstOrNull { it.code == code }
    }
}
```

- [ ] **Step 2: Write the data classes `Species.kt`**

```kotlin
package se.birdy.content

data class Species(
    val id: SpeciesId,
    val scientificName: String,
    val taxonomy: SpeciesTaxonomy,
    val name: String, // localized to requested locale (sv or en, with fallback)
    val abundance: Abundance,
    val iucnStatus: String,
    val regions: List<String>,
    val season: Map<String, String>,
    val description: String?, // localized
    val migration: String?,   // localized
    val images: List<SpeciesImage>,
)

data class SpeciesTaxonomy(
    val family: String,
    val familySv: String?,
    val genus: String,
    val iocOrder: String,
)

data class SpeciesImage(
    val role: String,
    val path: String,
    val width: Int,
    val height: Int,
    val license: String,
    val author: String,
    val sourceUrl: String,
)

data class SpeciesSummary(
    val id: SpeciesId,
    val name: String,
    val scientificName: String,
    val abundance: Abundance,
    val heroImagePath: String?,
)
```

- [ ] **Step 3: Write `SpeciesFilter.kt`**

```kotlin
package se.birdy.content

data class SpeciesFilter(
    val abundance: Set<Abundance> = emptySet(),
    val regions: Set<String> = emptySet(),
    val activeInMonth: String? = null, // "jan".."dec" or null
)
```

- [ ] **Step 4: Write `SpeciesRepository.kt` interface**

```kotlin
package se.birdy.content

import kotlinx.coroutines.flow.Flow

interface SpeciesRepository {
    fun getById(
        id: SpeciesId,
        locale: Locale,
    ): Flow<Species?>

    fun search(
        query: String,
        locale: Locale,
        filters: SpeciesFilter = SpeciesFilter(),
    ): Flow<List<SpeciesSummary>>

    fun listByFamily(
        familyKey: String,
        locale: Locale,
    ): Flow<List<SpeciesSummary>>

    fun all(locale: Locale): Flow<List<SpeciesSummary>>
}
```

- [ ] **Step 5: Write the failing test `shared/content/src/jvmTest/kotlin/se/birdy/content/SpeciesRepositoryTest.kt`**

```kotlin
package se.birdy.content

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import se.birdy.content.build.SpeciesDbBuilder
import se.birdy.content.build.SpeciesYamlParser
import se.birdy.content.db.BirdyContent
import java.nio.file.Path

class SpeciesRepositoryTest {
    private val parser = SpeciesYamlParser()

    private fun newDriverWithFixtures(tempDir: Path): JdbcSqliteDriver {
        val items =
            parser.parseAll(Path.of("src/jvmTest/resources/fixtures/species"))
        val outDb = tempDir.resolve("species.db")
        SpeciesDbBuilder().build(
            items = items,
            sourceImageRoot = Path.of("src/jvmTest/resources/fixtures/images"),
            targetDb = outDb,
            targetImageRoot = tempDir.resolve("images"),
        )
        return JdbcSqliteDriver("jdbc:sqlite:${outDb.toAbsolutePath()}")
    }

    @Test
    fun `get by id returns species in requested locale`(@TempDir tempDir: Path) =
        runTest {
            val driver = newDriverWithFixtures(tempDir)
            val repo = SqlDelightSpeciesRepository(BirdyContent(driver))

            val sv = repo.getById(SpeciesId("Q25485"), Locale.SV).first()
            assertNotNull(sv)
            assertEquals("Talgoxe", sv?.name)
            assertTrue(sv?.description?.contains("Talgoxen") == true)

            val en = repo.getById(SpeciesId("Q25485"), Locale.EN).first()
            assertEquals("Great Tit", en?.name)

            driver.close()
        }

    @Test
    fun `search by name returns matches`(@TempDir tempDir: Path) =
        runTest {
            val driver = newDriverWithFixtures(tempDir)
            val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
            val results =
                repo.search(query = "Talg", locale = Locale.SV, filters = SpeciesFilter()).first()
            assertTrue(results.any { it.id == SpeciesId("Q25485") })
            driver.close()
        }

    @Test
    fun `listByFamily returns all paridae`(@TempDir tempDir: Path) =
        runTest {
            val driver = newDriverWithFixtures(tempDir)
            val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
            val results = repo.listByFamily("Paridae", Locale.SV).first()
            assertTrue(results.any { it.id == SpeciesId("Q25485") })
            driver.close()
        }

    @Test
    fun `i18n fallback uses english when swedish missing`(@TempDir tempDir: Path) =
        runTest {
            val driver = newDriverWithFixtures(tempDir)
            val db = BirdyContent(driver)
            // remove sv text + sv name to force fallback
            driver.execute(null, "DELETE FROM SpeciesText WHERE locale = 'sv' AND species_id = 'Q25485'", 0)
            driver.execute(null, "DELETE FROM SpeciesName WHERE locale = 'sv' AND species_id = 'Q25485'", 0)
            val repo = SqlDelightSpeciesRepository(db)
            val sv = repo.getById(SpeciesId("Q25485"), Locale.SV).first()
            assertNotNull(sv)
            assertEquals("Great Tit", sv?.name)
            assertTrue(sv?.description?.contains("Great Tit") == true)
            driver.close()
        }
}
```

- [ ] **Step 6: Add `kotlinx-coroutines-test` to `gradle/libs.versions.toml`**

In `[versions]`:

```toml
kotlinx-coroutines = "1.9.0"
```

In `[libraries]`:

```toml
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
```

In `shared/content/build.gradle.kts`, update sourceSets:

```kotlin
commonMain.dependencies {
    implementation(project(":shared:domain"))
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.coroutines.core)
}
jvmTest.dependencies {
    implementation(libs.junit.jupiter)
    implementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 7: Run the failing test**

```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.SpeciesRepositoryTest"
```

Expected: tests fail (`SqlDelightSpeciesRepository` not defined).

- [ ] **Step 8: Implement `shared/content/src/commonMain/kotlin/se/birdy/content/SqlDelightSpeciesRepository.kt`**

```kotlin
package se.birdy.content

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import se.birdy.content.db.BirdyContent

class SqlDelightSpeciesRepository(
    private val db: BirdyContent,
) : SpeciesRepository {
    override fun getById(
        id: SpeciesId,
        locale: Locale,
    ): Flow<Species?> =
        flow {
            val row =
                db.speciesQueries
                    .selectById(id.raw)
                    .executeAsOneOrNull()
            if (row == null) {
                emit(null)
                return@flow
            }
            val taxonomy =
                db.speciesTaxonomyQueries
                    .selectBySpecies(id.raw)
                    .executeAsOne()
            val names =
                db.speciesNameQueries.selectBySpecies(id.raw).executeAsList()
            val texts =
                db.speciesTextQueries.selectBySpecies(id.raw).executeAsList()
            val regions =
                db.speciesRegionQueries.selectBySpecies(id.raw).executeAsList()
            val seasons =
                db.speciesSeasonQueries.selectBySpecies(id.raw).executeAsList()
            val images =
                db.speciesImageQueries.selectBySpecies(id.raw).executeAsList()

            val name =
                names.firstOrNull { it.locale == locale.code }?.name
                    ?: names.firstOrNull { it.locale == Locale.EN.code }?.name
                    ?: row.scientific_name
            val description = pickText(texts, locale, "description")
            val migration = pickText(texts, locale, "migration")

            emit(
                Species(
                    id = id,
                    scientificName = row.scientific_name,
                    taxonomy =
                        SpeciesTaxonomy(
                            family = taxonomy.family,
                            familySv = taxonomy.family_sv,
                            genus = taxonomy.genus,
                            iocOrder = taxonomy.ioc_order,
                        ),
                    name = name,
                    abundance =
                        Abundance.fromCode(row.abundance) ?: Abundance.OVANLIG,
                    iucnStatus = row.iucn_status,
                    regions = regions,
                    season = seasons.associate { it.month to it.status },
                    description = description,
                    migration = migration,
                    images =
                        images.map { img ->
                            SpeciesImage(
                                role = img.role,
                                path = img.path,
                                width = img.width.toInt(),
                                height = img.height.toInt(),
                                license = img.license,
                                author = img.author,
                                sourceUrl = img.source_url,
                            )
                        },
                ),
            )
        }

    override fun search(
        query: String,
        locale: Locale,
        filters: SpeciesFilter,
    ): Flow<List<SpeciesSummary>> =
        db.speciesNameQueries
            .searchByName(locale.code, query, 50L)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.mapNotNull { name ->
                    val sp =
                        db.speciesQueries
                            .selectById(name.species_id)
                            .executeAsOneOrNull() ?: return@mapNotNull null
                    val abundance =
                        Abundance.fromCode(sp.abundance) ?: Abundance.OVANLIG
                    if (filters.abundance.isNotEmpty() && abundance !in filters.abundance) {
                        return@mapNotNull null
                    }
                    SpeciesSummary(
                        id = SpeciesId(sp.id),
                        name = name.name,
                        scientificName = sp.scientific_name,
                        abundance = abundance,
                        heroImagePath =
                            db.speciesImageQueries
                                .selectBySpecies(sp.id)
                                .executeAsList()
                                .firstOrNull { it.role == "hero" }
                                ?.path,
                    )
                }
            }

    override fun listByFamily(
        familyKey: String,
        locale: Locale,
    ): Flow<List<SpeciesSummary>> =
        flow {
            val ids =
                db.speciesTaxonomyQueries
                    .selectByFamily(familyKey)
                    .executeAsList()
            emit(ids.mapNotNull { speciesId -> summaryFor(speciesId, locale) })
        }

    override fun all(locale: Locale): Flow<List<SpeciesSummary>> =
        flow {
            val rows = db.speciesQueries.selectAll().executeAsList()
            emit(rows.mapNotNull { summaryFor(it.id, locale) })
        }

    private fun summaryFor(
        speciesId: String,
        locale: Locale,
    ): SpeciesSummary? {
        val sp = db.speciesQueries.selectById(speciesId).executeAsOneOrNull() ?: return null
        val names = db.speciesNameQueries.selectBySpecies(speciesId).executeAsList()
        val name =
            names.firstOrNull { it.locale == locale.code }?.name
                ?: names.firstOrNull { it.locale == Locale.EN.code }?.name
                ?: sp.scientific_name
        val hero =
            db.speciesImageQueries
                .selectBySpecies(speciesId)
                .executeAsList()
                .firstOrNull { it.role == "hero" }
                ?.path
        return SpeciesSummary(
            id = SpeciesId(sp.id),
            name = name,
            scientificName = sp.scientific_name,
            abundance = Abundance.fromCode(sp.abundance) ?: Abundance.OVANLIG,
            heroImagePath = hero,
        )
    }

    private fun pickText(
        texts: List<se.birdy.content.db.SpeciesText>,
        locale: Locale,
        kind: String,
    ): String? {
        val match = texts.firstOrNull { it.locale == locale.code && it.kind == kind }
        if (match != null) return match.text
        return texts.firstOrNull { it.locale == Locale.EN.code && it.kind == kind }?.text
    }
}
```

- [ ] **Step 9: Run repository tests until green**

```bash
./gradlew :shared:content:jvmTest --tests "se.birdy.content.SpeciesRepositoryTest"
```

Expected: 4 passed.

- [ ] **Step 10: Run all checks**

```bash
./gradlew :shared:content:check
```

Expected: ktlint, detekt, jvmTest, validateSpeciesData all green.

- [ ] **Step 11: Commit**

```bash
git add gradle/libs.versions.toml shared/content/
git commit -m "feat(content): SpeciesRepository public API + SQLDelight implementation with i18n fallback"
```

---

### Task 14: Wire `species.db` + images into `composeApp` and verify on device

**Files:**
- Modify: `composeApp/build.gradle.kts` (depend on `:shared:content`, ensure assembleDebug depends on `buildSpeciesDb`)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/HomeScreen.kt` (read species count from db as smoke)
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/SpeciesRepositoryProvider.kt` (expect/actual)
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/SpeciesRepositoryProvider.android.kt`
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/SpeciesRepositoryProvider.ios.kt` (stub returns null for now)

> **Goal of this task:** demonstrate the bundled `species.db` is reachable by the app at runtime and contains the 5 walking-skeleton species. The HomeScreen renders something concrete, like "5 fågelarter laddade — Talgoxe, Koltrast, ...". This is a smoke test, not the Encyclopedia UI (Plan 3).

- [ ] **Step 1: Update `composeApp/build.gradle.kts`** to depend on `:shared:content` and ensure `species.db` is generated before package

Add to its `commonMain.dependencies` block:

```kotlin
implementation(project(":shared:content"))
implementation(libs.sqldelight.coroutines)
```

In its `androidMain.dependencies` block:

```kotlin
implementation(libs.sqldelight.android.driver)
```

In its `jvmMain.dependencies` (desktop, if present) or `jvmTest`:

```kotlin
implementation(libs.sqldelight.sqlite.driver)
```

At the bottom of `composeApp/build.gradle.kts`, ensure `assembleDebug` depends on the species build:

```kotlin
afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") || it.name.startsWith("merge") && it.name.contains("Asset") }
        .configureEach {
            dependsOn(":shared:content:buildSpeciesDb")
        }
}
```

(Adjust the dependency hook if AGP places assets at a different lifecycle on this project — `compose-resources` files are typically pulled in at `processResources`.)

- [ ] **Step 2: Write `composeApp/src/commonMain/kotlin/se/birdy/app/SpeciesRepositoryProvider.kt`**

```kotlin
package se.birdy.app

import se.birdy.content.SpeciesRepository

expect object SpeciesRepositoryProvider {
    fun get(): SpeciesRepository
}
```

- [ ] **Step 3: Write `composeApp/src/androidMain/kotlin/se/birdy/app/SpeciesRepositoryProvider.android.kt`**

```kotlin
package se.birdy.app

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import se.birdy.content.SpeciesRepository
import se.birdy.content.SqlDelightSpeciesRepository
import se.birdy.content.db.BirdyContent
import java.io.File
import java.io.FileOutputStream

actual object SpeciesRepositoryProvider {
    private var instance: SpeciesRepository? = null
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun get(): SpeciesRepository {
        instance?.let { return it }
        val dbFile = File(appContext.filesDir, "species.db")
        if (!dbFile.exists()) {
            appContext.assets.open("composeResources/composeApp.composeResources/files/species.db").use { input ->
                FileOutputStream(dbFile).use { output -> input.copyTo(output) }
            }
        }
        val driver = AndroidSqliteDriver(BirdyContent.Schema, appContext, "species.db")
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        instance = repo
        return repo
    }
}
```

> **Caveat:** Compose Multiplatform's `Res.readBytes("files/species.db")` is the canonical way to read bundled files; the `assets.open` path above mirrors the actual location AGP uses. If the asset path doesn't resolve at runtime (`FileNotFoundException`), inspect the produced APK with `unzip -l` to find where compose-resources writes `files/`. Update the path string accordingly. The exact path under `assets/` depends on the Compose Multiplatform version.

- [ ] **Step 4: Write the iOS stub `composeApp/src/iosMain/kotlin/se/birdy/app/SpeciesRepositoryProvider.ios.kt`**

```kotlin
package se.birdy.app

import se.birdy.content.SpeciesRepository

actual object SpeciesRepositoryProvider {
    actual fun get(): SpeciesRepository =
        throw NotImplementedError("iOS DB driver wiring is Plan 6")
}
```

- [ ] **Step 5: Update `MainActivity.kt`** (one-line init):

`androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` — call provider init in `onCreate`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    se.birdy.app.SpeciesRepositoryProvider.init(applicationContext)
    setContent {
        BirdyTheme {
            App()
        }
    }
}
```

(Adjust based on what Plan 1's MainActivity looks like; if it already wraps `App()` in `BirdyTheme`, just add the `init` line.)

- [ ] **Step 6: Update HomeScreen to render the species count**

Replace `composeApp/src/commonMain/kotlin/se/birdy/app/ui/HomeScreen.kt` with content that reads the repository:

```kotlin
package se.birdy.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import se.birdy.app.SpeciesRepositoryProvider
import se.birdy.content.Locale

@Composable
fun HomeScreen() {
    val repo = remember { SpeciesRepositoryProvider.get() }
    val state =
        remember { repo.all(Locale.SV) }.collectAsState(initial = emptyList())

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Birdy Bird Scanner",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "${state.value.size} fågelarter laddade",
            style = MaterialTheme.typography.titleMedium,
        )
        for (s in state.value.take(5)) {
            Text(s.name, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
```

- [ ] **Step 7: Build and install on the connected Galaxy S23 Ultra**

```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :androidApp:installDebug
```

Expected: BUILD SUCCESSFUL, APK installed.

- [ ] **Step 8: Launch the app**

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" \
    shell am start -n se.birdy.android/.MainActivity
```

Expected: HomeScreen renders "5 fågelarter laddade" + Talgoxe / Koltrast / Blåmes / Knölsvan / Tornfalk listed.

> **If the count is 0:** the app reads the wrong asset path. `adb shell run-as se.birdy.android ls files/` to confirm `species.db` was copied. If not, inspect APK contents (`unzip -l <path-to-debug.apk> | grep species.db`) and adjust `SpeciesRepositoryProvider.android.kt` accordingly. Halt and report to the user.

- [ ] **Step 9: APK size sanity check**

```bash
ls -lh androidApp/build/outputs/apk/debug/*.apk
```

Expected: APK is well under 130MB. With 5 species + Compose, expect ~20-30 MB. Note the size in the commit message so we can track growth across families.

- [ ] **Step 10: Take a screenshot for the milestone log**

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" exec-out screencap -p > docs/superpowers/screenshots/2026-05-02-walking-skeleton.png
```

- [ ] **Step 11: Commit**

```bash
git add composeApp/ androidApp/ docs/superpowers/screenshots/
git commit -m "feat(app): wire species.db into composeApp; HomeScreen shows 5 walking-skeleton species"
```

---

### Task 15: CI integration + final verification + plan close-out

**Files:**
- Modify: `.github/workflows/ci.yml` (add validate + buildSpeciesDb steps)
- Create: `.github/workflows/content-pipeline.yml` (Python pytest + ruff + mypy)
- Modify: `CLAUDE.md` (mark Plan 2a complete; pivot to Plan 2b runbook)

- [ ] **Step 1: Read existing CI workflow**

```bash
cat .github/workflows/ci.yml
```

Note the existing job structure so the additions integrate cleanly.

- [ ] **Step 2: Append validation + db build steps to `.github/workflows/ci.yml`**

Add steps (in the existing build job, after the existing `:shared:domain:jvmTest` step, before `assembleDebug`):

```yaml
- name: Validate species data
  run: ./gradlew :shared:content:validateSpeciesData

- name: Run shared:content tests
  run: ./gradlew :shared:content:jvmTest

- name: Build species.db
  run: ./gradlew :shared:content:buildSpeciesDb
```

Then ensure the existing `assembleDebug` step depends on these by ordering.

- [ ] **Step 3: Create `.github/workflows/content-pipeline.yml`**

```yaml
name: content-pipeline

on:
  push:
    paths:
      - "tools/content-pipeline/**"
  pull_request:
    paths:
      - "tools/content-pipeline/**"

jobs:
  python:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: tools/content-pipeline
    steps:
      - uses: actions/checkout@v4
      - name: Install uv
        uses: astral-sh/setup-uv@v3
        with:
          enable-cache: true
      - name: Sync dependencies
        run: uv sync
      - name: Lint
        run: uv run ruff check
      - name: Format check
        run: uv run ruff format --check
      - name: Type check
        run: uv run mypy
      - name: Tests
        run: uv run pytest
```

- [ ] **Step 4: Trigger CI by pushing a branch and watching it go green**

```bash
git push origin main
```

Watch GitHub Actions: both `ci` and `content-pipeline` should run. If `ci` fails on a step, fix locally, commit, push.

- [ ] **Step 5: Update `CLAUDE.md`**

Replace the "Status" line and the plan-of-plans table to reflect Plan 2a completion.

In `CLAUDE.md`, change:

```markdown
**Status (2026-05-02):** Plan 1 (Foundation) ✅ klar — alla 12 tasks committade, CI grönt, milstolpe taggad `v0.1.0-foundation`. Nästa: Plan 2 (Content pipeline). Specs/plans-driven utveckling — vi diskuterar i brainstorming, skriver spec, skriver implementationsplan, sen exekverar.
```

to:

```markdown
**Status (uppdateras vid plan-stäng):** Plan 2a (Content pipeline & walking skeleton) ✅ klar. Pipelinen producerar `species.db` från committat YAML; 5 walking-skeleton-arter (talgoxe, koltrast, blåmes, knölsvan, tornfalk) finns i appen, CI grönt. Nästa: Plan 2b (familjevis backfill av ~700 arter via fetcher + reviews → tag `v0.2.0-content`).
```

In the plan-of-plans table, split row 2:

```markdown
| 2a | Content pipeline + walking skeleton (5 spp) | ✅ Klar |
| 2b | Familjevis backfill av ~700 arter (runbook) | **Nästa** |
| 3 | Encyclopedia (browse + species profile) | |
```

- [ ] **Step 6: Sketch Plan 2b runbook**

Create `docs/superpowers/runbooks/2026-05-02-plan-2b-content-backfill.md`:

```markdown
# Plan 2b — Content backfill runbook

Plan 2a delivered the pipeline + walking skeleton. Plan 2b is the work of running the fetcher across all ~700 species, reviewing output family-by-family, and committing.

## Per-family loop (do this ~25-30 times)

1. Pick the next family (start with `paridae` extended, then alphabetical).
2. List Q-IDs in that family from `species_list.yaml`.
3. Run: `uv run birdy-fetcher refresh --species Q... --species Q... --max-cost 0.30`
4. For `abundance: allmän` species in this family: open generated `tools/content-pipeline/hero_review/{Q-ID}.html` per species, approve hero or override.
5. Spot-check 2-3 random YAML files: description reads OK, no hallucinations, image shows the right bird.
6. Update `shared/content/expected-species-count.txt` to current cumulative count.
7. Run `./gradlew :shared:content:validateSpeciesData :shared:content:buildSpeciesDb :composeApp:assembleDebug`.
8. Commit: `data(content): family <name> — N species (M approved, K auto)`.
9. Push as a small PR for review.

## Closeout

Once all ~700 species are committed and the validator/build green:

- `expected-species-count.txt` = ~700 (exact number)
- `git tag v0.2.0-content && git push --tags`
- Update CLAUDE.md status to "Plan 2 klar; nästa: Plan 3 Encyclopedia".

## Cost watch

Cumulative Claude budget for the full backfill: ~$5. Use `--max-cost` per run, sum tracking via `birdy-fetcher status` after each batch.
```

- [ ] **Step 7: Final smoke test**

```bash
./gradlew clean
./gradlew :shared:content:validateSpeciesData :shared:content:buildSpeciesDb \
          :shared:content:jvmTest :composeApp:assembleDebug
```

Expected: all green from a clean state.

- [ ] **Step 8: Commit Plan 2a closure**

```bash
git add .github/workflows/ CLAUDE.md docs/superpowers/runbooks/
git commit -m "ci(content): integrate validation + db build; mark Plan 2a complete"
git push
```

- [ ] **Step 9: Tag the milestone**

```bash
git tag -a v0.2.0a-pipeline -m "Plan 2a — content pipeline + walking skeleton (5 species)"
git push --tags
```

(Note: not `v0.2.0-content`. That tag is reserved for Plan 2b's full-backfill closure.)

---

## Self-review (run before kickoff)

**Spec coverage check** — every spec section maps to a task:

- Sek 2 (locked decisions) → all baked in as design choices.
- Sek 3 (architecture) → Tasks 1, 8 (orchestrator), 11-12 (build half).
- Sek 4 (YAML schema) → Tasks 8 (writer), 10 (kaml DTOs).
- Sek 5 (fetcher CLI + cache) → Tasks 1 (CLI), 4 (cache), 6 (Claude), 7 (images), 8 (orchestrator).
- Sek 6 (risk hardening) → 6.1 in Task 2-3, 6.3 in Task 5+11, 6.4 in Task 7, 6.5 in Task 14, 6.6 in Task 6+8 (cost cap + resume), 6.7 in plan structure (Plan 2b is the small-batch flow).
- Sek 7 (build tasks) → Tasks 11 (validate), 12 (build), 14 (wire).
- Sek 8 (testing) → tests are written first inside every task; SpeciesRepository in Task 13.
- Sek 9 (task skiss) → maps 1:1, with Task 3 explicit + Task 13/14 split.
- Sek 10 (deps + secrets) → Tasks 1 (.env.example), 1 (uv setup), 9 (api-key required).

**Open spec questions parked**:
- Cache as files vs SQLite — files (Task 4 cache.py).
- Few-shot tonprov for prompts — placeholder in Task 6, user fills before Plan 2b.
- `season_summary` text — deferred to Plan 3 design.
- Cron auto-refresh — Plan 6.

**Placeholder scan:** none. All steps contain concrete code/commands.

**Type consistency:** `SpeciesYaml` (parser) ↔ `SpeciesYamlData` (Python writer) — different language but same shape. `Species` (repository) ↔ `SpeciesYaml` (build) intentionally different — repository is the runtime API.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-02-v1-02a-content-pipeline.md`. Two execution options:

**1. Subagent-Driven (recommended)** — main thread (Opus 4.7) dispatches a fresh Sonnet 4.6 subagent per task, reviews between tasks, fast iteration with two-stage review.

**2. Inline Execution** — execute tasks here using `superpowers:executing-plans`, batch with checkpoints.

CLAUDE.md says subagent-driven is the default for this project. After the user confirms, the next step is `superpowers:subagent-driven-development`.



