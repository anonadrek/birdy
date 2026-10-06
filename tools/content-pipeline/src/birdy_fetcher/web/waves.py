"""Waves (spec 2026-09-25 §14, revised 2026-10-05 (b)): the queue order in which species are
reviewed, written and published, not a publication gate or a date. `publish_wave` (Task 23)
turns `publish` on for species and comparisons that are ready; it never turns it off."""

from __future__ import annotations

import json
from datetime import date
from pathlib import Path

from .compare import comparison_is_current
from .groups import GroupTable
from .paths import WebPaths
from .record import Record, facts_hash, load_all, load_record, record_path, save_record
from .report import StepOutcome
from .text_step import facts_verified, stale_cited_fact_ids
from .verify import missing_required_topics

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


# -- publishing (Task 23, spec Revision 2026-10-05 (b)) ---------------------------------


def _unready_reasons(record: Record) -> list[str]:
    """Every reason `record` is not ready to publish. The plan's original predicate
    (status "ok" + a verification set + a text not written with --allow-unreviewed) was
    too weak after the Task 20 reviews: it must also have every required topic, a
    verification that still matches the facts as they are now (`facts_verified`, shared
    with the comparison step, Task 22), a text written from these same facts, and a text
    that cites no fact id that has since vanished from `record["facts"]`
    (`stale_cited_fact_ids`, shared with `text_step`'s N1 guard -- checked as defence in
    depth even though a facts-hash mismatch above would normally catch the same drift
    first). Empty means ready."""
    reasons: list[str] = []
    if record.get("status") != "ok":
        reasons.append(f"status är {record.get('status')}, inte ok")
    missing = missing_required_topics(record.get("facts", []))
    if missing:
        reasons.append(f"det saknas fakta om {', '.join(missing)}")
    if not facts_verified(record):
        reasons.append("faktabladet är inte kontrollerat eller har ändrats sedan kontrollen")
    text_generated = record.get("generated", {}).get("text") or {}
    if text_generated.get("factsHash") != facts_hash(record):
        reasons.append("texten är inte skriven ur det nuvarande faktabladet")
    if text_generated.get("unreviewed"):
        reasons.append("texten skrevs med --allow-unreviewed")
    stale = stale_cited_fact_ids(record)
    if stale:
        reasons.append(f"texten anger fakta som inte längre finns ({', '.join(stale)})")
    return reasons


def _unready_published_error(reasons: list[str]) -> str:
    return f"publicerad men inte längre klar ({'; '.join(reasons)}): sätt publish: false"


def publish_wave(
    paths: WebPaths,
    wave: int | None = None,
    species: list[str] | None = None,
    *,
    now: date | None = None,
) -> list[StepOutcome]:
    """Turns `publish` on for every ready species, then for every finished, current
    comparison whose two species are both published. Nothing is ever turned off here --
    unpublishing is Albin's manual step (spec Revision 2026-10-05 (b)).

    Scope: `wave` narrows to a wave's queue order, `species` narrows to an explicit list;
    either, both or neither may be given. With neither, every ready species across all
    waves is published. Fas 2's continuous per-species loop normally calls this with a
    single `species` entry at a time, not a whole wave.

    A species or comparison already published that no longer satisfies the predicate
    (e.g. a spot-check strike, or facts that moved on since the text or comparison was
    written) is reported `failed`, naming "sätt publish: false", instead of being
    silently left as it is or flipped off -- this step never unpublishes anything itself.
    """
    when = (now or date.today()).isoformat()
    outcomes: list[StepOutcome] = []
    records = load_all(paths.data_out)
    for qid, record in sorted(records.items()):
        if wave is not None and record.get("review", {}).get("wave") != wave:
            continue
        if species is not None and qid not in species:
            continue
        name = _name(record)
        reasons = _unready_reasons(record)
        if not reasons:
            if not record.get("publishedAt"):
                # Fas 2's sitemap lastmod needs the first publish date; kept unchanged on
                # every later run, even a republish after an edit.
                record["publishedAt"] = when
            record["publish"] = True
            save_record(record_path(paths.data_out, qid), record)
            outcomes.append(StepOutcome(qid, name, "ok"))
        elif record.get("publish"):
            outcomes.append(StepOutcome(qid, name, "failed", [_unready_published_error(reasons)]))
        else:
            outcomes.append(StepOutcome(qid, name, "skipped", reasons))
    published = {qid for qid, record in records.items() if record.get("publish")}
    for path in sorted(paths.comparisons_out.glob("Q*_Q*.json")):
        comparison = load_record(path)
        if comparison is None or comparison.get("publish"):
            continue
        both = comparison.get("a") in published and comparison.get("b") in published
        if comparison.get("status") == "ok" and both and comparison_is_current(comparison, records):
            comparison["publish"] = True
            save_record(path, comparison)
            outcomes.append(StepOutcome(path.stem, path.stem, "ok", notes=["jämförelse"]))
    return outcomes
