"""The model's structured answer for one species (both languages in one call)."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel

SwedenStatus = Literal[
    "resident", "breeding_migrant", "passage", "winter_visitor", "rare_visitor", "absent"
]


class SizeFact(BaseModel):
    value: str
    quote: str


class StatusFact(BaseModel):
    value: SwedenStatus
    quote: str


class Facts(BaseModel):
    size: SizeFact | None
    sweden_status: StatusFact | None


class LangText(BaseModel):
    lead: str
    field_marks: list[str]
    voice: str
    where_when: str
    meta_description: str
    facts: Facts


class WebTextOutput(BaseModel):
    sv: LangText
    en: LangText
