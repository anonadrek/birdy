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
from subprocess import CompletedProcess, TimeoutExpired
from typing import Literal

CONFIDENCE_THRESHOLD = 0.10
FFMPEG_TIMEOUT = 120  # seconds; MP3 to WAV conversion should complete quickly
CLASSIFY_TIMEOUT = 900  # seconds; first run may install TensorFlow via uv sync
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
    try:
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
            timeout=FFMPEG_TIMEOUT,
        )
    except subprocess.CalledProcessError as e:
        raise AudioCheckFailed(f"ffmpeg misslyckades med kod {e.returncode}") from e
    except TimeoutExpired as e:
        raise AudioCheckFailed(f"ffmpeg timeout efter {FFMPEG_TIMEOUT} sekunder") from e


def _windows(stdout: str) -> list[dict[str, object]]:
    """The model's windows, or AudioCheckFailed when its output is not the JSON
    classify_clip.py writes (stray output, a missing key, a wrong type): a technical V4
    failure is a flag, never an error that fails the species (I4, final review 2026-10-06).
    Every entry is read here once, so `AudioCheckResult.matches` cannot fail later."""
    try:
        windows = json.loads(stdout)["windows"]
        if not isinstance(windows, list):
            raise TypeError("windows är ingen lista")
        for window in windows:
            for entry in window["top"]:
                str(entry["qid"])
                float(entry["confidence"])
    except (ValueError, KeyError, TypeError) as exc:  # JSONDecodeError is a ValueError
        raise AudioCheckFailed(
            f"ljudmodellen gav ett oläsbart svar ({type(exc).__name__})"
        ) from exc
    return windows


def classify_clip(
    mp3_path: Path,
    flexref_dir: Path,
    *,
    to_wav: ToWavFn = _default_to_wav,
    run: RunFn = subprocess.run,
) -> AudioCheckResult:
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = Path(tmp) / "clip.wav"
        try:
            to_wav(mp3_path, wav_path)
        except AudioCheckFailed:
            # Already converted to AudioCheckFailed
            raise
        except subprocess.CalledProcessError as e:
            raise AudioCheckFailed(f"ffmpeg misslyckades med kod {e.returncode}") from e
        except TimeoutExpired as e:
            raise AudioCheckFailed(f"ffmpeg timeout efter {FFMPEG_TIMEOUT} sekunder") from e
        except OSError as e:  # ffmpeg missing or not runnable
            raise AudioCheckFailed(f"ffmpeg kunde inte startas ({e})") from e

        print(
            "Kontrollerar inspelningen med ljudmodellen (första körningen kan ta flera minuter)..."
        )
        try:
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
                timeout=CLASSIFY_TIMEOUT,
            )
        except TimeoutExpired as e:
            raise AudioCheckFailed(f"ljudmodellen timeout efter {CLASSIFY_TIMEOUT} sekunder") from e
        except OSError as e:  # uv missing or not runnable
            raise AudioCheckFailed(f"ljudmodellen kunde inte startas ({e})") from e
    if completed.returncode != 0:
        raise AudioCheckFailed(completed.stderr.strip() or "ljudmodellen gav inget svar")
    return AudioCheckResult(windows=_windows(completed.stdout))
