# birdy-eval — ML eval pipeline

Measures TFLite model accuracy against a labeled photo corpus.

## Purpose

Runs the AIY Birds V1 TFLite model over `corpus/manifest.yaml`, computes top-1 / top-3
accuracy, per-family breakdown, and a confidence-threshold sweep, then renders a
Markdown report.

## Setup

```bash
cd tools/ml-eval
uv sync --all-extras
```

## Run

```bash
uv run birdy-eval run \
  --model ../../shared/ml/src/commonMain/composeResources/files/aiy_birds_V1_3.tflite \
  --corpus corpus/manifest.yaml \
  --mapping ../../shared/ml/src/commonMain/kotlin/se/birdy/ml/ModelMapping.kt \
  --metadata ../../shared/ml/src/commonMain/composeResources/files/aiy_birds_V1_3_metadata.json \
  --out report.md
```

## Tests

```bash
uv run pytest -v
```
