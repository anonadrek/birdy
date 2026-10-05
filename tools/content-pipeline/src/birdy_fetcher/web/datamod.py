"""Month and county shares from Artportalen report counts (spec 2026-09-25 §9.2).

Pure functions: the GBIF client fetches the counts, this module turns them into the numbers
and sentences on the page. A model is never involved."""

from __future__ import annotations

from dataclasses import dataclass

from .counties import COUNTIES

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
