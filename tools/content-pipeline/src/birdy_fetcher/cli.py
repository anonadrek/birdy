"""click-based CLI entrypoint. Subcommands stub out for later tasks."""

from __future__ import annotations

import asyncio
import os
import sys
from collections import Counter
from collections.abc import Sequence
from pathlib import Path

import click
from rich.console import Console

from . import __version__
from .web.paths import WebPaths
from .web.report import StepOutcome


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


@main.command("build-mapping")
@click.option(
    "--labelmap",
    type=click.Path(exists=True, path_type=Path),
    default=Path("../../shared/ml/src/commonMain/composeResources/files/ml/aiy_labelmap.csv"),
)
@click.option("--model-version", required=True, help="ex: aiy_birds_v1")
@click.option(
    "--out",
    type=click.Path(path_type=Path),
    default=Path("../../shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json"),
)
def build_mapping(labelmap: Path, model_version: str, out: Path) -> None:
    """Build AIY class_index → Q-ID mapping via SPARQL P225 (taxon name)."""
    from datetime import UTC, datetime

    from .name_mapping import (
        parse_labelmap_csv,
        render_mapping_json_by_class_index,
        run_build_name_mapping,
    )

    pairs = parse_labelmap_csv(labelmap)
    result = asyncio.run(run_build_name_mapping(pairs))
    rendered = render_mapping_json_by_class_index(
        result,
        model_version=model_version,
        generated_at=datetime.now(UTC),
    )
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(rendered, encoding="utf-8")
    click.echo(
        f"Wrote {out} ({result.mapped_classes}/{result.requested_classes} mapped, "
        f"coverage={result.coverage_pct}%)"
    )


@main.group()
def web() -> None:
    """Artsidorna på birdy.community: källor, faktablad, granskning, text och jämförelser."""


def _web_paths() -> WebPaths:
    pipeline_root = Path(__file__).resolve().parent.parent.parent
    return WebPaths(repo_root=pipeline_root.parent.parent)


def _require_api_key() -> None:
    # The locked anthropic 0.97 SDK only reads ANTHROPIC_API_KEY / ANTHROPIC_AUTH_TOKEN, so
    # fail fast with a clear message instead of an opaque SDK error partway through a run.
    if not (os.environ.get("ANTHROPIC_API_KEY") or os.environ.get("ANTHROPIC_AUTH_TOKEN")):
        raise click.ClickException(
            "ANTHROPIC_API_KEY saknas. Lägg den i miljön eller i "
            "tools/content-pipeline/.env och kör med uv run --env-file .env ..."
        )


def _print_outcomes(outcomes: Sequence[StepOutcome], reports: Path) -> None:
    # Swedish and Polish author names and error text can contain characters or literal
    # `[...]` that would crash or mangle on a Windows console.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    console = Console(markup=False, highlight=False)
    for o in outcomes:
        if o.status not in ("ok", "pending"):
            console.print(f"{o.status:8} {o.name} ({o.qid}): {'; '.join(o.errors)}")
    counts = Counter(o.status for o in outcomes)
    console.print(f"Klart: {dict(counts)}. Rapporter i {reports}.")


@web.command("sources")
@click.option("--species", multiple=True, help="Q-ID(s). Utan flaggan körs alla granskade arter.")
@click.option("--refresh", is_flag=True, help="Hämta alla källor på nytt i stället för från cache.")
@click.option("--force", is_flag=True, help="Hämta om källor även för granskade faktablad.")
@click.option("--workers", type=click.IntRange(min=1), default=4)
@click.option("--dry-run", is_flag=True, help="Hämta och visa, men skriv inga filer.")
def web_sources(
    species: tuple[str, ...], refresh: bool, force: bool, workers: int, dry_run: bool
) -> None:
    """Steg 1: Wikipedia, Artportalen, rödlistan, inspelning och foton. Gratis."""
    from .web.sources_step import SourcesOptions, run_sources

    paths = _web_paths()
    options = SourcesOptions(
        qids=species, refresh=refresh, force=force, workers=workers, dry_run=dry_run
    )
    _print_outcomes(asyncio.run(run_sources(paths, options)), paths.reports)


@web.command("facts")
@click.option("--species", multiple=True, help="Q-ID(s). Utan flaggan körs alla granskade arter.")
@click.option("--model", "model_key", type=click.Choice(["opus", "sonnet"]), default="opus")
@click.option("--effort", type=click.Choice(["low", "medium", "high"]), default="high")
@click.option("--max-cost", type=float, default=None, help="Kostnadstak i USD för körningen.")
@click.option("--force", is_flag=True, help="Ta fram faktablad även för granskade arter.")
@click.option("--regenerate", is_flag=True, help="Fråga modellen igen trots cachat svar.")
@click.option("--workers", type=click.IntRange(min=1), default=4)
def web_facts(
    species: tuple[str, ...],
    model_key: str,
    effort: str,
    max_cost: float | None,
    force: bool,
    regenerate: bool,
    workers: int,
) -> None:
    """Steg 2: faktablad med citat ur Wikipedia. Kostar pengar."""
    from .web.facts_step import FactsOptions, run_facts

    _require_api_key()
    paths = _web_paths()
    options = FactsOptions(
        qids=species,
        model_key=model_key,
        effort=effort,
        max_cost=max_cost,
        force=force,
        regenerate=regenerate,
        workers=workers,
    )
    _print_outcomes(asyncio.run(run_facts(paths, options)), paths.reports)


@web.command("verify")
@click.option("--species", multiple=True, help="Q-ID(s). Utan flaggan körs alla med ett faktablad.")
@click.option("--model", "model_key", type=click.Choice(["opus", "sonnet"]), default="sonnet")
@click.option("--effort", type=click.Choice(["low", "medium", "high"]), default="high")
@click.option("--max-cost", type=float, default=None, help="Kostnadstak i USD för körningen.")
@click.option("--force", is_flag=True, help="Kontrollera även arter som redan är kontrollerade.")
@click.option("--workers", type=click.IntRange(min=1), default=4)
def web_verify(
    species: tuple[str, ...],
    model_key: str,
    effort: str,
    max_cost: float | None,
    force: bool,
    workers: int,
) -> None:
    """Automatisk kontroll (V1 till V4) av faktabladet. Kostar pengar (V1)."""
    from .web.verify_step import VerifyOptions, run_verify

    _require_api_key()
    paths = _web_paths()
    options = VerifyOptions(
        qids=species,
        model_key=model_key,
        effort=effort,
        max_cost=max_cost,
        force=force,
        workers=workers,
    )
    _print_outcomes(asyncio.run(run_verify(paths, options)), paths.reports)


@web.command("waves")
@click.option("--size", type=click.IntRange(min=12), default=40, help="Antal arter i våg 1.")
@click.option(
    "--recompute", is_flag=True, help="Räkna om listan i stället för att läsa waves.json."
)
def web_waves(size: int, recompute: bool) -> None:
    """Delar in arterna i tre vågor (körordning, ingen publiceringsgrind) och skriver
    review/waves.json. Gratis."""
    from .web.waves import run_waves

    paths = _web_paths()
    waves = run_waves(paths, size=size, recompute=recompute)
    for number, qids in sorted(waves.items()):
        click.echo(f"Våg {number}: {len(qids)} arter")
    click.echo(f"Listan finns i {paths.review / 'waves.json'}.")


@web.command("sheet")
@click.option(
    "--wave",
    type=click.IntRange(1, 3),
    default=None,
    help="Filtrera till en våg. Utan flaggan (standard): alla vågor, i könordning.",
)
def web_sheet(wave: int | None) -> None:
    """Skriver undantagsarkets flaggor (alla vågor, löpande) till review/undantag.csv.
    Gratis."""
    from .web.review_sheet import export_wave

    result = export_wave(_web_paths(), wave)
    click.echo(f"{len(result.flagged)} flaggade arter. Ladda upp {result.path} till Drive.")


@web.command("spot-check")
@click.option("--seed", type=int, default=None, help="Frö för stickprovet. Standard: 2000.")
@click.option(
    "--extra", multiple=True, help="Extra Q-ID(er) till stickprovet efter ett bekräftat missat fel."
)
@click.option(
    "--force",
    is_flag=True,
    help="Dra direkt, utan att vänta på SPOT_CHECK_BATCH fler publicerade arter.",
)
def web_spot_check(seed: int | None, extra: tuple[str, ...], force: bool) -> None:
    """Stickprov efter publicering (spec Revision 2026-10-05 (b)). Körs av fas 2:s
    publiceringsloop efter varje publicerad art; skriver bara när något faktiskt drogs."""
    from .web.review_sheet import export_spot_check

    result = export_spot_check(_web_paths(), seed=seed, extra_species=extra, force=force)
    if result is None:
        click.echo("Inget drogs än (för få nypublicerade arter sedan sist).")
        return
    click.echo(
        f"Stickprov (frö {result.seed}): {', '.join(result.species)}. "
        f"Ladda upp {result.path} till Drive som Google-kalkylark."
    )


@web.command("import")
@click.option(
    "--wave",
    type=click.IntRange(1, 3),
    default=None,
    help="Valfritt säkerhetsnät, se Task 17 (ändrat 2026-10-05 (b)).",
)
@click.option(
    "--file",
    "sheet",
    type=click.Path(exists=True, dir_okay=False, path_type=Path),
    default=None,
    help="Exporterad CSV. Standard: review/undantag.csv.",
)
@click.option(
    "--date", "review_date", default=None, help="Kontrolldatum, YYYY-MM-DD. Standard: i dag."
)
def web_import(wave: int | None, sheet: Path | None, review_date: str | None) -> None:
    """Läser in Albins beslut ur undantagsarket (standard) eller stickprovet (--file
    review/stickprov.csv) och sätter verification på de berörda arterna. Ändrar
    ingenting om något är fel. En rättad stickprovsrad får ett nytt kontrolldatum,
    vilket fas 2:s publiceringsloop republicerar sidan med."""
    from datetime import date

    from .web.review_sheet import ReviewImportError, import_wave

    paths = _web_paths()
    path = sheet or paths.review / "undantag.csv"
    when = review_date or date.today().isoformat()
    try:
        result = import_wave(paths, path, wave=wave, date=when)
    except ReviewImportError as exc:
        raise click.ClickException(f"Arket har fel, inget ändrades:\n{exc}") from exc
    click.echo(
        f"{len(result.changed)} arter kontrollerade {when}. "
        f"Inspelningar strukna: {len(result.removed_audio)}."
    )


@web.command("v1")
@click.option("--species", multiple=True, help="Q-ID(s). Utan flaggan körs alla granskade arter.")
@click.option("--model", "model_key", type=click.Choice(["opus", "sonnet"]), default="opus")
@click.option(
    "--effort",
    type=click.Choice(["low", "medium", "high"]),
    default="high",
    help="Modellens svarsansträngning. 'high' är Opus 5-standarden (oförändrat).",
)
@click.option("--max-cost", type=float, default=None, help="Kostnadstak i USD för körningen.")
@click.option("--force", is_flag=True, help="Skriv över arter som redan har review: approved.")
@click.option("--refresh-sources", is_flag=True, help="Hämta Wikidata och Wikipedia på nytt.")
@click.option("--regenerate", is_flag=True, help="Fråga modellen igen trots cachat svar.")
@click.option("--workers", type=click.IntRange(min=1), default=4)
@click.option("--dry-run", is_flag=True, help="Hämta källor och visa artikelstorlek, inget anrop.")
def web_v1(
    species: tuple[str, ...],
    model_key: str,
    effort: str,
    max_cost: float | None,
    force: bool,
    refresh_sources: bool,
    regenerate: bool,
    workers: int,
    dry_run: bool,
) -> None:
    """Webbtexter, foton och licensdata för artsidorna på birdy.community."""
    from .web.run import WebRunOptions, run_web

    # --dry-run never calls the model, so it doesn't need a key.
    if not dry_run:
        _require_api_key()

    paths = _web_paths()
    options = WebRunOptions(
        qids=species,
        model_key=model_key,
        effort=effort,
        max_cost=max_cost,
        force=force,
        refresh_sources=refresh_sources,
        regenerate=regenerate,
        workers=workers,
        dry_run=dry_run,
    )
    outcomes = asyncio.run(run_web(paths, options, client=None))

    # 180 species' worth of Swedish/Polish/etc. author names and error text can contain
    # characters or literal `[...]` that would otherwise crash or mangle on a Windows
    # console; disable rich markup interpretation and make stdout tolerant of encoding gaps.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    console = Console(markup=False, highlight=False)
    for o in outcomes:
        if o.status != "ok":
            console.print(f"{o.status:8} {o.name_sv} ({o.qid}): {'; '.join(o.errors)}")
    counts = Counter(o.status for o in outcomes)
    if dry_run:
        console.print(f"Klart: {dict(counts)}.")
    else:
        console.print(f"Klart: {dict(counts)}. Rapport i {paths.reports}.")
    if counts["failed"]:
        console.print("Några arter fick ingen sida. Se rapporten.", style="yellow")


if __name__ == "__main__":
    main()
