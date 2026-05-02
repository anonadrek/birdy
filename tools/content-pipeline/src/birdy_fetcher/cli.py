"""click-based CLI entrypoint. Subcommands stub out for later tasks."""

from __future__ import annotations

import click

from . import __version__


@click.group()
@click.version_option(__version__)
def main() -> None:
    """birdy-fetcher — fetch & generate species YAML for Birdy Bird Scanner."""


@main.command()
def doctor() -> None:
    """Run pre-flight checks (env vars, sources, cache health)."""
    click.echo("doctor: not implemented yet (Task 1 scaffold)")


@main.command()
@click.option("--resume", is_flag=True, help="Continue an aborted init.")
def init(resume: bool) -> None:
    """Build species_list.yaml from IOC + BirdLife checklists."""
    click.echo(f"init: not implemented yet (resume={resume})")


@main.command()
@click.option("--all", "all_species", is_flag=True)
@click.option("--species", multiple=True, help="Q-ID(s) to refresh.")
@click.option("--field", type=click.Choice(["text", "images", "all"]), default="all")
@click.option("--stale", is_flag=True, help="Only refresh entries older than 30 days.")
@click.option("--force", is_flag=True, help="Bypass cache.")
@click.option("--resume", is_flag=True, help="Resume an aborted refresh.")
@click.option("--workers", type=int, default=4)
@click.option("--max-cost", type=float, default=None, help="Abort if total cost exceeds USD.")
@click.option("--dry-run", is_flag=True, help="Print intended actions, no API calls.")
@click.option("--model", type=click.Choice(["haiku", "sonnet"]), default="haiku")
def refresh(**kwargs: object) -> None:
    """Refresh species data from external sources."""
    click.echo(f"refresh: not implemented yet (args={kwargs})")


@main.command()
def status() -> None:
    """Report on coverage, review status, cache health."""
    click.echo("status: not implemented yet")


@main.command()
def eval_prompts() -> None:
    """Generate ten prompt-tuning samples for manual review."""
    click.echo("eval-prompts: not implemented yet")


if __name__ == "__main__":
    main()
