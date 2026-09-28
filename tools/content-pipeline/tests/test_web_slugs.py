"""Tests for web/slugs.py (spec 2026-09-25 §4)."""

from __future__ import annotations

import pytest

from birdy_fetcher.web.slugs import slugify


@pytest.mark.parametrize(
    ("name", "lang", "expected"),
    [
        ("Talgoxe", "sv", "talgoxe"),
        ("Större hackspett", "sv", "storre-hackspett"),
        ("Änder & gäss", "sv", "ander-och-gass"),
        ("Rödstrupig piplärka", "sv", "rodstrupig-piplarka"),
        ("Eurasian Blue Tit", "en", "eurasian-blue-tit"),
        ("Ducks & geese", "en", "ducks-and-geese"),
        ("Eurasian Three-toed Woodpecker", "en", "eurasian-three-toed-woodpecker"),
        ("Montagu's Harrier", "en", "montagus-harrier"),
        ("Montagu’s Harrier", "en", "montagus-harrier"),  # noqa: RUF001
        ("  Gök  ", "sv", "gok"),
    ],
)
def test_slugify(name: str, lang: str, expected: str) -> None:
    assert slugify(name, lang) == expected


def test_slugify_rejects_empty_result() -> None:
    with pytest.raises(ValueError):
        slugify("  ", "sv")
