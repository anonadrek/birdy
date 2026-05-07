"""Tests for inat_mapping.py — SPARQL P3151 cross-walk builder."""

from __future__ import annotations

import json
from datetime import UTC, datetime

import pytest

from birdy_fetcher.inat_mapping import (
    MappingResult,
    build_query,
    chunked,
    parse_sparql_response,
    render_mapping_json,
    run_build_mapping,
)


# ---------------------------------------------------------------------------
# parse_sparql_response
# ---------------------------------------------------------------------------


def test_parse_sparql_response_extracts_inat_to_qid_pairs() -> None:
    raw = json.dumps(
        {
            "results": {
                "bindings": [
                    {
                        "item": {"value": "http://www.wikidata.org/entity/Q25485"},
                        "inatId": {"value": "12345"},
                    },
                    {
                        "item": {"value": "http://www.wikidata.org/entity/Q25404"},
                        "inatId": {"value": "67890"},
                    },
                ]
            }
        }
    )
    result = parse_sparql_response(raw)
    assert result == {"12345": "Q25485", "67890": "Q25404"}


def test_parse_sparql_response_skips_duplicate_inat_ids_with_first_wins() -> None:
    # Wikidata kan ha flera Q-IDs som mappar till samma iNat-ID (taxonomic
    # disagreement) — ta första, men logga warning. Test verifierar first-wins.
    raw = json.dumps(
        {
            "results": {
                "bindings": [
                    {
                        "item": {"value": "http://www.wikidata.org/entity/Q1"},
                        "inatId": {"value": "100"},
                    },
                    {
                        "item": {"value": "http://www.wikidata.org/entity/Q2"},
                        "inatId": {"value": "100"},
                    },
                ]
            }
        }
    )
    result = parse_sparql_response(raw)
    assert result == {"100": "Q1"}


def test_parse_sparql_response_empty_bindings_returns_empty_dict() -> None:
    raw = json.dumps({"results": {"bindings": []}})
    result = parse_sparql_response(raw)
    assert result == {}


# ---------------------------------------------------------------------------
# chunked + build_query
# ---------------------------------------------------------------------------


def test_chunked_splits_into_batches_of_max_size() -> None:
    items = [f"Q{i}" for i in range(450)]
    batches = list(chunked(items, 200))
    assert len(batches) == 3
    assert len(batches[0]) == 200
    assert len(batches[1]) == 200
    assert len(batches[2]) == 50


def test_chunked_single_batch_when_items_fit() -> None:
    items = ["Q1", "Q2", "Q3"]
    batches = list(chunked(items, 200))
    assert len(batches) == 1
    assert batches[0] == ["Q1", "Q2", "Q3"]


def test_build_query_emits_values_clause_with_qids() -> None:
    qids = ["Q25485", "Q25404"]
    query = build_query(qids)
    assert "VALUES ?item" in query
    assert "wd:Q25485" in query
    assert "wd:Q25404" in query
    assert "wdt:P3151" in query


# ---------------------------------------------------------------------------
# render_mapping_json
# ---------------------------------------------------------------------------


def test_render_mapping_json_writes_meta_and_sorted_mappings() -> None:
    result = MappingResult(
        mappings={"67890": "Q25404", "12345": "Q25485"},
        requested_qids=10,
        mapped_qids=2,
    )
    rendered = render_mapping_json(
        result,
        model_version="inat2021_aves_quant_v1",
        generated_at=datetime(2026, 5, 7, 12, 0, 0, tzinfo=UTC),
    )
    parsed = json.loads(rendered)
    assert parsed["_meta"]["generated_for_model_version"] == "inat2021_aves_quant_v1"
    assert parsed["_meta"]["coverage_pct"] == 20.0
    assert parsed["_meta"]["mapped_qids"] == 2
    assert parsed["_meta"]["total_qids"] == 10
    # Mappings sorterade på iNat-ID-numeric
    keys = list(parsed["mappings"].keys())
    assert keys == ["12345", "67890"]


def test_render_mapping_json_ends_with_newline() -> None:
    result = MappingResult(mappings={}, requested_qids=0, mapped_qids=0)
    rendered = render_mapping_json(
        result,
        model_version="inat2021_aves_quant_v1",
        generated_at=datetime(2026, 5, 7, tzinfo=UTC),
    )
    assert rendered.endswith("\n")


def test_render_mapping_json_generated_at_utc_format() -> None:
    result = MappingResult(mappings={}, requested_qids=0, mapped_qids=0)
    rendered = render_mapping_json(
        result,
        model_version="inat2021_aves_quant_v1",
        generated_at=datetime(2026, 5, 7, 12, 0, 0, tzinfo=UTC),
    )
    parsed = json.loads(rendered)
    assert parsed["_meta"]["generated_at"] == "2026-05-07T12:00:00Z"


# ---------------------------------------------------------------------------
# MappingResult.coverage_pct
# ---------------------------------------------------------------------------


def test_mapping_result_coverage_pct_zero_when_no_requested() -> None:
    result = MappingResult(mappings={}, requested_qids=0, mapped_qids=0)
    assert result.coverage_pct == 0.0


def test_mapping_result_coverage_pct_rounded_to_one_decimal() -> None:
    result = MappingResult(mappings={}, requested_qids=3, mapped_qids=1)
    assert result.coverage_pct == 33.3


# ---------------------------------------------------------------------------
# run_build_mapping (offline integration test with fake runner)
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_run_build_mapping_merges_batches_via_fake_runner() -> None:
    """Verifies that the orchestrator chunks + merges results correctly."""
    # Simulate 250 QIDs split across 2 batches, each returning 2 mappings
    calls: list[str] = []

    async def fake_runner(query: str) -> str:
        calls.append(query)
        # Return a minimal but valid response per batch
        return json.dumps(
            {
                "results": {
                    "bindings": [
                        {
                            "item": {"value": f"http://www.wikidata.org/entity/Q{len(calls)}1"},
                            "inatId": {"value": f"{len(calls)}0001"},
                        },
                        {
                            "item": {"value": f"http://www.wikidata.org/entity/Q{len(calls)}2"},
                            "inatId": {"value": f"{len(calls)}0002"},
                        },
                    ]
                }
            }
        )

    qids = [f"Q{i}" for i in range(250)]
    result = await run_build_mapping(qids, run_sparql=fake_runner)
    # 250 QIDs → 2 batches (200 + 50) → 2 calls
    assert len(calls) == 2
    # Each batch returned 2 mappings → 4 total
    assert result.mapped_qids == 4
    assert result.requested_qids == 250


@pytest.mark.asyncio
async def test_run_build_mapping_empty_qids_returns_empty_result() -> None:
    async def fake_runner(query: str) -> str:
        return json.dumps({"results": {"bindings": []}})

    result = await run_build_mapping([], run_sparql=fake_runner)
    assert result.mappings == {}
    assert result.requested_qids == 0
    assert result.mapped_qids == 0
