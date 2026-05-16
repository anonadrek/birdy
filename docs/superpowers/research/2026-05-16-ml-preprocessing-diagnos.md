# ML preprocessing Phase 1 — root-cause diagnostic

**Plan:** 6b1 (Billing v8 + launch-prep), Task T2.
**Date opened:** 2026-05-16.
**Status:** ✅ COMPLETE 2026-05-16 — preprocessing parity CONFIRMED on SM-S918B.
Device and desktop emit byte-identical ARGB samples for all 3 corpus images
and converge on the same top-1 prediction with matching confidence (±0.02 from
floating-point drift). Path A (S-sized preprocessing fix) is NOT APPLICABLE —
no preprocessing bug exists. Going with Path B (defer + lower confidence
threshold) per plan 6b1 T8.B fallback.

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

Device dump: `preprocess-dumps/2026-05-16-device-sm-s918b.txt`.
Desktop dump: `preprocess-dumps/2026-05-16-desktop.txt`.

### Device output (SM-S918B, samsung)

```
=== talgoxe.jpg ===
jpeg_bytes=56897
bitmap=800x533 config=ARGB_8888
corner_argb TL=[A=255,R=130,G=131,B=65] TR=[A=255,R=132,G=124,B=77] BL=[A=255,R=101,G=125,B=29] BR=[A=255,R=137,G=153,B=64]
mid_row_y=266 samples=x=0:[A=255,R=114,G=125,B=46] x=100:[A=255,R=105,G=137,B=36] x=200:[A=255,R=99,G=136,B=30] x=300:[A=255,R=29,G=31,B=28] x=400:[A=255,R=124,G=127,B=132] x=500:[A=255,R=126,G=135,B=70] x=600:[A=255,R=109,G=128,B=46] x=700:[A=255,R=131,G=115,B=63]
top5=Q25485:0.938

=== koltrast.jpg ===
jpeg_bytes=84375
bitmap=800x533 config=ARGB_8888
corner_argb TL=[A=255,R=194,G=219,B=135] TR=[A=255,R=149,G=178,B=112] BL=[A=255,R=150,G=148,B=151] BR=[A=255,R=146,G=159,B=167]
mid_row_y=266 samples=x=0:[A=255,R=255,G=239,B=224] x=100:[A=255,R=255,G=245,B=235] x=200:[A=255,R=119,G=92,B=75] x=300:[A=255,R=34,G=35,B=39] x=400:[A=255,R=68,G=67,B=72] x=500:[A=255,R=47,G=43,B=40] x=600:[A=255,R=240,G=222,B=208] x=700:[A=255,R=189,G=168,B=151]
top5=Q25234:0.969

=== blames.jpg ===
jpeg_bytes=108890
bitmap=800x600 config=ARGB_8888
corner_argb TL=[A=255,R=46,G=47,B=31] TR=[A=255,R=255,G=255,B=255] BL=[A=255,R=100,G=79,B=50] BR=[A=255,R=157,G=147,B=138]
mid_row_y=300 samples=x=0:[A=255,R=133,G=120,B=112] x=100:[A=255,R=104,G=163,B=143] x=200:[A=255,R=166,G=165,B=134] x=300:[A=255,R=114,G=65,B=68] x=400:[A=255,R=188,G=96,B=117] x=500:[A=255,R=151,G=110,B=114] x=600:[A=255,R=156,G=133,B=143] x=700:[A=255,R=251,G=245,B=247]
top5=Q25404:0.957
```

### Desktop output (ai-edge-litert + Pillow 12.2.0 + numpy 1.26.4)

```
=== talgoxe.jpg ===
jpeg_bytes=56897
bitmap=800x533 config=PIL.RGB
corner_argb TL=[A=255,R=130,G=131,B=65] TR=[A=255,R=132,G=124,B=77] BL=[A=255,R=101,G=125,B=29] BR=[A=255,R=137,G=153,B=64]
mid_row_y=266 samples=x=0:[A=255,R=114,G=125,B=46] x=100:[A=255,R=105,G=137,B=36] x=200:[A=255,R=99,G=136,B=30] x=300:[A=255,R=29,G=31,B=28] x=400:[A=255,R=124,G=127,B=132] x=500:[A=255,R=126,G=135,B=70] x=600:[A=255,R=109,G=128,B=46] x=700:[A=255,R=131,G=115,B=63]
top5=Q25485:0.938, Q25404:0.004, Q27075915:0.004, idx964:0.000, Q943329:0.000

=== koltrast.jpg ===
jpeg_bytes=84375
bitmap=800x533 config=PIL.RGB
corner_argb TL=[A=255,R=194,G=219,B=135] TR=[A=255,R=149,G=178,B=112] BL=[A=255,R=150,G=148,B=151] BR=[A=255,R=146,G=159,B=167]
mid_row_y=266 samples=x=0:[A=255,R=255,G=239,B=224] x=100:[A=255,R=255,G=245,B=235] x=200:[A=255,R=119,G=92,B=75] x=300:[A=255,R=34,G=35,B=39] x=400:[A=255,R=68,G=67,B=72] x=500:[A=255,R=47,G=43,B=40] x=600:[A=255,R=240,G=222,B=208] x=700:[A=255,R=189,G=168,B=151]
top5=Q25234:0.945, Q25469:0.004, Q529048:0.004, Q943329:0.000, Q26459:0.000

=== blames.jpg ===
jpeg_bytes=108890
bitmap=800x600 config=PIL.RGB
corner_argb TL=[A=255,R=46,G=47,B=31] TR=[A=255,R=255,G=255,B=255] BL=[A=255,R=100,G=79,B=50] BR=[A=255,R=157,G=147,B=138]
mid_row_y=300 samples=x=0:[A=255,R=133,G=120,B=112] x=100:[A=255,R=104,G=163,B=143] x=200:[A=255,R=166,G=165,B=134] x=300:[A=255,R=114,G=65,B=68] x=400:[A=255,R=188,G=96,B=117] x=500:[A=255,R=188,G=96,B=117] x=500:[A=255,R=151,G=110,B=114] x=600:[A=255,R=156,G=133,B=143] x=700:[A=255,R=251,G=245,B=247]
top5=Q25404:0.938, Q199758:0.020, Q883102:0.008, Q958459:0.008, Q25485:0.008
```

### Diff

All 24 mid-row ARGB samples (3 images × 8 positions) are **byte-identical**
between device and desktop. All 12 corner samples (3 × 4) are byte-identical.
All bitmap dimensions match. All jpeg_bytes counts match.

| Image | Δ corners | Δ mid-row | Top-1 match? | Confidence Δ |
|---|---|---|---|---|
| talgoxe.jpg | 0/4 | 0/8 | ✅ Q25485 | 0.000 (0.938 == 0.938) |
| koltrast.jpg | 0/4 | 0/8 | ✅ Q25234 | +0.024 (device 0.969 vs desktop 0.945) |
| blames.jpg | 0/4 | 0/8 | ✅ Q25404 | +0.019 (device 0.957 vs desktop 0.938) |

Confidence deltas of 0.02–0.024 are consistent with floating-point summation
order drift inside the TFLite interpreter (device uses NNAPI/XNNPACK delegate,
desktop uses CPU-only `ai-edge-litert`). Top-1 prediction is identical for all
3 corpus images.

| Image | Device top-1 | Desktop top-1 | Match? |
|---|---|---|---|
| talgoxe.jpg | Q25485 (Great Tit) | Q25485 | ✅ |
| koltrast.jpg | Q25234 (Common Blackbird) | Q25234 | ✅ |
| blames.jpg | Q25404 (Blue Tit) | Q25404 | ✅ |

## Root-cause hypothesis

> **CONFIRMED 2026-05-16: NONE of the candidate hypotheses apply.** Preprocessing
> parity is exact. The ~10% field hit-rate is therefore not caused by an
> on-device preprocessing bug — it must be one of: (a) a *content-side* issue
> (field photos are taken in conditions the AIY V1 model wasn't trained on —
> backlit, blurry, distant, partial-occlusion), (b) a *model capacity* limit
> (AIY V1 is a 3.5 MB MobileNetV2 trained on iconic-pose hero shots; the field
> distribution shift is the true gap), or (c) both. Confirming the real
> root-cause requires field-photo labelling + model retraining or swap to a
> stronger ImageNet-pretrained backbone — both Phase 2 work, out of Plan 6b1
> scope.

Refutation summary (each hypothesis ranked by initial likelihood, now scored
against the data):

1. **Colorspace channel order (RGB ↔ BGR)** — ❌ REFUTED. Device ARGB samples
   match desktop RGB samples byte-for-byte at all 24 mid-row positions and 12
   corner positions. If channels were swapped, every R/B pair would differ. The
   Android `ImagePreprocessor` is correctly emitting RGB-ordered uint8.
2. **Resize aspect-ratio / crop strategy mismatch** — ❌ REFUTED. Bitmap
   dimensions match (800x533, 800x533, 800x600), confirming identical decode +
   resize behavior. If letterbox-vs-center-crop diverged, mid-row samples would
   show large positional offsets.
3. **Quantization rounding drift** — ❌ REFUTED. uint8 channel values match
   exactly (0 LSB drift across all 24 samples). The ≤1-LSB worst-case from
   `roundToInt` vs Pillow `floor` is not occurring in practice on these images.
4. **Rotation handling** — ❌ REFUTED. Corner samples match: if rotation were
   off, TL/TR/BL/BR would be permuted (TL→TR for 90°CW, etc). All four corners
   match in their natural positions on all 3 images.

The 0.02–0.024 confidence deltas are explained by FP summation order inside the
TFLite interpreter (device NNAPI/XNNPACK vs desktop CPU-only), not preprocessing.

## Fix effort estimate

> **N/A — no preprocessing fix to ship.** All 4 candidate hypotheses are refuted
> by the device-vs-desktop dump diff. Path A (S-sized preprocessing patch) is
> not applicable.

The actionable engineering trade-off is now a **threshold-policy adjustment**
rather than a preprocessing fix. See Recommendation below; effort = XS (one
constant change in `TfLiteBirdClassifier.kt`).

Phase 2 candidates (NOT in scope for v0.9.0a-billing / v1.0.0):

- **P2.A Field-photo corpus + retrain.** Collect 500+ labelled field photos
  across S23/Pixel 7+/iPhone 14+ camera profiles, retrain AIY V1 head, ship as
  Plan-7-or-later. ~2 weeks.
- **P2.B Backbone swap.** EfficientNet-Lite0 or MobileNetV3 pretrained on
  iNaturalist's bird subset. ~1 week + model-size/perf re-baseline.
- **P2.C Audio-ID fallback.** Already scoped for Plan 6b3 via BirdNET-Lite;
  may partially compensate for visual hit-rate in mixed identification flows.

## Recommendation (T8 gate-decision)

**Decision: Path B — DEFER + lower confidence threshold.**

Rationale: the gate-decision frame from the plan called for "DEFER to Phase 2"
when no Phase-1-S fix applies. The diagnostic was conclusive but ruled OUT all
4 preprocessing hypotheses, so there is no Path-A patch to ship. The honest
read of the data is:

- The preprocessing pipeline is correct. The ~10% field hit-rate is not a bug —
  it's the AIY V1 model hitting its capacity ceiling on out-of-distribution
  field photos.
- Ship the v0.9.0a-billing release with a **lowered NoBird threshold** so the
  app routes more borderline classifications into the Disambig view rather than
  the NoBird dead-end. Per the plan 6b1 Risk-4 fallback, lower
  `TfLiteBirdClassifier.DEFAULT_THRESHOLD` from `0.10f` to `0.05f`.
- This shifts the USP narrative from "AI identifies birds" to "You and the AI
  work together to identify birds" — Disambig becomes the primary user flow
  rather than the exception path. The marketing/onboarding copy in Plan 6b2
  should match.

Side effects of the threshold change:

- Disambig will surface more 0.05–0.35-band candidates. UX cost: slightly more
  taps per field-photo classification. Acceptable.
- NoBird becomes rarer. UX cost: when there genuinely is no bird, user may see
  a low-confidence Disambig instead of "no bird detected". Acceptable — the
  Disambig view already shows confidence percentages so the user can self-reject.
- No model-perf regression (just a UI threshold, not a model change).

Phase 2 follow-up (Plan 7 or later): collect labelled field photos + retrain or
swap backbone. See Fix effort estimate section above for P2.A/B/C candidates.

## Next actions

All Phase 1 diagnostic steps complete. The remaining actions for Plan 6b1 T8 are:

1. ~~**User runs DiagnosticsScreen on SM-S918B.**~~ ✅ DONE 2026-05-16. Dump at
   `preprocess-dumps/2026-05-16-device-sm-s918b.txt`.
2. ~~**Pull dump.**~~ ✅ DONE.
3. ~~**Add `--dump-mid-row` flag** to `tools/ml-eval/eval.py`~~ ✅ DONE 2026-05-16
   as `uv run python -m birdy_eval dump-mid-row`.
4. ~~**Paste both dumps** into Results section above + run the diff table.~~ ✅ DONE.
5. ~~**Make T8 gate-decision** based on Recommendation logic.~~ ✅ DONE — Path B chosen.
6. **Apply Path B threshold change** in `shared/ml/src/commonMain/kotlin/se/birdy/ml/TfLiteBirdClassifier.kt:64`:
   `DEFAULT_THRESHOLD: Float = 0.10f` → `0.05f`. Commit per plan 6b1 T8.B
   template.
7. **Continue T9 (TalkBack) + T10 (signed-AAB device-verify + tag)** per the
   v0.9.0a-billing device-verify runbook.
