"""Tests for web/sources_step.py with fake Wikipedia, GBIF and Commons."""

from __future__ import annotations

import hashlib
import io
import math
import struct
import wave
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path

import pytest

from birdy_fetcher.web.audio import AudioCandidate, audio_record
from birdy_fetcher.web.datamod import Counts
from birdy_fetcher.web.facts import data_facts
from birdy_fetcher.web.record import (
    audio_id,
    facts_hash,
    load_record,
    new_record,
    record_path,
    save_record,
)
from birdy_fetcher.web.sources_step import (
    SlugCollisionError,
    SourceClients,
    SourcesOptions,
    run_sources,
)
from birdy_fetcher.web.wiki_full import WikiArticle

from .test_web_source import _write
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
    # The model's facts stay; only the data facts are rebuilt from the new data (A4).
    assert record["facts"] == [{"id": "f01"}, *data_facts(record)]
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


async def test_a_forced_rerun_refuses_a_published_species(tmp_path: Path) -> None:
    """Follow-up 6 (wave A review): new sources under a live page would publish quotes,
    data and a recording nothing has checked; unpublish first, like facts and verify."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    seeded["facts"] = [{"id": "f01"}]
    seeded["publish"] = True
    path = record_path(paths.data_out, "Q1")
    save_record(path, seeded)
    before = path.read_bytes()
    audio = FakeAudio()
    outcomes = await run_sources(
        paths, SourcesOptions(force=True), clients=_clients(audio), now=NOW
    )
    assert [o.status for o in outcomes] == ["failed"]
    assert outcomes[0].errors == ["publicerad: sätt publish: false först"]
    assert path.read_bytes() == before
    assert audio.asked == []


async def test_a_forced_rerun_drops_the_verification_of_the_old_sources(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    seeded["facts"] = [{"id": "f01"}]
    seeded["verification"] = {"method": "auto", "at": "2026-11-01", "model": "x"}
    seeded["generated"] = {"facts": {"model": "m"}, "verify": {"factsHash": "abc"}}
    save_record(record_path(paths.data_out, "Q1"), seeded)
    await run_sources(paths, SourcesOptions(force=True), clients=_clients(), now=NOW)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert "verification" not in record
    assert record["generated"] == {"facts": {"model": "m"}}


async def test_a_forced_rerun_keeps_a_kept_recording_only_if_it_is_the_same(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit"), ("Q2", "Gök", "Cuckoo")])
    same = {**audio_record(RECORDING, "Q1"), "sha256": hashlib.sha256(_wav(3)).hexdigest()}
    for qid, kept in (("Q1", audio_id(same)), ("Q2", "an-older-recording")):
        seeded = new_record(qid)
        seeded["facts"] = [{"id": "f01"}]
        seeded["review"] = {"audioKept": kept}
        save_record(record_path(paths.data_out, qid), seeded)
    await run_sources(paths, SourcesOptions(force=True), clients=_clients(), now=NOW)
    q1 = load_record(record_path(paths.data_out, "Q1"))
    q2 = load_record(record_path(paths.data_out, "Q2"))
    assert q1 is not None and q2 is not None
    assert q1["review"]["audioKept"] == audio_id(same)
    assert "audioKept" not in q2["review"]


async def test_a_new_file_under_the_same_title_is_a_different_recording(tmp_path: Path) -> None:
    """A1 (wave A review): `audio_id` covered only the metadata, so a new Commons version
    under the same title kept Albin's old `behåll`. The raw download's hash is part of the
    `audio` object now, so different bytes are a different recording."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    old = {**audio_record(RECORDING, "Q1"), "sha256": hashlib.sha256(b"old file").hexdigest()}
    seeded = new_record("Q1")
    seeded["facts"] = [{"id": "f01"}]
    seeded["review"] = {"audioKept": audio_id(old)}
    save_record(record_path(paths.data_out, "Q1"), seeded)
    await run_sources(paths, SourcesOptions(force=True), clients=_clients(), now=NOW)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["audio"]["sha256"] == hashlib.sha256(_wav(3)).hexdigest()
    assert {k: v for k, v in record["audio"].items() if k != "sha256"} == audio_record(
        RECORDING, "Q1"
    )
    assert audio_id(record["audio"]) != audio_id(old)
    assert "audioKept" not in record["review"]


async def test_a_forced_rerun_forgets_a_confirmed_status(tmp_path: Path) -> None:
    """A3: Albin kept a status against the OLD report data; new data can contradict it in a
    new way, so the confirmation does not survive `--force`."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    seeded["facts"] = [{"id": "s01", "topic": "status", "value": "resident", "sv": "x"}]
    seeded["review"] = {"statusConfirmed": True, "wave": 1}
    save_record(record_path(paths.data_out, "Q1"), seeded)
    await run_sources(paths, SourcesOptions(force=True), clients=_clients(), now=NOW)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["review"] == {"wave": 1}


async def test_a_forced_rerun_drops_the_flags_of_the_old_sources(tmp_path: Path) -> None:
    """The flags were raised against the old articles, data and recording: `web sheet`
    must not export them again before `web verify` has run on the new sources."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    seeded["facts"] = [{"id": "f01"}]
    seeded["flags"] = [{"check": "V2", "factId": "f01", "message": "x"}]
    save_record(record_path(paths.data_out, "Q1"), seeded)
    await run_sources(paths, SourcesOptions(force=True), clients=_clients(), now=NOW)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert "flags" not in record


async def test_a_forced_rerun_rebuilds_the_data_facts_from_the_new_data(tmp_path: Path) -> None:
    """A4: the d-facts are code's own sentences from the report data and the red list; after
    `--force` they must say what the new data says, so the facts hash (and every text
    written from the old one) moves with it."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    seeded = new_record("Q1")
    f01 = {"id": "f01", "topic": "appearance", "sv": "Svart huvud.", "sources": []}
    seeded["facts"] = [
        f01,
        {"id": "d01", "topic": "data", "source": "artportalen", "sv": "Gammal mening."},
        {"id": "d02", "topic": "data", "source": "rodlistan", "sv": "Inte rödlistad."},
    ]
    path = record_path(paths.data_out, "Q1")
    save_record(path, seeded)
    old_hash = facts_hash(seeded)
    await run_sources(paths, SourcesOptions(force=True), clients=_clients(), now=NOW)
    record = load_record(path)
    assert record is not None
    assert record["facts"][0] == f01
    assert record["facts"][1:] == data_facts(record)
    assert record["facts"][-1]["sv"] == "Svenska rödlistan 2025: Sårbar (VU)."
    assert all(f["sv"] != "Gammal mening." for f in record["facts"])
    assert facts_hash(record) != old_hash


async def test_marginalia_are_written_without_dashes(tmp_path: Path) -> None:
    """I7: the app's approved marginalia for koboltmes have em dashes, which fail fas 2's
    dash guard for every page."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _write(
        paths.species_root,
        "Q1",
        "Talgoxe",
        "Great Tit",
        "approved",
        marginalia='marginalia:\n  sv: "Liten \\u2014 kvick."\n  en: "Small \\u2013 quick."\n',
    )
    await run_sources(paths, SourcesOptions(), clients=_clients(), now=NOW)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["marginalia"] == {"sv": "Liten, kvick.", "en": "Small, quick."}
