"""Orchestrator end-to-end with all collaborators mocked."""

from __future__ import annotations

from pathlib import Path

import pytest
import yaml

from birdy_fetcher.cache import Cache
from birdy_fetcher.claude_summarizer import ClaudeSummarizer, FakeClaudeClient
from birdy_fetcher.cost import CostTracker
from birdy_fetcher.images import ImageProcessor, ImageSelector
from birdy_fetcher.orchestrator import RefreshContext, RefreshOptions, refresh_one, run_refresh
from birdy_fetcher.wikidata import WikidataClient
from birdy_fetcher.wikipedia import WikipediaClient


@pytest.mark.asyncio
async def test_refresh_one_writes_yaml(fixtures_dir: Path, tmp_path: Path) -> None:
    pipeline_root = tmp_path / "pipeline"
    content_root = tmp_path / "content"
    (pipeline_root / "prompts").mkdir(parents=True)
    (pipeline_root / "prompts" / "description-v1.md").write_text(
        "System: ...\nUser: {scientific_name}\n", encoding="utf-8"
    )
    (pipeline_root / "prompts" / "migration-v1.md").write_text(
        "System: ...\nUser: {scientific_name}\n", encoding="utf-8"
    )

    cache = Cache(pipeline_root / ".cache")
    cost = CostTracker(max_usd=None)

    wd_fixture = (fixtures_dir / "wikidata_q25485.json").read_text()
    wp_fixture = (fixtures_dir / "wikipedia_sv_parus_major.json").read_text()
    img_fixture = (fixtures_dir / "commons_imageinfo_q25372.json").read_text()

    async def fake_sparql(query: str) -> str:
        return wd_fixture

    async def fake_wp(url: str) -> str:
        return wp_fixture

    async def fake_commons(url: str) -> str:
        return img_fixture

    async def fake_get_bytes(url: str) -> bytes:
        return (fixtures_dir / "sample_image.jpg").read_bytes()

    options = RefreshOptions(
        species_filter=["Q25485"],
        field="all",
        force=False,
        dry_run=False,
        workers=1,
        model="haiku",
        max_cost=None,
    )
    fake_claude = FakeClaudeClient(
        default=type(
            "M",
            (),
            {
                "text": "Talgoxen är ...",
                "input_tokens": 5000,
                "output_tokens": 250,
            },
        )()
    )
    ctx = RefreshContext(
        pipeline_root=pipeline_root,
        content_root=content_root,
        cache=cache,
        cost=cost,
        wikidata=WikidataClient(cache=cache, run_sparql=fake_sparql),
        wikipedia=WikipediaClient(cache=cache, http_get=fake_wp),
        images=ImageSelector(cache=cache, http_get=fake_commons),
        image_processor=ImageProcessor(http_get_bytes=fake_get_bytes),
        claude=ClaudeSummarizer(
            cache=cache,
            cost=cost,
            client=fake_claude,
            prompt_dir=pipeline_root / "prompts",
            prompt_version="v1",
        ),
        options=options,
    )

    listed = {
        "wikidata_id": "Q25485",
        "scientific_name": "Parus major",
        "common_sv": "Talgoxe",
        "common_en": "Great Tit",
        "family": "Paridae",
        "family_sv": "Mesar",
        "vp_status": "H",
    }
    data = await refresh_one(ctx, listed)
    assert data.wikidata_id == "Q25485"
    out_yaml = content_root / "species" / "paridae" / "Q25485.yaml"
    assert out_yaml.exists()
    parsed = yaml.safe_load(out_yaml.read_text(encoding="utf-8"))
    assert parsed["names"]["sv"] == "Talgoxe"
    assert parsed["abundance"] == "allmän"
    assert len(parsed["image_refs"]) >= 1


@pytest.mark.asyncio
async def test_run_refresh_returns_nonzero_when_species_fail(
    fixtures_dir: Path, tmp_path: Path
) -> None:
    """If refresh_one raises for a species, run_refresh should return 1."""
    pipeline_root = tmp_path / "pipeline"
    content_root = tmp_path / "content"
    (pipeline_root / "prompts").mkdir(parents=True)
    (pipeline_root / "prompts" / "description-v1.md").write_text(
        "System: ...\nUser: {scientific_name}\n", encoding="utf-8"
    )
    (pipeline_root / "prompts" / "migration-v1.md").write_text(
        "System: ...\nUser: {scientific_name}\n", encoding="utf-8"
    )
    (pipeline_root / "species_list.yaml").write_text(
        "- wikidata_id: Q99999\n  scientific_name: Failus testus\n",
        encoding="utf-8",
    )

    cache = Cache(pipeline_root / ".cache")
    cost = CostTracker(max_usd=None)

    async def failing_sparql(query: str) -> str:
        raise RuntimeError("simulated wikidata outage")

    async def _empty_str(url: str) -> str:
        return ""

    async def _empty_bytes(url: str) -> bytes:
        return b""

    options = RefreshOptions(
        species_filter=["Q99999"],
        field="all",
        force=False,
        dry_run=False,
        workers=1,
        model="haiku",
        max_cost=None,
    )
    fake_claude = FakeClaudeClient(
        default=type(
            "M",
            (),
            {
                "text": "...",
                "input_tokens": 0,
                "output_tokens": 0,
            },
        )()
    )
    ctx = RefreshContext(
        pipeline_root=pipeline_root,
        content_root=content_root,
        cache=cache,
        cost=cost,
        wikidata=WikidataClient(cache=cache, run_sparql=failing_sparql),
        wikipedia=WikipediaClient(cache=cache, http_get=_empty_str),
        images=ImageSelector(cache=cache, http_get=_empty_str),
        image_processor=ImageProcessor(http_get_bytes=_empty_bytes),
        claude=ClaudeSummarizer(
            cache=cache,
            cost=cost,
            client=fake_claude,
            prompt_dir=pipeline_root / "prompts",
            prompt_version="v1",
        ),
        options=options,
    )
    exit_code = await run_refresh(ctx)
    assert exit_code == 1
