"""Step 3 (spec 2026-09-25 §9.5 and §9.6): write the text from the reviewed fact sheet,
check it in code, check it with a second model, rewrite once, remove what still fails.

The loop itself lives in `checked_writer.py` (shared with the comparison texts since
Task 22); the guards that depend on the species record (`_keep_old_text`, the stale
published citations check N1, `_resolve_lookalikes`) stay here."""

from __future__ import annotations

import asyncio
import hashlib
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from anthropic.types import MessageParam

from ..claude_summarizer import _split_prompt
from ..cost import CostTracker, MaxCostExceeded
from .checked_writer import Checks, Written, write_checked
from .checker import PROMPT_VERSION as CHECK_PROMPT_VERSION
from .checker import CheckerFailed, SentenceChecker, check_items
from .checks import load_banned
from .defaults import TEXT_MODEL_KEY
from .facts import STATUS_SV, TOPIC_SV
from .groups import GroupTable
from .llm import MODELS, AnthropicJsonClient, JsonModelClient, ModelReply, record_cost
from .paths import WebPaths
from .record import Record, facts_hash, is_reviewed, load_all, record_path, save_record
from .report import StepOutcome, render_step_report, write_step_report
from .text_checks import TextContext, check_text, minimum_problems, path_texts, settle
from .text_model import WebTextV2, remove_paths, status_for_site, to_site
from .verify import missing_required_topics

PROMPT_VERSION = "web-v2"


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
        f"{STATUS_SV[status['value']]} (fact {status['factIds'][0]})"
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


@dataclass
class SpeciesTextWriter:
    client: JsonModelClient
    cost: CostTracker
    checker: SentenceChecker
    prompt_path: Path
    banned: list[str]
    model_key: str = TEXT_MODEL_KEY
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

    async def write(self, record: Record, group_sv: str, group_en: str) -> Written[WebTextV2]:
        facts = writer_facts(record)
        ctx = TextContext.from_facts(facts)
        about = (
            f"{record['names']['sv']} / {record['names']['en']} "
            f"({record['names']['scientific']}), familj {record['family']['sv']}"
        )
        template = self.prompt_path.read_text(encoding="utf-8")
        system, user = render_write_prompt(template, record, facts, group_sv, group_en, self.banned)
        checks: Checks[WebTextV2] = Checks(
            rules=lambda t: check_text(t, ctx, self.banned),
            settle=lambda t: settle(t, ctx, self.banned),
            items=lambda t: check_items(t, ctx),
            remove=remove_paths,
            minimum=minimum_problems,
            texts=path_texts,
        )
        return await write_checked(
            ask=lambda messages: self._ask(system, messages),
            user=user,
            checks=checks,
            checker=self.checker,
            about=about,
        )


def _lookalike_qid_map(record: Record) -> dict[str, str]:
    """Scientific name to QID for every look-alike fact that has one (M5, review fix
    2026-10-06): the writer is told to use the QID when the fact's label has one, but a
    rewrite can still echo back the scientific name instead -- map it before it reaches
    the site."""
    mapping: dict[str, str] = {}
    for fact in record.get("facts", []):
        if fact.get("topic") != "lookalike":
            continue
        other = fact.get("other") or {}
        qid = other.get("qid")
        for name in (other.get("scientific"), other.get("written")):
            if qid and name:
                mapping[name] = qid
    return mapping


def _resolve_lookalikes(text: WebTextV2, qid_map: dict[str, str]) -> WebTextV2:
    if not qid_map:
        return text
    data = text.model_dump()
    changed = False
    for lang in ("sv", "en"):
        for look_alike in data[lang]["look_alikes"]:
            if look_alike["other"] in qid_map:
                look_alike["other"] = qid_map[look_alike["other"]]
                changed = True
    return WebTextV2.model_validate(data) if changed else text


def _text_cited_fact_ids(lang_text: dict[str, Any]) -> set[str]:
    """Every fact id one language half of `record["text"]` (the site-JSON shape `to_site`
    produces) cites: sentences, look-alikes, size and swedenStatus."""
    ids: set[str] = set()
    for key in ("lead", "fieldMarks", "voice", "whereWhen", "behaviour"):
        for sentence in lang_text.get(key) or []:
            ids.update(sentence.get("factIds") or [])
    for look_alike in lang_text.get("lookAlikes") or []:
        for sentence in look_alike.get("text") or []:
            ids.update(sentence.get("factIds") or [])
    facts = lang_text.get("facts") or {}
    for key in ("size", "swedenStatus"):
        entry = facts.get(key)
        if entry:
            ids.update(entry.get("factIds") or [])
    return ids


def stale_cited_fact_ids(record: Record) -> list[str]:
    """Fact ids `record["text"]` cites that no longer exist in `record["facts"]`. Empty
    whenever there is no text yet. Public (Task 23): `waves.py`'s publish predicate reuses
    this to check a species' text before it is ever published, not only after -- see
    `_stale_published_fact_ids` below for the published-only guard N1 uses."""
    if not record.get("text"):
        return []
    current_ids = {f["id"] for f in record.get("facts", [])}
    cited: set[str] = set()
    for lang in ("sv", "en"):
        cited |= _text_cited_fact_ids(record["text"].get(lang) or {})
    return sorted(cited - current_ids)


def _stale_published_fact_ids(record: Record) -> list[str]:
    """Fact ids the live, published text cites that no longer exist in `record["facts"]`
    (N1, review fix 2026-10-06): a spot-check strike on a fact must never be allowed to
    quietly leave a published page citing a fact that is gone. Empty whenever the species
    is not published."""
    if not record.get("publish"):
        return []
    return stale_cited_fact_ids(record)


def _stale_published_error(stale: list[str]) -> str:
    return (
        f"publicerad text anger fakta som inte längre finns ({', '.join(stale)}): "
        "sätt publish: false"
    )


def apply_text(record: Record, result: Written[WebTextV2], generated: dict[str, Any]) -> None:
    status = status_for_site(record)
    qid_map = _lookalike_qid_map(record)
    if result.text is not None:
        resolved = _resolve_lookalikes(result.text, qid_map)
        record["text"] = {lang: to_site(getattr(resolved, lang), status) for lang in ("sv", "en")}
        record["status"] = "ok"
        record["errors"] = []
        record.pop("rejectedText", None)
    else:
        record["text"] = None
        record["status"] = "failed"
        record["errors"] = list(result.errors)
        rejected = result.rejected
        resolved_rejected = _resolve_lookalikes(rejected, qid_map) if rejected is not None else None
        record["rejectedText"] = (
            {lang: to_site(getattr(resolved_rejected, lang), status) for lang in ("sv", "en")}
            if resolved_rejected is not None
            else None
        )
    record.setdefault("generated", {})["text"] = generated


@dataclass(frozen=True)
class WriteOptions:
    wave: int | None = None
    qids: tuple[str, ...] = ()
    model_key: str = TEXT_MODEL_KEY
    effort: str = "high"
    checker_key: str = "sonnet"
    max_cost: float | None = None
    regenerate: bool = False
    allow_unreviewed: bool = False
    workers: int = 4
    # I6: try a text that failed with the same inputs once more, without --regenerate's
    # rewrite of every current text.
    retry_failed: bool = False


def facts_verified(record: Record) -> bool:
    """True when the automatic verification has passed the facts as they are now. Shared
    with the comparison step (Task 22), which writes only from two verified sheets."""
    if not is_reviewed(record):
        return False
    # Defence in depth (C1): if some other code path ever forgets to clear `verification`
    # when the facts move on, a verify hash that no longer matches the current facts is
    # treated as not reviewed instead of trusted blindly.
    verify_hash = (record.get("generated", {}).get("verify") or {}).get("factsHash")
    return bool(verify_hash == facts_hash(record))


def text_is_current(record: Record) -> bool:
    """True when `record["generated"]["text"]["factsHash"]` still matches the facts as
    they are now. Shared (M5, review fix 2026-10-06) by `_skip_reason`, `_keep_old_text`
    and `waves.py`'s publish predicate -- one definition of "the text we have was written
    from these facts", not three."""
    generated = record.get("generated", {}).get("text") or {}
    return bool(generated.get("factsHash") == facts_hash(record))


def attempt_settings(
    *,
    model_key: str,
    effort: str,
    checker_key: str,
    prompt_hash: str,
    checker_prompt_hash: str,
    banned: list[str],
) -> dict[str, str]:
    """What decides a written (or failed) text besides the facts: the writer, its effort,
    the checker, both prompts and the banned phrases (in the prompt and the code checks,
    follow-up 6 of the wave B review). Stored in `generated`, compared by `same_attempt`."""
    banned_hash = hashlib.sha256("\n".join(banned).encode("utf-8")).hexdigest()[:8]
    return {
        "model": MODELS[model_key],
        "effort": effort,
        "checker": MODELS[checker_key],
        "promptHash": prompt_hash,
        "checkerPromptHash": checker_prompt_hash,
        "bannedHash": banned_hash,
    }


def same_attempt(generated: dict[str, Any], settings: dict[str, str]) -> bool:
    return all(generated.get(key) == value for key, value in settings.items())


FAILED_BEFORE = (
    "misslyckades förra gången med samma faktablad, prompter och modeller: "
    "kör med --retry-failed (eller --regenerate) för att betala för ett nytt försök"
)


def _skip_reason(
    record: Record, options: WriteOptions, settings: dict[str, str] | None = None
) -> str | None:
    if missing_required_topics(record.get("facts", [])):
        # (C1, review fix 2026-10-06) The appearance-only proxy missed a sheet that still
        # has appearance but lost voice or habitat to a V1 strike -- use the same check V1
        # itself uses, so a failed sheet is skipped even under --allow-unreviewed.
        return "faktabladet saknas eller misslyckades: kör web facts"
    reviewed = facts_verified(record)
    if not reviewed and not options.allow_unreviewed:
        return "faktabladet är inte kontrollerat"
    generated = record.get("generated", {}).get("text") or {}
    current = (
        record.get("status") == "ok"
        and text_is_current(record)
        and bool(generated.get("unreviewed")) == (not reviewed)
    )
    if current and not options.regenerate:
        return "texten är redan skriven ur samma faktablad"
    failed_before = (
        record.get("status") == "failed"
        and text_is_current(record)
        and bool(generated.get("unreviewed")) == (not reviewed)
        and settings is not None
        and same_attempt(generated, settings)
    )
    if failed_before and not (options.regenerate or options.retry_failed):
        # I6 (final review 2026-10-06): the same inputs again would most likely fail again,
        # and every rerun paid for it.
        return f"texten {FAILED_BEFORE}"
    return None


def _keep_old_text(record: Record) -> bool:
    """A failed rewrite must never destroy a text the site may still depend on (I1, review
    fix 2026-10-06): a published page (the Astro build needs status == ok, spec §9.7) or a
    text that is still current for these facts (a flaky retry must not wipe a good
    answer). Only a stale, unpublished text may be nulled out."""
    if record.get("publish"):
        return True
    if record.get("status") != "ok":
        return False
    return text_is_current(record)


def prompt_file_hash(path: Path) -> str:
    return hashlib.sha256(path.read_text(encoding="utf-8").encode("utf-8")).hexdigest()[:8]


async def run_write(
    paths: WebPaths,
    options: WriteOptions,
    *,
    client: JsonModelClient | None = None,
    now: datetime | None = None,
) -> list[StepOutcome]:
    if options.model_key == options.checker_key:
        # I3 (review fix 2026-10-06, spec §9.6): the checker must be a different model than
        # the writer, in a fresh context.
        raise ValueError("Skribenten och kontrollen måste vara olika modeller.")
    if options.wave is None and not options.qids:
        raise ValueError("Ange en våg eller arter")
    now = now or datetime.now(UTC)
    records = load_all(paths.data_out)
    missing_outcomes: list[StepOutcome] = []
    if options.qids:
        # M1 (review fix 2026-10-06): dedupe --species, and report an unknown QID as a
        # failed outcome instead of silently dropping it.
        chosen = []
        for qid in dict.fromkeys(options.qids):
            if qid in records:
                chosen.append(records[qid])
            else:
                missing_outcomes.append(
                    StepOutcome(qid, qid, "failed", ["artposten saknas: kör web sources först"])
                )
    else:
        chosen = [r for r in records.values() if r.get("review", {}).get("wave") == options.wave]
    groups = GroupTable(paths.family_groups, paths.web_groups)
    # Everything that reads a file comes before the client exists (review fix 2026-10-06,
    # M7), so an early error cannot leave an Anthropic client open.
    banned = load_banned(paths.banned)
    prompt_hash = prompt_file_hash(paths.prompt_file(PROMPT_VERSION))
    settings = attempt_settings(
        model_key=options.model_key,
        effort=options.effort,
        checker_key=options.checker_key,
        prompt_hash=prompt_hash,
        checker_prompt_hash=prompt_file_hash(paths.prompt_file(CHECK_PROMPT_VERSION)),
        banned=banned,
    )
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
        banned=banned,
        model_key=options.model_key,
        effort=options.effort,
    )
    stop = asyncio.Event()
    semaphore = asyncio.Semaphore(options.workers)

    async def one(record: Record) -> StepOutcome:
        # M2 (review fix 2026-10-06): qid alone is safe before the try; a malformed
        # record (e.g. missing "names") must fail on its own, not abort the whole run.
        qid = str(record.get("qid", "?"))
        name = qid
        async with semaphore:
            try:
                name = str(record["names"]["sv"])
                # N1 (review fix 2026-10-06): computed once, before anything else -- a
                # published page citing a fact that no longer exists (e.g. a spot-check
                # strike) must fail loudly, not quietly skip or hide the warning in a note
                # `_print_outcomes` never shows.
                stale = _stale_published_fact_ids(record)
                reason = _skip_reason(record, options, settings)
                if reason is not None:
                    if stale:
                        return StepOutcome(qid, name, "failed", [_stale_published_error(stale)])
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
                if result.text is None and _keep_old_text(record):
                    # I1 (review fix 2026-10-06): a failed rewrite must not destroy a text
                    # the record still depends on. P5 (review fix 2026-10-06): the warning
                    # goes in `errors`, not `notes` -- `_print_outcomes` only shows errors.
                    notes = [*result.notes, "den tidigare texten behölls"]
                    errors = list(result.errors)
                    if stale:
                        errors.append(_stale_published_error(stale))
                    else:
                        text_generated = record.get("generated", {}).get("text") or {}
                        if record.get("publish") and text_generated.get("factsHash") != facts_hash(
                            record
                        ):
                            errors.append(
                                "arten är publicerad och faktabladet har ändrats sedan "
                                "texten skrevs: sätt publish: false om den gamla texten "
                                "nu är fel"
                            )
                    return StepOutcome(qid, name, "failed", errors, notes)
                generated: dict[str, Any] = {
                    **settings,
                    "prompt": PROMPT_VERSION,
                    "checkerPrompt": CHECK_PROMPT_VERSION,
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
        outcomes = missing_outcomes + list(await asyncio.gather(*(one(r) for r in chosen)))
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
