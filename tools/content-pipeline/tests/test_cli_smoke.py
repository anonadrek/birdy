"""Smoke test: CLI imports and shows --help without error."""

from __future__ import annotations

from pathlib import Path

import pytest
from click.testing import CliRunner

from birdy_fetcher import cli as cli_module
from birdy_fetcher.cli import main
from birdy_fetcher.web.record import record_path, save_record

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


def test_web_help_lists_all_flags() -> None:
    runner = CliRunner()
    result = runner.invoke(main, ["web", "v1", "--help"])
    assert result.exit_code == 0
    for flag in (
        "--species",
        "--model",
        "--effort",
        "--max-cost",
        "--force",
        "--refresh-sources",
        "--regenerate",
        "--workers",
        "--dry-run",
    ):
        assert flag in result.output


def test_web_workers_must_be_at_least_one() -> None:
    # click validates --workers before the command body runs, so this never touches
    # the network -- it is safe alongside the --help-only rule for `web`.
    runner = CliRunner()
    # --species Q1 is a second guard: should IntRange ever be loosened, the unknown QID
    # fails in load_approved before any Wikipedia request.
    result = runner.invoke(main, ["web", "v1", "--dry-run", "--workers", "0", "--species", "Q1"])
    assert result.exit_code != 0


def test_web_requires_an_api_key_unless_dry_run(monkeypatch: pytest.MonkeyPatch) -> None:
    # The key check is the very first thing `web` does, before touching any file or the
    # network, so invoking it this way stays within the "never run web except --help" rule.
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_AUTH_TOKEN", raising=False)
    runner = CliRunner()
    result = runner.invoke(main, ["web", "v1", "--species", "Q1", "--max-cost", "1"])
    assert result.exit_code != 0
    assert "ANTHROPIC_API_KEY" in str(result.output)


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


def test_web_write_requires_wave_or_species() -> None:
    result = CliRunner().invoke(main, ["web", "write"])
    assert result.exit_code != 0


def test_web_write_rejects_the_same_model_for_writer_and_checker() -> None:
    # I3 (review fix 2026-10-06, spec §9.6): the checker must be a different model than
    # the writer. This check runs before the API key check, so no key is needed here.
    result = CliRunner().invoke(
        main,
        ["web", "write", "--species", "Q1", "--model", "sonnet", "--checker-model", "sonnet"],
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
        main, ["web", "compare", "--model", "sonnet", "--checker-model", "sonnet"]
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
