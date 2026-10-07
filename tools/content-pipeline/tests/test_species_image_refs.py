"""Every photo the app ships: allowed licence, clean credit, file in place.

Runs over the committed species YAML and image folders (release 1.3.0 photo
audit). The Kotlin validator (`:shared:content:validateSpeciesData`) checks
the licence list, HTML in credits and orphan files on every build; this test
adds the credit-quality rules that live in Python (`credits.py`) and checks
that the Android asset pack mirrors `shared/content/images`.
"""

from __future__ import annotations

import filecmp
import re
from pathlib import Path
from typing import Any

import pytest
import yaml

from birdy_fetcher.credits import (
    canonical_license,
    clean_author,
    is_license_allowed,
    is_usable_author,
    requires_attribution,
)

REPO = Path(__file__).resolve().parents[3]
SPECIES = REPO / "shared/content/species"
IMAGES = REPO / "shared/content/images"
ASSET_PACK = REPO / "asset-pack/src/main/assets/images"

needs_repo = pytest.mark.skipif(
    not SPECIES.is_dir() or not IMAGES.is_dir(), reason="needs the Birdy repo checkout"
)


def _species() -> list[dict[str, Any]]:
    return [yaml.safe_load(p.read_text(encoding="utf-8")) for p in sorted(SPECIES.rglob("*.yaml"))]


def _refs() -> list[tuple[str, dict[str, Any]]]:
    return [(s["id"], ref) for s in _species() for ref in (s.get("image_refs") or [])]


def _is_desktop_junk(path: Path) -> bool:
    """Finder/Explorer files (.DS_Store, ._x, Thumbs.db): never photos, never orphans."""
    return path.name.startswith(".") or path.name.lower() in {"thumbs.db", "desktop.ini"}


def _files(root: Path) -> set[str]:
    return {
        p.relative_to(root).as_posix()
        for p in root.rglob("*")
        if p.is_file() and not _is_desktop_junk(p)
    }


def test_desktop_junk_is_not_counted_as_image_files(tmp_path: Path) -> None:
    for name in ["Q1/hero.webp", "Q1/.DS_Store", ".DS_Store", "Q1/._hero.webp", "Q1/Thumbs.db"]:
        (tmp_path / name).parent.mkdir(parents=True, exist_ok=True)
        (tmp_path / name).write_bytes(b"x")
    assert _files(tmp_path) == {"Q1/hero.webp"}


@needs_repo
def test_every_licence_is_on_the_allow_list() -> None:
    bad = [
        f"{qid} {ref['path']}: {ref['license']!r}"
        for qid, ref in _refs()
        if not is_license_allowed(ref["license"])
        or canonical_license(ref["license"]) != ref["license"]
    ]
    assert not bad, "licences outside the allow-list:\n" + "\n".join(bad)


@needs_repo
def test_credits_are_plain_clean_text() -> None:
    bad = [
        f"{qid} {ref['path']}: {ref['author']!r}"
        for qid, ref in _refs()
        if clean_author(ref["author"]) != ref["author"]
    ]
    assert not bad, "credits that still need clean_author():\n" + "\n".join(bad)


@needs_repo
def test_attribution_licences_name_a_photographer() -> None:
    bad = [
        f"{qid} {ref['path']} ({ref['license']}): {ref['author']!r}"
        for qid, ref in _refs()
        if requires_attribution(ref["license"]) and not is_usable_author(ref["author"])
    ]
    assert not bad, "CC BY / BY-SA photos without a usable photographer:\n" + "\n".join(bad)


@needs_repo
def test_every_referenced_file_exists() -> None:
    missing = [
        f"{qid} {ref['path']}" for qid, ref in _refs() if not (IMAGES / ref["path"]).is_file()
    ]
    assert not missing, "image_refs pointing at missing files:\n" + "\n".join(missing)


@needs_repo
def test_no_orphan_image_files() -> None:
    referenced = {ref["path"] for _, ref in _refs()}
    orphans = sorted(_files(IMAGES) - referenced)
    assert not orphans, "files nothing references (they would still ship):\n" + "\n".join(orphans)


@needs_repo
def test_asset_pack_mirrors_the_source_images() -> None:
    if not ASSET_PACK.is_dir():
        pytest.skip("no asset pack in this checkout")
    source, packed = _files(IMAGES), _files(ASSET_PACK)
    assert source == packed, (
        f"only in shared/content/images: {sorted(source - packed)[:20]}\n"
        f"only in the asset pack: {sorted(packed - source)[:20]}\n"
        "run ./gradlew :shared:content:buildSpeciesDb"
    )
    differ = [
        p for p in sorted(source) if not filecmp.cmp(IMAGES / p, ASSET_PACK / p, shallow=False)
    ]
    assert not differ, "asset pack is stale for:\n" + "\n".join(differ)


_SECONDARY = re.compile(r"^secondary-(\d+)\.webp$")


@needs_repo
def test_paths_follow_the_role_layout() -> None:
    bad = []
    for s in _species():
        refs = s.get("image_refs") or []
        heroes = [r for r in refs if r["role"] == "hero"]
        if len(heroes) > 1 or (refs and not heroes):
            bad.append(f"{s['id']}: {len(heroes)} heroes")
        for r in heroes:
            if r["path"] != f"{s['id']}/hero.webp":
                bad.append(f"{s['id']}: hero at {r['path']}")
        numbers = []
        for r in refs:
            if r["role"] == "secondary":
                folder, _, name = r["path"].partition("/")
                m = _SECONDARY.match(name)
                if folder != s["id"] or not m:
                    bad.append(f"{s['id']}: secondary at {r['path']}")
                else:
                    numbers.append(int(m.group(1)))
        if sorted(numbers) != list(range(1, len(numbers) + 1)):
            bad.append(f"{s['id']}: secondary numbering {sorted(numbers)}")
    assert not bad, "\n".join(bad)


@needs_repo
def test_no_commons_file_is_used_twice() -> None:
    seen: dict[str, str] = {}
    dupes = []
    for qid, ref in _refs():
        name = ref["commons_filename"]
        if name in seen:
            dupes.append(f"{name}: {seen[name]} and {qid} {ref['path']}")
        seen.setdefault(name, f"{qid} {ref['path']}")
    assert not dupes, "the same Commons file shown as two photos:\n" + "\n".join(dupes)
