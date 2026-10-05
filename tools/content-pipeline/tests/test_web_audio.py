"""Tests for web/audio.py: the recording filter, Commons metadata and MP3 conversion."""

from __future__ import annotations

from dataclasses import replace

from birdy_fetcher.web.audio import (
    AudioCandidate,
    audio_record,
    choose,
    normalize_license,
    rejection,
)

BASE = AudioCandidate(
    title="File:Parus major song.ogg",
    url="https://upload.wikimedia.org/a.ogg",
    page_url="https://commons.wikimedia.org/wiki/File:Parus_major_song.ogg",
    mime="application/ogg",
    duration=25.0,
    license="CC BY-SA 4.0",
    author="Anna",
    categories=("Category:Parus major",),
    from_wikidata=False,
)


def test_normalize_license() -> None:
    assert normalize_license("CC BY-SA 4.0") == "CC BY-SA 4.0"
    assert normalize_license("cc-by-sa-3.0") == "CC BY-SA 3.0"
    assert normalize_license("CC BY 2.0") == "CC BY 2.0"
    assert normalize_license("Public Domain") == "Public domain"
    assert normalize_license("CC0 1.0") == "CC0"
    assert normalize_license("GFDL") == "GFDL"
    assert normalize_license(None) is None


def test_a_good_recording_is_accepted() -> None:
    assert rejection(BASE, "Parus major") is None


def test_rejections() -> None:
    assert rejection(replace(BASE, mime="video/webm"), "Parus major") == "inte en ljudfil"
    pronunciation = replace(BASE, categories=("Category:German pronunciation of nouns",))
    assert rejection(pronunciation, "Parus major") == "uttalsfil"
    assert "licensen" in str(rejection(replace(BASE, license="GFDL"), "Parus major"))
    assert "licensen" in str(rejection(replace(BASE, license="CC BY 2.5"), "Parus major"))
    assert rejection(replace(BASE, duration=1.85), "Parus major") == "kortare än 3 sekunder"
    assert rejection(replace(BASE, duration=None), "Parus major") == "kortare än 3 sekunder"


def test_a_search_hit_must_name_the_species() -> None:
    xc_only = replace(BASE, title="File:XC538678.mp3", categories=("Category:Xeno-canto",))
    assert rejection(xc_only, "Parus major") == "nämner inte arten i titeln eller kategorierna"
    assert rejection(replace(xc_only, from_wikidata=True), "Parus major") is None


def test_choose_takes_the_first_accepted_and_reports_the_rest() -> None:
    bad = replace(BASE, title="File:De-Kohlmeise.ogg", duration=1.2)
    chosen, notes = choose([bad, BASE], "Parus major")
    assert chosen == BASE
    assert notes == ["File:De-Kohlmeise.ogg: kortare än 3 sekunder"]
    assert choose([bad], "Parus major") == (None, ["File:De-Kohlmeise.ogg: kortare än 3 sekunder"])


def test_audio_record() -> None:
    long = audio_record(replace(BASE, duration=57.8), "Q25485")
    assert long == {
        "file": "Q25485/voice.mp3",
        "durationSec": 20,
        "trimmed": True,
        "author": "Anna",
        "license": "CC BY-SA 4.0",
        "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
        "sourceUrl": "https://commons.wikimedia.org/wiki/File:Parus_major_song.ogg",
    }
    short = audio_record(replace(BASE, duration=12.4, license="CC0"), "Q25485")
    assert short["durationSec"] == 12
    assert short["trimmed"] is False
    assert short["licenseUrl"] is None
