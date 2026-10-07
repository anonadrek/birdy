"""Tests for web/scinames.py: a look-alike's name as an article writes it, matched to a QID."""

from __future__ import annotations

from birdy_fetcher.web.scinames import NameContext, Resolution, resolve_lookalike

# Birdy names Corvus corone "Kråka" in Swedish (the article says svartkråka): the match is
# by scientific name only, the common names are only evidence for a guessed genus.
INDEX = {
    "corvus frugilegus": "Q25386",
    "corvus corone": "Q26198",
    "corvus cornix": "Q27630",
    "corvus corax": "Q43365",
    "corvus macrorhynchos": "Q649000",
    "coloeus monedula": "Q25407",
    "nucifraga caryocatactes": "Q184820",
    "parus major": "Q25485",
    "cyanistes caeruleus": "Q25404",
    "cyanistes teneriffae": "Q10546857",
    "elanus caeruleus": "Q649002",
    "phylloscopus collybita": "Q25529",
    "pyrrhula major": "Q99",
    "motacilla flava": "Q25984",
    "anas crecca": "Q25700",
    "iduna caligata": "Q27011",
    "mareca americana": "Q26003",
    "aythya ferina": "Q26742",
    "melanitta nigra": "Q26002",
}
FAMILIES = {
    "corvus frugilegus": "Corvidae",
    "corvus corone": "Corvidae",
    "corvus cornix": "Corvidae",
    "corvus corax": "Corvidae",
    "corvus macrorhynchos": "Corvidae",
    "coloeus monedula": "Corvidae",
    "nucifraga caryocatactes": "Corvidae",
    "parus major": "Paridae",
    "cyanistes caeruleus": "Paridae",
    "cyanistes teneriffae": "Paridae",
    "elanus caeruleus": "Accipitridae",
    "phylloscopus collybita": "Phylloscopidae",
    "pyrrhula major": "Fringillidae",
    "motacilla flava": "Motacillidae",
    "anas crecca": "Anatidae",
    "iduna caligata": "Acrocephalidae",
    "mareca americana": "Anatidae",
    "aythya ferina": "Anatidae",
    "melanitta nigra": "Anatidae",
}
COMMON: dict[str, tuple[str, ...]] = {
    "cyanistes caeruleus": ("Blåmes", "Eurasian Blue Tit"),
    "parus major": ("Talgoxe", "Great Tit"),
    "pyrrhula major": ("Större domherre", "Large Bullfinch"),
    "mareca americana": ("Amerikansk bläsand", "American Wigeon"),
    "coloeus monedula": ("Kaja", "Western Jackdaw"),
}


def _ctx(subject: str | None, own_qid: str | None = None) -> NameContext:
    return NameContext(
        index=INDEX, families=FAMILIES, common=COMMON, subject=subject, own_qid=own_qid
    )


def _found(resolution: Resolution) -> tuple[str | None, str | None]:
    return resolution.binomial, resolution.qid


def test_a_full_binomial_matches() -> None:
    assert _found(resolve_lookalike("Corvus corone", _ctx(None))) == ("Corvus corone", "Q26198")


def test_rakas_abbreviated_lookalike_is_the_carrion_crow() -> None:
    """R3 (2026-10-07): Råka's article writes "C. corone"; the page's own genus expands it."""
    ctx = _ctx("Corvus frugilegus", "Q25386")
    assert _found(resolve_lookalike("C. corone", ctx)) == ("Corvus corone", "Q26198")
    assert _found(resolve_lookalike("C. corone corone", ctx)) == ("Corvus corone", "Q26198")


def test_a_subspecies_split_off_as_a_species_is_that_species() -> None:
    """Corvus corone cornix is today's Corvus cornix (Gråkråka), not Kråka."""
    ctx = _ctx("Corvus frugilegus", "Q25386")
    assert _found(resolve_lookalike("Corvus corone cornix", ctx)) == ("Corvus cornix", "Q27630")
    assert _found(resolve_lookalike("C. c. cornix", ctx)) == ("Corvus cornix", "Q27630")


def test_an_author_after_the_name_is_not_a_subspecies() -> None:
    found = resolve_lookalike("Corvus corone Linnaeus, 1758", _ctx(None))
    assert _found(found) == ("Corvus corone", "Q26198")


def test_a_lookalike_is_never_the_page_species_itself() -> None:
    """Fix wave 2026-10-07: "Motacilla flava thunbergi" on Gulärla's page is a subspecies of
    Gulärla, "Anas crecca carolinensis" on Kricka's another of Kricka, and "Hippolais
    caligata" on Iduna caligata's page its own older name. None of them links."""
    for written, subject, qid in (
        ("Motacilla flava thunbergi", "Motacilla flava", "Q25984"),
        ("Anas crecca carolinensis", "Anas crecca", "Q25700"),
        ("Hippolais caligata", "Iduna caligata", "Q27011"),
        ("Corvus corone", "Corvus corone", "Q26198"),
    ):
        found = resolve_lookalike(written, _ctx(subject, qid), evidence="")
        assert _found(found) == (None, None), written
        assert found.note is not None
        assert "arten själv" in found.note


def test_the_family_rule_is_not_used_for_a_subspecies() -> None:
    """Nötkråka's "Nucifraga c. macrorhynchos" (a nutcracker subspecies) became Corvus
    macrorhynchos through the family rule. A subspecies only counts in its written genus."""
    ctx = _ctx("Nucifraga caryocatactes", "Q184820")
    found = resolve_lookalike("Nucifraga c. macrorhynchos", ctx, evidence="tjocknäbbad")
    assert _found(found) == (None, None)
    found = resolve_lookalike("Nucifraga caryocatactes macrorhynchos", ctx)
    assert _found(found) == (None, None)


def test_an_abbreviation_that_fits_the_page_genus_means_only_that_genus() -> None:
    """On Råka's page "C. monedula" is not looked for in other genera that begin with C
    (Coloeus monedula): an abbreviation names the genus the article just wrote."""
    ctx = _ctx("Corvus frugilegus", "Q25386")
    assert _found(resolve_lookalike("C. monedula", ctx, evidence="kaja")) == (None, None)


def test_an_abbreviation_for_another_genus_needs_the_name_in_the_fact() -> None:
    """On Blåmes's page "P. major" fits Parus and Pyrrhula: the one whose Swedish or English
    name the fact or its quotes give is taken, and nothing without that."""
    ctx = _ctx("Cyanistes caeruleus", "Q25404")
    found = resolve_lookalike("P. major", ctx, evidence="Blåmesen är mindre än talgoxen.")
    assert _found(found) == ("Parus major", "Q25485")
    found = resolve_lookalike("P. major", ctx, evidence="Blåmesen är mindre.")
    assert _found(found) == (None, None)
    assert found.note is not None
    found = resolve_lookalike("Ph. collybita", _ctx("Parus major", "Q25485"), evidence="")
    assert _found(found) == (None, None)


def test_an_older_genus_needs_the_name_in_the_fact() -> None:
    """Birdy's list has no synonyms: the epithet decides inside the page species' family,
    but only when the fact or a quote names that species (Swedish or English)."""
    ctx = _ctx("Parus major", "Q25485")
    found = resolve_lookalike("Parus caeruleus", ctx, evidence="Kan förväxlas med blåmesen.")
    assert _found(found) == ("Cyanistes caeruleus", "Q25404")
    found = resolve_lookalike("Parus caeruleus", ctx, evidence="the blue tit is smaller")
    assert _found(found) == ("Cyanistes caeruleus", "Q25404")  # "Eurasian" may be left out
    found = resolve_lookalike("Parus caeruleus", ctx, evidence="Kan förväxlas.")
    assert _found(found) == (None, None)
    assert found.note is not None
    assert "Blåmes" in found.note


def test_a_different_bird_with_the_same_epithet_is_not_linked() -> None:
    """Brunand's "Aythya americana" (Redhead) and Sjöorre's "Melanitta americana" (Black
    Scoter) both became Mareca americana (American Wigeon) through the family rule."""
    for subject, qid, written, evidence in (
        (
            "Aythya ferina",
            "Q26742",
            "Aythya americana",
            "Liknar den amerikanska rödhuvade dykanden.",
        ),
        ("Melanitta nigra", "Q26002", "Melanitta americana", "the Black Scoter of North America"),
    ):
        found = resolve_lookalike(written, _ctx(subject, qid), evidence=evidence)
        assert _found(found) == (None, None), written
        assert found.note is not None


def test_a_species_birdy_does_not_have_is_none() -> None:
    ctx = _ctx("Corvus corax", "Q43365")
    assert _found(resolve_lookalike("Corvus brachyrhynchos", ctx)) == (None, None)
    assert _found(resolve_lookalike("C. brachyrhynchos", ctx)) == (None, None)
    assert _found(resolve_lookalike("Corvus", ctx)) == (None, None)
    assert _found(resolve_lookalike("", ctx)) == (None, None)
