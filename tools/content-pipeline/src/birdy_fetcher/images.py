"""Wikimedia Commons image fetcher + selection + processor."""

from __future__ import annotations

import io
import json
import re
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote_plus

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
        in_nature = 0 if any("birds in nature" in cat.lower() for cat in c.categories) else 1
        photographs = 0 if any("photographs of aves" in cat.lower() for cat in c.categories) else 1
        # higher resolution sorts first via negation
        size = -(c.width * c.height)
        return (license_rank, photographs, in_nature, size)

    return sorted(survivors, key=_score)


HttpGet = Callable[[str], Awaitable[str]]
HttpGetBytes = Callable[[str], Awaitable[bytes]]


async def _default_get_text(url: str) -> str:
    async with (
        aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session,
        session.get(url, timeout=aiohttp.ClientTimeout(total=60)) as r,
    ):
        r.raise_for_status()
        return await r.text()


async def _default_get_bytes(url: str) -> bytes:
    async with (
        aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session,
        session.get(url, timeout=aiohttp.ClientTimeout(total=120)) as r,
    ):
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
        # quote_plus preserves the surrounding %22 quotes while safely encoding
        # spaces/punctuation in scientific names (matters for trinomials and
        # any future name with apostrophes or parens).
        url = (
            "https://commons.wikimedia.org/w/api.php?"
            "action=query&format=json&prop=imageinfo&"
            "iiprop=url|size|mime|extmetadata&"
            f"generator=search&gsrsearch=intitle:%22{quote_plus(scientific_name)}%22"
            "&gsrnamespace=6&gsrlimit=20"
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

        loaded: Image.Image = Image.open(io.BytesIO(raw_bytes))
        img: Image.Image = loaded.convert("RGB") if loaded.mode in ("RGBA", "P") else loaded
        img.thumbnail((max_side, max_side), Image.Resampling.LANCZOS)

        out_path.parent.mkdir(parents=True, exist_ok=True)
        # exif=b"" actively strips EXIF rather than relying on Pillow's default
        # behavior (which has historically round-tripped some metadata fields).
        img.save(out_path, format="JPEG", quality=quality, optimize=True, exif=b"")
        return ProcessedImage(
            width=img.size[0],
            height=img.size[1],
            bytes_size=out_path.stat().st_size,
        )
