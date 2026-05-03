"""Tests for species_list module — VP11 + IOC → species_list.yaml."""

from __future__ import annotations

from pathlib import Path

import pytest
import yaml

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
    assert talgoxe.ioc_order == "Passeriformes"  # Capitalized to match IOC format
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
    assert result["Parus major"] == "Q25485"
    assert result["Cyanistes caeruleus"] == "Q25404"


@pytest.mark.asyncio
async def test_map_to_wikidata_falls_back_for_renamed_taxa(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Names missing from primary wdt:P225 are retried via the synonym query."""
    from birdy_fetcher import species_list

    primary_response = (
        '{"head":{"vars":["item","scientificName"]},'
        '"results":{"bindings":['
        '{"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q25485"},'
        '"scientificName":{"type":"literal","value":"Parus major"}}]}}'
    )
    # Renamed taxon "Botaurus minutus" appears only via the historical-statement query.
    fallback_response = (
        '{"head":{"vars":["item","scientificName"]},'
        '"results":{"bindings":['
        '{"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q26129"},'
        '"scientificName":{"type":"literal","value":"Botaurus minutus"}},'
        '{"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q25587867"},'
        '"scientificName":{"type":"literal","value":"Botaurus minutus"}}]}}'
    )
    calls: list[str] = []

    async def fake_sparql(query: str) -> str:
        calls.append(query)
        return fallback_response if "wikibase:rank" in query else primary_response

    monkeypatch.setattr(species_list, "_run_sparql", fake_sparql)

    result = await map_to_wikidata(["Parus major", "Botaurus minutus"])
    assert result["Parus major"] == "Q25485"
    # Lower Q-number wins on ambiguity (older/more-established item).
    assert result["Botaurus minutus"] == "Q26129"
    assert len(calls) == 2, "fallback query should be issued for the missing name"


@pytest.mark.asyncio
async def test_map_to_wikidata_skips_fallback_when_all_names_resolved(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """If primary query covers everything, the fallback query is not issued."""
    from birdy_fetcher import species_list

    response = (
        '{"head":{"vars":["item","scientificName"]},'
        '"results":{"bindings":['
        '{"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q25485"},'
        '"scientificName":{"type":"literal","value":"Parus major"}}]}}'
    )
    calls: list[str] = []

    async def fake_sparql(query: str) -> str:
        calls.append(query)
        return response

    monkeypatch.setattr(species_list, "_run_sparql", fake_sparql)

    await map_to_wikidata(["Parus major"])
    assert len(calls) == 1


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
    assert "Q25485" in text
    assert "Parus major" in text
    assert "vp_status: H" in text


@pytest.mark.asyncio
async def test_build_species_list_resume_preserves_manual_and_cleans_failures(
    sample_ioc: Path,
    sample_vp11: Path,
    fixtures_dir: Path,
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """--resume: manual entries survive; resolved failures auto-disappear."""
    from birdy_fetcher import species_list

    fixture = (fixtures_dir / "wikidata_sparql_response.json").read_text()

    async def fake_sparql(query: str) -> str:
        return fixture

    monkeypatch.setattr(species_list, "_run_sparql", fake_sparql)

    out_list = tmp_path / "species_list.yaml"
    out_failures = tmp_path / "mapping_failures.yaml"
    checklists_dir = fixtures_dir.parent.parent / "checklists"

    # First run: produces baseline pipeline output
    await build_species_list(
        ioc_xlsx=sample_ioc,
        vp11_pdf=sample_vp11,
        filter_yaml=checklists_dir / "vp11-filter.yaml",
        out_list=out_list,
        out_failures=out_failures,
    )
    pipeline_only = yaml.safe_load(out_list.read_text(encoding="utf-8"))
    pipeline_count = len(pipeline_only)

    # Simulate the post-Task-3 workflow: user added a manual entry for a species
    # that wasn't in the pipeline, and the same name still sits in failures.yaml.
    pipeline_only.append(
        {
            "wikidata_id": "Q99999",
            "scientific_name": "Manualicus addedicus",
            "family": "Manualidae",
            "ioc_order": "Manualiformes",
            "common_en": "Manual Bird",
            "vp_status": "H",
        }
    )
    out_list.write_text(yaml.safe_dump(pipeline_only), encoding="utf-8")
    out_failures.write_text(
        species_list._FAILURES_HEADER
        + yaml.safe_dump(
            [
                {
                    "scientific_name": "Manualicus addedicus",
                    "family": "Manualidae",
                    "common_en": "Manual Bird",
                    "reason": "stale failure entry",
                }
            ]
        ),
        encoding="utf-8",
    )

    # Resume: pipeline reruns, manual entry survives, stale failure disappears
    await build_species_list(
        ioc_xlsx=sample_ioc,
        vp11_pdf=sample_vp11,
        filter_yaml=checklists_dir / "vp11-filter.yaml",
        out_list=out_list,
        out_failures=out_failures,
        resume=True,
    )

    merged = yaml.safe_load(out_list.read_text(encoding="utf-8"))
    names = {e["scientific_name"] for e in merged}
    assert "Parus major" in names, "pipeline entry preserved"
    assert "Manualicus addedicus" in names, "manual addition preserved across resume"
    assert len(merged) == pipeline_count + 1

    final_failures = yaml.safe_load(out_failures.read_text(encoding="utf-8")) or []
    final_names = {f["scientific_name"] for f in final_failures}
    assert "Manualicus addedicus" not in final_names, (
        "resolved failure auto-removed from mapping_failures.yaml"
    )
