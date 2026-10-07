"""Tests for web/http.py: the shared throttled, retried GET."""

from __future__ import annotations

import aiohttp
import multidict
import pytest
import yarl

from birdy_fetcher.web.http import ThrottledHttp


def _response_error(status: int) -> aiohttp.ClientResponseError:
    request_info = aiohttp.RequestInfo(
        url=yarl.URL("https://x"),
        method="GET",
        headers=multidict.CIMultiDictProxy(multidict.CIMultiDict()),
        real_url=yarl.URL("https://x"),
    )
    return aiohttp.ClientResponseError(
        request_info=request_info, history=(), status=status, headers=multidict.CIMultiDict()
    )


async def test_fetch_bytes_retries_503_then_returns_the_body() -> None:
    calls = 0

    async def get_bytes(url: str) -> bytes:
        nonlocal calls
        calls += 1
        if calls == 1:
            raise _response_error(503)
        return b"ID3"

    sleeps: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleeps.append(seconds)

    http = ThrottledHttp(get_bytes=get_bytes, min_interval=0.0, sleep=fake_sleep, clock=lambda: 0.0)
    assert await http.fetch_bytes("https://x/a.mp3") == b"ID3"
    assert calls == 2
    assert 2.0 in sleeps


async def test_text_and_bytes_share_one_throttle() -> None:
    sleeps: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleeps.append(seconds)

    async def get_text(url: str) -> str:
        return "t"

    async def get_bytes(url: str) -> bytes:
        return b"b"

    http = ThrottledHttp(
        get_text=get_text,
        get_bytes=get_bytes,
        min_interval=1.0,
        sleep=fake_sleep,
        clock=lambda: 0.0,
    )
    await http.fetch_text("https://x/a")
    await http.fetch_bytes("https://x/b")
    assert sleeps == [1.0]


async def test_404_is_raised_without_retry() -> None:
    calls = 0

    async def get_text(url: str) -> str:
        nonlocal calls
        calls += 1
        raise FileNotFoundError(url)

    async def fake_sleep(seconds: float) -> None:
        return None

    http = ThrottledHttp(get_text=get_text, min_interval=0.0, sleep=fake_sleep, clock=lambda: 0.0)
    with pytest.raises(FileNotFoundError):
        await http.fetch_text("https://x/missing")
    assert calls == 1
