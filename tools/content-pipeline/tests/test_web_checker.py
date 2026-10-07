"""Tests for web/checker.py: the second model that checks every sentence."""

from __future__ import annotations

from pathlib import Path

import pytest

from birdy_fetcher.claude_summarizer import _split_prompt
from birdy_fetcher.cost import CostTracker
from birdy_fetcher.web.checker import (
    CheckerFailed,
    CheckItem,
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
ABOUT = "Talgoxe (Parus major), familj Mesar"

EDITED_FACT = {
    "id": "f03",
    "topic": "size",
    "sv": "16 till 18 cm",
    "edited": True,
    "sources": [{"article": "sv", "quote": "cirka 14 centimeter lång"}],
}


def _checker(client: FakeJsonClient) -> SentenceChecker:
    return SentenceChecker(
        client=client, cost=CostTracker(max_usd=None), prompt_path=PIPELINE / "prompts/check-v1.md"
    )


def test_items_cover_every_sentence_in_both_languages() -> None:
    items = check_items(VALID, CTX)
    ids = [i.id for i in items]
    assert "sv.lead[0]" in ids
    assert "en.look_alikes[0].sentences[0]" in ids
    # lead 2, field_marks 3, voice 1, where_when 1, look-alike 1, meta_description 1
    assert len(ids) == 2 * 9
    where = next(i for i in items if i.id == "sv.where_when[0]")
    assert [f["id"] for f in where.facts] == ["s01", "d01", "f05"]


def test_render_items_shows_the_quotes() -> None:
    text = render_items(check_items(VALID, CTX)[:1])
    assert '<item id="sv.lead[0]">' in text
    assert "f05: Talgoxen lever i skog, parker och trädgårdar." in text
    assert "citat: i skog, parker och trädgårdar" in text


def test_the_prompt_allows_look_alike_items_to_describe_the_other_species() -> None:
    """The right-species rule (above) would otherwise strike a correct sentence like
    "Blåmesen är mindre och har blå hätta." under look_alikes, whose job is exactly to
    describe the other species that the cited look-alike fact names (fix 2026-10-06)."""
    template = (PIPELINE / "prompts/check-v1.md").read_text(encoding="utf-8")
    system, _ = _split_prompt(template, items="", about="")
    assert "look_alikes" in system
    assert "may describe that other species" in system


def test_render_items_shows_an_edited_fact_without_its_stale_quote() -> None:
    item = CheckItem("sv.size", "16 till 18 cm.", (EDITED_FACT,))
    text = render_items([item])
    assert "f03: 16 till 18 cm (ändrad av redaktören, faktatexten gäller)" in text
    assert "citat" not in text


async def test_unsupported_and_missing_verdicts_are_returned() -> None:
    items = check_items(VALID, CTX)
    verdicts = [Verdict(id=i.id, supported=True, problem=None) for i in items[1:]]
    verdicts[0] = Verdict(id=items[1].id, supported=False, problem="nämner inte kinderna")
    client = FakeJsonClient([reply(CheckOutput(verdicts=verdicts))])
    result = await _checker(client).check(items, about=ABOUT)
    assert result == {
        items[0].id: "kontrollen gav inget svar för meningen",
        items[1].id: "nämner inte kinderna",
    }
    assert client.models == ["claude-sonnet-5"]
    assert client.schemas == ["CheckOutput"]
    assert ABOUT in client.calls[0][0]["content"]


async def test_no_items_means_no_call() -> None:
    client = FakeJsonClient([])
    assert await _checker(client).check([], about=ABOUT) == {}


async def test_an_unusable_answer_raises() -> None:
    client = FakeJsonClient([reply(None, stop="max_tokens")])
    checker = _checker(client)
    with pytest.raises(CheckerFailed):
        await checker.check(check_items(VALID, CTX), about=ABOUT)


async def test_cost_is_recorded_even_when_the_answer_is_unusable() -> None:
    client = FakeJsonClient([reply(None, stop="max_tokens")])
    checker = _checker(client)
    with pytest.raises(CheckerFailed):
        await checker.check(check_items(VALID, CTX), about=ABOUT)
    assert checker.cost.call_count == 1


async def test_duplicate_verdicts_keep_the_worst_one() -> None:
    """Mirrors the bbfbfcab fix in verify.py's FactChecker: an id answered both ways must
    end up unsupported, in either order."""
    items = check_items(VALID, CTX)
    target = items[0].id
    others = [Verdict(id=i.id, supported=True, problem=None) for i in items[1:]]

    false_first = [
        Verdict(id=target, supported=False, problem="saknar stöd"),
        Verdict(id=target, supported=True, problem=None),
        *others,
    ]
    client = FakeJsonClient([reply(CheckOutput(verdicts=false_first))])
    result = await _checker(client).check(items, about=ABOUT)
    assert result[target] == "saknar stöd"

    true_first = [
        Verdict(id=target, supported=True, problem=None),
        Verdict(id=target, supported=False, problem="saknar stöd igen"),
        *others,
    ]
    client2 = FakeJsonClient([reply(CheckOutput(verdicts=true_first))])
    result2 = await _checker(client2).check(items, about=ABOUT)
    assert result2[target] == "saknar stöd igen"


async def test_an_unknown_extra_id_is_ignored() -> None:
    items = check_items(VALID, CTX)
    verdicts = [Verdict(id=i.id, supported=True, problem=None) for i in items]
    verdicts.append(Verdict(id="sv.not_a_real_item[0]", supported=False, problem="okänt"))
    client = FakeJsonClient([reply(CheckOutput(verdicts=verdicts))])
    result = await _checker(client).check(items, about=ABOUT)
    assert result == {}


async def test_a_missing_problem_on_an_unsupported_verdict_gets_the_default_message() -> None:
    items = check_items(VALID, CTX)
    verdicts = [Verdict(id=i.id, supported=True, problem=None) for i in items]
    verdicts[0] = Verdict(id=items[0].id, supported=False, problem=None)
    client = FakeJsonClient([reply(CheckOutput(verdicts=verdicts))])
    result = await _checker(client).check(items, about=ABOUT)
    assert result == {items[0].id: "stöds inte av faktan"}


async def test_whitespace_around_a_returned_id_does_not_break_the_match() -> None:
    items = check_items(VALID, CTX)
    verdicts = [Verdict(id=i.id, supported=True, problem=None) for i in items]
    verdicts[0] = Verdict(id=f"  {items[0].id}  ", supported=False, problem="med mellanslag")
    client = FakeJsonClient([reply(CheckOutput(verdicts=verdicts))])
    result = await _checker(client).check(items, about=ABOUT)
    assert result == {items[0].id: "med mellanslag"}


def test_the_meta_description_is_checked_against_every_fact_the_text_cites() -> None:
    """I5 (final review 2026-10-06): the meta description reached the search results
    without the second model ever reading it."""
    items = {i.id: i for i in check_items(VALID, CTX)}
    meta = items["sv.meta_description"]
    assert meta.text == VALID.sv.meta_description
    cited: list[str] = []
    for item in check_items(VALID, CTX):
        if item.id.startswith("sv.") and item.id != "sv.meta_description":
            cited += [f["id"] for f in item.facts]
    if VALID.sv.size is not None:
        cited += VALID.sv.size.fact_ids
    assert [f["id"] for f in meta.facts] == list(dict.fromkeys(cited))
    assert "en.meta_description" in items


def test_the_prompt_explains_the_meta_description_item() -> None:
    prompt = (PIPELINE / "prompts/check-v1.md").read_text(encoding="utf-8")
    assert "meta_description" in prompt


def test_the_prompt_keeps_a_county_share_a_share() -> None:
    """R3 (2026-10-07): the checker passed "flest rapporter" written from a share."""
    template = (PIPELINE / "prompts/check-v1.md").read_text(encoding="utf-8")
    system, _ = _split_prompt(template, items="", about="")
    assert "share of all bird reports" in system
    assert "most reports come from there" in system
