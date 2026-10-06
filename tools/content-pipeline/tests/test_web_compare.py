"""Tests for web/compare.py: pairs, search volumes and the comparison step."""

from __future__ import annotations

import csv
from pathlib import Path

from birdy_fetcher.web.compare import (
    Pair,
    candidate_pairs,
    comparison_path,
    comparison_slugs,
    queries,
    read_volumes,
    select_pairs,
    write_candidates,
)
from birdy_fetcher.web.record import Record, new_record


def _species(qid: str, sv: str, en: str, slug_sv: str, slug_en: str, others: list[str]) -> Record:
    record = new_record(qid)
    record["names"] = {"sv": sv, "en": en, "scientific": sv}
    record["slug"] = {"sv": slug_sv, "en": slug_en}
    record["facts"] = [
        {
            "id": f"f0{i + 1}",
            "topic": "lookalike",
            "sv": "x",
            "sources": [],
            "other": {"scientific": o, "qid": o},
        }
        for i, o in enumerate(others)
    ]
    return record


RECORDS = {
    "Q25485": _species(
        "Q25485", "Talgoxe", "Great Tit", "talgoxe", "great-tit", ["Q25404", "Q999"]
    ),
    "Q25404": _species(
        "Q25404", "Blåmes", "Eurasian Blue Tit", "blames", "eurasian-blue-tit", ["Q25485"]
    ),
    "Q26000": _species("Q26000", "Kaja", "Western Jackdaw", "kaja", "western-jackdaw", []),
}


def test_candidate_pairs_are_unique_and_ordered_by_swedish_slug() -> None:
    assert candidate_pairs(RECORDS) == [Pair("Q25404", "Q25485")]


def test_queries_in_both_languages() -> None:
    q = queries(Pair("Q25404", "Q25485"), RECORDS)
    assert q["sv"] == [
        "blåmes eller talgoxe",
        "talgoxe eller blåmes",
        "skillnad blåmes talgoxe",
        "blåmes talgoxe skillnad",
    ]
    assert q["en"][0] == "eurasian blue tit vs great tit"
    assert q["en"][2] == "difference between eurasian blue tit and great tit"


def test_slugs_and_file_name() -> None:
    pair = Pair("Q25404", "Q25485")
    assert comparison_slugs(pair, RECORDS) == {
        "sv": "blames-eller-talgoxe",
        "en": "eurasian-blue-tit-vs-great-tit",
    }
    assert comparison_path(Path("out"), Pair("Q25485", "Q25404")).name == "Q25404_Q25485.json"


def test_candidates_file_keeps_volumes_that_are_filled_in(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    write_candidates(path, [Pair("Q25404", "Q25485")], RECORDS)
    rows = list(csv.DictReader(path.open(encoding="utf-8", newline="")))
    rows[0]["sv_volume"] = "1 300"
    rows[0]["en_volume"] = "880"
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    write_candidates(path, [Pair("Q25404", "Q25485")], RECORDS)
    assert read_volumes(path) == {Pair("Q25404", "Q25485"): (1300, 880)}


def test_select_pairs_ranks_by_swedish_then_english_and_drops_zero() -> None:
    volumes = {
        Pair("Q1", "Q2"): (100, 0),
        Pair("Q3", "Q4"): (100, 50),
        Pair("Q5", "Q6"): (0, 0),
        Pair("Q7", "Q8"): (10, 900),
    }
    assert select_pairs(volumes, top=2) == [Pair("Q3", "Q4"), Pair("Q1", "Q2")]
    assert Pair("Q5", "Q6") not in select_pairs(volumes, top=10)
