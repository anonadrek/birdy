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
    async with (
        aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session,
        session.get(url, timeout=aiohttp.ClientTimeout(total=30)) as response,
    ):
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

        # Cache lookup: reuse the most-recently-written revision file if any exists
        # (revision re-validation happens via --stale or --force, not on every call).
        species_dir = self.cache.root / q_id
        if not force and species_dir.exists():
            cached = list(species_dir.glob(f"wikipedia-{lang}-r*.json"))
            if cached:
                newest = max(cached, key=lambda p: p.stat().st_mtime)
                return self._parse(q_id, lang, title, newest.read_text(encoding="utf-8"))

        url = f"https://{lang}.wikipedia.org/api/rest_v1/page/summary/{title}"
        try:
            raw = await self._http_get(url)
        except FileNotFoundError:
            return WikipediaResult(q_id, lang, title, "", None, 0)

        result = self._parse(q_id, lang, title, raw)
        if result.revision:
            self.cache.put(q_id, f"wikipedia-{lang}-r{result.revision}.json", raw)
        return result

    @staticmethod
    def _parse(q_id: str, lang: str, title: str, raw: str) -> WikipediaResult:
        data = json.loads(raw)
        revision = str(data.get("revision")) if data.get("revision") else None
        extract = data.get("extract", "")
        word_count = len(extract.split())
        return WikipediaResult(q_id, lang, title, extract, revision, word_count)
