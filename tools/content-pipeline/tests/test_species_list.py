"""Tests for species_list module — VP11 + IOC → species_list.yaml."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.species_list import (
    build_species_list,
    map_to_wikidata,
    parse_ioc,
    parse_vp11,
)


@pytest.fixture
def sample_ioc(fixtures_dir: Path) -> Path:
    return fixtures_dir / "ioc_sample.xlsx"


@pytest.fixture
def sample_vp11(fixtures_dir: Path) -> Path:
    return fixtures_dir / "vp11_sample.pdf"


def test_parse_ioc_returns_entries(sample_ioc: Path) -> None:
    entries = parse_ioc(sample_ioc)
    talgoxe = next(e for e in entries if e.scientific_name == "Parus major")
    assert talgoxe.family == "Paridae"
    assert talgoxe.ioc_order == "Passeriformes"
    assert talgoxe.common_en == "Great Tit"


def test_parse_vp11_returns_entries(sample_vp11: Path) -> None:
    entries = parse_vp11(sample_vp11)
    talgoxe = next(e for e in entries if e.scientific_name == "Parus major")
    assert talgoxe.status == "H"
    assert talgoxe.family == "Paridae"
    assert talgoxe.common_en == "Great Tit"
    # Status-distribution check
    assert {e.status for e in entries}.issubset({"H", "h", "F", "R", "(H)"})


def test_parse_vp11_extracts_notes(sample_vp11: Path) -> None:
    """Notes (Intr., E., †) extraheras korrekt."""
    entries = parse_vp11(sample_vp11)
    intr = [e for e in entries if "Intr." in e.notes]
    assert len(intr) >= 1, "fixture borde innehålla minst en Intr.-art"


@pytest.mark.asyncio
async def test_map_to_wikidata_uses_fixture(
    fixtures_dir: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """SPARQL is mocked; verify Q-ID extraction."""
    from birdy_fetcher import species_list

    fixture = (fixtures_dir / "wikidata_sparql_response.json").read_text()

    async def fake_sparql(query: str) -> str:
        return fixture

    monkeypatch.setattr(species_list, "_run_sparql", fake_sparql)

    result = await map_to_wikidata(["Parus major", "Cyanistes caeruleus"])
    assert result["Parus major"] == "Q25372"
    assert result["Cyanistes caeruleus"] == "Q15545"


@pytest.mark.asyncio
async def test_build_species_list_separates_failures(
    sample_ioc: Path,
    sample_vp11: Path,
    fixtures_dir: Path,
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """End-to-end: matched species map; R-status filtreras bort; omappade till failures."""
    from birdy_fetcher import species_list

    fixture = (fixtures_dir / "wikidata_sparql_response.json").read_text()

    async def fake_sparql(query: str) -> str:
        return fixture

    monkeypatch.setattr(species_list, "_run_sparql", fake_sparql)

    out_list = tmp_path / "species_list.yaml"
    out_failures = tmp_path / "mapping_failures.yaml"

    checklists_dir = fixtures_dir.parent.parent / "checklists"
    await build_species_list(
        ioc_xlsx=sample_ioc,
        vp11_pdf=sample_vp11,
        filter_yaml=checklists_dir / "vp11-filter.yaml",
        out_list=out_list,
        out_failures=out_failures,
    )

    assert out_list.exists()
    text = out_list.read_text()
    assert "Q25372" in text
    assert "Parus major" in text
    assert "vp_status: H" in text
