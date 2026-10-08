"""GBIF: exact taxon match, Artportalen counts per month and county, and the Swedish Red
List 2025 (spec 2026-09-25 §9.1). Only CC0 records are counted (spec §10)."""

from __future__ import annotations

import json
from typing import Any
from urllib.parse import quote

from ..cache import Cache
from .counties import COUNTIES
from .datamod import Counts
from .http import ThrottledHttp

API = "https://api.gbif.org/v1"
# Artportalen's own dataset (checked against the GBIF API 2026-10-07: "Artportalen", CC0).
# Without it the counts also held the Bird Ringing Centre's captures (6.4 % of all Swedish
# bird records; 49.5 % of Kungsfågel's, 32.5 % of Rödhake's), which pile up at ringing
# stations in autumn and skewed the month chart, the county map and the status signal,
# and the Swedish Bird Survey's routes; the pages credit Artportalen.
ARTPORTALEN_DATASET = "38b4c89f-584c-41bb-bd8f-cd1def33e92f"
FILTERS = (
    f"country=SE&year=2016,2025&license=CC0_1_0&occurrenceStatus=PRESENT"
    f"&datasetKey={ARTPORTALEN_DATASET}"
)
AVES_TAXON_KEY = 212
# A fuzzy match must be this sure (GBIF's own confidence, 0 to 100).
FUZZY_MIN_CONFIDENCE = 95
ALL_BIRDS_CACHE_KEY = "_aves"
# A new name for the Artportalen-only counts, so counts cached before the dataset filter
# (gbif-counts-2016-2025.json) are never read again.
COUNTS_CACHE_NAME = "gbif-counts-artportalen-2016-2025.json"
REDLIST_DATASET = "87e639cc-30a9-4007-bd2c-b0cab60326b9"
REDLIST_CODES = {
    "REGIONALLY_EXTINCT": "RE",
    "EXTINCT": "RE",
    "EXTINCT_IN_THE_WILD": "RE",
    "CRITICALLY_ENDANGERED": "CR",
    "ENDANGERED": "EN",
    "VULNERABLE": "VU",
    "NEAR_THREATENED": "NT",
    "DATA_DEFICIENT": "DD",
}
NOT_LISTED = {"LEAST_CONCERN", "NOT_EVALUATED", "NOT_APPLICABLE"}


def parse_counts(data: dict[str, Any]) -> Counts:
    by_month = [0] * 12
    by_county: dict[str, int] = {}
    for facet in data.get("facets", []):
        field = facet.get("field")
        for entry in facet.get("counts", []):
            if field == "MONTH":
                by_month[int(entry["name"]) - 1] = int(entry["count"])
            elif field == "GADM_LEVEL_1_GID" and entry["name"] in COUNTIES:
                by_county[COUNTIES[entry["name"]].iso] = int(entry["count"])
    return Counts(by_month=by_month, by_county=by_county, total=int(data.get("count", 0)))


def _undoubled(name: str) -> str:
    """The name in lower case with every doubled letter written once."""
    out: list[str] = []
    for ch in name.lower():
        if not out or out[-1] != ch:
            out.append(ch)
    return "".join(out)


def same_name(gbif_name: str, scientific: str) -> bool:
    """GBIF's backbone spells a few names with a doubled letter ("Phylloscopus sibillatrix"
    for Grönsångare): such a fuzzy match is the same name, nothing else is (2026-10-07)."""
    return _undoubled(gbif_name) == _undoubled(scientific)


def red_list_code(results: list[dict[str, Any]], scientific: str, taxon_key: int) -> str | None:
    """`not_listed` when the species is not on the list, its code when it is, and None when
    the list uses a category this table does not know (reported, never guessed)."""
    name = scientific.lower()
    for entry in results:
        if entry.get("taxonomicStatus") != "ACCEPTED" or entry.get("rank") != "SPECIES":
            continue
        same_taxon = entry.get("nubKey") == taxon_key
        if not same_taxon and str(entry.get("canonicalName", "")).lower() != name:
            continue
        statuses = entry.get("threatStatuses") or []
        if not statuses or statuses[0] in NOT_LISTED:
            return "not_listed"
        return REDLIST_CODES.get(statuses[0])
    return "not_listed"


class GbifClient:
    def __init__(self, *, cache: Cache, http: ThrottledHttp | None = None) -> None:
        self.cache = cache
        self._http = http or ThrottledHttp(min_interval=0.2)

    async def _json(self, cache_key: str, name: str, url: str, refresh: bool) -> Any:
        raw = None if refresh else self.cache.get(cache_key, name)
        if raw is None:
            raw = await self._http.fetch_text(url)
            self.cache.put(cache_key, name, raw)
        return json.loads(raw)

    async def taxon_key(self, qid: str, scientific: str, *, refresh: bool = False) -> int | None:
        url = f"{API}/species/match?kingdom=Animalia&strict=true&name={quote(scientific)}"
        data = await self._json(qid, "gbif-match.json", url, refresh)
        # Fix wave 2026-10-07: only a bird (class Aves) counts, a fuzzy match only when
        # GBIF is sure and the names differ by a doubled letter alone, and the accepted key
        # is used whenever GBIF gives one (not only for "status": "SYNONYM").
        bird = data.get("classKey") == AVES_TAXON_KEY
        exact = data.get("matchType") == "EXACT" or (
            data.get("matchType") == "FUZZY"
            and int(data.get("confidence", 0)) >= FUZZY_MIN_CONFIDENCE
            and same_name(str(data.get("canonicalName", "")), scientific)
        )
        if not bird or not exact or data.get("rank") != "SPECIES":
            return None
        key = data.get("acceptedUsageKey")
        key = key if key is not None else data.get("usageKey")
        return int(key) if key is not None else None

    async def counts(self, qid: str, taxon_key: int, *, refresh: bool = False) -> Counts:
        return await self._counts(qid, taxon_key, refresh)

    async def all_birds(self, *, refresh: bool = False) -> Counts:
        return await self._counts(ALL_BIRDS_CACHE_KEY, AVES_TAXON_KEY, refresh)

    async def _counts(self, cache_key: str, taxon_key: int, refresh: bool) -> Counts:
        url = (
            f"{API}/occurrence/search?{FILTERS}&taxonKey={taxon_key}"
            "&limit=0&facet=month&facet=gadmLevel1Gid&facetLimit=30"
        )
        # The taxon key is part of the name: a species whose match changes (a synonym now
        # read as its accepted key) must not reuse the old key's counts.
        name = COUNTS_CACHE_NAME.replace(".json", f"-{taxon_key}.json")
        return parse_counts(await self._json(cache_key, name, url, refresh))

    async def swedish_red_list(
        self, qid: str, scientific: str, taxon_key: int, *, refresh: bool = False
    ) -> str | None:
        url = f"{API}/species/search?datasetKey={REDLIST_DATASET}&limit=20&q={quote(scientific)}"
        data = await self._json(qid, "gbif-redlist-2025.json", url, refresh)
        return red_list_code(data.get("results", []), scientific, taxon_key)
