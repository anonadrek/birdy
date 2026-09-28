"""URL slugs for species and group pages (spec 2026-09-25 §4)."""

from __future__ import annotations

import re
import unicodedata

_REPLACE = {"å": "a", "ä": "a", "ö": "o", "æ": "ae", "ø": "o", "ß": "ss"}
_AMPERSAND = {"sv": " och ", "en": " and "}
_APOSTROPHES = "'’`´"  # noqa: RUF001 -- literal apostrophe variants, not confusables


def slugify(name: str, lang: str) -> str:
    """Lowercase ASCII slug: å/ä to a, ö to o, & to och/and, apostrophes removed."""
    text = name.strip().lower()
    for ch in _APOSTROPHES:
        text = text.replace(ch, "")
    text = text.replace("&", _AMPERSAND[lang])
    text = "".join(_REPLACE.get(ch, ch) for ch in text)
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")
    text = re.sub(r"[^a-z0-9]+", "-", text).strip("-")
    if not text:
        raise ValueError(f"Kan inte göra en slug av {name!r}")
    return text
