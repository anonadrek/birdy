"""Release 1.3.1 part 7 (Albin 2026-10-08): no em dash (U+2014) or en dash (U+2013) in the
species texts the app shows. The app shows `description`, `migration` and `marginalia`
(SpeciesDbBuilder.kt) through SpeciesTextCleaner.kt, which hides the first line when it is a
heading and shows every other line. Number and month ranges get a rule ("12 till 14 cm",
"mars till april"); every other sentence that still has a dash goes to the model step in
app_dashes_run.py. `review_notes` and `image_refs` are never shown and never touched."""

from __future__ import annotations

import copy
import re
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml

DASH = re.compile("[\u2013\u2014]")
TEXT_FIELDS = ("description", "migration", "marginalia")

_RANGE_WORD = {"sv": "till", "en": "to"}
_MONTHS = {
    "sv": (
        "januari|februari|mars|april|maj|juni|juli|augusti|september|oktober|november|december"
        "|jan|feb|mar|apr|jun|jul|aug|sept|sep|okt|nov|dec"
    ),
    "en": (
        "January|February|March|April|May|June|July|August|September|October|November|December"
        "|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sept|Sep|Oct|Nov|Dec"
    ),
}
_NUMBER_RANGE = re.compile(r"(?<=\d)\s*[\u2013\u2014]\s*(?=\d)")
_NUMBER = re.compile(r"\d+(?:[.,]\d+)?")
# The sentence boundary of web/checks.py (punctuation, space, capital letter, so "bl.a." and "e.g."
# do not end a sentence), captured so a paragraph can be put back together byte for byte.
_SENTENCE_BOUNDARY = re.compile(r"((?<=[.?!])\s+(?=[A-ZÅÄÖ]))")
_ATX_HEADING_MAX_HASHES = 6
_BOLD_HEADING_MIN_LENGTH = 5
_MIN_LENGTH_RATIO = 0.7
_MAX_LENGTH_RATIO = 1.4


def is_hidden_heading(line: str) -> bool:
    """SpeciesTextCleaner.isHeadingLine: an ATX heading (`#` to `######`, then a space or the end
    of the line) or a line that is one `**bold**` span not ending a sentence."""
    t = line.strip()
    if not t:
        return False
    if t.startswith("#"):
        hashes = len(t) - len(t.lstrip("#"))
        return 1 <= hashes <= _ATX_HEADING_MAX_HASHES and (hashes == len(t) or t[hashes] == " ")
    if len(t) >= _BOLD_HEADING_MIN_LENGTH and t.startswith("**") and t.endswith("**"):
        inner = t[2:-2].strip()
        return bool(inner) and "**" not in inner and inner[-1] not in ".!?"
    return False


def visible_line_indexes(lines: list[str]) -> range:
    """The lines the app shows: all, except a first line that is a heading."""
    start = 1 if lines and is_hidden_heading(lines[0]) else 0
    return range(start, len(lines))


def rule_fix(line: str, lang: str) -> str:
    """Number ranges ("12–14 cm") and month ranges ("mars–april") read the same with a word
    instead of the dash: "12 till 14 cm", "mars till april" ("to" in English)."""  # noqa: RUF002
    word = _RANGE_WORD[lang]
    line = _NUMBER_RANGE.sub(f" {word} ", line)
    months = _MONTHS[lang]
    month_range = re.compile(rf"\b({months})\s*[\u2013\u2014]\s*({months})\b", re.IGNORECASE)
    return month_range.sub(rf"\1 {word} \2", line)


def split_sentences(paragraph: str) -> list[str]:
    """[sentence, boundary, sentence, ...]; `"".join(...)` gives the paragraph back unchanged.
    Sentences are at the even indexes."""
    return _SENTENCE_BOUNDARY.split(paragraph)


@dataclass(frozen=True)
class DashSentence:
    """A sentence the app shows that still has a dash after the rules."""

    key: str  # f"{field}.{lang}.{line}.{part}", unique within one species
    field: str
    lang: str
    line: int  # index in text.split("\n")
    part: int  # even index in split_sentences(line)
    text: str
    paragraph: str  # the whole line, as context for the model


@dataclass
class SpeciesScan:
    qid: str
    path: Path
    data: dict[str, Any]
    fixed: dict[tuple[str, str], str]  # (field, lang) -> text after the rules, only when changed
    sentences: list[DashSentence]
    rule_fixes: int  # dashes the rules replaced


def scan_species(path: Path) -> SpeciesScan:
    data: dict[str, Any] = yaml.safe_load(path.read_text(encoding="utf-8"))
    fixed: dict[tuple[str, str], str] = {}
    sentences: list[DashSentence] = []
    rule_fixes = 0
    for field in TEXT_FIELDS:
        for lang, text in (data.get(field) or {}).items():
            if not isinstance(text, str) or lang not in _RANGE_WORD:
                continue
            lines = text.split("\n")
            for i in visible_line_indexes(lines):
                before = lines[i]
                lines[i] = rule_fix(before, lang)
                rule_fixes += len(DASH.findall(before)) - len(DASH.findall(lines[i]))
                parts = split_sentences(lines[i])
                for j in range(0, len(parts), 2):
                    if DASH.search(parts[j]):
                        key = f"{field}.{lang}.{i}.{j}"
                        sentences.append(DashSentence(key, field, lang, i, j, parts[j], lines[i]))
            new_text = "\n".join(lines)
            if new_text != text:
                fixed[(field, lang)] = new_text
    return SpeciesScan(str(data["id"]), path, data, fixed, sentences, rule_fixes)


def check_rewrite(original: str, rewritten: str) -> list[str]:
    """Code checks on a model's rewrite of one sentence; empty when it may be used."""
    problems: list[str] = []
    if DASH.search(rewritten):
        problems.append("tankstreck kvar")
    if Counter(_NUMBER.findall(original)) != Counter(_NUMBER.findall(rewritten)):
        problems.append("talen skiljer sig")
    if original.count("*") != rewritten.count("*"):
        problems.append("betoningen (*) skiljer sig")
    if "\n" in rewritten:
        problems.append("radbrytning")
    ratio = len(rewritten) / max(1, len(original))
    if not _MIN_LENGTH_RATIO <= ratio <= _MAX_LENGTH_RATIO:
        problems.append(f"längden ändrades för mycket ({ratio:.2f})")
    return problems


def apply_rewrites(scan: SpeciesScan, rewrites: dict[str, str]) -> dict[str, Any]:
    """A copy of the species data with the rule fixes and the accepted rewrites (key -> new
    sentence). Sentences without an accepted rewrite keep their dash."""
    data = copy.deepcopy(scan.data)
    for (field, lang), text in scan.fixed.items():
        data[field][lang] = text
    targets: dict[tuple[str, str], list[DashSentence]] = {}
    for s in scan.sentences:
        if s.key in rewrites:
            targets.setdefault((s.field, s.lang), []).append(s)
    for (field, lang), items in targets.items():
        lines = data[field][lang].split("\n")
        for line_no in sorted({s.line for s in items}):
            parts = split_sentences(lines[line_no])
            for s in items:
                if s.line == line_no:
                    parts[s.part] = rewrites[s.key]
            lines[line_no] = "".join(parts)
        data[field][lang] = "\n".join(lines)
    return data


def dump_species(data: dict[str, Any], path: Path) -> None:
    """The settings of yaml_writer.write_species_yaml, so a field nobody touched keeps its form."""
    path.write_text(
        yaml.safe_dump(data, sort_keys=False, allow_unicode=True, default_flow_style=False),
        encoding="utf-8",
    )


def species_files(species_root: Path, qids: tuple[str, ...] = ()) -> list[Path]:
    files = sorted(species_root.rglob("Q*.yaml"))
    return [p for p in files if not qids or p.stem in qids]
