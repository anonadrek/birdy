"""A tiny fake repo for the web step tests: species files, photos, groups, model maps and
the real prompts that exist so far."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

from birdy_fetcher.web.paths import WebPaths

from .test_web_source import _write

PIPELINE = Path(__file__).resolve().parents[1]
PROMPTS = ("web-v1", "facts-v1", "web-v2", "check-v1", "compare-v1")


def make_repo(
    tmp_path: Path,
    species: list[tuple[str, str, str]],
    *,
    photo: frozenset[str] = frozenset(),
    sound: frozenset[str] = frozenset(),
) -> WebPaths:
    paths = WebPaths(repo_root=tmp_path)
    for qid, sv, en in species:
        _write(paths.species_root, qid, sv, en, "approved")
        img = paths.asset_images / qid / "hero.webp"
        img.parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", (2000, 1000), (90, 110, 70)).save(img, "WEBP")
    paths.family_groups.parent.mkdir(parents=True, exist_ok=True)
    paths.family_groups.write_text(
        "order: [songbirds, other]\ngroups:\n"
        "  songbirds: {keyed_by: order, ioc_order: Passeriformes}\n"
        "  other: {families: [Cuculidae]}\n",
        encoding="utf-8",
    )
    base = {"name": {"sv": "X", "en": "X"}, "photo": species[0][0], "intro": {"sv": "a", "en": "a"}}
    paths.web_groups.parent.mkdir(parents=True, exist_ok=True)
    paths.web_groups.write_text(
        json.dumps(
            {
                "groups": [
                    {**base, "key": "songbirds", "slug": {"sv": "tattingar", "en": "songbirds"}},
                    {**base, "key": "other", "slug": {"sv": "ovriga-faglar", "en": "other-birds"}},
                ],
                "common": [species[0][0]],
            }
        ),
        encoding="utf-8",
    )
    paths.ml_dir.mkdir(parents=True, exist_ok=True)
    (paths.ml_dir / "aiy_to_qid.json").write_text(
        json.dumps({"mappings": {str(i): q for i, q in enumerate(sorted(photo))}}),
        encoding="utf-8",
    )
    (paths.ml_dir / "birdnet_lite_to_qid.json").write_text(
        json.dumps({"mapping": {str(i): q for i, q in enumerate(sorted(sound))}}),
        encoding="utf-8",
    )
    prompts = paths.pipeline_root / "prompts"
    prompts.mkdir(parents=True, exist_ok=True)
    for name in PROMPTS:
        src = PIPELINE / "prompts" / f"{name}.md"
        if src.exists():
            (prompts / f"{name}.md").write_text(src.read_text(encoding="utf-8"), encoding="utf-8")
    paths.banned.write_text("fascinerande\n", encoding="utf-8")
    return paths
