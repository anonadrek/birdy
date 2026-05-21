# BirdNET-Lite Audio Accuracy Report

**Date:** 2026-05-21
**Model:** `birdnet_lite_v2.tflite` (~57 MB, 6362 classes, 627 mapped to Wikidata QIDs)
**Corpus:** (pending — eval blocked by missing ffmpeg; see note below)
**Runtime:** ai-edge-litert 2.1.4 (CPU), Python 3.12, numpy 1.26.4
**Spec target:** Top-3 ≥ 70%, Top-1 ≥ 50% (informal — matching Plan 4b image-eval bar)

---

## Eval status: BLOCKED — xeno-canto API v3 key required

The evaluation pipeline (`build_audio_corpus.py` → `eval_birdnet_audio.py`) is
ready and statically verified. ffmpeg 8.1.1 was installed via `scoop install ffmpeg`.

**Remaining blocker:** xeno-canto fully retired API v2 — v3 requires a free API key
(probe 2026-05-21: v2 returns `{"error": "server_error", "message": "Xeno-canto
API v2 is no longer available. Visit https://xeno-canto.org/explore/api for API v3
documentation."}`; v3 returns 401 without key). The script was migrated to v3
and now reads the key from `XC_API_KEY` env var or `tools/ml-eval/.xc_key` file
(both gitignored).

**Re-run once key is provided:**

```bash
# 1. Register at https://xeno-canto.org/
# 2. Copy key from https://xeno-canto.org/account
# 3. Persist locally (file is gitignored):
echo <your-key> > tools/ml-eval/.xc_key

# 4. From tools/ml-eval/:
uv run python scripts/build_audio_corpus.py   # ~5–15 min (network + ffmpeg)
uv run python scripts/eval_birdnet_audio.py   # ~30 s
```

Deferral rationale: corpus build requires a per-user API key. Same deferral
pattern as Billing v8 IPC runtime-verify (Plan 6b1) — scripts ship in this
plan, eval-run happens when key is available. Model verification below
proves the pipeline plumbing is correct.

---

## Results (pending)

| Metric              | Result     | Spec target |
|---------------------|------------|-------------|
| Corpus size         | — (pending) | 20–30 samples |
| Top-1 accuracy      | — (pending) | ≥ 50%       |
| Top-3 accuracy      | — (pending) | ≥ 70%       |
| Spec met            | — (pending) | —           |

### Threshold-band distribution (top-1 confidence)

| Band      | Count | % of corpus |
|-----------|-------|-------------|
| Match ≥ 0.50  | — | — |
| Disambig 0.35–0.50 | — | — |
| NoBird < 0.35  | — | — |

---

## Model verification (confirmed, no ffmpeg needed)

Model input/output tensors confirmed via ai-edge-litert:

```
input_shape=[1, 144000]  dtype=float32
output_shape=[1, 6362]   dtype=float32
```

- Input: `[1, 144000]` float32 → 144 000 samples × 48 kHz = exactly 3 seconds.
  Matches `AndroidAudioRecorder.expectedSamples = 48_000 * 3 = 144_000`.
- Preprocessing: `pcm_int16 / 32768.0` → float32 in [−1, 1].
  Matches `AudioPreprocessor.android.kt normalize()`.
- Output: 6362 classes (BirdNET-Lite 6K Global). 627 classes mapped to Wikidata QIDs
  via `birdnet_lite_to_qid.json`.

---

## Target species (30 candidates)

All 30 target species were cross-referenced against:
- `shared/content/species_list.yaml` (species must exist in content)
- `birdnet_lite_to_qid.json` (species QID must be mapped to a BirdNET class)

**Note:** The cross-reference filtering in `build_audio_corpus.py` is run at corpus-build
time. The actual skipped/accepted count will be printed when `build_audio_corpus.py` runs.
Based on the mapping (627/839 species = 74.7% coverage), approximately 22–28 of the 30
target species are expected to pass filtering.

---

## Corpus design

- **Source:** xeno-canto API v3 (`q:A q:B` quality filters — best two quality tiers; key from env/file)
- **Licence filter:** CC-BY, CC0, public-domain only (lic URL contains `creativecommons.org/licenses/by` or `creativecommons.org/publicdomain`)
- **Format:** 48 kHz mono PCM_16 WAV, 3s window
- **Window selection:** highest-RMS 3-second window (maximises bird-call signal)
- **Recordings per species:** up to 3 (for variance estimation)
- **Naming:** `Q{qid}_{Sci_underscored}_{xenoId}.wav`

---

## Preliminary expectations

Audio-ID with BirdNET on raw field recordings is harder than image classification:
- Background noise, multiple species calling, distance from bird, wind, equipment quality
  all reduce accuracy vs. controlled studio recordings.
- BirdNET-Lite 6K Global was optimised for European field recordings — EU-endemic species
  (the target set) should perform well.
- Expected top-3 ≥ 70% is achievable for common species with strong vocalisation
  profiles (tit, thrush, warbler families), but may be lower for cryptic species
  (reed warblers, Phylloscopus leaf-warblers, some raptors).

---

## Files produced by T8

| File | Purpose |
|------|---------|
| `tools/ml-eval/scripts/build_audio_corpus.py` | Fetch xeno-canto CC recordings, crop to highest-RMS 3s, output WAVs + manifest |
| `tools/ml-eval/scripts/eval_birdnet_audio.py` | Run BirdNET-Lite on corpus, report top-1/top-3 + threshold-band distribution |
| `tools/ml-eval/audio_accuracy_report_2026-05-21.md` | This file — will be updated with real numbers once ffmpeg is available |

---

*Report will be updated in-place once `build_audio_corpus.py` + `eval_birdnet_audio.py`
complete successfully. Run `uv run python scripts/eval_birdnet_audio.py` and paste the
stdout "=" summary block into the Results table above.*
