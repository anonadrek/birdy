"""hero_review.py renders top-N candidates as a static HTML page."""

from __future__ import annotations

from pathlib import Path

from birdy_fetcher.hero_review import render_hero_review
from birdy_fetcher.images import ImageCandidate


def test_render_hero_review_writes_html(tmp_path: Path) -> None:
    candidates = [
        ImageCandidate(
            commons_filename="Parus major - photo1.jpg",
            url="https://example/photo1.jpg",
            width=4000,
            height=3000,
            license="CC BY-SA 4.0",
            author="Pierre Dalous",
            categories=["Photographs of Aves", "Birds in nature"],
        ),
        ImageCandidate(
            commons_filename="Parus major - photo2.jpg",
            url="https://example/photo2.jpg",
            width=3500,
            height=2333,
            license="CC0",
            author="Anonymous",
            categories=["Photographs of Aves"],
        ),
    ]
    out_path = tmp_path / "Q25485.html"
    render_hero_review(
        q_id="Q25485",
        scientific_name="Parus major",
        common_sv="Talgoxe",
        candidates=candidates,
        out_path=out_path,
    )
    assert out_path.exists()
    html = out_path.read_text(encoding="utf-8")
    assert "Q25485" in html
    assert "Parus major" in html
    assert "Pierre Dalous" in html
    assert "https://example/photo1.jpg" in html
