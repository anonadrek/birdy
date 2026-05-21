"""Build BirdNET-Lite index→Qid mapping filtered to our 839 European species.

Reads:
  tools/birdnet_labels.txt        (one "Scientific_name_Common name" per line, ~6 000 lines)
  tools/content-pipeline/species_list.yaml (839 species with scientific_name + qid)

Writes:
  shared/ml/src/commonMain/composeResources/files/ml/birdnet_lite_to_qid.json

Format:
  {
    "_meta": {
      "generated_for_model_version": "birdnet_lite_6k_global",
      "coverage_pct": 87.5,
      "total_birdnet_classes": 6362,
      "mapped_to_qid": 734,
      "total_species_in_list": 839
    },
    "mapping": {
      "0": "Q12345",
      "42": "Q67890"
    }
  }
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
LABELS = ROOT / "tools" / "birdnet_labels.txt"
SPECIES_LIST = ROOT / "tools" / "content-pipeline" / "species_list.yaml"
OUT = ROOT / "shared" / "ml" / "src" / "commonMain" / "composeResources" / "files" / "ml" / "birdnet_lite_to_qid.json"


def main() -> int:
    if not LABELS.exists():
        print(f"ERROR: {LABELS} missing — download from birdnet-team/BirdNET-Lite/main/model/labels.txt", file=sys.stderr)
        return 1

    species_raw = yaml.safe_load(SPECIES_LIST.read_text(encoding="utf-8"))
    # species_list.yaml is a flat list of dicts with wikidata_id + scientific_name
    by_sci = {s["scientific_name"]: s["wikidata_id"] for s in species_raw}

    raw_labels = LABELS.read_text(encoding="utf-8").splitlines()
    mapping: dict[str, str] = {}
    for idx, line in enumerate(raw_labels):
        stripped = line.strip()
        if not stripped:
            continue
        if "_" not in stripped:
            continue
        sci, _common = stripped.split("_", 1)
        sci = sci.strip()
        if sci in by_sci:
            mapping[str(idx)] = by_sci[sci]

    coverage = (len(mapping) / len(by_sci)) * 100
    out_payload = {
        "_meta": {
            "generated_for_model_version": "birdnet_lite_6k_global",
            "coverage_pct": round(coverage, 1),
            "total_birdnet_classes": len(raw_labels),
            "mapped_to_qid": len(mapping),
            "total_species_in_list": len(by_sci),
        },
        "mapping": mapping,
    }

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {OUT}: {len(mapping)} mappings, coverage {coverage:.1f}%")
    return 0


if __name__ == "__main__":
    sys.exit(main())
