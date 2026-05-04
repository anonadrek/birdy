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
    fixture = (fixtures_dir / "wikidata_q25485.json").read_text()

    async def fake_sparql(query: str) -> str:
        return fixture

    cache = Cache(tmp_path)
    client = WikidataClient(cache=cache, run_sparql=fake_sparql)

    result = await client.fetch_structured("Q25485")
    assert result.q_id == "Q25485"
    assert result.family == "Paridae"
    assert result.family_sv == "Mesar"
    assert result.genus == "Parus"
    assert result.ioc_order == "Passeriformes"
    assert result.iucn_status == "LC"
    assert result.image_filename == "Parus major - Mindelheim - 2012.jpg"
    assert result.common_sv == "Talgoxe"


@pytest.mark.asyncio
async def test_fetch_structured_handles_missing_sv_labels(
    fixtures_dir: Path,
    tmp_path: Path,
) -> None:
    fixture = (fixtures_dir / "wikidata_no_sv_label.json").read_text()

    async def fake_sparql(query: str) -> str:
        return fixture

    cache = Cache(tmp_path)
    client = WikidataClient(cache=cache, run_sparql=fake_sparql)

    result = await client.fetch_structured("Q999")
    assert result.family == "Mysteriidae"
    assert result.family_sv is None
    assert result.common_sv is None


@pytest.mark.asyncio
async def test_fetch_structured_uses_cache_on_second_call(
    fixtures_dir: Path,
    tmp_path: Path,
) -> None:
    fixture = (fixtures_dir / "wikidata_q25485.json").read_text()

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
    fixture = (fixtures_dir / "wikidata_q25485.json").read_text()

    call_count = {"n": 0}

    async def counting_sparql(query: str) -> str:
        call_count["n"] += 1
        return fixture

    cache = Cache(tmp_path)
    client = WikidataClient(cache=cache, run_sparql=counting_sparql)
    await client.fetch_structured("Q25485")
    await client.fetch_structured("Q25485", force=True)
    assert call_count["n"] == 2
