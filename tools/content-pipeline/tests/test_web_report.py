"""Tests for web/report.py."""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path

from birdy_fetcher.web.report import StepOutcome, render_step_report, write_step_report


def test_step_report_lists_failures_skips_and_notes(tmp_path: Path) -> None:
    outcomes = [
        StepOutcome("Q1", "Talgoxe", "ok", notes=["ingen fri inspelning"]),
        StepOutcome("Q2", "Blåmes", "failed", errors=["ingen Wikipediaartikel"]),
        StepOutcome("Q3", "Koltrast", "skipped", errors=["granskad"]),
    ]
    text = render_step_report(
        title="Källor", date="2026-10-02", outcomes=outcomes, cost_usd=1.5, model_line="Modell: x"
    )
    assert text.startswith("# Källor 2026-10-02")
    assert "Kostnad för körningen: $1.50." in text
    assert "| ok | 1 |" in text
    assert "| failed | 1 |" in text
    assert "- **Blåmes (Q2)**: ingen Wikipediaartikel" in text
    assert "- **Koltrast (Q3)**: granskad" in text
    assert "- **Talgoxe (Q1)**: ingen fri inspelning" in text
    path = write_step_report(tmp_path, "sources", datetime(2026, 10, 2, 9, 30, tzinfo=UTC), text)
    assert path.name == "web-sources-2026-10-02-093000.md"
