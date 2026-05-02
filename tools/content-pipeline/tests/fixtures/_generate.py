"""Genererar ioc_sample.xlsx och vp11_sample.pdf. Kör en gång:
    uv run --with reportlab,openpyxl python tests/fixtures/_generate.py
Båda fixturerna committas binärt; skriptet finns för reproducerbarhet."""

from __future__ import annotations

from pathlib import Path

import openpyxl
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

HERE = Path(__file__).parent


def gen_ioc_xlsx() -> None:
    """Skapar en mini-IOC-xlsx med samma kolumn-layout som riktiga filen."""
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.append(["Order", "Family", "FamilyEN", "Scientific name", "English name"])
    rows = [
        ("Passeriformes", "Paridae", "Tits", "Parus major", "Great Tit"),
        ("Passeriformes", "Paridae", "Tits", "Cyanistes caeruleus", "Eurasian Blue Tit"),
        ("Passeriformes", "Turdidae", "Thrushes", "Turdus merula", "Common Blackbird"),
        ("Anseriformes", "Anatidae", "Ducks Geese and Swans", "Cygnus olor", "Mute Swan"),
        ("Falconiformes", "Falconidae", "Falcons", "Falco tinnunculus", "Common Kestrel"),
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
