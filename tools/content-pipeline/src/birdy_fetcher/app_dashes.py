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
# A number or month range only becomes "till"/"to" (rule_fix below, on this regex and the
# month one it builds) when a digit, or a month name, touches the dash (U+2013/U+2014) on
# BOTH sides. A bare year next to an aside remark that happens to be set off by a dash is not
# a range and is left for the model step in app_dashes_run.py on purpose.
_NUMBER_RANGE = re.compile(r"(?<=\d)\s*[\u2013\u2014]\s*(?=\d)")
_NUMBER = re.compile(r"\d+(?:[.,]\d+)?")
# A space, no-break space (U+00A0) or narrow no-break space (U+202F) used as a thousands
# grouping ("1 000"); folded away before counting digits in check_rewrite so a rewrite that
# groups the same number differently (or not at all, "1000") is not rejected as "talen
# skiljer sig".
_THOUSANDS_GROUP_SPACE = re.compile(r"(?<=\d)[ \u00a0\u202f](?=\d{3}\b)")
# The sentence boundary of web/checks.py (punctuation, space, capital letter, so "bl.a." and "e.g."
# do not end a sentence), captured so a paragraph can be put back together byte for byte.
_SENTENCE_BOUNDARY = re.compile(r"((?<=[.?!])\s+(?=[A-ZÅÄÖ]))")
_ATX_HEADING_MAX_HASHES = 6
_BOLD_HEADING_MIN_LENGTH = 5
_MIN_LENGTH_RATIO = 0.7
_MAX_LENGTH_RATIO = 1.4

# SpeciesTextNoData.kt: sentences the content pipeline writes when Wikipedia has no data, and
# openings of model meta-commentary about its source instead of species content. Both the
# HEADING form (isNoDataHeadingLine, as is_no_data_heading below) and the OPENING form
# (isNoDataText, as is_no_data_text below) are ported: a heading that IS one of these, or a
# body that OPENS with one once the heading (if any) and the blank lines after it are gone,
# collapses the WHOLE field, same as the app (SpeciesTextCleaner.cleanSpeciesText);
# visible_line_indexes uses both. isTrailingNoDataParagraph (the sentinel or a labelled remark
# as the paragraph at the END of an otherwise real text, T11f) stays unported: a known narrow
# gap, not a verified impossibility (343 corpus matches measured 2026-10-08, none with a dash
# in the real content kept before the trailing paragraph).
_NO_DATA_SENTINELS = (
    "Migration data unavailable for this species.",
    "Migrationsdata saknas för denna art.",
)
_SOURCE_META_OPENINGS = (
    "The source text",
    "The provided source text",
    "The Wikipedia source text",
    "Källtexten",
)
_LANGUAGE_LABEL_PREFIXES = ("sv:", "en:")
_OPENING_QUOTES = '"\u201c\u201d\u201e'


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


def is_no_data_heading(line: str) -> bool:
    """SpeciesTextNoData.isNoDataHeadingLine: `line` is already known to be a heading
    (`is_hidden_heading`); true when its text, with the `#`/`**` wrapping removed and a
    trailing dot or space trimmed, IS one of the `_NO_DATA_SENTINELS` (case-insensitively) or
    opens with one of the `_SOURCE_META_OPENINGS` phrases, e.g. "# Migration Data Unavailable
    for This Species" or "# Migration data unavailable for this species.".
    """
    inner = line.strip()
    if inner.startswith("#"):
        inner = inner.lstrip("#").strip()
    elif inner.startswith("**") and inner.endswith("**"):
        inner = inner[2:-2].strip()
    normalized = inner.rstrip(". ").lower()
    if not normalized:
        return False
    if any(normalized == sentinel.rstrip(".").lower() for sentinel in _NO_DATA_SENTINELS):
        return True
    return any(normalized.startswith(opening.lower()) for opening in _SOURCE_META_OPENINGS)


def _opening_for_no_data_check(body: str) -> str:
    """SpeciesTextNoData.openingForNoDataCheck: `body` with every bold marker removed, then
    one leading language label (`"sv:"`/`"en:"`, case-insensitive) and one leading opening
    quote (`_OPENING_QUOTES`) trimmed off the front, in that order."""
    opening = body.replace("**", "").lstrip()
    for label in _LANGUAGE_LABEL_PREFIXES:
        if opening[: len(label)].lower() == label:
            opening = opening[len(label) :].lstrip()
            break
    if opening and opening[0] in _OPENING_QUOTES:
        opening = opening[1:]
    return opening


def _starts_with_first_letter_ignoring_case(text: str, prefix: str) -> bool:
    """SpeciesTextNoData.startsWithFirstLetterIgnoringCase: `text` starts with `prefix`,
    case-insensitively on the first character and case-sensitively on the rest (a markdown
    heading capitalises every word of `prefix`, so this never matches a heading on its own;
    `is_no_data_heading` covers the heading form separately, by lowercasing the whole line)."""
    return (
        len(text) >= len(prefix)
        and text[0].lower() == prefix[0].lower()
        and text.startswith(prefix[1:], 1)
    )


def is_no_data_text(body: str) -> bool:
    """SpeciesTextNoData.isNoDataText: true when `body` opens with a `_NO_DATA_SENTINELS`
    sentence or a `_SOURCE_META_OPENINGS` phrase, once `_opening_for_no_data_check` has
    trimmed bold markers, a language label and an opening quote off its start. `body` here is
    whatever `cleanSpeciesText` built: the text with a real (non-no-data) heading and the
    blank lines after it removed, or the WHOLE text when there was no heading to remove.
    """
    opening = _opening_for_no_data_check(body)
    sentinel_match = any(
        _starts_with_first_letter_ignoring_case(opening, s) for s in _NO_DATA_SENTINELS
    )
    meta_match = any(
        _starts_with_first_letter_ignoring_case(opening, s) for s in _SOURCE_META_OPENINGS
    )
    return sentinel_match or meta_match


def visible_line_indexes(lines: list[str]) -> range:
    """The lines the app shows. A first line that is a heading is always hidden. Beyond that,
    SpeciesTextCleaner.cleanSpeciesText collapses the WHOLE field to its own empty-state text,
    so NOTHING is visible (not even the paragraph after the heading), when: that heading is
    itself a no-data heading (`is_no_data_heading`); or the body after the heading and the
    blank lines that followed it opens with a no-data sentinel or source-meta phrase
    (`is_no_data_text`); or, when there is no heading at all, the WHOLE text opens with one.
    """
    if lines and is_hidden_heading(lines[0]):
        if is_no_data_heading(lines[0]):
            return range(0, 0)
        body_start = 1
        while body_start < len(lines) and not lines[body_start].strip():
            body_start += 1
        if is_no_data_text("\n".join(lines[body_start:])):
            return range(0, 0)
        return range(1, len(lines))
    if is_no_data_text("\n".join(lines)):
        return range(0, 0)
    return range(0, len(lines))


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


def _grouped_numbers(text: str) -> list[str]:
    """`_NUMBER.findall`, after folding away a thousands-grouping space
    (`_THOUSANDS_GROUP_SPACE`) so "1 000" counts as the same number as "1000".
    """
    return _NUMBER.findall(_THOUSANDS_GROUP_SPACE.sub("", text))


def check_rewrite(original: str, rewritten: str) -> list[str]:
    """Code checks on a model's rewrite of one sentence; empty when it may be used."""
    problems: list[str] = []
    if DASH.search(rewritten):
        problems.append("tankstreck kvar")
    if Counter(_grouped_numbers(original)) != Counter(_grouped_numbers(rewritten)):
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
    """The same settings as yaml_writer.write_species_yaml, so a field nobody touched keeps
    its form: a file written by write_species_yaml round-trips byte for byte. A file whose
    long lines were wrapped by another tool may be rewrapped at 80 columns, with the parsed
    data identical either way.
    """
    path.write_text(
        yaml.safe_dump(data, sort_keys=False, allow_unicode=True, default_flow_style=False),
        encoding="utf-8",
    )


def species_files(species_root: Path, qids: tuple[str, ...] = ()) -> list[Path]:
    files = sorted(species_root.rglob("Q*.yaml"))
    return [p for p in files if not qids or p.stem in qids]
