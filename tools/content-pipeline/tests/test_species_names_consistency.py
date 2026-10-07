"""species_list.yaml and the committed species YAML agree on the Swedish names.

Release 1.3.0 Task 7m: BirdLife Sverige's official names are set as common_sv (and the name Birdy
used before as former_sv) in species_list.yaml, which a refresh reads, and written into each
species' YAML, which the app's database is built from. If the two drift apart, the next refresh
would silently change a name the app shows.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import pytest
import yaml

REPO = Path(__file__).resolve().parents[3]
SPECIES = REPO / "shared/content/species"
SPECIES_LIST = REPO / "tools/content-pipeline/species_list.yaml"

needs_repo = pytest.mark.skipif(
    not SPECIES.is_dir() or not SPECIES_LIST.is_file(), reason="needs the Birdy repo checkout"
)


def _species_by_id() -> dict[str, dict[str, Any]]:
    out: dict[str, dict[str, Any]] = {}
    for path in SPECIES.rglob("*.yaml"):
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
        out[data["id"]] = data
    return out


def _listed() -> list[dict[str, Any]]:
    data: list[dict[str, Any]] = yaml.safe_load(SPECIES_LIST.read_text(encoding="utf-8"))
    return data


@needs_repo
def test_common_sv_in_species_list_is_the_swedish_name_in_the_species_yaml() -> None:
    species = _species_by_id()
    mismatches = []
    for entry in _listed():
        official = entry.get("common_sv")
        in_yaml = species[entry["wikidata_id"]]["names"]["sv"]
        if official and in_yaml != official:
            mismatches.append(f"{entry['wikidata_id']}: list {official!r}, YAML {in_yaml!r}")
    assert mismatches == []


@needs_repo
def test_former_sv_is_the_same_in_species_list_and_the_species_yaml() -> None:
    species = _species_by_id()
    in_list = {e["wikidata_id"]: e["former_sv"] for e in _listed() if e.get("former_sv")}
    in_yaml = {
        qid: d["names"]["former_sv"] for qid, d in species.items() if d["names"].get("former_sv")
    }
    assert in_list == in_yaml
    assert len(in_list) == 29


@needs_repo
def test_every_renamed_species_has_its_official_name_in_species_list() -> None:
    missing = [e["wikidata_id"] for e in _listed() if e.get("former_sv") and not e.get("common_sv")]
    assert missing == []
