from __future__ import annotations

import json
import textwrap
from pathlib import Path

import pytest

from birdy_eval.__main__ import _load_mapping
from birdy_eval.corpus import CorpusItem, load_corpus


@pytest.fixture()
def tmp_manifest(tmp_path: Path) -> Path:
    manifest = tmp_path / "manifest.yaml"
    manifest.write_text(
        textwrap.dedent("""\
            items:
              - path: corpus/paridae/Q18009.jpg
                qid: Q18009
                family: paridae
              - path: corpus/accipitridae/Q13364.jpg
                qid: Q13364
                family: accipitridae
        """),
        encoding="utf-8",
    )
    return manifest


def test_load_corpus_returns_items(tmp_manifest: Path) -> None:
    items = load_corpus(tmp_manifest)
    assert len(items) == 2


def test_load_corpus_fields(tmp_manifest: Path) -> None:
    items = load_corpus(tmp_manifest)
    first = items[0]
    assert isinstance(first, CorpusItem)
    assert first.qid == "Q18009"
    assert first.family == "paridae"
    assert first.path == Path("corpus/paridae/Q18009.jpg")


def test_load_corpus_empty_manifest(tmp_path: Path) -> None:
    manifest = tmp_path / "manifest.yaml"
    manifest.write_text("items: []\n", encoding="utf-8")
    items = load_corpus(manifest)
    assert items == []


# ---------------------------------------------------------------------------
# _load_mapping tests
# ---------------------------------------------------------------------------


def test_load_mapping_flat(tmp_path: Path) -> None:
    """Flat JSON shape {\"0\": \"Q...\", ...} is loaded correctly."""
    p = tmp_path / "flat.json"
    p.write_text(json.dumps({"0": "Q11111", "1": "Q22222"}), encoding="utf-8")
    result = _load_mapping(p)
    assert result == {0: "Q11111", 1: "Q22222"}


def test_load_mapping_nested(tmp_path: Path) -> None:
    """Nested JSON shape {\"_meta\": {...}, \"mappings\": {...}} is loaded correctly."""
    payload = {
        "_meta": {"version": "1.0"},
        "mappings": {"0": "Q33333", "42": "Q44444"},
    }
    p = tmp_path / "nested.json"
    p.write_text(json.dumps(payload), encoding="utf-8")
    result = _load_mapping(p)
    assert result == {0: "Q33333", 42: "Q44444"}


def test_load_mapping_keys_are_ints(tmp_path: Path) -> None:
    """Keys in the returned dict must be Python ints, not strings."""
    p = tmp_path / "mapping.json"
    p.write_text(json.dumps({"5": "Q55555"}), encoding="utf-8")
    result = _load_mapping(p)
    assert 5 in result
    assert "5" not in result
