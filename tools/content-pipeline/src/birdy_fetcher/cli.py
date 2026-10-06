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
from .web.defaults import EFFORTS, FACTS_EFFORT, FACTS_MODEL_KEY
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
@click.option(
    "--force",
    is_flag=True,
    help=(
        "Hämta om källor även för arter som har ett faktablad (byter Wikipediaversion, "
        "rapportdata och inspelning under faktabladet; kör web facts och verify igen)."
    ),
)
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
@click.option(
    "--model", "model_key", type=click.Choice(["opus", "sonnet"]), default=FACTS_MODEL_KEY
)
@click.option("--effort", type=click.Choice(EFFORTS), default=FACTS_EFFORT)
@click.option(
    "--max-cost",
    type=click.FloatRange(min=0, min_open=True),
    required=True,
    help="Kostnadstak i USD för körningen (krävs).",
)
@click.option("--force", is_flag=True, help="Ta fram faktablad även för granskade arter.")
@click.option("--regenerate", is_flag=True, help="Fråga modellen igen trots cachat svar.")
@click.option("--workers", type=click.IntRange(min=1), default=4)
def web_facts(
    species: tuple[str, ...],
    model_key: str,
    effort: str,
    max_cost: float,
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
@click.option(
    "--max-cost",
    type=click.FloatRange(min=0, min_open=True),
    required=True,
    help="Kostnadstak i USD för körningen (krävs).",
)
@click.option("--force", is_flag=True, help="Kontrollera även arter som redan är kontrollerade.")
@click.option("--workers", type=click.IntRange(min=1), default=4)
@click.option(
    "--facts-model",
    "facts_model_key",
    type=click.Choice(["opus", "sonnet"]),
    default=None,
    help=(
        "Modell för V1-omförsökets nya faktablad. Standard: samma som artens faktablad, "
        f"annars {FACTS_MODEL_KEY}."
    ),
)
@click.option(
    "--facts-effort",
    type=click.Choice(EFFORTS),
    default=None,
    help=(
        f"Tankenivå för V1-omförsöket. Standard: samma som artens faktablad, annars {FACTS_EFFORT}."
    ),
)
def web_verify(
    species: tuple[str, ...],
    model_key: str,
    effort: str,
    max_cost: float,
    force: bool,
    workers: int,
    facts_model_key: str | None,
    facts_effort: str | None,
) -> None:
    """Automatisk kontroll (V1 till V4) av faktabladet. Kostar pengar (V1)."""
    from .web.verify_step import AudioPreflightFailed, VerifyOptions, run_verify

    _require_api_key()
    paths = _web_paths()
    options = VerifyOptions(
        qids=species,
        model_key=model_key,
        effort=effort,
        max_cost=max_cost,
        force=force,
        workers=workers,
        facts_model_key=facts_model_key,
        facts_effort=facts_effort,
    )
    try:
        outcomes = asyncio.run(run_verify(paths, options))
    except AudioPreflightFailed as exc:
        # Follow-up 3 (wave A review): the audio model cannot run at all; nothing was paid
        # or written.
        raise click.ClickException(str(exc)) from exc
    _print_outcomes(outcomes, paths.reports)


@web.command("write")
@click.option("--wave", type=click.IntRange(1, 3), default=None)
@click.option("--species", multiple=True, help="Q-ID(s) i stället för en våg.")
@click.option("--model", "model_key", type=click.Choice(["opus", "sonnet"]), default="opus")
@click.option("--effort", type=click.Choice(["low", "medium", "high"]), default="high")
@click.option(
    "--checker-model", "checker_key", type=click.Choice(["opus", "sonnet"]), default="sonnet"
)
@click.option(
    "--max-cost",
    type=click.FloatRange(min=0, min_open=True),
    required=True,
    help="Kostnadstak i USD för körningen (krävs).",
)
@click.option("--regenerate", is_flag=True, help="Skriv om även texter som är aktuella.")
@click.option(
    "--retry-failed",
    is_flag=True,
    help="Försök igen med texter som misslyckades förra gången med samma indata.",
)
@click.option(
    "--allow-unreviewed",
    is_flag=True,
    help="Skriv även ur okontrollerade faktablad (bara provkörning, publiceras aldrig).",
)
@click.option("--workers", type=click.IntRange(min=1), default=4)
def web_write(
    wave: int | None,
    species: tuple[str, ...],
    model_key: str,
    effort: str,
    checker_key: str,
    max_cost: float,
    regenerate: bool,
    retry_failed: bool,
    allow_unreviewed: bool,
    workers: int,
) -> None:
    """Steg 3: text ur det kontrollerade faktabladet, kontrollerad mening för mening. Kostar
    pengar."""
    from .web.text_step import WriteOptions, run_write

    if wave is None and not species:
        raise click.UsageError("Ange --wave eller --species.")
    if model_key == checker_key:
        # I3 (review fix 2026-10-06, spec §9.6): the checker must be a different model
        # than the writer, in a fresh context.
        raise click.UsageError("Skribent och kontroll måste vara olika modeller.")
    _require_api_key()
    paths = _web_paths()
    options = WriteOptions(
        wave=wave,
        qids=species,
        model_key=model_key,
        effort=effort,
        checker_key=checker_key,
        max_cost=max_cost,
        regenerate=regenerate,
        allow_unreviewed=allow_unreviewed,
        workers=workers,
        retry_failed=retry_failed,
    )
    _print_outcomes(asyncio.run(run_write(paths, options)), paths.reports)


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
@click.option(
    "--seed",
    type=int,
    default=None,
    help="Frö för dragningen. Standard: slumpas och sparas i review/stickprov-state.json.",
)
@click.option(
    "--extra", multiple=True, help="Extra Q-ID(er) till stickprovet efter ett bekräftat missat fel."
)
@click.option(
    "--force",
    is_flag=True,
    help="Dra direkt, utan att vänta på SPOT_CHECK_BATCH fler publicerade arter.",
)
def web_spot_check(seed: int | None, extra: tuple[str, ...], force: bool) -> None:
    """Stickprov efter publicering (spec Revision 2026-10-05 (b)): 2 arter per 40
    publicerade, räknat sedan förra dragningen (review/stickprov-state.json). Körs av fas
    2:s publiceringsloop efter varje publicerad art; skriver bara när något faktiskt
    drogs."""
    from .web.review_sheet import export_spot_check

    result = export_spot_check(_web_paths(), seed=seed, extra_species=extra, force=force)
    if result is None:
        click.echo("Inget drogs än (för få nypublicerade arter sedan sist).")
        return
    click.echo(
        f"Stickprov, dragning {result.draw} (frö {result.seed}): {', '.join(result.species)}. "
        f"Ladda upp {result.path} till Drive som Google-kalkylark; Albin fyller i Beslut "
        "på varje rad."
    )


def _iso_date(ctx: click.Context, param: click.Parameter, value: str | None) -> str | None:
    """`verification.at` must match fas 2's zod date regex (Minor 7, final review
    2026-10-06): validated, and normalised to YYYY-MM-DD (fromisoformat also reads
    20261101)."""
    from datetime import date

    if value is None:
        return None
    try:
        return date.fromisoformat(value.strip()).isoformat()
    except ValueError as exc:
        raise click.BadParameter(f"{value!r} är inget datum, skriv YYYY-MM-DD.") from exc


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
    "--date",
    "review_date",
    default=None,
    callback=_iso_date,
    help="Kontrolldatum, YYYY-MM-DD. Standard: i dag.",
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
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    click.echo(
        f"{len(result.changed)} arter uppdaterade {when}. "
        f"Inspelningar strukna: {len(result.removed_audio)}."
    )
    for line in result.waiting:
        click.echo(f"Väntar på beslut: {line}")
    for line in result.ignored:
        click.echo(f"Hoppade över: {line}")


@web.command("compare-candidates")
def web_compare_candidates() -> None:
    """Skriver förväxlingsparen till review/comparison-volumes.csv. Gratis."""
    from .web.compare import VOLUMES_FILE, candidate_pairs, write_candidates
    from .web.record import load_all

    paths = _web_paths()
    records = load_all(paths.data_out)
    pairs = candidate_pairs(records)
    result = write_candidates(paths.review / VOLUMES_FILE, pairs, records)
    click.echo(
        f"{len(pairs)} par i {result.path}. {result.missing_volumes} saknar volym "
        "(tom cell, skilt från en ifylld nolla). Fyll i sv_volume och en_volume."
    )
    if result.cleared_sv or result.cleared_en:
        click.echo(
            f"Namnbyte rensade {result.cleared_sv} sv_volume och {result.cleared_en} "
            "en_volume (fyll i på nytt)."
        )


@web.command("compare")
@click.option(
    "--top", type=click.IntRange(min=1), default=30, help="Antal par med störst sökvolym."
)
@click.option("--model", "model_key", type=click.Choice(["opus", "sonnet"]), default="opus")
@click.option("--effort", type=click.Choice(["low", "medium", "high"]), default="high")
@click.option(
    "--checker-model", "checker_key", type=click.Choice(["opus", "sonnet"]), default="sonnet"
)
@click.option(
    "--max-cost",
    type=click.FloatRange(min=0, min_open=True),
    required=True,
    help="Kostnadstak i USD för körningen (krävs).",
)
@click.option("--regenerate", is_flag=True, help="Skriv om även jämförelser som är aktuella.")
@click.option(
    "--retry-failed",
    is_flag=True,
    help="Försök igen med jämförelser som misslyckades förra gången med samma indata.",
)
@click.option("--workers", type=click.IntRange(min=1), default=4)
def web_compare(
    top: int,
    model_key: str,
    effort: str,
    checker_key: str,
    max_cost: float,
    regenerate: bool,
    retry_failed: bool,
    workers: int,
) -> None:
    """Jämförelsetexter för de mest sökta förväxlingsparen, ur två kontrollerade faktablad.
    Kostar pengar."""
    from .web.compare import CompareOptions, run_compare

    if model_key == checker_key:
        # Spec §9.6, same rule as `web write` (I3): the checker must be a different model.
        raise click.UsageError("Skribent och kontroll måste vara olika modeller.")
    _require_api_key()
    paths = _web_paths()
    options = CompareOptions(
        top=top,
        model_key=model_key,
        effort=effort,
        checker_key=checker_key,
        max_cost=max_cost,
        regenerate=regenerate,
        workers=workers,
        retry_failed=retry_failed,
    )
    try:
        outcomes = asyncio.run(run_compare(paths, options))
    except ValueError as exc:
        # A hand-edited comparison-volumes.csv with a bad or conflicting volume stops the
        # run before any call is paid for; show it as a plain error, not a traceback.
        raise click.ClickException(str(exc)) from exc
    _print_outcomes(outcomes, paths.reports)


@web.command("publish")
@click.option("--wave", type=click.IntRange(1, 3))
@click.option(
    "--species",
    multiple=True,
    help="QID, kan upprepas. En art i taget är det normala läget (ändrat 2026-10-05 (b)).",
)
@click.option(
    "--next",
    "next_mode",
    is_flag=True,
    help=(
        "Publicerar högst en färdig post (en art eller en jämförelse) i könordning, åt "
        "fas 2:s löpande loop. Utesluter --wave/--species."
    ),
)
@click.option(
    "--exclude",
    multiple=True,
    help="QID eller en jämförelses filnamn utan .json, att hoppa över med --next. Kan upprepas.",
)
def web_publish(
    wave: int | None, species: tuple[str, ...], next_mode: bool, exclude: tuple[str, ...]
) -> None:
    """Slår på publish för färdiga arter och jämförelser, filtrerat på våg, på en eller
    flera bestämda arter, eller båda -- eller (--next) högst en färdig post i könordning,
    åt fas 2:s löpande publiceringsloop. Gratis, ingen modell.

    Med --next skriver kommandot EXAKT en rad på stdout: "species QID", "comparison
    STEM", eller "none" när inget är klart -- inget annat går till stdout i det läget
    (publiceringsloopen läser den raden maskinellt); fel/usage-meddelanden går som
    vanligt till stderr via click."""
    from .web.waves import publish_next, publish_wave

    if next_mode:
        if wave is not None or species:
            raise click.UsageError("--next kan inte kombineras med --wave eller --species.")
        pick = publish_next(_web_paths(), exclude=frozenset(exclude))
        click.echo("none" if pick is None else f"{pick.kind} {pick.id}")
        return
    if wave is None and not species:
        raise click.UsageError("Ange --wave, en eller flera --species, eller båda.")
    paths = _web_paths()
    outcomes = publish_wave(paths, wave, list(species) or None)
    _print_outcomes(outcomes, paths.reports)
    status_by_qid = {o.qid: o.status for o in outcomes}
    if species:
        # I3 + N1 item 2 (review fix 2026-10-06): in --species mode, only the named
        # species (an unknown QID gets its own "failed" outcome under that same qid) and
        # a comparison THIS call itself tried to publish (both sides named,
        # `StepOutcome.attempted` set by `_publish_comparisons`) decide the exit code.
        # An already-published comparison's staleness finding stays loud in the
        # output/report ("sätt publish: false") but never flips it -- otherwise fas 2's
        # loop would `git checkout -- src/data` the very page it just correctly
        # published, over a problem on a species this call never touched.
        counted = set(species) | {o.qid for o in outcomes if o.attempted}
        if any(status_by_qid.get(qid) != "ok" for qid in counted):
            raise click.exceptions.Exit(1)
    elif any(o.status == "failed" for o in outcomes):
        raise click.exceptions.Exit(1)


if __name__ == "__main__":
    main()
