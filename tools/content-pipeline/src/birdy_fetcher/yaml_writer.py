"""Write SpeciesYamlData to disk in the canonical schema; apply overrides."""

from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml


@dataclass
class ImageRef:
    role: str
    path: str
    width: int
    height: int
    license: str
    author: str
    source_url: str
    commons_filename: str


@dataclass
class SpeciesYamlData:
    wikidata_id: str
    scientific_name: str
    family: str
    family_sv: str
    genus: str
    ioc_order: str
    common_sv: str | None
    common_en: str
    abundance: str
    iucn_status: str
    regions: list[str]
    season: dict[str, str]
    description: dict[str, str]
    migration: dict[str, str]
    image_refs: list[ImageRef] = field(default_factory=list)
    review_status: str = "auto"
    review_notes: str = ""
    generated_at: str = ""
    sources: dict[str, Any] = field(default_factory=dict)


def _serialize(data: SpeciesYamlData) -> dict[str, Any]:
    return {
        "id": data.wikidata_id,
        "scientific_name": data.scientific_name,
        "taxonomy": {
            "family": data.family,
            "family_sv": data.family_sv,
            "genus": data.genus,
            "ioc_order": data.ioc_order,
        },
        "names": {
            "sv": data.common_sv,
            "en": data.common_en,
        },
        "abundance": data.abundance,
        "iucn_status": data.iucn_status,
        "season": data.season,
        "regions": data.regions,
        "description": data.description,
        "migration": data.migration,
        "image_refs": [
            {
                "role": ref.role,
                "path": ref.path,
                "width": ref.width,
                "height": ref.height,
                "license": ref.license,
                "author": ref.author,
                "source_url": ref.source_url,
                "commons_filename": ref.commons_filename,
            }
            for ref in data.image_refs
        ],
        "review_status": data.review_status,
        "review_notes": data.review_notes,
        "generated_at": data.generated_at,
        "sources": data.sources,
    }


def write_species_yaml(data: SpeciesYamlData, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(
        yaml.safe_dump(
            _serialize(data),
            sort_keys=False,
            allow_unicode=True,
            default_flow_style=False,
        ),
        encoding="utf-8",
    )


def merge_overrides(
    data: SpeciesYamlData,
    overrides: dict[str, Any],
) -> SpeciesYamlData:
    patch = overrides.get(data.wikidata_id)
    if not patch:
        return data
    merged = deepcopy(data)

    if "description" in patch:
        for lang, text in patch["description"].items():
            if isinstance(text, dict) and text.get("accept_missing"):
                merged.description[lang] = "[accept_missing]"
            else:
                merged.description[lang] = str(text)

    if "migration" in patch:
        for lang, text in patch["migration"].items():
            merged.migration[lang] = str(text)

    if "image_refs" in patch:
        merged.image_refs = [
            ImageRef(
                role=ref["role"],
                path=ref["path"],
                width=int(ref.get("width", 0)),
                height=int(ref.get("height", 0)),
                license=ref.get("license", ""),
                author=ref.get("author", ""),
                source_url=ref.get("source_url", ""),
                commons_filename=ref.get("commons_filename", ""),
            )
            for ref in patch["image_refs"]
        ]

    if "abundance" in patch:
        merged.abundance = str(patch["abundance"])

    return merged
