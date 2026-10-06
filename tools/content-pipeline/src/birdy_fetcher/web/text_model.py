"""The page text (spec 2026-09-25 §9.5): every sentence carries the ids of the facts it is
built on. Also the conversion to the site's field names (appendix C) and removal of parts
that did not pass a check."""

from __future__ import annotations

import re
from collections.abc import Iterator
from typing import Any

from pydantic import BaseModel

from .datamod import record_status_contradiction
from .record import Record


class Sentence(BaseModel):
    text: str
    fact_ids: list[str]


class LookAlikeText(BaseModel):
    other: str
    sentences: list[Sentence]


class SizeText(BaseModel):
    value: str
    fact_ids: list[str]


class LangTextV2(BaseModel):
    lead: list[Sentence]
    field_marks: list[Sentence]
    voice: list[Sentence]
    where_when: list[Sentence]
    behaviour: list[Sentence]
    look_alikes: list[LookAlikeText]
    meta_description: str
    size: SizeText | None


class WebTextV2(BaseModel):
    sv: LangTextV2
    en: LangTextV2


SENTENCE_FIELDS = ("lead", "field_marks", "voice", "where_when", "behaviour")


def iter_sentences(text: LangTextV2) -> Iterator[tuple[str, Sentence]]:
    for name in SENTENCE_FIELDS:
        for i, sentence in enumerate(getattr(text, name)):
            yield f"{name}[{i}]", sentence
    for i, look_alike in enumerate(text.look_alikes):
        for j, sentence in enumerate(look_alike.sentences):
            yield f"look_alikes[{i}].sentences[{j}]", sentence


_PATH = re.compile(
    r"^(sv|en)\.(lead|field_marks|voice|where_when|behaviour|look_alikes|size)"
    r"(?:\[(\d+)\])?(?:\.sentences\[(\d+)\])?$"
)


def remove_paths(text: WebTextV2, paths: set[str]) -> WebTextV2:
    """A copy without the sentences, items, look-alikes or sizes named by `paths`. A
    look-alike that loses all its sentences is dropped too."""
    data = text.model_dump()
    drops: dict[tuple[str, str], set[int]] = {}
    nested: dict[tuple[str, int], set[int]] = {}
    for path in paths:
        match = _PATH.match(path)
        if match is None:
            continue
        lang, name, index, inner = match.groups()
        if name == "size":
            data[lang]["size"] = None
        elif name == "look_alikes" and inner is not None:
            nested.setdefault((lang, int(index)), set()).add(int(inner))
        elif index is not None:
            drops.setdefault((lang, name), set()).add(int(index))
    for (lang, i), inner_indexes in nested.items():
        sentences = data[lang]["look_alikes"][i]["sentences"]
        data[lang]["look_alikes"][i]["sentences"] = [
            s for k, s in enumerate(sentences) if k not in inner_indexes
        ]
    for (lang, name), indexes in drops.items():
        data[lang][name] = [x for k, x in enumerate(data[lang][name]) if k not in indexes]
    for lang in ("sv", "en"):
        data[lang]["look_alikes"] = [la for la in data[lang]["look_alikes"] if la["sentences"]]
    return WebTextV2.model_validate(data)


def status_for_site(record: Record) -> dict[str, Any] | None:
    """The reviewed status, unless the data contradicts it and Albin did not keep it. The
    contradiction is computed from the current data, not the stored `statusSignal` (I3,
    final review 2026-10-06)."""
    status = next((f for f in record.get("facts", []) if f.get("topic") == "status"), None)
    if status is None:
        return None
    contradicts = record_status_contradiction(record)
    if contradicts and not record.get("review", {}).get("statusConfirmed"):
        return None
    return {"value": status["value"], "factIds": [status["id"]]}


def _sentences(items: list[Sentence]) -> list[dict[str, Any]]:
    return [{"text": s.text, "factIds": list(s.fact_ids)} for s in items]


def to_site(text: LangTextV2, status: dict[str, Any] | None) -> dict[str, Any]:
    size = {"value": text.size.value, "factIds": list(text.size.fact_ids)} if text.size else None
    return {
        "lead": _sentences(text.lead),
        "fieldMarks": _sentences(text.field_marks),
        "voice": _sentences(text.voice),
        "whereWhen": _sentences(text.where_when),
        "behaviour": _sentences(text.behaviour),
        "lookAlikes": [
            {"other": la.other, "text": _sentences(la.sentences)} for la in text.look_alikes
        ],
        "metaDescription": text.meta_description,
        "facts": {"size": size, "swedenStatus": status},
    }
