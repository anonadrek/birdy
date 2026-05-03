"""Claude summarizer — Anthropic SDK wrapper with cache, cost cap, dry-run."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

from .cache import Cache
from .cost import CostTracker

MODEL_IDS = {
    "haiku": "claude-haiku-4-5-20251001",
    "sonnet": "claude-sonnet-4-6",
}


@dataclass
class ClaudeReply:
    text: str
    input_tokens: int
    output_tokens: int


@dataclass
class FakeClaudeClient:
    """Test double that returns canned messages and counts calls."""

    default: object = None
    call_count: int = 0
    by_q_id: dict[str, object] = field(default_factory=dict)

    async def messages_create(
        self,
        *,
        model: str,
        system: str,
        user: str,
        max_tokens: int,
    ) -> ClaudeReply:
        self.call_count += 1
        canned = self.default
        if hasattr(canned, "text"):
            return ClaudeReply(
                text=canned.text,
                input_tokens=canned.input_tokens,                  # type: ignore[attr-defined]
                output_tokens=canned.output_tokens,                # type: ignore[attr-defined]
            )
        return ClaudeReply(text="", input_tokens=0, output_tokens=0)


class _AnthropicAdapter:
    """Wraps the real anthropic SDK client into our messages_create signature."""

    def __init__(self) -> None:
        from anthropic import AsyncAnthropic

        api_key = os.environ.get("ANTHROPIC_API_KEY")
        if not api_key:
            raise RuntimeError(
                "ANTHROPIC_API_KEY not set. Copy .env.example to .env and fill it in."
            )
        self._client = AsyncAnthropic(api_key=api_key)

    async def messages_create(
        self,
        *,
        model: str,
        system: str,
        user: str,
        max_tokens: int,
    ) -> ClaudeReply:
        msg = await self._client.messages.create(
            model=model,
            max_tokens=max_tokens,
            system=system,
            messages=[{"role": "user", "content": user}],
        )
        text_parts = [b.text for b in msg.content if b.type == "text"]
        return ClaudeReply(
            text="\n\n".join(text_parts),
            input_tokens=msg.usage.input_tokens,
            output_tokens=msg.usage.output_tokens,
        )


def real_anthropic_client() -> _AnthropicAdapter:
    return _AnthropicAdapter()


@dataclass
class ClaudeSummarizer:
    cache: Cache
    cost: CostTracker
    client: object  # protocol-compatible: messages_create(...)
    prompt_dir: Path
    prompt_version: str
    dry_run: bool = False

    async def summarize_description(
        self,
        *,
        q_id: str,
        scientific_name: str,
        common_sv: str,
        common_en: str,
        family: str,
        family_sv: str,
        wikipedia_intro: str,
        lang: str,
        model: str,
    ) -> str:
        return await self._summarize(
            kind="description",
            q_id=q_id,
            scientific_name=scientific_name,
            common_sv=common_sv,
            common_en=common_en,
            family=family,
            family_sv=family_sv,
            wikipedia_intro=wikipedia_intro,
            lang=lang,
            model=model,
        )

    async def summarize_migration(
        self,
        *,
        q_id: str,
        scientific_name: str,
        common_sv: str,
        common_en: str,
        family: str,
        family_sv: str,
        wikipedia_intro: str,
        lang: str,
        model: str,
    ) -> str:
        return await self._summarize(
            kind="migration",
            q_id=q_id,
            scientific_name=scientific_name,
            common_sv=common_sv,
            common_en=common_en,
            family=family,
            family_sv=family_sv,
            wikipedia_intro=wikipedia_intro,
            lang=lang,
            model=model,
        )

    async def _summarize(
        self,
        *,
        kind: str,
        q_id: str,
        scientific_name: str,
        common_sv: str,
        common_en: str,
        family: str,
        family_sv: str,
        wikipedia_intro: str,
        lang: str,
        model: str,
    ) -> str:
        cache_filename = f"claude-{kind}-{lang}-{model}-{self.prompt_version}.txt"
        if self.cache.has(q_id, cache_filename):
            cached = self.cache.get(q_id, cache_filename)
            assert cached is not None
            return cached

        if self.dry_run:
            return "[dry-run]"

        prompt_path = self.prompt_dir / f"{kind}-{self.prompt_version}.md"
        prompt_template = prompt_path.read_text(encoding="utf-8")
        system, user = _split_prompt(
            prompt_template,
            scientific_name=scientific_name,
            common_sv=common_sv,
            common_en=common_en,
            family=family,
            family_sv=family_sv,
            lang=lang,
            wikipedia_intro_text=wikipedia_intro,
        )

        reply: ClaudeReply = await self.client.messages_create(  # type: ignore[attr-defined]
            model=MODEL_IDS[model],
            system=system,
            user=user,
            max_tokens=600,
        )

        self.cost.record(
            model=model,
            input_tokens=reply.input_tokens,
            output_tokens=reply.output_tokens,
        )
        self.cache.put(q_id, cache_filename, reply.text)
        return reply.text


def _split_prompt(
    template: str,
    **subs: str,
) -> tuple[str, str]:
    """Split markdown prompt template into (system, user) and substitute placeholders."""
    lines = template.splitlines()
    system_lines: list[str] = []
    user_lines: list[str] = []
    current: list[str] | None = None
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("System:"):
            current = system_lines
            system_lines.append(stripped[len("System:"):].strip())
        elif stripped.startswith("User:"):
            current = user_lines
            user_lines.append(stripped[len("User:"):].strip())
        elif current is not None:
            current.append(line)
    system = "\n".join(system_lines).strip()
    user = "\n".join(user_lines).strip()
    for key, value in subs.items():
        user = user.replace("{" + key + "}", value)
    return system, user
