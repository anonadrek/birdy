"""A look-alike's scientific name as an article writes it, matched to one of Birdy's species.

Articles abbreviate the genus once it has been named ("C. corone"), add a subspecies
("C. corone corone") or use an older genus ("Parus caeruleus"). The fact sheet keeps the
name as the article gives it (prompt facts-v1), so before the R3 trial (2026-10-07) only an
exact binomial got a QID and Råka's look-alike box could not link to its Kråka (Carrion
Crow, Corvus corone). Matching is by scientific name, never by Swedish name alone: Birdy
calls Corvus corone "Kråka", the article "svartkråka".

What counts (fix wave 2026-10-07, after wrong links in a review):
- A binomial in Birdy's list, written out in full, is that species.
- An abbreviated genus ("C.") that fits the page species' own genus means that genus only.
  One that does not fit names another genus: a guess.
- A subspecies is its species, or the species it has since been split off as ("Corvus
  corone cornix" is Corvus cornix), always in the genus as written.
- Birdy's list has no synonyms, so a full genus not in the list ("Parus caeruleus") is
  matched on the epithet inside the page species' family: a guess. Never for a subspecies
  ("Nucifraga c. macrorhynchos" is not Corvus macrorhynchos).
- A guess is only taken when the fact or its quotes name that species in Swedish or English
  ("Aythya americana" is not Mareca americana, the American Wigeon).
- A look-alike is never the page species itself (a subspecies of it, or an older name)."""

from __future__ import annotations

import re
from dataclasses import dataclass, field

_TOKEN = re.compile(r"[A-Za-z]+\.?")
# English names in Birdy's list carry a qualifier the articles often leave out.
_QUALIFIERS = ("eurasian ", "common ", "european ")


@dataclass(frozen=True)
class NameContext:
    """What a look-alike's written name is resolved against. All keys are lowercased
    binomials: `index` to QID, `families` to family, `common` to the Swedish and English
    names. `subject` and `own_qid` are the page's own species."""

    index: dict[str, str]
    families: dict[str, str] = field(default_factory=dict)
    common: dict[str, tuple[str, ...]] = field(default_factory=dict)
    subject: str | None = None
    own_qid: str | None = None


@dataclass(frozen=True)
class Resolution:
    """Birdy's binomial and QID for the look-alike, or neither; `note` says why a candidate
    was not linked (for the facts report)."""

    binomial: str | None = None
    qid: str | None = None
    note: str | None = None


def _canonical(binomial: str) -> str:
    genus, epithet = binomial.split(" ", 1)
    return f"{genus.capitalize()} {epithet.lower()}"


def _genus(binomial: str) -> str:
    return binomial.split(" ", 1)[0]


def _with_epithet(index: dict[str, str], epithet: str) -> list[str]:
    return [name for name in index if name.split(" ", 1)[1:] == [epithet]]


def _candidates(
    genus: str, epithet: str, ctx: NameContext, *, family_rule: bool
) -> tuple[list[str], bool]:
    """(binomials the written genus and epithet can mean, whether that is a guess)."""
    own_genus = _genus(ctx.subject.lower()) if ctx.subject else None
    if genus.endswith("."):
        prefix = genus[:-1]
        if own_genus is not None and own_genus.startswith(prefix):
            name = f"{own_genus} {epithet}"
            return ([name] if name in ctx.index else []), False
        found = [n for n in _with_epithet(ctx.index, epithet) if _genus(n).startswith(prefix)]
        return sorted(found), True
    name = f"{genus} {epithet}"
    if name in ctx.index:
        return [name], False
    family = ctx.families.get(ctx.subject.lower()) if ctx.subject else None
    if not family_rule or not family:
        return [], False
    same = [n for n in _with_epithet(ctx.index, epithet) if ctx.families.get(n) == family]
    return sorted(same), True


def _named(binomial: str, ctx: NameContext, evidence: str) -> bool:
    """Whether the fact or its quotes name this species, in Swedish or English (as a word
    start: "blåmes" is in "blåmesen", "kråka" is not in "svartkråka")."""
    text = evidence.lower()
    for name in ctx.common.get(binomial, ()):
        low = name.lower()
        variants = [low] + [low[len(q) :] for q in _QUALIFIERS if low.startswith(q)]
        if any(re.search(rf"(?<!\w){re.escape(v)}", text) for v in variants if v):
            return True
    return False


def _label(binomial: str, ctx: NameContext) -> str:
    names = ctx.common.get(binomial, ())
    return f"{names[0]} ({_canonical(binomial)})" if names else _canonical(binomial)


def resolve_lookalike(written: str, ctx: NameContext, *, evidence: str = "") -> Resolution:
    """How a look-alike written `written` resolves on the page of `ctx.subject`. `evidence`
    is the fact's text and quotes: a guessed genus needs the species named there."""
    raw = _TOKEN.findall(written)
    if len(raw) < 2:
        return Resolution()
    genus, epithet = raw[0].lower(), raw[1].lower()
    # A third word is a subspecies only in lower case ("Corvus corone Linnaeus" names the
    # author).
    third = raw[2] if len(raw) > 2 else ""
    subspecies = third if third.islower() and not third.endswith(".") else None
    attempts: list[tuple[str, bool]] = []
    if subspecies and subspecies != epithet:
        # "Corvus corone cornix": a subspecies since split off as its own species (Corvus
        # cornix) is that species, before the old species it was part of.
        attempts.append((subspecies, False))
    if not epithet.endswith("."):
        attempts.append((epithet, True))
    for name_epithet, family_rule in attempts:
        found, guessed = _candidates(genus, name_epithet, ctx, family_rule=family_rule)
        if not found:
            continue
        if ctx.own_qid is not None and any(ctx.index[n] == ctx.own_qid for n in found):
            return Resolution(
                note=f"förväxlingsarten {written} är arten själv eller en underart av den: "
                "ingen länk"
            )
        if guessed:
            named = [n for n in found if _named(n, ctx, evidence)]
            if len(named) != 1:
                options = ", ".join(_label(n, ctx) for n in found)
                return Resolution(
                    note=f"förväxlingsarten {written} kopplades inte till {options}: "
                    "namnet står inte (entydigt) i faktumet eller citaten"
                )
            found = named
        return Resolution(_canonical(found[0]), ctx.index[found[0]])
    return Resolution()
