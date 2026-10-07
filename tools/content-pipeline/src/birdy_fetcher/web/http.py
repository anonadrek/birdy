"""Throttled, retried GET shared by the source clients (spec 2026-09-25 §9.1).

Wikimedia rate-limits hard under concurrent load and GBIF asks for polite clients, so every
request from one client goes through a single in-flight slot with a minimum gap between
request starts, and 429/5xx are retried (Retry-After when given, else exponential backoff).
The backoff sleep happens while holding the lock, so a 429 pauses the whole client instead
of letting other workers keep hitting the server while it asked us to back off."""

from __future__ import annotations

import asyncio
import time
from collections.abc import Awaitable, Callable, Mapping

import aiohttp

from ..wikipedia import USER_AGENT, _default_http_get

GetText = Callable[[str], Awaitable[str]]
GetBytes = Callable[[str], Awaitable[bytes]]

MAX_ATTEMPTS = 5
BACKOFF_SECONDS = (2.0, 4.0, 8.0, 16.0)
MAX_RETRY_AFTER_SECONDS = 60.0


def is_retryable_status(status: int | None) -> bool:
    if status is None:
        return False
    return status == 429 or 500 <= status < 600


def retry_after_seconds(exc: aiohttp.ClientResponseError) -> float | None:
    headers: Mapping[str, str] = exc.headers or {}
    for key, value in headers.items():
        if key.lower() != "retry-after":
            continue
        try:
            return float(value)
        except ValueError:
            return None
    return None


def retry_delay(exc: aiohttp.ClientResponseError, failures: int) -> float:
    retry_after = retry_after_seconds(exc)
    if retry_after is not None:
        return min(retry_after, MAX_RETRY_AFTER_SECONDS)
    return BACKOFF_SECONDS[min(failures - 1, len(BACKOFF_SECONDS) - 1)]


async def default_get_bytes(url: str) -> bytes:
    async with (
        aiohttp.ClientSession(headers={"User-Agent": USER_AGENT}) as session,
        session.get(url, timeout=aiohttp.ClientTimeout(total=120)) as response,
    ):
        if response.status == 404:
            raise FileNotFoundError(url)
        response.raise_for_status()
        return await response.read()


class ThrottledHttp:
    def __init__(
        self,
        *,
        get_text: GetText | None = None,
        get_bytes: GetBytes | None = None,
        min_interval: float = 1.0,
        sleep: Callable[[float], Awaitable[None]] = asyncio.sleep,
        clock: Callable[[], float] = time.monotonic,
    ) -> None:
        self._get_text = get_text or _default_http_get
        self._get_bytes = get_bytes or default_get_bytes
        self._min_interval = min_interval
        self._sleep = sleep
        self._clock = clock
        self._lock = asyncio.Lock()
        self._last_start: float | None = None

    async def fetch_text(self, url: str) -> str:
        return await self._run(lambda: self._get_text(url))

    async def fetch_bytes(self, url: str) -> bytes:
        return await self._run(lambda: self._get_bytes(url))

    async def _run[T](self, call: Callable[[], Awaitable[T]]) -> T:
        failures = 0
        while True:
            async with self._lock:
                await self._wait_for_slot()
                try:
                    return await call()
                except aiohttp.ClientResponseError as exc:
                    failures += 1
                    if not is_retryable_status(exc.status) or failures >= MAX_ATTEMPTS:
                        raise
                    delay = retry_delay(exc, failures)
                await self._sleep(delay)

    async def _wait_for_slot(self) -> None:
        if self._last_start is not None:
            remaining = self._min_interval - (self._clock() - self._last_start)
            if remaining > 0:
                await self._sleep(remaining)
        self._last_start = self._clock()
