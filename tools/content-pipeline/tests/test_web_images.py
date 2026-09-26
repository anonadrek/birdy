"""Tests for web/images.py: downscaled WebP and photo metadata (spec §8)."""

from __future__ import annotations

from pathlib import Path

import pytest
from PIL import Image

from birdy_fetcher.web.images import MissingImageError, prepare_images
from birdy_fetcher.web.source import SourceImage, SpeciesSource


def _source(images: tuple[SourceImage, ...]) -> SpeciesSource:
    return SpeciesSource("Q1", "Parus major", "Talgoxe", "Great Tit", "Paridae", "Mesar",
                         "Passeriformes", "LC", None, None, images)


def _webp(path: Path, size: tuple[int, int]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", size, (120, 140, 90)).save(path, "WEBP")


HERO = SourceImage("hero", "Q1/hero.webp", "CC BY-SA 4.0", "<a>Ann</a>",
                   "https://commons.wikimedia.org/wiki/File:A b.jpg")
EXTRA = SourceImage("secondary", "Q1/secondary-1.webp", "CC0", None, "https://c/File:C.jpg")


def test_hero_and_extra_are_downscaled(tmp_path: Path) -> None:
    assets, out = tmp_path / "assets", tmp_path / "out"
    _webp(assets / "Q1/hero.webp", (2400, 1600))
    _webp(assets / "Q1/secondary-1.webp", (1800, 1200))
    images = prepare_images(_source((HERO, EXTRA)), asset_images=assets, out_root=out)
    assert [(i.role, i.file, i.width, i.height) for i in images] == [
        ("hero", "Q1/hero.webp", 1600, 1067),
        ("extra", "Q1/extra.webp", 1200, 800),
    ]
    assert images[0].author == "Ann"
    assert images[0].license_url == "https://creativecommons.org/licenses/by-sa/4.0/"
    assert images[0].source_url.endswith("File:A_b.jpg")
    assert images[1].license_url is None
    with Image.open(out / "Q1/hero.webp") as im:
        assert im.format == "WEBP" and im.size == (1600, 1067)


def test_small_images_are_not_upscaled(tmp_path: Path) -> None:
    assets = tmp_path / "assets"
    _webp(assets / "Q1/hero.webp", (900, 600))
    images = prepare_images(_source((HERO,)), asset_images=assets, out_root=tmp_path / "out")
    assert (images[0].width, images[0].height) == (900, 600)


def test_missing_extra_is_skipped_but_missing_hero_is_an_error(tmp_path: Path) -> None:
    assets = tmp_path / "assets"
    _webp(assets / "Q1/hero.webp", (2000, 1000))
    images = prepare_images(_source((HERO, EXTRA)), asset_images=assets, out_root=tmp_path / "o")
    assert [i.role for i in images] == ["hero"]
    with pytest.raises(MissingImageError):
        prepare_images(_source((EXTRA,)), asset_images=assets, out_root=tmp_path / "o")
