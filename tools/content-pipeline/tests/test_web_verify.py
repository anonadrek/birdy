"""Tests for web/verify.py: V1 (the fact checker), V2 (numbers) and V3 (red list)."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from birdy_fetcher.claude_summarizer import _split_prompt
from birdy_fetcher.cost import CostTracker
from birdy_fetcher.web.verify import (
    FactChecker,
    FactVerdict,
    FactVerifyOutput,
    Measurement,
    Verdict,
    extract_measurements,
    missing_required_topics,
    number_flag,
    redlist_occurrence_flag,
    render_facts_for_check,
    status_flags,
    strike_unsupported,
)
from birdy_fetcher.web.wiki_full import WikiArticle

from .web_fakes import FakeJsonClient, reply

PIPELINE = Path(__file__).resolve().parents[1]
ABOUT = "Talgoxe / Great Tit (Parus major)"

ARTICLE = WikiArticle(
    "sv",
    "Talgoxe",
    "1",
    "Talgoxen är en vanlig fågel i Sverige.\n\n"
    "Den har svart huvud med vita kinder och gul buk.\n\n"
    "Sången är ett ringande ti-ta ti-ta.",
)
FACTS: list[dict[str, Any]] = [
    {
        "id": "f01",
        "topic": "appearance",
        "sv": "Svart huvud med vita kinder.",
        "sources": [{"article": "sv", "quote": "svart huvud med vita kinder"}],
    },
    {
        "id": "f02",
        "topic": "voice",
        "sv": "Sången hörs på långt håll.",
        "sources": [{"article": "sv", "quote": "ett ringande ti-ta ti-ta"}],
    },
    {"id": "d01", "topic": "data", "source": "artportalen", "sv": "Rapporteras året runt."},
]


def _checker(client: FakeJsonClient, prompt_path: Path) -> FactChecker:
    return FactChecker(client=client, cost=CostTracker(max_usd=None), prompt_path=prompt_path)


def test_render_facts_for_check_shows_the_paragraph_and_skips_data_facts() -> None:
    text = render_facts_for_check(FACTS, {"sv": ARTICLE})
    assert '<fact id="f01" topic="appearance">' in text
    assert "svart huvud med vita kinder och gul buk" in text
    assert "d01" not in text


def test_render_facts_for_check_puts_the_topic_on_every_fact() -> None:
    """C1 (final review 2026-10-06): the checker must see which facts compare this species
    with another one, or it strikes every look-alike fact for describing the other species."""
    lookalike = {
        "id": "f03",
        "topic": "lookalike",
        "sv": "Blåmesen är mindre och har blå hätta.",
        "sources": [{"article": "sv", "quote": "ringande ti-ta ti-ta"}],
        "other": {"scientific": "Cyanistes caeruleus"},
    }
    text = render_facts_for_check([*FACTS, lookalike], {"sv": ARTICLE})
    assert '<fact id="f02" topic="voice">' in text
    assert '<fact id="f03" topic="lookalike" other="Cyanistes caeruleus">' in text


def test_render_facts_for_check_finds_the_paragraph_across_typographic_dashes_and_quotes() -> None:
    """Minor 4 (T14b): the quote passed `quote_in_sources`, which normalises dashes, quotes
    and whitespace; finding its paragraph must normalise the same way, or the checker only
    sees the bare quote."""
    article = WikiArticle(
        "sv",
        "Talgoxe",
        "1",
        "Inledning.\n\nDen är 14\u201316 cm lång och kallas \u201dmesen\u201d.  Den har gul buk.",
    )
    fact = {
        "id": "f01",
        "topic": "size",
        "sv": "Den är 14 till 16 cm lång.",
        "sources": [{"article": "sv", "quote": 'den är 14-16 cm lång och kallas "mesen"'}],
    }
    text = render_facts_for_check([fact], {"sv": article})
    assert "Den har gul buk." in text


def test_render_facts_for_check_labels_the_status_fact() -> None:
    """A bare "Stannfågel" claim says nothing about Sweden (C1)."""
    status = {
        "id": "s01",
        "topic": "status",
        "value": "resident",
        "sv": "Stannfågel",
        "sources": [{"article": "sv", "quote": "vanlig fågel i Sverige"}],
    }
    text = render_facts_for_check([status], {"sv": ARTICLE})
    assert '<fact id="s01" topic="status">' in text
    assert "<claim>Status i Sverige: Stannfågel</claim>" in text


STATUS_SV = WikiArticle(
    "sv",
    "Talgoxe",
    "1",
    "Talgoxen finns i hela Sverige och är en av de vanligaste mesarna.\n\n"
    "Par stannar vanligen nära eller inom sitt revir hela året runt, även i norr. "
    "Ungfåglar sprider sig på hösten.",
)
STATUS_DE = WikiArticle(
    "de", "Kohlmeise", "2", "In Europa harren viele Kohlmeisen in den Brutgebieten aus."
)
TWO_QUOTE_STATUS: dict[str, Any] = {
    "id": "s01",
    "topic": "status",
    "value": "resident",
    "sv": "Stannfågel",
    "sources": [
        {"article": "sv", "quote": "Talgoxen finns i hela Sverige"},
        {"article": "sv", "quote": "Par stannar vanligen nära eller inom sitt revir"},
        {"article": "sv", "quote": "Ungfåglar sprider sig på hösten"},
        {"article": "de", "quote": "In Europa harren viele Kohlmeisen"},
    ],
}


def test_render_facts_for_check_shows_every_quote_with_its_language() -> None:
    """R3 (2026-10-07): V1 saw only a fact's first quote, so a status backed by its second
    quote ("Par stannar ... hela året runt") was struck for all three Swedish species."""
    text = render_facts_for_check([TWO_QUOTE_STATUS], {"sv": STATUS_SV, "de": STATUS_DE})
    for source in TWO_QUOTE_STATUS["sources"]:
        assert f'<quote article="{source["article"]}">{source["quote"]}</quote>' in text
    assert "Par stannar vanligen nära eller inom sitt revir hela året runt, även i norr." in text
    assert "In Europa harren viele Kohlmeisen in den Brutgebieten aus." in text


def test_render_facts_for_check_shows_a_shared_paragraph_once() -> None:
    """Two quotes from one paragraph: the paragraph is sent once, not twice."""
    text = render_facts_for_check([TWO_QUOTE_STATUS], {"sv": STATUS_SV, "de": STATUS_DE})
    assert text.count("Par stannar vanligen nära eller inom sitt revir hela året runt") == 1
    assert text.count("<paragraph>") == 3


def test_the_verify_prompt_judges_all_quotes_of_a_fact_together() -> None:
    template = (PIPELINE / "prompts/verify-v1.md").read_text(encoding="utf-8")
    system, _ = _split_prompt(template, about=ABOUT, facts="")
    assert "several sources" in system
    assert "all of a fact's quotes and paragraphs together" in system


def test_a_changed_second_quote_makes_the_verification_stale() -> None:
    """Facts are verified again only when they change: every quote counts, not just the
    first (facts_hash covers the whole facts list)."""
    from birdy_fetcher.web.record import facts_hash

    record: dict[str, Any] = {"facts": [TWO_QUOTE_STATUS]}
    before = facts_hash(record)
    changed = {**TWO_QUOTE_STATUS, "sources": [*TWO_QUOTE_STATUS["sources"][:1]]}
    assert facts_hash({"facts": [changed]}) != before


def test_the_verify_prompt_lets_a_lookalike_fact_describe_the_other_species() -> None:
    template = (PIPELINE / "prompts/verify-v1.md").read_text(encoding="utf-8")
    system, user = _split_prompt(template, about=ABOUT, facts="")
    assert 'topic="lookalike"' in system
    assert "may describe that other species" in system
    assert user.startswith(f"Species: {ABOUT}")


async def test_the_checker_names_the_species_in_the_user_message() -> None:
    client = FakeJsonClient([reply(FactVerifyOutput(verdicts=[]))])
    await _checker(client, PIPELINE / "prompts/verify-v1.md").check(
        FACTS, {"sv": ARTICLE}, about=ABOUT
    )
    user = client.calls[0][0]["content"]
    assert isinstance(user, str)
    assert f"Species: {ABOUT}" in user
    assert '<fact id="f01" topic="appearance">' in user


async def test_unsupported_facts_are_reported(tmp_path: Path) -> None:
    prompt = tmp_path / "verify-v1.md"
    prompt.write_text("System: x\n\nUser: {facts}", encoding="utf-8")
    verdicts = [
        FactVerdict(fact_id="f01", verdict="supported", reason=""),
        FactVerdict(fact_id="f02", verdict="unsupported", reason="citatet nämner inget avstånd"),
    ]
    client = FakeJsonClient([reply(FactVerifyOutput(verdicts=verdicts))])
    result = await _checker(client, prompt).check(FACTS, {"sv": ARTICLE}, about=ABOUT)
    assert result == {"f02": ("unsupported", "citatet nämner inget avstånd")}
    assert client.schemas == ["FactVerifyOutput"]


async def test_a_fact_with_no_verdict_counts_as_unsupported(tmp_path: Path) -> None:
    prompt = tmp_path / "verify-v1.md"
    prompt.write_text("System: x\n\nUser: {facts}", encoding="utf-8")
    client = FakeJsonClient([reply(FactVerifyOutput(verdicts=[]))])
    result = await _checker(client, prompt).check(FACTS, {"sv": ARTICLE}, about=ABOUT)
    assert set(result) == {"f01", "f02"}


async def test_duplicate_verdicts_take_the_worst(tmp_path: Path) -> None:
    prompt = tmp_path / "verify-v1.md"
    prompt.write_text("System: x\n\nUser: {facts}", encoding="utf-8")
    # Model returns two verdicts for f01: first "unsupported", then "supported".
    # The result must treat that fact as unsupported (fail-closed, not the last one).
    verdicts = [
        FactVerdict(fact_id="f01", verdict="unsupported", reason="unsupported first"),
        FactVerdict(fact_id="f02", verdict="supported", reason="f02 ok"),
        FactVerdict(fact_id="f01", verdict="supported", reason="supported second"),
    ]
    client = FakeJsonClient([reply(FactVerifyOutput(verdicts=verdicts))])
    result = await _checker(client, prompt).check(FACTS, {"sv": ARTICLE}, about=ABOUT)
    # f01 must be unsupported despite the later "supported" verdict.
    assert result == {"f01": ("unsupported", "unsupported first")}
    # Also verify reverse order gives the same result (most severe always wins).
    verdicts_reversed = [
        FactVerdict(fact_id="f01", verdict="supported", reason="supported first"),
        FactVerdict(fact_id="f02", verdict="supported", reason="f02 ok"),
        FactVerdict(fact_id="f01", verdict="unsupported", reason="unsupported second"),
    ]
    client2 = FakeJsonClient([reply(FactVerifyOutput(verdicts=verdicts_reversed))])
    result2 = await _checker(client2, prompt).check(FACTS, {"sv": ARTICLE}, about=ABOUT)
    assert result2 == {"f01": ("unsupported", "unsupported second")}


async def test_no_checkable_facts_means_no_call(tmp_path: Path) -> None:
    prompt = tmp_path / "verify-v1.md"
    prompt.write_text("System: x\n\nUser: {facts}", encoding="utf-8")
    client = FakeJsonClient([])
    assert await _checker(client, prompt).check(FACTS[2:], {"sv": ARTICLE}, about=ABOUT) == {}


def test_strike_unsupported_keeps_everything_else() -> None:
    verdicts: dict[str, tuple[Verdict, str]] = {
        "f02": ("unsupported", "citatet nämner inget avstånd")
    }
    kept, notes = strike_unsupported(FACTS, verdicts)
    assert [f["id"] for f in kept] == ["f01", "d01"]
    assert "f02 ströks (unsupported): citatet nämner inget avstånd" in notes[0]


def test_missing_required_topics() -> None:
    assert missing_required_topics([{"topic": "appearance"}]) == ["läte", "miljö"]
    only = [{"topic": "appearance"}, {"topic": "voice"}, {"topic": "habitat"}]
    assert missing_required_topics(only) == []


def test_extract_measurements_handles_a_single_value_and_a_range() -> None:
    # Was `extract_measurements(text, "kg") == [Measurement(2.5, 2.5, "kg")]`: that encoded the
    # old behaviour of NOT normalising weight units, which is exactly what V2 now fixes (spec
    # 9.4) so a kg-value and a g-value of the same bird can be compared. Also dropped the
    # per-call `unit` filter argument: the function now scans a text for every kind at once, a
    # Measurement carries its own `kind`, and normalises weight to g (see the new tests below
    # for thousands separators, wingspan-vs-length and clutch size).
    assert extract_measurements("Cirka 14 cm lång.") == [Measurement(14.0, 14.0, "cm", "length")]
    assert extract_measurements("28 till 31 cm.") == [Measurement(28.0, 31.0, "cm", "length")]
    assert extract_measurements("Väger omkring 2,5 kg.") == [
        Measurement(2500.0, 2500.0, "g", "weight")
    ]
    assert extract_measurements("Ingen siffra här.") == []


def test_extract_measurements_reads_thousands_separators_and_decimal_commas() -> None:
    for text in ("1 200 g", "1 200 g", "1 200 g"):  # noqa: RUF001
        assert extract_measurements(text) == [Measurement(1200.0, 1200.0, "g", "weight")]
    assert extract_measurements("1,2 kg") == [Measurement(1200.0, 1200.0, "g", "weight")]
    assert extract_measurements("14,5 cm") == [Measurement(14.5, 14.5, "cm", "length")]
    assert extract_measurements("14.5 cm") == [Measurement(14.5, 14.5, "cm", "length")]


def test_extract_measurements_reads_a_range_with_any_separator() -> None:
    [m] = extract_measurements("28 to 31 cm")
    assert (m.low, m.high, m.kind) == (28.0, 31.0, "length")
    [m] = extract_measurements("Flügelspannweite 45 bis 55 cm")
    assert (m.low, m.high, m.kind) == (45.0, 55.0, "wingspan")


def test_extract_measurements_detects_wingspan_from_a_nearby_keyword() -> None:
    [m] = extract_measurements("vingbredd 65–70 cm")  # noqa: RUF001
    assert m.kind == "wingspan"
    [m] = extract_measurements("längd 28–31 cm")  # noqa: RUF001
    assert m.kind == "length"


def test_extract_measurements_uses_nearest_keyword_when_both_present() -> None:
    # When both wingspan and length keywords appear in the look-back window,
    # use the NEAREST one to decide the kind.
    measurements = extract_measurements("Vingbredden är 45–55 cm och längden 28–31 cm.")  # noqa: RUF001
    assert len(measurements) == 2
    assert measurements[0] == Measurement(45.0, 55.0, "cm", "wingspan")
    assert measurements[1] == Measurement(28.0, 31.0, "cm", "length")


def test_extract_measurements_reverses_order_when_length_comes_first() -> None:
    measurements = extract_measurements("Längden är 28–31 cm och vingbredden 45–55 cm.")  # noqa: RUF001
    assert len(measurements) == 2
    assert measurements[0] == Measurement(28.0, 31.0, "cm", "length")
    assert measurements[1] == Measurement(45.0, 55.0, "cm", "wingspan")


def test_extract_measurements_english_both_keywords() -> None:
    measurements = extract_measurements("Body length 28 to 31 cm, wingspan 45 to 55 cm.")
    assert len(measurements) == 2
    assert measurements[0] == Measurement(28.0, 31.0, "cm", "length")
    assert measurements[1] == Measurement(45.0, 55.0, "cm", "wingspan")


def test_extract_measurements_german_both_keywords() -> None:
    measurements = extract_measurements("Körperlänge 28 bis 31 cm, Flügelspannweite 45 bis 55 cm.")
    assert len(measurements) == 2
    assert measurements[0] == Measurement(28.0, 31.0, "cm", "length")
    assert measurements[1] == Measurement(45.0, 55.0, "cm", "wingspan")


def test_extract_measurements_reads_clutch_size_in_eggs() -> None:
    [m] = extract_measurements("4–6 ägg")  # noqa: RUF001
    assert (m.low, m.high, m.unit, m.kind) == (4.0, 6.0, "ägg", "clutch")
    [m] = extract_measurements("lays 4 to 6 eggs")
    assert (m.low, m.high, m.unit, m.kind) == (4.0, 6.0, "ägg", "clutch")


def test_extract_measurements_ignores_bare_numbers_without_a_unit() -> None:
    assert extract_measurements("1758") == []
    assert extract_measurements("över 100 000 par") == []


def test_number_flag_is_none_when_no_other_article_states_the_unit() -> None:
    fact = {
        "id": "f03",
        "topic": "size",
        "sv": "Talgoxen är cirka 14 cm lång.",
        "sources": [{"article": "sv", "quote": "cirka 14 centimeter lång"}],
    }
    articles = {"sv": WikiArticle("sv", "x", "1", "Talgoxen är cirka 14 cm lång.")}
    assert number_flag(fact, articles) is None


def test_number_flag_catches_a_real_disagreement() -> None:
    fact = {
        "id": "f03",
        "topic": "size",
        "sv": "Arten är cirka 25 cm lång.",
        "sources": [{"article": "sv", "quote": "cirka 25 cm lång"}],
    }
    articles = {
        "sv": WikiArticle("sv", "x", "1", "Arten är cirka 25 cm lång."),
        "en": WikiArticle("en", "x", "1", "The species is about 14 cm long."),
    }
    flag = number_flag(fact, articles)
    assert flag is not None and "f03" in flag


def test_number_flag_accepts_an_overlapping_range() -> None:
    fact = {
        "id": "f03",
        "topic": "size",
        "sv": "28 till 31 cm.",
        "sources": [{"article": "sv", "quote": "28 till 31 cm"}],
    }
    articles = {
        "sv": WikiArticle("sv", "x", "1", "28 till 31 cm."),
        "en": WikiArticle("en", "x", "1", "11 to 12.5 inches, about 29 to 32 cm."),
    }
    assert number_flag(fact, articles) is None


def test_number_flag_normalises_thousands_and_kg_before_comparing() -> None:
    fact = {
        "id": "f04",
        "topic": "size",
        "sv": "Arten väger cirka 1 200 g.",
        "sources": [{"article": "sv", "quote": "cirka 1 200 g"}],
    }
    articles = {
        "sv": WikiArticle("sv", "x", "1", "Arten väger cirka 1 200 g."),
        "en": WikiArticle("en", "x", "1", "The species weighs 1150 to 1250 g."),
    }
    assert number_flag(fact, articles) is None


def test_number_flag_does_not_compare_wingspan_with_body_length() -> None:
    fact = {
        "id": "f05",
        "topic": "size",
        "sv": "Vingbredden är 65 till 70 cm.",
        "sources": [{"article": "sv", "quote": "Vingbredden är 65 till 70 cm"}],
    }
    # The other article only states body length, never wingspan: no evidence either way.
    articles = {
        "sv": WikiArticle("sv", "x", "1", "Vingbredden är 65 till 70 cm."),
        "en": WikiArticle("en", "x", "1", "The body is 28 to 31 cm long."),
    }
    assert number_flag(fact, articles) is None


def test_number_flag_catches_a_real_wingspan_disagreement() -> None:
    fact = {
        "id": "f05",
        "topic": "size",
        "sv": "Vingbredden är 65 till 70 cm.",
        "sources": [{"article": "sv", "quote": "Vingbredden är 65 till 70 cm"}],
    }
    articles = {
        "sv": WikiArticle("sv", "x", "1", "Vingbredden är 65 till 70 cm."),
        "en": WikiArticle("en", "x", "1", "The wingspan is 120 to 130 cm."),
    }
    flag = number_flag(fact, articles)
    assert flag is not None and "f05" in flag


def test_number_flag_catches_a_real_clutch_disagreement() -> None:
    fact = {
        "id": "f06",
        "topic": "breeding",
        "sv": "Lägger 4–6 ägg.",  # noqa: RUF001
        "sources": [{"article": "sv", "quote": "Lägger 4–6 ägg"}],  # noqa: RUF001
    }
    articles = {
        "sv": WikiArticle("sv", "x", "1", "Lägger 4–6 ägg."),  # noqa: RUF001
        "en": WikiArticle("en", "x", "1", "The female lays 9 to 12 eggs."),
    }
    flag = number_flag(fact, articles)
    assert flag is not None and "f06" in flag


def test_redlist_occurrence_flag() -> None:
    assert redlist_occurrence_flag("resident", "VU") is None
    assert redlist_occurrence_flag("absent", "not_listed") is None
    assert redlist_occurrence_flag("absent", None) is None
    flag = redlist_occurrence_flag("rare_visitor", "VU")
    assert flag is not None and "VU" in flag


def test_status_flags_combines_the_data_contradiction_and_the_red_list() -> None:
    record = {
        "facts": [{"id": "s01", "topic": "status", "value": "absent", "sv": "Förekommer inte"}],
        "data": {"totalReports": 5000, "statusSignal": {"contradicts": None}},
        "swedishRedList": "VU",
    }
    flags = status_flags(record)
    assert [f["check"] for f in flags] == ["V3", "V3"]
    assert all(f["factId"] == "s01" for f in flags)
    assert "5000 rapporter" in flags[0]["message"]


def test_status_flags_derives_the_contradiction_from_the_data_not_the_stored_signal() -> None:
    """I3 (final review 2026-10-06): `web sources --force` rebuilds `data` with
    `statusSignal.contradicts = None`; the contradiction must come from the current data and
    status, never from that stored value."""
    status = {"id": "s01", "topic": "status", "value": "resident", "sv": "Stannfågel"}
    winter_gap = {"months": [100] * 11 + [1], "totalReports": 900}
    reset = {"facts": [status], "data": {**winter_gap, "statusSignal": {"contradicts": None}}}
    assert [f["check"] for f in status_flags(reset)] == ["V3"]
    stale = {
        "facts": [status],
        "data": {"months": [100] * 12, "totalReports": 900, "statusSignal": {"contradicts": "x"}},
    }
    assert status_flags(stale) == []


def test_status_flags_is_empty_without_a_status_fact() -> None:
    assert status_flags({"facts": []}) == []


EDITED_STATUS = {
    "id": "s01",
    "topic": "status",
    "value": "breeding_migrant",
    "sv": "Flyttfågel, häckar här",
    "sources": [],
    "edited": True,
}
EDITED_FACT = {
    "id": "f03",
    "topic": "appearance",
    "sv": "Gul buk med ett brett svart band.",
    "sources": [{"article": "sv", "quote": "gul buk"}],
    "edited": True,
}


async def test_edited_facts_are_not_sent_to_the_fact_checker() -> None:
    """Follow-up 1 (wave A review): a fact Albin changed in the sheet has no quote that
    backs it (an edited s01 has none at all, which crashed the rendering with an
    IndexError); V1 must leave his decisions alone."""
    client = FakeJsonClient([reply(FactVerifyOutput(verdicts=[]))])
    facts = [*FACTS, EDITED_STATUS, EDITED_FACT]
    result = await _checker(client, PIPELINE / "prompts/verify-v1.md").check(
        facts, {"sv": ARTICLE}, about=ABOUT
    )
    user = client.calls[0][0]["content"]
    assert isinstance(user, str)
    assert 'id="s01"' not in user
    assert 'id="f03"' not in user
    assert set(result) == {"f01", "f02"}


async def test_only_edited_facts_means_no_call() -> None:
    client = FakeJsonClient([])
    checker = _checker(client, PIPELINE / "prompts/verify-v1.md")
    assert await checker.check([EDITED_STATUS, FACTS[2]], {"sv": ARTICLE}, about=ABOUT) == {}
    assert client.calls == []


def test_number_flag_skips_an_edited_fact() -> None:
    fact = {
        "id": "f06",
        "topic": "size",
        "sv": "Cirka 25 cm lång.",
        "sources": [{"article": "sv", "quote": "cirka 25 cm lång"}],
        "edited": True,
    }
    articles = {"en": WikiArticle("en", "x", "1", "About 14 cm long.")}
    assert number_flag(fact, articles) is None


def test_status_flags_honours_a_status_albin_confirmed() -> None:
    """Follow-up 2: a forced re-verify after an import must not bring back a V3 flag Albin
    already kept (`review.statusConfirmed`; apply_facts clears it when the facts change)."""
    review: dict[str, Any] = {"statusConfirmed": True}
    record = {
        "facts": [{"id": "s01", "topic": "status", "value": "absent", "sv": "Förekommer inte"}],
        "data": {"totalReports": 5000},
        "swedishRedList": "VU",
        "review": review,
    }
    assert status_flags(record) == []
    review["statusConfirmed"] = False
    assert [f["check"] for f in status_flags(record)] == ["V3", "V3"]


def test_the_verify_prompt_accepts_scandinavia_and_translated_names_and_units() -> None:
    """Follow-up 5: status quotes about Scandinavia/Fennoscandia/Norden count, and a bird
    name or unit written differently in the quote is not a mismatch."""
    template = (PIPELINE / "prompts/verify-v1.md").read_text(encoding="utf-8")
    system, _ = _split_prompt(template, about=ABOUT, facts="")
    for place in ("Scandinavia", "Fennoscandia", "Skandinavien", "Norden"):
        assert place in system
    assert '"blue tit"' in system
    assert '"14 cm"' in system


def test_render_facts_for_check_shows_how_the_article_writes_the_lookalike() -> None:
    lookalike = {
        "id": "f03",
        "topic": "lookalike",
        "sv": "Kråkan är helsvart.",
        "sources": [{"article": "sv", "quote": "ringande ti-ta ti-ta"}],
        "other": {"scientific": "Corvus corone", "qid": "Q26198", "written": "C. corone"},
    }
    text = render_facts_for_check([lookalike], {"sv": ARTICLE})
    assert '<fact id="f03" topic="lookalike" other="Corvus corone" written="C. corone">' in text


def test_the_verify_prompt_explains_the_written_attribute() -> None:
    template = (PIPELINE / "prompts/verify-v1.md").read_text(encoding="utf-8")
    system, _ = _split_prompt(template, about=ABOUT, facts="")
    assert "the written attribute" in system
