"""Tests for web/identify.py and source.load_scientific_index."""

from __future__ import annotations

import json
from pathlib import Path

from birdy_fetcher.web.identify import load_coverage
from birdy_fetcher.web.source import load_scientific_index

from .test_web_source import YAML_TEMPLATE


def test_coverage_reads_both_model_maps(tmp_path: Path) -> None:
    (tmp_path / "aiy_to_qid.json").write_text(
        json.dumps({"_meta": {}, "mappings": {"0": "Q1", "1": "Q2", "2": None}}), encoding="utf-8"
    )
    (tmp_path / "birdnet_lite_to_qid.json").write_text(
        json.dumps({"_meta": {}, "mapping": {"33": "Q2", "45": "Q3"}}), encoding="utf-8"
    )
    coverage = load_coverage(tmp_path)
    assert coverage.for_qid("Q1") == {"photo": True, "sound": False}
    assert coverage.for_qid("Q2") == {"photo": True, "sound": True}
    assert coverage.for_qid("Q3") == {"photo": False, "sound": True}
    assert coverage.for_qid("Q4") == {"photo": False, "sound": False}


def test_scientific_index_covers_all_species_lowercase(tmp_path: Path) -> None:
    for qid, status, scientific in (
        ("Q1", "approved", "Parus major"),
        ("Q2", "auto", "Cyanistes caeruleus"),
    ):
        text = YAML_TEMPLATE.format(qid=qid, sv="X", en="X", status=status, marginalia="")
        text = text.replace("scientific_name: Parus major", f"scientific_name: {scientific}")
        path = tmp_path / "x" / f"{qid}.yaml"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    assert load_scientific_index(tmp_path) == {"parus major": "Q1", "cyanistes caeruleus": "Q2"}
