"""Tests for wikipedia.py — REST extract fetching, revision-keyed caching."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.cache import Cache
from birdy_fetcher.wikipedia import WikipediaClient


@pytest.mark.asyncio
async def test_fetch_extract_returns_intro(fixtures_dir: Path, tmp_path: Path) -> None:
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
    result = await client.fetch_extract("Q99999", title_by_lang={"sv": "Some bird"}, lang="sv")
    assert result.is_sparse
    assert result.word_count < 100


@pytest.mark.asyncio
async def test_fetch_extract_handles_missing_article(tmp_path: Path) -> None:
    async def fake_get(url: str) -> str:
        raise FileNotFoundError("404 from REST API")

    cache = Cache(tmp_path)
    client = WikipediaClient(cache=cache, http_get=fake_get)
    result = await client.fetch_extract("Q99999", title_by_lang={"sv": "Nonexistent"}, lang="sv")
    assert result.extract == ""
    assert result.is_sparse
    assert result.revision is None


@pytest.mark.asyncio
async def test_fetch_extract_caches_per_revision(fixtures_dir: Path, tmp_path: Path) -> None:
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
