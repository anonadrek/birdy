"""Sweden's 21 counties: GBIF's GADM level 1 ids to ISO 3166-2:SE codes (spec §9.1).

Verified 2026-10-01 against GBIF occurrence records (gadm.level1.name). The county names are
the same in Swedish and English on the pages."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class County:
    iso: str
    name: str


COUNTIES: dict[str, County] = {
    "SWE.1_1": County("SE-K", "Blekinge"),
    "SWE.2_1": County("SE-W", "Dalarna"),
    "SWE.3_1": County("SE-X", "Gävleborg"),
    "SWE.4_1": County("SE-I", "Gotland"),
    "SWE.5_1": County("SE-N", "Halland"),
    "SWE.6_1": County("SE-Z", "Jämtland"),
    "SWE.7_1": County("SE-F", "Jönköping"),
    "SWE.8_1": County("SE-H", "Kalmar"),
    "SWE.9_1": County("SE-G", "Kronoberg"),
    "SWE.10_1": County("SE-BD", "Norrbotten"),
    "SWE.11_1": County("SE-T", "Örebro"),
    "SWE.12_1": County("SE-E", "Östergötland"),
    "SWE.13_1": County("SE-M", "Skåne"),
    "SWE.14_1": County("SE-D", "Södermanland"),
    "SWE.15_1": County("SE-AB", "Stockholm"),
    "SWE.16_1": County("SE-C", "Uppsala"),
    "SWE.17_1": County("SE-S", "Värmland"),
    "SWE.18_1": County("SE-AC", "Västerbotten"),
    "SWE.19_1": County("SE-Y", "Västernorrland"),
    "SWE.20_1": County("SE-U", "Västmanland"),
    "SWE.21_1": County("SE-O", "Västra Götaland"),
}

COUNTY_NAMES: dict[str, str] = {c.iso: c.name for c in COUNTIES.values()}
