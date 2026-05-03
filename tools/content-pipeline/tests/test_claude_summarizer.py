"""Tests for claude_summarizer.py — fake Anthropic client, dry-run, cost cap."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import pytest

from birdy_fetcher.cache import Cache
from birdy_fetcher.claude_summarizer import ClaudeSummarizer, FakeClaudeClient
from birdy_fetcher.cost import CostTracker, MaxCostExceeded


@dataclass
class FakeMessage:
    text: str = "Genererad beskrivning av talgoxen."
    input_tokens: int = 5000
    output_tokens: int = 250


@pytest.mark.asyncio
async def test_summarize_returns_text(tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    client = FakeClaudeClient(default=FakeMessage())
    tracker = CostTracker(max_usd=None)
    sum_ = ClaudeSummarizer(
        cache=cache,
        cost=tracker,
        client=client,
        prompt_dir=Path("prompts"),
        prompt_version="v1",
    )

    text = await sum_.summarize_description(
        q_id="Q25485",
        scientific_name="Parus major",
        common_sv="Talgoxe",
        common_en="Great Tit",
        family="Paridae",
        family_sv="Mesar",
        wikipedia_intro="Talgoxen är ...",
        lang="sv",
        model="haiku",
    )
    assert "Genererad" in text
    assert tracker.call_count == 1
    assert tracker.total_usd > 0


@pytest.mark.asyncio
async def test_summarize_uses_cache_on_second_call(tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    client = FakeClaudeClient(default=FakeMessage())
    tracker = CostTracker(max_usd=None)
    sum_ = ClaudeSummarizer(
        cache=cache,
        cost=tracker,
        client=client,
        prompt_dir=Path("prompts"),
        prompt_version="v1",
    )

    common_args = dict(
        q_id="Q25485",
        scientific_name="Parus major",
        common_sv="Talgoxe",
        common_en="Great Tit",
        family="Paridae",
        family_sv="Mesar",
        wikipedia_intro="Talgoxen är ...",
        lang="sv",
        model="haiku",
    )
    await sum_.summarize_description(**common_args)
    await sum_.summarize_description(**common_args)
    assert client.call_count == 1


@pytest.mark.asyncio
async def test_dry_run_makes_no_api_call(tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    client = FakeClaudeClient(default=FakeMessage())
    tracker = CostTracker(max_usd=None)
    sum_ = ClaudeSummarizer(
        cache=cache,
        cost=tracker,
        client=client,
        prompt_dir=Path("prompts"),
        prompt_version="v1",
        dry_run=True,
    )

    text = await sum_.summarize_description(
        q_id="Q25485",
        scientific_name="Parus major",
        common_sv="Talgoxe",
        common_en="Great Tit",
        family="Paridae",
        family_sv="Mesar",
        wikipedia_intro="Talgoxen är ...",
        lang="sv",
        model="haiku",
    )
    assert text == "[dry-run]"
    assert client.call_count == 0
    assert tracker.call_count == 0


@pytest.mark.asyncio
async def test_max_cost_aborts(tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    client = FakeClaudeClient(default=FakeMessage(input_tokens=10_000, output_tokens=500))
    tracker = CostTracker(max_usd=0.001)
    sum_ = ClaudeSummarizer(
        cache=cache,
        cost=tracker,
        client=client,
        prompt_dir=Path("prompts"),
        prompt_version="v1",
    )

    with pytest.raises(MaxCostExceeded):
        await sum_.summarize_description(
            q_id="Q25485",
            scientific_name="Parus major",
            common_sv="Talgoxe",
            common_en="Great Tit",
            family="Paridae",
            family_sv="Mesar",
            wikipedia_intro="x",
            lang="sv",
            model="haiku",
        )
