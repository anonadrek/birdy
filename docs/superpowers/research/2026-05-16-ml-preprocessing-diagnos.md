# ML preprocessing Phase 1 — root-cause diagnostic

**Plan:** 6b1 (Billing v8 + launch-prep), Task T2.
**Date opened:** 2026-05-16.
**Status:** Device + desktop data PENDING — DiagnosticsScreen is shipped in commit; user will run on SM-S918B in next session.

## Why this exists

Field-photo top-1 hit-rate has been observed at ~10% in real-world use, while the
desktop ml-eval corpus run (`tools/ml-eval/accuracy_report_2026-05-08.md`) reports
top-1=52% / top-3=72% over 25 corpus photos. Closing that gap is the gate-decision
for whether Plan 6b1 ships before launch (`v0.9.0a-billing` → `v1.0.0`) or whether
ML preprocessing fixes are deferred to a Phase 2.

Phase 1's goal is a **diagnostic-only** pass: confirm whether the on-device
preprocessing path (CameraX YUV → NV21 → Bitmap → 224×224 uint8) produces the same
intermediate tensor as the desktop pipeline (Pillow → numpy → 224×224 uint8). If
they diverge — and especially if mid-row ARGB samples disagree — that points to
the root cause being **preprocessing parity**, not model quality.

## Method

The DEBUG-only `DiagnosticsScreen` (gated via `AppGraph.diagnosticsScreen` lambda,
parallel to existing `benchmarkScreen` from Plan 4b) runs three corpus images
through the live `BirdClassifier` + `ImagePreprocessor` pipeline and dumps:

1. JPEG byte count + decoded Bitmap dimensions + `Bitmap.Config`.
2. Mid-row ARGB pixel samples at 8 evenly-spaced x positions (A/R/G/B per channel).
3. Top-5 predictions sorted by confidence (3-decimal precision).

The same three images are then run through `tools/ml-eval/` on the desktop via
the `dump-mid-row` subcommand (added 2026-05-16), which emits a line-by-line
diffable report in the same format as `DiagnosticsRunner`:

```bash
cd tools/ml-eval
uv run python -m birdy_eval dump-mid-row \
  --model ../../shared/ml/src/commonMain/composeResources/files/ml/aiy_birds_v1.tflite \
  --mapping ../../shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json \
  --photos-dir ../../composeApp/src/androidMain/assets/benchmark/ \
  --out ../../docs/superpowers/research/2026-05-16-desktop-dump.txt
```

**Corpus images** (committed in `composeApp/src/androidMain/assets/benchmark/`):

- `talgoxe.jpg` — paridae (Great Tit), Q25435 — high contrast, yellow belly.
- `koltrast.jpg` — turdidae (Common Blackbird), Q25435-adjacent — solid dark body.
- `blames.jpg` — paridae (Blue Tit), Q25435-adjacent — small + colorful.

These were chosen for Plan 4b because they cover three families and three
confidence regimes; they are the same images used by `BenchmarkRunner`, which
keeps Plan 4b benchmarks comparable.

## Results

> **PENDING — device run.**
>
> Once the user runs DiagnosticsScreen on SM-S918B (Galaxy S23 Ultra), the dump
> will be pulled via `adb shell run-as se.birdy.android cat
> files/preprocess_dump_*.txt` and pasted into this section. The desktop run uses
> `uv run tools/ml-eval/eval.py --dump-mid-row` (TODO add flag).

### Device output

```
(paste preprocess_dump_*.txt here)
```

### Desktop output

```
(paste tools/ml-eval/ output here)
```

### Diff

| Image | Channel | Device samples (8x) | Desktop samples (8x) | Δ |
|---|---|---|---|---|
| talgoxe.jpg | R | TBD | TBD | TBD |
| talgoxe.jpg | G | TBD | TBD | TBD |
| talgoxe.jpg | B | TBD | TBD | TBD |
| koltrast.jpg | R | TBD | TBD | TBD |
| ... | ... | ... | ... | ... |

| Image | Device top-5 | Desktop top-5 | Top-1 match? |
|---|---|---|---|
| talgoxe.jpg | TBD | TBD | TBD |
| koltrast.jpg | TBD | TBD | TBD |
| blames.jpg | TBD | TBD | TBD |

## Root-cause hypothesis

> **PENDING — fill in after data.**

Candidate hypotheses (ranked by likelihood from offline reasoning):

1. **Colorspace channel order (RGB ↔ BGR).** Most TFLite Android inference bugs
   in the wild trace to this. The `ImagePreprocessor` Android-actual converts NV21
   → ARGB via `BitmapFactory`, but the AIY V1 model expects RGB and the desktop
   pipeline uses Pillow's RGB ordering. If the on-device path is silently passing
   BGR uint8 tensors, every prediction is being trained on the wrong channel
   permutation and we'd expect random-ish top-5 lists that still occasionally hit
   on shape-dominated species (which matches the ~10% field rate).
2. **Resize aspect-ratio / crop strategy mismatch.** Pillow defaults to
   bilinear without aspect-preservation. CameraX's typical path is letterbox or
   center-crop. If desktop center-crops while device letterboxes (or vice versa),
   the model sees different views of the same bird.
3. **Quantization rounding drift.** uint8 quantization via `roundToInt` (Plan 4b
   trap-catalog entry) vs Pillow's float→uint8 cast. Should be small (≤1 LSB),
   probably not enough to drop top-1 by 40 pp.
4. **Rotation handling.** CameraX surfaces `rotationDegrees` separately; if
   `ImagePreprocessor` doesn't apply it before resize, portrait photos become
   90°-rotated tensors. Field photos are predominantly portrait; corpus photos
   (Wikimedia heroes) are predominantly landscape. This could explain the
   asymmetric gap.

## Fix effort estimate

> **PENDING — depends on which hypothesis is confirmed.**

Per-hypothesis rough estimates (T-shirt sizing):

- **H1 (RGB/BGR):** S — one-line fix in `ImagePreprocessor.preprocess`. ½ day
  including device-verify.
- **H2 (resize/crop):** M — needs aspect-preserving letterbox or matching
  center-crop on both sides. 1-2 days.
- **H3 (quantization):** S — fix is trivial but the symptom is unlikely to
  improve top-1 by enough to gate the launch. ½ day.
- **H4 (rotation):** M — needs CameraX rotation hookup + portrait-corpus
  re-baseline. 1-2 days.

## Recommendation (T8 gate-decision)

> **PENDING — fill after data + hypothesis confirmation.**

Decision frame:

- **SHIP Phase 1 fix pre-launch** if hypothesis is S (H1 or H3) AND device-rerun
  shows ≥20 pp top-1 improvement on the corpus.
- **DEFER to Phase 2** if hypothesis is M (H2 or H4) and would require >2 dev-days
  + content re-baseline (T0.1 launch window: 2026-05-18 Closed Testing → 2026-06-01
  prod release; H2/H4 fixes risk slipping that window).
- **CONTINUE diagnostic** if device + desktop dumps disagree but no single
  hypothesis explains both the colorspace AND top-5 drift — implies multiple
  preprocessing bugs.

## Next actions

1. **User runs DiagnosticsScreen on SM-S918B.** Build `:androidApp:installDebug`,
   navigate Archive → overflow → "ML diagnos" → Run diagnostic; wait ~5s; back-out.
2. **Pull dump:** `adb shell run-as se.birdy.android cat
   files/preprocess_dump_*.txt > diagnos_device.txt`.
3. ~~**Add `--dump-mid-row` flag** to `tools/ml-eval/eval.py`~~ **DONE 2026-05-16**
   — see Method section for the exact `uv run python -m birdy_eval dump-mid-row` command.
4. **Paste both dumps** into Results section above + run the diff table.
5. **Make T8 gate-decision** based on Recommendation logic.
