"""Tests for web/gbif.py: taxon match, counts and the Swedish Red List 2025."""

from __future__ import annotations

import json
from pathlib import Path

from birdy_fetcher.cache import Cache
from birdy_fetcher.web.gbif import (
    ARTPORTALEN_DATASET,
    AVES_TAXON_KEY,
    GbifClient,
    parse_counts,
    red_list_code,
)
from birdy_fetcher.web.http import ThrottledHttp

COUNTS = {
    "count": 962339,
    "facets": [
        {"field": "MONTH", "counts": [{"name": "10", "count": 123789}, {"name": "1", "count": 5}]},
        {
            "field": "GADM_LEVEL_1_GID",
            "counts": [
                {"name": "SWE.21_1", "count": 146594},
                {"name": "NOR.1_1", "count": 12},
            ],
        },
    ],
}


class Routed:
    def __init__(self, routes: dict[str, object]) -> None:
        self.routes = routes
        self.urls: list[str] = []

    async def __call__(self, url: str) -> str:
        self.urls.append(url)
        for key, body in self.routes.items():
            if key in url:
                return json.dumps(body)
        raise FileNotFoundError(url)


def _client(tmp_path: Path, routes: dict[str, object]) -> tuple[GbifClient, Routed]:
    http = Routed(routes)
    client = GbifClient(cache=Cache(tmp_path), http=ThrottledHttp(get_text=http, min_interval=0))
    return client, http


def test_parse_counts_keeps_swedish_counties_only() -> None:
    counts = parse_counts(COUNTS)
    assert counts.total == 962339
    assert counts.by_month[9] == 123789
    assert counts.by_month[0] == 5
    assert counts.by_month[5] == 0
    assert counts.by_county == {"SE-O": 146594}


async def test_taxon_key_needs_an_exact_species_match(tmp_path: Path) -> None:
    client, _ = _client(
        tmp_path,
        {"name=Parus%20major": {"usageKey": 9705453, "matchType": "EXACT", "rank": "SPECIES"}},
    )
    assert await client.taxon_key("Q25485", "Parus major") == 9705453
    fuzzy, _ = _client(
        tmp_path / "b", {"species/match": {"usageKey": 1, "matchType": "FUZZY", "rank": "SPECIES"}}
    )
    assert await fuzzy.taxon_key("Q1", "Parus majr") is None


async def test_synonym_match_uses_the_accepted_key(tmp_path: Path) -> None:
    client, _ = _client(
        tmp_path,
        {
            "species/match": {
                "usageKey": 5,
                "acceptedUsageKey": 6,
                "synonym": True,
                "matchType": "EXACT",
                "rank": "SPECIES",
            }
        },
    )
    assert await client.taxon_key("Q1", "Delichon urbica") == 6


async def test_counts_filter_on_artportalen_cc0_and_years_and_are_cached(tmp_path: Path) -> None:
    client, http = _client(tmp_path, {"occurrence/search": COUNTS})
    first = await client.counts("Q25485", 9705453)
    second = await client.counts("Q25485", 9705453)
    assert first == second
    assert len(http.urls) == 1
    url = http.urls[0]
    for part in (
        "country=SE",
        "year=2016,2025",
        "license=CC0_1_0",
        "taxonKey=9705453",
        f"datasetKey={ARTPORTALEN_DATASET}",
    ):
        assert part in url


def test_the_dataset_is_artportalen() -> None:
    """Checked against the GBIF API 2026-10-07: dataset 38b4c89f... is "Artportalen"
    (CC0). Without it the counts included the Bird Ringing Centre's captures (6.4 % of all
    Swedish bird records, 49.5 % of Kungsfågel's), which pile up at ringing stations in
    autumn, and the pages say "Artportalen"."""
    assert ARTPORTALEN_DATASET == "38b4c89f-584c-41bb-bd8f-cd1def33e92f"


async def test_counts_cached_before_the_dataset_filter_are_not_reused(tmp_path: Path) -> None:
    old = {**COUNTS, "count": 1}
    Cache(tmp_path).put("Q25485", "gbif-counts-2016-2025.json", json.dumps(old))
    client, http = _client(tmp_path, {"occurrence/search": COUNTS})
    counts = await client.counts("Q25485", 9705453)
    assert counts.total == COUNTS["count"]
    assert len(http.urls) == 1


async def test_all_birds_uses_the_aves_key_and_the_same_dataset(tmp_path: Path) -> None:
    """The denominator has the same filters as the species, so the shares stay consistent."""
    client, http = _client(tmp_path, {"occurrence/search": COUNTS})
    await client.all_birds()
    assert f"taxonKey={AVES_TAXON_KEY}" in http.urls[0]
    assert f"datasetKey={ARTPORTALEN_DATASET}" in http.urls[0]
    assert (tmp_path / "_aves").is_dir()


def test_red_list_code() -> None:
    accepted = {
        "nubKey": 2489214,
        "canonicalName": "Delichon urbicum",
        "rank": "SPECIES",
        "taxonomicStatus": "ACCEPTED",
        "threatStatuses": ["VULNERABLE"],
    }
    synonym = {**accepted, "canonicalName": "Delichon urbica", "taxonomicStatus": "SYNONYM"}
    assert red_list_code([synonym, accepted], "Delichon urbicum", 2489214) == "VU"
    assert red_list_code([], "Parus major", 9705453) == "not_listed"
    least = {**accepted, "threatStatuses": ["LEAST_CONCERN"]}
    assert red_list_code([least], "Delichon urbicum", 2489214) == "not_listed"
    odd = {**accepted, "threatStatuses": ["SOMETHING_NEW"]}
    assert red_list_code([odd], "Delichon urbicum", 2489214) is None
