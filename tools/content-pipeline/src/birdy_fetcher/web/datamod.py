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
# The county map shows each county's share of all its bird reports (spec §9.2). Before the
# R3 trial (2026-10-07) the sentence named the top three as "Vanligast i rapporterna från
# ...", which reads as "most reports", and for a widespread species the top three are small
# differences (Talgoxe and Bofink both got three Norrland counties: northern counties report
# fewer species, so a common feeder bird makes up more of their reports). Now a flat profile
# says how many counties report the species, and a skewed one names the counties with the
# largest share, worded as a share.
COUNTIES_SHARE = {
    "sv": "Andelen av alla fågelrapporter är högst i {counties}.",
    "en": "Its share of all bird reports is highest in {counties}.",
}
COUNTIES_ALL = {"sv": "Rapporteras från alla 21 län.", "en": "Reported from all 21 counties."}
COUNTIES_SOME = {
    "sv": "Rapporteras från {n} av 21 län.",
    "en": "Reported from {n} of the 21 counties.",
}
# Flat: at least half the counties have at least half the top county's share.
FLAT_MEDIAN = 50
# Named in a skewed profile: at least half the top county's share, at most three.
SHARE_MIN = 50
SHARE_COUNTIES = 3
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
    """A county missing from `profile` has no reports."""
    values = sorted(profile.get(iso, 0) for iso in COUNTY_NAMES)
    reported = sum(1 for value in values if value > 0)
    if reported == 0:
        return None
    if values[len(values) // 2] >= FLAT_MEDIAN:
        if reported == len(COUNTY_NAMES):
            return COUNTIES_ALL[lang]
        return COUNTIES_SOME[lang].format(n=reported)
    ranked = sorted(
        (iso for iso, value in profile.items() if value >= SHARE_MIN),
        key=lambda iso: (-profile[iso], COUNTY_NAMES[iso]),
    )[:SHARE_COUNTIES]
    names = [COUNTY_NAMES[iso] for iso in ranked]
    return COUNTIES_SHARE[lang].format(counties=join_list(names, lang))


def is_county_share_sentence(sentence: str) -> bool:
    """True for the skewed-profile county sentence: a share, which the text must keep
    calling a share (text_checks)."""
    return any(sentence.startswith(t.split("{", 1)[0]) for t in COUNTIES_SHARE.values())


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


def record_status_contradiction(record: dict[str, Any]) -> str | None:
    """The contradiction for the record's current status fact and current `data`. Readers
    use this, not the stored `data.statusSignal`: `web sources --force` rebuilds `data` with
    the signal reset to None (I3, final review 2026-10-06)."""
    status = next((f for f in record.get("facts", []) if f.get("topic") == "status"), None)
    data = record.get("data")
    if status is None or not data:
        return None
    return status_contradiction(
        status["value"], data.get("months"), int(data.get("totalReports", 0))
    )


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
