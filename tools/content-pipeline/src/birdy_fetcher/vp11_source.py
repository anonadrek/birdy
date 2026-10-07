"""BirdLife Sverige's Western Palearctic list (VP11.pdf), fetched at run time.

The PDF carries no licence, so it is not part of the public repo. ``init`` downloads it
from BirdLife Sverige's own site into the gitignored ``.cache/sources/`` folder and checks
it against a pinned SHA-256, so a changed or truncated file never feeds the species list.
"""

from __future__ import annotations

import hashlib
import os
import urllib.request
from collections.abc import Callable
from pathlib import Path

#: Public page that lists the file: https://birdlife.se/tk/vastpalearktislistan/
VP11_PAGE_URL = "https://birdlife.se/tk/vastpalearktislistan/"
VP11_URL = "https://cdn.birdlife.se/wp-content/uploads/2025/06/VP11.pdf"
#: SHA-256 of version 11 (June 2025, 260 771 bytes), the file species_list.yaml was built from.
VP11_SHA256 = "bf73ba1365224f077fd3d54dff06f3f8d12154156ca544eec48ca1bb757778a2"
VP11_FILENAME = "vp11.pdf"

_USER_AGENT = "birdy-fetcher/0.1.0 (https://github.com/anonadrek/birdy)"
_TIMEOUT_S = 60


class Vp11UnavailableError(RuntimeError):
    """The VP11 PDF is neither cached nor downloadable, or its checksum is wrong."""


def vp11_cache_path(cache_root: Path) -> Path:
    """Where the downloaded PDF lives (``<pipeline>/.cache/sources/vp11.pdf``)."""
    return cache_root / "sources" / VP11_FILENAME


def sha256_of(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _http_get(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": _USER_AGENT})
    with urllib.request.urlopen(request, timeout=_TIMEOUT_S) as response:
        data: bytes = response.read()
    return data


def _manual_hint(target: Path, expected_sha256: str) -> str:
    return (
        f"Download VP11.pdf by hand from {VP11_PAGE_URL} ({VP11_URL}) and save it as "
        f"{target} (expected SHA-256 {expected_sha256})."
    )


def ensure_vp11(
    cache_root: Path,
    *,
    url: str = VP11_URL,
    expected_sha256: str = VP11_SHA256,
    fetch: Callable[[str], bytes] = _http_get,
) -> Path:
    """Return the path to a verified VP11.pdf, downloading it once if it is missing.

    Raises ``Vp11UnavailableError`` with a hint for a manual download when the cached file has the
    wrong checksum, the download fails, or the downloaded bytes do not match the pin.
    """
    target = vp11_cache_path(cache_root)
    if target.exists():
        actual = sha256_of(target)
        if actual != expected_sha256:
            raise Vp11UnavailableError(
                f"{target} has SHA-256 {actual}, expected {expected_sha256}. Delete it and run "
                f"again, or: {_manual_hint(target, expected_sha256)}"
            )
        return target

    try:
        data = fetch(url)
    except OSError as e:  # URLError, HTTPError, timeouts and socket errors are all OSError
        raise Vp11UnavailableError(
            f"Could not download {url}: {e}. {_manual_hint(target, expected_sha256)}"
        ) from e

    actual = hashlib.sha256(data).hexdigest()
    if actual != expected_sha256:
        raise Vp11UnavailableError(
            f"Downloaded {url} has SHA-256 {actual}, expected {expected_sha256}. BirdLife "
            "Sverige may have replaced the file with a new version; check the list before "
            f"updating VP11_SHA256 in {Path(__file__).name}."
        )

    target.parent.mkdir(parents=True, exist_ok=True)
    tmp = target.with_suffix(target.suffix + ".tmp")
    tmp.write_bytes(data)
    os.replace(tmp, target)
    return target
