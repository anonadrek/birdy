"""Smoke test: CLI imports and shows --help without error."""

from __future__ import annotations

import pytest
from click.testing import CliRunner

from birdy_fetcher.cli import main


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
