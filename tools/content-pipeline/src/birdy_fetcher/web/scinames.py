"""A look-alike's scientific name as an article writes it, matched to one of Birdy's species.

Articles abbreviate the genus once it has been named ("C. corone"), add a subspecies
("C. corone corone") or use an older genus ("Parus caeruleus"). The fact sheet keeps the
name as the article gives it (prompt facts-v1), so before the R3 trial (2026-10-07) only an
exact binomial got a QID and Råka's look-alike box could not link to its Kråka (Carrion
Crow, Corvus corone). Matching is by scientific name only, never by Swedish name: Birdy
calls Corvus corone "Kråka", the article "svartkråka".

Birdy's species list has no synonyms, so an older genus is matched on the species epithet
alone, and only inside the family of the species the page is about (look-alikes are nearly
always close relatives) when exactly one species there has that epithet."""

from __future__ import annotations

import re

_TOKEN = re.compile(r"[A-Za-z]+\.?")


def _canonical(binomial: str) -> str:
    genus, epithet = binomial.split(" ", 1)
    return f"{genus.capitalize()} {epithet.lower()}"


def _genus(binomial: str) -> str:
    return binomial.split(" ", 1)[0]


def _pick(candidates: list[str], subject: str | None, context: str) -> str | None:
    """One binomial from several the abbreviation fits: the species' own genus, else the
    only one the articles write out in full."""
    unique = sorted(set(candidates))
    if len(unique) == 1:
        return unique[0]
    if subject:
        own = [c for c in unique if _genus(c) == _genus(subject.lower())]
        if len(own) == 1:
            return own[0]
    written = [c for c in unique if re.search(rf"\b{re.escape(c)}\b", context, re.IGNORECASE)]
    return written[0] if len(written) == 1 else None


def _expand(
    genus: str, epithet: str, index: dict[str, str], subject: str | None, context: str
) -> str | None:
    """`genus epithet` in the index, with an abbreviated genus ("C.", "Ph.") expanded to a
    genus that begins the same way."""
    if not genus.endswith("."):
        name = f"{genus} {epithet}"
        return name if name in index else None
    prefix = genus[:-1]
    candidates = [
        name
        for name in index
        if name.split(" ", 1)[1:] == [epithet] and _genus(name).startswith(prefix)
    ]
    return _pick(candidates, subject, context)


def _older_genus(
    epithet: str, index: dict[str, str], subject: str | None, families: dict[str, str]
) -> str | None:
    """The one species in the subject's family with this epithet ("Parus caeruleus" for
    Cyanistes caeruleus when the page is about a tit)."""
    family = families.get(subject.lower()) if subject else None
    if not family:
        return None
    same = [
        name
        for name in index
        if name.split(" ", 1)[1:] == [epithet] and families.get(name) == family
    ]
    return same[0] if len(same) == 1 else None


def resolve_lookalike(
    written: str,
    index: dict[str, str],
    *,
    subject: str | None = None,
    families: dict[str, str] | None = None,
    context: str = "",
) -> tuple[str, str] | None:
    """(the species' scientific name as Birdy writes it, its QID), or None when the name
    matches none of Birdy's species. `index` maps lowercased binomials to QIDs, `families`
    them to their family, `subject` is the page's own species and `context` the article text
    an abbreviation can be read against."""
    raw = _TOKEN.findall(written)
    if len(raw) < 2:
        return None
    genus, epithet = raw[0].lower(), raw[1].lower()
    # A third word is a subspecies only in lower case ("Corvus corone Linnaeus" names the
    # author).
    third = raw[2] if len(raw) > 2 else ""
    subspecies = third if third.islower() and not third.endswith(".") else None
    tries: list[str] = []
    if subspecies and subspecies != epithet:
        # "Corvus corone cornix": a subspecies since split off as its own species
        # (Corvus cornix) is that species, before the old species it was part of.
        tries.append(subspecies)
    if not epithet.endswith("."):
        tries.append(epithet)
    for name_epithet in tries:
        found = _expand(genus, name_epithet, index, subject, context)
        if found is None and not genus.endswith("."):
            found = _older_genus(name_epithet, index, subject, families or {})
        if found is not None:
            return _canonical(found), index[found]
    return None
