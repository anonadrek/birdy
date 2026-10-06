"""The exception sheet (spec 2026-09-25 Revision 2026-10-05, §9.4 and appendix E): a
running CSV, uploaded to Albin's Drive as a Google Sheet, exported back as CSV and
imported. Only what the automatic kontroll (V1 to V4, Task 14e) flagged. The seeded spot
check moved out to `export_spot_check` after publication (Revision 2026-10-05 (b)).

Written as UTF-8 with a BOM (review fix 2026-10-06) so Excel shows åäö; a reader must
open with `encoding="utf-8-sig"` too, including the future Task 17 import step."""

from __future__ import annotations

import random
from collections.abc import Sequence
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from .datamod import status_contradiction
from .facts import STATUS_BY_SV, STATUS_SV, TOPIC_SV
from .paths import WebPaths
from .record import (
    Record,
    audio_id,
    facts_hash,
    is_reviewed,
    load_all,
    record_path,
    save_record,
)
from .sheet_csv import read_sheet as _sheet_csv_read_sheet
from .sheet_csv import write_sheet as _sheet_csv_write_sheet
from .verify import status_flags
from .waves import WAVES_FILE, read_waves

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
# Bilaga E (ändrat 2026-10-05 (b)): "Publicerad" finns bara i stickprovsfliken.
SPOT_CHECK_COLUMNS = [*COLUMNS, "Publicerad"]
# The columns `apply_review` reads; both sheets have them (Minor 8, final review 2026-10-06).
IMPORT_COLUMNS = ("QID", "Kontroll", "Typ", "Id", "Faktum", "Beslut")
KEEP = "behåll"
STRIKE = "stryk"
CHANGE = "ändra"
DATA_SOURCES = {"artportalen": "Artportalen via GBIF", "rodlistan": "Svenska rödlistan 2025"}
# Stickprovet efter publicering (ändrat 2026-10-05 (b)): 2 arter per 40 publicerade.
SPOT_CHECK_BATCH = 40
SPOT_CHECK_DRAW = 2


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
    }


def full_sheet_rows(record: Record) -> list[dict[str, str]]:
    """Every fact, status, data fact and the recording, for a spot-checked species (spec
    point 5: "visade med hela faktabladet"). `Publicerad` (bilaga E, stickprovsfliken) is
    filled from `record["publishedAt"]`, set by the publish step (Task 23) alongside
    `publish: true`; empty for a species that has not gone through that step."""
    published_at = str(record.get("publishedAt") or "")
    rows: list[dict[str, str]] = []
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
                )
            )
            continue
        source, quote = _sources(record, fact["sources"])
        label = TOPIC_SV[topic]
        if topic == "lookalike":
            label = f"förväxling med {fact['other']['scientific']}"
        # Bilaga E: Typ is only faktum/data/inspelning/flagga — the status fact (s01) is a
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
                decision=KEEP,
                publicerad=published_at,
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
                decision=KEEP,
                publicerad=published_at,
            )
        )
    return rows


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
            source, quote, topic = audio.get("sourceUrl", ""), "", "inspelning"
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


def _eligible(records: dict[str, Record], wave: int | None) -> list[Record]:
    return [
        r
        for r in records.values()
        if (wave is None or r.get("review", {}).get("wave") == wave)
        and not is_reviewed(r)
        and any(f["topic"] != "data" for f in r.get("facts", []))
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
    rows = [row for r in flagged for row in flag_rows(r)]
    path = paths.review / "undantag.csv"
    write_sheet(path, rows)
    return ExportResult(path=path, flagged=[str(r["qid"]) for r in flagged])


@dataclass
class SpotCheckResult:
    path: Path
    species: list[str] = field(default_factory=list)
    seed: int = 0


def _unspotchecked_published(records: dict[str, Record]) -> list[Record]:
    return [
        r
        for r in records.values()
        if r.get("publish") and r.get("verification") and not r["verification"].get("spotChecked")
    ]


def export_spot_check(
    paths: WebPaths,
    *,
    seed: int | None = None,
    extra_species: tuple[str, ...] = (),
    force: bool = False,
) -> SpotCheckResult | None:
    """Stickprovet efter publicering (spec Revision 2026-10-05 (b), ersätter export_wave:s
    tidigare stickprov före publicering). Drar SPOT_CHECK_DRAW arter först när
    SPOT_CHECK_BATCH fler publicerade-men-inte-stickprovade arter har samlats sedan
    senaste dragningen; returnerar None annars. `force` drar direkt oavsett antal, för en
    redragning efter ett bekräftat fel (`extra_species` lägger till namngivna arter på
    samma dragning — varje Q-ID måste finnas, vara publicerad och verifierad, annars ett
    tydligt `ValueError`, review fix 2026-10-06). Dragna arter får
    `verification.spotChecked = true` direkt; Albins beslut (behåll/stryk/ändra)
    importeras separat, som för undantagsarkets flaggor."""
    records = load_all(paths.data_out)
    pending = sorted(_unspotchecked_published(records), key=lambda r: str(r["names"]["sv"]))
    used_seed = seed if seed is not None else 2000
    drawn: list[Record] = []
    if force or len(pending) >= SPOT_CHECK_BATCH:
        drawn = random.Random(used_seed).sample(pending, k=min(SPOT_CHECK_DRAW, len(pending)))
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
    if not drawn_by_qid:
        return None
    drawn_list = sorted(drawn_by_qid.values(), key=lambda r: str(r["names"]["sv"]))
    rows = [row for r in drawn_list for row in full_sheet_rows(r)]
    path = paths.review / "stickprov.csv"
    # (review fix 2026-10-06) `read_sheet`, not a plain `csv.DictReader`: a Sheets
    # re-export can carry more than one BOM, and a bare `encoding="utf-8-sig"` only
    # strips the first one, leaving "\ufeffArt" as the real key -- `write_sheet`'s
    # `extrasaction="ignore"` then silently emptied every existing row's Art column.
    existing_rows = read_sheet(path) if path.exists() else []
    write_sheet(path, [*existing_rows, *rows], columns=SPOT_CHECK_COLUMNS)
    for record in drawn_list:
        record["verification"]["spotChecked"] = True
        save_record(record_path(paths.data_out, str(record["qid"])), record)
    return SpotCheckResult(path=path, species=[str(r["qid"]) for r in drawn_list], seed=used_seed)


class ReviewImportError(ValueError):
    """Raised by `apply_review` when any row in the sheet is invalid. Nothing is changed:
    the whole sheet is validated first (Task 17)."""


@dataclass
class ImportResult:
    changed: list[str] = field(default_factory=list)
    removed_audio: list[str] = field(default_factory=list)


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


def _validate(records: dict[str, Record], by_qid: dict[str, list[dict[str, str]]]) -> list[str]:
    errors: list[str] = []
    for qid, rows in by_qid.items():
        record = records.get(qid)
        if record is None:
            errors.append(f"{qid}: arten finns inte bland artfilerna")
            continue
        facts_by_id = {f["id"]: f for f in record.get("facts", [])}
        full_sheet = any(r["Typ"].strip() in ("faktum", "status") for r in rows)
        if full_sheet:
            expected = {f["id"] for f in record.get("facts", []) if f["topic"] != "data"}
            seen = {r["Id"].strip() for r in rows if r["Typ"].strip() in ("faktum", "status")}
            missing = sorted(expected - seen)
            if missing:
                errors.append(f"{qid}: raderna {', '.join(missing)} saknas i stickprovet")
        for r in rows:
            typ, decision, fid = r["Typ"].strip(), _decision(r), r["Id"].strip()
            if typ == "data":
                continue
            if typ == "flagga":
                # V1 on s01 (review fix 2026-10-06): the fact checker struck the status
                # fact, so Albin can set one from the sheet instead of only keeping it
                # empty -- same six labels as an `ändra` on a status row elsewhere.
                check = r["Kontroll"].strip()
                if check == "V1" and fid == "s01":
                    if decision not in (KEEP, STRIKE, CHANGE):
                        errors.append(f"{qid}: skriv behåll, stryk eller ändra på flaggan")
                    elif decision == CHANGE and r["Faktum"].strip() not in STATUS_BY_SV:
                        labels = ", ".join(STATUS_SV.values())
                        errors.append(f"{qid} {fid}: skriv en av {labels}")
                elif decision not in (KEEP, STRIKE):
                    errors.append(f"{qid}: skriv behåll eller stryk på flaggan")
                continue
            if decision not in (KEEP, STRIKE, CHANGE):
                errors.append(f"{qid} {fid}: okänt beslut {r['Beslut']!r}")
            elif decision == CHANGE and not r["Faktum"].strip():
                errors.append(f"{qid} {fid}: ändra kräver en ny text i Faktum")
            elif (
                decision == CHANGE
                and facts_by_id.get(fid, {}).get("topic") == "status"
                and r["Faktum"].strip() not in STATUS_BY_SV
            ):
                # Typ is "faktum" for the status fact too (bilaga E), never "status" — the
                # fact's own topic, not the row's Typ, is what marks this as the status row.
                labels = ", ".join(STATUS_SV.values())
                errors.append(f"{qid} {fid}: skriv en av {labels}")
    return errors


def _apply_one(
    record: Record, rows: list[dict[str, str]], result: ImportResult
) -> list[dict[str, Any]]:
    """Applies Albin's decisions to one species' facts and flags. Returns any new V3 flags
    produced by re-checking a status Albin just set via a V1 `ändra` on the struck status
    fact (review fix 2026-10-06; empty otherwise). `apply_review` uses a non-empty return to
    hold the species back from `verification` instead of marking it reviewed with a fresh,
    unchecked status."""
    decisions = {r["Id"].strip(): r for r in rows if r["Typ"].strip() in ("faktum", "status")}
    facts: list[dict[str, Any]] = []
    for fact in record.get("facts", []):
        if fact["topic"] == "data":
            facts.append(fact)
            continue
        row = decisions.get(fact["id"])
        if row is None:  # not in the sheet: not flagged, kept exactly as the kontroll left it
            facts.append(fact)
            continue
        decision = _decision(row)
        if decision == STRIKE:
            continue
        if decision == CHANGE:
            text = row["Faktum"].strip()
            if fact["topic"] == "status":
                fact = {**fact, "value": STATUS_BY_SV[text], "sv": text, "edited": True}
            elif text != fact["sv"]:
                fact = {**fact, "sv": text, "edited": True}
        facts.append(fact)
    review = record.setdefault("review", {})
    new_status: dict[str, Any] | None = None
    for row in rows:
        typ, decision = row["Typ"].strip(), _decision(row)
        if typ != "flagga":
            continue
        check, fid = row["Kontroll"].strip(), row["Id"].strip()
        if check == "V3":
            review["statusConfirmed"] = decision == KEEP
            if decision == STRIKE:
                facts = [f for f in facts if f["topic"] != "status"]
        elif check == "V4" and decision == STRIKE:
            record.pop("audio", None)
            review["audioStruck"] = True
            result.removed_audio.append(str(record["qid"]))
        elif check == "V4" and decision == KEEP and record.get("audio"):
            # Remembered for this exact recording, so a forced re-verify does not ask again
            # (follow-up 2, wave A review); a different recording is checked as usual.
            review["audioKept"] = audio_id(record["audio"])
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
    if new_status is None:
        record["facts"] = facts
        return []
    # behåll/stryk on the V1 flag both just leave the status empty (it is already gone from
    # `facts`, struck by the fact checker before the flag was written) -- only ändra needs
    # this recreate-and-recheck path.
    record["facts"] = [*(f for f in facts if f["id"] != "s01"), new_status]
    # A confirmation belonged to an earlier status; this new one has never been checked
    # against the data, and status_flags honours a confirmation (follow-up 2).
    review.pop("statusConfirmed", None)
    data = record.get("data")
    if data is not None:
        reason = status_contradiction(
            new_status["value"], data.get("months"), int(data.get("totalReports", 0))
        )
        data["statusSignal"] = {"contradicts": reason}
    return status_flags(record)


def apply_review(
    records: dict[str, Record], rows: list[dict[str, str]], *, date: str
) -> ImportResult:
    """Validates the whole sheet first (spec Revision 2026-10-05); changes nothing if any
    row is wrong. For every affected species, applies Albin's decisions and refreshes
    `generated.verify.factsHash` to match the edited facts (Task 17 requirement 4) so a
    later `web verify` run sees its own facts as already current and does not re-run V1 on
    text Albin just hand-corrected. `verification` (the sole "is this species' facts ready
    to write text from" flag now that `review.facts` is gone) is then set as usual -- unless
    Albin just set a status via a V1 `ändra` and the V3 recheck found it contradicts the
    report data or the red list (review fix 2026-10-06): the species then keeps that new
    flag instead and stays pending for another round."""
    by_qid: dict[str, list[dict[str, str]]] = {}
    for row in rows:
        by_qid.setdefault(row["QID"].strip(), []).append(row)
    errors = _validate(records, by_qid)
    if errors:
        raise ReviewImportError("\n".join(errors))
    result = ImportResult()
    for qid, qrows in by_qid.items():
        record = records[qid]
        new_flags = _apply_one(record, qrows, result)
        spot_checked = any(r["Typ"].strip() in ("faktum", "status") for r in qrows)
        verify_meta = record.setdefault("generated", {}).setdefault("verify", {})
        verify_meta["factsHash"] = facts_hash(record)
        if new_flags:
            record["flags"] = new_flags
            # N3 (review fix 2026-10-06): the hash above is already refreshed, but a
            # `verification` left over from before this import must not survive a brand
            # new flag -- it would otherwise still look reviewed.
            record.pop("verification", None)
            result.changed.append(qid)
            continue
        record["verification"] = {
            "method": "auto",
            "at": date,
            "model": verify_meta.get("model", "unknown"),
            "spotChecked": spot_checked,
        }
        result.changed.append(qid)
    return result


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
    det löpande undantagsarket och stickprovet efter publicering (`review/stickprov.csv`):
    en `ändra` på en stickprovsrad sätter `verification.at` till importdatumet, vilket är
    det nya datumet på "Kontrollerad mot källorna"."""
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
        (paths.images_out / qid / "voice.mp3").unlink(missing_ok=True)
    return result
