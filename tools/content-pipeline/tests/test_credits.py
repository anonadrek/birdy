"""Licence allow-list and photographer-credit cleaning for the app's photos."""

from __future__ import annotations

import re
from pathlib import Path

import pytest

from birdy_fetcher.credits import (
    ALLOWED_LICENSES,
    canonical_license,
    clean_author,
    is_license_allowed,
    is_usable_author,
    requires_attribution,
)


@pytest.mark.parametrize(
    "license_name",
    [
        "CC0",
        "Public domain",
        "CC BY 2.0",
        "CC BY 3.0",
        "CC BY 4.0",
        "CC BY-SA 2.0",
        "CC BY-SA 3.0",
        "CC BY-SA 4.0",
    ],
)
def test_allow_list_accepts_exactly_the_eight_licences(license_name: str) -> None:
    assert is_license_allowed(license_name)
    assert is_license_allowed(license_name.lower())
    assert license_name in ALLOWED_LICENSES
    assert len(ALLOWED_LICENSES) == 8


@pytest.mark.parametrize(
    "license_name",
    [
        "CC BY-NC 2.0",
        "CC BY-NC-SA 4.0",
        "CC BY-ND 4.0",
        "GFDL",
        "CC BY-SA 2.5",
        "CC BY 2.5",
        "CC BY-SA 3.0 de",
        "Attribution",
        "",
        None,
    ],
)
def test_allow_list_rejects_everything_else(license_name: str | None) -> None:
    assert not is_license_allowed(license_name)
    assert canonical_license(license_name) is None


def test_attribution_required_only_for_by_and_by_sa() -> None:
    assert requires_attribution("CC BY 4.0")
    assert requires_attribution("CC BY-SA 2.0")
    assert not requires_attribution("CC0")
    assert not requires_attribution("Public domain")
    assert not requires_attribution("CC BY-NC 2.0")


def test_canonical_license_normalises_case_and_spacing() -> None:
    assert canonical_license("cc  by-sa 4.0") == "CC BY-SA 4.0"
    assert canonical_license("public domain") == "Public domain"


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        (
            '<a href="//commons.wikimedia.org/wiki/User:Kyu3a" title="User:Kyu3a">Kyu3</a>',
            "Kyu3",
        ),
        (
            "Hobbyfotowiki Camera location51 deg 10 min 43.32 sec N, "
            "7 deg 00 min 23.65 sec E View this and other nearby images on: "
            "OpenStreetMap 51.178700; 7.006570",
            "Hobbyfotowiki",
        ),
        (
            "No machine-readable author provided. M.Buschmann assumed (based on copyright claims).",
            "M.Buschmann",
        ),
        ("unknown, USFWS", "USFWS"),
        ("JJ Harrison (https://www.jjharrison.com.au/)", "JJ Harrison"),
        ("JJ Harrison (jjharrison89@facebook.com)", "JJ Harrison"),
        (
            "Martin Olsson (mnemo on en/sv wikipedia and commons, martin@minimum.se).",
            "Martin Olsson",
        ),
        ("Julian Herzog (Website)", "Julian Herzog"),
        ("(c) Matt Muir, some rights reserved (CC BY)", "Matt Muir"),
        (
            "Marion Schneider &amp; Christoph Aistleitner Contact:Mediocrity",
            "Marion Schneider & Christoph Aistleitner",
        ),
        (
            "Falco_naumanni_back.jpg: Tim Sträter derivative work: aghith",
            "Tim Sträter, derivative work: aghith",
        ),
        ("Dannymoore1973Minor edits made by Subsidiary account", "Dannymoore1973"),
        ("Raf24~commonswiki", "Raf24"),
        ("user:AndreyA", "AndreyA"),
        ("Rob Hille/Rob Hille", "Rob Hille"),
        ("Jerzystrzelecki:Jerzy Strzelecki", "Jerzy Strzelecki"),
        (
            '<a rel="nofollow" class="external free" '
            'href="https://www.flickr.com/people/flowcomm/">'
            "https://www.flickr.com/people/flowcomm/</a>",
            "flowcomm",
        ),
        (
            '<a rel="nofollow" class="external autonumber" '
            'href="https://www.inaturalist.org/people/mourad-harzallah">[2]</a>',
            "Mourad Harzallah",
        ),
        (
            '<a rel="nofollow" class="external text" '
            'href="https://www.flickr.com/people/37024616@N03">Derek Keats</a> '
            "from Johannesburg, South Africa",
            "Derek Keats",
        ),
        ("Silver Leapers from remote NE Coastline", "Silver Leapers from remote NE Coastline"),
        ("Accipiter (Rainer Altenkamp, Berlin)", "Accipiter (Rainer Altenkamp, Berlin)"),
        ("", None),
        (None, None),
    ],
)
def test_clean_author(raw: str | None, expected: str | None) -> None:
    assert clean_author(raw) == expected


def test_clean_author_is_idempotent_on_clean_names() -> None:
    for name in ["Charles J. Sharp", "Forest & Kim Starr", "Åsa Berndtsson", "מינוזיג - MinoZig"]:
        assert clean_author(name) == name


@pytest.mark.parametrize(
    "text",
    [
        None,
        "",
        "x",
        "unknown",
        "Unknown",
        "no rights reserved",
        "Wikimedia Commons contributor (Public domain)",
        "[2]",
        "28092414@N03",
        "https://www.flickr.com/photos/28092414@N03/",
        "No machine-readable author provided.",
        "Hobbyfotowiki Camera location 51° N",
        "<a href='x'>A</a>",
    ],
)
def test_unusable_authors(text: str | None) -> None:
    assert not is_usable_author(text)


@pytest.mark.parametrize(
    "text", ["Charles J. Sharp", "flowcomm", "USFWS", "Kyu3", "葉子 ( leaf0605)"]
)
def test_usable_authors(text: str) -> None:
    assert is_usable_author(text)


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        # Same person, one spelling (release 1.3.0 review).
        ("blondinrikard", "Blondinrikard Fröberg"),
        ("Saudi press Agency (SPA)", "Saudi Press Agency (SPA)"),
        ("alexis_lours", "Alexis Lours"),
        ("christoph_moning", "Christoph Moning"),
        ("Christoph Moning (iNaturalist user christoph_moning)", "Christoph Moning"),
        ("mourad-harzallah", "Mourad Harzallah"),
        # Unsplash credits end in a link to the uploader's handle.
        (
            'Rodolfo Mari <a rel="nofollow" class="external text" '
            'href="https://unsplash.com/@dolfoto">dolfoto</a>',
            "Rodolfo Mari",
        ),
        (
            'Richard Hoeg <a rel="nofollow" class="external text" '
            'href="https://unsplash.com/@richardhoeg">richardhoeg</a>',
            "Richard Hoeg",
        ),
        # iNaturalist "Name (login)": the login repeats the name.
        ("Kudaibergen Amirekul (amirekul)", "Kudaibergen Amirekul"),
        ("Olivier Morel (olivier_morel)", "Olivier Morel"),
        ("James M. Maley (jmaley)", "James M. Maley"),
        # A parenthesis that is part of the credit stays.
        ("Sun Jiao (Interaccoonale)", "Sun Jiao (Interaccoonale)"),
        ("Duncan Brown (Cradlehall)", "Duncan Brown (Cradlehall)"),
        ("Le et al. (2024)", "Le et al. (2024)"),
        ("Saudi Press Agency (SPA)", "Saudi Press Agency (SPA)"),
    ],
)
def test_clean_author_uses_one_spelling_per_photographer(raw: str, expected: str) -> None:
    assert clean_author(raw) == expected


def test_allow_list_is_the_web_pipelines_licence_table() -> None:
    from birdy_fetcher.credits import PUBLIC_DOMAIN_LICENSES
    from birdy_fetcher.web.licenses import LICENSE_URLS

    assert set(LICENSE_URLS) == ALLOWED_LICENSES
    assert {k for k, url in LICENSE_URLS.items() if url is None} == PUBLIC_DOMAIN_LICENSES


KOTLIN_VALIDATOR = (
    Path(__file__).resolve().parents[3]
    / "shared/content/src/jvmMain/kotlin/se/birdy/content/build/SpeciesValidator.kt"
)


def _kotlin_set(source: str, name: str) -> set[str]:
    m = re.search(rf"val {name}\s*=\s*(.*?)\n\s*(?:private )?val ", source, re.DOTALL)
    assert m, f"{name} not found in SpeciesValidator.kt"
    return set(re.findall(r'"([^"]+)"', m.group(1)))


@pytest.mark.skipif(not KOTLIN_VALIDATOR.exists(), reason="needs the Birdy repo checkout")
def test_kotlin_validator_has_the_same_allow_list() -> None:
    from birdy_fetcher.credits import PUBLIC_DOMAIN_LICENSES

    source = KOTLIN_VALIDATOR.read_text(encoding="utf-8")
    assert _kotlin_set(source, "PUBLIC_DOMAIN_LICENSES") == PUBLIC_DOMAIN_LICENSES
    assert _kotlin_set(source, "ALLOWED_LICENSES") | PUBLIC_DOMAIN_LICENSES == ALLOWED_LICENSES


KOTLIN_APP_LICENSES = (
    Path(__file__).resolve().parents[3]
    / "shared/content/src/commonMain/kotlin/se/birdy/content/PhotoLicenses.kt"
)


@pytest.mark.skipif(not KOTLIN_APP_LICENSES.exists(), reason="needs the Birdy repo checkout")
def test_the_apps_licence_links_match_the_web_pipelines() -> None:
    """The app's photo credits (release 1.3.0, Task 7e-2) link the same deeds as the web pages.

    The app also links CC0's deed, which the web credits leave unlinked; public domain stays
    unlinked in both.
    """
    from birdy_fetcher.web.licenses import LICENSE_URLS

    source = KOTLIN_APP_LICENSES.read_text(encoding="utf-8")
    block = re.search(r"val DEED_URLS.*?mapOf\((.*?)\n\s*\)", source, re.DOTALL)
    assert block, "DEED_URLS not found in PhotoLicenses.kt"
    # Keys are string literals or `const val` names in the same object (PUBLIC_DOMAIN).
    constants = dict(re.findall(r'const val (\w+) = "([^"]+)"', source))
    kotlin = {
        (constants[ident] if ident else literal): (None if url == "null" else url.strip('"'))
        for literal, ident, url in re.findall(
            r'(?:"([^"]+)"|\b([A-Z_]+)\b) to ("[^"]+"|null)', block.group(1)
        )
    }
    assert set(kotlin) == set(LICENSE_URLS)
    for name, url in LICENSE_URLS.items():
        if url is not None:
            assert kotlin[name] == url, name
    assert kotlin["Public domain"] is None
    assert kotlin["CC0"] == "https://creativecommons.org/publicdomain/zero/1.0/"
