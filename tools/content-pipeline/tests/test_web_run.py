"""End-to-end test for web/run.py with fake Wikipedia and a fake model."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path

import pytest
from anthropic.types import MessageParam
from PIL import Image

from birdy_fetcher.web.run import SlugCollisionError, WebPaths, WebRunOptions, run_web
from birdy_fetcher.web.wiki_full import WikiArticle
from birdy_fetcher.web.writer import StructuredReply

from .test_web_source import _write
from .web_fixtures import ARTICLES, valid_output

PIPELINE = Path(__file__).resolve().parents[1]


def _repo(tmp_path: Path, species: list[tuple[str, str, str]]) -> WebPaths:
    paths = WebPaths(repo_root=tmp_path)
    for qid, sv, en in species:
        _write(paths.species_root, qid, sv, en, "approved")
        img = paths.asset_images / qid / "hero.webp"
        img.parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", (2000, 1000), (90, 110, 70)).save(img, "WEBP")
    paths.family_groups.parent.mkdir(parents=True, exist_ok=True)
    paths.family_groups.write_text(
        "order: [songbirds, other]\ngroups:\n"
        "  songbirds: {keyed_by: order, ioc_order: Passeriformes}\n"
        "  other: {families: [Cuculidae]}\n",
        encoding="utf-8",
    )
    base = {"name": {"sv": "X", "en": "X"}, "photo": species[0][0], "intro": {"sv": "a", "en": "a"}}
    paths.web_groups.parent.mkdir(parents=True, exist_ok=True)
    paths.web_groups.write_text(
        json.dumps({
            "groups": [
                {**base, "key": "songbirds", "slug": {"sv": "tattingar", "en": "songbirds"}},
                {**base, "key": "other", "slug": {"sv": "ovriga-faglar", "en": "other-birds"}},
            ],
            "common": [species[0][0]],
        }),
        encoding="utf-8",
    )
    paths.prompt.parent.mkdir(parents=True, exist_ok=True)
    paths.prompt.write_text((PIPELINE / "prompts/web-v1.md").read_text(encoding="utf-8"),
                            encoding="utf-8")
    paths.banned.write_text("fascinerande\n", encoding="utf-8")
    return paths


@dataclass
class FakeWiki:
    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]:
        return {} if qid == "Q9" else ARTICLES


@dataclass
class FakeClient:
    calls: int = 0
    seen: list[str] = field(default_factory=list)
    aclose_called: bool = False

    async def parse_web_text(
        self, *, model: str, system: str, messages: list[MessageParam], max_tokens: int,
        effort: str,
    ) -> StructuredReply:
        self.calls += 1
        return StructuredReply(valid_output(), "{}", 1000, 500, "end_turn")

    async def aclose(self) -> None:
        self.aclose_called = True


def _options(**kw: object) -> WebRunOptions:
    base: dict[str, object] = dict(qids=(), model_key="opus", effort="high", max_cost=None,
                                   force=False, refresh_sources=False, regenerate=False,
                                   workers=2, dry_run=False)
    base.update(kw)
    return WebRunOptions(**base)  # type: ignore[arg-type]


NOW = datetime(2026, 10, 1, tzinfo=UTC)


async def test_run_writes_records_images_and_report(tmp_path: Path) -> None:
    paths = _repo(tmp_path, [("Q1", "Talgoxe", "Great Tit"), ("Q9", "Blåmes", "Blue Tit")])
    client = FakeClient()
    outcomes = await run_web(paths, _options(), client=client, wiki=FakeWiki(), now=NOW)
    by_qid = {o.qid: o for o in outcomes}
    assert by_qid["Q1"].status == "ok"
    assert by_qid["Q9"].status == "failed"
    assert "Wikipedia" in by_qid["Q9"].errors[0]
    record = json.loads((paths.data_out / "Q1.json").read_text(encoding="utf-8"))
    assert record["status"] == "ok" and record["group"] == "songbirds"
    assert (paths.images_out / "Q1/hero.webp").exists()
    failed = json.loads((paths.data_out / "Q9.json").read_text(encoding="utf-8"))
    assert failed["status"] == "failed" and failed["text"] is None
    assert (paths.reports / "web-2026-10-01-000000.md").exists()
    assert client.calls == 1


async def test_approved_species_are_skipped_without_a_call(tmp_path: Path) -> None:
    paths = _repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    await run_web(paths, _options(), client=FakeClient(), wiki=FakeWiki(), now=NOW)
    record_path = paths.data_out / "Q1.json"
    record = json.loads(record_path.read_text(encoding="utf-8"))
    record["review"] = "approved"
    record_path.write_text(json.dumps(record), encoding="utf-8")
    client = FakeClient()
    outcomes = await run_web(paths, _options(regenerate=True), client=client, wiki=FakeWiki(),
                             now=NOW)
    assert outcomes[0].status == "skipped" and client.calls == 0


async def test_dry_run_writes_nothing(tmp_path: Path) -> None:
    paths = _repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    outcomes = await run_web(paths, _options(dry_run=True), client=None, wiki=FakeWiki(), now=NOW)
    assert outcomes[0].status == "dry-run"
    assert not paths.data_out.exists() and not paths.reports.exists()


async def test_slug_collision_stops_the_run(tmp_path: Path) -> None:
    paths = _repo(tmp_path, [("Q1", "Tättingar", "Great Tit")])
    with pytest.raises(SlugCollisionError, match="tattingar"):
        await run_web(paths, _options(), client=FakeClient(), wiki=FakeWiki(), now=NOW)


@dataclass
class FlakyWiki:
    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]:
        if qid == "Q2":
            raise TimeoutError("Wikipedia gav timeout")
        return ARTICLES


@dataclass
class FlakyClient:
    async def parse_web_text(
        self, *, model: str, system: str, messages: list[MessageParam], max_tokens: int,
        effort: str,
    ) -> StructuredReply:
        text = " ".join(str(m["content"]) for m in messages)
        if "Svartmes" in text:
            raise RuntimeError("modellen svarade inte")
        return StructuredReply(valid_output(), "{}", 1000, 500, "end_turn")


async def test_one_species_failing_does_not_abort_the_whole_run(tmp_path: Path) -> None:
    paths = _repo(
        tmp_path,
        [("Q1", "Talgoxe", "Great Tit"), ("Q2", "Blåmes", "Blue Tit"),
         ("Q3", "Svartmes", "Coal Tit")],
    )
    paths.data_out.mkdir(parents=True, exist_ok=True)
    stale = json.dumps({"qid": "Q2", "review": "unreviewed", "marker": "stale-from-earlier-run"})
    (paths.data_out / "Q2.json").write_text(stale, encoding="utf-8")

    outcomes = await run_web(paths, _options(), client=FlakyClient(), wiki=FlakyWiki(), now=NOW)
    by_qid = {o.qid: o for o in outcomes}
    assert by_qid["Q1"].status == "ok"
    assert by_qid["Q2"].status == "failed"
    assert "TimeoutError" in by_qid["Q2"].errors[0]
    assert by_qid["Q3"].status == "failed"
    assert "RuntimeError" in by_qid["Q3"].errors[0]
    assert (paths.reports / "web-2026-10-01-000000.md").exists()
    # A transient error must not overwrite a JSON record from an earlier, good run.
    assert (paths.data_out / "Q2.json").read_text(encoding="utf-8") == stale


async def test_broken_pre_existing_json_does_not_abort_the_run(tmp_path: Path) -> None:
    paths = _repo(
        tmp_path, [("Q1", "Talgoxe", "Great Tit"), ("Q2", "Blåmes", "Blue Tit")],
    )
    paths.data_out.mkdir(parents=True, exist_ok=True)
    broken = "{not valid json"
    (paths.data_out / "Q2.json").write_text(broken, encoding="utf-8")

    outcomes = await run_web(paths, _options(), client=FakeClient(), wiki=FakeWiki(), now=NOW)
    by_qid = {o.qid: o for o in outcomes}
    assert by_qid["Q1"].status == "ok"
    assert by_qid["Q2"].status == "failed"
    assert "Error" in by_qid["Q2"].errors[0] or "Decode" in by_qid["Q2"].errors[0]
    # The broken file must be left exactly as it was -- not overwritten, not "fixed".
    assert (paths.data_out / "Q2.json").read_text(encoding="utf-8") == broken


@dataclass
class ExpensiveClient:
    calls: int = 0

    async def parse_web_text(
        self, *, model: str, system: str, messages: list[MessageParam], max_tokens: int,
        effort: str,
    ) -> StructuredReply:
        self.calls += 1
        return StructuredReply(valid_output(), "{}", 1_000_000, 1_000_000, "end_turn")


async def test_cost_cap_stops_the_run_and_skips_the_remaining_species(tmp_path: Path) -> None:
    paths = _repo(
        tmp_path,
        [("Q1", "Talgoxe", "Great Tit"), ("Q2", "Blåmes", "Blue Tit"),
         ("Q3", "Svartmes", "Coal Tit")],
    )
    client = ExpensiveClient()
    outcomes = await run_web(paths, _options(max_cost=0.01, workers=1), client=client,
                             wiki=FakeWiki(), now=NOW)
    by_qid = {o.qid: o for o in outcomes}
    assert by_qid["Q1"].status == "skipped"
    assert "kostnadstaket nåddes:" in by_qid["Q1"].errors[0]
    assert not (paths.data_out / "Q1.json").exists()
    assert by_qid["Q2"].status == "skipped"
    assert by_qid["Q3"].status == "skipped"
    assert client.calls == 1  # the cap fired on the very first call; no more were made
    assert (paths.reports / "web-2026-10-01-000000.md").exists()


async def test_rerun_from_cache_keeps_the_existing_generated_timestamp(tmp_path: Path) -> None:
    paths = _repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    await run_web(paths, _options(), client=FakeClient(), wiki=FakeWiki(), now=NOW)
    first = json.loads((paths.data_out / "Q1.json").read_text(encoding="utf-8"))

    later = datetime(2026, 10, 2, tzinfo=UTC)
    second_client = FakeClient()
    outcomes = await run_web(paths, _options(), client=second_client, wiki=FakeWiki(), now=later)
    assert outcomes[0].status == "ok"
    assert second_client.calls == 0  # answered from cache, no new model call

    second = json.loads((paths.data_out / "Q1.json").read_text(encoding="utf-8"))
    assert second["generated"]["at"] == first["generated"]["at"]


async def test_run_web_closes_a_client_it_created_itself(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    import birdy_fetcher.web.run as run_module

    paths = _repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    closed: list[bool] = []

    @dataclass
    class FakeInternalClient:
        async def parse_web_text(
            self, *, model: str, system: str, messages: list[MessageParam], max_tokens: int,
            effort: str,
        ) -> StructuredReply:
            return StructuredReply(valid_output(), "{}", 1000, 500, "end_turn")

        async def aclose(self) -> None:
            closed.append(True)

    monkeypatch.setattr(run_module, "AnthropicStructuredClient", FakeInternalClient)

    outcomes = await run_web(paths, _options(), client=None, wiki=FakeWiki(), now=NOW)
    assert outcomes[0].status == "ok"
    assert closed == [True]


async def test_run_web_does_not_close_a_caller_supplied_client(tmp_path: Path) -> None:
    paths = _repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    client = FakeClient()
    await run_web(paths, _options(), client=client, wiki=FakeWiki(), now=NOW)
    assert client.calls == 1
    assert client.aclose_called is False  # the caller owns this client's lifecycle
