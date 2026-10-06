"""A reviewed talgoxe record and a text that passes every code check."""

from __future__ import annotations

from typing import Any

from birdy_fetcher.web.record import Record, new_record
from birdy_fetcher.web.text_model import LangTextV2, LookAlikeText, Sentence, SizeText, WebTextV2


def S(text: str, *ids: str) -> Sentence:  # noqa: N802 -- short constructor for test data
    return Sentence(text=text, fact_ids=list(ids))


FACTS: list[dict[str, Any]] = [
    {"id": "f01", "topic": "appearance", "sv": "Talgoxen har svart huvud med vita kinder.",
     "sources": [{"article": "sv", "quote": "svart huvud med vita kinder"}]},
    {"id": "f02", "topic": "appearance", "sv": "Buken är gul med ett svart band längs mitten.",
     "sources": [{"article": "sv", "quote": "gul buk med ett svart band"}]},
    {"id": "f03", "topic": "size", "sv": "Talgoxen är cirka 14 centimeter lång.",
     "sources": [{"article": "sv", "quote": "cirka 14 centimeter lång"}]},
    {"id": "f04", "topic": "voice", "sv": "Sången är ett ringande ti ta, ti ta.",
     "sources": [{"article": "sv", "quote": "ett ringande ti ta, ti ta"}]},
    {"id": "f05", "topic": "habitat", "sv": "Talgoxen lever i skog, parker och trädgårdar.",
     "sources": [{"article": "sv", "quote": "i skog, parker och trädgårdar"}]},
    {"id": "f06", "topic": "lookalike", "sv": "Blåmesen är mindre och har blå hätta.",
     "sources": [{"article": "sv", "quote": "blåmesen är mindre och har blå hätta"}],
     "other": {"scientific": "Cyanistes caeruleus", "qid": "Q25404"}},
    {"id": "s01", "topic": "status", "value": "resident", "sv": "Stannfågel",
     "sources": [{"article": "sv", "quote": "Den är stannfågel i hela Sverige"}]},
    {"id": "d01", "topic": "data", "source": "artportalen", "sv": "Rapporteras året runt."},
]  # fmt: skip

SV = LangTextV2(
    lead=[
        S("Talgoxen är en vanlig fågel i skog, parker och trädgårdar.", "f05"),
        S("Den har svart huvud med vita kinder.", "f01"),
    ],
    field_marks=[
        S("Svart huvud med vita kinder", "f01"),
        S("Gul buk med ett svart band längs mitten", "f02"),
        S("Cirka 14 centimeter lång", "f03"),
    ],
    voice=[S("Sången är ett ringande ti ta, ti ta.", "f04")],
    where_when=[
        S(
            "Stannfågel som rapporteras året runt i skog, parker och trädgårdar.",
            "s01",
            "d01",
            "f05",
        )
    ],
    behaviour=[],
    look_alikes=[
        LookAlikeText(other="Q25404", sentences=[S("Blåmesen är mindre och har blå hätta.", "f06")])
    ],
    meta_description=(
        "Talgoxe: så känner du igen den på svart huvud och gul buk, hur sången låter och var och "
        "när den ses i Sverige under hela året."
    ),
    size=SizeText(value="Cirka 14 cm", fact_ids=["f03"]),
)
EN = LangTextV2(
    lead=[
        S("The great tit is a common bird of woodland, parks and gardens.", "f05"),
        S("It has a black head with white cheeks.", "f01"),
    ],
    field_marks=[
        S("Black head with white cheeks", "f01"),
        S("Yellow belly with a black stripe down the middle", "f02"),
        S("About 14 centimetres long", "f03"),
    ],
    voice=[S("The song is a ringing tee cha, tee cha.", "f04")],
    where_when=[
        S(
            "A resident that is reported all year in woodland, parks and gardens.",
            "s01",
            "d01",
            "f05",
        )
    ],
    behaviour=[],
    look_alikes=[
        LookAlikeText(
            other="Q25404", sentences=[S("The blue tit is smaller with a blue cap.", "f06")]
        )
    ],
    meta_description=(
        "Great tit: how to recognise it by its black head and yellow belly, what its song sounds "
        "like and when it is seen in Sweden."
    ),
    size=SizeText(value="About 14 cm", fact_ids=["f03"]),
)
VALID = WebTextV2(sv=SV, en=EN)
BANNED = ["fascinerande", "remarkable"]


def reviewed_record(qid: str = "Q25485") -> Record:
    record = new_record(qid)
    record["names"] = {"sv": "Talgoxe", "en": "Great Tit", "scientific": "Parus major"}
    record["family"] = {"latin": "Paridae", "sv": "Mesar"}
    record["group"] = "songbirds"
    record["facts"] = [dict(f) for f in FACTS]
    record["data"] = {"statusSignal": {"contradicts": None}}
    record["review"] = {"wave": 1}
    record["verification"] = {
        "method": "auto",
        "at": "2026-11-20",
        "model": "claude-sonnet-5",
        "spotChecked": False,
    }
    return record
