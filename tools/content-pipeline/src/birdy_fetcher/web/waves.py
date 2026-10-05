"""Waves (spec 2026-09-25 §14, revised 2026-10-05 (b)): the queue order in which species are
reviewed, written and published, not a publication gate or a date. Publishing is added in
Task 23."""

from __future__ import annotations

import json
from pathlib import Path

from .groups import GroupTable
from .paths import WebPaths
from .record import Record, load_all, record_path, save_record

WINTER = (11, 0, 1)
MIGRANT_WINTER_MAX = 10
WAVE_ONE_SIZE = 40
WAVES_FILE = "waves.json"


def winter_reports(record: Record) -> int:
    raw = (record.get("data") or {}).get("raw", {}).get("speciesByMonth")
    return sum(raw[i] for i in WINTER) if raw else 0


def is_migrant(record: Record) -> bool:
    months = (record.get("data") or {}).get("months")
    if not months:
        return False
    return bool(sum(months[i] for i in WINTER) / len(WINTER) <= MIGRANT_WINTER_MAX)


def _name(record: Record) -> str:
    return str(record.get("names", {}).get("sv", record["qid"]))


def compute_waves(
    records: dict[str, Record], common: list[str], size: int = WAVE_ONE_SIZE
) -> dict[int, list[str]]:
    first = [q for q in common if q in records]
    rest = [q for q in records if q not in first and not is_migrant(records[q])]
    rest.sort(key=lambda q: (-winter_reports(records[q]), _name(records[q])))
    first += rest[: max(0, size - len(first))]
    chosen = set(first)
    third = sorted(
        (q for q in records if q not in chosen and is_migrant(records[q])),
        key=lambda q: _name(records[q]),
    )
    second = sorted(
        (q for q in records if q not in chosen and q not in third),
        key=lambda q: _name(records[q]),
    )
    return {1: first, 2: second, 3: third}


def write_waves(path: Path, waves: dict[int, list[str]], records: dict[str, Record]) -> None:
    data = {
        str(n): [{"qid": q, "name": _name(records[q])} for q in qids] for n, qids in waves.items()
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def read_waves(path: Path) -> dict[int, list[str]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    return {int(n): [entry["qid"] for entry in entries] for n, entries in data.items()}


def run_waves(paths: WebPaths, *, size: int, recompute: bool) -> dict[int, list[str]]:
    records = load_all(paths.data_out)
    file = paths.review / WAVES_FILE
    if file.exists() and not recompute:
        waves = read_waves(file)
    else:
        common = GroupTable(paths.family_groups, paths.web_groups).common
        waves = compute_waves(records, common, size)
        write_waves(file, waves, records)
    wave_of = {qid: n for n, qids in waves.items() for qid in qids}
    for qid, record in records.items():
        review = record.setdefault("review", {})
        if qid in wave_of:
            review["wave"] = wave_of[qid]
        else:
            review.pop("wave", None)
        save_record(record_path(paths.data_out, qid), record)
    return waves
