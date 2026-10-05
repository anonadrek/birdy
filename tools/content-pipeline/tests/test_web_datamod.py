"""Tests for web/datamod.py and web/counties.py: shares, sentences and the status signal."""

from __future__ import annotations

import pytest

from birdy_fetcher.web.counties import COUNTIES
from birdy_fetcher.web.datamod import (
    county_profile,
    county_sentence,
    data_sentences,
    join_list,
    month_profile,
    month_runs,
    month_sentences,
    months_text,
    scaled,
)


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


MIGRANT = [0, 0, 5, 60, 100, 90, 85, 70, 40, 5, 0, 0]


def test_runs_wrap_around_the_new_year() -> None:
    assert month_runs({10, 11, 0, 1}) == [[10, 11, 0, 1]]
    assert month_runs({4, 5, 8}) == [[4, 5], [8]]


def test_three_or_more_months_in_a_row_become_a_span() -> None:
    assert months_text({11, 0, 1}, "sv") == "december till februari"
    assert months_text({11, 0, 1}, "en") == "December to February"


def test_two_months_are_listed() -> None:
    assert months_text({4, 5}, "sv") == "maj och juni"
    assert months_text({2, 4, 5}, "en") == "March, May and June"


def test_migrant_sentences() -> None:
    assert month_sentences(MIGRANT, "sv") == [
        "Rapporteras mest i maj till juli.",
        "Nästan aldrig i oktober till mars.",
    ]
    assert month_sentences(MIGRANT, "en") == [
        "Reported most in May to July.",
        "Almost never in October to March.",
    ]


def test_resident_is_reported_all_year() -> None:
    assert month_sentences([70, 65, 60, 55, 70, 79, 64, 68, 74, 100, 66, 69], "sv") == [
        "Rapporteras året runt."
    ]


def test_county_sentence_names_the_top_three() -> None:
    profile = {iso: 0 for iso in ("SE-BD", "SE-AC", "SE-Z", "SE-M")}
    profile.update({"SE-BD": 100, "SE-AC": 80, "SE-Z": 60, "SE-M": 5})
    assert county_sentence(profile, "sv") == (
        "Vanligast i rapporterna från Norrbotten, Västerbotten och Jämtland."
    )
    assert county_sentence(profile, "en") == (
        "Most common in reports from Norrbotten, Västerbotten and Jämtland."
    )


def test_county_sentence_with_one_county_and_with_none() -> None:
    assert county_sentence({"SE-I": 100, "SE-M": 0}, "sv") == (
        "Vanligast i rapporterna från Gotland."
    )
    assert county_sentence({"SE-I": 0}, "sv") is None


def test_data_sentences_put_months_first() -> None:
    assert data_sentences(MIGRANT, {"SE-I": 100}, "sv") == [
        "Rapporteras mest i maj till juli.",
        "Nästan aldrig i oktober till mars.",
        "Vanligast i rapporterna från Gotland.",
    ]


def test_join_list_empty_is_empty_string() -> None:
    assert join_list([], "sv") == ""


def test_no_peak_month_gives_no_most_sentence() -> None:
    assert month_sentences([15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70], "sv") == []


def test_no_peak_but_low_months_gives_only_never_sentence() -> None:
    # Profile: January and February at 5 (low), rest at 50 (not peak, not low)
    profile = [5, 5, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50]
    result = month_sentences(profile, "sv")
    assert len(result) == 1
    expected = "Nästan aldrig i januari och februari."
    assert result[0] == expected


def test_all_zero_profile_gives_no_month_sentences() -> None:
    assert month_sentences([0] * 12, "sv") == []
