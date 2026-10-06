"""Licence allow-list and photographer-credit cleaning for the app's photos."""

from __future__ import annotations

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
            "mourad-harzallah",
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
