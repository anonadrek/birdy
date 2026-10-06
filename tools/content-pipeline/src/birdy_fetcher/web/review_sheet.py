"""The exception sheet (spec 2026-09-25 Revision 2026-10-05, §9.4 and appendix E): a
running CSV, uploaded to Albin's Drive as a Google Sheet, exported back as CSV and
imported. Only what the automatic kontroll (V1 to V4, Task 14e) flagged. The seeded spot
check moved out to `export_spot_check` after publication (Revision 2026-10-05 (b)).

Written as UTF-8 with a BOM (review fix 2026-10-06) so Excel shows åäö; a reader must
open with `encoding="utf-8-sig"` too, including the future Task 17 import step."""

from __future__ import annotations

import csv
import random
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from .facts import TOPIC_SV
from .paths import WebPaths
from .record import Record, is_reviewed, load_all, record_path, save_record
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
KEEP = "behåll"
DATA_SOURCES = {"artportalen": "Artportalen via GBIF", "rodlistan": "Svenska rödlistan 2025"}
# Stickprovet efter publicering (ändrat 2026-10-05 (b)): 2 arter per 40 publicerade.
SPOT_CHECK_BATCH = 40
SPOT_CHECK_DRAW = 2
# Formula-injection-skydd (review fix 2026-10-06): en cell som börjar med något av dessa
# tecken tolkas som en formel av Sheets/Excel om den inte neutraliseras på väg ut.
_FORMULA_PREFIXES = ("=", "+", "-", "@", "\t", "\r")


def _escape_formula(value: str) -> str:
    return f"'{value}" if value.startswith(_FORMULA_PREFIXES) else value


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
    """Writes the CSV as UTF-8 with a BOM (`utf-8-sig`) so Excel shows åäö, and neutralises
    every text cell that could be read as a formula by Sheets/Excel (review fix
    2026-10-06). `columns` defaults to the undantag sheet's `COLUMNS`; pass
    `SPOT_CHECK_COLUMNS` for the stickprov sheet. `extrasaction="ignore"` lets `_row()`
    stay a single shared row-builder even though `Publicerad` is only used by one sheet."""
    cols = COLUMNS if columns is None else columns
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=cols, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow({key: _escape_formula(value) for key, value in row.items()})


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
    existing_rows: list[dict[str, str]] = []
    if path.exists():
        with path.open(encoding="utf-8-sig", newline="") as f:
            existing_rows = list(csv.DictReader(f))
    write_sheet(path, [*existing_rows, *rows], columns=SPOT_CHECK_COLUMNS)
    for record in drawn_list:
        record["verification"]["spotChecked"] = True
        save_record(record_path(paths.data_out, str(record["qid"])), record)
    return SpotCheckResult(path=path, species=[str(r["qid"]) for r in drawn_list], seed=used_seed)
