"""Tests for web/text_checks.py and the helpers in web/text_model.py."""

from __future__ import annotations

from birdy_fetcher.web.text_checks import (
    TextContext,
    check_text,
    fact_corpus,
    minimum_problems,
    numbers,
    path_texts,
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


def test_numbers_with_space_grouped_thousands() -> None:
    """Swedish thousands separators: regular space, U+00A0 (NBSP), U+202F (NNBSP)."""
    assert numbers("Väger 1 200 g.") == {"1200"}
    assert numbers("Väger 1 200 g.") == {"1200"}  # U+00A0 NBSP  # noqa: RUF001
    assert numbers("Väger 1 200 g.") == {"1200"}  # U+202F NNBSP  # noqa: RUF001
    assert numbers("1200 g") == {"1200"}  # No space, already single number
    assert numbers("2 300 000 g") == {"2300000"}  # Multiple groups


def test_numbers_with_decimal_comma() -> None:
    """Decimal commas should normalize to dots, unchanged by thousands grouping."""
    assert numbers("14,5 cm") == {"14.5"}
    assert numbers("1 200,50 g") == {"1200.50"}


def test_numbers_does_not_merge_groups_separated_by_words() -> None:
    """Groups separated by words ('mellan 3 och 400') should NOT merge."""
    assert numbers("mellan 3 och 400") == {"3", "400"}
    # Rule: a thousands group is exactly 3 digits preceded by 1-3 digits
    # separated by one space-like char
    assert numbers("3 och 400 kg") == {"3", "400"}


def test_numbers_fact_citation_with_space_grouped_thousands() -> None:
    """Rule 5: sentence citing fact with space-grouped thousands should match correctly.

    f03 contains the number 14. When a sentence cites f03 and mentions "14" with spaces
    (e.g., "1 4" if that were split), the merge should allow the match to work.
    Simpler: a sentence citing f03 with the number "14" (space-free in this case) should match.
    """
    # Test: sentence citing f03 which has the number 14
    text = _with_sv(field_marks=[S("Cirka 14 centimeter lång.", "f03")])
    issues = check_text(text, CTX, BANNED)
    # Should NOT have a rule-5 issue about the number not being in the facts
    rule5_issues = [
        i for i in issues if "talet" in i.message and "finns inte i de fakta" in i.message
    ]
    assert not rule5_issues, f"Unexpected rule-5 issues: {rule5_issues}"


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


def test_fact_corpus_skips_the_stale_quote_of_an_edited_fact() -> None:
    """Once Albin edits a fact's value, its old quote no longer reflects it (fix 2026-10-06):
    the number rule must not let a now-wrong number from the stale quote pass as valid."""
    edited = {
        "id": "f03",
        "sv": "16 till 18 cm",
        "edited": True,
        "sources": [{"article": "sv", "quote": "cirka 14 centimeter lång"}],
    }
    assert fact_corpus(edited) == "16 till 18 cm"


def test_settle_removes_what_it_can_and_reports_the_rest() -> None:
    text = _with_sv(lead=[*SV.lead[:1], S("Den väger 99 gram.", "f01")])
    settled, notes, hard = settle(text, CTX, BANNED)
    assert len(settled.sv.lead) == 1
    assert notes and "sv.lead[1]" in notes[0]
    assert hard == []


def test_settle_notes_include_the_removed_sentence_text() -> None:
    """M4 (review fix 2026-10-06): Albin must be able to judge a removal from the note
    alone, without having to find the sentence some other way."""
    text = _with_sv(lead=[*SV.lead[:1], S("Den väger 99 gram.", "f01")])
    _settled, notes, _hard = settle(text, CTX, BANNED)
    assert any("Den väger 99 gram." in n for n in notes)


def test_path_texts_maps_sentences_and_size() -> None:
    texts = path_texts(VALID)
    assert texts["sv.lead[1]"] == "Den har svart huvud med vita kinder."
    assert texts["sv.size"] == "Cirka 14 cm"


def test_meta_description_with_digits_is_a_hard_issue() -> None:
    """M8 (review fix 2026-10-06): the prompt already tells the writer not to put numbers
    in meta_description; this backs that with a code check."""
    text = _with_sv(meta_description="Talgoxen: 14 cm lång fågel med svart huvud och gul buk.")
    issues = check_text(text, CTX, BANNED)
    assert any(
        i.path == "sv.meta_description" and not i.removable and "siffror" in i.message
        for i in issues
    )


def test_remove_paths_and_the_minimum() -> None:
    text = remove_paths(VALID, {"sv.voice[0]", "en.look_alikes[0].sentences[0]", "en.size"})
    assert text.sv.voice == []
    assert text.en.look_alikes == []
    assert text.en.size is None
    assert minimum_problems(text) == ["sv.voice saknas"]


def test_status_for_site_respects_an_unconfirmed_flag() -> None:
    record = reviewed_record()
    assert status_for_site(record) == {"value": "resident", "factIds": ["s01"]}
    # I3 (final review 2026-10-06): the contradiction comes from the report data, so a
    # winter gap for a resident hides the status even with a stored signal of None (what
    # `web sources --force` leaves behind).
    record["data"].update({"months": [100] * 11 + [1], "totalReports": 900})
    assert record["data"]["statusSignal"]["contradicts"] is None
    assert status_for_site(record) is None
    record["review"]["statusConfirmed"] = True
    assert status_for_site(record) == {"value": "resident", "factIds": ["s01"]}


def test_status_for_site_ignores_a_stale_stored_signal() -> None:
    record = reviewed_record()
    record["data"].update({"months": [100] * 12, "totalReports": 900})
    record["data"]["statusSignal"]["contradicts"] = "Statusen säger stannfågel, men ..."
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


# Minor 6 (final review 2026-10-06): the prompts say "13 till 15 cm" and no questions;
# code now holds the writer to it.


def test_a_range_written_with_a_hyphen_is_removable() -> None:
    text = _with_sv(voice=[S("Sången har 13-15 toner.", "f04")])
    issues = [i for i in check_text(text, CTX, BANNED) if i.path == "sv.voice[0]"]
    assert issues and all(i.removable for i in issues)
    assert any("till" in i.message for i in issues)


def test_a_hyphen_inside_a_word_is_fine() -> None:
    text = _with_sv(voice=[S("Sången är ett ringande ti-ta ti-ta.", "f04")])
    assert check_text(text, CTX, BANNED) == []


def test_a_size_written_with_a_hyphen_is_removable() -> None:
    from birdy_fetcher.web.text_model import SizeText

    text = _with_sv(size=SizeText(value="13-15 cm", fact_ids=["f03"]))
    issues = [i for i in check_text(text, CTX, BANNED) if i.path == "sv.size"]
    assert issues and issues[0].removable
    assert "till" in issues[0].message


def test_a_question_in_a_sentence_is_removable() -> None:
    text = _with_sv(voice=[S("Hur låter talgoxen?", "f04")])
    issues = [i for i in check_text(text, CTX, BANNED) if i.path == "sv.voice[0]"]
    assert issues and all(i.removable for i in issues)
    assert any("fråga" in i.message for i in issues)


def test_a_question_in_the_species_meta_description_is_a_hard_issue() -> None:
    meta = "Talgoxe? Så känner du igen den på svart huvud och vita kinder, hör den " + (
        "ringande sången och ser när den finns i Sverige under året."
    )
    text = _with_sv(meta_description=meta)
    issues = [i for i in check_text(text, CTX, BANNED) if i.path == "sv.meta_description"]
    assert issues and not any(i.removable for i in issues)


SHARE_FACT = {
    "id": "d02",
    "topic": "data",
    "source": "artportalen",
    "kind": "countyShare",
    "sv": "Andelen av alla fågelrapporter är högst i Halland, Gotland och Kalmar.",
}


def test_a_county_share_must_stay_a_share() -> None:
    """R3 (2026-10-07): from "Vanligast i rapporterna från Norrbotten ..." the writer wrote
    "flest rapporter kommer från Norrbotten", and the text check let it through. A sentence
    that cites the county share must say andel/share, or it is removed."""
    ctx = TextContext.from_facts([*FACTS, SHARE_FACT])
    wrong = _with_sv(where_when=[S("Flest rapporter kommer från Halland och Gotland.", "d02")])
    issues = check_text(wrong, ctx, BANNED)
    assert [(i.path, i.removable) for i in issues] == [("sv.where_when[0]", True)]
    assert "andel" in issues[0].message
    right = _with_sv(
        where_when=[S("Andelen av alla fågelrapporter är högst i Halland och Gotland.", "d02")]
    )
    assert check_text(right, ctx, BANNED) == []


def test_an_english_county_share_must_say_share() -> None:
    ctx = TextContext.from_facts([*FACTS, SHARE_FACT])
    wrong = WebTextV2(
        sv=SV,
        en=EN.model_copy(
            update={"where_when": [S("Most reports come from Halland and Gotland.", "d02")]}
        ),
    )
    assert [i.path for i in check_text(wrong, ctx, BANNED)] == ["en.where_when[0]"]
    right = WebTextV2(
        sv=SV,
        en=EN.model_copy(
            update={
                "where_when": [S("Its share of all bird reports is highest in Halland.", "d02")]
            }
        ),
    )
    assert check_text(right, ctx, BANNED) == []


def test_a_species_with_no_status_and_no_reports_shows_does_not_occur() -> None:
    """R3 (2026-10-07): no article mentions Sweden for Koboltmes, so it has no status fact;
    the data say it has no reports in ten years, so the page's "I Sverige" row says
    "Förekommer inte", citing that data fact."""
    record = reviewed_record()
    record["facts"] = [f for f in record["facts"] if f["topic"] != "status"]
    record["facts"].append(
        {
            "id": "d09",
            "topic": "data",
            "source": "artportalen",
            "kind": "absent",
            "sv": "Förekommer inte i Sverige: inga rapporter i Artportalen 2016 till 2025.",
        }
    )
    assert status_for_site(record) == {"value": "absent", "factIds": ["d09"]}
    record["facts"].pop()
    assert status_for_site(record) is None


def test_a_county_share_may_be_said_as_a_proportion() -> None:
    """Fix wave 2026-10-07: "proportion" counts as well as "share", and the Swedish word
    may sit inside a compound ("rapportandelen")."""
    ctx = TextContext.from_facts([*FACTS, SHARE_FACT])
    en = WebTextV2(
        sv=SV.model_copy(
            update={"where_when": [S("Rapportandelen är högst i Halland och Gotland.", "d02")]}
        ),
        en=EN.model_copy(
            update={
                "where_when": [S("The proportion of bird reports is highest in Halland.", "d02")]
            }
        ),
    )
    assert check_text(en, ctx, BANNED) == []
