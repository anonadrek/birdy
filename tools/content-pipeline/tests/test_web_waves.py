"""Tests for web/waves.py: the wave lists and how they are applied, and (Task 23) the
publishing step."""

from __future__ import annotations

import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import pytest

from birdy_fetcher.web.record import (
    Record,
    facts_hash,
    load_record,
    new_record,
    record_path,
    save_record,
)
from birdy_fetcher.web.waves import NextPick, compute_waves, publish_next, publish_wave, run_waves

from .web_repo import make_repo

RESIDENT = [70, 65, 60, 55, 70, 79, 64, 68, 74, 100, 66, 69]
MIGRANT = [0, 0, 5, 60, 100, 90, 85, 70, 40, 5, 0, 0]
NOW = datetime(2026, 11, 20, tzinfo=UTC)


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
    # M6 (review fix 2026-10-06): a comparison outcome is named "A eller B", like
    # compare.py names its own outcomes, not the bare file stem.
    pair_outcome = next(o for o in outcomes if o.qid == "Q1_Q2")
    assert pair_outcome.name == "Talgoxe eller Blåmes"


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
    publish_wave(paths, species=["Q1"], now=datetime(2026, 10, 6, tzinfo=UTC))
    first = load_record(record_path(paths.data_out, "Q1"))
    assert first is not None
    assert first["publishedAt"] == "2026-10-06"
    # A later run (an edit still current, or simply run again) keeps the original date.
    publish_wave(paths, species=["Q1"], now=NOW)
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
    assert not any(o.attempted for o in outcomes)


# -- review fixes 2026-10-06: I1 (--species scope), I2 (live, not just published), I3
# (unknown QID, already-published comparison scope), I4 (--next) and M6 (malformed file)


def test_publish_species_scope_does_not_touch_unrelated_comparisons(tmp_path: Path) -> None:
    """I1, probe P1: publishing one species must not also flip an unrelated comparison
    just because both its sides happen to already be live."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    live_a = _ready("Q1", "Talgoxe", 1)
    live_a["publish"] = True
    live_b = _ready("Q2", "Blåmes", 1)
    live_b["publish"] = True
    target = _ready("Q3", "Koltrast", 1)
    for record in (live_a, live_b, target):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    save_record(
        paths.comparisons_out / "Q1_Q2.json",
        {
            "a": "Q1",
            "b": "Q2",
            "status": "ok",
            "publish": False,
            "generated": {"factsHash": facts_hash(live_a) + facts_hash(live_b)},
        },
    )
    outcomes = publish_wave(paths, species=["Q3"])
    assert (load_record(record_path(paths.data_out, "Q3")) or {}).get("publish") is True
    comparison = load_record(paths.comparisons_out / "Q1_Q2.json")
    assert comparison is not None and comparison["publish"] is False
    assert not any(o.qid == "Q1_Q2" for o in outcomes)


def test_publish_species_scope_only_publishes_the_selected_pair(tmp_path: Path) -> None:
    """I1, probe P6: selecting two species must only publish the comparison between
    exactly those two, not one of them paired with a third, unselected species."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    first = _ready("Q1", "Talgoxe", 1)
    second = _ready("Q2", "Blåmes", 1)
    third = _ready("Q3", "Koltrast", 1)
    third["publish"] = True
    for record in (first, second, third):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    save_record(
        paths.comparisons_out / "Q1_Q2.json",
        {
            "a": "Q1",
            "b": "Q2",
            "status": "ok",
            "publish": False,
            "generated": {"factsHash": facts_hash(first) + facts_hash(second)},
        },
    )
    save_record(
        paths.comparisons_out / "Q1_Q3.json",
        {
            "a": "Q1",
            "b": "Q3",
            "status": "ok",
            "publish": False,
            "generated": {"factsHash": facts_hash(first) + facts_hash(third)},
        },
    )
    outcomes = publish_wave(paths, species=["Q1", "Q2"])
    assert (load_record(record_path(paths.data_out, "Q1")) or {}).get("publish") is True
    assert (load_record(record_path(paths.data_out, "Q2")) or {}).get("publish") is True
    q1_q2 = load_record(paths.comparisons_out / "Q1_Q2.json")
    q1_q3 = load_record(paths.comparisons_out / "Q1_Q3.json")
    assert q1_q2 is not None and q1_q2["publish"] is True
    assert q1_q3 is not None and q1_q3["publish"] is False
    by_qid = {o.qid: o.status for o in outcomes}
    assert by_qid["Q1_Q2"] == "ok"
    assert "Q1_Q3" not in by_qid  # Q3 was never selected: out of scope, not even reported


def test_publish_an_unready_published_species_does_not_make_a_comparison_look_ready(
    tmp_path: Path,
) -> None:
    """I2, probe P2: a species whose `publish` flag is stuck True only because this step
    never unpublishes anything must not count as a live side of a comparison."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    unready_but_published = _ready("Q1", "Talgoxe", 1)
    unready_but_published["publish"] = True
    unready_but_published["generated"]["verify"]["factsHash"] = "stale0000"
    other = _ready("Q2", "Blåmes", 1)
    for record in (unready_but_published, other):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    save_record(
        paths.comparisons_out / "Q1_Q2.json",
        {
            "a": "Q1",
            "b": "Q2",
            "status": "ok",
            "publish": False,
            "generated": {"factsHash": facts_hash(unready_but_published) + facts_hash(other)},
        },
    )
    outcomes = publish_wave(paths, species=["Q1", "Q2"])
    by_qid = {o.qid: o for o in outcomes}
    assert by_qid["Q1"].status == "failed"  # published but no longer qualifies
    assert by_qid["Q2"].status == "ok"
    comparison = load_record(paths.comparisons_out / "Q1_Q2.json")
    assert comparison is not None and comparison["publish"] is False
    assert by_qid["Q1_Q2"].status == "skipped"


def test_publish_reports_an_unknown_species_as_failed(tmp_path: Path) -> None:
    """I3 (mirrors run_write's M1): an unknown --species is a loud failure, not a
    silently ignored no-op."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    outcomes = publish_wave(paths, species=["Q999"])
    assert outcomes[0].qid == "Q999"
    assert outcomes[0].status == "failed"
    assert any("artposten saknas" in e for e in outcomes[0].errors)


def test_publish_reports_a_stale_published_comparison_when_its_species_is_selected(
    tmp_path: Path,
) -> None:
    """I3 scope follow-up: publishing Q1 for the first time must also catch an already
    published comparison of Q1's whose facts have since moved on, even though this call
    never names Q2."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    ready1 = _ready("Q1", "Talgoxe", 1)
    ready2 = _ready("Q2", "Blåmes", 1)
    for record in (ready1, ready2):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    save_record(
        paths.comparisons_out / "Q1_Q2.json",
        {
            "a": "Q1",
            "b": "Q2",
            "status": "ok",
            "publish": True,
            "generated": {"factsHash": facts_hash(ready1) + facts_hash(ready2)},
        },
    )
    # A spot-check strike on Q1: the fact sheet shrinks, but the verify and text hashes
    # are refreshed, so Q1 itself stays ready to be published for the first time.
    struck = load_record(record_path(paths.data_out, "Q1"))
    assert struck is not None
    struck["facts"] = [f for f in struck["facts"] if f["id"] != "f04"]
    new_hash = facts_hash(struck)
    struck["generated"]["verify"]["factsHash"] = new_hash
    struck["generated"]["text"]["factsHash"] = new_hash
    save_record(record_path(paths.data_out, "Q1"), struck)
    outcomes = publish_wave(paths, species=["Q1"])
    by_qid = {o.qid: o for o in outcomes}
    assert by_qid["Q1"].status == "ok"
    assert by_qid["Q1_Q2"].status == "failed"
    assert any("sätt publish: false" in e for e in by_qid["Q1_Q2"].errors)
    comparison = load_record(paths.comparisons_out / "Q1_Q2.json")
    assert comparison is not None and comparison["publish"] is True  # never flipped


def test_publish_a_malformed_comparison_file_fails_loudly_without_stopping_the_run(
    tmp_path: Path,
) -> None:
    """M6: one bad comparison file must not take the whole run down with it."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    ready1 = _ready("Q1", "Talgoxe", 1)
    ready2 = _ready("Q2", "Blåmes", 1)
    for record in (ready1, ready2):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    (paths.comparisons_out / "Q1_Q2.json").write_text("{not json", encoding="utf-8")
    outcomes = publish_wave(paths, wave=1)
    by_status = {o.qid: o.status for o in outcomes}
    assert by_status["Q1"] == "ok"
    assert by_status["Q2"] == "ok"
    assert by_status["Q1_Q2"] == "failed"


def test_publish_writes_a_step_report(tmp_path: Path) -> None:
    """M1: `web publish` writes a report like every other web step, so "Rapporter i ..."
    is true."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q1"), _ready("Q1", "Talgoxe", 1))
    publish_wave(paths, species=["Q1"], now=NOW)
    reports = list(paths.reports.glob("web-publish-*.md"))
    assert len(reports) == 1
    text = reports[0].read_text(encoding="utf-8")
    assert "Publicering" in text
    assert "| ok | 1 |" in text


def test_publish_does_not_rewrite_an_already_ready_published_species(tmp_path: Path) -> None:
    """M2: nothing to save when a species was already published and still qualifies --
    this step must not touch the file's mtime/content for no reason."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _ready("Q1", "Talgoxe", 1)
    record["publish"] = True
    record["publishedAt"] = "2026-09-01"
    save_record(record_path(paths.data_out, "Q1"), record)
    before = record_path(paths.data_out, "Q1").read_text(encoding="utf-8")
    outcomes = publish_wave(paths, species=["Q1"])
    assert outcomes[0].status == "ok"
    assert record_path(paths.data_out, "Q1").read_text(encoding="utf-8") == before


# -- --next (I4) --------------------------------------------------------------------------


def test_publish_next_picks_in_queue_order(tmp_path: Path) -> None:
    """A species in an earlier wave is picked before one in a later wave, even when the
    later wave's QID sorts first alphabetically."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    wave_two = _ready("Q2", "Blåmes", 2)
    wave_one = _ready("Q5", "Trana", 1)
    for record in (wave_two, wave_one):
        save_record(record_path(paths.data_out, record["qid"]), record)
    pick = publish_next(paths)
    assert pick == NextPick("species", "Q5")
    assert (load_record(record_path(paths.data_out, "Q5")) or {}).get("publish") is True
    assert (load_record(record_path(paths.data_out, "Q2")) or {}).get("publish") is not True


def test_publish_next_skips_excluded_items(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    first = _ready("Q1", "Talgoxe", 1)
    second = _ready("Q2", "Blåmes", 1)
    for record in (first, second):
        save_record(record_path(paths.data_out, record["qid"]), record)
    pick = publish_next(paths, exclude=frozenset({"Q1"}))
    assert pick == NextPick("species", "Q2")
    assert (load_record(record_path(paths.data_out, "Q1")) or {}).get("publish") is not True
    assert (load_record(record_path(paths.data_out, "Q2")) or {}).get("publish") is True


def test_publish_next_returns_none_when_nothing_is_ready(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _ready("Q1", "Talgoxe", 1)
    record["status"] = "pending"
    save_record(record_path(paths.data_out, "Q1"), record)
    assert publish_next(paths) is None


def test_publish_next_falls_back_to_a_ready_comparison_and_changes_only_that_file(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    live_a = _ready("Q1", "Talgoxe", 1)
    live_a["publish"] = True
    live_b = _ready("Q2", "Blåmes", 1)
    live_b["publish"] = True
    for record in (live_a, live_b):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    save_record(
        paths.comparisons_out / "Q1_Q2.json",
        {
            "a": "Q1",
            "b": "Q2",
            "status": "ok",
            "publish": False,
            "generated": {"factsHash": facts_hash(live_a) + facts_hash(live_b)},
        },
    )
    before_q1 = record_path(paths.data_out, "Q1").read_text(encoding="utf-8")
    before_q2 = record_path(paths.data_out, "Q2").read_text(encoding="utf-8")
    pick = publish_next(paths)
    assert pick == NextPick("comparison", "Q1_Q2")
    comparison = load_record(paths.comparisons_out / "Q1_Q2.json")
    assert comparison is not None and comparison["publish"] is True
    # Only the comparison file changed -- both species files are untouched.
    assert record_path(paths.data_out, "Q1").read_text(encoding="utf-8") == before_q1
    assert record_path(paths.data_out, "Q2").read_text(encoding="utf-8") == before_q2


def test_publish_next_reports_an_unreadable_comparison_file_on_stderr(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    """m3: --next's stdout contract (one line, or nothing when it exits with a pick) is
    never shared with a diagnostic about a file it had to skip."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    (paths.comparisons_out / "Q1_Q2.json").write_text("{not json", encoding="utf-8")
    pick = publish_next(paths)
    assert pick is None
    captured = capsys.readouterr()
    assert captured.out == ""
    assert "Q1_Q2.json" in captured.err


# -- re-review 2026-10-06: N1 (sides attribution + exit code), m1 (queue order), m2
# (named species outside the named wave) --------------------------------------------


def test_publish_species_mode_does_not_even_mention_an_unrelated_sides_staleness(
    tmp_path: Path,
) -> None:
    """N1 probe A: Q1 and Q2 published, Q1_Q2 live; only Q2's facts change (struck,
    verify + text refreshed). Publishing Q1 alone must not even mention Q1_Q2."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    live_a = _ready("Q1", "Talgoxe", 1)
    live_a["publish"] = True
    live_b = _ready("Q2", "Blåmes", 1)
    live_b["publish"] = True
    for record in (live_a, live_b):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    save_record(
        paths.comparisons_out / "Q1_Q2.json",
        {
            "a": "Q1",
            "b": "Q2",
            "status": "ok",
            "publish": True,
            "generated": {"factsHash": facts_hash(live_a) + facts_hash(live_b)},
        },
    )
    struck = load_record(record_path(paths.data_out, "Q2"))
    assert struck is not None
    struck["facts"] = [f for f in struck["facts"] if f["id"] != "f04"]
    new_hash = facts_hash(struck)
    struck["generated"]["verify"]["factsHash"] = new_hash
    struck["generated"]["text"]["factsHash"] = new_hash
    save_record(record_path(paths.data_out, "Q2"), struck)
    outcomes = publish_wave(paths, species=["Q1"])
    assert not any(o.qid == "Q1_Q2" for o in outcomes)
    comparison = load_record(paths.comparisons_out / "Q1_Q2.json")
    assert comparison is not None and comparison["publish"] is True  # never flipped


def test_publish_next_follows_waves_json_order_within_a_wave(tmp_path: Path) -> None:
    """m1: `review/waves.json`'s own order wins over alphabetical names.sv (and over
    QID) when both species are in the same wave."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    # Both QID order ("Q2" < "Q9") and names.sv order ("Blåmes" < "Trana") would pick Q2
    # first; waves.json instead lists Q9 first.
    first = _ready("Q9", "Trana", 1)
    second = _ready("Q2", "Blåmes", 1)
    for record in (first, second):
        save_record(record_path(paths.data_out, record["qid"]), record)
    waves_file = paths.review / "waves.json"
    waves_file.parent.mkdir(parents=True, exist_ok=True)
    waves_file.write_text(
        json.dumps({"1": [{"qid": "Q9", "name": "Trana"}, {"qid": "Q2", "name": "Blåmes"}]}),
        encoding="utf-8",
    )
    pick = publish_next(paths)
    assert pick == NextPick("species", "Q9")


def test_publish_next_falls_back_to_names_sv_without_waves_json(tmp_path: Path) -> None:
    """m1: with no `review/waves.json` at all, same-wave species fall back to
    alphabetical `names.sv`, not QID -- the two orders disagree here on purpose."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    first = _ready("Q2", "Örnsångare", 1)
    second = _ready("Q9", "Blåmes", 1)
    for record in (first, second):
        save_record(record_path(paths.data_out, record["qid"]), record)
    pick = publish_next(paths)
    assert pick == NextPick("species", "Q9")  # "Blåmes" < "Örnsångare"


def test_publish_species_outside_the_named_wave_is_reported_skipped(tmp_path: Path) -> None:
    """m2: --species Q1 --wave 2 for a wave-1 species names the mismatch instead of
    Q1 silently getting no outcome at all."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q1"), _ready("Q1", "Talgoxe", 1))  # wave 1
    outcomes = publish_wave(paths, wave=2, species=["Q1"])
    assert len(outcomes) == 1
    assert outcomes[0].qid == "Q1"
    assert outcomes[0].status == "skipped"
    assert any("inte i våg 2" in e for e in outcomes[0].errors)


# -- third re-review 2026-10-06: N1 item 1 (structural `attempted`), N1 item 2 (hash
# type/length safety) -----------------------------------------------------------------


def test_publish_species_pair_with_a_not_current_comparison_is_attempted(tmp_path: Path) -> None:
    """--species Q1 --species Q2 tries to publish Q1_Q2 too; a not-current comparison is
    this call's own business and must be marked `attempted` for the CLI's exit code
    (end to end in test_cli_smoke.py)."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    ready1 = _ready("Q1", "Talgoxe", 1)
    ready2 = _ready("Q2", "Blåmes", 1)
    for record in (ready1, ready2):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    save_record(
        paths.comparisons_out / "Q1_Q2.json",
        {
            "a": "Q1",
            "b": "Q2",
            "status": "ok",
            "publish": False,
            "generated": {"factsHash": "stale0000stale0000stale0000stal0"},
        },
    )
    outcomes = publish_wave(paths, species=["Q1", "Q2"])
    by_qid = {o.qid: o for o in outcomes}
    assert by_qid["Q1_Q2"].status == "skipped"
    assert by_qid["Q1_Q2"].attempted is True


def test_publish_a_malformed_comparison_within_the_named_pair_is_attempted(
    tmp_path: Path,
) -> None:
    """N1 item 1: a malformed comparison file is still this call's own business when its
    filename names exactly the --species pair, even though it cannot be parsed to
    confirm that from its content."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    ready1 = _ready("Q1", "Talgoxe", 1)
    ready2 = _ready("Q2", "Blåmes", 1)
    for record in (ready1, ready2):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    (paths.comparisons_out / "Q1_Q2.json").write_text("{not json", encoding="utf-8")
    outcomes = publish_wave(paths, species=["Q1", "Q2"])
    by_qid = {o.qid: o for o in outcomes}
    assert by_qid["Q1_Q2"].status == "failed"
    assert by_qid["Q1_Q2"].attempted is True


def test_publish_a_malformed_comparison_outside_the_named_pair_is_not_attempted(
    tmp_path: Path,
) -> None:
    """The same malformed file, but only ONE side is named: not this call's own publish
    attempt (a new comparison always needs both sides selected), so it must not affect
    the --species exit code."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    ready1 = _ready("Q1", "Talgoxe", 1)
    ready2 = _ready("Q2", "Blåmes", 1)
    for record in (ready1, ready2):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    (paths.comparisons_out / "Q1_Q2.json").write_text("{not json", encoding="utf-8")
    outcomes = publish_wave(paths, species=["Q1"])
    by_qid = {o.qid: o for o in outcomes}
    assert by_qid["Q1_Q2"].status == "failed"
    assert by_qid["Q1_Q2"].attempted is False


def test_a_species_whose_voice_file_is_not_its_recording_is_not_ready(tmp_path: Path) -> None:
    """Re-review 2026-10-07: publish must never ship an old voice.mp3 under a new
    recording's credits. With audio.mp3Sha256 the file must exist and match."""
    import hashlib

    from birdy_fetcher.web.waves import unready_reasons

    images_out = tmp_path / "img"
    record = _ready("Q1", "Talgoxe", 1)
    assert unready_reasons(record, images_out=images_out) == []
    record["audio"] = {
        "file": "Q1/voice.mp3",
        "mp3Sha256": hashlib.sha256(b"new").hexdigest(),
    }
    assert any("saknas" in r for r in unready_reasons(record, images_out=images_out))
    voice = images_out / "Q1" / "voice.mp3"
    voice.parent.mkdir(parents=True)
    voice.write_bytes(b"old")
    assert any("inte den" in r for r in unready_reasons(record, images_out=images_out))
    voice.write_bytes(b"new")
    assert unready_reasons(record, images_out=images_out) == []
