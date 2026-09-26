"""Markdown report for a web run: tools/content-pipeline/reports/web-<date>.md."""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass


@dataclass(frozen=True)
class SpeciesOutcome:
    qid: str
    name_sv: str
    status: str  # ok | failed | skipped | dry-run
    errors: list[str]
    dropped_facts: list[str]
    attempts: int
    from_cache: bool


def render_report(
    outcomes: list[SpeciesOutcome], *, cost_usd: float, model_id: str, date: str
) -> str:
    counts = Counter(o.status for o in outcomes)
    lines = [
        f"# Webbtexter {date}",
        "",
        f"Modell: `{model_id}`. Kostnad för körningen: ${cost_usd:.2f}.",
        "",
        "| Utfall | Antal |",
        "|---|---|",
        *(f"| {status} | {counts[status]} |" for status in ("ok", "failed", "skipped", "dry-run")),
        "",
        f"Två försök: {sum(1 for o in outcomes if o.attempts == 2)}. "
        f"Från cache: {sum(1 for o in outcomes if o.from_cache)}.",
        "",
        "## Arter som inte fick någon sida",
        "",
    ]
    failed = [o for o in outcomes if o.status == "failed"]
    lines += [f"- **{o.name_sv} ({o.qid})**: {'; '.join(o.errors)}" for o in failed] or ["Inga."]
    lines += ["", "## Faktauppgifter som ströks", ""]
    dropped = [o for o in outcomes if o.dropped_facts]
    lines += [f"- **{o.name_sv} ({o.qid})**: {'; '.join(o.dropped_facts)}" for o in dropped] or [
        "Inga."
    ]
    skipped = [o for o in outcomes if o.status == "skipped"]
    lines += ["", "## Hoppades över", ""]
    lines += [f"- {o.name_sv} ({o.qid}): {'; '.join(o.errors)}" for o in skipped] or ["Inga."]
    return "\n".join(lines) + "\n"
