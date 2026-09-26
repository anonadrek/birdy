"""Tests for web/wiki_full.py: sitelinks by QID and full plain-text articles."""

from __future__ import annotations

import asyncio
import json
import re
from pathlib import Path

import aiohttp
import multidict
import pytest
import yarl

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


def _response_error(
    status: int, headers: dict[str, str] | None = None
) -> aiohttp.ClientResponseError:
    request_info = aiohttp.RequestInfo(
        url=yarl.URL("https://x"),
        method="GET",
        headers=multidict.CIMultiDictProxy(multidict.CIMultiDict()),
        real_url=yarl.URL("https://x"),
    )
    return aiohttp.ClientResponseError(
        request_info=request_info,
        history=(),
        status=status,
        headers=multidict.CIMultiDict(headers or {}),
    )


class FlakyHttp:
    """Fails with the given status the first `fail_times` calls, then returns SITELINKS."""

    def __init__(self, fail_times: int, status: int, headers: dict[str, str] | None = None) -> None:
        self.calls = 0
        self.fail_times = fail_times
        self.status = status
        self.headers = headers

    async def __call__(self, url: str) -> str:
        self.calls += 1
        if self.calls <= self.fail_times:
            raise _response_error(self.status, self.headers)
        return SITELINKS


async def test_retries_429_honouring_retry_after_header(tmp_path: Path) -> None:
    http = FlakyHttp(fail_times=1, status=429, headers={"Retry-After": "3"})
    sleeps: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleeps.append(seconds)

    client = FullWikiClient(
        cache=Cache(tmp_path), http_get=http, min_interval=0.0, sleep=fake_sleep, clock=lambda: 0.0
    )
    result = await client.sitelinks("Q25485")
    assert result == {"sv": "Talgoxe", "en": "Great tit"}
    assert http.calls == 2
    assert 3.0 in sleeps  # plus any throttle sleeps


async def test_retries_503_with_exponential_backoff_when_no_retry_after(tmp_path: Path) -> None:
    http = FlakyHttp(fail_times=2, status=503)
    sleeps: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleeps.append(seconds)

    client = FullWikiClient(
        cache=Cache(tmp_path), http_get=http, min_interval=0.0, sleep=fake_sleep, clock=lambda: 0.0
    )
    await client.sitelinks("Q25485")
    assert http.calls == 3
    assert sleeps == [2.0, 4.0]


async def test_gives_up_after_five_attempts(tmp_path: Path) -> None:
    http = FlakyHttp(fail_times=999, status=429)

    async def fake_sleep(seconds: float) -> None:
        pass

    client = FullWikiClient(
        cache=Cache(tmp_path), http_get=http, min_interval=0.0, sleep=fake_sleep, clock=lambda: 0.0
    )
    with pytest.raises(aiohttp.ClientResponseError):
        await client.sitelinks("Q25485")
    assert http.calls == 5


async def test_404_is_raised_immediately_without_retry(tmp_path: Path) -> None:
    class NotFoundHttp:
        def __init__(self) -> None:
            self.calls = 0

        async def __call__(self, url: str) -> str:
            self.calls += 1
            raise FileNotFoundError(url)

    http = NotFoundHttp()
    client = FullWikiClient(cache=Cache(tmp_path), http_get=http, min_interval=0.0)
    with pytest.raises(FileNotFoundError):
        await client.sitelinks("Q25485")
    assert http.calls == 1


async def test_throttle_waits_between_uncached_requests_but_not_on_cache_hit(
    tmp_path: Path,
) -> None:
    sleeps: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleeps.append(seconds)

    http = FakeHttp()
    client = FullWikiClient(
        cache=Cache(tmp_path), http_get=http, min_interval=1.0, sleep=fake_sleep, clock=lambda: 0.0
    )

    await client.sitelinks("Q25485")
    assert sleeps == []  # first request ever: nothing to wait for

    await client.article("Q25485", "sv", "Talgoxe")
    assert sleeps == [1.0]  # second uncached request: waited ~min_interval

    sleeps.clear()
    await client.article("Q25485", "sv", "Talgoxe")  # now cached
    assert sleeps == []


async def test_only_one_request_in_flight_at_a_time(tmp_path: Path) -> None:
    in_flight = 0
    max_in_flight = 0

    async def http(url: str) -> str:
        nonlocal in_flight, max_in_flight
        in_flight += 1
        max_in_flight = max(max_in_flight, in_flight)
        await asyncio.sleep(0)  # yield, so a broken throttle would let another call overlap
        in_flight -= 1
        if "wikidata.org" in url:
            match = re.search(r"ids=(Q\d+)", url)
            assert match is not None
            qid = match.group(1)
            return json.dumps(
                {
                    "entities": {
                        qid: {
                            "sitelinks": {
                                "svwiki": {"site": "svwiki", "title": "Test"},
                                "enwiki": {"site": "enwiki", "title": "Test"},
                            }
                        }
                    }
                }
            )
        if url.startswith("https://sv."):
            return _article("Test", "Text på svenska.", 1)
        return _article("Test", "Text in English.", 2)

    client = FullWikiClient(cache=Cache(tmp_path), http_get=http, min_interval=0.0)
    await asyncio.gather(*(client.articles(f"Q{i}") for i in range(5)))
    assert max_in_flight == 1
