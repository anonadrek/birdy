"""A fake JsonModelClient that answers from a queue and records what it was asked."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast

from anthropic.types import MessageParam
from pydantic import BaseModel

from birdy_fetcher.web.llm import MAX_TOKENS, ModelReply


def reply(parsed: BaseModel | None, stop: str = "end_turn") -> ModelReply[Any]:
    raw = parsed.model_dump_json() if parsed is not None else ""
    return ModelReply(parsed, raw, 1000, 500, stop)


@dataclass
class FakeJsonClient:
    replies: list[ModelReply[Any]]
    calls: list[list[MessageParam]] = field(default_factory=list)
    schemas: list[str] = field(default_factory=list)
    models: list[str] = field(default_factory=list)
    efforts: list[str] = field(default_factory=list)
    closed: bool = False

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
        self.calls.append(list(messages))
        self.schemas.append(schema.__name__)
        self.models.append(model)
        self.efforts.append(effort)
        return cast(ModelReply[M], self.replies.pop(0))

    async def aclose(self) -> None:
        self.closed = True
