"""Tests for web/verify_step.py: V1 to V4 tied together, and the run."""

from __future__ import annotations

import threading
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path

import pytest

from birdy_fetcher.web.audio import AudioCandidate
from birdy_fetcher.web.audio_check import AudioCheckFailed, AudioCheckResult
from birdy_fetcher.web.facts import FactSheetOutput
from birdy_fetcher.web.facts_step import FactsOptions
from birdy_fetcher.web.llm import MODELS
from birdy_fetcher.web.paths import WebPaths
from birdy_fetcher.web.record import (
    audio_id,
    load_record,
    merge_sources,
    record_path,
    save_record,
    sweep_orphan_voices,
)
from birdy_fetcher.web.verify import FactVerdict, FactVerifyOutput
from birdy_fetcher.web.verify_step import AudioPreflightFailed, VerifyOptions, run_verify
from birdy_fetcher.web.wiki_full import WikiArticle

from .test_web_facts import ARTICLES, GOOD, STATUS, _fact
from .web_fakes import FakeJsonClient, reply
from .web_repo import make_repo

NOW = datetime(2026, 11, 10, tzinfo=UTC)
OK_PREFLIGHT = AudioCheckResult(windows=[{"startSec": 0.0, "top": []}])


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


def _all_supported(*ids: str, unsupported: dict[str, str] | None = None) -> FactVerifyOutput:
    """A V1 reply covering exactly `ids` (a retried sheet's ids differ from `_verdicts`'
    fixed set), supported unless the id is a key in `unsupported`."""
    unsupported = unsupported or {}
    return FactVerifyOutput(
        verdicts=[
            FactVerdict(
                fact_id=i,
                verdict="unsupported" if i in unsupported else "supported",
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


async def test_v1_is_told_which_species_it_checks(tmp_path: Path) -> None:
    """C1 (final review 2026-10-06): the prompt only counts quotes about the species being
    checked, so the V1 call must name it."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    client = FakeJsonClient([reply(_verdicts())])
    await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    user = client.calls[0][0]["content"]
    assert isinstance(user, str)
    assert user.startswith("Species: Talgoxe / Great Tit (Parus major)")
    assert '<fact id="s01" topic="status">' in user


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
    # f01-f06 = GOOD (size, sex_age, voice, habitat, appearance, lookalike), f07 = the new
    # voice fact, f08-f10 = the three extra appearance facts, s01 = the status.
    retried_ids = [f"f{i:02d}" for i in range(1, 11)] + ["s01"]
    # Item 3 (2026-10-05 review fix): the retried sheet gets its own V1 pass, so this needs a
    # third reply (the checker's second call) or the fake client pops from an empty list.
    client = FakeJsonClient(
        [
            reply(_verdicts(f04="citatet nämner inget avstånd")),
            reply(retry_sheet),
            reply(_all_supported(*retried_ids)),
        ]
    )
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert any(f["topic"] == "voice" for f in record["facts"])
    assert client.schemas.count("FactVerifyOutput") == 2


async def test_a_fact_struck_in_the_retried_sheet_does_not_survive(tmp_path: Path) -> None:
    """Item 3: the retried sheet gets its own V1 pass too, so a fact it invents is struck
    out there just like the very first pass — and the checker ran twice to catch it."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
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
    # f08 is the first of the three extra appearance facts ("Svart band på buken 0.");
    # appearance is still covered by f05 and f09/f10 once f08 is struck, so this is not fatal.
    retried_ids = [f"f{i:02d}" for i in range(1, 11)] + ["s01"]
    client = FakeJsonClient(
        [
            reply(_verdicts(f04="citatet nämner inget avstånd")),
            reply(retry_sheet),
            reply(
                _all_supported(*retried_ids, unsupported={"f08": "hittar inte citatet i artikeln"})
            ),
        ]
    )
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert not any(f["sv"] == "Svart band på buken 0." for f in record["facts"])
    assert client.schemas.count("FactVerifyOutput") == 2


async def test_missing_after_the_second_v1_pass_also_fails(tmp_path: Path) -> None:
    """Item 3: if V1 strikes a required-topic fact in the *retried* sheet too (here f04,
    the only habitat fact in the retry), that is still fatal — same as today's fatal path."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
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
    retried_ids = [f"f{i:02d}" for i in range(1, 11)] + ["s01"]
    client = FakeJsonClient(
        [
            reply(_verdicts(f04="citatet nämner inget avstånd")),
            reply(retry_sheet),
            reply(
                _all_supported(*retried_ids, unsupported={"f04": "hittar inte citatet i artikeln"})
            ),
        ]
    )
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["status"] == "failed"
    assert not any(f["id"] == "f04" for f in record["facts"])


async def test_a_fatal_retry_still_saves_only_the_post_strike_facts(tmp_path: Path) -> None:
    """Item 4: a failed path must never keep a fact V1 already struck in the saved record —
    only `kept` (post-first-strike) is saved alongside the failed status."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    fatal_retry_sheet = FactSheetOutput(facts=GOOD[:2], sweden_status=None)
    client = FakeJsonClient(
        [
            reply(_verdicts(f04="citatet nämner inget avstånd")),
            reply(fatal_retry_sheet),
            reply(fatal_retry_sheet),
        ]
    )
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["status"] == "failed"
    assert not any(f["id"] == "f04" for f in record["facts"])
    assert any(f["id"] == "f01" for f in record["facts"])


async def test_a_struck_status_fact_becomes_a_v1_flag_instead_of_vanishing(
    tmp_path: Path,
) -> None:
    """Item 1: status is not a required topic, so striking s01 must not pass through
    unnoticed with zero flags and a null status."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    client = FakeJsonClient([reply(_verdicts(s01="ingen av källorna säger att den är stannfågel"))])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert not any(f["id"] == "s01" for f in record["facts"])
    assert record["flags"] == [
        {
            "check": "V1",
            "factId": "s01",
            "message": (
                "Statusen i Sverige ströks av faktakontrollen: ingen av källorna säger att "
                "den är stannfågel. Bestäm status eller lämna tom."
            ),
        }
    ]
    assert "verification" not in record


async def test_an_audio_check_failure_is_a_distinct_v4_flag_and_keeps_the_recording(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """Item 2: a technical V4 failure (AudioCheckFailed) must read differently from a
    genuinely-uncovered species, and must not silently drop the recording."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True, identifiable_sound=True)

    def failing_classify_clip(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        if mp3_path.name != "voice.mp3":
            return OK_PREFLIGHT  # the model itself runs; only this file fails
        raise AudioCheckFailed("ljudmodellen kraschade")

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", failing_classify_clip)
    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert "audio" in record
    assert record["flags"] == [
        {
            "check": "V4",
            "factId": None,
            "message": (
                "Ljudmodellen kunde inte köras (ljudmodellen kraschade). Lyssna och besluta, "
                "eller kör om när felet är åtgärdat."
            ),
        }
    ]


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
    # Minor 11: a struck recording leaves no file behind for a later commit to pick up.
    assert not (paths.images_out / "Q1" / "voice.mp3").exists()


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


async def test_a_rerun_that_now_has_flags_clears_a_stale_verification(tmp_path: Path) -> None:
    """C1 (review fix 2026-10-06): a species that auto-passed before must not keep that
    `verification` once a rerun finds something to flag -- otherwise `web write` would
    trust a verification that no longer matches what V2/V3 just found."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    clean = FakeJsonClient([reply(_verdicts())])
    await run_verify(paths, VerifyOptions(), client=clean, wiki=FakeWiki(), now=NOW)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record.get("verification") is not None

    record["facts"].append(
        {
            "id": "f06",
            "topic": "size",
            "sv": "Cirka 25 cm lång.",
            "sources": [{"article": "sv", "quote": "cirka 25 cm lång"}],
        }
    )
    save_record(record_path(paths.data_out, "Q1"), record)
    articles_with_conflict = {**ARTICLES, "en": WikiArticle("en", "x", "1", "About 14 cm long.")}

    @dataclass
    class ConflictingWiki:
        async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]:
            return articles_with_conflict

    flagged = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(
        paths, VerifyOptions(), client=flagged, wiki=ConflictingWiki(), now=NOW
    )
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["flags"] != []
    assert "verification" not in record


async def test_a_fatal_v1_retry_clears_a_stale_verification(tmp_path: Path) -> None:
    """C1 (review fix 2026-10-06): the failure paths must also clear a `verification` that
    was left over from before this rerun."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    record["verification"] = {
        "method": "auto",
        "at": "2026-11-01",
        "model": "claude-sonnet-5",
        "spotChecked": False,
    }
    save_record(record_path(paths.data_out, "Q1"), record)
    fatal_retry_sheet = FactSheetOutput(facts=GOOD[:2], sweden_status=None)
    client = FakeJsonClient(
        [
            reply(_verdicts(f04="citatet nämner inget avstånd")),
            reply(fatal_retry_sheet),
            reply(fatal_retry_sheet),
        ]
    )
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert "verification" not in record


async def test_a_current_verification_is_skipped_unless_forced(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    client = FakeJsonClient([reply(_verdicts())])
    await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    again = await run_verify(
        paths, VerifyOptions(), client=FakeJsonClient([]), wiki=FakeWiki(), now=NOW
    )
    assert [o.status for o in again] == ["skipped"]


async def test_a_published_species_refuses_verify_even_with_force(tmp_path: Path) -> None:
    """N5 (review fix 2026-10-06): verify's V1-retry path can null a species' text and set
    status pending while publish stays true, breaking the fas 2 build -- refuse outright
    instead, with no model call."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    record["publish"] = True
    save_record(record_path(paths.data_out, "Q1"), record)
    client = FakeJsonClient([])
    outcomes = await run_verify(
        paths, VerifyOptions(force=True), client=client, wiki=FakeWiki(), now=NOW
    )
    assert [o.status for o in outcomes] == ["failed"]
    assert outcomes[0].errors == ["publicerad: sätt publish: false först"]
    assert client.calls == []


def _retry_sheet() -> FactSheetOutput:
    return FactSheetOutput(
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


RETRIED_IDS = [f"f{i:02d}" for i in range(1, 11)] + ["s01"]


async def test_the_v1_retry_uses_the_facts_model_and_effort(tmp_path: Path) -> None:
    """Minor 3 (final review 2026-10-06): the re-extraction is a fact sheet like any other,
    so it uses the model and effort chosen for `web facts` (R3), not a hard-coded opus/high,
    and `generated.facts` says what actually wrote the sheet now in the record."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    client = FakeJsonClient(
        [
            reply(_verdicts(f04="citatet nämner inget avstånd")),
            reply(_retry_sheet()),
            reply(_all_supported(*RETRIED_IDS)),
        ]
    )
    options = VerifyOptions(facts_model_key="sonnet", facts_effort="medium")
    outcomes = await run_verify(paths, options, client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    retry = client.schemas.index("FactSheetOutput")
    assert client.models[retry] == "claude-sonnet-5"
    assert client.efforts[retry] == "medium"
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["generated"]["facts"] == {
        "model": "claude-sonnet-5",
        "prompt": "facts-v1",
        "effort": "medium",
        "at": NOW.isoformat(),
        "v1Retry": True,
    }


async def test_the_v1_retry_defaults_to_the_records_own_facts_settings(tmp_path: Path) -> None:
    """Follow-up 7 (wave A review): without --facts-model/--facts-effort the retry writes
    with whatever wrote the sheet it replaces."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    record["generated"]["facts"] = {"model": "claude-sonnet-5", "effort": "medium"}
    save_record(record_path(paths.data_out, "Q1"), record)
    client = FakeJsonClient(
        [
            reply(_verdicts(f04="citatet nämner inget avstånd")),
            reply(_retry_sheet()),
            reply(_all_supported(*RETRIED_IDS)),
        ]
    )
    await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    retry = client.schemas.index("FactSheetOutput")
    assert (client.models[retry], client.efforts[retry]) == ("claude-sonnet-5", "medium")


async def test_the_v1_retry_falls_back_to_the_facts_defaults(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    record["generated"]["facts"] = {"model": "a-model-we-no-longer-have"}
    save_record(record_path(paths.data_out, "Q1"), record)
    client = FakeJsonClient(
        [
            reply(_verdicts(f04="citatet nämner inget avstånd")),
            reply(_retry_sheet()),
            reply(_all_supported(*RETRIED_IDS)),
        ]
    )
    await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    retry = client.schemas.index("FactSheetOutput")
    defaults = FactsOptions()
    assert client.models[retry] == MODELS[defaults.model_key]
    assert client.efforts[retry] == defaults.effort


async def test_the_v1_retry_does_not_overwrite_the_plain_facts_cache_entry(
    tmp_path: Path,
) -> None:
    """Minor 3: the retry answers a different prompt (the extra feedback), so it gets its own
    cache entry; the one `web facts` reads keeps the original answer."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    client = FakeJsonClient(
        [
            reply(_verdicts(f04="citatet nämner inget avstånd")),
            reply(_retry_sheet()),
            reply(_all_supported(*RETRIED_IDS)),
        ]
    )
    await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    cache_dir = paths.pipeline_root / ".cache" / "Q1"
    names = sorted(p.name for p in cache_dir.glob("facts-*.json"))
    assert len(names) == 1
    assert "-v1retry-" in names[0]


async def test_a_forced_reverify_that_fails_drops_the_old_verify_metadata(
    tmp_path: Path,
) -> None:
    """Minor 1 (T20 M-a): `generated.verify` left over from an earlier successful run made
    `web facts --regenerate` skip the species after this failure."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    first = FakeJsonClient([reply(_verdicts())])
    await run_verify(paths, VerifyOptions(), client=first, wiki=FakeWiki(), now=NOW)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None and record["generated"].get("verify")
    fatal_retry_sheet = FactSheetOutput(facts=GOOD[:2], sweden_status=None)
    client = FakeJsonClient(
        [
            reply(_verdicts(f04="citatet nämner inget avstånd")),
            reply(fatal_retry_sheet),
            reply(fatal_retry_sheet),
        ]
    )
    outcomes = await run_verify(
        paths, VerifyOptions(force=True), client=client, wiki=FakeWiki(), now=NOW
    )
    assert [o.status for o in outcomes] == ["failed"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert "verify" not in record["generated"]


async def test_a_forced_reverify_failing_after_the_second_v1_pass_drops_the_old_metadata(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    first = FakeJsonClient([reply(_verdicts())])
    await run_verify(paths, VerifyOptions(), client=first, wiki=FakeWiki(), now=NOW)
    client = FakeJsonClient(
        [
            reply(_verdicts(f04="citatet nämner inget avstånd")),
            reply(_retry_sheet()),
            reply(_all_supported(*RETRIED_IDS, unsupported={"f04": "hittar inte citatet"})),
        ]
    )
    outcomes = await run_verify(
        paths, VerifyOptions(force=True), client=client, wiki=FakeWiki(), now=NOW
    )
    assert [o.status for o in outcomes] == ["failed"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert "verify" not in record["generated"]


async def test_the_audio_check_runs_before_v1_and_off_the_event_loop(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """I4 (final review 2026-10-06): V4 is free but can fail; it runs before the paid V1 call
    so an audio problem can never waste V1 work, and in a thread so a slow subprocess does
    not stall the other workers."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True, identifiable_sound=True)
    client = FakeJsonClient([reply(_verdicts())])
    seen: dict[str, object] = {}

    def fake_classify_clip(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        seen["v1_calls"] = len(client.calls)
        seen["main_thread"] = threading.current_thread() is threading.main_thread()
        top = [{"qid": "Q1", "confidence": 0.5}]
        return AudioCheckResult(windows=[{"startSec": 0.0, "top": top}])

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", fake_classify_clip)
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    assert seen == {"v1_calls": 0, "main_thread": False}


async def test_an_unexpected_audio_error_fails_the_species_before_any_v1_call(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True, identifiable_sound=True)

    def broken_classify_clip(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        if mp3_path.name != "voice.mp3":
            return OK_PREFLIGHT
        raise RuntimeError("ett fel i koden")

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", broken_classify_clip)
    client = FakeJsonClient([])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert client.calls == []


async def test_v1_strikes_survive_an_audio_check_that_could_not_run(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True, identifiable_sound=True)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    record["facts"].insert(
        1,
        {
            "id": "f06",
            "topic": "appearance",
            "sv": "Gul buk.",
            "sources": [{"article": "sv", "quote": "gul buk med ett svart band"}],
        },
    )
    save_record(record_path(paths.data_out, "Q1"), record)

    def failing_classify_clip(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        if mp3_path.name != "voice.mp3":
            return OK_PREFLIGHT
        raise AudioCheckFailed("ljudmodellen gav ett oläsbart svar")

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", failing_classify_clip)
    client = FakeJsonClient([reply(_verdicts(f06="citatet säger inget om buken"))])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert not any(f["id"] == "f06" for f in record["facts"])
    assert [f["check"] for f in record["flags"]] == ["V4"]
    assert record["generated"]["verify"]["factsHash"]


async def test_a_forced_reverify_after_an_import_leaves_an_edited_status_alone(
    tmp_path: Path,
) -> None:
    """Follow-up 1 (wave A review): an s01 Albin set with `ändra` has `sources: []`; it
    crashed V1's rendering (IndexError) on every forced verify, and V1 must not judge it."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    record["facts"] = [f for f in record["facts"] if f["id"] != "s01"]
    record["facts"].append(
        {
            "id": "s01",
            "topic": "status",
            "value": "resident",
            "sv": "Stannfågel",
            "sources": [],
            "edited": True,
        }
    )
    save_record(record_path(paths.data_out, "Q1"), record)
    client = FakeJsonClient([reply(_all_supported("f01", "f04", "f05"))])
    outcomes = await run_verify(
        paths, VerifyOptions(force=True), client=client, wiki=FakeWiki(), now=NOW
    )
    assert [o.status for o in outcomes] == ["ok"]
    user = client.calls[0][0]["content"]
    assert isinstance(user, str) and 'id="s01"' not in user
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert any(f["id"] == "s01" and f.get("edited") for f in record["facts"])


async def test_a_confirmed_status_is_not_flagged_again_on_a_forced_reverify(
    tmp_path: Path,
) -> None:
    """Follow-up 2: Albin kept the status despite the V3 flag; a forced rerun honours it."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    record["data"] = {"months": [100] * 11 + [1], "totalReports": 900}
    record["review"]["statusConfirmed"] = True
    save_record(record_path(paths.data_out, "Q1"), record)
    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(
        paths, VerifyOptions(force=True), client=client, wiki=FakeWiki(), now=NOW
    )
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["flags"] == []
    assert record.get("verification")


async def test_a_recording_albin_kept_is_not_checked_or_flagged_again(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """Follow-up 2: a `behåll` on a V4 flag is remembered for that recording."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True, identifiable_sound=False)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    record["review"]["audioKept"] = audio_id(record["audio"])
    save_record(record_path(paths.data_out, "Q1"), record)

    def no_classify(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        raise AssertionError("the audio model must not run for a kept recording")

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", no_classify)
    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["flags"] == []
    assert "audio" in record


async def test_a_kept_decision_does_not_cover_a_different_recording(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True, identifiable_sound=False)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    record["review"]["audioKept"] = "an-older-recording"
    save_record(record_path(paths.data_out, "Q1"), record)
    client = FakeJsonClient([reply(_verdicts())])
    await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert [f["check"] for f in record["flags"]] == ["V4"]


async def test_a_species_the_sound_model_does_not_cover_never_runs_the_model(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """Follow-up 3: the verdict for an uncovered species is a flag whatever the model says,
    so neither the preflight nor the clip check runs."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True, identifiable_sound=False)

    def no_classify(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        raise AssertionError("the audio model must not run for an uncovered species")

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", no_classify)
    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert [f["check"] for f in record["flags"]] == ["V4"]
    assert "täcker inte" in record["flags"][0]["message"]


async def test_the_audio_model_is_preflighted_once_on_the_fixture(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit"), ("Q2", "Blåmes", "Blue Tit")])
    _seed(paths, "Q1", with_audio=True)
    _seed(paths, "Q2", with_audio=True)
    seen: list[Path] = []

    def fake_classify_clip(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        seen.append(mp3_path)
        return OK_PREFLIGHT

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", fake_classify_clip)
    client = FakeJsonClient([reply(_verdicts()), reply(_verdicts())])
    await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert seen[0] == paths.flexref / "fixtures" / "chirp_3s_48k.wav"
    assert [p.name for p in seen[1:]] == ["voice.mp3", "voice.mp3"]


async def test_a_failing_preflight_aborts_the_run_before_anything_is_paid_or_written(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True)
    path = record_path(paths.data_out, "Q1")
    before = path.read_bytes()

    def broken_model(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        raise AudioCheckFailed("uv hittades inte")

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", broken_model)
    client = FakeJsonClient([])
    with pytest.raises(AudioPreflightFailed, match="inga anrop gjordes"):
        await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert client.calls == []
    assert path.read_bytes() == before
    assert not paths.reports.exists() or not any(paths.reports.glob("web-verify-*"))


async def test_no_preflight_when_no_species_to_run_has_a_recording(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit"), ("Q2", "Blåmes", "Blue Tit")])
    _seed(paths, "Q1")
    _seed(paths, "Q2", with_audio=True)
    record = load_record(record_path(paths.data_out, "Q2"))
    assert record is not None
    record["publish"] = True  # refused before V4, so it does not need the model
    save_record(record_path(paths.data_out, "Q2"), record)

    def no_classify(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        raise AssertionError("nothing to check, the model must not run")

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", no_classify)
    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert {o.qid: o.status for o in outcomes} == {"Q1": "ok", "Q2": "failed"}


async def test_a_malformed_record_does_not_abort_the_preflight_or_the_run(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """A2 (wave A review): the preflight loads every record; a broken JSON file must count
    as "does not need the model" and fail on its own in `_one`, not stop the whole run
    with a traceback."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit"), ("Q2", "Blåmes", "Blue Tit")])
    _seed(paths, "Q1", with_audio=True)
    record_path(paths.data_out, "Q2").write_text("{ inte json", encoding="utf-8")
    seen: list[Path] = []

    def fake_classify_clip(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        seen.append(mp3_path)
        return OK_PREFLIGHT

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", fake_classify_clip)
    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert {o.qid: o.status for o in outcomes} == {"Q1": "ok", "Q2": "failed"}
    assert seen[0] == paths.flexref / "fixtures" / "chirp_3s_48k.wav"


async def test_a_record_that_is_not_an_object_does_not_abort_the_preflight(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit"), ("Q2", "Blåmes", "Blue Tit")])
    _seed(paths, "Q1")
    record_path(paths.data_out, "Q2").write_text("[]", encoding="utf-8")
    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert {o.qid: o.status for o in outcomes} == {"Q1": "ok", "Q2": "failed"}


async def test_a_flag_message_never_carries_a_dash_the_site_refuses(tmp_path: Path) -> None:
    """I7: V1's English reason often has an em dash, and it lands in `flags[].message`."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    client = FakeJsonClient([reply(_verdicts(s01="The quote is about Norway \u2014 not Sweden."))])
    await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    message = record["flags"][0]["message"]
    assert "about Norway, not Sweden" in message
    assert "\u2014" not in message


def _lock_voice_files(monkeypatch: pytest.MonkeyPatch) -> None:
    """A voice.mp3 another program holds open: on Windows `unlink` then raises
    PermissionError. Simulated so the test behaves the same on every OS."""
    real_unlink = Path.unlink

    def unlink(self: Path, missing_ok: bool = False) -> None:
        if self.name == "voice.mp3" and self.exists():
            raise PermissionError(13, "The process cannot access the file", str(self))
        real_unlink(self, missing_ok=missing_ok)

    monkeypatch.setattr(Path, "unlink", unlink)


async def test_a_locked_struck_recording_is_reported_and_the_record_still_saved(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """Follow-up 1 (wave B review): the record was saved before the delete; a locked
    voice.mp3 must not turn the species into a crash, and must be reported."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True, identifiable_sound=True)

    def fake_classify_clip(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        return AudioCheckResult(windows=[{"startSec": 0.0, "top": []}])

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", fake_classify_clip)
    _lock_voice_files(monkeypatch)
    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    by_qid = {o.qid: o for o in outcomes}
    assert by_qid["Q1"].status == "ok"
    sweep = by_qid["voice.mp3"]
    assert sweep.status == "failed" and "Q1" in sweep.errors[0]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None and "audio" not in record
    assert (paths.images_out / "Q1" / "voice.mp3").exists()


async def test_verify_sweeps_a_recording_left_without_audio(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1")
    orphan = paths.images_out / "Q2" / "voice.mp3"
    orphan.parent.mkdir(parents=True, exist_ok=True)
    orphan.write_bytes(b"id3")
    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(paths, VerifyOptions(), client=client, wiki=FakeWiki(), now=NOW)
    sweep = next(o for o in outcomes if o.qid == "voice.mp3")
    assert sweep.status == "ok" and any("Q2" in n for n in sweep.notes)
    assert not orphan.exists()


# V4 falls back to the species' next allowed Commons recording (fix wave 2026-10-07).
def _candidate(name: str) -> AudioCandidate:
    return AudioCandidate(
        title=f"File:Parus major {name}.ogg",
        url=f"https://upload.wikimedia.org/{name}.ogg",
        page_url=name,
        mime="application/ogg",
        duration=25.0,
        license="CC BY-SA 4.0",
        author="Anna",
        categories=("Category:Parus major",),
        from_wikidata=True,
    )


@dataclass
class FakeCommons:
    cands: list[AudioCandidate]
    downloaded: list[str] = field(default_factory=list)

    async def candidates(
        self, qid: str, scientific: str, *, refresh: bool = False
    ) -> list[AudioCandidate]:
        return self.cands

    async def download(
        self, qid: str, candidate: AudioCandidate, *, refresh: bool = False
    ) -> bytes:
        self.downloaded.append(candidate.page_url)
        return candidate.page_url.encode()


KEEP = AudioCheckResult(windows=[{"startSec": 0.0, "top": [{"qid": "Q1", "confidence": 0.9}]}])
WEAK = AudioCheckResult(
    windows=[
        {"startSec": 0.0, "top": [{"qid": "Q1", "confidence": 0.03}]},
        {"startSec": 3.0, "top": [{"qid": "Q1", "confidence": 0.025}]},
    ]
)
NONE = AudioCheckResult(windows=[{"startSec": 0.0, "top": []}])


def _fake_model(monkeypatch: pytest.MonkeyPatch, results: dict[bytes, AudioCheckResult]) -> None:
    """classify_clip answers by the file's bytes: b"id3" is the seeded voice.mp3, an
    alternative's bytes are its page url (FakeCommons.download + the fake converter)."""

    def fake_classify_clip(mp3_path: Path, flexref_dir: Path) -> AudioCheckResult:
        if mp3_path.suffix == ".wav":
            return OK_PREFLIGHT
        return results[mp3_path.read_bytes()]

    def fake_convert(raw: bytes, out_path: Path) -> None:
        out_path.write_bytes(raw)

    monkeypatch.setattr("birdy_fetcher.web.verify_step.classify_clip", fake_classify_clip)
    monkeypatch.setattr("birdy_fetcher.web.verify_step.convert_to_mp3", fake_convert)


async def _verify_with(paths: WebPaths, commons: FakeCommons) -> list[str]:
    client = FakeJsonClient([reply(_verdicts())])
    outcomes = await run_verify(
        paths, VerifyOptions(), client=client, wiki=FakeWiki(), audio=commons, now=NOW
    )
    assert [o.status for o in outcomes] == ["ok"]
    return outcomes[0].notes


async def test_a_struck_recording_gives_way_to_the_next_allowed_one(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True)
    _fake_model(monkeypatch, {b"id3": NONE, b"alt1": NONE, b"alt2": KEEP})
    commons = FakeCommons([_candidate("x"), _candidate("alt1"), _candidate("alt2")])
    notes = await _verify_with(paths, commons)
    assert commons.downloaded == ["alt1", "alt2"]  # the current one is never fetched again
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["audio"]["sourceUrl"] == "alt2"
    assert record["audio"]["author"] == "Anna"
    assert record["review"]["audioStruckSources"] == ["x", "alt1"]
    assert "audioStruck" not in record["review"]
    assert record["flags"] == []
    assert (paths.images_out / "Q1" / "voice.mp3").read_bytes() == b"alt2"
    assert any("byttes" in n for n in notes)


async def test_a_weak_recording_is_replaced_by_one_the_model_keeps(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True)
    _fake_model(monkeypatch, {b"id3": WEAK, b"alt1": KEEP})
    await _verify_with(paths, FakeCommons([_candidate("alt1")]))
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["audio"]["sourceUrl"] == "alt1"
    assert record["flags"] == []


async def test_a_weak_recording_with_nothing_better_is_a_flag_and_stays(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True)
    _fake_model(monkeypatch, {b"id3": WEAK, b"alt1": NONE})
    await _verify_with(paths, FakeCommons([_candidate("alt1")]))
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["audio"]["sourceUrl"] == "x"
    assert [f["check"] for f in record["flags"]] == ["V4"]
    assert "lyssna och besluta" in record["flags"][0]["message"]
    assert (paths.images_out / "Q1" / "voice.mp3").read_bytes() == b"id3"


async def test_with_no_recording_left_the_strike_remembers_every_one_tried(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True)
    _fake_model(monkeypatch, {b"id3": NONE, b"alt1": NONE, b"alt2": NONE})
    await _verify_with(paths, FakeCommons([_candidate("alt1"), _candidate("alt2")]))
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert "audio" not in record
    assert record["review"]["audioStruck"] is True
    assert record["review"]["audioStruckSources"] == ["x", "alt1", "alt2"]
    assert not (paths.images_out / "Q1" / "voice.mp3").exists()


async def test_a_locked_voice_file_leaves_the_species_unverified_with_a_flag(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """Re-review 2026-10-07: when the new recording cannot be put in place (Windows lock on
    os.replace), the record that names it is saved again without verification and with a
    V4 flag, the old file does not stay under the new credits, and no .new is left."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True)
    _fake_model(monkeypatch, {b"id3": NONE, b"alt1": KEEP})

    def locked(src: object, dst: object) -> None:
        raise PermissionError("filen används av en annan process")

    # Only the swap of voice.mp3 is locked, not the record's own atomic save.
    monkeypatch.setattr("birdy_fetcher.web.verify_step._swap", locked)
    notes = await _verify_with(paths, FakeCommons([_candidate("alt1")]))
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["audio"]["sourceUrl"] == "alt1"
    assert "verification" not in record
    assert [f["check"] for f in record["flags"]] == ["V4"]
    assert "kunde inte läggas på plats" in record["flags"][0]["message"]
    voice = paths.images_out / "Q1" / "voice.mp3"
    assert not voice.exists()
    assert not voice.with_name("voice.mp3.new").exists()
    assert any("kunde inte läggas på plats" in n for n in notes)


async def test_a_crash_between_save_and_swap_is_caught_by_the_next_sweep(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """The process dies after the record with the new recording is saved and before the
    file is swapped in: the next sweep (any step's end) removes the old file and the stray
    .new, and the record loses its verification."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True)
    _fake_model(monkeypatch, {b"id3": NONE, b"alt1": KEEP})

    class PowerCut(BaseException):
        """The process dies: nothing after this line of the step runs (a BaseException
        other than SystemExit, which asyncio would re-raise past the test)."""

    def crash(images_out: Path, qid: str, mp3: Path) -> str | None:
        (images_out / qid / "voice.mp3.new").write_bytes(b"alt1")
        raise PowerCut

    monkeypatch.setattr("birdy_fetcher.web.verify_step._place_voice", crash)
    with pytest.raises(PowerCut):
        await _verify_with(paths, FakeCommons([_candidate("alt1")]))
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["audio"]["sourceUrl"] == "alt1" and "verification" in record
    voice = paths.images_out / "Q1" / "voice.mp3"
    assert voice.read_bytes() == b"id3"  # the old recording under the new credits

    sweep = sweep_orphan_voices(paths.data_out, paths.images_out)
    assert not voice.exists() and not voice.with_name("voice.mp3.new").exists()
    assert sweep.errors and "Q1" in sweep.errors[0]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert "verification" not in record
    assert [f["check"] for f in record["flags"]] == ["V4"]


async def test_a_replacement_records_the_hash_of_its_file(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    import hashlib

    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths, "Q1", with_audio=True)
    _fake_model(monkeypatch, {b"id3": NONE, b"alt1": KEEP})
    await _verify_with(paths, FakeCommons([_candidate("alt1")]))
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["audio"]["mp3Sha256"] == hashlib.sha256(b"alt1").hexdigest()
