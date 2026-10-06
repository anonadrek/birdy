"""Tests for web/compare.py: pairs, search volumes and the comparison step."""

from __future__ import annotations

import csv
from pathlib import Path

import pytest

from birdy_fetcher.web.compare import (
    BELOW_THRESHOLD_VALUE,
    COLUMNS,
    Pair,
    _number,
    candidate_pairs,
    comparison_path,
    comparison_slugs,
    queries,
    read_volumes,
    select_pairs,
    write_candidates,
)
from birdy_fetcher.web.record import Record, new_record
from birdy_fetcher.web.sheet_csv import write_sheet


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


def _base_records() -> dict[str, Record]:
    """A fresh dict every call -- tests that mutate a record (e.g. a name change) must
    not leak into other tests."""
    return {
        "Q25485": _species(
            "Q25485", "Talgoxe", "Great Tit", "talgoxe", "great-tit", ["Q25404", "Q999"]
        ),
        "Q25404": _species(
            "Q25404", "Blåmes", "Eurasian Blue Tit", "blames", "eurasian-blue-tit", ["Q25485"]
        ),
        "Q26000": _species("Q26000", "Kaja", "Western Jackdaw", "kaja", "western-jackdaw", []),
    }


RECORDS = _base_records()


def _rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


# -- candidate_pairs ------------------------------------------------------------------


def test_candidate_pairs_are_unique_and_ordered_by_swedish_slug() -> None:
    assert candidate_pairs(RECORDS) == [Pair("Q25404", "Q25485")]


def test_non_lookalike_topic_is_ignored() -> None:
    records = _base_records()
    records["Q26000"]["facts"] = [
        {
            "id": "f01",
            "topic": "appearance",
            "sv": "x",
            "sources": [],
            "other": {"scientific": "Q25485", "qid": "Q25485"},
        }
    ]
    assert Pair("Q25485", "Q26000") not in candidate_pairs(records)
    assert Pair("Q26000", "Q25485") not in candidate_pairs(records)


def test_self_pair_is_skipped() -> None:
    records = _base_records()
    records["Q26000"]["facts"] = [
        {
            "id": "f01",
            "topic": "lookalike",
            "sv": "x",
            "sources": [],
            "other": {"scientific": "Q26000", "qid": "Q26000"},
        }
    ]
    assert candidate_pairs(records) == [Pair("Q25404", "Q25485")]


# -- queries ---------------------------------------------------------------------------


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


def test_en_queries_add_ioc_prefix_stripped_variants() -> None:
    # (fix 2026-10-06, item 12) "Eurasian Blue Tit" carries an IOC qualifier searchers
    # rarely type; the plain "blue tit" phrasings are added too.
    q = queries(Pair("Q25404", "Q25485"), RECORDS)
    assert "blue tit vs great tit" in q["en"]
    assert "great tit vs blue tit" in q["en"]
    assert "difference between blue tit and great tit" in q["en"]
    # the original, full-name phrasings are still first (existing index assertions above)
    assert q["en"][0] == "eurasian blue tit vs great tit"


def test_en_queries_no_duplicates_when_neither_name_has_a_prefix() -> None:
    records = {
        "Q1": _species("Q1", "Koltrast", "Blackbird", "koltrast", "blackbird", []),
        "Q2": _species("Q2", "Kaja", "Jackdaw", "kaja", "jackdaw", []),
    }
    q = queries(Pair("Q1", "Q2"), records)
    assert q["en"] == [
        "blackbird vs jackdaw",
        "jackdaw vs blackbird",
        "difference between blackbird and jackdaw",
    ]


# -- _number ----------------------------------------------------------------------------


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        ("1 300", 1300),
        ("1\u00a0300", 1300),
        ("1,300", 1300),
        ("1.300", 1300),
        ("880", 880),
        ("", 0),
        ("10K", 10000),
        ("10k", 10000),
    ],
)
def test_number_accepts_valid_formats(value: str, expected: int) -> None:
    assert _number(value, lang="sv", a_qid="Q1", b_qid="Q2") == expected


def test_number_below_threshold_marker() -> None:
    # Keyword Planner's "<10" bucket is a real, nonzero volume it will not show
    # precisely; documented choice (item 1): map it to a midpoint, not 0, so it is not
    # indistinguishable from a cell nobody has filled in yet.
    assert _number("<10", lang="sv", a_qid="Q1", b_qid="Q2") == BELOW_THRESHOLD_VALUE
    assert BELOW_THRESHOLD_VALUE != 0


@pytest.mark.parametrize("value", ["1K - 10K", "55.5", "=1+2", "abc", "-5", "1300 880"])
def test_number_rejects_invalid_formats(value: str) -> None:
    with pytest.raises(ValueError, match="Q25404/Q25485"):
        _number(value, lang="sv", a_qid="Q25404", b_qid="Q25485")


# -- comparison_slugs / comparison_path -------------------------------------------------


def test_slugs_and_file_name() -> None:
    pair = Pair("Q25404", "Q25485")
    assert comparison_slugs(pair, RECORDS) == {
        "sv": "blames-eller-talgoxe",
        "en": "eurasian-blue-tit-vs-great-tit",
    }
    assert comparison_path(Path("out"), Pair("Q25485", "Q25404")).name == "Q25404_Q25485.json"


def test_comparison_slugs_sorts_sv_like_en_regardless_of_pair_orientation() -> None:
    # Pair.a/.b flipped relative to Swedish-slug order (e.g. fed back from read_volumes
    # without re-normalising, fix 2026-10-06 item 3): the slugs must still come out
    # alphabetical in both languages.
    flipped = Pair("Q25485", "Q25404")
    assert comparison_slugs(flipped, RECORDS) == {
        "sv": "blames-eller-talgoxe",
        "en": "eurasian-blue-tit-vs-great-tit",
    }


# -- write_candidates / read_volumes: basics --------------------------------------------


def test_candidates_file_keeps_volumes_that_are_filled_in(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    write_candidates(path, [Pair("Q25404", "Q25485")], RECORDS)
    with path.open(encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))
    rows[0]["sv_volume"] = "1 300"
    rows[0]["en_volume"] = "880"
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    write_candidates(path, [Pair("Q25404", "Q25485")], RECORDS)
    assert read_volumes(path) == {Pair("Q25404", "Q25485"): (1300, 880)}


def test_read_volumes_strips_whitespace_around_qids(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    write_sheet(
        path,
        [
            {
                "a_qid": " Q25404 ",
                "b_qid": " Q25485 ",
                "a_sv": "Blåmes",
                "b_sv": "Talgoxe",
                "a_en": "Eurasian Blue Tit",
                "b_en": "Great Tit",
                "sv_queries": "",
                "en_queries": "",
                "sv_volume": "500",
                "en_volume": "300",
                "aktuell": "ja",
            }
        ],
        COLUMNS,
    )
    assert read_volumes(path) == {Pair("Q25404", "Q25485"): (500, 300)}


# -- write_candidates: orientation and whitespace survive hand editing -----------------


def test_write_candidates_keeps_volume_despite_a_flipped_old_row(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    # Albin (or a re-export) flipped a_qid/b_qid relative to our canonical order, and
    # added stray whitespace; the query text itself is untouched and still current.
    write_sheet(
        path,
        [
            _duplicate_row(
                a_qid="Q25485",
                b_qid=" Q25404",
                a_sv="Talgoxe",
                b_sv="Blåmes",
                a_en="Great Tit",
                b_en="Eurasian Blue Tit",
                sv_volume="900",
                en_volume="400",
            )
        ],
        COLUMNS,
    )
    write_candidates(path, [Pair("Q25404", "Q25485")], RECORDS)
    rows = _rows(path)
    assert rows[0]["sv_volume"] == "900"
    assert rows[0]["en_volume"] == "400"


# -- write_candidates: duplicate old rows -----------------------------------------------


def _old_sheet(path: Path, rows: list[dict[str, str]]) -> None:
    write_sheet(path, rows, COLUMNS)


def _duplicate_row(**overrides: str) -> dict[str, str]:
    row = {
        "a_qid": "Q25404",
        "b_qid": "Q25485",
        "a_sv": "Blåmes",
        "b_sv": "Talgoxe",
        "a_en": "Eurasian Blue Tit",
        "b_en": "Great Tit",
        "sv_queries": "blåmes eller talgoxe; talgoxe eller blåmes; "
        "skillnad blåmes talgoxe; blåmes talgoxe skillnad",
        "en_queries": "eurasian blue tit vs great tit; great tit vs eurasian blue tit; "
        "difference between eurasian blue tit and great tit; blue tit vs great tit; "
        "great tit vs blue tit; difference between blue tit and great tit",
        "sv_volume": "",
        "en_volume": "",
        "aktuell": "ja",
    }
    row.update(overrides)
    return row


def test_duplicate_old_rows_keep_the_non_empty_value(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    _old_sheet(
        path,
        [
            _duplicate_row(sv_volume=""),
            _duplicate_row(sv_volume="500"),
        ],
    )
    write_candidates(path, [Pair("Q25404", "Q25485")], RECORDS)
    assert read_volumes(path) == {Pair("Q25404", "Q25485"): (500, 0)}


def test_duplicate_old_rows_with_conflicting_values_raise(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    _old_sheet(
        path,
        [
            _duplicate_row(sv_volume="500"),
            _duplicate_row(sv_volume="600"),
        ],
    )
    with pytest.raises(ValueError, match="Q25404/Q25485"):
        write_candidates(path, [Pair("Q25404", "Q25485")], RECORDS)


# -- write_candidates: atomic write ------------------------------------------------------


def test_a_failure_mid_build_leaves_the_old_file_intact(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    _old_sheet(path, [_duplicate_row(sv_volume="garbage")])
    before = path.read_bytes()
    with pytest.raises(ValueError):
        write_candidates(path, [Pair("Q25404", "Q25485")], RECORDS)
    assert path.read_bytes() == before


# -- write_candidates: stale pairs --------------------------------------------------------


def test_stale_pair_with_a_volume_is_kept_and_marked_not_current(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    records = _base_records()
    both = [Pair("Q25404", "Q25485")]
    write_candidates(path, both, records)
    rows = _rows(path)
    rows[0]["sv_volume"] = "700"
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)
    # the pair is no longer a candidate (e.g. the lookalike fact changed)
    write_candidates(path, [], records)
    rows = _rows(path)
    assert len(rows) == 1
    assert rows[0]["aktuell"] == "nej"
    assert rows[0]["sv_volume"] == "700"
    # select_pairs must only see current candidates: a stale row is invisible here
    assert read_volumes(path) == {}


def test_stale_pair_without_a_volume_is_dropped(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    records = _base_records()
    write_candidates(path, [Pair("Q25404", "Q25485")], records)
    write_candidates(path, [], records)
    assert _rows(path) == []


def test_a_re_rolled_pair_keeps_its_old_volume_when_it_returns(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    records = _base_records()
    pair = Pair("Q25404", "Q25485")
    write_candidates(path, [pair], records)
    rows = _rows(path)
    rows[0]["sv_volume"] = "700"
    rows[0]["en_volume"] = "250"
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)
    write_candidates(path, [], records)  # goes stale
    write_candidates(path, [pair], records)  # comes back
    rows = _rows(path)
    assert rows[0]["aktuell"] == "ja"
    assert rows[0]["sv_volume"] == "700"
    assert rows[0]["en_volume"] == "250"
    assert read_volumes(path) == {pair: (700, 250)}


# -- write_candidates: missing-volume count and the name-change clear -------------------


def test_missing_volumes_count(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    records = {**RECORDS}
    result = write_candidates(path, [Pair("Q25404", "Q25485")], records)
    assert result.missing_volumes == 1
    rows = _rows(path)
    rows[0]["sv_volume"] = "500"
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)
    result = write_candidates(path, [Pair("Q25404", "Q25485")], records)
    assert result.missing_volumes == 0


def test_name_change_clears_the_stale_language_volume_and_is_counted(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    records = _base_records()
    pair = Pair("Q25404", "Q25485")
    write_candidates(path, [pair], records)
    rows = _rows(path)
    rows[0]["sv_volume"] = "700"
    rows[0]["en_volume"] = "250"
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)
    # the Swedish name changes; the English name (and its queries) stay the same
    records["Q25404"]["names"]["sv"] = "Mindre blåmes"
    result = write_candidates(path, [pair], records)
    assert result.cleared_sv == 1
    assert result.cleared_en == 0
    rows = _rows(path)
    assert rows[0]["sv_volume"] == ""
    assert rows[0]["en_volume"] == "250"


# -- write_candidates: row order ---------------------------------------------------------


def test_rows_are_sorted_by_swedish_names(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    records = {
        "Q1": _species("Q1", "Ängspiplärka", "Meadow Pipit", "angspiplarka", "meadow-pipit", []),
        "Q2": _species("Q2", "Blåmes", "Eurasian Blue Tit", "blames", "eurasian-blue-tit", []),
    }
    records["Q1"]["facts"] = [
        {
            "id": "f01",
            "topic": "lookalike",
            "sv": "x",
            "sources": [],
            "other": {"scientific": "Q2", "qid": "Q2"},
        }
    ]
    records["Q2"]["facts"] = [
        {
            "id": "f01",
            "topic": "lookalike",
            "sv": "x",
            "sources": [],
            "other": {"scientific": "Q1", "qid": "Q1"},
        }
    ]
    pairs = candidate_pairs(records)
    write_candidates(path, pairs, records)
    rows = _rows(path)
    assert [r["a_sv"] for r in rows] == sorted(r["a_sv"] for r in rows)


# -- CLI-level smoke (via write_candidates' result object) -----------------------------


def test_write_candidates_result_reports_pairs(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    pair = Pair("Q25404", "Q25485")
    result = write_candidates(path, [pair], RECORDS)
    assert result.pairs == [pair]
    assert result.path == path


# -- select_pairs ------------------------------------------------------------------------


def test_select_pairs_ranks_by_swedish_then_english_and_drops_zero() -> None:
    volumes = {
        Pair("Q1", "Q2"): (100, 0),
        Pair("Q3", "Q4"): (100, 50),
        Pair("Q5", "Q6"): (0, 0),
        Pair("Q7", "Q8"): (10, 900),
    }
    assert select_pairs(volumes, top=2) == [Pair("Q3", "Q4"), Pair("Q1", "Q2")]
    assert Pair("Q5", "Q6") not in select_pairs(volumes, top=10)
