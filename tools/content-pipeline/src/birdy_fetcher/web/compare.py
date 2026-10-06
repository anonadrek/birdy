"""Look-alike pairs, their search volumes and their comparison texts (spec 2026-09-25 §7
and §9.7).

Fix 2026-10-06 (Task 21 review, "With fixes"): strict volume parsing (item 1), the shared
BOM-safe/formula-escaping/atomic `sheet_csv` helpers instead of a plain `csv.DictWriter`
(item 2 and 4), orientation- and whitespace-proof matching of old rows (item 3), stale
candidates kept instead of silently dropped (item 5), a missing-volumes count and a
name-change clear (item 6 and 8), row order for whoever fills the sheet in (item 9),
duplicate-row handling (item 7), and English queries that also try the name without its
IOC qualifier (item 12)."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from .record import Record
from .sheet_csv import read_sheet, write_sheet

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
    is skipped too (fix 2026-10-06, item 2), consistent with `_load_old` below."""
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
        volumes[Pair(a_qid, b_qid)] = (sv, en)
    return volumes


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
