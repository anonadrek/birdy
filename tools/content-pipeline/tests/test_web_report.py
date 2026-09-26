"""Tests for web/report.py."""

from __future__ import annotations

from birdy_fetcher.web.report import SpeciesOutcome, render_report


def test_report_lists_counts_failures_and_dropped_facts() -> None:
    outcomes = [
        SpeciesOutcome("Q1", "Talgoxe", "ok", [], [], attempts=1, from_cache=False),
        SpeciesOutcome("Q2", "Blåmes", "ok", [], ["sv.facts.size: citatet finns inte"], 2, False),
        SpeciesOutcome("Q3", "Svartmes", "failed", ["en.voice: första person"], [], 2, False),
        SpeciesOutcome("Q4", "Tofsmes", "skipped", ["redan granskad"], [], 0, False),
    ]
    text = render_report(outcomes, cost_usd=1.234, model_id="claude-opus-5", effort="high",
                         date="2026-10-01")
    assert "# Webbtexter 2026-10-01" in text
    assert "`claude-opus-5`" in text and "1.23" in text
    assert "high" in text
    assert "| ok | 2 |" in text and "| failed | 1 |" in text and "| skipped | 1 |" in text
    assert "Svartmes (Q3)" in text and "en.voice: första person" in text
    assert "Blåmes (Q2)" in text and "sv.facts.size" in text
    assert "Två försök: 2" in text
