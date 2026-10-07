"""Tests for web/facts.py: quote checks, ids, status and the data facts."""

from __future__ import annotations

from birdy_fetcher.web.facts import (
    ArticleLang,
    FactSheetOutput,
    FactSource,
    ModelFact,
    ModelStatus,
    Topic,
    apply_facts,
    check_fact_sheet,
    data_facts,
)
from birdy_fetcher.web.record import new_record
from birdy_fetcher.web.wiki_full import WikiArticle

ARTICLES = {
    "sv": WikiArticle(
        "sv",
        "Talgoxe",
        "1",
        "Talgoxen är cirka 14 centimeter lång. Hanen har ett bredare svart band på buken än "
        "honan. Sången är ett ringande ti-ta ti-ta. Talgoxen lever i skog, parker och "
        "trädgårdar. Den är stannfågel i hela Sverige.",
    ),
    "de": WikiArticle(
        "de",
        "Kohlmeise",
        "3",
        "Die Kohlmeise kann mit der Blaumeise (Cyanistes caeruleus) verwechselt werden.",
    ),
}
INDEX = {"cyanistes caeruleus": "Q25404"}


def _fact(
    topic: Topic, sv: str, quote: str, lang: ArticleLang = "sv", other: str | None = None
) -> ModelFact:
    return ModelFact(
        topic=topic,
        sv=sv,
        sources=[FactSource(article=lang, quote=quote)],
        other_scientific=other,
    )


GOOD = [
    _fact("size", "Talgoxen är cirka 14 centimeter lång.", "Talgoxen är cirka 14 centimeter lång"),
    _fact("sex_age", "Hanen har bredare svart band på buken.", "Hanen har ett bredare svart band"),
    _fact("voice", "Sången är ett ringande ti-ta ti-ta.", "Sången är ett ringande ti-ta ti-ta"),
    _fact("habitat", "Lever i skog, parker och trädgårdar.", "lever i skog, parker och trädgårdar"),
    _fact("appearance", "Svart band på buken.", "ett bredare svart band på buken"),
    _fact(
        "lookalike",
        "Kan förväxlas med blåmesen.",
        "kann mit der Blaumeise (Cyanistes caeruleus) verwechselt",
        "de",
        "Cyanistes caeruleus",
    ),
]
STATUS = ModelStatus(
    value="resident", sources=[FactSource(article="sv", quote="Den är stannfågel i hela Sverige")]
)


def test_valid_facts_get_ids_and_lookalikes_get_their_qid() -> None:
    check = check_fact_sheet(FactSheetOutput(facts=GOOD, sweden_status=STATUS), ARTICLES, INDEX)
    assert [f["id"] for f in check.facts] == ["f01", "f02", "f03", "f04", "f05", "f06"]
    assert check.facts[5]["other"] == {"scientific": "Cyanistes caeruleus", "qid": "Q25404"}
    assert check.status is not None
    assert check.status["id"] == "s01"
    assert check.status["sv"] == "Stannfågel"
    assert check.fatal == []


def test_a_fact_with_an_invented_quote_is_struck() -> None:
    invented = _fact("appearance", "Har röd näbb.", "Talgoxen har en klarröd näbb")
    check = check_fact_sheet(
        FactSheetOutput(facts=[*GOOD, invented], sweden_status=None), ARTICLES, INDEX
    )
    assert len(check.facts) == len(GOOD)
    assert any("citatet finns inte" in n for n in check.notes)


def test_a_quote_from_an_article_that_is_missing_does_not_count() -> None:
    from_en = _fact("appearance", "Svart band.", "ett bredare svart band på buken", "en")
    check = check_fact_sheet(FactSheetOutput(facts=[from_en], sweden_status=None), ARTICLES, INDEX)
    assert check.facts == []


def test_a_lookalike_without_a_name_is_struck() -> None:
    nameless = _fact("lookalike", "Kan förväxlas.", "kann mit der Blaumeise (Cyanistes", "de")
    check = check_fact_sheet(
        FactSheetOutput(facts=[*GOOD, nameless], sweden_status=None), ARTICLES, INDEX
    )
    assert len(check.facts) == len(GOOD)


def test_missing_required_topics_are_fatal_and_too_few_facts_only_retried() -> None:
    check = check_fact_sheet(FactSheetOutput(facts=GOOD[:2], sweden_status=None), ARTICLES, INDEX)
    assert check.fatal == ["det saknas fakta om utseende, läte, miljö"]
    assert any("minst 10" in r for r in check.retry)


def _many(topic: Topic, count: int) -> list[ModelFact]:
    quotes = {
        "appearance": "ett bredare svart band på buken",
        "voice": "Sången är ett ringande ti-ta ti-ta",
        "habitat": "lever i skog, parker och trädgårdar",
        "food": "Talgoxen är cirka 14 centimeter lång",
        "behaviour": "Hanen har ett bredare svart band",
    }
    if topic == "lookalike":
        return [
            _fact(
                "lookalike",
                f"Förväxling {i}.",
                "kann mit der Blaumeise (Cyanistes caeruleus) verwechselt",
                "de",
                "Cyanistes caeruleus",
            )
            for i in range(1, count + 1)
        ]
    return [_fact(topic, f"{topic} {i}.", quotes[topic]) for i in range(1, count + 1)]


OVER_THE_CAP = [
    *_many("appearance", 12),
    *_many("voice", 12),
    *_many("habitat", 4),
    *_many("food", 6),
    *_many("behaviour", 6),
    *_many("lookalike", 3),
]


def test_the_cap_keeps_every_topic_instead_of_cutting_the_last_ones() -> None:
    """R3 (2026-10-07): Opus 5 gave 43 facts and the first 30 were kept, so every food,
    behaviour and look-alike fact was lost. The cap now takes the topics in turns (the first
    fact of each, then the second, ...), required topics first in each turn."""
    check = check_fact_sheet(
        FactSheetOutput(facts=OVER_THE_CAP, sweden_status=None), ARTICLES, INDEX
    )
    topics = [f["topic"] for f in check.facts]
    assert len(topics) == 30
    assert {t: topics.count(t) for t in dict.fromkeys(topics)} == {
        "appearance": 6,
        "voice": 6,
        "habitat": 4,
        "food": 6,
        "behaviour": 5,
        "lookalike": 3,
    }
    # The model's order, and within a topic its first facts.
    assert [f["sv"] for f in check.facts[:7]] == [
        *(f"appearance {i}." for i in range(1, 7)),
        "voice 1.",
    ]
    assert [f["id"] for f in check.facts] == [f"f{i:02d}" for i in range(1, 31)]


def test_the_cap_reports_every_fact_it_dropped() -> None:
    check = check_fact_sheet(
        FactSheetOutput(facts=OVER_THE_CAP, sweden_status=None), ARTICLES, INDEX
    )
    assert any(n.startswith("13 fakta över gränsen 30 ströks") for n in check.notes)
    dropped = [n for n in check.notes if "över gränsen 30 (" in n]
    assert len(dropped) == 13
    assert "faktum 7 ströks, över gränsen 30 (utseende): appearance 7." in dropped
    assert "faktum 40 ströks, över gränsen 30 (beteende): behaviour 6." in dropped
    assert check.retry == []


def test_data_facts_come_from_the_record() -> None:
    record = new_record("Q25485")
    record["data"] = {"sentences": {"sv": ["Rapporteras året runt.", "Vanligast i Skåne."]}}
    record["swedishRedList"] = "VU"
    assert data_facts(record) == [
        {"id": "d01", "topic": "data", "source": "artportalen", "sv": "Rapporteras året runt."},
        {"id": "d02", "topic": "data", "source": "artportalen", "sv": "Vanligast i Skåne."},
        {
            "id": "d03",
            "topic": "data",
            "source": "rodlistan",
            "sv": "Svenska rödlistan 2025: Sårbar (VU).",
        },
    ]
    record["swedishRedList"] = "not_listed"
    assert data_facts(record)[-1]["sv"] == "Inte rödlistad i Svenska rödlistan 2025."


def test_the_county_share_data_fact_is_marked() -> None:
    """The text checks find it by its kind (a sentence citing it must say andel/share)."""
    record = new_record("Q212055")
    share = "Andelen av alla fågelrapporter är högst i Halland, Gotland och Kalmar."
    record["data"] = {"sentences": {"sv": ["Rapporteras året runt.", share]}}
    facts = data_facts(record)
    assert "kind" not in facts[0]
    assert facts[1] == {
        "id": "d02",
        "topic": "data",
        "source": "artportalen",
        "kind": "countyShare",
        "sv": share,
    }


def test_an_absent_species_gets_the_absence_fact_and_no_red_list_fact() -> None:
    """R3 (2026-10-07): Koboltmes. The absence is a data fact the text can cite; "Inte
    rödlistad" is not written for a species Sweden has not assessed, even in a record from
    before `web sources` dropped its `not_listed`."""
    record = new_record("Q10546857")
    absent = "Förekommer inte i Sverige: inga rapporter i Artportalen 2016 till 2025."
    record["data"] = {"totalReports": 0, "sentences": {"sv": [absent]}}
    record["swedishRedList"] = "not_listed"
    assert data_facts(record) == [
        {"id": "d01", "topic": "data", "source": "artportalen", "kind": "absent", "sv": absent}
    ]


def test_apply_facts_clears_a_stale_verification_flags_and_verify_hash() -> None:
    """C1 (review fix 2026-10-06): new facts have never been through V1, so an earlier
    `verification`/`flags`/`generated.verify` must not survive -- otherwise `web write`
    could write from a sheet nothing has checked."""
    record = new_record("Q25485")
    record["verification"] = {
        "method": "auto",
        "at": "2026-11-01",
        "model": "claude-sonnet-5",
        "spotChecked": False,
    }
    record["flags"] = [{"check": "V2", "factId": "f03", "message": "x"}]
    record["generated"] = {"verify": {"model": "claude-sonnet-5", "factsHash": "deadbeef"}}
    check = check_fact_sheet(FactSheetOutput(facts=GOOD, sweden_status=STATUS), ARTICLES, INDEX)
    apply_facts(record, check, generated={"model": "claude-opus-5"})
    assert "verification" not in record
    assert "flags" not in record
    assert "verify" not in record["generated"]
    assert record["generated"]["facts"] == {"model": "claude-opus-5"}


def test_apply_facts_orders_facts_and_flags_a_status_contradiction() -> None:
    record = new_record("Q25485")
    record["text"] = {"sv": {}}
    record["review"] = {"statusConfirmed": True}
    record["data"] = {
        "months": [0, 0, 5, 60, 100, 90, 85, 70, 40, 5, 0, 0],
        "totalReports": 5000,
        "sentences": {"sv": ["Rapporteras mest i maj till juli."]},
        "statusSignal": {"contradicts": None},
    }
    check = check_fact_sheet(FactSheetOutput(facts=GOOD, sweden_status=STATUS), ARTICLES, INDEX)
    apply_facts(record, check, generated={"model": "claude-opus-5"})
    ids = [f["id"] for f in record["facts"]]
    assert ids == ["f01", "f02", "f03", "f04", "f05", "f06", "s01", "d01"]
    assert record["data"]["statusSignal"]["contradicts"] is not None
    assert record["text"] is None
    assert record["status"] == "pending"
    assert "statusConfirmed" not in record["review"]
    assert record["generated"]["facts"] == {"model": "claude-opus-5"}


def test_an_abbreviated_lookalike_gets_the_binomial_and_its_qid() -> None:
    """R3 (2026-10-07): Råka's look-alike was "C. corone" (or "C. corone corone"), so it
    got no QID and the box could not link to Kråka (Corvus corone)."""
    articles = {
        "sv": WikiArticle(
            "sv", "Råka", "1", "Råkan kan förväxlas med svartkråka (C. corone corone)."
        )
    }
    index = {"corvus corone": "Q26198", "corvus frugilegus": "Q25386"}
    lookalike = _fact(
        "lookalike",
        "Kan förväxlas med svartkråka.",
        "kan förväxlas med svartkråka (C. corone corone)",
        other="C. corone corone",
    )
    check = check_fact_sheet(
        FactSheetOutput(facts=[lookalike], sweden_status=None),
        articles,
        index,
        subject="Corvus frugilegus",
    )
    assert check.facts[0]["other"] == {"scientific": "Corvus corone", "qid": "Q26198"}


def test_a_lookalike_birdy_does_not_have_keeps_the_name_as_written() -> None:
    articles = {"en": WikiArticle("en", "Rook", "1", "It resembles the American crow C. brachy.")}
    lookalike = _fact(
        "lookalike", "Liknar amerikansk kråka.", "resembles the American crow", "en", "C. brachy"
    )
    check = check_fact_sheet(
        FactSheetOutput(facts=[lookalike], sweden_status=None),
        articles,
        {"corvus corone": "Q26198"},
        subject="Corvus frugilegus",
    )
    assert check.facts[0]["other"] == {"scientific": "C. brachy"}
