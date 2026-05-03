"""Pre-flight check before fetcher runs. Verifies env, sources, cache."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


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

    vp11 = root / "sources" / "vp11.pdf"
    checks.append(
        Check(
            name="sources/vp11.pdf",
            ok=vp11.exists(),
            detail=(
                "present"
                if vp11.exists()
                else "download from cdn.birdlife.se (TK Västpalearktis-lista v11)"
            ),
        )
    )

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
