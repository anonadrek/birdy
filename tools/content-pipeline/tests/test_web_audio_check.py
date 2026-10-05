"""Tests for web/audio_check.py: V4, Birdy's own sound model checks the recording."""

from __future__ import annotations

import json
from pathlib import Path
from subprocess import CompletedProcess

import pytest

from birdy_fetcher.web.audio_check import (
    AudioCheckFailed,
    AudioCheckResult,
    audio_verdict,
    classify_clip,
)


def _completed(stdout: str = "", returncode: int = 0, stderr: str = "") -> CompletedProcess[str]:
    return CompletedProcess(args=[], returncode=returncode, stdout=stdout, stderr=stderr)


def test_matches_checks_the_qid_and_the_threshold() -> None:
    result = AudioCheckResult(
        windows=[{"startSec": 0.0, "top": [{"qid": "Q25485", "confidence": 0.42}]}]
    )
    assert result.matches("Q25485")
    assert not result.matches("Q25485", threshold=0.5)
    assert not result.matches("Q99999")


def test_audio_verdict_flags_a_species_the_model_does_not_cover() -> None:
    assert audio_verdict(None, "Q1", identifiable_sound=False).action == "flag"


def test_audio_verdict_strikes_a_covered_species_without_a_match() -> None:
    result = AudioCheckResult(windows=[{"startSec": 0.0, "top": []}])
    assert audio_verdict(result, "Q25485", identifiable_sound=True).action == "strike"


def test_audio_verdict_keeps_a_matched_recording() -> None:
    result = AudioCheckResult(
        windows=[{"startSec": 0.0, "top": [{"qid": "Q25485", "confidence": 0.3}]}]
    )
    assert audio_verdict(result, "Q25485", identifiable_sound=True).action == "keep"


def test_classify_clip_calls_the_ml_eval_script_and_parses_json(tmp_path: Path) -> None:
    calls = []

    def fake_run(cmd: list[str], **kwargs: object) -> CompletedProcess[str]:
        calls.append(cmd)
        return _completed(stdout=json.dumps({"windows": [{"startSec": 0.0, "top": []}]}))

    def fake_to_wav(mp3_path: Path, wav_path: Path) -> None:
        wav_path.write_bytes(b"RIFF....")

    result = classify_clip(
        tmp_path / "voice.mp3", tmp_path / "flexref", to_wav=fake_to_wav, run=fake_run
    )
    assert result.windows == [{"startSec": 0.0, "top": []}]
    assert calls[0][:2] == ["uv", "run"]


def test_classify_clip_raises_on_a_nonzero_exit(tmp_path: Path) -> None:
    def fake_run(cmd: list[str], **kwargs: object) -> CompletedProcess[str]:
        return _completed(returncode=1, stderr="boom")

    def fake_to_wav(mp3_path: Path, wav_path: Path) -> None:
        wav_path.write_bytes(b"x")

    with pytest.raises(AudioCheckFailed):
        classify_clip(
            tmp_path / "voice.mp3",
            tmp_path / "flexref",
            to_wav=fake_to_wav,
            run=fake_run,
        )
