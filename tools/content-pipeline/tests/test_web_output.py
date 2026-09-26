"""Tests for web/output.py: the record in spec appendix C, and approved files are kept."""

from __future__ import annotations

import json
from datetime import UTC, datetime
from pathlib import Path

from birdy_fetcher.web.images import ImageOut
from birdy_fetcher.web.output import build_record, is_approved, write_record
from birdy_fetcher.web.source import SpeciesSource

from .web_fixtures import ARTICLES, valid_output

SOURCE = SpeciesSource("Q25485", "Parus major", "Talgoxe", "Great Tit", "Paridae", "Mesar",
                       "Passeriformes", "LC", "Söker frön.", "Forages.", ())
IMAGE = ImageOut("hero", "Q25485/hero.webp", 1600, 1067, "Hobbyfotowiki", "CC0", None,
                 "https://commons.wikimedia.org/wiki/File:A.jpg")
NOW = datetime(2026, 10, 1, 12, 0, tzinfo=UTC)


def _record(errors: list[str] | None = None) -> dict[str, object]:
    return build_record(
        source=SOURCE,
        group="songbirds",
        text=valid_output(),
        articles=ARTICLES,
        images=[IMAGE],
        errors=errors or [],
        model_id="claude-opus-5",
        generated_at=NOW,
    )


def test_record_matches_appendix_c() -> None:
    rec = _record()
    assert rec["qid"] == "Q25485" and rec["status"] == "ok" and rec["review"] == "unreviewed"
    assert rec["slug"] == {"sv": "talgoxe", "en": "great-tit"}
    assert rec["names"] == {"sv": "Talgoxe", "en": "Great Tit", "scientific": "Parus major"}
    assert rec["family"] == {"latin": "Paridae", "sv": "Mesar"}
    assert rec["group"] == "songbirds" and rec["iucn"] == "LC"
    assert rec["marginalia"] == {"sv": "Söker frön.", "en": "Forages."}
    assert rec["wikipedia"] == {
        "sv": {"title": "Talgoxe", "revision": "111"},
        "en": {"title": "Great tit", "revision": "222"},
    }
    assert rec["generated"] == {
        "model": "claude-opus-5", "prompt": "web-v1", "at": "2026-10-01T12:00:00+00:00"
    }
    text = rec["text"]
    assert isinstance(text, dict)
    sv = text["sv"]
    assert set(sv) == {"lead", "fieldMarks", "voice", "whereWhen", "metaDescription", "facts"}
    assert sv["facts"]["swedenStatus"]["value"] == "resident"
    images = rec["images"]
    assert isinstance(images, list)
    assert images[0] == {
        "role": "hero", "file": "Q25485/hero.webp", "width": 1600, "height": 1067,
        "author": "Hobbyfotowiki", "license": "CC0", "licenseUrl": None,
        "sourceUrl": "https://commons.wikimedia.org/wiki/File:A.jpg",
    }


def test_errors_make_the_record_failed() -> None:
    rec = _record(["en.voice: är skriven i första person"])
    assert rec["status"] == "failed" and rec["errors"] == ["en.voice: är skriven i första person"]


def test_write_keeps_approved_files_unless_forced(tmp_path: Path) -> None:
    rec = _record()
    assert write_record(rec, tmp_path, force=False) == "written"
    path = tmp_path / "Q25485.json"
    saved = json.loads(path.read_text(encoding="utf-8"))
    saved["review"] = "approved"
    path.write_text(json.dumps(saved), encoding="utf-8")
    assert is_approved(path)
    assert write_record(rec, tmp_path, force=False) == "skipped"
    assert write_record(rec, tmp_path, force=True) == "written"
    assert json.loads(path.read_text(encoding="utf-8"))["review"] == "unreviewed"
