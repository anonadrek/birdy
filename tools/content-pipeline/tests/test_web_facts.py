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
