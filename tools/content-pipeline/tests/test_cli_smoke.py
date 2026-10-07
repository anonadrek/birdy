"""Smoke test: CLI imports and shows --help without error."""

from __future__ import annotations

from pathlib import Path

import pytest
from click.testing import CliRunner

from birdy_fetcher import cli as cli_module
from birdy_fetcher.cli import main
from birdy_fetcher.web.record import facts_hash, load_record, record_path, save_record

from .test_web_waves import _ready
from .web_repo import make_repo


def test_cli_help_runs() -> None:
    runner = CliRunner()
    result = runner.invoke(main, ["--help"])
    assert result.exit_code == 0
    assert "birdy-fetcher" in result.output


def test_doctor_subcommand_exists() -> None:
    # doctor exits 1 when the environment is incomplete (no ANTHROPIC_API_KEY, no local
    # sources/), which is the normal state in CI. This smoke test only proves the command
    # is wired up and runs its checks without crashing, whatever the environment.
    runner = CliRunner()
    result = runner.invoke(main, ["doctor"])
    assert result.exit_code in (0, 1), result.output
    assert result.exception is None or isinstance(result.exception, SystemExit)
    assert "ANTHROPIC_API_KEY" in result.output


def test_refresh_dry_run_flag_exists() -> None:
    runner = CliRunner()
    result = runner.invoke(main, ["refresh", "--help"])
    assert result.exit_code == 0
    assert "--dry-run" in result.output
    assert "--max-cost" in result.output


def test_web_lists_every_step() -> None:
    result = CliRunner().invoke(main, ["web", "--help"])
    assert result.exit_code == 0
    for step in (
        "sources", "facts", "verify", "waves", "sheet", "import", "write",
        "compare-candidates", "compare", "publish",
    ):  # fmt: skip
        assert step in result.output


def test_web_sources_help_lists_its_flags() -> None:
    runner = CliRunner()
    result = runner.invoke(main, ["web", "sources", "--help"])
    assert result.exit_code == 0
    for flag in ("--species", "--refresh", "--force", "--workers", "--dry-run"):
        assert flag in result.output


def test_web_facts_requires_an_api_key(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_AUTH_TOKEN", raising=False)
    result = CliRunner().invoke(main, ["web", "facts", "--species", "Q1", "--max-cost", "1"])
    assert result.exit_code != 0
    assert "ANTHROPIC_API_KEY" in str(result.output)


def test_web_verify_passes_the_facts_settings_for_the_v1_retry(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Minor 3 + follow-up 7 (final review 2026-10-06): explicit --facts-model/--facts-effort
    reach the retry; without them the options are None, which means "the record's own
    generated.facts, else the facts defaults" (resolved per species in verify_step)."""
    from birdy_fetcher.web import verify_step
    from birdy_fetcher.web.verify_step import VerifyOptions

    seen: list[VerifyOptions] = []

    audio_sources: list[object] = []

    async def fake_run_verify(
        paths: object, options: VerifyOptions, *, audio: object = None
    ) -> list[object]:
        seen.append(options)
        audio_sources.append(audio)
        return []

    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key-not-used")
    monkeypatch.setattr(verify_step, "run_verify", fake_run_verify)
    base = ["web", "verify", "--species", "Q1", "--max-cost", "1"]
    result = CliRunner().invoke(main, [*base, "--facts-model", "sonnet", "--facts-effort", "low"])
    assert result.exit_code == 0, result.output
    assert (seen[0].facts_model_key, seen[0].facts_effort) == ("sonnet", "low")
    result = CliRunner().invoke(main, base)
    assert result.exit_code == 0, result.output
    assert (seen[1].facts_model_key, seen[1].facts_effort) == (None, None)
    # V4 may try the next allowed Commons recording (fix wave 2026-10-07).
    from birdy_fetcher.web.audio import CommonsAudioClient

    assert all(isinstance(a, CommonsAudioClient) for a in audio_sources)


def test_web_facts_defaults_are_the_shared_constants(monkeypatch: pytest.MonkeyPatch) -> None:
    """Follow-up 7: the CLI default and FactsOptions' default are one constant."""
    from birdy_fetcher.web import facts_step
    from birdy_fetcher.web.facts_step import FactsOptions

    seen: list[FactsOptions] = []

    async def fake_run_facts(paths: object, options: FactsOptions) -> list[object]:
        seen.append(options)
        return []

    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key-not-used")
    monkeypatch.setattr(facts_step, "run_facts", fake_run_facts)
    result = CliRunner().invoke(main, ["web", "facts", "--species", "Q1", "--max-cost", "1"])
    assert result.exit_code == 0, result.output
    defaults = FactsOptions()
    assert (seen[0].model_key, seen[0].effort) == (defaults.model_key, defaults.effort)


def test_web_verify_reports_a_failed_audio_preflight_without_a_traceback(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Follow-up 3: the whole run stops when the audio model cannot run at all."""
    from birdy_fetcher.web import verify_step
    from birdy_fetcher.web.verify_step import AudioPreflightFailed, VerifyOptions

    async def fake_run_verify(
        paths: object, options: VerifyOptions, *, audio: object = None
    ) -> list[object]:
        raise AudioPreflightFailed("Ljudmodellen kunde inte köras: uv saknas; inga anrop gjordes")

    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key-not-used")
    monkeypatch.setattr(verify_step, "run_verify", fake_run_verify)
    result = CliRunner().invoke(main, ["web", "verify", "--species", "Q1", "--max-cost", "1"])
    assert result.exit_code == 1
    assert "inga anrop gjordes" in result.output
    assert "Traceback" not in result.output


def test_web_import_rejects_a_malformed_date(tmp_path: Path) -> None:
    """Minor 7 (final review 2026-10-06): `verification.at` must match fas 2's zod date
    regex; a malformed --date stops before anything is read."""
    sheet = tmp_path / "undantag.csv"
    sheet.write_text("x", encoding="utf-8")
    result = CliRunner().invoke(
        main, ["web", "import", "--file", str(sheet), "--date", "2026-13-01"]
    )
    assert result.exit_code == 2
    assert "--date" in result.output


def test_web_import_normalises_the_date(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    """`date.fromisoformat` also reads the basic form 20261101; what reaches the record is
    always YYYY-MM-DD."""
    from birdy_fetcher.web import review_sheet

    seen: list[str] = []

    def fake_import_wave(
        paths: object, path: Path, *, wave: int | None, date: str
    ) -> review_sheet.ImportResult:
        seen.append(date)
        return review_sheet.ImportResult()

    monkeypatch.setattr(review_sheet, "import_wave", fake_import_wave)
    sheet = tmp_path / "undantag.csv"
    sheet.write_text("x", encoding="utf-8")
    result = CliRunner().invoke(main, ["web", "import", "--file", str(sheet), "--date", "20261101"])
    assert result.exit_code == 0, result.output
    assert seen == ["2026-11-01"]


def test_web_write_help_lists_its_flags() -> None:
    runner = CliRunner()
    result = runner.invoke(main, ["web", "write", "--help"])
    assert result.exit_code == 0
    for flag in (
        "--wave",
        "--species",
        "--model",
        "--effort",
        "--checker-model",
        "--max-cost",
        "--regenerate",
        "--allow-unreviewed",
        "--workers",
    ):
        assert flag in result.output


@pytest.mark.parametrize(
    "args",
    [
        ["web", "facts", "--species", "Q1"],
        ["web", "verify", "--species", "Q1"],
        ["web", "write", "--species", "Q1"],
        ["web", "compare"],
    ],
)
def test_the_paid_commands_require_a_cost_cap(
    args: list[str], monkeypatch: pytest.MonkeyPatch
) -> None:
    """Minor 13 (final review 2026-10-06): no paid run without a cap. Click rejects the
    missing option (exit 2) before the command body runs. No key in the environment, so
    nothing could reach the API even if the option were optional (that exits 1)."""
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_AUTH_TOKEN", raising=False)
    result = CliRunner().invoke(main, args)
    assert result.exit_code == 2
    assert "--max-cost" in result.output


def test_web_write_requires_wave_or_species() -> None:
    result = CliRunner().invoke(main, ["web", "write", "--max-cost", "1"])
    assert result.exit_code != 0
    assert "--wave" in result.output


def test_web_write_rejects_the_same_model_for_writer_and_checker() -> None:
    # I3 (review fix 2026-10-06, spec §9.6): the checker must be a different model than
    # the writer. This check runs before the API key check, so no key is needed here.
    result = CliRunner().invoke(
        main,
        [
            "web",
            "write",
            "--species",
            "Q1",
            "--model",
            "sonnet",
            "--checker-model",
            "sonnet",
            "--max-cost",
            "1",
        ],
    )
    assert result.exit_code != 0
    assert "olika modeller" in result.output


def test_web_compare_help_lists_its_flags() -> None:
    result = CliRunner().invoke(main, ["web", "compare", "--help"])
    assert result.exit_code == 0
    for flag in (
        "--top",
        "--model",
        "--effort",
        "--checker-model",
        "--max-cost",
        "--regenerate",
        "--workers",
    ):
        assert flag in result.output


def test_web_compare_rejects_the_same_model_for_writer_and_checker() -> None:
    result = CliRunner().invoke(
        main,
        ["web", "compare", "--model", "sonnet", "--checker-model", "sonnet", "--max-cost", "1"],
    )
    assert result.exit_code != 0
    assert "olika modeller" in result.output


def test_web_compare_requires_an_api_key(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_AUTH_TOKEN", raising=False)
    result = CliRunner().invoke(main, ["web", "compare", "--max-cost", "1"])
    assert result.exit_code != 0
    assert "ANTHROPIC_API_KEY" in str(result.output)


# -- web publish (Task 23 + review fixes 2026-10-06, I3/I4) -----------------------------
# `web publish` never calls a model, so unlike the commands above these invocations run
# for real -- but only against a fake repo (`_web_paths` monkeypatched), never the real
# one, so nothing here can touch a real species or comparison file.


def test_web_publish_requires_wave_or_species() -> None:
    result = CliRunner().invoke(main, ["web", "publish"])
    assert result.exit_code != 0


def test_web_publish_unknown_species_exits_nonzero(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    monkeypatch.setattr(cli_module, "_web_paths", lambda: paths)
    result = CliRunner().invoke(main, ["web", "publish", "--species", "Q999"])
    assert result.exit_code == 1


def test_web_publish_a_ready_species_exits_zero(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q1"), _ready("Q1", "Talgoxe", 1))
    monkeypatch.setattr(cli_module, "_web_paths", lambda: paths)
    result = CliRunner().invoke(main, ["web", "publish", "--species", "Q1"])
    assert result.exit_code == 0, result.output


def test_web_publish_a_not_yet_ready_species_exits_nonzero(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    # I3: --species names a real but unready species -- exit 1, even though the outcome
    # itself is "skipped", not "failed".
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    record = _ready("Q1", "Talgoxe", 1)
    record["status"] = "pending"
    save_record(record_path(paths.data_out, "Q1"), record)
    monkeypatch.setattr(cli_module, "_web_paths", lambda: paths)
    result = CliRunner().invoke(main, ["web", "publish", "--species", "Q1"])
    assert result.exit_code == 1


def test_web_publish_next_rejects_wave_and_species() -> None:
    result = CliRunner().invoke(main, ["web", "publish", "--next", "--wave", "1"])
    assert result.exit_code != 0
    assert "--next" in result.output


def test_web_publish_next_prints_none_on_an_empty_queue(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    monkeypatch.setattr(cli_module, "_web_paths", lambda: paths)
    result = CliRunner().invoke(main, ["web", "publish", "--next"])
    assert result.exit_code == 0
    assert result.output.strip() == "none"


def test_web_publish_next_prints_exactly_one_species_line(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q1"), _ready("Q1", "Talgoxe", 1))
    monkeypatch.setattr(cli_module, "_web_paths", lambda: paths)
    result = CliRunner().invoke(main, ["web", "publish", "--next"])
    assert result.exit_code == 0
    assert result.output.strip() == "species Q1"


def test_web_publish_species_outside_named_wave_exits_nonzero(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    # m2 (review fix 2026-10-06): named and not ok still exits 1, with the mismatch
    # named in the output rather than Q1 silently having no outcome at all.
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    save_record(record_path(paths.data_out, "Q1"), _ready("Q1", "Talgoxe", 1))  # wave 1
    monkeypatch.setattr(cli_module, "_web_paths", lambda: paths)
    result = CliRunner().invoke(main, ["web", "publish", "--species", "Q1", "--wave", "2"])
    assert result.exit_code == 1
    assert "inte i våg 2" in result.output


def test_web_publish_species_exit_code_ignores_an_unrelated_sides_comparison_staleness(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """N1 probe A, end to end: Q1 and Q2 published, Q1_Q2 live; only Q2's facts change.
    --species Q1 must exit 0 and never even mention Q1_Q2."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    live_a = _ready("Q1", "Talgoxe", 1)
    live_a["publish"] = True
    live_b = _ready("Q2", "Blåmes", 1)
    live_b["publish"] = True
    for record in (live_a, live_b):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    save_record(
        paths.comparisons_out / "Q1_Q2.json",
        {
            "a": "Q1",
            "b": "Q2",
            "status": "ok",
            "publish": True,
            "generated": {"factsHash": facts_hash(live_a) + facts_hash(live_b)},
        },
    )
    struck = load_record(record_path(paths.data_out, "Q2"))
    assert struck is not None
    struck["facts"] = [f for f in struck["facts"] if f["id"] != "f04"]
    new_hash = facts_hash(struck)
    struck["generated"]["verify"]["factsHash"] = new_hash
    struck["generated"]["text"]["factsHash"] = new_hash
    save_record(record_path(paths.data_out, "Q2"), struck)
    monkeypatch.setattr(cli_module, "_web_paths", lambda: paths)
    result = CliRunner().invoke(main, ["web", "publish", "--species", "Q1"])
    assert result.exit_code == 0, result.output
    assert "Q1_Q2" not in result.output


def test_web_publish_species_exit_code_ignores_a_reported_but_unselected_comparison(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """N1 probe B, end to end: Q1's OWN facts change (the spot-check republish path).
    Q1_Q2 is reported failed (loudly, for Albin), but --species Q1 still exits 0 so fas
    2's loop does not revert the page it just correctly published."""
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    ready1 = _ready("Q1", "Talgoxe", 1)
    ready2 = _ready("Q2", "Blåmes", 1)
    for record in (ready1, ready2):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    save_record(
        paths.comparisons_out / "Q1_Q2.json",
        {
            "a": "Q1",
            "b": "Q2",
            "status": "ok",
            "publish": True,
            "generated": {"factsHash": facts_hash(ready1) + facts_hash(ready2)},
        },
    )
    struck = load_record(record_path(paths.data_out, "Q1"))
    assert struck is not None
    struck["facts"] = [f for f in struck["facts"] if f["id"] != "f04"]
    new_hash = facts_hash(struck)
    struck["generated"]["verify"]["factsHash"] = new_hash
    struck["generated"]["text"]["factsHash"] = new_hash
    save_record(record_path(paths.data_out, "Q1"), struck)
    monkeypatch.setattr(cli_module, "_web_paths", lambda: paths)
    result = CliRunner().invoke(main, ["web", "publish", "--species", "Q1"])
    assert result.exit_code == 0, result.output
    assert "sätt publish: false" in result.output


def test_web_publish_species_pair_with_a_not_current_comparison_exits_nonzero(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    # N1 item 1 (second re-review): --species Q1 --species Q2 tries to publish Q1_Q2
    # too, so a not-current Q1_Q2 is this call's own business and must exit 1.
    paths = make_repo(tmp_path, [("Q1", "Talgoxe", "Great Tit")])
    ready1 = _ready("Q1", "Talgoxe", 1)
    ready2 = _ready("Q2", "Blåmes", 1)
    for record in (ready1, ready2):
        save_record(record_path(paths.data_out, record["qid"]), record)
    paths.comparisons_out.mkdir(parents=True, exist_ok=True)
    save_record(
        paths.comparisons_out / "Q1_Q2.json",
        {
            "a": "Q1",
            "b": "Q2",
            "status": "ok",
            "publish": False,
            "generated": {"factsHash": "stale0000stale0000stale0000stal0"},
        },
    )
    monkeypatch.setattr(cli_module, "_web_paths", lambda: paths)
    result = CliRunner().invoke(main, ["web", "publish", "--species", "Q1", "--species", "Q2"])
    assert result.exit_code == 1


def test_web_import_exits_non_zero_and_names_pages_to_republish(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """I8 (final review 2026-10-06)."""
    from birdy_fetcher.web import review_sheet

    def fake_import_wave(
        paths: object, path: Path, *, wave: int | None, date: str
    ) -> review_sheet.ImportResult:
        return review_sheet.ImportResult(
            changed=["Q1"],
            republish=[
                review_sheet.Republish(
                    "Q1",
                    "Talgoxe",
                    ["texten är inte skriven ur det nuvarande faktabladet"],
                    ["uv run birdy-fetcher web write --species Q1 --max-cost 2"],
                )
            ],
        )

    monkeypatch.setattr(review_sheet, "import_wave", fake_import_wave)
    sheet = tmp_path / "stickprov.csv"
    sheet.write_text("x", encoding="utf-8")
    result = CliRunner().invoke(main, ["web", "import", "--file", str(sheet)])
    assert result.exit_code == 1
    assert "Talgoxe" in result.output
    assert "web write --species Q1" in result.output


def test_web_import_exits_non_zero_when_a_recording_could_not_be_deleted(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    from birdy_fetcher.web import review_sheet

    def fake_import_wave(
        paths: object, path: Path, *, wave: int | None, date: str
    ) -> review_sheet.ImportResult:
        return review_sheet.ImportResult(audio_errors=["Q1: voice.mp3 kunde inte tas bort"])

    monkeypatch.setattr(review_sheet, "import_wave", fake_import_wave)
    sheet = tmp_path / "undantag.csv"
    sheet.write_text("x", encoding="utf-8")
    result = CliRunner().invoke(main, ["web", "import", "--file", str(sheet)])
    assert result.exit_code == 1
    assert "kunde inte tas bort" in result.output


def test_web_sheet_prints_decisions_it_does_not_carry(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    from birdy_fetcher.web import review_sheet

    def fake_export_wave(paths: object, wave: int | None) -> review_sheet.ExportResult:
        return review_sheet.ExportResult(
            path=tmp_path / "undantag.csv",
            not_carried=["väntar på web verify, raderna behölls oförändrade: Q1 Talgoxe (1)"],
            kept=1,
        )

    monkeypatch.setattr(review_sheet, "export_wave", fake_export_wave)
    result = CliRunner().invoke(main, ["web", "sheet"])
    assert result.exit_code == 0, result.output
    assert "väntar på web verify" in result.output


@pytest.mark.parametrize("command", ["facts", "verify", "write", "compare"])
def test_the_paid_commands_offer_opus55(command: str) -> None:
    """R3 (2026-10-07): Opus 5.5 can be chosen wherever a model can, defaults unchanged."""
    result = CliRunner().invoke(main, ["web", command, "--help"])
    assert result.exit_code == 0
    assert "opus55" in result.output


def test_web_write_accepts_opus55_as_a_model() -> None:
    # Same model for writer and checker is refused after click accepted both choices, so
    # the message proves `opus55` passed click.Choice (an unknown value exits 2 earlier).
    result = CliRunner().invoke(
        main,
        [
            "web",
            "write",
            "--species",
            "Q1",
            "--model",
            "opus55",
            "--checker-model",
            "opus55",
            "--max-cost",
            "1",
        ],
    )
    assert "olika modeller" in result.output


def test_the_defaults_are_albins_choice_opus55_writes_sonnet_checks() -> None:
    """R3 (2026-10-07): Albin chose Opus 5.5 for the fact sheets and the texts."""
    from birdy_fetcher.web.compare import CompareOptions
    from birdy_fetcher.web.defaults import FACTS_EFFORT, FACTS_MODEL_KEY, TEXT_MODEL_KEY
    from birdy_fetcher.web.llm import MODELS
    from birdy_fetcher.web.text_step import WriteOptions
    from birdy_fetcher.web.verify_step import VerifyOptions

    assert MODELS[FACTS_MODEL_KEY] == MODELS[TEXT_MODEL_KEY] == "claude-opus-5-5"
    assert FACTS_EFFORT == "high"
    assert WriteOptions().model_key == CompareOptions().model_key == "opus55"
    assert WriteOptions().checker_key == CompareOptions().checker_key == "sonnet"
    assert VerifyOptions().model_key == "sonnet"
    for command in ("write", "compare"):
        help_text = CliRunner().invoke(main, ["web", command, "--help"]).output
        assert "opus55" in help_text
