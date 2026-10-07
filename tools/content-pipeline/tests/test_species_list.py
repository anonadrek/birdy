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


@pytest.mark.asyncio
async def test_build_species_list_resume_keeps_hand_set_fields(
    sample_ioc: Path,
    sample_vp11: Path,
    fixtures_dir: Path,
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """--resume keeps the fields a person set on a species the pipeline lists again.

    Release 1.3.0: common_sv/former_sv (BirdLife Sverige's names, Task 7m), iucn_status (IUCN Red
    List where Wikidata has none), abundance, family_sv and commons_search_name live only in
    species_list.yaml; a resume used to rebuild the entry from the checklists and drop them.
    """
    from birdy_fetcher import species_list

    fixture = (fixtures_dir / "wikidata_sparql_response.json").read_text()

    async def fake_sparql(query: str) -> str:
        return fixture

    monkeypatch.setattr(species_list, "_run_sparql", fake_sparql)
    out_list = tmp_path / "species_list.yaml"
    out_failures = tmp_path / "mapping_failures.yaml"
    checklists_dir = fixtures_dir.parent.parent / "checklists"
    kwargs = {
        "ioc_xlsx": sample_ioc,
        "vp11_pdf": sample_vp11,
        "filter_yaml": checklists_dir / "vp11-filter.yaml",
        "out_list": out_list,
        "out_failures": out_failures,
    }
    await build_species_list(**kwargs)  # type: ignore[arg-type]
    entries = yaml.safe_load(out_list.read_text(encoding="utf-8"))
    hand_set = {
        "common_sv": "Talgoxe",
        "former_sv": "Gammal talgoxe",
        "iucn_status": "LC",
        "abundance": "allmän",
        "family_sv": "Mesar",
        "commons_search_name": "Parus major",
    }
    great_tit = next(e for e in entries if e["scientific_name"] == "Parus major")
    great_tit.update(hand_set)
    great_tit["common_en"] = "Stale English name"  # a pipeline field: the checklist wins
    out_list.write_text(yaml.safe_dump(entries, allow_unicode=True), encoding="utf-8")

    await build_species_list(**kwargs, resume=True)  # type: ignore[arg-type]

    merged = yaml.safe_load(out_list.read_text(encoding="utf-8"))
    great_tit = next(e for e in merged if e["scientific_name"] == "Parus major")
    assert {k: great_tit.get(k) for k in hand_set} == hand_set
    assert great_tit["common_en"] == "Great Tit"
    assert len(merged) == len(entries)


def test_hand_set_fields_survive_on_a_manual_entry_too(tmp_path: Path) -> None:
    from birdy_fetcher.models import SpeciesListEntry
    from birdy_fetcher.species_list import _merge_with_existing

    existing = tmp_path / "species_list.yaml"
    existing.write_text(
        yaml.safe_dump(
            [
                {
                    "wikidata_id": "Q99999",
                    "scientific_name": "Manualicus addedicus",
                    "family": "Manualidae",
                    "ioc_order": "Manualiformes",
                    "common_en": "Manual Bird",
                    "vp_status": "H",
                    "common_sv": "Handfågel",
                    "former_sv": "Gammal handfågel",
                }
            ],
            allow_unicode=True,
        ),
        encoding="utf-8",
    )
    pipeline = [
        SpeciesListEntry(
            wikidata_id="Q25485",
            scientific_name="Parus major",
            family="Paridae",
            ioc_order="Passeriformes",
            common_en="Great Tit",
            vp_status="H",
        )
    ]
    merged = [e.model_dump(exclude_none=True) for e in _merge_with_existing(pipeline, existing)]
    manual = next(e for e in merged if e["scientific_name"] == "Manualicus addedicus")
    assert (manual["common_sv"], manual["former_sv"]) == ("Handfågel", "Gammal handfågel")
