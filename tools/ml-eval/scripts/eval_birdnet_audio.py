"""eval_birdnet_audio.py — evaluate BirdNET-Lite accuracy on audio corpus.

Runs the bundled BirdNET-Lite TFLite model (birdnet_lite_v2.tflite) on all WAV
files in corpus_audio/ and reports top-1 / top-3 accuracy against the known QIDs
encoded in each filename (Q{qid}_{sci_underscored}_{xenoId}.wav).

Preprocessing mirrors Android (AndroidAudioRecorder + AudioPreprocessor.android.kt):
  - Read PCM_16 samples from WAV (already 48 kHz mono after build_audio_corpus.py)
  - Normalize: sample / 32768.0  (matches `normalize()` in AudioPreprocessor.android.kt)
  - Shape: [1, 144000] float32 (or [1, N] as reported by the model's input tensor)

Usage:
  uv run python scripts/eval_birdnet_audio.py

Outputs:
  tools/ml-eval/audio_eval_results.json  — full per-sample results
  (prints accuracy + threshold-band summary to stdout)
"""
from __future__ import annotations

import json
import struct
import sys
from pathlib import Path

import numpy as np  # type: ignore[import-untyped]
import yaml
from ai_edge_litert.interpreter import Interpreter  # type: ignore[import-untyped]

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
SCRIPT_DIR = Path(__file__).resolve().parent
EVAL_DIR = SCRIPT_DIR.parent
ROOT = EVAL_DIR.parent.parent

MAPPING_PATH = (
    ROOT
    / "shared"
    / "ml"
    / "src"
    / "commonMain"
    / "composeResources"
    / "files"
    / "ml"
    / "birdnet_lite_to_qid.json"
)
MODEL_PATH = (
    ROOT / "composeApp" / "src" / "androidMain" / "assets" / "models" / "birdnet_lite_v2.tflite"
)
CORPUS_DIR = EVAL_DIR / "corpus_audio"
MANIFEST_PATH = CORPUS_DIR / "manifest_audio.yaml"
RESULTS_PATH = EVAL_DIR / "audio_eval_results.json"

# Confidence thresholds matching production code (SharedPreferences / AudioClassifierFactory)
THRESHOLD_MATCH = 0.50    # >= MATCH → Match
THRESHOLD_DISAMBIG = 0.35  # >= DISAMBIG and < MATCH → Disambig
# < THRESHOLD_DISAMBIG → NoBird

SAMPLE_RATE = 48_000
DURATION_S = 3


# ---------------------------------------------------------------------------
# Loaders
# ---------------------------------------------------------------------------

def load_mapping() -> dict[int, str]:
    """Load birdnet_lite_to_qid.json → {class_index (int): qid (str)}."""
    data = json.loads(MAPPING_PATH.read_text(encoding="utf-8"))
    raw: dict[str, str] = data["mapping"]  # singular key; see BirdNetLabelMapper.kt
    return {int(k): v for k, v in raw.items()}


def read_wav_samples(wav_path: Path, expected_samples: int) -> np.ndarray | None:  # type: ignore[type-arg]
    """Read PCM_16 WAV and return a float32 numpy array of *expected_samples* values.

    Normalisation: divides by 32768.0, matching AudioPreprocessor.android.kt `normalize()`.
    Returns None if the file is too short or unreadable.
    """
    try:
        with wav_path.open("rb") as f:
            header = f.read(44)
            if len(header) < 44 or header[:4] != b"RIFF":
                return None
            data_size = struct.unpack_from("<I", header, 40)[0]
            pcm_bytes = f.read(data_size)
    except OSError:
        return None

    n_samples = len(pcm_bytes) // 2
    if n_samples < expected_samples:
        return None

    raw = np.frombuffer(pcm_bytes[: expected_samples * 2], dtype=np.int16)
    return raw.astype(np.float32) / 32768.0


def threshold_band(confidence: float) -> str:
    if confidence >= THRESHOLD_MATCH:
        return "Match"
    if confidence >= THRESHOLD_DISAMBIG:
        return "Disambig"
    return "NoBird"


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    # Validate prerequisites
    if not MODEL_PATH.exists():
        print(f"ERROR: Model not found: {MODEL_PATH}", file=sys.stderr)
        sys.exit(1)
    if not MANIFEST_PATH.exists():
        print(
            "ERROR: No audio corpus found.\n"
            "Run `uv run python scripts/build_audio_corpus.py` first.",
            file=sys.stderr,
        )
        sys.exit(1)

    # Load corpus manifest
    manifest = yaml.safe_load(MANIFEST_PATH.read_text(encoding="utf-8"))
    items: list[dict[str, str]] = manifest.get("items", [])
    if not items:
        print("ERROR: Corpus manifest is empty — nothing to measure.", file=sys.stderr)
        sys.exit(1)

    print(f"Corpus: {len(items)} audio samples from {MANIFEST_PATH}")

    # Load model
    print(f"Loading model from {MODEL_PATH} …")
    interpreter = Interpreter(model_path=str(MODEL_PATH))
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()[0]
    output_details = interpreter.get_output_details()[0]
    input_index = input_details["index"]
    output_index = output_details["index"]
    input_shape: list[int] = list(input_details["shape"])
    input_dtype = input_details["dtype"]
    output_dtype = output_details["dtype"]

    print(f"  input_shape={input_shape}, input_dtype={input_dtype.__name__}")
    print(f"  output_shape={list(output_details['shape'])}, output_dtype={output_dtype.__name__}")

    if len(input_shape) != 2 or input_shape[0] != 1:
        print(
            f"ERROR: Unexpected input shape {input_shape}. Expected [1, N] (raw PCM waveform).",
            file=sys.stderr,
        )
        sys.exit(1)
    expected_samples: int = input_shape[1]
    dur = expected_samples / SAMPLE_RATE
    print(f"  expected_samples={expected_samples}  ({dur:.1f}s @ {SAMPLE_RATE} Hz)")

    # Load label mapping
    print(f"Loading mapping from {MAPPING_PATH} …")
    idx_to_qid = load_mapping()
    print(f"  {len(idx_to_qid)} classes mapped to QIDs")

    # Run inference
    print("\nRunning inference …\n")
    records: list[dict] = []  # type: ignore[type-arg]
    top1_correct = 0
    top3_correct = 0
    band_counts: dict[str, int] = {"Match": 0, "Disambig": 0, "NoBird": 0}

    for item in items:
        rel_path: str = item["path"]
        true_qid: str = item["qid"]
        sci_name: str = item.get("sci_name", "")

        # WAV files are relative to EVAL_DIR (tools/ml-eval/)
        wav_path = EVAL_DIR / rel_path

        pcm = read_wav_samples(wav_path, expected_samples)
        if pcm is None:
            print(f"  SKIP {wav_path.name} — unreadable or too short")
            continue

        # Build input tensor -- model expects float32 in [-1, 1] (confirmed by Android runner)
        if input_dtype == np.float32:
            tensor = pcm[np.newaxis, :]  # shape [1, N]
        elif input_dtype == np.uint8:
            # Unlikely for BirdNET-Lite, but handle gracefully:
            # re-quantize float32 [-1,1] → uint8 using model quantization params
            scale, zero_point = input_details["quantization"]
            tensor = (pcm / scale + zero_point).clip(0, 255).astype(np.uint8)[np.newaxis, :]
        else:
            print(f"  SKIP {wav_path.name} — unknown input dtype {input_dtype}", file=sys.stderr)
            continue

        interpreter.set_tensor(input_index, tensor)
        interpreter.invoke()
        raw_scores = interpreter.get_tensor(output_index)[0]

        # Dequantize if output is uint8
        if output_dtype == np.uint8:
            scale, zero_point = output_details["quantization"]
            logits: np.ndarray = (raw_scores.astype(np.float32) - zero_point) * scale  # type: ignore[assignment]
        else:
            logits = raw_scores.astype(np.float32)
        # BirdNET-Lite emits pre-sigmoid logits — mirror BirdNET-Analyzer's flat_sigmoid
        # post-processing so confidences land in [0, 1]. Matches `flatSigmoid` in
        # AndroidTfliteAudioRunner.kt.
        scores = 1.0 / (1.0 + np.exp(-np.clip(logits, -15.0, 15.0)))

        top_indices = np.argsort(scores)[::-1][:3]
        top3_qids = [idx_to_qid.get(int(i)) for i in top_indices]
        top3_confs = [float(scores[int(i)]) for i in top_indices]

        top1_qid = top3_qids[0]
        top1_conf = top3_confs[0]
        band = threshold_band(top1_conf)
        band_counts[band] += 1

        hit1 = top1_qid == true_qid
        hit3 = true_qid in [q for q in top3_qids if q is not None]

        if hit1:
            top1_correct += 1
        if hit3:
            top3_correct += 1

        if hit3:
            rank = next(i + 1 for i, q in enumerate(top3_qids) if q == true_qid)
            conf = top3_confs[rank - 1]
            print(
                f"  PASS {true_qid} ({sci_name}): "
                f"top-3 hit (rank {rank}, conf {conf:.3f}) [{band}]"
            )
        else:
            top1_label = (
                f"{top1_qid} @ {top1_conf:.3f}" if top1_qid else f"idx_unknown @ {top1_conf:.3f}"
            )
            print(f"  FAIL {true_qid} ({sci_name}): missed — top-1 = {top1_label} [{band}]")

        records.append({
            "true_qid": true_qid,
            "sci_name": sci_name,
            "wav": str(wav_path.name),
            "top1_qid": top1_qid,
            "top1_confidence": round(top1_conf, 5),
            "top3_qids": [q for q in top3_qids],
            "top3_confidences": [round(c, 5) for c in top3_confs],
            "top1_correct": hit1,
            "top3_correct": hit3,
            "threshold_band": band,
        })

    n = len(records)
    if n == 0:
        print("No samples measured.", file=sys.stderr)
        sys.exit(1)

    top1_pct = top1_correct / n * 100
    top3_pct = top3_correct / n * 100

    print(f"\n{'='*60}")
    print(f"Measured {n} samples")
    print(f"  Top-1: {top1_correct}/{n} = {top1_pct:.1f}%")
    print(f"  Top-3: {top3_correct}/{n} = {top3_pct:.1f}%")
    print(f"  Spec target: Top-3 >= 70%  -> {'PASS' if top3_pct >= 70 else 'FAIL'}")
    print("\nThreshold-band distribution (top-1 confidence):")
    for band, count in band_counts.items():
        pct = count / n * 100
        print(f"  {band:10s}: {count:3d} ({pct:.0f}%)")

    # Write JSON results
    RESULTS_PATH.write_text(
        json.dumps(
            {
                "summary": {
                    "n": n,
                    "top1": top1_correct,
                    "top3": top3_correct,
                    "top1_pct": round(top1_pct, 1),
                    "top3_pct": round(top3_pct, 1),
                    "bands": band_counts,
                },
                "records": records,
            },
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    print(f"\nFull results written to {RESULTS_PATH}")


if __name__ == "__main__":
    main()
