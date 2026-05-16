from __future__ import annotations

import json
import sys
import time
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
    help="aiy_to_qid.json (model-index → QID, nested or flat).",
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
    out: Path,
) -> None:
    """Run evaluation and write a Markdown report to --out."""
    # Heavy imports are lazy so --help is fast.
    from ai_edge_litert.interpreter import Interpreter  # type: ignore[import-untyped]

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
    interpreter = Interpreter(model_path=str(model))
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


@main.command("dump-mid-row")
@click.option(
    "--model",
    required=True,
    type=click.Path(exists=True, path_type=Path),
    help="Path to the .tflite model file.",
)
@click.option(
    "--mapping",
    required=True,
    type=click.Path(exists=True, path_type=Path),
    help="aiy_to_qid.json (model-index to QID, nested or flat).",
)
@click.option(
    "--photos-dir",
    required=True,
    type=click.Path(exists=True, file_okay=False, path_type=Path),
    help="Corpus JPEGs dir (e.g. composeApp/src/androidMain/assets/benchmark/).",
)
@click.option(
    "--photos",
    default="talgoxe.jpg,koltrast.jpg,blames.jpg",
    show_default=True,
    help="Comma-separated list of photo filenames inside --photos-dir.",
)
@click.option(
    "--out",
    default="-",
    show_default=True,
    type=click.Path(path_type=Path, dir_okay=False),
    help="Output path, or '-' for stdout.",
)
def dump_mid_row(
    model: Path,
    mapping: Path,
    photos_dir: Path,
    photos: str,
    out: Path,
) -> None:
    """Emit preprocess-parity diagnostic matching the on-device DiagnosticsRunner format.

    For each photo: print decoded dimensions, 4 corner ARGB samples, 8 mid-row
    ARGB samples, and top-5 predictions (mapped to QIDs). Output is line-by-line
    diffable against `preprocess_dump_*.txt` produced by DiagnosticsScreen on
    SM-S918B. See docs/superpowers/research/2026-05-16-ml-preprocessing-diagnos.md.
    """
    # Heavy imports are lazy so --help is fast.
    from typing import Any

    import numpy as np
    import PIL
    from ai_edge_litert.interpreter import Interpreter  # type: ignore[import-untyped,unused-ignore]
    from PIL import Image

    photo_list = [p.strip() for p in photos.split(",") if p.strip()]
    if not photo_list:
        click.echo("No photos specified.", err=True)
        sys.exit(1)

    index_to_qid = _load_mapping(mapping)

    interpreter = Interpreter(model_path=str(model))
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()[0]
    output_details = interpreter.get_output_details()[0]
    input_index = input_details["index"]
    output_index = output_details["index"]
    input_dtype = input_details["dtype"]
    output_dtype = output_details["dtype"]
    input_shape = input_details["shape"]  # e.g. [1, 224, 224, 3]
    target_h = int(input_shape[1])
    target_w = int(input_shape[2])

    lines: list[str] = []
    lines.append("Birdy ML preprocess diagnostic (desktop ml-eval)")
    lines.append(f"runtime=ai_edge_litert pillow={PIL.__version__} numpy={np.__version__}")
    lines.append(f"ts={int(time.time() * 1000)}")
    lines.append(f"photos={', '.join(photo_list)}")
    lines.append("---")

    for photo in photo_list:
        path = photos_dir / photo
        lines.append("")
        lines.append(f"=== {photo} ===")
        if not path.exists():
            lines.append(f"ERROR: file not found: {path}")
            continue
        jpeg_bytes = path.stat().st_size
        lines.append(f"jpeg_bytes={jpeg_bytes}")

        try:
            img = Image.open(path).convert("RGB")
        except Exception as exc:
            lines.append(f"ERROR: failed to decode {photo}: {exc}")
            continue

        w, h = img.size
        lines.append(f"bitmap={w}x{h} config=PIL.RGB")

        # PIL RGB-mode images have no alpha channel; print A=255 to mirror the
        # Android BitmapFactory.decodeByteArray default of opaque alpha so the
        # ARGB lines remain line-by-line diffable.
        def fmt_argb(rgb: tuple[int, int, int]) -> str:
            r, g, b = rgb
            return f"A=255,R={r},G={g},B={b}"

        # Convert image to numpy once; getpixel returns a Union the type-checker
        # can't narrow on RGB-mode images, so we index the array instead.
        pixels = np.array(img, dtype=np.uint8)  # shape (h, w, 3)

        def at(arr: np.ndarray[Any, Any], x: int, y: int) -> tuple[int, int, int]:
            r, g, b = arr[y, x]
            return int(r), int(g), int(b)

        lines.append(
            f"corner_argb TL=[{fmt_argb(at(pixels, 0, 0))}] "
            f"TR=[{fmt_argb(at(pixels, w - 1, 0))}] "
            f"BL=[{fmt_argb(at(pixels, 0, h - 1))}] "
            f"BR=[{fmt_argb(at(pixels, w - 1, h - 1))}]"
        )

        mid_y = h // 2
        samples: list[str] = []
        for i in range(8):
            x = (w * i) // 8
            samples.append(f"x={x}:[{fmt_argb(at(pixels, x, mid_y))}]")
        lines.append(f"mid_row_y={mid_y} samples={' '.join(samples)}")

        # Run inference: resize, build tensor, invoke.
        resized = img.resize((target_w, target_h))
        tensor: Any
        if input_dtype == np.uint8:
            tensor = np.array(resized, dtype=np.uint8)[np.newaxis, ...]
        else:
            tensor = (np.array(resized, dtype=np.float32) / 255.0)[np.newaxis, ...]
        interpreter.set_tensor(input_index, tensor)
        interpreter.invoke()
        scores = interpreter.get_tensor(output_index)[0]
        if output_dtype == np.uint8:
            scale, zero_point = output_details["quantization"]
            scores = (scores.astype(np.float32) - zero_point) * scale

        top_indices = np.argsort(scores)[::-1][:5]
        top5_parts: list[str] = []
        for idx in top_indices:
            qid = index_to_qid.get(int(idx), f"idx{int(idx)}")
            top5_parts.append(f"{qid}:{float(scores[int(idx)]):.3f}")
        lines.append(f"top5={', '.join(top5_parts)}")

    lines.append("")
    lines.append("---")
    lines.append("end")
    report = "\n".join(lines) + "\n"

    if str(out) == "-":
        click.echo(report, nl=False)
    else:
        out.write_text(report, encoding="utf-8", newline="\n")
        click.echo(f"Dump written to {out}")


def _load_mapping(path: Path) -> dict[int, str]:
    """Load model-index to QID mapping from a JSON file.

    Accepts both nested shape ``{"_meta": {...}, "mappings": {"0": "Q..."}}``
    and flat shape ``{"0": "Q..."}``.

    Args:
        path: Path to the JSON mapping file.

    Returns:
        Dict mapping int index to QID string.
    """
    text = path.read_text(encoding="utf-8")
    data: dict[str, object] = json.loads(text)
    if "_meta" in data:
        raw: dict[str, str] = data["mappings"]  # type: ignore[assignment]
    else:
        raw = data  # type: ignore[assignment]
    return {int(k): v for k, v in raw.items()}


if __name__ == "__main__":
    main()
