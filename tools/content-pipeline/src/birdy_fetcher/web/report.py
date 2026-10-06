"""Markdown report for a web run: tools/content-pipeline/reports/web-<date>-<time>.md."""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path


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
    outcomes: list[SpeciesOutcome], *, cost_usd: float, model_id: str, effort: str, date: str
) -> str:
    counts = Counter(o.status for o in outcomes)
    lines = [
        f"# Webbtexter {date}",
        "",
        f"Modell: `{model_id}` (effort: {effort}). Kostnad för körningen: ${cost_usd:.2f}.",
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


STEP_STATUSES = ("ok", "pending", "failed", "skipped", "dry-run")


@dataclass(frozen=True)
class StepOutcome:
    qid: str
    name: str
    status: str
    errors: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)
    # True when this outcome is something the caller's own scope tried to do (review fix
    # 2026-10-06): `waves.py`'s `publish_wave` sets this on a comparison it tried to
    # newly publish (both sides named with --species), so the CLI's exit code can count
    # it without a note-text marker. A new field with a default so every existing
    # positional `StepOutcome(...)` call site is unaffected.
    attempted: bool = False


def render_step_report(
    *,
    title: str,
    date: str,
    outcomes: list[StepOutcome],
    cost_usd: float | None = None,
    model_line: str | None = None,
) -> str:
    counts = Counter(o.status for o in outcomes)
    lines = [f"# {title} {date}", ""]
    if model_line:
        lines += [model_line, ""]
    if cost_usd is not None:
        lines += [f"Kostnad för körningen: ${cost_usd:.2f}.", ""]
    lines += ["| Utfall | Antal |", "|---|---|"]
    lines += [f"| {s} | {counts[s]} |" for s in STEP_STATUSES if counts[s]]
    lines.append("")
    for heading, status in (("Misslyckades", "failed"), ("Hoppades över", "skipped")):
        rows = [o for o in outcomes if o.status == status]
        lines += [f"## {heading}", ""]
        lines += [f"- **{o.name} ({o.qid})**: {'; '.join(o.errors)}" for o in rows] or ["Inga."]
        lines.append("")
    noted = [o for o in outcomes if o.notes]
    lines += ["## Anteckningar", ""]
    lines += [f"- **{o.name} ({o.qid})**: {'; '.join(o.notes)}" for o in noted] or ["Inga."]
    return "\n".join(lines) + "\n"


def write_step_report(reports_dir: Path, step: str, now: datetime, text: str) -> Path:
    reports_dir.mkdir(parents=True, exist_ok=True)
    path = reports_dir / f"web-{step}-{now:%Y-%m-%d-%H%M%S}.md"
    path.write_text(text, encoding="utf-8")
    return path
