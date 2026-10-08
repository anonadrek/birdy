# ruff: noqa: RUF001, RUF003
"""Release 1.3.1 part 7: no dashes in the species texts the app shows (app_dashes.py and
app_dashes_run.py). The file is full of dashes on purpose, hence the RUF001 exemption."""

from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml

from birdy_fetcher.app_dashes import (
    apply_rewrites,
    check_rewrite,
    dump_species,
    is_hidden_heading,
    rule_fix,
    scan_species,
    split_sentences,
)

HEADING_AND_TWO_DASHES = (
    "# Testmes – Förekomst\n\nDen är 12–14 cm lång. "
    "Lätet — ett vasst pip — hörs i maj. Den häckar i skog."
)


def write_species(
    tmp_path: Path, description_sv: str, description_en: str = "A plain text."
) -> Path:
    data: dict[str, Any] = {
        "id": "Q1",
        "scientific_name": "Parus testus",
        "names": {"sv": "Testmes", "en": "Test Tit"},
        "description": {"sv": description_sv, "en": description_en},
        "migration": {"sv": "Flyttar i mars–april."},
        "marginalia": {"sv": None},
        "review_notes": "Anteckning — rörs inte.",
    }
    path = tmp_path / "paridae" / "Q1.yaml"
    path.parent.mkdir(parents=True, exist_ok=True)
    dump_species(data, path)
    return path


def test_hidden_heading_follows_the_app_cleaner() -> None:
    assert is_hidden_heading("# Talgoxe – Förekomst i Skandinavien")
    assert is_hidden_heading("## Flyttning")
    assert is_hidden_heading("#")
    assert is_hidden_heading("**Grågam - Förekomst i Norden**")
    assert not is_hidden_heading("#hashtag")
    assert not is_hidden_heading("####### sju")
    assert not is_hidden_heading("**Talgoxen är en vanlig fågel.**")
    assert not is_hidden_heading("**Talgoxen** är en **vanlig** fågel")
    assert not is_hidden_heading("")


def test_rule_fix_turns_number_and_month_ranges_into_words() -> None:
    assert rule_fix("Den är 12–14 cm.", "sv") == "Den är 12 till 14 cm."
    assert rule_fix("Mellan 1990—2000 ökade den.", "sv") == "Mellan 1990 till 2000 ökade den."
    assert rule_fix("Den flyttar i mars–april.", "sv") == "Den flyttar i mars till april."
    assert rule_fix("It is 12–14 cm long.", "en") == "It is 12 to 14 cm long."
    assert rule_fix("It passes in Oct–Nov.", "en") == "It passes in Oct to Nov."
    assert rule_fix("The call—a sharp pip—carries.", "en") == "The call—a sharp pip—carries."


def test_split_sentences_puts_the_paragraph_back_byte_for_byte() -> None:
    paragraph = "Den häckar bl.a. i Skåne. Lätet — ett pip — hörs. Ses i maj."
    parts = split_sentences(paragraph)
    assert "".join(parts) == paragraph
    assert parts[0] == "Den häckar bl.a. i Skåne."
    assert parts[2] == "Lätet — ett pip — hörs."
    assert parts[4] == "Ses i maj."


def test_scan_skips_the_hidden_heading_and_applies_the_rules(tmp_path: Path) -> None:
    scan = scan_species(write_species(tmp_path, HEADING_AND_TWO_DASHES))
    assert scan.qid == "Q1"
    assert scan.rule_fixes == 2  # 12–14 in the description, mars–april in the migration
    assert [s.text for s in scan.sentences] == ["Lätet — ett vasst pip — hörs i maj."]
    sentence = scan.sentences[0]
    assert (sentence.field, sentence.lang, sentence.line, sentence.part) == (
        "description",
        "sv",
        2,
        2,
    )
    fixed = scan.fixed[("description", "sv")]
    assert fixed.startswith("# Testmes – Förekomst\n")  # the hidden heading keeps its dash
    assert "12 till 14 cm" in fixed
    assert scan.fixed[("migration", "sv")] == "Flyttar i mars till april."


def test_check_rewrite_accepts_a_plain_rewrite() -> None:
    assert check_rewrite("Lätet — ett pip — hörs i maj.", "Lätet, ett pip, hörs i maj.") == []


def test_check_rewrite_names_each_problem() -> None:
    assert "tankstreck kvar" in check_rewrite("A — b.", "A – b.")
    assert "talen skiljer sig" in check_rewrite(
        "Den är 12 cm — ibland 14.", "Den är 12 cm, ibland 15."
    )
    assert "betoningen (*) skiljer sig" in check_rewrite(
        "*Parus major* — talgoxen.", "Parus major, talgoxen."
    )
    assert "radbrytning" in check_rewrite("A — b.", "A.\nB.")
    assert any(
        p.startswith("längden") for p in check_rewrite("Lätet — ett pip — hörs i maj.", "Ja.")
    )


def test_apply_rewrites_changes_only_the_target_sentence(tmp_path: Path) -> None:
    scan = scan_species(write_species(tmp_path, HEADING_AND_TWO_DASHES))
    data = apply_rewrites(scan, {scan.sentences[0].key: "Lätet, ett vasst pip, hörs i maj."})
    assert data["description"]["sv"] == (
        "# Testmes – Förekomst\n\nDen är 12 till 14 cm lång. "
        "Lätet, ett vasst pip, hörs i maj. Den häckar i skog."
    )
    assert data["review_notes"] == "Anteckning — rörs inte."
    assert data["description"]["en"] == "A plain text."


def test_dump_species_round_trips(tmp_path: Path) -> None:
    path = write_species(tmp_path, HEADING_AND_TWO_DASHES)
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    before = path.read_text(encoding="utf-8")
    dump_species(data, path)
    assert path.read_text(encoding="utf-8") == before
