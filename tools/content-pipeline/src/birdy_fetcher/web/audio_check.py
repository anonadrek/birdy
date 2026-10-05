"""V4 (spec 2026-09-25 Revision 2026-10-05): Birdy's own BirdNET model classifies the
species' recording, via tools/ml-eval/flexref in its own process. A species the photo/sound
coverage map (identify.py) says the audio model does not cover gets a flag instead of an
automatic verdict: there is nothing for the model to confirm or deny."""

from __future__ import annotations

import json
import subprocess
import tempfile
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from subprocess import CompletedProcess
from typing import Literal

CONFIDENCE_THRESHOLD = 0.10
ToWavFn = Callable[[Path, Path], None]
RunFn = Callable[..., "CompletedProcess[str]"]


class AudioCheckFailed(RuntimeError):  # noqa: N818
    pass


@dataclass(frozen=True)
class AudioCheckResult:
    windows: list[dict[str, object]]

    def matches(self, qid: str, *, threshold: float = CONFIDENCE_THRESHOLD) -> bool:
        return any(
            entry["qid"] == qid and float(entry["confidence"]) >= threshold
            for window in self.windows
            for entry in window["top"]  # type: ignore[attr-defined]
        )


@dataclass(frozen=True)
class AudioVerdict:
    action: Literal["keep", "strike", "flag"]
    reason: str | None = None


def audio_verdict(
    result: AudioCheckResult | None, qid: str, *, identifiable_sound: bool
) -> AudioVerdict:
    if not identifiable_sound:
        return AudioVerdict("flag", "ljudmodellen täcker inte arten: lyssna och besluta")
    if result is None or not result.matches(qid):
        return AudioVerdict(
            "strike", "ljudmodellen hittade inte arten i inspelningen (minst 0,10 i konfidens)"
        )
    return AudioVerdict("keep")


def _default_to_wav(mp3_path: Path, wav_path: Path) -> None:
    import imageio_ffmpeg

    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    subprocess.run(
        [
            ffmpeg,
            "-y",
            "-i",
            str(mp3_path),
            "-ar",
            "48000",
            "-ac",
            "1",
            "-sample_fmt",
            "s16",
            str(wav_path),
        ],
        check=True,
        capture_output=True,
    )


def classify_clip(
    mp3_path: Path,
    flexref_dir: Path,
    *,
    to_wav: ToWavFn = _default_to_wav,
    run: RunFn = subprocess.run,
) -> AudioCheckResult:
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = Path(tmp) / "clip.wav"
        to_wav(mp3_path, wav_path)
        completed = run(
            [
                "uv",
                "run",
                "--project",
                str(flexref_dir),
                "python",
                "classify_clip.py",
                str(wav_path),
            ],
            cwd=flexref_dir,
            capture_output=True,
            text=True,
            check=False,
        )
    if completed.returncode != 0:
        raise AudioCheckFailed(completed.stderr.strip() or "ljudmodellen gav inget svar")
    return AudioCheckResult(windows=json.loads(completed.stdout)["windows"])
