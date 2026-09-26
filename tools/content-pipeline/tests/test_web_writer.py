"""Tests for web/writer.py: prompt, retry with feedback, cache and cost."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

import pytest
from anthropic.types import MessageParam

from birdy_fetcher.cache import Cache
from birdy_fetcher.cost import CostTracker, MaxCostExceeded
from birdy_fetcher.web.checks import load_banned
from birdy_fetcher.web.model import WebTextOutput
from birdy_fetcher.web.source import SourceImage, SpeciesSource
from birdy_fetcher.web.writer import StructuredReply, WebTextWriter, render_prompt

from .web_fixtures import ARTICLES, valid_output

PIPELINE = Path(__file__).resolve().parents[1]
PROMPT = PIPELINE / "prompts" / "web-v1.md"
BANNED = load_banned(PIPELINE / "prompts" / "web-banned-phrases.txt")

SOURCE = SpeciesSource(
    qid="Q25485",
    scientific_name="Parus major",
    name_sv="Talgoxe",
    name_en="Great Tit",
    family="Paridae",
    family_sv="Mesar",
    ioc_order="Passeriformes",
    iucn="LC",
    marginalia_sv=None,
    marginalia_en=None,
    images=(SourceImage("hero", "Q25485/hero.webp", "CC0", None, "https://x"),),
)


@dataclass
class FakeClient:
    replies: list[StructuredReply]
    calls: list[list[MessageParam]] = field(default_factory=list)
    systems: list[str] = field(default_factory=list)

    async def parse_web_text(
        self, *, model: str, system: str, messages: list[MessageParam], max_tokens: int
    ) -> StructuredReply:
        self.calls.append(list(messages))
        self.systems.append(system)
        return self.replies.pop(0)


def _reply(output: WebTextOutput | None) -> StructuredReply:
    return StructuredReply(
        output=output,
        raw_text="{}",
        input_tokens=10_000,
        output_tokens=2_000,
        stop_reason="end_turn",
    )


def _writer(tmp_path: Path, client: FakeClient, regenerate: bool = False) -> WebTextWriter:
    return WebTextWriter(
        cache=Cache(tmp_path),
        cost=CostTracker(max_usd=None),
        client=client,
        prompt_path=PROMPT,
        banned=BANNED,
        model_key="opus",
        regenerate=regenerate,
    )


def test_render_prompt_fills_every_placeholder() -> None:
    system, user = render_prompt(
        PROMPT.read_text(encoding="utf-8"), SOURCE, ARTICLES, "Tättingar", "Songbirds", BANNED
    )
    assert "{" not in system and "{" not in user
    assert "fascinerande" in system
    assert "Talgoxe" in user and "Great Tit" in user and "Tättingar" in user
    assert "14 centimeter" in user and "14 centimetres" in user


async def test_valid_first_answer_is_one_call_and_costs(tmp_path: Path) -> None:
    client = FakeClient([_reply(valid_output())])
    writer = _writer(tmp_path, client)
    result = await writer.write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert result.output is not None and result.issues == [] and result.dropped_facts == []
    assert result.attempts == 1 and len(client.calls) == 1
    assert writer.cost.total_usd == pytest.approx(0.10)  # 10k in at $5/M + 2k out at $25/M


async def test_bad_first_answer_gets_feedback_and_second_try(tmp_path: Path) -> None:
    bad = valid_output()
    bad.sv.lead = "En fascinerande fågel. Den finns i hela Sverige."
    client = FakeClient([_reply(bad), _reply(valid_output())])
    result = await _writer(tmp_path, client).write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert result.issues == [] and result.attempts == 2
    feedback = client.calls[1][-1]
    assert feedback["role"] == "user"
    assert "sv.lead" in str(feedback["content"]) and "fascinerande" in str(feedback["content"])


async def test_fact_issue_left_after_retry_drops_the_fact(tmp_path: Path) -> None:
    bad = valid_output()
    assert bad.sv.facts.size is not None
    bad.sv.facts.size.quote = "en påhittad mening om storleken"
    client = FakeClient([_reply(bad), _reply(bad)])
    result = await _writer(tmp_path, client).write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert result.issues == []
    assert result.output is not None and result.output.sv.facts.size is None
    assert [i.fact for i in result.dropped_facts] == ["size"]


async def test_hard_issue_left_after_retry_is_reported(tmp_path: Path) -> None:
    bad = valid_output()
    bad.en.voice = "We love it!"
    client = FakeClient([_reply(bad), _reply(bad)])
    result = await _writer(tmp_path, client).write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert {i.path for i in result.issues} == {"en.voice"}


async def test_no_output_twice_is_reported(tmp_path: Path) -> None:
    client = FakeClient([_reply(None), _reply(None)])
    result = await _writer(tmp_path, client).write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert result.output is None and result.issues


async def test_cached_answer_is_reused_without_a_call(tmp_path: Path) -> None:
    client = FakeClient([_reply(valid_output())])
    await _writer(tmp_path, client).write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    second = FakeClient([])
    result = await _writer(tmp_path, second).write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert result.from_cache and second.calls == [] and result.output is not None
    third = FakeClient([_reply(valid_output())])
    await _writer(tmp_path, third, regenerate=True).write(
        SOURCE, ARTICLES, "Tättingar", "Songbirds"
    )
    assert len(third.calls) == 1


async def test_retry_keeps_the_better_answer_when_the_second_try_is_worse(
    tmp_path: Path,
) -> None:
    first = valid_output()
    assert first.sv.facts.size is not None
    first.sv.facts.size.quote = "en påhittad mening om storleken"  # fact issue only
    second = valid_output()
    second.en.voice = "We love it!"  # hard issue
    client = FakeClient([_reply(first), _reply(second)])
    result = await _writer(tmp_path, client).write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert result.issues == []
    assert result.output is not None and result.output.sv.facts.size is None
    assert [i.fact for i in result.dropped_facts] == ["size"]


async def test_only_valid_reply_is_kept_and_cached_when_retry_gives_no_output(
    tmp_path: Path,
) -> None:
    first = valid_output()
    first.en.voice = "We love it!"
    client = FakeClient([_reply(first), _reply(None)])
    writer = _writer(tmp_path, client)
    result = await writer.write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert result.output is not None
    assert {i.path for i in result.issues} == {"en.voice"}

    cached = await _writer(tmp_path, FakeClient([])).write(
        SOURCE, ARTICLES, "Tättingar", "Songbirds"
    )
    assert cached.from_cache and cached.output is not None


async def test_cost_cap_exceeded_still_caches_the_paid_reply(tmp_path: Path) -> None:
    client = FakeClient([_reply(valid_output())])
    writer = WebTextWriter(
        cache=Cache(tmp_path),
        cost=CostTracker(max_usd=0.01),
        client=client,
        prompt_path=PROMPT,
        banned=BANNED,
        model_key="opus",
    )
    with pytest.raises(MaxCostExceeded):
        await writer.write(SOURCE, ARTICLES, "Tättingar", "Songbirds")

    cached = await _writer(tmp_path, FakeClient([])).write(
        SOURCE, ARTICLES, "Tättingar", "Songbirds"
    )
    assert cached.from_cache and cached.output is not None


async def test_no_output_still_bills_cost(tmp_path: Path) -> None:
    client = FakeClient([_reply(None), _reply(None)])
    writer = _writer(tmp_path, client)
    await writer.write(SOURCE, ARTICLES, "Tättingar", "Songbirds")
    assert writer.cost.total_usd == pytest.approx(0.20)  # two billed calls at 10k/2k each
    assert writer.cost.call_count == 2
