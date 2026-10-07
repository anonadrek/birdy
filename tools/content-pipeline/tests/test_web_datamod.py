"""Tests for web/datamod.py and web/counties.py: shares, sentences and the status signal."""

from __future__ import annotations

import pytest

from birdy_fetcher.web.counties import COUNTIES
from birdy_fetcher.web.datamod import (
    MIN_REPORTS,
    Counts,
    build_data,
    county_profile,
    county_sentence,
    data_sentences,
    join_list,
    month_profile,
    month_runs,
    month_sentences,
    months_text,
    red_list_for_page,
    scaled,
    sentence_kind,
    status_contradiction,
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


# Artportalen-only county profiles (2026-10-07). Talgoxe's is flat: every county lies
# within a factor of about three of the top one, and the old sentence "Vanligast i
# rapporterna från Norrbotten, Västerbotten och Västernorrland" read as "most reports" while
# it meant the largest share of each county's reports (a composition effect: northern
# counties report fewer species, so a common feeder bird makes up more of their reports).
TALGOXE_COUNTIES = {
    "SE-K": 44, "SE-W": 53, "SE-X": 77, "SE-I": 39, "SE-N": 30, "SE-Z": 60, "SE-F": 62,
    "SE-H": 35, "SE-G": 45, "SE-BD": 100, "SE-T": 42, "SE-E": 62, "SE-M": 41, "SE-D": 83,
    "SE-AB": 66, "SE-C": 68, "SE-S": 49, "SE-AC": 69, "SE-Y": 80, "SE-U": 45, "SE-O": 64,
}  # fmt: skip
TOBISGRISSLA_COUNTIES = {
    "SE-K": 39, "SE-W": 0, "SE-X": 26, "SE-I": 67, "SE-N": 100, "SE-Z": 0, "SE-F": 0,
    "SE-H": 57, "SE-G": 0, "SE-BD": 13, "SE-T": 0, "SE-E": 7, "SE-M": 32, "SE-D": 10,
    "SE-AB": 42, "SE-C": 13, "SE-S": 1, "SE-AC": 29, "SE-Y": 52, "SE-U": 0, "SE-O": 29,
}  # fmt: skip


def test_a_flat_county_profile_says_where_it_is_reported_not_a_top_three() -> None:
    assert county_sentence(TALGOXE_COUNTIES, "sv") == "Rapporteras från alla 21 län."
    assert county_sentence(TALGOXE_COUNTIES, "en") == "Reported from all 21 counties."


def test_a_flat_profile_with_a_county_without_reports_counts_the_counties() -> None:
    profile = {**TALGOXE_COUNTIES, "SE-K": 0}
    assert county_sentence(profile, "sv") == "Rapporteras från 20 av 21 län."
    assert county_sentence(profile, "en") == "Reported from 20 of the 21 counties."


def test_a_skewed_profile_names_the_counties_by_their_share() -> None:
    """Half the counties under half the top share: the counties with at least half of it,
    at most three, said as a share so it cannot be read as "the most reports"."""
    assert county_sentence(TOBISGRISSLA_COUNTIES, "sv") == (
        "Andelen av alla fågelrapporter är högst i Halland, Gotland och Kalmar."
    )
    assert county_sentence(TOBISGRISSLA_COUNTIES, "en") == (
        "Its share of all bird reports is highest in Halland, Gotland and Kalmar."
    )


def test_a_skewed_profile_names_only_the_counties_near_the_top() -> None:
    assert county_sentence({"SE-I": 100, "SE-M": 0}, "sv") == (
        "Andelen av alla fågelrapporter är högst i Gotland."
    )
    tretaig_mas = {"SE-N": 100, "SE-O": 27, "SE-M": 25, "SE-H": 14, "SE-K": 13}
    assert (
        county_sentence(tretaig_mas, "sv") == "Andelen av alla fågelrapporter är högst i Halland."
    )
    assert county_sentence({"SE-I": 0}, "sv") is None


def test_data_sentences_put_months_first() -> None:
    assert data_sentences(MIGRANT, {"SE-I": 100}, "sv") == [
        "Rapporteras mest i maj till juli.",
        "Nästan aldrig i oktober till mars.",
        "Andelen av alla fågelrapporter är högst i Gotland.",
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


RESIDENT = [70, 65, 60, 55, 70, 79, 64, 68, 74, 100, 66, 69]
WINTER_VISITOR = [100, 90, 70, 20, 2, 0, 0, 1, 10, 40, 80, 95]


def test_no_contradiction_when_data_agrees() -> None:
    assert status_contradiction("resident", RESIDENT, 5000) is None
    assert status_contradiction("breeding_migrant", MIGRANT, 5000) is None
    assert status_contradiction("winter_visitor", WINTER_VISITOR, 5000) is None
    assert status_contradiction("passage", MIGRANT, 5000) is None


def test_resident_with_an_empty_month_is_flagged() -> None:
    reason = status_contradiction("resident", MIGRANT, 5000)
    assert reason is not None
    assert "januari" in reason


def test_migrant_reported_in_winter_is_flagged() -> None:
    assert status_contradiction("breeding_migrant", RESIDENT, 5000) is not None


def test_winter_visitor_reported_in_summer_is_flagged() -> None:
    assert status_contradiction("winter_visitor", RESIDENT, 5000) is not None


def test_absent_with_many_reports_is_flagged_even_without_a_profile() -> None:
    assert status_contradiction("absent", None, MIN_REPORTS) is not None
    assert status_contradiction("absent", None, MIN_REPORTS - 1) is None


def test_missing_profile_never_flags_other_statuses() -> None:
    assert status_contradiction("resident", None, 50) is None


def test_build_data_with_enough_reports() -> None:
    species = Counts([10] * 12, {"SE-I": 50}, 1200)
    all_birds = Counts([100] * 12, {"SE-I": 100, "SE-M": 100}, 99_000)
    data = build_data(taxon_key=7, species=species, all_birds=all_birds, fetched_at="2026-10-01")
    assert data["months"] == [100] * 12
    assert data["counties"]["SE-I"] == 100
    assert data["sentences"]["sv"][0] == "Rapporteras året runt."
    assert data["totalReports"] == 1200
    assert data["gbifTaxonKey"] == 7
    assert data["raw"]["speciesByCounty"] == {"SE-I": 50}
    assert data["statusSignal"] == {"contradicts": None}


def test_build_data_with_too_few_reports_has_no_modules() -> None:
    species = Counts([1] * 12, {}, MIN_REPORTS - 1)
    data = build_data(
        taxon_key=7, species=species, all_birds=Counts([100] * 12, {}, 1), fetched_at="x"
    )
    assert "months" not in data
    assert "counties" not in data
    assert data["sentences"]["sv"] == [
        "Sällsynt i Sverige: 199 rapporter i Artportalen 2016 till 2025."
    ]


# Artportalen-only month profiles 2016 to 2025 (fetched 2026-10-07, after the dataset
# filter): the R2 calibration species, and Kungsfågel, whose profile the ringing captures
# had turned into an autumn peak (September 89, October 100, May to August under 10).
TALGOXE_MONTHS = [100, 93, 60, 39, 30, 30, 25, 26, 37, 55, 82, 100]
LADUSVALA_MONTHS = [0, 1, 1, 32, 75, 65, 82, 100, 81, 17, 1, 1]
SIDENSVANS_MONTHS = [78, 54, 17, 5, 2, 4, 4, 2, 6, 62, 95, 100]
KUNGSFAGEL_MONTHS = [66, 49, 49, 40, 28, 33, 21, 26, 70, 100, 75, 64]


def test_the_calibration_species_pass_the_status_signal_on_artportalen_data() -> None:
    assert status_contradiction("resident", TALGOXE_MONTHS, 729_841) is None
    assert status_contradiction("breeding_migrant", LADUSVALA_MONTHS, 334_341) is None
    assert status_contradiction("winter_visitor", SIDENSVANS_MONTHS, 178_590) is None
    assert status_contradiction("absent", None, 0) is None  # Koboltmes


def test_kungsfagel_without_ringing_is_reported_all_winter() -> None:
    """A summer-visitor status for Kungsfågel passed the signal on the old counts; on
    Artportalen's own it is flagged, and a resident status is not."""
    assert status_contradiction("resident", KUNGSFAGEL_MONTHS, 244_744) is None
    assert status_contradiction("breeding_migrant", KUNGSFAGEL_MONTHS, 244_744) is not None


def test_no_reports_at_all_says_the_species_does_not_occur_in_sweden() -> None:
    """R3 (2026-10-07): Koboltmes's page never said the bird does not occur in Sweden. An
    exact GBIF match with no Artportalen report in ten years says so, with the evidence."""
    species = Counts([0] * 12, {}, 0)
    data = build_data(
        taxon_key=7341849, species=species, all_birds=Counts([100] * 12, {}, 1), fetched_at="x"
    )
    assert data["sentences"] == {
        "sv": ["Förekommer inte i Sverige: inga rapporter i Artportalen 2016 till 2025."],
        "en": ["Does not occur in Sweden: no reports in Artportalen 2016 to 2025."],
    }
    assert sentence_kind(data["sentences"]["sv"][0]) == "absent"


def test_a_few_reports_say_the_species_is_rare_in_sweden() -> None:
    few = Counts([1] * 12, {}, 12)
    data = build_data(taxon_key=7, species=few, all_birds=Counts([100] * 12, {}, 1), fetched_at="x")
    assert data["sentences"] == {
        "sv": ["Sällsynt i Sverige: 12 rapporter i Artportalen 2016 till 2025."],
        "en": ["Rare in Sweden: 12 reports in Artportalen 2016 to 2025."],
    }
    assert sentence_kind(data["sentences"]["sv"][0]) == "rare"
    one = build_data(taxon_key=7, species=Counts([0] * 12, {}, 1), all_birds=few, fetched_at="x")
    assert one["sentences"]["sv"] == ["Sällsynt i Sverige: 1 rapport i Artportalen 2016 till 2025."]
    assert one["sentences"]["en"] == ["Rare in Sweden: 1 report in Artportalen 2016 to 2025."]


def test_the_other_sentences_have_no_kind_but_the_county_share() -> None:
    assert sentence_kind("Rapporteras året runt.") is None
    assert sentence_kind("Rapporteras från alla 21 län.") is None
    assert sentence_kind("Andelen av alla fågelrapporter är högst i Gotland.") == "countyShare"


def test_a_species_not_regular_in_sweden_is_not_shown_as_not_red_listed() -> None:
    """The red list on GBIF holds only red-listed species, so `not_listed` cannot tell
    "assessed, least concern" from "not assessed". A species with fewer than 200 reports is
    not regular in Sweden and not assessed (NA/NE): no red-list row, no data fact."""
    assert red_list_for_page("not_listed", 962_371) == "not_listed"
    assert red_list_for_page("not_listed", MIN_REPORTS - 1) is None
    assert red_list_for_page("not_listed", 0) is None
    assert red_list_for_page("VU", 120) == "VU"
    assert red_list_for_page(None, 962_371) is None


def test_a_status_saying_the_species_is_here_contradicts_zero_reports() -> None:
    for status in ("resident", "breeding_migrant", "passage", "winter_visitor"):
        assert status_contradiction(status, None, 0) is not None
    assert status_contradiction("rare_visitor", None, 0) is None
    assert status_contradiction("absent", None, 0) is None
    assert status_contradiction("resident", None, 12) is None
