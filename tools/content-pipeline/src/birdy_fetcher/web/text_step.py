"""Step 3 (spec 2026-09-25 §9.5 and §9.6): write the text from the reviewed fact sheet,
check it in code, check it with a second model, rewrite once, remove what still fails."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from anthropic.types import MessageParam

from ..claude_summarizer import _split_prompt
from ..cost import CostTracker, MaxCostExceeded
from .checker import PROMPT_VERSION as CHECK_PROMPT_VERSION
from .checker import CheckerFailed, SentenceChecker, check_items
from .checks import load_banned
from .facts import STATUS_SV, TOPIC_SV
from .groups import GroupTable
from .llm import MODELS, AnthropicJsonClient, JsonModelClient, ModelReply, record_cost
from .paths import WebPaths
from .record import Record, facts_hash, is_reviewed, load_all, record_path, save_record
from .report import StepOutcome, render_step_report, write_step_report
from .text_checks import TextContext, TextIssue, check_text, minimum_problems, settle
from .text_model import WebTextV2, remove_paths, status_for_site, to_site

PROMPT_VERSION = "web-v2"
RULE_ATTEMPTS = 2


def writer_facts(record: Record) -> list[dict[str, Any]]:
    """The facts the writer may use: everything except a status that is not shown."""
    show_status = status_for_site(record) is not None
    return [f for f in record.get("facts", []) if f["topic"] != "status" or show_status]


def render_facts(facts: list[dict[str, Any]]) -> str:
    lines = []
    for fact in facts:
        label = TOPIC_SV.get(fact["topic"], fact["topic"])
        if fact["topic"] == "lookalike":
            other = fact["other"]
            other_id = other.get("qid") or other["scientific"]
            label = f"förväxling, other={other_id} ({other['scientific']})"
        lines.append(f"{fact['id']} [{label}] {fact['sv']}")
    return "\n".join(lines)


def render_write_prompt(
    template: str,
    record: Record,
    facts: list[dict[str, Any]],
    group_sv: str,
    group_en: str,
    banned: list[str],
) -> tuple[str, str]:
    status = status_for_site(record)
    status_line = (
        f"{STATUS_SV[status['value']]} (fact s01)"
        if status
        else "not decided, do not state a status"
    )
    return _split_prompt(
        template,
        name_sv=record["names"]["sv"],
        name_en=record["names"]["en"],
        scientific_name=record["names"]["scientific"],
        family=record["family"]["latin"],
        family_sv=record["family"]["sv"],
        group_sv=group_sv,
        group_en=group_en,
        status_line=status_line,
        banned_phrases=", ".join(banned),
        facts=render_facts(facts),
    )


def rules_feedback(issues: list[TextIssue]) -> str:
    lines = "\n".join(f"- {i.path}: {i.message}" for i in issues)
    return (
        "Your answer broke these rules. Write the whole answer again with the same structure "
        "and fix only these points:\n" + lines + "\n"
        "Every sentence must list the ids of the facts it uses and say no more than they do."
    )


def support_feedback(unsupported: dict[str, str]) -> str:
    lines = "\n".join(f"- {path}: {problem}" for path, problem in unsupported.items())
    return (
        "A checker found sentences that say more than the facts they cite. Write the whole "
        "answer again with the same structure. Fix or drop these sentences and add nothing "
        "the facts do not say:\n" + lines
    )


@dataclass
class TextResult:
    text: WebTextV2 | None
    rejected: WebTextV2 | None
    notes: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)
    attempts: int = 0


@dataclass
class SpeciesTextWriter:
    client: JsonModelClient
    cost: CostTracker
    checker: SentenceChecker
    prompt_path: Path
    banned: list[str]
    model_key: str = "opus"
    effort: str = "high"

    async def _ask(self, system: str, messages: list[MessageParam]) -> ModelReply[WebTextV2]:
        reply = await self.client.complete(
            model=MODELS[self.model_key],
            system=system,
            messages=messages,
            effort=self.effort,
            schema=WebTextV2,
        )
        record_cost(self.cost, self.model_key, reply)
        return reply

    async def write(self, record: Record, group_sv: str, group_en: str) -> TextResult:
        facts = writer_facts(record)
        ctx = TextContext.from_facts(facts)
        about = (
            f"{record['names']['sv']} / {record['names']['en']} "
            f"({record['names']['scientific']}), familj {record['family']['sv']}"
        )
        template = self.prompt_path.read_text(encoding="utf-8")
        system, user = render_write_prompt(template, record, facts, group_sv, group_en, self.banned)
        base: list[MessageParam] = [{"role": "user", "content": user}]
        result = TextResult(None, None)
        messages = list(base)
        text: WebTextV2 | None = None
        last_stop: str | None = None
        for attempt in range(1, RULE_ATTEMPTS + 1):
            result.attempts = attempt
            reply = await self._ask(system, messages)
            last_stop = reply.stop_reason
            if reply.parsed is None:
                if reply.stop_reason in ("max_tokens", "refusal"):
                    break
                continue
            text = reply.parsed
            issues = check_text(text, ctx, self.banned)
            if not issues:
                break
            if attempt < RULE_ATTEMPTS:
                messages = [
                    *messages,
                    {"role": "assistant", "content": reply.raw_text},
                    {"role": "user", "content": rules_feedback(issues)},
                ]
        if text is None:
            result.errors = [f"modellen gav inget giltigt svar (stop_reason={last_stop})"]
            return result
        text, removed, hard = settle(text, ctx, self.banned)
        result.notes += removed
        if hard:
            result.rejected, result.errors = text, hard
            return result

        unsupported = await self.checker.check(check_items(text, ctx), about=about)
        if unsupported:
            result.attempts += 1
            retry: list[MessageParam] = [
                *base,
                {"role": "assistant", "content": text.model_dump_json()},
                {"role": "user", "content": support_feedback(unsupported)},
            ]
            reply = await self._ask(system, retry)
            if reply.parsed is not None:
                candidate, removed_again, hard_again = settle(reply.parsed, ctx, self.banned)
                if not hard_again:
                    text = candidate
                    result.notes += removed_again
                    unsupported = await self.checker.check(check_items(text, ctx), about=about)
            if unsupported:
                result.notes += [f"{path} togs bort: {p}" for path, p in unsupported.items()]
                text = remove_paths(text, set(unsupported))
        problems = minimum_problems(text)
        if problems:
            result.rejected, result.errors = text, problems
            return result
        result.text = text
        return result


def apply_text(record: Record, result: TextResult, generated: dict[str, Any]) -> None:
    status = status_for_site(record)
    if result.text is not None:
        record["text"] = {
            lang: to_site(getattr(result.text, lang), status) for lang in ("sv", "en")
        }
        record["status"] = "ok"
        record["errors"] = []
        record.pop("rejectedText", None)
    else:
        record["text"] = None
        record["status"] = "failed"
        record["errors"] = list(result.errors)
        rejected = result.rejected
        record["rejectedText"] = (
            {lang: to_site(getattr(rejected, lang), status) for lang in ("sv", "en")}
            if rejected is not None
            else None
        )
    record.setdefault("generated", {})["text"] = generated


@dataclass(frozen=True)
class WriteOptions:
    wave: int | None = None
    qids: tuple[str, ...] = ()
    model_key: str = "opus"
    effort: str = "high"
    checker_key: str = "sonnet"
    max_cost: float | None = None
    regenerate: bool = False
    allow_unreviewed: bool = False
    workers: int = 4


def _skip_reason(record: Record, options: WriteOptions) -> str | None:
    if not any(f["topic"] == "appearance" for f in record.get("facts", [])):
        return "faktabladet saknas eller misslyckades: kör web facts"
    reviewed = is_reviewed(record)
    if not reviewed and not options.allow_unreviewed:
        return "faktabladet är inte kontrollerat"
    generated = record.get("generated", {}).get("text") or {}
    current = (
        record.get("status") == "ok"
        and generated.get("factsHash") == facts_hash(record)
        and bool(generated.get("unreviewed")) == (not reviewed)
    )
    if current and not options.regenerate:
        return "texten är redan skriven ur samma faktablad"
    return None


async def run_write(
    paths: WebPaths,
    options: WriteOptions,
    *,
    client: JsonModelClient | None = None,
    now: datetime | None = None,
) -> list[StepOutcome]:
    if options.wave is None and not options.qids:
        raise ValueError("Ange en våg eller arter")
    now = now or datetime.now(UTC)
    records = load_all(paths.data_out)
    chosen = (
        [records[q] for q in options.qids if q in records]
        if options.qids
        else [r for r in records.values() if r.get("review", {}).get("wave") == options.wave]
    )
    groups = GroupTable(paths.family_groups, paths.web_groups)
    owned = client is None
    model_client: JsonModelClient = client or AnthropicJsonClient()
    cost = CostTracker(max_usd=options.max_cost)
    checker = SentenceChecker(
        client=model_client,
        cost=cost,
        prompt_path=paths.prompt_file(CHECK_PROMPT_VERSION),
        model_key=options.checker_key,
    )
    writer = SpeciesTextWriter(
        client=model_client,
        cost=cost,
        checker=checker,
        prompt_path=paths.prompt_file(PROMPT_VERSION),
        banned=load_banned(paths.banned),
        model_key=options.model_key,
        effort=options.effort,
    )
    stop = asyncio.Event()
    semaphore = asyncio.Semaphore(options.workers)

    async def one(record: Record) -> StepOutcome:
        qid, name = str(record["qid"]), str(record["names"]["sv"])
        async with semaphore:
            try:
                reason = _skip_reason(record, options)
                if reason is not None:
                    return StepOutcome(qid, name, "skipped", [reason])
                if stop.is_set():
                    return StepOutcome(qid, name, "skipped", ["kostnadstaket nåddes"])
                group = groups.by_key(record["group"])
                try:
                    result = await writer.write(record, group.name_sv, group.name_en)
                except MaxCostExceeded as exc:
                    stop.set()
                    return StepOutcome(qid, name, "skipped", [f"kostnadstaket nåddes: {exc}"])
                except CheckerFailed as exc:
                    return StepOutcome(qid, name, "failed", [str(exc)])
                generated: dict[str, Any] = {
                    "model": MODELS[options.model_key],
                    "prompt": PROMPT_VERSION,
                    "effort": options.effort,
                    "checker": MODELS[options.checker_key],
                    "at": now.isoformat(),
                    "factsHash": facts_hash(record),
                }
                if not is_reviewed(record):
                    generated["unreviewed"] = True
                apply_text(record, result, generated)
                save_record(record_path(paths.data_out, qid), record)
                return StepOutcome(qid, name, str(record["status"]), result.errors, result.notes)
            except Exception as exc:  # one species' error must not stop the run
                return StepOutcome(qid, name, "failed", [f"{type(exc).__name__}: {exc}"])

    try:
        outcomes = list(await asyncio.gather(*(one(r) for r in chosen)))
    finally:
        if owned and isinstance(model_client, AnthropicJsonClient):
            await model_client.aclose()
    model_line = (
        f"Skribent: `{MODELS[options.model_key]}` (effort: {options.effort}). "
        f"Kontroll: `{MODELS[options.checker_key]}`."
    )
    report = render_step_report(
        title="Text",
        date=now.date().isoformat(),
        outcomes=outcomes,
        cost_usd=cost.total_usd,
        model_line=model_line,
    )
    write_step_report(paths.reports, "text", now, report)
    return outcomes
