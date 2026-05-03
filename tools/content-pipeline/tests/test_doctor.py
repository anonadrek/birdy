"""doctor pre-flight check."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.doctor import run_doctor


def test_doctor_passes_when_everything_present(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    sources = tmp_path / "sources"
    sources.mkdir()
    (sources / "ioc-14.1.xlsx").write_bytes(b"PK\x03\x04fakeexcelplaceholder")
    (sources / "vp11.pdf").write_bytes(b"%PDF-1.4 fakepdfplaceholder")
    (tmp_path / "species_list.yaml").write_text("- wikidata_id: Q1\n  scientific_name: x\n")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "sk-ant-fake")

    report = run_doctor(root=tmp_path)
    assert report.is_ok
    assert all(r.ok for r in report.checks)


def test_doctor_fails_without_api_key(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    report = run_doctor(root=tmp_path)
    assert not report.is_ok
    api_check = next(c for c in report.checks if "API_KEY" in c.name)
    assert not api_check.ok


def test_doctor_fails_without_ioc_xlsx(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ANTHROPIC_API_KEY", "sk-ant-fake")
    report = run_doctor(root=tmp_path)
    assert not report.is_ok
