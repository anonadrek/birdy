"""Pre-flight check before fetcher runs. Verifies env, sources, cache."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from .vp11_source import VP11_SHA256, VP11_URL, sha256_of, vp11_cache_path


@dataclass(frozen=True)
class Check:
    name: str
    ok: bool
    detail: str = ""


@dataclass(frozen=True)
class DoctorReport:
    checks: list[Check]

    @property
    def is_ok(self) -> bool:
        return all(c.ok for c in self.checks)


def run_doctor(*, root: Path) -> DoctorReport:
    checks: list[Check] = []

    api_key = os.environ.get("ANTHROPIC_API_KEY", "")
    checks.append(
        Check(
            name="ANTHROPIC_API_KEY",
            ok=bool(api_key) and api_key.startswith("sk-ant-"),
            detail=("set" if api_key else "missing — copy .env.example to .env"),
        )
    )

    ioc = root / "sources" / "ioc-14.1.xlsx"
    checks.append(
        Check(
            name="sources/ioc-14.1.xlsx",
            ok=ioc.exists(),
            detail=("present" if ioc.exists() else "download from worldbirdnames.org"),
        )
    )

    checks.append(_vp11_check(root / ".cache"))

    species_list = root / "species_list.yaml"
    checks.append(
        Check(
            name="species_list.yaml",
            ok=species_list.exists(),
            detail=("present" if species_list.exists() else "run: uv run birdy-fetcher init"),
        )
    )

    cache = root / ".cache"
    checks.append(
        Check(
            name=".cache/",
            ok=True,
            detail=(
                f"{sum(1 for _ in cache.iterdir())} entries"
                if cache.exists()
                else "empty (will be created on first refresh)"
            ),
        )
    )

    return DoctorReport(checks=checks)


def _vp11_check(cache_root: Path) -> Check:
    """VP11.pdf is only needed by ``init``, which downloads it; a wrong checksum fails."""
    path = vp11_cache_path(cache_root)
    name = ".cache/sources/vp11.pdf"
    if not path.exists():
        return Check(name=name, ok=True, detail=f"not cached; `init` downloads {VP11_URL}")
    if sha256_of(path) != VP11_SHA256:
        return Check(name=name, ok=False, detail="wrong SHA-256; delete it and run `init`")
    return Check(name=name, ok=True, detail="cached, SHA-256 matches")
