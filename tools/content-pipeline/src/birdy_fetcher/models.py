"""Pydantic dataclasses for pipeline-internal types."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel


class IocEntry(BaseModel):
    """A row from the IOC master list (v14.1 xlsx)."""

    scientific_name: str
    family: str
    family_en: str | None = None
    ioc_order: str
    common_en: str


VpStatus = Literal["H", "h", "F", "R", "(H)"]


class Vp11Entry(BaseModel):
    """A species row from BirdLife Sveriges TK Västpalearktis-lista v11."""

    status: VpStatus
    ioc_order: str
    family: str
    scientific_name: str
    common_en: str
    notes: str = ""  # "Intr.", "E.", "†", "#1" etc


class SpeciesListEntry(BaseModel):
    """One mapped species in species_list.yaml."""

    wikidata_id: str | None = None  # Q-ID; null if mapping failed
    scientific_name: str
    family: str
    ioc_order: str
    common_en: str
    vp_status: VpStatus  # H, h, F, R, (H) — drives default abundance heuristic in Task 8


class MappingFailure(BaseModel):
    """Why a VP11 sci-name failed to map to a Wikidata Q-ID."""

    scientific_name: str
    family: str
    common_en: str
    reason: str
