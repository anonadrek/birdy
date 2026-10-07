"""Step 2 (spec 2026-09-25 §9.3): the fact sheet for each species, with one retry and a
cache, so a rerun never pays twice for the same articles and prompt."""

from __future__ import annotations

import asyncio
import hashlib
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path

from anthropic.types import MessageParam

from ..cache import Cache
from ..claude_summarizer import _split_prompt
from ..cost import CostTracker, MaxCostExceeded
from .defaults import FACTS_EFFORT, FACTS_MODEL_KEY
from .facts import FactCheck, FactSheetOutput, apply_facts, check_fact_sheet
from .llm import MODELS, AnthropicJsonClient, JsonModelClient, record_cost
from .paths import WebPaths
from .record import is_reviewed, load_record, record_path, save_record
from .report import StepOutcome, render_step_report, write_step_report
from .source import SpeciesSource, load_approved, load_name_index
from .sources_step import ArticleSource
from .wiki_full import FullWikiClient, WikiArticle

PROMPT_VERSION = "facts-v1"
ATTEMPTS = 2


class FactsFailed(RuntimeError):  # noqa: N818
    pass


def render_facts_prompt(
    template: str, source: SpeciesSource, articles: dict[str, WikiArticle]
) -> tuple[str, str]:
    def text(lang: str, missing: str) -> str:
        return articles[lang].text if lang in articles else missing

    return _split_prompt(
        template,
        name_sv=source.name_sv,
        name_en=source.name_en,
        scientific_name=source.scientific_name,
        family=source.family,
        family_sv=source.family_sv,
        article_sv=text("sv", "(no Swedish article)"),
        article_en=text("en", "(no English article)"),
        article_de=text("de", "(no German article)"),
    )


def facts_feedback(check: FactCheck) -> str:
    lines = "\n".join(f"- {item}" for item in [*check.retry, *check.notes[:10]])
    return (
        "Your answer broke these rules. Write the whole answer again with the same structure "
        "and fix them:\n" + lines + "\n"
        "Every quote must be copied character for character from the named article. Never "
        "invent or paraphrase a quote."
    )


def _better(new: FactCheck, old: FactCheck) -> bool:
    return (len(new.fatal), len(new.retry), -len(new.facts)) <= (
        len(old.fatal),
        len(old.retry),
        -len(old.facts),
    )


@dataclass
class FactExtractor:
    cache: Cache
    cost: CostTracker
    client: JsonModelClient
    prompt_path: Path
    scientific_index: dict[str, str]
    model_key: str = FACTS_MODEL_KEY
    effort: str = FACTS_EFFORT
    regenerate: bool = False
    # Lowercased scientific name to family, for a look-alike in an older genus (scinames).
    scientific_families: dict[str, str] = field(default_factory=dict)

    def _cache_name(
        self, template: str, articles: dict[str, WikiArticle], extra_feedback: str | None = None
    ) -> str:
        """A V1 retry (extra feedback) answers a different prompt than `web facts`, so it
        gets its own entry: it must never overwrite the plain one (Minor 3, final review
        2026-10-06)."""
        prompt_hash = hashlib.sha256(template.encode("utf-8")).hexdigest()[:8]
        revs = "-".join(f"{lang}{articles[lang].revision}" for lang in sorted(articles))
        retry = ""
        if extra_feedback:
            retry = "-v1retry-" + hashlib.sha256(extra_feedback.encode("utf-8")).hexdigest()[:8]
        return f"facts-{self.model_key}-{self.effort}-{prompt_hash}{retry}-{revs}.json"

    def _check(
        self, out: FactSheetOutput, articles: dict[str, WikiArticle], source: SpeciesSource
    ) -> FactCheck:
        return check_fact_sheet(
            out,
            articles,
            self.scientific_index,
            subject=source.scientific_name,
            families=self.scientific_families,
        )

    async def extract(
        self,
        source: SpeciesSource,
        articles: dict[str, WikiArticle],
        *,
        extra_feedback: str | None = None,
    ) -> tuple[FactCheck, int, bool]:
        """(check, attempts, from_cache). Raises FactsFailed when no answer was usable and
        MaxCostExceeded when the cap is passed. extra_feedback (Revision 2026-10-05, the V1
        retry) is part of the cache name, so the plain answer (same articles, no feedback) is
        never reused for it, and the retry's answer never replaces the plain one."""
        template = self.prompt_path.read_text(encoding="utf-8")
        name = self._cache_name(template, articles, extra_feedback)
        cached = None if self.regenerate else self.cache.get(source.qid, name)
        if cached is not None:
            return (
                self._check(FactSheetOutput.model_validate_json(cached), articles, source),
                0,
                True,
            )

        system, user = render_facts_prompt(template, source, articles)
        messages: list[MessageParam] = [{"role": "user", "content": user}]
        if extra_feedback:
            messages.append({"role": "user", "content": extra_feedback})
        best: tuple[FactSheetOutput, FactCheck] | None = None
        reason = "modellen gav inget svar"
        attempts = 0
        for attempt in range(1, ATTEMPTS + 1):
            attempts = attempt
            reply = await self.client.complete(
                model=MODELS[self.model_key],
                system=system,
                messages=messages,
                effort=self.effort,
                schema=FactSheetOutput,
            )
            try:
                record_cost(self.cost, self.model_key, reply)
            except MaxCostExceeded:
                if reply.parsed is not None:
                    capped = self._check(reply.parsed, articles, source)
                    if not capped.retry:
                        self.cache.put(source.qid, name, reply.parsed.model_dump_json(indent=2))
                raise
            check = (
                self._check(reply.parsed, articles, source) if reply.parsed is not None else None
            )
            if reply.parsed is None or check is None:
                reason = f"modellen gav inget giltigt svar (stop_reason={reply.stop_reason})"
                if reply.stop_reason in ("max_tokens", "refusal"):
                    break
                continue
            if best is None or _better(check, best[1]):
                best = (reply.parsed, check)
            if not check.retry:
                break
            if attempt < ATTEMPTS:
                messages = [
                    *messages,
                    {"role": "assistant", "content": reply.raw_text},
                    {"role": "user", "content": facts_feedback(check)},
                ]
        if best is None:
            raise FactsFailed(reason)
        self.cache.put(source.qid, name, best[0].model_dump_json(indent=2))
        return best[1], attempts, False


def facts_generated(extractor: FactExtractor, now: datetime) -> dict[str, str]:
    """`generated.facts` for a sheet this extractor just wrote."""
    return {
        "model": MODELS[extractor.model_key],
        "prompt": PROMPT_VERSION,
        "effort": extractor.effort,
        "at": now.isoformat(),
    }


@dataclass(frozen=True)
class FactsOptions:
    qids: tuple[str, ...] = ()
    model_key: str = FACTS_MODEL_KEY
    effort: str = FACTS_EFFORT
    max_cost: float | None = None
    force: bool = False
    regenerate: bool = False
    workers: int = 4


async def run_facts(
    paths: WebPaths,
    options: FactsOptions,
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
    names = load_name_index(paths.species_root)
    extractor = FactExtractor(
        cache=cache,
        cost=cost,
        client=model_client,
        prompt_path=paths.prompt_file(PROMPT_VERSION),
        scientific_index=names.qids,
        scientific_families=names.families,
        model_key=options.model_key,
        effort=options.effort,
        regenerate=options.regenerate,
    )
    stop = asyncio.Event()
    semaphore = asyncio.Semaphore(options.workers)

    async def one(source: SpeciesSource) -> StepOutcome:
        async with semaphore:
            return await _one(source, paths, options, wiki, extractor, stop, now)

    try:
        outcomes = list(await asyncio.gather(*(one(s) for s in sources)))
    finally:
        if owned and isinstance(model_client, AnthropicJsonClient):
            await model_client.aclose()
    model_line = f"Modell: `{MODELS[options.model_key]}` (effort: {options.effort})."
    report = render_step_report(
        title="Faktablad",
        date=now.date().isoformat(),
        outcomes=outcomes,
        cost_usd=cost.total_usd,
        model_line=model_line,
    )
    write_step_report(paths.reports, "facts", now, report)
    return outcomes


async def _one(
    source: SpeciesSource,
    paths: WebPaths,
    options: FactsOptions,
    wiki: ArticleSource,
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
        if record is None:
            return out("failed", ["artposten saknas: kör web sources först"])
        if is_reviewed(record) and not options.force:
            return out("skipped", ["faktabladet är kontrollerat: körs inte om utan --force"])
        if record.get("generated", {}).get("verify") and not options.force:
            # N4 (review fix 2026-10-06): a species with open flags (verified, but not yet
            # `is_reviewed`) must not be silently rebuilt from the cache -- that would undo
            # V1's strikes and drop the flags Albin still needs to decide on.
            return out(
                "skipped",
                [
                    "faktabladet har varit igenom web verify (väntar på beslut): "
                    "körs inte om utan --force"
                ],
            )
        if record.get("publish"):
            # N5 (review fix 2026-10-06): --force would otherwise null the text and set
            # status pending while publish stays true, breaking the fas 2 build.
            return out("failed", ["publicerad: sätt publish: false först"])
        if stop.is_set():
            return out("skipped", ["kostnadstaket nåddes: körs vid nästa körning"])
        articles = await wiki.articles(source.qid)
        if not articles:
            return out("failed", ["ingen Wikipediaartikel"])
        try:
            check, attempts, cached = await extractor.extract(source, articles)
        except MaxCostExceeded as exc:
            stop.set()
            return out("skipped", [f"kostnadstaket nåddes: {exc}"])
        except FactsFailed as exc:
            record["status"] = "failed"
            record["errors"] = [str(exc)]
            save_record(path, record)
            return out("failed", [str(exc)])
        generated = facts_generated(extractor, now)
        if cached and record.get("generated", {}).get("facts"):
            generated = record["generated"]["facts"]
        apply_facts(record, check, generated=generated)
        save_record(path, record)
        notes = [*check.notes, *([] if attempts < 2 else ["två försök"])]
        if check.fatal:
            return out("failed", check.fatal, notes)
        return out("pending", [], [*notes, *check.retry])
    except Exception as exc:  # one species' error must not stop the run or overwrite a file
        return out("failed", [f"{type(exc).__name__}: {exc}"])
