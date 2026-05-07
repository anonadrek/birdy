"""Build AIY V1 class_index → Q-ID mapping via Wikidata SPARQL property P225 (taxon name)."""

from __future__ import annotations

import csv
import json
import logging
from collections.abc import Iterable
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from .inat_mapping import (
    SPARQL_BATCH_SIZE,
    SparqlRunner,
    _default_run_sparql,
    chunked,
)

BACKGROUND_CLASS_INDEX = 964

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class NameMappingResult:
    mappings: dict[int, str]
    requested_classes: int
    mapped_classes: int

    @property
    def coverage_pct(self) -> float:
        if self.requested_classes == 0:
            return 0.0
        return round(100.0 * self.mapped_classes / self.requested_classes, 1)


def parse_labelmap_csv(path: Path) -> list[tuple[int, str]]:
    """Returnera (class_index, scientific_name)-par. Skippar `background` (index 964)."""
    out: list[tuple[int, str]] = []
    with path.open(encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            class_index = int(row["id"])
            name = row["name"].strip()
            if class_index == BACKGROUND_CLASS_INDEX or name == "background":
                continue
            out.append((class_index, name))
    return out


def build_query(names: Iterable[str]) -> str:
    def escape(name: str) -> str:
        return name.replace('\\', '\\\\').replace('"', '\\"')

    values = " ".join(f'"{escape(n)}"' for n in names)
    return f"""
    SELECT ?item ?name WHERE {{
      VALUES ?name {{ {values} }}
      ?item wdt:P225 ?name .
    }}
    """


def parse_sparql_response_for_names(raw: str) -> dict[str, str]:
    data = json.loads(raw)
    out: dict[str, str] = {}
    for binding in data["results"]["bindings"]:
        qid_uri = binding["item"]["value"]
        name = binding["name"]["value"]
        entity = qid_uri.rsplit("/", 1)[-1]
        # P225 can resolve to Lexeme entities (L-prefix) for some taxa
        # (e.g. "Gallus gallus domesticus" → L1370926-S1 alongside Q780).
        # Validate Q-prefix to keep aiy_to_qid.json clean for downstream consumers.
        if not entity.startswith("Q"):
            logger.info("Skipping non-Q entity for %r: %s", name, entity)
            continue
        if name in out:
            logger.warning(
                "Duplicate taxon name %r: keeping %s, dropping %s",
                name, out[name], entity,
            )
            continue
        out[name] = entity
    return out


def render_mapping_json_by_class_index(
    result: NameMappingResult,
    *,
    model_version: str,
    generated_at: datetime,
) -> str:
    sorted_mappings = {
        str(k): v for k, v in sorted(result.mappings.items(), key=lambda kv: kv[0])
    }
    payload = {
        "_meta": {
            "generated_for_model_version": model_version,
            "generated_at": generated_at.isoformat().replace("+00:00", "Z"),
            "coverage_pct": result.coverage_pct,
            "mapped_classes": result.mapped_classes,
            "total_classes": result.requested_classes,
        },
        "mappings": sorted_mappings,
    }
    return json.dumps(payload, indent=2, ensure_ascii=False) + "\n"


async def run_build_name_mapping(
    pairs: list[tuple[int, str]],
    *,
    run_sparql: SparqlRunner | None = None,
) -> NameMappingResult:
    runner = run_sparql or _default_run_sparql
    name_to_index: dict[str, int] = {name: idx for idx, name in pairs}
    merged_by_index: dict[int, str] = {}
    names = [name for _, name in pairs]
    for batch in chunked(names, SPARQL_BATCH_SIZE):
        query = build_query(batch)
        raw = await runner(query)
        for name, qid in parse_sparql_response_for_names(raw).items():
            class_index = name_to_index.get(name)
            if class_index is None:
                continue
            if class_index in merged_by_index:
                logger.warning(
                    "Cross-batch duplicate for %r (class_index=%d): keeping %s, dropping %s",
                    name, class_index, merged_by_index[class_index], qid,
                )
                continue
            merged_by_index[class_index] = qid
    return NameMappingResult(
        mappings=merged_by_index,
        requested_classes=len(pairs),
        mapped_classes=len(merged_by_index),
    )
