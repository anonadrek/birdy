"""Smoke test: CLI imports and shows --help without error."""

from __future__ import annotations

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
