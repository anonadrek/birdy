from __future__ import annotations

import json
import sys
from pathlib import Path

import click


@click.group()
def main() -> None:
    """Birdy ML eval - measure TFLite model accuracy against a labeled corpus."""


@main.command()
@click.option(
    "--model",
    required=True,
    type=click.Path(exists=True, path_type=Path),
    help="Path to the .tflite model file.",
)
@click.option(
    "--corpus",
    required=True,
    type=click.Path(exists=True, path_type=Path),
    help="Path to corpus/manifest.yaml.",
)
@click.option(
    "--mapping",
    required=True,
    type=click.Path(exists=True, path_type=Path),
    help="Path to ModelMapping.kt (or a JSON model-index-to-QID map).",
)
@click.option(
    "--metadata",
    required=True,
    type=click.Path(exists=True, path_type=Path),
    help="Path to the TFLite model metadata JSON.",
)
@click.option(
    "--out",
    default="report.md",
    show_default=True,
    type=click.Path(path_type=Path),
    help="Output path for the generated Markdown report.",
)
def run(
    model: Path,
    corpus: Path,
    mapping: Path,
    metadata: Path,
    out: Path,
) -> None:
    """Run evaluation and write a Markdown report to --out."""
    # Heavy imports are lazy so --help is fast.
    import tensorflow as tf  # type: ignore[import-untyped]

    from birdy_eval.corpus import load_corpus
    from birdy_eval.metrics import compute_metrics
    from birdy_eval.report import render_report
    from birdy_eval.runner import Predictor

    click.echo(f"Loading corpus from {corpus} …")
    items = load_corpus(corpus)
    if not items:
        click.echo("Corpus is empty — nothing to evaluate.", err=True)
        sys.exit(1)

    click.echo(f"Loading model from {model} …")
    interpreter = tf.lite.Interpreter(model_path=str(model))
    interpreter.allocate_tensors()
    predictor = Predictor(interpreter=interpreter)

    click.echo(f"Loading model mapping from {mapping} …")
    index_to_qid = _load_mapping(mapping)

    click.echo(f"Running inference on {len(items)} images …")
    predictions = [predictor.predict(item) for item in items]

    click.echo("Computing metrics …")
    result = compute_metrics(predictions, index_to_qid)

    click.echo("Rendering report …")
    report = render_report(result, model_path=model.name, corpus_size=len(items))
    out.write_text(report, encoding="utf-8", newline="\n")

    click.echo(f"Report written to {out}")
    click.echo(
        f"Top-1: {result.top1_accuracy:.1%}  Top-3: {result.top3_accuracy:.1%}"
        f"  ({result.total} images)"
    )


def _load_mapping(path: Path) -> dict[int, str]:
    """Load model-index to QID mapping from a JSON file or Kotlin source.

    If *path* ends with ``.json``, it is parsed directly as
    ``{"0": "Q18009", ...}``.  Otherwise the file is scanned for lines
    matching ``index to "Q<digits>"`` (Kotlin map literal pattern).

    Args:
        path: Path to the mapping file.

    Returns:
        Dict mapping int index to QID string.
    """
    text = path.read_text(encoding="utf-8")
    if path.suffix == ".json":
        raw: dict[str, str] = json.loads(text)
        return {int(k): v for k, v in raw.items()}

    # Parse Kotlin: `0 to "Q18009"` or `0 to "Q18009",`
    import re

    mapping: dict[int, str] = {}
    for m in re.finditer(r"(\d+)\s+to\s+\"(Q\d+)\"", text):
        mapping[int(m.group(1))] = m.group(2)
    return mapping


if __name__ == "__main__":
    main()
