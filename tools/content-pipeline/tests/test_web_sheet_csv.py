"""Tests for the shared CSV helpers in web/sheet_csv.py (fix 2026-10-06, Task 21 review
item 2): BOM-safe reading, formula-injection escaping, and the required-column check."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.web.sheet_csv import read_sheet, write_sheet

COLUMNS = ["a_qid", "b_qid", "note"]


def test_write_sheet_starts_with_a_bom(tmp_path: Path) -> None:
    path = tmp_path / "sheet.csv"
    write_sheet(path, [{"a_qid": "Q1", "b_qid": "Q2", "note": "x"}], COLUMNS)
    assert path.read_bytes().startswith(b"\xef\xbb\xbf")


def test_a_file_starting_with_a_double_bom_reads(tmp_path: Path) -> None:
    path = tmp_path / "sheet.csv"
    path.write_bytes("﻿﻿a_qid,b_qid,note\r\nQ1,Q2,x\r\n".encode())
    rows = read_sheet(path)
    assert rows == [{"a_qid": "Q1", "b_qid": "Q2", "note": "x"}]


def test_a_formula_prefixed_cell_round_trips(tmp_path: Path) -> None:
    path = tmp_path / "sheet.csv"
    write_sheet(path, [{"a_qid": "Q1", "b_qid": "Q2", "note": "=1+2"}], COLUMNS)
    assert read_sheet(path)[0]["note"] == "=1+2"


def test_a_missing_required_column_raises_a_clear_swedish_error(tmp_path: Path) -> None:
    path = tmp_path / "sheet.csv"
    # Saved with ";" instead of "," -- the whole header folds into one column.
    path.write_text("a_qid;b_qid;note\r\nQ1;Q2;x\r\n", encoding="utf-8")
    with pytest.raises(ValueError, match="a_qid"):
        read_sheet(path, required_columns=("a_qid",))


def test_no_required_columns_means_no_check(tmp_path: Path) -> None:
    path = tmp_path / "sheet.csv"
    path.write_text("a_qid;b_qid;note\r\nQ1;Q2;x\r\n", encoding="utf-8")
    # No required_columns given: review_sheet.py's own sheets never hit this check.
    rows = read_sheet(path)
    assert rows == [{"a_qid;b_qid;note": "Q1;Q2;x"}]
