"""Hero plus one extra photo per species, downscaled to WebP for the website (spec §8)."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from PIL import Image

from .licenses import clean_author, commons_url, license_url
from .source import SourceImage, SpeciesSource

MAX_WIDTH = {"hero": 1600, "extra": 1200}
QUALITY = 78


class MissingImageError(FileNotFoundError):
    pass


@dataclass(frozen=True)
class ImageOut:
    role: str
    file: str
    width: int
    height: int
    author: str | None
    license: str
    license_url: str | None
    source_url: str


def _pick(source: SpeciesSource) -> list[tuple[str, SourceImage]]:
    hero = next((i for i in source.images if i.role == "hero"), None)
    if hero is None:
        raise MissingImageError(f"{source.qid} saknar huvudfoto i artfilen")
    extra = next((i for i in source.images if i.role == "secondary"), None)
    return [("hero", hero)] + ([("extra", extra)] if extra is not None else [])


def prepare_images(source: SpeciesSource, *, asset_images: Path, out_root: Path) -> list[ImageOut]:
    result: list[ImageOut] = []
    for role, img in _pick(source):
        src = asset_images / img.path
        if not src.exists():
            if role == "hero":
                raise MissingImageError(f"{source.qid}: huvudfotot finns inte: {src}")
            continue
        rel = f"{source.qid}/{role}.webp"
        dst = out_root / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        with Image.open(src) as loaded:
            im = loaded.convert("RGB")
            limit = MAX_WIDTH[role]
            if im.width > limit:
                im = im.resize(
                    (limit, round(im.height * limit / im.width)), Image.Resampling.LANCZOS
                )
            im.save(dst, "WEBP", quality=QUALITY, method=6)
            width, height = im.size
        result.append(
            ImageOut(
                role=role,
                file=rel,
                width=width,
                height=height,
                author=clean_author(img.author),
                license=img.license,
                license_url=license_url(img.license),
                source_url=commons_url(img.source_url),
            )
        )
    return result
