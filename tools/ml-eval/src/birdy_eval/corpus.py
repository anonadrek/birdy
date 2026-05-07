from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


@dataclass(frozen=True)
class CorpusItem:
    path: Path
    qid: str
    family: str


def load_corpus(manifest_path: Path) -> list[CorpusItem]:
    """Load corpus items from a manifest YAML file.

    The manifest must have the structure::

        items:
          - path: corpus/paridae/Q18009.jpg
            qid: Q18009
            family: paridae

    Args:
        manifest_path: Path to the manifest.yaml file.

    Returns:
        List of CorpusItem instances, one per entry in `items`.
    """
    raw: dict[str, Any] = yaml.safe_load(manifest_path.read_text(encoding="utf-8"))
    items_raw: list[dict[str, Any]] = raw.get("items") or []
    return [
        CorpusItem(
            path=Path(item["path"]),
            qid=str(item["qid"]),
            family=str(item["family"]),
        )
        for item in items_raw
    ]
