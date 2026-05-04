"""Tests for images.py — selection algorithm + resize/EXIF strip."""

from __future__ import annotations

from pathlib import Path

import pytest
from PIL import Image

from birdy_fetcher.cache import Cache
from birdy_fetcher.images import (
    ImageCandidate,
    ImageProcessor,
    ImageSelector,
    parse_imageinfo_response,
    rank_candidates,
)


def test_parse_imageinfo_extracts_candidates(fixtures_dir: Path) -> None:
    raw = (fixtures_dir / "commons_imageinfo_q25372.json").read_text()
    candidates = parse_imageinfo_response(raw)
    titles = [c.commons_filename for c in candidates]
    assert "Parus major - Mindelheim - 2012.jpg" in titles


def test_rank_rejects_illustrations() -> None:
    illust = ImageCandidate(
        commons_filename="Parus major illustration.jpg",
        url="x",
        width=4000,
        height=3000,
        license="Public domain",
        author="A",
        categories=["Bird illustrations"],
    )
    photo = ImageCandidate(
        commons_filename="Parus major - photo.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="B",
        categories=["Photographs of Aves", "Birds in nature"],
    )
    ranked = rank_candidates([illust, photo])
    assert ranked[0].commons_filename == "Parus major - photo.jpg"
    assert all("illustration" not in c.commons_filename.lower() for c in ranked)


def test_rank_rejects_specimens() -> None:
    specimen = ImageCandidate(
        commons_filename="Parus major specimen.jpg",
        url="x",
        width=4000,
        height=3000,
        license="CC0",
        author="A",
        categories=["Bird specimens"],
    )
    photo = ImageCandidate(
        commons_filename="Parus major - garden.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="B",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([specimen, photo])
    assert all("specimen" not in c.commons_filename.lower() for c in ranked)


def test_rank_rejects_plural_categories() -> None:
    illust_plural = ImageCandidate(
        commons_filename="Parus major - field.jpg",
        url="x",
        width=4000,
        height=3000,
        license="Public domain",
        author="A",
        categories=["Parus major (illustrations)"],
    )
    specimen_plural = ImageCandidate(
        commons_filename="Parus major - museum.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC0",
        author="A",
        categories=["Parus major (museum specimens)", "Taxidermied birds"],
    )
    photo = ImageCandidate(
        commons_filename="Parus major - garden.jpg",
        url="z",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="B",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([illust_plural, specimen_plural, photo])
    assert len(ranked) == 1
    assert ranked[0].commons_filename == "Parus major - garden.jpg"


def test_rank_rejects_historical_print_filenames() -> None:
    print_file = ImageCandidate(
        commons_filename="Pernis apivorus - 1700-1880 - Print - Iconographia Zoologica.jpg",
        url="x",
        width=4000,
        height=3000,
        license="Public domain",
        author="A",
        categories=[],
    )
    chromolitho = ImageCandidate(
        # Wellcome upload truncated as "Chromolithograp" — regex must still match.
        commons_filename="Aquila adalberti Chromolithograp Wellcome V0022220.jpg",
        url="y",
        width=4000,
        height=3000,
        license="Public domain",
        author="A",
        categories=[],
    )
    hardwicke = ImageCandidate(
        commons_filename="Butastur teesa Hardwicke.jpg",
        url="z",
        width=4000,
        height=3000,
        license="Public domain",
        author="A",
        categories=[],
    )
    photo = ImageCandidate(
        commons_filename="Pernis apivorus - flying.jpg",
        url="w",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="B",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([print_file, chromolitho, hardwicke, photo])
    assert len(ranked) == 1
    assert ranked[0].commons_filename == "Pernis apivorus - flying.jpg"


def test_rank_rejects_non_image_extensions() -> None:
    video = ImageCandidate(
        commons_filename="Galerida cristata, South Hebron.webm",
        url="x",
        width=2276,
        height=1280,
        license="CC0",
        author="A",
        categories=[],
    )
    svg = ImageCandidate(
        commons_filename="Alauda arvensis distribution.svg",
        url="y",
        width=4000,
        height=3000,
        license="CC0",
        author="A",
        categories=[],
    )
    photo = ImageCandidate(
        commons_filename="Alauda arvensis Ehedydd.jpg",
        url="z",
        width=2339,
        height=2665,
        license="CC0",
        author="A",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([video, svg, photo])
    assert ranked == [photo]


def test_rank_rejects_below_min_resolution() -> None:
    too_small = ImageCandidate(
        commons_filename="Parus major small.jpg",
        url="x",
        width=1024,
        height=768,
        license="CC0",
        author="A",
        categories=[],
    )
    big = ImageCandidate(
        commons_filename="Parus major big.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC0",
        author="A",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([too_small, big])
    assert too_small not in ranked


def test_rank_prefers_pd_over_cc_by_sa() -> None:
    pd = ImageCandidate(
        commons_filename="Parus major - photo1.jpg",
        url="x",
        width=4000,
        height=3000,
        license="Public domain",
        author="A",
        categories=["Photographs of Aves"],
    )
    sa = ImageCandidate(
        commons_filename="Parus major - photo2.jpg",
        url="y",
        width=4000,
        height=3000,
        license="CC BY-SA 4.0",
        author="B",
        categories=["Photographs of Aves"],
    )
    ranked = rank_candidates([sa, pd])
    assert ranked[0].license == "Public domain"


def test_processor_resizes_to_hero_dimensions(fixtures_dir: Path, tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    cache.put_bytes(
        "Q25485",
        "images/raw-hero.jpg",
        (fixtures_dir / "sample_image.jpg").read_bytes(),
    )

    processor = ImageProcessor()
    out_path = tmp_path / "hero.jpg"
    metadata = processor.process(
        cache.get_bytes("Q25485", "images/raw-hero.jpg") or b"",
        out_path=out_path,
        role="hero",
    )
    assert out_path.exists()
    img = Image.open(out_path)
    assert max(img.size) <= 2400
    assert metadata.width == img.size[0]
    assert metadata.height == img.size[1]


@pytest.mark.asyncio
async def test_selector_url_quotes_scientific_name_and_caches(
    fixtures_dir: Path, tmp_path: Path
) -> None:
    """ImageSelector quotes the scientific_name into the search URL and
    caches the raw response on first call (no second HTTP call)."""
    captured_urls: list[str] = []
    fixture = (fixtures_dir / "commons_imageinfo_q25372.json").read_text()

    async def fake_get(url: str) -> str:
        captured_urls.append(url)
        return fixture

    cache = Cache(tmp_path)
    selector = ImageSelector(cache=cache, http_get=fake_get)

    first = await selector.fetch_candidates("Q25485", "Parus major minor")
    assert len(captured_urls) == 1
    assert "intitle:%22Parus+major+minor%22" in captured_urls[0]
    assert any(c.commons_filename.startswith("Parus major") for c in first)

    # Second call hits cache, no new HTTP call.
    second = await selector.fetch_candidates("Q25485", "Parus major minor")
    assert len(captured_urls) == 1
    assert [c.commons_filename for c in second] == [c.commons_filename for c in first]

    # force=True bypasses cache.
    await selector.fetch_candidates("Q25485", "Parus major minor", force=True)
    assert len(captured_urls) == 2
