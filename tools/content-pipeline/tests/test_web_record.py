"""Tests for web/record.py: the record is the state between the steps."""

from __future__ import annotations

from pathlib import Path

from birdy_fetcher.web.record import (
    facts_hash,
    is_reviewed,
    load_all,
    load_record,
    merge_sources,
    new_record,
    record_path,
    save_record,
)


def test_new_record_is_pending_and_unpublished() -> None:
    record = new_record("Q1")
    assert record["status"] == "pending"
    assert record["publish"] is False
    assert record["review"] == {}
    assert "verification" not in record
    assert record["facts"] == []
    assert record["text"] is None


def test_merge_sources_keeps_what_later_steps_own() -> None:
    existing = new_record("Q1")
    existing["facts"] = [{"id": "f01"}]
    existing["review"] = {"wave": 1}
    existing["verification"] = {
        "method": "auto",
        "at": "2026-11-01",
        "model": "x",
        "spotChecked": False,
    }
    existing["audio"] = {"file": "Q1/voice.mp3"}
    merged = merge_sources(existing, "Q1", {"iucn": "LC", "audio": None, "data": {"x": 1}})
    assert merged["facts"] == [{"id": "f01"}]
    assert merged["review"]["wave"] == 1
    assert merged["verification"]["model"] == "x"
    assert merged["iucn"] == "LC"
    assert merged["data"] == {"x": 1}
    assert "audio" not in merged


def test_save_and_load_round_trip(tmp_path: Path) -> None:
    path = record_path(tmp_path, "Q1")
    record = merge_sources(None, "Q1", {"names": {"sv": "Talgoxe"}})
    save_record(path, record)
    assert load_record(path) == record
    assert path.read_text(encoding="utf-8").endswith("}\n")
    assert "Talgoxe" in path.read_text(encoding="utf-8")
    assert load_record(tmp_path / "Q404.json") is None
    assert list(load_all(tmp_path)) == ["Q1"]


def test_facts_hash_changes_with_the_facts() -> None:
    record = new_record("Q1")
    before = facts_hash(record)
    record["facts"] = [{"id": "f01", "sv": "Svart huvud."}]
    assert facts_hash(record) != before
    assert len(facts_hash(record)) == 16


def test_is_reviewed() -> None:
    record = new_record("Q1")
    assert not is_reviewed(record)
    record["verification"] = {
        "method": "auto",
        "at": "2026-11-01",
        "model": "x",
        "spotChecked": False,
    }
    assert is_reviewed(record)
