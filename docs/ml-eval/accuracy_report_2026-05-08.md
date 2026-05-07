# Birdy ML Eval Report

**Generated:** 2026-05-07T23:52:27Z  
**Model:** `aiy_birds_v1.tflite`  
**Corpus size:** 25 images  

## Overall Accuracy

| Metric | Value |
|--------|-------|
| Top-1 accuracy | 52.0% |
| Top-3 accuracy | 72.0% |
| Total evaluated | 25 |

## Per-Family Breakdown

| Family | N | Top-1 | Top-3 |
|--------|---|-------|-------|
| accipitridae | 4 | 25.0% | 25.0% |
| aegithalidae | 1 | 0.0% | 100.0% |
| alaudidae | 1 | 100.0% | 100.0% |
| alcedinidae | 1 | 100.0% | 100.0% |
| alcidae | 3 | 33.3% | 66.7% |
| anatidae | 5 | 60.0% | 60.0% |
| apodidae | 1 | 100.0% | 100.0% |
| ardeidae | 1 | 100.0% | 100.0% |
| bombycillidae | 1 | 0.0% | 100.0% |
| calcariidae | 2 | 50.0% | 100.0% |
| falconidae | 1 | 0.0% | 0.0% |
| paridae | 3 | 66.7% | 100.0% |
| turdidae | 1 | 100.0% | 100.0% |

## Confidence Threshold Sweep

| Threshold | Coverage | Top-1 (covered) |
|-----------|----------|-----------------|
| 0.00 | 100.0% | 52.0% |
| 0.05 | 100.0% | 52.0% |
| 0.10 | 100.0% | 52.0% |
| 0.15 | 100.0% | 52.0% |
| 0.20 | 100.0% | 52.0% |
| 0.25 | 100.0% | 52.0% |
| 0.30 | 100.0% | 52.0% |
| 0.35 | 100.0% | 52.0% |
| 0.40 | 100.0% | 52.0% |
| 0.45 | 100.0% | 52.0% |
| 0.50 | 100.0% | 52.0% |
| 0.55 | 100.0% | 52.0% |
| 0.60 | 100.0% | 52.0% |
| 0.65 | 100.0% | 52.0% |
| 0.70 | 100.0% | 52.0% |
| 0.75 | 100.0% | 52.0% |
| 0.80 | 100.0% | 52.0% |
| 0.85 | 100.0% | 52.0% |
| 0.90 | 100.0% | 52.0% |
| 0.95 | 100.0% | 52.0% |

## Methodology + caveats

### Corpus source

Images were taken from `shared/content/images/<qid>/hero.jpg` — Wikimedia Commons
photos curated by Plan 2b's content-pipeline. These photos are already license-cleared
(CC BY / CC BY-SA) and are the same hero images displayed in the app's encyclopedia.

### Selection criteria

Species were included if all of the following held:

1. **In model** — QID present in `aiy_to_qid.json`'s `mappings` dict (i.e., AIY V1 can predict the species).
2. **Swedish common species** — `abundance: allmän` in the YAML and `SE` in the `regions` list.
3. **Has a hero photo** — `shared/content/images/<qid>/hero.jpg` exists.

From 35 qualifying candidates across 13 families, 25 were selected by round-robin across
families (alphabetical by QID within each family) for deterministic, reproducible results.
The selection algorithm lives in `tools/ml-eval/scripts/build_corpus.py` and can be re-run
as Plan 2b adds more species families.

### Training-data overlap bias

The AIY Birds V1 model was trained on images drawn partly from Wikimedia Commons. The hero
photos in this corpus come from the same source and **very likely overlap with the model's
training set**. Accuracy measured here is therefore biased **upward** compared to what the
model would achieve on unseen field photos taken in the wild.

Real-world performance (hand-held photos, variable lighting, partial occlusion) will likely
score 10–25 percentage points lower on top-3 accuracy. A future eval using GBIF observation
photos or user-contributed field shots would give a more realistic estimate.

### Reproduction command

```bash
# From repo root:
cd tools/ml-eval
uv run birdy-eval run \
  --model ../../shared/ml/src/commonMain/composeResources/files/ml/aiy_birds_v1.tflite \
  --corpus corpus/manifest.yaml \
  --mapping ../../shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json \
  --out ../../docs/ml-eval/accuracy_report_2026-05-08.md
```

To rebuild the corpus from scratch (e.g. after Plan 2b adds more families):

```bash
cd tools/ml-eval
uv run python scripts/build_corpus.py
```
