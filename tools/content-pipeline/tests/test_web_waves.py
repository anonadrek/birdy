"""Tests for web/waves.py: the wave lists and how they are applied."""

from __future__ import annotations

import json
from pathlib import Path

from birdy_fetcher.web.record import Record, load_record, new_record, record_path, save_record
from birdy_fetcher.web.waves import compute_waves, run_waves

from .web_repo import make_repo

RESIDENT = [70, 65, 60, 55, 70, 79, 64, 68, 74, 100, 66, 69]
MIGRANT = [0, 0, 5, 60, 100, 90, 85, 70, 40, 5, 0, 0]


def _record(qid: str, name: str, months: list[int] | None, winter: int) -> Record:
    record = new_record(qid)
    record["names"] = {"sv": name}
    if months is not None:
        record["data"] = {"months": months, "raw": {"speciesByMonth": [winter] + [0] * 11}}
    return record


def test_common_species_go_first_even_when_they_migrate() -> None:
    records = {
        "Q1": _record("Q1", "Trana", MIGRANT, 1),
        "Q2": _record("Q2", "Talgoxe", RESIDENT, 900),
        "Q3": _record("Q3", "Blåmes", RESIDENT, 800),
        "Q4": _record("Q4", "Koltrast", RESIDENT, 100),
        "Q5": _record("Q5", "Ladusvala", MIGRANT, 0),
        "Q6": _record("Q6", "Okänd", None, 0),
    }
    waves = compute_waves(records, common=["Q1"], size=3)
    assert waves[1] == ["Q1", "Q2", "Q3"]
    assert waves[3] == ["Q5"]
    assert waves[2] == ["Q4", "Q6"]


def test_run_waves_writes_the_file_once_and_applies_edits(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    rows = (("Q1", "Talgoxe", RESIDENT, 900), ("Q5", "Ladusvala", MIGRANT, 0))
    for qid, name, months, winter in rows:
        save_record(record_path(paths.data_out, qid), _record(qid, name, months, winter))
    waves = run_waves(paths, size=40, recompute=False)
    assert waves[1] == ["Q1"]
    assert waves[3] == ["Q5"]
    file = paths.review / "waves.json"
    data = json.loads(file.read_text(encoding="utf-8"))
    assert data["1"] == [{"qid": "Q1", "name": "Talgoxe"}]
    data["1"].append({"qid": "Q5", "name": "Ladusvala"})
    data["3"] = []
    file.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    run_waves(paths, size=40, recompute=False)
    record = load_record(record_path(paths.data_out, "Q5"))
    assert record is not None
    assert record["review"]["wave"] == 1
