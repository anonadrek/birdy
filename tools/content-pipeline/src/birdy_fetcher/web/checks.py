"""Style and quote checks shared by the fact sheet, text and comparison steps (spec §9.6)."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

LANGS = ("sv", "en")
DASHES = ("—", "–", "--")  # noqa: RUF001
FIRST_PERSON = {
    # "vår/våra" is left out on purpose: "våra vanligaste fåglar" is idiomatic Swedish.
    "sv": re.compile(r"\b(jag|vi|oss)\b", re.IGNORECASE),
    "en": re.compile(r"\b(I|[Ww]e|[Uu]s|[Oo]ur|[Oo]urs|[Mm]y)\b"),
}


@dataclass(frozen=True)
class Issue:
    """One broken rule. `fact` is set when the problem only affects one fact (size or status),
    which can then be dropped instead of failing the whole species."""

    path: str
    message: str
    lang: str | None = None
    fact: str | None = None


def load_banned(path: Path) -> list[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    return [ln.strip().lower() for ln in lines if ln.strip() and not ln.startswith("#")]


def banned_hits(text: str, banned: list[str]) -> list[str]:
    lower = text.lower()
    return [p for p in banned if re.search(rf"(?<!\w){re.escape(p)}(?!\w)", lower)]


_SENTENCE_SPLIT = re.compile(r"(?<=[.?!])\s+(?=[A-ZÅÄÖ])")


def _split_sentences(text: str) -> list[str]:
    """Split on sentence-ending punctuation followed by an uppercase letter, so abbreviations
    like "bl.a." or "e.g." do not count as sentence ends."""
    return [s for s in _SENTENCE_SPLIT.split(text.strip()) if s]


def sentence_count(text: str) -> int:
    return len(_split_sentences(text))


def _words(text: str) -> int:
    return len(text.split())


def _style(path: str, lang: str, text: str, banned: list[str]) -> list[Issue]:
    issues: list[Issue] = []
    if not text.strip():
        issues.append(Issue(path, "är tom"))
        return issues
    if any(d in text for d in DASHES):
        issues.append(Issue(path, "innehåller tankstreck eller --"))
    if "!" in text:
        issues.append(Issue(path, "innehåller utropstecken"))
    first_person = FIRST_PERSON[lang].search(text)
    if first_person:
        issues.append(Issue(path, f"är skriven i första person ('{first_person.group(0)}')"))
    issues.extend(
        Issue(path, f"innehåller den förbjudna frasen '{h}'") for h in banned_hits(text, banned)
    )
    return issues


_NUMBER_DASH = re.compile(r"(\d)\s*[\u2013\u2014]\s*(\d)")
_DASH_BREAK = re.compile(r"\s*(?:\u2014|--)\s*|\s+\u2013\s+")
_COMMA_BEFORE_STOP = re.compile(r",\s*([,.;:!?])")


def without_dashes(text: str) -> str:
    """Text the pipeline copies onto a page without a model writing it (the app's
    marginalia, photographers and recordists) and the flag messages, which hold the
    checking model's own reasons: the site's dash guard refuses an em dash, an en dash
    with spaces around it and `--` (spec §9.6 rule 1), so they become a comma. A dash
    between two numbers becomes a hyphen ("13-15"); an en dash between two words is
    left alone, like the guard does (I7, final review 2026-10-06)."""
    out = _NUMBER_DASH.sub(r"\1-\2", text)
    out = _DASH_BREAK.sub(", ", out)
    out = _COMMA_BEFORE_STOP.sub(r"\1", out)
    return out.strip(" ,")


MIN_QUOTE_CHARS = 20
_QUOTE_CHARS = {
    "’": "'",  # noqa: RUF001
    "‘": "'",  # noqa: RUF001
    "“": '"',
    "”": '"',
    "«": '"',
    "»": '"',
    " ": " ",  # noqa: RUF001 -- key is U+00A0 (non-breaking space)
}
# Wikipedia writes size ranges with a typographic dash or minus sign; the prompt forbids
# dashes in the model's own prose, so a quoted "28-31" must still match a differently
# dashed source.
_DASH_TO_HYPHEN = {chr(cp): "-" for cp in (*range(0x2010, 0x2016), 0x2212)}


def _normalize(text: str) -> str:
    for src, dst in _QUOTE_CHARS.items():
        text = text.replace(src, dst)
    for src, dst in _DASH_TO_HYPHEN.items():
        text = text.replace(src, dst)
    return " ".join(text.lower().split())


def quote_in_sources(quote: str, sources: list[str]) -> bool:
    q = _normalize(quote)
    return len(q) >= MIN_QUOTE_CHARS and any(q in _normalize(s) for s in sources)
