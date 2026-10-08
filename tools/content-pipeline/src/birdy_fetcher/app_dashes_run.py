"""Release 1.3.1 part 7: the model step of `birdy-fetcher app-dashes`. One call per species
rewrites its sentences that still have a dash (Opus 5.5 by default); code checks come first
(app_dashes.check_rewrite), then one call to a different model checks that each rewrite says
the same thing. A sentence that fails any check keeps its dash and is listed in the report
for a hand fix. The rule fixes of a species are written even when some of its sentences fail."""

from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

from pydantic import BaseModel

from .app_dashes import (
    DashSentence,
    SpeciesScan,
    apply_rewrites,
    check_rewrite,
    dump_species,
    scan_species,
    species_files,
)
from .cost import CostTracker, MaxCostExceeded
from .web.llm import MODELS, AnthropicJsonClient, JsonModelClient, record_cost


class Rewrite(BaseModel):
    id: str
    text: str


class RewriteReply(BaseModel):
    rewrites: list[Rewrite]


class Verdict(BaseModel):
    id: str
    same_meaning: bool
    reason: str


class CheckReply(BaseModel):
    verdicts: list[Verdict]


WRITER_SYSTEM = """
You remove dashes from short bird species texts in the Birdy app, in Swedish and English.
Rewrite each sentence you get so it has no em dash (U+2014) and no en dash (U+2013).
- Keep the sentence's language: Swedish stays Swedish, English stays English.
- Keep every fact, number, unit, name and place, and every *emphasis* marker, exactly.
- Change as little as possible: use a comma, a colon, parentheses or a full stop,
  or make two sentences.
- A dash that joins two names becomes a plain hyphen (Tigris-Euphrates).
- Write plain, natural prose. Add nothing and leave nothing out.
The paragraph is context only: rewrite just the sentence. Answer with one rewrite per id.
""".strip()

CHECKER_SYSTEM = """
You check edits to bird species texts. For each id you get an original sentence and a
rewrite whose only job was to remove dashes. Answer same_meaning=true only when the
rewrite says exactly what the original says: the same facts, numbers, names and certainty,
nothing added and nothing left out. Different punctuation and small wording changes are
fine. Give a short reason either way.
""".strip()

CHECKER_EFFORT = "medium"


@dataclass
class DashOptions:
    species_root: Path
    reports: Path
    qids: tuple[str, ...] = ()
    model_key: str = "opus55"
    effort: str = "medium"
    checker_key: str = "sonnet"
    max_cost: float = 12.0
    workers: int = 4
    dry_run: bool = False


@dataclass
class SpeciesOutcome:
    qid: str
    rule_fixes: int
    rewritten: list[tuple[DashSentence, str]] = field(default_factory=list)
    failed: list[tuple[DashSentence, str]] = field(default_factory=list)  # sentence, reason
    written: bool = False


@dataclass
class DashRunResult:
    outcomes: list[SpeciesOutcome]
    cost_usd: float
    stopped_by_cost: bool
    report: Path


async def run_app_dashes(opts: DashOptions, client: JsonModelClient | None = None) -> DashRunResult:
    scans = [
        s
        for s in (scan_species(p) for p in species_files(opts.species_root, opts.qids))
        if s.fixed or s.sentences
    ]
    cost = CostTracker(max_usd=opts.max_cost)
    own_client: AnthropicJsonClient | None = None
    if client is None and not opts.dry_run and any(s.sentences for s in scans):
        own_client = AnthropicJsonClient()
        client = own_client
    semaphore = asyncio.Semaphore(opts.workers)
    outcomes: list[SpeciesOutcome] = []
    stopped = False

    async def one(scan: SpeciesScan) -> None:
        nonlocal stopped
        async with semaphore:
            if stopped:
                return
            try:
                outcomes.append(await _species(scan, client, opts, cost))
            except MaxCostExceeded:
                stopped = True

    try:
        await asyncio.gather(*(one(s) for s in scans))
    finally:
        if own_client is not None:
            await own_client.aclose()
    outcomes.sort(key=lambda o: o.qid)
    report = _write_report(opts, outcomes, cost.total_usd, stopped)
    return DashRunResult(outcomes, cost.total_usd, stopped, report)


async def _species(
    scan: SpeciesScan, client: JsonModelClient | None, opts: DashOptions, cost: CostTracker
) -> SpeciesOutcome:
    outcome = SpeciesOutcome(scan.qid, scan.rule_fixes)
    accepted: dict[str, str] = {}
    if opts.dry_run:
        outcome.failed += [(s, "torrkörning") for s in scan.sentences]
        return outcome
    if scan.sentences:
        assert client is not None
        accepted = await _rewrite(scan, client, opts, cost, outcome)
    if scan.fixed or accepted:
        dump_species(apply_rewrites(scan, accepted), scan.path)
        outcome.written = True
    return outcome


async def _rewrite(
    scan: SpeciesScan,
    client: JsonModelClient,
    opts: DashOptions,
    cost: CostTracker,
    outcome: SpeciesOutcome,
) -> dict[str, str]:
    asked = [
        {"id": s.key, "lang": s.lang, "sentence": s.text, "paragraph": s.paragraph}
        for s in scan.sentences
    ]
    written = await client.complete(
        model=MODELS[opts.model_key],
        system=WRITER_SYSTEM,
        messages=[{"role": "user", "content": json.dumps(asked, ensure_ascii=False)}],
        effort=opts.effort,
        schema=RewriteReply,
    )
    record_cost(cost, opts.model_key, written)
    proposals = {r.id: r.text.strip() for r in (written.parsed.rewrites if written.parsed else [])}
    candidates: dict[str, str] = {}
    for s in scan.sentences:
        text = proposals.get(s.key)
        if text is None:
            outcome.failed.append((s, f"modellen svarade inte ({written.stop_reason})"))
        elif problems := check_rewrite(s.text, text):
            outcome.failed.append((s, ", ".join(problems)))
        else:
            candidates[s.key] = text
    if not candidates:
        return {}
    pairs = [
        {"id": key, "original": s.text, "rewrite": candidates[key]}
        for s in scan.sentences
        if (key := s.key) in candidates
    ]
    checked = await client.complete(
        model=MODELS[opts.checker_key],
        system=CHECKER_SYSTEM,
        messages=[{"role": "user", "content": json.dumps(pairs, ensure_ascii=False)}],
        effort=CHECKER_EFFORT,
        schema=CheckReply,
    )
    record_cost(cost, opts.checker_key, checked)
    verdicts = {v.id: v for v in (checked.parsed.verdicts if checked.parsed else [])}
    accepted: dict[str, str] = {}
    for s in scan.sentences:
        if s.key not in candidates:
            continue
        verdict = verdicts.get(s.key)
        if verdict is None:
            outcome.failed.append((s, f"kontrollen svarade inte ({checked.stop_reason})"))
        elif not verdict.same_meaning:
            outcome.failed.append((s, f"kontrollen: {verdict.reason}"))
        else:
            accepted[s.key] = candidates[s.key]
            outcome.rewritten.append((s, candidates[s.key]))
    return accepted


def _write_report(
    opts: DashOptions, outcomes: list[SpeciesOutcome], cost_usd: float, stopped: bool
) -> Path:
    opts.reports.mkdir(parents=True, exist_ok=True)
    path = opts.reports / f"app-dashes-{datetime.now():%Y-%m-%d-%H%M%S}.md"
    rewritten = sum(len(o.rewritten) for o in outcomes)
    failed = sum(len(o.failed) for o in outcomes)
    notes = (" Stoppad vid kostnadstaket." if stopped else "") + (
        " Torrkörning, inget skrivet." if opts.dry_run else ""
    )
    lines = [
        "# Tankstreck i arttexterna som appen visar (release 1.3.1 del 7)",
        "",
        f"Arter: {len(outcomes)}. Regelbyten: {sum(o.rule_fixes for o in outcomes)}. "
        f"Omskrivna meningar: {rewritten}. Kvar att rätta för hand: {failed}.",
        f"Kostnad: ${cost_usd:.2f}.{notes}",
        "",
    ]
    for o in outcomes:
        if not o.rewritten and not o.failed:
            continue
        lines.append(f"## {o.qid}")
        for s, new in o.rewritten:
            lines += [f"- {s.field}.{s.lang}: {s.text}", f"  blir: {new}"]
        for s, why in o.failed:
            lines.append(f"- KVAR {s.field}.{s.lang}: {s.text} ({why})")
        lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")
    return path
