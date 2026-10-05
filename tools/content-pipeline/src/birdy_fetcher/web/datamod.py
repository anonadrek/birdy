"""Month and county shares from Artportalen report counts (spec 2026-09-25 §9.2).

Pure functions: the GBIF client fetches the counts, this module turns them into the numbers
and sentences on the page. A model is never involved."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .counties import COUNTIES, COUNTY_NAMES

MIN_REPORTS = 200


@dataclass(frozen=True)
class Counts:
    """Report counts for one taxon in Sweden 2016 to 2025, CC0 records only."""

    by_month: list[int]
    by_county: dict[str, int]
    total: int


def scaled(shares: list[float]) -> list[int]:
    """Scale so the largest share becomes 100. A share above zero never rounds down to 0, so
    a county shown as 'no reports' really has none."""
    top = max(shares, default=0.0)
    if top <= 0:
        return [0] * len(shares)
    return [max(1, round(s / top * 100)) if s > 0 else 0 for s in shares]


def _share(species: int, total: int) -> float:
    return species / total if total > 0 else 0.0


def month_profile(species_by_month: list[int], all_by_month: list[int]) -> list[int]:
    if len(species_by_month) != 12 or len(all_by_month) != 12:
        raise ValueError("Månadsdatan ska ha tolv värden")
    pairs = zip(species_by_month, all_by_month, strict=True)
    return scaled([_share(s, a) for s, a in pairs])


def county_profile(
    species_by_county: dict[str, int], all_by_county: dict[str, int]
) -> dict[str, int]:
    isos = [c.iso for c in COUNTIES.values()]
    shares = [_share(species_by_county.get(i, 0), all_by_county.get(i, 0)) for i in isos]
    return dict(zip(isos, scaled(shares), strict=True))


MONTHS = {
    "sv": (
        "januari", "februari", "mars", "april", "maj", "juni",
        "juli", "augusti", "september", "oktober", "november", "december",
    ),
    "en": (
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    ),
}  # fmt: skip
_AND = {"sv": "och", "en": "and"}
_TO = {"sv": "till", "en": "to"}
ALL_YEAR = {"sv": "Rapporteras året runt.", "en": "Reported all year round."}
MOST = {"sv": "Rapporteras mest i {months}.", "en": "Reported most in {months}."}
NEVER = {"sv": "Nästan aldrig i {months}.", "en": "Almost never in {months}."}
COUNTIES_SENTENCE = {
    "sv": "Vanligast i rapporterna från {counties}.",
    "en": "Most common in reports from {counties}.",
}
PEAK = 80
LOW = 10
ALL_YEAR_MIN = 30


def join_list(items: list[str], lang: str) -> str:
    if not items:
        return ""
    if len(items) == 1:
        return items[0]
    return ", ".join(items[:-1]) + f" {_AND[lang]} " + items[-1]


def month_runs(selected: set[int]) -> list[list[int]]:
    """Runs of consecutive months (0 to 11), where December and January are neighbours."""
    if not selected:
        return []
    if len(selected) == 12:
        return [list(range(12))]
    start = next(i for i in range(12) if i in selected and (i - 1) % 12 not in selected)
    runs: list[list[int]] = []
    current: list[int] = []
    for k in range(12):
        month = (start + k) % 12
        if month in selected:
            current.append(month)
        elif current:
            runs.append(current)
            current = []
    if current:
        runs.append(current)
    return runs


def months_text(selected: set[int], lang: str) -> str:
    parts: list[str] = []
    for run in month_runs(selected):
        names = [MONTHS[lang][i] for i in run]
        parts.extend([f"{names[0]} {_TO[lang]} {names[-1]}"] if len(run) >= 3 else names)
    return join_list(parts, lang)


def month_sentences(profile: list[int], lang: str) -> list[str]:
    if min(profile) >= ALL_YEAR_MIN:
        return [ALL_YEAR[lang]]
    if max(profile, default=0) <= 0:
        return []
    peak = {i for i, value in enumerate(profile) if value >= PEAK}
    sentences: list[str] = []
    if peak:
        sentences.append(MOST[lang].format(months=months_text(peak, lang)))
    low = {i for i, value in enumerate(profile) if value <= LOW}
    if low:
        sentences.append(NEVER[lang].format(months=months_text(low, lang)))
    return sentences


def county_sentence(profile: dict[str, int], lang: str) -> str | None:
    ranked = sorted(
        (iso for iso, value in profile.items() if value > 0),
        key=lambda iso: (-profile[iso], COUNTY_NAMES[iso]),
    )[:3]
    if not ranked:
        return None
    names = [COUNTY_NAMES[iso] for iso in ranked]
    return COUNTIES_SENTENCE[lang].format(counties=join_list(names, lang))


def data_sentences(months: list[int], counties: dict[str, int], lang: str) -> list[str]:
    sentences = month_sentences(months, lang)
    county = county_sentence(counties, lang)
    if county is not None:
        sentences.append(county)
    return sentences


RESIDENT_MIN_MONTH = 5
MIGRANT_WINTER_MAX = 25
WINTER_VISITOR_SUMMER_MAX = 25
_WINTER = (11, 0, 1)
_SUMMER = (5, 6)


def _mean(profile: list[int], months: tuple[int, ...]) -> float:
    return sum(profile[i] for i in months) / len(months)


def status_contradiction(status: str, months: list[int] | None, total_reports: int) -> str | None:
    """A plain Swedish reason when the report data clearly contradicts the stated status,
    otherwise None. Only clear contradictions count (spec §9.2); passage and rare_visitor are
    never flagged."""
    if status == "absent":
        if total_reports >= MIN_REPORTS:
            return (
                "Statusen säger att arten inte förekommer i Sverige, men den har "
                f"{total_reports} rapporter i Artportalen 2016 till 2025."
            )
        return None
    if months is None:
        return None
    if status == "resident" and min(months) < RESIDENT_MIN_MONTH:
        lowest = MONTHS["sv"][months.index(min(months))]
        return f"Statusen säger stannfågel, men arten rapporteras nästan aldrig i {lowest}."
    if status == "breeding_migrant" and _mean(months, _WINTER) > MIGRANT_WINTER_MAX:
        return "Statusen säger flyttfågel, men arten rapporteras ofta december till februari."
    if status == "winter_visitor" and _mean(months, _SUMMER) > WINTER_VISITOR_SUMMER_MAX:
        return "Statusen säger vintergäst, men arten rapporteras ofta i juni och juli."
    return None


def build_data(
    *, taxon_key: int, species: Counts, all_birds: Counts, fetched_at: str
) -> dict[str, Any]:
    """The record's `data` object (spec appendix C)."""
    data: dict[str, Any] = {
        "fetchedAt": fetched_at,
        "gbifTaxonKey": taxon_key,
        "totalReports": species.total,
    }
    if species.total >= MIN_REPORTS:
        months = month_profile(species.by_month, all_birds.by_month)
        counties = county_profile(species.by_county, all_birds.by_county)
        data["months"] = months
        data["counties"] = counties
        data["sentences"] = {lang: data_sentences(months, counties, lang) for lang in ("sv", "en")}
    else:
        data["sentences"] = {"sv": [], "en": []}
    data["raw"] = {
        "speciesByMonth": species.by_month,
        "allBirdsByMonth": all_birds.by_month,
        "speciesByCounty": species.by_county,
        "allBirdsByCounty": all_birds.by_county,
    }
    data["statusSignal"] = {"contradicts": None}
    return data
