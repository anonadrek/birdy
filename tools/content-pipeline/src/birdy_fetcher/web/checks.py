"""Checks that every web text must pass before it is saved (spec 2026-09-25 §7)."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from .model import LangText, WebTextOutput

LANGS = ("sv", "en")
DASHES = ("—", "–", "--")  # noqa: RUF001
FIRST_PERSON = {
    # "vår/våra" is left out on purpose: "våra vanligaste fåglar" is idiomatic Swedish.
    "sv": re.compile(r"\b(jag|vi|oss)\b", re.IGNORECASE),
    "en": re.compile(r"\b(I|[Ww]e|[Uu]s|[Oo]ur|[Oo]urs|[Mm]y)\b"),
}
LEAD_MAX_WORDS = 45
LEAD_MAX_SENTENCES = 2
MARKS_MIN, MARKS_MAX, MARK_MAX_WORDS = 3, 4, 16
VOICE_MAX_WORDS = 60
WHERE_MAX_WORDS = 70
META_MIN, META_MAX = 120, 155


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


def sentence_count(text: str) -> int:
    return len([s for s in re.split(r"(?<=[.?!])\s+", text.strip()) if s])


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
    if FIRST_PERSON[lang].search(text):
        issues.append(Issue(path, "är skriven i första person"))
    issues.extend(
        Issue(path, f"innehåller den förbjudna frasen '{h}'") for h in banned_hits(text, banned)
    )
    return issues


def _check_lang(lang: str, t: LangText, banned: list[str]) -> list[Issue]:
    issues: list[Issue] = []
    fields = {
        "lead": t.lead,
        "voice": t.voice,
        "where_when": t.where_when,
        "meta_description": t.meta_description,
    }
    fields.update({f"field_marks[{i}]": mark for i, mark in enumerate(t.field_marks)})
    for name, text in fields.items():
        issues.extend(_style(f"{lang}.{name}", lang, text, banned))

    if _words(t.lead) > LEAD_MAX_WORDS or sentence_count(t.lead) > LEAD_MAX_SENTENCES:
        issues.append(Issue(f"{lang}.lead", "ska vara högst 2 meningar och 45 ord"))
    if not MARKS_MIN <= len(t.field_marks) <= MARKS_MAX:
        issues.append(Issue(f"{lang}.field_marks", "ska ha 3 eller 4 punkter"))
    for i, mark in enumerate(t.field_marks):
        if _words(mark) > MARK_MAX_WORDS:
            issues.append(Issue(f"{lang}.field_marks[{i}]", "ska vara högst 16 ord"))
    if _words(t.voice) > VOICE_MAX_WORDS:
        issues.append(Issue(f"{lang}.voice", "ska vara högst 60 ord"))
    if _words(t.where_when) > WHERE_MAX_WORDS:
        issues.append(Issue(f"{lang}.where_when", "ska vara högst 70 ord"))
    if not META_MIN <= len(t.meta_description) <= META_MAX:
        issues.append(
            Issue(
                f"{lang}.meta_description",
                f"ska vara 120 till 155 tecken (är {len(t.meta_description)})",
            )
        )
    size = t.facts.size
    if size is not None and any(d in size.value for d in DASHES):
        issues.append(
            Issue(f"{lang}.facts.size", "storleken innehåller tankstreck", lang=lang, fact="size")
        )
    return issues


def check_text(out: WebTextOutput, banned: list[str]) -> list[Issue]:
    issues: list[Issue] = []
    for lang in LANGS:
        issues.extend(_check_lang(lang, getattr(out, lang), banned))
    return issues
