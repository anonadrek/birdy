"""Waves (spec 2026-09-25 §14, revised 2026-10-05 (b)): the queue order in which species are
reviewed, written and published, not a publication gate or a date. `publish_wave` (Task 23)
turns `publish` on for species and comparisons that are ready; it never turns it off."""

from __future__ import annotations

import json
import sys
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from .compare import comparison_is_current, published_comparison_errors
from .groups import GroupTable
from .paths import WebPaths
from .record import Record, load_all, load_record, record_path, save_record
from .report import StepOutcome, render_step_report, write_step_report
from .text_step import facts_verified, stale_cited_fact_ids, text_is_current
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
    with the comparison step, Task 22), a text written from these same facts
    (`text_is_current`, shared with `text_step`, M5), and a text that cites no fact id
    that has since vanished from `record["facts"]` (`stale_cited_fact_ids`, shared with
    `text_step`'s N1 guard -- checked as defence in depth even though a facts-hash
    mismatch above would normally catch the same drift first). Empty means ready."""
    reasons: list[str] = []
    if record.get("status") != "ok":
        reasons.append(f"status är {record.get('status')}, inte ok")
    missing = missing_required_topics(record.get("facts", []))
    if missing:
        reasons.append(f"det saknas fakta om {', '.join(missing)}")
    if not facts_verified(record):
        reasons.append("faktabladet är inte kontrollerat eller har ändrats sedan kontrollen")
    if not text_is_current(record):
        reasons.append("texten är inte skriven ur det nuvarande faktabladet")
    text_generated = record.get("generated", {}).get("text") or {}
    if text_generated.get("unreviewed"):
        reasons.append("texten skrevs med --allow-unreviewed")
    stale = stale_cited_fact_ids(record)
    if stale:
        reasons.append(f"texten anger fakta som inte längre finns ({', '.join(stale)})")
    return reasons


def _unready_published_error(reasons: list[str]) -> str:
    return f"publicerad men inte längre klar ({'; '.join(reasons)}): sätt publish: false"


def _live_set(records: dict[str, Record]) -> set[str]:
    """QIDs that are published AND still satisfy the predicate right now (I2, review fix
    2026-10-06): a species whose `publish` flag is stuck `True` only because this step
    never unpublishes anything must not count as a usable side of a comparison."""
    return {q for q, r in records.items() if r.get("publish") and not _unready_reasons(r)}


def _comparison_name(comparison: Record, records: dict[str, Record], fallback: str) -> str:
    """ "A eller B" like `compare.py` names its own outcomes (M6, review fix 2026-10-06),
    falling back to the file stem when a side's species record is gone."""
    a, b = records.get(str(comparison.get("a"))), records.get(str(comparison.get("b")))
    if a is not None and b is not None:
        return f"{a['names']['sv']} eller {b['names']['sv']}"
    return fallback


def _comparison_unready_reasons(
    comparison: Record, a_qid: str, b_qid: str, live: set[str], current: bool
) -> list[str]:
    reasons: list[str] = []
    if comparison.get("status") != "ok":
        reasons.append(f"status är {comparison.get('status')}, inte ok")
    not_live = [q for q in (a_qid, b_qid) if q not in live]
    if not_live:
        reasons.append(f"{', '.join(not_live)} är inte publicerad(e) eller inte längre klar(a)")
    elif not current:
        reasons.append("jämförelsen är inte skriven ur de nuvarande faktabladen")
    return reasons


def _publish_comparisons(
    paths: WebPaths, records: dict[str, Record], live: set[str], selected: set[str] | None
) -> list[StepOutcome]:
    """The second half of `publish_wave`: turns `publish` on for every finished, current
    comparison whose two species are both live, and reports an already published
    comparison that no longer qualifies as `failed` (I3, review fix 2026-10-06, reusing
    `published_comparison_errors`, shared with `web compare`'s own sweep so a comparison
    already live never gets a free pass just because this call does not visit it).

    Scope (I1, review fix 2026-10-06): with `selected` given (fas 2's loop normally passes
    a single species), a NEW comparison is only ever published when BOTH its species are
    in `selected` -- a comparison outside that pair is not this call's business, so it is
    left untouched. An ALREADY published comparison is instead checked for staleness
    whenever EITHER of its species is in `selected`, so the one species fas 2 just
    touched still catches every comparison it is part of. Every in-scope, not-yet-ready
    comparison is reported `skipped` with its reasons -- but only in `--species` mode;
    with `selected` left `None` (wave-only, or neither filter at all) every comparison is
    in scope either way and an unpublished, not-yet-ready one stays silent, matching the
    behaviour before this fix."""
    outcomes: list[StepOutcome] = []
    for path in sorted(paths.comparisons_out.glob("Q*_Q*.json")):
        name = path.stem
        try:
            comparison = load_record(path)
            if comparison is None:
                continue
            a_qid, b_qid = str(comparison.get("a")), str(comparison.get("b"))
            name = _comparison_name(comparison, records, path.stem)
            if comparison.get("publish"):
                if selected is not None and a_qid not in selected and b_qid not in selected:
                    continue
                # N1 (review fix 2026-10-06): attribute the problem to a selected side,
                # so a --species call is never failed by its OTHER, unselected side.
                errors = published_comparison_errors(comparison, records, sides=selected)
                if errors:
                    outcomes.append(StepOutcome(path.stem, name, "failed", errors))
                continue
            if selected is not None and not (a_qid in selected and b_qid in selected):
                continue
            both_live = a_qid in live and b_qid in live
            current = comparison_is_current(comparison, records)
            if comparison.get("status") == "ok" and both_live and current:
                comparison["publish"] = True
                save_record(path, comparison)
                outcomes.append(StepOutcome(path.stem, name, "ok", notes=["jämförelse"]))
            elif selected is not None:
                reasons = _comparison_unready_reasons(comparison, a_qid, b_qid, live, current)
                # Tagged "jämförelse" (N1 item 2, review fix 2026-10-06) so the CLI can
                # tell a NEW-publish attempt (this call's own business, counts toward
                # its exit code) apart from an already-published staleness finding just
                # above (never counts, see `cli.py`'s `web_publish`).
                outcomes.append(
                    StepOutcome(path.stem, name, "skipped", reasons, notes=["jämförelse"])
                )
        except Exception as exc:  # one file's error must not stop the run (M6)
            outcomes.append(
                StepOutcome(path.stem, name, "failed", [f"{type(exc).__name__}: {exc}"])
            )
    return outcomes


def publish_wave(
    paths: WebPaths,
    wave: int | None = None,
    species: list[str] | None = None,
    *,
    now: datetime | None = None,
) -> list[StepOutcome]:
    """Turns `publish` on for every ready species, then for every finished, current
    comparison whose two species are both live (`_live_set`, I2). Nothing is ever turned
    off here -- unpublishing is Albin's manual step (spec Revision 2026-10-05 (b)).

    Scope: `wave` narrows to a wave's queue order, `species` narrows to an explicit list;
    either, both or neither may be given. With neither, every ready species across all
    waves is published. Fas 2's continuous per-species loop normally calls this with a
    single `species` entry at a time, not a whole wave -- see `_publish_comparisons` for
    how that narrows the comparison half too (I1). An unknown QID named in `species` is
    reported `failed` (I3, mirrors `run_write`'s M1) rather than silently dropped.

    A species or comparison already published that no longer satisfies the predicate
    (e.g. a spot-check strike, or facts that moved on since the text or comparison was
    written) is reported `failed`, naming "sätt publish: false", instead of being
    silently left as it is or flipped off -- this step never unpublishes anything itself.

    Writes a step report under `paths.reports` (M1), like every other web step."""
    now_dt = now or datetime.now(UTC)
    when = now_dt.date().isoformat()
    outcomes: list[StepOutcome] = []
    records = load_all(paths.data_out)
    selected = set(species) if species is not None else None
    if selected is not None:
        for qid in dict.fromkeys(species or ()):
            if qid not in records:
                outcomes.append(
                    StepOutcome(qid, qid, "failed", ["artposten saknas: kör web sources först"])
                )
    for qid, record in sorted(records.items()):
        if selected is not None and qid not in selected:
            # Not named at all: the wave filter (if any) is not this record's concern.
            continue
        if wave is not None and record.get("review", {}).get("wave") != wave:
            if selected is not None:
                # m2 (review fix 2026-10-06): a QID explicitly named with --species that
                # simply is not in the --wave also given is a loud, named skip -- not a
                # silent drop that leaves it with no outcome at all.
                outcomes.append(StepOutcome(qid, _name(record), "skipped", [f"inte i våg {wave}"]))
            continue
        name = _name(record)
        reasons = _unready_reasons(record)
        if not reasons:
            changed = not record.get("publish")
            if not record.get("publishedAt"):
                # `review_sheet`'s "Publicerad" column (bilaga E) needs the species' very
                # first publish date; it means "first published ever" and must survive an
                # unpublish/republish, so it is set once and never touched again (M3,
                # review fix 2026-10-06 -- NOT fas 2's sitemap lastmod, which instead
                # reads `generated.text.at` or `verification.at`).
                record["publishedAt"] = when
                changed = True
            record["publish"] = True
            if changed:  # M2: no write at all when nothing actually changed
                save_record(record_path(paths.data_out, qid), record)
            outcomes.append(StepOutcome(qid, name, "ok"))
        elif record.get("publish"):
            outcomes.append(StepOutcome(qid, name, "failed", [_unready_published_error(reasons)]))
        else:
            outcomes.append(StepOutcome(qid, name, "skipped", reasons))
    live = _live_set(records)
    outcomes += _publish_comparisons(paths, records, live, selected)
    report = render_step_report(
        title="Publicering", date=now_dt.date().isoformat(), outcomes=outcomes
    )
    write_step_report(paths.reports, "publish", now_dt, report)
    return outcomes


# -- --next (I4, review fix 2026-10-06): fas 2's continuous loop picks at most one item --


_NO_WAVE_ORDER = 4  # after every real wave; `review.wave` is only ever 1, 2 or 3.


def _wave_positions(paths: WebPaths) -> dict[str, int]:
    """QID to its position within its wave in `review/waves.json`, when that file exists
    (m1, review fix 2026-10-06). The file preserves the order Albin or `compute_waves`
    assigned (common species first in wave 1, say) -- plain alphabetical order inside a
    wave would lose that. Positions from different waves are never compared against each
    other (the wave number is the primary sort key), so reusing the same numbering
    space across waves is harmless."""
    file = paths.review / WAVES_FILE
    if not file.exists():
        return {}
    positions: dict[str, int] = {}
    for qids in read_waves(file).values():
        for i, qid in enumerate(qids):
            positions[qid] = i
    return positions


def _queue_order(records: dict[str, Record], positions: dict[str, int]) -> list[str]:
    """Fas 2's publish queue: species grouped by wave ascending, a species with no wave
    assigned last; within a wave, `review/waves.json`'s own order (`positions`) when the
    file lists the species, falling back to alphabetical by `names.sv` -- breaking ties
    on QID -- for one that is not listed, or when the file does not exist at all (m1,
    review fix 2026-10-06)."""

    def key(qid: str) -> tuple[int, int, str, str]:
        wave = records[qid].get("review", {}).get("wave")
        order = wave if isinstance(wave, int) else _NO_WAVE_ORDER
        position = positions.get(qid, len(positions))
        return (order, position, _name(records[qid]), qid)

    return sorted(records, key=key)


@dataclass(frozen=True)
class NextPick:
    kind: str  # "species" | "comparison"
    id: str


def publish_next(
    paths: WebPaths, *, exclude: frozenset[str] = frozenset(), now: datetime | None = None
) -> NextPick | None:
    """Publishes at most one ready species or comparison, in queue order, for fas 2's
    continuous publishing loop. The loop's own candidate picker uses a weaker predicate
    than this module's `publish_wave`, so an item it thinks is ready can still turn out
    not to be once checked here, head-of-line-blocking the whole wave behind it. `--next`
    instead asks `_unready_reasons`/`comparison_is_current` directly and simply skips
    anything not ready, or named in `exclude` (e.g. a page that has already failed
    repeatedly this loop session, so one stuck page never blocks the others) -- never
    raising a skip as a failure the way `publish_wave` does for an explicitly named
    --species. Returns `None` when nothing is ready."""
    when = (now or datetime.now(UTC)).date().isoformat()
    records = load_all(paths.data_out)
    positions = _wave_positions(paths)
    for qid in _queue_order(records, positions):
        record = records[qid]
        if qid in exclude or record.get("publish") or _unready_reasons(record):
            continue
        if not record.get("publishedAt"):
            record["publishedAt"] = when
        record["publish"] = True
        save_record(record_path(paths.data_out, qid), record)
        return NextPick("species", qid)
    live = _live_set(records)
    for path in sorted(paths.comparisons_out.glob("Q*_Q*.json")):
        stem = path.stem
        if stem in exclude:
            continue
        try:
            comparison = load_record(path)
        except Exception as exc:
            # m3 (review fix 2026-10-06): --next's stdout contract is exactly one line
            # (the pick, or "none"), so a skipped unreadable file is reported on stderr
            # instead -- never silently, and never on stdout.
            print(f"web publish --next: kunde inte läsa {path.name}: {exc}", file=sys.stderr)
            continue
        if comparison is None or comparison.get("publish") or comparison.get("status") != "ok":
            continue
        a_qid, b_qid = str(comparison.get("a")), str(comparison.get("b"))
        if a_qid not in live or b_qid not in live:
            continue
        if not comparison_is_current(comparison, records):
            continue
        comparison["publish"] = True
        save_record(path, comparison)
        return NextPick("comparison", stem)
    return None
