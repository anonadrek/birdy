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


def test_an_author_never_carries_a_dash_the_site_refuses() -> None:
    """I7: fas 2's dash guard reads the credits."""
    assert clean_author("<a>Anna</a> \u2014 xeno-canto") == "Anna, xeno-canto"


# Re-review 2026-10-07: credits as they stood on data/artsidor after R2.
def test_an_unknown_author_template_is_no_author() -> None:
    """Skäggdopping's CC BY 4.0 recording: Commons' {{Unknown|author}} rendered twice. As
    None, the CC BY rule rejects the file and the next allowed recording is used."""
    assert clean_author("Unknown authorUnknown author") is None
    assert clean_author("Unknown author") is None
    assert clean_author("<span>Unknown author</span> / Okänd upphovsman") is None
    assert clean_author("Anonymous") is None
    assert clean_author("Unknown Pleasures Records") == "Unknown Pleasures Records"


def test_an_author_assumed_from_copyright_claims_is_unwrapped() -> None:
    raw = "No machine-readable author provided. Mdf assumed (based on copyright claims)."
    assert clean_author(raw) == "Mdf"


def test_a_signature_suffix_is_stripped() -> None:
    assert clean_author("Rabe19 (Diskussion)") == "Rabe19"
    assert clean_author("Anna Svensson (talk)") == "Anna Svensson"
    assert clean_author("Rabe19 (Diskussion · Beiträge)") == "Rabe19"
    assert clean_author("Kim (talk · contribs)") == "Kim"


def test_a_name_is_taken_from_created_by_user_at_a_site() -> None:
    raw = (
        "This image is created by user Justin Jansen at Waarneming.nl, a source of nature "
        "observations in the Netherlands."
    )
    assert clean_author(raw) == "Justin Jansen"
    # Unsure what the name is: no name rather than a sentence as the credit.
    assert clean_author("This file was created by the team of a large project at night.") is None
