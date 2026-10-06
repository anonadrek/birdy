"""Tests for web/text_step.py: write, check, rewrite, remove, and the run."""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path

import pytest

from birdy_fetcher.web.checker import CheckOutput, Verdict, check_items
from birdy_fetcher.web.facts import FactCheck, apply_facts
from birdy_fetcher.web.record import facts_hash, load_record, record_path, save_record
from birdy_fetcher.web.text_checks import TextContext
from birdy_fetcher.web.text_model import LookAlikeText, WebTextV2
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


# C1 (review fix 2026-10-06): a stale `verification` must never let text be written from
# facts that were never actually checked.


async def test_new_facts_after_verification_are_not_written_until_reverified(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    new_check = FactCheck(
        facts=[dict(f) for f in FACTS if f["topic"] in ("appearance", "voice", "habitat")]
    )
    apply_facts(record, new_check, generated={"model": "claude-opus-5"})
    save_record(record_path(paths.data_out, "Q25485"), record)
    client = FakeJsonClient([])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]
    assert outcomes[0].errors == ["faktabladet är inte kontrollerat"]
    assert client.calls == []


async def test_a_stale_verify_hash_is_treated_as_not_verified(tmp_path: Path) -> None:
    """Defence in depth: even if `verification` itself was somehow left behind, a
    `generated.verify.factsHash` that no longer matches the current facts must not be
    trusted."""
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    record = reviewed_record()
    record["generated"]["verify"]["factsHash"] = "stale0000"
    save_record(record_path(paths.data_out, "Q25485"), record)
    client = FakeJsonClient([])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]
    assert outcomes[0].errors == ["faktabladet är inte kontrollerat"]
    assert client.calls == []


# I1 (review fix 2026-10-06): a failed rewrite must not destroy a text the site still
# depends on.


async def test_a_failed_regenerate_keeps_the_current_text_and_status(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    first = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    await run_write(paths, WriteOptions(wave=1), client=first, now=NOW)
    bad = FakeJsonClient([reply(None, stop="max_tokens")])
    outcomes = await run_write(paths, WriteOptions(wave=1, regenerate=True), client=bad, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert any("den tidigare texten behölls" in n for n in outcomes[0].notes)
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    assert record["status"] == "ok"
    assert record["text"] is not None


async def test_a_failed_rewrite_on_a_published_species_with_changed_facts_keeps_old_text(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    first = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    await run_write(paths, WriteOptions(wave=1), client=first, now=NOW)
    published = load_record(record_path(paths.data_out, "Q25485"))
    assert published is not None
    published["publish"] = True
    # Facts changed after the text was written (e.g. via a later `web facts` + `web
    # verify`), but the old text is still the one live on the site.
    published["facts"] = published["facts"] + [
        {
            "id": "f99",
            "topic": "behaviour",
            "sv": "Den äter frön och insekter.",
            "sources": [{"article": "sv", "quote": "äter frön och insekter"}],
        }
    ]
    published["generated"]["verify"]["factsHash"] = facts_hash(published)
    save_record(record_path(paths.data_out, "Q25485"), published)
    bad = FakeJsonClient([reply(None, stop="max_tokens")])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=bad, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert any("sätt publish: false" in n for n in outcomes[0].notes)
    saved = load_record(record_path(paths.data_out, "Q25485"))
    assert saved is not None
    assert saved["status"] == "ok"
    assert saved["publish"] is True


# I2 (review fix 2026-10-06): the best attempt (fewest hard problems) is kept, and a bad
# rewrite falls back to removing from the original instead of destroying a good answer.


async def test_an_earlier_clean_attempt_beats_a_later_broken_one(tmp_path: Path) -> None:
    attempt1 = WebTextV2(
        sv=SV.model_copy(update={"field_marks": [*SV.field_marks, S("Okänd uppgift", "f99")]}),
        en=EN,
    )
    attempt2 = WebTextV2(sv=SV.model_copy(update={"meta_description": "Kort."}), en=EN)
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    client = FakeJsonClient([reply(attempt1), reply(attempt2), reply(_verdicts(VALID))])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    assert len(record["text"]["sv"]["fieldMarks"]) == 3


async def test_a_bad_rewrite_falls_back_to_removing_from_the_original(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    client = FakeJsonClient(
        [
            reply(VALID),
            reply(_verdicts(VALID, {"sv.lead[1]": "nämner inget om kinderna"})),
            reply(VALID),
            reply(_verdicts(VALID, {"sv.voice[0]": "sången är inte styrkt"})),
        ]
    )
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    assert any("omskrivningen" in n for n in outcomes[0].notes)
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    assert len(record["text"]["sv"]["lead"]) == 1
    assert len(record["text"]["sv"]["voice"]) == 1


# I3 (review fix 2026-10-06): the writer and the checker must be different models.


async def test_the_writer_and_the_checker_must_be_different_models(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    with pytest.raises(ValueError):
        await run_write(
            paths,
            WriteOptions(wave=1, model_key="sonnet", checker_key="sonnet"),
            client=FakeJsonClient([]),
            now=NOW,
        )


# M1: --species is deduplicated, and an unknown QID is a failed outcome, not a silent drop.


async def test_unknown_species_are_reported_failed_and_deduplicated(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    client = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    outcomes = await run_write(
        paths,
        WriteOptions(qids=("Q25485", "Q25485", "QUNKNOWN")),
        client=client,
        now=NOW,
    )
    assert len(outcomes) == 2
    by_qid = {o.qid: o for o in outcomes}
    assert by_qid["Q25485"].status == "ok"
    assert by_qid["QUNKNOWN"].status == "failed"
    assert by_qid["QUNKNOWN"].errors == ["artposten saknas: kör web sources först"]


# M2: a malformed record must fail on its own, never abort the whole run.


async def test_a_malformed_record_fails_without_aborting_the_run(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    broken = reviewed_record("Q99999")
    del broken["names"]
    save_record(record_path(paths.data_out, "Q99999"), broken)
    client = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    statuses = {o.qid: o.status for o in outcomes}
    assert statuses["Q25485"] == "ok"
    assert statuses["Q99999"] == "failed"


# M3: generated.text records the checker's prompt version and a writer prompt hash.


async def test_generated_text_records_the_checker_prompt_and_a_prompt_hash(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    client = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    generated = record["generated"]["text"]
    assert generated["checkerPrompt"] == "check-v1"
    assert isinstance(generated["promptHash"], str)
    assert len(generated["promptHash"]) == 8


# M5: a look-alike named by scientific name in the model's answer is mapped to the fact's
# QID before it reaches the site.


async def test_a_lookalike_named_by_scientific_name_is_mapped_to_its_qid(
    tmp_path: Path,
) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    by_scientific = WebTextV2(
        sv=SV.model_copy(
            update={
                "look_alikes": [
                    LookAlikeText(
                        other="Cyanistes caeruleus",
                        sentences=[S("Blåmesen är mindre och har blå hätta.", "f06")],
                    )
                ]
            }
        ),
        en=EN,
    )
    client = FakeJsonClient([reply(by_scientific), reply(_verdicts(by_scientific))])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["ok"]
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    assert record["text"]["sv"]["lookAlikes"][0]["other"] == "Q25404"


# M7: cost cap, a checker failure that leaves the record untouched, a removal that empties
# a required field, a writer refusal, an edited/data fact reaching the prompt, `about`
# reaching the checker, and `publish` staying False.


async def test_the_cost_cap_skips_instead_of_failing(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    client = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    outcomes = await run_write(paths, WriteOptions(wave=1, max_cost=0.0001), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]
    assert "kostnadstaket nåddes" in outcomes[0].errors[0]
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    assert record["status"] == "pending"


async def test_a_checker_failure_leaves_the_record_untouched(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    client = FakeJsonClient([reply(VALID), reply(None, stop="refusal")])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    assert record["status"] == "pending"
    assert record["text"] is None


async def test_a_removal_that_empties_a_required_field_fails(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    bad = {"sv.voice[0]": "sången är inte styrkt"}
    client = FakeJsonClient(
        [reply(VALID), reply(_verdicts(VALID, bad)), reply(VALID), reply(_verdicts(VALID, bad))]
    )
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    assert record["text"] is None
    assert record["rejectedText"]["sv"]["voice"] == []


async def test_a_refused_writer_reply_fails_without_retrying_forever(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    client = FakeJsonClient([reply(None, stop="refusal")])
    outcomes = await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    assert "refusal" in outcomes[0].errors[0]
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    assert record["text"] is None
    assert record["rejectedText"] is None


async def test_an_edited_fact_and_a_data_fact_reach_the_writer_prompt(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    record = reviewed_record()
    record["facts"].append(
        {
            "id": "f07",
            "topic": "appearance",
            "sv": "Ungfågeln har en brunare ton.",
            "sources": [],
            "edited": True,
        }
    )
    # Simulate the edit having gone through `web import`/`apply_review`, which refreshes
    # this hash to match the edited facts (otherwise C1's stale-verify defence would skip
    # the species before it ever reaches the writer).
    record["generated"]["verify"]["factsHash"] = facts_hash(record)
    save_record(record_path(paths.data_out, "Q25485"), record)
    client = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    prompt = str(client.calls[0][0]["content"])
    assert "d01" in prompt and "Rapporteras året runt." in prompt
    assert "f07" in prompt and "Ungfågeln har en brunare ton." in prompt


async def test_about_reaches_the_checker(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    client = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    checker_prompt = str(client.calls[1][0]["content"])
    assert "Talgoxe / Great Tit (Parus major), familj Mesar" in checker_prompt


async def test_write_never_sets_publish(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q25485", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q25485"), reviewed_record())
    client = FakeJsonClient([reply(VALID), reply(_verdicts(VALID))])
    await run_write(paths, WriteOptions(wave=1), client=client, now=NOW)
    record = load_record(record_path(paths.data_out, "Q25485"))
    assert record is not None
    assert record.get("publish") is False
