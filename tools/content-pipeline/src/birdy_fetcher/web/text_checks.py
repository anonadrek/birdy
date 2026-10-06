"""Code checks for the page text (spec 2026-09-25 §9.6, rules 1 to 7)."""
# ruff: noqa: RUF001

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

from .checks import DASHES, _style, sentence_count
from .text_model import LangTextV2, Sentence, WebTextV2, iter_sentences, remove_paths

LEAD_MAX_WORDS = 45
LEAD_MAX_SENTENCES = 2
MARKS_MIN, MARKS_MAX, MARK_MAX_WORDS = 3, 4, 16
VOICE_MAX_WORDS = 60
WHERE_MAX_WORDS = 70
BEHAVIOUR_MAX_WORDS = 70
LOOKALIKES_MAX = 3
LOOKALIKE_MAX_WORDS = 35
META_MIN, META_MAX = 120, 155


@dataclass(frozen=True)
class TextIssue:
    path: str
    message: str
    removable: bool


@dataclass(frozen=True)
class TextContext:
    facts_by_id: dict[str, dict[str, Any]]
    lookalike_others: frozenset[str]

    @classmethod
    def from_facts(cls, facts: list[dict[str, Any]]) -> TextContext:
        others: set[str] = set()
        for fact in facts:
            if fact.get("topic") == "lookalike":
                other = fact.get("other", {})
                others.update(v for v in (other.get("qid"), other.get("scientific")) if v)
        return cls({f["id"]: f for f in facts}, frozenset(others))


_NUMBER = re.compile(r"\d+(?:[.,]\d+)?")


def _merge_space_grouped_thousands(text: str) -> str:
    """Merge Swedish space-grouped thousands like '1 200' or '2 300 000'.

    Handles regular space, U+00A0 (NBSP), and U+202F (NNBSP) as separators.
    Only merges valid thousands groups: 1-3 leading digits followed by one or more
    groups of exactly 3 digits. Does not merge groups separated by words.
    """

    def merge(match: re.Match[str]) -> str:
        # Remove all space-like separators
        result = match.group(0)
        result = result.replace(" ", "")  # regular space
        result = result.replace(" ", "")  # NBSP
        result = result.replace(" ", "")  # NNBSP
        return result

    # Pattern: 1-3 digits followed by (space-like + exactly 3 digits) one or more times
    # Negative lookahead ensures no match if followed by a digit (invalid structure)
    pattern = r"(?<!\d)(\d{1,3})(?:[   ]\d{3})+(?!\d)"
    return re.sub(pattern, merge, text)


def numbers(text: str) -> set[str]:
    """Extract all numbers from text, merging space-grouped thousands first.

    Normalizes decimal commas to dots for comparison.
    """
    merged = _merge_space_grouped_thousands(text)
    return {n.replace(",", ".") for n in _NUMBER.findall(merged)}


def fact_corpus(fact: dict[str, Any]) -> str:
    """The text a sentence citing this fact may draw numbers from. An edited fact's own
    sources still hold the pre-edit quote (fix 2026-10-06, the edited fact is judged on its
    own value, not a quote that may now be stale), so it is left out."""
    if fact.get("edited"):
        return str(fact.get("sv", ""))
    quotes = [s.get("quote", "") for s in fact.get("sources", [])]
    return " ".join([str(fact.get("sv", "")), *quotes])


def _words(items: list[Sentence]) -> int:
    return sum(len(s.text.split()) for s in items)


def sentence_issues(
    path: str,
    lang: str,
    sentence: Sentence,
    ctx: TextContext,
    banned: list[str],
    *,
    prefix: str | None = None,
) -> list[TextIssue]:
    """Rules 1 to 5 for one sentence. `prefix` limits which fact ids it may cite (used by
    the comparison table, where a cell for species A may only cite A's facts)."""
    issues = [TextIssue(path, i.message, True) for i in _style(path, lang, sentence.text, banned)]
    if not sentence.fact_ids:
        return [*issues, TextIssue(path, "anger inga fakta", True)]
    unknown = [
        f
        for f in sentence.fact_ids
        if f not in ctx.facts_by_id or (prefix is not None and not f.startswith(prefix))
    ]
    if unknown:
        return [
            *issues,
            TextIssue(path, f"anger fakta som inte finns här: {', '.join(unknown)}", True),
        ]
    corpus = " ".join(fact_corpus(ctx.facts_by_id[f]) for f in sentence.fact_ids)
    missing = sorted(numbers(sentence.text) - numbers(corpus))
    if missing:
        issues.append(
            TextIssue(
                path, f"talet {', '.join(missing)} finns inte i de fakta meningen anger", True
            )
        )
    return issues


def _size_issues(lang: str, text: LangTextV2, ctx: TextContext) -> list[TextIssue]:
    size = text.size
    path = f"{lang}.size"
    if size is None:
        return []
    if not re.search(r"\d", size.value):
        return [TextIssue(path, "storleken saknar siffror", True)]
    if any(d in size.value for d in DASHES):
        return [TextIssue(path, "storleken innehåller tankstreck", True)]
    if not size.fact_ids or any(f not in ctx.facts_by_id for f in size.fact_ids):
        return [TextIssue(path, "storleken anger inga giltiga fakta", True)]
    corpus = " ".join(fact_corpus(ctx.facts_by_id[f]) for f in size.fact_ids)
    if numbers(size.value) - numbers(corpus):
        return [TextIssue(path, "siffrorna i storleken finns inte i faktan", True)]
    return []


def check_lang(lang: str, text: LangTextV2, ctx: TextContext, banned: list[str]) -> list[TextIssue]:
    issues: list[TextIssue] = []
    for suffix, sentence in iter_sentences(text):
        issues += sentence_issues(f"{lang}.{suffix}", lang, sentence, ctx, banned)

    def field(name: str, message: str) -> None:
        issues.append(TextIssue(f"{lang}.{name}", message, False))

    lead_sentences = sum(sentence_count(s.text) for s in text.lead)
    if not text.lead:
        field("lead", "saknas")
    elif lead_sentences > LEAD_MAX_SENTENCES or _words(text.lead) > LEAD_MAX_WORDS:
        field("lead", "ska vara högst 2 meningar och 45 ord")
    if not MARKS_MIN <= len(text.field_marks) <= MARKS_MAX:
        field("field_marks", "ska ha 3 eller 4 punkter")
    for i, mark in enumerate(text.field_marks):
        if len(mark.text.split()) > MARK_MAX_WORDS:
            issues.append(TextIssue(f"{lang}.field_marks[{i}]", "ska vara högst 16 ord", True))
    if not text.voice:
        field("voice", "saknas")
    elif _words(text.voice) > VOICE_MAX_WORDS:
        field("voice", "ska vara högst 60 ord")
    if not text.where_when:
        field("where_when", "saknas")
    elif _words(text.where_when) > WHERE_MAX_WORDS:
        field("where_when", "ska vara högst 70 ord")
    if _words(text.behaviour) > BEHAVIOUR_MAX_WORDS:
        field("behaviour", "ska vara högst 70 ord")
    if len(text.look_alikes) > LOOKALIKES_MAX:
        field("look_alikes", "ska vara högst 3")
    for i, look_alike in enumerate(text.look_alikes):
        path = f"{lang}.look_alikes[{i}]"
        if look_alike.other not in ctx.lookalike_others:
            message = f"{look_alike.other} finns inte bland förväxlingsarterna i faktabladet"
            issues.append(TextIssue(path, message, True))
        elif _words(look_alike.sentences) > LOOKALIKE_MAX_WORDS:
            issues.append(TextIssue(path, "ska vara högst 35 ord", True))
    meta_path = f"{lang}.meta_description"
    issues += [
        TextIssue(meta_path, i.message, False)
        for i in _style(meta_path, lang, text.meta_description, banned)
    ]
    if not META_MIN <= len(text.meta_description) <= META_MAX:
        field("meta_description", f"ska vara 120 till 155 tecken (är {len(text.meta_description)})")
    return issues + _size_issues(lang, text, ctx)


def check_text(text: WebTextV2, ctx: TextContext, banned: list[str]) -> list[TextIssue]:
    return check_lang("sv", text.sv, ctx, banned) + check_lang("en", text.en, ctx, banned)


def settle(
    text: WebTextV2, ctx: TextContext, banned: list[str]
) -> tuple[WebTextV2, list[str], list[str]]:
    """Removes every removable part that breaks a rule, then checks again. Returns the text,
    notes about what was removed, and the problems that remain (they fail the species)."""
    issues = check_text(text, ctx, banned)
    removable = {i.path for i in issues if i.removable}
    notes = [f"{i.path} togs bort: {i.message}" for i in issues if i.removable]
    if removable:
        text = remove_paths(text, removable)
        issues = check_text(text, ctx, banned)
    return text, notes, [f"{i.path}: {i.message}" for i in issues]


def minimum_problems(text: WebTextV2) -> list[str]:
    problems: list[str] = []
    for lang in ("sv", "en"):
        t: LangTextV2 = getattr(text, lang)
        if not t.lead:
            problems.append(f"{lang}.lead saknas")
        if len(t.field_marks) < MARKS_MIN:
            problems.append(f"{lang}.field_marks har färre än 3 punkter")
        if not t.voice:
            problems.append(f"{lang}.voice saknas")
        if not t.where_when:
            problems.append(f"{lang}.where_when saknas")
        if not META_MIN <= len(t.meta_description) <= META_MAX:
            problems.append(f"{lang}.meta_description har fel längd")
    return problems
