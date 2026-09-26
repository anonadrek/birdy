"""Photo licences, photographer names and Commons links for the credits (spec §8)."""

from __future__ import annotations

from html.parser import HTMLParser

LICENSE_URLS: dict[str, str | None] = {
    "CC0": None,
    "Public domain": None,
    "CC BY 2.0": "https://creativecommons.org/licenses/by/2.0/",
    "CC BY 3.0": "https://creativecommons.org/licenses/by/3.0/",
    "CC BY 4.0": "https://creativecommons.org/licenses/by/4.0/",
    "CC BY-SA 2.0": "https://creativecommons.org/licenses/by-sa/2.0/",
    "CC BY-SA 3.0": "https://creativecommons.org/licenses/by-sa/3.0/",
    "CC BY-SA 4.0": "https://creativecommons.org/licenses/by-sa/4.0/",
}


class UnknownLicenseError(ValueError):
    pass


def license_url(license_id: str) -> str | None:
    if license_id not in LICENSE_URLS:
        raise UnknownLicenseError(f"Okänd bildlicens: {license_id!r}")
    return LICENSE_URLS[license_id]


class _TextCollector(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []

    def handle_data(self, data: str) -> None:
        self.parts.append(data)


_NAMESPACE_PREFIXES = ("Template:", "User:")


def clean_author(raw: str | None) -> str | None:
    """Commons author fields are HTML; the site shows plain text."""
    if not raw:
        return None
    parser = _TextCollector()
    parser.feed(raw)
    parser.close()
    text = " ".join("".join(parser.parts).split())
    for prefix in _NAMESPACE_PREFIXES:
        if text.startswith(prefix):
            text = text.removeprefix(prefix).strip()
            break
    return text or None


def commons_url(source_url: str) -> str:
    return source_url.strip().replace(" ", "_")
