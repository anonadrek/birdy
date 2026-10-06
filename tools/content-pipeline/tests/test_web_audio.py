"""Tests for web/audio.py: the recording filter, Commons metadata and MP3 conversion."""

from __future__ import annotations

import io
import json
import math
import struct
import subprocess
import wave
from dataclasses import replace
from pathlib import Path

import imageio_ffmpeg

from birdy_fetcher.cache import Cache
from birdy_fetcher.web.audio import (
    AudioCandidate,
    CommonsAudioClient,
    audio_record,
    choose,
    convert_to_mp3,
    normalize_license,
    probe_seconds,
    rejection,
)
from birdy_fetcher.web.http import ThrottledHttp

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


def test_search_hit_with_longer_name_is_rejected() -> None:
    """A file titled "Parus majoroides calling.ogg" should be rejected for "Parus major"."""
    candidate = replace(
        BASE,
        title="File:Parus majoroides calling.ogg",
        categories=(),
        from_wikidata=False,
    )
    assert rejection(candidate, "Parus major") == "nämner inte arten i titeln eller kategorierna"


def test_search_hit_with_underscores_is_accepted() -> None:
    """A file titled "Parus_major_song.ogg" should be accepted for "Parus major"."""
    candidate = replace(
        BASE,
        title="File:Parus_major_song.ogg",
        categories=(),
        from_wikidata=False,
    )
    assert rejection(candidate, "Parus major") is None


def test_search_hit_with_name_in_category_is_accepted() -> None:
    """A file with species name only in category should be accepted."""
    candidate = replace(
        BASE,
        title="File:Songbird recording.ogg",
        categories=("Category:Sounds of Parus major",),
        from_wikidata=False,
    )
    assert rejection(candidate, "Parus major") is None


P51 = {"claims": {"P51": [{"mainsnak": {"datavalue": {"value": "Parus_major_song.ogg"}}}]}}
SEARCH = {
    "query": {
        "search": [{"title": "File:De-Kohlmeise.ogg"}, {"title": "File:Parus major song.ogg"}]
    }
}
INFO = {
    "query": {
        "normalized": [{"from": "File:Parus_major_song.ogg", "to": "File:Parus major song.ogg"}],
        "pages": [
            {
                "title": "File:Parus major song.ogg",
                "imageinfo": [
                    {
                        "url": "https://upload.wikimedia.org/a.ogg",
                        "descriptionurl": (
                            "https://commons.wikimedia.org/wiki/File:Parus_major_song.ogg"
                        ),
                        "mime": "application/ogg",
                        "duration": 31.5,
                        "extmetadata": {
                            "LicenseShortName": {"value": "CC BY-SA 4.0"},
                            "Artist": {"value": '<a href="//commons.wikimedia.org/x">Anna</a>'},
                        },
                    }
                ],
                "categories": [{"title": "Category:Parus major"}],
            },
            {
                "title": "File:De-Kohlmeise.ogg",
                "imageinfo": [
                    {
                        "url": "https://upload.wikimedia.org/b.ogg",
                        "descriptionurl": "https://commons.wikimedia.org/wiki/File:De-Kohlmeise.ogg",
                        "mime": "application/ogg",
                        "duration": 1.2,
                        "extmetadata": {"LicenseShortName": {"value": "CC BY-SA 4.0"}},
                    }
                ],
                "categories": [{"title": "Category:German pronunciation of nouns"}],
            },
        ],
    }
}


class Routed:
    def __init__(self, routes: dict[str, object]) -> None:
        self.routes = routes
        self.urls: list[str] = []

    async def __call__(self, url: str) -> str:
        self.urls.append(url)
        for key, body in self.routes.items():
            if key in url:
                return json.dumps(body)
        raise FileNotFoundError(url)


def _wav(seconds: int) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(22050)
        frames = b"".join(
            struct.pack("<hh", int(8000 * math.sin(2 * math.pi * 440 * i / 22050)), 0)
            for i in range(22050 * seconds)
        )
        w.writeframes(frames)
    return buf.getvalue()


async def test_p51_with_underscores_is_not_duplicated_by_search(tmp_path: Path) -> None:
    """P51 with underscores; search returns spaces; should deduplicate."""
    p51_val = "Parus_major_song.ogg"
    p51_underscore = {"claims": {"P51": [{"mainsnak": {"datavalue": {"value": p51_val}}}]}}
    search_with_spaces = {"query": {"search": [{"title": "File:Parus major song.ogg"}]}}
    info_normalized = {
        "query": {
            "normalized": [
                {"from": "File:Parus_major_song.ogg", "to": "File:Parus major song.ogg"}
            ],
            "pages": [
                {
                    "title": "File:Parus major song.ogg",
                    "imageinfo": [
                        {
                            "url": "https://upload.wikimedia.org/a.ogg",
                            "descriptionurl": (
                                "https://commons.wikimedia.org/wiki/File:Parus_major_song.ogg"
                            ),
                            "mime": "application/ogg",
                            "duration": 25.0,
                            "extmetadata": {
                                "LicenseShortName": {"value": "CC BY-SA 4.0"},
                                "Artist": {"value": "Anna"},
                            },
                        }
                    ],
                    "categories": [{"title": "Category:Parus major"}],
                }
            ],
        }
    }
    http = Routed(
        {
            "wbgetclaims": p51_underscore,
            "list=search": search_with_spaces,
            "prop=imageinfo": info_normalized,
        }
    )
    client = CommonsAudioClient(
        cache=Cache(tmp_path), http=ThrottledHttp(get_text=http, min_interval=0)
    )
    candidates = await client.candidates("Q25485", "Parus major")
    assert len(candidates) == 1
    assert candidates[0].from_wikidata is True
    assert candidates[0].title == "File:Parus major song.ogg"


async def test_candidates_put_wikidata_first_and_parse_metadata(tmp_path: Path) -> None:
    http = Routed({"wbgetclaims": P51, "list=search": SEARCH, "prop=imageinfo": INFO})
    client = CommonsAudioClient(
        cache=Cache(tmp_path), http=ThrottledHttp(get_text=http, min_interval=0)
    )
    candidates = await client.candidates("Q25485", "Parus major")
    assert [c.title for c in candidates] == ["File:Parus major song.ogg", "File:De-Kohlmeise.ogg"]
    first = candidates[0]
    assert first.from_wikidata is True
    assert first.duration == 31.5
    assert first.author == "Anna"
    assert first.license == "CC BY-SA 4.0"
    assert first.categories == ("Category:Parus major",)
    assert candidates[1].from_wikidata is False
    await client.candidates("Q25485", "Parus major")
    assert len(http.urls) == 3


async def test_no_candidates_means_no_info_request(tmp_path: Path) -> None:
    http = Routed({"wbgetclaims": {"claims": {}}, "list=search": {"query": {"search": []}}})
    client = CommonsAudioClient(
        cache=Cache(tmp_path), http=ThrottledHttp(get_text=http, min_interval=0)
    )
    assert await client.candidates("Q1", "Parus major") == []
    assert len(http.urls) == 2


async def test_download_is_cached(tmp_path: Path) -> None:
    calls = 0

    async def get_bytes(url: str) -> bytes:
        nonlocal calls
        calls += 1
        return b"OggS"

    client = CommonsAudioClient(
        cache=Cache(tmp_path), http=ThrottledHttp(get_bytes=get_bytes, min_interval=0)
    )
    assert await client.download("Q25485", BASE) == b"OggS"
    assert await client.download("Q25485", BASE) == b"OggS"
    assert calls == 1


def test_convert_trims_to_20_seconds_mono_mp3(tmp_path: Path) -> None:
    out = tmp_path / "Q1" / "voice.mp3"
    convert_to_mp3(_wav(25), out)
    assert out.exists()
    assert 19.5 <= probe_seconds(out) <= 20.5
    assert out.stat().st_size < 250_000
    info = subprocess.run(
        [imageio_ffmpeg.get_ffmpeg_exe(), "-hide_banner", "-i", str(out)],
        capture_output=True,
        text=True,
        check=False,
    ).stderr
    assert "mono" in info


def test_convert_raises_readable_error_on_bad_input(tmp_path: Path) -> None:
    """convert_to_mp3 with invalid audio should raise RuntimeError with 'ffmpeg' in message."""
    out = tmp_path / "Q1" / "voice.mp3"
    try:
        convert_to_mp3(b"not audio at all", out)
        raise AssertionError("Should have raised RuntimeError")
    except RuntimeError as e:
        assert "ffmpeg" in str(e).lower()


def test_an_attribution_licence_without_an_author_is_rejected() -> None:
    """Minor 11 (final review 2026-10-06): CC BY and CC BY-SA require the author's name in
    the credit; CC0 and public domain do not."""
    for licence in ("CC BY 4.0", "CC BY-SA 3.0"):
        reason = rejection(replace(BASE, license=licence, author=None), "Parus major")
        assert reason is not None and "upphovsperson" in reason
    assert rejection(replace(BASE, license="CC0", author=None), "Parus major") is None
    assert rejection(replace(BASE, license="Public domain", author=None), "Parus major") is None
