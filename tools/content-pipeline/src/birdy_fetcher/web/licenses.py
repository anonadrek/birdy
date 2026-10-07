"""Photo licences, photographer names and Commons links for the credits (spec §8)."""

from __future__ import annotations

import re
from html.parser import HTMLParser

from .checks import without_dashes

LICENSE_URLS: dict[str, str | None] = {
    "CC0": None,
    "Public domain": None,
    "CC BY 2.0": "https://creativecommons.org/licenses/by/2.0/",
    "CC BY 3.0": "https://creativecommons.org/licenses/by/3.0/",
    "CC BY 4.0": "https://creativecommons.org/licenses/by/4.0/",
    "CC BY-SA 2.0": "https://creativecommons.org/licenses/by-sa/2.0/",
    "CC BY-SA 3.0": "https://creativecommons.org/licenses/by-sa/3.0/",
    "CC BY-SA 4.0": "https://creativecommons.org/licenses/by-sa/4.0/",
}


class UnknownLicenseError(ValueError):
    pass


def license_url(license_id: str) -> str | None:
    if license_id not in LICENSE_URLS:
        raise UnknownLicenseError(f"Okänd bildlicens: {license_id!r}")
    return LICENSE_URLS[license_id]


class _TextCollector(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []

    def handle_data(self, data: str) -> None:
        self.parts.append(data)


_NAMESPACE_PREFIXES = ("Template:", "User:")

# Credits as Commons renders some templates (re-review 2026-10-07, real R2 credits):
# {{Unknown|author}} ("Unknown authorUnknown author", sometimes with a translation) is no
# author at all, so a CC BY or CC BY-SA file is rejected and the next one is used.
_UNKNOWN = re.compile(
    r"(?:(?:unknown|anonymous|unbekannt|okänd|inconnu)"
    r"(?:\s+(?:author|photographer|artist|recordist|upphovsman|fotograf|autor|auteur))?"
    r"[\s.,;/|]*)+",
    re.IGNORECASE,
)
# "No machine-readable author provided. Mdf assumed (based on copyright claims)."
_ASSUMED = re.compile(
    r"^No machine-readable author provided\.\s*(.+?)\s+assumed\b.*$", re.IGNORECASE
)
# A wiki signature: "Rabe19 (Diskussion)", "Kim (talk · contribs)".
_SIGNATURE_WORD = r"(?:talk|diskussion|discussion|disk|beiträge|contribs|contributions|bidrag)"
_SIGNATURE = re.compile(
    rf"\s*\({_SIGNATURE_WORD}(?:\s*[·•|,/]\s*{_SIGNATURE_WORD})*\s*\)", re.IGNORECASE
)
# "This image is created by user Justin Jansen at Waarneming.nl, ...".
# Only a sentence about the file itself ("This image is created by ..."); "Minor edits made
# by ..." after a name is the app credits' own case (credits.py).
_CREATED_BY = re.compile(
    r"^This (?:image|file|photo|photograph|picture|sound|recording|work)\b.*?"
    r"\b(?:created|made|recorded|taken|photographed) by\b",
    re.IGNORECASE,
)
_CREATED_BY_NAME = re.compile(
    r"\b(?:created|made|recorded|taken|photographed) by (?:user\s+)?([^,.;:()]+?) at \S",
    re.IGNORECASE,
)
_NOT_IN_A_NAME = {"the", "a", "an", "of", "and", "by", "team", "project"}


def _name_from_sentence(text: str) -> str | None:
    """The name in "... created by user X at Y ...", when it plainly is one (at most four
    words, no small words of a sentence); otherwise None: no name beats a sentence."""
    match = _CREATED_BY_NAME.search(text)
    if match is None:
        return None
    name = match.group(1).strip()
    words = name.split()
    if not 1 <= len(words) <= 4 or any(w.lower() in _NOT_IN_A_NAME for w in words):
        return None
    return name


def clean_author(raw: str | None) -> str | None:
    """Commons author fields are HTML; the site shows plain text, without the dashes its
    dash guard refuses (I7, final review 2026-10-06). Template text that names nobody
    becomes None, a name Commons wraps in a sentence or a signature is unwrapped
    (re-review 2026-10-07)."""
    if not raw:
        return None
    parser = _TextCollector()
    parser.feed(raw)
    parser.close()
    text = " ".join("".join(parser.parts).split())
    for prefix in _NAMESPACE_PREFIXES:
        if text.startswith(prefix):
            text = text.removeprefix(prefix).strip()
            break
    if match := _ASSUMED.match(text):
        text = match.group(1)
    if _CREATED_BY.search(text):
        text = _name_from_sentence(text) or ""
    text = _SIGNATURE.sub("", text).strip()
    if _UNKNOWN.fullmatch(text):
        return None
    return without_dashes(text) or None


def commons_url(source_url: str) -> str:
    return source_url.strip().replace(" ", "_")
