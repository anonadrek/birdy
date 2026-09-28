"""Tests for web/groups.py and the shared website/src/data/species-groups.json."""

from __future__ import annotations

import json
import re
from pathlib import Path

import pytest

from birdy_fetcher.web.groups import GroupTable

REPO = Path(__file__).resolve().parents[3]
FAMILY_GROUPS = REPO / "shared/content/src/jvmMain/resources/family_groups.yaml"
WEB_GROUPS = REPO / "website/src/data/species-groups.json"


def _small_tables(tmp_path: Path) -> tuple[Path, Path]:
    yaml_path = tmp_path / "family_groups.yaml"
    yaml_path.write_text(
        "order: [songbirds, owls, other]\n"
        "groups:\n"
        "  songbirds: {keyed_by: order, ioc_order: Passeriformes}\n"
        "  owls: {families: [Strigidae, Tytonidae]}\n"
        "  other: {families: [Cuculidae]}\n",
        encoding="utf-8",
    )
    group = {
        "slug": {"sv": "x", "en": "x"},
        "name": {"sv": "X", "en": "X"},
        "photo": "Q1",
        "intro": {"sv": "a", "en": "a"},
    }
    json_path = tmp_path / "species-groups.json"
    json_path.write_text(
        json.dumps(
            {
                "groups": [
                    {**group, "key": "songbirds", "slug": {"sv": "tattingar", "en": "songbirds"}},
                    {**group, "key": "owls", "slug": {"sv": "ugglor", "en": "owls"}},
                    {**group, "key": "other", "slug": {"sv": "ovriga", "en": "other"}},
                ],
                "common": ["Q1"],
            }
        ),
        encoding="utf-8",
    )
    return yaml_path, json_path


def test_group_for_uses_order_for_passerines(tmp_path: Path) -> None:
    table = GroupTable(*_small_tables(tmp_path))
    assert table.group_for(family="Paridae", ioc_order="Passeriformes") == "songbirds"


def test_group_for_uses_family_and_falls_back_to_other(tmp_path: Path) -> None:
    table = GroupTable(*_small_tables(tmp_path))
    assert table.group_for(family="Strigidae", ioc_order="Strigiformes") == "owls"
    assert table.group_for(family="Unknownidae", ioc_order="X") == "other"


def test_by_key_and_common(tmp_path: Path) -> None:
    table = GroupTable(*_small_tables(tmp_path))
    assert table.by_key("owls").slug_sv == "ugglor"
    assert table.common == ["Q1"]
    with pytest.raises(KeyError):
        table.by_key("nope")


def test_order_mismatch_is_an_error(tmp_path: Path) -> None:
    yaml_path, json_path = _small_tables(tmp_path)
    data = json.loads(json_path.read_text(encoding="utf-8"))
    data["groups"].reverse()
    json_path.write_text(json.dumps(data), encoding="utf-8")
    with pytest.raises(ValueError, match="ordning"):
        GroupTable(yaml_path, json_path)


def test_real_tables_load_and_match_the_app() -> None:
    table = GroupTable(FAMILY_GROUPS, WEB_GROUPS)
    assert len(table.groups) == 15
    assert len(table.common) == 12
    assert len(set(table.common)) == 12


def test_real_intros_follow_the_writing_rules() -> None:
    data = json.loads(WEB_GROUPS.read_text(encoding="utf-8"))
    for group in data["groups"]:
        for lang in ("sv", "en"):
            text = group["intro"][lang]
            where = f"{group['key']}.{lang}"
            assert "—" not in text and "–" not in text and "--" not in text, where  # noqa: RUF001
            assert "!" not in text, where
            sentences = [s for s in re.split(r"(?<=[.?!])\s+", text.strip()) if s]
            assert 2 <= len(sentences) <= 3, where
            assert len(text.split()) <= 60, where
