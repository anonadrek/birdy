"""Tests for web/compare.py: pairs, search volumes and the comparison step."""

from __future__ import annotations

import csv
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import pytest

from birdy_fetcher.web.checker import CheckOutput, Verdict
from birdy_fetcher.web.compare import (
    BELOW_THRESHOLD_VALUE,
    COLUMNS,
    STALE_HASH_ERROR,
    Cell,
    CompareLang,
    CompareOptions,
    CompareOutput,
    Pair,
    Row,
    _number,
    candidate_pairs,
    check_compare,
    compare_items,
    comparison_is_current,
    comparison_path,
    comparison_slugs,
    meta_openings,
    pair_context,
    published_comparison_errors,
    queries,
    read_volumes,
    render_compare_prompt,
    run_compare,
    select_pairs,
    write_candidates,
)
from birdy_fetcher.web.paths import WebPaths
from birdy_fetcher.web.record import (
    Record,
    facts_hash,
    load_record,
    new_record,
    record_path,
    save_record,
)
from birdy_fetcher.web.sheet_csv import write_sheet
from birdy_fetcher.web.text_model import Sentence
from birdy_fetcher.web.text_step import WriteOptions, run_write

from .text_fixtures import reviewed_record
from .web_fakes import FakeJsonClient, reply
from .web_repo import PIPELINE, make_repo


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


def test_number_below_threshold_marker_with_a_space() -> None:
    # (review fix 2026-10-06, item 3) Keyword Planner (or a hand-edit) can write "< 10"
    # with a space; it means the same thing as "<10".
    assert _number("< 10", lang="sv", a_qid="Q1", b_qid="Q2") == BELOW_THRESHOLD_VALUE


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
    with path.open(encoding="utf-8-sig", newline="") as f:
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


def test_read_volumes_skips_a_row_with_an_empty_qid(tmp_path: Path) -> None:
    # Consistent with `_load_old` (review fix 2026-10-06, item 2): a row missing a_qid or
    # b_qid is skipped, not crashed on or counted.
    path = tmp_path / "comparison-volumes.csv"
    write_sheet(
        path,
        [
            {
                "a_qid": "",
                "b_qid": "Q25485",
                "a_sv": "",
                "b_sv": "Talgoxe",
                "a_en": "",
                "b_en": "Great Tit",
                "sv_queries": "",
                "en_queries": "",
                "sv_volume": "500",
                "en_volume": "300",
                "aktuell": "ja",
            },
            {
                "a_qid": "Q25404",
                "b_qid": "",
                "a_sv": "Blåmes",
                "b_sv": "",
                "a_en": "Eurasian Blue Tit",
                "b_en": "",
                "sv_queries": "",
                "en_queries": "",
                "sv_volume": "500",
                "en_volume": "300",
                "aktuell": "ja",
            },
        ],
        COLUMNS,
    )
    assert read_volumes(path) == {}


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


# -- the comparison texts (Task 22) ------------------------------------------------------

NOW = datetime(2026, 11, 22, tzinfo=UTC)
BANNED = ["fascinerande"]


# The blue tit has its own fact sheet (review fix 2026-10-06, M2), so a swap of the
# sides anywhere (prefixed_facts, compare_items, the prompt) shows up as a wrong text.
BLUE_FACTS: list[dict[str, Any]] = [
    {"id": "f01", "topic": "appearance", "sv": "Blåmesen har blå hätta och vita kinder.",
     "sources": [{"article": "sv", "quote": "blå hätta och vita kinder"}]},
    {"id": "f02", "topic": "appearance", "sv": "Buken är gul.",
     "sources": [{"article": "sv", "quote": "gul buk"}]},
    {"id": "f03", "topic": "size", "sv": "Blåmesen är cirka 12 centimeter lång.",
     "sources": [{"article": "sv", "quote": "cirka 12 centimeter lång"}]},
    {"id": "f04", "topic": "voice", "sv": "Sången är ett klart tsi-tsi-tsirr.",
     "sources": [{"article": "sv", "quote": "ett klart tsi-tsi-tsirr"}]},
    {"id": "f05", "topic": "habitat", "sv": "Blåmesen lever i lövskog, parker och trädgårdar.",
     "sources": [{"article": "sv", "quote": "i lövskog, parker och trädgårdar"}]},
    {"id": "f06", "topic": "lookalike", "sv": "Talgoxen är större och har svart huvud.",
     "sources": [{"article": "sv", "quote": "talgoxen är större och har svart huvud"}],
     "other": {"scientific": "Parus major", "qid": "Q25485"}},
    {"id": "s01", "topic": "status", "value": "resident", "sv": "Stannfågel",
     "sources": [{"article": "sv", "quote": "Blåmesen är stannfågel"}]},
    {"id": "d01", "topic": "data", "source": "artportalen", "sv": "Rapporteras året runt."},
]  # fmt: skip


def _blue_tit() -> Record:
    record = reviewed_record("Q25404")
    record["names"] = {
        "sv": "Blåmes",
        "en": "Eurasian Blue Tit",
        "scientific": "Cyanistes caeruleus",
    }
    record["slug"] = {"sv": "blames", "en": "eurasian-blue-tit"}
    record["family"] = {"latin": "Paridae", "sv": "Mesar"}
    record["facts"] = [dict(f) for f in BLUE_FACTS]
    record["generated"]["verify"]["factsHash"] = facts_hash(record)
    return record


def _great_tit() -> Record:
    record = reviewed_record("Q25485")
    record["slug"] = {"sv": "talgoxe", "en": "great-tit"}
    return record


def _cell(text: str, *ids: str) -> Cell:
    return Cell(text=text, fact_ids=list(ids))


# Species A is the blue tit (blames < talgoxe), species B the great tit (the talgoxe fact
# sheet from text_fixtures).
SV_COMPARE = CompareLang(
    short_answer=[
        Sentence(
            text="Blåmesen har blå hätta, talgoxen har svart huvud.", fact_ids=["a:f01", "b:f01"]
        )
    ],
    rows=[
        Row(
            feature="Huvud",
            a=_cell("Blå hätta och vita kinder", "a:f01"),
            b=_cell("Svart huvud med vita kinder", "b:f01"),
        ),
        Row(
            feature="Buk",
            a=_cell("Gul", "a:f02"),
            b=_cell("Gul med svart band", "b:f02"),
        ),
        Row(
            feature="Sång",
            a=_cell("Ett klart tsi-tsi-tsirr", "a:f04"),
            b=_cell("Ringande ti ta, ti ta", "b:f04"),
        ),
    ],
    meta_description=(
        "Blåmes eller talgoxe? Blåmesen är mindre med blå hätta, talgoxen är större och har "
        "svart huvud. Så skiljer du dem åt i fält."
    ),
)
EN_COMPARE = CompareLang(
    short_answer=[
        Sentence(
            text="The blue tit has a blue cap, the great tit a black head.",
            fact_ids=["a:f01", "b:f01"],
        )
    ],
    rows=[
        Row(
            feature="Head",
            a=_cell("Blue cap and white cheeks", "a:f01"),
            b=_cell("Black head with white cheeks", "b:f01"),
        ),
        Row(
            feature="Belly",
            a=_cell("Yellow", "a:f02"),
            b=_cell("Yellow with a black stripe", "b:f02"),
        ),
        Row(
            feature="Song",
            a=_cell("A clear tsi-tsi-tsirr", "a:f04"),
            b=_cell("A ringing tee cha, tee cha", "b:f04"),
        ),
    ],
    meta_description=(
        "Eurasian Blue Tit or Great Tit? The blue tit is smaller with a blue cap, the great "
        "tit is larger with a black head. How to tell them apart."
    ),
)
COMPARE = CompareOutput(sv=SV_COMPARE, en=EN_COMPARE)
CTX = pair_context(_blue_tit(), _great_tit())


def _verdicts(text: CompareOutput, unsupported: dict[str, str] | None = None) -> CheckOutput:
    bad = unsupported or {}
    return CheckOutput(
        verdicts=[
            Verdict(id=i.id, supported=i.id not in bad, problem=bad.get(i.id))
            for i in compare_items(text, CTX)
        ]
    )


VOLUMES_HEADER = "a_qid,b_qid,a_sv,b_sv,a_en,b_en,sv_queries,en_queries,sv_volume,en_volume\n"
ONE_PAIR = "Q25404,Q25485,Blåmes,Talgoxe,x,x,x,x,1300,880\n"


def _repo_with_pair(tmp_path: Path, rows: str = ONE_PAIR) -> WebPaths:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25404"), _blue_tit())
    save_record(record_path(paths.data_out, "Q25485"), _great_tit())
    paths.review.mkdir(parents=True, exist_ok=True)
    (paths.review / "comparison-volumes.csv").write_text(VOLUMES_HEADER + rows, encoding="utf-8")
    return paths


def _changed_great_tit() -> Record:
    """The great tit after a later `web facts` + `web verify`: one more fact, verified."""
    great = _great_tit()
    great["facts"].append(
        {
            "id": "f99",
            "topic": "behaviour",
            "sv": "Den äter frön och insekter.",
            "sources": [{"article": "sv", "quote": "äter frön och insekter"}],
        }
    )
    great["generated"]["verify"]["factsHash"] = facts_hash(great)
    return great


def _saved(paths: WebPaths) -> Record | None:
    return load_record(paths.comparisons_out / "Q25404_Q25485.json")


async def test_a_comparison_is_written_with_both_slugs_and_volumes(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    client = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    assert client.models == ["claude-opus-5-5", "claude-sonnet-5"]  # Albin's choice
    assert client.schemas == ["CompareOutput", "CheckOutput"]
    saved = _saved(paths)
    assert saved is not None
    assert saved["a"] == "Q25404"
    assert saved["b"] == "Q25485"
    assert saved["status"] == "ok"
    assert saved["slug"] == {"sv": "blames-eller-talgoxe", "en": "eurasian-blue-tit-vs-great-tit"}
    assert saved["volumes"] == {"sv": 1300, "en": 880}
    assert saved["publish"] is False
    assert saved["errors"] == []
    assert saved["text"]["sv"]["rows"][0]["a"] == {
        "text": "Blå hätta och vita kinder",
        "factIds": ["a:f01"],
    }
    assert saved["text"]["en"]["shortAnswer"] == [
        {
            "text": "The blue tit has a blue cap, the great tit a black head.",
            "factIds": ["a:f01", "b:f01"],
        }
    ]
    assert saved["text"]["sv"]["metaDescription"].startswith("Blåmes eller talgoxe?")
    assert "rejectedText" not in saved
    generated = saved["generated"]
    assert generated["prompt"] == "compare-v1"
    assert len(generated["promptHash"]) == 8
    assert generated["checker"] == "claude-sonnet-5"
    assert generated["checkerPrompt"] == "check-v1"
    assert generated["factsHash"] == facts_hash(_blue_tit()) + facts_hash(_great_tit())
    assert any(p.name.startswith("web-compare-") for p in paths.reports.iterdir())


def test_a_cell_may_only_cite_its_own_species() -> None:
    wrong = SV_COMPARE.model_copy(
        update={
            "rows": [
                *SV_COMPARE.rows[:2],
                Row(feature="Sång", a=_cell("Ringande", "b:f04"), b=_cell("Ringande", "b:f04")),
            ]
        }
    )
    issues = check_compare(CompareOutput(sv=wrong, en=EN_COMPARE), CTX, BANNED)
    assert [(i.path, i.removable) for i in issues] == [("sv.rows[2]", True)]


def test_the_valid_comparison_passes_every_code_check() -> None:
    assert check_compare(COMPARE, CTX, BANNED) == []


def test_a_short_answer_may_cite_both_species() -> None:
    both = SV_COMPARE.model_copy(
        update={
            "short_answer": [
                Sentence(text="Båda har svart huvud med vita kinder.", fact_ids=["a:f01", "b:f01"])
            ]
        }
    )
    assert check_compare(CompareOutput(sv=both, en=EN_COMPARE), CTX, BANNED) == []


def test_a_meta_description_with_digits_breaks_a_rule() -> None:
    digits = SV_COMPARE.model_copy(
        update={
            "meta_description": (
                "Blåmes eller talgoxe? Blåmesen är 12 cm med blå hätta, talgoxen är 14 cm och "
                "har svart huvud. Så skiljer du dem åt i fält."
            )
        }
    )
    issues = check_compare(CompareOutput(sv=digits, en=EN_COMPARE), CTX, BANNED)
    assert [(i.path, i.removable) for i in issues] == [("sv.meta_description", False)]


def test_too_few_rows_is_a_hard_problem() -> None:
    two_rows = SV_COMPARE.model_copy(update={"rows": SV_COMPARE.rows[:2]})
    issues = check_compare(CompareOutput(sv=two_rows, en=EN_COMPARE), CTX, BANNED)
    assert [(i.path, i.removable) for i in issues] == [("sv.rows", False)]


def test_checker_items_name_the_species_each_cell_is_about() -> None:
    items = {i.id: i for i in compare_items(COMPARE, CTX, names=(_blue_tit(), _great_tit()))}
    assert items["sv.rows[0].a"].text == "Huvud (Blåmes): Blå hätta och vita kinder"
    assert items["en.rows[0].b"].text == "Head (Great Tit): Black head with white cheeks"
    assert [f["id"] for f in items["sv.rows[1].b"].facts] == ["b:f02"]
    # M2: side a's fact is the blue tit's own, side b's the great tit's.
    assert [f["sv"] for f in items["sv.rows[0].a"].facts] == [
        "Blåmesen har blå hätta och vita kinder."
    ]
    assert [f["sv"] for f in items["sv.rows[0].b"].facts] == [
        "Talgoxen har svart huvud med vita kinder."
    ]
    assert items["sv.short_answer[0]"].text == "Blåmesen har blå hätta, talgoxen har svart huvud."


async def test_a_pair_waits_until_both_fact_sheets_are_reviewed(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    blue = _blue_tit()
    del blue["verification"]
    save_record(record_path(paths.data_out, "Q25404"), blue)
    client = FakeJsonClient([])
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]
    assert outcomes[0].errors == ["Blåmes: faktabladet är inte kontrollerat"]
    assert client.calls == []
    assert _saved(paths) is None


async def test_a_pair_waits_while_a_fact_sheet_misses_a_required_topic(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    great = _great_tit()
    great["facts"] = [f for f in great["facts"] if f["id"] != "f04"]
    great["generated"]["verify"]["factsHash"] = facts_hash(great)
    save_record(record_path(paths.data_out, "Q25485"), great)
    client = FakeJsonClient([])
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]
    assert outcomes[0].errors == ["Talgoxe: faktabladet saknas eller misslyckades: kör web facts"]
    assert client.calls == []


async def test_a_stale_verify_hash_counts_as_not_reviewed(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    great = _great_tit()
    great["generated"]["verify"]["factsHash"] = "stale0000"
    save_record(record_path(paths.data_out, "Q25485"), great)
    client = FakeJsonClient([])
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]
    assert client.calls == []


async def test_about_names_both_sides_for_the_checker(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    client = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    await run_compare(paths, CompareOptions(), client=client, now=NOW)
    checker_prompt = str(client.calls[1][0]["content"])
    assert (
        "side a: Blåmes / Eurasian Blue Tit (Cyanistes caeruleus); "
        "side b: Talgoxe / Great Tit (Parus major)"
    ) in checker_prompt
    # M5: the checker is told which side each id prefix belongs to.
    assert "ids starting with a: are facts about side a" in checker_prompt
    assert "a look-alike fact may describe the other side" in checker_prompt
    assert "Huvud (Blåmes): Blå hätta och vita kinder" in checker_prompt
    assert "a:f01: Blåmesen har blå hätta och vita kinder." in checker_prompt
    writer_prompt = str(client.calls[0][0]["content"])
    # M2: each side's own fact text reaches the writer under its own prefix.
    assert "a:f01 [utseende] Blåmesen har blå hätta och vita kinder." in writer_prompt
    assert "b:f01 [utseende] Talgoxen har svart huvud med vita kinder." in writer_prompt


async def test_a_flipped_volume_row_is_normalised(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path, "Q25485,Q25404,Talgoxe,Blåmes,x,x,x,x,1300,880\n")
    client = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [(o.qid, o.status) for o in outcomes] == [("Q25404_Q25485", "ok")]
    saved = _saved(paths)
    assert saved is not None
    assert (saved["a"], saved["b"]) == ("Q25404", "Q25485")
    assert saved["volumes"] == {"sv": 1300, "en": 880}


async def test_flipped_duplicate_rows_with_different_volumes_stop_before_any_call(
    tmp_path: Path,
) -> None:
    paths = _repo_with_pair(tmp_path, ONE_PAIR + "Q25485,Q25404,Talgoxe,Blåmes,x,x,x,x,900,880\n")
    client = FakeJsonClient([])
    with pytest.raises(ValueError, match="Q25404"):
        await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert client.calls == []


async def test_flipped_duplicate_rows_with_the_same_volumes_write_one_comparison(
    tmp_path: Path,
) -> None:
    paths = _repo_with_pair(tmp_path, ONE_PAIR + "Q25485,Q25404,Talgoxe,Blåmes,x,x,x,x,1300,880\n")
    client = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]


async def test_a_pair_that_is_not_a_candidate_cannot_take_a_top_slot(tmp_path: Path) -> None:
    # Q25404/Q99999 has the biggest volume in the hand-edited file, but it is not a
    # look-alike pair (there is no Q99999 record), so it must not push the real pair out.
    paths = _repo_with_pair(tmp_path, ONE_PAIR + "Q25404,Q99999,Blåmes,Okänd,x,x,x,x,9000,9000\n")
    client = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    outcomes = await run_compare(paths, CompareOptions(top=1), client=client, now=NOW)
    assert [(o.qid, o.status) for o in outcomes] == [("Q25404_Q25485", "ok")]


async def test_the_writer_and_the_checker_must_be_different_models(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    with pytest.raises(ValueError, match="olika modeller"):
        await run_compare(
            paths,
            CompareOptions(model_key="sonnet", checker_key="sonnet"),
            client=FakeJsonClient([]),
            now=NOW,
        )


async def test_the_cost_cap_skips_and_still_writes_the_report(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    client = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    outcomes = await run_compare(paths, CompareOptions(max_cost=0.0001), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]
    assert "kostnadstaket nåddes" in outcomes[0].errors[0]
    assert len(client.calls) == 1
    assert _saved(paths) is None
    assert any(p.name.startswith("web-compare-") for p in paths.reports.iterdir())


async def test_an_unsupported_cell_removes_its_row(tmp_path: Path) -> None:
    four_rows = SV_COMPARE.model_copy(
        update={
            "rows": [
                *SV_COMPARE.rows,
                Row(
                    feature="Storlek",
                    a=_cell("Cirka 12 centimeter", "a:f03"),
                    b=_cell("Cirka 14 centimeter", "b:f03"),
                ),
            ]
        }
    )
    text = CompareOutput(sv=four_rows, en=EN_COMPARE)
    bad = {"sv.rows[3].a": "storleken gäller talgoxen"}
    paths = _repo_with_pair(tmp_path)
    client = FakeJsonClient(
        [reply(text), reply(_verdicts(text, bad)), reply(text), reply(_verdicts(text, bad))]
    )
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    assert any('sv.rows[3].a togs bort ("Cirka 12 centimeter")' in n for n in outcomes[0].notes)
    saved = _saved(paths)
    assert saved is not None
    assert [r["feature"] for r in saved["text"]["sv"]["rows"]] == ["Huvud", "Buk", "Sång"]


async def test_fewer_than_three_rows_after_the_check_gives_no_page(tmp_path: Path) -> None:
    bad = {"sv.rows[2].b": "sången stöds inte"}
    paths = _repo_with_pair(tmp_path)
    client = FakeJsonClient(
        [
            reply(COMPARE),
            reply(_verdicts(COMPARE, bad)),
            reply(COMPARE),
            reply(_verdicts(COMPARE, bad)),
        ]
    )
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    saved = _saved(paths)
    assert saved is not None
    assert saved["status"] == "failed"
    assert saved["text"] is None
    assert len(saved["rejectedText"]["sv"]["rows"]) == 2
    assert saved["publish"] is False


async def test_a_current_comparison_is_skipped_unless_regenerated(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    first = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    await run_compare(paths, CompareOptions(), client=first, now=NOW)
    again = FakeJsonClient([])
    outcomes = await run_compare(paths, CompareOptions(), client=again, now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]
    assert again.calls == []
    redo = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    outcomes = await run_compare(paths, CompareOptions(regenerate=True), client=redo, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]


async def test_a_failed_rewrite_keeps_a_good_comparison(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    first = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    await run_compare(paths, CompareOptions(), client=first, now=NOW)
    path = paths.comparisons_out / "Q25404_Q25485.json"
    before = path.read_bytes()
    bad = FakeJsonClient([reply(None, stop="max_tokens")])
    outcomes = await run_compare(paths, CompareOptions(regenerate=True), client=bad, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert any("den tidigare jämförelsen behölls" in n for n in outcomes[0].notes)
    assert path.read_bytes() == before


async def test_a_failed_rewrite_of_a_stale_unpublished_comparison_replaces_it(
    tmp_path: Path,
) -> None:
    # Mirrors text_step's `_keep_old_text`: a stale, unpublished text must not stay "ok",
    # or `web publish` could later put a comparison built from old facts on the site.
    paths = _repo_with_pair(tmp_path)
    first = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    await run_compare(paths, CompareOptions(), client=first, now=NOW)
    great = _changed_great_tit()
    save_record(record_path(paths.data_out, "Q25485"), great)
    bad = FakeJsonClient([reply(None, stop="refusal")])
    outcomes = await run_compare(paths, CompareOptions(), client=bad, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    saved = _saved(paths)
    assert saved is not None
    assert saved["status"] == "failed"
    assert saved["text"] is None


async def test_a_published_comparison_keeps_publish_and_warns_when_facts_changed(
    tmp_path: Path,
) -> None:
    paths = _repo_with_pair(tmp_path)
    first = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    await run_compare(paths, CompareOptions(), client=first, now=NOW)
    path = paths.comparisons_out / "Q25404_Q25485.json"
    published = load_record(path)
    assert published is not None
    published["publish"] = True
    save_record(path, published)
    great = _changed_great_tit()
    save_record(record_path(paths.data_out, "Q25485"), great)
    bad = FakeJsonClient([reply(None, stop="refusal")])
    outcomes = await run_compare(paths, CompareOptions(), client=bad, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert any("sätt publish: false" in e for e in outcomes[0].errors)
    saved = load_record(path)
    assert saved is not None
    assert saved["publish"] is True
    assert saved["status"] == "ok"


async def test_a_rewrite_never_sets_publish_but_keeps_it(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    first = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    await run_compare(paths, CompareOptions(), client=first, now=NOW)
    path = paths.comparisons_out / "Q25404_Q25485.json"
    published = load_record(path)
    assert published is not None
    published["publish"] = True
    save_record(path, published)
    redo = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    await run_compare(paths, CompareOptions(regenerate=True), client=redo, now=NOW)
    saved = load_record(path)
    assert saved is not None
    assert saved["publish"] is True


async def test_one_pair_failing_does_not_stop_the_run(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    client = FakeJsonClient([])  # the writer call raises IndexError inside the fake
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert "IndexError" in outcomes[0].errors[0]
    assert _saved(paths) is None
    assert any(p.name.startswith("web-compare-") for p in paths.reports.iterdir())


async def test_a_checker_failure_leaves_no_file(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    client = FakeJsonClient([reply(COMPARE), reply(None, stop="refusal")])
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert _saved(paths) is None


# -- review fix 2026-10-06 ---------------------------------------------------------------


def _publish(paths: WebPaths) -> Path:
    path = paths.comparisons_out / "Q25404_Q25485.json"
    comparison = load_record(path)
    assert comparison is not None
    comparison["publish"] = True
    save_record(path, comparison)
    return path


def _strike(record: Record, fact_id: str) -> Record:
    """A spot-check strike as `web import` leaves it: the fact is gone and the verification
    is refreshed for the facts that remain."""
    record["facts"] = [f for f in record["facts"] if f["id"] != fact_id]
    record["generated"]["verify"]["factsHash"] = facts_hash(record)
    return record


async def _written_and_published(tmp_path: Path, rows: str = ONE_PAIR) -> tuple[WebPaths, Path]:
    paths = _repo_with_pair(tmp_path, rows)
    first = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    outcomes = await run_compare(paths, CompareOptions(), client=first, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    return paths, _publish(paths)


# I1: a published comparison never keeps a struck fact quietly.


async def test_a_published_comparison_citing_a_struck_fact_fails_loudly(tmp_path: Path) -> None:
    paths, path = await _written_and_published(tmp_path)
    before = path.read_bytes()
    # A spot check strikes f04, the great tit's only voice fact, which the published
    # comparison cites in its song row. The pair now waits for a new fact sheet.
    save_record(record_path(paths.data_out, "Q25485"), _strike(_great_tit(), "f04"))
    client = FakeJsonClient([])
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert any("b:f04" in e and "sätt publish: false" in e for e in outcomes[0].errors), outcomes
    assert client.calls == []
    assert path.read_bytes() == before


async def test_a_failed_rewrite_of_a_published_comparison_names_the_struck_fact(
    tmp_path: Path,
) -> None:
    paths, path = await _written_and_published(tmp_path)
    # f02 is cited by the belly row; the great tit keeps f01, so it is still writable.
    save_record(record_path(paths.data_out, "Q25485"), _strike(_great_tit(), "f02"))
    bad = FakeJsonClient([reply(None, stop="max_tokens")])
    outcomes = await run_compare(paths, CompareOptions(), client=bad, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert any(
        "b:f02" in e and "inte längre finns" in e and "sätt publish: false" in e
        for e in outcomes[0].errors
    ), outcomes[0].errors
    saved = load_record(path)
    assert saved is not None
    assert saved["publish"] is True


def _zero_volumes() -> str:
    return "Q25404,Q25485,Blåmes,Talgoxe,x,x,x,x,0,0\n"


async def test_a_published_comparison_outside_the_top_with_changed_facts_is_reported(
    tmp_path: Path,
) -> None:
    paths, path = await _written_and_published(tmp_path)
    # The pair drops out of the run (no search volume any more), and the facts move on.
    (paths.review / "comparison-volumes.csv").write_text(
        VOLUMES_HEADER + _zero_volumes(), encoding="utf-8"
    )
    save_record(record_path(paths.data_out, "Q25485"), _changed_great_tit())
    client = FakeJsonClient([])
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [(o.qid, o.status) for o in outcomes] == [("Q25404_Q25485", "failed")]
    assert any("sätt publish: false" in e for e in outcomes[0].errors)
    assert client.calls == []
    saved = load_record(path)
    assert saved is not None
    assert saved["publish"] is True


async def test_a_published_comparison_outside_the_top_citing_a_struck_fact_names_it(
    tmp_path: Path,
) -> None:
    paths, _ = await _written_and_published(tmp_path)
    (paths.review / "comparison-volumes.csv").write_text(
        VOLUMES_HEADER + _zero_volumes(), encoding="utf-8"
    )
    # f02 (the blue tit's belly) is cited by the published belly row.
    save_record(record_path(paths.data_out, "Q25404"), _strike(_blue_tit(), "f02"))
    client = FakeJsonClient([])
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert any("a:f02" in e and "sätt publish: false" in e for e in outcomes[0].errors)
    assert client.calls == []


async def test_a_current_published_comparison_outside_the_top_is_not_reported(
    tmp_path: Path,
) -> None:
    paths, _ = await _written_and_published(tmp_path)
    (paths.review / "comparison-volumes.csv").write_text(
        VOLUMES_HEADER + _zero_volumes(), encoding="utf-8"
    )
    outcomes = await run_compare(paths, CompareOptions(), client=FakeJsonClient([]), now=NOW)
    assert outcomes == []


# I2: whether a comparison was written from the facts as they are now.


async def test_comparison_is_current_follows_both_fact_sheets(tmp_path: Path) -> None:
    paths = _repo_with_pair(tmp_path)
    client = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    await run_compare(paths, CompareOptions(), client=client, now=NOW)
    saved = _saved(paths)
    assert saved is not None
    records = {"Q25404": _blue_tit(), "Q25485": _great_tit()}
    assert comparison_is_current(saved, records)
    assert not comparison_is_current(saved, {**records, "Q25485": _changed_great_tit()})
    assert not comparison_is_current(saved, {"Q25404": _blue_tit()})
    assert not comparison_is_current({**saved, "generated": {}}, records)


# N1 (review fix 2026-10-06, probe A/B): `sides` attributes a published comparison's
# problem to the species that actually caused it, so `waves.py`'s `--species` mode never
# reports (and never fails) a comparison over a change on the OTHER, unselected side.


async def test_published_comparison_errors_with_sides_hides_an_unrelated_hash_mismatch(
    tmp_path: Path,
) -> None:
    """Probe A: only the great tit (side b) moved on. Naming just the blue tit (side a)
    must see no problem at all."""
    paths, _ = await _written_and_published(tmp_path)
    saved = _saved(paths)
    assert saved is not None
    records = {"Q25404": _blue_tit(), "Q25485": _changed_great_tit()}
    assert published_comparison_errors(saved, records) == [STALE_HASH_ERROR]
    assert published_comparison_errors(saved, records, sides={"Q25404"}) == []


async def test_published_comparison_errors_with_sides_still_reports_the_named_sides_mismatch(
    tmp_path: Path,
) -> None:
    """Probe B: the great tit (side b) moved on AND is the named side -- still reported."""
    paths, _ = await _written_and_published(tmp_path)
    saved = _saved(paths)
    assert saved is not None
    records = {"Q25404": _blue_tit(), "Q25485": _changed_great_tit()}
    assert published_comparison_errors(saved, records, sides={"Q25485"}) == [STALE_HASH_ERROR]


async def test_published_comparison_errors_with_sides_hides_an_unrelated_stale_citation(
    tmp_path: Path,
) -> None:
    """The blue tit (side a) loses f02, cited as "a:f02" by the published belly row.
    Naming only the great tit (side b) must see no problem."""
    paths, _ = await _written_and_published(tmp_path)
    saved = _saved(paths)
    assert saved is not None
    records = {"Q25404": _strike(_blue_tit(), "f02"), "Q25485": _great_tit()}
    assert any("a:f02" in e for e in published_comparison_errors(saved, records))
    assert published_comparison_errors(saved, records, sides={"Q25485"}) == []


async def test_published_comparison_errors_with_sides_still_reports_the_named_sides_citation(
    tmp_path: Path,
) -> None:
    paths, _ = await _written_and_published(tmp_path)
    saved = _saved(paths)
    assert saved is not None
    records = {"Q25404": _strike(_blue_tit(), "f02"), "Q25485": _great_tit()}
    errors = published_comparison_errors(saved, records, sides={"Q25404"})
    assert any("a:f02" in e for e in errors)


async def test_published_comparison_errors_treats_a_non_32_char_hash_as_stale_on_both_sides(
    tmp_path: Path,
) -> None:
    """Item 2 (second re-review): a non-string or wrong-length `factsHash` (hand-edited
    JSON, a bug elsewhere) must never crash on `raw[:16]` -- treat both sides as stale,
    so it is reported whichever single side is named."""
    paths, _ = await _written_and_published(tmp_path)
    saved = _saved(paths)
    assert saved is not None
    records = {"Q25404": _blue_tit(), "Q25485": _great_tit()}
    for bad_hash in (12345, None, "", "tooshort", "x" * 40):
        bad = {**saved, "generated": {**saved["generated"], "factsHash": bad_hash}}
        assert published_comparison_errors(bad, records) == [STALE_HASH_ERROR]
        assert published_comparison_errors(bad, records, sides={"Q25404"}) == [STALE_HASH_ERROR]
        assert published_comparison_errors(bad, records, sides={"Q25485"}) == [STALE_HASH_ERROR]


# (c): the shared loop with the comparison callbacks.


def _four_rows() -> CompareOutput:
    rows = [
        *SV_COMPARE.rows,
        Row(
            feature="Storlek",
            a=_cell("Cirka 12 centimeter", "a:f03"),
            b=_cell("Cirka 14 centimeter", "b:f03"),
        ),
    ]
    return CompareOutput(sv=SV_COMPARE.model_copy(update={"rows": rows}), en=EN_COMPARE)


async def test_a_checker_failure_on_the_rewrite_falls_back_to_the_original(
    tmp_path: Path,
) -> None:
    text = _four_rows()
    bad = {"sv.rows[3].a": "storleken stöds inte"}
    paths = _repo_with_pair(tmp_path)
    client = FakeJsonClient(
        [reply(text), reply(_verdicts(text, bad)), reply(text), reply(None, stop="refusal")]
    )
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    assert any("kontrollen av omskrivningen misslyckades" in n for n in outcomes[0].notes)
    saved = _saved(paths)
    assert saved is not None
    assert [r["feature"] for r in saved["text"]["sv"]["rows"]] == ["Huvud", "Buk", "Sång"]


async def test_an_earlier_clean_comparison_beats_a_later_broken_one(tmp_path: Path) -> None:
    # Attempt 1 has a removable problem (an unknown fact id in the size row, which settling
    # drops, leaving three rows). Attempt 2 comes back with a meta description that is far
    # too short, a hard problem. Attempt 1 must win.
    rows = [
        *SV_COMPARE.rows,
        Row(feature="Storlek", a=_cell("Liten", "a:f99"), b=_cell("Större", "b:f03")),
    ]
    attempt1 = CompareOutput(sv=SV_COMPARE.model_copy(update={"rows": rows}), en=EN_COMPARE)
    attempt2 = CompareOutput(
        sv=SV_COMPARE, en=EN_COMPARE.model_copy(update={"meta_description": "Kort."})
    )
    paths = _repo_with_pair(tmp_path)
    client = FakeJsonClient([reply(attempt1), reply(attempt2), reply(_verdicts(COMPARE))])
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    saved = _saved(paths)
    assert saved is not None
    assert [r["feature"] for r in saved["text"]["sv"]["rows"]] == ["Huvud", "Buk", "Sång"]
    assert saved["text"]["en"]["metaDescription"] == EN_COMPARE.meta_description


# M1: the English question follows English slug order, the Swedish one Swedish order.


def _marsh_tit() -> Record:
    """Entita / Marsh Tit: first in Swedish slug order (entita < talgoxe), second in
    English (great-tit < marsh-tit)."""
    record = _blue_tit()
    record["names"] = {"sv": "Entita", "en": "Marsh Tit", "scientific": "Poecile palustris"}
    record["slug"] = {"sv": "entita", "en": "marsh-tit"}
    return record


def test_the_english_meta_question_follows_english_slug_order() -> None:
    template = (PIPELINE / "prompts" / "compare-v1.md").read_text(encoding="utf-8")
    system, _ = render_compare_prompt(template, _marsh_tit(), _great_tit(), BANNED)
    assert '"Entita eller Talgoxe?"' in system
    assert '"Great Tit or Marsh Tit?"' in system
    assert meta_openings(_marsh_tit(), _great_tit()) == {
        "sv": "Entita eller Talgoxe?",
        "en": "Great Tit or Marsh Tit?",
    }


def test_a_meta_description_must_open_with_the_expected_question() -> None:
    a, b = _marsh_tit(), _great_tit()
    ctx = pair_context(a, b)
    rest = EN_COMPARE.meta_description.removeprefix("Eurasian Blue Tit or Great Tit?")
    sv_meta = "Entita eller talgoxe?" + SV_COMPARE.meta_description.removeprefix(
        "Blåmes eller talgoxe?"
    )
    good = CompareOutput(
        sv=SV_COMPARE.model_copy(update={"meta_description": sv_meta}),
        en=EN_COMPARE.model_copy(update={"meta_description": "Great Tit or Marsh Tit?" + rest}),
    )
    wrong = good.model_copy(
        update={
            "en": EN_COMPARE.model_copy(
                update={"meta_description": "Marsh Tit or Great Tit?" + rest}
            )
        }
    )
    openings = meta_openings(a, b)
    assert check_compare(good, ctx, BANNED, openings=openings) == []
    issues = check_compare(wrong, ctx, BANNED, openings=openings)
    assert [(i.path, i.removable) for i in issues] == [("en.meta_description", False)]


# M6: same-orientation duplicate rows with different volumes.


def test_read_volumes_rejects_duplicate_rows_with_different_volumes(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    path.write_text(
        VOLUMES_HEADER + ONE_PAIR + "Q25404,Q25485,Blåmes,Talgoxe,x,x,x,x,900,880\n",
        encoding="utf-8",
    )
    with pytest.raises(ValueError, match="Q25404/Q25485"):
        read_volumes(path)


def test_read_volumes_accepts_identical_duplicate_rows(tmp_path: Path) -> None:
    path = tmp_path / "comparison-volumes.csv"
    path.write_text(VOLUMES_HEADER + ONE_PAIR + ONE_PAIR, encoding="utf-8")
    assert read_volumes(path) == {Pair("Q25404", "Q25485"): (1300, 880)}


# M7: an early error never leaves an Anthropic client open.


class _CountingClient:
    created = 0

    def __init__(self) -> None:
        type(self).created += 1

    async def aclose(self) -> None:
        pass


async def test_compare_creates_no_client_before_its_setup_succeeds(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    import birdy_fetcher.web.compare as compare_module

    _CountingClient.created = 0
    monkeypatch.setattr(compare_module, "AnthropicJsonClient", _CountingClient)
    paths = _repo_with_pair(tmp_path)
    paths.banned.unlink()
    with pytest.raises(FileNotFoundError):
        await run_compare(paths, CompareOptions(), now=NOW)
    assert _CountingClient.created == 0


async def test_write_creates_no_client_before_its_setup_succeeds(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    import birdy_fetcher.web.text_step as text_step_module

    _CountingClient.created = 0
    monkeypatch.setattr(text_step_module, "AnthropicJsonClient", _CountingClient)
    paths = _repo_with_pair(tmp_path)
    paths.banned.unlink()
    with pytest.raises(FileNotFoundError):
        await run_write(paths, WriteOptions(wave=1), now=NOW)
    assert _CountingClient.created == 0


# Follow-up 2026-10-06: the opening tolerates curly apostrophes and a leading space, and
# the keep-old path judges a comparison in the orientation it was stored in.


def _montagus_harrier() -> Record:
    """Ängshök / Montagu's Harrier: first in Swedish slug order (angshok < talgoxe),
    second in English (great-tit < montagus-harrier), with an apostrophe in the name."""
    record = _blue_tit()
    record["names"] = {"sv": "Ängshök", "en": "Montagu's Harrier", "scientific": "Circus pygargus"}
    record["slug"] = {"sv": "angshok", "en": "montagus-harrier"}
    return record


def test_the_meta_opening_tolerates_a_curly_apostrophe_and_a_leading_space() -> None:
    a, b = _montagus_harrier(), _great_tit()
    openings = meta_openings(a, b)
    assert openings["en"] == "Great Tit or Montagu's Harrier?"
    sv_rest = SV_COMPARE.meta_description.removeprefix("Blåmes eller talgoxe?")
    en_rest = EN_COMPARE.meta_description.removeprefix("Eurasian Blue Tit or Great Tit?")
    text = CompareOutput(
        sv=SV_COMPARE.model_copy(update={"meta_description": " Ängshök eller talgoxe?" + sv_rest}),
        en=EN_COMPARE.model_copy(
            update={"meta_description": " Great Tit or Montagu\u2019s Harrier?" + en_rest}
        ),
    )
    assert check_compare(text, pair_context(a, b), BANNED, openings=openings) == []


async def test_a_failed_rewrite_keeps_a_current_comparison_stored_in_the_old_orientation(
    tmp_path: Path,
) -> None:
    paths = _repo_with_pair(tmp_path)
    first = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    await run_compare(paths, CompareOptions(), client=first, now=NOW)
    path = paths.comparisons_out / "Q25404_Q25485.json"
    before = path.read_bytes()
    # A Swedish slug rename flips the pair: the great tit now comes first, while the
    # stored comparison (and its facts hash) still has the blue tit as side a.
    blue = _blue_tit()
    blue["slug"] = {"sv": "ublames", "en": "eurasian-blue-tit"}
    save_record(record_path(paths.data_out, "Q25404"), blue)
    bad = FakeJsonClient([reply(None, stop="max_tokens")])
    outcomes = await run_compare(paths, CompareOptions(regenerate=True), client=bad, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert any("den tidigare jämförelsen behölls" in n for n in outcomes[0].notes)
    assert path.read_bytes() == before


async def test_a_flipped_orientation_alone_does_not_warn_about_changed_facts(
    tmp_path: Path,
) -> None:
    paths, path = await _written_and_published(tmp_path)
    blue = _blue_tit()
    blue["slug"] = {"sv": "ublames", "en": "eurasian-blue-tit"}
    save_record(record_path(paths.data_out, "Q25404"), blue)
    bad = FakeJsonClient([reply(None, stop="max_tokens")])
    outcomes = await run_compare(paths, CompareOptions(regenerate=True), client=bad, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert not any("faktabladen har ändrats" in e for e in outcomes[0].errors), outcomes[0].errors
    assert not any("sätt publish: false" in e for e in outcomes[0].errors)
    saved = load_record(path)
    assert saved is not None
    assert saved["publish"] is True
    assert saved["status"] == "ok"


# I5 and I6 (final review 2026-10-06).


def test_the_meta_description_is_a_checker_item_with_every_cited_fact() -> None:
    items = {i.id: i for i in compare_items(COMPARE, CTX)}
    meta = items["sv.meta_description"]
    assert meta.text == SV_COMPARE.meta_description
    assert [f["id"] for f in meta.facts] == ["a:f01", "b:f01", "a:f02", "b:f02", "a:f04", "b:f04"]
    assert "en.meta_description" in items


async def test_an_unsupported_meta_description_that_stays_unsupported_gives_no_page(
    tmp_path: Path,
) -> None:
    bad = {"en.meta_description": "says the blue tit is smaller"}
    paths = _repo_with_pair(tmp_path)
    client = FakeJsonClient(
        [
            reply(COMPARE),
            reply(_verdicts(COMPARE, bad)),
            reply(COMPARE),
            reply(_verdicts(COMPARE, bad)),
        ]
    )
    outcomes = await run_compare(paths, CompareOptions(), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert any("meta_description" in e for e in outcomes[0].errors)


async def test_a_failed_comparison_is_not_paid_for_again_unless_regenerated(
    tmp_path: Path,
) -> None:
    bad = {"sv.rows[2].b": "sången stöds inte"}
    paths = _repo_with_pair(tmp_path)
    first = FakeJsonClient(
        [
            reply(COMPARE),
            reply(_verdicts(COMPARE, bad)),
            reply(COMPARE),
            reply(_verdicts(COMPARE, bad)),
        ]
    )
    await run_compare(paths, CompareOptions(), client=first, now=NOW)
    again = FakeJsonClient([])
    outcomes = await run_compare(paths, CompareOptions(), client=again, now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]
    assert "--retry-failed" in outcomes[0].errors[0]
    assert again.calls == []
    redo = FakeJsonClient([reply(COMPARE), reply(_verdicts(COMPARE))])
    outcomes = await run_compare(paths, CompareOptions(retry_failed=True), client=redo, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    # --retry-failed never rewrites a comparison that is already fine.
    quiet = FakeJsonClient([])
    outcomes = await run_compare(paths, CompareOptions(retry_failed=True), client=quiet, now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]
