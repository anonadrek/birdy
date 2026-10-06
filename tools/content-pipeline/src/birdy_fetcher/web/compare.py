"""Look-alike pairs, their search volumes and their comparison texts (spec 2026-09-25 §7
and §9.7)."""

from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path

from .record import Record

VOLUMES_FILE = "comparison-volumes.csv"
COLUMNS = [
    "a_qid", "b_qid", "a_sv", "b_sv", "a_en", "b_en",
    "sv_queries", "en_queries", "sv_volume", "en_volume",
]  # fmt: skip
TOP = 30


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


def queries(pair: Pair, records: dict[str, Record]) -> dict[str, list[str]]:
    a_sv, b_sv = (str(records[q]["names"]["sv"]).lower() for q in (pair.a, pair.b))
    a_en, b_en = (str(records[q]["names"]["en"]).lower() for q in (pair.a, pair.b))
    return {
        "sv": [
            f"{a_sv} eller {b_sv}",
            f"{b_sv} eller {a_sv}",
            f"skillnad {a_sv} {b_sv}",
            f"{a_sv} {b_sv} skillnad",
        ],
        "en": [f"{a_en} vs {b_en}", f"{b_en} vs {a_en}", f"difference between {a_en} and {b_en}"],
    }


def _number(value: str | None) -> int:
    digits = "".join(ch for ch in (value or "") if ch.isdigit())
    return int(digits) if digits else 0


def read_volumes(path: Path) -> dict[Pair, tuple[int, int]]:
    if not path.exists():
        return {}
    with path.open(encoding="utf-8-sig", newline="") as f:
        return {
            Pair(row["a_qid"], row["b_qid"]): (_number(row["sv_volume"]), _number(row["en_volume"]))
            for row in csv.DictReader(f)
        }


def write_candidates(path: Path, pairs: list[Pair], records: dict[str, Record]) -> None:
    old: dict[Pair, dict[str, str]] = {}
    if path.exists():
        with path.open(encoding="utf-8-sig", newline="") as f:
            old = {Pair(r["a_qid"], r["b_qid"]): r for r in csv.DictReader(f)}
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        for pair in pairs:
            q = queries(pair, records)
            kept = old.get(pair, {})
            writer.writerow(
                {
                    "a_qid": pair.a,
                    "b_qid": pair.b,
                    "a_sv": records[pair.a]["names"]["sv"],
                    "b_sv": records[pair.b]["names"]["sv"],
                    "a_en": records[pair.a]["names"]["en"],
                    "b_en": records[pair.b]["names"]["en"],
                    "sv_queries": "; ".join(q["sv"]),
                    "en_queries": "; ".join(q["en"]),
                    "sv_volume": kept.get("sv_volume", ""),
                    "en_volume": kept.get("en_volume", ""),
                }
            )


def select_pairs(volumes: dict[Pair, tuple[int, int]], top: int = TOP) -> list[Pair]:
    ranked = sorted(
        ((pair, sv, en) for pair, (sv, en) in volumes.items() if sv or en),
        key=lambda x: (-x[1], -x[2], x[0]),
    )
    return [pair for pair, _, _ in ranked[:top]]


def comparison_slugs(pair: Pair, records: dict[str, Record]) -> dict[str, str]:
    a, b = records[pair.a], records[pair.b]
    first_en, second_en = sorted([str(a["slug"]["en"]), str(b["slug"]["en"])])
    return {"sv": f"{a['slug']['sv']}-eller-{b['slug']['sv']}", "en": f"{first_en}-vs-{second_en}"}


def comparison_path(out_dir: Path, pair: Pair) -> Path:
    first, second = sorted((pair.a, pair.b))
    return out_dir / f"{first}_{second}.json"
