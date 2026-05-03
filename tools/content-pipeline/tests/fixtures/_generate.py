"""Genererar ioc_sample.xlsx och vp11_sample.pdf. Kör en gång:
    uv run --with reportlab,openpyxl python tests/fixtures/_generate.py
Båda fixturerna committas binärt; skriptet finns för reproducerbarhet."""

from __future__ import annotations

from pathlib import Path

import openpyxl
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

HERE = Path(__file__).parent

# IOC v14.1 har 13 kolumner. Vi använder bara fyra (Rank, English name, Counters,
# Scientific Name); helpern fyller resten med None så fixturen speglar riktiga filen.
_IOC_HEADERS = [
    "",
    "13.2",
    "13.1",
    "Rank",
    "SP; Extinct",
    "English name",
    "Counters",
    "Scientific Name",
    "Authority",
    "Breeding Range",
    "Nonbreeding Range",
    "Code",
    "Comment",
]


def _ioc_row(rank: str, eng: str | None, counters: str | None, sci: str | None) -> tuple:
    """Bygg en 13-kolumns IOC-rad där bara fälten parsern läser har innehåll."""
    return (None, None, None, rank, None, eng, counters, sci, None, None, None, None, None)


def gen_ioc_xlsx() -> None:
    """Skapar en mini-IOC-xlsx som speglar riktiga IOC v14.1 hierarkiska struktur.

    Rank-värden: 'INFRACLASS', 'ORDER', 'Family', 'Genus', 'Species', 'ssp', 'Blank'.
    'Scientific Name' har "ORDER X" / "Family Y" / Genus / binom beroende på rank.
    """
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = "14.1"
    ws.append(_IOC_HEADERS)
    rows = [
        _ioc_row("INFRACLASS", "PASSERIFORMES", None, "NEOAVES"),
        _ioc_row("ORDER", None, None, "ORDER PASSERIFORMES"),
        _ioc_row("Family", "Tits", "2", "Family Paridae"),
        _ioc_row("Genus", None, None, "Parus"),
        _ioc_row("Species", "Great Tit", "Paridae", "Parus major"),
        _ioc_row("ssp", "Great Tit", None, "Parus major major"),
        _ioc_row("Genus", None, None, "Cyanistes"),
        _ioc_row("Species", "Eurasian Blue Tit", "Paridae", "Cyanistes caeruleus"),
        _ioc_row("Blank", None, None, None),
        _ioc_row("Family", "Thrushes", "1", "Family Turdidae"),
        _ioc_row("Genus", None, None, "Turdus"),
        _ioc_row("Species", "Common Blackbird", "Turdidae", "Turdus merula"),
        _ioc_row("Blank", None, None, None),
        _ioc_row("ORDER", None, None, "ORDER ANSERIFORMES"),
        _ioc_row("Family", "Ducks, Geese, Swans", "1", "Family Anatidae"),
        _ioc_row("Genus", None, None, "Cygnus"),
        _ioc_row("Species", "Mute Swan", "Anatidae", "Cygnus olor"),
        _ioc_row("Blank", None, None, None),
        _ioc_row("ORDER", None, None, "ORDER FALCONIFORMES"),
        _ioc_row("Family", "Falcons, Caracaras", "1", "Family Falconidae"),
        _ioc_row("Genus", None, None, "Falco"),
        _ioc_row("Species", "Common Kestrel", "Falconidae", "Falco tinnunculus"),
    ]
    for r in rows:
        ws.append(r)
    wb.save(HERE / "ioc_sample.xlsx")


def gen_vp11_pdf() -> None:
    """Skapar en mini-VP11-PDF med samma kolumn-x-koordinater som riktiga filen."""
    c = canvas.Canvas(str(HERE / "vp11_sample.pdf"), pagesize=A4)
    # Status @ x=110-145, sci @ 147-265, swe @ 268-355, eng @ 359-455, notes @ 457+
    # y växer nedåt i reportlab; rad-höjd ~12pt; använd y=800 → 700.
    c.setFont("Helvetica", 8)
    c.drawString(110, 815, "STATUS")
    c.drawString(147, 815, "VETENSKAPLIGT NAMN")
    c.drawString(268, 815, "SVENSKT NAMN")
    c.drawString(359, 815, "ENGELSKT NAMN")
    c.drawString(457, 815, "NOTES")
    rows = [
        # status, sci, swe (placeholder), eng, notes, family-row?
        ("Ordning PASSERIFORMES TATTINGAR", None),
        ("Familj Paridae mesar Tits", None),
        ("H", "Parus major", "talgoxe", "Great Tit", ""),
        ("H", "Cyanistes caeruleus", "blamesplaceholder", "Eurasian Blue Tit", ""),
        ("Familj Turdidae trastar Thrushes", None),
        ("H", "Turdus merula", "koltrastplaceholder", "Common Blackbird", ""),
        ("Ordning ANSERIFORMES ANDFAGLAR", None),
        ("Familj Anatidae anderplaceholder", None),
        ("H", "Cygnus olor", "knolsvanplaceholder", "Mute Swan", "Intr."),
        ("Ordning FALCONIFORMES FALKFAGLAR", None),
        ("Familj Falconidae falkar Falcons", None),
        ("H", "Falco tinnunculus", "tornfalkplaceholder", "Common Kestrel", ""),
        ("R", "Setophaga ruticilla", "rodstjartplaceholder", "American Redstart", ""),
    ]
    y = 790
    for row in rows:
        if row[1] is None:
            c.drawString(147, y, row[0])
        else:
            status, sci, swe, eng, notes = row
            c.drawString(110, y, status)
            c.drawString(147, y, sci)
            c.drawString(268, y, swe)
            c.drawString(359, y, eng)
            if notes:
                c.drawString(457, y, notes)
        y -= 12
    c.save()


if __name__ == "__main__":
    gen_ioc_xlsx()
    gen_vp11_pdf()
    print(f"Wrote {HERE / 'ioc_sample.xlsx'}")
    print(f"Wrote {HERE / 'vp11_sample.pdf'}")
