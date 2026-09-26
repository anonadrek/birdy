"""Runs the web step for the approved species (spec 2026-09-25 §7 and §8)."""

from __future__ import annotations

import asyncio
import json
from collections import Counter
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, Protocol

from ..cache import Cache
from ..cost import CostTracker, MaxCostExceeded
from .checks import load_banned
from .groups import GroupTable
from .images import prepare_images
from .output import build_record, is_approved, write_record
from .report import SpeciesOutcome, render_report
from .slugs import slugify
from .source import SpeciesSource, load_approved
from .wiki_full import FullWikiClient, WikiArticle
from .writer import WEB_MODELS, AnthropicStructuredClient, StructuredClient, WebTextWriter


class SlugCollisionError(ValueError):
    pass


class ArticleSource(Protocol):
    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]: ...


@dataclass(frozen=True)
class WebPaths:
    repo_root: Path

    @property
    def pipeline_root(self) -> Path:
        return self.repo_root / "tools" / "content-pipeline"

    @property
    def species_root(self) -> Path:
        return self.repo_root / "shared" / "content" / "species"

    @property
    def asset_images(self) -> Path:
        return self.repo_root / "asset-pack" / "src" / "main" / "assets" / "images"

    @property
    def family_groups(self) -> Path:
        return (self.repo_root / "shared" / "content" / "src" / "jvmMain" / "resources"
                / "family_groups.yaml")

    @property
    def web_groups(self) -> Path:
        return self.repo_root / "website" / "src" / "data" / "species-groups.json"

    @property
    def data_out(self) -> Path:
        return self.repo_root / "website" / "src" / "data" / "species"

    @property
    def images_out(self) -> Path:
        return self.repo_root / "website" / "src" / "assets" / "species"

    @property
    def reports(self) -> Path:
        return self.pipeline_root / "reports"

    @property
    def prompt(self) -> Path:
        return self.pipeline_root / "prompts" / "web-v1.md"

    @property
    def banned(self) -> Path:
        return self.pipeline_root / "prompts" / "web-banned-phrases.txt"


@dataclass(frozen=True)
class WebRunOptions:
    qids: tuple[str, ...]
    model_key: str
    max_cost: float | None
    force: bool
    refresh_sources: bool
    regenerate: bool
    workers: int
    dry_run: bool


def check_slug_collisions(sources: Sequence[SpeciesSource], groups: GroupTable) -> None:
    for lang in ("sv", "en"):
        slugs = [slugify(s.name_sv if lang == "sv" else s.name_en, lang) for s in sources]
        slugs += [g.slug_sv if lang == "sv" else g.slug_en for g in groups.groups]
        dupes = sorted(slug for slug, n in Counter(slugs).items() if n > 1)
        if dupes:
            raise SlugCollisionError(f"Samma adress används två gånger ({lang}): {dupes}")


async def run_web(
    paths: WebPaths,
    options: WebRunOptions,
    *,
    client: StructuredClient | None,
    wiki: ArticleSource | None = None,
    now: datetime | None = None,
) -> list[SpeciesOutcome]:
    now = now or datetime.now(UTC)
    cache = Cache(paths.pipeline_root / ".cache")
    groups = GroupTable(paths.family_groups, paths.web_groups)
    all_approved = load_approved(paths.species_root)
    sources = load_approved(paths.species_root, options.qids) if options.qids else all_approved
    check_slug_collisions(all_approved, groups)
    approved = {s.qid for s in all_approved}
    missing = [q for q in groups.common + [g.photo for g in groups.groups] if q not in approved]
    if missing:
        raise ValueError(f"species-groups.json pekar på arter som inte är granskade: {missing}")

    wiki = wiki or FullWikiClient(cache=cache)
    cost = CostTracker(max_usd=options.max_cost)
    writer: WebTextWriter | None = None
    if not options.dry_run:
        writer = WebTextWriter(
            cache=cache,
            cost=cost,
            client=client or AnthropicStructuredClient(),
            prompt_path=paths.prompt,
            banned=load_banned(paths.banned),
            model_key=options.model_key,
            regenerate=options.regenerate,
        )
    stop = asyncio.Event()
    semaphore = asyncio.Semaphore(options.workers)

    async def one(source: SpeciesSource) -> SpeciesOutcome:
        async with semaphore:
            return await _process(source, paths, options, groups, wiki, writer, stop, now)

    outcomes = list(await asyncio.gather(*(one(s) for s in sources)))
    if not options.dry_run:
        paths.reports.mkdir(parents=True, exist_ok=True)
        report = render_report(outcomes, cost_usd=cost.total_usd,
                               model_id=WEB_MODELS[options.model_key],
                               date=now.date().isoformat())
        (paths.reports / f"web-{now.date().isoformat()}.md").write_text(report, encoding="utf-8")
    return outcomes


async def _process(
    source: SpeciesSource,
    paths: WebPaths,
    options: WebRunOptions,
    groups: GroupTable,
    wiki: ArticleSource,
    writer: WebTextWriter | None,
    stop: asyncio.Event,
    now: datetime,
) -> SpeciesOutcome:
    def outcome(status: str, errors: list[str], dropped: list[str] | None = None,
                attempts: int = 0, cached: bool = False) -> SpeciesOutcome:
        return SpeciesOutcome(source.qid, source.name_sv, status, errors, dropped or [],
                              attempts, cached)

    if not options.force and is_approved(paths.data_out / f"{source.qid}.json"):
        return outcome("skipped", ["redan granskad (review: approved)"])
    try:
        articles = await wiki.articles(source.qid, refresh=options.refresh_sources)
        group = groups.group_for(family=source.family, ioc_order=source.ioc_order)
        model_id = WEB_MODELS[options.model_key]

        if options.dry_run or writer is None:
            sizes = ", ".join(f"{lang} {len(a.text)} tecken" for lang, a in articles.items())
            return outcome("dry-run", [sizes or "ingen artikel"])

        if not articles:
            errors = ["ingen Wikipediaartikel på svenska eller engelska"]
            images = prepare_images(source, asset_images=paths.asset_images,
                                    out_root=paths.images_out)
            record = build_record(source=source, group=group, text=None, articles=articles,
                                  images=images, errors=errors, model_id=model_id,
                                  generated_at=now)
            write_record(record, paths.data_out, force=options.force)
            return outcome("failed", errors)

        if stop.is_set():
            return outcome("skipped", ["kostnadstaket nåddes, körs vid nästa körning"])
        web_group = groups.by_key(group)
        try:
            result = await writer.write(source, articles, web_group.name_sv, web_group.name_en)
        except MaxCostExceeded as exc:
            stop.set()
            return outcome("skipped", [f"kostnadstaket nåddes: {exc}"])

        errors = [f"{i.path}: {i.message}" for i in result.issues]
        dropped = [f"{i.path}: {i.message}" for i in result.dropped_facts]
        images = prepare_images(source, asset_images=paths.asset_images, out_root=paths.images_out)
        record = build_record(source=source, group=group, text=result.output, articles=articles,
                              images=images, errors=errors, model_id=model_id, generated_at=now)
        out_path = paths.data_out / f"{source.qid}.json"
        if result.from_cache:
            record = _keep_existing_timestamp_if_text_unchanged(record, out_path)
        write_record(record, paths.data_out, force=options.force)
        status = "ok" if record["status"] == "ok" else "failed"
        return outcome(status, errors, dropped, result.attempts, result.from_cache)
    except Exception as exc:  # one species' transient error must not abort the whole run
        # Deliberately does not write/overwrite the species' JSON record here: a transient
        # error (Wikipedia hiccup, a model API error, an unreadable image) must not replace a
        # good file from an earlier run. Just report it; the species is retried next run.
        return outcome("failed", [f"{type(exc).__name__}: {exc}"])


def _keep_existing_timestamp_if_text_unchanged(
    record: dict[str, Any], existing_path: Path
) -> dict[str, Any]:
    """A cached answer reruns with identical `text` -- keep the old file's `generated` object
    so a full rerun does not rewrite every species' file just to change a timestamp."""
    if not existing_path.exists():
        return record
    existing: dict[str, Any] = json.loads(existing_path.read_text(encoding="utf-8"))
    if existing.get("text") == record.get("text"):
        record["generated"] = existing["generated"]
    return record
