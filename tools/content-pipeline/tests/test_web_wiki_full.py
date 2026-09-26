"""Tests for web/wiki_full.py: sitelinks by QID and full plain-text articles."""

from __future__ import annotations

import json
from pathlib import Path

from birdy_fetcher.cache import Cache
from birdy_fetcher.web.wiki_full import FullWikiClient

SITELINKS = json.dumps(
    {
        "entities": {
            "Q25485": {
                "sitelinks": {
                    "svwiki": {"site": "svwiki", "title": "Talgoxe"},
                    "enwiki": {"site": "enwiki", "title": "Great tit"},
                }
            }
        }
    }
)


def _article(title: str, text: str, revid: int) -> str:
    return json.dumps(
        {"query": {"pages": [{"title": title, "extract": text, "revisions": [{"revid": revid}]}]}}
    )


class FakeHttp:
    def __init__(self) -> None:
        self.urls: list[str] = []

    async def __call__(self, url: str) -> str:
        self.urls.append(url)
        if "wikidata.org" in url:
            return SITELINKS
        if url.startswith("https://sv."):
            return _article("Talgoxe", "Talgoxen är cirka 14 centimeter lång.", 111)
        return _article("Great tit", "The great tit is about 14 centimetres long.", 222)


async def test_articles_uses_sitelinks_and_full_text(tmp_path: Path) -> None:
    http = FakeHttp()
    client = FullWikiClient(cache=Cache(tmp_path), http_get=http)
    articles = await client.articles("Q25485")
    assert set(articles) == {"sv", "en"}
    assert articles["sv"].title == "Talgoxe"
    assert articles["sv"].revision == "111"
    assert "14 centimeter" in articles["sv"].text
    assert articles["en"].revision == "222"
    assert any("sitefilter=svwiki%7Cenwiki" in u for u in http.urls)
    assert any("explaintext=1" in u and "titles=Great%20tit" in u for u in http.urls)


async def test_second_call_uses_cache(tmp_path: Path) -> None:
    http = FakeHttp()
    client = FullWikiClient(cache=Cache(tmp_path), http_get=http)
    await client.articles("Q25485")
    calls = len(http.urls)
    await client.articles("Q25485")
    assert len(http.urls) == calls
    await client.articles("Q25485", refresh=True)
    assert len(http.urls) == calls * 2


async def test_missing_sitelink_and_missing_page(tmp_path: Path) -> None:
    async def http(url: str) -> str:
        if "wikidata.org" in url:
            return json.dumps(
                {"entities": {"Q1": {"sitelinks": {"svwiki": {"site": "svwiki", "title": "X"}}}}}
            )
        return json.dumps({"query": {"pages": [{"title": "X", "missing": True}]}})

    client = FullWikiClient(cache=Cache(tmp_path), http_get=http)
    assert await client.articles("Q1") == {}
