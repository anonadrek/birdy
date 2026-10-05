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

LANGS = ("sv", "en", "de")


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

    async def _item_sitelinks(
        self, cache_qid: str, item: str, name: str, refresh: bool
    ) -> dict[str, str]:
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

    async def _original_combination(self, qid: str, refresh: bool) -> str | None:
        url = (
            "https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json"
            f"&entity={qid}&property=P1403"
        )
        raw = await self._cached(qid, "web-original-combination.json", url, refresh)
        for claim in json.loads(raw).get("claims", {}).get("P1403", []):
            value = claim.get("mainsnak", {}).get("datavalue", {}).get("value", {})
            target = value.get("id") if isinstance(value, dict) else None
            if isinstance(target, str):
                return target
        return None

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
