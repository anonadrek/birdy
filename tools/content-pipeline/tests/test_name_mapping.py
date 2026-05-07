import json
from datetime import UTC, datetime
from pathlib import Path

from birdy_fetcher.name_mapping import (
    parse_labelmap_csv,
    build_query,
    parse_sparql_response_for_names,
    render_mapping_json_by_class_index,
    NameMappingResult,
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
