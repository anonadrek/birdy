"""The second model (spec 2026-09-25 §9.6): a different model than the writer, in a fresh
context, judges every sentence against the facts it cites."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

from pydantic import BaseModel

from ..claude_summarizer import _split_prompt
from ..cost import CostTracker
from .llm import MODELS, JsonModelClient, record_cost
from .text_checks import TextContext
from .text_model import WebTextV2, iter_sentences

PROMPT_VERSION = "check-v1"


class Verdict(BaseModel):
    id: str
    supported: bool
    problem: str | None


class CheckOutput(BaseModel):
    verdicts: list[Verdict]


class CheckerFailed(RuntimeError):  # noqa: N818
    pass


@dataclass(frozen=True)
class CheckItem:
    id: str
    text: str
    facts: tuple[dict[str, Any], ...]


def check_items(text: WebTextV2, ctx: TextContext) -> list[CheckItem]:
    items: list[CheckItem] = []
    for lang in ("sv", "en"):
        for suffix, sentence in iter_sentences(getattr(text, lang)):
            facts = tuple(ctx.facts_by_id[f] for f in sentence.fact_ids if f in ctx.facts_by_id)
            items.append(CheckItem(f"{lang}.{suffix}", sentence.text, facts))
    return items


def _fact_line(fact: dict[str, Any]) -> str:
    line = f"{fact['id']}: {fact.get('sv', '')}"
    quotes = [s["quote"] for s in fact.get("sources", [])]
    return f"{line} (citat: {' | '.join(quotes)})" if quotes else line


def render_items(items: list[CheckItem]) -> str:
    blocks = []
    for item in items:
        facts = "\n".join(_fact_line(f) for f in item.facts)
        blocks.append(
            f'<item id="{item.id}">\n<sentence>{item.text}</sentence>\n'
            f"<facts>\n{facts}\n</facts>\n</item>"
        )
    return "\n\n".join(blocks)


@dataclass
class SentenceChecker:
    client: JsonModelClient
    cost: CostTracker
    prompt_path: Path
    model_key: str = "sonnet"
    effort: str = "high"

    async def check(self, items: list[CheckItem]) -> dict[str, str]:
        """Id to problem for every sentence that is not fully supported. A sentence the
        checker did not answer for counts as unsupported."""
        if not items:
            return {}
        template = self.prompt_path.read_text(encoding="utf-8")
        system, user = _split_prompt(template, items=render_items(items))
        reply = await self.client.complete(
            model=MODELS[self.model_key],
            system=system,
            messages=[{"role": "user", "content": user}],
            effort=self.effort,
            schema=CheckOutput,
        )
        record_cost(self.cost, self.model_key, reply)
        if reply.parsed is None:
            raise CheckerFailed(
                f"kontrollen gav inget giltigt svar (stop_reason={reply.stop_reason})"
            )
        verdicts = {v.id: v for v in reply.parsed.verdicts}
        result: dict[str, str] = {}
        for item in items:
            verdict = verdicts.get(item.id)
            if verdict is None:
                result[item.id] = "kontrollen gav inget svar för meningen"
            elif not verdict.supported:
                result[item.id] = verdict.problem or "stöds inte av faktan"
        return result
