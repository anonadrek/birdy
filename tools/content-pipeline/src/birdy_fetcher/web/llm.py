"""Claude calls that answer with JSON matching a pydantic model (spec §9). Uses
`messages.create` with a JSON-schema output format, not the SDK's `.parse()`: in anthropic
0.97 `.parse()` raises inside its own post-parser when a reply is cut off at max_tokens or
refused, which throws away the paid usage and the real stop_reason. We validate ourselves."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Protocol

from anthropic import AsyncAnthropic, transform_schema
from anthropic.types import JSONOutputFormatParam, MessageParam, OutputConfigParam
from pydantic import BaseModel, ValidationError

from ..cost import CostTracker

# The keys the CLI offers are defaults.MODEL_KEYS (kept apart so --help needs no SDK).
MODELS = {"opus": "claude-opus-5", "opus55": "claude-opus-5-5", "sonnet": "claude-sonnet-5"}
COST_KEYS = {"opus": "opus5", "opus55": "opus55", "sonnet": "sonnet5"}
MAX_TOKENS = 16_000


@dataclass
class ModelReply[M: BaseModel]:
    parsed: M | None
    raw_text: str
    input_tokens: int
    output_tokens: int
    stop_reason: str | None


class JsonModelClient(Protocol):
    async def complete[M: BaseModel](
        self,
        *,
        model: str,
        system: str,
        messages: list[MessageParam],
        effort: str,
        schema: type[M],
        max_tokens: int = MAX_TOKENS,
    ) -> ModelReply[M]: ...


class AnthropicJsonClient:
    """The locked anthropic 0.97 SDK reads ANTHROPIC_API_KEY or ANTHROPIC_AUTH_TOKEN from the
    environment; there is no `ant auth login` profile support. Retries are turned up because
    180 species means 429/529 answers are routine."""

    def __init__(self, client: AsyncAnthropic | None = None) -> None:
        self._client = client or AsyncAnthropic(max_retries=5)

    async def complete[M: BaseModel](
        self,
        *,
        model: str,
        system: str,
        messages: list[MessageParam],
        effort: str,
        schema: type[M],
        max_tokens: int = MAX_TOKENS,
    ) -> ModelReply[M]:
        output_format: JSONOutputFormatParam = {
            "type": "json_schema",
            "schema": transform_schema(schema),
        }
        msg = await self._client.messages.create(
            model=model,
            max_tokens=max_tokens,
            system=system,
            messages=messages,
            # `effort` is a plain str in our code; the CLI's click.Choice limits it to values
            # the SDK's Literal accepts.
            output_config=OutputConfigParam(format=output_format, effort=effort),  # type: ignore[typeddict-item]
        )
        text = "".join(block.text for block in msg.content if block.type == "text")
        try:
            parsed: M | None = schema.model_validate_json(text)
        except ValidationError:
            parsed = None
        return ModelReply(
            parsed, text, msg.usage.input_tokens, msg.usage.output_tokens, msg.stop_reason
        )

    async def aclose(self) -> None:
        await self._client.close()


def record_cost(cost: CostTracker, model_key: str, reply: ModelReply[Any]) -> None:
    """Raises MaxCostExceeded when the cap is passed; the reply is already paid for then."""
    cost.record(
        model=COST_KEYS[model_key],
        input_tokens=reply.input_tokens,
        output_tokens=reply.output_tokens,
    )
