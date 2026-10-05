"""Tests for web/verify.py: V1 (the fact checker), V2 (numbers) and V3 (red list)."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from birdy_fetcher.cost import CostTracker
from birdy_fetcher.web.verify import (
    FactChecker,
    FactVerdict,
    FactVerifyOutput,
    Verdict,
    missing_required_topics,
    render_facts_for_check,
    strike_unsupported,
)
from birdy_fetcher.web.wiki_full import WikiArticle

from .web_fakes import FakeJsonClient, reply

ARTICLE = WikiArticle(
    "sv",
    "Talgoxe",
    "1",
    "Talgoxen är en vanlig fågel i Sverige.\n\n"
    "Den har svart huvud med vita kinder och gul buk.\n\n"
    "Sången är ett ringande ti-ta ti-ta.",
)
FACTS: list[dict[str, Any]] = [
    {
        "id": "f01",
        "topic": "appearance",
        "sv": "Svart huvud med vita kinder.",
        "sources": [{"article": "sv", "quote": "svart huvud med vita kinder"}],
    },
    {
        "id": "f02",
        "topic": "voice",
        "sv": "Sången hörs på långt håll.",
        "sources": [{"article": "sv", "quote": "ett ringande ti-ta ti-ta"}],
    },
    {"id": "d01", "topic": "data", "source": "artportalen", "sv": "Rapporteras året runt."},
]


def _checker(client: FakeJsonClient, prompt_path: Path) -> FactChecker:
    return FactChecker(client=client, cost=CostTracker(max_usd=None), prompt_path=prompt_path)


def test_render_facts_for_check_shows_the_paragraph_and_skips_data_facts() -> None:
    text = render_facts_for_check(FACTS, {"sv": ARTICLE})
    assert '<fact id="f01">' in text
    assert "svart huvud med vita kinder och gul buk" in text
    assert "d01" not in text


async def test_unsupported_facts_are_reported(tmp_path: Path) -> None:
    prompt = tmp_path / "verify-v1.md"
    prompt.write_text("System: x\n\nUser: {facts}", encoding="utf-8")
    verdicts = [
        FactVerdict(fact_id="f01", verdict="supported", reason=""),
        FactVerdict(fact_id="f02", verdict="unsupported", reason="citatet nämner inget avstånd"),
    ]
    client = FakeJsonClient([reply(FactVerifyOutput(verdicts=verdicts))])
    result = await _checker(client, prompt).check(FACTS, {"sv": ARTICLE})
    assert result == {"f02": ("unsupported", "citatet nämner inget avstånd")}
    assert client.schemas == ["FactVerifyOutput"]


async def test_a_fact_with_no_verdict_counts_as_unsupported(tmp_path: Path) -> None:
    prompt = tmp_path / "verify-v1.md"
    prompt.write_text("System: x\n\nUser: {facts}", encoding="utf-8")
    client = FakeJsonClient([reply(FactVerifyOutput(verdicts=[]))])
    result = await _checker(client, prompt).check(FACTS, {"sv": ARTICLE})
    assert set(result) == {"f01", "f02"}


async def test_no_checkable_facts_means_no_call(tmp_path: Path) -> None:
    prompt = tmp_path / "verify-v1.md"
    prompt.write_text("System: x\n\nUser: {facts}", encoding="utf-8")
    client = FakeJsonClient([])
    assert await _checker(client, prompt).check(FACTS[2:], {"sv": ARTICLE}) == {}


def test_strike_unsupported_keeps_everything_else() -> None:
    verdicts: dict[str, tuple[Verdict, str]] = {
        "f02": ("unsupported", "citatet nämner inget avstånd")
    }
    kept, notes = strike_unsupported(FACTS, verdicts)
    assert [f["id"] for f in kept] == ["f01", "d01"]
    assert "f02 ströks (unsupported): citatet nämner inget avstånd" in notes[0]


def test_missing_required_topics() -> None:
    assert missing_required_topics([{"topic": "appearance"}]) == ["läte", "miljö"]
    only = [{"topic": "appearance"}, {"topic": "voice"}, {"topic": "habitat"}]
    assert missing_required_topics(only) == []
