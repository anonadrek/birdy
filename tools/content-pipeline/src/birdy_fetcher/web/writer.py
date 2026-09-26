"""One model call per species (both languages), checked, with one retry (spec §7)."""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from anthropic import AsyncAnthropic, transform_schema
from anthropic.types import JSONOutputFormatParam, MessageParam, OutputConfigParam
from pydantic import ValidationError

from ..cache import Cache
from ..claude_summarizer import _split_prompt
from ..cost import CostTracker, MaxCostExceeded
from .checks import Issue, check_facts, check_text, drop_facts
from .model import WebTextOutput
from .source import SpeciesSource
from .wiki_full import WikiArticle

WEB_MODELS = {"opus": "claude-opus-5", "sonnet": "claude-sonnet-5"}
COST_KEYS = {"opus": "opus5", "sonnet": "sonnet5"}
PROMPT_VERSION = "web-v1"
MAX_TOKENS = 16_000
ATTEMPTS = 2
# `messages.parse()`'s post-parse failure path (see AnthropicStructuredClient) loses paid
# usage and the real stop_reason, so we build the JSON-schema output format ourselves and
# call `messages.create()` directly instead.
_FORMAT: JSONOutputFormatParam = {
    "type": "json_schema",
    "schema": transform_schema(WebTextOutput),
}


@dataclass
class StructuredReply:
    output: WebTextOutput | None
    raw_text: str
    input_tokens: int
    output_tokens: int
    stop_reason: str | None


class StructuredClient(Protocol):
    async def parse_web_text(
        self, *, model: str, system: str, messages: list[MessageParam], max_tokens: int,
        effort: str,
    ) -> StructuredReply: ...


class AnthropicStructuredClient:
    """The real client. The locked anthropic 0.97 SDK reads `ANTHROPIC_API_KEY` or
    `ANTHROPIC_AUTH_TOKEN` from the environment for `AsyncAnthropic()` -- there is no
    `ant auth login` profile support. Retries are turned up because 180 species means
    429/529 responses are routine.

    Uses `messages.create` with a JSON-schema output format, not the SDK's `.parse()` helper:
    in anthropic 0.97, `.parse()` raises a `pydantic.ValidationError` inside its own
    post-parser when a reply is cut off at `max_tokens` or is a refusal, which throws away
    the already-paid usage and the real `stop_reason` before the caller ever sees them. We
    validate the JSON text ourselves instead, so usage and stop_reason survive every path."""

    def __init__(self) -> None:
        self._client = AsyncAnthropic(max_retries=5)

    async def parse_web_text(
        self, *, model: str, system: str, messages: list[MessageParam], max_tokens: int,
        effort: str,
    ) -> StructuredReply:
        msg = await self._client.messages.create(
            model=model,
            max_tokens=max_tokens,
            system=system,
            messages=messages,
            # `effort` is a plain `str` throughout our own code (matching `model_key`), but
            # the SDK's TypedDict narrows it to a Literal -- the CLI's click.Choice already
            # constrains the actual values to a subset of that Literal at runtime.
            output_config=OutputConfigParam(format=_FORMAT, effort=effort),  # type: ignore[typeddict-item]
        )
        text = "".join(block.text for block in msg.content if block.type == "text")
        try:
            output: WebTextOutput | None = WebTextOutput.model_validate_json(text)
        except ValidationError:
            output = None
        return StructuredReply(
            output=output,
            raw_text=text,
            input_tokens=msg.usage.input_tokens,
            output_tokens=msg.usage.output_tokens,
            stop_reason=msg.stop_reason,
        )

    async def aclose(self) -> None:
        await self._client.close()


@dataclass
class WriteResult:
    output: WebTextOutput | None
    issues: list[Issue]
    dropped_facts: list[Issue]
    attempts: int
    from_cache: bool


def render_prompt(
    template: str,
    source: SpeciesSource,
    articles: dict[str, WikiArticle],
    group_sv: str,
    group_en: str,
    banned: list[str],
) -> tuple[str, str]:
    return _split_prompt(
        template,
        name_sv=source.name_sv,
        name_en=source.name_en,
        scientific_name=source.scientific_name,
        family=source.family,
        family_sv=source.family_sv,
        group_sv=group_sv,
        group_en=group_en,
        iucn=source.iucn,
        banned_phrases=", ".join(banned),
        article_sv=articles["sv"].text if "sv" in articles else "(no Swedish article)",
        article_en=articles["en"].text if "en" in articles else "(no English article)",
    )


def feedback_message(issues: list[Issue]) -> str:
    lines = "\n".join(f"- {i.path}: {i.message}" for i in issues)
    return (
        "Your answer broke these rules. Write the whole answer again with the same structure "
        "and fix only these points:\n" + lines + "\n"
        "For any facts.* point above, either copy an exact quote from one of the articles or "
        "set that fact to null. Never invent or paraphrase a quote."
    )


@dataclass
class WebTextWriter:
    cache: Cache
    cost: CostTracker
    client: StructuredClient
    prompt_path: Path
    banned: list[str]
    model_key: str
    effort: str = "high"  # the API default for Opus 5, so this keeps prior behaviour unchanged
    regenerate: bool = False

    @property
    def model_id(self) -> str:
        return WEB_MODELS[self.model_key]

    def _cache_name(self, template: str, articles: dict[str, WikiArticle]) -> str:
        prompt_hash = hashlib.sha256(template.encode("utf-8")).hexdigest()[:8]
        revs = "-".join(f"{lang}{articles[lang].revision}" for lang in sorted(articles))
        return f"web-text-{self.model_key}-{self.effort}-{prompt_hash}-{revs}.json"

    def _finish(
        self, output: WebTextOutput, articles: dict[str, WikiArticle], attempts: int, cached: bool
    ) -> WriteResult:
        issues = check_text(output, self.banned) + check_facts(output, articles)
        hard = [i for i in issues if i.fact is None]
        facts = [i for i in issues if i.fact is not None]
        return WriteResult(drop_facts(output, facts), hard, facts, attempts, cached)

    async def write(
        self,
        source: SpeciesSource,
        articles: dict[str, WikiArticle],
        group_sv: str,
        group_en: str,
    ) -> WriteResult:
        template = self.prompt_path.read_text(encoding="utf-8")
        cache_name = self._cache_name(template, articles)
        cached = None if self.regenerate else self.cache.get(source.qid, cache_name)
        if cached is not None:
            return self._finish(
                WebTextOutput.model_validate_json(cached), articles, attempts=0, cached=True
            )

        system, user = render_prompt(template, source, articles, group_sv, group_en, self.banned)
        messages: list[MessageParam] = [{"role": "user", "content": user}]
        best_output: WebTextOutput | None = None
        best_hard_count = 0
        last_issues: list[Issue] = []
        attempts = 0
        for attempt in range(1, ATTEMPTS + 1):
            attempts = attempt
            reply = await self.client.parse_web_text(
                model=self.model_id, system=system, messages=messages, max_tokens=MAX_TOKENS,
                effort=self.effort,
            )
            try:
                self.cost.record(
                    model=COST_KEYS[self.model_key],
                    input_tokens=reply.input_tokens,
                    output_tokens=reply.output_tokens,
                )
            except MaxCostExceeded:
                # The reply is already paid for. Keep the better of this attempt and any
                # earlier one -- but only if it is good enough (zero hard issues) to skip
                # re-asking on the next run. Otherwise cache nothing, so a rerun with more
                # budget starts fresh with its own retry instead of reusing a bad answer.
                candidate, candidate_hard = best_output, best_hard_count
                if reply.output is not None:
                    reply_issues = check_text(reply.output, self.banned) + check_facts(
                        reply.output, articles
                    )
                    reply_hard = len([i for i in reply_issues if i.fact is None])
                    if candidate is None or reply_hard <= candidate_hard:
                        candidate, candidate_hard = reply.output, reply_hard
                if candidate is not None and candidate_hard == 0:
                    self.cache.put(source.qid, cache_name, candidate.model_dump_json(indent=2))
                raise

            if reply.output is None:
                last_issues = [
                    Issue(
                        "svar",
                        f"modellen gav inget giltigt svar (stop_reason={reply.stop_reason})",
                    )
                ]
                # A cut-off (max_tokens) or a content-policy refusal will not change on an
                # identical retry -- sending the same request again just pays for the same
                # non-answer twice. Stop and report it instead.
                if reply.stop_reason in ("max_tokens", "refusal"):
                    break
                continue

            output = reply.output
            issues = check_text(output, self.banned) + check_facts(output, articles)
            last_issues = issues
            # Keep the best valid answer seen so far, not just the last one: a retry meant to
            # fix one problem can introduce a worse one, and that must not throw away an
            # otherwise usable first answer. Fewest hard (non-droppable) issues wins; ties go
            # to the later attempt.
            hard_count = len([i for i in issues if i.fact is None])
            if best_output is None or hard_count <= best_hard_count:
                best_output, best_hard_count = output, hard_count
            if not issues:
                break
            if attempt < ATTEMPTS:
                messages = [
                    *messages,
                    {"role": "assistant", "content": reply.raw_text or output.model_dump_json()},
                    {"role": "user", "content": feedback_message(issues)},
                ]

        if best_output is None:
            return WriteResult(None, last_issues, [], attempts, False)
        self.cache.put(source.qid, cache_name, best_output.model_dump_json(indent=2))
        return self._finish(best_output, articles, attempts=attempts, cached=False)
