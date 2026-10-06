"""Tests for web/record.py: the record is the state between the steps."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.web.record import (
    delete_voice,
    facts_hash,
    is_reviewed,
    load_all,
    load_record,
    merge_sources,
    new_record,
    record_path,
    save_record,
    sweep_orphan_voices,
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


def test_a_failed_save_leaves_the_old_record_and_no_temp_file(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """Minor 5 (final review 2026-10-06): every step saves through save_record; a crash or a
    full disk halfway through the write must never leave a truncated record."""
    path = record_path(tmp_path, "Q1")
    save_record(path, {"qid": "Q1", "facts": [{"id": "f01"}]})
    before = path.read_bytes()
    real_write_text = Path.write_text

    def half_then_fail(self: Path, data: str, *args: object, **kwargs: object) -> int:
        real_write_text(self, data[: len(data) // 2], *args, **kwargs)  # type: ignore[arg-type]
        raise OSError(28, "No space left on device")

    monkeypatch.setattr(Path, "write_text", half_then_fail)
    with pytest.raises(OSError):
        save_record(path, {"qid": "Q1", "facts": [{"id": "f01"}, {"id": "f02"}]})
    monkeypatch.undo()
    assert path.read_bytes() == before
    assert [p.name for p in tmp_path.iterdir()] == ["Q1.json"]


def _lock_voice_files(monkeypatch: pytest.MonkeyPatch) -> None:
    """A voice.mp3 another program holds open: on Windows `unlink` then raises
    PermissionError. Simulated so the test behaves the same on every OS."""
    real_unlink = Path.unlink

    def unlink(self: Path, missing_ok: bool = False) -> None:
        if self.name == "voice.mp3" and self.exists():
            raise PermissionError(13, "The process cannot access the file", str(self))
        real_unlink(self, missing_ok=missing_ok)

    monkeypatch.setattr(Path, "unlink", unlink)


def _voice(images_out: Path, qid: str) -> Path:
    voice = images_out / qid / "voice.mp3"
    voice.parent.mkdir(parents=True, exist_ok=True)
    voice.write_bytes(b"id3")
    return voice


def test_the_sweep_removes_a_recording_whose_record_has_no_audio(tmp_path: Path) -> None:
    data_out, images_out = tmp_path / "data", tmp_path / "img"
    with_audio, without, no_record = new_record("Q1"), new_record("Q2"), "Q3"
    with_audio["audio"] = {"file": "Q1/voice.mp3"}
    save_record(record_path(data_out, "Q1"), with_audio)
    save_record(record_path(data_out, "Q2"), without)
    kept, orphan, stray = (
        _voice(images_out, "Q1"),
        _voice(images_out, "Q2"),
        _voice(images_out, no_record),
    )
    sweep = sweep_orphan_voices(data_out, images_out)
    assert sweep.removed == ["Q2", "Q3"]
    assert sweep.errors == []
    assert kept.exists() and not orphan.exists() and not stray.exists()


def test_the_sweep_reports_a_locked_file_and_goes_on(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    data_out, images_out = tmp_path / "data", tmp_path / "img"
    save_record(record_path(data_out, "Q2"), new_record("Q2"))
    orphan = _voice(images_out, "Q2")
    _lock_voice_files(monkeypatch)
    sweep = sweep_orphan_voices(data_out, images_out)
    assert sweep.removed == []
    assert len(sweep.errors) == 1 and "Q2" in sweep.errors[0]
    assert orphan.exists()


def test_the_sweep_skips_an_unreadable_record(tmp_path: Path) -> None:
    data_out, images_out = tmp_path / "data", tmp_path / "img"
    data_out.mkdir(parents=True)
    record_path(data_out, "Q2").write_text("{ inte json", encoding="utf-8")
    voice = _voice(images_out, "Q2")
    sweep = sweep_orphan_voices(data_out, images_out)
    assert sweep.removed == [] and sweep.errors == []
    assert voice.exists()


def test_delete_voice_never_raises(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    images_out = tmp_path / "img"
    _voice(images_out, "Q1")
    _lock_voice_files(monkeypatch)
    error = delete_voice(images_out, "Q1")
    assert error is not None and "PermissionError" in error
    assert delete_voice(images_out, "Q9") is None
