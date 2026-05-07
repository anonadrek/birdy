"""Build iNat-taxon-ID → Q-ID mapping via Wikidata SPARQL property P3151."""

from __future__ import annotations

import json
import logging
from collections.abc import Awaitable, Callable, Iterable
from dataclasses import dataclass
from datetime import datetime

WIKIDATA_SPARQL = "https://query.wikidata.org/sparql"
USER_AGENT = "birdy-fetcher/0.1.0 (https://github.com/anonadrek/birdy)"
SPARQL_BATCH_SIZE = 200

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class MappingResult:
    mappings: dict[str, str]
    requested_qids: int
    mapped_qids: int

    @property
    def coverage_pct(self) -> float:
        if self.requested_qids == 0:
            return 0.0
        return round(100.0 * self.mapped_qids / self.requested_qids, 1)


SparqlRunner = Callable[[str], Awaitable[str]]


def parse_sparql_response(raw: str) -> dict[str, str]:
    data = json.loads(raw)
    out: dict[str, str] = {}
    for binding in data["results"]["bindings"]:
        qid_uri = binding["item"]["value"]
        inat_id = binding["inatId"]["value"]
        qid = qid_uri.rsplit("/", 1)[-1]
        if inat_id in out:
            logger.warning(
                "Duplicate iNat-ID %s: keeping %s, dropping %s",
                inat_id,
                out[inat_id],
                qid,
            )
            continue
        out[inat_id] = qid
    return out


def chunked(items: list[str], size: int) -> Iterable[list[str]]:
    for i in range(0, len(items), size):
        yield items[i : i + size]


def build_query(qids: Iterable[str]) -> str:
    values = " ".join(f"wd:{q}" for q in qids)
    return f"""
    SELECT ?item ?inatId WHERE {{
      VALUES ?item {{ {values} }}
      ?item wdt:P3151 ?inatId .
    }}
    """


def render_mapping_json(
    result: MappingResult,
    *,
    model_version: str,
    generated_at: datetime,
) -> str:
    sorted_mappings = dict(sorted(result.mappings.items(), key=lambda kv: int(kv[0])))
    payload = {
        "_meta": {
            "generated_for_model_version": model_version,
            "generated_at": generated_at.isoformat().replace("+00:00", "Z"),
            "coverage_pct": result.coverage_pct,
            "mapped_qids": result.mapped_qids,
            "total_qids": result.requested_qids,
        },
        "mappings": sorted_mappings,
    }
    return json.dumps(payload, indent=2, ensure_ascii=False) + "\n"


async def _default_run_sparql(query: str) -> str:
    import aiohttp

    async with (
        aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session,
        session.get(
            WIKIDATA_SPARQL,
            params={"query": query, "format": "json"},
            timeout=aiohttp.ClientTimeout(total=120),
        ) as response,
    ):
        response.raise_for_status()
        return await response.text()


async def run_build_mapping(
    qids: list[str],
    *,
    run_sparql: SparqlRunner | None = None,
) -> MappingResult:
    runner = run_sparql or _default_run_sparql
    merged: dict[str, str] = {}
    for batch in chunked(qids, SPARQL_BATCH_SIZE):
        query = build_query(batch)
        raw = await runner(query)
        for inat_id, qid in parse_sparql_response(raw).items():
            if inat_id not in merged:
                merged[inat_id] = qid
    return MappingResult(mappings=merged, requested_qids=len(qids), mapped_qids=len(merged))
