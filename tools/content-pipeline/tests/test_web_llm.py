"""Tests for web/llm.py: JSON-schema answers and cost bookkeeping."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast

from pydantic import BaseModel

from birdy_fetcher.cost import CostTracker
from birdy_fetcher.web.llm import COST_KEYS, MODELS, AnthropicJsonClient, ModelReply, record_cost


class Answer(BaseModel):
    name: str


@dataclass
class _Usage:
    input_tokens: int
    output_tokens: int


@dataclass
class _Block:
    type: str
    text: str


@dataclass
class _Message:
    content: list[_Block]
    usage: _Usage
    stop_reason: str


@dataclass
class _Messages:
    reply: _Message
    kwargs: dict[str, Any] = field(default_factory=dict)

    async def create(self, **kwargs: Any) -> _Message:
        self.kwargs = kwargs
        return self.reply


@dataclass
class _Anthropic:
    messages: _Messages
    closed: bool = False

    async def close(self) -> None:
        self.closed = True


def _client(text: str, stop: str = "end_turn") -> tuple[AnthropicJsonClient, _Anthropic]:
    fake = _Anthropic(_Messages(_Message([_Block("text", text)], _Usage(1000, 200), stop)))
    return AnthropicJsonClient(client=cast(Any, fake)), fake


async def test_valid_json_is_parsed_and_the_schema_is_sent() -> None:
    client, fake = _client('{"name": "Talgoxe"}')
    reply = await client.complete(
        model="claude-opus-5", system="s", messages=[], effort="high", schema=Answer
    )
    assert reply.parsed == Answer(name="Talgoxe")
    assert (reply.input_tokens, reply.output_tokens) == (1000, 200)
    sent = fake.messages.kwargs["output_config"]
    assert sent["effort"] == "high"
    assert sent["format"]["type"] == "json_schema"
    assert "name" in sent["format"]["schema"]["properties"]


async def test_invalid_json_keeps_usage_and_stop_reason() -> None:
    client, _ = _client('{"name": ', stop="max_tokens")
    reply = await client.complete(
        model="claude-opus-5", system="s", messages=[], effort="high", schema=Answer
    )
    assert reply.parsed is None
    assert reply.stop_reason == "max_tokens"
    assert reply.output_tokens == 200


async def test_aclose_closes_the_sdk_client() -> None:
    client, fake = _client("{}")
    await client.aclose()
    assert fake.closed


def test_record_cost_uses_the_model_price() -> None:
    cost = CostTracker(max_usd=None)
    record_cost(cost, "opus", ModelReply(None, "", 1_000_000, 0, "end_turn"))
    record_cost(cost, "sonnet", ModelReply(None, "", 0, 1_000_000, "end_turn"))
    assert round(cost.total_usd, 2) == 15.00


def test_opus55_is_a_model_with_its_own_price() -> None:
    """R3 (2026-10-07): Opus 5.5 as an option next to Opus 5, at $4 / $20 per Mtok."""
    assert MODELS["opus55"] == "claude-opus-5-5"
    cost = CostTracker(max_usd=None)
    record_cost(cost, "opus55", ModelReply(None, "", 1_000_000, 1_000_000, "end_turn"))
    assert round(cost.total_usd, 2) == 24.00


def test_every_model_key_has_a_price() -> None:
    assert set(COST_KEYS) == set(MODELS)


def test_the_cli_model_keys_are_the_models() -> None:
    from birdy_fetcher.web.defaults import MODEL_KEYS

    assert set(MODEL_KEYS) == set(MODELS)
