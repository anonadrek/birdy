"""Full Wikipedia articles as plain text, found through Wikidata sitelinks (spec §7)."""

from __future__ import annotations

import json
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from urllib.parse import quote

from ..cache import Cache
from ..wikipedia import _default_http_get

HttpGet = Callable[[str], Awaitable[str]]
LANGS = ("sv", "en")


@dataclass(frozen=True)
class WikiArticle:
    lang: str
    title: str
    revision: str
    text: str


class FullWikiClient:
    def __init__(self, *, cache: Cache, http_get: HttpGet | None = None) -> None:
        self.cache = cache
        self._http_get = http_get or _default_http_get

    async def _cached(self, qid: str, name: str, url: str, refresh: bool) -> str:
        raw = None if refresh else self.cache.get(qid, name)
        if raw is None:
            raw = await self._http_get(url)
            self.cache.put(qid, name, raw)
        return raw

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
