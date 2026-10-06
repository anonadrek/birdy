"""Tests for wikidata.py — SPARQL parsing + caching."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.cache import Cache
from birdy_fetcher.wikidata import WikidataClient


@pytest.mark.asyncio
async def test_fetch_structured_uses_fixture(
    fixtures_dir: Path,
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    fixture = (fixtures_dir / "wikidata_q25485.json").read_text()

    async def fake_sparql(query: str) -> str:
        return fixture

    cache = Cache(tmp_path)
    client = WikidataClient(cache=cache, run_sparql=fake_sparql)

    result = await client.fetch_structured("Q25485")
    assert result.q_id == "Q25485"
    assert result.family == "Paridae"
    assert result.family_sv == "Mesar"
    assert result.genus == "Parus"
    assert result.ioc_order == "Passeriformes"
    assert result.iucn_status == "LC"
    assert result.image_filename == "Parus major - Mindelheim - 2012.jpg"
    assert result.common_sv == "Talgoxe"


@pytest.mark.asyncio
async def test_fetch_structured_capitalizes_lowercase_sv_labels(
    fixtures_dir: Path,
    tmp_path: Path,
) -> None:
    fixture = (fixtures_dir / "wikidata_lowercase_labels.json").read_text(encoding="utf-8")

    async def fake_sparql(query: str) -> str:
        return fixture

    cache = Cache(tmp_path)
    client = WikidataClient(cache=cache, run_sparql=fake_sparql)

    result = await client.fetch_structured("Q126167")
    assert result.common_sv == "Lammgam"
    assert result.family_sv == "H\u00f6kar"


@pytest.mark.asyncio
async def test_fetch_structured_handles_missing_sv_labels(
    fixtures_dir: Path,
    tmp_path: Path,
) -> None:
    fixture = (fixtures_dir / "wikidata_no_sv_label.json").read_text()

    async def fake_sparql(query: str) -> str:
        return fixture

    cache = Cache(tmp_path)
    client = WikidataClient(cache=cache, run_sparql=fake_sparql)

    result = await client.fetch_structured("Q999")
    assert result.family == "Mysteriidae"
    assert result.family_sv is None
    assert result.common_sv is None


@pytest.mark.asyncio
async def test_fetch_structured_uses_cache_on_second_call(
    fixtures_dir: Path,
    tmp_path: Path,
) -> None:
    fixture = (fixtures_dir / "wikidata_q25485.json").read_text()

    call_count = {"n": 0}

    async def counting_sparql(query: str) -> str:
        call_count["n"] += 1
        return fixture

    cache = Cache(tmp_path)
    client = WikidataClient(cache=cache, run_sparql=counting_sparql)
    await client.fetch_structured("Q25485")
    await client.fetch_structured("Q25485")
    assert call_count["n"] == 1


@pytest.mark.asyncio
async def test_force_bypasses_cache(
    fixtures_dir: Path,
    tmp_path: Path,
) -> None:
    fixture = (fixtures_dir / "wikidata_q25485.json").read_text()

    call_count = {"n": 0}

    async def counting_sparql(query: str) -> str:
        call_count["n"] += 1
        return fixture

    cache = Cache(tmp_path)
    client = WikidataClient(cache=cache, run_sparql=counting_sparql)
    await client.fetch_structured("Q25485")
    await client.fetch_structured("Q25485", force=True)
    assert call_count["n"] == 2


# --- Release 1.3.0 Task 7g: IUCN status by item, Swedish names after genus renames ---


def _sparql_response(**extra: dict[str, str]) -> str:
    """One SPARQL row like the real query returns, plus the given extra bindings."""
    import json

    binding: dict[str, dict[str, str]] = {
        "taxonName": {"type": "literal", "value": "Testus testus"},
        "familyLabel": {"type": "literal", "value": "Testidae"},
        "genusLabel": {"type": "literal", "value": "Testus"},
        "ordoLabel": {"type": "literal", "value": "Passeriformes"},
    }
    binding.update(extra)
    return json.dumps({"head": {"vars": list(binding)}, "results": {"bindings": [binding]}})


def _uri(qid: str) -> dict[str, str]:
    return {"type": "uri", "value": f"http://www.wikidata.org/entity/{qid}"}


def _lit(value: str) -> dict[str, str]:
    return {"type": "literal", "value": value}


async def _structured(tmp_path: Path, response: str):  # type: ignore[no-untyped-def]
    async def fake_sparql(query: str) -> str:
        return response

    return await WikidataClient(cache=Cache(tmp_path), run_sparql=fake_sparql).fetch_structured(
        "Q1"
    )


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("status_qid", "label", "expected"),
    [
        # The English labels in May 2026, when the committed YAML was generated: neither was in
        # the label table, so 12 endangered and 3 extinct species were written as NE.
        ("Q96377276", "endangered status", "EN"),
        ("Q237350", "extinct species", "EX"),
        ("Q211005", "least concern", "LC"),
        ("Q719675", "near threatened", "NT"),
        ("Q278113", "vulnerable", "VU"),
        ("Q219127", "critically endangered", "CR"),
        ("Q3245245", "Data Deficient", "DD"),
        ("Q239509", "extinct in the wild", "EW"),
        ("Q3350324", "not evaluated", "NE"),
    ],
)
async def test_iucn_status_is_read_from_the_status_item_not_its_label(
    tmp_path: Path, status_qid: str, label: str, expected: str
) -> None:
    response = _sparql_response(iucnStatus=_uri(status_qid), iucnStatusLabel=_lit(label))
    assert (await _structured(tmp_path, response)).iucn_status == expected


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("label", "expected"),
    [
        ("endangered species", "EN"),
        ("Endangered status", "EN"),
        ("extinct species", "EX"),
        ("least concern species", "LC"),
    ],
)
async def test_an_unknown_status_item_falls_back_to_its_label_without_suffix(
    tmp_path: Path, label: str, expected: str
) -> None:
    response = _sparql_response(iucnStatus=_uri("Q123456789"), iucnStatusLabel=_lit(label))
    assert (await _structured(tmp_path, response)).iucn_status == expected


@pytest.mark.asyncio
async def test_a_status_nobody_knows_is_not_evaluated(tmp_path: Path) -> None:
    response = _sparql_response(
        iucnStatus=_uri("Q123456789"), iucnStatusLabel=_lit("something new")
    )
    assert (await _structured(tmp_path, response)).iucn_status == "NE"
