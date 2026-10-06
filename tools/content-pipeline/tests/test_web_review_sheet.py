"""Tests for web/review_sheet.py: the exception sheet out and Albin's decisions in."""

from __future__ import annotations

import copy
import csv
import io
from pathlib import Path

import pytest

from birdy_fetcher.web.record import (
    Record,
    audio_id,
    facts_hash,
    load_record,
    new_record,
    record_path,
    save_record,
)
from birdy_fetcher.web.review_sheet import (
    CHANGE,
    COLUMNS,
    KEEP,
    SPOT_CHECK_COLUMNS,
    STRIKE,
    ReviewImportError,
    apply_review,
    export_spot_check,
    export_wave,
    flag_rows,
    full_sheet_rows,
    import_wave,
    mark_drawn,
    read_sheet,
    read_spot_check_state,
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
    # The flags come from a `web verify` run on these facts (I2: the import checks it).
    record["generated"] = {"verify": {"model": "claude-sonnet-5", "factsHash": facts_hash(record)}}
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
    # I1 (final review 2026-10-06): no decision is pre-filled; an import before Albin has
    # looked must not count the species as checked.
    assert first["Beslut"] == ""
    assert rows[4]["Beslut"] == ""
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


def test_publishing_one_at_a_time_draws_two_per_forty(tmp_path: Path) -> None:
    """C2 (final review 2026-10-06): the old trigger counted every published-but-undrawn
    species, so after the first 40 every two publications drew again (142 of 180). Run the
    way fas 2's loop runs it, after each publication: 81 publications give two draws."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for i in range(1, 82):
        record = _published(f"Q{i}")
        record["publish"] = False
        save_record(record_path(paths.data_out, f"Q{i}"), record)
    drawn: list[str] = []
    for i in range(1, 82):
        loaded = load_record(record_path(paths.data_out, f"Q{i}"))
        assert loaded is not None
        loaded["publish"] = True
        loaded["publishedAt"] = f"2026-11-{1 + i // 10:02d}"
        save_record(record_path(paths.data_out, f"Q{i}"), loaded)
        result = export_spot_check(paths, seed=i, today="2026-11-30")
        if result is not None:
            drawn += result.species
    assert len(drawn) == 4
    state = read_spot_check_state(paths.review / "stickprov-state.json")
    assert [d["id"] for d in state["draws"]] == [1, 2]
    assert state["publishedAtLastDraw"] == 80
    assert [d["seed"] for d in state["draws"]] == [40, 80]


def test_a_late_run_draws_two_per_whole_batch_and_keeps_the_rest(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for i in range(1, 86):
        save_record(record_path(paths.data_out, f"Q{i}"), _published(f"Q{i}"))
    result = export_spot_check(paths, seed=7)
    assert result is not None and len(result.species) == 4
    state = read_spot_check_state(paths.review / "stickprov-state.json")
    assert len(state["counted"]) == 80
    assert state["draws"][0]["batches"] == 2
    # Five left over; 35 more make the next batch.
    assert export_spot_check(paths, seed=7) is None
    for i in range(86, 121):
        save_record(record_path(paths.data_out, f"Q{i}"), _published(f"Q{i}"))
    again = export_spot_check(paths, seed=8)
    assert again is not None and len(again.species) == 2
    assert again.draw == 2


def test_the_seed_of_every_draw_is_saved(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for i in range(1, 41):
        save_record(record_path(paths.data_out, f"Q{i}"), _published(f"Q{i}"))
    result = export_spot_check(paths, today="2026-11-30")
    assert result is not None
    state = read_spot_check_state(paths.review / "stickprov-state.json")
    assert state["draws"] == [
        {
            "id": 1,
            "seed": result.seed,
            "at": "2026-11-30",
            "batches": 1,
            "forced": False,
            "extra": [],
            "species": result.species,
        }
    ]


def test_a_forced_draw_counts_nothing(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for r in (_published(f"Q{i}") for i in range(1, 6)):
        save_record(record_path(paths.data_out, r["qid"]), r)
    result = export_spot_check(paths, seed=1, force=True)
    assert result is not None and len(result.species) == 2
    state = read_spot_check_state(paths.review / "stickprov-state.json")
    assert state["counted"] == []
    assert state["draws"][0]["forced"] is True


def test_drawn_rows_carry_their_draw_and_no_decision(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for r in (_published(f"Q{i}") for i in range(1, 6)):
        save_record(record_path(paths.data_out, r["qid"]), r)
    result = export_spot_check(paths, seed=1, force=True)
    assert result is not None
    rows = read_sheet(paths.review / "stickprov.csv")
    assert {r["Dragning"] for r in rows} == {"1"}
    assert {r["Beslut"] for r in rows if r["Typ"] != "data"} == {""}
    record = load_record(record_path(paths.data_out, result.species[0]))
    assert record is not None
    assert record["review"]["spotCheck"] == {
        "draw": 1,
        "factsHash": facts_hash(record),
        "audio": audio_id(record["audio"]),
    }


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


def test_spot_check_keeps_the_art_column_when_the_old_sheet_has_a_double_bom(
    tmp_path: Path,
) -> None:
    """Review fix 2026-10-06: a plain `encoding="utf-8-sig"` `csv.DictReader` only strips
    ONE BOM, so a Sheets re-export with two BOMs left "Art" glued to the leftover one;
    `write_sheet`'s `extrasaction="ignore"` then silently emptied every existing row's Art
    column (the dict key "\ufeffArt" is missing from `fieldnames`, so `DictWriter` fills
    the real "Art" column with its default `restval`, "")."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    for r in (_published(f"Q{i}") for i in range(1, 6)):
        save_record(record_path(paths.data_out, r["qid"]), r)
    path = paths.review / "stickprov.csv"
    path.parent.mkdir(parents=True, exist_ok=True)
    buf = io.StringIO()
    writer = csv.DictWriter(buf, fieldnames=SPOT_CHECK_COLUMNS)
    writer.writeheader()
    writer.writerow({"Art": "Blåmes", "QID": "Q25404"})
    path.write_bytes("\ufeff\ufeff".encode() + buf.getvalue().encode("utf-8"))
    result = export_spot_check(paths, seed=1, force=True)
    assert result is not None
    rows = read_sheet(path)
    assert rows[0]["Art"] == "Blåmes"


# -- Task 17: Albin's decisions in, `web import` ------------------------------------------


def _decide(
    rows: list[dict[str, str]], fill: str = KEEP, **changes: tuple[str, str]
) -> list[dict[str, str]]:
    """changes: Id -> (Beslut, Faktum), for faktum/status/inspelning rows; every other such
    row gets `fill` (behåll by default, "" to leave it undecided)."""
    out = []
    for row in rows:
        row = dict(row)
        if row["Typ"] in ("data", "flagga"):
            out.append(row)
            continue
        if row["Id"] in changes:
            row["Beslut"], new_text = changes[row["Id"]]
            if new_text:
                row["Faktum"] = new_text
        else:
            row["Beslut"] = fill
        out.append(row)
    return out


def _drawn(qid: str = "Q25485", draw: int = 1) -> Record:
    """A published, verified species drawn for the spot check in draw `draw`."""
    record = _published(qid)
    mark_drawn(record, draw)
    return record


def _flag_decisions(rows: list[dict[str, str]], **decisions: str) -> list[dict[str, str]]:
    """decisions: fact id (or "" for a V4 flag) -> Beslut, for flagga rows."""
    out = []
    for row in rows:
        row = dict(row)
        if row["Typ"] == "flagga" and row["Id"] in decisions:
            row["Beslut"] = decisions[row["Id"]]
        out.append(row)
    return out


def _decide_flag(rows: list[dict[str, str]], **changes: tuple[str, str]) -> list[dict[str, str]]:
    """changes: fact id -> (Beslut, Faktum), for flagga rows -- the V1 ändra case, where the
    replacement status label goes in the Faktum column like it does for a faktum/status row
    (`_decide` above explicitly skips flagga rows, so a separate helper is needed here)."""
    out = []
    for row in rows:
        row = dict(row)
        if row["Typ"] == "flagga" and row["Id"] in changes:
            row["Beslut"], row["Faktum"] = changes[row["Id"]]
        out.append(row)
    return out


def test_a_flagged_species_only_needs_its_flags_decided() -> None:
    record = _flagged()
    rows = _flag_decisions(flag_rows(record), s01=KEEP)
    result = apply_review({"Q25485": record}, rows, date="2026-11-20")
    assert result.changed == ["Q25485"]
    assert any(f["id"] == "s01" for f in record["facts"])
    assert record["review"]["statusConfirmed"] is True
    assert record["verification"] == {
        "method": "auto",
        "at": "2026-11-20",
        "model": "claude-sonnet-5",
        "spotChecked": False,
    }


def test_striking_a_v3_flag_removes_the_status() -> None:
    record = _flagged()
    rows = _flag_decisions(flag_rows(record), s01=STRIKE)
    apply_review({"Q25485": record}, rows, date="2026-11-20")
    assert all(f["id"] != "s01" for f in record["facts"])
    assert record["review"]["statusConfirmed"] is False


def test_striking_a_v4_flag_removes_the_recording() -> None:
    record = _record()
    record["flags"] = [{"check": "V4", "factId": None, "message": "ljudmodellen täcker inte arten"}]
    rows = _flag_decisions(flag_rows(record), **{"": STRIKE})
    result = apply_review({"Q25485": record}, rows, date="2026-11-20")
    assert "audio" not in record
    assert record["review"]["audioStruck"] is True
    assert result.removed_audio == ["Q25485"]


def test_spot_checked_species_keep_strike_and_change_like_before() -> None:
    record = _drawn()
    rows = _decide(
        full_sheet_rows(record, draw=1),
        f01=(CHANGE, "Svart huvud och vita kinder."),
        f02=(STRIKE, ""),
    )
    result = apply_review({"Q25485": record}, rows, date="2026-12-01")
    assert result.changed == ["Q25485"]
    ids = [f["id"] for f in record["facts"]]
    assert ids == ["f01", "s01", "d01"]
    assert record["facts"][0]["sv"] == "Svart huvud och vita kinder."
    assert record["facts"][0]["edited"] is True
    assert record["verification"]["spotChecked"] is True
    assert record["verification"]["at"] == "2026-12-01"
    assert record["review"]["spotCheck"]["decidedAt"] == "2026-12-01"


def test_a_spot_check_without_decisions_waits_and_changes_nothing() -> None:
    """I1: the rows are written with an empty Beslut; importing before Albin has decided
    must neither mark the species checked nor re-date it."""
    record = _drawn()
    before = copy.deepcopy(record)
    rows = _decide(full_sheet_rows(record, draw=1), fill="", f01=(KEEP, ""))
    result = apply_review({"Q25485": record}, rows, date="2026-12-01")
    assert result.changed == []
    assert result.waiting and "Q25485" in result.waiting[0]
    assert record == before


def test_an_all_keep_spot_check_is_decided_without_a_new_date() -> None:
    """Nothing changed, so the page keeps its "Kontrollerad mot källorna" date and needs no
    republishing; the draw is marked decided so a re-import does not apply it again."""
    record = _drawn()
    rows = _decide(full_sheet_rows(record, draw=1))
    result = apply_review({"Q25485": record}, rows, date="2026-12-01")
    assert result.changed == ["Q25485"]
    assert record["verification"]["at"] == "2026-11-20"
    assert record["review"]["spotCheck"]["decidedAt"] == "2026-12-01"


def test_reimporting_the_cumulative_sheet_never_redates_an_old_draw() -> None:
    """I1: stickprov.csv keeps every earlier draw. Importing it again (with a later draw's
    rows added) must apply only the open draw and leave the decided one exactly as it was."""
    first, second = _drawn("Q1", draw=1), _drawn("Q2", draw=2)
    first_rows = _decide(full_sheet_rows(first, draw=1), f02=(STRIKE, ""))
    apply_review({"Q1": first}, first_rows, date="2026-12-01")
    assert first["verification"]["at"] == "2026-12-01"
    decided = copy.deepcopy(first)
    sheet = [*first_rows, *_decide(full_sheet_rows(second, draw=2), f01=(STRIKE, ""))]
    result = apply_review({"Q1": first, "Q2": second}, sheet, date="2026-12-15")
    assert result.changed == ["Q2"]
    assert first == decided
    assert any("redan avgjord" in note for note in result.ignored)
    assert second["verification"]["at"] == "2026-12-15"


def test_rows_from_an_older_draw_are_ignored() -> None:
    record = _drawn(draw=1)
    old_rows = _decide(full_sheet_rows(record, draw=1), f02=(STRIKE, ""))
    record["review"]["spotCheck"]["draw"] = 3  # drawn again since (web spot-check --extra)
    result = apply_review({"Q25485": record}, old_rows, date="2026-12-01")
    assert result.changed == []
    assert any("dragning 1" in note for note in result.ignored)
    assert any(f["id"] == "f02" for f in record["facts"])


def test_rows_drawn_from_an_older_fact_sheet_are_not_applied() -> None:
    record = _drawn()
    rows = _decide(full_sheet_rows(record, draw=1), f02=(STRIKE, ""))
    record["facts"][0]["sv"] = "Ett nytt faktum."
    result = apply_review({"Q25485": record}, rows, date="2026-12-01")
    assert result.changed == []
    assert result.waiting and "--extra Q25485" in result.waiting[0]
    assert any(f["id"] == "f02" for f in record["facts"])


def test_a_spot_check_never_verifies_a_species_with_open_flags() -> None:
    record = _drawn()
    rows = _decide(full_sheet_rows(record, draw=1), f02=(STRIKE, ""))
    record.pop("verification")
    record["flags"] = [{"check": "V2", "factId": "f01", "message": "x"}]
    result = apply_review({"Q25485": record}, rows, date="2026-12-01")
    assert result.changed == []
    assert "verification" not in record
    assert result.waiting and "undantagsarket" in result.waiting[0]


def test_striking_the_recording_in_the_spot_check_removes_it() -> None:
    """I1: the `inspelning` row was never read; a stryk on it left the recording live."""
    record = _drawn()
    rows = _decide(full_sheet_rows(record, draw=1), a01=(STRIKE, ""))
    result = apply_review({"Q25485": record}, rows, date="2026-12-01")
    assert "audio" not in record
    assert record["review"]["audioStruck"] is True
    assert result.removed_audio == ["Q25485"]
    assert record["verification"]["at"] == "2026-12-01"


def test_changing_the_recording_is_an_error() -> None:
    record = _drawn()
    rows = _decide(full_sheet_rows(record, draw=1), a01=(CHANGE, "annan fil"))
    with pytest.raises(ReviewImportError, match="inspelning"):
        apply_review({"Q25485": record}, rows, date="2026-12-01")


def test_a_spot_check_row_without_its_draw_is_an_error() -> None:
    record = _drawn()
    rows = [{**r, "Dragning": ""} for r in _decide(full_sheet_rows(record, draw=1))]
    with pytest.raises(ReviewImportError, match="Dragning"):
        apply_review({"Q25485": record}, rows, date="2026-12-01")


def test_a_status_changed_in_the_spot_check_is_rechecked_against_the_data() -> None:
    """Minor 9: an `ändra` on s01 in the spot check recomputes the signal and runs V3 on
    the new status, like the exception sheet's V1 `ändra`; a contradiction holds the
    species back (and `web import` lists the published page)."""
    record = _drawn()
    record["review"]["statusConfirmed"] = True
    record["data"] = {
        "months": [0, *([50] * 11)],
        "totalReports": 1000,
        "statusSignal": {"contradicts": None},
    }
    record["facts"] = [
        {**f, "value": "breeding_migrant", "sv": "Flyttfågel, häckar här"}
        if f["id"] == "s01"
        else f
        for f in record["facts"]
    ]
    mark_drawn(record, 1)
    rows = _decide(full_sheet_rows(record, draw=1), s01=(CHANGE, "Stannfågel"))
    apply_review({"Q25485": record}, rows, date="2026-12-01")
    assert "statusConfirmed" not in record["review"]
    assert record["data"]["statusSignal"]["contradicts"] is not None
    assert [f["check"] for f in record["flags"]] == ["V3"]
    assert "verification" not in record


def test_errors_stop_the_whole_import() -> None:
    record = _drawn()
    other = _flagged("Q2")
    before = copy.deepcopy([record, other])
    rows = [r for r in _decide(full_sheet_rows(record, draw=1)) if r["Id"] != "f02"]
    rows = [{**r, "Beslut": "kanske"} if r["Id"] == "f01" else r for r in rows]
    # A valid decision on another species is not applied either: nothing changes.
    rows += _flag_decisions(flag_rows(other), s01=KEEP)
    with pytest.raises(ReviewImportError) as error:
        apply_review({"Q25485": record, "Q2": other}, rows, date="2026-11-20")
    message = str(error.value)
    assert "f02" in message
    assert "kanske" in message
    assert [record, other] == before


def test_a_flag_without_a_decision_waits_and_the_rest_is_imported() -> None:
    """I2 (final review 2026-10-06): partial imports. A species with an undecided flag is
    left exactly as it was and listed as waiting; the other species' decisions apply."""
    waiting, decided = _flagged("Q1"), _flagged("Q2")
    before = copy.deepcopy(waiting)
    rows = [*flag_rows(waiting), *_flag_decisions(flag_rows(decided), s01=KEEP)]
    result = apply_review({"Q1": waiting, "Q2": decided}, rows, date="2026-11-20")
    assert result.changed == ["Q2"]
    assert waiting == before
    assert result.waiting and result.waiting[0].startswith("Q1")
    assert decided["verification"]["at"] == "2026-11-20"


def test_reimporting_a_decided_sheet_changes_nothing() -> None:
    """I2: the exception sheet in Drive is a running sheet; importing it again must not
    re-date `verification.at` or reset `spotChecked` on a species already decided."""
    record = _flagged()
    rows = _flag_decisions(flag_rows(record), s01=KEEP)
    apply_review({"Q25485": record}, rows, date="2026-11-20")
    record["verification"]["spotChecked"] = True
    decided = copy.deepcopy(record)
    result = apply_review({"Q25485": record}, rows, date="2026-12-24")
    assert result.changed == []
    assert record == decided
    assert any("redan kontrollerad" in note for note in result.ignored)


def test_a_row_for_a_renumbered_fact_never_strikes_the_new_one() -> None:
    """I2: after a re-extraction, `f01` is another fact. The old row's quote no longer
    matches, so its `stryk` is not applied to the new fact; the new flag waits."""
    record = _record()
    record["flags"] = [{"check": "V2", "factId": "f01", "message": "f01 anger ett tal i cm"}]
    old_rows = _flag_decisions(flag_rows(record), f01=STRIKE)
    record["facts"][0] = {
        "id": "f01",
        "topic": "appearance",
        "sv": "Ett annat faktum med ett annat citat.",
        "sources": [{"article": "sv", "quote": "ett helt annat citat ur artikeln"}],
    }
    record["generated"]["verify"]["factsHash"] = facts_hash(record)
    result = apply_review({"Q25485": record}, old_rows, date="2026-11-20")
    assert result.changed == []
    assert record["facts"][0]["sv"] == "Ett annat faktum med ett annat citat."
    assert any("inaktuell" in note for note in result.ignored)
    assert result.waiting


def test_rows_for_flags_from_an_older_fact_sheet_are_not_applied() -> None:
    """I2: the facts changed after `web verify` wrote the flags (its hash no longer
    matches): nothing is decided until verify has run on the facts as they are now."""
    record = _flagged()
    rows = _flag_decisions(flag_rows(record), s01=KEEP)
    record["facts"].pop(0)
    result = apply_review({"Q25485": record}, rows, date="2026-11-20")
    assert result.changed == []
    assert "verification" not in record
    assert result.waiting and "web verify" in result.waiting[0]


def test_a_stale_v1_row_never_touches_a_published_species() -> None:
    """I2: a leftover V1 s01 `ändra` must not pop the verification of a live page (fas
    2's zod rejects `publish` without `verification`, failing the whole build)."""
    record = _v1_flagged()
    rows = _decide_flag(flag_rows(record), s01=(CHANGE, "Stannfågel"))
    record["publish"] = True
    record["verification"] = {
        "method": "auto",
        "at": "2026-11-01",
        "model": "claude-sonnet-5",
        "spotChecked": False,
    }
    record["data"] = {"months": [0, *([50] * 11)], "totalReports": 1000}
    before = copy.deepcopy(record)
    result = apply_review({"Q25485": record}, rows, date="2026-11-20")
    assert result.changed == []
    assert record == before


def test_two_different_decisions_on_one_flag_stop_the_import() -> None:
    record = _flagged()
    rows = _flag_decisions(flag_rows(record), s01=KEEP)
    rows.append({**rows[0], "Beslut": STRIKE})
    with pytest.raises(ReviewImportError, match="olika beslut"):
        apply_review({"Q25485": record}, rows, date="2026-11-20")


def test_export_skips_flags_from_an_older_fact_sheet(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    current, stale = _flagged("Q1"), _flagged("Q2")
    stale["generated"]["verify"]["factsHash"] = "an-older-sheet"
    save_record(record_path(paths.data_out, "Q1"), current)
    save_record(record_path(paths.data_out, "Q2"), stale)
    assert export_wave(paths).flagged == ["Q1"]


def test_an_invalid_status_label_stops_the_import() -> None:
    """Requirement 5: 'en status som inte är en av de sex etiketterna' must stop the whole
    import, not just silently keep the old status. The status fact's row has Typ "faktum"
    (bilaga E), not "faktum"/"status" split by row -- the check must key off the fact's own
    topic, not the row's Typ, or this validation would never fire."""
    record = _drawn()
    rows = _decide(full_sheet_rows(record, draw=1), s01=(CHANGE, "kanske"))
    with pytest.raises(ReviewImportError) as error:
        apply_review({"Q25485": record}, rows, date="2026-11-20")
    message = str(error.value)
    assert "s01" in message
    assert "Stannfågel" in message  # one of the six valid labels is listed in the error


def test_changing_the_status_via_andra_updates_value_and_sv() -> None:
    record = _drawn()
    rows = _decide(full_sheet_rows(record, draw=1), s01=(CHANGE, "Flyttfågel, häckar här"))
    apply_review({"Q25485": record}, rows, date="2026-11-20")
    status = next(f for f in record["facts"] if f["id"] == "s01")
    assert status["value"] == "breeding_migrant"
    assert status["sv"] == "Flyttfågel, häckar här"
    assert status["edited"] is True


def test_apply_review_refreshes_facts_hash_so_verify_does_not_rerun() -> None:
    """Requirement 4: once Albin's edit is in, `generated.verify.factsHash` must match the
    new facts so a later `web verify` run treats the species as already current instead of
    re-running V1 on text Albin just hand-corrected."""
    record = _drawn()
    record["generated"]["verify"]["factsHash"] = "stale-hash-from-before-the-edit"
    rows = _decide(full_sheet_rows(record, draw=1), f01=(CHANGE, "Svart huvud och vita kinder."))
    apply_review({"Q25485": record}, rows, date="2026-11-20")
    assert record["generated"]["verify"]["factsHash"] == facts_hash(record)


def test_read_sheet_accepts_a_bom(tmp_path: Path) -> None:
    path = tmp_path / "wave-1.csv"
    write_sheet(path, full_sheet_rows(_record()))
    path.write_bytes(b"\xef\xbb\xbf" + path.read_bytes())
    assert read_sheet(path)[0]["Art"] == "Talgoxe"


def test_read_sheet_unescapes_formula_prefixed_values_written_by_write_sheet(
    tmp_path: Path,
) -> None:
    """Requirement 2, round trip: write_sheet's leading apostrophe (so Sheets/Excel never
    evaluates "-5 cm" or "=1+1" as a formula) must come back off on import."""
    record = _record()
    record["flags"] = [
        {"check": "V2", "factId": "f01", "message": "=1+1"},
        {"check": "V3", "factId": "s01", "message": "-5 cm"},
    ]
    path = tmp_path / "undantag.csv"
    write_sheet(path, flag_rows(record))
    rows = read_sheet(path)
    assert rows[0]["Faktum"] == "=1+1"
    assert rows[1]["Faktum"] == "-5 cm"


def test_import_wave_also_verifies_species_without_a_sheet_row(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    flagged, clean = _flagged("Q1"), _record("Q2")
    save_record(record_path(paths.data_out, "Q1"), flagged)
    save_record(record_path(paths.data_out, "Q2"), clean)
    sheet = tmp_path / "wave-1.csv"
    write_sheet(sheet, _flag_decisions(flag_rows(flagged), s01=KEEP))
    result = import_wave(paths, sheet, wave=1, date="2026-11-20")
    assert set(result.changed) == {"Q1", "Q2"}
    cleared = load_record(record_path(paths.data_out, "Q2"))
    assert cleared is not None
    assert cleared["verification"]["spotChecked"] is False


def test_import_without_a_wave_only_touches_the_sheets_species(tmp_path: Path) -> None:
    """Ändrat 2026-10-05 (b): samma kommando importerar stickprovet efter publicering,
    utan --wave och utan att röra arter som inte stod i arket."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    spot_checked = _drawn("Q1")
    untouched = _record("Q2")
    save_record(record_path(paths.data_out, "Q1"), spot_checked)
    save_record(record_path(paths.data_out, "Q2"), untouched)
    sheet = tmp_path / "stickprov.csv"
    rows = _decide(
        full_sheet_rows(spot_checked, draw=1), f01=(CHANGE, "Svart huvud och vita kinder.")
    )
    write_sheet(sheet, rows, columns=SPOT_CHECK_COLUMNS)
    result = import_wave(paths, sheet, date="2026-12-01")
    assert result.changed == ["Q1"]
    fixed = load_record(record_path(paths.data_out, "Q1"))
    assert fixed is not None
    assert fixed["verification"]["at"] == "2026-12-01"
    assert fixed["facts"][0]["sv"] == "Svart huvud och vita kinder."


def test_import_wave_removes_the_struck_audio_file(tmp_path: Path) -> None:
    """The V4-strike branch pops `record["audio"]`; `import_wave` must also delete the
    orphaned voice.mp3 so a struck recording does not linger as a dead file on disk."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _record("Q1")
    record["flags"] = [{"check": "V4", "factId": None, "message": "inget konfident träff"}]
    save_record(record_path(paths.data_out, "Q1"), record)
    voice = paths.images_out / "Q1" / "voice.mp3"
    voice.parent.mkdir(parents=True, exist_ok=True)
    voice.write_bytes(b"fake-mp3")
    sheet = tmp_path / "undantag.csv"
    write_sheet(sheet, _flag_decisions(flag_rows(record), **{"": STRIKE}))
    result = import_wave(paths, sheet, date="2026-11-20")
    assert result.removed_audio == ["Q1"]
    assert not voice.exists()


def test_import_wave_deletes_a_recording_struck_in_the_spot_check(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _drawn("Q1")
    save_record(record_path(paths.data_out, "Q1"), record)
    voice = paths.images_out / "Q1" / "voice.mp3"
    voice.parent.mkdir(parents=True, exist_ok=True)
    voice.write_bytes(b"fake-mp3")
    sheet = tmp_path / "stickprov.csv"
    write_sheet(
        sheet,
        _decide(full_sheet_rows(record, draw=1), a01=(STRIKE, "")),
        columns=SPOT_CHECK_COLUMNS,
    )
    result = import_wave(paths, sheet, date="2026-12-01")
    assert result.removed_audio == ["Q1"]
    assert not voice.exists()


def _v1_flagged(qid: str = "Q25485") -> Record:
    """A species whose status fact (s01) the fact checker (V1) struck: `strike_unsupported`
    already dropped it from `record["facts"]` before the flag was written, so the flag row
    is all that is left pointing at it."""
    record = _record(qid)
    record["facts"] = [f for f in record["facts"] if f["id"] != "s01"]
    record["flags"] = [
        {
            "check": "V1",
            "factId": "s01",
            "message": (
                "Statusen i Sverige ströks av faktakontrollen: skäl. Bestäm status eller lämna tom."
            ),
        }
    ]
    record["generated"]["verify"]["factsHash"] = facts_hash(record)
    return record


def test_v1_flag_andra_recreates_the_status_fact_and_verifies() -> None:
    record = _v1_flagged()
    rows = _decide_flag(flag_rows(record), s01=(CHANGE, "Flyttfågel, häckar här"))
    result = apply_review({"Q25485": record}, rows, date="2026-11-20")
    assert result.changed == ["Q25485"]
    status = next(f for f in record["facts"] if f["id"] == "s01")
    assert status == {
        "id": "s01",
        "topic": "status",
        "value": "breeding_migrant",
        "sv": "Flyttfågel, häckar här",
        "sources": [],
        "edited": True,
    }
    assert record["verification"] is not None


def test_v1_flag_andra_with_a_data_contradiction_adds_a_v3_flag_and_withholds_verification() -> (
    None
):
    record = _v1_flagged()
    record["data"] = {"months": [0, *([50] * 11)], "totalReports": 1000}
    rows = _decide_flag(flag_rows(record), s01=(CHANGE, "Stannfågel"))
    result = apply_review({"Q25485": record}, rows, date="2026-11-20")
    assert result.changed == ["Q25485"]
    assert "verification" not in record
    assert record["flags"] == [
        {
            "check": "V3",
            "factId": "s01",
            "message": (
                "Statusen säger stannfågel, men arten rapporteras nästan aldrig i januari."
            ),
        }
    ]


def test_flag_rows_of_a_verified_species_are_ignored() -> None:
    """Was N3 (review fix 2026-10-06), "a new flag clears a stale verification". Since I2
    (final review 2026-10-06) a species that already has `verification` is decided: its
    flag rows are ignored, so no leftover row can give it a new flag or drop its
    verification."""
    record = _v1_flagged()
    record["verification"] = {
        "method": "auto",
        "at": "2026-11-01",
        "model": "claude-sonnet-5",
        "spotChecked": False,
    }
    record["data"] = {"months": [0, *([50] * 11)], "totalReports": 1000}
    rows = _decide_flag(flag_rows(record), s01=(CHANGE, "Stannfågel"))
    result = apply_review({"Q25485": record}, rows, date="2026-11-20")
    assert result.changed == []
    assert record["verification"]["at"] == "2026-11-01"
    assert all(f["id"] != "s01" for f in record["facts"])


def test_v1_flag_andra_with_an_invalid_label_stops_the_import() -> None:
    record = _v1_flagged()
    rows = _decide_flag(flag_rows(record), s01=(CHANGE, "kanske"))
    with pytest.raises(ReviewImportError) as error:
        apply_review({"Q25485": record}, rows, date="2026-11-20")
    message = str(error.value)
    assert "s01" in message
    assert "Stannfågel" in message  # one of the six valid labels is listed in the error
    assert all(f["id"] != "s01" for f in record["facts"])
    assert "verification" not in record


def test_v1_flag_behall_leaves_the_status_empty_and_verifies() -> None:
    record = _v1_flagged()
    rows = _flag_decisions(flag_rows(record), s01=KEEP)
    result = apply_review({"Q25485": record}, rows, date="2026-11-20")
    assert result.changed == ["Q25485"]
    assert all(f["id"] != "s01" for f in record["facts"])
    assert record["verification"] is not None


def test_import_rejects_an_unknown_species(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _flagged("Q1")
    save_record(record_path(paths.data_out, "Q1"), record)
    rows = flag_rows(record)
    rows.append({**rows[0], "QID": "Q999", "Beslut": KEEP})
    sheet = tmp_path / "undantag.csv"
    write_sheet(sheet, _flag_decisions(rows, s01=KEEP))
    with pytest.raises(ReviewImportError, match="Q999"):
        import_wave(paths, sheet, date="2026-11-20")


def test_a_semicolon_separated_export_is_a_clear_import_error(tmp_path: Path) -> None:
    """Minor 8 (final review 2026-10-06): a sheet saved with `;` folds the header into one
    column; the import must say so, not crash with a KeyError traceback."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _flagged("Q1")
    save_record(record_path(paths.data_out, "Q1"), record)
    rows = _flag_decisions(flag_rows(record), s01=KEEP)
    sheet = tmp_path / "undantag.csv"
    buffer = io.StringIO()
    writer = csv.DictWriter(buffer, fieldnames=COLUMNS, delimiter=";", extrasaction="ignore")
    writer.writeheader()
    writer.writerows(rows)
    sheet.write_text(buffer.getvalue(), encoding="utf-8")
    with pytest.raises(ReviewImportError, match="semikolon"):
        import_wave(paths, sheet, date="2026-11-20")


def test_keeping_a_v4_flag_remembers_that_recording() -> None:
    """Follow-up 2 (wave A review): a forced re-verify must not bring back a V4 flag Albin
    already kept; the decision is stored against this exact recording."""
    record = _record()
    record["flags"] = [{"check": "V4", "factId": None, "message": "ljudmodellen täcker inte arten"}]
    rows = _flag_decisions(flag_rows(record), **{"": KEEP})
    apply_review({"Q25485": record}, rows, date="2026-11-20")
    assert record["review"]["audioKept"] == audio_id(record["audio"])
    assert record["verification"] is not None


def test_a_status_set_with_andra_is_rechecked_even_after_an_older_confirmation() -> None:
    """`status_flags` now honours `statusConfirmed`; a confirmation of an earlier status
    must not wave through a new one Albin just set (it has never been checked)."""
    record = _v1_flagged()
    record["review"]["statusConfirmed"] = True
    record["data"] = {"months": [0, *([50] * 11)], "totalReports": 1000}
    rows = _decide_flag(flag_rows(record), s01=(CHANGE, "Stannfågel"))
    apply_review({"Q25485": record}, rows, date="2026-11-20")
    assert "statusConfirmed" not in record["review"]
    assert [f["check"] for f in record["flags"]] == ["V3"]
    assert "verification" not in record


# I8 (final review 2026-10-06): `web import` says which published pages need republishing.


def _with_text(record: Record) -> Record:
    """A published page whose text was written from the facts as they are now, with every
    required topic."""
    ids = {f["id"] for f in record["facts"]}
    for fid, topic, text in (("f08", "voice", "Sjunger ti-ta."), ("f09", "habitat", "I skog.")):
        if fid not in ids:
            record["facts"].insert(0, {"id": fid, "topic": topic, "sv": text, "sources": []})
    record["status"] = "ok"
    record["text"] = {"sv": {}, "en": {}}
    record["generated"]["text"] = {"factsHash": facts_hash(record)}
    record["generated"]["verify"]["factsHash"] = facts_hash(record)
    return record


def test_import_lists_every_published_page_that_needs_rewriting(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _with_text(_drawn("Q1"))
    mark_drawn(record, 1)
    save_record(record_path(paths.data_out, "Q1"), record)
    sheet = tmp_path / "stickprov.csv"
    write_sheet(
        sheet,
        _decide(full_sheet_rows(record, draw=1), f02=(STRIKE, "")),
        columns=SPOT_CHECK_COLUMNS,
    )
    result = import_wave(paths, sheet, date="2026-12-01")
    assert [r.qid for r in result.republish] == ["Q1"]
    item = result.republish[0]
    assert any("texten" in reason for reason in item.reasons)
    assert item.commands == [
        "uv run birdy-fetcher web write --species Q1 --max-cost 2",
        "uv run birdy-fetcher web compare --max-cost 5",
        "uv run birdy-fetcher web publish --species Q1",
    ]


def test_a_published_page_held_back_by_a_new_flag_is_told_to_unpublish_first(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _with_text(_drawn("Q1"))
    record["data"] = {"months": [0, *([50] * 11)], "totalReports": 1000}
    record["facts"] = [
        {**f, "value": "breeding_migrant", "sv": "Flyttfågel, häckar här"}
        if f["id"] == "s01"
        else f
        for f in record["facts"]
    ]
    record = _with_text(record)
    mark_drawn(record, 1)
    save_record(record_path(paths.data_out, "Q1"), record)
    sheet = tmp_path / "stickprov.csv"
    write_sheet(
        sheet,
        _decide(full_sheet_rows(record, draw=1), s01=(CHANGE, "Stannfågel")),
        columns=SPOT_CHECK_COLUMNS,
    )
    result = import_wave(paths, sheet, date="2026-12-01")
    item = result.republish[0]
    assert "publish" in item.commands[0] and "false" in item.commands[0]
    assert any("web sheet" in c for c in item.commands)


def test_a_changed_page_that_is_still_ready_is_listed_for_a_rebuild(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _with_text(_drawn("Q1"))
    mark_drawn(record, 1)
    save_record(record_path(paths.data_out, "Q1"), record)
    sheet = tmp_path / "stickprov.csv"
    write_sheet(
        sheet,
        _decide(full_sheet_rows(record, draw=1), a01=(STRIKE, "")),
        columns=SPOT_CHECK_COLUMNS,
    )
    result = import_wave(paths, sheet, date="2026-12-01")
    assert result.republish == []
    assert result.changed_published == ["Q1"]
