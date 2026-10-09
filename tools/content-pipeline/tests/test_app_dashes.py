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
    is_no_data_heading,
    is_no_data_text,
    rule_fix,
    scan_species,
    split_sentences,
)
from birdy_fetcher.app_dashes_run import (
    _WORD_COUNT_FAIL_REASON,
    CheckReply,
    DashOptions,
    Rewrite,
    RewriteReply,
    Verdict,
    run_app_dashes,
)
from birdy_fetcher.web.llm import MODELS

from .web_fakes import FakeJsonClient, reply

HEADING_AND_TWO_DASHES = (
    "# Testmes – Förekomst\n\nDen är 12–14 cm lång. "
    "Lätet — ett vasst pip — hörs i maj. Den häckar i skog."
)

NO_DATA_HEADING_WITH_DASH_PARAGRAPH = (
    "# Migration data unavailable for this species.\n\nLätet — ett vasst pip — hörs i maj."
)

# A REAL title heading (not itself a no-data heading, so is_no_data_heading on its own misses
# this) followed by a body that OPENS with the no-data sentinel; the real corpus shape behind
# this fix (Q891376 migration.en, Q27074601 migration.en: a title, then "Migration data
# unavailable for this species.", then a real paragraph that happens to have a dash).
HEADING_THEN_NO_DATA_BODY_WITH_DASH_PARAGRAPH = (
    "# Testmes – Förekomst i Skandinavien\n\n"
    "Migration data unavailable for this species.\n\n"
    "Lätet — ett vasst pip — hörs i maj."
)

# No heading at all; the sentinel is the very first line (the real corpus shape behind this
# fix for Q611324 migration.sv: no "#"/"**" heading, the text opens directly with "Migrations-
# data saknas för denna art.", then a real paragraph with a dash).
NO_DATA_BODY_NO_HEADING_WITH_DASH_PARAGRAPH = (
    "Migrationsdata saknas för denna art.\n\nLätet — ett vasst pip — hörs i maj."
)


def write_species(
    tmp_path: Path,
    description_sv: str,
    description_en: str = "A plain text.",
    migration_en: str | None = None,
    migration_sv: str | None = None,
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
    if migration_en is not None:
        data["migration"]["en"] = migration_en
    if migration_sv is not None:
        data["migration"]["sv"] = migration_sv
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


def test_is_no_data_heading_matches_the_app_cleaner() -> None:
    # Real corpus forms (Q3178456.yaml, Q85758401.yaml): title case without a trailing dot, and
    # sentence case with one.
    assert is_no_data_heading("# Migration Data Unavailable for This Species")
    assert is_no_data_heading("# Migration data unavailable for this species.")
    assert is_no_data_heading("# Migrationsdata saknas för denna art.")
    assert is_no_data_heading("# The provided source text contains only taxonomy.")
    assert not is_no_data_heading("# Talgoxe – Förekomst i Skandinavien och Nordeuropa")
    assert not is_no_data_heading("# Flyttning")


def test_rule_fix_turns_number_and_month_ranges_into_words() -> None:
    assert rule_fix("Den är 12–14 cm.", "sv") == "Den är 12 till 14 cm."
    assert rule_fix("Den flyttar i mars–april.", "sv") == "Den flyttar i mars till april."
    assert rule_fix("It is 12–14 cm long.", "en") == "It is 12 to 14 cm long."
    assert rule_fix("It passes in Oct–Nov.", "en") == "It passes in Oct to Nov."
    assert rule_fix("The call—a sharp pip—carries.", "en") == "The call—a sharp pip—carries."


def test_rule_fix_uses_and_och_right_after_between_mellan() -> None:
    # The bug this closes: "mellan 40–50" became "mellan 40 till 50" and "between 24–39.5"
    # became "between 24 to 39.5" -- both read wrong. "Mellan 1990—2000" is the same shape
    # (mellan directly before a number range), so it is no longer an exception either, unlike
    # the plain "Den är 12–14 cm." case above where no mellan/between precedes the range.
    assert rule_fix("between 24–39.5 grams", "en") == "between 24 and 39.5 grams"
    assert rule_fix("mellan 40–50 cm", "sv") == "mellan 40 och 50 cm"
    assert rule_fix("mellan mars–april", "sv") == "mellan mars och april"
    assert rule_fix("Mellan 1990—2000 ökade den.", "sv") == "Mellan 1990 och 2000 ökade den."
    # "about"/"ungefär" right before the range is not "mellan"/"between", so unaffected.
    assert rule_fix("about 24–39.5 grams", "en") == "about 24 to 39.5 grams"


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


def test_scan_skips_a_whole_field_behind_a_no_data_heading(tmp_path: Path) -> None:
    scan = scan_species(
        write_species(
            tmp_path, HEADING_AND_TWO_DASHES, migration_en=NO_DATA_HEADING_WITH_DASH_PARAGRAPH
        )
    )
    assert not any(s.field == "migration" and s.lang == "en" for s in scan.sentences)
    assert ("migration", "en") not in scan.fixed


def test_is_no_data_text_matches_the_app_cleaner() -> None:
    # Mirrors SpeciesTextCleanerTest.kt's isNoDataText-covering cases: the heading form is
    # test_is_no_data_heading_matches_the_app_cleaner above, and the trailing-paragraph form
    # (isTrailingNoDataParagraph) stays unported, so neither is repeated here.
    assert is_no_data_text("Migration data unavailable for this species.")
    assert is_no_data_text("Migrationsdata saknas för denna art.")
    assert is_no_data_text(
        "Migration data unavailable for this species.\n\n"
        "The provided source text contains no information about migration."
    )
    assert is_no_data_text("The source text provides no information about migration.")
    assert is_no_data_text("the source text provides no information about migration.")
    assert is_no_data_text("The provided source text contains only taxonomy.")
    assert is_no_data_text("The Wikipedia source text provided contains no information.")
    assert is_no_data_text("Källtexten innehåller ingen information om flyttning.")
    assert is_no_data_text("källtexten innehåller ingen information om flyttning.")
    assert is_no_data_text("sv: Migrationsdata saknas för denna art.")
    assert is_no_data_text(
        'sv: "Migrationsdata saknas för denna art."\n\n'
        'en: "Migration data unavailable for this species."'
    )
    assert is_no_data_text(
        "**Migrationsdata saknas för denna art.**\n\nKälltexten nämner inget om flyttning."
    )
    assert not is_no_data_text("Stenfalken häckar i fjällen.")
    assert not is_no_data_text(
        "The Iago sparrow does not occur in Scandinavia.\n\n"
        "Migration data unavailable for this species in a Northern European context."
    )
    assert not is_no_data_text("The sourcebook describes the species well.")


def test_scan_skips_a_whole_field_behind_a_real_heading_over_a_no_data_body(tmp_path: Path) -> None:
    # The finding this fix closes: a REAL title heading (kept hidden either way) followed by a
    # body that opens with the no-data sentinel. is_no_data_heading alone misses this because
    # the heading itself is not a no-data heading; the dash paragraph after the sentinel must
    # stay invisible too, because the app collapses the WHOLE field, not just the sentinel line.
    scan = scan_species(
        write_species(
            tmp_path,
            HEADING_AND_TWO_DASHES,
            migration_en=HEADING_THEN_NO_DATA_BODY_WITH_DASH_PARAGRAPH,
        )
    )
    assert not any(s.field == "migration" and s.lang == "en" for s in scan.sentences)
    assert ("migration", "en") not in scan.fixed
    # description.sv is unrelated prose and keeps scanning normally: the fix is scoped to the
    # no-data field, not to the species as a whole.
    assert any(s.field == "description" and s.lang == "sv" for s in scan.sentences)


def test_scan_skips_a_whole_field_with_no_heading_at_all_over_a_no_data_body(
    tmp_path: Path,
) -> None:
    # Same finding, no-heading shape: the text opens directly with the sentinel (no "#"/"**"
    # line at all), so is_hidden_heading(lines[0]) is False and the WHOLE text is the body.
    scan = scan_species(
        write_species(
            tmp_path,
            HEADING_AND_TWO_DASHES,
            migration_sv=NO_DATA_BODY_NO_HEADING_WITH_DASH_PARAGRAPH,
        )
    )
    assert not any(s.field == "migration" and s.lang == "sv" for s in scan.sentences)
    assert ("migration", "sv") not in scan.fixed
    assert any(s.field == "description" and s.lang == "sv" for s in scan.sentences)


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


def test_check_rewrite_folds_a_space_grouped_thousands_separator() -> None:
    assert check_rewrite("Cirka 1 000 individer.", "Cirka 1000 individer.") == []
    assert "talen skiljer sig" in check_rewrite("Cirka 1 000 individer.", "Cirka 100 individer.")


def test_check_rewrite_folds_a_no_break_space_thousands_separator() -> None:
    assert check_rewrite("Cirka 1 000 individer.", "Cirka 1000 individer.") == []
    assert check_rewrite("Cirka 1 000 individer.", "Cirka 1000 individer.") == []


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


GOOD = "Lätet, ett vasst pip, hörs i maj."


def options(tmp_path: Path, **overrides: Any) -> DashOptions:
    values: dict[str, Any] = {
        "species_root": tmp_path,
        "reports": tmp_path / "reports",
        "max_cost": 5.0,
    }
    values.update(overrides)
    return DashOptions(**values)


async def test_an_accepted_rewrite_is_written(tmp_path: Path) -> None:
    path = write_species(tmp_path, HEADING_AND_TWO_DASHES)
    key = scan_species(path).sentences[0].key
    client = FakeJsonClient(
        [
            reply(RewriteReply(rewrites=[Rewrite(id=key, text=GOOD)])),
            reply(CheckReply(verdicts=[Verdict(id=key, same_meaning=True, reason="samma")])),
        ]
    )
    result = await run_app_dashes(options(tmp_path), client)
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    assert GOOD in data["description"]["sv"]
    assert "12 till 14 cm" in data["description"]["sv"]
    assert data["migration"]["sv"] == "Flyttar i mars till april."
    assert data["review_notes"] == "Anteckning — rörs inte."
    assert client.models == [MODELS["opus55"], MODELS["sonnet"]]
    assert result.outcomes[0].written
    assert [new for _, new in result.outcomes[0].rewritten] == [GOOD]
    assert GOOD in result.report.read_text(encoding="utf-8")


# SpeciesValidator.kt's validateSpeciesData requires >=80 words per description language.
# 72 filler words + the 9-word dash sentence ("Lätet", "—", "ett", "vasst", "pip", "—", "hörs",
# "i", "maj.") is 81 before; GOOD turns the sentence into 7 words (its two spaced dashes were
# their own whitespace-delimited token each, and the commas that replace them attach to the
# word before instead of standing alone), landing at 79 after -- a regression even though the
# rewrite itself passes both check_rewrite and the checker model.
_WORD_COUNT_FILLER = " ".join(["Detta är en testmening."] * 18)  # 72 words
DESCRIPTION_SV_81_WORDS = f"{_WORD_COUNT_FILLER} Lätet — ett vasst pip — hörs i maj."


async def test_a_rewrite_that_would_drop_the_description_under_80_words_is_not_written(
    tmp_path: Path,
) -> None:
    path = write_species(tmp_path, DESCRIPTION_SV_81_WORDS)
    scan = scan_species(path)
    assert len(scan.sentences) == 1
    key = scan.sentences[0].key
    client = FakeJsonClient(
        [
            reply(RewriteReply(rewrites=[Rewrite(id=key, text=GOOD)])),
            reply(CheckReply(verdicts=[Verdict(id=key, same_meaning=True, reason="samma")])),
        ]
    )
    before = path.read_text(encoding="utf-8")
    result = await run_app_dashes(options(tmp_path), client)
    assert path.read_text(encoding="utf-8") == before  # nothing written, not even the rule fixes
    outcome = result.outcomes[0]
    assert not outcome.written
    assert outcome.rewritten == []
    assert outcome.failed == [(scan.sentences[0], _WORD_COUNT_FAIL_REASON)]


async def test_a_rewrite_the_checker_rejects_keeps_its_dash(tmp_path: Path) -> None:
    path = write_species(tmp_path, HEADING_AND_TWO_DASHES)
    key = scan_species(path).sentences[0].key
    client = FakeJsonClient(
        [
            reply(RewriteReply(rewrites=[Rewrite(id=key, text=GOOD)])),
            reply(CheckReply(verdicts=[Verdict(id=key, same_meaning=False, reason="pip saknas")])),
        ]
    )
    result = await run_app_dashes(options(tmp_path), client)
    text = yaml.safe_load(path.read_text(encoding="utf-8"))["description"]["sv"]
    assert "Lätet — ett vasst pip — hörs i maj." in text
    assert "12 till 14 cm" in text  # the rule fixes are still written
    assert result.outcomes[0].failed[0][1] == "kontrollen: pip saknas"


async def test_a_rewrite_failing_code_checks_skips_the_checker(tmp_path: Path) -> None:
    path = write_species(tmp_path, HEADING_AND_TWO_DASHES)
    key = scan_species(path).sentences[0].key
    client = FakeJsonClient(
        [reply(RewriteReply(rewrites=[Rewrite(id=key, text="Lätet hörs i maj 2026.")]))]
    )
    result = await run_app_dashes(options(tmp_path), client)
    assert len(client.calls) == 1
    assert "talen skiljer sig" in result.outcomes[0].failed[0][1]


async def test_a_dry_run_writes_nothing_and_calls_no_model(tmp_path: Path) -> None:
    path = write_species(tmp_path, HEADING_AND_TWO_DASHES)
    before = path.read_text(encoding="utf-8")
    result = await run_app_dashes(options(tmp_path, dry_run=True), None)
    assert path.read_text(encoding="utf-8") == before
    assert result.outcomes[0].rule_fixes == 2
    assert result.outcomes[0].failed[0][1] == "torrkörning"
    assert not result.outcomes[0].written


async def test_the_cost_cap_stops_the_run_without_writing(tmp_path: Path) -> None:
    path = write_species(tmp_path, HEADING_AND_TWO_DASHES)
    before = path.read_text(encoding="utf-8")
    key = scan_species(path).sentences[0].key
    client = FakeJsonClient([reply(RewriteReply(rewrites=[Rewrite(id=key, text=GOOD)]))])
    result = await run_app_dashes(options(tmp_path, max_cost=0.0001), client)
    assert result.stopped_by_cost
    assert path.read_text(encoding="utf-8") == before


async def test_species_without_dashes_are_left_alone(tmp_path: Path) -> None:
    path = write_species(tmp_path, "Inga streck här.")
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    data["migration"] = {"sv": "Flyttar i mars."}
    dump_species(data, path)
    result = await run_app_dashes(options(tmp_path), FakeJsonClient([]))
    assert result.outcomes == []


async def test_an_error_in_one_species_does_not_stop_the_run(tmp_path: Path) -> None:
    path = write_species(tmp_path, HEADING_AND_TWO_DASHES)
    before = path.read_text(encoding="utf-8")
    result = await run_app_dashes(options(tmp_path), FakeJsonClient([]))  # the first call raises
    assert path.read_text(encoding="utf-8") == before
    assert result.outcomes[0].failed[0][1].startswith("fel: IndexError")
    assert "fel: IndexError" in result.report.read_text(encoding="utf-8")
