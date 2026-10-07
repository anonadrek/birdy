"""The fact sheet (spec 2026-09-25 §9.3): the model's answer, the code checks of every
quote, the status in Sweden and the data facts that code adds. Before any text is written
from it, the automatic verification (Revision 2026-10-05, verify.py: V1 to V4) checks the
sheet, and Albin only decides on what it flags, in the exceptions sheet (review_sheet.py)."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Literal

from pydantic import BaseModel

from .checks import quote_in_sources
from .datamod import status_contradiction
from .record import Record
from .wiki_full import WikiArticle

Topic = Literal[
    "appearance",
    "sex_age",
    "size",
    "voice",
    "habitat",
    "sweden",
    "breeding",
    "food",
    "behaviour",
    "lookalike",
]
ArticleLang = Literal["sv", "en", "de"]
SwedenStatus = Literal[
    "resident", "breeding_migrant", "passage", "winter_visitor", "rare_visitor", "absent"
]


class FactSource(BaseModel):
    article: ArticleLang
    quote: str


class ModelFact(BaseModel):
    topic: Topic
    sv: str
    sources: list[FactSource]
    other_scientific: str | None


class ModelStatus(BaseModel):
    value: SwedenStatus
    sources: list[FactSource]


class FactSheetOutput(BaseModel):
    facts: list[ModelFact]
    sweden_status: ModelStatus | None


REQUIRED_TOPICS = ("appearance", "voice", "habitat")
MIN_FACTS = 10
MAX_FACTS = 30
MAX_FACT_WORDS = 30
TOPIC_SV = {
    "appearance": "utseende",
    "sex_age": "hane, hona och ungfågel",
    "size": "storlek",
    "voice": "läte",
    "habitat": "miljö",
    "sweden": "i Sverige",
    "breeding": "häckning",
    "food": "föda",
    "behaviour": "beteende",
    "lookalike": "förväxling",
    "status": "status i Sverige",
    "data": "data",
}
STATUS_SV = {
    "resident": "Stannfågel",
    "breeding_migrant": "Flyttfågel, häckar här",
    "passage": "Ses under flyttningen",
    "winter_visitor": "Vintergäst",
    "rare_visitor": "Sällsynt gäst",
    "absent": "Förekommer inte",
}
STATUS_BY_SV = {label: value for value, label in STATUS_SV.items()}
REDLIST_SV = {
    "RE": "Nationellt utdöd",
    "CR": "Akut hotad",
    "EN": "Starkt hotad",
    "VU": "Sårbar",
    "NT": "Nära hotad",
    "DD": "Kunskapsbrist",
}


@dataclass
class FactCheck:
    facts: list[dict[str, Any]] = field(default_factory=list)
    status: dict[str, Any] | None = None
    notes: list[str] = field(default_factory=list)
    fatal: list[str] = field(default_factory=list)
    retry: list[str] = field(default_factory=list)


def _valid_sources(
    sources: list[FactSource], articles: dict[str, WikiArticle]
) -> list[dict[str, str]]:
    return [
        {"article": s.article, "quote": s.quote}
        for s in sources
        if s.article in articles and quote_in_sources(s.quote, [articles[s.article].text])
    ]


def _entry(
    number: int, fact: ModelFact, articles: dict[str, WikiArticle], index: dict[str, str]
) -> tuple[dict[str, Any] | None, str | None]:
    sources = _valid_sources(fact.sources, articles)
    if not sources:
        return None, f"faktum {number} ströks, citatet finns inte i artikeln: {fact.sv}"
    if len(fact.sv.split()) > MAX_FACT_WORDS:
        return None, f"faktum {number} ströks, längre än {MAX_FACT_WORDS} ord: {fact.sv}"
    entry: dict[str, Any] = {"topic": fact.topic, "sv": fact.sv.strip(), "sources": sources}
    if fact.topic == "lookalike":
        if not fact.other_scientific:
            return None, f"faktum {number} ströks, förväxlingsarten saknar namn: {fact.sv}"
        other = {"scientific": fact.other_scientific.strip()}
        qid = index.get(other["scientific"].lower())
        if qid:
            other["qid"] = qid
        entry["other"] = other
    return entry, None


def cap_by_topic(
    entries: list[tuple[int, dict[str, Any]]], limit: int
) -> tuple[list[tuple[int, dict[str, Any]]], list[tuple[int, dict[str, Any]]]]:
    """(kept, dropped), both in the model's order. Over the limit the topics take turns: the
    first fact of every topic, then the second of every topic and so on, the required topics
    first in each turn and the others in the order the model first used them. Before the
    R3 trial (2026-10-07) the first `limit` facts were kept, so a model that wrote 43 facts
    lost every food, behaviour and look-alike fact (they come last). Within a topic the
    model's earlier facts win."""
    if len(entries) <= limit:
        return entries, []
    by_topic: dict[str, list[int]] = {}
    for index, (_, entry) in enumerate(entries):
        by_topic.setdefault(str(entry["topic"]), []).append(index)
    order = [t for t in REQUIRED_TOPICS if t in by_topic]
    order += [t for t in by_topic if t not in REQUIRED_TOPICS]
    chosen: set[int] = set()
    turn = 0
    while len(chosen) < limit:
        for topic in order:
            if turn < len(by_topic[topic]) and len(chosen) < limit:
                chosen.add(by_topic[topic][turn])
        turn += 1
    kept = [e for i, e in enumerate(entries) if i in chosen]
    dropped = [e for i, e in enumerate(entries) if i not in chosen]
    return kept, dropped


def _cap_notes(dropped: list[tuple[int, dict[str, Any]]]) -> list[str]:
    """One summary line and one line per dropped fact, for the step report."""
    if not dropped:
        return []
    counts: dict[str, int] = {}
    for _, entry in dropped:
        label = TOPIC_SV[entry["topic"]]
        counts[label] = counts.get(label, 0) + 1
    summary = ", ".join(f"{label} {n}" for label, n in counts.items())
    notes = [f"{len(dropped)} fakta över gränsen {MAX_FACTS} ströks ({summary})"]
    notes += [
        f"faktum {number} ströks, över gränsen {MAX_FACTS} ({TOPIC_SV[entry['topic']]}): "
        f"{entry['sv']}"
        for number, entry in dropped
    ]
    return notes


def check_fact_sheet(
    out: FactSheetOutput, articles: dict[str, WikiArticle], scientific_index: dict[str, str]
) -> FactCheck:
    check = FactCheck()
    valid: list[tuple[int, dict[str, Any]]] = []
    for number, fact in enumerate(out.facts, start=1):
        entry, note = _entry(number, fact, articles, scientific_index)
        if note is not None:
            check.notes.append(note)
        if entry is not None:
            valid.append((number, entry))
    capped, dropped = cap_by_topic(valid, MAX_FACTS)
    check.notes += _cap_notes(dropped)
    kept = [entry for _, entry in capped]
    check.facts = [{"id": f"f{i:02d}", **entry} for i, entry in enumerate(kept, start=1)]

    if out.sweden_status is not None:
        sources = _valid_sources(out.sweden_status.sources, articles)
        if sources:
            value = out.sweden_status.value
            check.status = {
                "id": "s01",
                "topic": "status",
                "value": value,
                "sv": STATUS_SV[value],
                "sources": sources,
            }
        else:
            check.notes.append("statusen ströks, citatet finns inte i artikeln")

    missing = [
        TOPIC_SV[t] for t in REQUIRED_TOPICS if not any(f["topic"] == t for f in check.facts)
    ]
    if missing:
        check.fatal.append(f"det saknas fakta om {', '.join(missing)}")
    check.retry = list(check.fatal)
    if len(check.facts) < MIN_FACTS:
        check.retry.append(
            f"bara {len(check.facts)} fakta klarade kontrollen, minst {MIN_FACTS} behövs"
        )
    return check


def data_facts(record: Record) -> list[dict[str, Any]]:
    """Facts that code writes from the report data and the red list (never the model)."""
    out: list[dict[str, Any]] = []

    def add(source: str, text: str) -> None:
        out.append({"id": f"d{len(out) + 1:02d}", "topic": "data", "source": source, "sv": text})

    for sentence in (record.get("data") or {}).get("sentences", {}).get("sv", []):
        add("artportalen", sentence)
    red = record.get("swedishRedList")
    if red == "not_listed":
        add("rodlistan", "Inte rödlistad i Svenska rödlistan 2025.")
    elif red in REDLIST_SV:
        add("rodlistan", f"Svenska rödlistan 2025: {REDLIST_SV[red]} ({red}).")
    return out


def apply_facts(record: Record, check: FactCheck, *, generated: dict[str, Any]) -> None:
    """New facts make any earlier text stale: it is removed and the species is pending. Any
    earlier automatic verification is stale too (fix 2026-10-06, C1): new facts have never
    been through V1, so `verification`, `flags` and `generated.verify` are cleared here --
    otherwise a stale `verification` would let `web write` write from a sheet nothing has
    checked."""
    facts = list(check.facts)
    if check.status is not None:
        facts.append(check.status)
    facts.extend(data_facts(record))
    record["facts"] = facts
    data = record.get("data")
    if data is not None:
        reason = None
        if check.status is not None:
            reason = status_contradiction(
                check.status["value"], data.get("months"), int(data.get("totalReports", 0))
            )
        data["statusSignal"] = {"contradicts": reason}
    generated_dict = record.setdefault("generated", {})
    generated_dict["facts"] = generated
    generated_dict.pop("verify", None)
    record.pop("verification", None)
    record.pop("flags", None)
    record["text"] = None
    record.pop("rejectedText", None)
    record["status"] = "failed" if check.fatal else "pending"
    record["errors"] = list(check.fatal)
    record.setdefault("review", {}).pop("statusConfirmed", None)
