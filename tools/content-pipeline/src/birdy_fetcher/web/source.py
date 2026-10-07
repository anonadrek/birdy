"""Reads the approved species (review_status: approved) from shared/content/species."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml

from .families import family_sv


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
        # One Swedish name per family from BirdLife Sverige's list, not the species file's
        # own `family_sv` (re-review 2026-10-07, web/families.py).
        family_sv=family_sv(taxonomy["family"]),
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


@dataclass(frozen=True)
class NameIndex:
    """Lowercased scientific name to QID and to family, for all species, approved or not.
    Used to link a look-alike named in a fact to its species (web/scinames.py)."""

    qids: dict[str, str]
    families: dict[str, str]
    # Swedish and English names: a look-alike matched by a guessed genus must be named in
    # its fact (scinames.py).
    common: dict[str, tuple[str, ...]] = field(default_factory=dict)


def load_name_index(species_root: Path) -> NameIndex:
    qids: dict[str, str] = {}
    families: dict[str, str] = {}
    common: dict[str, tuple[str, ...]] = {}
    for path in sorted(species_root.rglob("*.yaml")):
        data: dict[str, Any] = yaml.safe_load(path.read_text(encoding="utf-8"))
        name = str(data["scientific_name"]).lower()
        qids[name] = data["id"]
        family = (data.get("taxonomy") or {}).get("family")
        if family:
            families[name] = str(family)
        names = data.get("names") or {}
        common[name] = tuple(str(names[k]) for k in ("sv", "en") if names.get(k))
    return NameIndex(qids=qids, families=families, common=common)
