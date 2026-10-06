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


def test_to_wav_failure_raises_audio_check_failed(tmp_path: Path) -> None:
    """ffmpeg failure (non-zero exit) should raise AudioCheckFailed, not raw CalledProcessError."""

    def fake_to_wav_fail(mp3_path: Path, wav_path: Path) -> None:
        import subprocess

        raise subprocess.CalledProcessError(1, "ffmpeg", stderr="corrupt MP3")

    def fake_run(cmd: list[str], **kwargs: object) -> CompletedProcess[str]:
        return _completed(stdout=json.dumps({"windows": [{"startSec": 0.0, "top": []}]}))

    with pytest.raises(AudioCheckFailed, match="ffmpeg"):
        classify_clip(
            tmp_path / "voice.mp3",
            tmp_path / "flexref",
            to_wav=fake_to_wav_fail,
            run=fake_run,
        )


def test_to_wav_timeout_raises_audio_check_failed(tmp_path: Path) -> None:
    """ffmpeg timeout should raise AudioCheckFailed."""

    def fake_to_wav_timeout(mp3_path: Path, wav_path: Path) -> None:
        import subprocess

        raise subprocess.TimeoutExpired("ffmpeg", 120)

    def fake_run(cmd: list[str], **kwargs: object) -> CompletedProcess[str]:
        return _completed(stdout=json.dumps({"windows": [{"startSec": 0.0, "top": []}]}))

    with pytest.raises(AudioCheckFailed, match=r"timeout|tidsgräns"):
        classify_clip(
            tmp_path / "voice.mp3",
            tmp_path / "flexref",
            to_wav=fake_to_wav_timeout,
            run=fake_run,
        )


def test_classify_timeout_raises_audio_check_failed(tmp_path: Path) -> None:
    """Classification uv run timeout should raise AudioCheckFailed."""
    import subprocess

    def fake_run(cmd: list[str], **kwargs: object) -> CompletedProcess[str]:
        if "classify_clip.py" in str(cmd):
            raise subprocess.TimeoutExpired(cmd[0], 900)
        return _completed(stdout=json.dumps({"windows": [{"startSec": 0.0, "top": []}]}))

    def fake_to_wav(mp3_path: Path, wav_path: Path) -> None:
        wav_path.write_bytes(b"RIFF....")

    with pytest.raises(AudioCheckFailed, match=r"timeout|tidsgräns"):
        classify_clip(
            tmp_path / "voice.mp3",
            tmp_path / "flexref",
            to_wav=fake_to_wav,
            run=fake_run,
        )


def test_classify_clip_calls_the_ml_eval_script_and_parses_json(tmp_path: Path) -> None:
    calls = []
    kwargs_list = []

    def fake_run(cmd: list[str], **kwargs: object) -> CompletedProcess[str]:
        calls.append(cmd)
        kwargs_list.append(kwargs)
        return _completed(stdout=json.dumps({"windows": [{"startSec": 0.0, "top": []}]}))

    def fake_to_wav(mp3_path: Path, wav_path: Path) -> None:
        wav_path.write_bytes(b"RIFF....")

    result = classify_clip(
        tmp_path / "voice.mp3", tmp_path / "flexref", to_wav=fake_to_wav, run=fake_run
    )
    assert result.windows == [{"startSec": 0.0, "top": []}]
    # Verify full command prefix, not just first two elements
    flexref_dir = str(tmp_path / "flexref")
    expected_prefix = [
        "uv",
        "run",
        "--project",
        flexref_dir,
        "python",
        "classify_clip.py",
    ]
    assert calls[0][:6] == expected_prefix
    # Verify cwd is set correctly
    assert kwargs_list[0].get("cwd") == tmp_path / "flexref"
    # Verify timeout is passed
    assert "timeout" in kwargs_list[0]


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


@pytest.mark.parametrize(
    "stdout",
    [
        "Downloading tensorflow\n{}",
        "",
        json.dumps({"window": []}),
        json.dumps({"windows": 5}),
        json.dumps({"windows": [{"startSec": 0.0}]}),
        json.dumps({"windows": [{"startSec": 0.0, "top": [{"qid": "Q1"}]}]}),
        json.dumps({"windows": [{"startSec": 0.0, "top": [{"qid": "Q1", "confidence": "x"}]}]}),
    ],
)
def test_unreadable_model_output_raises_audio_check_failed(tmp_path: Path, stdout: str) -> None:
    """I4 (final review 2026-10-06): stray stdout or an unexpected shape must become
    AudioCheckFailed (a V4 flag), never a JSONDecodeError/KeyError that fails the species."""

    def fake_run(cmd: list[str], **kwargs: object) -> CompletedProcess[str]:
        return _completed(stdout=stdout)

    def fake_to_wav(mp3_path: Path, wav_path: Path) -> None:
        wav_path.write_bytes(b"x")

    with pytest.raises(AudioCheckFailed):
        classify_clip(
            tmp_path / "voice.mp3", tmp_path / "flexref", to_wav=fake_to_wav, run=fake_run
        )


def test_a_missing_program_raises_audio_check_failed(tmp_path: Path) -> None:
    """`uv` (or ffmpeg) not on PATH is a technical V4 failure too, not a crash."""

    def fake_run(cmd: list[str], **kwargs: object) -> CompletedProcess[str]:
        raise FileNotFoundError(2, "No such file or directory", "uv")

    def fake_to_wav(mp3_path: Path, wav_path: Path) -> None:
        wav_path.write_bytes(b"x")

    with pytest.raises(AudioCheckFailed):
        classify_clip(
            tmp_path / "voice.mp3", tmp_path / "flexref", to_wav=fake_to_wav, run=fake_run
        )

    def missing_ffmpeg(mp3_path: Path, wav_path: Path) -> None:
        raise FileNotFoundError(2, "No such file or directory", "ffmpeg")

    with pytest.raises(AudioCheckFailed):
        classify_clip(
            tmp_path / "voice.mp3", tmp_path / "flexref", to_wav=missing_ffmpeg, run=fake_run
        )
