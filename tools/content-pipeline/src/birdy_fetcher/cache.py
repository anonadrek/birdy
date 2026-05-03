"""Filesystem cache for fetcher steps. One directory per species, one file per step."""

from __future__ import annotations

import os
import time
from pathlib import Path


class Cache:
    """A simple atomic-write filesystem cache keyed by (q_id, filename)."""

    def __init__(self, root: Path) -> None:
        self.root = root

    def _path(self, q_id: str, filename: str) -> Path:
        return self.root / q_id / filename

    def has(self, q_id: str, filename: str) -> bool:
        return self._path(q_id, filename).exists()

    def get(self, q_id: str, filename: str) -> str | None:
        path = self._path(q_id, filename)
        if not path.exists():
            return None
        return path.read_text(encoding="utf-8")

    def put(self, q_id: str, filename: str, content: str) -> None:
        path = self._path(q_id, filename)
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(path.suffix + ".tmp")
        tmp.write_text(content, encoding="utf-8")
        os.replace(tmp, path)

    def get_bytes(self, q_id: str, filename: str) -> bytes | None:
        path = self._path(q_id, filename)
        if not path.exists():
            return None
        return path.read_bytes()

    def put_bytes(self, q_id: str, filename: str, content: bytes) -> None:
        path = self._path(q_id, filename)
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(path.suffix + ".tmp")
        tmp.write_bytes(content)
        os.replace(tmp, path)

    def is_stale(self, q_id: str, filename: str, *, max_age_days: int) -> bool:
        path = self._path(q_id, filename)
        if not path.exists():
            return True
        age_seconds = time.time() - path.stat().st_mtime
        return age_seconds > max_age_days * 86400
