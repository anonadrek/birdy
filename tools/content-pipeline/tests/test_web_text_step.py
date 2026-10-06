"""Tests for web/text_step.py: write, check, rewrite, remove, and the run."""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path

from birdy_fetcher.web.checker import CheckOutput, Verdict, check_items
from birdy_fetcher.web.record import load_record, record_path, save_record
from birdy_fetcher.web.text_checks import TextContext
from birdy_fetcher.web.text_model import WebTextV2
from birdy_fetcher.web.text_step import WriteOptions, run_write

from .text_fixtures import EN, FACTS, SV, VALID, S, reviewed_record
from .web_fakes import FakeJsonClient, reply
from .web_repo import make_repo

NOW = datetime(2026, 11, 21, tzinfo=UTC)
CTX = TextContext.from_facts(FACTS)


def _verdicts(text: WebTextV2, unsupported: dict[str, str] | None = None) -> CheckOutput:
    bad = unsupported or {}
    return CheckOutput(
        verdicts=[
            Verdict(id=i.id, supported=i.id not in bad, problem=bad.get(i.id))
            for i in check_items(text, CTX)
        ]
    )


async def test_a_good_text_is_saved_with_the_status_from_the_fact_sheet(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    client = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    assert client.models == ["claude-opus-5", "claude-sonnet-5"]
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    assert record["status"] == "ok"
    assert record["text"]["sv"]["facts"]["swedenStatus"] == {
        "value": "resident",
        "factIds": ["s01"],
    }
    assert record["text"]["en"]["lead"][1]["factIds"] == ["f01"]
    assert record["generated"]["text"]["checker"] == "claude-sonnet-5"
    assert "unreviewed" not in record["generated"]["text"]
    assert any(p.name.startswith("web-text-") for p in paths.reports.iterdir())


async def test_a_broken_rule_gets_one_retry(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    short = WebTextV2(sv=SV.model_copy(update={"meta_description": "Kort."}), en=EN)
    client = FakeJsonClient([reply(short), reply(VALID), reply(_verdicts(VALID))])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    assert len(client.calls) == 3
    assert "meta_description" in str(client.calls[1][-1]["content"])


async def test_an_unsupported_sentence_is_rewritten_then_removed(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    bad = {"sv.lead[1]": "nämner inget om kinderna"}
    client = FakeJsonClient(
        [reply(VALID), reply(_verdicts(VALID, bad)), reply(VALID), reply(_verdicts(VALID, bad))]
    )
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    assert any("sv.lead[1] togs bort" in n for n in outcomes[0].notes)
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    assert len(record["text"]["sv"]["lead"]) == 1


async def test_an_unreviewed_fact_sheet_is_only_written_when_allowed(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    record = reviewed_record()
    del record["verification"]
    save_record(record_path(paths.data_out, "Q25485"), record)
    skipped = await run_write(paths, WriteOptions(wave=1), client=FakeJsonClient([]), now=NOW)
    assert [o.status for o in skipped] == ["skipped"]
    client = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    await run_write(
        paths, WriteOptions(qids=("Q25485",), allow_unreviewed=True), client=client, now=NOW
    )
    saved = load_record(record_path(paths.data_out, "Q25485"))
    assert saved is not None
    assert saved["generated"]["text"]["unreviewed"] is True


async def test_a_current_text_is_skipped_unless_regenerated(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    first = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    await run_write(paths, WriteOptions(wave=1), client=first, now=NOW)
    again = await run_write(paths, WriteOptions(wave=1), client=FakeJsonClient([]), now=NOW)
    assert [o.status for o in again] == ["skipped"]
    redo = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    outcomes = await run_write(paths, WriteOptions(wave=1, regenerate=True), client=redo, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]


async def test_an_unconfirmed_status_flag_hides_s01_from_the_writer(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    record = reviewed_record()
    record["data"]["statusSignal"]["contradicts"] = "Statusen säger stannfågel, men ..."
    save_record(record_path(paths.data_out, "Q25485"), record)
    without_s01 = WebTextV2(
        sv=SV.model_copy(
            update={
                "where_when": [
                    S("Rapporteras året runt i skog, parker och trädgårdar.", "d01", "f05")
                ]
            }
        ),
        en=EN.model_copy(
            update={
                "where_when": [S("Reported all year in woodland, parks and gardens.", "d01", "f05")]
            }
        ),
    )
    client = FakeJsonClient([reply(without_s01), reply(_verdicts(without_s01))])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    assert "s01" not in str(client.calls[0][0]["content"])
    saved = load_record(record_path(paths.data_out, "Q25485"))
    assert saved is not None
    assert saved["text"]["sv"]["facts"]["swedenStatus"] is None


async def test_a_text_that_cannot_be_fixed_fails_and_keeps_the_rejected_text(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    two_marks = WebTextV2(sv=SV.model_copy(update={"field_marks": SV.field_marks[:2]}), en=EN)
    client = FakeJsonClient([reply(two_marks), reply(two_marks)])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    saved = load_record(record_path(paths.data_out, "Q25485"))
    assert saved is not None
    assert saved["text"] is None
    assert len(saved["rejectedText"]["sv"]["fieldMarks"]) == 2
