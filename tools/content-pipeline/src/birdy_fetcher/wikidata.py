"""Wikidata structured fetch — taxonomy, IUCN, P18 image filename."""

from __future__ import annotations

import json
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from urllib.parse import unquote

import aiohttp

from .cache import Cache

WIKIDATA_SPARQL = "https://query.wikidata.org/sparql"
USER_AGENT = "birdy-fetcher/0.1.0 (https://github.com/anonadrek/birdy)"

IUCN_LABEL_TO_CODE = {
    "least concern": "LC",
    "near threatened": "NT",
    "vulnerable": "VU",
    "endangered": "EN",
    "critically endangered": "CR",
    "data deficient": "DD",
    "not evaluated": "NE",
    "extinct": "EX",
    "extinct in the wild": "EW",
}


@dataclass(frozen=True)
class WikidataStructured:
    q_id: str
    scientific_name: str
    family: str
    genus: str
    ioc_order: str
    iucn_status: str
    image_filename: str | None


SparqlRunner = Callable[[str], Awaitable[str]]


async def _default_run_sparql(query: str) -> str:
    async with (
        aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session,
        session.get(
            WIKIDATA_SPARQL,
            params={"query": query, "format": "json"},
            timeout=aiohttp.ClientTimeout(total=60),
        ) as response,
    ):
        response.raise_for_status()
        return await response.text()


class WikidataClient:
    def __init__(
        self,
        *,
        cache: Cache,
        run_sparql: SparqlRunner | None = None,
    ) -> None:
        self.cache = cache
        self._run_sparql = run_sparql or _default_run_sparql

    async def fetch_structured(
        self,
        q_id: str,
        *,
        force: bool = False,
    ) -> WikidataStructured:
        if not force and self.cache.has(q_id, "wikidata.json"):
            raw = self.cache.get(q_id, "wikidata.json")
            assert raw is not None
        else:
            query = self._build_query(q_id)
            raw = await self._run_sparql(query)
            self.cache.put(q_id, "wikidata.json", raw)
        return self._parse(q_id, raw)

    @staticmethod
    def _build_query(q_id: str) -> str:
        return f"""
        SELECT ?taxonName ?family ?familyLabel ?genus ?genusLabel ?ordo ?ordoLabel
               ?iucnStatus ?iucnStatusLabel ?image WHERE {{
          BIND(wd:{q_id} AS ?taxon)
          ?taxon wdt:P225 ?taxonName ;
                 wdt:P171* ?family .
          ?family wdt:P105 wd:Q35409 .
          ?taxon wdt:P171* ?genus .
          ?genus wdt:P105 wd:Q34740 .
          ?taxon wdt:P171* ?ordo .
          ?ordo wdt:P105 wd:Q36602 .
          OPTIONAL {{ ?taxon wdt:P141 ?iucnStatus . }}
          OPTIONAL {{ ?taxon wdt:P18 ?image . }}
          SERVICE wikibase:label {{ bd:serviceParam wikibase:language "en". }}
        }}
        LIMIT 1
        """

    @staticmethod
    def _parse(q_id: str, raw: str) -> WikidataStructured:
        data = json.loads(raw)
        bindings = data["results"]["bindings"]
        if not bindings:
            raise ValueError(f"No Wikidata structured data for {q_id}")
        b = bindings[0]
        iucn_label = b.get("iucnStatusLabel", {}).get("value", "").lower()
        iucn_code = IUCN_LABEL_TO_CODE.get(iucn_label, "NE")
        image_uri = b.get("image", {}).get("value", "")
        image_filename: str | None = None
        if image_uri:
            tail = image_uri.rsplit("/", 1)[-1]
            image_filename = unquote(tail)
        return WikidataStructured(
            q_id=q_id,
            scientific_name=b["taxonName"]["value"],
            family=b["familyLabel"]["value"],
            genus=b["genusLabel"]["value"],
            ioc_order=b["ordoLabel"]["value"],
            iucn_status=iucn_code,
            image_filename=image_filename,
        )
