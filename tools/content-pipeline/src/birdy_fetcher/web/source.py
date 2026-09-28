"""Reads the approved species (review_status: approved) from shared/content/species."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


class NotApprovedError(ValueError):
    pass


@dataclass(frozen=True)
class SourceImage:
    role: str
    path: str
    license: str
    author: str | None
    source_url: str


@dataclass(frozen=True)
class SpeciesSource:
    qid: str
    scientific_name: str
    name_sv: str
    name_en: str
    family: str
    family_sv: str
    ioc_order: str
    iucn: str
    marginalia_sv: str | None
    marginalia_en: str | None
    images: tuple[SourceImage, ...]


def _parse(data: dict[str, Any]) -> SpeciesSource:
    taxonomy = data["taxonomy"]
    marginalia = data.get("marginalia") or {}
    return SpeciesSource(
        qid=data["id"],
        scientific_name=data["scientific_name"],
        name_sv=data["names"]["sv"],
        name_en=data["names"]["en"],
        family=taxonomy["family"],
        family_sv=taxonomy.get("family_sv") or taxonomy["family"],
        ioc_order=taxonomy["ioc_order"],
        iucn=data["iucn_status"],
        marginalia_sv=marginalia.get("sv"),
        marginalia_en=marginalia.get("en"),
        images=tuple(
            SourceImage(
                role=ref["role"],
                path=ref["path"],
                license=ref["license"],
                author=ref.get("author"),
                source_url=ref.get("source_url", ""),
            )
            for ref in data.get("image_refs") or []
        ),
    )


def load_approved(species_root: Path, qids: Sequence[str] = ()) -> list[SpeciesSource]:
    """All approved species, or only `qids`. A requested species that isn't approved is an error."""
    wanted = set(qids)
    found: list[SpeciesSource] = []
    seen: set[str] = set()
    for path in sorted(species_root.rglob("*.yaml")):
        data: dict[str, Any] = yaml.safe_load(path.read_text(encoding="utf-8"))
        qid = data["id"]
        if wanted and qid not in wanted:
            continue
        seen.add(qid)
        if data.get("review_status") != "approved":
            if wanted:
                raise NotApprovedError(f"{qid} är inte granskad (review_status: approved krävs)")
            continue
        found.append(_parse(data))
    missing = wanted - seen
    if missing:
        raise KeyError(f"Saknas i artfilerna: {sorted(missing)}")
    return found
