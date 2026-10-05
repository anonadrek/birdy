"""Tests for web/verify_step.py: V1 to V4 tied together, and the run."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

import pytest

from birdy_fetcher.web.audio_check import AudioCheckResult
from birdy_fetcher.web.facts import FactSheetOutput
from birdy_fetcher.web.paths import WebPaths
from birdy_fetcher.web.record import load_record, merge_sources, record_path, save_record
from birdy_fetcher.web.verify import FactVerdict, FactVerifyOutput
from birdy_fetcher.web.verify_step import VerifyOptions, run_verify
from birdy_fetcher.web.wiki_full import WikiArticle

from .test_web_facts import ARTICLES, GOOD, STATUS, _fact
from .web_fakes import FakeJsonClient, reply
from .web_repo import make_repo

NOW = datetime(2026, 11, 10, tzinfo=UTC)


@dataclass
class FakeWiki:
    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]:
        return ARTICLES


def _seed(
    paths: WebPaths, qid: str, *, with_audio: bool = False, identifiable_sound: bool = True
) -> None:
    record = merge_sources(
        None,
        qid,
        {
            "names": {"sv": "Talgoxe", "en": "Great Tit", "scientific": "Parus major"},
            "wikipedia": {"sv": {"title": "Talgoxe", "revision": "1"}},
            "identifiable": {"photo": True, "sound": identifiable_sound},
            "audio": {"file": f"{qid}/voice.mp3", "sourceUrl": "x"} if with_audio else None,
        },
    )
    record["facts"] = [
        {
            "id": "f01",
            "topic": "appearance",
            "sv": "Svart huvud med vita kinder.",
            "sources": [{"article": "sv", "quote": "svart huvud med vita kinder"}],
        },
        {
            "id": "f04",
            "topic": "voice",
            "sv": "Sången är ett ringande ti-ta ti-ta.",
            "sources": [{"article": "sv", "quote": "Sången är ett ringande ti-ta ti-ta"}],
        },
        {
            "id": "f05",
            "topic": "habitat",
            "sv": "Talgoxen lever i skog, parker och trädgårdar.",
            "sources": [{"article": "sv", "quote": "lever i skog, parker och trädgårdar"}],
        },
        {
            "id": "s01",
            "topic": "status",
            "value": "resident",
            "sv": "Stannfågel",
            "sources": [{"article": "sv", "quote": "Den är stannfågel i hela Sverige"}],
        },
        {"id": "d01", "topic": "data", "source": "artportalen", "sv": "Rapporteras året runt."},
    ]
    record["data"] = {"statusSignal": {"contradicts": None}}
    record["generated"] = {"facts": {"model": "claude-opus-5"}}
    if with_audio:
        (paths.images_out / qid).mkdir(parents=True, exist_ok=True)
        (paths.images_out / qid / "voice.mp3").write_bytes(b"id3")
    save_record(record_path(paths.data_out, qid), record)


def _verdicts(**unsupported: str) -> FactVerifyOutput:
    # Adaptation (2026-10-05): the task doc's `ids` only covered {"f01", "f04", "f05"},
    # but FactChecker.check() (verify.py) treats EVERY non-"data" fact as checkable,
    # including the status fact (s01) and any size fact a test appends (f06) — a fact the
    # fake model gives no verdict for is struck as "unsupported" (per its own docstring).
    # The real verify-v1.md prompt asks for a verdict on every fact shown to it, so a
    # complete fake reply must cover s01 and f06 too, or V2/V3's checks never see them.
    ids = {"f01", "f04", "f05", "f06", "s01"}
    return FactVerifyOutput(
        verdicts=[
            FactVerdict(
                fact_id=i,
                verdict="supported" if i not in unsupported else "unsupported",
                reason=unsupported.get(i, ""),
            )
            for i in ids
        ]
    )


async def test_a_clean_fact_sheet_is_verified_with_no_flags(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["flags"] == []
    assert record["generated"]["verify"]["model"] == "claude-sonnet-5"
    # Ändrat 2026-10-05 (b): sätts direkt, utan att vänta på web import.
    assert record["verification"]["spotChecked"] is False
    assert record["verification"]["model"] == "claude-sonnet-5"


async def test_a_struck_required_topic_is_retried_once(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    # Adaptation (2026-10-05): the task doc's retry_sheet had only 7 facts (GOOD + the new
    # voice fact). facts.py's MIN_FACTS = 10 then makes check_fact_sheet's own FactCheck.retry
    # non-empty, so FactExtractor.extract() (facts_step.py) loops for a SECOND model call that
    # this test never queued ("pop from empty list"). Padded to 10 facts, same pattern as
    # test_web_facts_step.py's own TEN fixture.
    retry_sheet = FactSheetOutput(
        facts=[
            *GOOD,
            _fact("voice", "Sången hörs på långt håll.", "Sången är ett ringande ti-ta ti-ta"),
            *[
                _fact("appearance", f"Svart band på buken {i}.", "ett bredare svart band på buken")
                for i in range(3)
            ],
        ],
        sweden_status=STATUS,
    )
    client = FakeJsonClient(
        [reply(_verdicts(f04="citatet nämner inget avstånd")), reply(retry_sheet)]
    )
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert any(f["topic"] == "voice" for f in record["facts"])


async def test_a_number_disagreement_becomes_a_v2_flag(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    record["facts"].append(
        {
            "id": "f06",
            "topic": "size",
            "sv": "Cirka 25 cm lång.",
            "sources": [{"article": "sv", "quote": "cirka 25 cm lång"}],
        }
    )
    save_record(record_path(paths.data_out, "Q1"), record)

    # Adaptation (2026-10-05): the shared ARTICLES fixture (test_web_facts.py) only has a
    # "sv" and a "de" article, and the "de" one states no measurement at all, so number_flag
    # (verify.py) never has another article's number to disagree with ("if not others:
    # continue"). Add an "en" article with a conflicting length so V2 has something to catch,
    # the same shape as verify.py's own test_number_flag_catches_a_real_disagreement.
    articles_with_conflict = {**ARTICLES, "en": WikiArticle("en", "x", "1", "About 14 cm long.")}

    @dataclass
    class ConflictingWiki:
        async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]:
            return articles_with_conflict

    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(
        paths, VerifyOptions(), client=client, wiki=ConflictingWiki(), now=NOW
    )
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert [f["check"] for f in record["flags"]] == ["V2"]


async def test_audio_is_struck_silently_when_the_model_covers_the_species_and_misses(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True, identifiable_sound=True)

    # Adaptation (2026-10-05): classify_clip's `to_wav`/`run` keyword defaults (audio_check.py)
    # are bound once at module-definition time (confirmed against Task 14d's own
    # test_web_audio_check.py, which always passes `to_wav=`/`run=` explicitly for exactly
    # this reason), so patching `audio_check._default_to_wav` / `subprocess.run` afterwards
    # never reaches a call that omits them, as verify_step.py's call does. Patching the
    # `classify_clip` name imported into verify_step's own namespace is the one monkeypatch
    # target that is looked up fresh at call time, and keeps ffmpeg/flexref out of the test.
    def fake_classify_clip(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        return AudioCheckResult(windows=[{"startSec": 0.0, "top": []}])

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", fake_classify_clip)
    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert "audio" not in record
    assert record["review"]["audioStruck"] is True
    assert record["flags"] == []


async def test_audio_the_model_does_not_cover_becomes_a_v4_flag_and_is_kept(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True, identifiable_sound=False)

    # Same adaptation as the test above: classify_clip is called unconditionally even when
    # the species is not covered (its result is simply discarded by audio_verdict), so it
    # still needs a deterministic fake instead of the real ffmpeg/flexref call.
    def fake_classify_clip(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        return AudioCheckResult(windows=[{"startSec": 0.0, "top": []}])

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", fake_classify_clip)
    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert "audio" in record
    assert [f["check"] for f in record["flags"]] == ["V4"]


async def test_a_current_verification_is_skipped_unless_forced(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    client = FakeJsonClient([reply(_verdicts())])
    await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    again = await run_verify(
        paths, VerifyOptions(), client=FakeJsonClient([]), wiki=FakeWiki(), now=NOW
    )
    assert [o.status for o in again] == ["skipped"]
