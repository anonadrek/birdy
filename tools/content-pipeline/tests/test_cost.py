"""Tests for cost tracker — token accounting + max-cost guard."""

from __future__ import annotations

import pytest

from birdy_fetcher.cost import CostTracker, MaxCostExceeded


def test_tracker_estimates_haiku_cost_correctly() -> None:
    tracker = CostTracker(max_usd=None)
    tracker.record(model="haiku", input_tokens=5000, output_tokens=250)
    assert tracker.total_usd == pytest.approx(
        5000 / 1_000_000 * 0.80 + 250 / 1_000_000 * 4.00,
        rel=1e-6,
    )


def test_tracker_raises_when_max_exceeded() -> None:
    tracker = CostTracker(max_usd=0.005)
    tracker.record(model="haiku", input_tokens=5000, output_tokens=250)
    with pytest.raises(MaxCostExceeded):
        tracker.record(model="haiku", input_tokens=5000, output_tokens=250)


def test_tracker_no_cap_never_raises() -> None:
    tracker = CostTracker(max_usd=None)
    for _ in range(1000):
        tracker.record(model="haiku", input_tokens=5000, output_tokens=250)
    assert tracker.total_usd > 0
