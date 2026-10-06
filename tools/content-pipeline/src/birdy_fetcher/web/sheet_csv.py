"""Shared CSV helpers for Albin's sheets: `review_sheet.py`'s undantag/stickprov sheets
and `compare.py`'s comparison-volumes sheet. BOM-safe reading, formula-injection
escaping, and an atomic write (fix 2026-10-06, Task 21 review item 2 and 4): moved out of
`review_sheet.py` (originally from the review fix `c30f44e2`) so `compare.py` gets the
same hardening instead of a second, drifting copy."""

from __future__ import annotations

import csv
import io
import os
import tempfile
from collections.abc import Sequence
from pathlib import Path

_FORMULA_PREFIXES = ("=", "+", "-", "@", "\t", "\r")
_BOM = b"\xef\xbb\xbf"


def escape_formula(value: str) -> str:
    """A cell starting with one of `_FORMULA_PREFIXES` is read as a formula by
    Sheets/Excel unless escaped with a leading apostrophe."""
    return f"'{value}" if value.startswith(_FORMULA_PREFIXES) else value


def unescape_formula(value: str) -> str:
    """Reverses `escape_formula`: a cell `write_sheet` protected with a leading `'`
    because its real content starts with `=`, `+`, `-`, `@`, tab or CR must have exactly
    that one apostrophe stripped back off on import. An apostrophe that is not followed
    by one of those characters is left alone: it is real content, not our escaping."""
    if len(value) >= 2 and value[0] == "'" and value[1] in _FORMULA_PREFIXES:
        return value[1:]
    return value


def write_sheet(path: Path, rows: list[dict[str, str]], columns: list[str]) -> None:
    """Writes `rows` as UTF-8 with a BOM (`utf-8-sig`, so Excel shows åäö), escaping
    every cell that could be read as a formula, and atomically (fix 2026-10-06, item 4):
    the rows are written to a temporary file in the same directory first, then swapped
    into place with `Path.replace`. A crash or an exception raised while a caller is
    still building `rows` (before this function is even called) therefore never leaves
    the previous good file half-overwritten -- and neither does a failure during the
    write itself, since the real path is never touched until the temp file is complete."""
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(dir=path.parent, prefix=f".{path.name}.", suffix=".tmp")
    tmp_path = Path(tmp_name)
    try:
        with os.fdopen(fd, "w", encoding="utf-8-sig", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=columns, extrasaction="ignore")
            writer.writeheader()
            for row in rows:
                writer.writerow({key: escape_formula(value) for key, value in row.items()})
        tmp_path.replace(path)
    except BaseException:
        tmp_path.unlink(missing_ok=True)
        raise


def read_sheet(path: Path, *, required_columns: Sequence[str] = ()) -> list[dict[str, str]]:
    """Reads a sheet written by `write_sheet` and reverses its formula-injection escaping
    on every cell. Strips every leading UTF-8 BOM, not just the one `encoding="utf-8-sig"`
    would strip on its own: a Google Sheet re-exported on top of an already BOM'd file can
    end up with more than one, and a bare `utf-8-sig` open only removes the first, leaving
    a stray U+FEFF glued onto the header's first column name.

    `required_columns` raises a clear Swedish error naming the missing column if the
    header lacks it (fix 2026-10-06, item 2) -- the usual cause is the sheet having been
    saved with `;` instead of `,`, which folds the whole header into one column."""
    raw = path.read_bytes()
    while raw.startswith(_BOM):
        raw = raw[len(_BOM) :]
    text = raw.decode("utf-8")
    reader = csv.DictReader(io.StringIO(text, newline=""))
    missing = [c for c in required_columns if c not in (reader.fieldnames or ())]
    if missing:
        raise ValueError(f"kolumnen {missing[0]} saknas, sparades filen med semikolon?")
    return [{k: unescape_formula(v or "") for k, v in row.items()} for row in reader]
