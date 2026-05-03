"""yaml_writer round-trip and overrides merging."""

from __future__ import annotations

from pathlib import Path

import yaml

from birdy_fetcher.yaml_writer import (
    SpeciesYamlData,
    merge_overrides,
    write_species_yaml,
)


def make_data() -> SpeciesYamlData:
    return SpeciesYamlData(
        wikidata_id="Q25485",
        scientific_name="Parus major",
        family="Paridae",
        family_sv="Mesar",
        genus="Parus",
        ioc_order="Passeriformes",
        common_sv="Talgoxe",
        common_en="Great Tit",
        abundance="allmän",
        iucn_status="LC",
        regions=["SE", "NO", "FI"],
        season={
            "jan": "present",
            "feb": "present",
            "mar": "present",
            "apr": "breeding",
            "may": "breeding",
            "jun": "breeding",
            "jul": "breeding",
            "aug": "present",
            "sep": "present",
            "oct": "present",
            "nov": "present",
            "dec": "present",
        },
        description={"sv": "Talgoxen är ...", "en": "The great tit ..."},
        migration={"sv": "Stationär ...", "en": "Resident ..."},
        image_refs=[],
        review_status="auto",
        review_notes="",
        generated_at="2026-05-02T14:30:00Z",
        sources={
            "wikipedia_sv_revision": 12345678,
            "wikipedia_en_revision": 87654321,
            "wikidata_revision": 1234567,
            "claude_model": "claude-haiku-4-5-20251001",
        },
    )


def test_round_trip(tmp_path: Path) -> None:
    data = make_data()
    out_path = tmp_path / "paridae" / "Q25485.yaml"
    write_species_yaml(data, out_path)
    assert out_path.exists()
    parsed = yaml.safe_load(out_path.read_text(encoding="utf-8"))
    assert parsed["id"] == "Q25485"
    assert parsed["names"]["sv"] == "Talgoxe"
    assert parsed["abundance"] == "allmän"


def test_overrides_replace_description() -> None:
    data = make_data()
    overrides = {
        "Q25485": {
            "description": {"sv": "Manuell svensk text."},
        }
    }
    merged = merge_overrides(data, overrides)
    assert merged.description["sv"] == "Manuell svensk text."
    assert merged.description["en"] == data.description["en"]  # untouched


def test_overrides_accept_missing_marks_field() -> None:
    data = make_data()
    overrides = {
        "Q25485": {
            "description": {"sv": {"accept_missing": True}},
        }
    }
    merged = merge_overrides(data, overrides)
    assert merged.description["sv"] == "[accept_missing]"
