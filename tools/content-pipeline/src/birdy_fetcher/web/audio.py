"""One free recording per species from Wikimedia Commons, trimmed to a 20 s MP3
(spec 2026-09-25 §9.1 and §10). Wikimedia Commons only accepts free licenses, and the
license table is the same as for the photos. Xeno-canto's own NonCommercial recordings are
never used."""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

from .licenses import LICENSE_URLS

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
    if candidate.duration is None or candidate.duration < MIN_SECONDS:
        return "kortare än 3 sekunder"
    if not candidate.from_wikidata:
        name = scientific.lower()
        in_title = name in candidate.title.lower().replace("_", " ")
        if not in_title and not any(name in c for c in categories):
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
