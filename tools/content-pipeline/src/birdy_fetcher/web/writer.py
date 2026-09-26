"""One model call per species (both languages), checked, with one retry (spec §7)."""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from anthropic.types import MessageParam
from pydantic import ValidationError

from ..cache import Cache
from ..claude_summarizer import _split_prompt
from ..cost import CostTracker
from .checks import Issue, check_facts, check_text, drop_facts
from .model import WebTextOutput
from .source import SpeciesSource
from .wiki_full import WikiArticle

WEB_MODELS = {"opus": "claude-opus-5", "sonnet": "claude-sonnet-5"}
COST_KEYS = {"opus": "opus5", "sonnet": "sonnet5"}
PROMPT_VERSION = "web-v1"
MAX_TOKENS = 16_000
ATTEMPTS = 2


@dataclass
class StructuredReply:
    output: WebTextOutput | None
    raw_text: str
    input_tokens: int
    output_tokens: int
    stop_reason: str | None


class StructuredClient(Protocol):
    async def parse_web_text(
        self, *, model: str, system: str, messages: list[MessageParam], max_tokens: int
    ) -> StructuredReply: ...


class AnthropicStructuredClient:
    """The real client. `AsyncAnthropic()` finds ANTHROPIC_API_KEY or an `ant auth login`
    profile."""

    def __init__(self) -> None:
        from anthropic import AsyncAnthropic

        self._client = AsyncAnthropic()

    async def parse_web_text(
        self, *, model: str, system: str, messages: list[MessageParam], max_tokens: int
    ) -> StructuredReply:
        try:
            msg = await self._client.messages.parse(
                model=model,
                max_tokens=max_tokens,
                system=system,
                messages=messages,
                output_format=WebTextOutput,
            )
        except ValidationError as exc:
            return StructuredReply(None, str(exc), 0, 0, "invalid_output")
        text = "".join(block.text for block in msg.content if block.type == "text")
        return StructuredReply(
            output=msg.parsed_output,
            raw_text=text,
            input_tokens=msg.usage.input_tokens,
            output_tokens=msg.usage.output_tokens,
            stop_reason=msg.stop_reason,
        )


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
        "and fix only these points:\n" + lines
    )


@dataclass
class WebTextWriter:
    cache: Cache
    cost: CostTracker
    client: StructuredClient
    prompt_path: Path
    banned: list[str]
    model_key: str
    regenerate: bool = False

    @property
    def model_id(self) -> str:
        return WEB_MODELS[self.model_key]

    def _cache_name(self, template: str, articles: dict[str, WikiArticle]) -> str:
        prompt_hash = hashlib.sha256(template.encode("utf-8")).hexdigest()[:8]
        revs = "-".join(f"{lang}{articles[lang].revision}" for lang in sorted(articles))
        return f"web-text-{self.model_key}-{prompt_hash}-{revs}.json"

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
        output: WebTextOutput | None = None
        issues: list[Issue] = []
        attempts = 0
        for attempt in range(1, ATTEMPTS + 1):
            attempts = attempt
            reply = await self.client.parse_web_text(
                model=self.model_id, system=system, messages=messages, max_tokens=MAX_TOKENS
            )
            self.cost.record(
                model=COST_KEYS[self.model_key],
                input_tokens=reply.input_tokens,
                output_tokens=reply.output_tokens,
            )
            if reply.output is None:
                issues = [
                    Issue(
                        "svar",
                        f"modellen gav inget giltigt svar (stop_reason={reply.stop_reason})",
                    )
                ]
                continue
            output = reply.output
            issues = check_text(output, self.banned) + check_facts(output, articles)
            if not issues:
                break
            if attempt < ATTEMPTS:
                messages = [
                    *messages,
                    {"role": "assistant", "content": reply.raw_text or output.model_dump_json()},
                    {"role": "user", "content": feedback_message(issues)},
                ]

        if output is None:
            return WriteResult(None, issues, [], attempts, False)
        self.cache.put(source.qid, cache_name, output.model_dump_json(indent=2))
        return self._finish(output, articles, attempts=attempts, cached=False)
