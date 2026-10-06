"""Tests for web/review_sheet.py: the exception sheet out and Albin's decisions in."""

from __future__ import annotations

import csv
from pathlib import Path

import pytest

from birdy_fetcher.web.record import (
    Record,
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
    read_sheet,
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


# -- Task 17: Albin's decisions in, `web import` ------------------------------------------


def _decide(rows: list[dict[str, str]], **changes: tuple[str, str]) -> list[dict[str, str]]:
    """changes: Id -> (Beslut, Faktum), for faktum/status rows."""
    out = []
    for row in rows:
        row = dict(row)
        if row["Id"] in changes and row["Typ"] not in ("data", "flagga"):
            row["Beslut"], new_text = changes[row["Id"]]
            if new_text:
                row["Faktum"] = new_text
        out.append(row)
    return out


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
    record = _record()
    rows = _decide(
        full_sheet_rows(record), f01=(CHANGE, "Svart huvud och vita kinder."), f02=(STRIKE, "")
    )
    result = apply_review({"Q25485": record}, rows, date="2026-11-20")
    assert result.changed == ["Q25485"]
    ids = [f["id"] for f in record["facts"]]
    assert ids == ["f01", "s01", "d01"]
    assert record["facts"][0]["sv"] == "Svart huvud och vita kinder."
    assert record["facts"][0]["edited"] is True
    assert record["verification"]["spotChecked"] is True


def test_errors_stop_the_whole_import() -> None:
    record = _record()
    rows = [r for r in full_sheet_rows(record) if r["Id"] != "f02"]
    rows = [{**r, "Beslut": "kanske"} if r["Id"] == "f01" else r for r in rows]
    with pytest.raises(ReviewImportError) as error:
        apply_review({"Q25485": record}, rows, date="2026-11-20")
    message = str(error.value)
    assert "f02" in message
    assert "kanske" in message
    assert "verification" not in record


def test_a_flag_without_a_decision_stops_the_import() -> None:
    record = _flagged()
    with pytest.raises(ReviewImportError) as error:
        apply_review({"Q25485": record}, flag_rows(record), date="2026-11-20")
    assert "flaggan" in str(error.value)


def test_an_invalid_status_label_stops_the_import() -> None:
    """Requirement 5: 'en status som inte är en av de sex etiketterna' must stop the whole
    import, not just silently keep the old status. The status fact's row has Typ "faktum"
    (bilaga E), not "faktum"/"status" split by row -- the check must key off the fact's own
    topic, not the row's Typ, or this validation would never fire."""
    record = _record()
    rows = _decide(full_sheet_rows(record), s01=(CHANGE, "kanske"))
    with pytest.raises(ReviewImportError) as error:
        apply_review({"Q25485": record}, rows, date="2026-11-20")
    message = str(error.value)
    assert "s01" in message
    assert "Stannfågel" in message  # one of the six valid labels is listed in the error


def test_changing_the_status_via_andra_updates_value_and_sv() -> None:
    record = _record()
    rows = _decide(full_sheet_rows(record), s01=(CHANGE, "Flyttfågel, häckar här"))
    apply_review({"Q25485": record}, rows, date="2026-11-20")
    status = next(f for f in record["facts"] if f["id"] == "s01")
    assert status["value"] == "breeding_migrant"
    assert status["sv"] == "Flyttfågel, häckar här"
    assert status["edited"] is True


def test_apply_review_refreshes_facts_hash_so_verify_does_not_rerun() -> None:
    """Requirement 4: once Albin's edit is in, `generated.verify.factsHash` must match the
    new facts so a later `web verify` run treats the species as already current instead of
    re-running V1 on text Albin just hand-corrected."""
    record = _record()
    record["generated"]["verify"]["factsHash"] = "stale-hash-from-before-the-edit"
    rows = _decide(full_sheet_rows(record), f01=(CHANGE, "Svart huvud och vita kinder."))
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
    spot_checked = _record("Q1")
    spot_checked["publish"] = True
    spot_checked["verification"] = {
        "method": "auto",
        "at": "2026-11-10",
        "model": "claude-sonnet-5",
        "spotChecked": True,
    }
    untouched = _record("Q2")
    save_record(record_path(paths.data_out, "Q1"), spot_checked)
    save_record(record_path(paths.data_out, "Q2"), untouched)
    sheet = tmp_path / "stickprov.csv"
    rows = _decide(full_sheet_rows(spot_checked), f01=(CHANGE, "Svart huvud och vita kinder."))
    write_sheet(sheet, rows)
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
    status = next(f for f in record["facts"] if f["id"] == "s01")
    assert status["value"] == "resident"


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
