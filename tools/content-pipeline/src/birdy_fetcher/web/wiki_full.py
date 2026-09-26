"""Full Wikipedia articles as plain text, found through Wikidata sitelinks (spec §7)."""

from __future__ import annotations

import asyncio
import json
import time
from collections.abc import Awaitable, Callable, Mapping
from dataclasses import dataclass
from urllib.parse import quote

import aiohttp

from ..cache import Cache
from ..wikipedia import _default_http_get

HttpGet = Callable[[str], Awaitable[str]]
LANGS = ("sv", "en")

# Wikimedia rate-limits us hard under concurrent load ("Your bot is making too many
# requests. Please reduce your request rate") -- retry 429/5xx (Retry-After when given,
# else exponential backoff) and throttle every request from one client to a single
# in-flight slot with a minimum gap between request starts, so several workers never
# hammer the API at once.
_MAX_ATTEMPTS = 5
_BACKOFF_SECONDS = (2.0, 4.0, 8.0, 16.0)
_MAX_RETRY_AFTER_SECONDS = 60.0


def _is_retryable_status(status: int | None) -> bool:
    if status is None:
        return False
    return status == 429 or 500 <= status < 600


def _retry_after_seconds(exc: aiohttp.ClientResponseError) -> float | None:
    headers: Mapping[str, str] = exc.headers or {}
    for key, value in headers.items():
        if key.lower() != "retry-after":
            continue
        try:
            return float(value)
        except ValueError:
            return None
    return None


def _retry_delay(exc: aiohttp.ClientResponseError, failures: int) -> float:
    retry_after = _retry_after_seconds(exc)
    if retry_after is not None:
        return min(retry_after, _MAX_RETRY_AFTER_SECONDS)
    index = min(failures - 1, len(_BACKOFF_SECONDS) - 1)
    return _BACKOFF_SECONDS[index]


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
        http_get: HttpGet | None = None,
        min_interval: float = 1.0,
        sleep: Callable[[float], Awaitable[None]] = asyncio.sleep,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        self.cache = cache
        self._http_get = http_get or _default_http_get
        self._min_interval = min_interval
        self._sleep = sleep
        self._clock = clock
        self._throttle_lock = asyncio.Lock()
        self._last_request_started_at: float | None = None

    async def _cached(self, qid: str, name: str, url: str, refresh: bool) -> str:
        raw = None if refresh else self.cache.get(qid, name)
        if raw is None:
            raw = await self._fetch(url)
            self.cache.put(qid, name, raw)
        return raw

    async def _fetch(self, url: str) -> str:
        """One throttled, retried request: a single in-flight slot shared by every call on
        this client, at least `min_interval` seconds between request starts, and up to
        `_MAX_ATTEMPTS` tries on 429/5xx (honouring Retry-After, else backoff)."""
        failures = 0
        while True:
            async with self._throttle_lock:
                await self._wait_for_slot()
                try:
                    return await self._http_get(url)
                except aiohttp.ClientResponseError as exc:
                    failures += 1
                    if not _is_retryable_status(exc.status) or failures >= _MAX_ATTEMPTS:
                        raise
                    delay = _retry_delay(exc, failures)
            await self._sleep(delay)

    async def _wait_for_slot(self) -> None:
        if self._last_request_started_at is not None:
            remaining = self._min_interval - (self._clock() - self._last_request_started_at)
            if remaining > 0:
                await self._sleep(remaining)
        self._last_request_started_at = self._clock()

    async def sitelinks(self, qid: str, *, refresh: bool = False) -> dict[str, str]:
        url = (
            "https://www.wikidata.org/w/api.php?action=wbgetentities&format=json"
            f"&props=sitelinks&sitefilter=svwiki%7Cenwiki&ids={qid}"
        )
        raw = await self._cached(qid, "web-sitelinks.json", url, refresh)
        links = json.loads(raw).get("entities", {}).get(qid, {}).get("sitelinks", {})
        return {
            site.removesuffix("wiki"): link["title"]
            for site, link in links.items()
            if site in ("svwiki", "enwiki")
        }

    async def article(
        self, qid: str, lang: str, title: str, *, refresh: bool = False
    ) -> WikiArticle | None:
        url = (
            f"https://{lang}.wikipedia.org/w/api.php?action=query&format=json&formatversion=2"
            "&prop=extracts%7Crevisions&explaintext=1&rvprop=ids&redirects=1"
            f"&titles={quote(title)}"
        )
        raw = await self._cached(qid, f"web-article-{lang}.json", url, refresh)
        pages = json.loads(raw).get("query", {}).get("pages", [])
        if not pages or pages[0].get("missing"):
            return None
        page = pages[0]
        text = str(page.get("extract", "")).strip()
        revisions = page.get("revisions") or []
        if not text or not revisions:
            return None
        return WikiArticle(
            lang=lang, title=page["title"], revision=str(revisions[0]["revid"]), text=text
        )

    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]:
        titles = await self.sitelinks(qid, refresh=refresh)
        found: dict[str, WikiArticle] = {}
        for lang in LANGS:
            if lang not in titles:
                continue
            art = await self.article(qid, lang, titles[lang], refresh=refresh)
            if art is not None:
                found[lang] = art
        return found
