"""The exception sheet (spec 2026-09-25 Revision 2026-10-05, §9.4 and appendix E): a
running CSV, uploaded to Albin's Drive as a Google Sheet, exported back as CSV and
imported. Only what the automatic kontroll (V1 to V4, Task 14e) flagged. The seeded spot
check moved out to `export_spot_check` after publication (Revision 2026-10-05 (b))."""

from __future__ import annotations

import csv
import random
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from .facts import TOPIC_SV
from .paths import WebPaths
from .record import Record, is_reviewed, load_all, record_path, save_record

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
    }


def full_sheet_rows(record: Record) -> list[dict[str, str]]:
    """Every fact, status, data fact and the recording, for a spot-checked species (spec
    point 5: "visade med hela faktabladet")."""
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
                )
            )
            continue
        source, quote = _sources(record, fact["sources"])
        label = TOPIC_SV[topic]
        if topic == "lookalike":
            label = f"förväxling med {fact['other']['scientific']}"
        typ = "status" if topic == "status" else "faktum"
        rows.append(
            _row(
                record,
                rad="stickprov",
                typ=typ,
                fid=fact["id"],
                topic=label,
                fact=fact["sv"],
                source=source,
                quote=quote,
                decision=KEEP,
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


def write_sheet(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)


@dataclass
class ExportResult:
    path: Path
    flagged: list[str] = field(default_factory=list)


def _eligible(records: dict[str, Record], wave: int) -> list[Record]:
    return [
        r
        for r in records.values()
        if r.get("review", {}).get("wave") == wave
        and not is_reviewed(r)
        and any(f["topic"] != "data" for f in r.get("facts", []))
    ]


def export_wave(paths: WebPaths, wave: int) -> ExportResult:
    """Flagged species only (ändrat 2026-10-05 (b): stickprovet flyttat till
    export_spot_check, se nedan). Arket är löpande, inte uppdelat per våg i Drive, men
    `wave` är kvar som ett filter så Albin kan be om bara en körnings flaggor."""
    records = load_all(paths.data_out)
    eligible = _eligible(records, wave)
    flagged = sorted((r for r in eligible if r.get("flags")), key=lambda r: str(r["names"]["sv"]))
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
    samma dragning). Dragna arter får `verification.spotChecked = true` direkt; Albins
    beslut (behåll/stryk/ändra) importeras separat, som för undantagsarkets flaggor."""
    records = load_all(paths.data_out)
    pending = sorted(_unspotchecked_published(records), key=lambda r: str(r["names"]["sv"]))
    used_seed = seed if seed is not None else 2000
    drawn: list[Record] = []
    if force or len(pending) >= SPOT_CHECK_BATCH:
        drawn = random.Random(used_seed).sample(pending, k=min(SPOT_CHECK_DRAW, len(pending)))
    drawn_by_qid = {str(r["qid"]): r for r in drawn}
    for qid in extra_species:
        if qid in records and qid not in drawn_by_qid:
            drawn_by_qid[qid] = records[qid]
    if not drawn_by_qid:
        return None
    drawn_list = sorted(drawn_by_qid.values(), key=lambda r: str(r["names"]["sv"]))
    rows = [row for r in drawn_list for row in full_sheet_rows(r)]
    path = paths.review / "stickprov.csv"
    existing_rows: list[dict[str, str]] = []
    if path.exists():
        with path.open(encoding="utf-8", newline="") as f:
            existing_rows = list(csv.DictReader(f))
    write_sheet(path, [*existing_rows, *rows])
    for record in drawn_list:
        record["verification"]["spotChecked"] = True
        save_record(record_path(paths.data_out, str(record["qid"])), record)
    return SpotCheckResult(path=path, species=[str(r["qid"]) for r in drawn_list], seed=used_seed)
