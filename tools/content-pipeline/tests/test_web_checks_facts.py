"""Tests for fact checks, plausibility and dropping facts (spec §7, checks 4 and 5)."""

from __future__ import annotations

from birdy_fetcher.web.checks import Issue, check_facts, drop_facts, quote_in_sources

from .web_fixtures import ARTICLES, valid_output


def test_valid_output_has_no_fact_issues() -> None:
    assert check_facts(valid_output(), ARTICLES) == []


def test_quote_matching_ignores_case_spaces_and_quote_styles() -> None:
    sources = ["Den  är “stannfågel” i hela Sverige."]
    assert quote_in_sources('den är "stannfågel" i hela sverige', sources)
    assert not quote_in_sources("i hela", sources)  # shorter than 20 characters
    assert not quote_in_sources("den är flyttfågel i hela Sverige", sources)


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


def test_status_mismatch_between_languages_drops_both() -> None:
    out = valid_output()
    assert out.en.facts.sweden_status is not None
    out.en.facts.sweden_status.value = "passage"
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
    assert [i.path for i in hard] == ["sv.where_when"]


def test_drop_facts_sets_only_the_named_facts_to_none() -> None:
    out = valid_output()
    dropped = drop_facts(out, [Issue("sv.facts.size", "x", lang="sv", fact="size")])
    assert dropped.sv.facts.size is None
    assert dropped.sv.facts.sweden_status is not None
    assert dropped.en.facts.size is not None
    assert out.sv.facts.size is not None  # the original is not changed
