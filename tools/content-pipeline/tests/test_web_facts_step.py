"""Tests for web/facts_step.py: extraction with retry, cache, cost cap and the run."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from birdy_fetcher.web.facts import FactSheetOutput
from birdy_fetcher.web.facts_step import FactsOptions, run_facts
from birdy_fetcher.web.record import load_record, merge_sources, record_path, save_record
from birdy_fetcher.web.wiki_full import WikiArticle

from .test_web_facts import ARTICLES, GOOD, STATUS, _fact
from .web_fakes import FakeJsonClient, reply
from .web_repo import make_repo

NOW = datetime(2026, 10, 3, tzinfo=UTC)
TEN = GOOD + [
    _fact("appearance", f"Svart band på buken {i}.", "ett bredare svart band på buken")
    for i in range(4)
]
FULL = FactSheetOutput(facts=TEN, sweden_status=STATUS)
NO_VOICE = FactSheetOutput(facts=[f for f in TEN if f.topic != "voice"], sweden_status=STATUS)


@dataclass
class FakeWiki:
    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]:
        return ARTICLES


def _seed(paths_data_out: Path, qid: str) -> None:
    record = merge_sources(
        None,
        qid,
        {
            "names": {"sv": "Talgoxe", "en": "Great Tit", "scientific": "Parus major"},
            "wikipedia": {"sv": {"title": "Talgoxe", "revision": "1"}},
            "data": {"sentences": {"sv": ["Rapporteras året runt."]}, "statusSignal": {}},
            "swedishRedList": "not_listed",
        },
    )
    save_record(record_path(paths_data_out, qid), record)


async def test_facts_are_saved_and_the_species_stays_pending(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    client = FakeJsonClient([reply(FULL)])
    outcomes = await run_facts(paths, FactsOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["pending"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert [f["id"] for f in record["facts"]][-3:] == ["s01", "d01", "d02"]
    assert record["generated"]["facts"]["model"] == "claude-opus-5"
    assert record["generated"]["facts"]["prompt"] == "facts-v1"
    assert client.schemas == ["FactSheetOutput"]
    assert any(p.name.startswith("web-facts-") for p in paths.reports.iterdir())


async def test_a_missing_topic_is_retried_with_feedback(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    client = FakeJsonClient([reply(NO_VOICE), reply(FULL)])
    await run_facts(paths, FactsOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert len(client.calls) == 2
    feedback = str(client.calls[1][-1]["content"])
    assert "läte" in feedback


async def test_still_missing_after_the_retry_fails_the_species(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    client = FakeJsonClient([reply(NO_VOICE), reply(NO_VOICE)])
    outcomes = await run_facts(paths, FactsOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["failed"]
    record = load_record(record_path(paths.data_out, "Q1"))
    assert record is not None
    assert record["status"] == "failed"
    assert "läte" in record["errors"][0]


async def test_a_reviewed_fact_sheet_is_left_alone(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    path = record_path(paths.data_out, "Q1")
    record = load_record(path)
    assert record is not None
    record["verification"] = {
        "method": "auto",
        "at": "2026-11-01",
        "model": "x",
        "spotChecked": False,
    }
    save_record(path, record)
    client = FakeJsonClient([])
    outcomes = await run_facts(paths, FactsOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]


async def test_a_species_waiting_on_open_flags_is_left_alone_unless_forced(
    tmp_path: Path,
) -> None:
    """N4 (review fix 2026-10-06): a species that has been through `web verify` but has
    open flags (so `verification` is absent) must not be silently rebuilt from the cache
    by an unforced rerun -- that would undo V1's strikes and drop the flags."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    path = record_path(paths.data_out, "Q1")
    record = load_record(path)
    assert record is not None
    record["flags"] = [{"check": "V2", "factId": "f01", "message": "x"}]
    record["generated"]["verify"] = {"model": "claude-sonnet-5", "factsHash": "abc"}
    save_record(path, record)
    client = FakeJsonClient([])
    outcomes = await run_facts(paths, FactsOptions(), client=client, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["skipped"]
    assert client.calls == []
    forced = FakeJsonClient([reply(FULL)])
    outcomes = await run_facts(
        paths, FactsOptions(force=True), client=forced, wiki=FakeWiki(), now=NOW
    )
    assert [o.status for o in outcomes] == ["pending"]


async def test_a_published_species_refuses_facts_even_with_force(tmp_path: Path) -> None:
    """N5 (review fix 2026-10-06): `web facts --force` on a published species would null
    its text and set status pending while publish stays true, breaking the fas 2 build."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    path = record_path(paths.data_out, "Q1")
    record = load_record(path)
    assert record is not None
    record["publish"] = True
    save_record(path, record)
    client = FakeJsonClient([])
    outcomes = await run_facts(
        paths, FactsOptions(force=True), client=client, wiki=FakeWiki(), now=NOW
    )
    assert [o.status for o in outcomes] == ["failed"]
    assert outcomes[0].errors == ["publicerad: sätt publish: false först"]
    assert client.calls == []


async def test_a_second_run_uses_the_cache(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    await run_facts(
        paths, FactsOptions(), client=FakeJsonClient([reply(FULL)]), wiki=FakeWiki(), now=NOW
    )
    again = FakeJsonClient([])
    outcomes = await run_facts(paths, FactsOptions(), client=again, wiki=FakeWiki(), now=NOW)
    assert [o.status for o in outcomes] == ["pending"]
    assert again.calls == []


async def test_the_cost_cap_skips_the_rest(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    client = FakeJsonClient([reply(FULL)])
    outcomes = await run_facts(
        paths, FactsOptions(max_cost=0.001), client=client, wiki=FakeWiki(), now=NOW
    )
    assert [o.status for o in outcomes] == ["skipped"]
    assert "kostnadstaket" in outcomes[0].errors[0]


async def test_a_species_without_sources_fails(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    outcomes = await run_facts(
        paths, FactsOptions(), client=FakeJsonClient([]), wiki=FakeWiki(), now=NOW
    )
    assert outcomes[0].status == "failed"
    assert "web sources" in outcomes[0].errors[0]


async def test_reply_is_costed_even_if_the_check_raises(tmp_path: Path) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    _seed(paths.data_out, "Q1")
    client = FakeJsonClient([reply(FULL)])

    # Track record_cost calls to verify cost is recorded despite check failure
    record_cost_calls: list[tuple[object, ...]] = []
    import birdy_fetcher.web.facts_step as facts_step_module

    original_record_cost = facts_step_module.record_cost  # type: ignore[attr-defined]
    original_check = facts_step_module.check_fact_sheet  # type: ignore[attr-defined]

    def mock_record_cost(cost, model_key, reply_obj):  # type: ignore[no-untyped-def]
        record_cost_calls.append((cost, model_key, reply_obj))
        original_record_cost(cost, model_key, reply_obj)

    def mock_check(*args: object, **kwargs: object) -> None:
        raise RuntimeError("boom")

    facts_step_module.record_cost = mock_record_cost  # type: ignore[attr-defined]
    facts_step_module.check_fact_sheet = mock_check  # type: ignore[attr-defined,assignment]

    try:
        outcomes = await run_facts(paths, FactsOptions(), client=client, wiki=FakeWiki(), now=NOW)
    finally:
        facts_step_module.record_cost = original_record_cost  # type: ignore[attr-defined]
        facts_step_module.check_fact_sheet = original_check  # type: ignore[attr-defined]

    # The species should fail due to the exception
    assert [o.status for o in outcomes] == ["failed"]
    assert "boom" in outcomes[0].errors[0]

    # But record_cost should have been called before the check
    assert len(record_cost_calls) == 1
    cost_tracker, _model_key, _reply_obj = record_cost_calls[0]
    assert cost_tracker.call_count == 1  # type: ignore[attr-defined]
    assert cost_tracker.total_usd > 0  # type: ignore[attr-defined]


def test_the_facts_prompt_describes_the_automatic_verification() -> None:
    """Minor 2 (final review 2026-10-06): no person reviews every fact any more (Revision
    2026-10-05); the prompt says what actually happens to the facts."""
    template = (Path(__file__).resolve().parents[1] / "prompts/facts-v1.md").read_text(
        encoding="utf-8"
    )
    assert "person reviews every fact" not in template
    assert "checks every fact against its quote" in template
