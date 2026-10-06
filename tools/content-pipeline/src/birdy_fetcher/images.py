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
from PIL import Image, ImageCms, ImageOps

from .cache import Cache
from .credits import canonical_license, clean_author, is_usable_author, requires_attribution

USER_AGENT = "birdy-fetcher/0.1.0 (https://github.com/anonadrek/birdy)"

MIN_DIMENSION = 2048
HERO_MAX = 2400
SECONDARY_MAX = 1800
# The app ships WebP (Plan 6b3 T22 converted the whole set with
# `cwebp -q 75 -m 6`); new photos are written the same way.
WEBP_QUALITY = 75
WEBP_METHOD = 6

REJECT_PATTERNS = re.compile(
    # Trailing `s?` matches plural Commons category names like "Bird illustrations"
    # or "(museum specimens)" — without it `\b...\b` would only match singular.
    # `print|chromolithograph|lithograph|engraving|iconographia|hardwicke` cover
    # historical zoological plates that the Commons search surfaces near the top.
    r"\b("
    r"illustration|drawing|painting|specimen|skeleton|skull|egg|nest only|"
    r"taxidermy|taxidermied|"
    # Trailing `h?` on chromolithograph handles truncated Wellcome filenames
    # like "Chromolithograp Wellcome V0022220.jpg".
    r"print|chromolithograph?|lithograph|engraving|iconographia|hardwicke|wellcome|"
    # Rijksmuseum manuscripts/prints (e.g. "Historia Naturalis van Rudolf II"
    # category for 1600s watercolour collections; author field "Rijksmuseum"
    # for inventory-coded plates like "RP-T-BR-2017-1-4-18.jpg").
    r"historia naturalis|rijksmuseum|"
    # Release 1.3.0 photo audit: none of these show a living bird of the
    # species. MHNT = the Toulouse museum (eggs, study skins); "map" catches
    # "Vanellus gregarius range map.png" and categories like "Distribution maps".
    r"map|distribution|irruption|mhnt|museum|mounted|feather|nest|"
    r"dead(?!\s+sea)|carcass|roadkill|banding|ringing|in hand|figure|plate|pamphlet"
    r")s?\b",
    re.IGNORECASE,
)

# Allowed but weaker: captive birds and young ones rank after everything else.
WEAK_PATTERNS = re.compile(
    r"\b(zoo|zoological|tierpark|vogelpark|wildpark|aviary|captive|captivity|"
    r"chick|nestling|fledgling|gosling|duckling|juvenile|pullus|downy)s?\b",
    re.IGNORECASE,
)

QUALITY_CATEGORIES = re.compile(
    r"^(featured pictures|quality images|valued images)\b", re.IGNORECASE
)

# Pillow-decodable raster formats. Commons search occasionally returns videos
# (.webm, .ogv) or vectors (.svg) which crash ImageProcessor.process().
ALLOWED_IMAGE_EXTS = frozenset({".jpg", ".jpeg", ".png", ".gif", ".webp", ".tif", ".tiff"})

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


def is_license_ok(c: ImageCandidate) -> bool:
    """Allow-listed licence, and a usable photographer when it must be credited."""
    if canonical_license(c.license) is None:
        return False
    return not requires_attribution(c.license) or is_usable_author(clean_author(c.author))


def mentions_species(title: str, scientific_name: str) -> bool:
    """True when ``title`` names the species itself, not a look-alike title.

    The Commons title search ignores punctuation and case, so "Alle alle" also
    finds the 1919 pamphlet "An Alle, Alle!" and "Perdix perdix" finds the
    moth "Syntypistis perdix perdix". Both were shipped as photos before
    release 1.3.0.
    """
    parts = scientific_name.split()
    if len(parts) < 2:
        return True
    pattern = re.compile(
        r"(?<![A-Za-z])" + re.escape(parts[0]) + r"[ _]+" + re.escape(parts[1]) + r"(?![a-z])",
        re.IGNORECASE,
    )
    for m in pattern.finditer(title):
        if title[m.start()].islower() and re.search(r"[A-Z][a-z]+[ _]+$", title[: m.start()]):
            continue  # "Othergenus genus species": another animal's trinomial
        return True
    return False


def rank_candidates(
    candidates: list[ImageCandidate], *, scientific_name: str | None = None
) -> list[ImageCandidate]:
    """Usable candidates, best first.

    ``scientific_name`` is for title-search results: the file title must then
    name the species (see :func:`mentions_species`).
    """
    survivors: list[ImageCandidate] = []
    for c in candidates:
        if not is_license_ok(c):
            continue
        if scientific_name and not mentions_species(c.commons_filename, scientific_name):
            continue
        if "." not in c.commons_filename:
            continue
        ext = "." + c.commons_filename.rsplit(".", 1)[-1].lower()
        if ext not in ALLOWED_IMAGE_EXTS:
            continue
        if REJECT_PATTERNS.search(c.commons_filename):
            continue
        if any(REJECT_PATTERNS.search(cat) for cat in c.categories):
            continue
        if REJECT_PATTERNS.search(c.author):
            continue
        if max(c.width, c.height) < MIN_DIMENSION:
            continue
        survivors.append(c)

    def _score(c: ImageCandidate) -> tuple[int, int, int, int, int, int]:
        weak = int(
            bool(WEAK_PATTERNS.search(c.commons_filename))
            or any(WEAK_PATTERNS.search(cat) for cat in c.categories)
        )
        # Commons' own reviews: featured, quality and valued images first.
        reviewed = 0 if any(QUALITY_CATEGORIES.search(cat) for cat in c.categories) else 1
        license_rank = _LICENSE_PRIORITY.get(c.license.lower(), 5)
        in_nature = 0 if any("birds in nature" in cat.lower() for cat in c.categories) else 1
        photographs = 0 if any("photographs of aves" in cat.lower() for cat in c.categories) else 1
        # higher resolution sorts first via negation
        size = -(c.width * c.height)
        return (weak, reviewed, license_rank, photographs, in_nature, size)

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
            "&gsrnamespace=6&gsrlimit=50"
        )
        raw = await get(url)
        self.cache.put(q_id, cache_key, raw)
        return parse_imageinfo_response(raw)

    async def fetch_category_candidates(
        self,
        q_id: str,
        category: str,
        *,
        force: bool = False,
    ) -> list[ImageCandidate]:
        """Files in ``Category:<category>`` (usually the scientific name).

        The species category holds many photos whose titles don't carry the
        scientific name, so it complements the title search when that comes
        up short.
        """
        cache_key = "image-category-candidates.json"
        if not force and self.cache.has(q_id, cache_key):
            raw = self.cache.get(q_id, cache_key)
            assert raw is not None
            return parse_imageinfo_response(raw)

        get = self.http_get or _default_get_text
        url = (
            "https://commons.wikimedia.org/w/api.php?"
            "action=query&format=json&prop=imageinfo&"
            "iiprop=url|size|mime|extmetadata&"
            f"generator=categorymembers&gcmtitle=Category:{quote_plus(category)}"
            "&gcmtype=file&gcmlimit=100"
        )
        raw = await get(url)
        self.cache.put(q_id, cache_key, raw)
        return parse_imageinfo_response(raw)


_SRGB = ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB"))


def _to_srgb(img: Image.Image, icc: bytes | None) -> Image.Image:
    """RGB pixels in sRGB. The WebP carries no profile, so an Adobe RGB, Display
    P3 or ProPhoto original has to be converted or its colours come out dull
    (49 of the 221 photos fetched for release 1.3.0 had such a profile)."""
    if icc:
        try:
            source = ImageCms.ImageCmsProfile(io.BytesIO(icc))
            if "srgb" not in ImageCms.getProfileDescription(source).lower():
                converted = ImageCms.profileToProfile(img, source, _SRGB, outputMode="RGB")
                if converted is not None:
                    return converted
        except (ImageCms.PyCMSError, OSError, ValueError):
            pass  # unreadable profile: treat the pixels as sRGB, as before
    return img.convert("RGB")


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

        loaded: Image.Image = Image.open(io.BytesIO(raw_bytes))
        # Apply the EXIF orientation before the metadata is dropped: without it
        # a phone photo stored sideways stays sideways (Stenhöna's hero did).
        upright = ImageOps.exif_transpose(loaded) or loaded
        img: Image.Image = _to_srgb(upright, loaded.info.get("icc_profile"))
        img.thumbnail((max_side, max_side), Image.Resampling.LANCZOS)

        out_path.parent.mkdir(parents=True, exist_ok=True)
        # exif=b"" actively strips EXIF (GPS, camera serials) from the output.
        img.save(out_path, format="WEBP", quality=WEBP_QUALITY, method=WEBP_METHOD, exif=b"")
        return ProcessedImage(
            width=img.size[0],
            height=img.size[1],
            bytes_size=out_path.stat().st_size,
        )
