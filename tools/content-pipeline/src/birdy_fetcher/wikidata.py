"""Wikidata structured fetch — taxonomy, IUCN, P18 image filename."""

from __future__ import annotations

import json
import re
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from urllib.parse import unquote

import aiohttp

from .cache import Cache

WIKIDATA_SPARQL = "https://query.wikidata.org/sparql"
USER_AGENT = "birdy-fetcher/0.1.0 (https://github.com/anonadrek/birdy)"

# The IUCN category items P141 points to, by Q-ID (stable). The English labels are not: in May
# 2026 Q96377276 was labelled "endangered status" and Q237350 "extinct species" (both renamed
# 2026-08-07), so the label table below missed them and 12 endangered and 3 extinct species were
# written out as NE (release 1.3.0 Task 7g). The labels are only a fallback for an item not here.
IUCN_QID_TO_CODE = {
    "Q211005": "LC",
    "Q719675": "NT",
    "Q278113": "VU",
    "Q96377276": "EN",
    "Q11394": "EN",  # "endangered species", the older item some taxa still point to
    "Q219127": "CR",
    "Q3245245": "DD",
    "Q3350324": "NE",
    "Q237350": "EX",
    "Q239509": "EW",
}

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

_LABEL_SUFFIXES = (" species", " status")


def iucn_code(status_uri: str, label: str) -> str:
    """The IUCN code for a P141 value: by its item, else by its label ("... species/status"
    suffixes ignored), else NE."""
    qid = status_uri.rsplit("/", 1)[-1]
    if qid in IUCN_QID_TO_CODE:
        return IUCN_QID_TO_CODE[qid]
    key = label.strip().lower()
    for suffix in _LABEL_SUFFIXES:
        key = key.removesuffix(suffix)
    return IUCN_LABEL_TO_CODE.get(key, "NE")


@dataclass(frozen=True)
class WikidataStructured:
    q_id: str
    scientific_name: str
    family: str
    family_sv: str | None
    genus: str
    ioc_order: str
    iucn_status: str
    image_filename: str | None
    common_sv: str | None


SparqlRunner = Callable[[str], Awaitable[str]]


_TRAILING_PARENTHESIS = re.compile(r"\s*\([^)]*\)$")


def _swedish_name(
    *,
    scientific_name: str,
    label: str | None,
    taxon_title: str | None,
    related_title: str | None,
    related_name: str | None,
) -> str | None:
    """The Swedish name for a taxon, or None.

    In order: the item's Swedish label, the title of its Swedish Wikipedia article, the article
    title of a previous combination (P1420 taxon synonym / P1403 original combination). A value
    that is only a scientific name does not count: Wikidata often puts the taxon name in the
    Swedish label (Fringilla polatzeki), and after a genus move the new item has neither label nor
    article while the old Charadrius/Oceanodroma item has both (release 1.3.0 Task 7g).
    """

    def usable(value: str | None, *scientific: str | None) -> str | None:
        if not value:
            return None
        name = _TRAILING_PARENTHESIS.sub("", value).strip()
        if not name or any(s and name.casefold() == s.casefold() for s in scientific):
            return None
        return _capitalize_first(name)

    return (
        usable(label, scientific_name)
        or usable(taxon_title, scientific_name)
        or usable(related_title, scientific_name, related_name)
    )


def _capitalize_first(s: str | None) -> str | None:
    # Wikidata rdfs:label@sv arrives inconsistently: "Talgoxe" capitalized but
    # "lammgam"/"vitögd vråk" lowercase. Force capital-first while preserving
    # any internal casing (proper nouns, abbreviations like "USA-stork").
    if not s:
        return s
    return s[0].upper() + s[1:]


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
        SELECT ?taxonName ?taxonLabelSv ?family ?familyLabel ?familyLabelSv ?genus ?genusLabel
               ?ordo ?ordoLabel ?iucnStatus ?iucnStatusLabel ?image
               ?taxonSvTitle ?relatedSvTitle ?relatedName WHERE {{
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
          OPTIONAL {{ ?taxon rdfs:label ?taxonLabelSv . FILTER(LANG(?taxonLabelSv) = "sv") }}
          OPTIONAL {{
            ?taxonArticle schema:about ?taxon ;
                          schema:isPartOf <https://sv.wikipedia.org/> ;
                          schema:name ?taxonSvTitle .
          }}
          OPTIONAL {{
            ?taxon wdt:P1420|wdt:P1403 ?related .
            ?relatedArticle schema:about ?related ;
                            schema:isPartOf <https://sv.wikipedia.org/> ;
                            schema:name ?relatedSvTitle .
            OPTIONAL {{ ?related wdt:P225 ?relatedName . }}
          }}
          OPTIONAL {{ ?family rdfs:label ?familyLabelSv . FILTER(LANG(?familyLabelSv) = "sv") }}
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
        iucn_status = iucn_code(
            b.get("iucnStatus", {}).get("value", ""),
            b.get("iucnStatusLabel", {}).get("value", ""),
        )
        image_uri = b.get("image", {}).get("value", "")
        image_filename: str | None = None
        if image_uri:
            tail = image_uri.rsplit("/", 1)[-1]
            image_filename = unquote(tail)
        common_sv = _swedish_name(
            scientific_name=b["taxonName"]["value"],
            label=b.get("taxonLabelSv", {}).get("value"),
            taxon_title=b.get("taxonSvTitle", {}).get("value"),
            related_title=b.get("relatedSvTitle", {}).get("value"),
            related_name=b.get("relatedName", {}).get("value"),
        )
        family_sv = _capitalize_first(b.get("familyLabelSv", {}).get("value") or None)
        return WikidataStructured(
            q_id=q_id,
            scientific_name=b["taxonName"]["value"],
            family=b["familyLabel"]["value"],
            family_sv=family_sv,
            genus=b["genusLabel"]["value"],
            ioc_order=b["ordoLabel"]["value"],
            iucn_status=iucn_status,
            image_filename=image_filename,
            common_sv=common_sv,
        )
