"""Shared test data for the web step: a valid model answer and matching articles."""

from __future__ import annotations

from birdy_fetcher.web.model import Facts, LangText, SizeFact, StatusFact, WebTextOutput
from birdy_fetcher.web.wiki_full import WikiArticle

ARTICLES = {
    "sv": WikiArticle(
        lang="sv",
        title="Talgoxe",
        revision="111",
        text="Talgoxen är cirka 14 centimeter lång och väger omkring 18 gram. "
        "Den är stannfågel i hela Sverige och ses året runt.",
    ),
    "en": WikiArticle(
        lang="en",
        title="Great tit",
        revision="222",
        text="The great tit is about 14 centimetres long. It is a resident bird across Sweden.",
    ),
}


def valid_output() -> WebTextOutput:
    return WebTextOutput(
        sv=LangText(
            lead="Den största av mesarna och en vanlig gäst vid fågelbordet. "
            "Den finns i hela Sverige året runt.",
            field_marks=[
                "Svart huvud med vita kinder",
                "Gul buk med ett svart band längs mitten",
                "Olivgrön rygg och blågrå vingar med vitt vingband",
            ],
            voice="Sången är ett ringande ti ta, ti ta som hörs redan i februari.",
            where_when="Stannfågel i hela landet. Vanligast i lövskog, parker och trädgårdar.",
            meta_description=(
                "Talgoxe: så känner du igen den på gul buk och svart slips, hur sången låter "
                "och var och när du ser den i Sverige under året."
            ),
            facts=Facts(
                size=SizeFact(value="Cirka 14 cm", quote="cirka 14 centimeter lång och väger"),
                sweden_status=StatusFact(
                    value="resident", quote="Den är stannfågel i hela Sverige"
                ),
            ),
        ),
        en=LangText(
            lead="The largest of the tits and a regular visitor to bird feeders. "
            "It lives across Sweden all year.",
            field_marks=[
                "Black head with white cheeks",
                "Yellow belly with a black stripe down the middle",
                "Olive back and blue grey wings with a white wing bar",
            ],
            voice="The song is a ringing tee cha, tee cha that is heard as early as February.",
            where_when="Resident across the country. Most common in woodland, parks and gardens.",
            meta_description=(
                "Great tit: how to recognise it by its yellow belly and black stripe, what its "
                "song sounds like and where you see it in Sweden."
            ),
            facts=Facts(
                size=SizeFact(value="About 14 cm", quote="about 14 centimetres long"),
                sweden_status=StatusFact(
                    value="resident", quote="It is a resident bird across Sweden"
                ),
            ),
        ),
    )
