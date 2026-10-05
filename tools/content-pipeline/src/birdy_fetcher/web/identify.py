"""What the app can identify per species, read from the app's own model maps (spec §9.1).
The app box on the page only promises what this says (photo, song, both or neither)."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class ModelCoverage:
    photo: frozenset[str]
    sound: frozenset[str]

    def for_qid(self, qid: str) -> dict[str, bool]:
        return {"photo": qid in self.photo, "sound": qid in self.sound}


def _qids(path: Path, key: str) -> frozenset[str]:
    mapping = json.loads(path.read_text(encoding="utf-8"))[key]
    return frozenset(value for value in mapping.values() if isinstance(value, str))


def load_coverage(ml_dir: Path) -> ModelCoverage:
    return ModelCoverage(
        photo=_qids(ml_dir / "aiy_to_qid.json", "mappings"),
        sound=_qids(ml_dir / "birdnet_lite_to_qid.json", "mapping"),
    )
