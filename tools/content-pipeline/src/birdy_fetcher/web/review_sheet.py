"""The exception sheet (spec 2026-09-25 Revision 2026-10-05, §9.4 and appendix E): a
running CSV, uploaded to Albin's Drive as a Google Sheet, exported back as CSV and
imported. Only what the automatic kontroll (V1 to V4, Task 14e) flagged. The seeded spot
check moved out to `export_spot_check` after publication (Revision 2026-10-05 (b)).

Written as UTF-8 with a BOM (review fix 2026-10-06) so Excel shows åäö; a reader must
open with `encoding="utf-8-sig"` too, including the future Task 17 import step."""

from __future__ import annotations

import copy
import json
import random
from collections.abc import Sequence
from dataclasses import dataclass, field
from datetime import date as _date
from pathlib import Path
from typing import Any

from .checks import without_dashes
from .datamod import data_status_contradiction
from .facts import STATUS_BY_SV, STATUS_SV, TOPIC_SV
from .paths import WebPaths
from .record import (
    Record,
    audio_id,
    delete_voice,
    facts_hash,
    is_reviewed,
    load_all,
    record_path,
    save_record,
    sweep_orphan_voices,
)
from .sheet_csv import read_sheet as _sheet_csv_read_sheet
from .sheet_csv import write_sheet as _sheet_csv_write_sheet
from .text_step import facts_verified
from .verify import missing_required_topics, status_flags
from .waves import WAVES_FILE, read_waves, unready_reasons

COLUMNS = [
    "Art",
    "QID",
    "Rad",
    "Kontroll",
    "Typ",
    "Id",
    "Ämne",
    "Faktum",
    "Källa",
    "Citat",
    "Beslut",
    "Kommentar",
]
# Bilaga E (ändrat 2026-10-05 (b)): "Publicerad" finns bara i stickprovsfliken. "Dragning"
# (C2/I1, final review 2026-10-06) says which draw a row belongs to: the sheet is
# cumulative, and only the rows of a species' current, undecided draw are ever applied.
SPOT_CHECK_COLUMNS = [*COLUMNS, "Publicerad", "Dragning"]
# The columns `apply_review` reads; both sheets have them (Minor 8, final review 2026-10-06).
IMPORT_COLUMNS = ("QID", "Kontroll", "Typ", "Id", "Faktum", "Beslut")
KEEP = "behåll"
STRIKE = "stryk"
CHANGE = "ändra"
DATA_SOURCES = {"artportalen": "Artportalen via GBIF", "rodlistan": "Svenska rödlistan 2025"}
# Stickprovet efter publicering (ändrat 2026-10-05 (b)): 2 arter per 40 publicerade.
SPOT_CHECK_BATCH = 40
SPOT_CHECK_DRAW = 2
# Which species have been counted toward a draw, and every draw with its seed (C2).
SPOT_CHECK_STATE = "stickprov-state.json"
# The species row of a spot check (follow-up 3, wave B review).
SPECIES_TYP = "art"
SPECIES_ID = "*"


def spot_check_file(review: Path, draw: int) -> Path:
    """Each draw is its own file, and its own sheet in Drive (follow-up 5, wave B review):
    a new draw is never uploaded over one whose decisions are not imported yet."""
    return review / f"stickprov-dragning-{draw}.csv"


def revision_url(lang: str, revision: str) -> str:
    return f"https://{lang}.wikipedia.org/w/index.php?oldid={revision}"


def _sources(record: Record, sources: list[dict[str, str]]) -> tuple[str, str]:
    revisions = record.get("wikipedia", {})
    links = []
    for s in sources:
        lang = s["article"]
        rev = revisions.get(lang, {}).get("revision")
        links.append(f"{lang}: {revision_url(lang, rev)}" if rev else lang)
    return " | ".join(links), " | ".join(s["quote"] for s in sources)


def _row(
    record: Record,
    *,
    rad: str,
    typ: str,
    fid: str,
    topic: str,
    fact: str,
    source: str,
    quote: str,
    decision: str,
    kontroll: str = "",
    publicerad: str = "",
    dragning: str = "",
) -> dict[str, str]:
    return {
        "Art": str(record["names"]["sv"]),
        "QID": str(record["qid"]),
        "Rad": rad,
        "Kontroll": kontroll,
        "Typ": typ,
        "Id": fid,
        "Ämne": topic,
        "Faktum": fact,
        "Källa": source,
        "Citat": quote,
        "Beslut": decision,
        "Kommentar": "",
        "Publicerad": publicerad,
        "Dragning": dragning,
    }


def full_sheet_rows(record: Record, *, draw: int | None = None) -> list[dict[str, str]]:
    """Every fact, status, data fact and the recording, for a spot-checked species (spec
    point 5: "visade med hela faktabladet"). `Publicerad` (bilaga E, stickprovsfliken) is
    filled from `record["publishedAt"]`, set by the publish step (Task 23) alongside
    `publish: true`; empty for a species that has not gone through that step. `Dragning`
    is the draw the rows belong to (C2/I1, final review 2026-10-06).

    Beslut is left empty on every fact and on the recording (I1): a sheet imported before
    Albin has looked at it must not count as checked. Data rows stay locked ("(data)").
    The first row is the species row (Typ `art`, Id `*`, follow-up 3 of the wave B
    review): `behåll` there keeps every row he left empty; a decision on a row wins."""
    published_at = str(record.get("publishedAt") or "")
    dragning = "" if draw is None else str(draw)
    rows: list[dict[str, str]] = [
        _row(
            record,
            rad="stickprov",
            typ=SPECIES_TYP,
            fid=SPECIES_ID,
            topic="hela arten",
            fact="behåll här gäller alla rader nedan som lämnas tomma",
            source="",
            quote="",
            decision="",
            publicerad=published_at,
            dragning=dragning,
        )
    ]
    for fact in record.get("facts", []):
        topic = fact["topic"]
        if topic == "data":
            source = DATA_SOURCES[fact["source"]]
            rows.append(
                _row(
                    record,
                    rad="stickprov",
                    typ="data",
                    fid=fact["id"],
                    topic=TOPIC_SV["data"],
                    fact=fact["sv"],
                    source=source,
                    quote="",
                    decision="(data)",
                    publicerad=published_at,
                    dragning=dragning,
                )
            )
            continue
        source, quote = _sources(record, fact["sources"])
        label = TOPIC_SV[topic]
        if topic == "lookalike":
            label = f"förväxling med {fact['other']['scientific']}"
        # Bilaga E: Typ is only faktum/data/inspelning/flagga; the status fact (s01) is a
        # faktum too, its Ämne column ("status i Sverige") already says what it is about.
        rows.append(
            _row(
                record,
                rad="stickprov",
                typ="faktum",
                fid=fact["id"],
                topic=label,
                fact=fact["sv"],
                source=source,
                quote=quote,
                decision="",
                publicerad=published_at,
                dragning=dragning,
            )
        )
    audio: dict[str, Any] | None = record.get("audio")
    if audio:
        summary = f"{audio.get('author') or 'okänd'}, {audio['license']}, {audio['durationSec']} s"
        rows.append(
            _row(
                record,
                rad="stickprov",
                typ="inspelning",
                fid="a01",
                topic="inspelning",
                fact=summary,
                source=audio["sourceUrl"],
                quote="",
                decision="",
                publicerad=published_at,
                dragning=dragning,
            )
        )
    return rows


def _audio_tag(audio: dict[str, Any]) -> str:
    """`sha:` and the file's first 12 hex digits. The prefix keeps Google Sheets from reading
    an all-digit (or digits-e-digits) hash as a number (merge review 2026-10-06)."""
    sha = str(audio.get("sha256") or "")
    return f"sha:{sha[:12]}" if sha else ""


def flag_rows(record: Record) -> list[dict[str, str]]:
    """One row per flag the automatic kontroll raised (spec V2 to V4). A V4 (recording)
    flag has no fact id; everything else points at the fact it is about."""
    facts_by_id = {f["id"]: f for f in record.get("facts", [])}
    rows: list[dict[str, str]] = []
    for flag in record.get("flags", []):
        fid = flag.get("factId") or ""
        fact = facts_by_id.get(fid)
        if fact is not None:
            source, quote = _sources(record, fact.get("sources", []))
            topic = TOPIC_SV.get(fact["topic"], fact["topic"])
        elif flag["check"] == "V4":
            audio = record.get("audio") or {}
            # The file's content (follow-up 6, wave B review): a new upload under the same
            # file page is a different recording, and the import must see that.
            source, quote, topic = audio.get("sourceUrl", ""), _audio_tag(audio), "inspelning"
        else:
            source, quote, topic = "", "", ""
        rows.append(
            _row(
                record,
                rad="flagga",
                typ="flagga",
                fid=fid,
                topic=topic,
                fact=flag["message"],
                source=source,
                quote=quote,
                decision="",
                kontroll=flag["check"],
            )
        )
    return rows


def write_sheet(path: Path, rows: list[dict[str, str]], columns: list[str] | None = None) -> None:
    """Writes the CSV as UTF-8 with a BOM (`utf-8-sig`) so Excel shows åäö, neutralises
    every text cell that could be read as a formula by Sheets/Excel, and writes
    atomically (`sheet_csv.write_sheet`, moved out 2026-10-06 so `compare.py` shares the
    same hardening). `columns` defaults to the undantag sheet's `COLUMNS`; pass
    `SPOT_CHECK_COLUMNS` for the stickprov sheet. `extrasaction="ignore"` lets `_row()`
    stay a single shared row-builder even though `Publicerad` is only used by one sheet."""
    _sheet_csv_write_sheet(path, rows, COLUMNS if columns is None else columns)


@dataclass
class ExportResult:
    path: Path
    flagged: list[str] = field(default_factory=list)
    # Decisions carried over from the sheet that was there (follow-up 5).
    carried: int = 0
    # Decided rows of species that wait for `web verify`, kept as they were (merge review).
    kept: int = 0
    # Decided rows that did not reach the new file, by reason ("redan kontrollerad" is the
    # expected one: the import has applied them).
    not_carried: list[str] = field(default_factory=list)


def _eligible(records: dict[str, Record], wave: int | None) -> list[Record]:
    return [
        r
        for r in records.values()
        if (wave is None or r.get("review", {}).get("wave") == wave)
        and not is_reviewed(r)
        and any(f["topic"] != "data" for f in r.get("facts", []))
        # Flags from an older fact sheet would only be rejected by the import (I2).
        and _verify_current(r)
    ]


def _queue_order(paths: WebPaths) -> dict[str, tuple[int, int]]:
    """Each species' (wave, position-in-wave) from review/waves.json, the run order (spec
    §14). Empty if waves.json does not exist yet — every species then falls back to its
    Swedish name (review fix 2026-10-06)."""
    file = paths.review / WAVES_FILE
    if not file.exists():
        return {}
    order: dict[str, tuple[int, int]] = {}
    for wave, qids in sorted(read_waves(file).items()):
        for index, qid in enumerate(qids):
            order[qid] = (wave, index)
    return order


def _queue_key(order: dict[str, tuple[int, int]], record: Record) -> tuple[int, int, int, str]:
    """Sorts known species by their queue position (wave, then position in that wave);
    a species missing from waves.json sorts after all of those, by Swedish name. The
    leading 0/1 keeps the two groups' differently-typed tails (`""` vs. `name`) from ever
    being compared against each other."""
    position = order.get(str(record["qid"]))
    if position is None:
        return (1, 0, 0, str(record["names"]["sv"]))
    wave, index = position
    return (0, wave, index, "")


def export_wave(paths: WebPaths, wave: int | None = None) -> ExportResult:
    """All flagged, undecided species (ändrat 2026-10-05 (b): stickprovet flyttat till
    export_spot_check, se nedan). The sheet is one continuous CSV in Drive, not split per
    wave — review fix 2026-10-06: exporting used to overwrite review/undantag.csv with
    just one wave's flags, dropping every other wave's still-open rows. `wave` stays as an
    optional filter ("just this run's flags"); the default (no filter) is what Task 17's
    import expects to read. Rows are sorted in queue order (wave, then each wave's order
    in review/waves.json), not alphabetically, so Albin reviews species in run order."""
    records = load_all(paths.data_out)
    eligible = _eligible(records, wave)
    order = _queue_order(paths)
    flagged = sorted((r for r in eligible if r.get("flags")), key=lambda r: _queue_key(order, r))
    path = paths.review / "undantag.csv"
    # Follow-up 5 (wave B review): the file there is the sheet downloaded from Drive. A
    # species that still waits keeps what Albin already wrote on its open flags, so the new
    # file can be uploaded over the Drive sheet without losing a decision.
    old_rows = read_sheet(path, required_columns=IMPORT_COLUMNS) if path.exists() else []
    old_by_qid: dict[str, list[dict[str, str]]] = {}
    for old_row in old_rows:
        old_by_qid.setdefault(old_row["QID"].strip(), []).append(old_row)
    result = ExportResult(path=path, flagged=[str(r["qid"]) for r in flagged])
    rows: list[dict[str, str]] = []
    lost: dict[str, list[str]] = {}
    for record in flagged:
        old = old_by_qid.get(str(record["qid"]), [])
        used: set[int] = set()
        for flag, row in zip(record.get("flags", []), flag_rows(record), strict=True):
            index = _carry_decision(record, flag, row, old)
            if index is not None:
                used.add(index)
                result.carried += 1
            rows.append(row)
        unused = [r for i, r in enumerate(old) if i not in used and _decided(r)]
        if unused:
            lost.setdefault(FLAG_GONE, []).append(f"{_name(record)} ({len(unused)})")
    flagged_qids = set(result.flagged)
    for qid, old in old_by_qid.items():
        decided = [r for r in old if _decided(r)]
        if qid in flagged_qids or not decided:
            continue
        found = records.get(qid)
        if found is not None and not is_reviewed(found) and not _verify_current(found):
            # Merge review (2026-10-06): the facts changed after the flags were written and
            # `web verify` has not run again. Kept as they are: the import skips them until
            # it has, and the next `web sheet` matches them to the new flags.
            rows += old
            result.kept += len(decided)
            lost.setdefault(WAITING_FOR_VERIFY, []).append(f"{_name(found)} ({len(decided)})")
            continue
        if found is None:
            reason, name = "arten finns inte", qid
        elif is_reviewed(found):
            reason, name = ALREADY_DECIDED, _name(found)
        elif wave is not None and found.get("review", {}).get("wave") != wave:
            reason, name = "utanför --wave", _name(found)
        else:
            reason, name = FLAG_GONE, _name(found)
        lost.setdefault(reason, []).append(f"{name} ({len(decided)})")
    result.not_carried = [f"{reason}: {', '.join(names)}" for reason, names in lost.items()]
    write_sheet(path, rows)
    return result


ALREADY_DECIDED = "redan kontrollerad (väntat, importen har tagit dem)"
WAITING_FOR_VERIFY = "väntar på web verify, raderna behölls oförändrade"
FLAG_GONE = "flaggan finns inte längre"


def _decided(row: dict[str, str]) -> bool:
    return bool(_decision(row) or row.get("Kommentar", "").strip())


def _carry_decision(
    record: Record, flag: dict[str, Any], row: dict[str, str], old: list[dict[str, str]]
) -> int | None:
    """Copies Beslut, Kommentar and (for a status `ändra`) Faktum from the first old row
    that decides this very flag (`_row_matches`, the import's own test). Returns that
    row's index in `old`, or None."""
    for index, old_row in enumerate(old):
        if _decided(old_row) and _row_matches(record, old_row, flag):
            row["Beslut"] = old_row.get("Beslut", "").strip()
            row["Kommentar"] = old_row.get("Kommentar", "")
            if _sets_status(old_row):
                row["Faktum"] = old_row["Faktum"].strip()
            return index
    return None


@dataclass
class SpotCheckResult:
    path: Path
    species: list[str] = field(default_factory=list)
    seed: int = 0
    draw: int = 0


def _empty_state() -> dict[str, Any]:
    return {"publishedAtLastDraw": 0, "counted": [], "draws": []}


def read_spot_check_state(path: Path) -> dict[str, Any]:
    """`review/stickprov-state.json` (C2, final review 2026-10-06): `counted` is every
    species already counted toward a draw (published at some point), so a draw only ever
    samples among species published since the last one; `draws` lists every draw with its
    id, seed, date and species, so each one can be re-run from the file."""
    if not path.exists():
        return _empty_state()
    data: dict[str, Any] = {**_empty_state(), **json.loads(path.read_text(encoding="utf-8"))}
    return data


def _write_state(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    tmp.replace(path)


def _drawable(record: Record) -> bool:
    """Live, verified and not drawn before."""
    verification = record.get("verification") or {}
    return bool(record.get("publish") and verification and not verification.get("spotChecked"))


def _published_ever(record: Record) -> bool:
    return bool(record.get("publish") or record.get("publishedAt"))


def _publish_order(record: Record) -> tuple[str, str, str]:
    return (
        str(record.get("publishedAt") or "9999"),
        str(record["names"]["sv"]),
        str(record["qid"]),
    )


def _by_name(record: Record) -> tuple[str, str]:
    return (str(record["names"]["sv"]), str(record["qid"]))


def mark_drawn(record: Record, draw: int) -> None:
    """What a draw writes on the species: `verification.spotChecked` (spec: set at the draw,
    not at the import) and `review.spotCheck`, which ties the sheet's rows to this draw and
    to the fact sheet and recording they were drawn from (I1, final review 2026-10-06)."""
    record["verification"]["spotChecked"] = True
    audio = record.get("audio")
    record.setdefault("review", {})["spotCheck"] = {
        "draw": draw,
        "factsHash": facts_hash(record),
        "audio": audio_id(audio) if audio else None,
    }


def export_spot_check(
    paths: WebPaths,
    *,
    seed: int | None = None,
    extra_species: tuple[str, ...] = (),
    force: bool = False,
    today: str | None = None,
) -> SpotCheckResult | None:
    """Stickprovet efter publicering (spec Revision 2026-10-05 (b)): SPOT_CHECK_DRAW arter
    per SPOT_CHECK_BATCH publicerade. Rewritten for C2 (final review 2026-10-06): the old
    trigger counted every published-but-undrawn species, so after the first 40 every two
    further publications drew again (142 of 180). Now `review/stickprov-state.json` keeps
    the species already counted; a draw takes the next whole batches of species published
    since then (in publish order), samples 2 per batch among those still live and verified,
    and counts the batch. A remainder waits for the next batch.

    `force` draws SPOT_CHECK_DRAW among every live, verified, undrawn species without
    counting anything (a redraw after a confirmed miss); `extra_species` adds named species
    to this draw (each must exist, be published and verified, review fix 2026-10-06).
    The seed is random unless given, and saved with the draw, so the draw can be re-run.
    The drawn rows go to the draw's own file, `review/stickprov-dragning-N.csv`
    (follow-up 5, wave B review), with an empty Beslut (I1)."""
    records = load_all(paths.data_out)
    state_path = paths.review / SPOT_CHECK_STATE
    state = read_spot_check_state(state_path)
    counted = set(state["counted"])
    new = sorted(
        (r for q, r in records.items() if _published_ever(r) and q not in counted),
        key=_publish_order,
    )
    batches = len(new) // SPOT_CHECK_BATCH
    pool = new[: batches * SPOT_CHECK_BATCH]
    used_seed = seed if seed is not None else random.SystemRandom().randrange(1, 1_000_000)
    rng = random.Random(used_seed)
    drawn: list[Record] = []
    if pool:
        candidates = sorted((r for r in pool if _drawable(r)), key=_by_name)
        drawn = rng.sample(candidates, k=min(batches * SPOT_CHECK_DRAW, len(candidates)))
    elif force:
        candidates = sorted((r for r in records.values() if _drawable(r)), key=_by_name)
        drawn = rng.sample(candidates, k=min(SPOT_CHECK_DRAW, len(candidates)))
    drawn_by_qid = {str(r["qid"]): r for r in drawn}
    for qid in extra_species:
        record = records.get(qid)
        if record is None:
            raise ValueError(f"--extra {qid}: okänd art, hittar ingen post i {paths.data_out}")
        if not record.get("publish"):
            raise ValueError(f"--extra {qid}: arten är inte publicerad (publish: true saknas)")
        if not record.get("verification"):
            raise ValueError(f"--extra {qid}: arten är inte verifierad (verification saknas)")
        drawn_by_qid.setdefault(qid, record)
    if not drawn_by_qid and not pool:
        return None
    draw_id = len(state["draws"]) + 1
    drawn_list = sorted(drawn_by_qid.values(), key=_by_name)
    path = spot_check_file(paths.review, draw_id)
    if drawn_list:
        rows = [row for r in drawn_list for row in full_sheet_rows(r, draw=draw_id)]
        write_sheet(path, rows, columns=SPOT_CHECK_COLUMNS)
        for record in drawn_list:
            mark_drawn(record, draw_id)
            save_record(record_path(paths.data_out, str(record["qid"])), record)
    state["counted"] = sorted(counted | {str(r["qid"]) for r in pool})
    state["publishedAtLastDraw"] = len(state["counted"])
    state["draws"].append(
        {
            "id": draw_id,
            "seed": used_seed,
            "at": today or _date.today().isoformat(),
            "batches": batches,
            "forced": bool(force and not pool),
            "extra": list(extra_species),
            "species": [str(r["qid"]) for r in drawn_list],
        }
    )
    _write_state(state_path, state)
    if not drawn_list:
        return None
    return SpotCheckResult(
        path=path, species=[str(r["qid"]) for r in drawn_list], seed=used_seed, draw=draw_id
    )


class ReviewImportError(ValueError):
    """Raised by `apply_review` when any row in the sheet is invalid. Nothing is changed:
    the whole sheet is validated first (Task 17)."""


@dataclass
class Republish:
    """A published page that is no longer ready after an import, why, and the exact
    commands that bring it back (I8, final review 2026-10-06)."""

    qid: str
    name: str
    reasons: list[str]
    commands: list[str]


@dataclass
class ImportResult:
    changed: list[str] = field(default_factory=list)
    removed_audio: list[str] = field(default_factory=list)
    # Species whose rows are not all decided yet: nothing applied, they wait (I1, I2).
    waiting: list[str] = field(default_factory=list)
    # Rows that no longer apply (an older or already decided draw, a changed fact sheet).
    ignored: list[str] = field(default_factory=list)
    # Every published page that is not ready any more (I8): `web import` exits non-zero.
    republish: list[Republish] = field(default_factory=list)
    # Published pages this import changed that are still ready: rebuild and push them.
    changed_published: list[str] = field(default_factory=list)
    # Recordings deleted by the end-of-run sweep (their record has no `audio`), and every
    # voice.mp3 that could not be deleted (follow-up 1, wave B review): `web import` exits 1.
    swept_audio: list[str] = field(default_factory=list)
    audio_errors: list[str] = field(default_factory=list)


def read_sheet(path: Path, *, required_columns: Sequence[str] = ()) -> list[dict[str, str]]:
    """Reads a sheet written by `write_sheet` and reverses its formula-injection escaping
    on every cell. Strips every leading UTF-8 BOM, not just the one `encoding="utf-8-sig"`
    would strip on its own: a Google Sheet re-exported on top of an already BOM'd file can
    end up with more than one, and a bare `utf-8-sig` open only removes the first, leaving
    a stray U+FEFF glued onto the header's first column name (Task 17 requirement 1).
    Delegates to `sheet_csv.read_sheet` (moved out 2026-10-06)."""
    return _sheet_csv_read_sheet(path, required_columns=required_columns)


def _decision(row: dict[str, str]) -> str:
    return row.get("Beslut", "").strip().lower()


def _typ(row: dict[str, str]) -> str:
    return row.get("Typ", "").strip()


def _name(record: Record) -> str:
    return f"{record['qid']} {record.get('names', {}).get('sv', '')}".strip()


@dataclass
class _Plan:
    """What one species' rows would do, worked out on a copy (nothing is changed until the
    whole sheet has passed): `record` is the species after the decisions, or None when
    nothing is applied."""

    errors: list[str] = field(default_factory=list)
    ignored: list[str] = field(default_factory=list)
    waiting: str | None = None
    record: Record | None = None
    removed_audio: bool = False


def _status_label_error(qid: str, fid: str, text: str) -> str | None:
    if text in STATUS_BY_SV:
        return None
    return f"{qid} {fid}: skriv en av {', '.join(STATUS_SV.values())}"


def _recheck_status(record: Record) -> list[dict[str, Any]]:
    """A status Albin just set has never been checked against the report data: a
    confirmation of an earlier status does not cover it (follow-up 2), the stored signal is
    recomputed, and V3 runs on it. Returns the new flags (empty when it agrees)."""
    record.setdefault("review", {}).pop("statusConfirmed", None)
    status = next((f for f in record.get("facts", []) if f.get("topic") == "status"), None)
    data = record.get("data")
    if data is not None:
        reason = None
        if status is not None:
            reason = data_status_contradiction(status["value"], data)
        data["statusSignal"] = {"contradicts": reason}
    return [{**f, "message": without_dashes(str(f["message"]))} for f in status_flags(record)]


def _refused_on_a_live_page(record: Record, new_flags: list[dict[str, Any]], plan: _Plan) -> bool:
    """Follow-up 2 (wave B review, Albin's call): a status Albin sets that the report data
    (or the red list) contradicts would hold the species back from `verification`; on a
    published page that fails the whole fas 2 build. Refused instead, like Minor 10."""
    if not new_flags or not record.get("publish"):
        return False
    reasons = "; ".join(str(f["message"]) for f in new_flags)
    plan.errors.append(
        f"{record['qid']} är publicerad och den nya statusen motsäger rapportdatan: sätt "
        f"publish: false först (eller välj en annan status), importera sedan igen. ({reasons})"
    )
    return True


def _empties_a_required_topic(before: Record, after: Record, plan: _Plan) -> bool:
    """Minor 10 (final review 2026-10-06): a decision that strikes the last appearance,
    voice or habitat fact leaves a page that may not exist (spec §9.6); the import refuses
    it instead of saving a species `web write` and `web publish` would then skip."""
    lost = [
        topic
        for topic in missing_required_topics(after.get("facts", []))
        if topic not in missing_required_topics(before.get("facts", []))
    ]
    if lost:
        plan.errors.append(
            f"{before['qid']}: besluten stryker allt om {', '.join(lost)}. Behåll eller ändra "
            "minst ett sådant faktum (eller avpublicera arten och kör web facts --force)."
        )
    return bool(lost)


# -- the spot check (stickprov-dragning-N.csv) --------------------------------------------


def _spot_rows_for_current_draw(
    record: Record, rows: list[dict[str, str]], plan: _Plan
) -> list[dict[str, str]]:
    """The rows of the species' current draw, if it is still open and the fact sheet and
    recording are the ones it was drawn from. Every other row is reported and left out:
    the sheet is cumulative, so a re-import must never re-apply (or re-date) an old draw."""
    qid = str(record["qid"])
    spot = record.get("review", {}).get("spotCheck") or {}
    current = spot.get("draw")
    by_draw: dict[str, list[dict[str, str]]] = {}
    for row in rows:
        by_draw.setdefault(row.get("Dragning", "").strip(), []).append(row)
    if "" in by_draw:
        plan.errors.append(f"{qid}: stickprovsrader utan Dragning (kolumnen saknas eller är tom)")
        return []
    selected: list[dict[str, str]] = []
    for draw, draw_rows in by_draw.items():
        if current is None or draw != str(current):
            plan.ignored.append(
                f"{_name(record)}: raderna från dragning {draw} gäller inte (artens dragning "
                f"är {current if current is not None else 'ingen'})"
            )
        elif spot.get("decidedAt"):
            plan.ignored.append(
                f"{_name(record)}: dragning {draw} är redan avgjord {spot['decidedAt']}"
            )
        else:
            selected = draw_rows
    if not selected:
        return []
    if not record.get("verification"):
        # Open flags from a later `web verify` (the species was unpublished and re-run):
        # those come first, in the exception sheet; the spot check never verifies by itself.
        plan.waiting = f"{_name(record)}: arten har öppna flaggor, besluta dem i undantagsarket"
        return []
    audio = record.get("audio")
    if spot.get("factsHash") != facts_hash(record) or spot.get("audio") != (
        audio_id(audio) if audio else None
    ):
        plan.waiting = (
            f"{_name(record)}: faktabladet eller inspelningen har ändrats sedan dragning "
            f"{current}, raderna gäller inte längre (dra om: web spot-check --extra {qid})"
        )
        return []
    return selected


def _validate_spot_rows(record: Record, rows: list[dict[str, str]], plan: _Plan) -> bool:
    """Errors for anything Albin wrote that cannot be applied; True when every row has a
    decision (an empty Beslut means he has not decided yet, I1)."""
    qid = str(record["qid"])
    facts_by_id = {f["id"]: f for f in record.get("facts", []) if f["topic"] != "data"}
    seen: dict[str, int] = {}
    audio_rows = 0
    undecided = 0
    species_rows = 0
    for row in rows:
        typ, decision, fid = _typ(row), _decision(row), row["Id"].strip()
        if typ == "data":
            continue
        if typ == SPECIES_TYP:
            species_rows += 1
            if decision not in ("", KEEP):
                plan.errors.append(f"{qid}: skriv behåll eller lämna tomt på artraden")
            continue
        if typ == "inspelning":
            audio_rows += 1
            if decision == CHANGE:
                plan.errors.append(f"{qid} inspelning: skriv behåll eller stryk")
            elif decision and decision not in (KEEP, STRIKE):
                plan.errors.append(f"{qid} inspelning: okänt beslut {row['Beslut']!r}")
            undecided += not decision
            continue
        if typ not in ("faktum", "status"):
            plan.errors.append(f"{qid} {fid}: okänd Typ {typ!r} i stickprovet")
            continue
        seen[fid] = seen.get(fid, 0) + 1
        if fid not in facts_by_id:
            plan.errors.append(f"{qid} {fid}: faktumet finns inte i artens faktablad")
            continue
        if not decision:
            undecided += 1
        elif decision not in (KEEP, STRIKE, CHANGE):
            plan.errors.append(f"{qid} {fid}: okänt beslut {row['Beslut']!r}")
        elif decision == CHANGE and not row["Faktum"].strip():
            plan.errors.append(f"{qid} {fid}: ändra kräver en ny text i Faktum")
        elif decision == CHANGE and facts_by_id[fid]["topic"] == "status":
            # Typ is "faktum" for the status fact too (bilaga E): the fact's own topic is
            # what marks this as the status row.
            error = _status_label_error(qid, fid, row["Faktum"].strip())
            if error:
                plan.errors.append(error)
    missing = sorted(set(facts_by_id) - set(seen))
    if missing:
        plan.errors.append(f"{qid}: raderna {', '.join(missing)} saknas i stickprovet")
    twice = sorted(fid for fid, n in seen.items() if n > 1)
    if twice:
        plan.errors.append(f"{qid}: raderna {', '.join(twice)} står flera gånger")
    if bool(record.get("audio")) != bool(audio_rows) or audio_rows > 1:
        plan.errors.append(f"{qid}: stickprovet ska ha en inspelningsrad om arten har en")
    if species_rows > 1:
        plan.errors.append(f"{qid}: artraden står flera gånger i samma dragning")
    return undecided == 0


def _keep_the_rest(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    """With `behåll` on the species row, every fact and recording row Albin left empty
    counts as `behåll`; a decision he wrote on a row wins (follow-up 3, wave B review).
    Without it, every row must be decided."""
    if not any(_typ(r) == SPECIES_TYP and _decision(r) == KEEP for r in rows):
        return rows
    decidable = ("faktum", "status", "inspelning")
    return [{**r, "Beslut": KEEP} if _typ(r) in decidable and not _decision(r) else r for r in rows]


def _plan_spot_check(record: Record, rows: list[dict[str, str]], date: str) -> _Plan:
    plan = _Plan()
    rows = _spot_rows_for_current_draw(record, rows, plan)
    if not rows or plan.errors:
        return plan
    rows = _keep_the_rest(rows)
    decided = _validate_spot_rows(record, rows, plan)
    if plan.errors:
        return plan
    if not decided:
        plan.waiting = f"{_name(record)}: stickprovet har rader utan beslut"
        return plan
    new = copy.deepcopy(record)
    decisions = {r["Id"].strip(): r for r in rows if _typ(r) in ("faktum", "status")}
    facts: list[dict[str, Any]] = []
    for fact in new.get("facts", []):
        row = decisions.get(fact["id"])
        if fact["topic"] == "data" or row is None:
            facts.append(fact)
            continue
        decision = _decision(row)
        if decision == STRIKE:
            continue
        if decision == CHANGE:
            text = row["Faktum"].strip()
            if fact["topic"] == "status":
                if text != fact["sv"]:
                    fact = {**fact, "value": STATUS_BY_SV[text], "sv": text, "edited": True}
            elif text != fact["sv"]:
                fact = {**fact, "sv": text, "edited": True}
        facts.append(fact)
    status_before = next((f for f in record["facts"] if f["topic"] == "status"), None)
    status_after = next((f for f in facts if f["topic"] == "status"), None)
    new["facts"] = facts
    audio_struck = any(_typ(r) == "inspelning" and _decision(r) == STRIKE for r in rows)
    if audio_struck:
        new.pop("audio", None)
        new.setdefault("review", {})["audioStruck"] = True
        plan.removed_audio = True
    if _empties_a_required_topic(record, new, plan):
        return plan
    new["review"]["spotCheck"]["decidedAt"] = date
    changed = facts != record["facts"] or audio_struck
    if changed:
        # A confirmed error: the page gets a new "Kontrollerad mot källorna" date and is
        # published again (spec). An all-`behåll` draw changes nothing and keeps its date.
        verify_meta = new.setdefault("generated", {}).setdefault("verify", {})
        verify_meta["factsHash"] = facts_hash(new)
        new["verification"] = {**new["verification"], "at": date, "spotChecked": True}
        if status_after is not None and status_after != status_before:
            new_flags = _recheck_status(new)
            if _refused_on_a_live_page(record, new_flags, plan):
                return plan
            if new_flags:
                new["flags"] = new_flags
                new.pop("verification", None)
    plan.record = new
    return plan


# -- the exception sheet (undantag.csv) ---------------------------------------------------


def _verify_current(record: Record) -> bool:
    """The flags in `record` came from a `web verify` run on the facts as they are now."""
    verify = record.get("generated", {}).get("verify") or {}
    return bool(verify.get("factsHash") == facts_hash(record))


def _same(a: str, b: str) -> bool:
    return " ".join(a.split()) == " ".join(b.split())


def _sets_status(row: dict[str, str]) -> bool:
    """A V1 or V3 row on the status fact whose Faktum Albin replaced with a status label
    (`ändra`): it is about the status as a whole, not one flag's wording."""
    return row["Id"].strip() == "s01" and _decision(row) == CHANGE


def _row_matches(record: Record, row: dict[str, str], flag: dict[str, Any]) -> bool:
    """I2 (final review 2026-10-06): a sheet row only ever decides the flag it was written
    for. Same check and fact id, and (unless Albin replaced the Faktum with a new status)
    the same message, the same quotes for a fact flag and the same file for a recording
    flag. A row written for an earlier fact sheet or recording matches nothing."""
    check, fid = row["Kontroll"].strip(), row["Id"].strip()
    if flag["check"] != check or (flag.get("factId") or "") != fid:
        return False
    if _sets_status(row) or (check == "V1" and fid == "s01"):
        return True
    if not _same(row["Faktum"], str(flag["message"])):
        return False
    fact = next((f for f in record.get("facts", []) if f["id"] == fid), None)
    if fact is not None:
        return _same(row.get("Citat", ""), _sources(record, fact.get("sources", []))[1])
    if check == "V4":
        audio = record.get("audio") or {}
        return _same(row.get("Källa", ""), str(audio.get("sourceUrl", ""))) and _same(
            row.get("Citat", ""), _audio_tag(audio)
        )
    return True


def _validate_flag_decision(qid: str, row: dict[str, str], plan: _Plan) -> None:
    decision, fid, check = _decision(row), row["Id"].strip(), row["Kontroll"].strip()
    if not decision:
        return
    if check in ("V1", "V3") and fid == "s01":
        # V1 on s01 (review fix 2026-10-06): the fact checker struck the status fact, so
        # Albin can set one from the sheet instead of only keeping it empty -- same six
        # labels as an `ändra` on a status row elsewhere. V3 (Minor 9, final review
        # 2026-10-06): the data contradicts the status, and Albin can write the right one.
        if decision not in (KEEP, STRIKE, CHANGE):
            plan.errors.append(f"{qid}: skriv behåll, stryk eller ändra på flaggan")
        elif decision == CHANGE:
            error = _status_label_error(qid, fid, row["Faktum"].strip())
            if error:
                plan.errors.append(error)
    elif decision not in (KEEP, STRIKE):
        plan.errors.append(f"{qid}: skriv behåll eller stryk på flaggan")


def _flag_decisions(
    record: Record, rows: list[dict[str, str]], plan: _Plan
) -> list[tuple[dict[str, Any], dict[str, str]]] | None:
    """Each current flag with the row that decides it, or None when the species waits (a
    flag without a decided row). Rows that match no current flag are reported and left
    out; two different decisions on one flag are an error."""
    qid = str(record["qid"])
    flags = record.get("flags") or []
    matched: dict[int, list[dict[str, str]]] = {}
    for row in rows:
        _validate_flag_decision(qid, row, plan)
        hits = [i for i, flag in enumerate(flags) if _row_matches(record, row, flag)]
        if not hits:
            plan.ignored.append(
                f"{_name(record)}: raden {row['Kontroll'].strip()} {row['Id'].strip()} "
                "stämmer inte med någon aktuell flagga (inaktuell rad)"
            )
        for i in hits:
            matched.setdefault(i, []).append(row)
    decided: list[tuple[dict[str, Any], dict[str, str]]] = []
    undecided = 0
    for i, flag in enumerate(flags):
        with_decision = [r for r in matched.get(i, []) if _decision(r)]
        if not with_decision:
            undecided += 1
            continue
        kinds = {
            (_decision(r), r["Faktum"].strip() if _sets_status(r) else "") for r in with_decision
        }
        if len(kinds) > 1:
            label = f"{flag['check']} {flag.get('factId') or ''}".strip()
            plan.errors.append(f"{qid}: olika beslut på flaggan {label}")
            continue
        decided.append((flag, with_decision[0]))
    if undecided:
        plan.waiting = f"{_name(record)}: {undecided} flaggor saknar beslut"
        return None
    return decided


def _plan_flags(record: Record, rows: list[dict[str, str]], date: str) -> _Plan:
    """Applies Albin's decisions on one species' flags (Task 17), guarded (I2, final review
    2026-10-06): only for a species still waiting (no `verification`) whose flags came
    from `web verify` on the facts as they are now, only rows that match a current flag,
    and only once every current flag has a decision (otherwise the species waits; the rest
    of the sheet is imported). A V1 `ändra` on the struck status fact recreates it and
    re-runs V3 on it (review fix 2026-10-06); new V3 flags hold the species back from
    `verification` instead of marking it checked with a fresh, unchecked status."""
    plan = _Plan()
    if is_reviewed(record):
        plan.ignored.append(f"{_name(record)}: redan kontrollerad, raderna hoppades över")
        return plan
    if not _verify_current(record):
        plan.waiting = (
            f"{_name(record)}: faktabladet har ändrats sedan flaggorna skrevs, kör web verify "
            "(raderna hoppades över)"
        )
        return plan
    decided = _flag_decisions(record, rows, plan)
    if plan.errors or decided is None:
        return plan
    v3 = [_decision(row) for flag, row in decided if flag["check"] == "V3"]
    v3_labels = {
        row["Faktum"].strip()
        for flag, row in decided
        if flag["check"] == "V3" and _decision(row) == CHANGE
    }
    if len(v3_labels) > 1 or (v3_labels and STRIKE in v3):
        plan.errors.append(f"{record['qid']}: motstridiga beslut om statusen (ändra och stryk)")
        return plan
    new = copy.deepcopy(record)
    facts = list(new.get("facts", []))
    review = new.setdefault("review", {})
    new_status: dict[str, Any] | None = None
    if v3_labels:
        # Minor 9: the right status, from a V3 flag. Like a V1 `ändra` it is rechecked
        # against the data below; the other V3 flags were about the old status.
        label = v3_labels.pop()
        old = next(
            (f for f in facts if f["id"] == "s01"), {"id": "s01", "topic": "status", "sources": []}
        )
        new_status = {**old, "value": STATUS_BY_SV[label], "sv": label, "edited": True}
    elif v3:
        # Every V3 flag kept: the status stands against the data. A stryk on any of them
        # takes the status away.
        review["statusConfirmed"] = all(d == KEEP for d in v3)
        if STRIKE in v3:
            facts = [f for f in facts if f["topic"] != "status"]
    for flag, row in decided:
        decision = _decision(row)
        check, fid = flag["check"], flag.get("factId") or ""
        if check == "V3":
            continue
        elif check == "V4" and decision == STRIKE:
            new.pop("audio", None)
            review["audioStruck"] = True
            plan.removed_audio = True
        elif check == "V4" and decision == KEEP and new.get("audio"):
            # Remembered for this exact recording, so a forced re-verify does not ask again
            # (follow-up 2, wave A review); a different recording is checked as usual.
            review["audioKept"] = audio_id(new["audio"])
        elif check == "V2" and decision == STRIKE:
            facts = [f for f in facts if f["id"] != fid]
        elif check == "V1" and fid == "s01" and decision == CHANGE:
            text = row["Faktum"].strip()
            new_status = {
                "id": "s01",
                "topic": "status",
                "value": STATUS_BY_SV[text],
                "sv": text,
                "sources": [],
                "edited": True,
            }
    new_flags: list[dict[str, Any]] = []
    if new_status is None:
        new["facts"] = facts
    else:
        # behåll/stryk on the V1 flag both just leave the status empty (it is already gone
        # from `facts`, struck by the fact checker before the flag was written) -- only
        # ändra needs this recreate-and-recheck path. A V3 `ändra` replaces s01 in place.
        if any(f["id"] == "s01" for f in facts):
            new["facts"] = [new_status if f["id"] == "s01" else f for f in facts]
        else:
            new["facts"] = [*facts, new_status]
        new_flags = _recheck_status(new)
        if _refused_on_a_live_page(record, new_flags, plan):
            return plan
    if _empties_a_required_topic(record, new, plan):
        return plan
    verify_meta = new.setdefault("generated", {}).setdefault("verify", {})
    verify_meta["factsHash"] = facts_hash(new)
    if new_flags:
        new["flags"] = new_flags
        # N3 (review fix 2026-10-06): a `verification` must not survive a brand new flag.
        new.pop("verification", None)
    else:
        new["verification"] = {
            "method": "auto",
            "at": date,
            "model": verify_meta.get("model", "unknown"),
            "spotChecked": False,
        }
    plan.record = new
    return plan


def apply_review(
    records: dict[str, Record], rows: list[dict[str, str]], *, date: str
) -> ImportResult:
    """Validates the whole sheet first (spec Revision 2026-10-05); changes nothing if any
    row is wrong. Each species' decisions are worked out on a copy, and only swapped in
    once every species has passed. Flag rows (Typ `flagga`, the exception sheet) set
    `verification` and refresh `generated.verify.factsHash` to match the edited facts (Task
    17 requirement 4). Spot-check rows (the other Typ values, `stickprov-dragning-N.csv`) apply only
    for the species' current, undecided draw (I1, final review 2026-10-06), re-date
    `verification.at` only when a decision changed something, and a species with an empty
    Beslut waits instead of counting as checked."""
    by_qid: dict[str, list[dict[str, str]]] = {}
    for row in rows:
        by_qid.setdefault(row["QID"].strip(), []).append(row)
    result = ImportResult()
    errors: list[str] = []
    plans: dict[str, _Plan] = {}
    for qid, qrows in by_qid.items():
        record = records.get(qid)
        if record is None:
            errors.append(f"{qid}: arten finns inte bland artfilerna")
            continue
        flagged = [r for r in qrows if _typ(r) == "flagga"]
        spot = [r for r in qrows if _typ(r) != "flagga"]
        if flagged and spot:
            errors.append(f"{qid}: flaggor och stickprovsrader i samma ark, importera var för sig")
            continue
        plan = _plan_spot_check(record, spot, date) if spot else _plan_flags(record, flagged, date)
        errors += plan.errors
        plans[qid] = plan
    if errors:
        raise ReviewImportError("\n".join(errors))
    for qid, plan in plans.items():
        result.ignored += plan.ignored
        if plan.waiting:
            result.waiting.append(plan.waiting)
        if plan.record is None:
            continue
        records[qid].clear()
        records[qid].update(plan.record)
        result.changed.append(qid)
        if plan.removed_audio:
            result.removed_audio.append(qid)
    return result


def republish_commands(qid: str, record: Record) -> list[str]:
    """What brings a published page back to ready: the text and comparisons rewritten
    from the facts as they are now, then `web publish` for the page (fas 2 Task 16 Step 4).
    A page whose facts wait for a decision, or lack a required topic, goes offline first."""
    rewrite = [
        f"uv run birdy-fetcher web write --species {qid} --max-cost 2",
        "uv run birdy-fetcher web compare --max-cost 5",
        f"uv run birdy-fetcher web publish --species {qid}",
    ]
    unpublish = f'sätt "publish": false i website/src/data/species/{qid}.json, bygg och pusha'
    if missing_required_topics(record.get("facts", [])):
        return [unpublish, f"uv run birdy-fetcher web facts --species {qid} --force --max-cost 5"]
    if not facts_verified(record):
        return [unpublish, "uv run birdy-fetcher web sheet, Albin beslutar, web import", *rewrite]
    return rewrite


def _auto_clear(record: Record) -> bool:
    """A species that was verified, had no flags and was never drawn for the spot check
    needs no decision: it never got a sheet row at all (spec point 6)."""
    return (
        bool(record.get("generated", {}).get("verify"))
        and not record.get("flags")
        and not is_reviewed(record)
    )


def import_wave(
    paths: WebPaths, sheet: Path, *, wave: int | None = None, date: str
) -> ImportResult:
    """Ändrat 2026-10-05 (b): `wave` är valfritt. Utan den importeras bara arket, utan
    säkerhetsnätet som letar upp opåverkade arter i en bestämd våg (`_auto_clear` behövs
    sällan längre, se Task 17, men är kvar för äldre data). Samma funktion importerar både
    det löpande undantagsarket och stickprovet efter publicering (en fil per dragning,
    `review/stickprov-dragning-N.csv`):
    ett stickprov där Albin stryker eller ändrar något sätter `verification.at` till
    importdatumet, vilket är det nya datumet på "Kontrollerad mot källorna"."""
    records = load_all(paths.data_out)
    try:
        rows = read_sheet(sheet, required_columns=IMPORT_COLUMNS)
    except ValueError as exc:
        # A `;`-separated export (or a missing column) is a sheet error like any other,
        # not a KeyError traceback (Minor 8, final review 2026-10-06).
        raise ReviewImportError(str(exc)) from exc
    result = apply_review(records, rows, date=date)
    if wave is not None:
        for qid, record in records.items():
            if qid in result.changed or record.get("review", {}).get("wave") != wave:
                continue
            if _auto_clear(record):
                record["verification"] = {
                    "method": "auto",
                    "at": date,
                    "model": record["generated"]["verify"]["model"],
                    "spotChecked": False,
                }
                result.changed.append(qid)
    for qid in result.changed:
        save_record(record_path(paths.data_out, qid), records[qid])
    for qid in result.removed_audio:
        delete_voice(paths.images_out, qid)
    # Every record without `audio` (the ones just struck included): the sweep is the last
    # word, so a file still locked is reported once and a stray one from earlier goes too.
    sweep = sweep_orphan_voices(paths.data_out, paths.images_out)
    result.swept_audio = [q for q in sweep.removed if q not in result.removed_audio]
    result.audio_errors = sweep.errors
    # I8 (final review 2026-10-06): every live page that is not ready any more after this
    # import (not just the ones it changed), and the ones it changed that still are.
    for qid, record in sorted(records.items()):
        if not record.get("publish"):
            continue
        reasons = unready_reasons(record)
        if reasons:
            name = str(record.get("names", {}).get("sv", qid))
            result.republish.append(Republish(qid, name, reasons, republish_commands(qid, record)))
        elif qid in result.changed:
            result.changed_published.append(qid)
    return result
