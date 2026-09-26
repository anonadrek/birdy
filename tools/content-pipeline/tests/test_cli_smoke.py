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
    runner = CliRunner()
    result = runner.invoke(main, ["doctor"])
    assert result.exit_code == 0


def test_refresh_dry_run_flag_exists() -> None:
    runner = CliRunner()
    result = runner.invoke(main, ["refresh", "--help"])
    assert result.exit_code == 0
    assert "--dry-run" in result.output
    assert "--max-cost" in result.output


def test_web_help_lists_all_flags() -> None:
    runner = CliRunner()
    result = runner.invoke(main, ["web", "--help"])
    assert result.exit_code == 0
    for flag in ("--species", "--model", "--effort", "--max-cost", "--force",
                "--refresh-sources", "--regenerate", "--workers", "--dry-run"):
        assert flag in result.output


def test_web_workers_must_be_at_least_one() -> None:
    # click validates --workers before the command body runs, so this never touches
    # the network -- it is safe alongside the --help-only rule for `web`.
    runner = CliRunner()
    # --species Q1 is a second guard: should IntRange ever be loosened, the unknown QID
    # fails in load_approved before any Wikipedia request.
    result = runner.invoke(main, ["web", "--dry-run", "--workers", "0", "--species", "Q1"])
    assert result.exit_code != 0


def test_web_requires_an_api_key_unless_dry_run(monkeypatch: pytest.MonkeyPatch) -> None:
    # The key check is the very first thing `web` does, before touching any file or the
    # network, so invoking it this way stays within the "never run web except --help" rule.
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_AUTH_TOKEN", raising=False)
    runner = CliRunner()
    result = runner.invoke(main, ["web", "--species", "Q1", "--max-cost", "1"])
    assert result.exit_code != 0
    assert "ANTHROPIC_API_KEY" in str(result.output)
