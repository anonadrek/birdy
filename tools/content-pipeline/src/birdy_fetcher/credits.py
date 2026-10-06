"""Licences and photographer credits for the app's species photos.

Commons hands us the photographer as HTML (``Artist`` in ``extmetadata``),
sometimes with geotag widgets, boilerplate or a bare profile URL in it. The
app shows the credit as plain text, so everything that ends up in a species
YAML goes through :func:`clean_author` first, and a photo whose licence
requires attribution is only used when the cleaned name is usable.
"""

from __future__ import annotations

import re
from urllib.parse import urlparse

from .web.licenses import clean_author as _html_to_text

# Hard allow-list (release 1.3.0): only these licences may ship in the app.
# NonCommercial, NoDerivatives, GFDL-only, ported/odd CC versions (2.5, 3.0 de,
# …) and anything unknown are rejected.
PUBLIC_DOMAIN_LICENSES: frozenset[str] = frozenset({"CC0", "Public domain"})
ATTRIBUTION_LICENSES: frozenset[str] = frozenset(
    {
        "CC BY 2.0",
        "CC BY 3.0",
        "CC BY 4.0",
        "CC BY-SA 2.0",
        "CC BY-SA 3.0",
        "CC BY-SA 4.0",
    }
)
ALLOWED_LICENSES: frozenset[str] = PUBLIC_DOMAIN_LICENSES | ATTRIBUTION_LICENSES

_CANONICAL = {name.lower(): name for name in ALLOWED_LICENSES}


def canonical_license(raw: str | None) -> str | None:
    """The allow-listed spelling of ``raw`` (case-insensitive), or None."""
    if not raw:
        return None
    return _CANONICAL.get(" ".join(raw.split()).lower())


def is_license_allowed(raw: str | None) -> bool:
    return canonical_license(raw) is not None


def requires_attribution(raw: str | None) -> bool:
    return canonical_license(raw) in ATTRIBUTION_LICENSES


_HREF = re.compile(r"""href\s*=\s*["']([^"']+)["']""", re.IGNORECASE)
_PROFILE_HOSTS = ("flickr.com", "inaturalist.org")
_GEO_WIDGET = re.compile(r"\s*Camera location.*$", re.IGNORECASE | re.DOTALL)
_ASSUMED = re.compile(
    r"^No machine-readable author provided\.\s*(.+?)\s+assumed\b.*$", re.IGNORECASE
)
_COPYRIGHT = re.compile(r"^\(c\)\s*(.+?),\s*some rights reserved.*$", re.IGNORECASE)
_CONTACT = re.compile(r"\s*Contact:\S.*$")
_MINOR_EDITS = re.compile(r"Minor edits made by.*$", re.IGNORECASE)
_DERIVATIVE = re.compile(
    r"^\S+\.(?:jpe?g|png|tiff?):\s*(.+?)\s+derivative work:\s*(.+)$", re.IGNORECASE
)
# "(https://…)", "(name@mail…)", "(mnemo on en/sv wikipedia …, martin@minimum.se)."
_LINK_PAREN = re.compile(r"\s*\((?:[^)]*https?://[^)]*|[^)]*@[^)]*|Website)\)\.?", re.IGNORECASE)
_UNKNOWN_PREFIX = re.compile(r"^unknown,\s*", re.IGNORECASE)
_USERNAME_COLON_NAME = re.compile(r"^([^\s:]+):(\S.*\s.*)$")
_WIKI_SUFFIX = re.compile(r"~\w+wiki$")
_USER_PREFIX = re.compile(r"^user:\s*", re.IGNORECASE)
_FOOTNOTE = re.compile(r"^\[\d+\]$")
_FLICKR_FROM = re.compile(
    r"""^\s*(<a\s[^>]*flickr\.com/(?:people|photos)/[^>]*>.*?</a>)\s+from\s+\S""",
    re.IGNORECASE | re.DOTALL,
)


def _profile_name(raw: str) -> str | None:
    """Last path segment of a Flickr/iNaturalist profile link in ``raw``."""
    for href in _HREF.findall(raw):
        url = href if "//" in href else f"https://{href}"
        parsed = urlparse(url if not url.startswith("//") else f"https:{url}")
        if not any(parsed.netloc.endswith(h) for h in _PROFILE_HOSTS):
            continue
        parts = [p for p in parsed.path.split("/") if p]
        if len(parts) >= 2 and parts[0] in ("people", "photos"):
            return parts[1]
    return None


def clean_author(raw: str | None) -> str | None:
    """Plain-text photographer credit, or None when nothing usable is left."""
    if not raw:
        return None
    # Commons' {{Flickr}} credit reads "<a …flickr…>Name</a> from Place": the
    # place is the uploader's profile location, not part of the name.
    if m := _FLICKR_FROM.search(raw):
        raw = m.group(1)
    text = _html_to_text(raw)
    if not text:
        return None
    text = _GEO_WIDGET.sub("", text)
    text = _MINOR_EDITS.sub("", text)
    text = _CONTACT.sub("", text)
    if m := _ASSUMED.match(text):
        text = m.group(1)
    if m := _COPYRIGHT.match(text):
        text = m.group(1)
    if m := _DERIVATIVE.match(text):
        text = f"{m.group(1)}, derivative work: {m.group(2)}"
    text = _LINK_PAREN.sub("", text)
    text = _UNKNOWN_PREFIX.sub("", text)
    text = _USER_PREFIX.sub("", text)
    text = _WIKI_SUFFIX.sub("", text)
    if m := _USERNAME_COLON_NAME.match(text):
        text = m.group(2)
    if text.startswith(("http://", "https://")) or _FOOTNOTE.match(text):
        text = _profile_name(raw) or text
    halves = text.split("/")
    if len(halves) == 2 and halves[0].strip() == halves[1].strip():
        text = halves[0]
    text = " ".join(text.split()).strip(" ,;")
    return text or None


_JUNK = re.compile(
    r"(?i)^(?:unknown|anonymous|unbekannt|okänd|n/?a|none|own work|"
    r"no rights reserved|wikimedia commons contributor.*|\[\d+\])$"
)
_JUNK_FRAGMENTS = re.compile(
    r"(?i)machine-readable|camera location|openstreetmap|https?://|www\.|<|>|@N\d\d"
)


def is_usable_author(text: str | None) -> bool:
    """True when ``text`` (already cleaned) names someone we can credit."""
    if not text or len(text) < 2 or len(text) > 120:
        return False
    return not (_JUNK.match(text) or _JUNK_FRAGMENTS.search(text))
