# Artsidor fas 1b: källor, faktablad, granskning, text och jämförelser (implementationsplan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bygga om pipelinesteget `birdy-fetcher web` så att varje art får källor från tre Wikipedior, Artportalen, Svenska rödlistan och Commons, ett faktablad som Albin granskar, text som bara skrivs ur godkända fakta och kontrolleras mening för mening, samt jämförelsetexter för de mest sökta förväxlingsparen.

**Architecture:** Artens JSON-fil i `website/src/data/species/<QID>.json` är tillståndet mellan stegen: `sources` → `facts` → `waves` → `sheet` → (Albin granskar) → `import` → `write` → `compare` → `publish`. Varje steg äger vissa nycklar och lämnar resten orörda. Allt som går att räkna (månader, län, rödlista, status mot data) görs av kod. Modellen tar bara ut citerade fakta, skriver ur godkända fakta och kontrollerar meningar.

**Tech Stack:** Python 3.12 med uv, click, aiohttp, pydantic 2, anthropic 0.97 (låst), Pillow, imageio-ffmpeg (ny), pytest, ruff, mypy strict.

**Spec:** `docs/superpowers/specs/2026-09-25-artsidor-design.md`, reviderad 2026-10-01 (avsnitt 9, 10 och 14, bilaga C, D och E). **Sidorna** byggs parallellt enligt `docs/superpowers/plans/2026-09-25-artsidor-fas2-sidor.md` mot samma datakontrakt (bilaga C och D).

---

## Innan du börjar

- **Arbetskatalog:** `tools/content-pipeline` (alla kommandon nedan körs därifrån om inget annat sägs). Grenen är `main`, små commits per task.
- **Baslinje (kör innan Task 1 och notera resultatet):**
  - `uv run pytest -q` ska vara grönt.
  - `uv run ruff check . && uv run ruff format --check .` ska vara rent.
  - `uv run mypy` har bara de 6 gamla felen i `tests/test_name_mapping.py`. Inga nya fel får tillkomma.
- **Inga nätverksanrop i testerna.** Alla klienter tar emot en falsk HTTP-funktion eller en falsk modellklient. ffmpeg-testet i Task 8 kör den lokala binären, inget nät.
- **Kör aldrig ett betalt kommando** (`web facts`, `web write`, `web compare` utan `--help`) utanför körtaskarna R1 till R7. Dessa kräver `ANTHROPIC_API_KEY` och Albins kredit.
- **Lint i varje task:** `uv run ruff check --fix . && uv run ruff format . && uv run mypy`. Koden i planen är inte garanterat byte-exakt formaterad eller importsorterad; låt ruff rätta det. Bara mypy får inte få nya fel.
- **Husregler:** inga tankstreck (U+2014) och inga tankstreck med mellanslag runt (U+2013) i text som kan hamna på sajten. Felmeddelanden på svenska som i befintlig kod. Inga nya `# type: ignore` utan kod som motiverar dem.
- **Det som återanvänds från fas 1** (`src/birdy_fetcher/web/`): `checks.py` (`_style`, `quote_in_sources`, `load_banned`, `sentence_count`, `DASHES`), `licenses.py`, `images.py`, `slugs.py`, `groups.py`, `source.py`, `wiki_full.py`, `../cache.py`, `../cost.py`, `../claude_summarizer._split_prompt`. Det gamla enkla skrivpasset (`writer.py`, `model.py`, `output.py`, `run.py`, `prompts/web-v1.md`) ligger kvar som `web v1` tills Task 24 tar bort det.

## Filkarta

Nya filer under `src/birdy_fetcher/web/`:

| Fil | Ansvar |
|---|---|
| `http.py` | Strypt GET med omförsök (text och bytes), delad av alla källklienter |
| `counties.py` | Sveriges 21 län: GBIF:s GADM-id till ISO-kod och namn |
| `datamod.py` | Månads- och länsandelar, meningar ur datan, statussignal, `build_data` |
| `gbif.py` | Taxonmatchning, Artportalens antal per månad och län, Svenska rödlistan 2025 |
| `audio.py` | Kandidater från Wikidata och Commons, filtret, nedladdning, MP3-omkodning |
| `identify.py` | Vad appens foto- och ljudmodell täcker per art |
| `llm.py` | Claude-anrop med JSON-schema, kostnadsbokföring |
| `paths.py` | `WebPaths` (flyttas hit från `run.py`) |
| `record.py` | Läsa, skriva och slå ihop artposten, `facts_hash` |
| `sources_step.py` | Steg 1: källor för varje art, `check_slug_collisions` |
| `facts.py` | Faktabladets modell, citatkontroll, status, datafakta |
| `facts_step.py` | Steg 2: faktabladet med modellen, omförsök, cache |
| `waves.py` | Vågorna och publiceringen |
| `review_sheet.py` | Granskningsarket ut (CSV) och Albins beslut in |
| `text_model.py` | Textens modell med meningar och fakta-id, omvandling till sajtens form |
| `text_checks.py` | Kodkontrollerna för texten, borttagning av meningar |
| `checker.py` | Den andra modellen som kontrollerar meningar |
| `text_step.py` | Steg 3: skriva, kontrollera, skriva om, ta bort |
| `compare.py` | Förväxlingspar, sökvolymer, jämförelsetexter |

Ändrade filer: `wiki_full.py`, `report.py`, `run.py` (tills Task 24), `../cli.py`, `pyproject.toml`, `uv.lock`, rotens `LICENSE`. Nya promptar: `prompts/facts-v1.md`, `prompts/web-v2.md`, `prompts/check-v1.md`, `prompts/compare-v1.md`. Nya mappar: `review/` (granskningsark, vågor, sökvolymer), `website/src/data/species/LICENSE.md`, `website/src/data/comparisons/LICENSE.md`.

---

### Task 1: Strypt HTTP i en egen modul

`FullWikiClient` har en strypning (en förfrågan i taget, minsta mellanrum, omförsök på 429 och 5xx) som GBIF- och Commons-klienterna också behöver, och Commons behöver dessutom hämta bytes. Flytta den till `http.py` utan att ändra beteendet.

**Files:**
- Create: `src/birdy_fetcher/web/http.py`
- Modify: `src/birdy_fetcher/web/wiki_full.py:1-127` (allt från modulens början till och med `_wait_for_slot`)
- Test: `tests/test_web_http.py`

- [ ] **Step 1: Skriv de fallerande testerna**

`tests/test_web_http.py`:

```python
"""Tests for web/http.py: the shared throttled, retried GET."""

from __future__ import annotations

import aiohttp
import multidict
import pytest
import yarl

from birdy_fetcher.web.http import ThrottledHttp


def _response_error(status: int) -> aiohttp.ClientResponseError:
    request_info = aiohttp.RequestInfo(
        url=yarl.URL("https://x"),
        method="GET",
        headers=multidict.CIMultiDictProxy(multidict.CIMultiDict()),
        real_url=yarl.URL("https://x"),
    )
    return aiohttp.ClientResponseError(
        request_info=request_info, history=(), status=status, headers=multidict.CIMultiDict()
    )


async def test_fetch_bytes_retries_503_then_returns_the_body() -> None:
    calls = 0

    async def get_bytes(url: str) -> bytes:
        nonlocal calls
        calls += 1
        if calls == 1:
            raise _response_error(503)
        return b"ID3"

    sleeps: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleeps.append(seconds)

    http = ThrottledHttp(get_bytes=get_bytes, min_interval=0.0, sleep=fake_sleep, clock=lambda: 0.0)
    assert await http.fetch_bytes("https://x/a.mp3") == b"ID3"
    assert calls == 2
    assert 2.0 in sleeps


async def test_text_and_bytes_share_one_throttle() -> None:
    sleeps: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleeps.append(seconds)

    async def get_text(url: str) -> str:
        return "t"

    async def get_bytes(url: str) -> bytes:
        return b"b"

    http = ThrottledHttp(
        get_text=get_text, get_bytes=get_bytes, min_interval=1.0, sleep=fake_sleep, clock=lambda: 0.0
    )
    await http.fetch_text("https://x/a")
    await http.fetch_bytes("https://x/b")
    assert sleeps == [1.0]


async def test_404_is_raised_without_retry() -> None:
    calls = 0

    async def get_text(url: str) -> str:
        nonlocal calls
        calls += 1
        raise FileNotFoundError(url)

    async def fake_sleep(seconds: float) -> None:
        return None

    http = ThrottledHttp(get_text=get_text, min_interval=0.0, sleep=fake_sleep, clock=lambda: 0.0)
    with pytest.raises(FileNotFoundError):
        await http.fetch_text("https://x/missing")
    assert calls == 1
```

- [ ] **Step 2: Kör testerna och se dem falla**

Run: `uv run pytest tests/test_web_http.py -v`
Expected: FAIL med `ModuleNotFoundError: No module named 'birdy_fetcher.web.http'`

- [ ] **Step 3: Skriv `http.py`**

```python
"""Throttled, retried GET shared by the source clients (spec 2026-09-25 §9.1).

Wikimedia rate-limits hard under concurrent load and GBIF asks for polite clients, so every
request from one client goes through a single in-flight slot with a minimum gap between
request starts, and 429/5xx are retried (Retry-After when given, else exponential backoff).
The backoff sleep happens while holding the lock, so a 429 pauses the whole client instead
of letting other workers keep hitting the server while it asked us to back off."""

from __future__ import annotations

import asyncio
import time
from collections.abc import Awaitable, Callable, Mapping

import aiohttp

from ..wikipedia import USER_AGENT, _default_http_get

GetText = Callable[[str], Awaitable[str]]
GetBytes = Callable[[str], Awaitable[bytes]]

MAX_ATTEMPTS = 5
BACKOFF_SECONDS = (2.0, 4.0, 8.0, 16.0)
MAX_RETRY_AFTER_SECONDS = 60.0


def is_retryable_status(status: int | None) -> bool:
    if status is None:
        return False
    return status == 429 or 500 <= status < 600


def retry_after_seconds(exc: aiohttp.ClientResponseError) -> float | None:
    headers: Mapping[str, str] = exc.headers or {}
    for key, value in headers.items():
        if key.lower() != "retry-after":
            continue
        try:
            return float(value)
        except ValueError:
            return None
    return None


def retry_delay(exc: aiohttp.ClientResponseError, failures: int) -> float:
    retry_after = retry_after_seconds(exc)
    if retry_after is not None:
        return min(retry_after, MAX_RETRY_AFTER_SECONDS)
    return BACKOFF_SECONDS[min(failures - 1, len(BACKOFF_SECONDS) - 1)]


async def default_get_bytes(url: str) -> bytes:
    async with (
        aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session,
        session.get(url, timeout=aiohttp.ClientTimeout(total=120)) as response,
    ):
        if response.status == 404:
            raise FileNotFoundError(url)
        response.raise_for_status()
        return await response.read()


class ThrottledHttp:
    def __init__(
        self,
        *,
        get_text: GetText | None = None,
        get_bytes: GetBytes | None = None,
        min_interval: float = 1.0,
        sleep: Callable[[float], Awaitable[None]] = asyncio.sleep,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        self._get_text = get_text or _default_http_get
        self._get_bytes = get_bytes or default_get_bytes
        self._min_interval = min_interval
        self._sleep = sleep
        self._clock = clock
        self._lock = asyncio.Lock()
        self._last_start: float | None = None

    async def fetch_text(self, url: str) -> str:
        return await self._run(lambda: self._get_text(url))

    async def fetch_bytes(self, url: str) -> bytes:
        return await self._run(lambda: self._get_bytes(url))

    async def _run[T](self, call: Callable[[], Awaitable[T]]) -> T:
        failures = 0
        while True:
            async with self._lock:
                await self._wait_for_slot()
                try:
                    return await call()
                except aiohttp.ClientResponseError as exc:
                    failures += 1
                    if not is_retryable_status(exc.status) or failures >= MAX_ATTEMPTS:
                        raise
                    delay = retry_delay(exc, failures)
                await self._sleep(delay)

    async def _wait_for_slot(self) -> None:
        if self._last_start is not None:
            remaining = self._min_interval - (self._clock() - self._last_start)
            if remaining > 0:
                await self._sleep(remaining)
        self._last_start = self._clock()
```

- [ ] **Step 4: Låt `wiki_full.py` använda den**

Ersätt allt i `src/birdy_fetcher/web/wiki_full.py` från första raden till och med metoden `_wait_for_slot` (alltså docstring, importer, `HttpGet`, `LANGS`, konstanterna, `_is_retryable_status`, `_retry_after_seconds`, `_retry_delay`, `WikiArticle`, `__init__`, `_cached`, `_fetch` och `_wait_for_slot`) med blocket nedan. Metoderna `sitelinks`, `_item_sitelinks`, `_original_combination`, `article` och `articles` står kvar oförändrade.

```python
"""Full Wikipedia articles as plain text, found through Wikidata sitelinks (spec §9.1)."""

from __future__ import annotations

import asyncio
import json
import time
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from urllib.parse import quote

from ..cache import Cache
from .http import GetText, ThrottledHttp

LANGS = ("sv", "en")


@dataclass(frozen=True)
class WikiArticle:
    lang: str
    title: str
    revision: str
    text: str


class FullWikiClient:
    def __init__(
        self,
        *,
        cache: Cache,
        http_get: GetText | None = None,
        min_interval: float = 1.0,
        sleep: Callable[[float], Awaitable[None]] = asyncio.sleep,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        self.cache = cache
        self._http = ThrottledHttp(
            get_text=http_get, min_interval=min_interval, sleep=sleep, clock=clock
        )

    async def _cached(self, qid: str, name: str, url: str, refresh: bool) -> str:
        raw = None if refresh else self.cache.get(qid, name)
        if raw is None:
            raw = await self._http.fetch_text(url)
            self.cache.put(qid, name, raw)
        return raw
```

- [ ] **Step 5: Kör testerna**

Run: `uv run pytest tests/test_web_http.py tests/test_web_wiki_full.py -v`
Expected: PASS (alla, även de befintliga strypnings- och omförsökstesterna för Wikipedia).

- [ ] **Step 6: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`
Expected: rent, mypy bara med de 6 gamla felen.

```bash
git add src/birdy_fetcher/web/http.py src/birdy_fetcher/web/wiki_full.py tests/test_web_http.py
git commit -m "refactor(pipeline): strypt HTTP i egen modul för alla källklienter"
```

---

### Task 2: Tyska Wikipedia

**Files:**
- Modify: `src/birdy_fetcher/web/wiki_full.py` (`LANGS`, `sitelinks`, `_item_sitelinks`)
- Test: `tests/test_web_wiki_full.py` (nya tester längst ned)

- [ ] **Step 1: Skriv de fallerande testerna**

Lägg till längst ned i `tests/test_web_wiki_full.py`:

```python
class RoutedHttp:
    """Answers with the first route whose key is in the URL."""

    def __init__(self, routes: dict[str, str]) -> None:
        self.routes = routes
        self.urls: list[str] = []

    async def __call__(self, url: str) -> str:
        self.urls.append(url)
        for key, body in self.routes.items():
            if key in url:
                return body
        raise FileNotFoundError(url)


def _entities(qid: str, links: dict[str, str]) -> str:
    sitelinks = {site: {"site": site, "title": title} for site, title in links.items()}
    return json.dumps({"entities": {qid: {"sitelinks": sitelinks}}})


async def test_articles_include_german(tmp_path: Path) -> None:
    http = RoutedHttp(
        {
            "ids=Q25485": _entities(
                "Q25485", {"svwiki": "Talgoxe", "enwiki": "Great tit", "dewiki": "Kohlmeise"}
            ),
            "sv.wikipedia": _article("Talgoxe", "Talgoxen är cirka 14 centimeter lång.", 111),
            "en.wikipedia": _article("Great tit", "The great tit is about 14 cm long.", 222),
            "de.wikipedia": _article("Kohlmeise", "Die Kohlmeise ist etwa 14 cm lang.", 333),
        }
    )
    client = FullWikiClient(cache=Cache(tmp_path), http_get=http, min_interval=0.0)
    articles = await client.articles("Q25485")
    assert set(articles) == {"sv", "en", "de"}
    assert articles["de"].title == "Kohlmeise"
    assert articles["de"].revision == "333"
    assert any("dewiki" in url for url in http.urls)


async def test_german_only_item_still_uses_its_original_combination(tmp_path: Path) -> None:
    claims = json.dumps(
        {"claims": {"P1403": [{"mainsnak": {"datavalue": {"value": {"id": "Q901"}}}}]}}
    )
    http = RoutedHttp(
        {
            "ids=Q900": _entities("Q900", {"dewiki": "Mornellregenpfeifer"}),
            "property=P1403": claims,
            "ids=Q901": _entities("Q901", {"svwiki": "Fjällpipare"}),
        }
    )
    client = FullWikiClient(cache=Cache(tmp_path), http_get=http, min_interval=0.0)
    assert await client.sitelinks("Q900") == {"sv": "Fjällpipare"}
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_wiki_full.py -v -k "german"`
Expected: FAIL (`de` saknas i resultatet, respektive `{'de': 'Mornellregenpfeifer'}` i stället för fjällpiparen).

- [ ] **Step 3: Implementera**

I `wiki_full.py`, ändra `LANGS` till:

```python
LANGS = ("sv", "en", "de")
```

Ersätt metoden `sitelinks` med:

```python
    async def sitelinks(self, qid: str, *, refresh: bool = False) -> dict[str, str]:
        """Titles per language (sv, en, de). An item with neither a sv nor an en sitelink is
        often a newer name combination whose articles still hang on the original combination
        (P1403), e.g. fjällpipare Q25677554 -> Q202504; those are used instead. Cached under
        `qid`. The cache names end in -v2 because the v1 files were fetched without dewiki."""
        titles = await self._item_sitelinks(qid, qid, "web-sitelinks-v2.json", refresh)
        if "sv" in titles or "en" in titles:
            return titles
        original = await self._original_combination(qid, refresh)
        if original is None:
            return titles
        original_titles = await self._item_sitelinks(
            qid, original, "web-sitelinks-original-v2.json", refresh
        )
        return original_titles or titles
```

I `_item_sitelinks`, ändra adressen och filtret så att `dewiki` kommer med:

```python
        url = (
            "https://www.wikidata.org/w/api.php?action=wbgetentities&format=json"
            f"&props=sitelinks&sitefilter=svwiki%7Cenwiki%7Cdewiki&ids={item}"
        )
        raw = await self._cached(cache_qid, name, url, refresh)
        links = json.loads(raw).get("entities", {}).get(item, {}).get("sitelinks", {})
        return {
            site.removesuffix("wiki"): link["title"]
            for site, link in links.items()
            if site in ("svwiki", "enwiki", "dewiki")
        }
```

- [ ] **Step 4: Kör alla Wikipediatester**

Run: `uv run pytest tests/test_web_wiki_full.py -v`
Expected: PASS (alla gamla och båda nya).

- [ ] **Step 5: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/wiki_full.py tests/test_web_wiki_full.py
git commit -m "feat(pipeline): tyska Wikipedia som tredje källa"
```

---

### Task 3: Länen och andelarna

**Files:**
- Create: `src/birdy_fetcher/web/counties.py`, `src/birdy_fetcher/web/datamod.py`
- Test: `tests/test_web_datamod.py`

Länstabellen är kontrollerad 2026-10-01 mot GBIF (`occurrence/search?gadmGid=SWE.<n>_1&limit=1`, fältet `gadm.level1.name`): 1 Blekinge, 2 Dalarna, 3 Gävleborg, 4 Gotland, 5 Halland, 6 Jämtland, 7 Jönköping, 8 Kalmar, 9 Kronoberg, 10 Norrbotten, 11 Örebro, 12 Östergötland, 13 Skåne, 14 Södermanland, 15 Stockholm, 16 Uppsala, 17 Värmland, 18 Västerbotten, 19 Västernorrland, 20 Västmanland, 21 Västra Götaland.

- [ ] **Step 1: Skriv de fallerande testerna**

`tests/test_web_datamod.py`:

```python
"""Tests for web/datamod.py and web/counties.py: shares, sentences and the status signal."""

from __future__ import annotations

import pytest

from birdy_fetcher.web.counties import COUNTIES
from birdy_fetcher.web.datamod import county_profile, month_profile, scaled


def test_counties_table_has_21_unique_iso_codes() -> None:
    assert len(COUNTIES) == 21
    assert len({c.iso for c in COUNTIES.values()}) == 21
    assert COUNTIES["SWE.10_1"].name == "Norrbotten"
    assert COUNTIES["SWE.21_1"].iso == "SE-O"


def test_month_profile_adjusts_for_how_many_people_report() -> None:
    species = [10] * 12
    species[4] = 40
    all_birds = [100] * 12
    all_birds[4] = 400
    assert month_profile(species, all_birds) == [100] * 12


def test_month_profile_scales_the_top_month_to_100() -> None:
    species = [0, 0, 0, 5, 10, 10, 10, 8, 2, 0, 0, 0]
    assert month_profile(species, [100] * 12) == [0, 0, 0, 50, 100, 100, 100, 80, 20, 0, 0, 0]


def test_a_tiny_share_never_rounds_down_to_zero() -> None:
    assert scaled([1000.0, 1.0, 0.0]) == [100, 1, 0]


def test_month_profile_needs_twelve_values() -> None:
    with pytest.raises(ValueError):
        month_profile([1] * 11, [1] * 12)


def test_county_profile_covers_all_21_counties() -> None:
    profile = county_profile({"SE-BD": 30, "SE-M": 10}, {"SE-BD": 100, "SE-M": 100})
    assert len(profile) == 21
    assert profile["SE-BD"] == 100
    assert profile["SE-M"] == 33
    assert profile["SE-AB"] == 0
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_datamod.py -v`
Expected: FAIL med `ModuleNotFoundError`.

- [ ] **Step 3: Skriv `counties.py`**

```python
"""Sweden's 21 counties: GBIF's GADM level 1 ids to ISO 3166-2:SE codes (spec §9.1).

Verified 2026-10-01 against GBIF occurrence records (gadm.level1.name). The county names are
the same in Swedish and English on the pages."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class County:
    iso: str
    name: str


COUNTIES: dict[str, County] = {
    "SWE.1_1": County("SE-K", "Blekinge"),
    "SWE.2_1": County("SE-W", "Dalarna"),
    "SWE.3_1": County("SE-X", "Gävleborg"),
    "SWE.4_1": County("SE-I", "Gotland"),
    "SWE.5_1": County("SE-N", "Halland"),
    "SWE.6_1": County("SE-Z", "Jämtland"),
    "SWE.7_1": County("SE-F", "Jönköping"),
    "SWE.8_1": County("SE-H", "Kalmar"),
    "SWE.9_1": County("SE-G", "Kronoberg"),
    "SWE.10_1": County("SE-BD", "Norrbotten"),
    "SWE.11_1": County("SE-T", "Örebro"),
    "SWE.12_1": County("SE-E", "Östergötland"),
    "SWE.13_1": County("SE-M", "Skåne"),
    "SWE.14_1": County("SE-D", "Södermanland"),
    "SWE.15_1": County("SE-AB", "Stockholm"),
    "SWE.16_1": County("SE-C", "Uppsala"),
    "SWE.17_1": County("SE-S", "Värmland"),
    "SWE.18_1": County("SE-AC", "Västerbotten"),
    "SWE.19_1": County("SE-Y", "Västernorrland"),
    "SWE.20_1": County("SE-U", "Västmanland"),
    "SWE.21_1": County("SE-O", "Västra Götaland"),
}

COUNTY_NAMES: dict[str, str] = {c.iso: c.name for c in COUNTIES.values()}
```

- [ ] **Step 4: Skriv början på `datamod.py`**

```python
"""Month and county shares from Artportalen report counts (spec 2026-09-25 §9.2).

Pure functions: the GBIF client fetches the counts, this module turns them into the numbers
and sentences on the page. A model is never involved."""

from __future__ import annotations

from dataclasses import dataclass

from .counties import COUNTIES

MIN_REPORTS = 200


@dataclass(frozen=True)
class Counts:
    """Report counts for one taxon in Sweden 2016 to 2025, CC0 records only."""

    by_month: list[int]
    by_county: dict[str, int]
    total: int


def scaled(shares: list[float]) -> list[int]:
    """Scale so the largest share becomes 100. A share above zero never rounds down to 0, so
    a county shown as 'no reports' really has none."""
    top = max(shares, default=0.0)
    if top <= 0:
        return [0] * len(shares)
    return [max(1, round(s / top * 100)) if s > 0 else 0 for s in shares]


def _share(species: int, total: int) -> float:
    return species / total if total > 0 else 0.0


def month_profile(species_by_month: list[int], all_by_month: list[int]) -> list[int]:
    if len(species_by_month) != 12 or len(all_by_month) != 12:
        raise ValueError("Månadsdatan ska ha tolv värden")
    pairs = zip(species_by_month, all_by_month, strict=True)
    return scaled([_share(s, a) for s, a in pairs])


def county_profile(
    species_by_county: dict[str, int], all_by_county: dict[str, int]
) -> dict[str, int]:
    isos = [c.iso for c in COUNTIES.values()]
    shares = [_share(species_by_county.get(i, 0), all_by_county.get(i, 0)) for i in isos]
    return dict(zip(isos, scaled(shares), strict=True))
```

- [ ] **Step 5: Kör testerna**

Run: `uv run pytest tests/test_web_datamod.py -v`
Expected: PASS

- [ ] **Step 6: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/counties.py src/birdy_fetcher/web/datamod.py tests/test_web_datamod.py
git commit -m "feat(pipeline): länstabell och andelar per månad och län"
```

---

### Task 4: Meningarna ur datan

**Files:**
- Modify: `src/birdy_fetcher/web/datamod.py` (lägg till längst ned)
- Test: `tests/test_web_datamod.py` (lägg till)

- [ ] **Step 1: Skriv de fallerande testerna**

Lägg till i `tests/test_web_datamod.py` (och lägg till `county_sentence`, `data_sentences`, `month_runs`, `month_sentences`, `months_text` i importen från `birdy_fetcher.web.datamod`):

```python
MIGRANT = [0, 0, 5, 60, 100, 90, 85, 70, 40, 5, 0, 0]


def test_runs_wrap_around_the_new_year() -> None:
    assert month_runs({10, 11, 0, 1}) == [[10, 11, 0, 1]]
    assert month_runs({4, 5, 8}) == [[4, 5], [8]]


def test_three_or_more_months_in_a_row_become_a_span() -> None:
    assert months_text({11, 0, 1}, "sv") == "december till februari"
    assert months_text({11, 0, 1}, "en") == "December to February"


def test_two_months_are_listed() -> None:
    assert months_text({4, 5}, "sv") == "maj och juni"
    assert months_text({2, 4, 5}, "en") == "March, May and June"


def test_migrant_sentences() -> None:
    assert month_sentences(MIGRANT, "sv") == [
        "Rapporteras mest i maj till juli.",
        "Nästan aldrig i oktober till mars.",
    ]
    assert month_sentences(MIGRANT, "en") == [
        "Reported most in May to July.",
        "Almost never in October to March.",
    ]


def test_resident_is_reported_all_year() -> None:
    assert month_sentences([70, 65, 60, 55, 70, 79, 64, 68, 74, 100, 66, 69], "sv") == [
        "Rapporteras året runt."
    ]


def test_county_sentence_names_the_top_three() -> None:
    profile = {iso: 0 for iso in ("SE-BD", "SE-AC", "SE-Z", "SE-M")}
    profile.update({"SE-BD": 100, "SE-AC": 80, "SE-Z": 60, "SE-M": 5})
    assert county_sentence(profile, "sv") == (
        "Vanligast i rapporterna från Norrbotten, Västerbotten och Jämtland."
    )
    assert county_sentence(profile, "en") == (
        "Most common in reports from Norrbotten, Västerbotten and Jämtland."
    )


def test_county_sentence_with_one_county_and_with_none() -> None:
    assert county_sentence({"SE-I": 100, "SE-M": 0}, "sv") == (
        "Vanligast i rapporterna från Gotland."
    )
    assert county_sentence({"SE-I": 0}, "sv") is None


def test_data_sentences_put_months_first() -> None:
    assert data_sentences(MIGRANT, {"SE-I": 100}, "sv") == [
        "Rapporteras mest i maj till juli.",
        "Nästan aldrig i oktober till mars.",
        "Vanligast i rapporterna från Gotland.",
    ]
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_datamod.py -v`
Expected: FAIL med `ImportError` för de nya namnen.

- [ ] **Step 3: Implementera**

Lägg till längst ned i `datamod.py` (och `from .counties import COUNTIES, COUNTY_NAMES` överst i stället för bara `COUNTIES`):

```python
MONTHS = {
    "sv": (
        "januari", "februari", "mars", "april", "maj", "juni",
        "juli", "augusti", "september", "oktober", "november", "december",
    ),
    "en": (
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    ),
}  # fmt: skip
_AND = {"sv": "och", "en": "and"}
_TO = {"sv": "till", "en": "to"}
ALL_YEAR = {"sv": "Rapporteras året runt.", "en": "Reported all year round."}
MOST = {"sv": "Rapporteras mest i {months}.", "en": "Reported most in {months}."}
NEVER = {"sv": "Nästan aldrig i {months}.", "en": "Almost never in {months}."}
COUNTIES_SENTENCE = {
    "sv": "Vanligast i rapporterna från {counties}.",
    "en": "Most common in reports from {counties}.",
}
PEAK = 80
LOW = 10
ALL_YEAR_MIN = 30


def join_list(items: list[str], lang: str) -> str:
    if len(items) == 1:
        return items[0]
    return ", ".join(items[:-1]) + f" {_AND[lang]} " + items[-1]


def month_runs(selected: set[int]) -> list[list[int]]:
    """Runs of consecutive months (0 to 11), where December and January are neighbours."""
    if not selected:
        return []
    if len(selected) == 12:
        return [list(range(12))]
    start = next(i for i in range(12) if i in selected and (i - 1) % 12 not in selected)
    runs: list[list[int]] = []
    current: list[int] = []
    for k in range(12):
        month = (start + k) % 12
        if month in selected:
            current.append(month)
        elif current:
            runs.append(current)
            current = []
    if current:
        runs.append(current)
    return runs


def months_text(selected: set[int], lang: str) -> str:
    parts: list[str] = []
    for run in month_runs(selected):
        names = [MONTHS[lang][i] for i in run]
        parts.extend([f"{names[0]} {_TO[lang]} {names[-1]}"] if len(run) >= 3 else names)
    return join_list(parts, lang)


def month_sentences(profile: list[int], lang: str) -> list[str]:
    if min(profile) >= ALL_YEAR_MIN:
        return [ALL_YEAR[lang]]
    peak = {i for i, value in enumerate(profile) if value >= PEAK}
    sentences = [MOST[lang].format(months=months_text(peak, lang))]
    low = {i for i, value in enumerate(profile) if value <= LOW}
    if low:
        sentences.append(NEVER[lang].format(months=months_text(low, lang)))
    return sentences


def county_sentence(profile: dict[str, int], lang: str) -> str | None:
    ranked = sorted(
        (iso for iso, value in profile.items() if value > 0),
        key=lambda iso: (-profile[iso], COUNTY_NAMES[iso]),
    )[:3]
    if not ranked:
        return None
    names = [COUNTY_NAMES[iso] for iso in ranked]
    return COUNTIES_SENTENCE[lang].format(counties=join_list(names, lang))


def data_sentences(months: list[int], counties: dict[str, int], lang: str) -> list[str]:
    sentences = month_sentences(months, lang)
    county = county_sentence(counties, lang)
    if county is not None:
        sentences.append(county)
    return sentences
```

- [ ] **Step 4: Kör testerna**

Run: `uv run pytest tests/test_web_datamod.py -v`
Expected: PASS

- [ ] **Step 5: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/datamod.py tests/test_web_datamod.py
git commit -m "feat(pipeline): meningar om månader och län skrivna av kod"
```

---

### Task 5: Statussignalen och `build_data`

**Files:**
- Modify: `src/birdy_fetcher/web/datamod.py` (lägg till längst ned)
- Test: `tests/test_web_datamod.py` (lägg till)

Trösklarna här är startvärden. Körtask R2 kalibrerar dem mot riktig data för fyra kända arter och ändrar konstanterna om det behövs.

- [ ] **Step 1: Skriv de fallerande testerna**

Lägg till i `tests/test_web_datamod.py` (och `Counts`, `MIN_REPORTS`, `build_data`, `status_contradiction` i importen):

```python
RESIDENT = [70, 65, 60, 55, 70, 79, 64, 68, 74, 100, 66, 69]
WINTER_VISITOR = [100, 90, 70, 20, 2, 0, 0, 1, 10, 40, 80, 95]


def test_no_contradiction_when_data_agrees() -> None:
    assert status_contradiction("resident", RESIDENT, 5000) is None
    assert status_contradiction("breeding_migrant", MIGRANT, 5000) is None
    assert status_contradiction("winter_visitor", WINTER_VISITOR, 5000) is None
    assert status_contradiction("passage", MIGRANT, 5000) is None


def test_resident_with_an_empty_month_is_flagged() -> None:
    reason = status_contradiction("resident", MIGRANT, 5000)
    assert reason is not None
    assert "januari" in reason


def test_migrant_reported_in_winter_is_flagged() -> None:
    assert status_contradiction("breeding_migrant", RESIDENT, 5000) is not None


def test_winter_visitor_reported_in_summer_is_flagged() -> None:
    assert status_contradiction("winter_visitor", RESIDENT, 5000) is not None


def test_absent_with_many_reports_is_flagged_even_without_a_profile() -> None:
    assert status_contradiction("absent", None, MIN_REPORTS) is not None
    assert status_contradiction("absent", None, MIN_REPORTS - 1) is None


def test_missing_profile_never_flags_other_statuses() -> None:
    assert status_contradiction("resident", None, 50) is None


def test_build_data_with_enough_reports() -> None:
    species = Counts([10] * 12, {"SE-I": 50}, 1200)
    all_birds = Counts([100] * 12, {"SE-I": 100, "SE-M": 100}, 99_000)
    data = build_data(taxon_key=7, species=species, all_birds=all_birds, fetched_at="2026-10-01")
    assert data["months"] == [100] * 12
    assert data["counties"]["SE-I"] == 100
    assert data["sentences"]["sv"][0] == "Rapporteras året runt."
    assert data["totalReports"] == 1200
    assert data["gbifTaxonKey"] == 7
    assert data["raw"]["speciesByCounty"] == {"SE-I": 50}
    assert data["statusSignal"] == {"contradicts": None}


def test_build_data_with_too_few_reports_has_no_modules() -> None:
    species = Counts([1] * 12, {}, MIN_REPORTS - 1)
    data = build_data(
        taxon_key=7, species=species, all_birds=Counts([100] * 12, {}, 1), fetched_at="x"
    )
    assert "months" not in data
    assert "counties" not in data
    assert data["sentences"] == {"sv": [], "en": []}
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_datamod.py -v`
Expected: FAIL med `ImportError`.

- [ ] **Step 3: Implementera**

Lägg till längst ned i `datamod.py` (och `from typing import Any` bland importerna):

```python
RESIDENT_MIN_MONTH = 5
MIGRANT_WINTER_MAX = 25
WINTER_VISITOR_SUMMER_MAX = 25
_WINTER = (11, 0, 1)
_SUMMER = (5, 6)


def _mean(profile: list[int], months: tuple[int, ...]) -> float:
    return sum(profile[i] for i in months) / len(months)


def status_contradiction(status: str, months: list[int] | None, total_reports: int) -> str | None:
    """A plain Swedish reason when the report data clearly contradicts the stated status,
    otherwise None. Only clear contradictions count (spec §9.2); passage and rare_visitor are
    never flagged."""
    if status == "absent":
        if total_reports >= MIN_REPORTS:
            return (
                "Statusen säger att arten inte förekommer i Sverige, men den har "
                f"{total_reports} rapporter i Artportalen 2016 till 2025."
            )
        return None
    if months is None:
        return None
    if status == "resident" and min(months) < RESIDENT_MIN_MONTH:
        lowest = MONTHS["sv"][months.index(min(months))]
        return f"Statusen säger stannfågel, men arten rapporteras nästan aldrig i {lowest}."
    if status == "breeding_migrant" and _mean(months, _WINTER) > MIGRANT_WINTER_MAX:
        return "Statusen säger flyttfågel, men arten rapporteras ofta december till februari."
    if status == "winter_visitor" and _mean(months, _SUMMER) > WINTER_VISITOR_SUMMER_MAX:
        return "Statusen säger vintergäst, men arten rapporteras ofta i juni och juli."
    return None


def build_data(
    *, taxon_key: int, species: Counts, all_birds: Counts, fetched_at: str
) -> dict[str, Any]:
    """The record's `data` object (spec appendix C)."""
    data: dict[str, Any] = {
        "fetchedAt": fetched_at,
        "gbifTaxonKey": taxon_key,
        "totalReports": species.total,
    }
    if species.total >= MIN_REPORTS:
        months = month_profile(species.by_month, all_birds.by_month)
        counties = county_profile(species.by_county, all_birds.by_county)
        data["months"] = months
        data["counties"] = counties
        data["sentences"] = {lang: data_sentences(months, counties, lang) for lang in ("sv", "en")}
    else:
        data["sentences"] = {"sv": [], "en": []}
    data["raw"] = {
        "speciesByMonth": species.by_month,
        "allBirdsByMonth": all_birds.by_month,
        "speciesByCounty": species.by_county,
        "allBirdsByCounty": all_birds.by_county,
    }
    data["statusSignal"] = {"contradicts": None}
    return data
```

- [ ] **Step 4: Kör testerna**

Run: `uv run pytest tests/test_web_datamod.py -v`
Expected: PASS

- [ ] **Step 5: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/datamod.py tests/test_web_datamod.py
git commit -m "feat(pipeline): statussignal ur rapportdatan och data-objektet"
```

---

### Task 6: GBIF-klienten

**Files:**
- Create: `src/birdy_fetcher/web/gbif.py`
- Test: `tests/test_web_gbif.py`

Svaren nedan är förkortade kopior av riktiga svar (2026-10-01). Sökningen i rödlistan returnerar `threatStatuses` direkt, så inget extra anrop behövs.

- [ ] **Step 1: Skriv de fallerande testerna**

`tests/test_web_gbif.py`:

```python
"""Tests for web/gbif.py: taxon match, counts and the Swedish Red List 2025."""

from __future__ import annotations

import json
from pathlib import Path

from birdy_fetcher.cache import Cache
from birdy_fetcher.web.gbif import (
    AVES_TAXON_KEY,
    GbifClient,
    parse_counts,
    red_list_code,
)
from birdy_fetcher.web.http import ThrottledHttp

COUNTS = {
    "count": 962339,
    "facets": [
        {"field": "MONTH", "counts": [{"name": "10", "count": 123789}, {"name": "1", "count": 5}]},
        {
            "field": "GADM_LEVEL_1_GID",
            "counts": [
                {"name": "SWE.21_1", "count": 146594},
                {"name": "NOR.1_1", "count": 12},
            ],
        },
    ],
}


class Routed:
    def __init__(self, routes: dict[str, object]) -> None:
        self.routes = routes
        self.urls: list[str] = []

    async def __call__(self, url: str) -> str:
        self.urls.append(url)
        for key, body in self.routes.items():
            if key in url:
                return json.dumps(body)
        raise FileNotFoundError(url)


def _client(tmp_path: Path, routes: dict[str, object]) -> tuple[GbifClient, Routed]:
    http = Routed(routes)
    return GbifClient(cache=Cache(tmp_path), http=ThrottledHttp(get_text=http, min_interval=0)), http


def test_parse_counts_keeps_swedish_counties_only() -> None:
    counts = parse_counts(COUNTS)
    assert counts.total == 962339
    assert counts.by_month[9] == 123789
    assert counts.by_month[0] == 5
    assert counts.by_month[5] == 0
    assert counts.by_county == {"SE-O": 146594}


async def test_taxon_key_needs_an_exact_species_match(tmp_path: Path) -> None:
    client, _ = _client(
        tmp_path,
        {"name=Parus%20major": {"usageKey": 9705453, "matchType": "EXACT", "rank": "SPECIES"}},
    )
    assert await client.taxon_key("Q25485", "Parus major") == 9705453
    fuzzy, _ = _client(
        tmp_path / "b", {"species/match": {"usageKey": 1, "matchType": "FUZZY", "rank": "SPECIES"}}
    )
    assert await fuzzy.taxon_key("Q1", "Parus majr") is None


async def test_synonym_match_uses_the_accepted_key(tmp_path: Path) -> None:
    client, _ = _client(
        tmp_path,
        {
            "species/match": {
                "usageKey": 5,
                "acceptedUsageKey": 6,
                "synonym": True,
                "matchType": "EXACT",
                "rank": "SPECIES",
            }
        },
    )
    assert await client.taxon_key("Q1", "Delichon urbica") == 6


async def test_counts_filter_on_cc0_and_years_and_are_cached(tmp_path: Path) -> None:
    client, http = _client(tmp_path, {"occurrence/search": COUNTS})
    first = await client.counts("Q25485", 9705453)
    second = await client.counts("Q25485", 9705453)
    assert first == second
    assert len(http.urls) == 1
    url = http.urls[0]
    for part in ("country=SE", "year=2016,2025", "license=CC0_1_0", "taxonKey=9705453"):
        assert part in url


async def test_all_birds_uses_the_aves_key(tmp_path: Path) -> None:
    client, http = _client(tmp_path, {"occurrence/search": COUNTS})
    await client.all_birds()
    assert f"taxonKey={AVES_TAXON_KEY}" in http.urls[0]
    assert (tmp_path / "_aves").is_dir()


def test_red_list_code() -> None:
    accepted = {
        "nubKey": 2489214,
        "canonicalName": "Delichon urbicum",
        "rank": "SPECIES",
        "taxonomicStatus": "ACCEPTED",
        "threatStatuses": ["VULNERABLE"],
    }
    synonym = {**accepted, "canonicalName": "Delichon urbica", "taxonomicStatus": "SYNONYM"}
    assert red_list_code([synonym, accepted], "Delichon urbicum", 2489214) == "VU"
    assert red_list_code([], "Parus major", 9705453) == "not_listed"
    least = {**accepted, "threatStatuses": ["LEAST_CONCERN"]}
    assert red_list_code([least], "Delichon urbicum", 2489214) == "not_listed"
    odd = {**accepted, "threatStatuses": ["SOMETHING_NEW"]}
    assert red_list_code([odd], "Delichon urbicum", 2489214) is None
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_gbif.py -v`
Expected: FAIL med `ModuleNotFoundError`.

- [ ] **Step 3: Skriv `gbif.py`**

```python
"""GBIF: exact taxon match, Artportalen counts per month and county, and the Swedish Red
List 2025 (spec 2026-09-25 §9.1). Only CC0 records are counted (spec §10)."""

from __future__ import annotations

import json
from typing import Any
from urllib.parse import quote

from ..cache import Cache
from .counties import COUNTIES
from .datamod import Counts
from .http import ThrottledHttp

API = "https://api.gbif.org/v1"
FILTERS = "country=SE&year=2016,2025&license=CC0_1_0&occurrenceStatus=PRESENT"
AVES_TAXON_KEY = 212
ALL_BIRDS_CACHE_KEY = "_aves"
COUNTS_CACHE_NAME = "gbif-counts-2016-2025.json"
REDLIST_DATASET = "87e639cc-30a9-4007-bd2c-b0cab60326b9"
REDLIST_CODES = {
    "REGIONALLY_EXTINCT": "RE",
    "EXTINCT": "RE",
    "EXTINCT_IN_THE_WILD": "RE",
    "CRITICALLY_ENDANGERED": "CR",
    "ENDANGERED": "EN",
    "VULNERABLE": "VU",
    "NEAR_THREATENED": "NT",
    "DATA_DEFICIENT": "DD",
}
NOT_LISTED = {"LEAST_CONCERN", "NOT_EVALUATED", "NOT_APPLICABLE"}


def parse_counts(data: dict[str, Any]) -> Counts:
    by_month = [0] * 12
    by_county: dict[str, int] = {}
    for facet in data.get("facets", []):
        field = facet.get("field")
        for entry in facet.get("counts", []):
            if field == "MONTH":
                by_month[int(entry["name"]) - 1] = int(entry["count"])
            elif field == "GADM_LEVEL_1_GID" and entry["name"] in COUNTIES:
                by_county[COUNTIES[entry["name"]].iso] = int(entry["count"])
    return Counts(by_month=by_month, by_county=by_county, total=int(data.get("count", 0)))


def red_list_code(results: list[dict[str, Any]], scientific: str, taxon_key: int) -> str | None:
    """`not_listed` when the species is not on the list, its code when it is, and None when
    the list uses a category this table does not know (reported, never guessed)."""
    name = scientific.lower()
    for entry in results:
        if entry.get("taxonomicStatus") != "ACCEPTED" or entry.get("rank") != "SPECIES":
            continue
        same_taxon = entry.get("nubKey") == taxon_key
        if not same_taxon and str(entry.get("canonicalName", "")).lower() != name:
            continue
        statuses = entry.get("threatStatuses") or []
        if not statuses or statuses[0] in NOT_LISTED:
            return "not_listed"
        return REDLIST_CODES.get(statuses[0])
    return "not_listed"


class GbifClient:
    def __init__(self, *, cache: Cache, http: ThrottledHttp | None = None) -> None:
        self.cache = cache
        self._http = http or ThrottledHttp(min_interval=0.2)

    async def _json(self, cache_key: str, name: str, url: str, refresh: bool) -> Any:
        raw = None if refresh else self.cache.get(cache_key, name)
        if raw is None:
            raw = await self._http.fetch_text(url)
            self.cache.put(cache_key, name, raw)
        return json.loads(raw)

    async def taxon_key(self, qid: str, scientific: str, *, refresh: bool = False) -> int | None:
        url = f"{API}/species/match?kingdom=Animalia&strict=true&name={quote(scientific)}"
        data = await self._json(qid, "gbif-match.json", url, refresh)
        if data.get("matchType") != "EXACT" or data.get("rank") != "SPECIES":
            return None
        key = data.get("acceptedUsageKey") if data.get("synonym") else data.get("usageKey")
        return int(key) if key is not None else None

    async def counts(self, qid: str, taxon_key: int, *, refresh: bool = False) -> Counts:
        return await self._counts(qid, taxon_key, refresh)

    async def all_birds(self, *, refresh: bool = False) -> Counts:
        return await self._counts(ALL_BIRDS_CACHE_KEY, AVES_TAXON_KEY, refresh)

    async def _counts(self, cache_key: str, taxon_key: int, refresh: bool) -> Counts:
        url = (
            f"{API}/occurrence/search?{FILTERS}&taxonKey={taxon_key}"
            "&limit=0&facet=month&facet=gadmLevel1Gid&facetLimit=30"
        )
        return parse_counts(await self._json(cache_key, COUNTS_CACHE_NAME, url, refresh))

    async def swedish_red_list(
        self, qid: str, scientific: str, taxon_key: int, *, refresh: bool = False
    ) -> str | None:
        url = f"{API}/species/search?datasetKey={REDLIST_DATASET}&limit=20&q={quote(scientific)}"
        data = await self._json(qid, "gbif-redlist-2025.json", url, refresh)
        return red_list_code(data.get("results", []), scientific, taxon_key)
```

- [ ] **Step 4: Kör testerna**

Run: `uv run pytest tests/test_web_gbif.py -v`
Expected: PASS

- [ ] **Step 5: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/gbif.py tests/test_web_gbif.py
git commit -m "feat(pipeline): GBIF-klient för Artportalen och Svenska rödlistan 2025"
```

---

### Task 7: Ljudfiltret

Reglerna för vilken inspelning som får användas (spec §9.1, skärpt 2026-10-01): en ljudfil, ingen uttalsfil, licens ur tabellen, minst 3 sekunder, och en fil från sökningen (inte från Wikidata) måste nämna arten i titeln eller i en kategori. Ett xeno-canto-nummer räcker inte ensamt.

**Files:**
- Create: `src/birdy_fetcher/web/audio.py`
- Test: `tests/test_web_audio.py`

- [ ] **Step 1: Skriv de fallerande testerna**

`tests/test_web_audio.py`:

```python
"""Tests for web/audio.py: the recording filter, Commons metadata and MP3 conversion."""

from __future__ import annotations

from dataclasses import replace

from birdy_fetcher.web.audio import (
    AudioCandidate,
    audio_record,
    choose,
    normalize_license,
    rejection,
)

BASE = AudioCandidate(
    title="File:Parus major song.ogg",
    url="https://upload.wikimedia.org/a.ogg",
    page_url="https://commons.wikimedia.org/wiki/File:Parus_major_song.ogg",
    mime="application/ogg",
    duration=25.0,
    license="CC BY-SA 4.0",
    author="Anna",
    categories=("Category:Parus major",),
    from_wikidata=False,
)


def test_normalize_license() -> None:
    assert normalize_license("CC BY-SA 4.0") == "CC BY-SA 4.0"
    assert normalize_license("cc-by-sa-3.0") == "CC BY-SA 3.0"
    assert normalize_license("CC BY 2.0") == "CC BY 2.0"
    assert normalize_license("Public Domain") == "Public domain"
    assert normalize_license("CC0 1.0") == "CC0"
    assert normalize_license("GFDL") == "GFDL"
    assert normalize_license(None) is None


def test_a_good_recording_is_accepted() -> None:
    assert rejection(BASE, "Parus major") is None


def test_rejections() -> None:
    assert rejection(replace(BASE, mime="video/webm"), "Parus major") == "inte en ljudfil"
    pronunciation = replace(BASE, categories=("Category:German pronunciation of nouns",))
    assert rejection(pronunciation, "Parus major") == "uttalsfil"
    assert "licensen" in str(rejection(replace(BASE, license="GFDL"), "Parus major"))
    assert "licensen" in str(rejection(replace(BASE, license="CC BY 2.5"), "Parus major"))
    assert rejection(replace(BASE, duration=1.85), "Parus major") == "kortare än 3 sekunder"
    assert rejection(replace(BASE, duration=None), "Parus major") == "kortare än 3 sekunder"


def test_a_search_hit_must_name_the_species() -> None:
    xc_only = replace(BASE, title="File:XC538678.mp3", categories=("Category:Xeno-canto",))
    assert rejection(xc_only, "Parus major") == "nämner inte arten i titeln eller kategorierna"
    assert rejection(replace(xc_only, from_wikidata=True), "Parus major") is None


def test_choose_takes_the_first_accepted_and_reports_the_rest() -> None:
    bad = replace(BASE, title="File:De-Kohlmeise.ogg", duration=1.2)
    chosen, notes = choose([bad, BASE], "Parus major")
    assert chosen == BASE
    assert notes == ["File:De-Kohlmeise.ogg: kortare än 3 sekunder"]
    assert choose([bad], "Parus major") == (None, ["File:De-Kohlmeise.ogg: kortare än 3 sekunder"])


def test_audio_record() -> None:
    long = audio_record(replace(BASE, duration=57.8), "Q25485")
    assert long == {
        "file": "Q25485/voice.mp3",
        "durationSec": 20,
        "trimmed": True,
        "author": "Anna",
        "license": "CC BY-SA 4.0",
        "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
        "sourceUrl": "https://commons.wikimedia.org/wiki/File:Parus_major_song.ogg",
    }
    short = audio_record(replace(BASE, duration=12.4, license="CC0"), "Q25485")
    assert short["durationSec"] == 12
    assert short["trimmed"] is False
    assert short["licenseUrl"] is None
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_audio.py -v`
Expected: FAIL med `ModuleNotFoundError`.

- [ ] **Step 3: Skriv början på `audio.py`**

```python
"""One free recording per species from Wikimedia Commons, trimmed to a 20 s MP3
(spec 2026-09-25 §9.1 and §10). Wikimedia Commons only accepts free licenses, and the
license table is the same as for the photos. Xeno-canto's own NonCommercial recordings are
never used."""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

from .licenses import LICENSE_URLS

MIN_SECONDS = 3.0
MAX_SECONDS = 20


@dataclass(frozen=True)
class AudioCandidate:
    title: str
    url: str
    page_url: str
    mime: str
    duration: float | None
    license: str | None
    author: str | None
    categories: tuple[str, ...]
    from_wikidata: bool


_CC = re.compile(r"cc[ -]by(-sa)?[ -](\d\.\d)")


def normalize_license(raw: str | None) -> str | None:
    """Commons' LicenseShortName in the photo table's spelling ("CC BY-SA 4.0")."""
    if not raw:
        return None
    text = " ".join(raw.split())
    low = text.lower()
    if low in {"public domain", "pd", "public domain mark"}:
        return "Public domain"
    if low.startswith("cc0"):
        return "CC0"
    match = _CC.fullmatch(low)
    if match:
        return f"CC BY{'-SA' if match.group(1) else ''} {match.group(2)}"
    return text


def rejection(candidate: AudioCandidate, scientific: str) -> str | None:
    """Why a file may not be used, or None when it may."""
    if not (candidate.mime.startswith("audio/") or candidate.mime == "application/ogg"):
        return "inte en ljudfil"
    categories = [c.lower() for c in candidate.categories]
    if any("pronunciation" in c or "lingua libre" in c for c in categories):
        return "uttalsfil"
    if candidate.license not in LICENSE_URLS:
        return f"licensen {candidate.license!r} finns inte i licenstabellen"
    if candidate.duration is None or candidate.duration < MIN_SECONDS:
        return "kortare än 3 sekunder"
    if not candidate.from_wikidata:
        name = scientific.lower()
        in_title = name in candidate.title.lower().replace("_", " ")
        if not in_title and not any(name in c for c in categories):
            return "nämner inte arten i titeln eller kategorierna"
    return None


def choose(
    candidates: list[AudioCandidate], scientific: str
) -> tuple[AudioCandidate | None, list[str]]:
    notes: list[str] = []
    for candidate in candidates:
        reason = rejection(candidate, scientific)
        if reason is None:
            return candidate, notes
        notes.append(f"{candidate.title}: {reason}")
    return None, notes


def audio_record(candidate: AudioCandidate, qid: str) -> dict[str, Any]:
    """The record's `audio` object (spec appendix C). Only for an accepted candidate."""
    if candidate.duration is None or candidate.license is None:
        raise ValueError(f"{candidate.title} har ingen längd eller licens")
    return {
        "file": f"{qid}/voice.mp3",
        "durationSec": min(MAX_SECONDS, round(candidate.duration)),
        "trimmed": candidate.duration > MAX_SECONDS,
        "author": candidate.author,
        "license": candidate.license,
        "licenseUrl": LICENSE_URLS[candidate.license],
        "sourceUrl": candidate.page_url,
    }
```

- [ ] **Step 4: Kör testerna**

Run: `uv run pytest tests/test_web_audio.py -v`
Expected: PASS

- [ ] **Step 5: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/audio.py tests/test_web_audio.py
git commit -m "feat(pipeline): filter för fria inspelningar från Commons"
```

---

### Task 8: Commons-klienten och MP3-omkodningen

**Files:**
- Modify: `pyproject.toml`, `uv.lock` (via `uv add`)
- Modify: `src/birdy_fetcher/web/audio.py` (lägg till)
- Test: `tests/test_web_audio.py` (lägg till)

- [ ] **Step 1: Lägg till ffmpeg-paketet**

Run: `uv add "imageio-ffmpeg>=0.6.0"`
Expected: `pyproject.toml` och `uv.lock` uppdateras. Kontrollera att binären har MP3-kodaren (provat 2026-10-01: imageio-ffmpeg 0.6.0 ger ffmpeg 7.1 med `libmp3lame` och `loudnorm`):

Run: `uv run python -c "import imageio_ffmpeg, subprocess; e=imageio_ffmpeg.get_ffmpeg_exe(); print('libmp3lame' in subprocess.run([e,'-hide_banner','-encoders'],capture_output=True,text=True).stdout)"`
Expected: `True`

Lägg till längst ned i `pyproject.toml`:

```toml
[[tool.mypy.overrides]]
module = ["imageio_ffmpeg"]
ignore_missing_imports = true
```

- [ ] **Step 2: Skriv de fallerande testerna**

Lägg till i `tests/test_web_audio.py` (importerna överst i filen, testerna längst ned):

```python
import io
import json
import math
import struct
import subprocess
import wave
from pathlib import Path

import imageio_ffmpeg

from birdy_fetcher.cache import Cache
from birdy_fetcher.web.audio import CommonsAudioClient, convert_to_mp3, probe_seconds
from birdy_fetcher.web.http import ThrottledHttp
```

```python
P51 = {"claims": {"P51": [{"mainsnak": {"datavalue": {"value": "Parus_major_song.ogg"}}}]}}
SEARCH = {
    "query": {
        "search": [{"title": "File:De-Kohlmeise.ogg"}, {"title": "File:Parus_major_song.ogg"}]
    }
}
INFO = {
    "query": {
        "normalized": [{"from": "File:Parus_major_song.ogg", "to": "File:Parus major song.ogg"}],
        "pages": [
            {
                "title": "File:Parus major song.ogg",
                "imageinfo": [
                    {
                        "url": "https://upload.wikimedia.org/a.ogg",
                        "descriptionurl": (
                            "https://commons.wikimedia.org/wiki/File:Parus_major_song.ogg"
                        ),
                        "mime": "application/ogg",
                        "duration": 31.5,
                        "extmetadata": {
                            "LicenseShortName": {"value": "CC BY-SA 4.0"},
                            "Artist": {"value": '<a href="//commons.wikimedia.org/x">Anna</a>'},
                        },
                    }
                ],
                "categories": [{"title": "Category:Parus major"}],
            },
            {
                "title": "File:De-Kohlmeise.ogg",
                "imageinfo": [
                    {
                        "url": "https://upload.wikimedia.org/b.ogg",
                        "descriptionurl": "https://commons.wikimedia.org/wiki/File:De-Kohlmeise.ogg",
                        "mime": "application/ogg",
                        "duration": 1.2,
                        "extmetadata": {"LicenseShortName": {"value": "CC BY-SA 4.0"}},
                    }
                ],
                "categories": [{"title": "Category:German pronunciation of nouns"}],
            },
        ],
    }
}


class Routed:
    def __init__(self, routes: dict[str, object]) -> None:
        self.routes = routes
        self.urls: list[str] = []

    async def __call__(self, url: str) -> str:
        self.urls.append(url)
        for key, body in self.routes.items():
            if key in url:
                return json.dumps(body)
        raise FileNotFoundError(url)


def _wav(seconds: int) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(22050)
        frames = b"".join(
            struct.pack("<hh", int(8000 * math.sin(2 * math.pi * 440 * i / 22050)), 0)
            for i in range(22050 * seconds)
        )
        w.writeframes(frames)
    return buf.getvalue()


async def test_candidates_put_wikidata_first_and_parse_metadata(tmp_path: Path) -> None:
    http = Routed({"wbgetclaims": P51, "list=search": SEARCH, "prop=imageinfo": INFO})
    client = CommonsAudioClient(
        cache=Cache(tmp_path), http=ThrottledHttp(get_text=http, min_interval=0)
    )
    candidates = await client.candidates("Q25485", "Parus major")
    assert [c.title for c in candidates] == ["File:Parus major song.ogg", "File:De-Kohlmeise.ogg"]
    first = candidates[0]
    assert first.from_wikidata is True
    assert first.duration == 31.5
    assert first.author == "Anna"
    assert first.license == "CC BY-SA 4.0"
    assert first.categories == ("Category:Parus major",)
    assert candidates[1].from_wikidata is False
    await client.candidates("Q25485", "Parus major")
    assert len(http.urls) == 3


async def test_no_candidates_means_no_info_request(tmp_path: Path) -> None:
    http = Routed({"wbgetclaims": {"claims": {}}, "list=search": {"query": {"search": []}}})
    client = CommonsAudioClient(
        cache=Cache(tmp_path), http=ThrottledHttp(get_text=http, min_interval=0)
    )
    assert await client.candidates("Q1", "Parus major") == []
    assert len(http.urls) == 2


async def test_download_is_cached(tmp_path: Path) -> None:
    calls = 0

    async def get_bytes(url: str) -> bytes:
        nonlocal calls
        calls += 1
        return b"OggS"

    client = CommonsAudioClient(
        cache=Cache(tmp_path), http=ThrottledHttp(get_bytes=get_bytes, min_interval=0)
    )
    assert await client.download("Q25485", BASE) == b"OggS"
    assert await client.download("Q25485", BASE) == b"OggS"
    assert calls == 1


def test_convert_trims_to_20_seconds_mono_mp3(tmp_path: Path) -> None:
    out = tmp_path / "Q1" / "voice.mp3"
    convert_to_mp3(_wav(25), out)
    assert out.exists()
    assert 19.5 <= probe_seconds(out) <= 20.5
    assert out.stat().st_size < 250_000
    info = subprocess.run(
        [imageio_ffmpeg.get_ffmpeg_exe(), "-hide_banner", "-i", str(out)],
        capture_output=True,
        text=True,
        check=False,
    ).stderr
    assert "mono" in info
```

- [ ] **Step 3: Kör och se dem falla**

Run: `uv run pytest tests/test_web_audio.py -v`
Expected: FAIL med `ImportError` för `CommonsAudioClient`, `convert_to_mp3` och `probe_seconds`.

- [ ] **Step 4: Implementera**

Ersätt importblocket i `audio.py` med:

```python
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import quote

import imageio_ffmpeg

from ..cache import Cache
from .http import ThrottledHttp
from .licenses import LICENSE_URLS, clean_author
```

Lägg sedan till längst ned i `audio.py`:

```python
COMMONS_API = "https://commons.wikimedia.org/w/api.php"
WIKIDATA_API = "https://www.wikidata.org/w/api.php"
MAX_TITLES = 20
BITRATE = "64k"


def parse_candidate(page: dict[str, Any], *, from_wikidata: bool) -> AudioCandidate:
    info = page["imageinfo"][0]
    meta = info.get("extmetadata", {})
    duration = info.get("duration")
    return AudioCandidate(
        title=page["title"],
        url=info["url"],
        page_url=info.get("descriptionurl", ""),
        mime=info.get("mime", ""),
        duration=float(duration) if duration is not None else None,
        license=normalize_license(meta.get("LicenseShortName", {}).get("value")),
        author=clean_author(meta.get("Artist", {}).get("value")),
        categories=tuple(c["title"] for c in page.get("categories", [])),
        from_wikidata=from_wikidata,
    )


class CommonsAudioClient:
    def __init__(self, *, cache: Cache, http: ThrottledHttp | None = None) -> None:
        self.cache = cache
        self._http = http or ThrottledHttp(min_interval=1.0)

    async def _json(self, qid: str, name: str, url: str, refresh: bool) -> Any:
        raw = None if refresh else self.cache.get(qid, name)
        if raw is None:
            raw = await self._http.fetch_text(url)
            self.cache.put(qid, name, raw)
        return json.loads(raw)

    async def candidates(
        self, qid: str, scientific: str, *, refresh: bool = False
    ) -> list[AudioCandidate]:
        """Wikidata's own recordings (P51) first, then a Commons search for the name."""
        claims_url = f"{WIKIDATA_API}?action=wbgetclaims&format=json&entity={qid}&property=P51"
        claims = await self._json(qid, "audio-p51.json", claims_url, refresh)
        p51 = [
            "File:" + claim["mainsnak"]["datavalue"]["value"]
            for claim in claims.get("claims", {}).get("P51", [])
            if "datavalue" in claim.get("mainsnak", {})
        ]
        search = quote(f'"{scientific}" filetype:audio')
        search_url = (
            f"{COMMONS_API}?action=query&format=json&formatversion=2&list=search"
            f"&srnamespace=6&srlimit=10&srsearch={search}"
        )
        found = await self._json(qid, "audio-search.json", search_url, refresh)
        hits = [hit["title"] for hit in found.get("query", {}).get("search", [])]
        titles = (p51 + [t for t in hits if t not in p51])[:MAX_TITLES]
        if not titles:
            return []
        info_url = (
            f"{COMMONS_API}?action=query&format=json&formatversion=2"
            "&prop=imageinfo%7Ccategories&clshow=!hidden&cllimit=max"
            "&iiprop=url%7Cextmetadata%7Cmime%7Csize"
            "&iiextmetadatafilter=LicenseShortName%7CArtist"
            f"&titles={quote('|'.join(titles))}"
        )
        info = await self._json(qid, "audio-info.json", info_url, refresh)
        query = info.get("query", {})
        normalized = {n["from"]: n["to"] for n in query.get("normalized", [])}
        pages = {page["title"]: page for page in query.get("pages", [])}
        result: list[AudioCandidate] = []
        for title in titles:
            page = pages.get(normalized.get(title, title))
            if page is None or not page.get("imageinfo"):
                continue
            result.append(parse_candidate(page, from_wikidata=title in p51))
        return result

    async def download(
        self, qid: str, candidate: AudioCandidate, *, refresh: bool = False
    ) -> bytes:
        digest = hashlib.sha256(candidate.title.encode("utf-8")).hexdigest()[:12]
        name = f"audio-{digest}.bin"
        raw = None if refresh else self.cache.get_bytes(qid, name)
        if raw is None:
            raw = await self._http.fetch_bytes(candidate.url)
            self.cache.put_bytes(qid, name, raw)
        return raw


def convert_to_mp3(raw: bytes, out_path: Path) -> None:
    """The first 20 s, mono, loudness-normalised, MP3 at 64 kbit/s (about 160 kB)."""
    exe = imageio_ffmpeg.get_ffmpeg_exe()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "in.bin"
        dst = Path(tmp) / "out.mp3"
        src.write_bytes(raw)
        command = [exe, "-hide_banner", "-loglevel", "error", "-y", "-i", str(src)]
        command += ["-t", str(MAX_SECONDS), "-ac", "1", "-af", "loudnorm=I=-16:TP=-1.5:LRA=11"]
        command += ["-codec:a", "libmp3lame", "-b:a", BITRATE, str(dst)]
        subprocess.run(command, check=True, capture_output=True)
        shutil.copyfile(dst, out_path)


_DURATION = re.compile(r"Duration: (\d+):(\d+):(\d+(?:\.\d+)?)")


def probe_seconds(path: Path) -> float:
    exe = imageio_ffmpeg.get_ffmpeg_exe()
    result = subprocess.run(
        [exe, "-hide_banner", "-i", str(path)], capture_output=True, text=True, check=False
    )
    match = _DURATION.search(result.stderr)
    if match is None:
        raise ValueError(f"Hittar ingen längd för {path}")
    hours, minutes, seconds = match.groups()
    return int(hours) * 3600 + int(minutes) * 60 + float(seconds)
```

- [ ] **Step 5: Kör testerna**

Run: `uv run pytest tests/test_web_audio.py -v`
Expected: PASS (ffmpeg-testet tar någon sekund).

- [ ] **Step 6: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add pyproject.toml uv.lock src/birdy_fetcher/web/audio.py tests/test_web_audio.py
git commit -m "feat(pipeline): inspelningar från Wikidata och Commons, klippta till MP3"
```

---

### Task 9: Vad appen klarar per art, och index över vetenskapliga namn

**Files:**
- Create: `src/birdy_fetcher/web/identify.py`
- Modify: `src/birdy_fetcher/web/source.py` (lägg till `load_scientific_index`)
- Test: `tests/test_web_identify.py`

Fotomodellens fil har nyckeln `mappings`, ljudmodellens `mapping` (kontrollerat 2026-10-01). Bland de 180 täcker fotomodellen 122 och ljudmodellen 174, och 6 arter finns i ingen.

- [ ] **Step 1: Skriv de fallerande testerna**

`tests/test_web_identify.py`:

```python
"""Tests for web/identify.py and source.load_scientific_index."""

from __future__ import annotations

import json
from pathlib import Path

from birdy_fetcher.web.identify import load_coverage
from birdy_fetcher.web.source import load_scientific_index

from .test_web_source import YAML_TEMPLATE


def test_coverage_reads_both_model_maps(tmp_path: Path) -> None:
    (tmp_path / "aiy_to_qid.json").write_text(
        json.dumps({"_meta": {}, "mappings": {"0": "Q1", "1": "Q2", "2": None}}), encoding="utf-8"
    )
    (tmp_path / "birdnet_lite_to_qid.json").write_text(
        json.dumps({"_meta": {}, "mapping": {"33": "Q2", "45": "Q3"}}), encoding="utf-8"
    )
    coverage = load_coverage(tmp_path)
    assert coverage.for_qid("Q1") == {"photo": True, "sound": False}
    assert coverage.for_qid("Q2") == {"photo": True, "sound": True}
    assert coverage.for_qid("Q3") == {"photo": False, "sound": True}
    assert coverage.for_qid("Q4") == {"photo": False, "sound": False}


def test_scientific_index_covers_all_species_lowercase(tmp_path: Path) -> None:
    for qid, status, scientific in (
        ("Q1", "approved", "Parus major"),
        ("Q2", "auto", "Cyanistes caeruleus"),
    ):
        text = YAML_TEMPLATE.format(qid=qid, sv="X", en="X", status=status, marginalia="")
        text = text.replace("scientific_name: Parus major", f"scientific_name: {scientific}")
        path = tmp_path / "x" / f"{qid}.yaml"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    assert load_scientific_index(tmp_path) == {"parus major": "Q1", "cyanistes caeruleus": "Q2"}
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_identify.py -v`
Expected: FAIL med `ModuleNotFoundError` respektive `ImportError`.

- [ ] **Step 3: Implementera**

`src/birdy_fetcher/web/identify.py`:

```python
"""What the app can identify per species, read from the app's own model maps (spec §9.1).
The app box on the page only promises what this says (photo, song, both or neither)."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class ModelCoverage:
    photo: frozenset[str]
    sound: frozenset[str]

    def for_qid(self, qid: str) -> dict[str, bool]:
        return {"photo": qid in self.photo, "sound": qid in self.sound}


def _qids(path: Path, key: str) -> frozenset[str]:
    mapping = json.loads(path.read_text(encoding="utf-8"))[key]
    return frozenset(value for value in mapping.values() if isinstance(value, str))


def load_coverage(ml_dir: Path) -> ModelCoverage:
    return ModelCoverage(
        photo=_qids(ml_dir / "aiy_to_qid.json", "mappings"),
        sound=_qids(ml_dir / "birdnet_lite_to_qid.json", "mapping"),
    )
```

Lägg till längst ned i `source.py`:

```python
def load_scientific_index(species_root: Path) -> dict[str, str]:
    """Lowercased scientific name to QID for all species, approved or not. Used to link a
    look-alike named in a fact to its species."""
    index: dict[str, str] = {}
    for path in sorted(species_root.rglob("*.yaml")):
        data: dict[str, Any] = yaml.safe_load(path.read_text(encoding="utf-8"))
        index[str(data["scientific_name"]).lower()] = data["id"]
    return index
```

- [ ] **Step 4: Kör testerna**

Run: `uv run pytest tests/test_web_identify.py tests/test_web_source.py -v`
Expected: PASS

- [ ] **Step 5: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/identify.py src/birdy_fetcher/web/source.py tests/test_web_identify.py
git commit -m "feat(pipeline): appens foto- och ljudtäckning per art och namnindex"
```

---

### Task 10: Modellklienten med JSON-schema

Fas 1:s klient var bunden till textens modell. Faktabladet, texten, kontrollen och jämförelserna behöver var sitt schema. Samma beteende som förut: `messages.create` med `output_config` (inte `.parse()`), så att användning och `stop_reason` överlever även avklippta och avvisade svar.

Server-side `fallbacks` vid avvisning används inte: SDK 0.97 är låst, och en reservmodell skulle prissättas fel i kostnadsspärren. En avvisning ger `failed` för arten och syns i rapporten.

**Files:**
- Create: `src/birdy_fetcher/web/llm.py`
- Test: `tests/test_web_llm.py`

- [ ] **Step 1: Skriv de fallerande testerna**

`tests/test_web_llm.py`:

```python
"""Tests for web/llm.py: JSON-schema answers and cost bookkeeping."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast

from pydantic import BaseModel

from birdy_fetcher.cost import CostTracker
from birdy_fetcher.web.llm import AnthropicJsonClient, ModelReply, record_cost


class Answer(BaseModel):
    name: str


@dataclass
class _Usage:
    input_tokens: int
    output_tokens: int


@dataclass
class _Block:
    type: str
    text: str


@dataclass
class _Message:
    content: list[_Block]
    usage: _Usage
    stop_reason: str


@dataclass
class _Messages:
    reply: _Message
    kwargs: dict[str, Any] = field(default_factory=dict)

    async def create(self, **kwargs: Any) -> _Message:
        self.kwargs = kwargs
        return self.reply


@dataclass
class _Anthropic:
    messages: _Messages
    closed: bool = False

    async def close(self) -> None:
        self.closed = True


def _client(text: str, stop: str = "end_turn") -> tuple[AnthropicJsonClient, _Anthropic]:
    fake = _Anthropic(_Messages(_Message([_Block("text", text)], _Usage(1000, 200), stop)))
    return AnthropicJsonClient(client=cast(Any, fake)), fake


async def test_valid_json_is_parsed_and_the_schema_is_sent() -> None:
    client, fake = _client('{"name": "Talgoxe"}')
    reply = await client.complete(
        model="claude-opus-5", system="s", messages=[], effort="high", schema=Answer
    )
    assert reply.parsed == Answer(name="Talgoxe")
    assert (reply.input_tokens, reply.output_tokens) == (1000, 200)
    sent = fake.messages.kwargs["output_config"]
    assert sent["effort"] == "high"
    assert sent["format"]["type"] == "json_schema"
    assert "name" in sent["format"]["schema"]["properties"]


async def test_invalid_json_keeps_usage_and_stop_reason() -> None:
    client, _ = _client('{"name": ', stop="max_tokens")
    reply = await client.complete(
        model="claude-opus-5", system="s", messages=[], effort="high", schema=Answer
    )
    assert reply.parsed is None
    assert reply.stop_reason == "max_tokens"
    assert reply.output_tokens == 200


async def test_aclose_closes_the_sdk_client() -> None:
    client, fake = _client("{}")
    await client.aclose()
    assert fake.closed


def test_record_cost_uses_the_model_price() -> None:
    cost = CostTracker(max_usd=None)
    record_cost(cost, "opus", ModelReply(None, "", 1_000_000, 0, "end_turn"))
    record_cost(cost, "sonnet", ModelReply(None, "", 0, 1_000_000, "end_turn"))
    assert round(cost.total_usd, 2) == 15.00
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_llm.py -v`
Expected: FAIL med `ModuleNotFoundError`.

- [ ] **Step 3: Skriv `llm.py`**

```python
"""Claude calls that answer with JSON matching a pydantic model (spec §9). Uses
`messages.create` with a JSON-schema output format, not the SDK's `.parse()`: in anthropic
0.97 `.parse()` raises inside its own post-parser when a reply is cut off at max_tokens or
refused, which throws away the paid usage and the real stop_reason. We validate ourselves."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol

from anthropic import AsyncAnthropic, transform_schema
from anthropic.types import JSONOutputFormatParam, MessageParam, OutputConfigParam
from pydantic import BaseModel, ValidationError

from ..cost import CostTracker

MODELS = {"opus": "claude-opus-5", "sonnet": "claude-sonnet-5"}
COST_KEYS = {"opus": "opus5", "sonnet": "sonnet5"}
MAX_TOKENS = 16_000


@dataclass
class ModelReply[M: BaseModel]:
    parsed: M | None
    raw_text: str
    input_tokens: int
    output_tokens: int
    stop_reason: str | None


class JsonModelClient(Protocol):
    async def complete[M: BaseModel](
        self,
        *,
        model: str,
        system: str,
        messages: list[MessageParam],
        effort: str,
        schema: type[M],
        max_tokens: int = MAX_TOKENS,
    ) -> ModelReply[M]: ...


class AnthropicJsonClient:
    """The locked anthropic 0.97 SDK reads ANTHROPIC_API_KEY or ANTHROPIC_AUTH_TOKEN from the
    environment; there is no `ant auth login` profile support. Retries are turned up because
    180 species means 429/529 answers are routine."""

    def __init__(self, client: AsyncAnthropic | None = None) -> None:
        self._client = client or AsyncAnthropic(max_retries=5)

    async def complete[M: BaseModel](
        self,
        *,
        model: str,
        system: str,
        messages: list[MessageParam],
        effort: str,
        schema: type[M],
        max_tokens: int = MAX_TOKENS,
    ) -> ModelReply[M]:
        output_format: JSONOutputFormatParam = {
            "type": "json_schema",
            "schema": transform_schema(schema),
        }
        msg = await self._client.messages.create(
            model=model,
            max_tokens=max_tokens,
            system=system,
            messages=messages,
            # `effort` is a plain str in our code; the CLI's click.Choice limits it to values
            # the SDK's Literal accepts.
            output_config=OutputConfigParam(format=output_format, effort=effort),  # type: ignore[typeddict-item]
        )
        text = "".join(block.text for block in msg.content if block.type == "text")
        try:
            parsed: M | None = schema.model_validate_json(text)
        except ValidationError:
            parsed = None
        return ModelReply(
            parsed, text, msg.usage.input_tokens, msg.usage.output_tokens, msg.stop_reason
        )

    async def aclose(self) -> None:
        await self._client.close()


def record_cost(cost: CostTracker, model_key: str, reply: ModelReply[Any]) -> None:
    """Raises MaxCostExceeded when the cap is passed; the reply is already paid for then."""
    cost.record(
        model=COST_KEYS[model_key],
        input_tokens=reply.input_tokens,
        output_tokens=reply.output_tokens,
    )
```

- [ ] **Step 4: Kör testerna**

Run: `uv run pytest tests/test_web_llm.py -v`
Expected: PASS

- [ ] **Step 5: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`
Expected: rent. Säger mypy att `type: ignore[typeddict-item]` är onödig, ta bort kommentaren (samma rad behövde den i `writer.py`).

```bash
git add src/birdy_fetcher/web/llm.py tests/test_web_llm.py
git commit -m "feat(pipeline): modellklient med JSON-schema för alla nya steg"
```

---

### Task 11: Sökvägar, artposten, stegrapporten och licensfilerna

**Files:**
- Create: `src/birdy_fetcher/web/paths.py`, `src/birdy_fetcher/web/record.py`
- Modify: `src/birdy_fetcher/web/run.py` (importera `WebPaths` från `paths.py`), `src/birdy_fetcher/web/report.py` (lägg till)
- Create: `website/src/data/species/LICENSE.md`, `website/src/data/comparisons/LICENSE.md` (sökvägar från repots rot)
- Modify: `LICENSE` (repots rot)
- Test: `tests/test_web_record.py`, `tests/test_web_report.py` (lägg till)

- [ ] **Step 1: Skriv de fallerande testerna**

`tests/test_web_record.py`:

```python
"""Tests for web/record.py: the record is the state between the steps."""

from __future__ import annotations

from pathlib import Path

from birdy_fetcher.web.record import (
    facts_hash,
    is_reviewed,
    load_all,
    load_record,
    merge_sources,
    new_record,
    record_path,
    save_record,
)


def test_new_record_is_pending_and_unpublished() -> None:
    record = new_record("Q1")
    assert record["status"] == "pending"
    assert record["publish"] is False
    assert record["review"] == {}
    assert record["facts"] == []
    assert record["text"] is None


def test_merge_sources_keeps_what_later_steps_own() -> None:
    existing = new_record("Q1")
    existing["facts"] = [{"id": "f01"}]
    existing["review"] = {"facts": {"by": "Albin Abrahamsson", "at": "2026-11-01"}}
    existing["audio"] = {"file": "Q1/voice.mp3"}
    merged = merge_sources(existing, "Q1", {"iucn": "LC", "audio": None, "data": {"x": 1}})
    assert merged["facts"] == [{"id": "f01"}]
    assert merged["review"]["facts"]["by"] == "Albin Abrahamsson"
    assert merged["iucn"] == "LC"
    assert merged["data"] == {"x": 1}
    assert "audio" not in merged


def test_save_and_load_round_trip(tmp_path: Path) -> None:
    path = record_path(tmp_path, "Q1")
    record = merge_sources(None, "Q1", {"names": {"sv": "Talgoxe"}})
    save_record(path, record)
    assert load_record(path) == record
    assert path.read_text(encoding="utf-8").endswith("}\n")
    assert "Talgoxe" in path.read_text(encoding="utf-8")
    assert load_record(tmp_path / "Q404.json") is None
    assert list(load_all(tmp_path)) == ["Q1"]


def test_facts_hash_changes_with_the_facts() -> None:
    record = new_record("Q1")
    before = facts_hash(record)
    record["facts"] = [{"id": "f01", "sv": "Svart huvud."}]
    assert facts_hash(record) != before
    assert len(facts_hash(record)) == 16


def test_is_reviewed() -> None:
    record = new_record("Q1")
    assert not is_reviewed(record)
    record["review"]["facts"] = {"by": "Albin Abrahamsson", "at": "2026-11-01"}
    assert is_reviewed(record)
```

Lägg till i `tests/test_web_report.py` (importera `StepOutcome`, `render_step_report` och `write_step_report` från `birdy_fetcher.web.report`, samt `from datetime import UTC, datetime` och `from pathlib import Path`):

```python
def test_step_report_lists_failures_skips_and_notes(tmp_path: Path) -> None:
    outcomes = [
        StepOutcome("Q1", "Talgoxe", "ok", notes=["ingen fri inspelning"]),
        StepOutcome("Q2", "Blåmes", "failed", errors=["ingen Wikipediaartikel"]),
        StepOutcome("Q3", "Koltrast", "skipped", errors=["granskad"]),
    ]
    text = render_step_report(
        title="Källor", date="2026-10-02", outcomes=outcomes, cost_usd=1.5, model_line="Modell: x"
    )
    assert text.startswith("# Källor 2026-10-02")
    assert "Kostnad för körningen: $1.50." in text
    assert "| ok | 1 |" in text
    assert "| failed | 1 |" in text
    assert "- **Blåmes (Q2)**: ingen Wikipediaartikel" in text
    assert "- **Koltrast (Q3)**: granskad" in text
    assert "- **Talgoxe (Q1)**: ingen fri inspelning" in text
    path = write_step_report(tmp_path, "sources", datetime(2026, 10, 2, 9, 30, tzinfo=UTC), text)
    assert path.name == "web-sources-2026-10-02-093000.md"
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_record.py tests/test_web_report.py -v`
Expected: FAIL med `ModuleNotFoundError` respektive `ImportError`.

- [ ] **Step 3: Skriv `paths.py`**

Flytta klassen `WebPaths` från `run.py` till `src/birdy_fetcher/web/paths.py` och lägg till de nya egenskaperna. Hela filen:

```python
"""Where the web step reads and writes, relative to the repo root."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class WebPaths:
    repo_root: Path

    @property
    def pipeline_root(self) -> Path:
        return self.repo_root / "tools" / "content-pipeline"

    @property
    def species_root(self) -> Path:
        return self.repo_root / "shared" / "content" / "species"

    @property
    def asset_images(self) -> Path:
        return self.repo_root / "asset-pack" / "src" / "main" / "assets" / "images"

    @property
    def family_groups(self) -> Path:
        return (
            self.repo_root
            / "shared"
            / "content"
            / "src"
            / "jvmMain"
            / "resources"
            / "family_groups.yaml"
        )

    @property
    def web_groups(self) -> Path:
        return self.repo_root / "website" / "src" / "data" / "species-groups.json"

    @property
    def data_out(self) -> Path:
        return self.repo_root / "website" / "src" / "data" / "species"

    @property
    def comparisons_out(self) -> Path:
        return self.repo_root / "website" / "src" / "data" / "comparisons"

    @property
    def images_out(self) -> Path:
        """Photos and recordings, website/src/assets/species/<QID>/. Not public/, so a file is
        only served once a built page uses it (spec §9.1)."""
        return self.repo_root / "website" / "src" / "assets" / "species"

    @property
    def ml_dir(self) -> Path:
        return (
            self.repo_root
            / "shared"
            / "ml"
            / "src"
            / "commonMain"
            / "composeResources"
            / "files"
            / "ml"
        )

    @property
    def reports(self) -> Path:
        return self.pipeline_root / "reports"

    @property
    def review(self) -> Path:
        return self.pipeline_root / "review"

    @property
    def prompt(self) -> Path:
        return self.pipeline_root / "prompts" / "web-v1.md"

    def prompt_file(self, name: str) -> Path:
        return self.pipeline_root / "prompts" / f"{name}.md"

    @property
    def banned(self) -> Path:
        return self.pipeline_root / "prompts" / "web-banned-phrases.txt"
```

I `run.py`: ta bort klassen `WebPaths` och lägg till importen nedan. Skrivsättet `as WebPaths` exporterar namnet vidare (mypy strict kräver det), så `tests/test_web_run.py` och `cli.py` som importerar `WebPaths` från `run` fungerar till Task 24.

```python
from .paths import WebPaths as WebPaths
```

- [ ] **Step 4: Skriv `record.py`**

```python
"""The species record (website/src/data/species/<QID>.json) is the state between the steps
(spec 2026-09-25 appendix C). Each step owns some keys and leaves the rest alone: sources
owns SOURCE_KEYS, facts owns `facts` and `generated.facts`, import owns `review`, write owns
`text`, `status` and `generated.text`, publish owns `publish`."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

from .images import ImageOut

Record = dict[str, Any]

SOURCE_KEYS = (
    "slug",
    "names",
    "family",
    "group",
    "iucn",
    "swedishRedList",
    "identifiable",
    "marginalia",
    "images",
    "audio",
    "wikipedia",
    "data",
)


def record_path(out_dir: Path, qid: str) -> Path:
    return out_dir / f"{qid}.json"


def load_record(path: Path) -> Record | None:
    if not path.exists():
        return None
    data: Record = json.loads(path.read_text(encoding="utf-8"))
    return data


def save_record(path: Path, record: Record) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def load_all(out_dir: Path) -> dict[str, Record]:
    records: dict[str, Record] = {}
    for path in sorted(out_dir.glob("Q*.json")):
        record = load_record(path)
        if record is not None:
            records[path.stem] = record
    return records


def new_record(qid: str) -> Record:
    return {
        "qid": qid,
        "status": "pending",
        "publish": False,
        "review": {},
        "facts": [],
        "text": None,
        "generated": {},
        "errors": [],
    }


def merge_sources(existing: Record | None, qid: str, sources: dict[str, Any]) -> Record:
    """Writes the source keys. A key whose value is None is removed (spec appendix C: e.g.
    `audio` is missing when no free recording exists)."""
    record = existing if existing is not None else new_record(qid)
    for key in SOURCE_KEYS:
        value = sources.get(key)
        if value is None:
            record.pop(key, None)
        else:
            record[key] = value
    return record


def image_dict(image: ImageOut) -> dict[str, Any]:
    return {
        "role": image.role,
        "file": image.file,
        "width": image.width,
        "height": image.height,
        "author": image.author,
        "license": image.license,
        "licenseUrl": image.license_url,
        "sourceUrl": image.source_url,
    }


def is_reviewed(record: Record) -> bool:
    return bool(record.get("review", {}).get("facts"))


def facts_hash(record: Record) -> str:
    payload = json.dumps(record.get("facts", []), ensure_ascii=False, sort_keys=True)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]
```

- [ ] **Step 5: Lägg till stegrapporten i `report.py`**

Ersätt raden `from dataclasses import dataclass` med importerna nedan och lägg till koden längst ned i filen.

```python
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
```

```python
STEP_STATUSES = ("ok", "pending", "failed", "skipped", "dry-run")


@dataclass(frozen=True)
class StepOutcome:
    qid: str
    name: str
    status: str
    errors: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)


def render_step_report(
    *,
    title: str,
    date: str,
    outcomes: list[StepOutcome],
    cost_usd: float | None = None,
    model_line: str | None = None,
) -> str:
    counts = Counter(o.status for o in outcomes)
    lines = [f"# {title} {date}", ""]
    if model_line:
        lines += [model_line, ""]
    if cost_usd is not None:
        lines += [f"Kostnad för körningen: ${cost_usd:.2f}.", ""]
    lines += ["| Utfall | Antal |", "|---|---|"]
    lines += [f"| {s} | {counts[s]} |" for s in STEP_STATUSES if counts[s]]
    lines.append("")
    for heading, status in (("Misslyckades", "failed"), ("Hoppades över", "skipped")):
        rows = [o for o in outcomes if o.status == status]
        lines += [f"## {heading}", ""]
        lines += [f"- **{o.name} ({o.qid})**: {'; '.join(o.errors)}" for o in rows] or ["Inga."]
        lines.append("")
    noted = [o for o in outcomes if o.notes]
    lines += ["## Anteckningar", ""]
    lines += [f"- **{o.name} ({o.qid})**: {'; '.join(o.notes)}" for o in noted] or ["Inga."]
    return "\n".join(lines) + "\n"


def write_step_report(reports_dir: Path, step: str, now: datetime, text: str) -> Path:
    reports_dir.mkdir(parents=True, exist_ok=True)
    path = reports_dir / f"web-{step}-{now:%Y-%m-%d-%H%M%S}.md"
    path.write_text(text, encoding="utf-8")
    return path
```

- [ ] **Step 6: Licensfilerna (spec §10 punkt 1)**

Skapa `website/src/data/species/LICENSE.md` (sökväg från repots rot):

```markdown
# Licens för arttexterna

Texterna i den här mappen (fälten `facts`, `text` och `rejectedText` i varje JSON-fil) bygger på artiklar på svenska, engelska och tyska Wikipedia och delas under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.sv). Vilka artiklar och revisioner som använts står under `wikipedia` i varje fil.

Rapportdatan under `data` kommer från Artportalen (SLU Artdatabanken) via GBIF.org och rödlistestatusen från Rödlistade arter i Sverige 2025 (SLU Artdatabanken). Båda är CC0. Foton och inspelningar har de licenser som står under `images` och `audio` i varje fil.

Resten av repot omfattas av licensen i rotens `LICENSE`.

## License for the species texts

The texts in this folder (the `facts`, `text` and `rejectedText` fields of each JSON file) are based on articles on Swedish, English and German Wikipedia and are shared under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). The articles and revisions used are listed under `wikipedia` in each file. Report data under `data` comes from Artportalen (SLU Swedish Species Information Centre) via GBIF.org and the red list status from The Swedish Red List 2025, both CC0. Photos and recordings carry the licenses listed under `images` and `audio`. The rest of the repository is covered by the root `LICENSE`.
```

Skapa `website/src/data/comparisons/LICENSE.md`:

```markdown
# Licens för jämförelsetexterna

Texterna i den här mappen är skrivna ur arternas faktablad, som bygger på artiklar på svenska, engelska och tyska Wikipedia, och delas under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.sv). Vilka artiklar som använts står i de två arternas filer i `../species/`.

## License for the comparison texts

The texts in this folder are written from the species' fact sheets, which are based on Swedish, English and German Wikipedia, and are shared under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). The articles used are listed in the two species' files in `../species/`.
```

Lägg till längst ned i rotens `LICENSE`, efter en tom rad:

```text
EXCEPTIONS

The species texts in website/src/data/species/ and the comparison texts in
website/src/data/comparisons/ are licensed under CC BY-SA 4.0, see the LICENSE.md
file in each folder. Photos and recordings in website/src/assets/species/ keep the
licenses credited in the species files and on each page.
```

- [ ] **Step 7: Kör testerna**

Run: `uv run pytest tests/test_web_record.py tests/test_web_report.py tests/test_web_run.py -v`
Expected: PASS (även fas 1:s körtest, som nu hämtar `WebPaths` via `run.py`).

- [ ] **Step 8: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/paths.py src/birdy_fetcher/web/record.py src/birdy_fetcher/web/run.py src/birdy_fetcher/web/report.py tests/test_web_record.py tests/test_web_report.py ../../website/src/data/species/LICENSE.md ../../website/src/data/comparisons/LICENSE.md ../../LICENSE
git commit -m "feat(pipeline): artposten som tillstånd, stegrapport och CC BY-SA för arttexterna"
```

---

### Task 12: Steg 1, `web sources`

**Files:**
- Create: `src/birdy_fetcher/web/sources_step.py`, `tests/web_repo.py`, `tests/test_web_sources_step.py`
- Modify: `src/birdy_fetcher/web/run.py` (flytta `SlugCollisionError` och `check_slug_collisions` till `sources_step.py`), `src/birdy_fetcher/cli.py`, `tests/test_cli_smoke.py`

`web` blir en kommandogrupp. Fas 1:s enkla skrivpass ligger kvar som `web v1` tills Task 24.

- [ ] **Step 1: Testrepot**

`tests/web_repo.py`:

```python
"""A tiny fake repo for the web step tests: species files, photos, groups, model maps and
the real prompts that exist so far."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

from birdy_fetcher.web.paths import WebPaths

from .test_web_source import _write

PIPELINE = Path(__file__).resolve().parents[1]
PROMPTS = ("web-v1", "facts-v1", "web-v2", "check-v1", "compare-v1")


def make_repo(
    tmp_path: Path,
    species: list[tuple[str, str, str]],
    *,
    photo: frozenset[str] = frozenset(),
    sound: frozenset[str] = frozenset(),
) -> WebPaths:
    paths = WebPaths(repo_root=tmp_path)
    for qid, sv, en in species:
        _write(paths.species_root, qid, sv, en, "approved")
        img = paths.asset_images / qid / "hero.webp"
        img.parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", (2000, 1000), (90, 110, 70)).save(img, "WEBP")
    paths.family_groups.parent.mkdir(parents=True, exist_ok=True)
    paths.family_groups.write_text(
        "order: [songbirds, other]\ngroups:\n"
        "  songbirds: {keyed_by: order, ioc_order: Passeriformes}\n"
        "  other: {families: [Cuculidae]}\n",
        encoding="utf-8",
    )
    base = {"name": {"sv": "X", "en": "X"}, "photo": species[0][0], "intro": {"sv": "a", "en": "a"}}
    paths.web_groups.parent.mkdir(parents=True, exist_ok=True)
    paths.web_groups.write_text(
        json.dumps(
            {
                "groups": [
                    {**base, "key": "songbirds", "slug": {"sv": "tattingar", "en": "songbirds"}},
                    {**base, "key": "other", "slug": {"sv": "ovriga-faglar", "en": "other-birds"}},
                ],
                "common": [species[0][0]],
            }
        ),
        encoding="utf-8",
    )
    paths.ml_dir.mkdir(parents=True, exist_ok=True)
    (paths.ml_dir / "aiy_to_qid.json").write_text(
        json.dumps({"mappings": {str(i): q for i, q in enumerate(sorted(photo))}}),
        encoding="utf-8",
    )
    (paths.ml_dir / "birdnet_lite_to_qid.json").write_text(
        json.dumps({"mapping": {str(i): q for i, q in enumerate(sorted(sound))}}),
        encoding="utf-8",
    )
    prompts = paths.pipeline_root / "prompts"
    prompts.mkdir(parents=True, exist_ok=True)
    for name in PROMPTS:
        src = PIPELINE / "prompts" / f"{name}.md"
        if src.exists():
            (prompts / f"{name}.md").write_text(src.read_text(encoding="utf-8"), encoding="utf-8")
    paths.banned.write_text("fascinerande\n", encoding="utf-8")
    return paths
```

- [ ] **Step 2: Skriv de fallerande testerna**

`tests/test_web_sources_step.py`:

```python
"""Tests for web/sources_step.py with fake Wikipedia, GBIF and Commons."""

from __future__ import annotations

import io
import math
import struct
import wave
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path

import pytest

from birdy_fetcher.web.audio import AudioCandidate
from birdy_fetcher.web.datamod import Counts
from birdy_fetcher.web.record import load_record, new_record, record_path, save_record
from birdy_fetcher.web.sources_step import (
    SlugCollisionError,
    SourceClients,
    SourcesOptions,
    run_sources,
)
from birdy_fetcher.web.wiki_full import WikiArticle

from .web_repo import make_repo

NOW = datetime(2026, 10, 2, tzinfo=UTC)
ARTICLES = {
    "sv": WikiArticle("sv", "Talgoxe", "1", "Talgoxen är cirka 14 centimeter lång."),
    "en": WikiArticle("en", "Great tit", "2", "The great tit is about 14 cm long."),
    "de": WikiArticle("de", "Kohlmeise", "3", "Die Kohlmeise ist etwa 14 cm lang."),
}
RECORDING = AudioCandidate(
    title="File:Parus major song.ogg",
    url="https://upload.wikimedia.org/a.ogg",
    page_url="https://commons.wikimedia.org/wiki/File:Parus_major_song.ogg",
    mime="application/ogg",
    duration=25.0,
    license="CC BY-SA 4.0",
    author="Anna",
    categories=("Category:Parus major",),
    from_wikidata=True,
)


def _wav(seconds: int) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(8000)
        w.writeframes(
            b"".join(struct.pack("<h", int(8000 * math.sin(i / 3))) for i in range(8000 * seconds))
        )
    return buf.getvalue()


@dataclass
class FakeWiki:
    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]:
        if qid == "Q9":
            raise RuntimeError("Wikipedia svarar inte")
        return ARTICLES


@dataclass
class FakeGbif:
    async def taxon_key(self, qid: str, scientific: str, *, refresh: bool = False) -> int | None:
        return 7

    async def counts(self, qid: str, taxon_key: int, *, refresh: bool = False) -> Counts:
        return Counts([10] * 12, {"SE-I": 50}, 1200)

    async def all_birds(self, *, refresh: bool = False) -> Counts:
        return Counts([100] * 12, {"SE-I": 100}, 99_000)

    async def swedish_red_list(
        self, qid: str, scientific: str, taxon_key: int, *, refresh: bool = False
    ) -> str | None:
        return "VU"


@dataclass
class FakeAudio:
    asked: list[str] = field(default_factory=list)

    async def candidates(
        self, qid: str, scientific: str, *, refresh: bool = False
    ) -> list[AudioCandidate]:
        self.asked.append(qid)
        return [RECORDING]

    async def download(
        self, qid: str, candidate: AudioCandidate, *, refresh: bool = False
    ) -> bytes:
        return _wav(3)


def _clients(audio: FakeAudio | None = None) -> SourceClients:
    return SourceClients(wiki=FakeWiki(), gbif=FakeGbif(), audio=audio or FakeAudio())


async def test_sources_write_a_pending_record_with_every_source(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")], sound=frozenset({"Q1"}))
    outcomes = await run_sources(paths, SourcesOptions(), clients=_clients(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["status"] == "pending"
    assert record["publish"] is False
    assert record["slug"] == {"sv": "talgoxe", "en": "great-tit"}
    assert record["swedishRedList"] == "VU"
    assert record["identifiable"] == {"photo": False, "sound": True}
    assert record["data"]["months"] == [100] * 12
    assert record["wikipedia"]["de"] == {"title": "Kohlmeise", "revision": "3"}
    assert record["audio"]["file"] == "Q1/voice.mp3"
    assert record["audio"]["trimmed"] is True
    assert (paths.images_out / "Q1" / "voice.mp3").exists()
    assert (paths.images_out / "Q1" / "hero.webp").exists()
    assert any(p.name.startswith("web-sources-") for p in paths.reports.iterdir())


async def test_rerun_keeps_facts_review_and_text(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    seeded["facts"] = [{"id": "f01"}]
    seeded["text"] = {"sv": {}}
    save_record(record_path(paths.data_out, "Q1"), seeded)
    await run_sources(paths, SourcesOptions(), clients=_clients(), now=NOW)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["facts"] == [{"id": "f01"}]
    assert record["text"] == {"sv": {}}


async def test_one_failing_species_does_not_stop_the_run(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit"), ("Q9", "Gök", "Cuckoo")])
    outcomes = await run_sources(paths, SourcesOptions(), clients=_clients(), now=NOW)
    assert {o.qid: o.status for o in outcomes} == {"Q1": "ok", "Q9": "failed"}
    assert load_record(record_path(paths.data_out, "Q9")) is None


async def test_dry_run_writes_nothing(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    outcomes = await run_sources(paths, SourcesOptions(dry_run=True), clients=_clients(), now=NOW)
    assert [o.status for o in outcomes] == ["dry-run"]
    assert not paths.data_out.exists()


async def test_reviewed_species_are_not_refreshed_without_force(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    seeded["review"] = {"facts": {"by": "Albin Abrahamsson", "at": "2026-11-01"}}
    save_record(record_path(paths.data_out, "Q1"), seeded)
    outcomes = await run_sources(paths, SourcesOptions(refresh=True), clients=_clients(), now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]


async def test_a_struck_recording_is_not_fetched_again(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    seeded["review"] = {"audioStruck": True}
    save_record(record_path(paths.data_out, "Q1"), seeded)
    audio = FakeAudio()
    await run_sources(paths, SourcesOptions(), clients=_clients(audio), now=NOW)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert "audio" not in record
    assert audio.asked == []


async def test_slug_collision_stops_before_any_request(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit"), ("Q2", "Talgoxe", "Tit")])
    with pytest.raises(SlugCollisionError):
        await run_sources(paths, SourcesOptions(), clients=_clients(), now=NOW)
```

- [ ] **Step 3: Kör och se dem falla**

Run: `uv run pytest tests/test_web_sources_step.py -v`
Expected: FAIL med `ModuleNotFoundError: No module named 'birdy_fetcher.web.sources_step'`.

- [ ] **Step 4: Skriv `sources_step.py`**

```python
"""Step 1 (spec 2026-09-25 §9.1): sources for each approved species, written into its record.
No model calls; everything here is free and cached under .cache/."""

from __future__ import annotations

import asyncio
from collections import Counter
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any, Protocol

from ..cache import Cache
from .audio import AudioCandidate, CommonsAudioClient, audio_record, choose, convert_to_mp3
from .datamod import MIN_REPORTS, Counts, build_data
from .gbif import GbifClient
from .groups import GroupTable
from .identify import ModelCoverage, load_coverage
from .images import ImageOut, prepare_images
from .paths import WebPaths
from .record import image_dict, is_reviewed, load_record, merge_sources, record_path, save_record
from .report import StepOutcome, render_step_report, write_step_report
from .slugs import slugify
from .source import SpeciesSource, load_approved
from .wiki_full import FullWikiClient, WikiArticle


class SlugCollisionError(ValueError):
    pass


def check_slug_collisions(sources: Sequence[SpeciesSource], groups: GroupTable) -> None:
    for lang in ("sv", "en"):
        slugs = [slugify(s.name_sv if lang == "sv" else s.name_en, lang) for s in sources]
        slugs += [g.slug_sv if lang == "sv" else g.slug_en for g in groups.groups]
        dupes = sorted(slug for slug, n in Counter(slugs).items() if n > 1)
        if dupes:
            raise SlugCollisionError(f"Samma adress används två gånger ({lang}): {dupes}")


class ArticleSource(Protocol):
    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]: ...


class GbifSource(Protocol):
    async def taxon_key(
        self, qid: str, scientific: str, *, refresh: bool = False
    ) -> int | None: ...

    async def counts(self, qid: str, taxon_key: int, *, refresh: bool = False) -> Counts: ...

    async def all_birds(self, *, refresh: bool = False) -> Counts: ...

    async def swedish_red_list(
        self, qid: str, scientific: str, taxon_key: int, *, refresh: bool = False
    ) -> str | None: ...


class AudioSource(Protocol):
    async def candidates(
        self, qid: str, scientific: str, *, refresh: bool = False
    ) -> list[AudioCandidate]: ...

    async def download(
        self, qid: str, candidate: AudioCandidate, *, refresh: bool = False
    ) -> bytes: ...


@dataclass
class SourceClients:
    wiki: ArticleSource
    gbif: GbifSource
    audio: AudioSource


@dataclass(frozen=True)
class SourcesOptions:
    qids: tuple[str, ...] = ()
    refresh: bool = False
    force: bool = False
    workers: int = 4
    dry_run: bool = False


def default_clients(cache: Cache) -> SourceClients:
    return SourceClients(
        wiki=FullWikiClient(cache=cache),
        gbif=GbifClient(cache=cache),
        audio=CommonsAudioClient(cache=cache),
    )


@dataclass(frozen=True)
class _Context:
    paths: WebPaths
    groups: GroupTable
    coverage: ModelCoverage
    clients: SourceClients
    all_birds: Counts
    options: SourcesOptions
    now: datetime


async def _data_and_red_list(
    source: SpeciesSource, ctx: _Context, notes: list[str]
) -> tuple[dict[str, Any] | None, str | None]:
    refresh = ctx.options.refresh
    taxon = await ctx.clients.gbif.taxon_key(source.qid, source.scientific_name, refresh=refresh)
    if taxon is None:
        notes.append("ingen exakt träff i GBIF: inga diagram och ingen svensk rödlista")
        return None, None
    counts = await ctx.clients.gbif.counts(source.qid, taxon, refresh=refresh)
    data = build_data(
        taxon_key=taxon, species=counts, all_birds=ctx.all_birds, fetched_at=ctx.now.isoformat()
    )
    if counts.total < MIN_REPORTS:
        notes.append(f"bara {counts.total} rapporter: inga diagram")
    red = await ctx.clients.gbif.swedish_red_list(
        source.qid, source.scientific_name, taxon, refresh=refresh
    )
    if red is None:
        notes.append("okänd kategori i Svenska rödlistan")
    return data, red


async def _audio(
    source: SpeciesSource, ctx: _Context, notes: list[str], *, skip: bool
) -> dict[str, Any] | None:
    voice = ctx.paths.images_out / source.qid / "voice.mp3"
    audio: dict[str, Any] | None = None
    if not skip:
        refresh = ctx.options.refresh
        candidates = await ctx.clients.audio.candidates(
            source.qid, source.scientific_name, refresh=refresh
        )
        chosen, rejected = choose(candidates, source.scientific_name)
        notes.extend(f"inspelning avvisad: {r}" for r in rejected[:5])
        if chosen is None:
            notes.append("ingen fri inspelning")
        elif not ctx.options.dry_run:
            raw = await ctx.clients.audio.download(source.qid, chosen, refresh=refresh)
            await asyncio.to_thread(convert_to_mp3, raw, voice)
            audio = audio_record(chosen, source.qid)
    if audio is None and not ctx.options.dry_run:
        voice.unlink(missing_ok=True)
    return audio


async def _collect(
    source: SpeciesSource, ctx: _Context, *, skip_audio: bool
) -> tuple[dict[str, Any], list[str]]:
    notes: list[str] = []
    articles = await ctx.clients.wiki.articles(source.qid, refresh=ctx.options.refresh)
    if not articles:
        notes.append("ingen Wikipediaartikel")
    data, red = await _data_and_red_list(source, ctx, notes)
    audio = await _audio(source, ctx, notes, skip=skip_audio)
    images: list[ImageOut] = []
    if not ctx.options.dry_run:
        images = await asyncio.to_thread(
            prepare_images,
            source,
            asset_images=ctx.paths.asset_images,
            out_root=ctx.paths.images_out,
        )
    has_marginalia = source.marginalia_sv or source.marginalia_en
    collected: dict[str, Any] = {
        "slug": {"sv": slugify(source.name_sv, "sv"), "en": slugify(source.name_en, "en")},
        "names": {"sv": source.name_sv, "en": source.name_en, "scientific": source.scientific_name},
        "family": {"latin": source.family, "sv": source.family_sv},
        "group": ctx.groups.group_for(family=source.family, ioc_order=source.ioc_order),
        "iucn": source.iucn,
        "swedishRedList": red,
        "identifiable": ctx.coverage.for_qid(source.qid),
        "marginalia": (
            {"sv": source.marginalia_sv, "en": source.marginalia_en} if has_marginalia else None
        ),
        "images": [image_dict(i) for i in images],
        "audio": audio,
        "wikipedia": {
            lang: {"title": a.title, "revision": a.revision} for lang, a in articles.items()
        },
        "data": data,
    }
    return collected, notes


async def run_sources(
    paths: WebPaths,
    options: SourcesOptions,
    *,
    clients: SourceClients | None = None,
    now: datetime | None = None,
) -> list[StepOutcome]:
    now = now or datetime.now(UTC)
    groups = GroupTable(paths.family_groups, paths.web_groups)
    all_approved = load_approved(paths.species_root)
    check_slug_collisions(all_approved, groups)
    sources = load_approved(paths.species_root, options.qids) if options.qids else all_approved
    clients = clients or default_clients(Cache(paths.pipeline_root / ".cache"))
    ctx = _Context(
        paths=paths,
        groups=groups,
        coverage=load_coverage(paths.ml_dir),
        clients=clients,
        all_birds=await clients.gbif.all_birds(refresh=options.refresh),
        options=options,
        now=now,
    )
    semaphore = asyncio.Semaphore(options.workers)

    async def one(source: SpeciesSource) -> StepOutcome:
        async with semaphore:
            path = record_path(paths.data_out, source.qid)
            try:
                existing = load_record(path)
                if existing and is_reviewed(existing) and options.refresh and not options.force:
                    return StepOutcome(
                        source.qid,
                        source.name_sv,
                        "skipped",
                        ["faktabladet är granskat: hämtas inte om utan --force"],
                    )
                skip_audio = bool(existing and existing.get("review", {}).get("audioStruck"))
                collected, notes = await _collect(source, ctx, skip_audio=skip_audio)
                if options.dry_run:
                    return StepOutcome(source.qid, source.name_sv, "dry-run", notes=notes)
                save_record(path, merge_sources(existing, source.qid, collected))
                return StepOutcome(source.qid, source.name_sv, "ok", notes=notes)
            except Exception as exc:  # one species' error must not stop the run
                return StepOutcome(
                    source.qid, source.name_sv, "failed", [f"{type(exc).__name__}: {exc}"]
                )

    outcomes = list(await asyncio.gather(*(one(s) for s in sources)))
    if not options.dry_run:
        report = render_step_report(title="Källor", date=now.date().isoformat(), outcomes=outcomes)
        write_step_report(paths.reports, "sources", now, report)
    return outcomes
```

I `run.py`: ta bort klassen `SlugCollisionError` och funktionen `check_slug_collisions` och importera dem i stället (formen `as` exporterar namnet vidare till `tests/test_web_run.py`):

```python
from .sources_step import SlugCollisionError as SlugCollisionError
from .sources_step import check_slug_collisions
```

Ta bort importer i `run.py` som blir oanvända (`Counter`, `Sequence`, `slugify`) om ruff säger det.

- [ ] **Step 5: Kör testerna**

Run: `uv run pytest tests/test_web_sources_step.py tests/test_web_run.py -v`
Expected: PASS

- [ ] **Step 6: Kommandogruppen i `cli.py`**

Ändra dekoratorn på den befintliga funktionen `web` från `@main.command()` till `@web.command("v1")` och döp om funktionen till `web_v1`. Lägg in gruppen och hjälpfunktionerna direkt före den:

```python
@main.group()
def web() -> None:
    """Artsidorna på birdy.community: källor, faktablad, granskning, text och jämförelser."""


def _web_paths() -> WebPaths:
    pipeline_root = Path(__file__).resolve().parent.parent.parent
    return WebPaths(repo_root=pipeline_root.parent.parent)


def _require_api_key() -> None:
    # The locked anthropic 0.97 SDK only reads ANTHROPIC_API_KEY / ANTHROPIC_AUTH_TOKEN, so
    # fail fast with a clear message instead of an opaque SDK error partway through a run.
    if not (os.environ.get("ANTHROPIC_API_KEY") or os.environ.get("ANTHROPIC_AUTH_TOKEN")):
        raise click.ClickException(
            "ANTHROPIC_API_KEY saknas. Lägg den i miljön eller i "
            "tools/content-pipeline/.env och kör med uv run --env-file .env ..."
        )


def _print_outcomes(outcomes: Sequence[StepOutcome], reports: Path) -> None:
    # Swedish and Polish author names and error text can contain characters or literal
    # `[...]` that would crash or mangle on a Windows console.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    console = Console(markup=False, highlight=False)
    for o in outcomes:
        if o.status not in ("ok", "pending"):
            console.print(f"{o.status:8} {o.name} ({o.qid}): {'; '.join(o.errors)}")
    counts = Counter(o.status for o in outcomes)
    console.print(f"Klart: {dict(counts)}. Rapporter i {reports}.")


@web.command("sources")
@click.option("--species", multiple=True, help="Q-ID(s). Utan flaggan körs alla granskade arter.")
@click.option("--refresh", is_flag=True, help="Hämta alla källor på nytt i stället för från cache.")
@click.option("--force", is_flag=True, help="Hämta om källor även för granskade faktablad.")
@click.option("--workers", type=click.IntRange(min=1), default=4)
@click.option("--dry-run", is_flag=True, help="Hämta och visa, men skriv inga filer.")
def web_sources(
    species: tuple[str, ...], refresh: bool, force: bool, workers: int, dry_run: bool
) -> None:
    """Steg 1: Wikipedia, Artportalen, rödlistan, inspelning och foton. Gratis."""
    from .web.sources_step import SourcesOptions, run_sources

    paths = _web_paths()
    options = SourcesOptions(
        qids=species, refresh=refresh, force=force, workers=workers, dry_run=dry_run
    )
    _print_outcomes(asyncio.run(run_sources(paths, options)), paths.reports)
```

Lägg till de importer som saknas överst i `cli.py`: `import os`, `import sys`, `from collections import Counter`, `from collections.abc import Sequence`, `from rich.console import Console`, `from .web.paths import WebPaths` och `from .web.report import StepOutcome`. Ta bort de lokala importerna av `os`, `sys`, `Counter`, `Console` och `WebPaths` inne i `web_v1` och låt den använda `_web_paths()` och `_require_api_key()` (behåll villkoret att `--dry-run` inte kräver nyckel).

- [ ] **Step 7: Uppdatera röktesterna**

I `tests/test_cli_smoke.py`: ändra `["web", "--help"]` till `["web", "v1", "--help"]`, `["web", "--dry-run", "--workers", "0", "--species", "Q1"]` till `["web", "v1", "--dry-run", "--workers", "0", "--species", "Q1"]` och `["web", "--species", "Q1", "--max-cost", "1"]` till `["web", "v1", "--species", "Q1", "--max-cost", "1"]`. Lägg till:

```python
def test_web_sources_help_lists_its_flags() -> None:
    runner = CliRunner()
    result = runner.invoke(main, ["web", "sources", "--help"])
    assert result.exit_code == 0
    for flag in ("--species", "--refresh", "--force", "--workers", "--dry-run"):
        assert flag in result.output
```

- [ ] **Step 8: Kör alla tester**

Run: `uv run pytest -q`
Expected: PASS

- [ ] **Step 9: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/sources_step.py src/birdy_fetcher/web/run.py src/birdy_fetcher/cli.py tests/web_repo.py tests/test_web_sources_step.py tests/test_cli_smoke.py
git commit -m "feat(pipeline): web sources hämtar Wikipedia, Artportalen, rödlistan och inspelningar"
```

---

### Task 13: Faktabladets modell, citatkontrollen och datafakta

**Files:**
- Create: `src/birdy_fetcher/web/facts.py`, `prompts/facts-v1.md`
- Test: `tests/test_web_facts.py`

- [ ] **Step 1: Skriv de fallerande testerna**

`tests/test_web_facts.py`:

```python
"""Tests for web/facts.py: quote checks, ids, status and the data facts."""

from __future__ import annotations

from birdy_fetcher.web.facts import (
    ArticleLang,
    FactSheetOutput,
    FactSource,
    ModelFact,
    ModelStatus,
    Topic,
    apply_facts,
    check_fact_sheet,
    data_facts,
)
from birdy_fetcher.web.record import new_record
from birdy_fetcher.web.wiki_full import WikiArticle

ARTICLES = {
    "sv": WikiArticle(
        "sv",
        "Talgoxe",
        "1",
        "Talgoxen är cirka 14 centimeter lång. Hanen har ett bredare svart band på buken än "
        "honan. Sången är ett ringande ti-ta ti-ta. Talgoxen lever i skog, parker och "
        "trädgårdar. Den är stannfågel i hela Sverige.",
    ),
    "de": WikiArticle(
        "de",
        "Kohlmeise",
        "3",
        "Die Kohlmeise kann mit der Blaumeise (Cyanistes caeruleus) verwechselt werden.",
    ),
}
INDEX = {"cyanistes caeruleus": "Q25404"}


def _fact(
    topic: Topic, sv: str, quote: str, lang: ArticleLang = "sv", other: str | None = None
) -> ModelFact:
    return ModelFact(
        topic=topic,
        sv=sv,
        sources=[FactSource(article=lang, quote=quote)],
        other_scientific=other,
    )


GOOD = [
    _fact("size", "Talgoxen är cirka 14 centimeter lång.", "Talgoxen är cirka 14 centimeter lång"),
    _fact("sex_age", "Hanen har bredare svart band på buken.", "Hanen har ett bredare svart band"),
    _fact("voice", "Sången är ett ringande ti-ta ti-ta.", "Sången är ett ringande ti-ta ti-ta"),
    _fact("habitat", "Lever i skog, parker och trädgårdar.", "lever i skog, parker och trädgårdar"),
    _fact("appearance", "Svart band på buken.", "ett bredare svart band på buken"),
    _fact(
        "lookalike",
        "Kan förväxlas med blåmesen.",
        "kann mit der Blaumeise (Cyanistes caeruleus) verwechselt",
        "de",
        "Cyanistes caeruleus",
    ),
]
STATUS = ModelStatus(
    value="resident", sources=[FactSource(article="sv", quote="Den är stannfågel i hela Sverige")]
)


def test_valid_facts_get_ids_and_lookalikes_get_their_qid() -> None:
    check = check_fact_sheet(FactSheetOutput(facts=GOOD, sweden_status=STATUS), ARTICLES, INDEX)
    assert [f["id"] for f in check.facts] == ["f01", "f02", "f03", "f04", "f05", "f06"]
    assert check.facts[5]["other"] == {"scientific": "Cyanistes caeruleus", "qid": "Q25404"}
    assert check.status is not None
    assert check.status["id"] == "s01"
    assert check.status["sv"] == "Stannfågel"
    assert check.fatal == []


def test_a_fact_with_an_invented_quote_is_struck() -> None:
    invented = _fact("appearance", "Har röd näbb.", "Talgoxen har en klarröd näbb")
    check = check_fact_sheet(FactSheetOutput(facts=[*GOOD, invented], sweden_status=None), ARTICLES, INDEX)
    assert len(check.facts) == len(GOOD)
    assert any("citatet finns inte" in n for n in check.notes)


def test_a_quote_from_an_article_that_is_missing_does_not_count() -> None:
    from_en = _fact("appearance", "Svart band.", "ett bredare svart band på buken", "en")
    check = check_fact_sheet(FactSheetOutput(facts=[from_en], sweden_status=None), ARTICLES, INDEX)
    assert check.facts == []


def test_a_lookalike_without_a_name_is_struck() -> None:
    nameless = _fact("lookalike", "Kan förväxlas.", "kann mit der Blaumeise (Cyanistes", "de")
    check = check_fact_sheet(FactSheetOutput(facts=[*GOOD, nameless], sweden_status=None), ARTICLES, INDEX)
    assert len(check.facts) == len(GOOD)


def test_missing_required_topics_are_fatal_and_too_few_facts_only_retried() -> None:
    check = check_fact_sheet(FactSheetOutput(facts=GOOD[:2], sweden_status=None), ARTICLES, INDEX)
    assert check.fatal == ["det saknas fakta om utseende, läte, miljö"]
    assert any("minst 10" in r for r in check.retry)


def test_data_facts_come_from_the_record() -> None:
    record = new_record("Q25485")
    record["data"] = {"sentences": {"sv": ["Rapporteras året runt.", "Vanligast i Skåne."]}}
    record["swedishRedList"] = "VU"
    assert data_facts(record) == [
        {"id": "d01", "topic": "data", "source": "artportalen", "sv": "Rapporteras året runt."},
        {"id": "d02", "topic": "data", "source": "artportalen", "sv": "Vanligast i Skåne."},
        {
            "id": "d03",
            "topic": "data",
            "source": "rodlistan",
            "sv": "Svenska rödlistan 2025: Sårbar (VU).",
        },
    ]
    record["swedishRedList"] = "not_listed"
    assert data_facts(record)[-1]["sv"] == "Inte rödlistad i Svenska rödlistan 2025."


def test_apply_facts_orders_facts_and_flags_a_status_contradiction() -> None:
    record = new_record("Q25485")
    record["text"] = {"sv": {}}
    record["review"] = {"statusConfirmed": True}
    record["data"] = {
        "months": [0, 0, 5, 60, 100, 90, 85, 70, 40, 5, 0, 0],
        "totalReports": 5000,
        "sentences": {"sv": ["Rapporteras mest i maj till juli."]},
        "statusSignal": {"contradicts": None},
    }
    check = check_fact_sheet(FactSheetOutput(facts=GOOD, sweden_status=STATUS), ARTICLES, INDEX)
    apply_facts(record, check, generated={"model": "claude-opus-5"})
    ids = [f["id"] for f in record["facts"]]
    assert ids == ["f01", "f02", "f03", "f04", "f05", "f06", "s01", "d01"]
    assert record["data"]["statusSignal"]["contradicts"] is not None
    assert record["text"] is None
    assert record["status"] == "pending"
    assert "statusConfirmed" not in record["review"]
    assert record["generated"]["facts"] == {"model": "claude-opus-5"}
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_facts.py -v`
Expected: FAIL med `ModuleNotFoundError`.

- [ ] **Step 3: Skriv `facts.py`**

```python
"""The fact sheet (spec 2026-09-25 §9.3): the model's answer, the code checks of every
quote, the status in Sweden and the data facts that code adds. Albin reviews what this
produces before any text is written from it."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Literal

from pydantic import BaseModel

from .checks import quote_in_sources
from .datamod import status_contradiction
from .record import Record
from .wiki_full import WikiArticle

Topic = Literal[
    "appearance",
    "sex_age",
    "size",
    "voice",
    "habitat",
    "sweden",
    "breeding",
    "food",
    "behaviour",
    "lookalike",
]
ArticleLang = Literal["sv", "en", "de"]
SwedenStatus = Literal[
    "resident", "breeding_migrant", "passage", "winter_visitor", "rare_visitor", "absent"
]


class FactSource(BaseModel):
    article: ArticleLang
    quote: str


class ModelFact(BaseModel):
    topic: Topic
    sv: str
    sources: list[FactSource]
    other_scientific: str | None


class ModelStatus(BaseModel):
    value: SwedenStatus
    sources: list[FactSource]


class FactSheetOutput(BaseModel):
    facts: list[ModelFact]
    sweden_status: ModelStatus | None


REQUIRED_TOPICS = ("appearance", "voice", "habitat")
MIN_FACTS = 10
MAX_FACTS = 30
MAX_FACT_WORDS = 30
TOPIC_SV = {
    "appearance": "utseende",
    "sex_age": "hane, hona och ungfågel",
    "size": "storlek",
    "voice": "läte",
    "habitat": "miljö",
    "sweden": "i Sverige",
    "breeding": "häckning",
    "food": "föda",
    "behaviour": "beteende",
    "lookalike": "förväxling",
    "status": "status i Sverige",
    "data": "data",
}
STATUS_SV = {
    "resident": "Stannfågel",
    "breeding_migrant": "Flyttfågel, häckar här",
    "passage": "Ses under flyttningen",
    "winter_visitor": "Vintergäst",
    "rare_visitor": "Sällsynt gäst",
    "absent": "Förekommer inte",
}
STATUS_BY_SV = {label: value for value, label in STATUS_SV.items()}
REDLIST_SV = {
    "RE": "Nationellt utdöd",
    "CR": "Akut hotad",
    "EN": "Starkt hotad",
    "VU": "Sårbar",
    "NT": "Nära hotad",
    "DD": "Kunskapsbrist",
}


@dataclass
class FactCheck:
    facts: list[dict[str, Any]] = field(default_factory=list)
    status: dict[str, Any] | None = None
    notes: list[str] = field(default_factory=list)
    fatal: list[str] = field(default_factory=list)
    retry: list[str] = field(default_factory=list)


def _valid_sources(
    sources: list[FactSource], articles: dict[str, WikiArticle]
) -> list[dict[str, str]]:
    return [
        {"article": s.article, "quote": s.quote}
        for s in sources
        if s.article in articles and quote_in_sources(s.quote, [articles[s.article].text])
    ]


def _entry(
    number: int, fact: ModelFact, articles: dict[str, WikiArticle], index: dict[str, str]
) -> tuple[dict[str, Any] | None, str | None]:
    sources = _valid_sources(fact.sources, articles)
    if not sources:
        return None, f"faktum {number} ströks, citatet finns inte i artikeln: {fact.sv}"
    if len(fact.sv.split()) > MAX_FACT_WORDS:
        return None, f"faktum {number} ströks, längre än {MAX_FACT_WORDS} ord: {fact.sv}"
    entry: dict[str, Any] = {"topic": fact.topic, "sv": fact.sv.strip(), "sources": sources}
    if fact.topic == "lookalike":
        if not fact.other_scientific:
            return None, f"faktum {number} ströks, förväxlingsarten saknar namn: {fact.sv}"
        other = {"scientific": fact.other_scientific.strip()}
        qid = index.get(other["scientific"].lower())
        if qid:
            other["qid"] = qid
        entry["other"] = other
    return entry, None


def check_fact_sheet(
    out: FactSheetOutput, articles: dict[str, WikiArticle], scientific_index: dict[str, str]
) -> FactCheck:
    check = FactCheck()
    kept: list[dict[str, Any]] = []
    for number, fact in enumerate(out.facts, start=1):
        entry, note = _entry(number, fact, articles, scientific_index)
        if note is not None:
            check.notes.append(note)
        if entry is not None:
            kept.append(entry)
    if len(kept) > MAX_FACTS:
        check.notes.append(f"{len(kept) - MAX_FACTS} fakta över gränsen {MAX_FACTS} ströks")
        kept = kept[:MAX_FACTS]
    check.facts = [{"id": f"f{i:02d}", **entry} for i, entry in enumerate(kept, start=1)]

    if out.sweden_status is not None:
        sources = _valid_sources(out.sweden_status.sources, articles)
        if sources:
            value = out.sweden_status.value
            check.status = {
                "id": "s01",
                "topic": "status",
                "value": value,
                "sv": STATUS_SV[value],
                "sources": sources,
            }
        else:
            check.notes.append("statusen ströks, citatet finns inte i artikeln")

    missing = [TOPIC_SV[t] for t in REQUIRED_TOPICS if not any(f["topic"] == t for f in check.facts)]
    if missing:
        check.fatal.append(f"det saknas fakta om {', '.join(missing)}")
    check.retry = list(check.fatal)
    if len(check.facts) < MIN_FACTS:
        check.retry.append(
            f"bara {len(check.facts)} fakta klarade kontrollen, minst {MIN_FACTS} behövs"
        )
    return check


def data_facts(record: Record) -> list[dict[str, Any]]:
    """Facts that code writes from the report data and the red list (never the model)."""
    out: list[dict[str, Any]] = []

    def add(source: str, text: str) -> None:
        out.append({"id": f"d{len(out) + 1:02d}", "topic": "data", "source": source, "sv": text})

    for sentence in (record.get("data") or {}).get("sentences", {}).get("sv", []):
        add("artportalen", sentence)
    red = record.get("swedishRedList")
    if red == "not_listed":
        add("rodlistan", "Inte rödlistad i Svenska rödlistan 2025.")
    elif red in REDLIST_SV:
        add("rodlistan", f"Svenska rödlistan 2025: {REDLIST_SV[red]} ({red}).")
    return out


def apply_facts(record: Record, check: FactCheck, *, generated: dict[str, Any]) -> None:
    """New facts make any earlier text stale: it is removed and the species is pending."""
    facts = list(check.facts)
    if check.status is not None:
        facts.append(check.status)
    facts.extend(data_facts(record))
    record["facts"] = facts
    data = record.get("data")
    if data is not None:
        reason = None
        if check.status is not None:
            reason = status_contradiction(
                check.status["value"], data.get("months"), int(data.get("totalReports", 0))
            )
        data["statusSignal"] = {"contradicts": reason}
    record.setdefault("generated", {})["facts"] = generated
    record["text"] = None
    record.pop("rejectedText", None)
    record["status"] = "failed" if check.fatal else "pending"
    record["errors"] = list(check.fatal)
    record.setdefault("review", {}).pop("statusConfirmed", None)
```

- [ ] **Step 4: Skriv prompten `prompts/facts-v1.md`**

```markdown
# facts prompt v1 (artsidor, spec 2026-09-25 reviderad 2026-10-01, avsnitt 9.3)

System: You extract facts about one bird species from Wikipedia articles, for the field guide pages on birdy.community. A person reviews every fact before anything is written from it, so precision matters more than coverage.

Rules:
- Use ONLY what the articles in the user message state. Never add knowledge from memory.
- Every fact needs at least one source: the article language (sv, en or de) and a quote of at least 20 characters copied character for character from that article. The quote must contain what the fact says. Copy it exactly as it stands, including dashes, brackets and punctuation.
- Write each fact in Swedish as one plain sentence of at most 30 words, without opinions. Use Swedish bird names.
- One fact, one thing. Keep features together when they describe one look ("svart huvud med vita kinder").
- Topics: appearance (plumage, bill, legs, shape), sex_age (differences between male, female and young birds), size (length, wingspan or weight with the numbers), voice (song and calls described so people can recognise them), habitat, sweden (occurrence, numbers, migration and time of year in Sweden or the Nordic countries), breeding, food, behaviour, lookalike (another species it can be confused with and what tells them apart).
- For lookalike, set other_scientific to the scientific name of the other species exactly as an article gives it. Only use lookalike when an article names the other species. For every other topic, set other_scientific to null.
- Give 10 to 30 facts, at least one each about appearance, voice and habitat. Prefer facts that help someone recognise the bird in Sweden.
- sweden_status: exactly one of resident, breeding_migrant, passage, winter_visitor, rare_visitor or absent, with a quote that supports it. Use resident only when an article says the bird stays in Sweden or the Nordic countries all year. If the articles do not support a status for Sweden, set sweden_status to null.

User: Species: {name_sv} (Swedish), {name_en} (English), scientific name {scientific_name}. Family: {family_sv} ({family}).

Swedish Wikipedia article:
<article lang="sv">
{article_sv}
</article>

English Wikipedia article:
<article lang="en">
{article_en}
</article>

German Wikipedia article:
<article lang="de">
{article_de}
</article>
```

- [ ] **Step 5: Kör testerna**

Run: `uv run pytest tests/test_web_facts.py -v`
Expected: PASS

- [ ] **Step 6: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/facts.py prompts/facts-v1.md tests/test_web_facts.py
git commit -m "feat(pipeline): faktablad med kontrollerade citat, status och datafakta"
```

---

### Task 14: Steg 2, `web facts`

**Files:**
- Create: `src/birdy_fetcher/web/facts_step.py`, `tests/web_fakes.py`, `tests/test_web_facts_step.py`
- Modify: `src/birdy_fetcher/cli.py`

- [ ] **Step 1: Den falska modellklienten (delas av Task 14, 20 och 22)**

`tests/web_fakes.py`:

```python
"""A fake JsonModelClient that answers from a queue and records what it was asked."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast

from anthropic.types import MessageParam
from pydantic import BaseModel

from birdy_fetcher.web.llm import MAX_TOKENS, ModelReply


def reply(parsed: BaseModel | None, stop: str = "end_turn") -> ModelReply[Any]:
    raw = parsed.model_dump_json() if parsed is not None else ""
    return ModelReply(parsed, raw, 1000, 500, stop)


@dataclass
class FakeJsonClient:
    replies: list[ModelReply[Any]]
    calls: list[list[MessageParam]] = field(default_factory=list)
    schemas: list[str] = field(default_factory=list)
    models: list[str] = field(default_factory=list)
    closed: bool = False

    async def complete[M: BaseModel](
        self,
        *,
        model: str,
        system: str,
        messages: list[MessageParam],
        effort: str,
        schema: type[M],
        max_tokens: int = MAX_TOKENS,
    ) -> ModelReply[M]:
        self.calls.append(list(messages))
        self.schemas.append(schema.__name__)
        self.models.append(model)
        return cast(ModelReply[M], self.replies.pop(0))

    async def aclose(self) -> None:
        self.closed = True
```

- [ ] **Step 2: Skriv de fallerande testerna**

`tests/test_web_facts_step.py`:

```python
"""Tests for web/facts_step.py: extraction with retry, cache, cost cap and the run."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from birdy_fetcher.web.facts import FactSheetOutput
from birdy_fetcher.web.facts_step import FactsOptions, run_facts
from birdy_fetcher.web.record import load_record, merge_sources, record_path, save_record
from birdy_fetcher.web.wiki_full import WikiArticle

from .test_web_facts import ARTICLES, GOOD, STATUS, _fact
from .web_fakes import FakeJsonClient, reply
from .web_repo import make_repo

NOW = datetime(2026, 10, 3, tzinfo=UTC)
TEN = GOOD + [
    _fact("appearance", f"Svart band på buken {i}.", "ett bredare svart band på buken")
    for i in range(4)
]
FULL = FactSheetOutput(facts=TEN, sweden_status=STATUS)
NO_VOICE = FactSheetOutput(facts=[f for f in TEN if f.topic != "voice"], sweden_status=STATUS)


@dataclass
class FakeWiki:
    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]:
        return ARTICLES


def _seed(paths_data_out: Path, qid: str) -> None:
    record = merge_sources(
        None,
        qid,
        {
            "names": {"sv": "Talgoxe", "en": "Great Tit", "scientific": "Parus major"},
            "wikipedia": {"sv": {"title": "Talgoxe", "revision": "1"}},
            "data": {"sentences": {"sv": ["Rapporteras året runt."]}, "statusSignal": {}},
            "swedishRedList": "not_listed",
        },
    )
    save_record(record_path(paths_data_out, qid), record)


async def test_facts_are_saved_and_the_species_stays_pending(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    client = FakeJsonClient([reply(FULL)])
    outcomes = await run_facts(paths, FactsOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["pending"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert [f["id"] for f in record["facts"]][-3:] == ["s01", "d01", "d02"]
    assert record["generated"]["facts"]["model"] == "claude-opus-5"
    assert record["generated"]["facts"]["prompt"] == "facts-v1"
    assert client.schemas == ["FactSheetOutput"]
    assert any(p.name.startswith("web-facts-") for p in paths.reports.iterdir())


async def test_a_missing_topic_is_retried_with_feedback(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    client = FakeJsonClient([reply(NO_VOICE), reply(FULL)])
    await run_facts(paths, FactsOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert len(client.calls) == 2
    feedback = str(client.calls[1][-1]["content"])
    assert "läte" in feedback


async def test_still_missing_after_the_retry_fails_the_species(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    client = FakeJsonClient([reply(NO_VOICE), reply(NO_VOICE)])
    outcomes = await run_facts(paths, FactsOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["status"] == "failed"
    assert "läte" in record["errors"][0]


async def test_a_reviewed_fact_sheet_is_left_alone(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    path = record_path(paths.data_out, "Q1")
    record = load_record(path)
    assert record is not None
    record["review"] = {"facts": {"by": "Albin Abrahamsson", "at": "2026-11-01"}}
    save_record(path, record)
    client = FakeJsonClient([])
    outcomes = await run_facts(paths, FactsOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]


async def test_a_second_run_uses_the_cache(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    await run_facts(paths, FactsOptions(), client=FakeJsonClient([reply(FULL)]), wiki=FakeWiki(), now=NOW)
    again = FakeJsonClient([])
    outcomes = await run_facts(paths, FactsOptions(), client=again, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["pending"]
    assert again.calls == []


async def test_the_cost_cap_skips_the_rest(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    client = FakeJsonClient([reply(FULL)])
    outcomes = await run_facts(
        paths, FactsOptions(max_cost=0.001), client=client, wiki=FakeWiki(), now=NOW
    )
    assert [o.status for o in outcomes] == ["skipped"]
    assert "kostnadstaket" in outcomes[0].errors[0]


async def test_a_species_without_sources_fails(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    outcomes = await run_facts(
        paths, FactsOptions(), client=FakeJsonClient([]), wiki=FakeWiki(), now=NOW
    )
    assert outcomes[0].status == "failed"
    assert "web sources" in outcomes[0].errors[0]
```

- [ ] **Step 3: Kör och se dem falla**

Run: `uv run pytest tests/test_web_facts_step.py -v`
Expected: FAIL med `ModuleNotFoundError`.

- [ ] **Step 4: Skriv `facts_step.py`**

```python
"""Step 2 (spec 2026-09-25 §9.3): the fact sheet for each species, with one retry and a
cache, so a rerun never pays twice for the same articles and prompt."""

from __future__ import annotations

import asyncio
import hashlib
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from anthropic.types import MessageParam

from ..cache import Cache
from ..claude_summarizer import _split_prompt
from ..cost import CostTracker, MaxCostExceeded
from .facts import FactCheck, FactSheetOutput, apply_facts, check_fact_sheet
from .llm import MODELS, AnthropicJsonClient, JsonModelClient, record_cost
from .paths import WebPaths
from .record import is_reviewed, load_record, record_path, save_record
from .report import StepOutcome, render_step_report, write_step_report
from .source import SpeciesSource, load_approved, load_scientific_index
from .sources_step import ArticleSource
from .wiki_full import FullWikiClient, WikiArticle

PROMPT_VERSION = "facts-v1"
ATTEMPTS = 2


class FactsFailed(RuntimeError):
    pass


def render_facts_prompt(
    template: str, source: SpeciesSource, articles: dict[str, WikiArticle]
) -> tuple[str, str]:
    def text(lang: str, missing: str) -> str:
        return articles[lang].text if lang in articles else missing

    return _split_prompt(
        template,
        name_sv=source.name_sv,
        name_en=source.name_en,
        scientific_name=source.scientific_name,
        family=source.family,
        family_sv=source.family_sv,
        article_sv=text("sv", "(no Swedish article)"),
        article_en=text("en", "(no English article)"),
        article_de=text("de", "(no German article)"),
    )


def facts_feedback(check: FactCheck) -> str:
    lines = "\n".join(f"- {item}" for item in [*check.retry, *check.notes[:10]])
    return (
        "Your answer broke these rules. Write the whole answer again with the same structure "
        "and fix them:\n" + lines + "\n"
        "Every quote must be copied character for character from the named article. Never "
        "invent or paraphrase a quote."
    )


def _better(new: FactCheck, old: FactCheck) -> bool:
    return (len(new.fatal), len(new.retry), -len(new.facts)) <= (
        len(old.fatal),
        len(old.retry),
        -len(old.facts),
    )


@dataclass
class FactExtractor:
    cache: Cache
    cost: CostTracker
    client: JsonModelClient
    prompt_path: Path
    scientific_index: dict[str, str]
    model_key: str = "opus"
    effort: str = "high"
    regenerate: bool = False

    def _cache_name(self, template: str, articles: dict[str, WikiArticle]) -> str:
        prompt_hash = hashlib.sha256(template.encode("utf-8")).hexdigest()[:8]
        revs = "-".join(f"{lang}{articles[lang].revision}" for lang in sorted(articles))
        return f"facts-{self.model_key}-{self.effort}-{prompt_hash}-{revs}.json"

    def _check(self, out: FactSheetOutput, articles: dict[str, WikiArticle]) -> FactCheck:
        return check_fact_sheet(out, articles, self.scientific_index)

    async def extract(
        self, source: SpeciesSource, articles: dict[str, WikiArticle]
    ) -> tuple[FactCheck, int, bool]:
        """(check, attempts, from_cache). Raises FactsFailed when no answer was usable and
        MaxCostExceeded when the cap is passed."""
        template = self.prompt_path.read_text(encoding="utf-8")
        name = self._cache_name(template, articles)
        cached = None if self.regenerate else self.cache.get(source.qid, name)
        if cached is not None:
            return self._check(FactSheetOutput.model_validate_json(cached), articles), 0, True

        system, user = render_facts_prompt(template, source, articles)
        messages: list[MessageParam] = [{"role": "user", "content": user}]
        best: tuple[FactSheetOutput, FactCheck] | None = None
        reason = "modellen gav inget svar"
        attempts = 0
        for attempt in range(1, ATTEMPTS + 1):
            attempts = attempt
            reply = await self.client.complete(
                model=MODELS[self.model_key],
                system=system,
                messages=messages,
                effort=self.effort,
                schema=FactSheetOutput,
            )
            check = self._check(reply.parsed, articles) if reply.parsed is not None else None
            try:
                record_cost(self.cost, self.model_key, reply)
            except MaxCostExceeded:
                if reply.parsed is not None and check is not None and not check.retry:
                    self.cache.put(source.qid, name, reply.parsed.model_dump_json(indent=2))
                raise
            if reply.parsed is None or check is None:
                reason = f"modellen gav inget giltigt svar (stop_reason={reply.stop_reason})"
                if reply.stop_reason in ("max_tokens", "refusal"):
                    break
                continue
            if best is None or _better(check, best[1]):
                best = (reply.parsed, check)
            if not check.retry:
                break
            if attempt < ATTEMPTS:
                messages = [
                    *messages,
                    {"role": "assistant", "content": reply.raw_text},
                    {"role": "user", "content": facts_feedback(check)},
                ]
        if best is None:
            raise FactsFailed(reason)
        self.cache.put(source.qid, name, best[0].model_dump_json(indent=2))
        return best[1], attempts, False


@dataclass(frozen=True)
class FactsOptions:
    qids: tuple[str, ...] = ()
    model_key: str = "opus"
    effort: str = "high"
    max_cost: float | None = None
    force: bool = False
    regenerate: bool = False
    workers: int = 4


async def run_facts(
    paths: WebPaths,
    options: FactsOptions,
    *,
    client: JsonModelClient | None = None,
    wiki: ArticleSource | None = None,
    now: datetime | None = None,
) -> list[StepOutcome]:
    now = now or datetime.now(UTC)
    cache = Cache(paths.pipeline_root / ".cache")
    sources = load_approved(paths.species_root, options.qids)
    wiki = wiki or FullWikiClient(cache=cache)
    owned = client is None
    model_client: JsonModelClient = client or AnthropicJsonClient()
    cost = CostTracker(max_usd=options.max_cost)
    extractor = FactExtractor(
        cache=cache,
        cost=cost,
        client=model_client,
        prompt_path=paths.prompt_file(PROMPT_VERSION),
        scientific_index=load_scientific_index(paths.species_root),
        model_key=options.model_key,
        effort=options.effort,
        regenerate=options.regenerate,
    )
    stop = asyncio.Event()
    semaphore = asyncio.Semaphore(options.workers)

    async def one(source: SpeciesSource) -> StepOutcome:
        async with semaphore:
            return await _one(source, paths, options, wiki, extractor, stop, now)

    try:
        outcomes = list(await asyncio.gather(*(one(s) for s in sources)))
    finally:
        if owned and isinstance(model_client, AnthropicJsonClient):
            await model_client.aclose()
    model_line = f"Modell: `{MODELS[options.model_key]}` (effort: {options.effort})."
    report = render_step_report(
        title="Faktablad",
        date=now.date().isoformat(),
        outcomes=outcomes,
        cost_usd=cost.total_usd,
        model_line=model_line,
    )
    write_step_report(paths.reports, "facts", now, report)
    return outcomes


async def _one(
    source: SpeciesSource,
    paths: WebPaths,
    options: FactsOptions,
    wiki: ArticleSource,
    extractor: FactExtractor,
    stop: asyncio.Event,
    now: datetime,
) -> StepOutcome:
    def out(status: str, errors: list[str] | None = None, notes: list[str] | None = None) -> StepOutcome:
        return StepOutcome(source.qid, source.name_sv, status, errors or [], notes or [])

    path = record_path(paths.data_out, source.qid)
    try:
        record = load_record(path)
        if record is None:
            return out("failed", ["artposten saknas: kör web sources först"])
        if is_reviewed(record) and not options.force:
            return out("skipped", ["faktabladet är granskat: körs inte om utan --force"])
        if stop.is_set():
            return out("skipped", ["kostnadstaket nåddes: körs vid nästa körning"])
        articles = await wiki.articles(source.qid)
        if not articles:
            return out("failed", ["ingen Wikipediaartikel"])
        try:
            check, attempts, cached = await extractor.extract(source, articles)
        except MaxCostExceeded as exc:
            stop.set()
            return out("skipped", [f"kostnadstaket nåddes: {exc}"])
        except FactsFailed as exc:
            record["status"] = "failed"
            record["errors"] = [str(exc)]
            save_record(path, record)
            return out("failed", [str(exc)])
        generated = {
            "model": MODELS[extractor.model_key],
            "prompt": PROMPT_VERSION,
            "effort": extractor.effort,
            "at": now.isoformat(),
        }
        if cached and record.get("generated", {}).get("facts"):
            generated = record["generated"]["facts"]
        apply_facts(record, check, generated=generated)
        save_record(path, record)
        notes = [*check.notes, *([] if attempts < 2 else ["två försök"])]
        if check.fatal:
            return out("failed", check.fatal, notes)
        return out("pending", [], [*notes, *check.retry])
    except Exception as exc:  # one species' error must not stop the run or overwrite a file
        return out("failed", [f"{type(exc).__name__}: {exc}"])
```

- [ ] **Step 5: Kommandot i `cli.py`**

Lägg till efter `web_sources`:

```python
@web.command("facts")
@click.option("--species", multiple=True, help="Q-ID(s). Utan flaggan körs alla granskade arter.")
@click.option("--model", "model_key", type=click.Choice(["opus", "sonnet"]), default="opus")
@click.option("--effort", type=click.Choice(["low", "medium", "high"]), default="high")
@click.option("--max-cost", type=float, default=None, help="Kostnadstak i USD för körningen.")
@click.option("--force", is_flag=True, help="Ta fram faktablad även för granskade arter.")
@click.option("--regenerate", is_flag=True, help="Fråga modellen igen trots cachat svar.")
@click.option("--workers", type=click.IntRange(min=1), default=4)
def web_facts(
    species: tuple[str, ...],
    model_key: str,
    effort: str,
    max_cost: float | None,
    force: bool,
    regenerate: bool,
    workers: int,
) -> None:
    """Steg 2: faktablad med citat ur Wikipedia. Kostar pengar."""
    from .web.facts_step import FactsOptions, run_facts

    _require_api_key()
    paths = _web_paths()
    options = FactsOptions(
        qids=species,
        model_key=model_key,
        effort=effort,
        max_cost=max_cost,
        force=force,
        regenerate=regenerate,
        workers=workers,
    )
    _print_outcomes(asyncio.run(run_facts(paths, options)), paths.reports)
```

Lägg till i `tests/test_cli_smoke.py`:

```python
def test_web_facts_requires_an_api_key(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_AUTH_TOKEN", raising=False)
    result = CliRunner().invoke(main, ["web", "facts", "--species", "Q1", "--max-cost", "1"])
    assert result.exit_code != 0
    assert "ANTHROPIC_API_KEY" in str(result.output)
```

- [ ] **Step 6: Kör testerna**

Run: `uv run pytest tests/test_web_facts_step.py tests/test_cli_smoke.py -v`
Expected: PASS

- [ ] **Step 7: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/facts_step.py src/birdy_fetcher/cli.py tests/web_fakes.py tests/test_web_facts_step.py tests/test_cli_smoke.py
git commit -m "feat(pipeline): web facts tar fram faktablad med ett omförsök och cache"
```

---

### Task 15: Vågorna, `web waves`

**Files:**
- Create: `src/birdy_fetcher/web/waves.py`
- Modify: `src/birdy_fetcher/cli.py`
- Test: `tests/test_web_waves.py`

Regel (spec §14): våg 1 är de tolv vanliga arterna i `species-groups.json` plus arterna som har flest rapporter december till februari, upp till 40. Våg 3 är flyttfåglarna (snitt 10 eller lägre i månadsdiagrammet december till februari). Våg 2 är resten. Listan sparas i `review/waves.json`. Albin justerar den i chatten, agenten ändrar filen och kör kommandot igen utan `--recompute`.

- [ ] **Step 1: Skriv de fallerande testerna**

`tests/test_web_waves.py`:

```python
"""Tests for web/waves.py: the wave lists and how they are applied."""

from __future__ import annotations

import json
from pathlib import Path

from birdy_fetcher.web.record import Record, load_record, new_record, record_path, save_record
from birdy_fetcher.web.waves import compute_waves, run_waves

from .web_repo import make_repo

RESIDENT = [70, 65, 60, 55, 70, 79, 64, 68, 74, 100, 66, 69]
MIGRANT = [0, 0, 5, 60, 100, 90, 85, 70, 40, 5, 0, 0]


def _record(qid: str, name: str, months: list[int] | None, winter: int) -> Record:
    record = new_record(qid)
    record["names"] = {"sv": name}
    if months is not None:
        record["data"] = {"months": months, "raw": {"speciesByMonth": [winter] + [0] * 11}}
    return record


def test_common_species_go_first_even_when_they_migrate() -> None:
    records = {
        "Q1": _record("Q1", "Trana", MIGRANT, 1),
        "Q2": _record("Q2", "Talgoxe", RESIDENT, 900),
        "Q3": _record("Q3", "Blåmes", RESIDENT, 800),
        "Q4": _record("Q4", "Koltrast", RESIDENT, 100),
        "Q5": _record("Q5", "Ladusvala", MIGRANT, 0),
        "Q6": _record("Q6", "Okänd", None, 0),
    }
    waves = compute_waves(records, common=["Q1"], size=3)
    assert waves[1] == ["Q1", "Q2", "Q3"]
    assert waves[3] == ["Q5"]
    assert waves[2] == ["Q4", "Q6"]


def test_run_waves_writes_the_file_once_and_applies_edits(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for qid, name, months, winter in (("Q1", "Talgoxe", RESIDENT, 900), ("Q5", "Ladusvala", MIGRANT, 0)):
        save_record(record_path(paths.data_out, qid), _record(qid, name, months, winter))
    waves = run_waves(paths, size=40, recompute=False)
    assert waves[1] == ["Q1"]
    assert waves[3] == ["Q5"]
    file = paths.review / "waves.json"
    data = json.loads(file.read_text(encoding="utf-8"))
    assert data["1"] == [{"qid": "Q1", "name": "Talgoxe"}]
    data["1"].append({"qid": "Q5", "name": "Ladusvala"})
    data["3"] = []
    file.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    run_waves(paths, size=40, recompute=False)
    record = load_record(record_path(paths.data_out, "Q5"))
    assert record is not None
    assert record["review"]["wave"] == 1
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_waves.py -v`
Expected: FAIL med `ModuleNotFoundError`.

- [ ] **Step 3: Skriv `waves.py`**

```python
"""Waves (spec 2026-09-25 §14): which species are reviewed, written and published together.
Publishing is added in Task 23."""

from __future__ import annotations

import json
from pathlib import Path

from .groups import GroupTable
from .paths import WebPaths
from .record import Record, load_all, record_path, save_record

WINTER = (11, 0, 1)
MIGRANT_WINTER_MAX = 10
WAVE_ONE_SIZE = 40
WAVES_FILE = "waves.json"


def winter_reports(record: Record) -> int:
    raw = (record.get("data") or {}).get("raw", {}).get("speciesByMonth")
    return sum(raw[i] for i in WINTER) if raw else 0


def is_migrant(record: Record) -> bool:
    months = (record.get("data") or {}).get("months")
    return bool(months) and sum(months[i] for i in WINTER) / len(WINTER) <= MIGRANT_WINTER_MAX


def _name(record: Record) -> str:
    return str(record.get("names", {}).get("sv", record["qid"]))


def compute_waves(
    records: dict[str, Record], common: list[str], size: int = WAVE_ONE_SIZE
) -> dict[int, list[str]]:
    first = [q for q in common if q in records]
    rest = [q for q in records if q not in first and not is_migrant(records[q])]
    rest.sort(key=lambda q: (-winter_reports(records[q]), _name(records[q])))
    first += rest[: max(0, size - len(first))]
    chosen = set(first)
    third = sorted(
        (q for q in records if q not in chosen and is_migrant(records[q])),
        key=lambda q: _name(records[q]),
    )
    second = sorted(
        (q for q in records if q not in chosen and q not in third),
        key=lambda q: _name(records[q]),
    )
    return {1: first, 2: second, 3: third}


def write_waves(path: Path, waves: dict[int, list[str]], records: dict[str, Record]) -> None:
    data = {
        str(n): [{"qid": q, "name": _name(records[q])} for q in qids] for n, qids in waves.items()
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def read_waves(path: Path) -> dict[int, list[str]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return {int(n): [entry["qid"] for entry in entries] for n, entries in data.items()}


def run_waves(paths: WebPaths, *, size: int, recompute: bool) -> dict[int, list[str]]:
    records = load_all(paths.data_out)
    file = paths.review / WAVES_FILE
    if file.exists() and not recompute:
        waves = read_waves(file)
    else:
        common = GroupTable(paths.family_groups, paths.web_groups).common
        waves = compute_waves(records, common, size)
        write_waves(file, waves, records)
    wave_of = {qid: n for n, qids in waves.items() for qid in qids}
    for qid, record in records.items():
        review = record.setdefault("review", {})
        if qid in wave_of:
            review["wave"] = wave_of[qid]
        else:
            review.pop("wave", None)
        save_record(record_path(paths.data_out, qid), record)
    return waves
```

- [ ] **Step 4: Kommandot i `cli.py`**

```python
@web.command("waves")
@click.option("--size", type=click.IntRange(min=12), default=40, help="Antal arter i våg 1.")
@click.option("--recompute", is_flag=True, help="Räkna om listan i stället för att läsa waves.json.")
def web_waves(size: int, recompute: bool) -> None:
    """Delar in arterna i tre vågor och skriver review/waves.json. Gratis."""
    from .web.waves import run_waves

    paths = _web_paths()
    waves = run_waves(paths, size=size, recompute=recompute)
    for number, qids in sorted(waves.items()):
        click.echo(f"Våg {number}: {len(qids)} arter")
    click.echo(f"Listan finns i {paths.review / 'waves.json'}.")
```

- [ ] **Step 5: Kör testerna**

Run: `uv run pytest tests/test_web_waves.py -v`
Expected: PASS

- [ ] **Step 6: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/waves.py src/birdy_fetcher/cli.py tests/test_web_waves.py
git commit -m "feat(pipeline): web waves delar in arterna i tre publiceringsvågor"
```

---

### Task 16: Granskningsarket ut, `web sheet`

**Files:**
- Create: `src/birdy_fetcher/web/review_sheet.py`
- Modify: `src/birdy_fetcher/cli.py`
- Test: `tests/test_web_review_sheet.py`

Arket följer bilaga E. Förväxlingsartens vetenskapliga namn står i kolumnen Ämne, så att Faktum bara innehåller själva faktumet och kan ändras fritt.

- [ ] **Step 1: Skriv de fallerande testerna**

`tests/test_web_review_sheet.py`:

```python
"""Tests for web/review_sheet.py: the review sheet out and Albin's decisions in."""

from __future__ import annotations

import csv
from pathlib import Path

from birdy_fetcher.web.record import Record, new_record
from birdy_fetcher.web.review_sheet import COLUMNS, sheet_rows, write_sheet


def _record() -> Record:
    record = new_record("Q25485")
    record["names"] = {"sv": "Talgoxe", "en": "Great Tit", "scientific": "Parus major"}
    record["wikipedia"] = {"sv": {"title": "Talgoxe", "revision": "111"}}
    record["facts"] = [
        {
            "id": "f01",
            "topic": "appearance",
            "sv": "Svart huvud med vita kinder.",
            "sources": [{"article": "sv", "quote": "svart huvud med vita kinder"}],
        },
        {
            "id": "f02",
            "topic": "lookalike",
            "sv": "Kan förväxlas med blåmesen.",
            "sources": [{"article": "sv", "quote": "kan förväxlas med blåmes"}],
            "other": {"scientific": "Cyanistes caeruleus", "qid": "Q25404"},
        },
        {
            "id": "s01",
            "topic": "status",
            "value": "resident",
            "sv": "Stannfågel",
            "sources": [{"article": "sv", "quote": "Den är stannfågel i hela Sverige"}],
        },
        {"id": "d01", "topic": "data", "source": "artportalen", "sv": "Rapporteras året runt."},
    ]
    record["audio"] = {
        "file": "Q25485/voice.mp3",
        "durationSec": 20,
        "trimmed": True,
        "author": "Anna",
        "license": "CC BY-SA 4.0",
        "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
        "sourceUrl": "https://commons.wikimedia.org/wiki/File:x.ogg",
    }
    record["data"] = {"statusSignal": {"contradicts": "Statusen säger stannfågel, men ..."}}
    return record


def test_rows_cover_facts_status_data_recording_and_flag() -> None:
    rows = sheet_rows(_record())
    assert [r["Typ"] for r in rows] == ["faktum", "faktum", "status", "data", "inspelning", "flagga"]
    first = rows[0]
    assert first["Art"] == "Talgoxe"
    assert first["Id"] == "f01"
    assert first["Ämne"] == "utseende"
    assert first["Källa"] == "sv: https://sv.wikipedia.org/w/index.php?oldid=111"
    assert first["Citat"] == "svart huvud med vita kinder"
    assert first["Beslut"] == "behåll"
    assert rows[1]["Ämne"] == "förväxling med Cyanistes caeruleus"
    assert rows[1]["Faktum"] == "Kan förväxlas med blåmesen."
    assert rows[3]["Beslut"] == "(data)"
    assert rows[4]["Faktum"] == "Anna, CC BY-SA 4.0, 20 s"
    assert rows[5]["Beslut"] == ""


def test_write_sheet_has_the_columns(tmp_path: Path) -> None:
    path = tmp_path / "wave-1-ark.csv"
    write_sheet(path, sheet_rows(_record()))
    with path.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        assert reader.fieldnames == COLUMNS
        assert len(list(reader)) == 6
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_review_sheet.py -v`
Expected: FAIL med `ModuleNotFoundError`.

- [ ] **Step 3: Skriv `review_sheet.py`**

```python
"""Albin's review sheet (spec 2026-09-25 §9.4 and appendix E): one CSV per wave, uploaded
to his Drive as a Google Sheet, exported back as CSV and imported."""

from __future__ import annotations

import csv
from pathlib import Path
from typing import Any

from .facts import TOPIC_SV
from .paths import WebPaths
from .record import Record, is_reviewed, load_all

COLUMNS = ["Art", "QID", "Typ", "Id", "Ämne", "Faktum", "Källa", "Citat", "Beslut", "Kommentar"]
KEEP = "behåll"
STRIKE = "stryk"
CHANGE = "ändra"
DATA_SOURCES = {"artportalen": "Artportalen via GBIF", "rodlistan": "Svenska rödlistan 2025"}


def revision_url(lang: str, revision: str) -> str:
    return f"https://{lang}.wikipedia.org/w/index.php?oldid={revision}"


def _sources(record: Record, sources: list[dict[str, str]]) -> tuple[str, str]:
    revisions = record.get("wikipedia", {})
    links = []
    for s in sources:
        lang = s["article"]
        rev = revisions.get(lang, {}).get("revision")
        links.append(f"{lang}: {revision_url(lang, rev)}" if rev else lang)
    return " | ".join(links), " | ".join(s["quote"] for s in sources)


def sheet_rows(record: Record) -> list[dict[str, str]]:
    name = str(record["names"]["sv"])
    qid = str(record["qid"])

    def row(typ: str, fid: str, topic: str, fact: str, source: str, quote: str, decision: str) -> dict[str, str]:
        return {
            "Art": name,
            "QID": qid,
            "Typ": typ,
            "Id": fid,
            "Ämne": topic,
            "Faktum": fact,
            "Källa": source,
            "Citat": quote,
            "Beslut": decision,
            "Kommentar": "",
        }

    rows: list[dict[str, str]] = []
    for fact in record.get("facts", []):
        topic = fact["topic"]
        if topic == "data":
            source = DATA_SOURCES[fact["source"]]
            rows.append(row("data", fact["id"], TOPIC_SV["data"], fact["sv"], source, "", "(data)"))
            continue
        source, quote = _sources(record, fact["sources"])
        label = TOPIC_SV[topic]
        if topic == "lookalike":
            label = f"förväxling med {fact['other']['scientific']}"
        typ = "status" if topic == "status" else "faktum"
        rows.append(row(typ, fact["id"], label, fact["sv"], source, quote, KEEP))
    audio: dict[str, Any] | None = record.get("audio")
    if audio:
        summary = f"{audio.get('author') or 'okänd'}, {audio['license']}, {audio['durationSec']} s"
        rows.append(row("inspelning", "a01", "inspelning", summary, audio["sourceUrl"], "", KEEP))
    reason = (record.get("data") or {}).get("statusSignal", {}).get("contradicts")
    if reason:
        rows.append(row("flagga", "s01", "status mot data", reason, DATA_SOURCES["artportalen"], "", ""))
    return rows


def write_sheet(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)


def export_wave(paths: WebPaths, wave: int) -> tuple[Path, int]:
    """Species in the wave that have facts and are not reviewed yet."""
    records = load_all(paths.data_out)
    chosen = [
        r
        for r in records.values()
        if r.get("review", {}).get("wave") == wave
        and not is_reviewed(r)
        and any(f["topic"] != "data" for f in r.get("facts", []))
    ]
    chosen.sort(key=lambda r: str(r["names"]["sv"]))
    path = paths.review / f"wave-{wave}-ark.csv"
    write_sheet(path, [row for r in chosen for row in sheet_rows(r)])
    return path, len(chosen)
```

- [ ] **Step 4: Kommandot i `cli.py`**

```python
@web.command("sheet")
@click.option("--wave", type=click.IntRange(1, 3), required=True)
def web_sheet(wave: int) -> None:
    """Skriver granskningsarket för en våg till review/wave-<n>-ark.csv. Gratis."""
    from .web.review_sheet import export_wave

    path, count = export_wave(_web_paths(), wave)
    click.echo(f"{count} arter i {path}. Ladda upp filen till Drive som Google-kalkylark.")
```

- [ ] **Step 5: Kör testerna**

Run: `uv run pytest tests/test_web_review_sheet.py -v`
Expected: PASS

- [ ] **Step 6: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/review_sheet.py src/birdy_fetcher/cli.py tests/test_web_review_sheet.py
git commit -m "feat(pipeline): web sheet skriver granskningsarket för en våg"
```

---

### Task 17: Albins beslut in, `web import`

**Files:**
- Modify: `src/birdy_fetcher/web/review_sheet.py` (lägg till), `src/birdy_fetcher/cli.py`
- Test: `tests/test_web_review_sheet.py` (lägg till)

Importen kontrollerar hela arket först och ändrar ingenting om något är fel: en rad som saknas för ett faktum, ett okänt beslut, `ändra` utan ny text, en status som inte är en av de sex etiketterna, eller en flagga utan `behåll` eller `stryk`.

- [ ] **Step 1: Skriv de fallerande testerna**

Lägg till i `tests/test_web_review_sheet.py` (importera `ReviewImportError`, `apply_review`, `read_sheet` från `birdy_fetcher.web.review_sheet` och `pytest`):

```python
def _decide(rows: list[dict[str, str]], **changes: tuple[str, str]) -> list[dict[str, str]]:
    """changes: Id -> (Beslut, Faktum)."""
    out = []
    for row in rows:
        row = dict(row)
        if row["Id"] in changes and row["Typ"] != "data":
            row["Beslut"], new_text = changes[row["Id"]]
            if new_text:
                row["Faktum"] = new_text
        out.append(row)
    return out


def test_keep_strike_change_and_flag() -> None:
    record = _record()
    rows = sheet_rows(record)
    rows = _decide(rows, f01=("ändra", "Svart huvud och vita kinder."), f02=("stryk", ""))
    rows = [r if r["Typ"] != "flagga" else {**r, "Beslut": "behåll"} for r in rows]
    result = apply_review({"Q25485": record}, rows, reviewer="Albin Abrahamsson", date="2026-11-20")
    assert result.changed == ["Q25485"]
    ids = [f["id"] for f in record["facts"]]
    assert ids == ["f01", "s01", "d01"]
    assert record["facts"][0]["sv"] == "Svart huvud och vita kinder."
    assert record["facts"][0]["edited"] is True
    assert record["review"]["statusConfirmed"] is True
    assert record["review"]["facts"] == {"by": "Albin Abrahamsson", "at": "2026-11-20"}


def test_status_can_be_changed_to_another_label() -> None:
    record = _record()
    rows = _decide(sheet_rows(record), s01=("ändra", "Vintergäst"))
    rows = [r if r["Typ"] != "flagga" else {**r, "Beslut": "behåll"} for r in rows]
    apply_review({"Q25485": record}, rows, reviewer="A", date="2026-11-20")
    status = next(f for f in record["facts"] if f["id"] == "s01")
    assert status["value"] == "winter_visitor"
    assert status["sv"] == "Vintergäst"


def test_striking_the_flag_removes_the_status_and_striking_audio_removes_it() -> None:
    record = _record()
    rows = sheet_rows(record)
    rows = [
        {**r, "Beslut": "stryk"} if r["Typ"] in ("flagga", "inspelning") else r for r in rows
    ]
    result = apply_review({"Q25485": record}, rows, reviewer="A", date="2026-11-20")
    assert all(f["id"] != "s01" for f in record["facts"])
    assert record["review"]["statusConfirmed"] is False
    assert "audio" not in record
    assert record["review"]["audioStruck"] is True
    assert result.removed_audio == ["Q25485"]


def test_errors_stop_the_whole_import() -> None:
    record = _record()
    rows = [r for r in sheet_rows(record) if r["Id"] != "f02"]
    rows = [{**r, "Beslut": "kanske"} if r["Id"] == "f01" else r for r in rows]
    with pytest.raises(ReviewImportError) as error:
        apply_review({"Q25485": record}, rows, reviewer="A", date="2026-11-20")
    message = str(error.value)
    assert "f02" in message
    assert "kanske" in message
    assert "flaggan" in message
    assert "review" not in record or "facts" not in record["review"]


def test_read_sheet_accepts_a_bom(tmp_path: Path) -> None:
    path = tmp_path / "wave-1.csv"
    write_sheet(path, sheet_rows(_record()))
    path.write_bytes(b"\xef\xbb\xbf" + path.read_bytes())
    assert read_sheet(path)[0]["Art"] == "Talgoxe"
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_review_sheet.py -v`
Expected: FAIL med `ImportError`.

- [ ] **Step 3: Implementera**

Lägg till i `review_sheet.py` (och `from dataclasses import dataclass, field` samt `from .facts import STATUS_BY_SV, STATUS_SV, TOPIC_SV` och `from .record import record_path, save_record` bland importerna):

```python
class ReviewImportError(ValueError):
    pass


@dataclass
class ImportResult:
    changed: list[str] = field(default_factory=list)
    removed_audio: list[str] = field(default_factory=list)


def read_sheet(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as f:
        return [{k: (v or "") for k, v in row.items()} for row in csv.DictReader(f)]


def _decision(row: dict[str, str]) -> str:
    return row.get("Beslut", "").strip().lower()


def _validate(records: dict[str, Record], by_qid: dict[str, list[dict[str, str]]]) -> list[str]:
    errors: list[str] = []
    for qid, rows in by_qid.items():
        record = records.get(qid)
        if record is None:
            errors.append(f"{qid}: arten finns inte bland artfilerna")
            continue
        expected = {f["id"] for f in record.get("facts", []) if f["topic"] != "data"}
        seen = {r["Id"].strip() for r in rows if r["Typ"].strip() in ("faktum", "status")}
        missing = sorted(expected - seen)
        if missing:
            errors.append(f"{qid}: raderna {', '.join(missing)} saknas i arket")
        for r in rows:
            typ, decision, fid = r["Typ"].strip(), _decision(r), r["Id"].strip()
            if typ == "data":
                continue
            if typ == "flagga":
                if decision not in (KEEP, STRIKE):
                    errors.append(f"{qid}: skriv behåll eller stryk på flaggan")
                continue
            if decision not in (KEEP, STRIKE, CHANGE):
                errors.append(f"{qid} {fid}: okänt beslut {r['Beslut']!r}")
            elif decision == CHANGE and not r["Faktum"].strip():
                errors.append(f"{qid} {fid}: ändra kräver en ny text i Faktum")
            elif typ == "status" and decision == CHANGE and r["Faktum"].strip() not in STATUS_BY_SV:
                labels = ", ".join(STATUS_SV.values())
                errors.append(f"{qid} {fid}: skriv en av {labels}")
    return errors


def _apply_one(record: Record, rows: list[dict[str, str]], result: ImportResult) -> None:
    decisions = {r["Id"].strip(): r for r in rows if r["Typ"].strip() in ("faktum", "status")}
    facts: list[dict[str, Any]] = []
    for fact in record.get("facts", []):
        if fact["topic"] == "data":
            facts.append(fact)
            continue
        row = decisions[fact["id"]]
        decision = _decision(row)
        if decision == STRIKE:
            continue
        if decision == CHANGE:
            text = row["Faktum"].strip()
            if fact["topic"] == "status":
                fact = {**fact, "value": STATUS_BY_SV[text], "sv": text, "edited": True}
            elif text != fact["sv"]:
                fact = {**fact, "sv": text, "edited": True}
        facts.append(fact)
    review = record.setdefault("review", {})
    for row in rows:
        typ, decision = row["Typ"].strip(), _decision(row)
        if typ == "flagga":
            review["statusConfirmed"] = decision == KEEP
            if decision == STRIKE:
                facts = [f for f in facts if f["topic"] != "status"]
        elif typ == "inspelning" and decision == STRIKE:
            record.pop("audio", None)
            review["audioStruck"] = True
            result.removed_audio.append(str(record["qid"]))
    record["facts"] = facts


def apply_review(
    records: dict[str, Record], rows: list[dict[str, str]], *, reviewer: str, date: str
) -> ImportResult:
    by_qid: dict[str, list[dict[str, str]]] = {}
    for row in rows:
        by_qid.setdefault(row["QID"].strip(), []).append(row)
    errors = _validate(records, by_qid)
    if errors:
        raise ReviewImportError("\n".join(errors))
    result = ImportResult()
    for qid, qrows in by_qid.items():
        record = records[qid]
        _apply_one(record, qrows, result)
        record["review"]["facts"] = {"by": reviewer, "at": date}
        result.changed.append(qid)
    return result


def import_wave(paths: WebPaths, sheet: Path, *, reviewer: str, date: str) -> ImportResult:
    records = load_all(paths.data_out)
    result = apply_review(records, read_sheet(sheet), reviewer=reviewer, date=date)
    for qid in result.changed:
        save_record(record_path(paths.data_out, qid), records[qid])
    for qid in result.removed_audio:
        (paths.images_out / qid / "voice.mp3").unlink(missing_ok=True)
    return result
```

- [ ] **Step 4: Kommandot i `cli.py`**

```python
@web.command("import")
@click.option("--wave", type=click.IntRange(1, 3), required=True)
@click.option(
    "--file", "sheet", type=click.Path(exists=True, dir_okay=False, path_type=Path), default=None,
    help="Exporterad CSV. Standard: review/wave-<n>.csv.",
)
@click.option("--reviewer", default="Albin Abrahamsson", show_default=True)
@click.option("--date", "review_date", default=None, help="Granskningsdatum, YYYY-MM-DD. Standard: i dag.")
def web_import(wave: int, sheet: Path | None, reviewer: str, review_date: str | None) -> None:
    """Läser in Albins beslut ur granskningsarket. Ändrar ingenting om något är fel."""
    from datetime import date

    from .web.review_sheet import ReviewImportError, import_wave

    paths = _web_paths()
    path = sheet or paths.review / f"wave-{wave}.csv"
    when = review_date or date.today().isoformat()
    try:
        result = import_wave(paths, path, reviewer=reviewer, date=when)
    except ReviewImportError as exc:
        raise click.ClickException(f"Arket har fel, inget ändrades:\n{exc}") from exc
    click.echo(f"{len(result.changed)} arter granskade {when}. Inspelningar strukna: {len(result.removed_audio)}.")
```

- [ ] **Step 5: Kör testerna**

Run: `uv run pytest tests/test_web_review_sheet.py -v`
Expected: PASS

- [ ] **Step 6: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/review_sheet.py src/birdy_fetcher/cli.py tests/test_web_review_sheet.py
git commit -m "feat(pipeline): web import läser in Albins beslut ur granskningsarket"
```

---

### Task 18: Textens modell och kodkontrollerna

**Files:**
- Create: `src/birdy_fetcher/web/text_model.py`, `src/birdy_fetcher/web/text_checks.py`
- Create: `tests/text_fixtures.py`
- Test: `tests/test_web_text_checks.py`

Kontrollerna följer spec §9.6 regel 1 till 7. En överträdelse i en enskild mening, punkt, förväxlingspost eller storleken är `removable`: den delen kan tas bort. En överträdelse på fältnivå (fel antal punkter, för många ord i ett helt fält, meta description) är det inte.

- [ ] **Step 1: Testdata som Task 18, 19 och 20 delar**

`tests/text_fixtures.py`:

```python
"""A reviewed talgoxe record and a text that passes every code check."""

from __future__ import annotations

from typing import Any

from birdy_fetcher.web.record import Record, new_record
from birdy_fetcher.web.text_model import LangTextV2, LookAlikeText, Sentence, SizeText, WebTextV2


def S(text: str, *ids: str) -> Sentence:  # noqa: N802 -- short constructor for test data
    return Sentence(text=text, fact_ids=list(ids))


FACTS: list[dict[str, Any]] = [
    {"id": "f01", "topic": "appearance", "sv": "Talgoxen har svart huvud med vita kinder.",
     "sources": [{"article": "sv", "quote": "svart huvud med vita kinder"}]},
    {"id": "f02", "topic": "appearance", "sv": "Buken är gul med ett svart band längs mitten.",
     "sources": [{"article": "sv", "quote": "gul buk med ett svart band"}]},
    {"id": "f03", "topic": "size", "sv": "Talgoxen är cirka 14 centimeter lång.",
     "sources": [{"article": "sv", "quote": "cirka 14 centimeter lång"}]},
    {"id": "f04", "topic": "voice", "sv": "Sången är ett ringande ti ta, ti ta.",
     "sources": [{"article": "sv", "quote": "ett ringande ti ta, ti ta"}]},
    {"id": "f05", "topic": "habitat", "sv": "Talgoxen lever i skog, parker och trädgårdar.",
     "sources": [{"article": "sv", "quote": "i skog, parker och trädgårdar"}]},
    {"id": "f06", "topic": "lookalike", "sv": "Blåmesen är mindre och har blå hätta.",
     "sources": [{"article": "sv", "quote": "blåmesen är mindre och har blå hätta"}],
     "other": {"scientific": "Cyanistes caeruleus", "qid": "Q25404"}},
    {"id": "s01", "topic": "status", "value": "resident", "sv": "Stannfågel",
     "sources": [{"article": "sv", "quote": "Den är stannfågel i hela Sverige"}]},
    {"id": "d01", "topic": "data", "source": "artportalen", "sv": "Rapporteras året runt."},
]  # fmt: skip

SV = LangTextV2(
    lead=[
        S("Talgoxen är en vanlig fågel i skog, parker och trädgårdar.", "f05"),
        S("Den har svart huvud med vita kinder.", "f01"),
    ],
    field_marks=[
        S("Svart huvud med vita kinder", "f01"),
        S("Gul buk med ett svart band längs mitten", "f02"),
        S("Cirka 14 centimeter lång", "f03"),
    ],
    voice=[S("Sången är ett ringande ti ta, ti ta.", "f04")],
    where_when=[
        S("Stannfågel som rapporteras året runt i skog, parker och trädgårdar.", "s01", "d01", "f05")
    ],
    behaviour=[],
    look_alikes=[
        LookAlikeText(other="Q25404", sentences=[S("Blåmesen är mindre och har blå hätta.", "f06")])
    ],
    meta_description=(
        "Talgoxe: så känner du igen den på svart huvud och gul buk, hur sången låter och var och "
        "när den ses i Sverige under hela året."
    ),
    size=SizeText(value="Cirka 14 cm", fact_ids=["f03"]),
)
EN = LangTextV2(
    lead=[
        S("The great tit is a common bird of woodland, parks and gardens.", "f05"),
        S("It has a black head with white cheeks.", "f01"),
    ],
    field_marks=[
        S("Black head with white cheeks", "f01"),
        S("Yellow belly with a black stripe down the middle", "f02"),
        S("About 14 centimetres long", "f03"),
    ],
    voice=[S("The song is a ringing tee cha, tee cha.", "f04")],
    where_when=[
        S("A resident that is reported all year in woodland, parks and gardens.", "s01", "d01", "f05")
    ],
    behaviour=[],
    look_alikes=[
        LookAlikeText(other="Q25404", sentences=[S("The blue tit is smaller with a blue cap.", "f06")])
    ],
    meta_description=(
        "Great tit: how to recognise it by its black head and yellow belly, what its song sounds "
        "like and when it is seen in Sweden."
    ),
    size=SizeText(value="About 14 cm", fact_ids=["f03"]),
)
VALID = WebTextV2(sv=SV, en=EN)
BANNED = ["fascinerande", "remarkable"]


def reviewed_record(qid: str = "Q25485") -> Record:
    record = new_record(qid)
    record["names"] = {"sv": "Talgoxe", "en": "Great Tit", "scientific": "Parus major"}
    record["family"] = {"latin": "Paridae", "sv": "Mesar"}
    record["group"] = "songbirds"
    record["facts"] = [dict(f) for f in FACTS]
    record["data"] = {"statusSignal": {"contradicts": None}}
    record["review"] = {"facts": {"by": "Albin Abrahamsson", "at": "2026-11-20"}, "wave": 1}
    return record
```

- [ ] **Step 2: Skriv de fallerande testerna**

`tests/test_web_text_checks.py`:

```python
"""Tests for web/text_checks.py and the helpers in web/text_model.py."""

from __future__ import annotations

from birdy_fetcher.web.text_checks import (
    TextContext,
    check_text,
    minimum_problems,
    numbers,
    settle,
)
from birdy_fetcher.web.text_model import WebTextV2, remove_paths, status_for_site, to_site

from .text_fixtures import BANNED, EN, FACTS, SV, VALID, S, reviewed_record

CTX = TextContext.from_facts(FACTS)


def _with_sv(**changes: object) -> WebTextV2:
    return WebTextV2(sv=SV.model_copy(update=changes), en=EN)


def test_the_valid_text_has_no_issues() -> None:
    assert check_text(VALID, CTX, BANNED) == []


def test_numbers() -> None:
    assert numbers("Cirka 14 cm och 2,5 kg") == {"14", "2.5"}


def test_a_number_that_the_facts_do_not_give_is_removable() -> None:
    text = _with_sv(voice=[S("Sången hörs från 3 meters håll.", "f04")])
    issues = check_text(text, CTX, BANNED)
    assert [(i.path, i.removable) for i in issues] == [("sv.voice[0]", True)]
    assert "talet 3" in issues[0].message


def test_unknown_or_missing_fact_ids() -> None:
    text = _with_sv(voice=[S("Sången är ett ringande ti ta, ti ta.", "f99")])
    assert "f99" in check_text(text, CTX, BANNED)[0].message
    bare = _with_sv(voice=[S("Sången är ett ringande ti ta, ti ta.")])
    assert check_text(bare, CTX, BANNED)[0].message == "anger inga fakta"


def test_style_rules_apply_to_every_sentence() -> None:
    text = _with_sv(voice=[S("Sången är fascinerande!", "f04")])
    messages = [i.message for i in check_text(text, CTX, BANNED)]
    assert any("utropstecken" in m for m in messages)
    assert any("fascinerande" in m for m in messages)


def test_field_rules_are_not_removable() -> None:
    text = _with_sv(field_marks=SV.field_marks[:2], meta_description="För kort.")
    issues = {i.path: i.removable for i in check_text(text, CTX, BANNED)}
    assert issues == {"sv.field_marks": False, "sv.meta_description": False}


def test_a_look_alike_that_is_not_in_the_facts() -> None:
    from birdy_fetcher.web.text_model import LookAlikeText

    text = _with_sv(
        look_alikes=[LookAlikeText(other="Q1", sentences=[S("Mindre och blå.", "f06")])]
    )
    issues = check_text(text, CTX, BANNED)
    assert [(i.path, i.removable) for i in issues] == [("sv.look_alikes[0]", True)]


def test_size_numbers_must_come_from_the_cited_fact() -> None:
    from birdy_fetcher.web.text_model import SizeText

    text = _with_sv(size=SizeText(value="Cirka 16 cm", fact_ids=["f03"]))
    assert [i.path for i in check_text(text, CTX, BANNED)] == ["sv.size"]


def test_settle_removes_what_it_can_and_reports_the_rest() -> None:
    text = _with_sv(lead=[*SV.lead[:1], S("Den väger 99 gram.", "f01")])
    settled, notes, hard = settle(text, CTX, BANNED)
    assert len(settled.sv.lead) == 1
    assert notes and "sv.lead[1]" in notes[0]
    assert hard == []


def test_remove_paths_and_the_minimum() -> None:
    text = remove_paths(VALID, {"sv.voice[0]", "en.look_alikes[0].sentences[0]", "en.size"})
    assert text.sv.voice == []
    assert text.en.look_alikes == []
    assert text.en.size is None
    assert minimum_problems(text) == ["sv.voice saknas"]


def test_status_for_site_respects_an_unconfirmed_flag() -> None:
    record = reviewed_record()
    assert status_for_site(record) == {"value": "resident", "factIds": ["s01"]}
    record["data"]["statusSignal"]["contradicts"] = "Statusen säger stannfågel, men ..."
    assert status_for_site(record) is None
    record["review"]["statusConfirmed"] = True
    assert status_for_site(record) == {"value": "resident", "factIds": ["s01"]}


def test_to_site_uses_the_site_field_names() -> None:
    site = to_site(SV, {"value": "resident", "factIds": ["s01"]})
    assert site["lead"][0] == {
        "text": "Talgoxen är en vanlig fågel i skog, parker och trädgårdar.",
        "factIds": ["f05"],
    }
    assert site["lookAlikes"][0]["other"] == "Q25404"
    assert site["facts"]["size"] == {"value": "Cirka 14 cm", "factIds": ["f03"]}
    assert site["facts"]["swedenStatus"]["value"] == "resident"
    assert set(site) == {
        "lead", "fieldMarks", "voice", "whereWhen", "behaviour", "lookAlikes",
        "metaDescription", "facts",
    }  # fmt: skip
```

- [ ] **Step 3: Kör och se dem falla**

Run: `uv run pytest tests/test_web_text_checks.py -v`
Expected: FAIL med `ModuleNotFoundError`.

- [ ] **Step 4: Skriv `text_model.py`**

```python
"""The page text (spec 2026-09-25 §9.5): every sentence carries the ids of the facts it is
built on. Also the conversion to the site's field names (appendix C) and removal of parts
that did not pass a check."""

from __future__ import annotations

import re
from collections.abc import Iterator
from typing import Any

from pydantic import BaseModel

from .record import Record


class Sentence(BaseModel):
    text: str
    fact_ids: list[str]


class LookAlikeText(BaseModel):
    other: str
    sentences: list[Sentence]


class SizeText(BaseModel):
    value: str
    fact_ids: list[str]


class LangTextV2(BaseModel):
    lead: list[Sentence]
    field_marks: list[Sentence]
    voice: list[Sentence]
    where_when: list[Sentence]
    behaviour: list[Sentence]
    look_alikes: list[LookAlikeText]
    meta_description: str
    size: SizeText | None


class WebTextV2(BaseModel):
    sv: LangTextV2
    en: LangTextV2


SENTENCE_FIELDS = ("lead", "field_marks", "voice", "where_when", "behaviour")


def iter_sentences(text: LangTextV2) -> Iterator[tuple[str, Sentence]]:
    for name in SENTENCE_FIELDS:
        for i, sentence in enumerate(getattr(text, name)):
            yield f"{name}[{i}]", sentence
    for i, look_alike in enumerate(text.look_alikes):
        for j, sentence in enumerate(look_alike.sentences):
            yield f"look_alikes[{i}].sentences[{j}]", sentence


_PATH = re.compile(
    r"^(sv|en)\.(lead|field_marks|voice|where_when|behaviour|look_alikes|size)"
    r"(?:\[(\d+)\])?(?:\.sentences\[(\d+)\])?$"
)


def remove_paths(text: WebTextV2, paths: set[str]) -> WebTextV2:
    """A copy without the sentences, items, look-alikes or sizes named by `paths`. A
    look-alike that loses all its sentences is dropped too."""
    data = text.model_dump()
    drops: dict[tuple[str, str], set[int]] = {}
    nested: dict[tuple[str, int], set[int]] = {}
    for path in paths:
        match = _PATH.match(path)
        if match is None:
            continue
        lang, name, index, inner = match.groups()
        if name == "size":
            data[lang]["size"] = None
        elif name == "look_alikes" and inner is not None:
            nested.setdefault((lang, int(index)), set()).add(int(inner))
        elif index is not None:
            drops.setdefault((lang, name), set()).add(int(index))
    for (lang, i), inner_indexes in nested.items():
        sentences = data[lang]["look_alikes"][i]["sentences"]
        data[lang]["look_alikes"][i]["sentences"] = [
            s for k, s in enumerate(sentences) if k not in inner_indexes
        ]
    for (lang, name), indexes in drops.items():
        data[lang][name] = [x for k, x in enumerate(data[lang][name]) if k not in indexes]
    for lang in ("sv", "en"):
        data[lang]["look_alikes"] = [la for la in data[lang]["look_alikes"] if la["sentences"]]
    return WebTextV2.model_validate(data)


def status_for_site(record: Record) -> dict[str, Any] | None:
    """The reviewed status, unless the data contradicts it and Albin did not keep it."""
    status = next((f for f in record.get("facts", []) if f.get("topic") == "status"), None)
    if status is None:
        return None
    contradicts = (record.get("data") or {}).get("statusSignal", {}).get("contradicts")
    if contradicts and not record.get("review", {}).get("statusConfirmed"):
        return None
    return {"value": status["value"], "factIds": [status["id"]]}


def _sentences(items: list[Sentence]) -> list[dict[str, Any]]:
    return [{"text": s.text, "factIds": list(s.fact_ids)} for s in items]


def to_site(text: LangTextV2, status: dict[str, Any] | None) -> dict[str, Any]:
    size = {"value": text.size.value, "factIds": list(text.size.fact_ids)} if text.size else None
    return {
        "lead": _sentences(text.lead),
        "fieldMarks": _sentences(text.field_marks),
        "voice": _sentences(text.voice),
        "whereWhen": _sentences(text.where_when),
        "behaviour": _sentences(text.behaviour),
        "lookAlikes": [
            {"other": la.other, "text": _sentences(la.sentences)} for la in text.look_alikes
        ],
        "metaDescription": text.meta_description,
        "facts": {"size": size, "swedenStatus": status},
    }
```

- [ ] **Step 5: Skriv `text_checks.py`**

```python
"""Code checks for the page text (spec 2026-09-25 §9.6, rules 1 to 7)."""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

from .checks import DASHES, _style, sentence_count
from .text_model import LangTextV2, Sentence, WebTextV2, iter_sentences, remove_paths

LEAD_MAX_WORDS = 45
LEAD_MAX_SENTENCES = 2
MARKS_MIN, MARKS_MAX, MARK_MAX_WORDS = 3, 4, 16
VOICE_MAX_WORDS = 60
WHERE_MAX_WORDS = 70
BEHAVIOUR_MAX_WORDS = 70
LOOKALIKES_MAX = 3
LOOKALIKE_MAX_WORDS = 35
META_MIN, META_MAX = 120, 155


@dataclass(frozen=True)
class TextIssue:
    path: str
    message: str
    removable: bool


@dataclass(frozen=True)
class TextContext:
    facts_by_id: dict[str, dict[str, Any]]
    lookalike_others: frozenset[str]

    @classmethod
    def from_facts(cls, facts: list[dict[str, Any]]) -> TextContext:
        others: set[str] = set()
        for fact in facts:
            if fact.get("topic") == "lookalike":
                other = fact.get("other", {})
                others.update(v for v in (other.get("qid"), other.get("scientific")) if v)
        return cls({f["id"]: f for f in facts}, frozenset(others))


_NUMBER = re.compile(r"\d+(?:[.,]\d+)?")


def numbers(text: str) -> set[str]:
    return {n.replace(",", ".") for n in _NUMBER.findall(text)}


def fact_corpus(fact: dict[str, Any]) -> str:
    quotes = [s.get("quote", "") for s in fact.get("sources", [])]
    return " ".join([str(fact.get("sv", "")), *quotes])


def _words(items: list[Sentence]) -> int:
    return sum(len(s.text.split()) for s in items)


def sentence_issues(
    path: str,
    lang: str,
    sentence: Sentence,
    ctx: TextContext,
    banned: list[str],
    *,
    prefix: str | None = None,
) -> list[TextIssue]:
    """Rules 1 to 5 for one sentence. `prefix` limits which fact ids it may cite (used by
    the comparison table, where a cell for species A may only cite A's facts)."""
    issues = [TextIssue(path, i.message, True) for i in _style(path, lang, sentence.text, banned)]
    if not sentence.fact_ids:
        return [*issues, TextIssue(path, "anger inga fakta", True)]
    unknown = [
        f
        for f in sentence.fact_ids
        if f not in ctx.facts_by_id or (prefix is not None and not f.startswith(prefix))
    ]
    if unknown:
        return [*issues, TextIssue(path, f"anger fakta som inte finns här: {', '.join(unknown)}", True)]
    corpus = " ".join(fact_corpus(ctx.facts_by_id[f]) for f in sentence.fact_ids)
    missing = sorted(numbers(sentence.text) - numbers(corpus))
    if missing:
        issues.append(
            TextIssue(path, f"talet {', '.join(missing)} finns inte i de fakta meningen anger", True)
        )
    return issues


def _size_issues(lang: str, text: LangTextV2, ctx: TextContext) -> list[TextIssue]:
    size = text.size
    path = f"{lang}.size"
    if size is None:
        return []
    if not re.search(r"\d", size.value):
        return [TextIssue(path, "storleken saknar siffror", True)]
    if any(d in size.value for d in DASHES):
        return [TextIssue(path, "storleken innehåller tankstreck", True)]
    if not size.fact_ids or any(f not in ctx.facts_by_id for f in size.fact_ids):
        return [TextIssue(path, "storleken anger inga giltiga fakta", True)]
    corpus = " ".join(fact_corpus(ctx.facts_by_id[f]) for f in size.fact_ids)
    if numbers(size.value) - numbers(corpus):
        return [TextIssue(path, "siffrorna i storleken finns inte i faktan", True)]
    return []


def check_lang(lang: str, text: LangTextV2, ctx: TextContext, banned: list[str]) -> list[TextIssue]:
    issues: list[TextIssue] = []
    for suffix, sentence in iter_sentences(text):
        issues += sentence_issues(f"{lang}.{suffix}", lang, sentence, ctx, banned)

    def field(name: str, message: str) -> None:
        issues.append(TextIssue(f"{lang}.{name}", message, False))

    lead_sentences = sum(sentence_count(s.text) for s in text.lead)
    if not text.lead:
        field("lead", "saknas")
    elif lead_sentences > LEAD_MAX_SENTENCES or _words(text.lead) > LEAD_MAX_WORDS:
        field("lead", "ska vara högst 2 meningar och 45 ord")
    if not MARKS_MIN <= len(text.field_marks) <= MARKS_MAX:
        field("field_marks", "ska ha 3 eller 4 punkter")
    for i, mark in enumerate(text.field_marks):
        if len(mark.text.split()) > MARK_MAX_WORDS:
            issues.append(TextIssue(f"{lang}.field_marks[{i}]", "ska vara högst 16 ord", True))
    if not text.voice:
        field("voice", "saknas")
    elif _words(text.voice) > VOICE_MAX_WORDS:
        field("voice", "ska vara högst 60 ord")
    if not text.where_when:
        field("where_when", "saknas")
    elif _words(text.where_when) > WHERE_MAX_WORDS:
        field("where_when", "ska vara högst 70 ord")
    if _words(text.behaviour) > BEHAVIOUR_MAX_WORDS:
        field("behaviour", "ska vara högst 70 ord")
    if len(text.look_alikes) > LOOKALIKES_MAX:
        field("look_alikes", "ska vara högst 3")
    for i, look_alike in enumerate(text.look_alikes):
        path = f"{lang}.look_alikes[{i}]"
        if look_alike.other not in ctx.lookalike_others:
            message = f"{look_alike.other} finns inte bland förväxlingsarterna i faktabladet"
            issues.append(TextIssue(path, message, True))
        elif _words(look_alike.sentences) > LOOKALIKE_MAX_WORDS:
            issues.append(TextIssue(path, "ska vara högst 35 ord", True))
    meta_path = f"{lang}.meta_description"
    issues += [
        TextIssue(meta_path, i.message, False)
        for i in _style(meta_path, lang, text.meta_description, banned)
    ]
    if not META_MIN <= len(text.meta_description) <= META_MAX:
        field("meta_description", f"ska vara 120 till 155 tecken (är {len(text.meta_description)})")
    return issues + _size_issues(lang, text, ctx)


def check_text(text: WebTextV2, ctx: TextContext, banned: list[str]) -> list[TextIssue]:
    return check_lang("sv", text.sv, ctx, banned) + check_lang("en", text.en, ctx, banned)


def settle(
    text: WebTextV2, ctx: TextContext, banned: list[str]
) -> tuple[WebTextV2, list[str], list[str]]:
    """Removes every removable part that breaks a rule, then checks again. Returns the text,
    notes about what was removed, and the problems that remain (they fail the species)."""
    issues = check_text(text, ctx, banned)
    removable = {i.path for i in issues if i.removable}
    notes = [f"{i.path} togs bort: {i.message}" for i in issues if i.removable]
    if removable:
        text = remove_paths(text, removable)
        issues = check_text(text, ctx, banned)
    return text, notes, [f"{i.path}: {i.message}" for i in issues]


def minimum_problems(text: WebTextV2) -> list[str]:
    problems: list[str] = []
    for lang in ("sv", "en"):
        t: LangTextV2 = getattr(text, lang)
        if not t.lead:
            problems.append(f"{lang}.lead saknas")
        if len(t.field_marks) < MARKS_MIN:
            problems.append(f"{lang}.field_marks har färre än 3 punkter")
        if not t.voice:
            problems.append(f"{lang}.voice saknas")
        if not t.where_when:
            problems.append(f"{lang}.where_when saknas")
        if not META_MIN <= len(t.meta_description) <= META_MAX:
            problems.append(f"{lang}.meta_description har fel längd")
    return problems
```

- [ ] **Step 6: Kör testerna**

Run: `uv run pytest tests/test_web_text_checks.py -v`
Expected: PASS

- [ ] **Step 7: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/text_model.py src/birdy_fetcher/web/text_checks.py tests/text_fixtures.py tests/test_web_text_checks.py
git commit -m "feat(pipeline): text med fakta-id per mening och kodkontrollerna"
```

---

### Task 19: Skrivprompten, kontrollprompten och kontrollmodellen

**Files:**
- Create: `prompts/web-v2.md`, `prompts/check-v1.md`, `src/birdy_fetcher/web/checker.py`
- Test: `tests/test_web_checker.py`

- [ ] **Step 1: Skriv de fallerande testerna**

`tests/test_web_checker.py`:

```python
"""Tests for web/checker.py: the second model that checks every sentence."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.cost import CostTracker
from birdy_fetcher.web.checker import (
    CheckerFailed,
    CheckOutput,
    SentenceChecker,
    Verdict,
    check_items,
    render_items,
)
from birdy_fetcher.web.text_checks import TextContext

from .text_fixtures import FACTS, VALID
from .web_fakes import FakeJsonClient, reply

PIPELINE = Path(__file__).resolve().parents[1]
CTX = TextContext.from_facts(FACTS)


def _checker(client: FakeJsonClient) -> SentenceChecker:
    return SentenceChecker(
        client=client, cost=CostTracker(max_usd=None), prompt_path=PIPELINE / "prompts/check-v1.md"
    )


def test_items_cover_every_sentence_in_both_languages() -> None:
    items = check_items(VALID, CTX)
    ids = [i.id for i in items]
    assert "sv.lead[0]" in ids
    assert "en.look_alikes[0].sentences[0]" in ids
    assert len(ids) == 2 * 8  # lead 2, field_marks 3, voice 1, where_when 1, look-alike 1
    where = next(i for i in items if i.id == "sv.where_when[0]")
    assert [f["id"] for f in where.facts] == ["s01", "d01", "f05"]


def test_render_items_shows_the_quotes() -> None:
    text = render_items(check_items(VALID, CTX)[:1])
    assert '<item id="sv.lead[0]">' in text
    assert "f05: Talgoxen lever i skog, parker och trädgårdar." in text
    assert "citat: i skog, parker och trädgårdar" in text


async def test_unsupported_and_missing_verdicts_are_returned() -> None:
    items = check_items(VALID, CTX)
    verdicts = [Verdict(id=i.id, supported=True, problem=None) for i in items[1:]]
    verdicts[0] = Verdict(id=items[1].id, supported=False, problem="nämner inte kinderna")
    client = FakeJsonClient([reply(CheckOutput(verdicts=verdicts))])
    result = await _checker(client).check(items)
    assert result == {
        items[0].id: "kontrollen gav inget svar för meningen",
        items[1].id: "nämner inte kinderna",
    }
    assert client.models == ["claude-sonnet-5"]
    assert client.schemas == ["CheckOutput"]


async def test_no_items_means_no_call() -> None:
    client = FakeJsonClient([])
    assert await _checker(client).check([]) == {}


async def test_an_unusable_answer_raises() -> None:
    client = FakeJsonClient([reply(None, stop="max_tokens")])
    with pytest.raises(CheckerFailed):
        await _checker(client).check(check_items(VALID, CTX))
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_checker.py -v`
Expected: FAIL med `ModuleNotFoundError`.

- [ ] **Step 3: Skriv `checker.py`**

```python
"""The second model (spec 2026-09-25 §9.6): a different model than the writer, in a fresh
context, judges every sentence against the facts it cites."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

from pydantic import BaseModel

from ..claude_summarizer import _split_prompt
from ..cost import CostTracker
from .llm import MODELS, JsonModelClient, record_cost
from .text_checks import TextContext
from .text_model import WebTextV2, iter_sentences

PROMPT_VERSION = "check-v1"


class Verdict(BaseModel):
    id: str
    supported: bool
    problem: str | None


class CheckOutput(BaseModel):
    verdicts: list[Verdict]


class CheckerFailed(RuntimeError):
    pass


@dataclass(frozen=True)
class CheckItem:
    id: str
    text: str
    facts: tuple[dict[str, Any], ...]


def check_items(text: WebTextV2, ctx: TextContext) -> list[CheckItem]:
    items: list[CheckItem] = []
    for lang in ("sv", "en"):
        for suffix, sentence in iter_sentences(getattr(text, lang)):
            facts = tuple(ctx.facts_by_id[f] for f in sentence.fact_ids if f in ctx.facts_by_id)
            items.append(CheckItem(f"{lang}.{suffix}", sentence.text, facts))
    return items


def _fact_line(fact: dict[str, Any]) -> str:
    line = f"{fact['id']}: {fact.get('sv', '')}"
    quotes = [s["quote"] for s in fact.get("sources", [])]
    return f"{line} (citat: {' | '.join(quotes)})" if quotes else line


def render_items(items: list[CheckItem]) -> str:
    blocks = []
    for item in items:
        facts = "\n".join(_fact_line(f) for f in item.facts)
        blocks.append(
            f'<item id="{item.id}">\n<sentence>{item.text}</sentence>\n'
            f"<facts>\n{facts}\n</facts>\n</item>"
        )
    return "\n\n".join(blocks)


@dataclass
class SentenceChecker:
    client: JsonModelClient
    cost: CostTracker
    prompt_path: Path
    model_key: str = "sonnet"
    effort: str = "high"

    async def check(self, items: list[CheckItem]) -> dict[str, str]:
        """Id to problem for every sentence that is not fully supported. A sentence the
        checker did not answer for counts as unsupported."""
        if not items:
            return {}
        template = self.prompt_path.read_text(encoding="utf-8")
        system, user = _split_prompt(template, items=render_items(items))
        reply = await self.client.complete(
            model=MODELS[self.model_key],
            system=system,
            messages=[{"role": "user", "content": user}],
            effort=self.effort,
            schema=CheckOutput,
        )
        record_cost(self.cost, self.model_key, reply)
        if reply.parsed is None:
            raise CheckerFailed(f"kontrollen gav inget giltigt svar (stop_reason={reply.stop_reason})")
        verdicts = {v.id: v for v in reply.parsed.verdicts}
        result: dict[str, str] = {}
        for item in items:
            verdict = verdicts.get(item.id)
            if verdict is None:
                result[item.id] = "kontrollen gav inget svar för meningen"
            elif not verdict.supported:
                result[item.id] = verdict.problem or "stöds inte av faktan"
        return result
```

- [ ] **Step 4: Skriv `prompts/check-v1.md`**

```markdown
# check prompt v1 (artsidor, spec 2026-09-25 reviderad 2026-10-01, avsnitt 9.6)

System: You check sentences for a field guide against the facts each sentence cites. For every item, decide whether everything the sentence says is supported by the facts listed with it.
- The sentence may be in Swedish or English. The facts are in Swedish, and their quotes may be in Swedish, English or German. Translation is fine; new information is not.
- Plain wording that follows directly from the facts is fine ("liten fågel" when the facts give 14 cm). Any extra detail is not supported: a number, place, time of year, colour, sound, behaviour or comparison that the cited facts do not state.
- Answer for every item id, in the given order. Set supported to true or false. When false, write problem as one short sentence saying what is not supported. When true, set problem to null.

User: {items}
```

- [ ] **Step 5: Skriv `prompts/web-v2.md`**

```markdown
# web prompt v2 (artsidor, spec 2026-09-25 reviderad 2026-10-01, avsnitt 9.5)

System: You write short species texts for the field guide pages on birdy.community, in Swedish and English, from a reviewed fact sheet. The reader is often outdoors with a phone and wants to know what the bird is and how to recognise it.

Source rule: the numbered facts in the user message are your only source. Every sentence lists the ids of the facts it is built on (fact_ids) and says nothing those facts do not say. If the facts do not cover something, leave it out and write shorter.

Style rules for both languages:
- Plain, concrete sentences, like a knowledgeable friend, not a brochure.
- No dashes of any kind: no em dash, no en dash, no double hyphen. Use a comma, a full stop or a colon instead. Write ranges with "till" in Swedish and "to" in English, for example "13 till 15 cm" and "13 to 15 cm".
- No exclamation marks. No first person (no jag, vi, oss, I, we, us, our, my). No questions to the reader.
- Never use any of these words or phrases: {banned_phrases}
- The Swedish text must read as natural Swedish written by a Swede, the English text as natural English. Use the bird names given in the user message.
- Do not mention Birdy, apps, photos or Wikipedia.
- Write numbers as digits exactly as the facts give them.

Fields, for each language (each sentence is an object with text and fact_ids):
- lead: 1 or 2 sentences, at most 45 words together. What the bird is and where people usually meet it.
- field_marks: 3 or 4 items, one sentence each, at most 16 words each, no full stop at the end. What to look at to recognise it: plumage, bill, size compared with a familiar bird, behaviour. Mention differences between male and female when the facts do.
- voice: at most 60 words. How the song and the calls sound.
- where_when: at most 70 words. Where and when it is seen in Sweden. You may use the data facts (ids starting with d) and the status fact.
- behaviour: at most 70 words about food and behaviour. An empty list if the facts say nothing about food or behaviour.
- look_alikes: one item per look-alike fact, at most 3. Set other to the value after "other=" in that fact's label, and write 1 or 2 sentences on how to tell the two apart.
- meta_description: 120 to 155 characters including spaces, as a plain string without fact ids. Start with the bird's name, then say what the page offers: how to recognise it, its call and when it is seen.
- size: the body length as the size facts give it, written like "Cirka 14 cm" or "13 till 15 cm" in Swedish and "About 14 cm" or "13 to 15 cm" in English, with the fact ids. null if no fact gives the length.

User: Species: {name_sv} (Swedish), {name_en} (English), scientific name {scientific_name}. Family: {family_sv} ({family}). Group on the site: {group_sv} / {group_en}.
Status in Sweden, already decided: {status_line}

Facts:
{facts}
```

- [ ] **Step 6: Kör testerna**

Run: `uv run pytest tests/test_web_checker.py -v`
Expected: PASS

- [ ] **Step 7: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/checker.py prompts/web-v2.md prompts/check-v1.md tests/test_web_checker.py
git commit -m "feat(pipeline): skrivprompt v2, kontrollprompt och kontrollmodellen"
```

---

### Task 20: Steg 3, `web write`

**Files:**
- Create: `src/birdy_fetcher/web/text_step.py`, `tests/test_web_text_step.py`
- Modify: `src/birdy_fetcher/cli.py`

Flödet per art (spec §9.6): skriv, kodkontroll, ett nytt försök med felen, ta bort det som går att ta bort, kontrollmodellen, en omskrivning med de meningar som inte stöds, kontrollera igen, ta bort det som fortfarande inte stöds, minimikontroll. Statusfaktumet `s01` visas bara för skribenten när `status_for_site` ger en status.

- [ ] **Step 1: Skriv de fallerande testerna**

`tests/test_web_text_step.py`:

```python
"""Tests for web/text_step.py: write, check, rewrite, remove, and the run."""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path

from birdy_fetcher.web.checker import CheckOutput, Verdict, check_items
from birdy_fetcher.web.record import load_record, record_path, save_record
from birdy_fetcher.web.text_checks import TextContext
from birdy_fetcher.web.text_model import WebTextV2
from birdy_fetcher.web.text_step import WriteOptions, run_write

from .text_fixtures import EN, FACTS, SV, VALID, S, reviewed_record
from .web_fakes import FakeJsonClient, reply
from .web_repo import make_repo

NOW = datetime(2026, 11, 21, tzinfo=UTC)
CTX = TextContext.from_facts(FACTS)


def _verdicts(text: WebTextV2, unsupported: dict[str, str] | None = None) -> CheckOutput:
    bad = unsupported or {}
    return CheckOutput(
        verdicts=[
            Verdict(id=i.id, supported=i.id not in bad, problem=bad.get(i.id))
            for i in check_items(text, CTX)
        ]
    )


async def test_a_good_text_is_saved_with_the_status_from_the_fact_sheet(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    client = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    assert client.models == ["claude-opus-5", "claude-sonnet-5"]
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    assert record["status"] == "ok"
    assert record["text"]["sv"]["facts"]["swedenStatus"] == {"value": "resident", "factIds": ["s01"]}
    assert record["text"]["en"]["lead"][1]["factIds"] == ["f01"]
    assert record["generated"]["text"]["checker"] == "claude-sonnet-5"
    assert "unreviewed" not in record["generated"]["text"]
    assert any(p.name.startswith("web-text-") for p in paths.reports.iterdir())


async def test_a_broken_rule_gets_one_retry(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    short = WebTextV2(sv=SV.model_copy(update={"meta_description": "Kort."}), en=EN)
    client = FakeJsonClient([reply(short), reply(VALID), reply(_verdicts(VALID))])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    assert len(client.calls) == 3
    assert "meta_description" in str(client.calls[1][-1]["content"])


async def test_an_unsupported_sentence_is_rewritten_then_removed(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    bad = {"sv.lead[1]": "nämner inget om kinderna"}
    client = FakeJsonClient(
        [reply(VALID), reply(_verdicts(VALID, bad)), reply(VALID), reply(_verdicts(VALID, bad))]
    )
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    assert any("sv.lead[1] togs bort" in n for n in outcomes[0].notes)
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    assert len(record["text"]["sv"]["lead"]) == 1


async def test_an_unreviewed_fact_sheet_is_only_written_when_allowed(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    record = reviewed_record()
    record["review"] = {"wave": 1}
    save_record(record_path(paths.data_out, "Q25485"), record)
    skipped = await run_write(paths, WriteOptions(wave=1), client=FakeJsonClient([]), now=NOW)
    assert [o.status for o in skipped] == ["skipped"]
    client = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    await run_write(
        paths, WriteOptions(qids=("Q25485",), allow_unreviewed=True), client=client, now=NOW
    )
    saved = load_record(record_path(paths.data_out, "Q25485"))
    assert saved is not None
    assert saved["generated"]["text"]["unreviewed"] is True


async def test_a_current_text_is_skipped_unless_regenerated(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    first = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    await run_write(paths, WriteOptions(wave=1), client=first, now=NOW)
    again = await run_write(paths, WriteOptions(wave=1), client=FakeJsonClient([]), now=NOW)
    assert [o.status for o in again] == ["skipped"]
    redo = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    outcomes = await run_write(paths, WriteOptions(wave=1, regenerate=True), client=redo, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]


async def test_an_unconfirmed_status_flag_hides_s01_from_the_writer(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    record = reviewed_record()
    record["data"]["statusSignal"]["contradicts"] = "Statusen säger stannfågel, men ..."
    save_record(record_path(paths.data_out, "Q25485"), record)
    without_s01 = WebTextV2(
        sv=SV.model_copy(
            update={"where_when": [S("Rapporteras året runt i skog, parker och trädgårdar.", "d01", "f05")]}
        ),
        en=EN.model_copy(
            update={"where_when": [S("Reported all year in woodland, parks and gardens.", "d01", "f05")]}
        ),
    )
    client = FakeJsonClient([reply(without_s01), reply(_verdicts(without_s01))])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    assert "s01" not in str(client.calls[0][0]["content"])
    saved = load_record(record_path(paths.data_out, "Q25485"))
    assert saved is not None
    assert saved["text"]["sv"]["facts"]["swedenStatus"] is None


async def test_a_text_that_cannot_be_fixed_fails_and_keeps_the_rejected_text(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    two_marks = WebTextV2(sv=SV.model_copy(update={"field_marks": SV.field_marks[:2]}), en=EN)
    client = FakeJsonClient([reply(two_marks), reply(two_marks)])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    saved = load_record(record_path(paths.data_out, "Q25485"))
    assert saved is not None
    assert saved["text"] is None
    assert len(saved["rejectedText"]["sv"]["fieldMarks"]) == 2
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_text_step.py -v`
Expected: FAIL med `ModuleNotFoundError`.

- [ ] **Step 3: Skriv `text_step.py`**

```python
"""Step 3 (spec 2026-09-25 §9.5 and §9.6): write the text from the reviewed fact sheet,
check it in code, check it with a second model, rewrite once, remove what still fails."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from anthropic.types import MessageParam

from ..claude_summarizer import _split_prompt
from ..cost import CostTracker, MaxCostExceeded
from .checker import CheckerFailed, SentenceChecker, check_items
from .checker import PROMPT_VERSION as CHECK_PROMPT_VERSION
from .checks import load_banned
from .facts import STATUS_SV, TOPIC_SV
from .groups import GroupTable
from .llm import MODELS, AnthropicJsonClient, JsonModelClient, ModelReply, record_cost
from .paths import WebPaths
from .record import Record, facts_hash, is_reviewed, load_all, record_path, save_record
from .report import StepOutcome, render_step_report, write_step_report
from .text_checks import TextContext, TextIssue, check_text, minimum_problems, settle
from .text_model import WebTextV2, remove_paths, status_for_site, to_site

PROMPT_VERSION = "web-v2"
RULE_ATTEMPTS = 2


def writer_facts(record: Record) -> list[dict[str, Any]]:
    """The facts the writer may use: everything except a status that is not shown."""
    show_status = status_for_site(record) is not None
    return [f for f in record.get("facts", []) if f["topic"] != "status" or show_status]


def render_facts(facts: list[dict[str, Any]]) -> str:
    lines = []
    for fact in facts:
        label = TOPIC_SV.get(fact["topic"], fact["topic"])
        if fact["topic"] == "lookalike":
            other = fact["other"]
            label = f"förväxling, other={other.get('qid') or other['scientific']} ({other['scientific']})"
        lines.append(f"{fact['id']} [{label}] {fact['sv']}")
    return "\n".join(lines)


def render_write_prompt(
    template: str,
    record: Record,
    facts: list[dict[str, Any]],
    group_sv: str,
    group_en: str,
    banned: list[str],
) -> tuple[str, str]:
    status = status_for_site(record)
    status_line = (
        f"{STATUS_SV[status['value']]} (fact s01)"
        if status
        else "not decided, do not state a status"
    )
    return _split_prompt(
        template,
        name_sv=record["names"]["sv"],
        name_en=record["names"]["en"],
        scientific_name=record["names"]["scientific"],
        family=record["family"]["latin"],
        family_sv=record["family"]["sv"],
        group_sv=group_sv,
        group_en=group_en,
        status_line=status_line,
        banned_phrases=", ".join(banned),
        facts=render_facts(facts),
    )


def rules_feedback(issues: list[TextIssue]) -> str:
    lines = "\n".join(f"- {i.path}: {i.message}" for i in issues)
    return (
        "Your answer broke these rules. Write the whole answer again with the same structure "
        "and fix only these points:\n" + lines + "\n"
        "Every sentence must list the ids of the facts it uses and say no more than they do."
    )


def support_feedback(unsupported: dict[str, str]) -> str:
    lines = "\n".join(f"- {path}: {problem}" for path, problem in unsupported.items())
    return (
        "A checker found sentences that say more than the facts they cite. Write the whole "
        "answer again with the same structure. Fix or drop these sentences and add nothing "
        "the facts do not say:\n" + lines
    )


@dataclass
class TextResult:
    text: WebTextV2 | None
    rejected: WebTextV2 | None
    notes: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)
    attempts: int = 0


@dataclass
class SpeciesTextWriter:
    client: JsonModelClient
    cost: CostTracker
    checker: SentenceChecker
    prompt_path: Path
    banned: list[str]
    model_key: str = "opus"
    effort: str = "high"

    async def _ask(self, system: str, messages: list[MessageParam]) -> ModelReply[WebTextV2]:
        reply = await self.client.complete(
            model=MODELS[self.model_key],
            system=system,
            messages=messages,
            effort=self.effort,
            schema=WebTextV2,
        )
        record_cost(self.cost, self.model_key, reply)
        return reply

    async def write(self, record: Record, group_sv: str, group_en: str) -> TextResult:
        facts = writer_facts(record)
        ctx = TextContext.from_facts(facts)
        template = self.prompt_path.read_text(encoding="utf-8")
        system, user = render_write_prompt(template, record, facts, group_sv, group_en, self.banned)
        base: list[MessageParam] = [{"role": "user", "content": user}]
        result = TextResult(None, None)
        messages = list(base)
        text: WebTextV2 | None = None
        last_stop: str | None = None
        for attempt in range(1, RULE_ATTEMPTS + 1):
            result.attempts = attempt
            reply = await self._ask(system, messages)
            last_stop = reply.stop_reason
            if reply.parsed is None:
                if reply.stop_reason in ("max_tokens", "refusal"):
                    break
                continue
            text = reply.parsed
            issues = check_text(text, ctx, self.banned)
            if not issues:
                break
            if attempt < RULE_ATTEMPTS:
                messages = [
                    *messages,
                    {"role": "assistant", "content": reply.raw_text},
                    {"role": "user", "content": rules_feedback(issues)},
                ]
        if text is None:
            result.errors = [f"modellen gav inget giltigt svar (stop_reason={last_stop})"]
            return result
        text, removed, hard = settle(text, ctx, self.banned)
        result.notes += removed
        if hard:
            result.rejected, result.errors = text, hard
            return result

        unsupported = await self.checker.check(check_items(text, ctx))
        if unsupported:
            result.attempts += 1
            retry: list[MessageParam] = [
                *base,
                {"role": "assistant", "content": text.model_dump_json()},
                {"role": "user", "content": support_feedback(unsupported)},
            ]
            reply = await self._ask(system, retry)
            if reply.parsed is not None:
                candidate, removed_again, hard_again = settle(reply.parsed, ctx, self.banned)
                if not hard_again:
                    text = candidate
                    result.notes += removed_again
                    unsupported = await self.checker.check(check_items(text, ctx))
            if unsupported:
                result.notes += [f"{path} togs bort: {p}" for path, p in unsupported.items()]
                text = remove_paths(text, set(unsupported))
        problems = minimum_problems(text)
        if problems:
            result.rejected, result.errors = text, problems
            return result
        result.text = text
        return result


def apply_text(record: Record, result: TextResult, generated: dict[str, Any]) -> None:
    status = status_for_site(record)
    if result.text is not None:
        record["text"] = {lang: to_site(getattr(result.text, lang), status) for lang in ("sv", "en")}
        record["status"] = "ok"
        record["errors"] = []
        record.pop("rejectedText", None)
    else:
        record["text"] = None
        record["status"] = "failed"
        record["errors"] = list(result.errors)
        rejected = result.rejected
        record["rejectedText"] = (
            {lang: to_site(getattr(rejected, lang), status) for lang in ("sv", "en")}
            if rejected is not None
            else None
        )
    record.setdefault("generated", {})["text"] = generated


@dataclass(frozen=True)
class WriteOptions:
    wave: int | None = None
    qids: tuple[str, ...] = ()
    model_key: str = "opus"
    effort: str = "high"
    checker_key: str = "sonnet"
    max_cost: float | None = None
    regenerate: bool = False
    allow_unreviewed: bool = False
    workers: int = 4


def _skip_reason(record: Record, options: WriteOptions) -> str | None:
    if not any(f["topic"] == "appearance" for f in record.get("facts", [])):
        return "faktabladet saknas eller misslyckades: kör web facts"
    reviewed = is_reviewed(record)
    if not reviewed and not options.allow_unreviewed:
        return "faktabladet är inte granskat"
    generated = record.get("generated", {}).get("text") or {}
    current = (
        record.get("status") == "ok"
        and generated.get("factsHash") == facts_hash(record)
        and bool(generated.get("unreviewed")) == (not reviewed)
    )
    if current and not options.regenerate:
        return "texten är redan skriven ur samma faktablad"
    return None


async def run_write(
    paths: WebPaths,
    options: WriteOptions,
    *,
    client: JsonModelClient | None = None,
    now: datetime | None = None,
) -> list[StepOutcome]:
    if options.wave is None and not options.qids:
        raise ValueError("Ange en våg eller arter")
    now = now or datetime.now(UTC)
    records = load_all(paths.data_out)
    chosen = (
        [records[q] for q in options.qids if q in records]
        if options.qids
        else [r for r in records.values() if r.get("review", {}).get("wave") == options.wave]
    )
    groups = GroupTable(paths.family_groups, paths.web_groups)
    owned = client is None
    model_client: JsonModelClient = client or AnthropicJsonClient()
    cost = CostTracker(max_usd=options.max_cost)
    checker = SentenceChecker(
        client=model_client,
        cost=cost,
        prompt_path=paths.prompt_file(CHECK_PROMPT_VERSION),
        model_key=options.checker_key,
    )
    writer = SpeciesTextWriter(
        client=model_client,
        cost=cost,
        checker=checker,
        prompt_path=paths.prompt_file(PROMPT_VERSION),
        banned=load_banned(paths.banned),
        model_key=options.model_key,
        effort=options.effort,
    )
    stop = asyncio.Event()
    semaphore = asyncio.Semaphore(options.workers)

    async def one(record: Record) -> StepOutcome:
        qid, name = str(record["qid"]), str(record["names"]["sv"])
        async with semaphore:
            try:
                reason = _skip_reason(record, options)
                if reason is not None:
                    return StepOutcome(qid, name, "skipped", [reason])
                if stop.is_set():
                    return StepOutcome(qid, name, "skipped", ["kostnadstaket nåddes"])
                group = groups.by_key(record["group"])
                try:
                    result = await writer.write(record, group.name_sv, group.name_en)
                except MaxCostExceeded as exc:
                    stop.set()
                    return StepOutcome(qid, name, "skipped", [f"kostnadstaket nåddes: {exc}"])
                except CheckerFailed as exc:
                    return StepOutcome(qid, name, "failed", [str(exc)])
                generated: dict[str, Any] = {
                    "model": MODELS[options.model_key],
                    "prompt": PROMPT_VERSION,
                    "effort": options.effort,
                    "checker": MODELS[options.checker_key],
                    "at": now.isoformat(),
                    "factsHash": facts_hash(record),
                }
                if not is_reviewed(record):
                    generated["unreviewed"] = True
                apply_text(record, result, generated)
                save_record(record_path(paths.data_out, qid), record)
                return StepOutcome(qid, name, str(record["status"]), result.errors, result.notes)
            except Exception as exc:  # one species' error must not stop the run
                return StepOutcome(qid, name, "failed", [f"{type(exc).__name__}: {exc}"])

    try:
        outcomes = list(await asyncio.gather(*(one(r) for r in chosen)))
    finally:
        if owned and isinstance(model_client, AnthropicJsonClient):
            await model_client.aclose()
    model_line = (
        f"Skribent: `{MODELS[options.model_key]}` (effort: {options.effort}). "
        f"Kontroll: `{MODELS[options.checker_key]}`."
    )
    report = render_step_report(
        title="Text",
        date=now.date().isoformat(),
        outcomes=outcomes,
        cost_usd=cost.total_usd,
        model_line=model_line,
    )
    write_step_report(paths.reports, "text", now, report)
    return outcomes
```

- [ ] **Step 4: Kommandot i `cli.py`**

```python
@web.command("write")
@click.option("--wave", type=click.IntRange(1, 3), default=None)
@click.option("--species", multiple=True, help="Q-ID(s) i stället för en våg.")
@click.option("--model", "model_key", type=click.Choice(["opus", "sonnet"]), default="opus")
@click.option("--effort", type=click.Choice(["low", "medium", "high"]), default="high")
@click.option("--checker-model", "checker_key", type=click.Choice(["opus", "sonnet"]), default="sonnet")
@click.option("--max-cost", type=float, default=None, help="Kostnadstak i USD för körningen.")
@click.option("--regenerate", is_flag=True, help="Skriv om även texter som är aktuella.")
@click.option(
    "--allow-unreviewed", is_flag=True,
    help="Skriv även ur ogranskade faktablad (bara provkörning, publiceras aldrig).",
)
@click.option("--workers", type=click.IntRange(min=1), default=4)
def web_write(
    wave: int | None,
    species: tuple[str, ...],
    model_key: str,
    effort: str,
    checker_key: str,
    max_cost: float | None,
    regenerate: bool,
    allow_unreviewed: bool,
    workers: int,
) -> None:
    """Steg 3: text ur det granskade faktabladet, kontrollerad mening för mening. Kostar pengar."""
    from .web.text_step import WriteOptions, run_write

    if wave is None and not species:
        raise click.UsageError("Ange --wave eller --species.")
    _require_api_key()
    paths = _web_paths()
    options = WriteOptions(
        wave=wave,
        qids=species,
        model_key=model_key,
        effort=effort,
        checker_key=checker_key,
        max_cost=max_cost,
        regenerate=regenerate,
        allow_unreviewed=allow_unreviewed,
        workers=workers,
    )
    _print_outcomes(asyncio.run(run_write(paths, options)), paths.reports)
```

- [ ] **Step 5: Kör testerna**

Run: `uv run pytest tests/test_web_text_step.py tests/test_web_text_checks.py tests/test_web_checker.py -v`
Expected: PASS

- [ ] **Step 6: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/text_step.py src/birdy_fetcher/cli.py tests/test_web_text_step.py
git commit -m "feat(pipeline): web write skriver ur faktabladet och kontrollerar varje mening"
```

---

### Task 21: Förväxlingsparen och sökvolymerna, `web compare-candidates`

**Files:**
- Create: `src/birdy_fetcher/web/compare.py`
- Modify: `src/birdy_fetcher/cli.py`
- Test: `tests/test_web_compare.py`

Kandidaterna är alla förväxlingspar ur faktabladen där båda arterna finns bland artposterna. Filen `review/comparison-volumes.csv` får tomma volymkolumner som agenten fyller i ur sökordsplaneraren (körtask R7). En ny körning behåller volymer som redan är ifyllda.

- [ ] **Step 1: Skriv de fallerande testerna**

`tests/test_web_compare.py`:

```python
"""Tests for web/compare.py: pairs, search volumes and the comparison step."""

from __future__ import annotations

import csv
from pathlib import Path

from birdy_fetcher.web.compare import (
    Pair,
    candidate_pairs,
    comparison_path,
    comparison_slugs,
    queries,
    read_volumes,
    select_pairs,
    write_candidates,
)
from birdy_fetcher.web.record import Record, new_record


def _species(qid: str, sv: str, en: str, slug_sv: str, slug_en: str, others: list[str]) -> Record:
    record = new_record(qid)
    record["names"] = {"sv": sv, "en": en, "scientific": sv}
    record["slug"] = {"sv": slug_sv, "en": slug_en}
    record["facts"] = [
        {"id": f"f0{i + 1}", "topic": "lookalike", "sv": "x", "sources": [], "other": {"scientific": o, "qid": o}}
        for i, o in enumerate(others)
    ]
    return record


RECORDS = {
    "Q25485": _species("Q25485", "Talgoxe", "Great Tit", "talgoxe", "great-tit", ["Q25404", "Q999"]),
    "Q25404": _species("Q25404", "Blåmes", "Eurasian Blue Tit", "blames", "eurasian-blue-tit", ["Q25485"]),
    "Q26000": _species("Q26000", "Kaja", "Western Jackdaw", "kaja", "western-jackdaw", []),
}


def test_candidate_pairs_are_unique_and_ordered_by_swedish_slug() -> None:
    assert candidate_pairs(RECORDS) == [Pair("Q25404", "Q25485")]


def test_queries_in_both_languages() -> None:
    q = queries(Pair("Q25404", "Q25485"), RECORDS)
    assert q["sv"] == [
        "blåmes eller talgoxe",
        "talgoxe eller blåmes",
        "skillnad blåmes talgoxe",
        "blåmes talgoxe skillnad",
    ]
    assert q["en"][0] == "eurasian blue tit vs great tit"
    assert q["en"][2] == "difference between eurasian blue tit and great tit"


def test_slugs_and_file_name() -> None:
    pair = Pair("Q25404", "Q25485")
    assert comparison_slugs(pair, RECORDS) == {
        "sv": "blames-eller-talgoxe",
        "en": "eurasian-blue-tit-vs-great-tit",
    }
    assert comparison_path(Path("out"), Pair("Q25485", "Q25404")).name == "Q25404_Q25485.json"


def test_candidates_file_keeps_volumes_that_are_filled_in(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    write_candidates(path, [Pair("Q25404", "Q25485")], RECORDS)
    rows = list(csv.DictReader(path.open(encoding="utf-8", newline="")))
    rows[0]["sv_volume"] = "1 300"
    rows[0]["en_volume"] = "880"
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    write_candidates(path, [Pair("Q25404", "Q25485")], RECORDS)
    assert read_volumes(path) == {Pair("Q25404", "Q25485"): (1300, 880)}


def test_select_pairs_ranks_by_swedish_then_english_and_drops_zero() -> None:
    volumes = {
        Pair("Q1", "Q2"): (100, 0),
        Pair("Q3", "Q4"): (100, 50),
        Pair("Q5", "Q6"): (0, 0),
        Pair("Q7", "Q8"): (10, 900),
    }
    assert select_pairs(volumes, top=2) == [Pair("Q3", "Q4"), Pair("Q1", "Q2")]
    assert Pair("Q5", "Q6") not in select_pairs(volumes, top=10)
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_compare.py -v`
Expected: FAIL med `ModuleNotFoundError`.

- [ ] **Step 3: Skriv början på `compare.py`**

```python
"""Look-alike pairs, their search volumes and their comparison texts (spec 2026-09-25 §7
and §9.7)."""

from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path

from .record import Record

VOLUMES_FILE = "comparison-volumes.csv"
COLUMNS = [
    "a_qid", "b_qid", "a_sv", "b_sv", "a_en", "b_en",
    "sv_queries", "en_queries", "sv_volume", "en_volume",
]  # fmt: skip
TOP = 30


@dataclass(frozen=True, order=True)
class Pair:
    """`a` comes first in Swedish slug order (spec appendix D)."""

    a: str
    b: str


def ordered_pair(x: str, y: str, records: dict[str, Record]) -> Pair:
    first, second = sorted((x, y), key=lambda q: str(records[q]["slug"]["sv"]))
    return Pair(first, second)


def candidate_pairs(records: dict[str, Record]) -> list[Pair]:
    seen: set[frozenset[str]] = set()
    pairs: list[Pair] = []
    for qid, record in records.items():
        for fact in record.get("facts", []):
            if fact.get("topic") != "lookalike":
                continue
            other = fact.get("other", {}).get("qid")
            if not other or other == qid or other not in records:
                continue
            key = frozenset((qid, other))
            if key not in seen:
                seen.add(key)
                pairs.append(ordered_pair(qid, other, records))
    return sorted(pairs)


def queries(pair: Pair, records: dict[str, Record]) -> dict[str, list[str]]:
    a_sv, b_sv = (str(records[q]["names"]["sv"]).lower() for q in (pair.a, pair.b))
    a_en, b_en = (str(records[q]["names"]["en"]).lower() for q in (pair.a, pair.b))
    return {
        "sv": [
            f"{a_sv} eller {b_sv}",
            f"{b_sv} eller {a_sv}",
            f"skillnad {a_sv} {b_sv}",
            f"{a_sv} {b_sv} skillnad",
        ],
        "en": [f"{a_en} vs {b_en}", f"{b_en} vs {a_en}", f"difference between {a_en} and {b_en}"],
    }


def _number(value: str | None) -> int:
    digits = "".join(ch for ch in (value or "") if ch.isdigit())
    return int(digits) if digits else 0


def read_volumes(path: Path) -> dict[Pair, tuple[int, int]]:
    if not path.exists():
        return {}
    with path.open(encoding="utf-8-sig", newline="") as f:
        return {
            Pair(row["a_qid"], row["b_qid"]): (_number(row["sv_volume"]), _number(row["en_volume"]))
            for row in csv.DictReader(f)
        }


def write_candidates(path: Path, pairs: list[Pair], records: dict[str, Record]) -> None:
    old: dict[Pair, dict[str, str]] = {}
    if path.exists():
        with path.open(encoding="utf-8-sig", newline="") as f:
            old = {Pair(r["a_qid"], r["b_qid"]): r for r in csv.DictReader(f)}
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        for pair in pairs:
            q = queries(pair, records)
            kept = old.get(pair, {})
            writer.writerow(
                {
                    "a_qid": pair.a,
                    "b_qid": pair.b,
                    "a_sv": records[pair.a]["names"]["sv"],
                    "b_sv": records[pair.b]["names"]["sv"],
                    "a_en": records[pair.a]["names"]["en"],
                    "b_en": records[pair.b]["names"]["en"],
                    "sv_queries": "; ".join(q["sv"]),
                    "en_queries": "; ".join(q["en"]),
                    "sv_volume": kept.get("sv_volume", ""),
                    "en_volume": kept.get("en_volume", ""),
                }
            )


def select_pairs(volumes: dict[Pair, tuple[int, int]], top: int = TOP) -> list[Pair]:
    ranked = sorted(
        ((pair, sv, en) for pair, (sv, en) in volumes.items() if sv or en),
        key=lambda x: (-x[1], -x[2], x[0]),
    )
    return [pair for pair, _, _ in ranked[:top]]


def comparison_slugs(pair: Pair, records: dict[str, Record]) -> dict[str, str]:
    a, b = records[pair.a], records[pair.b]
    first_en, second_en = sorted([str(a["slug"]["en"]), str(b["slug"]["en"])])
    return {"sv": f"{a['slug']['sv']}-eller-{b['slug']['sv']}", "en": f"{first_en}-vs-{second_en}"}


def comparison_path(out_dir: Path, pair: Pair) -> Path:
    first, second = sorted((pair.a, pair.b))
    return out_dir / f"{first}_{second}.json"
```

- [ ] **Step 4: Kommandot i `cli.py`**

```python
@web.command("compare-candidates")
def web_compare_candidates() -> None:
    """Skriver förväxlingsparen till review/comparison-volumes.csv. Gratis."""
    from .web.compare import VOLUMES_FILE, candidate_pairs, write_candidates
    from .web.record import load_all

    paths = _web_paths()
    records = load_all(paths.data_out)
    pairs = candidate_pairs(records)
    write_candidates(paths.review / VOLUMES_FILE, pairs, records)
    click.echo(f"{len(pairs)} par i {paths.review / VOLUMES_FILE}. Fyll i sv_volume och en_volume.")
```

- [ ] **Step 5: Kör testerna**

Run: `uv run pytest tests/test_web_compare.py -v`
Expected: PASS

- [ ] **Step 6: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/compare.py src/birdy_fetcher/cli.py tests/test_web_compare.py
git commit -m "feat(pipeline): förväxlingspar och fil för sökvolymer"
```

---

### Task 22: Den delade skrivslingan och jämförelsetexterna, `web compare`

**Files:**
- Create: `src/birdy_fetcher/web/checked_writer.py`, `prompts/compare-v1.md`
- Modify: `src/birdy_fetcher/web/text_step.py` (använd den delade slingan), `src/birdy_fetcher/web/compare.py` (lägg till), `src/birdy_fetcher/cli.py`
- Test: `tests/test_web_compare.py` (lägg till)

Skriv-, kontroll- och omskrivningsslingan i Task 20 behövs också för jämförelserna. Lyft ut den först, med Task 20:s tester som skydd.

- [ ] **Step 1: Skriv `checked_writer.py`**

```python
"""The write, check, rewrite and remove loop that species texts and comparisons share
(spec 2026-09-25 §9.6)."""

from __future__ import annotations

from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field

from anthropic.types import MessageParam
from pydantic import BaseModel

from .checker import CheckItem, SentenceChecker
from .llm import ModelReply
from .text_checks import TextIssue

RULE_ATTEMPTS = 2


@dataclass(frozen=True)
class Checks[T: BaseModel]:
    rules: Callable[[T], list[TextIssue]]
    settle: Callable[[T], tuple[T, list[str], list[str]]]
    items: Callable[[T], list[CheckItem]]
    remove: Callable[[T, set[str]], T]
    minimum: Callable[[T], list[str]]


@dataclass
class Written[T: BaseModel]:
    text: T | None = None
    rejected: T | None = None
    notes: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)
    attempts: int = 0


def rules_feedback(issues: list[TextIssue]) -> str:
    lines = "\n".join(f"- {i.path}: {i.message}" for i in issues)
    return (
        "Your answer broke these rules. Write the whole answer again with the same structure "
        "and fix only these points:\n" + lines + "\n"
        "Every sentence must list the ids of the facts it uses and say no more than they do."
    )


def support_feedback(unsupported: dict[str, str]) -> str:
    lines = "\n".join(f"- {path}: {problem}" for path, problem in unsupported.items())
    return (
        "A checker found sentences that say more than the facts they cite. Write the whole "
        "answer again with the same structure. Fix or drop these sentences and add nothing "
        "the facts do not say:\n" + lines
    )


async def write_checked[T: BaseModel](
    *,
    ask: Callable[[list[MessageParam]], Awaitable[ModelReply[T]]],
    user: str,
    checks: Checks[T],
    checker: SentenceChecker,
) -> Written[T]:
    result: Written[T] = Written()
    base: list[MessageParam] = [{"role": "user", "content": user}]
    messages = list(base)
    text: T | None = None
    last_stop: str | None = None
    for attempt in range(1, RULE_ATTEMPTS + 1):
        result.attempts = attempt
        reply = await ask(messages)
        last_stop = reply.stop_reason
        if reply.parsed is None:
            if reply.stop_reason in ("max_tokens", "refusal"):
                break
            continue
        text = reply.parsed
        issues = checks.rules(text)
        if not issues:
            break
        if attempt < RULE_ATTEMPTS:
            messages = [
                *messages,
                {"role": "assistant", "content": reply.raw_text},
                {"role": "user", "content": rules_feedback(issues)},
            ]
    if text is None:
        result.errors = [f"modellen gav inget giltigt svar (stop_reason={last_stop})"]
        return result
    text, removed, hard = checks.settle(text)
    result.notes += removed
    if hard:
        result.rejected, result.errors = text, hard
        return result

    unsupported = await checker.check(checks.items(text))
    if unsupported:
        result.attempts += 1
        reply = await ask(
            [
                *base,
                {"role": "assistant", "content": text.model_dump_json()},
                {"role": "user", "content": support_feedback(unsupported)},
            ]
        )
        if reply.parsed is not None:
            candidate, removed_again, hard_again = checks.settle(reply.parsed)
            if not hard_again:
                text = candidate
                result.notes += removed_again
                unsupported = await checker.check(checks.items(text))
        if unsupported:
            result.notes += [f"{path} togs bort: {p}" for path, p in unsupported.items()]
            text = checks.remove(text, set(unsupported))
    problems = checks.minimum(text)
    if problems:
        result.rejected, result.errors = text, problems
        return result
    result.text = text
    return result
```

- [ ] **Step 2: Låt `text_step.py` använda den**

I `text_step.py`: ta bort `RULE_ATTEMPTS`, `rules_feedback`, `support_feedback` och klassen `TextResult`, och ersätt metoden `SpeciesTextWriter.write` med:

```python
    async def write(self, record: Record, group_sv: str, group_en: str) -> Written[WebTextV2]:
        facts = writer_facts(record)
        ctx = TextContext.from_facts(facts)
        template = self.prompt_path.read_text(encoding="utf-8")
        system, user = render_write_prompt(template, record, facts, group_sv, group_en, self.banned)
        checks: Checks[WebTextV2] = Checks(
            rules=lambda t: check_text(t, ctx, self.banned),
            settle=lambda t: settle(t, ctx, self.banned),
            items=lambda t: check_items(t, ctx),
            remove=remove_paths,
            minimum=minimum_problems,
        )
        return await write_checked(
            ask=lambda messages: self._ask(system, messages),
            user=user,
            checks=checks,
            checker=self.checker,
        )
```

Ändra `apply_text(record: Record, result: TextResult, ...)` till `apply_text(record: Record, result: Written[WebTextV2], ...)`, lägg till `from .checked_writer import Checks, Written, write_checked` och ta bort importer som blivit oanvända (`TextIssue`, `field`).

Run: `uv run pytest tests/test_web_text_step.py -v`
Expected: PASS (samma beteende som förut).

- [ ] **Step 3: Skriv de fallerande testerna för jämförelserna**

Lägg till i `tests/test_web_compare.py`:

```python
from datetime import UTC, datetime

from birdy_fetcher.web.checker import CheckOutput, Verdict
from birdy_fetcher.web.compare import (
    Cell,
    CompareLang,
    CompareOptions,
    CompareOutput,
    Row,
    compare_items,
    pair_context,
    run_compare,
)
from birdy_fetcher.web.record import load_record, record_path, save_record
from birdy_fetcher.web.text_model import Sentence

from birdy_fetcher.web.paths import WebPaths

from .text_fixtures import reviewed_record
from .web_fakes import FakeJsonClient, reply
from .web_repo import make_repo

NOW = datetime(2026, 11, 22, tzinfo=UTC)


def _blue_tit() -> Record:
    record = reviewed_record("Q25404")
    record["names"] = {"sv": "Blåmes", "en": "Eurasian Blue Tit", "scientific": "Cyanistes caeruleus"}
    record["slug"] = {"sv": "blames", "en": "eurasian-blue-tit"}
    return record


def _great_tit() -> Record:
    record = reviewed_record("Q25485")
    record["slug"] = {"sv": "talgoxe", "en": "great-tit"}
    return record


def _cell(text: str, *ids: str) -> Cell:
    return Cell(text=text, fact_ids=list(ids))


SV_COMPARE = CompareLang(
    short_answer=[Sentence(text="Talgoxen har svart huvud med vita kinder.", fact_ids=["b:f01"])],
    rows=[
        Row(feature="Huvud", a=_cell("Svart huvud med vita kinder", "a:f01"), b=_cell("Svart huvud med vita kinder", "b:f01")),
        Row(feature="Buk", a=_cell("Gul med svart band", "a:f02"), b=_cell("Gul med svart band", "b:f02")),
        Row(feature="Sång", a=_cell("Ringande ti ta, ti ta", "a:f04"), b=_cell("Ringande ti ta, ti ta", "b:f04")),
    ],
    meta_description=(
        "Talgoxe eller blåmes? Talgoxen är större och har svart huvud, blåmesen är mindre med "
        "blå hätta. Så skiljer du dem åt i fält."
    ),
)
EN_COMPARE = CompareLang(
    short_answer=[Sentence(text="The great tit has a black head with white cheeks.", fact_ids=["b:f01"])],
    rows=[
        Row(feature="Head", a=_cell("Black head with white cheeks", "a:f01"), b=_cell("Black head with white cheeks", "b:f01")),
        Row(feature="Belly", a=_cell("Yellow with a black stripe", "a:f02"), b=_cell("Yellow with a black stripe", "b:f02")),
        Row(feature="Song", a=_cell("A ringing tee cha, tee cha", "a:f04"), b=_cell("A ringing tee cha, tee cha", "b:f04")),
    ],
    meta_description=(
        "Great tit or blue tit? The great tit is larger with a black head, the blue tit is "
        "smaller with a blue cap. How to tell them apart."
    ),
)
COMPARE = CompareOutput(sv=SV_COMPARE, en=EN_COMPARE)


def _all_supported(text: CompareOutput) -> CheckOutput:
    ctx = pair_context(_blue_tit(), _great_tit())
    return CheckOutput(
        verdicts=[Verdict(id=i.id, supported=True, problem=None) for i in compare_items(text, ctx)]
    )


def _repo_with_pair(tmp_path: Path) -> WebPaths:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25404"), _blue_tit())
    save_record(record_path(paths.data_out, "Q25485"), _great_tit())
    paths.review.mkdir(parents=True, exist_ok=True)
    (paths.review / "comparison-volumes.csv").write_text(
        "a_qid,b_qid,a_sv,b_sv,a_en,b_en,sv_queries,en_queries,sv_volume,en_volume\n"
        "Q25404,Q25485,Blåmes,Talgoxe,x,x,x,x,1300,880\n",
        encoding="utf-8",
    )
    return paths


async def test_a_comparison_is_written_with_both_slugs_and_volumes(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    client = FakeJsonClient([reply(COMPARE), reply(_all_supported(COMPARE))])
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    saved = load_record(paths.comparisons_out / "Q25404_Q25485.json")
    assert saved is not None
    assert saved["a"] == "Q25404"
    assert saved["slug"] == {"sv": "blames-eller-talgoxe", "en": "eurasian-blue-tit-vs-great-tit"}
    assert saved["volumes"] == {"sv": 1300, "en": 880}
    assert saved["publish"] is False
    assert saved["text"]["sv"]["rows"][0]["a"] == {"text": "Svart huvud med vita kinder", "factIds": ["a:f01"]}


def test_a_cell_may_only_cite_its_own_species() -> None:
    from birdy_fetcher.web.compare import check_compare

    ctx = pair_context(_blue_tit(), _great_tit())
    wrong = SV_COMPARE.model_copy(
        update={"rows": [*SV_COMPARE.rows[:2], Row(feature="Sång", a=_cell("Ringande", "b:f04"), b=_cell("Ringande", "b:f04"))]}
    )
    issues = check_compare(CompareOutput(sv=wrong, en=EN_COMPARE), ctx, ["fascinerande"])
    assert [(i.path, i.removable) for i in issues] == [("sv.rows[2]", True)]


async def test_a_pair_waits_until_both_fact_sheets_are_reviewed(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    blue = _blue_tit()
    blue["review"] = {"wave": 2}
    save_record(record_path(paths.data_out, "Q25404"), blue)
    outcomes = await run_compare(paths, CompareOptions(), client=FakeJsonClient([]), now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]
```

- [ ] **Step 4: Kör och se dem falla**

Run: `uv run pytest tests/test_web_compare.py -v`
Expected: FAIL med `ImportError` för `Cell`, `CompareOptions` med flera.

- [ ] **Step 5: Implementera jämförelsetexterna**

Lägg till i `compare.py` (importerna överst, koden längst ned):

```python
import asyncio
import re
from datetime import UTC, datetime
from typing import Any

from anthropic.types import MessageParam
from pydantic import BaseModel

from ..claude_summarizer import _split_prompt
from ..cost import CostTracker, MaxCostExceeded
from .checked_writer import Checks, Written, write_checked
from .checker import CheckerFailed, CheckItem, SentenceChecker
from .checker import PROMPT_VERSION as CHECK_PROMPT_VERSION
from .checks import _style, load_banned, sentence_count
from .llm import MODELS, AnthropicJsonClient, JsonModelClient, ModelReply, record_cost
from .paths import WebPaths
from .record import facts_hash, is_reviewed, load_all, load_record, save_record
from .report import StepOutcome, render_step_report, write_step_report
from .text_checks import TextContext, TextIssue, sentence_issues
from .text_model import Sentence
from .text_step import render_facts, writer_facts
```

```python
PROMPT_VERSION = "compare-v1"
SHORT_MAX_WORDS = 45
SHORT_MAX_SENTENCES = 2
ROWS_MIN, ROWS_MAX = 3, 5
CELL_MAX_WORDS = 14
FEATURE_MAX_WORDS = 4
META_MIN, META_MAX = 120, 155


class Cell(BaseModel):
    text: str
    fact_ids: list[str]


class Row(BaseModel):
    feature: str
    a: Cell
    b: Cell


class CompareLang(BaseModel):
    short_answer: list[Sentence]
    rows: list[Row]
    meta_description: str


class CompareOutput(BaseModel):
    sv: CompareLang
    en: CompareLang


def prefixed_facts(a: Record, b: Record) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    def prefix(p: str, record: Record) -> list[dict[str, Any]]:
        return [{**f, "id": p + f["id"]} for f in writer_facts(record)]

    return prefix("a:", a), prefix("b:", b)


def pair_context(a: Record, b: Record) -> TextContext:
    a_facts, b_facts = prefixed_facts(a, b)
    return TextContext.from_facts(a_facts + b_facts)


def _check_lang(lang: str, t: CompareLang, ctx: TextContext, banned: list[str]) -> list[TextIssue]:
    issues: list[TextIssue] = []
    for i, sentence in enumerate(t.short_answer):
        issues += sentence_issues(f"{lang}.short_answer[{i}]", lang, sentence, ctx, banned)
    words = sum(len(s.text.split()) for s in t.short_answer)
    sentences = sum(sentence_count(s.text) for s in t.short_answer)
    if not t.short_answer:
        issues.append(TextIssue(f"{lang}.short_answer", "saknas", False))
    elif words > SHORT_MAX_WORDS or sentences > SHORT_MAX_SENTENCES:
        issues.append(TextIssue(f"{lang}.short_answer", "ska vara högst 2 meningar och 45 ord", False))
    if not ROWS_MIN <= len(t.rows) <= ROWS_MAX:
        issues.append(TextIssue(f"{lang}.rows", "ska ha 3 till 5 rader", False))
    for i, row in enumerate(t.rows):
        path = f"{lang}.rows[{i}]"
        problems = [x.message for x in _style(path, lang, row.feature, banned)]
        if len(row.feature.split()) > FEATURE_MAX_WORDS:
            problems.append("kännetecknet ska vara 1 till 4 ord")
        for side in ("a", "b"):
            cell: Cell = getattr(row, side)
            if len(cell.text.split()) > CELL_MAX_WORDS:
                problems.append(f"{side} ska vara högst 14 ord")
            sentence = Sentence(text=cell.text, fact_ids=cell.fact_ids)
            problems += [
                x.message
                for x in sentence_issues(path, lang, sentence, ctx, banned, prefix=f"{side}:")
            ]
        issues += [TextIssue(path, message, True) for message in problems]
    meta_path = f"{lang}.meta_description"
    issues += [TextIssue(meta_path, x.message, False) for x in _style(meta_path, lang, t.meta_description, banned)]
    if not META_MIN <= len(t.meta_description) <= META_MAX:
        issues.append(TextIssue(meta_path, f"ska vara 120 till 155 tecken (är {len(t.meta_description)})", False))
    return issues


def check_compare(text: CompareOutput, ctx: TextContext, banned: list[str]) -> list[TextIssue]:
    return _check_lang("sv", text.sv, ctx, banned) + _check_lang("en", text.en, ctx, banned)


_COMPARE_PATH = re.compile(r"^(sv|en)\.(short_answer|rows)\[(\d+)\]")


def remove_compare_paths(text: CompareOutput, paths: set[str]) -> CompareOutput:
    data = text.model_dump()
    drops: dict[tuple[str, str], set[int]] = {}
    for path in paths:
        match = _COMPARE_PATH.match(path)
        if match is not None:
            lang, name, index = match.groups()
            drops.setdefault((lang, name), set()).add(int(index))
    for (lang, name), indexes in drops.items():
        data[lang][name] = [x for k, x in enumerate(data[lang][name]) if k not in indexes]
    return CompareOutput.model_validate(data)


def settle_compare(
    text: CompareOutput, ctx: TextContext, banned: list[str]
) -> tuple[CompareOutput, list[str], list[str]]:
    issues = check_compare(text, ctx, banned)
    removable = {i.path for i in issues if i.removable}
    notes = [f"{i.path} togs bort: {i.message}" for i in issues if i.removable]
    if removable:
        text = remove_compare_paths(text, removable)
        issues = check_compare(text, ctx, banned)
    return text, notes, [f"{i.path}: {i.message}" for i in issues]


def compare_items(text: CompareOutput, ctx: TextContext) -> list[CheckItem]:
    def facts(ids: list[str]) -> tuple[dict[str, Any], ...]:
        return tuple(ctx.facts_by_id[f] for f in ids if f in ctx.facts_by_id)

    items: list[CheckItem] = []
    for lang in ("sv", "en"):
        t: CompareLang = getattr(text, lang)
        for i, s in enumerate(t.short_answer):
            items.append(CheckItem(f"{lang}.short_answer[{i}]", s.text, facts(s.fact_ids)))
        for i, row in enumerate(t.rows):
            for side in ("a", "b"):
                cell: Cell = getattr(row, side)
                items.append(
                    CheckItem(f"{lang}.rows[{i}].{side}", f"{row.feature}: {cell.text}", facts(cell.fact_ids))
                )
    return items


def compare_minimum(text: CompareOutput) -> list[str]:
    problems: list[str] = []
    for lang in ("sv", "en"):
        t: CompareLang = getattr(text, lang)
        if not t.short_answer:
            problems.append(f"{lang}.short_answer saknas")
        if len(t.rows) < ROWS_MIN:
            problems.append(f"{lang}.rows har färre än 3 rader")
        if not META_MIN <= len(t.meta_description) <= META_MAX:
            problems.append(f"{lang}.meta_description har fel längd")
    return problems


def _cells(t: CompareLang) -> dict[str, Any]:
    return {
        "shortAnswer": [{"text": s.text, "factIds": list(s.fact_ids)} for s in t.short_answer],
        "rows": [
            {
                "feature": r.feature,
                "a": {"text": r.a.text, "factIds": list(r.a.fact_ids)},
                "b": {"text": r.b.text, "factIds": list(r.b.fact_ids)},
            }
            for r in t.rows
        ],
        "metaDescription": t.meta_description,
    }


def render_compare_prompt(template: str, a: Record, b: Record, banned: list[str]) -> tuple[str, str]:
    a_facts, b_facts = prefixed_facts(a, b)
    return _split_prompt(
        template,
        a_sv=a["names"]["sv"],
        a_en=a["names"]["en"],
        a_scientific=a["names"]["scientific"],
        b_sv=b["names"]["sv"],
        b_en=b["names"]["en"],
        b_scientific=b["names"]["scientific"],
        banned_phrases=", ".join(banned),
        a_facts=render_facts(a_facts),
        b_facts=render_facts(b_facts),
    )


@dataclass
class ComparisonWriter:
    client: JsonModelClient
    cost: CostTracker
    checker: SentenceChecker
    prompt_path: Path
    banned: list[str]
    model_key: str = "opus"
    effort: str = "high"

    async def _ask(self, system: str, messages: list[MessageParam]) -> ModelReply[CompareOutput]:
        reply = await self.client.complete(
            model=MODELS[self.model_key],
            system=system,
            messages=messages,
            effort=self.effort,
            schema=CompareOutput,
        )
        record_cost(self.cost, self.model_key, reply)
        return reply

    async def write(self, a: Record, b: Record) -> Written[CompareOutput]:
        ctx = pair_context(a, b)
        template = self.prompt_path.read_text(encoding="utf-8")
        system, user = render_compare_prompt(template, a, b, self.banned)
        checks: Checks[CompareOutput] = Checks(
            rules=lambda t: check_compare(t, ctx, self.banned),
            settle=lambda t: settle_compare(t, ctx, self.banned),
            items=lambda t: compare_items(t, ctx),
            remove=remove_compare_paths,
            minimum=compare_minimum,
        )
        return await write_checked(
            ask=lambda messages: self._ask(system, messages),
            user=user,
            checks=checks,
            checker=self.checker,
        )


@dataclass(frozen=True)
class CompareOptions:
    top: int = TOP
    model_key: str = "opus"
    effort: str = "high"
    checker_key: str = "sonnet"
    max_cost: float | None = None
    regenerate: bool = False
    workers: int = 4


async def run_compare(
    paths: WebPaths,
    options: CompareOptions,
    *,
    client: JsonModelClient | None = None,
    now: datetime | None = None,
) -> list[StepOutcome]:
    now = now or datetime.now(UTC)
    records = load_all(paths.data_out)
    volumes = read_volumes(paths.review / VOLUMES_FILE)
    pairs = select_pairs(volumes, options.top)
    owned = client is None
    model_client: JsonModelClient = client or AnthropicJsonClient()
    cost = CostTracker(max_usd=options.max_cost)
    checker = SentenceChecker(
        client=model_client,
        cost=cost,
        prompt_path=paths.prompt_file(CHECK_PROMPT_VERSION),
        model_key=options.checker_key,
    )
    writer = ComparisonWriter(
        client=model_client,
        cost=cost,
        checker=checker,
        prompt_path=paths.prompt_file(PROMPT_VERSION),
        banned=load_banned(paths.banned),
        model_key=options.model_key,
        effort=options.effort,
    )
    stop = asyncio.Event()
    semaphore = asyncio.Semaphore(options.workers)

    async def one(pair: Pair) -> StepOutcome:
        label = f"{pair.a}_{pair.b}"
        async with semaphore:
            if pair.a not in records or pair.b not in records:
                return StepOutcome(label, label, "skipped", ["en av arterna saknar artpost"])
            a, b = records[pair.a], records[pair.b]
            name = f"{a['names']['sv']} eller {b['names']['sv']}"
            if not (is_reviewed(a) and is_reviewed(b)):
                return StepOutcome(label, name, "skipped", ["väntar på att båda faktabladen granskas"])
            path = comparison_path(paths.comparisons_out, pair)
            existing = load_record(path)
            both = facts_hash(a) + facts_hash(b)
            current = (
                existing is not None
                and existing.get("status") == "ok"
                and existing.get("generated", {}).get("factsHash") == both
            )
            if current and not options.regenerate:
                return StepOutcome(label, name, "skipped", ["texten är redan skriven ur samma faktablad"])
            if stop.is_set():
                return StepOutcome(label, name, "skipped", ["kostnadstaket nåddes"])
            try:
                result = await writer.write(a, b)
            except MaxCostExceeded as exc:
                stop.set()
                return StepOutcome(label, name, "skipped", [f"kostnadstaket nåddes: {exc}"])
            except CheckerFailed as exc:
                return StepOutcome(label, name, "failed", [str(exc)])
            sv_volume, en_volume = volumes[pair]
            record: Record = {
                "a": pair.a,
                "b": pair.b,
                "status": "ok" if result.text is not None else "failed",
                "publish": bool(existing.get("publish")) if existing else False,
                "slug": comparison_slugs(pair, records),
                "volumes": {"sv": sv_volume, "en": en_volume},
                "text": (
                    {lang: _cells(getattr(result.text, lang)) for lang in ("sv", "en")}
                    if result.text is not None
                    else None
                ),
                "generated": {
                    "model": MODELS[options.model_key],
                    "prompt": PROMPT_VERSION,
                    "checker": MODELS[options.checker_key],
                    "at": now.isoformat(),
                    "factsHash": both,
                },
                "errors": result.errors,
            }
            if result.text is None and result.rejected is not None:
                record["rejectedText"] = {
                    lang: _cells(getattr(result.rejected, lang)) for lang in ("sv", "en")
                }
            save_record(path, record)
            return StepOutcome(label, name, str(record["status"]), result.errors, result.notes)

    try:
        outcomes = list(await asyncio.gather(*(one(p) for p in pairs)))
    finally:
        if owned and isinstance(model_client, AnthropicJsonClient):
            await model_client.aclose()
    report = render_step_report(
        title="Jämförelser",
        date=now.date().isoformat(),
        outcomes=outcomes,
        cost_usd=cost.total_usd,
        model_line=f"Skribent: `{MODELS[options.model_key]}`. Kontroll: `{MODELS[options.checker_key]}`.",
    )
    write_step_report(paths.reports, "compare", now, report)
    return outcomes
```

- [ ] **Step 6: Skriv `prompts/compare-v1.md`**

```markdown
# compare prompt v1 (artsidor, spec 2026-09-25 reviderad 2026-10-01, avsnitt 7 och 9.7)

System: You write a short comparison of two bird species that are easy to confuse, for birdy.community, in Swedish and English, from two reviewed fact sheets. Facts about species A have ids starting with a:, facts about species B ids starting with b:.

Source rule: the facts in the user message are your only source. Every sentence and every table cell lists the ids of the facts it is built on (fact_ids) and says nothing those facts do not say.

Style rules for both languages:
- Plain, concrete words, like a knowledgeable friend, not a brochure.
- No dashes of any kind: no em dash, no en dash, no double hyphen. Use a comma, a full stop or a colon instead.
- No exclamation marks. No first person. No questions to the reader except the bird names in meta_description.
- Never use any of these words or phrases: {banned_phrases}
- Natural Swedish written by a Swede, natural English. Use the bird names given in the user message.
- Do not mention Birdy, apps, photos or Wikipedia. Write numbers as digits exactly as the facts give them.

Fields, for each language:
- short_answer: 1 or 2 sentences, at most 45 words together: the quickest way to tell the two apart in the field. It may cite facts from both species.
- rows: 3 to 5 rows. feature names what to compare in 1 to 4 words (for example "Storlek", "Huvud", "Sång", or "Size", "Head", "Song"). a describes species A and cites only a: facts; b describes species B and cites only b: facts; at most 14 words each. Only compare features that both fact sheets cover.
- meta_description: 120 to 155 characters including spaces, without fact ids. Start with "{a_sv} eller {b_sv}?" in Swedish and "{a_en} or {b_en}?" in English, then say how to tell them apart.

User: Species A: {a_sv} / {a_en} ({a_scientific}). Species B: {b_sv} / {b_en} ({b_scientific}).

Facts about species A:
{a_facts}

Facts about species B:
{b_facts}
```

Platshållarna `{a_sv}`, `{b_sv}`, `{a_en}` och `{b_en}` i systemdelen fylls i av `_split_prompt` precis som i användardelen.

- [ ] **Step 7: Kommandot i `cli.py`**

```python
@web.command("compare")
@click.option("--top", type=click.IntRange(min=1), default=30, help="Antal par med störst sökvolym.")
@click.option("--model", "model_key", type=click.Choice(["opus", "sonnet"]), default="opus")
@click.option("--effort", type=click.Choice(["low", "medium", "high"]), default="high")
@click.option("--checker-model", "checker_key", type=click.Choice(["opus", "sonnet"]), default="sonnet")
@click.option("--max-cost", type=float, default=None, help="Kostnadstak i USD för körningen.")
@click.option("--regenerate", is_flag=True, help="Skriv om även jämförelser som är aktuella.")
@click.option("--workers", type=click.IntRange(min=1), default=4)
def web_compare(
    top: int,
    model_key: str,
    effort: str,
    checker_key: str,
    max_cost: float | None,
    regenerate: bool,
    workers: int,
) -> None:
    """Jämförelsetexter för de mest sökta förväxlingsparen. Kostar pengar."""
    from .web.compare import CompareOptions, run_compare

    _require_api_key()
    paths = _web_paths()
    options = CompareOptions(
        top=top,
        model_key=model_key,
        effort=effort,
        checker_key=checker_key,
        max_cost=max_cost,
        regenerate=regenerate,
        workers=workers,
    )
    _print_outcomes(asyncio.run(run_compare(paths, options)), paths.reports)
```

- [ ] **Step 8: Kör testerna**

Run: `uv run pytest tests/test_web_compare.py tests/test_web_text_step.py -v`
Expected: PASS

- [ ] **Step 9: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/checked_writer.py src/birdy_fetcher/web/text_step.py src/birdy_fetcher/web/compare.py prompts/compare-v1.md src/birdy_fetcher/cli.py tests/test_web_compare.py
git commit -m "feat(pipeline): delad skrivslinga och jämförelsetexter för förväxlingspar"
```

---

### Task 23: Publicering, `web publish`

**Files:**
- Modify: `src/birdy_fetcher/web/waves.py` (lägg till), `src/birdy_fetcher/cli.py`
- Test: `tests/test_web_waves.py` (lägg till)

En art publiceras när den hör till vågen, har `status: "ok"`, ett granskat faktablad och en text som inte skrevs med `--allow-unreviewed`. En jämförelse publiceras när den är `ok` och båda arterna är publicerade.

- [ ] **Step 1: Skriv de fallerande testerna**

Lägg till i `tests/test_web_waves.py` (och `publish_wave` i importen från `birdy_fetcher.web.waves`):

```python
def _ready(qid: str, name: str, wave: int) -> Record:
    record = _record(qid, name, RESIDENT, 0)
    record["status"] = "ok"
    record["review"] = {"facts": {"by": "Albin Abrahamsson", "at": "2026-11-20"}, "wave": wave}
    record["generated"] = {"text": {"factsHash": "x"}}
    return record


def test_publish_turns_on_ready_species_and_their_comparisons(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    ready = _ready("Q1", "Talgoxe", 1)
    partner = _ready("Q2", "Blåmes", 1)
    unreviewed = _ready("Q3", "Koltrast", 1)
    unreviewed["generated"]["text"]["unreviewed"] = True
    later = _ready("Q4", "Ladusvala", 3)
    failed = _ready("Q5", "Kaja", 1)
    failed["status"] = "failed"
    for record in (ready, partner, unreviewed, later, failed):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    for a, b in (("Q1", "Q2"), ("Q1", "Q4")):
        save_record(
            paths.comparisons_out / f"{a}_{b}.json",
            {"a": a, "b": b, "status": "ok", "publish": False},
        )
    outcomes = publish_wave(paths, 1)
    published = {qid for qid in ("Q1", "Q2", "Q3", "Q4", "Q5")
                 if (load_record(record_path(paths.data_out, qid)) or {}).get("publish")}
    assert published == {"Q1", "Q2"}
    pair = load_record(paths.comparisons_out / "Q1_Q2.json")
    other = load_record(paths.comparisons_out / "Q1_Q4.json")
    assert pair is not None and pair["publish"] is True
    assert other is not None and other["publish"] is False
    assert {o.qid: o.status for o in outcomes}["Q3"] == "skipped"
```

- [ ] **Step 2: Kör och se dem falla**

Run: `uv run pytest tests/test_web_waves.py -v`
Expected: FAIL med `ImportError` för `publish_wave`.

- [ ] **Step 3: Implementera**

Lägg till i `waves.py` (importera `is_reviewed` och `load_record` från `.record` och `StepOutcome` från `.report`):

```python
def _ready(record: Record) -> bool:
    text = (record.get("generated") or {}).get("text") or {}
    return record.get("status") == "ok" and is_reviewed(record) and not text.get("unreviewed")


def publish_wave(paths: WebPaths, wave: int) -> list[StepOutcome]:
    """Turns `publish` on for the wave's finished species, then for every finished comparison
    whose two species are both published. Nothing is ever turned off here."""
    outcomes: list[StepOutcome] = []
    records = load_all(paths.data_out)
    for qid, record in sorted(records.items()):
        if record.get("review", {}).get("wave") != wave:
            continue
        name = _name(record)
        if _ready(record):
            record["publish"] = True
            save_record(record_path(paths.data_out, qid), record)
            outcomes.append(StepOutcome(qid, name, "ok"))
        else:
            reason = f"inte klar: status {record.get('status')}, granskad {is_reviewed(record)}"
            outcomes.append(StepOutcome(qid, name, "skipped", [reason]))
    published = {qid for qid, record in records.items() if record.get("publish")}
    for path in sorted(paths.comparisons_out.glob("Q*_Q*.json")):
        comparison = load_record(path)
        if comparison is None or comparison.get("publish"):
            continue
        both = comparison.get("a") in published and comparison.get("b") in published
        if comparison.get("status") == "ok" and both:
            comparison["publish"] = True
            save_record(path, comparison)
            outcomes.append(StepOutcome(path.stem, path.stem, "ok", notes=["jämförelse"]))
    return outcomes
```

- [ ] **Step 4: Kommandot i `cli.py`**

```python
@web.command("publish")
@click.option("--wave", type=click.IntRange(1, 3), required=True)
def web_publish(wave: int) -> None:
    """Slår på publish för vågens färdiga arter och jämförelser. Körs efter Albins godkännande."""
    from .web.waves import publish_wave

    paths = _web_paths()
    _print_outcomes(publish_wave(paths, wave), paths.reports)
```

- [ ] **Step 5: Kör testerna**

Run: `uv run pytest tests/test_web_waves.py -v`
Expected: PASS

- [ ] **Step 6: Lint, typer och commit**

Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`

```bash
git add src/birdy_fetcher/web/waves.py src/birdy_fetcher/cli.py tests/test_web_waves.py
git commit -m "feat(pipeline): web publish slår på en färdig våg och dess jämförelser"
```

---

### Task 24: Ta bort det gamla enkla skrivpasset

**Files:**
- Delete: `src/birdy_fetcher/web/writer.py`, `src/birdy_fetcher/web/model.py`, `src/birdy_fetcher/web/output.py`, `src/birdy_fetcher/web/run.py`, `prompts/web-v1.md`, `tests/test_web_writer.py`, `tests/test_web_output.py`, `tests/test_web_run.py`, `tests/test_web_checks_text.py`, `tests/test_web_checks_facts.py`, `tests/web_fixtures.py`
- Modify: `src/birdy_fetcher/web/checks.py`, `src/birdy_fetcher/web/report.py`, `src/birdy_fetcher/web/paths.py`, `src/birdy_fetcher/cli.py`, `tests/test_web_report.py`, `tests/test_cli_smoke.py`, `tests/web_repo.py`
- Create: `tests/test_web_checks.py`

- [ ] **Step 1: Tester för hjälpfunktionerna som stannar i `checks.py`**

`tests/test_web_checks.py`:

```python
"""Tests for the helpers in web/checks.py that the new steps use."""

from __future__ import annotations

from birdy_fetcher.web.checks import _style, banned_hits, quote_in_sources, sentence_count


def test_style_flags_dashes_exclamations_first_person_and_banned_words() -> None:
    issues = _style("sv.x", "sv", "Vi såg en fantastisk fågel \u2014 den sjöng!", ["fantastisk"])
    messages = [i.message for i in issues]
    assert "innehåller tankstreck eller --" in messages
    assert "innehåller utropstecken" in messages
    assert any("första person" in m for m in messages)
    assert any("fantastisk" in m for m in messages)


def test_style_flags_an_empty_text() -> None:
    assert [i.message for i in _style("en.x", "en", "   ", [])] == ["är tom"]


def test_banned_hits_match_whole_words_only() -> None:
    assert banned_hits("En unik fågel.", ["unik"]) == ["unik"]
    assert banned_hits("Unikum.", ["unik"]) == []


def test_sentence_count_ignores_abbreviations() -> None:
    assert sentence_count("Den äter bl.a. frön. Den häckar i hål.") == 2


def test_quote_in_sources_normalizes_quotes_dashes_and_spaces() -> None:
    source = 'Kroppslängden är "28-31 cm" hos vuxna fåglar.'
    assert quote_in_sources("Kroppslängden är \u201c28\u201331 cm\u201d  hos vuxna", [source])
    assert not quote_in_sources("kort", ["kort text"])
```

Run: `uv run pytest tests/test_web_checks.py -v`
Expected: PASS (hjälpfunktionerna finns redan).

- [ ] **Step 2: Rensa `checks.py`**

Ta bort `from .model import LangText, WebTextOutput` och allt som bara det gamla skrivpasset använde: konstanterna `LEAD_MAX_WORDS`, `LEAD_MAX_SENTENCES`, `MARKS_MIN`, `MARKS_MAX`, `MARK_MAX_WORDS`, `VOICE_MAX_WORDS`, `WHERE_MAX_WORDS`, `META_MIN`, `META_MAX`, funktionerna `_check_lang`, `check_text`, `_is_wingspan_not_length`, `_status_flagged`, `check_facts`, `drop_facts` samt `_PRESENCE`, `_SWEDEN`, `_NEGATION`, `_WINGSPAN_RE` och `_LENGTH_RE`. Kvar blir `LANGS`, `DASHES`, `FIRST_PERSON`, `Issue`, `load_banned`, `banned_hits`, `_SENTENCE_SPLIT`, `_split_sentences`, `sentence_count`, `_words`, `_style`, `MIN_QUOTE_CHARS`, `_QUOTE_CHARS`, `_DASH_TO_HYPHEN`, `_normalize` och `quote_in_sources`. Ändra modulens docstring till `"""Style and quote checks shared by the fact sheet, text and comparison steps (spec §9.6)."""`.

- [ ] **Step 3: Rensa resten**

```bash
git rm src/birdy_fetcher/web/writer.py src/birdy_fetcher/web/model.py src/birdy_fetcher/web/output.py src/birdy_fetcher/web/run.py prompts/web-v1.md tests/test_web_writer.py tests/test_web_output.py tests/test_web_run.py tests/test_web_checks_text.py tests/test_web_checks_facts.py tests/web_fixtures.py
```

- I `report.py`: ta bort `SpeciesOutcome` och `render_report` (bara stegrapporten blir kvar). I `tests/test_web_report.py`: ta bort testerna för `render_report` och behåll `test_step_report_lists_failures_skips_and_notes`.
- I `paths.py`: ta bort egenskapen `prompt` (web-v1).
- I `tests/web_repo.py`: ta bort `"web-v1"` ur `PROMPTS`.
- I `cli.py`: ta bort kommandot `web_v1` och dess importer.
- I `tests/test_cli_smoke.py`: ta bort de tre testerna för `web v1` och lägg till:

```python
def test_web_lists_every_step() -> None:
    result = CliRunner().invoke(main, ["web", "--help"])
    assert result.exit_code == 0
    for step in (
        "sources", "facts", "waves", "sheet", "import", "write",
        "compare-candidates", "compare", "publish",
    ):  # fmt: skip
        assert step in result.output
```

- [ ] **Step 4: Kör allt**

Run: `uv run pytest -q`
Expected: PASS. Run: `uv run ruff check --fix . && uv run ruff format . && uv run mypy`
Expected: rent, mypy bara med de 6 gamla felen. Run: `grep -rn "web_fixtures\|from .writer\|from .model\|from .output\|web.run import" src tests`
Expected: inga träffar.

- [ ] **Step 5: Commit**

```bash
git add -A src tests prompts
git commit -m "chore(pipeline): det gamla enkla skrivpasset är borta"
```

---

## Körtaskar (kräver Albin eller kostar pengar)

Kör dem i ordning. Varje körtask slutar med commit och push av data och rapporter, och en rad i CLAUDE.md:s status (synkregeln).

### R1: Före första betalda körningen

- [ ] Albin fyller på **200 USD** i Anthropic Console för nyckeln i `ANTHROPIC_API_KEY` (räknat 2026-10-01: cirka 145 USD för hela körningen, resten är marginal för provkörning och omkörningar).
- [ ] Kontrollera att nyckeln syns: `uv run python -c "import os; print(bool(os.environ.get('ANTHROPIC_API_KEY')))"` ska skriva `True`. Annars läggs nyckeln i `tools/content-pipeline/.env` och alla betalda kommandon körs som `uv run --env-file .env birdy-fetcher web ...`.

### R2: Källor för alla 180 och kalibrering av statussignalen (gratis)

- [ ] Kör i bakgrunden (cirka 25 minuter, Wikimedia stryps till en förfrågan per sekund): `uv run birdy-fetcher web sources`
- [ ] Läs `reports/web-sources-*.md`. Notera arter utan GBIF-träff, utan diagram (under 200 rapporter), med okänd rödlistekategori och utan inspelning. Kör om misslyckade arter med `--species`.
- [ ] Kalibrera statussignalen mot fyra kända arter bland de 180 (QID kontrollerade 2026-10-01): talgoxe Q25485 (stannfågel), ladusvala Q25429 (flyttfågel), sidensvans Q26135 (vintergäst) och koboltmes Q10546857 (förekommer inte i Sverige).

Kör:

```bash
uv run python -c "
import json, pathlib
from birdy_fetcher.web.datamod import status_contradiction
expected = {'Q25485': 'resident', 'Q25429': 'breeding_migrant', 'Q26135': 'winter_visitor', 'Q10546857': 'absent'}
for qid, status in expected.items():
    p = pathlib.Path('../../website/src/data/species') / f'{qid}.json'
    if not p.exists(): print(qid, 'saknas'); continue
    d = json.loads(p.read_text(encoding='utf-8')).get('data') or {}
    print(qid, status, d.get('months'), status_contradiction(status, d.get('months'), d.get('totalReports', 0)))
"
```

Expected: `None` sist på varje rad. Flaggas en art fel: justera konstanten i `datamod.py`, lägg till ett test i `tests/test_web_datamod.py` med artens riktiga månadsprofil och kör om.
- [ ] Kontrollera storleken: `du -sh ../../website/src/assets/species` (förväntat cirka 70 MB: foton 39 MB och inspelningar cirka 30 MB).
- [ ] Commit och push:

```bash
git add ../../website/src/data/species ../../website/src/assets/species reports
git commit -m "data(artsidor): källor, rapportdata, rödlista och inspelningar för 180 arter"
git push
```

### R3: Provkörning på fyra arter (cirka 5 USD)

- [ ] `uv run birdy-fetcher web facts --species Q25485 --species Q25383 --species Q25386 --species Q10546857 --max-cost 5`
- [ ] `uv run birdy-fetcher web write --species Q25485 --species Q25383 --species Q25386 --species Q10546857 --allow-unreviewed --max-cost 5`
- [ ] Läs rapporterna `reports/web-facts-*.md` och `reports/web-text-*.md`. Räkna ut kostnad per art för faktablad och text. Visa Albin i chatten: talgoxens faktablad (fakta med citat), talgoxens text på svenska och engelska, borttagna meningar och kostnaden per art omräknad till 180 arter.
- [ ] **Albin väljer** modell och tankenivå för faktablad, text och kontroll (standard: Opus 5 `high` för faktablad och text, Sonnet 5 för kontrollen). Ändras något: ändra standardvärdena i `cli.py` och prompterna innan R4, och kör om provkörningen.
- [ ] Commit och push (texterna från provkörningen är märkta `unreviewed` och kan aldrig publiceras).

### R4: Faktablad för alla 180 (cirka 75 USD)

- [ ] `uv run birdy-fetcher web facts --max-cost 120`
- [ ] Läs rapporten. Arter med `failed`: kör om en gång med `--regenerate --species ...`. Arter som fortfarande misslyckas listas för Albin.
- [ ] Commit och push.

### R5: Vågor och granskning av våg 1 (Albin, cirka 2 timmar)

- [ ] `uv run birdy-fetcher web waves` och visa Albin våg 1:s 40 arter i chatten. Albin byter arter om han vill; ändra `review/waves.json` och kör `uv run birdy-fetcher web waves` igen.
- [ ] `uv run birdy-fetcher web sheet --wave 1`
- [ ] Ladda upp `review/wave-1-ark.csv` till Albins Google Drive som Google-kalkylark med Google Drive-verktyget, och ge Albin länken med en kort instruktion: ändra bara kolumnen Beslut (och Faktum vid `ändra`), fyll i alla flaggor, lyssna på inspelningarna.
- [ ] När Albin säger att han är klar: exportera kalkylarket som CSV till `review/wave-1.csv`, kör `uv run birdy-fetcher web import --wave 1`. Rättar Albin fel som importen hittar, kör om.
- [ ] Commit och push.

### R6: Text för våg 1 (cirka 25 USD)

- [ ] `uv run birdy-fetcher web write --wave 1 --max-cost 40`
- [ ] Läs rapporten. Visa Albin tre slumpvisa texter och alla arter med `failed`. Kör om misslyckade arter en gång med `--regenerate --species ...`.
- [ ] Commit och push.

### R7: Jämförelser för våg 1 (cirka 5 USD)

- [ ] `uv run birdy-fetcher web compare-candidates`
- [ ] **Fråga Albin innan** sökordsplaneraren används i hans Google Ads-konto. Fyll sedan i `sv_volume` (Sverige, svenska) och `en_volume` (Storbritannien, engelska) i `review/comparison-volumes.csv`: summan av de genomsnittliga månadssökningarna för parets fyra svenska respektive tre engelska sökningar. Ger planeraren ett intervall, använd mitten.
- [ ] `uv run birdy-fetcher web compare --top 30 --max-cost 15` (par där båda arterna inte är granskade hoppas över och skrivs i en senare våg).
- [ ] Commit och push.

### R8: Överlämning till sidorna och go-live för våg 1

- [ ] Fas 2-planens förhandsvisning (Vercel Preview med `SPECIES_PREVIEW=1`) visar våg 1. Albin läser jämförelsesidorna och skummar artsidorna.
- [ ] Efter Albins godkännande: `uv run birdy-fetcher web publish --wave 1`, commit och push. Fas 2-planen tar sammanslagningen och go-live därifrån.
- [ ] CLAUDE.md: status för våg 1 (datum, antal sidor, kostnad).

### R9: Våg 2 och 3

Upprepa R5 till R8 för våg 2 (mål 15 januari 2027) och våg 3 (mål 26 februari 2027), med `--wave 2` respektive `--wave 3`. Kör `web compare-candidates` och `web compare` igen i varje våg: par som väntade på en art i den nya vågen skrivs då.

---

## Självgranskning mot specen

| Spec | Var i planen |
|---|---|
| §9.1 Wikipedia sv, en, de | Task 1, 2 |
| §9.1 Artportalen via GBIF, CC0, 2016 till 2025 | Task 3, 6 |
| §9.1 Svenska rödlistan 2025 | Task 6 |
| §9.1 Inspelning, filter, 20 s MP3 i `src/assets` | Task 7, 8, 12 |
| §9.1 Appens modeller | Task 9, 12 |
| §9.2 Andelar, meningar, statussignal, för lite data | Task 3, 4, 5, R2 (kalibrering) |
| §9.3 Faktablad med citat, status s01, datafakta | Task 13, 14 |
| §9.4 Granskningsark, import, `review.facts` | Task 16, 17, R5 |
| §9.5 Text ur godkända fakta, fakta-id per mening | Task 18, 19, 20 |
| §9.6 Kodkontroller och andra modellen, omskrivning, borttagning | Task 18, 19, 20, 22 |
| §9.7 Jämförelsetexter | Task 21, 22 |
| §9.8 Provkörning, kostnadstak, rapport | Task 11, 14, 20, 22, R3 |
| §9.9 Utdata, skriv aldrig över granskat | Task 11, 14, 20 |
| §10 Licenser (CC BY-SA, LICENSE.md, CC0-filter, licenstabell, credits) | Task 6, 7, 11 (byggkontrollen av credits ligger i fas 2) |
| §14 Vågor, `publish`, granskad text | Task 15, 23, R5 till R9 |
| §15 Baslinje, UTM, egna besök, länkutskick | Fas 2-planen och R8 |
| Bilaga C och D (schema) | Task 11, 12, 13, 20, 22 |
| Bilaga E (granskningsarket) | Task 16 |

Det som avviker från specens ord, och varför (redan infört i specen 2026-10-01): statusen tas fram i faktabladet i stället för av skribenten, så att Albin granskar den och flaggan syns i arket; inspelningar ligger i `website/src/assets/species/` och inte i `public/`; en sökträff måste nämna arten (ett xeno-canto-nummer räcker inte); våglistan justeras i chatten innan första arket.
