"""One free recording per species from Wikimedia Commons, trimmed to a 20 s MP3
(spec 2026-09-25 §9.1 and §10). Wikimedia Commons only accepts free licenses, and the
license table is the same as for the photos. Xeno-canto's own NonCommercial recordings are
never used."""

from __future__ import annotations

import hashlib
import json
import re
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import quote

import imageio_ffmpeg

from ..cache import Cache
from .http import ThrottledHttp
from .licenses import LICENSE_URLS, clean_author

MIN_SECONDS = 3.0
MAX_SECONDS = 20


@dataclass(frozen=True)
class AudioCandidate:
    title: str
    url: str
    page_url: str
    mime: str
    duration: float | None
    license: str | None
    author: str | None
    categories: tuple[str, ...]
    from_wikidata: bool


_CC = re.compile(r"cc[ -]by(-sa)?[ -](\d\.\d)")


def normalize_license(raw: str | None) -> str | None:
    """Commons' LicenseShortName in the photo table's spelling ("CC BY-SA 4.0")."""
    if not raw:
        return None
    text = " ".join(raw.split())
    low = text.lower()
    if low in {"public domain", "pd", "public domain mark"}:
        return "Public domain"
    if low.startswith("cc0"):
        return "CC0"
    match = _CC.fullmatch(low)
    if match:
        return f"CC BY{'-SA' if match.group(1) else ''} {match.group(2)}"
    return text


def rejection(candidate: AudioCandidate, scientific: str) -> str | None:
    """Why a file may not be used, or None when it may."""
    if not (candidate.mime.startswith("audio/") or candidate.mime == "application/ogg"):
        return "inte en ljudfil"
    categories = [c.lower() for c in candidate.categories]
    if any("pronunciation" in c or "lingua libre" in c for c in categories):
        return "uttalsfil"
    if candidate.license not in LICENSE_URLS:
        return f"licensen {candidate.license!r} finns inte i licenstabellen"
    if LICENSE_URLS[candidate.license] is not None and not candidate.author:
        # CC BY and CC BY-SA require the author's name in the credit (Minor 11, final
        # review 2026-10-06); CC0 and public domain do not.
        return "licensen kräver en upphovsperson, men filen saknar namn"
    if candidate.duration is None or candidate.duration < MIN_SECONDS:
        return "kortare än 3 sekunder"
    if not candidate.from_wikidata:
        name = scientific.lower()
        # Build a word-boundary regex: each word in the species name must match as a whole word
        words = name.split()
        # Pattern: word1 followed by word2, etc., with word boundaries and underscores as spaces
        pattern = re.compile(
            r"(?<![a-z])"
            + re.escape(words[0])
            + r"(?![a-z])"
            + (
                r"(?:\s|_)+" + r"(?<![a-z])" + re.escape(words[1]) + r"(?![a-z])"
                if len(words) > 1
                else ""
            )
        )
        text_with_spaces = candidate.title.lower().replace("_", " ")
        in_title = pattern.search(text_with_spaces) is not None
        in_categories = any(pattern.search(c) is not None for c in categories)
        if not in_title and not in_categories:
            return "nämner inte arten i titeln eller kategorierna"
    return None


def choose(
    candidates: list[AudioCandidate], scientific: str
) -> tuple[AudioCandidate | None, list[str]]:
    notes: list[str] = []
    for candidate in candidates:
        reason = rejection(candidate, scientific)
        if reason is None:
            return candidate, notes
        notes.append(f"{candidate.title}: {reason}")
    return None, notes


def audio_record(candidate: AudioCandidate, qid: str) -> dict[str, Any]:
    """The record's `audio` object (spec appendix C). Only for an accepted candidate."""
    if candidate.duration is None or candidate.license is None:
        raise ValueError(f"{candidate.title} har ingen längd eller licens")
    return {
        "file": f"{qid}/voice.mp3",
        "durationSec": min(MAX_SECONDS, round(candidate.duration)),
        "trimmed": candidate.duration > MAX_SECONDS,
        "author": candidate.author,
        "license": candidate.license,
        "licenseUrl": LICENSE_URLS[candidate.license],
        "sourceUrl": candidate.page_url,
    }


COMMONS_API = "https://commons.wikimedia.org/w/api.php"
WIKIDATA_API = "https://www.wikidata.org/w/api.php"
MAX_TITLES = 20
BITRATE = "64k"
FFMPEG_TIMEOUT = 120


def parse_candidate(page: dict[str, Any], *, from_wikidata: bool) -> AudioCandidate:
    info = page["imageinfo"][0]
    meta = info.get("extmetadata", {})
    duration = info.get("duration")
    return AudioCandidate(
        title=page["title"],
        url=info["url"],
        page_url=info.get("descriptionurl", ""),
        mime=info.get("mime", ""),
        duration=float(duration) if duration is not None else None,
        license=normalize_license(meta.get("LicenseShortName", {}).get("value")),
        author=clean_author(meta.get("Artist", {}).get("value")),
        categories=tuple(c["title"] for c in page.get("categories", [])),
        from_wikidata=from_wikidata,
    )


class CommonsAudioClient:
    def __init__(self, *, cache: Cache, http: ThrottledHttp | None = None) -> None:
        self.cache = cache
        self._http = http or ThrottledHttp(min_interval=1.0)

    async def _json(self, qid: str, name: str, url: str, refresh: bool) -> Any:
        raw = None if refresh else self.cache.get(qid, name)
        if raw is None:
            raw = await self._http.fetch_text(url)
            self.cache.put(qid, name, raw)
        return json.loads(raw)

    async def candidates(
        self, qid: str, scientific: str, *, refresh: bool = False
    ) -> list[AudioCandidate]:
        """Wikidata's own recordings (P51) first, then a Commons search for the name."""
        claims_url = f"{WIKIDATA_API}?action=wbgetclaims&format=json&entity={qid}&property=P51"
        claims = await self._json(qid, "audio-p51.json", claims_url, refresh)
        p51 = [
            "File:" + claim["mainsnak"]["datavalue"]["value"].replace("_", " ")
            for claim in claims.get("claims", {}).get("P51", [])
            if "datavalue" in claim.get("mainsnak", {})
        ]
        search = quote(f'"{scientific}" filetype:audio')
        search_url = (
            f"{COMMONS_API}?action=query&format=json&formatversion=2&list=search"
            f"&srnamespace=6&srlimit=10&srsearch={search}"
        )
        found = await self._json(qid, "audio-search.json", search_url, refresh)
        hits = [hit["title"] for hit in found.get("query", {}).get("search", [])]
        seen: set[str] = set()
        titles: list[str] = []
        for title in p51 + hits:
            if title not in seen and len(titles) < MAX_TITLES:
                seen.add(title)
                titles.append(title)
        if not titles:
            return []
        info_url = (
            f"{COMMONS_API}?action=query&format=json&formatversion=2"
            "&prop=imageinfo%7Ccategories&clshow=!hidden&cllimit=max"
            "&iiprop=url%7Cextmetadata%7Cmime%7Csize"
            "&iiextmetadatafilter=LicenseShortName%7CArtist"
            f"&titles={quote('|'.join(titles))}"
        )
        info = await self._json(qid, "audio-info.json", info_url, refresh)
        query = info.get("query", {})
        normalized = {n["from"]: n["to"] for n in query.get("normalized", [])}
        pages = {page["title"]: page for page in query.get("pages", [])}
        result: list[AudioCandidate] = []
        for title in titles:
            resolved_title = normalized.get(title, title)
            page = pages.get(resolved_title)
            if page is None or not page.get("imageinfo"):
                continue
            result.append(parse_candidate(page, from_wikidata=title in p51))
        return result

    async def download(
        self, qid: str, candidate: AudioCandidate, *, refresh: bool = False
    ) -> bytes:
        digest = hashlib.sha256(candidate.title.encode("utf-8")).hexdigest()[:12]
        name = f"audio-{digest}.bin"
        raw = None if refresh else self.cache.get_bytes(qid, name)
        if raw is None:
            raw = await self._http.fetch_bytes(candidate.url)
            self.cache.put_bytes(qid, name, raw)
        return raw


def convert_to_mp3(raw: bytes, out_path: Path) -> None:
    """The first 20 s, mono, loudness-normalised, MP3 at 64 kbit/s (about 160 kB)."""
    exe = imageio_ffmpeg.get_ffmpeg_exe()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "in.bin"
        dst = Path(tmp) / "out.mp3"
        src.write_bytes(raw)
        command = [exe, "-hide_banner", "-loglevel", "error", "-y", "-i", str(src)]
        command += ["-t", str(MAX_SECONDS), "-ac", "1", "-af", "loudnorm=I=-16:TP=-1.5:LRA=11"]
        command += ["-codec:a", "libmp3lame", "-b:a", BITRATE, str(dst)]
        result = subprocess.run(command, check=False, capture_output=True, timeout=FFMPEG_TIMEOUT)
        if result.returncode != 0:
            stderr_text = result.stderr.decode("utf-8", errors="replace")
            stderr_tail = stderr_text[-500:].strip() if stderr_text else ""
            raise RuntimeError(f"ffmpeg misslyckades med kod {result.returncode}: {stderr_tail}")
        shutil.copyfile(dst, out_path)


_DURATION = re.compile(r"Duration: (\d+):(\d+):(\d+(?:\.\d+)?)")


def probe_seconds(path: Path) -> float:
    exe = imageio_ffmpeg.get_ffmpeg_exe()
    result = subprocess.run(
        [exe, "-hide_banner", "-i", str(path)],
        capture_output=True,
        text=True,
        check=False,
        timeout=FFMPEG_TIMEOUT,
    )
    match = _DURATION.search(result.stderr)
    if match is None:
        raise ValueError(f"Hittar ingen längd för {path}")
    hours, minutes, seconds = match.groups()
    return int(hours) * 3600 + int(minutes) * 60 + float(seconds)
