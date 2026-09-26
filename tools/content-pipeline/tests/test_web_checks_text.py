"""Tests for the text checks in web/checks.py (spec §7, checks 1 to 3)."""

from __future__ import annotations

import json
from collections.abc import Sequence
from pathlib import Path

from birdy_fetcher.web.checks import banned_hits, check_text, load_banned, sentence_count

from .web_fixtures import valid_output

PIPELINE = Path(__file__).resolve().parents[1]
BANNED = load_banned(PIPELINE / "prompts" / "web-banned-phrases.txt")


def _paths(issues: Sequence[object]) -> set[str]:
    return {getattr(i, "path") for i in issues}  # noqa: B009


def test_valid_output_has_no_text_issues() -> None:
    assert check_text(valid_output(), BANNED) == []


def test_dashes_exclamation_and_first_person() -> None:
    out = valid_output()
    out.sv.lead = "Den största av mesarna — och vanlig! Jag ser den ofta."
    out.en.voice = "We hear it -- often."
    issues = check_text(out, BANNED)
    messages = " ".join(i.message for i in issues)
    assert "sv.lead" in _paths(issues)
    assert "en.voice" in _paths(issues)
    assert "tankstreck" in messages
    assert "utropstecken" in messages
    assert "första person" in messages
    assert "('Jag')" in messages
    assert "('We')" in messages


def test_vara_is_not_first_person() -> None:
    out = valid_output()
    out.sv.lead = "En av våra vanligaste mesar. Den finns i hela Sverige."
    assert check_text(out, BANNED) == []


def test_banned_phrases_are_whole_words() -> None:
    assert banned_hits("En fascinerande fågel", BANNED) == ["fascinerande"]
    assert banned_hits("Kommunikationen fungerar", BANNED) == []
    assert banned_hits("Unika teckningar", BANNED) == ["unika"]
    assert banned_hits("It is truly common", BANNED) == ["truly"]


def test_length_limits() -> None:
    out = valid_output()
    out.sv.lead = "Ett. Två. Tre."
    out.sv.field_marks = ["Svart huvud", "Gul buk"]
    out.en.field_marks = ["x " * 17, "b", "c"]
    out.en.voice = "word " * 61
    out.sv.where_when = "ord " * 71
    out.en.meta_description = "Too short."
    issues = check_text(out, BANNED)
    paths = _paths(issues)
    assert {"sv.lead", "sv.field_marks", "en.field_marks[0]", "en.voice"} <= paths
    assert {"sv.where_when", "en.meta_description"} <= paths


def test_empty_field() -> None:
    out = valid_output()
    out.en.lead = "  "
    assert "en.lead" in _paths(check_text(out, BANNED))


def test_size_value_with_dash_is_a_fact_issue() -> None:
    out = valid_output()
    assert out.sv.facts.size is not None
    out.sv.facts.size.value = "13–15 cm"  # noqa: RUF001
    issues = check_text(out, BANNED)
    assert [(i.lang, i.fact) for i in issues] == [("sv", "size")]


def test_sentence_count() -> None:
    assert sentence_count("Ett. Två? Tre!") == 3
    assert sentence_count("Ingen punkt") == 1
    assert sentence_count("Den äter bl.a. insekter. Den finns i hela Sverige.") == 2
    assert sentence_count("It is c. 14 cm long, e.g. like a sparrow. It is common.") == 2


def test_group_intros_have_no_banned_phrases() -> None:
    groups = json.loads(
        (PIPELINE.parents[1] / "website/src/data/species-groups.json").read_text(encoding="utf-8")
    )
    for group in groups["groups"]:
        for lang in ("sv", "en"):
            assert banned_hits(group["intro"][lang], BANNED) == [], group["key"]
