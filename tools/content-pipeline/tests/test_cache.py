"""Cache atomicity + key construction tests."""

from __future__ import annotations

from pathlib import Path

from birdy_fetcher.cache import Cache


def test_cache_write_then_read_round_trip(tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    cache.put("Q25485", "wikidata.json", '{"foo": "bar"}')
    assert cache.has("Q25485", "wikidata.json")
    assert cache.get("Q25485", "wikidata.json") == '{"foo": "bar"}'


def test_cache_write_is_atomic(tmp_path: Path) -> None:
    """A write must not leave partial files even if interrupted."""
    cache = Cache(tmp_path)
    cache.put("Q25485", "wikidata.json", '{"x": 1}')
    children = list((tmp_path / "Q25485").iterdir())
    assert all(not c.name.endswith(".tmp") for c in children)


def test_cache_miss_returns_none(tmp_path: Path) -> None:
    cache = Cache(tmp_path)
    assert not cache.has("Q99999", "wikidata.json")
    assert cache.get("Q99999", "wikidata.json") is None


def test_cache_invalidation_by_age(tmp_path: Path) -> None:
    import os
    import time

    cache = Cache(tmp_path)
    cache.put("Q25485", "wikidata.json", "{}")
    path = tmp_path / "Q25485" / "wikidata.json"
    old = time.time() - (40 * 86400)
    os.utime(path, (old, old))
    assert cache.is_stale("Q25485", "wikidata.json", max_age_days=30)
    assert not cache.is_stale("Q25485", "wikidata.json", max_age_days=60)
