"""Tests for web/datamod.py and web/counties.py: shares, sentences and the status signal."""

from __future__ import annotations

import pytest

from birdy_fetcher.web.counties import COUNTIES
from birdy_fetcher.web.datamod import county_profile, month_profile, scaled


def test_counties_table_has_21_unique_iso_codes() -> None:
    assert len(COUNTIES) == 21
    assert len({c.iso for c in COUNTIES.values()}) == 21
    assert COUNTIES["SWE.10_1"].name == "Norrbotten"
    assert COUNTIES["SWE.21_1"].iso == "SE-O"


def test_month_profile_adjusts_for_how_many_people_report() -> None:
    species = [10] * 12
    species[4] = 40
    all_birds = [100] * 12
    all_birds[4] = 400
    assert month_profile(species, all_birds) == [100] * 12


def test_month_profile_scales_the_top_month_to_100() -> None:
    species = [0, 0, 0, 5, 10, 10, 10, 8, 2, 0, 0, 0]
    assert month_profile(species, [100] * 12) == [0, 0, 0, 50, 100, 100, 100, 80, 20, 0, 0, 0]


def test_a_tiny_share_never_rounds_down_to_zero() -> None:
    assert scaled([1000.0, 1.0, 0.0]) == [100, 1, 0]


def test_month_profile_needs_twelve_values() -> None:
    with pytest.raises(ValueError):
        month_profile([1] * 11, [1] * 12)


def test_county_profile_covers_all_21_counties() -> None:
    profile = county_profile({"SE-BD": 30, "SE-M": 10}, {"SE-BD": 100, "SE-M": 100})
    assert len(profile) == 21
    assert profile["SE-BD"] == 100
    assert profile["SE-M"] == 33
    assert profile["SE-AB"] == 0
