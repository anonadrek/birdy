"""Tests for the helpers in web/checks.py that the new steps use."""

from __future__ import annotations

import pytest

from birdy_fetcher.web.checks import (
    _style,
    banned_hits,
    quote_in_sources,
    sentence_count,
    without_dashes,
)


def test_style_flags_dashes_exclamations_first_person_and_banned_words() -> None:
    issues = _style("sv.x", "sv", "Vi såg en fantastisk fågel \u2014 den sjöng!", ["fantastisk"])
    messages = [i.message for i in issues]
    assert "innehåller tankstreck eller --" in messages
    assert "innehåller utropstecken" in messages
    assert any("första person" in m for m in messages)
    assert any("fantastisk" in m for m in messages)


def test_style_flags_an_empty_text() -> None:
    assert [i.message for i in _style("en.x", "en", "   ", [])] == ["är tom"]


def test_banned_hits_match_whole_words_only() -> None:
    assert banned_hits("En unik fågel.", ["unik"]) == ["unik"]
    assert banned_hits("Unikum.", ["unik"]) == []


def test_sentence_count_ignores_abbreviations() -> None:
    assert sentence_count("Den äter bl.a. frön. Den häckar i hål.") == 2


def test_quote_in_sources_normalizes_quotes_dashes_and_spaces() -> None:
    source = 'Kroppslängden är "28-31 cm" hos vuxna fåglar.'
    assert quote_in_sources("Kroppslängden är \u201c28\u201331 cm\u201d  hos vuxna", [source])
    assert not quote_in_sources("kort", ["kort text"])


# I7 (final review 2026-10-06): text the pipeline copies onto the page, or into a field fas
# 2's dash guard reads, never carries a dash the guard refuses.


@pytest.mark.parametrize(
    ("raw", "clean"),
    [
        ("Liten och kvick \u2014 ofta i flock.", "Liten och kvick, ofta i flock."),
        ("Liten\u2014kvick", "Liten, kvick"),
        ("Small \u2013 quick", "Small, quick"),
        ("The quote says X \u2014 not Y.", "The quote says X, not Y."),
        ("Anna -- xeno-canto", "Anna, xeno-canto"),
        ("\u2014 Anna", "Anna"),
        ("Anna \u2014", "Anna"),
        ("13\u201315 cm", "13-15 cm"),
        ("13 \u2014 15 cm", "13-15 cm"),
        ("ti-ta ti-ta", "ti-ta ti-ta"),
        ("Göteborg\u2013Stockholm", "Göteborg\u2013Stockholm"),
        ("Rapporteras \u2014.", "Rapporteras."),
    ],
)
def test_without_dashes(raw: str, clean: str) -> None:
    assert without_dashes(raw) == clean
