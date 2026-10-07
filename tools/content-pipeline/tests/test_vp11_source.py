"""VP11.pdf is downloaded at run time (no licence, so not in the public repo)."""

from __future__ import annotations

import hashlib
import http.client
import re
import urllib.error
from pathlib import Path

import pytest
from click.testing import CliRunner

from birdy_fetcher import vp11_source
from birdy_fetcher.cli import VP11_UNAVAILABLE_EXIT, main
from birdy_fetcher.doctor import run_doctor
from birdy_fetcher.species_list import parse_vp11
from birdy_fetcher.vp11_source import (
    VP11_SHA256,
    VP11_URL,
    Vp11UnavailableError,
    ensure_vp11,
    vp11_cache_path,
)

PIPELINE_ROOT = Path(__file__).resolve().parent.parent
FAKE_PDF = b"%PDF-1.4 synthetic test bytes, not BirdLife's list"
FAKE_SHA = hashlib.sha256(FAKE_PDF).hexdigest()


def _no_network(url: str) -> bytes:
    raise AssertionError(f"unexpected download of {url}")


def test_downloads_once_and_caches(tmp_path: Path) -> None:
    calls: list[str] = []

    def fetch(url: str) -> bytes:
        calls.append(url)
        return FAKE_PDF

    path = ensure_vp11(tmp_path, expected_sha256=FAKE_SHA, fetch=fetch)
    assert path == vp11_cache_path(tmp_path)
    assert path.read_bytes() == FAKE_PDF
    assert calls == [VP11_URL]

    again = ensure_vp11(tmp_path, expected_sha256=FAKE_SHA, fetch=_no_network)
    assert again == path
    assert not list(path.parent.glob("*.tmp"))


def test_wrong_download_is_not_written(tmp_path: Path) -> None:
    with pytest.raises(Vp11UnavailableError, match="expected"):
        ensure_vp11(tmp_path, expected_sha256=FAKE_SHA, fetch=lambda url: b"something else")
    assert not vp11_cache_path(tmp_path).exists()


def test_wrong_cached_file_fails_with_a_hint(tmp_path: Path) -> None:
    target = vp11_cache_path(tmp_path)
    target.parent.mkdir(parents=True)
    target.write_bytes(b"truncated")
    hint = re.escape("birdlife.se/tk/vastpalearktislistan")
    with pytest.raises(Vp11UnavailableError, match=hint):
        ensure_vp11(tmp_path, expected_sha256=FAKE_SHA, fetch=_no_network)


def test_network_error_says_how_to_get_the_file(tmp_path: Path) -> None:
    def offline(url: str) -> bytes:
        raise urllib.error.URLError("no network")

    with pytest.raises(Vp11UnavailableError) as excinfo:
        ensure_vp11(tmp_path, expected_sha256=FAKE_SHA, fetch=offline)
    message = str(excinfo.value)
    assert "no network" in message
    assert str(vp11_cache_path(tmp_path)) in message
    assert FAKE_SHA in message


def test_truncated_response_is_reported_not_raised(tmp_path: Path) -> None:
    """IncompleteRead and friends are http.client.HTTPException, not OSError."""

    def truncated(url: str) -> bytes:
        raise http.client.IncompleteRead(b"%PDF-1.4 half", 260771)

    with pytest.raises(Vp11UnavailableError, match="Could not download"):
        ensure_vp11(tmp_path, expected_sha256=FAKE_SHA, fetch=truncated)
    assert not vp11_cache_path(tmp_path).exists()


def test_init_exits_3_with_the_manual_download_hint(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """`birdy-fetcher init` offline: exit 3 (not click's usage-error 2) and say what to do."""
    target = tmp_path / "sources" / "vp11.pdf"

    def offline(url: str) -> bytes:
        raise urllib.error.URLError("no network")

    monkeypatch.setattr(vp11_source, "vp11_cache_path", lambda cache_root: target)
    monkeypatch.setattr(vp11_source, "_http_get", offline)

    result = CliRunner().invoke(main, ["init"])

    assert result.exit_code == VP11_UNAVAILABLE_EXIT == 3
    assert vp11_source.VP11_PAGE_URL in result.output
    assert str(target) in result.output
    assert VP11_SHA256 in result.output
    assert not target.exists()


def test_pdf_is_not_tracked_in_the_repo() -> None:
    """The licence-less PDF must never come back into sources/."""
    assert not (PIPELINE_ROOT / "sources" / "vp11.pdf").exists()


def test_doctor_reports_missing_vp11_as_downloadable(tmp_path: Path) -> None:
    check = next(c for c in run_doctor(root=tmp_path).checks if "vp11" in c.name)
    assert check.ok
    assert VP11_URL in check.detail


def test_doctor_fails_on_wrong_cached_vp11(tmp_path: Path) -> None:
    target = vp11_cache_path(tmp_path / ".cache")
    target.parent.mkdir(parents=True)
    target.write_bytes(b"not the pinned file")
    check = next(c for c in run_doctor(root=tmp_path).checks if "vp11" in c.name)
    assert not check.ok


_REAL_VP11 = vp11_cache_path(PIPELINE_ROOT / ".cache")


@pytest.mark.skipif(
    not _REAL_VP11.exists(),
    reason="VP11.pdf is not cached; `uv run birdy-fetcher init` downloads it to .cache/sources/",
)
def test_real_vp11_matches_the_pin_and_parses() -> None:
    assert hashlib.sha256(_REAL_VP11.read_bytes()).hexdigest() == VP11_SHA256
    entries = parse_vp11(_REAL_VP11)
    assert len(entries) > 1000
    great_tit = next(e for e in entries if e.scientific_name == "Parus major")
    assert great_tit.family == "Paridae"
