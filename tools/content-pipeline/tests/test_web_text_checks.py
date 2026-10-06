"""Tests for web/text_checks.py and the helpers in web/text_model.py."""

from __future__ import annotations

from birdy_fetcher.web.text_checks import (
    TextContext,
    check_text,
    minimum_problems,
    numbers,
    settle,
)
from birdy_fetcher.web.text_model import WebTextV2, remove_paths, status_for_site, to_site

from .text_fixtures import BANNED, EN, FACTS, SV, VALID, S, reviewed_record

CTX = TextContext.from_facts(FACTS)


def _with_sv(**changes: object) -> WebTextV2:
    return WebTextV2(sv=SV.model_copy(update=changes), en=EN)


def test_the_valid_text_has_no_issues() -> None:
    assert check_text(VALID, CTX, BANNED) == []


def test_numbers() -> None:
    assert numbers("Cirka 14 cm och 2,5 kg") == {"14", "2.5"}


def test_a_number_that_the_facts_do_not_give_is_removable() -> None:
    text = _with_sv(voice=[S("Sången hörs från 3 meters håll.", "f04")])
    issues = check_text(text, CTX, BANNED)
    assert [(i.path, i.removable) for i in issues] == [("sv.voice[0]", True)]
    assert "talet 3" in issues[0].message


def test_unknown_or_missing_fact_ids() -> None:
    text = _with_sv(voice=[S("Sången är ett ringande ti ta, ti ta.", "f99")])
    assert "f99" in check_text(text, CTX, BANNED)[0].message
    bare = _with_sv(voice=[S("Sången är ett ringande ti ta, ti ta.")])
    assert check_text(bare, CTX, BANNED)[0].message == "anger inga fakta"


def test_style_rules_apply_to_every_sentence() -> None:
    text = _with_sv(voice=[S("Sången är fascinerande!", "f04")])
    messages = [i.message for i in check_text(text, CTX, BANNED)]
    assert any("utropstecken" in m for m in messages)
    assert any("fascinerande" in m for m in messages)


def test_field_rules_are_not_removable() -> None:
    text = _with_sv(field_marks=SV.field_marks[:2], meta_description="För kort.")
    issues = {i.path: i.removable for i in check_text(text, CTX, BANNED)}
    assert issues == {"sv.field_marks": False, "sv.meta_description": False}


def test_a_look_alike_that_is_not_in_the_facts() -> None:
    from birdy_fetcher.web.text_model import LookAlikeText

    text = _with_sv(
        look_alikes=[LookAlikeText(other="Q1", sentences=[S("Mindre och blå.", "f06")])]
    )
    issues = check_text(text, CTX, BANNED)
    assert [(i.path, i.removable) for i in issues] == [("sv.look_alikes[0]", True)]


def test_size_numbers_must_come_from_the_cited_fact() -> None:
    from birdy_fetcher.web.text_model import SizeText

    text = _with_sv(size=SizeText(value="Cirka 16 cm", fact_ids=["f03"]))
    assert [i.path for i in check_text(text, CTX, BANNED)] == ["sv.size"]


def test_settle_removes_what_it_can_and_reports_the_rest() -> None:
    text = _with_sv(lead=[*SV.lead[:1], S("Den väger 99 gram.", "f01")])
    settled, notes, hard = settle(text, CTX, BANNED)
    assert len(settled.sv.lead) == 1
    assert notes and "sv.lead[1]" in notes[0]
    assert hard == []


def test_remove_paths_and_the_minimum() -> None:
    text = remove_paths(VALID, {"sv.voice[0]", "en.look_alikes[0].sentences[0]", "en.size"})
    assert text.sv.voice == []
    assert text.en.look_alikes == []
    assert text.en.size is None
    assert minimum_problems(text) == ["sv.voice saknas"]


def test_status_for_site_respects_an_unconfirmed_flag() -> None:
    record = reviewed_record()
    assert status_for_site(record) == {"value": "resident", "factIds": ["s01"]}
    record["data"]["statusSignal"]["contradicts"] = "Statusen säger stannfågel, men ..."
    assert status_for_site(record) is None
    record["review"]["statusConfirmed"] = True
    assert status_for_site(record) == {"value": "resident", "factIds": ["s01"]}


def test_to_site_uses_the_site_field_names() -> None:
    site = to_site(SV, {"value": "resident", "factIds": ["s01"]})
    assert site["lead"][0] == {
        "text": "Talgoxen är en vanlig fågel i skog, parker och trädgårdar.",
        "factIds": ["f05"],
    }
    assert site["lookAlikes"][0]["other"] == "Q25404"
    assert site["facts"]["size"] == {"value": "Cirka 14 cm", "factIds": ["f03"]}
    assert site["facts"]["swedenStatus"]["value"] == "resident"
    assert set(site) == {
        "lead", "fieldMarks", "voice", "whereWhen", "behaviour", "lookAlikes",
        "metaDescription", "facts",
    }  # fmt: skip
