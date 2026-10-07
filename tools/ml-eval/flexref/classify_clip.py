"""CLI wrapper around reference.py's top3() for the content pipeline's V4 recording check
(spec 2026-09-25 Revision 2026-10-05). Reads one WAV file (48 kHz mono 16-bit, any length)
and prints JSON top-3 per 3-second window to stdout. Kept separate from reference.py so the
facit generator's file-writing behaviour (used by Albin/the agent by hand) is untouched."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import tensorflow as tf

from reference import MAPPING, MODEL, load_wav, top3

WINDOW = 144_000  # 3 s at 48 kHz
MIN_TAIL = WINDOW // 3  # a shorter tail is mostly zero-padding, not worth scoring


def main() -> None:
    path = Path(sys.argv[1])
    mapping = json.loads(MAPPING.read_text())
    lookup = {int(k): v for k, v in mapping["mapping"].items()}
    interp = tf.lite.Interpreter(model_path=str(MODEL))
    interp.allocate_tensors()
    waveform = load_wav(path)
    windows = []
    for start in range(0, max(len(waveform), 1), WINDOW):
        chunk = waveform[start : start + WINDOW]
        if len(chunk) < MIN_TAIL:
            continue
        result = top3(chunk, interp, lookup)
        windows.append(
            {
                "startSec": start / 48_000,
                "top": [{"qid": qid, "confidence": conf} for qid, conf in result],
            }
        )
    json.dump({"windows": windows}, sys.stdout)


if __name__ == "__main__":
    main()
