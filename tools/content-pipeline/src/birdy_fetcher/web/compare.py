"""Look-alike pairs, their search volumes and their comparison texts (spec 2026-09-25 §7
and §9.7).

Fix 2026-10-06 (Task 21 review, "With fixes"): strict volume parsing (item 1), the shared
BOM-safe/formula-escaping/atomic `sheet_csv` helpers instead of a plain `csv.DictWriter`
(item 2 and 4), orientation- and whitespace-proof matching of old rows (item 3), stale
candidates kept instead of silently dropped (item 5), a missing-volumes count and a
name-change clear (item 6 and 8), row order for whoever fills the sheet in (item 9),
duplicate-row handling (item 7), and English queries that also try the name without its
IOC qualifier (item 12).

Task 22 adds the comparison texts (`web compare`, spec §9.7): written from the two
species' verified fact sheets with the write/check/rewrite/remove loop the species texts
use (`checked_writer.write_checked`)."""

from __future__ import annotations

import asyncio
import re
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from anthropic.types import MessageParam
from pydantic import BaseModel

from ..claude_summarizer import _split_prompt
from ..cost import CostTracker, MaxCostExceeded
from .checked_writer import Checks, Written, write_checked
from .checker import PROMPT_VERSION as CHECK_PROMPT_VERSION
from .checker import CheckerFailed, CheckItem, SentenceChecker
from .checks import _style, load_banned, sentence_count
from .llm import MODELS, AnthropicJsonClient, JsonModelClient, ModelReply, record_cost
from .paths import WebPaths
from .record import Record, facts_hash, load_all, load_record, save_record
from .report import StepOutcome, render_step_report, write_step_report
from .sheet_csv import read_sheet, write_sheet
from .text_checks import TextContext, TextIssue, sentence_issues
from .text_model import Sentence
from .text_step import facts_verified, prompt_file_hash, render_facts, writer_facts
from .verify import missing_required_topics

VOLUMES_FILE = "comparison-volumes.csv"
COLUMNS = [
    "a_qid", "b_qid", "a_sv", "b_sv", "a_en", "b_en",
    "sv_queries", "en_queries", "sv_volume", "en_volume", "aktuell",
]  # fmt: skip
TOP = 30
# 78 of 180 species carry an IOC qualifier searchers rarely type (spec 2026-09-25 §7,
# fix 2026-10-06 item 12).
EN_IOC_PREFIXES = ("eurasian ", "common ", "northern ", "western ", "european ")


@dataclass(frozen=True, order=True)
class Pair:
    """`a` comes first in Swedish slug order (spec appendix D)."""

    a: str
    b: str


def ordered_pair(x: str, y: str, records: dict[str, Record]) -> Pair:
    first, second = sorted((x, y), key=lambda q: str(records[q]["slug"]["sv"]))
    return Pair(first, second)


def candidate_pairs(records: dict[str, Record]) -> list[Pair]:
    seen: set[frozenset[str]] = set()
    pairs: list[Pair] = []
    for qid, record in records.items():
        for fact in record.get("facts", []):
            if fact.get("topic") != "lookalike":
                continue
            other = fact.get("other", {}).get("qid")
            if not other or other == qid or other not in records:
                continue
            key = frozenset((qid, other))
            if key not in seen:
                seen.add(key)
                pairs.append(ordered_pair(qid, other, records))
    return sorted(pairs)


def _strip_en_prefix(name: str) -> str:
    for prefix in EN_IOC_PREFIXES:
        if name.startswith(prefix):
            return name[len(prefix) :]
    return name


def queries(pair: Pair, records: dict[str, Record]) -> dict[str, list[str]]:
    a_sv, b_sv = (str(records[q]["names"]["sv"]).lower() for q in (pair.a, pair.b))
    a_en, b_en = (str(records[q]["names"]["en"]).lower() for q in (pair.a, pair.b))
    en = [f"{a_en} vs {b_en}", f"{b_en} vs {a_en}", f"difference between {a_en} and {b_en}"]
    a_short, b_short = _strip_en_prefix(a_en), _strip_en_prefix(b_en)
    if a_short != a_en or b_short != b_en:
        # (fix 2026-10-06, item 12) e.g. "Eurasian Blue Tit" vs "Great Tit" reads ~0 in
        # Keyword Planner; add the same phrasings with the qualifier stripped, deduped.
        for extra in (
            f"{a_short} vs {b_short}",
            f"{b_short} vs {a_short}",
            f"difference between {a_short} and {b_short}",
        ):
            if extra not in en:
                en.append(extra)
    return {
        "sv": [
            f"{a_sv} eller {b_sv}",
            f"{b_sv} eller {a_sv}",
            f"skillnad {a_sv} {b_sv}",
            f"{a_sv} {b_sv} skillnad",
        ],
        "en": en,
    }


_PLAIN = re.compile(r"\d+")
_GROUPED = re.compile(r"\d{1,3}([ \u00a0,.]\d{3})+")
_THOUSANDS_K = re.compile(r"\d+[kK]")
# Keyword Planner (and a hand-edit) can write "<10" with or without a space inside
# (review fix 2026-10-06, item 3).
_BELOW_THRESHOLD = re.compile(r"<\s*10")
# Keyword Planner's "<10" bucket is a real, nonzero volume it will not show precisely.
# Documented choice (item 1): map it to 5, the bucket's midpoint, not 0 -- 0 would be
# indistinguishable from a cell nobody has filled in yet, and would let `select_pairs`
# silently drop a pair real searchers do look up (item 6).
BELOW_THRESHOLD_VALUE = 5


def _number(value: str, *, lang: str, a_qid: str, b_qid: str) -> int:
    """Strict volume parser (fix 2026-10-06, item 1): "", a plain integer, digits grouped
    in threes by space/NBSP/comma/period, an optional "K" suffix, or Keyword Planner's
    "<10" marker. Anything else -- a range like "1K - 10K", a decimal, a formula -- raises
    rather than silently mis-parsing (the old version joined every digit, so "1K - 10K"
    became 110)."""
    text = value.strip()
    if not text:
        return 0
    if _BELOW_THRESHOLD.fullmatch(text):
        return BELOW_THRESHOLD_VALUE
    if _PLAIN.fullmatch(text):
        return int(text)
    if _GROUPED.fullmatch(text):
        return int(re.sub(r"[ \u00a0,.]", "", text))
    if _THOUSANDS_K.fullmatch(text):
        return int(text[:-1]) * 1000
    raise ValueError(
        f"Ogiltig volym {value!r} för {a_qid}/{b_qid} ({lang}_volume): skriv ett heltal"
    )


def read_volumes(path: Path) -> dict[Pair, tuple[int, int]]:
    """Current candidates only (fix 2026-10-06, item 5): a row an earlier `write_candidates`
    marked `aktuell: nej` (no longer a look-alike pair, kept only because it had a volume)
    is skipped, so `select_pairs` never ranks a stale pair. A row missing a_qid or b_qid
    is skipped too (fix 2026-10-06, item 2), consistent with `_load_old` below. Two rows
    for the same pair in the same orientation with different volumes raise instead of the
    last one silently winning (review fix 2026-10-06, M6; `run_compare` does the same for
    flipped rows)."""
    if not path.exists():
        return {}
    volumes: dict[Pair, tuple[int, int]] = {}
    for row in read_sheet(path, required_columns=("a_qid", "b_qid")):
        if row.get("aktuell", "ja").strip().lower() == "nej":
            continue
        a_qid, b_qid = row.get("a_qid", "").strip(), row.get("b_qid", "").strip()
        if not a_qid or not b_qid:
            continue
        sv = _number(row.get("sv_volume", ""), lang="sv", a_qid=a_qid, b_qid=b_qid)
        en = _number(row.get("en_volume", ""), lang="en", a_qid=a_qid, b_qid=b_qid)
        pair = Pair(a_qid, b_qid)
        if pair in volumes and volumes[pair] != (sv, en):
            raise ValueError(_duplicate_volumes_error(a_qid, b_qid, volumes[pair], (sv, en)))
        volumes[pair] = (sv, en)
    return volumes


def _duplicate_volumes_error(
    a_qid: str, b_qid: str, first: tuple[int, int], second: tuple[int, int]
) -> str:
    return (
        f"Dubblettrader för {a_qid}/{b_qid} i {VOLUMES_FILE} har olika volymer: {first} och "
        f"{second}. Rätta filen eller kör web compare-candidates."
    )


def _merge_duplicate(
    first: dict[str, str], second: dict[str, str], *, a_qid: str, b_qid: str
) -> dict[str, str]:
    """Two old rows for the same pair (fix 2026-10-06, item 7): keep the non-empty volume;
    if both are non-empty and parse to different numbers, raise rather than silently pick
    one."""
    merged = dict(first)
    for lang in ("sv", "en"):
        key = f"{lang}_volume"
        a_value, b_value = first.get(key, "").strip(), second.get(key, "").strip()
        if a_value and b_value and a_value != b_value:
            same = _number(a_value, lang=lang, a_qid=a_qid, b_qid=b_qid) == _number(
                b_value, lang=lang, a_qid=a_qid, b_qid=b_qid
            )
            if not same:
                raise ValueError(
                    f"Dubblettrader för {a_qid}/{b_qid} har olika {lang}_volume: "
                    f"{a_value!r} och {b_value!r}"
                )
        merged[key] = a_value or b_value
    return merged


def _load_old(path: Path) -> dict[frozenset[str], dict[str, str]]:
    """Old rows keyed by the frozenset of stripped QIDs (fix 2026-10-06, item 3), so a
    hand-flipped a/b or stray whitespace still matches and keeps its volume."""
    if not path.exists():
        return {}
    merged: dict[frozenset[str], dict[str, str]] = {}
    for row in read_sheet(path, required_columns=("a_qid",)):
        a_qid, b_qid = row.get("a_qid", "").strip(), row.get("b_qid", "").strip()
        if not a_qid or not b_qid:
            continue
        key = frozenset((a_qid, b_qid))
        if key in merged:
            merged[key] = _merge_duplicate(merged[key], row, a_qid=a_qid, b_qid=b_qid)
        else:
            merged[key] = {**row, "a_qid": a_qid, "b_qid": b_qid}
    return merged


@dataclass
class CandidatesWrite:
    path: Path
    pairs: list[Pair]
    missing_volumes: int = 0
    cleared_sv: int = 0
    cleared_en: int = 0


def write_candidates(path: Path, pairs: list[Pair], records: dict[str, Record]) -> CandidatesWrite:
    """Writes `review/comparison-volumes.csv`. Builds every row in memory first and
    raises before touching the file if anything is wrong with the old data (invalid or
    conflicting volumes) -- `sheet_csv.write_sheet`'s atomic replace (item 4) then means a
    raise here never leaves a half-written file where a good one was."""
    old = _load_old(path)
    current_keys = {frozenset((pair.a, pair.b)) for pair in pairs}
    rows: list[dict[str, str]] = []
    missing = cleared_sv = cleared_en = 0
    for pair in pairs:
        q = queries(pair, records)
        sv_queries, en_queries = "; ".join(q["sv"]), "; ".join(q["en"])
        kept = old.get(frozenset((pair.a, pair.b)), {})
        sv_volume, en_volume = kept.get("sv_volume", "").strip(), kept.get("en_volume", "").strip()
        if sv_volume:
            _number(sv_volume, lang="sv", a_qid=pair.a, b_qid=pair.b)
            if kept.get("sv_queries", sv_queries) != sv_queries:
                # (fix 2026-10-06, item 8) a name change: the old volume no longer
                # measures what the file now says it does.
                sv_volume = ""
                cleared_sv += 1
        if en_volume:
            _number(en_volume, lang="en", a_qid=pair.a, b_qid=pair.b)
            if kept.get("en_queries", en_queries) != en_queries:
                en_volume = ""
                cleared_en += 1
        if not sv_volume and not en_volume:
            # (fix 2026-10-06, item 6) an empty cell, distinct from a typed "0".
            missing += 1
        rows.append(
            {
                "a_qid": pair.a,
                "b_qid": pair.b,
                "a_sv": records[pair.a]["names"]["sv"],
                "b_sv": records[pair.b]["names"]["sv"],
                "a_en": records[pair.a]["names"]["en"],
                "b_en": records[pair.b]["names"]["en"],
                "sv_queries": sv_queries,
                "en_queries": en_queries,
                "sv_volume": sv_volume,
                "en_volume": en_volume,
                "aktuell": "ja",
            }
        )
    for key, row in old.items():
        if key in current_keys:
            continue
        sv_volume, en_volume = row.get("sv_volume", "").strip(), row.get("en_volume", "").strip()
        if not sv_volume and not en_volume:
            continue  # nothing worth preserving (item 5): drop it like before
        a_qid, b_qid = row.get("a_qid", "").strip(), row.get("b_qid", "").strip()
        if sv_volume:
            _number(sv_volume, lang="sv", a_qid=a_qid, b_qid=b_qid)
        if en_volume:
            _number(en_volume, lang="en", a_qid=a_qid, b_qid=b_qid)
        rows.append(
            {
                "a_qid": a_qid,
                "b_qid": b_qid,
                "a_sv": row.get("a_sv", ""),
                "b_sv": row.get("b_sv", ""),
                "a_en": row.get("a_en", ""),
                "b_en": row.get("b_en", ""),
                "sv_queries": row.get("sv_queries", ""),
                "en_queries": row.get("en_queries", ""),
                "sv_volume": sv_volume,
                "en_volume": en_volume,
                "aktuell": "nej",
            }
        )
    # (fix 2026-10-06, item 9 and 5) current rows first, sorted for whoever fills them
    # in; stale rows (aktuell: nej) kept at the end, sorted the same way.
    rows.sort(key=lambda r: (r["aktuell"] == "nej", r["a_sv"], r["b_sv"]))
    write_sheet(path, rows, COLUMNS)
    return CandidatesWrite(
        path=path,
        pairs=pairs,
        missing_volumes=missing,
        cleared_sv=cleared_sv,
        cleared_en=cleared_en,
    )


def select_pairs(volumes: dict[Pair, tuple[int, int]], top: int = TOP) -> list[Pair]:
    ranked = sorted(
        ((pair, sv, en) for pair, (sv, en) in volumes.items() if sv or en),
        key=lambda x: (-x[1], -x[2], x[0]),
    )
    return [pair for pair, _, _ in ranked[:top]]


def comparison_slugs(pair: Pair, records: dict[str, Record]) -> dict[str, str]:
    a, b = records[pair.a], records[pair.b]
    # (fix 2026-10-06, item 3) sort the Swedish slugs independently, like the English
    # ones already were -- `pair.a`/`pair.b` are not guaranteed to be in Swedish-slug
    # order if the Pair came back from `read_volumes` without re-normalising.
    first_sv, second_sv = sorted([str(a["slug"]["sv"]), str(b["slug"]["sv"])])
    first_en, second_en = sorted([str(a["slug"]["en"]), str(b["slug"]["en"])])
    return {"sv": f"{first_sv}-eller-{second_sv}", "en": f"{first_en}-vs-{second_en}"}


def comparison_path(out_dir: Path, pair: Pair) -> Path:
    first, second = sorted((pair.a, pair.b))
    return out_dir / f"{first}_{second}.json"


# -- the comparison texts (Task 22, spec §7 and §9.7) ------------------------------------

PROMPT_VERSION = "compare-v1"
SHORT_MAX_WORDS = 45
SHORT_MAX_SENTENCES = 2
ROWS_MIN, ROWS_MAX = 3, 5
CELL_MAX_WORDS = 14
FEATURE_MAX_WORDS = 4
META_MIN, META_MAX = 120, 155
SIDES = ("a", "b")


class Cell(BaseModel):
    text: str
    fact_ids: list[str]


class Row(BaseModel):
    feature: str
    a: Cell
    b: Cell


class CompareLang(BaseModel):
    short_answer: list[Sentence]
    rows: list[Row]
    meta_description: str


class CompareOutput(BaseModel):
    sv: CompareLang
    en: CompareLang


def prefixed_facts(a: Record, b: Record) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """Each species' writer facts with its side in front of the id (`a:f01`, `b:d01`,
    spec appendix D)."""

    def prefix(side: str, record: Record) -> list[dict[str, Any]]:
        return [{**f, "id": f"{side}:{f['id']}"} for f in writer_facts(record)]

    return prefix("a", a), prefix("b", b)


def pair_context(a: Record, b: Record) -> TextContext:
    a_facts, b_facts = prefixed_facts(a, b)
    return TextContext.from_facts(a_facts + b_facts)


def meta_openings(a: Record, b: Record) -> dict[str, str]:
    """The question each meta description opens with (review fix 2026-10-06, M1): the
    Swedish one in Swedish slug order, the English one in English slug order, the order
    fas 2 uses for each language's page title and slug."""
    sv_first, sv_second = sorted((a, b), key=lambda r: str(r["slug"]["sv"]))
    en_first, en_second = sorted((a, b), key=lambda r: str(r["slug"]["en"]))
    return {
        "sv": f"{sv_first['names']['sv']} eller {sv_second['names']['sv']}?",
        "en": f"{en_first['names']['en']} or {en_second['names']['en']}?",
    }


def _check_lang(
    lang: str, t: CompareLang, ctx: TextContext, banned: list[str], opening: str | None
) -> list[TextIssue]:
    issues: list[TextIssue] = []
    for i, sentence in enumerate(t.short_answer):
        issues += sentence_issues(f"{lang}.short_answer[{i}]", lang, sentence, ctx, banned)
    words = sum(len(s.text.split()) for s in t.short_answer)
    sentences = sum(sentence_count(s.text) for s in t.short_answer)
    if not t.short_answer:
        issues.append(TextIssue(f"{lang}.short_answer", "saknas", False))
    elif words > SHORT_MAX_WORDS or sentences > SHORT_MAX_SENTENCES:
        issues.append(
            TextIssue(f"{lang}.short_answer", "ska vara högst 2 meningar och 45 ord", False)
        )
    if not ROWS_MIN <= len(t.rows) <= ROWS_MAX:
        issues.append(TextIssue(f"{lang}.rows", "ska ha 3 till 5 rader", False))
    for i, row in enumerate(t.rows):
        # A row stands or falls as a whole: a cell that breaks a rule takes its row with it.
        path = f"{lang}.rows[{i}]"
        problems = [f"kännetecknet {x.message}" for x in _style(path, lang, row.feature, banned)]
        if len(row.feature.split()) > FEATURE_MAX_WORDS:
            problems.append("kännetecknet ska vara 1 till 4 ord")
        for side in SIDES:
            cell: Cell = getattr(row, side)
            if len(cell.text.split()) > CELL_MAX_WORDS:
                problems.append(f"{side} ska vara högst 14 ord")
            sentence = Sentence(text=cell.text, fact_ids=cell.fact_ids)
            # A cell may only cite facts from its own species' sheet (spec §9.7).
            problems += [
                f"{side}: {x.message}"
                for x in sentence_issues(path, lang, sentence, ctx, banned, prefix=f"{side}:")
            ]
        issues += [TextIssue(path, message, True) for message in problems]
    meta_path = f"{lang}.meta_description"
    issues += [
        TextIssue(meta_path, x.message, False)
        for x in _style(meta_path, lang, t.meta_description, banned)
    ]
    if not META_MIN <= len(t.meta_description) <= META_MAX:
        issues.append(
            TextIssue(
                meta_path, f"ska vara 120 till 155 tecken (är {len(t.meta_description)})", False
            )
        )
    if any(ch.isdigit() for ch in t.meta_description):
        # Same rule as the species texts (M8): the meta description cites no facts, so a
        # number in it could never be checked.
        issues.append(TextIssue(meta_path, "innehåller siffror", False))
    if opening is not None and not _opening_key(t.meta_description).startswith(
        _opening_key(opening)
    ):
        issues.append(TextIssue(meta_path, f'ska börja med frågan "{opening}"', False))
    return issues


_CURLY_APOSTROPHES = str.maketrans({"\u2019": "'", "\u2018": "'"})


def _opening_key(text: str) -> str:
    """How a meta description's opening question is compared: case-insensitive (the
    second Swedish name is written as in the middle of a sentence, "Blåmes eller
    talgoxe?"), without leading or trailing space, and with curly apostrophes (U+2019,
    U+2018) read as straight ones, so a model writing "Montagu's Harrier" with a curly
    apostrophe still matches (follow-up 2026-10-06)."""
    return text.strip().translate(_CURLY_APOSTROPHES).casefold()


def check_compare(
    text: CompareOutput,
    ctx: TextContext,
    banned: list[str],
    *,
    openings: dict[str, str] | None = None,
) -> list[TextIssue]:
    """Every code-check issue. With `openings` (see `meta_openings`), each meta description
    must also open with its language's question."""
    sv_opening = openings["sv"] if openings else None
    en_opening = openings["en"] if openings else None
    return _check_lang("sv", text.sv, ctx, banned, sv_opening) + _check_lang(
        "en", text.en, ctx, banned, en_opening
    )


def compare_path_texts(text: CompareOutput) -> dict[str, str]:
    """Every removable path (and every checker item id) to the text it holds, so a removal
    note shows what was dropped (same as `text_checks.path_texts`)."""
    mapping: dict[str, str] = {}
    for lang in ("sv", "en"):
        t: CompareLang = getattr(text, lang)
        for i, sentence in enumerate(t.short_answer):
            mapping[f"{lang}.short_answer[{i}]"] = sentence.text
        for i, row in enumerate(t.rows):
            mapping[f"{lang}.rows[{i}]"] = f"{row.feature}: {row.a.text} / {row.b.text}"
            for side in SIDES:
                mapping[f"{lang}.rows[{i}].{side}"] = getattr(row, side).text
    return mapping


_COMPARE_PATH = re.compile(r"^(sv|en)\.(short_answer|rows)\[(\d+)\]")


def remove_compare_paths(text: CompareOutput, paths: set[str]) -> CompareOutput:
    """A copy without the short-answer sentences and rows named by `paths`. A cell path
    (`sv.rows[2].a`) removes its whole row."""
    data = text.model_dump()
    drops: dict[tuple[str, str], set[int]] = {}
    for path in paths:
        match = _COMPARE_PATH.match(path)
        if match is not None:
            lang, name, index = match.groups()
            drops.setdefault((lang, name), set()).add(int(index))
    for (lang, name), indexes in drops.items():
        data[lang][name] = [x for k, x in enumerate(data[lang][name]) if k not in indexes]
    return CompareOutput.model_validate(data)


def settle_compare(
    text: CompareOutput,
    ctx: TextContext,
    banned: list[str],
    *,
    openings: dict[str, str] | None = None,
) -> tuple[CompareOutput, list[str], list[str]]:
    """Same contract as `text_checks.settle`: removes every removable part that breaks a
    rule, then checks again. Returns the text, removal notes and the remaining problems."""
    issues = check_compare(text, ctx, banned, openings=openings)
    removable = {i.path for i in issues if i.removable}
    texts = compare_path_texts(text)
    notes = [
        f'{i.path} togs bort ("{texts.get(i.path, "")}"): {i.message}'
        for i in issues
        if i.removable
    ]
    if removable:
        text = remove_compare_paths(text, removable)
        issues = check_compare(text, ctx, banned, openings=openings)
    return text, notes, [f"{i.path}: {i.message}" for i in issues]


def compare_items(
    text: CompareOutput, ctx: TextContext, names: tuple[Record, Record] | None = None
) -> list[CheckItem]:
    """One checker item per short-answer sentence and per table cell. With `names` (the
    two species records), a cell's item text names the species it describes, so the
    checker never has to guess which side a cell is about."""

    def facts(ids: list[str]) -> tuple[dict[str, Any], ...]:
        return tuple(ctx.facts_by_id[f] for f in ids if f in ctx.facts_by_id)

    items: list[CheckItem] = []
    for lang in ("sv", "en"):
        t: CompareLang = getattr(text, lang)
        for i, sentence in enumerate(t.short_answer):
            items.append(
                CheckItem(f"{lang}.short_answer[{i}]", sentence.text, facts(sentence.fact_ids))
            )
        for i, row in enumerate(t.rows):
            for side, index in zip(SIDES, (0, 1), strict=True):
                cell: Cell = getattr(row, side)
                label = row.feature
                if names is not None:
                    label = f"{row.feature} ({names[index]['names'][lang]})"
                items.append(
                    CheckItem(
                        f"{lang}.rows[{i}].{side}", f"{label}: {cell.text}", facts(cell.fact_ids)
                    )
                )
    return items


def compare_minimum(text: CompareOutput) -> list[str]:
    """Spec §9.7: fewer than 3 valid rows (or no short answer, or a meta description of
    the wrong length) and the pair gets no page."""
    problems: list[str] = []
    for lang in ("sv", "en"):
        t: CompareLang = getattr(text, lang)
        if not t.short_answer:
            problems.append(f"{lang}.short_answer saknas")
        if len(t.rows) < ROWS_MIN:
            problems.append(f"{lang}.rows har färre än 3 rader")
        if not META_MIN <= len(t.meta_description) <= META_MAX:
            problems.append(f"{lang}.meta_description har fel längd")
    return problems


def _site(t: CompareLang) -> dict[str, Any]:
    """The site's field names (spec appendix D)."""
    return {
        "shortAnswer": [{"text": s.text, "factIds": list(s.fact_ids)} for s in t.short_answer],
        "rows": [
            {
                "feature": r.feature,
                "a": {"text": r.a.text, "factIds": list(r.a.fact_ids)},
                "b": {"text": r.b.text, "factIds": list(r.b.fact_ids)},
            }
            for r in t.rows
        ],
        "metaDescription": t.meta_description,
    }


def render_compare_prompt(
    template: str, a: Record, b: Record, banned: list[str]
) -> tuple[str, str]:
    a_facts, b_facts = prefixed_facts(a, b)
    en_first, en_second = sorted((a, b), key=lambda r: str(r["slug"]["en"]))
    return _split_prompt(
        template,
        en_first=en_first["names"]["en"],
        en_second=en_second["names"]["en"],
        a_sv=a["names"]["sv"],
        a_en=a["names"]["en"],
        a_scientific=a["names"]["scientific"],
        b_sv=b["names"]["sv"],
        b_en=b["names"]["en"],
        b_scientific=b["names"]["scientific"],
        banned_phrases=", ".join(banned),
        a_facts=render_facts(a_facts),
        b_facts=render_facts(b_facts),
    )


def pair_about(a: Record, b: Record) -> str:
    """Who the comparison is about, for the checker (`SentenceChecker.check(about=)`), and
    which side each fact id prefix belongs to (review fix 2026-10-06, M5)."""
    return (
        f"side a: {a['names']['sv']} / {a['names']['en']} ({a['names']['scientific']}); "
        f"side b: {b['names']['sv']} / {b['names']['en']} ({b['names']['scientific']}). "
        "Fact ids starting with a: are facts about side a, ids starting with b: are facts "
        "about side b; a look-alike fact may describe the other side."
    )


@dataclass
class ComparisonWriter:
    client: JsonModelClient
    cost: CostTracker
    checker: SentenceChecker
    prompt_path: Path
    banned: list[str]
    model_key: str = "opus"
    effort: str = "high"

    async def _ask(self, system: str, messages: list[MessageParam]) -> ModelReply[CompareOutput]:
        reply = await self.client.complete(
            model=MODELS[self.model_key],
            system=system,
            messages=messages,
            effort=self.effort,
            schema=CompareOutput,
        )
        record_cost(self.cost, self.model_key, reply)
        return reply

    async def write(self, a: Record, b: Record) -> Written[CompareOutput]:
        ctx = pair_context(a, b)
        template = self.prompt_path.read_text(encoding="utf-8")
        system, user = render_compare_prompt(template, a, b, self.banned)
        openings = meta_openings(a, b)
        checks: Checks[CompareOutput] = Checks(
            rules=lambda t: check_compare(t, ctx, self.banned, openings=openings),
            settle=lambda t: settle_compare(t, ctx, self.banned, openings=openings),
            items=lambda t: compare_items(t, ctx, (a, b)),
            remove=remove_compare_paths,
            minimum=compare_minimum,
            texts=compare_path_texts,
        )
        return await write_checked(
            ask=lambda messages: self._ask(system, messages),
            user=user,
            checks=checks,
            checker=self.checker,
            about=pair_about(a, b),
        )


@dataclass(frozen=True)
class CompareOptions:
    top: int = TOP
    model_key: str = "opus"
    effort: str = "high"
    checker_key: str = "sonnet"
    max_cost: float | None = None
    regenerate: bool = False
    workers: int = 4


def _candidate_volumes(
    raw: dict[Pair, tuple[int, int]], records: dict[str, Record]
) -> dict[frozenset[str], tuple[int, int]]:
    """Volumes for the current look-alike pairs only, keyed by the frozenset of QIDs
    (Task 21 review, item 7): `comparison-volumes.csv` is edited by hand, so a stale or
    mistyped row must neither hide a real candidate nor sneak a non-candidate into the top
    list. `read_volumes` keeps a row's orientation as written, so two rows for the same
    pair can come back as Pair(a, b) and Pair(b, a); different volumes for them stop the
    run before anything is paid for."""
    candidates = {frozenset((p.a, p.b)) for p in candidate_pairs(records)}
    volumes: dict[frozenset[str], tuple[int, int]] = {}
    for pair, value in raw.items():
        key = frozenset((pair.a, pair.b))
        if key not in candidates:
            continue
        if key in volumes and volumes[key] != value:
            first, second = sorted(key)
            raise ValueError(_duplicate_volumes_error(first, second, volumes[key], value))
        volumes[key] = value
    return volumes


def _ordered(key: frozenset[str], records: dict[str, Record]) -> Pair:
    """The pair in Swedish slug order (spec appendix D), whatever order the volume file
    had it in."""
    x, y = sorted(key)
    return ordered_pair(x, y, records)


def _pair_skip_reasons(a: Record, b: Record) -> list[str]:
    """A comparison is written only from two verified fact sheets (spec §7 and §9.7), with
    the same checks and messages as `web write` (`text_step._skip_reason`, review fix
    2026-10-06, M3). Empty when both sheets are ready."""
    reasons: list[str] = []
    for record in (a, b):
        name = str(record["names"]["sv"])
        if missing_required_topics(record.get("facts", [])):
            reasons.append(f"{name}: faktabladet saknas eller misslyckades: kör web facts")
        elif not facts_verified(record):
            reasons.append(f"{name}: faktabladet är inte kontrollerat")
    return reasons


def _both_hash(a: Record, b: Record) -> str:
    """The facts hash a comparison is written from: side a's, then side b's."""
    return facts_hash(a) + facts_hash(b)


def comparison_is_current(comparison: Record, records: dict[str, Record]) -> bool:
    """True when the comparison was written from both species' facts as they are now (its
    `generated.factsHash` matches, review fix 2026-10-06, I2). A comparison that is `ok`
    but not current must not be published: `web publish` checks this, since `web compare`
    leaves a stale comparison as it is whenever it does not rewrite it (a pair waiting for a
    fact sheet, outside `--top`, or no longer a look-alike pair)."""
    a, b = records.get(str(comparison.get("a"))), records.get(str(comparison.get("b")))
    if a is None or b is None:
        return False
    written = (comparison.get("generated") or {}).get("factsHash")
    return bool(written == _both_hash(a, b))


def _comparison_cited_ids(comparison: Record) -> set[str]:
    """Every fact id the comparison's site text (spec appendix D shape) cites."""
    ids: set[str] = set()
    text = comparison.get("text") or {}
    for lang in ("sv", "en"):
        lang_text = text.get(lang) or {}
        for sentence in lang_text.get("shortAnswer") or []:
            ids.update(sentence.get("factIds") or [])
        for row in lang_text.get("rows") or []:
            for side in SIDES:
                ids.update((row.get(side) or {}).get("factIds") or [])
    return ids


def _stale_comparison_ids(comparison: Record, records: dict[str, Record]) -> list[str]:
    """Cited ids (`a:f04`) whose fact no longer exists in that side's current `facts`
    (review fix 2026-10-06, I1, the comparison counterpart of text_step's N1). A side whose
    record is gone makes all its ids stale; an id without a side prefix is stale too."""
    current: dict[str, set[str]] = {}
    for side in SIDES:
        record = records.get(str(comparison.get(side)))
        current[side] = {f["id"] for f in record.get("facts", [])} if record else set()
    stale = []
    for fact_id in _comparison_cited_ids(comparison):
        side, _, local = fact_id.partition(":")
        if local not in current.get(side, set()):
            stale.append(fact_id)
    return sorted(stale)


def _stale_ids_error(stale: list[str]) -> str:
    return (
        f"publicerad jämförelse anger fakta som inte längre finns ({', '.join(stale)}): "
        "sätt publish: false"
    )


def _published_stale_error(comparison: Record | None, records: dict[str, Record]) -> str | None:
    """The loud error for a published comparison that cites a struck fact, or None."""
    if comparison is None or not comparison.get("publish"):
        return None
    stale = _stale_comparison_ids(comparison, records)
    return _stale_ids_error(stale) if stale else None


STALE_HASH_ERROR = (
    "jämförelsen är publicerad och faktabladen har ändrats sedan texten skrevs: "
    "sätt publish: false om den gamla texten nu är fel"
)


def published_comparison_errors(comparison: Record, records: dict[str, Record]) -> list[str]:
    """Every loud error a published comparison has right now, assuming the caller has
    already checked `comparison.get("publish")`: a struck cited fact, or -- only when
    that is clean -- facts that have moved on since the comparison was written. Shared
    (I3, scope follow-up to the review fix 2026-10-06) by `_sweep_published` (every
    `web compare` run) and `waves.py`'s `publish_wave`, so a comparison already live
    never gets a free pass just because this particular run does not rewrite it."""
    stale = _stale_comparison_ids(comparison, records)
    if stale:
        return [_stale_ids_error(stale)]
    if not comparison_is_current(comparison, records):
        return [STALE_HASH_ERROR]
    return []


def _keep_old_comparison(existing: Record, records: dict[str, Record]) -> bool:
    """Mirrors `text_step._keep_old_text`: a failed rewrite never destroys a comparison the
    site may still use (a published one) or one that is still current for these facts.
    A stale, unpublished comparison is replaced by the failed result. That alone does not
    keep stale text off the site (a comparison this run never rewrites stays `ok`), so
    `web publish` is only safe because it also requires `comparison_is_current`. Judged
    in the orientation the comparison was stored in (follow-up 2026-10-06): a Swedish
    slug rename that flips the pair does not make its facts stale."""
    if existing.get("publish"):
        return True
    if existing.get("status") != "ok":
        return False
    return comparison_is_current(existing, records)


def _sweep_published(
    out_dir: Path, records: dict[str, Record], in_run: set[str]
) -> list[StepOutcome]:
    """Published comparisons this run does not visit (outside `--top`, no volume any more,
    or no longer a look-alike pair) are checked without a model call (review fix
    2026-10-06, I1): `published_comparison_errors` reports a struck cited fact or changed
    facts as failed, so the page does not stay live on old facts unnoticed."""
    outcomes: list[StepOutcome] = []
    for path in sorted(out_dir.glob("Q*_Q*.json")):
        if path.name in in_run:
            continue
        name = path.stem
        try:
            comparison = load_record(path)
            if comparison is None or not comparison.get("publish"):
                continue
            a, b = records.get(str(comparison.get("a"))), records.get(str(comparison.get("b")))
            if a is not None and b is not None:
                name = f"{a['names']['sv']} eller {b['names']['sv']}"
            errors = published_comparison_errors(comparison, records)
            if errors:
                notes = (
                    []
                    if _stale_comparison_ids(comparison, records)
                    else [
                        "paret skrivs inte om i den här körningen (utanför --top eller "
                        "inte ett förväxlingspar längre)"
                    ]
                )
                outcomes.append(StepOutcome(path.stem, name, "failed", errors, notes))
        except Exception as exc:  # one file's error must not stop the run
            outcomes.append(
                StepOutcome(path.stem, name, "failed", [f"{type(exc).__name__}: {exc}"])
            )
    return outcomes


async def run_compare(
    paths: WebPaths,
    options: CompareOptions,
    *,
    client: JsonModelClient | None = None,
    now: datetime | None = None,
) -> list[StepOutcome]:
    if options.model_key == options.checker_key:
        # Spec §9.6 (same rule as `web write`, I3): the checker must be a different model
        # than the writer, in a fresh context.
        raise ValueError("Skribenten och kontrollen måste vara olika modeller.")
    now = now or datetime.now(UTC)
    records = load_all(paths.data_out)
    volumes = _candidate_volumes(read_volumes(paths.review / VOLUMES_FILE), records)
    pairs = select_pairs({_ordered(key, records): v for key, v in volumes.items()}, options.top)
    # Everything that reads a file comes before the client exists (review fix 2026-10-06,
    # M7), so an early error cannot leave an Anthropic client open.
    banned = load_banned(paths.banned)
    prompt_hash = prompt_file_hash(paths.prompt_file(PROMPT_VERSION))
    owned = client is None
    model_client: JsonModelClient = client or AnthropicJsonClient()
    cost = CostTracker(max_usd=options.max_cost)
    checker = SentenceChecker(
        client=model_client,
        cost=cost,
        prompt_path=paths.prompt_file(CHECK_PROMPT_VERSION),
        model_key=options.checker_key,
    )
    writer = ComparisonWriter(
        client=model_client,
        cost=cost,
        checker=checker,
        prompt_path=paths.prompt_file(PROMPT_VERSION),
        banned=banned,
        model_key=options.model_key,
        effort=options.effort,
    )
    stop = asyncio.Event()
    semaphore = asyncio.Semaphore(options.workers)

    async def one(pair: Pair) -> StepOutcome:
        path = comparison_path(paths.comparisons_out, pair)
        label = path.stem
        name = label
        async with semaphore:
            try:
                a, b = records[pair.a], records[pair.b]
                name = f"{a['names']['sv']} eller {b['names']['sv']}"
                existing = load_record(path)
                # I1 (review fix 2026-10-06): computed once, before anything else. Every
                # way out that leaves a published comparison as it is fails loudly when it
                # cites a fact that no longer exists.
                stale_error = _published_stale_error(existing, records)

                def leave(status: str, reasons: list[str]) -> StepOutcome:
                    if stale_error is not None:
                        return StepOutcome(label, name, "failed", [stale_error], reasons)
                    return StepOutcome(label, name, status, reasons)

                reasons = _pair_skip_reasons(a, b)
                if reasons:
                    return leave("skipped", reasons)
                both_hash = _both_hash(a, b)
                current = (
                    existing is not None
                    and existing.get("status") == "ok"
                    and comparison_is_current(existing, records)
                )
                if current and not options.regenerate:
                    return leave("skipped", ["jämförelsen är redan skriven ur samma faktablad"])
                if stop.is_set():
                    return leave("skipped", ["kostnadstaket nåddes"])
                try:
                    result = await writer.write(a, b)
                except MaxCostExceeded as exc:
                    stop.set()
                    return leave("skipped", [f"kostnadstaket nåddes: {exc}"])
                except CheckerFailed as exc:
                    return leave("failed", [str(exc)])
                if (
                    result.text is None
                    and existing is not None
                    and _keep_old_comparison(existing, records)
                ):
                    notes = [*result.notes, "den tidigare jämförelsen behölls"]
                    errors = list(result.errors)
                    if stale_error is not None:
                        errors.append(stale_error)
                    elif existing.get("publish") and not comparison_is_current(existing, records):
                        errors.append(STALE_HASH_ERROR)
                    return StepOutcome(label, name, "failed", errors, notes)
                sv_volume, en_volume = volumes[frozenset((pair.a, pair.b))]
                # Keys this step does not own (`publish`, and whatever later steps add)
                # are kept as they are; a new file starts unpublished.
                record: Record = dict(existing) if existing is not None else {}
                record.update(
                    {
                        "a": pair.a,
                        "b": pair.b,
                        "status": "ok" if result.text is not None else "failed",
                        "publish": bool(record.get("publish", False)),
                        "slug": comparison_slugs(pair, records),
                        "volumes": {"sv": sv_volume, "en": en_volume},
                        "text": (
                            {lang: _site(getattr(result.text, lang)) for lang in ("sv", "en")}
                            if result.text is not None
                            else None
                        ),
                        "generated": {
                            "model": MODELS[options.model_key],
                            "prompt": PROMPT_VERSION,
                            "promptHash": prompt_hash,
                            "effort": options.effort,
                            "checker": MODELS[options.checker_key],
                            "checkerPrompt": CHECK_PROMPT_VERSION,
                            "at": now.isoformat(),
                            "factsHash": both_hash,
                        },
                        "errors": list(result.errors),
                    }
                )
                if result.text is None:
                    record["rejectedText"] = (
                        {lang: _site(getattr(result.rejected, lang)) for lang in ("sv", "en")}
                        if result.rejected is not None
                        else None
                    )
                else:
                    record.pop("rejectedText", None)
                save_record(path, record)
                return StepOutcome(label, name, str(record["status"]), result.errors, result.notes)
            except Exception as exc:  # one pair's error must not stop the run
                return StepOutcome(label, name, "failed", [f"{type(exc).__name__}: {exc}"])

    try:
        outcomes = list(await asyncio.gather(*(one(p) for p in pairs)))
    finally:
        if owned and isinstance(model_client, AnthropicJsonClient):
            await model_client.aclose()
    in_run = {comparison_path(paths.comparisons_out, p).name for p in pairs}
    outcomes += _sweep_published(paths.comparisons_out, records, in_run)
    model_line = (
        f"Skribent: `{MODELS[options.model_key]}` (effort: {options.effort}). "
        f"Kontroll: `{MODELS[options.checker_key]}`."
    )
    report = render_step_report(
        title="Jämförelser",
        date=now.date().isoformat(),
        outcomes=outcomes,
        cost_usd=cost.total_usd,
        model_line=model_line,
    )
    write_step_report(paths.reports, "compare", now, report)
    return outcomes
