from __future__ import annotations

from datetime import UTC, datetime

from birdy_eval.metrics import EvalResult


def render_report(
    result: EvalResult,
    *,
    model_path: str,
    corpus_size: int,
    generated_at: str | None = None,
) -> str:
    """Render a Markdown evaluation report from *result*.

    The output is deterministic for a given *result* so it can be
    golden-tested.  Pass *generated_at* to pin the timestamp in tests.

    Args:
        result: Computed :class:`~birdy_eval.metrics.EvalResult`.
        model_path: Path or name of the TFLite model file (display only).
        corpus_size: Total number of images in the corpus.
        generated_at: Optional UTC ISO-8601 timestamp override (for testing).

    Returns:
        Markdown string ready to write to a file.
    """
    now = generated_at or datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
    lines: list[str] = []

    # --- Header ---
    lines += [
        "# Birdy ML Eval Report",
        "",
        f"**Generated:** {now}  ",
        f"**Model:** `{model_path}`  ",
        f"**Corpus size:** {corpus_size} images  ",
        "",
    ]

    # --- Overall accuracy ---
    lines += [
        "## Overall Accuracy",
        "",
        "| Metric | Value |",
        "|--------|-------|",
        f"| Top-1 accuracy | {result.top1_accuracy:.1%} |",
        f"| Top-3 accuracy | {result.top3_accuracy:.1%} |",
        f"| Total evaluated | {result.total} |",
        "",
    ]

    # --- Per-family breakdown ---
    lines += [
        "## Per-Family Breakdown",
        "",
        "| Family | N | Top-1 | Top-3 |",
        "|--------|---|-------|-------|",
    ]
    for fam in sorted(result.per_family, key=lambda f: f.family):
        lines.append(
            f"| {fam.family} | {fam.total}"
            f" | {fam.top1_accuracy:.1%}"
            f" | {fam.top3_accuracy:.1%} |"
        )
    lines.append("")

    # --- Threshold sweep ---
    lines += [
        "## Confidence Threshold Sweep",
        "",
        "| Threshold | Coverage | Top-1 (covered) |",
        "|-----------|----------|-----------------|",
    ]
    for pt in result.threshold_sweep:
        lines.append(
            f"| {pt.threshold:.2f}"
            f" | {pt.coverage:.1%}"
            f" | {pt.top1_accuracy:.1%} |"
        )
    lines.append("")

    return "\n".join(lines)
