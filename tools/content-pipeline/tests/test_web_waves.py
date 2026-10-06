"""Tests for web/waves.py: the wave lists and how they are applied, and (Task 23) the
publishing step."""

from __future__ import annotations

import json
from datetime import date
from pathlib import Path
from typing import Any

from birdy_fetcher.web.record import (
    Record,
    facts_hash,
    load_record,
    new_record,
    record_path,
    save_record,
)
from birdy_fetcher.web.waves import compute_waves, publish_wave, run_waves

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


# -- publishing (Task 23) ----------------------------------------------------------------
#
# `_ready` builds a record that passes every condition of the publish predicate: status
# "ok", a fact sheet with all three required topics plus one extra ("size", never cited
# by `record["text"]`, so a test can freely strike it to isolate the stale-citation
# check), a `verification` whose `generated.verify.factsHash` matches those facts, and a
# `generated.text.factsHash` that matches them too. Tests for each blocking condition
# start from this baseline and break exactly one thing.


def _facts() -> list[dict[str, Any]]:
    return [
        {"id": "f01", "topic": "appearance", "sv": "x", "sources": []},
        {"id": "f02", "topic": "voice", "sv": "x", "sources": []},
        {"id": "f03", "topic": "habitat", "sv": "x", "sources": []},
        {"id": "f04", "topic": "size", "sv": "x", "sources": []},
    ]


def _ready(qid: str, name: str, wave: int) -> Record:
    record = _record(qid, name, RESIDENT, 0)
    record["facts"] = _facts()
    verify_hash = facts_hash(record)
    record["status"] = "ok"
    record["review"] = {"wave": wave}
    record["verification"] = {
        "method": "auto",
        "at": "2026-11-20",
        "model": "claude-sonnet-5",
        "spotChecked": False,
    }
    record["generated"] = {
        "verify": {"factsHash": verify_hash},
        "text": {"factsHash": verify_hash},
    }
    return record


def test_publish_turns_on_ready_species_and_their_comparisons(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    ready = _ready("Q1", "Talgoxe", 1)
    partner = _ready("Q2", "Blåmes", 1)
    unreviewed = _ready("Q3", "Koltrast", 1)
    unreviewed["generated"]["text"]["unreviewed"] = True
    later = _ready("Q4", "Ladusvala", 3)
    failed = _ready("Q5", "Kaja", 1)
    failed["status"] = "failed"
    for record in (ready, partner, unreviewed, later, failed):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    for a, b in (("Q1", "Q2"), ("Q1", "Q4")):
        both = {"Q1": ready, "Q2": partner, "Q4": later}
        save_record(
            paths.comparisons_out / f"{a}_{b}.json",
            {
                "a": a,
                "b": b,
                "status": "ok",
                "publish": False,
                "generated": {"factsHash": facts_hash(both[a]) + facts_hash(both[b])},
            },
        )
    outcomes = publish_wave(paths, wave=1)
    published = {
        qid
        for qid in ("Q1", "Q2", "Q3", "Q4", "Q5")
        if (load_record(record_path(paths.data_out, qid)) or {}).get("publish")
    }
    assert published == {"Q1", "Q2"}
    pair = load_record(paths.comparisons_out / "Q1_Q2.json")
    other = load_record(paths.comparisons_out / "Q1_Q4.json")
    assert pair is not None and pair["publish"] is True
    assert other is not None and other["publish"] is False
    assert {o.qid: o.status for o in outcomes}["Q3"] == "skipped"


def test_publish_one_species_at_a_time(tmp_path: Path) -> None:
    """Spec Revision 2026-10-05 (b): publishing does not wait for the whole wave."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    ready = _ready("Q1", "Talgoxe", 1)
    sibling = _ready("Q2", "Blåmes", 1)
    for record in (ready, sibling):
        save_record(record_path(paths.data_out, record["qid"]), record)
    outcomes = publish_wave(paths, species=["Q1"])
    assert {o.qid for o in outcomes} == {"Q1"}
    assert (load_record(record_path(paths.data_out, "Q1")) or {}).get("publish") is True
    assert (load_record(record_path(paths.data_out, "Q2")) or {}).get("publish") is not True


def test_publish_sets_published_at_once_and_keeps_it(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q1"), _ready("Q1", "Talgoxe", 1))
    publish_wave(paths, species=["Q1"], now=date(2026, 10, 6))
    first = load_record(record_path(paths.data_out, "Q1"))
    assert first is not None
    assert first["publishedAt"] == "2026-10-06"
    # A later run (an edit still current, or simply run again) keeps the original date.
    publish_wave(paths, species=["Q1"], now=date(2026, 11, 20))
    second = load_record(record_path(paths.data_out, "Q1"))
    assert second is not None
    assert second["publishedAt"] == "2026-10-06"


def test_publish_blocks_a_species_with_status_not_ok(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _ready("Q1", "Talgoxe", 1)
    record["status"] = "pending"
    save_record(record_path(paths.data_out, "Q1"), record)
    outcomes = publish_wave(paths, species=["Q1"])
    assert outcomes[0].status == "skipped"
    assert (load_record(record_path(paths.data_out, "Q1")) or {}).get("publish") is not True


def test_publish_blocks_a_species_missing_a_required_topic(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _ready("Q1", "Talgoxe", 1)
    record["facts"] = [f for f in record["facts"] if f["topic"] != "voice"]
    new_hash = facts_hash(record)
    record["generated"]["verify"]["factsHash"] = new_hash
    record["generated"]["text"]["factsHash"] = new_hash
    save_record(record_path(paths.data_out, "Q1"), record)
    outcomes = publish_wave(paths, species=["Q1"])
    assert outcomes[0].status == "skipped"
    assert any("läte" in reason for reason in outcomes[0].errors)
    assert (load_record(record_path(paths.data_out, "Q1")) or {}).get("publish") is not True


def test_publish_blocks_an_unverified_species(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _ready("Q1", "Talgoxe", 1)
    del record["verification"]
    save_record(record_path(paths.data_out, "Q1"), record)
    outcomes = publish_wave(paths, species=["Q1"])
    assert outcomes[0].status == "skipped"
    assert (load_record(record_path(paths.data_out, "Q1")) or {}).get("publish") is not True


def test_publish_blocks_a_species_whose_verify_hash_is_stale(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _ready("Q1", "Talgoxe", 1)
    record["generated"]["verify"]["factsHash"] = "stale0000"
    save_record(record_path(paths.data_out, "Q1"), record)
    outcomes = publish_wave(paths, species=["Q1"])
    assert outcomes[0].status == "skipped"
    assert (load_record(record_path(paths.data_out, "Q1")) or {}).get("publish") is not True


def test_publish_blocks_a_species_whose_text_is_not_written_from_the_current_facts(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _ready("Q1", "Talgoxe", 1)
    record["generated"]["text"]["factsHash"] = "stale0000"
    save_record(record_path(paths.data_out, "Q1"), record)
    outcomes = publish_wave(paths, species=["Q1"])
    assert outcomes[0].status == "skipped"
    assert (load_record(record_path(paths.data_out, "Q1")) or {}).get("publish") is not True


def test_publish_blocks_a_species_written_with_allow_unreviewed(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _ready("Q1", "Talgoxe", 1)
    record["generated"]["text"]["unreviewed"] = True
    save_record(record_path(paths.data_out, "Q1"), record)
    outcomes = publish_wave(paths, species=["Q1"])
    assert outcomes[0].status == "skipped"
    assert (load_record(record_path(paths.data_out, "Q1")) or {}).get("publish") is not True


def test_publish_blocks_a_species_whose_text_cites_a_fact_that_is_gone(tmp_path: Path) -> None:
    """Isolates the stale-citation check from the facts-hash check above it: the facts
    hash is recomputed to match the trimmed facts, so only the stale citation blocks."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _ready("Q1", "Talgoxe", 1)
    record["text"] = {
        "sv": {"lead": [{"text": "x", "factIds": ["f04"]}]},
        "en": {},
    }
    record["facts"] = [f for f in record["facts"] if f["id"] != "f04"]
    new_hash = facts_hash(record)
    record["generated"]["verify"]["factsHash"] = new_hash
    record["generated"]["text"]["factsHash"] = new_hash
    save_record(record_path(paths.data_out, "Q1"), record)
    outcomes = publish_wave(paths, species=["Q1"])
    assert outcomes[0].status == "skipped"
    assert any("f04" in reason for reason in outcomes[0].errors)
    assert (load_record(record_path(paths.data_out, "Q1")) or {}).get("publish") is not True


def test_publish_reports_an_already_published_species_that_no_longer_qualifies(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _ready("Q1", "Talgoxe", 1)
    record["publish"] = True
    record["publishedAt"] = "2026-09-01"
    # A spot-check strike after publication: the verify hash no longer matches.
    record["generated"]["verify"]["factsHash"] = "stale0000"
    save_record(record_path(paths.data_out, "Q1"), record)
    outcomes = publish_wave(paths, species=["Q1"])
    assert outcomes[0].status == "failed"
    assert any("sätt publish: false" in error for error in outcomes[0].errors)
    saved = load_record(record_path(paths.data_out, "Q1"))
    assert saved is not None
    assert saved["publish"] is True  # never flipped by this step
    assert saved["publishedAt"] == "2026-09-01"  # untouched


def test_publish_blocks_a_comparison_whose_facts_hash_is_stale(tmp_path: Path) -> None:
    """Tillägg 2026-10-06 (Task 22 review, I2): a `status: ok` comparison is not published
    just because both species are, when `web compare` left it stale (outside --top, no
    longer a look-alike pair, or waiting for a fact sheet)."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    ready = _ready("Q1", "Talgoxe", 1)
    partner = _ready("Q2", "Blåmes", 1)
    for record in (ready, partner):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    save_record(
        paths.comparisons_out / "Q1_Q2.json",
        {"a": "Q1", "b": "Q2", "status": "ok", "publish": False, "generated": {"factsHash": "x"}},
    )
    outcomes = publish_wave(paths, wave=1)
    assert (load_record(record_path(paths.data_out, "Q1")) or {}).get("publish") is True
    assert (load_record(record_path(paths.data_out, "Q2")) or {}).get("publish") is True
    comparison = load_record(paths.comparisons_out / "Q1_Q2.json")
    assert comparison is not None and comparison["publish"] is False
    assert not any("jämförelse" in o.notes for o in outcomes)
