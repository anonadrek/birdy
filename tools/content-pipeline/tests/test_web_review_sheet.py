"""Tests for web/review_sheet.py: the exception sheet out and Albin's decisions in."""

from __future__ import annotations

import csv
from pathlib import Path

import pytest

from birdy_fetcher.web.record import Record, load_record, new_record, record_path, save_record
from birdy_fetcher.web.review_sheet import (
    COLUMNS,
    SPOT_CHECK_COLUMNS,
    export_spot_check,
    export_wave,
    flag_rows,
    full_sheet_rows,
    write_sheet,
)
from birdy_fetcher.web.waves import write_waves

from .web_repo import make_repo


def _record(qid: str = "Q25485") -> Record:
    record = new_record(qid)
    record["names"] = {"sv": "Talgoxe", "en": "Great Tit", "scientific": "Parus major"}
    record["wikipedia"] = {"sv": {"title": "Talgoxe", "revision": "111"}}
    record["review"] = {"wave": 1}
    record["facts"] = [
        {
            "id": "f01",
            "topic": "appearance",
            "sv": "Svart huvud med vita kinder.",
            "sources": [{"article": "sv", "quote": "svart huvud med vita kinder"}],
        },
        {
            "id": "f02",
            "topic": "lookalike",
            "sv": "Kan förväxlas med blåmesen.",
            "sources": [{"article": "sv", "quote": "kan förväxlas med blåmes"}],
            "other": {"scientific": "Cyanistes caeruleus", "qid": "Q25404"},
        },
        {
            "id": "s01",
            "topic": "status",
            "value": "resident",
            "sv": "Stannfågel",
            "sources": [{"article": "sv", "quote": "Den är stannfågel i hela Sverige"}],
        },
        {"id": "d01", "topic": "data", "source": "artportalen", "sv": "Rapporteras året runt."},
    ]
    record["audio"] = {
        "file": f"{qid}/voice.mp3",
        "durationSec": 20,
        "trimmed": True,
        "author": "Anna",
        "license": "CC BY-SA 4.0",
        "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
        "sourceUrl": "https://commons.wikimedia.org/wiki/File:x.ogg",
    }
    record["flags"] = []
    record["generated"] = {"verify": {"model": "claude-sonnet-5"}}
    return record


def _flagged(qid: str = "Q25485") -> Record:
    record = _record(qid)
    record["flags"] = [
        {"check": "V3", "factId": "s01", "message": "Statusen säger stannfågel, men ..."}
    ]
    return record


def test_full_sheet_rows_cover_facts_status_data_and_recording() -> None:
    """Bilaga E lists only four Typ values (faktum, data, inspelning, flagga) — the status
    fact s01 is a `faktum` too, its Ämne column already says it is about status."""
    rows = full_sheet_rows(_record())
    assert [r["Typ"] for r in rows] == ["faktum", "faktum", "faktum", "data", "inspelning"]
    assert all(r["Rad"] == "stickprov" and r["Kontroll"] == "" for r in rows)
    first = rows[0]
    assert first["Art"] == "Talgoxe"
    assert first["Id"] == "f01"
    assert first["Ämne"] == "utseende"
    assert first["Källa"] == "sv: https://sv.wikipedia.org/w/index.php?oldid=111"
    assert first["Citat"] == "svart huvud med vita kinder"
    assert first["Beslut"] == "behåll"
    assert rows[1]["Ämne"] == "förväxling med Cyanistes caeruleus"
    assert rows[1]["Faktum"] == "Kan förväxlas med blåmesen."
    assert rows[2]["Ämne"] == "status i Sverige"
    assert rows[3]["Beslut"] == "(data)"
    assert rows[4]["Faktum"] == "Anna, CC BY-SA 4.0, 20 s"


def test_full_sheet_rows_fill_publicerad_from_published_at() -> None:
    record = _record()
    record["publishedAt"] = "2026-11-20"
    rows = full_sheet_rows(record)
    assert all(r["Publicerad"] == "2026-11-20" for r in rows)


def test_full_sheet_rows_publicerad_is_empty_when_not_published() -> None:
    rows = full_sheet_rows(_record())
    assert all(r["Publicerad"] == "" for r in rows)


def test_formula_cells_are_neutralised_when_written_to_csv(tmp_path: Path) -> None:
    """CSV-injection mitigation (Important): a cell starting with =, +, -, @, tab or CR
    gets a leading apostrophe once it reaches the CSV, so Sheets/Excel never evaluates it
    as a formula."""
    record = _record()
    record["flags"] = [
        {"check": "V2", "factId": "f01", "message": "=1+1"},
        {"check": "V3", "factId": "s01", "message": "-5 cm"},
    ]
    path = tmp_path / "undantag.csv"
    write_sheet(path, flag_rows(record))
    with path.open(encoding="utf-8-sig", newline="") as f:
        written = list(csv.DictReader(f))
    assert written[0]["Faktum"] == "'=1+1"
    assert written[1]["Faktum"] == "'-5 cm"


def test_flag_rows_cover_v2_v3_and_v4() -> None:
    record = _record()
    record["flags"] = [
        {"check": "V2", "factId": "f01", "message": "f01 anger ett tal som inte stämmer"},
        {"check": "V3", "factId": "s01", "message": "Statusen säger stannfågel, men ..."},
        {"check": "V4", "factId": None, "message": "ljudmodellen hittade inte arten"},
    ]
    rows = flag_rows(record)
    assert [r["Kontroll"] for r in rows] == ["V2", "V3", "V4"]
    assert all(r["Typ"] == "flagga" and r["Rad"] == "flagga" and r["Beslut"] == "" for r in rows)
    assert rows[0]["Id"] == "f01" and rows[0]["Ämne"] == "utseende"
    assert (
        rows[2]["Id"] == "" and rows[2]["Källa"] == "https://commons.wikimedia.org/wiki/File:x.ogg"
    )


def test_write_sheet_has_the_columns(tmp_path: Path) -> None:
    path = tmp_path / "wave-1-ark.csv"
    write_sheet(path, full_sheet_rows(_record()))
    with path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        assert reader.fieldnames == COLUMNS
        assert len(list(reader)) == 5


def test_write_sheet_is_utf8_with_bom_so_excel_shows_aao(tmp_path: Path) -> None:
    path = tmp_path / "wave-1-ark.csv"
    write_sheet(path, full_sheet_rows(_record()))
    raw = path.read_bytes()
    assert raw.startswith(b"\xef\xbb\xbf")
    # A plain utf-8-sig read strips the BOM and still parses the header correctly.
    with path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        assert reader.fieldnames == COLUMNS


def test_spot_check_sheet_has_the_publicerad_column(tmp_path: Path) -> None:
    path = tmp_path / "stickprov.csv"
    write_sheet(path, full_sheet_rows(_record()), columns=SPOT_CHECK_COLUMNS)
    with path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        assert reader.fieldnames == SPOT_CHECK_COLUMNS
        assert "Publicerad" not in COLUMNS


def test_export_wave_picks_only_flagged_species(tmp_path: Path) -> None:
    """Ändrat 2026-10-05 (b): export_wave drar inte längre ett stickprov. Den delen
    flyttade till export_spot_check, efter publicering."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    flagged = _flagged("Q1")
    clean = [_record(f"Q{i}") for i in range(2, 7)]
    for r in (flagged, *clean):
        save_record(record_path(paths.data_out, r["qid"]), r)
    result = export_wave(paths, 1)
    assert result.flagged == ["Q1"]
    assert not hasattr(result, "spot_checked")


def test_export_wave_without_a_filter_is_one_continuous_sheet_in_queue_order(
    tmp_path: Path,
) -> None:
    """2026-10-06 review fix: the sheet is one running export across every wave, not
    overwritten per wave — exporting wave 2 must not drop wave 1's still-open flags.
    Order follows the queue (wave, then each wave's position in waves.json), and a
    species whose flags were already decided (has `verification`) drops out."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    wave1 = _flagged("Q1")
    wave1["review"] = {"wave": 1}
    wave2 = _flagged("Q2")
    wave2["review"] = {"wave": 2}
    decided = _flagged("Q3")
    decided["review"] = {"wave": 1}
    decided["verification"] = {
        "method": "auto",
        "at": "2026-11-20",
        "model": "claude-sonnet-5",
        "spotChecked": False,
    }
    records = {"Q1": wave1, "Q2": wave2, "Q3": decided}
    for r in records.values():
        save_record(record_path(paths.data_out, r["qid"]), r)
    write_waves(paths.review / "waves.json", {1: ["Q3", "Q1"], 2: ["Q2"]}, records)
    result = export_wave(paths)
    assert result.flagged == ["Q1", "Q2"]


def test_export_wave_falls_back_to_the_swedish_name_outside_waves_json(
    tmp_path: Path,
) -> None:
    """A species missing from waves.json (e.g. waves not computed yet for it) still gets
    exported, ordered by its Swedish name rather than crashing on a missing position."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    bortom = _flagged("Q1")
    bortom["names"] = {"sv": "Böjvinge", "en": "x", "scientific": "y"}
    arla = _flagged("Q2")
    arla["names"] = {"sv": "Arla", "en": "x", "scientific": "y"}
    for r in (bortom, arla):
        save_record(record_path(paths.data_out, r["qid"]), r)
    result = export_wave(paths)
    assert result.flagged == ["Q2", "Q1"]


def _published(qid: str) -> Record:
    record = _record(qid)
    record["publish"] = True
    record["verification"] = {
        "method": "auto",
        "at": "2026-11-20",
        "model": "claude-sonnet-5",
        "spotChecked": False,
    }
    return record


def test_spot_check_draws_nothing_below_the_batch_size(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for r in (_published(f"Q{i}") for i in range(1, 40)):  # 39, one short of SPOT_CHECK_BATCH
        save_record(record_path(paths.data_out, r["qid"]), r)
    assert export_spot_check(paths, seed=1) is None


def test_spot_check_draws_two_once_the_batch_is_full(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for r in (_published(f"Q{i}") for i in range(1, 41)):  # exactly SPOT_CHECK_BATCH
        save_record(record_path(paths.data_out, r["qid"]), r)
    result = export_spot_check(paths, seed=1)
    assert result is not None
    assert len(result.species) == 2
    for qid in result.species:
        record = load_record(record_path(paths.data_out, qid))
        assert record is not None and record["verification"]["spotChecked"] is True
    # drawn species don't come up again once the batch has been consumed
    again = export_spot_check(paths, seed=1)
    assert again is None


def test_spot_check_force_draws_regardless_of_batch_size(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for r in (_published(f"Q{i}") for i in range(1, 6)):
        save_record(record_path(paths.data_out, r["qid"]), r)
    result = export_spot_check(paths, seed=1, force=True)
    assert result is not None and len(result.species) == 2


def test_spot_check_extra_species_is_a_redraw_after_a_confirmed_miss(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for r in (_published(f"Q{i}") for i in range(1, 6)):
        save_record(record_path(paths.data_out, r["qid"]), r)
    result = export_spot_check(paths, seed=1, extra_species=("Q5",))
    assert result is not None
    assert "Q5" in result.species


def test_spot_check_extra_species_rejects_an_unknown_qid(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for r in (_published(f"Q{i}") for i in range(1, 6)):
        save_record(record_path(paths.data_out, r["qid"]), r)
    with pytest.raises(ValueError, match="Q999"):
        export_spot_check(paths, seed=1, extra_species=("Q999",))


def test_spot_check_extra_species_rejects_an_unpublished_qid(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for r in (_published(f"Q{i}") for i in range(1, 6)):
        save_record(record_path(paths.data_out, r["qid"]), r)
    unpublished = _record("Q7")
    save_record(record_path(paths.data_out, "Q7"), unpublished)
    with pytest.raises(ValueError, match="Q7"):
        export_spot_check(paths, seed=1, extra_species=("Q7",))


def test_spot_check_extra_species_rejects_an_unverified_qid(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for r in (_published(f"Q{i}") for i in range(1, 6)):
        save_record(record_path(paths.data_out, r["qid"]), r)
    published_but_unverified = _record("Q8")
    published_but_unverified["publish"] = True
    save_record(record_path(paths.data_out, "Q8"), published_but_unverified)
    with pytest.raises(ValueError, match="Q8"):
        export_spot_check(paths, seed=1, extra_species=("Q8",))
