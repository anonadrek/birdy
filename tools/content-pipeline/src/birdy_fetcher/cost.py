"""Cost tracker for Claude API calls. Aborts if --max-cost exceeded."""

from __future__ import annotations

from dataclasses import dataclass

# Anthropic published pricing (2026-01) — $/1M tokens. Update yearly.
_PRICING = {
    "haiku": {"input": 0.80, "output": 4.00},     # claude-haiku-4-5
    "sonnet": {"input": 3.00, "output": 15.00},   # claude-sonnet-4-6
}


class MaxCostExceeded(RuntimeError):  # noqa: N818
    pass


@dataclass
class CostTracker:
    max_usd: float | None
    total_usd: float = 0.0
    call_count: int = 0

    def record(self, *, model: str, input_tokens: int, output_tokens: int) -> None:
        prices = _PRICING[model]
        cost = (input_tokens / 1_000_000) * prices["input"] + (
            output_tokens / 1_000_000
        ) * prices["output"]
        self.total_usd += cost
        self.call_count += 1
        if self.max_usd is not None and self.total_usd > self.max_usd:
            raise MaxCostExceeded(
                f"Cost ${self.total_usd:.4f} exceeds cap ${self.max_usd:.4f} "
                f"after {self.call_count} calls"
            )
