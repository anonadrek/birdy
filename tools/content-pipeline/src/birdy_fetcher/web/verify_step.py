"""Step between the fact sheet and the exception sheet (spec 2026-09-25 Revision 2026-10-05):
V1 to V4, run once per species, no Albin. A species with an empty `flags` list needs no row
in the exception sheet at all (Task 16)."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from datetime import UTC, datetime

from ..cache import Cache
from ..cost import CostTracker, MaxCostExceeded
from .audio_check import AudioCheckFailed, audio_verdict, classify_clip
from .facts import apply_facts
from .facts_step import PROMPT_VERSION as FACTS_PROMPT_VERSION
from .facts_step import FactExtractor
from .llm import MODELS, AnthropicJsonClient, JsonModelClient
from .paths import WebPaths
from .record import Record, facts_hash, load_record, record_path, save_record
from .report import StepOutcome, render_step_report, write_step_report
from .source import SpeciesSource, load_approved, load_scientific_index
from .sources_step import ArticleSource
from .verify import (
    FactChecker,
    missing_required_topics,
    number_flags,
    status_flags,
    status_strike_flag,
    strike_unsupported,
)
from .wiki_full import FullWikiClient

PROMPT_VERSION = "verify-v1"


@dataclass(frozen=True)
class VerifyOptions:
    qids: tuple[str, ...] = ()
    model_key: str = "sonnet"
    effort: str = "high"
    max_cost: float | None = None
    force: bool = False
    workers: int = 4


def species_about(source: SpeciesSource) -> str:
    """The species line V1 is told it checks (C1, final review 2026-10-06)."""
    return f"{source.name_sv} / {source.name_en} ({source.scientific_name})"


def _current(record: Record) -> bool:
    stored = record.get("generated", {}).get("verify", {}).get("factsHash")
    return bool(stored == facts_hash(record))


async def run_verify(
    paths: WebPaths,
    options: VerifyOptions,
    *,
    client: JsonModelClient | None = None,
    wiki: ArticleSource | None = None,
    now: datetime | None = None,
) -> list[StepOutcome]:
    now = now or datetime.now(UTC)
    cache = Cache(paths.pipeline_root / ".cache")
    sources = load_approved(paths.species_root, options.qids)
    wiki = wiki or FullWikiClient(cache=cache)
    owned = client is None
    model_client: JsonModelClient = client or AnthropicJsonClient()
    cost = CostTracker(max_usd=options.max_cost)
    checker = FactChecker(
        client=model_client,
        cost=cost,
        prompt_path=paths.prompt_file(PROMPT_VERSION),
        model_key=options.model_key,
        effort=options.effort,
    )
    extractor = FactExtractor(
        cache=cache,
        cost=cost,
        client=model_client,
        prompt_path=paths.prompt_file(FACTS_PROMPT_VERSION),
        scientific_index=load_scientific_index(paths.species_root),
    )
    stop = asyncio.Event()
    semaphore = asyncio.Semaphore(options.workers)

    async def one(source: SpeciesSource) -> StepOutcome:
        async with semaphore:
            return await _one(source, paths, options, wiki, checker, extractor, stop, now)

    try:
        outcomes = list(await asyncio.gather(*(one(s) for s in sources)))
    finally:
        if owned and isinstance(model_client, AnthropicJsonClient):
            await model_client.aclose()
    report = render_step_report(
        title="Automatisk kontroll",
        date=now.date().isoformat(),
        outcomes=outcomes,
        cost_usd=cost.total_usd,
        model_line=f"Kontroll: `{MODELS[options.model_key]}`.",
    )
    write_step_report(paths.reports, "verify", now, report)
    return outcomes


def _retry_feedback(strike_notes: list[str], missing: list[str]) -> str:
    return (
        "A fact checker rejected some of the facts you gave earlier for this species:\n"
        + "\n".join(f"- {n}" for n in strike_notes)
        + f"\nWrite the whole fact sheet again. Keep every fact the checker did not reject, "
        f"and add what is missing so it still covers: {', '.join(missing)}."
    )


async def _one(
    source: SpeciesSource,
    paths: WebPaths,
    options: VerifyOptions,
    wiki: ArticleSource,
    checker: FactChecker,
    extractor: FactExtractor,
    stop: asyncio.Event,
    now: datetime,
) -> StepOutcome:
    def out(
        status: str, errors: list[str] | None = None, notes: list[str] | None = None
    ) -> StepOutcome:
        return StepOutcome(source.qid, source.name_sv, status, errors or [], notes or [])

    path = record_path(paths.data_out, source.qid)
    try:
        record = load_record(path)
        if record is None or not record.get("facts"):
            return out("failed", ["faktabladet saknas: kör web facts först"])
        if record.get("status") == "failed":
            return out("skipped", ["faktabladet är failed: ingenting att kontrollera"])
        if _current(record) and not options.force:
            return out("skipped", ["redan kontrollerat ur samma faktablad"])
        if record.get("publish"):
            # N5 (review fix 2026-10-06): the V1-retry path below (and --force) would
            # otherwise null the text and set status pending while publish stays true,
            # breaking the fas 2 build.
            return out("failed", ["publicerad: sätt publish: false först"])
        if stop.is_set():
            return out("skipped", ["kostnadstaket nåddes: körs vid nästa körning"])
        articles = await wiki.articles(source.qid)
        about = species_about(source)

        notes: list[str] = []
        try:
            verdicts = await checker.check(record["facts"], articles, about=about)
        except MaxCostExceeded as exc:
            stop.set()
            return out("skipped", [f"kostnadstaket nåddes: {exc}"])
        kept, strike_notes = strike_unsupported(record["facts"], verdicts)
        notes += strike_notes
        status_flag = status_strike_flag(verdicts)
        missing = missing_required_topics(kept)
        if missing and strike_notes:
            try:
                check, _, _ = await extractor.extract(
                    source, articles, extra_feedback=_retry_feedback(strike_notes, missing)
                )
            except MaxCostExceeded as exc:
                stop.set()
                return out("skipped", [f"kostnadstaket nåddes: {exc}"])
            if check.fatal:
                record["facts"] = kept
                record["status"] = "failed"
                record["errors"] = [f"saknas efter V1-omförsöket: {', '.join(missing)}"]
                # A verification left over from an earlier, better-looking run must not
                # survive this failure (review fix 2026-10-06, C1).
                record.pop("verification", None)
                save_record(path, record)
                return out("failed", record["errors"], notes)
            apply_facts(record, check, generated=record["generated"]["facts"])

            # The retried sheet is brand new text the model just wrote: it gets the same V1
            # pass the original facts did, so a fact it invents cannot slip through
            # unchecked (item 3, 2026-10-05 review fix).
            try:
                verdicts = await checker.check(record["facts"], articles, about=about)
            except MaxCostExceeded as exc:
                stop.set()
                return out("skipped", [f"kostnadstaket nåddes: {exc}"])
            kept, strike_notes = strike_unsupported(record["facts"], verdicts)
            notes += strike_notes
            status_flag = status_strike_flag(verdicts)
            missing = missing_required_topics(kept)
            if missing:
                record["facts"] = kept
                record["status"] = "failed"
                record["errors"] = [f"saknas efter V1-omförsöket: {', '.join(missing)}"]
                record.pop("verification", None)
                save_record(path, record)
                return out("failed", record["errors"], notes)
        record["facts"] = kept

        flags = number_flags(record, articles) + status_flags(record)
        if status_flag:
            flags.append(status_flag)

        audio = record.get("audio")
        if audio:
            identifiable = bool(record.get("identifiable", {}).get("sound"))
            try:
                result = classify_clip(paths.images_out / source.qid / "voice.mp3", paths.flexref)
            except AudioCheckFailed as exc:
                flags.append(
                    {
                        "check": "V4",
                        "factId": None,
                        "message": (
                            f"Ljudmodellen kunde inte köras ({exc}). Lyssna och besluta, "
                            "eller kör om när felet är åtgärdat."
                        ),
                    }
                )
            else:
                verdict = audio_verdict(result, source.qid, identifiable_sound=identifiable)
                if verdict.action == "strike":
                    record.pop("audio", None)
                    record.setdefault("review", {})["audioStruck"] = True
                    notes.append(f"inspelningen ströks: {verdict.reason}")
                elif verdict.action == "flag":
                    flags.append({"check": "V4", "factId": None, "message": verdict.reason})

        record["flags"] = flags
        record.setdefault("generated", {})["verify"] = {
            "model": MODELS[options.model_key],
            "prompt": PROMPT_VERSION,
            "at": now.isoformat(),
            "factsHash": facts_hash(record),
        }
        if flags:
            # A `verification` from an earlier, cleaner run must not survive once this
            # rerun finds something to flag (review fix 2026-10-06, C1): otherwise `web
            # write` would trust a decision that no longer matches these facts.
            record.pop("verification", None)
        else:
            # Ändrat 2026-10-05 (b): inga flaggor betyder inget att vänta på. Arten
            # behöver aldrig gå via undantagsarket eller `web import` (Task 17).
            record["verification"] = {
                "method": "auto",
                "at": now.date().isoformat(),
                "model": MODELS[options.model_key],
                "spotChecked": False,
            }
        save_record(path, record)
        return out("ok", [], [*notes, *([f"{len(flags)} flaggor"] if flags else [])])
    except Exception as exc:  # one species' error must not stop the run or overwrite a file
        return out("failed", [f"{type(exc).__name__}: {exc}"])
