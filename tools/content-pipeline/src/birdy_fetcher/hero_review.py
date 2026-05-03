"""Render a static HTML review page for hero candidate selection."""

from __future__ import annotations

from pathlib import Path

from .images import ImageCandidate

_TEMPLATE = """<!DOCTYPE html>
<html lang="sv">
<head>
<meta charset="utf-8">
<title>{q_id} — {common_sv}</title>
<style>
body {{ font-family: system-ui, sans-serif; background: #E8E2D2; color: #2A3525;
        margin: 2rem; }}
h1 {{ font-family: 'Crimson Pro', Georgia, serif; }}
.candidate {{ border: 1px solid #5C6E48; padding: 1rem; margin: 1rem 0; border-radius: 4px;
              background: #D8D0BC; }}
.candidate img {{ max-width: 800px; max-height: 500px; display: block; margin-bottom: 0.5rem; }}
.metadata {{ font-size: 0.9rem; color: #3F4F30; }}
.choose {{ background: #8C5A3C; color: #F0EAD8; padding: 0.5rem 1rem; border: none;
           font-weight: 600; cursor: pointer; }}
</style>
</head>
<body>
<h1>{q_id} — {common_sv} <em>({scientific_name})</em></h1>
<p>To accept a candidate as hero, copy its filename to
<code>shared/content/overrides.yaml</code> under
<code>{q_id}.image_refs[0].commons_filename</code>.</p>
{candidates_html}
</body>
</html>
"""

_CANDIDATE = """
<div class="candidate">
  <img src="{url}" alt="{filename}">
  <div class="metadata">
    <strong>{filename}</strong><br>
    {width}x{height} | {license} | {author}<br>
    Categories: {categories}<br>
    <a href="https://commons.wikimedia.org/wiki/File:{filename}" target="_blank">View on Commons</a>
  </div>
</div>
"""


def render_hero_review(
    *,
    q_id: str,
    scientific_name: str,
    common_sv: str,
    candidates: list[ImageCandidate],
    out_path: Path,
) -> None:
    parts = [
        _CANDIDATE.format(
            url=c.url,
            filename=c.commons_filename,
            width=c.width,
            height=c.height,
            license=c.license,
            author=c.author,
            categories=", ".join(c.categories) or "(none)",
        )
        for c in candidates[:5]
    ]
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(
        _TEMPLATE.format(
            q_id=q_id,
            common_sv=common_sv,
            scientific_name=scientific_name,
            candidates_html="\n".join(parts),
        ),
        encoding="utf-8",
    )
