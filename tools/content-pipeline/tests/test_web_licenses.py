"""Tests for web/licenses.py."""

from __future__ import annotations

import pytest

from birdy_fetcher.web.licenses import (
    LICENSE_URLS,
    UnknownLicenseError,
    clean_author,
    commons_url,
    license_url,
)


def test_all_eight_licenses_in_the_data_are_known() -> None:
    assert set(LICENSE_URLS) == {
        "CC0",
        "Public domain",
        "CC BY 2.0",
        "CC BY 3.0",
        "CC BY 4.0",
        "CC BY-SA 2.0",
        "CC BY-SA 3.0",
        "CC BY-SA 4.0",
    }


def test_license_url() -> None:
    assert license_url("CC BY-SA 4.0") == "https://creativecommons.org/licenses/by-sa/4.0/"
    assert license_url("CC BY 2.0") == "https://creativecommons.org/licenses/by/2.0/"
    assert license_url("CC0") is None
    assert license_url("Public domain") is None


def test_unknown_license_is_an_error() -> None:
    with pytest.raises(UnknownLicenseError):
        license_url("All rights reserved")


def test_clean_author_strips_commons_html() -> None:
    raw = (
        '<div class="fn value">\n<a href="//commons.wikimedia.org/wiki/User:Archaeodontosaurus" '
        'title="User:Archaeodontosaurus">Didier\n    Descouens</a></div>'
    )
    assert clean_author(raw) == "Didier Descouens"


def test_clean_author_decodes_entities_and_handles_empty() -> None:
    assert clean_author("J&ouml;rg &amp; Anna") == "Jörg & Anna"
    assert clean_author("") is None
    assert clean_author(None) is None
    assert clean_author("<span> </span>") is None


def test_clean_author_strips_commons_namespace_prefix() -> None:
    assert clean_author('<a href="x">Template:Kjetil Hansen</a>') == "Kjetil Hansen"
    assert clean_author("User:Ann") == "Ann"
    assert clean_author("Template:") is None
    assert clean_author("User:") is None


def test_commons_url_replaces_spaces() -> None:
    url = (
        "https://commons.wikimedia.org/wiki/File:Great tit (Parus major), "
        "North Rhine-Westphalia.jpg"
    )
    assert commons_url(url) == (
        "https://commons.wikimedia.org/wiki/File:Great_tit_(Parus_major),_"
        "North_Rhine-Westphalia.jpg"
    )
