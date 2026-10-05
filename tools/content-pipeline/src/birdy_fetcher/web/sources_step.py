"""Step 1 (spec 2026-09-25 §9.1): sources for each approved species, written into its record.
No model calls; everything here is free and cached under .cache/."""

from __future__ import annotations

import asyncio
from collections import Counter
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any, Protocol

from ..cache import Cache
from .audio import AudioCandidate, CommonsAudioClient, audio_record, choose, convert_to_mp3
from .datamod import MIN_REPORTS, Counts, build_data
from .gbif import GbifClient
from .groups import GroupTable
from .identify import ModelCoverage, load_coverage
from .images import ImageOut, prepare_images
from .paths import WebPaths
from .record import image_dict, is_reviewed, load_record, merge_sources, record_path, save_record
from .report import StepOutcome, render_step_report, write_step_report
from .slugs import slugify
from .source import SpeciesSource, load_approved
from .wiki_full import FullWikiClient, WikiArticle


class SlugCollisionError(ValueError):
    pass


def check_slug_collisions(sources: Sequence[SpeciesSource], groups: GroupTable) -> None:
    for lang in ("sv", "en"):
        slugs = [slugify(s.name_sv if lang == "sv" else s.name_en, lang) for s in sources]
        slugs += [g.slug_sv if lang == "sv" else g.slug_en for g in groups.groups]
        dupes = sorted(slug for slug, n in Counter(slugs).items() if n > 1)
        if dupes:
            raise SlugCollisionError(f"Samma adress används två gånger ({lang}): {dupes}")


class ArticleSource(Protocol):
    async def articles(self, qid: str, *, refresh: bool = False) -> dict[str, WikiArticle]: ...


class GbifSource(Protocol):
    async def taxon_key(
        self, qid: str, scientific: str, *, refresh: bool = False
    ) -> int | None: ...

    async def counts(self, qid: str, taxon_key: int, *, refresh: bool = False) -> Counts: ...

    async def all_birds(self, *, refresh: bool = False) -> Counts: ...

    async def swedish_red_list(
        self, qid: str, scientific: str, taxon_key: int, *, refresh: bool = False
    ) -> str | None: ...


class AudioSource(Protocol):
    async def candidates(
        self, qid: str, scientific: str, *, refresh: bool = False
    ) -> list[AudioCandidate]: ...

    async def download(
        self, qid: str, candidate: AudioCandidate, *, refresh: bool = False
    ) -> bytes: ...


@dataclass
class SourceClients:
    wiki: ArticleSource
    gbif: GbifSource
    audio: AudioSource


@dataclass(frozen=True)
class SourcesOptions:
    qids: tuple[str, ...] = ()
    refresh: bool = False
    force: bool = False
    workers: int = 4
    dry_run: bool = False


def default_clients(cache: Cache) -> SourceClients:
    return SourceClients(
        wiki=FullWikiClient(cache=cache),
        gbif=GbifClient(cache=cache),
        audio=CommonsAudioClient(cache=cache),
    )


@dataclass(frozen=True)
class _Context:
    paths: WebPaths
    groups: GroupTable
    coverage: ModelCoverage
    clients: SourceClients
    all_birds: Counts
    options: SourcesOptions
    now: datetime


async def _data_and_red_list(
    source: SpeciesSource, ctx: _Context, notes: list[str]
) -> tuple[dict[str, Any] | None, str | None]:
    refresh = ctx.options.refresh
    taxon = await ctx.clients.gbif.taxon_key(source.qid, source.scientific_name, refresh=refresh)
    if taxon is None:
        notes.append("ingen exakt träff i GBIF: inga diagram och ingen svensk rödlista")
        return None, None
    counts = await ctx.clients.gbif.counts(source.qid, taxon, refresh=refresh)
    data = build_data(
        taxon_key=taxon, species=counts, all_birds=ctx.all_birds, fetched_at=ctx.now.isoformat()
    )
    if counts.total < MIN_REPORTS:
        notes.append(f"bara {counts.total} rapporter: inga diagram")
    red = await ctx.clients.gbif.swedish_red_list(
        source.qid, source.scientific_name, taxon, refresh=refresh
    )
    if red is None:
        notes.append("okänd kategori i Svenska rödlistan")
    return data, red


async def _audio(
    source: SpeciesSource, ctx: _Context, notes: list[str], *, skip: bool
) -> dict[str, Any] | None:
    voice = ctx.paths.images_out / source.qid / "voice.mp3"
    audio: dict[str, Any] | None = None
    if not skip:
        refresh = ctx.options.refresh
        candidates = await ctx.clients.audio.candidates(
            source.qid, source.scientific_name, refresh=refresh
        )
        chosen, rejected = choose(candidates, source.scientific_name)
        notes.extend(f"inspelning avvisad: {r}" for r in rejected[:5])
        if chosen is None:
            notes.append("ingen fri inspelning")
        elif not ctx.options.dry_run:
            raw = await ctx.clients.audio.download(source.qid, chosen, refresh=refresh)
            await asyncio.to_thread(convert_to_mp3, raw, voice)
            audio = audio_record(chosen, source.qid)
    if audio is None and not ctx.options.dry_run:
        voice.unlink(missing_ok=True)
    return audio


async def _collect(
    source: SpeciesSource, ctx: _Context, *, skip_audio: bool
) -> tuple[dict[str, Any], list[str]]:
    notes: list[str] = []
    articles = await ctx.clients.wiki.articles(source.qid, refresh=ctx.options.refresh)
    if not articles:
        notes.append("ingen Wikipediaartikel")
    data, red = await _data_and_red_list(source, ctx, notes)
    audio = await _audio(source, ctx, notes, skip=skip_audio)
    images: list[ImageOut] = []
    if not ctx.options.dry_run:
        images = await asyncio.to_thread(
            prepare_images,
            source,
            asset_images=ctx.paths.asset_images,
            out_root=ctx.paths.images_out,
        )
    has_marginalia = source.marginalia_sv or source.marginalia_en
    collected: dict[str, Any] = {
        "slug": {"sv": slugify(source.name_sv, "sv"), "en": slugify(source.name_en, "en")},
        "names": {"sv": source.name_sv, "en": source.name_en, "scientific": source.scientific_name},
        "family": {"latin": source.family, "sv": source.family_sv},
        "group": ctx.groups.group_for(family=source.family, ioc_order=source.ioc_order),
        "iucn": source.iucn,
        "swedishRedList": red,
        "identifiable": ctx.coverage.for_qid(source.qid),
        "marginalia": (
            {"sv": source.marginalia_sv, "en": source.marginalia_en} if has_marginalia else None
        ),
        "images": [image_dict(i) for i in images],
        "audio": audio,
        "wikipedia": {
            lang: {"title": a.title, "revision": a.revision} for lang, a in articles.items()
        },
        "data": data,
    }
    return collected, notes


async def run_sources(
    paths: WebPaths,
    options: SourcesOptions,
    *,
    clients: SourceClients | None = None,
    now: datetime | None = None,
) -> list[StepOutcome]:
    now = now or datetime.now(UTC)
    groups = GroupTable(paths.family_groups, paths.web_groups)
    all_approved = load_approved(paths.species_root)
    check_slug_collisions(all_approved, groups)
    sources = load_approved(paths.species_root, options.qids) if options.qids else all_approved
    clients = clients or default_clients(Cache(paths.pipeline_root / ".cache"))
    ctx = _Context(
        paths=paths,
        groups=groups,
        coverage=load_coverage(paths.ml_dir),
        clients=clients,
        all_birds=await clients.gbif.all_birds(refresh=options.refresh),
        options=options,
        now=now,
    )
    semaphore = asyncio.Semaphore(options.workers)

    async def one(source: SpeciesSource) -> StepOutcome:
        async with semaphore:
            path = record_path(paths.data_out, source.qid)
            try:
                existing = load_record(path)
                if existing and is_reviewed(existing) and options.refresh and not options.force:
                    return StepOutcome(
                        source.qid,
                        source.name_sv,
                        "skipped",
                        ["faktabladet är kontrollerat: hämtas inte om utan --force"],
                    )
                skip_audio = bool(existing and existing.get("review", {}).get("audioStruck"))
                collected, notes = await _collect(source, ctx, skip_audio=skip_audio)
                if options.dry_run:
                    return StepOutcome(source.qid, source.name_sv, "dry-run", notes=notes)
                save_record(path, merge_sources(existing, source.qid, collected))
                return StepOutcome(source.qid, source.name_sv, "ok", notes=notes)
            except Exception as exc:  # one species' error must not stop the run
                return StepOutcome(
                    source.qid, source.name_sv, "failed", [f"{type(exc).__name__}: {exc}"]
                )

    outcomes = list(await asyncio.gather(*(one(s) for s in sources)))
    if not options.dry_run:
        report = render_step_report(title="Källor", date=now.date().isoformat(), outcomes=outcomes)
        write_step_report(paths.reports, "sources", now, report)
    return outcomes
