from __future__ import annotations

import textwrap
from pathlib import Path

import pytest

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
