"""Tests for fact checks, plausibility and dropping facts (spec §7, checks 4 and 5)."""

from __future__ import annotations

from birdy_fetcher.web.checks import Issue, check_facts, drop_facts, quote_in_sources
from birdy_fetcher.web.wiki_full import WikiArticle

from .web_fixtures import ARTICLES, valid_output


def test_valid_output_has_no_fact_issues() -> None:
    assert check_facts(valid_output(), ARTICLES) == []


def test_quote_matching_ignores_case_spaces_and_quote_styles() -> None:
    sources = ["Den  är “stannfågel” i hela Sverige."]
    assert quote_in_sources('den är "stannfågel" i hela sverige', sources)
    assert not quote_in_sources("i hela", sources)  # shorter than 20 characters
    assert not quote_in_sources("den är flyttfågel i hela Sverige", sources)


def test_quote_matching_normalizes_dash_variants() -> None:
    # Wikipedia writes size ranges with an en dash; the prompt forbids dashes in the model's
    # own prose, so the model may quote the plain-hyphen form of the same text.
    assert quote_in_sources(
        "Den är 28-31 cm lång, med ett",
        ["Den är 28–31 cm lång, med ett vingspann"],  # noqa: RUF001
    )


def test_quote_not_in_source_is_a_fact_issue() -> None:
    out = valid_output()
    assert out.sv.facts.size is not None
    out.sv.facts.size.quote = "cirka 16 centimeter lång och väger"
    issues = check_facts(out, ARTICLES)
    assert [(i.lang, i.fact) for i in issues] == [("sv", "size")]


def test_size_digits_must_appear_in_quote() -> None:
    out = valid_output()
    assert out.en.facts.size is not None
    out.en.facts.size.value = "About 15 cm"
    issues = check_facts(out, ARTICLES)
    assert [(i.lang, i.fact) for i in issues] == [("en", "size")]


def test_size_value_without_digits_is_a_fact_issue() -> None:
    out = valid_output()
    assert out.sv.facts.size is not None
    out.sv.facts.size.value = "Ungefär som en talgoxe"
    issues = check_facts(out, ARTICLES)
    assert [(i.lang, i.fact) for i in issues] == [("sv", "size")]


def test_size_quote_about_wingspan_not_length_is_a_fact_issue() -> None:
    out = valid_output()
    assert out.en.facts.size is not None
    out.en.facts.size.quote = "has a wingspan of about 14 centimetres across"
    articles = {
        **ARTICLES,
        "en": WikiArticle(
            lang="en",
            title="Great tit",
            revision="222",
            text="The great tit has a wingspan of about 14 centimetres across the wings. "
            "It is a resident bird across Sweden.",
        ),
    }
    issues = check_facts(out, articles)
    assert [(i.lang, i.fact) for i in issues] == [("en", "size")]


def test_status_mismatch_between_languages_drops_both() -> None:
    out = valid_output()
    assert out.en.facts.sweden_status is not None
    out.en.facts.sweden_status.value = "passage"
    issues = check_facts(out, ARTICLES)
    assert sorted((i.lang, i.fact) for i in issues) == [
        ("en", "sweden_status"),
        ("sv", "sweden_status"),
    ]


def test_missing_status_on_one_language_drops_the_other() -> None:
    out = valid_output()
    out.en.facts.sweden_status = None
    issues = check_facts(out, ARTICLES)
    assert [(i.lang, i.fact) for i in issues] == [("sv", "sweden_status")]


def test_status_fact_issue_on_one_language_drops_the_other() -> None:
    out = valid_output()
    assert out.sv.facts.sweden_status is not None
    out.sv.facts.sweden_status.quote = "this text does not appear in the article"
    issues = check_facts(out, ARTICLES)
    assert sorted((i.lang, i.fact) for i in issues) == [
        ("en", "sweden_status"),
        ("sv", "sweden_status"),
    ]


def test_absent_status_contradicting_text_is_a_hard_issue() -> None:
    out = valid_output()
    assert out.sv.facts.sweden_status is not None and out.en.facts.sweden_status is not None
    out.sv.facts.sweden_status.value = "absent"
    out.en.facts.sweden_status.value = "absent"
    out.sv.where_when = "Stannfågel i hela landet."
    issues = check_facts(out, ARTICLES)
    hard = [i for i in issues if i.fact is None]
    assert [i.path for i in hard] == ["sv.where_when", "en.where_when"]


def test_absent_status_with_negated_or_unrelated_text_has_no_issue() -> None:
    out = valid_output()
    assert out.sv.facts.sweden_status is not None and out.en.facts.sweden_status is not None
    out.sv.facts.sweden_status.value = "absent"
    out.en.facts.sweden_status.value = "absent"
    out.sv.where_when = "Arten finns inte i Sverige. Den är stannfågel på Kanarieöarna."
    out.en.where_when = "The azure tit is not found in Sweden. It lives in Russia and Central Asia."
    issues = check_facts(out, ARTICLES)
    assert [i for i in issues if i.fact is None] == []


def test_absent_status_with_never_seen_text_has_no_issue() -> None:
    out = valid_output()
    assert out.sv.facts.sweden_status is not None and out.en.facts.sweden_status is not None
    out.sv.facts.sweden_status.value = "absent"
    out.en.facts.sweden_status.value = "absent"
    out.sv.where_when = "Arten finns inte i Sverige."
    out.en.where_when = "It has never been seen in Sweden."
    issues = check_facts(out, ARTICLES)
    assert [i for i in issues if i.fact is None] == []


def test_absent_status_with_real_contradiction_is_flagged() -> None:
    out = valid_output()
    assert out.sv.facts.sweden_status is not None and out.en.facts.sweden_status is not None
    out.sv.facts.sweden_status.value = "absent"
    out.en.facts.sweden_status.value = "absent"
    out.sv.where_when = "Den häckar i hela Sverige."
    out.en.where_when = "It lives in the mountains of Central Asia."
    issues = check_facts(out, ARTICLES)
    hard = [i for i in issues if i.fact is None]
    assert [i.path for i in hard] == ["sv.where_when"]


def test_drop_facts_sets_only_the_named_facts_to_none() -> None:
    out = valid_output()
    dropped = drop_facts(out, [Issue("sv.facts.size", "x", lang="sv", fact="size")])
    assert dropped.sv.facts.size is None
    assert dropped.sv.facts.sweden_status is not None
    assert dropped.en.facts.size is not None
    assert out.sv.facts.size is not None  # the original is not changed
