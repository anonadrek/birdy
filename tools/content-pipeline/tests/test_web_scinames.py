"""Tests for web/scinames.py: a look-alike's name as an article writes it, matched to a QID."""

from __future__ import annotations

from birdy_fetcher.web.scinames import resolve_lookalike

# Birdy names Corvus corone "Kråka" in Swedish (the article says svartkråka): the match is
# by scientific name only.
INDEX = {
    "corvus frugilegus": "Q25386",
    "corvus corone": "Q26198",
    "corvus cornix": "Q27630",
    "corvus corax": "Q43365",
    "coloeus monedula": "Q25407",
    "parus major": "Q25485",
    "cyanistes caeruleus": "Q25404",
    "cyanistes teneriffae": "Q10546857",
    "elanus caeruleus": "Q649002",
    "phylloscopus collybita": "Q25529",
    "pyrrhula major": "Q99",
    "cinclus caeruleus": "Q98",
}
FAMILIES = {
    "corvus frugilegus": "Corvidae",
    "corvus corone": "Corvidae",
    "corvus cornix": "Corvidae",
    "corvus corax": "Corvidae",
    "coloeus monedula": "Corvidae",
    "parus major": "Paridae",
    "cyanistes caeruleus": "Paridae",
    "cyanistes teneriffae": "Paridae",
    "elanus caeruleus": "Accipitridae",
    "phylloscopus collybita": "Phylloscopidae",
    "pyrrhula major": "Fringillidae",
    "cinclus caeruleus": "Cinclidae",
}


def test_a_full_binomial_matches() -> None:
    assert resolve_lookalike("Corvus corone", INDEX) == ("Corvus corone", "Q26198")


def test_rakas_abbreviated_lookalike_is_the_carrion_crow() -> None:
    """R3 (2026-10-07): Råka's article writes "C. corone"; the page's own genus expands it."""
    assert resolve_lookalike("C. corone", INDEX, subject="Corvus frugilegus") == (
        "Corvus corone",
        "Q26198",
    )


def test_a_subspecies_is_its_species() -> None:
    assert resolve_lookalike("C. corone corone", INDEX, subject="Corvus frugilegus") == (
        "Corvus corone",
        "Q26198",
    )
    assert resolve_lookalike("Corvus corone corone", INDEX) == ("Corvus corone", "Q26198")


def test_a_subspecies_split_off_as_a_species_is_that_species() -> None:
    """Corvus corone cornix is today's Corvus cornix (Gråkråka), not Kråka."""
    assert resolve_lookalike("Corvus corone cornix", INDEX) == ("Corvus cornix", "Q27630")
    assert resolve_lookalike("C. c. cornix", INDEX, subject="Corvus frugilegus") == (
        "Corvus cornix",
        "Q27630",
    )


def test_an_author_after_the_name_is_not_a_subspecies() -> None:
    assert resolve_lookalike("Corvus corone Linnaeus, 1758", INDEX) == ("Corvus corone", "Q26198")


def test_an_abbreviation_with_one_fitting_genus_needs_no_subject() -> None:
    assert resolve_lookalike("Ph. collybita", INDEX) == ("Phylloscopus collybita", "Q25529")
    assert resolve_lookalike("C. monedula", INDEX) == ("Coloeus monedula", "Q25407")


def test_an_ambiguous_abbreviation_takes_the_page_species_genus() -> None:
    # "C. caeruleus" fits Cyanistes and Cinclus (Elanus starts with E): the page's genus.
    assert resolve_lookalike("C. caeruleus", INDEX, subject="Cyanistes teneriffae") == (
        "Cyanistes caeruleus",
        "Q25404",
    )
    assert resolve_lookalike("C. caeruleus", INDEX) is None
    # "P. major" fits Parus and Pyrrhula: neither is the page's genus (Cyanistes), so the
    # articles decide.
    assert resolve_lookalike("P. major", INDEX, subject="Cyanistes caeruleus") is None
    assert resolve_lookalike(
        "P. major",
        INDEX,
        subject="Cyanistes caeruleus",
        context="Blåmesen är mindre än talgoxen (Parus major).",
    ) == ("Parus major", "Q25485")


def test_an_older_genus_matches_in_the_page_species_family() -> None:
    """No synonyms in Birdy's list: the epithet decides, inside the page species' family."""
    found = resolve_lookalike("Parus caeruleus", INDEX, subject="Parus major", families=FAMILIES)
    assert found == ("Cyanistes caeruleus", "Q25404")
    # Outside the family the same epithet is a different bird (Elanus caeruleus), so no
    # guess.
    assert (
        resolve_lookalike("Accipiter caeruleus", INDEX, subject="Corvus corax", families=FAMILIES)
        is None
    )
    # Without family data the older genus is not guessed at all.
    assert resolve_lookalike("Parus caeruleus", INDEX, subject="Parus major") is None


def test_a_species_birdy_does_not_have_is_none() -> None:
    assert resolve_lookalike("Corvus brachyrhynchos", INDEX, subject="Corvus corax") is None
    assert resolve_lookalike("C. brachyrhynchos", INDEX, subject="Corvus corax") is None
    assert resolve_lookalike("Corvus", INDEX) is None
    assert resolve_lookalike("", INDEX) is None
