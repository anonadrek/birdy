"""Tests for web/report.py."""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path

from birdy_fetcher.web.report import (
    SpeciesOutcome,
    StepOutcome,
    render_report,
    render_step_report,
    write_step_report,
)


def test_report_lists_counts_failures_and_dropped_facts() -> None:
    outcomes = [
        SpeciesOutcome("Q1", "Talgoxe", "ok", [], [], attempts=1, from_cache=False),
        SpeciesOutcome("Q2", "Blåmes", "ok", [], ["sv.facts.size: citatet finns inte"], 2, False),
        SpeciesOutcome("Q3", "Svartmes", "failed", ["en.voice: första person"], [], 2, False),
        SpeciesOutcome("Q4", "Tofsmes", "skipped", ["redan granskad"], [], 0, False),
    ]
    text = render_report(
        outcomes, cost_usd=1.234, model_id="claude-opus-5", effort="high", date="2026-10-01"
    )
    assert "# Webbtexter 2026-10-01" in text
    assert "`claude-opus-5`" in text and "1.23" in text
    assert "high" in text
    assert "| ok | 2 |" in text and "| failed | 1 |" in text and "| skipped | 1 |" in text
    assert "Svartmes (Q3)" in text and "en.voice: första person" in text
    assert "Blåmes (Q2)" in text and "sv.facts.size" in text
    assert "Två försök: 2" in text


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
