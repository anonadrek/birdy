"""Tests for web/source.py."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.web.source import NotApprovedError, load_approved

YAML_TEMPLATE = """id: {qid}
scientific_name: Parus major
taxonomy:
  family: Paridae
  family_sv: Mesar
  genus: Parus
  ioc_order: Passeriformes
names:
  sv: {sv}
  en: {en}
abundance: allmän
iucn_status: LC
description:
  sv: x
  en: x
migration:
  sv: x
  en: x
{marginalia}image_refs:
- role: hero
  path: {qid}/hero.webp
  width: 2400
  height: 1600
  license: CC0
  author: '<a href="x">Hobbyfotowiki</a>'
  source_url: https://commons.wikimedia.org/wiki/File:A b.jpg
- role: secondary
  path: {qid}/secondary-1.webp
  width: 1800
  height: 1200
  license: CC BY 2.0
  author: Anton Whoa
  source_url: https://commons.wikimedia.org/wiki/File:C.jpg
review_status: {status}
"""


def _write(root: Path, qid: str, sv: str, en: str, status: str, marginalia: str = "") -> None:
    path = root / "paridae" / f"{qid}.yaml"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        YAML_TEMPLATE.format(qid=qid, sv=sv, en=en, status=status, marginalia=marginalia),
        encoding="utf-8",
    )


def test_loads_only_approved(tmp_path: Path) -> None:
    _write(tmp_path, "Q1", "Talgoxe", "Great Tit", "approved")
    _write(tmp_path, "Q2", "Blåmes", "Blue Tit", "auto")
    sources = load_approved(tmp_path)
    assert [s.qid for s in sources] == ["Q1"]
    src = sources[0]
    assert src.name_sv == "Talgoxe"
    assert src.family == "Paridae"
    assert src.family_sv == "Mesar"
    assert src.ioc_order == "Passeriformes"
    assert src.iucn == "LC"
    assert src.marginalia_sv is None
    assert [i.role for i in src.images] == ["hero", "secondary"]
    assert src.images[1].author == "Anton Whoa"


def test_reads_marginalia(tmp_path: Path) -> None:
    _write(
        tmp_path,
        "Q1",
        "Talgoxe",
        "Great Tit",
        "approved",
        marginalia="marginalia:\n  sv: Söker frön.\n  en: Forages.\n",
    )
    src = load_approved(tmp_path)[0]
    assert src.marginalia_sv == "Söker frön."
    assert src.marginalia_en == "Forages."


def test_filter_by_qid_and_reject_unapproved(tmp_path: Path) -> None:
    _write(tmp_path, "Q1", "Talgoxe", "Great Tit", "approved")
    _write(tmp_path, "Q2", "Blåmes", "Blue Tit", "auto")
    _write(tmp_path, "Q3", "Svartmes", "Coal Tit", "approved")
    assert [s.qid for s in load_approved(tmp_path, qids=("Q3",))] == ["Q3"]
    with pytest.raises(NotApprovedError, match="Q2"):
        load_approved(tmp_path, qids=("Q2",))
    with pytest.raises(KeyError, match="Q9"):
        load_approved(tmp_path, qids=("Q9",))
