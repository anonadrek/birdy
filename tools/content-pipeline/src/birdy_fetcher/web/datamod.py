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
# About reports, not presence (fix wave 2026-10-07): "Nästan aldrig i juni" read as "not
# there in June" for Pilgrimsfalk and Sparvhök, whose breeding records are withheld.
RARELY = {"sv": "Rapporteras sällan i {months}.", "en": "Rarely reported in {months}."}
# May to July (0-based): a resident's or breeding migrant's few reports then are a gap in
# the data (withheld breeding records, no ringing), so the writer does not get those months
# as "rarely reported" (facts.data_facts).
BREEDING_MONTHS = frozenset({4, 5, 6})
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
    rarely = rarely_sentence(profile, lang)
    if rarely is not None:
        sentences.append(rarely)
    return sentences


def rarely_sentence(
    profile: list[int], lang: str, *, skip: frozenset[int] = frozenset()
) -> str | None:
    """The months with a value of LOW or less, minus `skip`, as a "rarely reported"
    sentence; None when there are none."""
    low = {i for i, value in enumerate(profile) if value <= LOW} - skip
    return RARELY[lang].format(months=months_text(low, lang)) if low else None


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


# Fewer than MIN_REPORTS reports: no charts, but what the data says about the species in
# Sweden (R3, 2026-10-07: Koboltmes's page never said the bird does not occur here). An
# exact GBIF match with no Artportalen report in ten years does not occur in Sweden.
NO_REPORTS = {
    "sv": "Förekommer inte i Sverige: inga rapporter i Artportalen 2016 till 2025.",
    "en": "Does not occur in Sweden: no reports in Artportalen 2016 to 2025.",
}
FEW_REPORTS = {
    "sv": "Sällsynt i Sverige: {n} {reports} i Artportalen 2016 till 2025.",
    "en": "Rare in Sweden: {n} {reports} in Artportalen 2016 to 2025.",
}
_REPORTS = {"sv": ("rapport", "rapporter"), "en": ("report", "reports")}


def presence_sentence(total: int, lang: str) -> str:
    """The sentence for a species with fewer than MIN_REPORTS reports."""
    if total == 0:
        return NO_REPORTS[lang]
    one, many = _REPORTS[lang]
    return FEW_REPORTS[lang].format(n=total, reports=one if total == 1 else many)


def _prefix(template: str) -> str:
    return template.split("{", 1)[0]


def sentence_kind(sentence: str) -> str | None:
    """What a data sentence is, where code needs to know (a Swedish or English sentence):
    `absent` (no reports: the page's status is "Förekommer inte"), `countyShare` (a share,
    which the text must keep calling a share, text_checks), `rarelyReported` (the writer
    gets it without breeding-season months for a species that breeds here, facts)."""
    if sentence in NO_REPORTS.values():
        return "absent"
    if any(sentence.startswith(_prefix(t)) for t in COUNTIES_SHARE.values()):
        return "countyShare"
    if any(sentence.startswith(_prefix(t)) for t in RARELY.values()):
        return "rarelyReported"
    return None


def red_list_for_page(code: str | None, total_reports: int) -> str | None:
    """The red list's dataset on GBIF holds only red-listed species, so `not_listed` cannot
    tell "assessed, least concern" from "not assessed". A species with fewer than
    MIN_REPORTS reports in ten years is not regular in Sweden and not assessed (NA or NE):
    no category then, so the page hides the row and no "Inte rödlistad" sentence is written
    (R3, 2026-10-07, Koboltmes). A listed category always stands."""
    if code == "not_listed" and total_reports < MIN_REPORTS:
        return None
    return code


def data_sentences(months: list[int], counties: dict[str, int], lang: str) -> list[str]:
    sentences = month_sentences(months, lang)
    county = county_sentence(counties, lang)
    if county is not None:
        sentences.append(county)
    return sentences


RESIDENT_MIN_MONTH = 5
MIGRANT_WINTER_MAX = 25
# A partial migrant has some of its birds here all winter: a winter mean this low is a
# species that leaves altogether (Ladusvala's is under 1).
PARTIAL_MIGRANT_WINTER_MIN = 5
WINTER_VISITOR_SUMMER_MAX = 25
_WINTER = (11, 0, 1)
_SUMMER = (5, 6)


def _mean(profile: list[int], months: tuple[int, ...]) -> float:
    return sum(profile[i] for i in months) / len(months)


# A status that says the species is in Sweden, with its label in the flag message.
_PRESENT = {
    "resident": "stannfågel",
    "partial_migrant": "delvis flyttfågel",
    "breeding_migrant": "flyttfågel som häckar här",
    "passage": "ses under flyttningen",
    "winter_visitor": "vintergäst",
}


def status_contradiction(
    status: str, months: list[int] | None, total_reports: int | None
) -> str | None:
    """A plain Swedish reason when the report data clearly contradicts the stated status,
    otherwise None. Only clear contradictions count (spec §9.2); rare_visitor is never
    flagged. `total_reports` is None when the data does not say (no GBIF match)."""
    if status == "absent":
        if total_reports is not None and total_reports >= MIN_REPORTS:
            return (
                "Statusen säger att arten inte förekommer i Sverige, men den har "
                f"{total_reports} rapporter i Artportalen 2016 till 2025."
            )
        return None
    if status in _PRESENT and total_reports == 0:
        return (
            f"Statusen ({_PRESENT[status]}) säger att arten finns i Sverige, men den har "
            "inga rapporter i Artportalen 2016 till 2025."
        )
    if months is None:
        return None
    if status == "resident" and min(months) < RESIDENT_MIN_MONTH:
        lowest = MONTHS["sv"][months.index(min(months))]
        return f"Statusen säger stannfågel, men arten rapporteras nästan aldrig i {lowest}."
    if status == "partial_migrant" and _mean(months, _WINTER) < PARTIAL_MIGRANT_WINTER_MIN:
        return (
            "Statusen säger delvis flyttfågel, men arten rapporteras nästan aldrig "
            "december till februari."
        )
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
    return data_status_contradiction(status["value"], data)


def data_status_contradiction(status: str, data: dict[str, Any]) -> str | None:
    """`status_contradiction` for a record's `data` object."""
    total = data.get("totalReports")
    return status_contradiction(
        status, data.get("months"), int(total) if total is not None else None
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
        data["sentences"] = {
            lang: [presence_sentence(species.total, lang)] for lang in ("sv", "en")
        }
    data["raw"] = {
        "speciesByMonth": species.by_month,
        "allBirdsByMonth": all_birds.by_month,
        "speciesByCounty": species.by_county,
        "allBirdsByCounty": all_birds.by_county,
    }
    data["statusSignal"] = {"contradicts": None}
    return data
