"""Where the web step reads and writes, relative to the repo root."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class WebPaths:
    repo_root: Path

    @property
    def pipeline_root(self) -> Path:
        return self.repo_root / "tools" / "content-pipeline"

    @property
    def species_root(self) -> Path:
        return self.repo_root / "shared" / "content" / "species"

    @property
    def asset_images(self) -> Path:
        return self.repo_root / "asset-pack" / "src" / "main" / "assets" / "images"

    @property
    def family_groups(self) -> Path:
        return (
            self.repo_root
            / "shared"
            / "content"
            / "src"
            / "jvmMain"
            / "resources"
            / "family_groups.yaml"
        )

    @property
    def web_groups(self) -> Path:
        return self.repo_root / "website" / "src" / "data" / "species-groups.json"

    @property
    def data_out(self) -> Path:
        return self.repo_root / "website" / "src" / "data" / "species"

    @property
    def comparisons_out(self) -> Path:
        return self.repo_root / "website" / "src" / "data" / "comparisons"

    @property
    def images_out(self) -> Path:
        """Photos and recordings, website/src/assets/species/<QID>/. Not public/, so a file is
        only served once a built page uses it (spec §9.1)."""
        return self.repo_root / "website" / "src" / "assets" / "species"

    @property
    def ml_dir(self) -> Path:
        return (
            self.repo_root
            / "shared"
            / "ml"
            / "src"
            / "commonMain"
            / "composeResources"
            / "files"
            / "ml"
        )

    @property
    def reports(self) -> Path:
        return self.pipeline_root / "reports"

    @property
    def review(self) -> Path:
        return self.pipeline_root / "review"

    @property
    def flexref(self) -> Path:
        """V4 (Revision 2026-10-05): the desktop BirdNET reference client that `audio_check.py`
        shells out to, so the pipeline itself never imports TensorFlow."""
        return self.repo_root / "tools" / "ml-eval" / "flexref"

    def prompt_file(self, name: str) -> Path:
        return self.pipeline_root / "prompts" / f"{name}.md"

    @property
    def banned(self) -> Path:
        return self.pipeline_root / "prompts" / "web-banned-phrases.txt"
