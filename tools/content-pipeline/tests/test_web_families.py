"""Tests for web/families.py: one Swedish name per Latin family (re-review 2026-10-07)."""

from __future__ import annotations

from pathlib import Path

import pytest
import yaml

from birdy_fetcher.web.families import FAMILY_SV, UnknownFamilyError, family_sv

SPECIES_ROOT = Path(__file__).resolve().parents[3] / "shared" / "content" / "species"


def test_every_family_among_birdys_species_has_a_swedish_name() -> None:
    """Fails loudly when a species (approved or not) has a family the table lacks: the
    sources step would stop on it."""
    families: dict[str, list[str]] = {}
    for path in SPECIES_ROOT.rglob("*.yaml"):
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
        families.setdefault(data["taxonomy"]["family"], []).append(data["scientific_name"])
    missing = {f: names[:3] for f, names in families.items() if f not in FAMILY_SV}
    assert missing == {}


def test_the_families_the_review_found_have_one_proper_name() -> None:
    assert FAMILY_SV["Paridae"] == "Mesar"  # was "Mesar" or "Mesfåglar"
    assert FAMILY_SV["Calcariidae"] == "Sporrsparvar"  # was "Fältsparvar", like Emberizidae
    assert FAMILY_SV["Emberizidae"] == "Fältsparvar"
    for latin, swedish in (
        ("Bombycillidae", "Sidensvansar"),
        ("Locustellidae", "Gräsfåglar"),
        ("Panuridae", "Skäggmesar"),
        ("Regulidae", "Kungsfåglar"),
        ("Recurvirostridae", "Skärfläckor"),
    ):
        assert FAMILY_SV[latin] == swedish  # was the Latin name


def test_no_family_has_a_latin_or_shared_swedish_name() -> None:
    assert not [latin for latin, sv in FAMILY_SV.items() if sv.lower() == latin.lower()]
    assert len(set(FAMILY_SV.values())) == len(FAMILY_SV)
    assert all(sv[0].isupper() for sv in FAMILY_SV.values())


def test_an_unknown_family_stops_with_a_clear_error() -> None:
    with pytest.raises(UnknownFamilyError, match="Mysteriidae"):
        family_sv("Mysteriidae")
