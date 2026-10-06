"""Tests for web/checker.py: the second model that checks every sentence."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.cost import CostTracker
from birdy_fetcher.web.checker import (
    CheckerFailed,
    CheckOutput,
    SentenceChecker,
    Verdict,
    check_items,
    render_items,
)
from birdy_fetcher.web.text_checks import TextContext

from .text_fixtures import FACTS, VALID
from .web_fakes import FakeJsonClient, reply

PIPELINE = Path(__file__).resolve().parents[1]
CTX = TextContext.from_facts(FACTS)


def _checker(client: FakeJsonClient) -> SentenceChecker:
    return SentenceChecker(
        client=client, cost=CostTracker(max_usd=None), prompt_path=PIPELINE / "prompts/check-v1.md"
    )


def test_items_cover_every_sentence_in_both_languages() -> None:
    items = check_items(VALID, CTX)
    ids = [i.id for i in items]
    assert "sv.lead[0]" in ids
    assert "en.look_alikes[0].sentences[0]" in ids
    assert len(ids) == 2 * 8  # lead 2, field_marks 3, voice 1, where_when 1, look-alike 1
    where = next(i for i in items if i.id == "sv.where_when[0]")
    assert [f["id"] for f in where.facts] == ["s01", "d01", "f05"]


def test_render_items_shows_the_quotes() -> None:
    text = render_items(check_items(VALID, CTX)[:1])
    assert '<item id="sv.lead[0]">' in text
    assert "f05: Talgoxen lever i skog, parker och trädgårdar." in text
    assert "citat: i skog, parker och trädgårdar" in text


async def test_unsupported_and_missing_verdicts_are_returned() -> None:
    items = check_items(VALID, CTX)
    verdicts = [Verdict(id=i.id, supported=True, problem=None) for i in items[1:]]
    verdicts[0] = Verdict(id=items[1].id, supported=False, problem="nämner inte kinderna")
    client = FakeJsonClient([reply(CheckOutput(verdicts=verdicts))])
    result = await _checker(client).check(items)
    assert result == {
        items[0].id: "kontrollen gav inget svar för meningen",
        items[1].id: "nämner inte kinderna",
    }
    assert client.models == ["claude-sonnet-5"]
    assert client.schemas == ["CheckOutput"]


async def test_no_items_means_no_call() -> None:
    client = FakeJsonClient([])
    assert await _checker(client).check([]) == {}


async def test_an_unusable_answer_raises() -> None:
    client = FakeJsonClient([reply(None, stop="max_tokens")])
    with pytest.raises(CheckerFailed):
        await _checker(client).check(check_items(VALID, CTX))
