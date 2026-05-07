"""build_corpus.py — curate a 25-species eval corpus from hero images.

Filters species that:
  - are present in aiy_to_qid.json mappings (model can predict them)
  - have abundance: allmän
  - include SE in regions
  - have shared/content/images/<qid>/hero.jpg

Selection: round-robin across families, alphabetical by QID within each family,
until 25 species are chosen. Deterministic — no randomness.

Outputs:
  tools/ml-eval/corpus/<family>/<qid>.jpg  (copied hero images)
  tools/ml-eval/corpus/manifest.yaml
"""
from __future__ import annotations

import json
import shutil
from collections import defaultdict
from pathlib import Path

import yaml

# ---------------------------------------------------------------------------
# Paths — relative to repo root (script is run from tools/ml-eval/)
# ---------------------------------------------------------------------------
SCRIPT_DIR = Path(__file__).parent
EVAL_DIR = SCRIPT_DIR.parent  # tools/ml-eval/
REPO_ROOT = EVAL_DIR.parent.parent  # repo root

MAPPING_PATH = (
    REPO_ROOT
    / "shared/ml/src/commonMain/composeResources/files/ml/aiy_to_qid.json"
)
SPECIES_DIR = REPO_ROOT / "shared/content/species"
IMAGES_DIR = REPO_ROOT / "shared/content/images"
CORPUS_DIR = EVAL_DIR / "corpus"
MANIFEST_PATH = CORPUS_DIR / "manifest.yaml"

TARGET_COUNT = 25


def load_model_qids() -> set[str]:
    data = json.loads(MAPPING_PATH.read_text(encoding="utf-8"))
    if "_meta" in data:
        mappings: dict[str, str] = data["mappings"]
    else:
        mappings = data
    return set(mappings.values())


def collect_candidates(model_qids: set[str]) -> dict[str, list[str]]:
    """Return {family: [qid, ...]} for species passing all filters, sorted by QID."""
    candidates: dict[str, list[str]] = defaultdict(list)
    for family_dir in sorted(SPECIES_DIR.iterdir()):
        if not family_dir.is_dir():
            continue
        family = family_dir.name
        for yaml_file in sorted(family_dir.glob("Q*.yaml")):
            qid = yaml_file.stem
            data = yaml.safe_load(yaml_file.read_text(encoding="utf-8"))
            abundance = data.get("abundance", "")
            regions: list[str] = data.get("regions") or []
            hero_path = IMAGES_DIR / qid / "hero.jpg"
            if (
                abundance == "allmän"
                and "SE" in regions
                and qid in model_qids
                and hero_path.exists()
            ):
                candidates[family].append(qid)
    return dict(candidates)


def select_round_robin(
    candidates: dict[str, list[str]], target: int
) -> list[tuple[str, str]]:
    """Round-robin across families (sorted), pick alphabetically by QID.

    Returns list of (family, qid) tuples, length == min(target, total_available).
    """
    # Build per-family iterators (already sorted alphabetically)
    families = sorted(candidates.keys())
    iters = {fam: iter(candidates[fam]) for fam in families}
    selected: list[tuple[str, str]] = []
    while len(selected) < target:
        progress = False
        for fam in families:
            if len(selected) >= target:
                break
            try:
                qid = next(iters[fam])
                selected.append((fam, qid))
                progress = True
            except StopIteration:
                pass
        if not progress:
            break  # exhausted all candidates
    return selected


def build_corpus(selected: list[tuple[str, str]]) -> None:
    """Copy hero images and write manifest.yaml."""
    CORPUS_DIR.mkdir(parents=True, exist_ok=True)

    manifest_items = []
    for family, qid in selected:
        dest_dir = CORPUS_DIR / family
        dest_dir.mkdir(parents=True, exist_ok=True)
        src = IMAGES_DIR / qid / "hero.jpg"
        dest = dest_dir / f"{qid}.jpg"
        shutil.copy2(src, dest)
        # POSIX-style path relative to manifest (which is in corpus/)
        rel_path = f"corpus/{family}/{qid}.jpg"
        manifest_items.append({"path": rel_path, "qid": qid, "family": family})

    manifest_data = {"items": manifest_items}
    MANIFEST_PATH.write_text(
        yaml.dump(manifest_data, allow_unicode=True, default_flow_style=False,
                  sort_keys=False),
        encoding="utf-8",
    )


def main() -> None:
    print("Loading model QIDs …")
    model_qids = load_model_qids()
    print(f"  Model knows {len(model_qids)} Q-IDs")

    print("Collecting candidates (allmän + SE + in model + has hero) …")
    candidates = collect_candidates(model_qids)
    total_candidates = sum(len(v) for v in candidates.values())
    print(f"  {total_candidates} candidates across {len(candidates)} families:")
    for fam, qids in sorted(candidates.items()):
        print(f"    {fam}: {len(qids)} ({', '.join(qids)})")

    print(f"\nSelecting {TARGET_COUNT} species (round-robin across families) …")
    selected = select_round_robin(candidates, TARGET_COUNT)
    print(f"  Selected {len(selected)} species:")

    from collections import Counter
    fam_counts: Counter[str] = Counter(fam for fam, _ in selected)
    for fam, qid in selected:
        print(f"    {fam}/{qid}")

    print(f"\nFamily distribution: {dict(sorted(fam_counts.items()))}")

    print("\nCopying images and writing manifest …")
    build_corpus(selected)
    print(f"  Manifest written to {MANIFEST_PATH}")
    print(f"  {len(selected)} JPEG(s) copied to {CORPUS_DIR}/")
    print("\nDone.")


if __name__ == "__main__":
    main()
