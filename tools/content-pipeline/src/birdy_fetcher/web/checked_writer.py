"""The write, check, rewrite and remove loop that species texts and comparisons share
(spec 2026-09-25 §9.6 and §9.7).

Lifted out of `text_step.SpeciesTextWriter.write` (Task 22) as it stood after Task 20's
review fixes, with `Checks[T]` as the seam between the loop and each text type. What the
loop keeps from those fixes:

- (I2) the best attempt, ranked on the number of hard problems left after `settle`, wins
  over the rule retries, not just whichever attempt parsed last (ties go to the later
  attempt, it saw the retry feedback);
- (I2) after the second model flags sentences, the rewrite is used only when it passes the
  minimum requirements once its own unsupported sentences are removed; otherwise the
  original, minus what the FIRST check flagged, is used;
- (N6) a checker failure on the rewrite's own re-check falls back to the original the same
  way instead of failing the whole text;
- (M4) removal notes quote the removed text: `'{path} togs bort ("{text}"): {problem}'`.

Guards that depend on the species record (`_keep_old_text`, the published-page-cites-a-
struck-fact check N1, `_resolve_lookalikes`) are not part of the loop; they stay with the
step that owns the record."""

from __future__ import annotations

from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field

from anthropic.types import MessageParam
from pydantic import BaseModel

from .checker import CheckerFailed, CheckItem, SentenceChecker
from .llm import ModelReply
from .text_checks import TextIssue

RULE_ATTEMPTS = 2


@dataclass(frozen=True)
class Checks[T: BaseModel]:
    """What the loop needs to know about one kind of text.

    - `rules`: every code-check issue (an empty list means the answer passed as written);
    - `settle`: removes every removable part that breaks a rule and checks again; returns
      the text, removal notes and the problems that remain (they fail the text);
    - `items`: one item per sentence or cell for the second model;
    - `remove`: a copy without the parts named by the given paths;
    - `minimum`: the problems that keep the text off the site (empty when it may be saved);
    - `texts`: every removable path to the text it holds, for removal notes."""

    rules: Callable[[T], list[TextIssue]]
    settle: Callable[[T], tuple[T, list[str], list[str]]]
    items: Callable[[T], list[CheckItem]]
    remove: Callable[[T, set[str]], T]
    minimum: Callable[[T], list[str]]
    texts: Callable[[T], dict[str, str]]


@dataclass
class Written[T: BaseModel]:
    text: T | None = None
    rejected: T | None = None
    notes: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)
    attempts: int = 0


def rules_feedback(issues: list[TextIssue]) -> str:
    lines = "\n".join(f"- {i.path}: {i.message}" for i in issues)
    return (
        "Your answer broke these rules. Write the whole answer again with the same structure "
        "and fix only these points:\n" + lines + "\n"
        "Every sentence must list the ids of the facts it uses and say no more than they do."
    )


def support_feedback(unsupported: dict[str, str]) -> str:
    lines = "\n".join(f"- {path}: {problem}" for path, problem in unsupported.items())
    return (
        "A checker found sentences that say more than the facts they cite. Write the whole "
        "answer again with the same structure. Fix or drop these sentences and add nothing "
        "the facts do not say:\n" + lines
    )


def _removal_notes(unsupported: dict[str, str], texts: dict[str, str]) -> list[str]:
    return [
        f'{path} togs bort ("{texts.get(path, "")}"): {problem}'
        for path, problem in unsupported.items()
    ]


async def write_checked[T: BaseModel](
    *,
    ask: Callable[[list[MessageParam]], Awaitable[ModelReply[T]]],
    user: str,
    checks: Checks[T],
    checker: SentenceChecker,
    about: str,
) -> Written[T]:
    """Write with `ask`, check in code (one retry with the broken rules), check every
    sentence with `checker`, rewrite once, remove what still fails. `ask` must record the
    cost of every call itself; `MaxCostExceeded` and a `CheckerFailed` from the first check
    propagate to the caller."""
    base: list[MessageParam] = [{"role": "user", "content": user}]
    result: Written[T] = Written()
    messages = list(base)
    last_stop: str | None = None
    # (I2, review fix 2026-10-06) Track the attempt with the fewest hard problems after
    # settling, not just whichever attempt happened to parse last: a later retry can come
    # back worse than an earlier one, and the earlier one must still win. Ties favour the
    # later attempt (it saw the retry feedback).
    best: tuple[int, T, list[str], list[str]] | None = None
    for attempt in range(1, RULE_ATTEMPTS + 1):
        result.attempts = attempt
        reply = await ask(messages)
        last_stop = reply.stop_reason
        if reply.parsed is None:
            if reply.stop_reason in ("max_tokens", "refusal"):
                break
            continue
        candidate = reply.parsed
        issues = checks.rules(candidate)
        settled, removed, hard = checks.settle(candidate)
        if best is None or len(hard) <= best[0]:
            best = (len(hard), settled, removed, hard)
        if not issues:
            break
        if attempt < RULE_ATTEMPTS:
            messages = [
                *messages,
                {"role": "assistant", "content": reply.raw_text},
                {"role": "user", "content": rules_feedback(issues)},
            ]
    if best is None:
        result.errors = [f"modellen gav inget giltigt svar (stop_reason={last_stop})"]
        return result
    _, text, removed, hard = best
    result.notes += removed
    if hard:
        result.rejected, result.errors = text, hard
        return result

    unsupported = await checker.check(checks.items(text), about=about)
    if unsupported:
        original = text
        result.attempts += 1
        retry: list[MessageParam] = [
            *base,
            {"role": "assistant", "content": text.model_dump_json()},
            {"role": "user", "content": support_feedback(unsupported)},
        ]
        reply = await ask(retry)
        # (I2, review fix 2026-10-06) A rewrite that fixes the cited problems can still
        # introduce a new one (e.g. striking the only sentence in a required field).
        # Build both candidates and prefer the rewrite only when it actually passes the
        # minimum requirements; otherwise fall back to removing from the original, which
        # is always at least as safe since it already passed the rule checks.
        fixed_candidate: T | None = None
        candidate_removed: list[str] = []
        candidate_unsupported: dict[str, str] = {}
        candidate_texts: dict[str, str] = {}
        checker_failure: str | None = None
        if reply.parsed is not None:
            settled_candidate, candidate_removed, hard_again = checks.settle(reply.parsed)
            if not hard_again:
                try:
                    candidate_unsupported = await checker.check(
                        checks.items(settled_candidate), about=about
                    )
                except CheckerFailed as exc:
                    # (N6, review fix 2026-10-06) The checker failing on the rewrite's own
                    # re-check must not fail the whole text when a safe fallback (the
                    # original, already checked once) exists.
                    checker_failure = str(exc)
                else:
                    candidate_texts = checks.texts(settled_candidate)
                    fixed_candidate = checks.remove(settled_candidate, set(candidate_unsupported))
        if fixed_candidate is not None and not checks.minimum(fixed_candidate):
            text = fixed_candidate
            result.notes += candidate_removed
            result.notes += _removal_notes(candidate_unsupported, candidate_texts)
        else:
            if checker_failure is not None:
                result.notes.append(
                    f"kontrollen av omskrivningen misslyckades ({checker_failure}), den "
                    "ursprungliga texten användes i stället"
                )
            else:
                result.notes.append(
                    "omskrivningen för de fakta som inte stöds förkastades, den ursprungliga "
                    "texten användes i stället"
                )
            original_texts = checks.texts(original)
            text = checks.remove(original, set(unsupported))
            result.notes += _removal_notes(unsupported, original_texts)
    problems = checks.minimum(text)
    if problems:
        result.rejected, result.errors = text, problems
        return result
    result.text = text
    return result
