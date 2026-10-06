"""Tests for web/sources_step.py with fake Wikipedia, GBIF and Commons."""

from __future__ import annotations

import io
import math
import struct
import wave
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path

import pytest

from birdy_fetcher.web.audio import AudioCandidate
from birdy_fetcher.web.datamod import Counts
from birdy_fetcher.web.record import load_record, new_record, record_path, save_record
from birdy_fetcher.web.sources_step import (
    SlugCollisionError,
    SourceClients,
    SourcesOptions,
    run_sources,
)
from birdy_fetcher.web.wiki_full import WikiArticle

from .web_repo import make_repo

NOW = datetime(2026, 10, 2, tzinfo=UTC)
ARTICLES = {
    "sv": WikiArticle("sv", "Talgoxe", "1", "Talgoxen är cirka 14 centimeter lång."),
    "en": WikiArticle("en", "Great tit", "2", "The great tit is about 14 cm long."),
    "de": WikiArticle("de", "Kohlmeise", "3", "Die Kohlmeise ist etwa 14 cm lang."),
}
RECORDING = AudioCandidate(
    title="File:Parus major song.ogg",
    url="https://upload.wikimedia.org/a.ogg",
    page_url="https://commons.wikimedia.org/wiki/File:Parus_major_song.ogg",
    mime="application/ogg",
    duration=25.0,
    license="CC BY-SA 4.0",
    author="Anna",
    categories=("Category:Parus major",),
    from_wikidata=True,
)


def _wav(seconds: int) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(8000)
        w.writeframes(
            b"".join(struct.pack("<h", int(8000 * math.sin(i / 3))) for i in range(8000 * seconds))
        )
    return buf.getvalue()


@dataclass
class FakeWiki:
    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]:
        if qid == "Q9":
            raise RuntimeError("Wikipedia svarar inte")
        return ARTICLES


@dataclass
class FakeGbif:
    async def taxon_key(self, qid: str, scientific: str, *, refresh: bool = False) -> int | None:
        return 7

    async def counts(self, qid: str, taxon_key: int, *, refresh: bool = False) -> Counts:
        return Counts([10] * 12, {"SE-I": 50}, 1200)

    async def all_birds(self, *, refresh: bool = False) -> Counts:
        return Counts([100] * 12, {"SE-I": 100}, 99_000)

    async def swedish_red_list(
        self, qid: str, scientific: str, taxon_key: int, *, refresh: bool = False
    ) -> str | None:
        return "VU"


@dataclass
class FakeAudio:
    asked: list[str] = field(default_factory=list)

    async def candidates(
        self, qid: str, scientific: str, *, refresh: bool = False
    ) -> list[AudioCandidate]:
        self.asked.append(qid)
        return [RECORDING]

    async def download(
        self, qid: str, candidate: AudioCandidate, *, refresh: bool = False
    ) -> bytes:
        return _wav(3)


def _clients(audio: FakeAudio | None = None) -> SourceClients:
    return SourceClients(wiki=FakeWiki(), gbif=FakeGbif(), audio=audio or FakeAudio())


async def test_sources_write_a_pending_record_with_every_source(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")], sound=frozenset({"Q1"}))
    outcomes = await run_sources(paths, SourcesOptions(), clients=_clients(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["status"] == "pending"
    assert record["publish"] is False
    assert record["slug"] == {"sv": "talgoxe", "en": "great-tit"}
    assert record["swedishRedList"] == "VU"
    assert record["identifiable"] == {"photo": False, "sound": True}
    assert record["data"]["months"] == [100] * 12
    assert record["wikipedia"]["de"] == {"title": "Kohlmeise", "revision": "3"}
    assert record["audio"]["file"] == "Q1/voice.mp3"
    assert record["audio"]["trimmed"] is True
    assert (paths.images_out / "Q1" / "voice.mp3").exists()
    assert (paths.images_out / "Q1" / "hero.webp").exists()
    assert any(p.name.startswith("web-sources-") for p in paths.reports.iterdir())


async def test_a_forced_rerun_keeps_facts_review_and_text(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    seeded["facts"] = [{"id": "f01"}]
    seeded["text"] = {"sv": {}}
    save_record(record_path(paths.data_out, "Q1"), seeded)
    outcomes = await run_sources(paths, SourcesOptions(force=True), clients=_clients(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["facts"] == [{"id": "f01"}]
    assert record["text"] == {"sv": {}}
    assert record["wikipedia"]["sv"] == {"title": "Talgoxe", "revision": "1"}


@pytest.mark.parametrize("refresh", [False, True])
async def test_a_species_with_facts_is_frozen_without_force(tmp_path: Path, refresh: bool) -> None:
    """I3 (final review 2026-10-06): the facts quote one Wikipedia revision, the status was
    checked against one Artportalen pull and V4 heard one recording. A plain rerun (or a
    cold .cache) must not swap any of them under the facts; only --force does."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    seeded["facts"] = [{"id": "f01"}]
    seeded["wikipedia"] = {"sv": {"title": "Talgoxe", "revision": "0"}}
    seeded["audio"] = {"file": "Q1/voice.mp3", "sourceUrl": "checked"}
    seeded["data"] = {"totalReports": 900, "statusSignal": {"contradicts": "x"}}
    path = record_path(paths.data_out, "Q1")
    save_record(path, seeded)
    before = path.read_bytes()
    audio = FakeAudio()
    outcomes = await run_sources(
        paths, SourcesOptions(refresh=refresh), clients=_clients(audio), now=NOW
    )
    assert [o.status for o in outcomes] == ["skipped"]
    assert path.read_bytes() == before
    assert audio.asked == []
    assert not (paths.images_out / "Q1" / "voice.mp3").exists()


async def test_a_verified_species_is_frozen_without_force_even_without_refresh(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    seeded["verification"] = {"method": "auto", "at": "2026-11-01", "model": "x"}
    path = record_path(paths.data_out, "Q1")
    save_record(path, seeded)
    before = path.read_bytes()
    outcomes = await run_sources(paths, SourcesOptions(), clients=_clients(), now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]
    assert path.read_bytes() == before


async def test_a_forced_rerun_recomputes_the_status_signal_from_the_new_data(
    tmp_path: Path,
) -> None:
    """build_data resets statusSignal; with a status fact in the record, --force writes the
    contradiction the new data gives (here: an absent species with 1200 reports)."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    seeded["facts"] = [{"id": "s01", "topic": "status", "value": "absent", "sv": "x"}]
    save_record(record_path(paths.data_out, "Q1"), seeded)
    await run_sources(paths, SourcesOptions(force=True), clients=_clients(), now=NOW)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert "1200 rapporter" in record["data"]["statusSignal"]["contradicts"]


async def test_one_failing_species_does_not_stop_the_run(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit"), ("Q9", "Gök", "Cuckoo")])
    outcomes = await run_sources(paths, SourcesOptions(), clients=_clients(), now=NOW)
    assert {o.qid: o.status for o in outcomes} == {"Q1": "ok", "Q9": "failed"}
    assert load_record(record_path(paths.data_out, "Q9")) is None


async def test_dry_run_writes_nothing(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    outcomes = await run_sources(paths, SourcesOptions(dry_run=True), clients=_clients(), now=NOW)
    assert [o.status for o in outcomes] == ["dry-run"]
    assert not paths.data_out.exists()


async def test_reviewed_species_are_not_refreshed_without_force(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    seeded["verification"] = {
        "method": "auto",
        "at": "2026-11-01",
        "model": "x",
        "spotChecked": False,
    }
    save_record(record_path(paths.data_out, "Q1"), seeded)
    outcomes = await run_sources(paths, SourcesOptions(refresh=True), clients=_clients(), now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]


async def test_a_struck_recording_is_not_fetched_again(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    seeded["review"] = {"audioStruck": True}
    save_record(record_path(paths.data_out, "Q1"), seeded)
    audio = FakeAudio()
    await run_sources(paths, SourcesOptions(), clients=_clients(audio), now=NOW)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert "audio" not in record
    assert audio.asked == []


async def test_slug_collision_stops_before_any_request(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit"), ("Q2", "Talgoxe", "Tit")])
    with pytest.raises(SlugCollisionError):
        await run_sources(paths, SourcesOptions(), clients=_clients(), now=NOW)
