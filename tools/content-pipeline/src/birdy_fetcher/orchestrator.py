"""Per-species orchestration of the refresh pipeline."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import yaml
from rich.console import Console
from rich.progress import (
    BarColumn,
    Progress,
    TaskProgressColumn,
    TextColumn,
    TimeRemainingColumn,
)

from .cache import Cache
from .claude_summarizer import ClaudeSummarizer, real_anthropic_client
from .cost import CostTracker
from .images import ImageProcessor, ImageSelector, rank_candidates
from .wikidata import WikidataClient
from .wikipedia import WikipediaClient
from .yaml_writer import (
    ImageRef,
    SpeciesYamlData,
    merge_overrides,
    write_species_yaml,
)

console = Console()


@dataclass
class RefreshOptions:
    species_filter: list[str] | None
    field: str  # "text" | "images" | "all"
    force: bool
    dry_run: bool
    workers: int
    model: str
    max_cost: float | None


@dataclass
class RefreshContext:
    pipeline_root: Path
    content_root: Path  # shared/content
    cache: Cache
    cost: CostTracker
    wikidata: WikidataClient
    wikipedia: WikipediaClient
    images: ImageSelector
    image_processor: ImageProcessor
    claude: ClaudeSummarizer
    options: RefreshOptions

    @property
    def species_yaml_root(self) -> Path:
        return self.content_root / "species"

    @property
    def images_root(self) -> Path:
        return self.content_root / "images"

    @property
    def overrides_path(self) -> Path:
        return self.content_root / "overrides.yaml"


class _NoopClient:
    async def messages_create(self, **kwargs: Any) -> Any:
        from .claude_summarizer import ClaudeReply

        return ClaudeReply(text="[dry-run]", input_tokens=0, output_tokens=0)


def build_context(
    pipeline_root: Path,
    content_root: Path,
    options: RefreshOptions,
) -> RefreshContext:
    cache = Cache(pipeline_root / ".cache")
    cost = CostTracker(max_usd=options.max_cost)
    return RefreshContext(
        pipeline_root=pipeline_root,
        content_root=content_root,
        cache=cache,
        cost=cost,
        wikidata=WikidataClient(cache=cache),
        wikipedia=WikipediaClient(cache=cache),
        images=ImageSelector(cache=cache),
        image_processor=ImageProcessor(),
        claude=ClaudeSummarizer(
            cache=cache,
            cost=cost,
            client=real_anthropic_client() if not options.dry_run else _NoopClient(),
            prompt_dir=pipeline_root / "prompts",
            prompt_version="v1",
            dry_run=options.dry_run,
        ),
        options=options,
    )


def load_species_list(path: Path) -> list[dict[str, Any]]:
    return yaml.safe_load(path.read_text(encoding="utf-8")) or []


def filter_species(
    all_species: list[dict[str, Any]],
    options: RefreshOptions,
) -> list[dict[str, Any]]:
    if not options.species_filter:
        return all_species
    wanted = set(options.species_filter)
    return [s for s in all_species if s.get("wikidata_id") in wanted]


async def refresh_one(ctx: RefreshContext, listed: dict[str, Any]) -> SpeciesYamlData:
    q_id = listed["wikidata_id"]
    scientific_name = listed["scientific_name"]

    wd = await ctx.wikidata.fetch_structured(q_id, force=ctx.options.force)
    title_by_lang = {
        "sv": listed.get("common_sv") or scientific_name,
        "en": listed.get("common_en") or scientific_name,
    }
    sv_article = await ctx.wikipedia.fetch_extract(
        q_id, title_by_lang=title_by_lang, lang="sv", force=ctx.options.force
    )
    en_article = await ctx.wikipedia.fetch_extract(
        q_id, title_by_lang=title_by_lang, lang="en", force=ctx.options.force
    )

    description = {"sv": "", "en": ""}
    migration = {"sv": "", "en": ""}

    if ctx.options.field in ("text", "all"):
        for lang, article in (("sv", sv_article), ("en", en_article)):
            if article.is_sparse:
                description[lang] = ""
                migration[lang] = ""
                continue
            description[lang] = await ctx.claude.summarize_description(
                q_id=q_id,
                scientific_name=scientific_name,
                common_sv=listed.get("common_sv") or "",
                common_en=listed.get("common_en") or "",
                family=wd.family,
                family_sv=listed.get("family_sv") or wd.family,
                wikipedia_intro=article.extract,
                lang=lang,
                model=ctx.options.model,
            )
            migration[lang] = await ctx.claude.summarize_migration(
                q_id=q_id,
                scientific_name=scientific_name,
                common_sv=listed.get("common_sv") or "",
                common_en=listed.get("common_en") or "",
                family=wd.family,
                family_sv=listed.get("family_sv") or wd.family,
                wikipedia_intro=article.extract,
                lang=lang,
                model=ctx.options.model,
            )

    image_refs: list[ImageRef] = []
    if ctx.options.field in ("images", "all"):
        candidates = await ctx.images.fetch_candidates(
            q_id=q_id, scientific_name=scientific_name, force=ctx.options.force
        )
        ranked = rank_candidates(candidates)[:3]
        for idx, candidate in enumerate(ranked):
            role = "hero" if idx == 0 else "secondary"
            filename = "hero.jpg" if idx == 0 else f"secondary-{idx}.jpg"
            out_path = ctx.images_root / q_id / filename
            if not ctx.options.dry_run:
                raw = await ctx.image_processor.download(candidate.url)
                meta = ctx.image_processor.process(raw, out_path=out_path, role=role)
            else:
                meta = type(
                    "Meta",
                    (),
                    {"width": candidate.width, "height": candidate.height},
                )()
            image_refs.append(
                ImageRef(
                    role=role,
                    path=f"{q_id}/{filename}",
                    width=meta.width,
                    height=meta.height,
                    license=candidate.license,
                    author=candidate.author,
                    source_url=(
                        f"https://commons.wikimedia.org/wiki/File:{candidate.commons_filename}"
                    ),
                    commons_filename=candidate.commons_filename,
                )
            )

    # vp_status from VP11: H = breeding in WP (default allmän), F = migratory,
    # h/(H) = unclear/non-established → ovanlig.
    abundance = "allmän" if listed.get("vp_status") in {"H", "F"} else "ovanlig"

    season = _default_season()
    regions = ["SE", "NO", "FI", "DK", "DE"]

    # Prefer ioc_order from species_list (source: IOC checklist) over Wikidata.
    # Wikidata's P105=Q36602 traversal can return wrong ancestors (e.g. Saurischia).
    ioc_order = listed.get("ioc_order") or wd.ioc_order

    data = SpeciesYamlData(
        wikidata_id=q_id,
        scientific_name=scientific_name,
        family=wd.family,
        family_sv=listed.get("family_sv") or wd.family,
        genus=wd.genus,
        ioc_order=ioc_order,
        common_sv=listed.get("common_sv"),
        common_en=listed.get("common_en") or "",
        abundance=abundance,
        iucn_status=wd.iucn_status,
        regions=regions,
        season=season,
        description=description,
        migration=migration,
        image_refs=image_refs,
        review_status="auto",
        review_notes="",
        generated_at=datetime.now(UTC).isoformat(),
        sources={
            "wikipedia_sv_revision": sv_article.revision,
            "wikipedia_en_revision": en_article.revision,
            "claude_model": (
                "claude-haiku-4-5-20251001" if ctx.options.model == "haiku" else "claude-sonnet-4-6"
            ),
        },
    )

    overrides_raw: dict[str, Any] = {}
    if ctx.overrides_path.exists():
        overrides_raw = yaml.safe_load(ctx.overrides_path.read_text(encoding="utf-8")) or {}
    data = merge_overrides(data, overrides_raw)

    family_dir = wd.family.lower()
    out_path = ctx.species_yaml_root / family_dir / f"{q_id}.yaml"
    if not ctx.options.dry_run:
        write_species_yaml(data, out_path)

    return data


def _default_season() -> dict[str, str]:
    return {
        m: "present"
        for m in (
            "jan",
            "feb",
            "mar",
            "apr",
            "may",
            "jun",
            "jul",
            "aug",
            "sep",
            "oct",
            "nov",
            "dec",
        )
    }


async def run_refresh(ctx: RefreshContext) -> int:
    species_list = load_species_list(ctx.pipeline_root / "species_list.yaml")
    target = filter_species(species_list, ctx.options)

    if not target:
        console.print("[yellow]No species matched filter; nothing to do.[/yellow]")
        return 0

    semaphore = asyncio.Semaphore(ctx.options.workers)
    failed: list[str] = []

    async def bound(listed: dict[str, Any]) -> None:
        async with semaphore:
            try:
                await refresh_one(ctx, listed)
            except Exception as exc:
                qid = listed.get("wikidata_id") or "unknown"
                console.print(f"[red]Failed {qid}: {exc}[/red]")
                failed.append(qid)

    with Progress(
        TextColumn("[progress.description]{task.description}"),
        BarColumn(),
        TaskProgressColumn(),
        TimeRemainingColumn(),
    ) as progress:
        prog_task = progress.add_task("refreshing", total=len(target))
        coros = []
        for listed in target:
            coros.append(bound(listed))
        for done in asyncio.as_completed(coros):
            await done
            progress.advance(prog_task)

    if failed:
        console.print(f"[red]{len(failed)} species failed: {', '.join(failed)}[/red]")
        return 1

    console.print(f"Done. {ctx.cost.call_count} Claude calls, ~${ctx.cost.total_usd:.4f} total.")
    return 0
