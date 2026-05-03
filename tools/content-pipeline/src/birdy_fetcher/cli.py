"""click-based CLI entrypoint. Subcommands stub out for later tasks."""

from __future__ import annotations

import asyncio
from pathlib import Path

import click

from . import __version__


@click.group()
@click.version_option(__version__)
def main() -> None:
    """birdy-fetcher — fetch & generate species YAML for Birdy Bird Scanner."""


@main.command()
def doctor() -> None:
    """Run pre-flight checks (env vars, sources, cache health)."""
    from pathlib import Path

    from rich.console import Console
    from rich.table import Table

    from .doctor import run_doctor

    root = Path(__file__).resolve().parent.parent.parent
    report = run_doctor(root=root)
    table = Table(title="birdy-fetcher doctor")
    table.add_column("Check")
    table.add_column("OK")
    table.add_column("Detail")
    for c in report.checks:
        table.add_row(c.name, "OK" if c.ok else "FAIL", c.detail)
    Console().print(table)
    if not report.is_ok:
        raise click.exceptions.Exit(1)


@main.command()
@click.option(
    "--resume",
    is_flag=True,
    help="Re-run init while preserving manual edits in species_list.yaml.",
)
def init(resume: bool) -> None:
    """Build species_list.yaml from IOC + BirdLife checklists."""
    # species_list import is lazy because it pulls in pdfplumber/openpyxl/aiohttp,
    # which would slow `birdy-fetcher --help` for sibling commands.
    from .species_list import cli_init

    root = Path(__file__).resolve().parent.parent.parent
    exit_code = asyncio.run(
        cli_init(
            sources_dir=root / "sources",
            checklists_dir=root / "checklists",
            out_dir=root,
            resume=resume,
        )
    )
    if exit_code != 0:
        click.secho(
            "Mapping failures present — patch species_list.yaml manually then "
            "run `init --resume` to merge your additions.",
            fg="yellow",
        )
        raise click.exceptions.Exit(exit_code)
    click.secho("species_list.yaml generated, all species mapped.", fg="green")


@main.command()
@click.option("--all", "all_species", is_flag=True)
@click.option("--species", multiple=True, help="Q-ID(s) to refresh.")
@click.option(
    "--field",
    type=click.Choice(["text", "images", "all"]),
    default="all",
)
@click.option("--stale", is_flag=True)
@click.option("--force", is_flag=True)
@click.option("--resume", is_flag=True)
@click.option("--workers", type=int, default=4)
@click.option("--max-cost", type=float, default=None)
@click.option("--dry-run", is_flag=True)
@click.option("--model", type=click.Choice(["haiku", "sonnet"]), default="haiku")
def refresh(
    all_species: bool,
    species: tuple[str, ...],
    field: str,
    stale: bool,
    force: bool,
    resume: bool,
    workers: int,
    max_cost: float | None,
    dry_run: bool,
    model: str,
) -> None:
    """Refresh species data from external sources."""
    from pathlib import Path

    from .orchestrator import RefreshOptions, build_context, run_refresh

    if not (all_species or species):
        raise click.UsageError("Must pass --all or --species Q-ID")

    pipeline_root = Path(__file__).resolve().parent.parent.parent
    content_root = pipeline_root.parent.parent / "shared" / "content"

    options = RefreshOptions(
        species_filter=list(species) if species else None,
        field=field,
        force=force,
        dry_run=dry_run,
        workers=workers,
        model=model,
        max_cost=max_cost,
    )
    ctx = build_context(pipeline_root, content_root, options)
    exit_code = asyncio.run(run_refresh(ctx))
    raise click.exceptions.Exit(exit_code)


@main.command()
def status() -> None:
    """Report on coverage, review status, cache health."""
    from pathlib import Path

    import yaml
    from rich.console import Console
    from rich.table import Table

    pipeline_root = Path(__file__).resolve().parent.parent.parent
    content_root = pipeline_root.parent.parent / "shared" / "content"

    species_list = (
        yaml.safe_load((pipeline_root / "species_list.yaml").read_text(encoding="utf-8"))
        if (pipeline_root / "species_list.yaml").exists()
        else []
    )
    yaml_files = list((content_root / "species").rglob("*.yaml"))

    table = Table(title="birdy-fetcher status")
    table.add_column("Metric")
    table.add_column("Value")
    table.add_row("listed species", str(len(species_list or [])))
    table.add_row("YAML files committed", str(len(yaml_files)))

    review_counts: dict[str, int] = {"approved": 0, "auto": 0, "needs_review": 0}
    for path in yaml_files:
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
        status_value = data.get("review_status", "auto")
        review_counts[status_value] = review_counts.get(status_value, 0) + 1
    for k, v in review_counts.items():
        table.add_row(f"  review_status: {k}", str(v))

    cache = pipeline_root / ".cache"
    table.add_row(
        "cache entries",
        str(sum(1 for _ in cache.iterdir())) if cache.exists() else "0",
    )
    Console().print(table)


@main.command()
def eval_prompts() -> None:
    """Generate ten prompt-tuning samples for manual review."""
    click.echo("eval-prompts: not implemented yet")


if __name__ == "__main__":
    main()
