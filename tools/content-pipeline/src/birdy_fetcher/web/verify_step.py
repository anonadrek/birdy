"""Step between the fact sheet and the exception sheet (spec 2026-09-25 Revision 2026-10-05):
V1 to V4, run once per species, no Albin. A species with an empty `flags` list needs no row
in the exception sheet at all (Task 16)."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from ..cache import Cache
from ..cost import CostTracker, MaxCostExceeded
from .audio_check import AudioCheckFailed, AudioVerdict, audio_verdict, classify_clip
from .facts import apply_facts
from .facts_step import DEFAULT_EFFORT as FACTS_DEFAULT_EFFORT
from .facts_step import DEFAULT_MODEL_KEY as FACTS_DEFAULT_MODEL_KEY
from .facts_step import PROMPT_VERSION as FACTS_PROMPT_VERSION
from .facts_step import FactExtractor, facts_generated
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
    # The V1 retry writes a new fact sheet: same model and effort as `web facts` (Minor 3,
    # final review 2026-10-06). Change both together if R3 picks something else.
    facts_model_key: str = FACTS_DEFAULT_MODEL_KEY
    facts_effort: str = FACTS_DEFAULT_EFFORT


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
        model_key=options.facts_model_key,
        effort=options.facts_effort,
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
        model_line=(
            f"Kontroll: `{MODELS[options.model_key]}`. Omförsök av faktabladet: "
            f"`{MODELS[options.facts_model_key]}` (effort: {options.facts_effort})."
        ),
    )
    write_step_report(paths.reports, "verify", now, report)
    return outcomes


async def _audio_check(
    record: Record, source: SpeciesSource, paths: WebPaths
) -> AudioVerdict | str | None:
    """V4, run before the paid V1 call (I4, final review 2026-10-06) so an audio problem
    never wastes V1 work, and in a thread so the subprocess does not block the other
    workers. None without a recording, the verdict, or the reason the model could not run.
    Only the verdict is computed here; the record is changed after V1, as before."""
    if not record.get("audio"):
        return None
    identifiable = bool(record.get("identifiable", {}).get("sound"))
    try:
        result = await asyncio.to_thread(
            classify_clip, paths.images_out / source.qid / "voice.mp3", paths.flexref
        )
    except AudioCheckFailed as exc:
        return str(exc)
    return audio_verdict(result, source.qid, identifiable_sound=identifiable)


def _apply_audio(
    record: Record,
    audio: AudioVerdict | str | None,
    flags: list[dict[str, Any]],
    notes: list[str],
) -> None:
    if audio is None:
        return
    if isinstance(audio, str):
        flags.append(
            {
                "check": "V4",
                "factId": None,
                "message": (
                    f"Ljudmodellen kunde inte köras ({audio}). Lyssna och besluta, "
                    "eller kör om när felet är åtgärdat."
                ),
            }
        )
    elif audio.action == "strike":
        record.pop("audio", None)
        record.setdefault("review", {})["audioStruck"] = True
        notes.append(f"inspelningen ströks: {audio.reason}")
    elif audio.action == "flag":
        flags.append({"check": "V4", "factId": None, "message": audio.reason})


def _fail(record: Record, path: Path, kept: list[dict[str, Any]], missing: list[str]) -> None:
    record["facts"] = kept
    record["status"] = "failed"
    record["errors"] = [f"saknas efter V1-omförsöket: {', '.join(missing)}"]
    # A verification left over from an earlier, better-looking run must not survive this
    # failure (review fix 2026-10-06, C1), and neither may its metadata: `web facts`
    # refuses a species with `generated.verify` unless forced, so a stale one would make
    # `web facts --regenerate` skip the species this failure asks it to redo (Minor 1).
    record.pop("verification", None)
    record.get("generated", {}).pop("verify", None)
    save_record(path, record)


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
        audio = await _audio_check(record, source, paths)
        if stop.is_set():
            # Another worker reached the cap while the audio model ran.
            return out("skipped", ["kostnadstaket nåddes: körs vid nästa körning"])

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
                _fail(record, path, kept, missing)
                return out("failed", record["errors"], notes)
            # The record now holds the retried sheet: say what wrote it (Minor 3, final
            # review 2026-10-06), not the original `web facts` run's model and time.
            apply_facts(
                record, check, generated={**facts_generated(extractor, now), "v1Retry": True}
            )

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
                _fail(record, path, kept, missing)
                return out("failed", record["errors"], notes)
        record["facts"] = kept

        flags = number_flags(record, articles) + status_flags(record)
        if status_flag:
            flags.append(status_flag)

        _apply_audio(record, audio, flags, notes)

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
