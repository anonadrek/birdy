"""Automatic verification (spec 2026-09-25 Revision 2026-10-05): replaces Albin reviewing
every fact sheet by hand. V1 here is the second model that checks every fact against its
own quote. V2 and V3 (added in Task 14c) are code, no model."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel

from ..claude_summarizer import _split_prompt
from ..cost import CostTracker
from .facts import REQUIRED_TOPICS, TOPIC_SV
from .llm import MODELS, JsonModelClient, record_cost
from .wiki_full import WikiArticle

PROMPT_VERSION = "verify-v1"
Verdict = Literal["supported", "partial", "unsupported"]

# Severity order: unsupported > partial > supported (highest to lowest)
VERDICT_SEVERITY = {"unsupported": 3, "partial": 2, "supported": 1}


class FactVerdict(BaseModel):
    fact_id: str
    verdict: Verdict
    reason: str


class FactVerifyOutput(BaseModel):
    verdicts: list[FactVerdict]


class FactCheckFailed(RuntimeError):  # noqa: N818
    pass


def _paragraph(article_text: str, quote: str) -> str:
    """The paragraph the quote sits in. Blank lines split paragraphs; this only needs to
    find roughly where the quote is, the exact-match normalisation lives in checks.py."""
    for paragraph in article_text.split("\n\n"):
        if quote[:40].lower() in paragraph.lower():
            return paragraph.strip()
    return quote


def render_facts_for_check(facts: list[dict[str, Any]], articles: dict[str, WikiArticle]) -> str:
    blocks = []
    for fact in facts:
        if fact["topic"] == "data":
            continue
        source = fact["sources"][0]
        article = articles.get(source["article"])
        paragraph = _paragraph(article.text, source["quote"]) if article else source["quote"]
        blocks.append(
            f'<fact id="{fact["id"]}">\n<claim>{fact["sv"]}</claim>\n'
            f"<quote>{source['quote']}</quote>\n<paragraph>{paragraph}</paragraph>\n</fact>"
        )
    return "\n\n".join(blocks)


@dataclass
class FactChecker:
    client: JsonModelClient
    cost: CostTracker
    prompt_path: Path
    model_key: str = "sonnet"
    effort: str = "high"

    async def check(
        self, facts: list[dict[str, Any]], articles: dict[str, WikiArticle]
    ) -> dict[str, tuple[Verdict, str]]:
        """Fact id to (verdict, reason), for every non-`partial`/`unsupported`-free fact
        that is not a data fact. A fact the model did not answer for counts as unsupported."""
        checkable = [f for f in facts if f["topic"] != "data"]
        if not checkable:
            return {}
        template = self.prompt_path.read_text(encoding="utf-8")
        system, user = _split_prompt(template, facts=render_facts_for_check(checkable, articles))
        reply = await self.client.complete(
            model=MODELS[self.model_key],
            system=system,
            messages=[{"role": "user", "content": user}],
            effort=self.effort,
            schema=FactVerifyOutput,
        )
        record_cost(self.cost, self.model_key, reply)
        if reply.parsed is None:
            raise FactCheckFailed(
                f"kontrollen gav inget giltigt svar (stop_reason={reply.stop_reason})"
            )
        # Keep the most severe verdict per fact id (unsupported > partial > supported).
        by_id: dict[str, FactVerdict] = {}
        for v in reply.parsed.verdicts:
            existing = by_id.get(v.fact_id)
            if existing is None or VERDICT_SEVERITY[v.verdict] > VERDICT_SEVERITY[existing.verdict]:
                by_id[v.fact_id] = v
        result: dict[str, tuple[Verdict, str]] = {}
        for fact in checkable:
            verdict = by_id.get(fact["id"])
            if verdict is None:
                result[fact["id"]] = ("unsupported", "kontrollen gav inget svar för faktumet")
            elif verdict.verdict != "supported":
                result[fact["id"]] = (verdict.verdict, verdict.reason)
        return result


def strike_unsupported(
    facts: list[dict[str, Any]], verdicts: dict[str, tuple[Verdict, str]]
) -> tuple[list[dict[str, Any]], list[str]]:
    """Facts with a `partial` or `unsupported` verdict are struck; everything else (including
    every data fact, which is never in `verdicts`) is kept."""
    notes = [f"{fid} ströks ({label}): {reason}" for fid, (label, reason) in verdicts.items()]
    kept = [f for f in facts if f["id"] not in verdicts]
    return kept, notes


def missing_required_topics(facts: list[dict[str, Any]]) -> list[str]:
    return [TOPIC_SV[t] for t in REQUIRED_TOPICS if not any(f["topic"] == t for f in facts)]
