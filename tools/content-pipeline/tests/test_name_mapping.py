import asyncio
import json
from datetime import UTC, datetime
from pathlib import Path

from birdy_fetcher.name_mapping import (
    NameMappingResult,
    build_query,
    parse_labelmap_csv,
    parse_sparql_response_for_names,
    render_mapping_json_by_class_index,
    run_build_name_mapping,
)


def test_parse_labelmap_csv_skips_background_and_header(tmp_path: Path) -> None:
    csv = tmp_path / "labelmap.csv"
    csv.write_text(
        "id,name\n"
        "964,background\n"
        "0,Cyanistes caeruleus\n"
        "1,Turdus merula\n"
        "2,Parus major\n",
        encoding="utf-8",
    )
    pairs = parse_labelmap_csv(csv)
    assert pairs == [
        (0, "Cyanistes caeruleus"),
        (1, "Turdus merula"),
        (2, "Parus major"),
    ]


def test_build_query_emits_p225_values_clause_for_names() -> None:
    names = ["Cyanistes caeruleus", "Turdus merula"]
    query = build_query(names)
    assert "wdt:P225" in query
    assert '"Cyanistes caeruleus"' in query
    assert '"Turdus merula"' in query
    assert "VALUES ?name" in query


def test_parse_sparql_response_for_names_returns_name_to_qid() -> None:
    raw = json.dumps({
        "results": {"bindings": [
            {"item": {"value": "http://www.wikidata.org/entity/Q1226346"},
             "name": {"value": "Cyanistes caeruleus"}},
            {"item": {"value": "http://www.wikidata.org/entity/Q913049"},
             "name": {"value": "Turdus merula"}},
        ]}
    })
    result = parse_sparql_response_for_names(raw)
    assert result == {"Cyanistes caeruleus": "Q1226346", "Turdus merula": "Q913049"}


def test_parse_sparql_response_for_names_first_wins_on_duplicates() -> None:
    raw = json.dumps({
        "results": {"bindings": [
            {"item": {"value": "http://www.wikidata.org/entity/Q1"},
             "name": {"value": "Parus major"}},
            {"item": {"value": "http://www.wikidata.org/entity/Q2"},
             "name": {"value": "Parus major"}},
        ]}
    })
    result = parse_sparql_response_for_names(raw)
    assert result == {"Parus major": "Q1"}


def test_parse_sparql_response_for_names_skips_lexeme_entities() -> None:
    # P225 can resolve to Lexeme entities (L-prefix) for some taxa. Filter them
    # so downstream consumers get only Q-IDs in aiy_to_qid.json.
    raw = json.dumps({
        "results": {"bindings": [
            {"item": {"value": "http://www.wikidata.org/entity/L1370926-S1"},
             "name": {"value": "Gallus gallus domesticus"}},
            {"item": {"value": "http://www.wikidata.org/entity/Q780"},
             "name": {"value": "Gallus gallus domesticus"}},
        ]}
    })
    result = parse_sparql_response_for_names(raw)
    assert result == {"Gallus gallus domesticus": "Q780"}


def test_render_mapping_json_writes_meta_and_class_index_keys() -> None:
    result = NameMappingResult(
        mappings={1: "Q913049", 0: "Q1226346"},
        requested_classes=964,
        mapped_classes=2,
    )
    rendered = render_mapping_json_by_class_index(
        result,
        model_version="aiy_birds_v1",
        generated_at=datetime(2026, 5, 7, 12, 0, 0, tzinfo=UTC),
    )
    parsed = json.loads(rendered)
    assert parsed["_meta"]["generated_for_model_version"] == "aiy_birds_v1"
    assert parsed["_meta"]["mapped_classes"] == 2
    assert parsed["_meta"]["total_classes"] == 964
    assert parsed["_meta"]["coverage_pct"] == 0.2
    keys = list(parsed["mappings"].keys())
    assert keys == ["0", "1"]
    assert parsed["mappings"]["0"] == "Q1226346"


def test_run_build_name_mapping_stitches_batches_and_drops_unknown_names() -> None:
    pairs = [(0, "Cyanistes caeruleus"), (1, "Turdus merula"), (2, "Parus major")]

    batch_responses = [
        json.dumps({
            "results": {"bindings": [
                {"item": {"value": "http://www.wikidata.org/entity/Q1226346"},
                 "name": {"value": "Cyanistes caeruleus"}},
                {"item": {"value": "http://www.wikidata.org/entity/Q913049"},
                 "name": {"value": "Turdus merula"}},
                {"item": {"value": "http://www.wikidata.org/entity/Q9999"},
                 "name": {"value": "Not in pairs — must be ignored"}},
            ]}
        }),
        json.dumps({
            "results": {"bindings": [
                {"item": {"value": "http://www.wikidata.org/entity/Q3534"},
                 "name": {"value": "Parus major"}},
            ]}
        }),
    ]
    queries: list[str] = []

    async def fake_runner(query: str) -> str:
        queries.append(query)
        return batch_responses[len(queries) - 1]

    # Batch boundary forced via SPARQL_BATCH_SIZE — patch to 2 so 3 pairs split 2+1.
    from birdy_fetcher import name_mapping
    original_size = name_mapping.SPARQL_BATCH_SIZE
    name_mapping.SPARQL_BATCH_SIZE = 2
    try:
        result = asyncio.run(run_build_name_mapping(pairs, run_sparql=fake_runner))
    finally:
        name_mapping.SPARQL_BATCH_SIZE = original_size

    assert len(queries) == 2
    assert result.mappings == {0: "Q1226346", 1: "Q913049", 2: "Q3534"}
    assert result.requested_classes == 3
    assert result.mapped_classes == 3


def test_run_build_name_mapping_first_batch_wins_on_cross_batch_duplicates() -> None:
    pairs = [(0, "Parus major"), (1, "Turdus merula")]

    batch_responses = [
        json.dumps({"results": {"bindings": [
            {"item": {"value": "http://www.wikidata.org/entity/Q3534"},
             "name": {"value": "Parus major"}},
        ]}}),
        json.dumps({"results": {"bindings": [
            {"item": {"value": "http://www.wikidata.org/entity/Q9999"},
             "name": {"value": "Parus major"}},
            {"item": {"value": "http://www.wikidata.org/entity/Q913049"},
             "name": {"value": "Turdus merula"}},
        ]}}),
    ]
    call_count = [0]

    async def fake_runner(_: str) -> str:
        idx = call_count[0]
        call_count[0] += 1
        return batch_responses[idx]

    from birdy_fetcher import name_mapping
    original_size = name_mapping.SPARQL_BATCH_SIZE
    name_mapping.SPARQL_BATCH_SIZE = 1
    try:
        result = asyncio.run(run_build_name_mapping(pairs, run_sparql=fake_runner))
    finally:
        name_mapping.SPARQL_BATCH_SIZE = original_size

    assert result.mappings == {0: "Q3534", 1: "Q913049"}
